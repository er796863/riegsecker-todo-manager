package data;

import business.Schedule;
import business.Task;
import business.User;
import business.AccountCredentials;
import business.InAppNotification;
import business.NotificationSchedule;
import business.NotificationTask;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Time;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import javax.naming.NamingException;

public class TodoDB {
    // language=MySQL
    private static final String BASE_TASKS_QUERY = "SELECT task_id, task_title, task_description, "
            + "date_created, date_due FROM tasks";

    // language=MySQL
    private static final String SELECT_TASK_QUERY = BASE_TASKS_QUERY
            + " WHERE (? IS NULL OR user_id = ?) AND task_id = ?";

    // language=MySQL
    private static final String SELECT_TASKS_QUERY = BASE_TASKS_QUERY
            + " WHERE (? IS NULL OR user_id = ?) ORDER BY task_id";

    // language=MySQL
    private static final String SEARCH_TASKS_QUERY = BASE_TASKS_QUERY
            + " WHERE (? IS NULL OR user_id = ?) "
            + "AND CONCAT_WS(' ', CAST(task_id AS CHAR), task_title, task_description) "
            + "LIKE ? ORDER BY task_id";

    private static final String USER_ROLE_QUERY = "COALESCE((SELECT GROUP_CONCAT(ur.role_name "
            + "ORDER BY ur.role_name SEPARATOR ', ') "
            + "FROM user_roles ur WHERE ur.username = users.username), 'USER') AS user_role";
    private static final String BASE_USERS_QUERY = "SELECT user_id, username, first_name, last_name, email, "
            + USER_ROLE_QUERY + " FROM users";
    // language=MySQL
    private static final String SELECT_USER_QUERY = BASE_USERS_QUERY
            + " WHERE (? IS NULL OR user_id = ?) AND user_id = ?";

    // language=MySQL
    private static final String SELECT_USERS_QUERY = BASE_USERS_QUERY
            + " WHERE (? IS NULL OR user_id = ?) ORDER BY user_id";

    // language=MySQL
    private static final String SEARCH_USERS_QUERY = BASE_USERS_QUERY
            + " WHERE (? IS NULL OR user_id = ?) AND CONCAT_WS(' ', username, first_name, last_name, email, "
            + "COALESCE((SELECT GROUP_CONCAT(ur.role_name ORDER BY ur.role_name SEPARATOR ', ') "
            + "FROM user_roles ur WHERE ur.username = users.username), 'USER')) "
            + "LIKE ? ORDER BY user_id";

    // language=MySQL
    private static final String SCHEDULES_BASE_QUERY = "SELECT s.notification_setting AS notificationSettings, "
            + "s.notification_enabled AS notificationsEnabled, s.notification_days AS notificationDaysMask, "
            + "TIME_FORMAT(s.notification_time, '%H:%i') AS notificationTime, "
            + "s.notification_timezone AS notificationTimezone, "
            + "u.user_id, u.username, u.first_name, u.last_name, u.email, "
            + "COALESCE((SELECT GROUP_CONCAT(ur.role_name ORDER BY ur.role_name SEPARATOR ', ') "
            + "FROM user_roles ur WHERE ur.username = u.username), 'USER') AS user_role, "
            + "t.task_id, t.task_title, t.task_description, t.date_created, t.date_due "
            + "FROM schedules s JOIN users u ON s.user_id = u.user_id "
            + "LEFT JOIN schedule_tasks st ON s.schedule_id = st.schedule_id "
            + "LEFT JOIN tasks t ON st.task_id = t.task_id AND t.user_id = u.user_id";

    // language=MySQL
    private static final String SELECT_SCHEDULES_QUERY = SCHEDULES_BASE_QUERY
            + " WHERE (? IS NULL OR u.user_id = ?) ORDER BY u.user_id, t.task_id";

    // language=MySQL
    private static final String SELECT_SCHEDULE_QUERY = SCHEDULES_BASE_QUERY
            + " WHERE (? IS NULL OR s.user_id = ?) AND s.user_id = ? ORDER BY t.task_id";

    // language=MySQL
    private static final String SEARCH_SCHEDULES_QUERY = SCHEDULES_BASE_QUERY
            + " WHERE (? IS NULL OR s.user_id = ?) AND CONCAT_WS(' ', CAST(s.user_id AS CHAR), s.notification_setting, "
            + "u.username, u.first_name, u.last_name, "
            + "t.task_title, t.task_description) LIKE ? ORDER BY u.user_id, t.task_id";

    private static final String ACCOUNT_QUERY = "SELECT user_id, username, first_name, last_name, email, "
            + USER_ROLE_QUERY + ", password_hash FROM users WHERE username = ?";

    /**
     * Prevents instantiation of this database utility class.
     */
    private TodoDB() {
        /* This utility class should not be instantiated */
    }

    /**
     * Maps a result-set row to an entity.
     */
    private interface RowMapper<T> {
        /**
         * Builds an entity from the current result-set row.
         */
        T mapRow(ResultSet rs) throws SQLException;
    }

    /**
     * Builds an insertion-ordered map of entities from a query.
     */
    private static <T> Map<Integer, T> buildEntities(PreparedStatement ps, RowMapper<T> mapper, ToIntFunction<T> getId)
            throws SQLException {
        return buildEntities(ps, mapper, getId, (existing, incoming) -> {
        });
    }

    /**
     * Builds an insertion-ordered map and merges rows with duplicate entity IDs.
     */
    private static <T> Map<Integer, T> buildEntities(PreparedStatement ps, RowMapper<T> mapper,
            ToIntFunction<T> getId, BiConsumer<T, T> merge) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            LinkedHashMap<Integer, T> entities = new LinkedHashMap<>();

            while (rs.next()) {
                T entity = mapper.mapRow(rs);
                Integer id = getId.applyAsInt(entity);
                T existing = entities.putIfAbsent(id, entity);

                if (existing != null) {
                    merge.accept(existing, entity);
                }
            }

            return entities;
        }
    }

    /**
     * Selects one entity by ID.
     */
    private static <T> T selectEntity(Integer id, String query, RowMapper<T> mapper, ToIntFunction<T> getId)
            throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setObject(1, id);
            return buildEntities(ps, mapper, getId).get(id);
        }
    }

    /**
     * Selects one entity by ID and merges rows that share its ID.
     */
    private static <T> T selectEntity(Integer id, String query, RowMapper<T> mapper, ToIntFunction<T> getId,
            BiConsumer<T, T> merge) throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setObject(1, id);
            return buildEntities(ps, mapper, getId, merge).get(id);
        }
    }

    /**
     * Selects all entities returned by a query.
     */
    private static <T> Map<Integer, T> selectEntities(String query, RowMapper<T> mapper, ToIntFunction<T> getId)
            throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            return buildEntities(ps, mapper, getId);
        }
    }

    /**
     * Selects entities and merges rows that share an entity ID.
     */
    private static <T> Map<Integer, T> selectEntities(String query, RowMapper<T> mapper, ToIntFunction<T> getId,
            BiConsumer<T, T> merge) throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            return buildEntities(ps, mapper, getId, merge);
        }
    }

    private static <T> T selectEntity(Integer id, Integer ownerId, String query, RowMapper<T> mapper,
            ToIntFunction<T> getId, BiConsumer<T, T> merge) throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();

        try (Connection connection = pool.getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            setNullableInteger(ps, 1, ownerId);
            setNullableInteger(ps, 2, ownerId);

            ps.setObject(3, id);
            return buildEntities(ps, mapper, getId, merge).get(id);
        }
    }

    private static <T> T selectEntity(Integer id, Integer ownerId, String query, RowMapper<T> mapper,
            ToIntFunction<T> getId) throws NamingException, SQLException {
        return selectEntity(id, ownerId, query, mapper, getId, (existing, incoming) -> {
        });
    }

    private static <T> Map<Integer, T> selectEntities(Integer ownerId, String query, RowMapper<T> mapper,
            ToIntFunction<T> getId) throws NamingException, SQLException {
        return selectEntities(ownerId, query, mapper, getId, (existing, incoming) -> {
        });
    }

    private static <T> Map<Integer, T> selectEntities(Integer ownerId, String query, RowMapper<T> mapper,
            ToIntFunction<T> getId, BiConsumer<T, T> merge) throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();

        try (Connection connection = pool.getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            setNullableInteger(ps, 1, ownerId);
            setNullableInteger(ps, 2, ownerId);

            return buildEntities(ps, mapper, getId, merge);
        }
    }

    private static <T> Map<Integer, T> searchEntities(String search, Integer ownerId, String query,
            String fallbackQuery, RowMapper<T> mapper, ToIntFunction<T> getId, BiConsumer<T, T> merge)
            throws NamingException, SQLException {
        if (search == null || search.isBlank()) {
            return selectEntities(ownerId, fallbackQuery, mapper, getId, merge);
        }

        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            setNullableInteger(ps, 1, ownerId);
            setNullableInteger(ps, 2, ownerId);
            ps.setString(3, "%" + search.trim() + "%");

            return buildEntities(ps, mapper, getId, merge);
        }
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    /**
     * Searches for entities, falling back to the full query for a blank search.
     */
    private static <T> Map<Integer, T> searchEntities(String search, String query, String fallbackQuery,
            RowMapper<T> mapper, ToIntFunction<T> getId) throws NamingException, SQLException {
        if (search == null || search.isBlank()) {
            return selectEntities(fallbackQuery, mapper, getId);
        }

        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, "%" + search.trim() + "%");
            return buildEntities(ps, mapper, getId);
        }
    }

    /**
     * Searches for entities and merges rows that share an entity ID.
     */
    private static <T> Map<Integer, T> searchEntities(String search, String query, String fallbackQuery,
            RowMapper<T> mapper, ToIntFunction<T> getId, BiConsumer<T, T> merge)
            throws NamingException, SQLException {
        if (search == null || search.isBlank()) {
            return selectEntities(fallbackQuery, mapper, getId, merge);
        }

        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, "%" + search.trim() + "%");
            return buildEntities(ps, mapper, getId, merge);
        }
    }

    /**
     * Builds a task from the current result-set row.
     */
    private static Task buildTask(ResultSet rs) throws SQLException {
        Integer taskId = rs.getInt("task_id");
        String taskTitle = rs.getString("task_title");
        String taskDescription = rs.getString("task_description");
        LocalDateTime taskDateCreated = toLocalDateTime(rs.getTimestamp("date_created"));
        LocalDateTime taskDateDue = toLocalDateTime(rs.getTimestamp("date_due"));

        return new Task(taskId, taskTitle, taskDescription, taskDateCreated, taskDateDue);
    }

    /**
     * Selects a task by ID.
     */
    public static Task selectTask(Integer id, Integer ownerId) throws NamingException, SQLException {
        return selectEntity(id, ownerId, SELECT_TASK_QUERY, TodoDB::buildTask, Task::getTaskId);
    }

    /**
     * Selects all tasks, keyed by task ID.
     */
    public static Map<Integer, Task> selectTasks(Integer ownerId) throws NamingException, SQLException {
        return selectEntities(ownerId, SELECT_TASKS_QUERY, TodoDB::buildTask, Task::getTaskId);
    }

    /**
     * Searches for tasks, keyed by task ID.
     */
    public static Map<Integer, Task> searchTasks(String search, Integer ownerId) throws NamingException, SQLException {
        return searchEntities(search, ownerId, SEARCH_TASKS_QUERY, SELECT_TASKS_QUERY,
                TodoDB::buildTask, Task::getTaskId, (existing, incoming) -> {
                });
    }

    public static int createTask(Integer userId, Task task) throws NamingException, SQLException {
        String query = "INSERT INTO tasks (user_id, task_title, task_description, date_due) VALUES (?, ?, ?, ?)";
        ConnectionPool pool = ConnectionPool.getInstance();

        try (Connection connection = pool.getConnection();
                PreparedStatement ps = connection.prepareStatement(query, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, task.getTaskTitle());
            ps.setString(3, task.getTaskDescription());
            setTimestamp(ps, 4, task.getTaskDateDue());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return a new task ID.");
                }

                return keys.getInt(1);
            }
        }
    }

    public static boolean updateTask(Integer id, Integer ownerId, Task task)
            throws NamingException, SQLException {
        String query = "UPDATE tasks SET task_title = ?, task_description = ?, date_due = ? "
                + "WHERE task_id = ? AND (? IS NULL OR user_id = ?)";
        ConnectionPool pool = ConnectionPool.getInstance();

        try (Connection connection = pool.getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, task.getTaskTitle());
            ps.setString(2, task.getTaskDescription());
            setTimestamp(ps, 3, task.getTaskDateDue());
            ps.setInt(4, id);
            setNullableInteger(ps, 5, ownerId);
            setNullableInteger(ps, 6, ownerId);

            if (ps.executeUpdate() == 1) {
                return true;
            }
        }

        try (Connection connection = pool.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT 1 FROM tasks WHERE task_id = ? AND (? IS NULL OR user_id = ?)")) {
            ps.setInt(1, id);
            setNullableInteger(ps, 2, ownerId);
            setNullableInteger(ps, 3, ownerId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean deleteTask(Integer id, Integer ownerId) throws NamingException, SQLException {
        String query = "DELETE FROM tasks WHERE task_id = ? AND (? IS NULL OR user_id = ?)";
        ConnectionPool pool = ConnectionPool.getInstance();

        try (Connection connection = pool.getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, id);
            setNullableInteger(ps, 2, ownerId);
            setNullableInteger(ps, 3, ownerId);

            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Builds a user from the current result-set row.
     */
    private static User buildUser(ResultSet rs) throws SQLException {
        Integer userId = rs.getInt("user_id");
        String username = rs.getString("username");
        String firstName = rs.getString("first_name");
        String lastName = rs.getString("last_name");
        String email = rs.getString("email");
        String userRole = rs.getString("user_role");

        return new User(userId, username, firstName, lastName, email, null, userRole);
    }

    /**
     * Selects a user by ID.
     */
    public static User selectUser(Integer id, Integer ownerId) throws NamingException, SQLException {
        return selectEntity(id, ownerId, SELECT_USER_QUERY, TodoDB::buildUser, User::getUserId);
    }

    /**
     * Selects all users, keyed by user ID.
     */
    public static Map<Integer, User> selectUsers(Integer ownerId) throws NamingException, SQLException {
        return selectEntities(ownerId, SELECT_USERS_QUERY, TodoDB::buildUser, User::getUserId);
    }

    /**
     * Searches for users, keyed by user ID.
     */
    public static Map<Integer, User> searchUsers(String search, Integer ownerId) throws NamingException, SQLException {
        return searchEntities(search, ownerId, SEARCH_USERS_QUERY, SELECT_USERS_QUERY,
                TodoDB::buildUser, User::getUserId, (existing, incoming) -> {
                });
    }

    public static User createManagedUser(String username, String firstName, String lastName,
            String email, String passwordHash, String role) throws NamingException, SQLException {
        String insertUser = "INSERT INTO users (username, first_name, last_name, email, password_hash) "
                + "VALUES (?, ?, ?, ?, ?)";
        ConnectionPool pool = ConnectionPool.getInstance();

        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Integer userId;
                try (PreparedStatement ps = connection.prepareStatement(insertUser,
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, username);
                    ps.setString(2, firstName);
                    ps.setString(3, lastName);
                    ps.setString(4, email);
                    ps.setString(5, passwordHash);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Database did not return a new account ID.");
                        }

                        userId = keys.getInt(1);
                    }
                }

                try (
                    PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO user_roles (username, role_name) VALUES (?, ?)"
                    );
                    PreparedStatement schedule = connection.prepareStatement(
                        "INSERT INTO schedules (user_id, notification_setting, "
                        + "notification_checked_at_utc) VALUES (?, 'NONE', UTC_TIMESTAMP())"
                    )
                ) {
                    ps.setString(1, username);
                    ps.setString(2, role);
                    ps.executeUpdate();

                    schedule.setInt(1, userId);
                    schedule.executeUpdate();
                }

                connection.commit();
                return new User(userId, username, firstName, lastName, email, null, role);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public static boolean updateManagedUser(Integer id, String username, String firstName, String lastName,
            String email, String passwordHash, String role) throws NamingException, SQLException {
        String update = "UPDATE users SET username = ?, first_name = ?, last_name = ?, email = ?, "
                + "password_hash = COALESCE(?, password_hash) WHERE user_id = ?";
        ConnectionPool pool = ConnectionPool.getInstance();

        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UserAdminStatus status = lockUserAdminStatus(connection, id);

                if (status == null) {
                    connection.rollback();
                    return false;
                }

                if (status.isAdmin() && !"ADMIN".equals(role) && status.adminCount() <= 1) {
                    throw new IllegalArgumentException("The last administrator cannot be demoted");
                }

                try (PreparedStatement ps = connection.prepareStatement(update)) {
                    ps.setString(1, username);
                    ps.setString(2, firstName);
                    ps.setString(3, lastName);
                    ps.setString(4, email);
                    ps.setString(5, passwordHash);
                    ps.setInt(6, id);
                    ps.executeUpdate();
                }

                try (
                    PreparedStatement deleteRole = connection.prepareStatement(
                        "DELETE FROM user_roles WHERE username = ?"
                    );
                    PreparedStatement insertRole = connection.prepareStatement(
                        "INSERT INTO user_roles (username, role_name) VALUES (?, ?)"
                    )
                ) {
                    deleteRole.setString(1, username);
                    deleteRole.executeUpdate();
                    insertRole.setString(1, username);
                    insertRole.setString(2, role);
                    insertRole.executeUpdate();
                }

                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public static boolean deleteManagedUser(Integer id, Integer actorId) throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UserAdminStatus status = lockUserAdminStatus(connection, id);

                if (status == null || id.equals(actorId)) {
                    connection.rollback();
                    return false;
                }

                if (status.isAdmin() && status.adminCount() <= 1) {
                    throw new IllegalArgumentException("The last administrator cannot be deleted");
                }

                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
                    ps.setInt(1, id);
                    boolean deleted = ps.executeUpdate() == 1;

                    connection.commit();
                    return deleted;
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private record UserAdminStatus(boolean isAdmin, int adminCount) {
    }

    private static UserAdminStatus lockUserAdminStatus(Connection connection, Integer id) throws SQLException {
        String username;
        try (
            PreparedStatement ps = connection.prepareStatement(
                "SELECT username FROM users WHERE user_id = ? FOR UPDATE"
            )
        ) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                username = rs.getString("username");
            }
        }

        int adminCount = 0;
        boolean isAdmin = false;

        try (
            PreparedStatement ps = connection.prepareStatement(
                "SELECT username FROM user_roles WHERE role_name = 'ADMIN' FOR UPDATE"
            );
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                adminCount++;
                if (username.equals(rs.getString("username"))) {
                    isAdmin = true;
                }
            }
        }

        return new UserAdminStatus(isAdmin, adminCount);
    }

    public static boolean createSchedule(Integer userId, String notificationSettings)
            throws NamingException, SQLException {
        return createSchedule(userId, notificationSettings, legacyNotificationsEnabled(notificationSettings),
                legacyNotificationDays(notificationSettings), "09:00", "UTC", null);
    }

    public static boolean createSchedule(Integer userId, String notificationSettings, List<Integer> taskIds)
            throws NamingException, SQLException {
        return createSchedule(userId, notificationSettings, legacyNotificationsEnabled(notificationSettings),
                legacyNotificationDays(notificationSettings), "09:00", "UTC", taskIds);
    }

    public static boolean createSchedule(Integer userId, String notificationSettings, boolean enabled,
            List<String> days, String time, String timezone, List<Integer> taskIds)
            throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (
                    PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO schedules (user_id, notification_setting, notification_enabled, "
                        + "notification_days, notification_time, notification_timezone, "
                        + "notification_checked_at_utc) "
                        + "VALUES (?, ?, ?, ?, ?, ?, UTC_TIMESTAMP())"
                    )
                ) {
                    ps.setInt(1, userId);
                    ps.setString(2, notificationSettings);
                    ps.setBoolean(3, enabled);
                    ps.setInt(4, notificationDaysMask(days));
                    ps.setTime(5, Time.valueOf(LocalTime.parse(time)));
                    ps.setString(6, timezone);
                    ps.executeUpdate();
                }

                if (taskIds != null) {
                    insertScheduleTasks(connection, userId, taskIds);
                }

                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public static boolean updateSchedule(Integer userId, Integer ownerId, String notificationSettings,
            List<Integer> taskIds) throws NamingException, SQLException {
        return updateSchedule(userId, ownerId, notificationSettings, legacyNotificationsEnabled(notificationSettings),
                legacyNotificationDays(notificationSettings), "09:00", "UTC", taskIds);
    }

    public static boolean updateSchedule(Integer userId, Integer ownerId, String notificationSettings,
            boolean enabled, List<String> days, String time, String timezone, List<Integer> taskIds)
            throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String update = "UPDATE schedules SET notification_setting = ?, notification_enabled = ?, "
                        + "notification_days = ?, notification_time = ?, notification_timezone = ?, "
                        + "notification_checked_at_utc = UTC_TIMESTAMP() "
                        + "WHERE user_id = ? AND (? IS NULL OR user_id = ?)";

                try (PreparedStatement ps = connection.prepareStatement(update)) {
                    ps.setString(1, notificationSettings);
                    ps.setBoolean(2, enabled);
                    ps.setInt(3, notificationDaysMask(days));
                    ps.setTime(4, Time.valueOf(LocalTime.parse(time)));
                    ps.setString(5, timezone);
                    ps.setInt(6, userId);
                    setNullableInteger(ps, 7, ownerId);
                    setNullableInteger(ps, 8, ownerId);

                    if (ps.executeUpdate() != 1) {
                        boolean exists;
                        try (
                            PreparedStatement check = connection.prepareStatement(
                                "SELECT 1 FROM schedules WHERE user_id = ? AND (? IS NULL OR user_id = ?)"
                            )
                        ) {
                            check.setInt(1, userId);

                            setNullableInteger(check, 2, ownerId);
                            setNullableInteger(check, 3, ownerId);

                            try (ResultSet rs = check.executeQuery()) {
                                exists = rs.next();
                            }
                        }

                        if (!exists) {
                            connection.rollback();
                            return false;
                        }
                    }
                }

                if (taskIds != null) {
                    try (
                        PreparedStatement ps = connection.prepareStatement(
                            "DELETE st FROM schedule_tasks st JOIN schedules s ON s.schedule_id = st.schedule_id "
                            + "WHERE s.user_id = ?"
                        )
                    ) {
                        ps.setInt(1, userId);
                        ps.executeUpdate();
                    }

                    insertScheduleTasks(connection, userId, taskIds);
                }

                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void insertScheduleTasks(Connection connection, Integer userId, List<Integer> taskIds)
            throws SQLException {
        try (
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO schedule_tasks (schedule_id, task_id) "
                + "SELECT s.schedule_id, t.task_id FROM schedules s "
                + "JOIN tasks t ON t.user_id = s.user_id AND t.task_id = ? "
                + "WHERE s.user_id = ?"
            )
        ) {
            for (Integer taskId : taskIds) {
                ps.setInt(1, taskId);
                ps.setInt(2, userId);
                if (ps.executeUpdate() != 1) {
                    throw new IllegalArgumentException(
                        "Every scheduled task must belong to the schedule's user"
                    );
                }
            }
        }
    }

    private static int notificationDaysMask(List<String> days) {
        int mask = 0;
        for (String day : days) {
            mask |= 1 << (DayOfWeek.valueOf(day).getValue() - 1);
        }

        return mask;
    }

    private static boolean legacyNotificationsEnabled(String settings) {
        return settings != null && !"NONE".equalsIgnoreCase(settings);
    }

    private static List<String> legacyNotificationDays(String settings) {
        if ("WEEKLY".equalsIgnoreCase(settings)) {
            return List.of("MONDAY");
        }

        return List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
    }

    public static boolean deleteSchedule(Integer userId, Integer ownerId)
            throws NamingException, SQLException {
        String query = "DELETE FROM schedules WHERE user_id = ? AND (? IS NULL OR user_id = ?)";
        ConnectionPool pool = ConnectionPool.getInstance();

        try (
            Connection connection = pool.getConnection();
            PreparedStatement ps = connection.prepareStatement(query)
        ) {
            ps.setInt(1, userId);
            setNullableInteger(ps, 2, ownerId);
            setNullableInteger(ps, 3, ownerId);

            return ps.executeUpdate() == 1;
        }
    }

    private static void setTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    /**
     * Builds a schedule and its task map from the current result-set row.
     */
    private static Schedule buildSchedule(ResultSet rs) throws SQLException {
        User user = buildUser(rs);
        Map<Integer, Task> tasks = new LinkedHashMap<>();

        Integer taskId = rs.getObject("task_id", Integer.class);
        if (taskId != null) {
            Task task = buildTask(rs);
            tasks.put(taskId, task);
        }

        String notificationSettings = rs.getString("notificationSettings");
        boolean notificationsEnabled = rs.getBoolean("notificationsEnabled");
        int dayMask = rs.getInt("notificationDaysMask");

        List<String> notificationDays = new java.util.ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            if ((dayMask & 1 << (day.getValue() - 1)) != 0) {
                notificationDays.add(day.name());
            }
        }

        String notificationTime = rs.getString("notificationTime");
        String notificationTimezone = rs.getString("notificationTimezone");

        return new Schedule(notificationSettings, notificationsEnabled, notificationDays,
                notificationTime, notificationTimezone, tasks, user);
    }

    /**
     * Adds the incoming schedule's tasks to the existing schedule.
     */
    private static void mergeScheduleTasks(Schedule existing, Schedule incoming) {
        existing.getTasks().putAll(incoming.getTasks());
    }

    /**
     * Returns the user ID used to key a schedule.
     */
    private static int scheduleUserId(Schedule schedule) {
        return schedule.getUser().getUserId();
    }

    /**
     * Selects all schedules, keyed by user ID.
     */
    public static Map<Integer, Schedule> selectSchedules(Integer ownerId) throws NamingException, SQLException {
        return selectEntities(ownerId, SELECT_SCHEDULES_QUERY, TodoDB::buildSchedule, TodoDB::scheduleUserId,
                TodoDB::mergeScheduleTasks);
    }

    /**
     * Selects the schedule belonging to a user.
     */
    public static Schedule selectSchedule(Integer id, Integer ownerId) throws NamingException, SQLException {
        return selectEntity(id, ownerId, SELECT_SCHEDULE_QUERY, TodoDB::buildSchedule,
                TodoDB::scheduleUserId, TodoDB::mergeScheduleTasks);
    }

    /**
     * Searches for schedules, keyed by user ID.
     */
    public static Map<Integer, Schedule> searchSchedules(String search, Integer ownerId)
            throws NamingException, SQLException {
        return searchEntities(search, ownerId, SEARCH_SCHEDULES_QUERY, SELECT_SCHEDULES_QUERY,
                TodoDB::buildSchedule, TodoDB::scheduleUserId, TodoDB::mergeScheduleTasks);
    }

    public static AccountCredentials findAccount(String username) throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (
            Connection connection = pool.getConnection();
            PreparedStatement ps = connection.prepareStatement(ACCOUNT_QUERY)
        ) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new AccountCredentials(buildUser(rs), rs.getString("password_hash"));
            }
        }
    }

    public static boolean isAdministrator(Integer userId) throws NamingException, SQLException {
        String query = "SELECT EXISTS (SELECT 1 FROM users u "
                + "JOIN user_roles ur ON ur.username = u.username "
                + "WHERE u.user_id = ? AND ur.role_name = 'ADMIN')";

        ConnectionPool pool = ConnectionPool.getInstance();
        try (
            Connection connection = pool.getConnection();
            PreparedStatement ps = connection.prepareStatement(query)
        ) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    public static void updatePasswordHash(String username, String passwordHash)
            throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (
            Connection connection = pool.getConnection();
            PreparedStatement ps = connection.prepareStatement(
                "UPDATE users SET password_hash = ? WHERE username = ?"
            )
        ) {
            ps.setString(1, passwordHash);
            ps.setString(2, username);
            ps.executeUpdate();
        }
    }

    public static User createAccount(String username, String firstName, String lastName,
            String email, String passwordHash) throws NamingException, SQLException {
        return createManagedUser(username, firstName, lastName, email, passwordHash, "USER");
    }

    public static NotificationSchedule selectNotificationSchedule(Integer userId)
            throws NamingException, SQLException {
        String query = "SELECT s.schedule_id, s.notification_enabled, s.notification_days, "
                + "TIME_FORMAT(s.notification_time, '%H:%i') AS notification_time, "
                + "s.notification_timezone, "
                + "DATE_FORMAT(s.notification_checked_at_utc, '%Y-%m-%dT%H:%i:%s') AS checked_at_utc, "
                + "COUNT(t.task_id) OVER() AS task_count, "
                + "t.task_id, t.task_title, DATE_FORMAT(t.date_due, '%Y-%m-%dT%H:%i:%s') AS task_date_due "
                + "FROM schedules s "
                + "LEFT JOIN schedule_tasks st ON st.schedule_id = s.schedule_id "
                + "LEFT JOIN tasks t ON t.task_id = st.task_id AND t.user_id = s.user_id "
                + "WHERE s.user_id = ? "
                + "ORDER BY (t.date_due IS NULL), t.date_due, t.task_id LIMIT 10";

        ConnectionPool pool = ConnectionPool.getInstance();
        try (
            Connection connection = pool.getConnection();
            PreparedStatement ps = connection.prepareStatement(query)
        ) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                Integer scheduleId = rs.getInt("schedule_id");
                boolean enabled = rs.getBoolean("notification_enabled");
                int daysMask = rs.getInt("notification_days");
                LocalTime time = LocalTime.parse(rs.getString("notification_time"));
                String timezone = rs.getString("notification_timezone");
                LocalDateTime checkedAtUtc = LocalDateTime.parse(rs.getString("checked_at_utc"));
                int taskCount = rs.getInt("task_count");

                List<NotificationTask> tasks = new ArrayList<>();
                do {
                    Integer taskId = rs.getObject("task_id", Integer.class);
                    if (taskId != null) {
                        tasks.add(
                            new NotificationTask(
                                taskId,
                                rs.getString("task_title"),
                                rs.getString("task_date_due")
                            )
                        );
                    }
                } while (rs.next());

                return new NotificationSchedule(
                        scheduleId, enabled, daysMask, time, timezone, checkedAtUtc, taskCount, tasks);
            }
        }
    }

    public static boolean createScheduledNotification(Integer userId, Integer scheduleId,
            LocalDate scheduledDate, LocalTime scheduledTime, String timezone,
            List<NotificationTask> tasks, int additionalTaskCount)
            throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int notificationId;
                try (
                    PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO notifications (user_id, schedule_id, scheduled_date, scheduled_time, "
                        + "notification_timezone, additional_task_count) VALUES (?, ?, ?, ?, ?, ?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS
                    )
                ) {
                    ps.setInt(1, userId);
                    ps.setInt(2, scheduleId);
                    ps.setDate(3, java.sql.Date.valueOf(scheduledDate));
                    ps.setTime(4, Time.valueOf(scheduledTime));
                    ps.setString(5, timezone);
                    ps.setInt(6, additionalTaskCount);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Notification insert did not return an ID");
                        }

                        notificationId = keys.getInt(1);
                    }
                }

                try (
                    PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO notification_tasks "
                        + "(notification_id, task_id, task_title, task_date_due) VALUES (?, ?, ?, ?)"
                    )
                ) {
                    for (NotificationTask task : tasks) {
                        ps.setInt(1, notificationId);
                        ps.setInt(2, task.taskId());
                        ps.setString(3, task.taskTitle());

                        if (task.taskDateDue() == null) {
                            ps.setNull(4, Types.TIMESTAMP);
                        } else {
                            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.parse(task.taskDateDue())));
                        }

                        ps.addBatch();
                    }

                    ps.executeBatch();
                }
                try (
                    PreparedStatement ps = connection.prepareStatement(
                        "UPDATE schedules SET notification_checked_at_utc = UTC_TIMESTAMP() "
                        + "WHERE schedule_id = ? AND user_id = ?"
                    )
                ) {
                    ps.setInt(1, scheduleId);
                    ps.setInt(2, userId);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Unable to advance notification schedule");
                    }
                }

                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                if (exception.getErrorCode() == 1062 && exception.getMessage() != null
                        && exception.getMessage().contains("uq_notifications_schedule_date")) {
                    return false;
                }

                throw exception;
            } catch (RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public static void advanceNotificationCheck(Integer userId) throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (
            Connection connection = pool.getConnection();
            PreparedStatement ps = connection.prepareStatement(
                "UPDATE schedules SET notification_checked_at_utc = UTC_TIMESTAMP() WHERE user_id = ?"
            )
        ) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    public static List<InAppNotification> selectNotifications(Integer userId)
            throws NamingException, SQLException {
        String query = "SELECT n.notification_id, DATE_FORMAT(n.scheduled_date, '%Y-%m-%d') AS scheduled_date, "
                + "TIME_FORMAT(n.scheduled_time, '%H:%i') AS scheduled_time, n.notification_timezone, "
                + "n.is_read, n.additional_task_count, nt.task_id, nt.task_title, "
                + "DATE_FORMAT(nt.task_date_due, '%Y-%m-%dT%H:%i:%s') AS task_date_due "
                + "FROM notifications n LEFT JOIN notification_tasks nt ON nt.notification_id = n.notification_id "
                + "WHERE n.user_id = ? ORDER BY n.scheduled_date DESC, n.notification_id DESC, "
                + "(nt.task_date_due IS NULL), nt.task_date_due, nt.task_id";

        ConnectionPool pool = ConnectionPool.getInstance();
        try (
            Connection connection = pool.getConnection();
            PreparedStatement ps = connection.prepareStatement(query)
        ) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<Integer, InAppNotification> records = new LinkedHashMap<>();
                while (rs.next()) {
                    Integer notificationId = rs.getInt("notification_id");
                    InAppNotification notification = records.get(notificationId);

                    if (notification == null) {
                        notification = new InAppNotification(
                            notificationId,
                            rs.getString("scheduled_date"),
                            rs.getString("scheduled_time"),
                            rs.getString("notification_timezone"),
                            rs.getBoolean("is_read"),
                            rs.getInt("additional_task_count"),
                            new ArrayList<>()
                        );

                        records.put(notificationId, notification);
                    }

                    Integer taskId = rs.getObject("task_id", Integer.class);
                    if (taskId != null) {
                        notification.tasks().add(
                            new NotificationTask(
                                taskId,
                                rs.getString("task_title"),
                                rs.getString("task_date_due")
                            )
                        );
                    }
                }

                return List.copyOf(records.values());
            }
        }
    }

    public static boolean markNotificationRead(Integer notificationId, Integer userId)
            throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (
            Connection connection = pool.getConnection();
            PreparedStatement update = connection.prepareStatement(
                "UPDATE notifications SET is_read = TRUE WHERE notification_id = ? AND user_id = ?"
            )
        ) {
            update.setInt(1, notificationId);
            update.setInt(2, userId);

            if (update.executeUpdate() > 0) {
                return true;
            }

            try (
                PreparedStatement exists = connection.prepareStatement(
                    "SELECT 1 FROM notifications WHERE notification_id = ? AND user_id = ?"
                )
            ) {
                exists.setInt(1, notificationId);
                exists.setInt(2, userId);

                try (ResultSet rs = exists.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    public static boolean deleteNotification(Integer notificationId, Integer userId)
            throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (
            Connection connection = pool.getConnection();
            PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM notifications WHERE notification_id = ? AND user_id = ?"
            )
        ) {
            ps.setInt(1, notificationId);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        }
    }

    public static int clearNotifications(Integer userId) throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (
            Connection connection = pool.getConnection();
            PreparedStatement ps = connection.prepareStatement(
               "DELETE FROM notifications WHERE user_id = ?"
            )
        ) {
            ps.setInt(1, userId);
            return ps.executeUpdate();
        }
    }

    /**
     * Converts a nullable SQL timestamp to a local date-time.
     */
    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
