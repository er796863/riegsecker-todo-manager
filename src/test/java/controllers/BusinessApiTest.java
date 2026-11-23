package controllers;

import org.junit.Before;
import org.junit.Test;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import business.User;
import business.Task;
import business.Schedule;
import data.TodoDB;

import static controllers.BusinessApi.isAdmin;
import static data.TodoDB.createManagedUser;
import static data.TodoDB.createTask;
import static data.TodoDB.deleteTask;
import static data.TodoDB.isAdministrator;
import static data.TodoDB.selectTasks;
import static data.TodoDB.updateSchedule;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BusinessApiTest {
    private BusinessApi api;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseBody;

    @Before
    public void setUp() throws Exception {
        api = new BusinessApi();

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        when(request.getParameter("search")).thenReturn(null);

        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AccountApi.SESSION_USER))
                .thenReturn(new User(
                    7,
                    "jdoe",
                    "Jane",
                    "Doe",
                    "jane@example.com",
                    null,
                    "USER"
                ));
    }

    @Test
    public void unknownResourceReturnsNotFoundJson() throws Exception {
        when(request.getPathInfo()).thenReturn("/unknown/");

        api.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
        assertEquals("{\"error\":\"Unknown resource\"}", responseBody.toString());
    }

    @Test
    public void entityApiRequiresAuthentication() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        when(request.getSession(false)).thenReturn(null);

        api.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals("{\"error\":\"Sign in to view records\"}", responseBody.toString());
    }

    @Test
    public void adminRoleIsRecognizedAlongsideOtherRoles() {
        User admin = new User(1, "admin", "Alex", "Rivera", "admin@example.com", null, "USER, ADMIN");
        User regularUser = new User(2, "jdoe", "Jane", "Doe", "jane@example.com", null, "USER");

        assertTrue(isAdmin(admin));
        assertFalse(isAdmin(regularUser));
        assertFalse(isAdmin(null));
    }

    @Test
    public void adminGetsOwnRecordsByDefault() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        when(request.getSession(false).getAttribute(AccountApi.SESSION_USER))
                .thenReturn(new User(1, "admin", "Alex", "Rivera", "admin@example.com", null, "ADMIN"));
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> selectTasks(1)).thenReturn(java.util.Map.of());

            api.doGet(request, response);

            database.verify(() -> selectTasks(1));
            database.verify(() -> selectTasks(null), never());
        }
    }

    @Test
    public void onlyAdminsCanRequestAllRecords() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        when(request.getParameter("scope")).thenReturn("all");
        when(request.getSession(false).getAttribute(AccountApi.SESSION_USER))
                .thenReturn(new User(7, "admin", "Alex", "Admin", "admin@example.com", null, "ADMIN"));
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> isAdministrator(7)).thenReturn(false);

            api.doGet(request, response);

            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            assertEquals("{\"error\":\"Administrator access required\"}", responseBody.toString());
            database.verify(
                    () -> selectTasks(isNull()),
                    never());
        }
    }

    @Test
    public void adminCanRequestAllRecordsExplicitly() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        when(request.getParameter("scope")).thenReturn("all");
        when(request.getSession(false).getAttribute(AccountApi.SESSION_USER))
                .thenReturn(new User(
                        1, "admin", "Alex", "Rivera", "admin@example.com", null, "ADMIN"));
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> isAdministrator(1)).thenReturn(true);
            database.when(() -> selectTasks(null)).thenReturn(java.util.Map.of());

            api.doGet(request, response);

            database.verify(() -> selectTasks(null));
        }
    }

    @Test
    public void nonIntegerIdReturnsBadRequestJson() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks/");
        when(request.getParameter("id")).thenReturn("not-an-integer");

        api.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertEquals("{\"error\":\"IDs must be integers\"}", responseBody.toString());
    }

    @Test
    public void taskCreationUsesCurrentUserAndReturnsCreatedId() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        setJsonBody("{\"taskTitle\":\"New task\",\"taskDescription\":\"Description\"}");

        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> createTask(
                    eq(7),
                    any(Task.class))).thenReturn(42);

            api.doPost(request, response);

            database.verify(() -> createTask(
                    eq(7),
                    any(Task.class)));

            verify(response).setStatus(HttpServletResponse.SC_CREATED);
            assertEquals("{\"taskId\":42}", responseBody.toString());
        }
    }

    @Test
    public void regularUserCannotCreateRecordsInAllScope() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        when(request.getParameter("scope")).thenReturn("all");

        setJsonBody("{\"taskTitle\":\"New task\"}");
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> isAdministrator(7)).thenReturn(false);

            api.doPost(request, response);

            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            assertEquals("{\"error\":\"Administrator access required\"}", responseBody.toString());

            database.verify(() -> createTask(
                    anyInt(),
                    any(Task.class)), never());
        }
    }

    @Test
    public void userManagementRequiresAdministratorRole() throws Exception {
        when(request.getPathInfo()).thenReturn("/users");
        setJsonBody("{\"username\":\"newuser\",\"firstName\":\"New\",\"lastName\":\"User\","
                + "\"email\":\"new@example.com\",\"password\":\"a-long-password\",\"role\":\"USER\"}");

        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> isAdministrator(7)).thenReturn(false);

            api.doPost(request, response);

            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            assertEquals("{\"error\":\"Administrator access required\"}", responseBody.toString());
            database.verify(() -> createManagedUser(
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()), never());
        }
    }

    @Test
    public void administratorCanCreateUserWithoutReturningPasswordHash() throws Exception {
        when(request.getPathInfo()).thenReturn("/users");
        when(request.getSession(false).getAttribute(AccountApi.SESSION_USER))
                .thenReturn(new User(1, "admin", "Alex", "Admin", "admin@example.com", null, "ADMIN"));

        setJsonBody("{\"username\":\"newuser\",\"firstName\":\"New\",\"lastName\":\"User\","
                + "\"email\":\"new@example.com\",\"password\":\"a-long-password\",\"role\":\"ADMIN\"}");
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> isAdministrator(1)).thenReturn(true);
            database.when(() -> createManagedUser(
                    eq("newuser"),
                    eq("New"),
                    eq("User"),
                    eq("new@example.com"),
                    anyString(),
                    eq("ADMIN")))
                    .thenReturn(new User(8, "newuser", "New", "User", "new@example.com", "secret-hash", "ADMIN"));

            api.doPost(request, response);

            verify(response).setStatus(HttpServletResponse.SC_CREATED);
            assertEquals("{\"userId\":8,\"userName\":\"newuser\",\"firstName\":\"New\","
                    + "\"lastName\":\"User\",\"email\":\"new@example.com\",\"role\":\"ADMIN\"}",
                    responseBody.toString());
        }
    }

    @Test
    public void scheduleUpdatePersistsWeekdaysTimeAndTimezone() throws Exception {
        when(request.getPathInfo()).thenReturn("/schedules");
        when(request.getParameter("id")).thenReturn("7");

        setJsonBody("{\"notificationsEnabled\":true,\"notificationDays\":[\"WEDNESDAY\",\"MONDAY\"],"
                + "\"notificationTime\":\"08:30\",\"notificationTimezone\":\"America/Denver\","
                + "\"taskIds\":[]}");

        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> updateSchedule(7, 7, null, "WEEKLY", true,
                    java.util.List.of("MONDAY", "WEDNESDAY"), "08:30", "America/Denver", java.util.List.of()))
                    .thenReturn(true);

            api.doPut(request, response);

            database.verify(() -> updateSchedule(7, 7, null, "WEEKLY", true,
                    java.util.List.of("MONDAY", "WEDNESDAY"), "08:30", "America/Denver", java.util.List.of()));
            assertEquals("{\"updated\":true}", responseBody.toString());
        }
    }

    @Test
    public void scheduleUpdateRejectsInvalidTimezone() throws Exception {
        when(request.getPathInfo()).thenReturn("/schedules");
        when(request.getParameter("id")).thenReturn("7");

        setJsonBody("{\"notificationsEnabled\":true,\"notificationDays\":[\"MONDAY\"],"
                + "\"notificationTime\":\"08:30\",\"notificationTimezone\":\"Not/AZone\",\"taskIds\":[]}");

        try (var database = mockStatic(TodoDB.class)) {
            api.doPut(request, response);

            verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
            assertEquals("{\"error\":\"Enter a valid IANA time zone\"}", responseBody.toString());
            database.verifyNoInteractions();
        }
    }

    @Test
    public void taskDeletionUsesCurrentUserOwnershipScope() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        when(request.getParameter("id")).thenReturn("15");

        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> deleteTask(15, 7)).thenReturn(TodoDB.TaskDeleteResult.DELETED);

            api.doDelete(request, response);

            database.verify(() -> deleteTask(15, 7));
            assertEquals("{\"deleted\":true}", responseBody.toString());
        }
    }

    private void setJsonBody(String body) throws Exception {
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new java.io.BufferedReader(new java.io.StringReader(body)));
    }

    @Test
    public void taskCreationIgnoresClientSuppliedOwner() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        setJsonBody("{\"userId\":1,\"taskTitle\":\"Plan project\",\"taskDescription\":\"Start now\",\"taskDateDue\":null}");
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> createTask(eq(7), any(Task.class))).thenReturn(41);
            api.doPost(request, response);
            database.verify(() -> createTask(eq(7), any(Task.class)));
        }
        verify(response).setStatus(HttpServletResponse.SC_CREATED);
        assertEquals("{\"taskId\":41}", responseBody.toString());
    }

    @Test
    public void taskUpdatesRemainScopedToTheSessionOwner() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        when(request.getParameter("id")).thenReturn("41");
        setJsonBody("{\"taskTitle\":\"Updated title\",\"taskDescription\":\"Updated\",\"taskDateDue\":null}");
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> TodoDB.updateTask(eq(41), eq(7), any(Task.class))).thenReturn(true);
            api.doPut(request, response);
            database.verify(() -> TodoDB.updateTask(eq(41), eq(7), any(Task.class)));
            database.verify(() -> TodoDB.updateTask(eq(41), eq(1), any(Task.class)), never());
        }
        assertEquals("{\"updated\":true}", responseBody.toString());
    }

    @Test
    public void scheduleUpdateUsesTheSessionUserAndAllowsSharedTasks() throws Exception {
        when(request.getPathInfo()).thenReturn("/schedules");
        setJsonBody("{\"userId\":1,\"scheduleName\":\" Weekly plan \","
                + "\"notificationSettings\":\"weekly\",\"taskIds\":[8,4]}");
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> updateSchedule(7, 7, "Weekly plan", "WEEKLY", List.of(8, 4)))
                    .thenReturn(TodoDB.ScheduleUpdateResult.UPDATED);
            api.doPut(request, response);
            database.verify(() -> updateSchedule(7, 7, "Weekly plan", "WEEKLY", List.of(8, 4)));
            database.verify(() -> updateSchedule(1, 7, "Weekly plan", "WEEKLY", List.of(8, 4)), never());
        }
        assertEquals("{\"updated\":true}", responseBody.toString());
    }

    @Test
    public void scheduleResponseIncludesItsName() throws Exception {
        when(request.getPathInfo()).thenReturn("/schedules");
        when(request.getParameter("id")).thenReturn("7");
        User user = new User(7, "jdoe", "Jane", "Doe", "jane@example.com", null, "USER");
        Schedule schedule = new Schedule("Work week", "DAILY", Map.of(), user);
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> TodoDB.selectSchedule(7, 7)).thenReturn(schedule);
            api.doGet(request, response);
            database.verify(() -> TodoDB.selectSchedule(7, 7));
        }
        assertTrue(responseBody.toString().contains("\"scheduleName\":\"Work week\""));
    }

    @Test
    public void scheduleTaskPickerReturnsMinimalOptionsForSharedTasks() throws Exception {
        when(request.getPathInfo()).thenReturn("/schedules/available-tasks");
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> selectTasks(null)).thenReturn(Map.of(
                    8, new Task(8, "Shared task", "private description",
                            LocalDateTime.of(2026, 1, 1, 9, 0), null)));
            api.doGet(request, response);
            database.verify(() -> selectTasks(null));
        }
        assertTrue(responseBody.toString().contains("\"taskTitle\":\"Shared task\""));
        assertTrue(responseBody.toString().contains("\"taskId\":8"));
        assertFalse(responseBody.toString().contains("private description"));
    }

    @Test
    public void scheduleTaskPickerRequiresAuthentication() throws Exception {
        when(request.getPathInfo()).thenReturn("/schedules/available-tasks");
        when(request.getSession(false)).thenReturn(null);
        try (var database = mockStatic(TodoDB.class)) {
            api.doGet(request, response);
            database.verifyNoInteractions();
        }
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void taskDeletionIsBlockedWhenAnotherScheduleUsesIt() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        when(request.getParameter("id")).thenReturn("41");
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> TodoDB.deleteTask(41, 7))
                    .thenReturn(TodoDB.TaskDeleteResult.ASSIGNED_TO_OTHER_USERS);
            api.doDelete(request, response);
            database.verify(() -> TodoDB.deleteTask(41, 7));
        }
        verify(response).setStatus(HttpServletResponse.SC_CONFLICT);
        assertTrue(responseBody.toString().contains("another user"));
        assertTrue(responseBody.toString().contains("schedule"));
    }

}
