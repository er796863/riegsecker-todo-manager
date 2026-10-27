package controllers;

import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;

import business.User;
import data.TodoDB;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
                .thenReturn(new User(7, "jdoe", "Jane", "Doe", "jane@example.com", null, "USER"));
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

        org.junit.Assert.assertTrue(BusinessApi.isAdmin(admin));
        org.junit.Assert.assertFalse(BusinessApi.isAdmin(regularUser));
        org.junit.Assert.assertFalse(BusinessApi.isAdmin(null));
    }

    @Test
    public void adminGetsOwnRecordsByDefault() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        when(request.getSession(false).getAttribute(AccountApi.SESSION_USER))
                .thenReturn(new User(1, "admin", "Alex", "Rivera", "admin@example.com", null, "ADMIN"));
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> TodoDB.selectTasks(1)).thenReturn(java.util.Map.of());

            api.doGet(request, response);

            database.verify(() -> TodoDB.selectTasks(1));
            database.verify(() -> TodoDB.selectTasks(null), org.mockito.Mockito.never());
        }
    }

    @Test
    public void onlyAdminsCanRequestAllRecords() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        when(request.getParameter("scope")).thenReturn("all");
        try (var database = mockStatic(TodoDB.class)) {
            api.doGet(request, response);

            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            assertEquals("{\"error\":\"Administrator access required\"}", responseBody.toString());
            database.verifyNoInteractions();
        }
    }

    @Test
    public void adminCanRequestAllRecordsExplicitly() throws Exception {
        when(request.getPathInfo()).thenReturn("/tasks");
        when(request.getParameter("scope")).thenReturn("all");
        when(request.getSession(false).getAttribute(AccountApi.SESSION_USER))
                .thenReturn(new User(1, "admin", "Alex", "Rivera", "admin@example.com", null, "ADMIN"));
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> TodoDB.selectTasks(null)).thenReturn(java.util.Map.of());

            api.doGet(request, response);

            database.verify(() -> TodoDB.selectTasks(null));
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

}
