package data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.naming.NamingException;

import business.Task;

public class TodoDB {
    private TodoDB() {
        /* This utility class should not be instantiated */
    }

    public static Map<Integer, Task> selectTasks() throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        Connection connection = pool.getConnection();
        String query = "SELECT * FROM tasks";
        try (PreparedStatement ps = connection.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
            LinkedHashMap<Integer, Task> tasks = new LinkedHashMap<>();
            while (rs.next()) {
                Integer taskId = rs.getInt("task_id");
                String taskTitle = rs.getString("task_title");
                String taskDescription = rs.getString("task_description");
                LocalDateTime dateCreated = rs.getTimestamp("date_created").toLocalDateTime();
                LocalDateTime dateDue = rs.getTimestamp("date_due").toLocalDateTime();

                Task task = new Task(taskId, taskTitle, taskDescription, dateCreated, dateDue);
                tasks.put(taskId, task);
            }
            return tasks;
        } finally {
            pool.freeConnection(connection);
        }
    }
}
