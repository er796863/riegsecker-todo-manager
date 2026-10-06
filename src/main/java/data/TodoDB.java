package data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;

import business.Schedule;
import business.Task;
import business.User;

public class TodoDB {
    private TodoDB() {
        /* This utility class should not be instantiated */
    }

    public static Map<Integer, Task> selectTasks() throws NamingException, SQLException {
        return queryTasks("SELECT * FROM tasks", null);
    }

    public static Task selectTask(Integer taskId) throws NamingException, SQLException {
        Map<Integer, Task> tasks = queryTasks("SELECT * FROM tasks WHERE task_id = ?", taskId);
        return tasks.get(taskId);
    }

    public static List<Task> searchTasks(String search) throws NamingException, SQLException {
        if (search == null || search.isBlank()) {
            return new ArrayList<>(selectTasks().values());
        }
        String query = "SELECT * FROM tasks WHERE CAST(task_id AS CHAR) LIKE ? "
                + "OR task_title LIKE ? OR task_description LIKE ?";
        return new ArrayList<>(queryTasks(query, "%" + search.trim() + "%").values());
    }

    private static Map<Integer, Task> queryTasks(String query, Object parameter)
            throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        Connection connection = pool.getConnection();
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            if (parameter instanceof String search) {
                for (int index = 1; index <= 3; index++) {
                    ps.setString(index, search);
                }
            } else if (parameter != null) {
                ps.setObject(1, parameter);
            }
            try (ResultSet rs = ps.executeQuery()) {
                LinkedHashMap<Integer, Task> tasks = new LinkedHashMap<>();
                while (rs.next()) {
                    Integer taskId = rs.getInt("task_id");
                    String taskTitle = rs.getString("task_title");
                    String taskDescription = rs.getString("task_description");
                    LocalDateTime dateCreated = toLocalDateTime(rs.getTimestamp("date_created"));
                    LocalDateTime dateDue = toLocalDateTime(rs.getTimestamp("date_due"));

                    Task task = new Task(taskId, taskTitle, taskDescription, dateCreated, dateDue);
                    tasks.put(taskId, task);
                }
                return tasks;
            }
        } finally {
            pool.freeConnection(connection);
        }
    }

    public static List<User> selectUsers() throws NamingException, SQLException {
        return queryUsers("SELECT user_id, username, first_name, last_name, email, user_role FROM users",
                null).values().stream().toList();
    }

    public static User selectUser(Integer userId) throws NamingException, SQLException {
        Map<Integer, User> users = queryUsers(
                "SELECT user_id, username, first_name, last_name, email, user_role FROM users WHERE user_id = ?",
                userId);
        return users.get(userId);
    }

    public static List<User> searchUsers(String search) throws NamingException, SQLException {
        if (search == null || search.isBlank()) {
            return selectUsers();
        }
        String query = "SELECT user_id, username, first_name, last_name, email, user_role FROM users "
                + "WHERE CAST(user_id AS CHAR) LIKE ? OR username LIKE ? OR first_name LIKE ? "
                + "OR last_name LIKE ? OR email LIKE ? OR user_role LIKE ?";
        return new ArrayList<>(queryUsers(query, "%" + search.trim() + "%").values());
    }

    private static Map<Integer, User> queryUsers(String query, Object parameter)
            throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        Connection connection = pool.getConnection();
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            if (parameter instanceof String search) {
                for (int index = 1; index <= 6; index++) {
                    ps.setString(index, search);
                }
            } else if (parameter != null) {
                ps.setObject(1, parameter);
            }
            try (ResultSet rs = ps.executeQuery()) {
                LinkedHashMap<Integer, User> users = new LinkedHashMap<>();
                while (rs.next()) {
                    Integer userId = rs.getInt("user_id");
                    User user = new User(userId, rs.getString("username"), rs.getString("first_name"),
                            rs.getString("last_name"), rs.getString("email"), null, rs.getString("user_role"));
                    users.put(userId, user);
                }
                return users;
            }
        } finally {
            pool.freeConnection(connection);
        }
    }

    public static List<Schedule> selectSchedules() throws NamingException, SQLException {
        return querySchedules("SELECT task_id, user_id, notification_setting FROM schedules", null);
    }

    public static Schedule selectSchedule(Integer taskId, Integer userId)
            throws NamingException, SQLException {
        List<Schedule> schedules = querySchedules(
                "SELECT task_id, user_id, notification_setting FROM schedules WHERE task_id = ? AND user_id = ?",
                new Integer[] {taskId, userId});
        return schedules.isEmpty() ? null : schedules.get(0);
    }

    public static List<Schedule> searchSchedules(String search) throws NamingException, SQLException {
        if (search == null || search.isBlank()) {
            return selectSchedules();
        }
        String query = "SELECT task_id, user_id, notification_setting FROM schedules "
                + "WHERE CAST(task_id AS CHAR) LIKE ? OR CAST(user_id AS CHAR) LIKE ? "
                + "OR notification_setting LIKE ?";
        return querySchedules(query, "%" + search.trim() + "%");
    }

    private static List<Schedule> querySchedules(String query, Object parameter)
            throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        Connection connection = pool.getConnection();
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            if (parameter instanceof String search) {
                for (int index = 1; index <= 3; index++) {
                    ps.setString(index, search);
                }
            } else if (parameter instanceof Integer[] ids) {
                ps.setInt(1, ids[0]);
                ps.setInt(2, ids[1]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Schedule> schedules = new ArrayList<>();
                while (rs.next()) {
                    schedules.add(new Schedule(rs.getInt("task_id"), rs.getInt("user_id"),
                            rs.getString("notification_setting")));
                }
                return schedules;
            }
        } finally {
            pool.freeConnection(connection);
        }
    }

    private static LocalDateTime toLocalDateTime(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
