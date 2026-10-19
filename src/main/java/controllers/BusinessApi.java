package controllers;

import business.Schedule;
import business.Task;
import business.User;
import data.TodoDB;

import com.google.gson.Gson;

import javax.naming.NamingException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class BusinessApi extends HttpServlet {
    private static final Gson GSON = new Gson();

    private static final String TASKS = "tasks";
    private static final String USERS = "users";
    private static final String SCHEDULES = "schedules";

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

        User currentUser = AccountApi.getSessionUser(request);
        if (currentUser == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to view records");
            return;
        }
        Integer ownerId = isAdmin(currentUser) ? null : currentUser.getUserId();

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
