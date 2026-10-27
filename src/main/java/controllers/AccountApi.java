package controllers;

import business.AccountCredentials;
import business.PasswordHasher;
import business.User;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import data.TodoDB;

import javax.naming.NamingException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class AccountApi extends HttpServlet {
    public static final String SESSION_USER = AccountApi.class.getName() + ".user";

    private static final Gson GSON = new Gson();
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$8p9N.tnIKRvU0lebUNsGqO9AzSehd3AisiLpU7ruiJB8iypIZJvuu";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{3,50}");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    private static final class AccountRequest {
        private String username;
        private String password;
        private String firstName;
        private String lastName;
        private String email;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        setNoStore(response);
        if (!"session".equals(path(request))) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown account resource");
            return;
        }

        User user = getSessionUser(request);
        if (user == null) {
            sendJson(response, Map.of("authenticated", false));
            return;
        }
        sendJson(response, Map.of("authenticated", true, "user", userPayload(user)));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        setNoStore(response);
        if (!isJson(request)) {
            sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Send account requests as JSON");
            return;
        }

        try {
            switch (path(request)) {
                case "login" -> login(request, response);
                case "register" -> register(request, response);
                case "logout" -> logout(request, response);
                default -> sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown account resource");
            }
        } catch (NamingException | SQLException exception) {
            handleDatabaseError(response, exception);
        }
    }

    private void login(HttpServletRequest request, HttpServletResponse response)
            throws NamingException, SQLException {
        AccountRequest body = readBody(request, response);
        if (body == null) {
            return;
        }
        String username = body.username == null ? "" : body.username.trim();
        if (username.isEmpty() || body.password == null || body.password.isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Username and password are required");
            return;
        }
        if (body.password.getBytes(StandardCharsets.UTF_8).length > 72) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid username or password");
            return;
        }

        AccountCredentials account = TodoDB.findAccount(username);
        String storedHash = account == null ? DUMMY_PASSWORD_HASH : account.passwordHash();
        boolean passwordMatches = PasswordHasher.verify(body.password, storedHash);
        if (account == null || !passwordMatches) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid username or password");
            return;
        }
        if (PasswordHasher.needsUpgrade(storedHash)) {
            TodoDB.updatePasswordHash(username, PasswordHasher.hash(body.password));
        }
        startSession(request, account.user());
        sendJson(response, Map.of("user", userPayload(account.user())));
    }

    private void register(HttpServletRequest request, HttpServletResponse response)
            throws NamingException, SQLException {
        AccountRequest body = readBody(request, response);
        if (body == null) {
            return;
        }

        String username = body.username == null ? "" : body.username.trim();
        String firstName = body.firstName == null ? "" : body.firstName.trim();
        String lastName = body.lastName == null ? "" : body.lastName.trim();
        String email = body.email == null ? "" : body.email.trim().toLowerCase(Locale.ROOT);
        String validationError = validateRegistration(username, firstName, lastName, email, body.password);
        if (validationError != null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, validationError);
            return;
        }

        String passwordHash = PasswordHasher.hash(body.password);
        User user = TodoDB.createAccount(username, firstName, lastName, email, passwordHash);
        startSession(request, user);
        response.setStatus(HttpServletResponse.SC_CREATED);
        sendJson(response, Map.of("user", userPayload(user)));
    }

    private void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        sendJson(response, Map.of("authenticated", false));
    }

    private static String validateRegistration(String username, String firstName, String lastName,
            String email, String password) {
        if (!USERNAME.matcher(username).matches()) {
            return "Username must be 3-50 characters using letters, numbers, dots, underscores, or hyphens";
        }
        if (firstName.isBlank() || firstName.length() > 50
                || lastName.isBlank() || lastName.length() > 50) {
            return "First and last names are required and must be no longer than 50 characters";
        }
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            return "Enter a valid email address";
        }
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "Password must be at least 12 characters and no more than 72 UTF-8 bytes";
        }
        return null;
    }

    private AccountRequest readBody(HttpServletRequest request, HttpServletResponse response) {
        if (request.getContentLengthLong() > 8192) {
            sendError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Account request is too large");
            return null;
        }
        try {
            StringBuilder json = new StringBuilder();
            char[] buffer = new char[1024];
            try (BufferedReader reader = request.getReader()) {
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    if (json.length() + count > 8192) {
                        sendError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                                "Account request is too large");
                        return null;
                    }
                    json.append(buffer, 0, count);
                }
            }
            AccountRequest body = GSON.fromJson(json.toString(), AccountRequest.class);
            if (body == null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "A JSON request body is required");
            }
            return body;
        } catch (IOException | JsonParseException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON request");
            return null;
        }
    }

    private void startSession(HttpServletRequest request, User user) {
        HttpSession session = request.getSession();
        request.changeSessionId();
        session.setMaxInactiveInterval(30 * 60);
        session.setAttribute(SESSION_USER, user);
    }

    public static User getSessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_USER);
        return value instanceof User user ? user : null;
    }

    private static String path(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static boolean isJson(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null
                && contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json");
    }

    private void handleDatabaseError(HttpServletResponse response, Exception exception) {
        if (exception instanceof SQLException sqlException
                && sqlException.getSQLState() != null
                && sqlException.getSQLState().startsWith("23")) {
            sendError(response, HttpServletResponse.SC_CONFLICT, "Username or email is already in use");
            return;
        }
        getServletContext().log("Unable to process account request", exception);
        sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to process account request");
    }

    private static Map<String, Object> userPayload(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getUserId());
        result.put("userName", user.getUserName());
        result.put("firstName", user.getFirstName());
        result.put("lastName", user.getLastName());
        result.put("email", user.getEmail());
        result.put("role", user.getRole());
        return result;
    }

    private static void setNoStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    private void sendJson(HttpServletResponse response, Object value) {
        try {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(GSON.toJson(value));
        } catch (IOException exception) {
            getServletContext().log("Unable to write account API response", exception);
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        sendJson(response, Map.of("error", message));
    }
}
