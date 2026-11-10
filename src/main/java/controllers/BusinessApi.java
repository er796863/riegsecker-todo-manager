package controllers;

import business.Schedule;
import business.Task;
import business.User;
import data.TodoDB;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import javax.naming.NamingException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static business.PasswordHasher.hash;
import static controllers.AccountApi.getSessionUser;
import static controllers.AccountApi.validateProfile;
import static data.TodoDB.createManagedUser;
import static data.TodoDB.deleteManagedUser;
import static data.TodoDB.isAdministrator;
import static data.TodoDB.updateManagedUser;

public class BusinessApi extends HttpServlet {
    private static final Gson GSON = new Gson();
    private static final Set<String> ROLES = Set.of("USER", "ADMIN");
    private static final Set<String> WEEKDAYS = Set.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    private static final String TASKS = "tasks";
    private static final String USERS = "users";
    private static final String SCHEDULES = "schedules";

    private static final class TaskRequest {
        private String taskTitle;
        private String taskDescription;
        private String taskDateDue;
        private Integer userId;
    }

    private static final class ScheduleRequest {
        private Boolean notificationsEnabled;
        private List<String> notificationDays;
        private String notificationTime;
        private String notificationTimezone;
        private List<Integer> taskIds;
        private Integer userId;
    }

    private static final class UserRequest {
        private String username;
        private String firstName;
        private String lastName;
        private String email;
        private String password;
        private String role;
    }

    private record RequestScope(boolean authorized, Integer ownerId, boolean allRecords) {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");

        String path = request.getPathInfo();
        String entity = path == null ? "" : determineEntityFromPath(path);
        String search = request.getParameter("search");

        if (!TASKS.equals(entity) && !USERS.equals(entity) && !SCHEDULES.equals(entity)) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown resource");
            return;
        }

        User currentUser = getSessionUser(request);

        if (currentUser == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to view records");
            return;
        }

        String scope = request.getParameter("scope");
        boolean allRecords = "all".equalsIgnoreCase(scope);

        if (scope != null && !allRecords && !"mine".equalsIgnoreCase(scope)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Scope must be mine or all");
            return;
        }

        if (allRecords && !hasAdminAccess(response, currentUser)) {
            return;
        }

        Integer ownerId = allRecords ? null : currentUser.getUserId();
        String requestedUserId = request.getParameter("userId");

        if (requestedUserId != null) {
            if (!allRecords) {
                sendError(response, HttpServletResponse.SC_FORBIDDEN, "Administrator access required");
                return;
            }

            try {
                ownerId = Integer.valueOf(requestedUserId);
            } catch (NumberFormatException exception) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "IDs must be integers");
                return;
            }
        }

        try {
            switch (entity) {
                case TASKS -> getTasks(request, response, search, ownerId);
                case USERS -> getUsers(request, response, search, ownerId);
                case SCHEDULES -> getSchedules(request, response, search, ownerId);
                default -> sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown resource");
            }
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "IDs must be integers");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        String entity = entityFromRequest(request, response);

        if (entity == null) {
            return;
        }

        User currentUser = requireUser(request, response);

        if (currentUser == null) {
            return;
        }

        if (!isJson(request)) {
            sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Send requests as JSON");
            return;
        }

        try {
            switch (entity) {
                case TASKS -> createTask(request, response, currentUser);
                case SCHEDULES -> createSchedule(request, response, currentUser);
                case USERS -> createUser(request, response, currentUser);
                default -> sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown resource");
            }
        } catch (IOException exception) {
            getServletContext().log("Unable to read " + entity + " request", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to read request");
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "IDs must be integers");
        } catch (DateTimeException | IllegalArgumentException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (NamingException | SQLException exception) {
            handleMutationError(entity, response, exception);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        String entity = entityFromRequest(request, response);

        if (entity == null) {
            return;
        }

        User currentUser = requireUser(request, response);

        if (currentUser == null) {
            return;
        }

        if (!isJson(request)) {
            sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Send requests as JSON");
            return;
        }

        Integer id = parseId(request, response);

        if (id == null) {
            return;
        }

        try {
            switch (entity) {
                case TASKS -> updateTask(request, response, currentUser, id);
                case SCHEDULES -> updateSchedule(request, response, currentUser, id);
                case USERS -> updateUser(request, response, currentUser, id);
                default -> sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown resource");
            }
        } catch (IOException exception) {
            getServletContext().log("Unable to read " + entity + " request", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to read request");
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "IDs must be integers");
        } catch (DateTimeException | IllegalArgumentException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (NamingException | SQLException exception) {
            handleMutationError(entity, response, exception);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        String entity = entityFromRequest(request, response);

        if (entity == null) {
            return;
        }

        User currentUser = requireUser(request, response);

        if (currentUser == null) {
            return;
        }

        Integer id = parseId(request, response);

        if (id == null) {
            return;
        }

        try {
            switch (entity) {
                case TASKS -> deleteTask(request, response, currentUser, id);
                case SCHEDULES -> deleteSchedule(request, response, currentUser, id);
                case USERS -> deleteUser(response, currentUser, id);
                default -> sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown resource");
            }
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "IDs must be integers");
        } catch (IllegalArgumentException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (NamingException | SQLException exception) {
            handleMutationError(entity, response, exception);
        }
    }

    private String entityFromRequest(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getPathInfo();
        String entity = path == null ? "" : determineEntityFromPath(path);

        if (!TASKS.equals(entity) && !USERS.equals(entity) && !SCHEDULES.equals(entity)) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown resource");
            return null;
        }

        return entity;
    }

    private User requireUser(HttpServletRequest request, HttpServletResponse response) {
        User currentUser = getSessionUser(request);
        if (currentUser == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to manage records");
        }

        return currentUser;
    }

    private RequestScope resolveScope(HttpServletRequest request, HttpServletResponse response, User user) {
        String scope = request.getParameter("scope");
        boolean allRecords = "all".equalsIgnoreCase(scope);

        if (scope != null && !allRecords && !"mine".equalsIgnoreCase(scope)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Scope must be mine or all");
            return new RequestScope(false, null, false);
        }

        if (allRecords && !hasAdminAccess(response, user)) {
            return new RequestScope(false, null, false);
        }

        return new RequestScope(true, allRecords ? null : user.getUserId(), allRecords);
    }

    private boolean requireAdmin(HttpServletResponse response, User user) {
        return hasAdminAccess(response, user);
    }

    private boolean hasAdminAccess(HttpServletResponse response, User user) {
        if (user.getUserId() == null) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "Administrator access required");
            return false;
        }

        try {
            if (isAdministrator(user.getUserId())) {
                return true;
            }
        } catch (NamingException | SQLException exception) {
            getServletContext().log("Unable to verify administrator access", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to verify permissions");
            return false;
        }

        sendError(response, HttpServletResponse.SC_FORBIDDEN, "Administrator access required");
        return false;
    }

    private <T> T readBody(HttpServletRequest request, HttpServletResponse response, Class<T> type)
            throws IOException {
        if (request.getContentLengthLong() > 8192) {
            sendError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body is too large");
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
                                "Request body is too large");

                        return null;
                    }

                    json.append(buffer, 0, count);
                }
            }

            T body = GSON.fromJson(json.toString(), type);
            if (body == null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "A JSON request body is required");
            }

            return body;
        } catch (JsonParseException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON request");
            return null;
        }
    }

    private Integer parseId(HttpServletRequest request, HttpServletResponse response) {
        String value = request.getParameter("id");
        if (value == null || value.isBlank()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "An entity ID is required");
            return null;
        }

        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "IDs must be integers");
            return null;
        }
    }

    private void createTask(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws IOException, NamingException, SQLException {
        TaskRequest body = readBody(request, response, TaskRequest.class);
        if (body == null) {
            return;
        }

        RequestScope scope = resolveScope(request, response, currentUser);
        if (!scope.authorized()) {
            return;
        }

        String error = validateTask(body);
        if (error != null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, error);
            return;
        }

        Integer ownerId = scope.ownerId() == null ? currentUser.getUserId() : scope.ownerId();
        if (scope.allRecords() && body.userId != null) {
            ownerId = body.userId;
        }

        int id = TodoDB.createTask(ownerId, taskFrom(body));
        response.setStatus(HttpServletResponse.SC_CREATED);
        sendJson(response, Map.of("taskId", id));
    }

    private void updateTask(HttpServletRequest request, HttpServletResponse response, User currentUser, Integer id)
            throws IOException, NamingException, SQLException {
        TaskRequest body = readBody(request, response, TaskRequest.class);
        if (body == null) {
            return;
        }

        RequestScope scope = resolveScope(request, response, currentUser);
        if (!scope.authorized()) {
            return;
        }

        String error = validateTask(body);
        if (error != null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, error);
            return;
        }

        if (!TodoDB.updateTask(id, scope.ownerId(), taskFrom(body))) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Task not found");
            return;
        }

        sendJson(response, Map.of("updated", true));
    }

    private void deleteTask(HttpServletRequest request, HttpServletResponse response, User currentUser, Integer id)
            throws NamingException, SQLException {
        RequestScope scope = resolveScope(request, response, currentUser);
        if (!scope.authorized()) {
            return;
        }

        if (!TodoDB.deleteTask(id, scope.ownerId())) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Task not found");
            return;
        }

        sendJson(response, Map.of("deleted", true));
    }

    private void createSchedule(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws IOException, NamingException, SQLException {
        ScheduleRequest body = readBody(request, response, ScheduleRequest.class);
        if (body == null) {
            return;
        }

        RequestScope scope = resolveScope(request, response, currentUser);
        if (!scope.authorized()) {
            return;
        }

        String error = validateSchedule(body);
        if (error != null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, error);
            return;
        }

        String taskIdsError = validateTaskIds(body.taskIds);
        if (taskIdsError != null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, taskIdsError);
            return;
        }

        Integer userId = scope.ownerId() == null ? currentUser.getUserId() : scope.ownerId();
        if (scope.allRecords() && body.userId != null) {
            userId = body.userId;
        }

        List<String> notificationDays = normalizeNotificationDays(body.notificationDays);
        if (!TodoDB.createSchedule(userId, notificationSetting(body.notificationsEnabled, notificationDays),
                body.notificationsEnabled, notificationDays, body.notificationTime,
                body.notificationTimezone.trim(), body.taskIds)) {
            sendError(response, HttpServletResponse.SC_CONFLICT, "A schedule already exists for this user");
            return;
        }

        response.setStatus(HttpServletResponse.SC_CREATED);
        sendJson(response, Map.of("userId", userId));
    }

    private void updateSchedule(HttpServletRequest request, HttpServletResponse response, User currentUser,
            Integer userId) throws IOException, NamingException, SQLException {
        ScheduleRequest body = readBody(request, response, ScheduleRequest.class);
        if (body == null) {
            return;
        }

        RequestScope scope = resolveScope(request, response, currentUser);
        if (!scope.authorized()) {
            return;
        }

        String error = validateSchedule(body);
        if (error != null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, error);
            return;
        }

        String taskIdsError = validateTaskIds(body.taskIds);
        if (taskIdsError != null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, taskIdsError);
            return;
        }

        List<String> notificationDays = normalizeNotificationDays(body.notificationDays);
        if (!TodoDB.updateSchedule(userId, scope.ownerId(),
                notificationSetting(body.notificationsEnabled, notificationDays), body.notificationsEnabled,
                notificationDays, body.notificationTime, body.notificationTimezone.trim(), body.taskIds)) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Schedule not found");
            return;
        }

        sendJson(response, Map.of("updated", true));
    }

    private void deleteSchedule(HttpServletRequest request, HttpServletResponse response, User currentUser,
            Integer userId) throws NamingException, SQLException {
        RequestScope scope = resolveScope(request, response, currentUser);
        if (!scope.authorized()) {
            return;
        }

        if (!TodoDB.deleteSchedule(userId, scope.ownerId())) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Schedule not found");
            return;
        }

        sendJson(response, Map.of("deleted", true));
    }

    private void createUser(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws IOException, NamingException, SQLException {
        if (!requireAdmin(response, currentUser)) {
            return;
        }

        UserRequest body = readBody(request, response, UserRequest.class);
        if (body == null) {
            return;
        }

        String error = validateUser(body, true);
        if (error != null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, error);
            return;
        }

        User user = createManagedUser(body.username.trim(), body.firstName.trim(), body.lastName.trim(),
                body.email.trim().toLowerCase(Locale.ROOT), hash(body.password),
                normalizeRole(body.role));

        response.setStatus(HttpServletResponse.SC_CREATED);
        sendJson(response, toJson(user));
    }

    private void updateUser(HttpServletRequest request, HttpServletResponse response, User currentUser, Integer id)
            throws IOException, NamingException, SQLException {
        if (!requireAdmin(response, currentUser)) {
            return;
        }

        if (id.equals(currentUser.getUserId())) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "You cannot edit your own administrator account");
            return;
        }

        UserRequest body = readBody(request, response, UserRequest.class);
        if (body == null) {
            return;
        }

        String error = validateUser(body, false);
        if (error != null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, error);
            return;
        }

        String passwordHash = body.password == null || body.password.isBlank()
                ? null : hash(body.password);

        if (!updateManagedUser(id, body.username.trim(), body.firstName.trim(), body.lastName.trim(),
                body.email.trim().toLowerCase(Locale.ROOT), passwordHash, normalizeRole(body.role))) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "User not found");
            return;
        }

        sendJson(response, Map.of("updated", true));
    }

    private void deleteUser(HttpServletResponse response, User currentUser, Integer id)
            throws NamingException, SQLException {
        if (!requireAdmin(response, currentUser)) {
            return;
        }

        if (id.equals(currentUser.getUserId())) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "You cannot delete your own administrator account");
            return;
        }

        if (!deleteManagedUser(id, currentUser.getUserId())) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "User not found");
            return;
        }

        sendJson(response, Map.of("deleted", true));
    }

    private static String validateTask(TaskRequest body) {
        if (body.taskTitle == null || body.taskTitle.isBlank() || body.taskTitle.trim().length() > 150) {
            return "Task title is required and must be no longer than 150 characters";
        }

        if (body.taskDescription != null && body.taskDescription.length() > 1000) {
            return "Task description must be no longer than 1000 characters";
        }

        parseDate(body.taskDateDue);
        return null;
    }

    private static Task taskFrom(TaskRequest body) {
        return new Task(null, body.taskTitle.trim(),
                body.taskDescription == null || body.taskDescription.isBlank() ? null : body.taskDescription.trim(),
                null, parseDate(body.taskDateDue));
    }

    private static LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return LocalDateTime.parse(value);
    }

    private static String validateSchedule(ScheduleRequest body) {
        if (body.notificationsEnabled == null) {
            return "Choose whether notifications are enabled";
        }

        if (body.notificationDays == null
                || body.notificationDays.stream().anyMatch(day -> day == null
                        || !WEEKDAYS.contains(day.trim().toUpperCase(Locale.ROOT)))) {
            return "Choose valid days of the week";
        }

        List<String> normalizedDays = normalizeNotificationDays(body.notificationDays);
        if (normalizedDays.size() != body.notificationDays.size()) {
            return "Choose each day no more than once";
        }

        if (body.notificationsEnabled && normalizedDays.isEmpty()) {
            return "Select at least one day when notifications are enabled";
        }

        if (body.notificationTime == null || !body.notificationTime.matches("\\d{2}:\\d{2}")) {
            return "Enter a valid notification time";
        }

        try {
            LocalTime.parse(body.notificationTime);
        } catch (DateTimeException exception) {
            return "Enter a valid notification time";
        }

        if (body.notificationTimezone == null || body.notificationTimezone.isBlank()
                || body.notificationTimezone.length() > 64) {
            return "A valid time zone is required";
        }

        try {
            String timezone = body.notificationTimezone.trim();
            if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
                return "Enter a valid IANA time zone";
            }

            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            return "Enter a valid IANA time zone";
        }

        return null;
    }

    private static List<String> normalizeNotificationDays(List<String> days) {
        return days.stream()
                .map(day -> day.trim().toUpperCase(Locale.ROOT))
                .sorted((first, second) -> Integer.compare(
                        DayOfWeek.valueOf(first).getValue(), DayOfWeek.valueOf(second).getValue()))
                .toList();
    }

    private static String notificationSetting(boolean enabled, List<String> days) {
        if (!enabled) {
            return "NONE";
        }

        return days.size() == WEEKDAYS.size() ? "DAILY" : "WEEKLY";
    }

    private static String validateTaskIds(List<Integer> taskIds) {
        if (taskIds == null) {
            return null;
        }

        if (taskIds.stream().anyMatch(id -> id == null || id <= 0)) {
            return "Task IDs must be positive integers";
        }

        if (taskIds.size() != taskIds.stream().distinct().count()) {
            return "Task IDs must be unique";
        }

        return null;
    }

    private static String validateUser(UserRequest body, boolean requirePassword) {
        String username = body.username == null ? "" : body.username.trim();
        String firstName = body.firstName == null ? "" : body.firstName.trim();
        String lastName = body.lastName == null ? "" : body.lastName.trim();
        String email = body.email == null ? "" : body.email.trim();
        String profileError = validateProfile(username, firstName, lastName, email);

        if (profileError != null) {
            return profileError;
        }

        if (requirePassword || (body.password != null && !body.password.isBlank())) {
            if (body.password == null || body.password.length() < 12
                    || body.password.getBytes(StandardCharsets.UTF_8).length > 72) {
                return "Password must be at least 12 characters and no more than 72 UTF-8 bytes";
            }
        }

        if (body.role == null || !ROLES.contains(body.role.trim().toUpperCase(Locale.ROOT))) {
            return "Role must be USER or ADMIN";
        }

        return null;
    }

    private static String normalizeRole(String role) {
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isJson(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null
                && contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json");
    }

    private void handleMutationError(String entity, HttpServletResponse response, Exception exception) {
        if (exception instanceof IllegalArgumentException) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
            return;
        }

        if (exception instanceof SQLException sqlException && sqlException.getSQLState() != null
                && sqlException.getSQLState().startsWith("23")) {
            sendError(response, HttpServletResponse.SC_CONFLICT, "A record with these values already exists");
            return;
        }

        getServletContext().log("Unable to modify " + entity + " in the database", exception);
        sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to modify records");
    }

    static boolean isAdmin(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        for (String role : user.getRole().split(",")) {
            if ("ADMIN".equalsIgnoreCase(role.trim())) {
                return true;
            }
        }

        return false;
    }

    private static String determineEntityFromPath(String path) {
        int start = 0;
        int end = path.length();

        // Move start index past leading slashes
        while (start < end && path.charAt(start) == '/') {
            start++;
        }

        // Move end index back past trailing slashes
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }

        return path.substring(start, end);
    }

    private interface EntityFunctionWithArg<K, T> {
        T apply(K input, Integer userId) throws NamingException, SQLException;
    }

    private interface EntityFunctionNoArg<T> {
        Map<Integer, T> apply(Integer userId) throws NamingException, SQLException;
    }

    private record EntityOperations<T>(
        String type,
        EntityFunctionWithArg<Integer, T> selectEntity,
        EntityFunctionNoArg<T> selectEntities,
        EntityFunctionWithArg<String, Map<Integer, T>> searchEntities,
        Function<T, Map<String, Object>> toJson) {
    }

    private static final EntityOperations<Task> TASK_OPERATIONS = new EntityOperations<>(
            TASKS, TodoDB::selectTask, TodoDB::selectTasks, TodoDB::searchTasks, BusinessApi::toJson);
    private static final EntityOperations<User> USER_OPERATIONS = new EntityOperations<>(
            USERS, TodoDB::selectUser, TodoDB::selectUsers, TodoDB::searchUsers, BusinessApi::toJson);
    private static final EntityOperations<Schedule> SCHEDULE_OPERATIONS = new EntityOperations<>(
            SCHEDULES, TodoDB::selectSchedule, TodoDB::selectSchedules, TodoDB::searchSchedules, BusinessApi::toJson);

    private <T> void getEntities(HttpServletRequest request, HttpServletResponse response, String search,
            EntityOperations<T> operations, Integer userId) {
        try {
            String id = request.getParameter("id");
            if (id != null) {
                T entity = operations.selectEntity().apply(Integer.parseInt(id), userId);
                if (entity == null) {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND, "Entity not found");
                    return;
                }

                sendJson(response, operations.toJson().apply(entity));
                return;
            }

            Map<Integer, Map<String, Object>> entities = new LinkedHashMap<>();

            (search == null
                    ? operations.selectEntities().apply(userId)
                    : operations.searchEntities().apply(search, userId))
                    .forEach((entityId, entity) -> entities.put(entityId, operations.toJson().apply(entity)));

            sendJson(response, entities);
        } catch (NamingException | SQLException exception) {
            handleDatabaseError(operations.type(), response, exception);
        }
    }

    private void getTasks(HttpServletRequest request, HttpServletResponse response, String search, Integer userId) {
        getEntities(request, response, search, TASK_OPERATIONS, userId);
    }

    private void getUsers(HttpServletRequest request, HttpServletResponse response, String search, Integer userId) {
        getEntities(request, response, search, USER_OPERATIONS, userId);
    }

    private void getSchedules(HttpServletRequest request, HttpServletResponse response, String search, Integer userId) {
        getEntities(request, response, search, SCHEDULE_OPERATIONS, userId);
    }

    private void handleDatabaseError(String entity, HttpServletResponse response, Exception exception) {
        getServletContext().log("Unable to retrieve " + entity + " from the database", exception);
        sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to retrieve records");
    }

    private static Map<String, Object> toJson(Task task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getTaskId());
        result.put("taskTitle", task.getTaskTitle());
        result.put("taskDescription", task.getTaskDescription());
        result.put("taskDateCreated", format(task.getTaskDateCreated()));
        result.put("taskDateDue", format(task.getTaskDateDue()));
        return result;
    }

    private static Map<String, Object> toJson(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getUserId());
        result.put("userName", user.getUserName());
        result.put("firstName", user.getFirstName());
        result.put("lastName", user.getLastName());
        result.put("email", user.getEmail());
        result.put("role", user.getRole());
        return result;
    }

    private static Map<String, Object> toJson(Schedule schedule) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notificationSettings", schedule.getNotificationSettings());
        result.put("notificationsEnabled", schedule.isNotificationsEnabled());
        result.put("notificationDays", schedule.getNotificationDays());
        result.put("notificationTime", schedule.getNotificationTime());
        result.put("notificationTimezone", schedule.getNotificationTimezone());

        Map<Integer, Map<String, Object>> tasks = new LinkedHashMap<>();
        schedule.getTasks().forEach((id, task) -> tasks.put(id, toJson(task)));

        result.put(TASKS, tasks);
        result.put("user", toJson(schedule.getUser()));

        return result;
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private void sendJson(HttpServletResponse response, Object value) {
        try {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(GSON.toJson(value));
        } catch (IOException exception) {
            getServletContext().log("Unable to write API response", exception);
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        sendJson(response, Map.of("error", message));
    }
}
