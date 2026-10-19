package data;

import business.Schedule;
import business.Task;
import business.User;
import business.AccountCredentials;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

import javax.naming.NamingException;

public class TodoDB {
    // language=MySQL
    private static final String BASE_TASKS_QUERY = "SELECT task_id, task_title, task_description, date_created, date_due FROM tasks";

    // language=MySQL
    private static final String SELECT_TASK_QUERY = BASE_TASKS_QUERY
            + " WHERE (? IS NULL OR user_id = ?) AND task_id = ?";

    // language=MySQL
    private static final String SELECT_TASKS_QUERY = BASE_TASKS_QUERY
            + " WHERE (? IS NULL OR user_id = ?) ORDER BY task_id";

    // language=MySQL
    private static final String SEARCH_TASKS_QUERY = BASE_TASKS_QUERY
            + " WHERE (? IS NULL OR user_id = ?) AND CONCAT_WS(' ', CAST(task_id AS CHAR), task_title, task_description) "
            + "LIKE ? ORDER BY task_id";

    private static final String USER_ROLE_QUERY = "COALESCE((SELECT GROUP_CONCAT(ur.role_name ORDER BY ur.role_name SEPARATOR ', ') "
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
            + "FROM user_roles ur WHERE ur.username = users.username), 'USER')) LIKE ? ORDER BY user_id";

    // language=MySQL
    private static final String SCHEDULES_BASE_QUERY = "SELECT s.notification_setting AS notificationSettings, "
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

        return new Schedule(notificationSettings, tasks, user);
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
    public static Map<Integer, Schedule> searchSchedules(String search, Integer ownerId) throws NamingException, SQLException {
        return searchEntities(search, ownerId, SEARCH_SCHEDULES_QUERY, SELECT_SCHEDULES_QUERY,
                TodoDB::buildSchedule, TodoDB::scheduleUserId, TodoDB::mergeScheduleTasks);
    }

    public static AccountCredentials findAccount(String username) throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection();
                PreparedStatement ps = connection.prepareStatement(ACCOUNT_QUERY)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new AccountCredentials(buildUser(rs), rs.getString("password_hash"));
            }
        }
    }

    public static void updatePasswordHash(String username, String passwordHash)
            throws NamingException, SQLException {
        ConnectionPool pool = ConnectionPool.getInstance();
        try (Connection connection = pool.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "UPDATE users SET password_hash = ? WHERE username = ?")) {
            ps.setString(1, passwordHash);
            ps.setString(2, username);
            ps.executeUpdate();
        }
    }

    public static User createAccount(String username, String firstName, String lastName,
            String email, String passwordHash) throws NamingException, SQLException {
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
                try (PreparedStatement role = connection.prepareStatement(
                        "INSERT INTO user_roles (username, role_name) VALUES (?, 'USER')");
                        PreparedStatement schedule = connection.prepareStatement(
                                "INSERT INTO schedules (user_id, notification_setting) VALUES (?, 'NONE')")) {
                    role.setString(1, username);
                    role.executeUpdate();
                    schedule.setInt(1, userId);
                    schedule.executeUpdate();
                }
                connection.commit();
                return new User(userId, username, firstName, lastName, email, null, "USER");
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Converts a nullable SQL timestamp to a local date-time.
     */
    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
