package controllers;

import business.InAppNotification;
import business.NotificationService;
import business.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import data.TodoDB;

import javax.naming.NamingException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class NotificationsApi extends HttpServlet {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        setNoStore(response);
        User user = requireUser(request, response);
        if (user == null) {
            return;
        }
        if (!"/".equals(path(request))) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown notification resource");
            return;
        }
        try {
            List<InAppNotification> notifications = TodoDB.selectNotifications(user.getUserId());
            sendJson(response, notifications);
        } catch (NamingException | SQLException exception) {
            handleDatabaseError(response, exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        setNoStore(response);
        User user = requireUser(request, response);
        if (user == null) {
            return;
        }
        if (!"/check".equals(path(request))) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown notification resource");
            return;
        }
        try {
            boolean generated = NotificationService.processStartup(user.getUserId(), Instant.now());
            sendJson(response, Map.of("generated", generated));
        } catch (NamingException | SQLException exception) {
            handleDatabaseError(response, exception);
        } catch (DateTimeException exception) {
            getServletContext().log("Unable to process scheduled notifications", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to process scheduled notifications");
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        setNoStore(response);
        User user = requireUser(request, response);
        if (user == null) {
            return;
        }
        if (!"/read".equals(path(request))) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown notification resource");
            return;
        }
        Integer notificationId = parseId(request, response);
        if (notificationId == null) {
            return;
        }
        try {
            if (!TodoDB.markNotificationRead(notificationId, user.getUserId())) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Notification not found");
                return;
            }
            sendJson(response, Map.of("read", true));
        } catch (NamingException | SQLException exception) {
            handleDatabaseError(response, exception);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        setNoStore(response);
        User user = requireUser(request, response);
        if (user == null) {
            return;
        }
        if (!"/".equals(path(request))) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown notification resource");
            return;
        }
        String id = request.getParameter("id");
        try {
            if (id == null) {
                int deletedCount = TodoDB.clearNotifications(user.getUserId());
                sendJson(response, Map.of("deletedCount", deletedCount));
                return;
            }
            Integer notificationId = parseId(id, response);
            if (notificationId == null) {
                return;
            }
            if (!TodoDB.deleteNotification(notificationId, user.getUserId())) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Notification not found");
                return;
            }
            sendJson(response, Map.of("deleted", true));
        } catch (NamingException | SQLException exception) {
            handleDatabaseError(response, exception);
        }
    }

    private User requireUser(HttpServletRequest request, HttpServletResponse response) {
        User user = AccountApi.getSessionUser(request);
        if (user == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to view notifications");
        }
        return user;
    }

    private static String path(HttpServletRequest request) {
        String path = request.getPathInfo();
        return path == null || path.isEmpty() ? "/" : path;
    }

    private Integer parseId(HttpServletRequest request, HttpServletResponse response) {
        return parseId(request.getParameter("id"), response);
    }

    private Integer parseId(String value, HttpServletResponse response) {
        if (value == null || value.isBlank()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "A notification ID is required");
            return null;
        }
        try {
            int id = Integer.parseInt(value);
            if (id <= 0) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "IDs must be positive integers");
                return null;
            }
            return id;
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "IDs must be integers");
            return null;
        }
    }

    private void handleDatabaseError(HttpServletResponse response, Exception exception) {
        getServletContext().log("Unable to process notifications", exception);
        sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to process notifications");
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
            getServletContext().log("Unable to write notification API response", exception);
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        sendJson(response, Map.of("error", message));
    }
}
