package controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

import business.Schedule;
import business.Task;
import business.User;
import data.TodoDB;

public class BusinessApi extends HttpServlet {
    private static final Gson GSON = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        String entity = path == null ? "" : path.replaceAll("^/+|/+$", "");
        String search = request.getParameter("search");

        try {
            switch (entity) {
                case "tasks" -> getTasks(request, response, search);
                case "users" -> getUsers(request, response, search);
                case "schedules" -> getSchedules(request, response, search);
                default -> sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown resource");
            }
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "IDs must be integers");
        } catch (NamingException | SQLException exception) {
            getServletContext().log("Unable to retrieve " + entity + " from the database", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to retrieve records");
        }
    }

    private static void getTasks(HttpServletRequest request, HttpServletResponse response, String search)
            throws IOException, NamingException, SQLException {
        String id = request.getParameter("id");
        if (id != null) {
            Task task = TodoDB.selectTask(Integer.valueOf(id));
            if (task == null) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Task not found");
                return;
            }
            sendJson(response, toJson(task));
            return;
        }

        List<Task> tasks = search == null ? new ArrayList<>(TodoDB.selectTasks().values())
                : TodoDB.searchTasks(search);
        sendJson(response, tasks.stream().map(BusinessApi::toJson).toList());
    }

    private static void getUsers(HttpServletRequest request, HttpServletResponse response, String search)
            throws IOException, NamingException, SQLException {
        String id = request.getParameter("id");
        if (id != null) {
            User user = TodoDB.selectUser(Integer.valueOf(id));
            if (user == null) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "User not found");
                return;
            }
            sendJson(response, toJson(user));
            return;
        }

        List<User> users = search == null ? TodoDB.selectUsers() : TodoDB.searchUsers(search);
        sendJson(response, users.stream().map(BusinessApi::toJson).toList());
    }

    private static void getSchedules(HttpServletRequest request, HttpServletResponse response, String search)
            throws IOException, NamingException, SQLException {
        String taskId = request.getParameter("taskId");
        String userId = request.getParameter("userId");
        if (taskId != null || userId != null) {
            if (taskId == null || userId == null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Both taskId and userId are required");
                return;
            }
            Schedule schedule = TodoDB.selectSchedule(Integer.valueOf(taskId), Integer.valueOf(userId));
            if (schedule == null) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Schedule not found");
                return;
            }
            sendJson(response, toJson(schedule));
            return;
        }

        List<Schedule> schedules = search == null ? TodoDB.selectSchedules() : TodoDB.searchSchedules(search);
        sendJson(response, schedules.stream().map(BusinessApi::toJson).toList());
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
        result.put("taskId", schedule.getTaskId());
        result.put("userId", schedule.getUserId());
        result.put("notificationSettings", schedule.getNotificationSettings());
        return result;
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private static void sendJson(HttpServletResponse response, Object value) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(GSON.toJson(value));
    }

    private static void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        sendJson(response, Map.of("error", message));
    }
}
