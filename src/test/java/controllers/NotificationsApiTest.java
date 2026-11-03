package controllers;

import business.InAppNotification;
import business.NotificationTask;
import business.User;
import data.TodoDB;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NotificationsApiTest {
    private NotificationsApi api;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseBody;

    @Before
    public void setUp() throws Exception {
        api = new NotificationsApi();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AccountApi.SESSION_USER))
                .thenReturn(new User(7, "jdoe", "Jane", "Doe", "jane@example.com", null, "USER"));
    }

    @Test
    public void listingReturnsOnlyTheSignedInUsersNotifications() throws Exception {
        when(request.getPathInfo()).thenReturn(null);
        InAppNotification notification = new InAppNotification(
                12, "2026-11-02", "09:00", "America/Denver", false, 1,
                List.of(new NotificationTask(31, "Review project", null)));
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> TodoDB.selectNotifications(7)).thenReturn(List.of(notification));

            api.doGet(request, response);

            database.verify(() -> TodoDB.selectNotifications(7));
            assertEquals(
                    "[{\"notificationId\":12,\"scheduledDate\":\"2026-11-02\",\"scheduledTime\":\"09:00\","
                            + "\"notificationTimezone\":\"America/Denver\",\"read\":false,\"additionalTaskCount\":1,"
                            + "\"tasks\":[{\"taskId\":31,\"taskTitle\":\"Review project\",\"taskDateDue\":null}]}]",
                    responseBody.toString());
        }
    }

    @Test
    public void markReadUsesTheCurrentUserAsOwner() throws Exception {
        when(request.getPathInfo()).thenReturn("/read");
        when(request.getParameter("id")).thenReturn("12");
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> TodoDB.markNotificationRead(12, 7)).thenReturn(true);

            api.doPut(request, response);

            database.verify(() -> TodoDB.markNotificationRead(12, 7));
            assertEquals("{\"read\":true}", responseBody.toString());
        }
    }

    @Test
    public void clearAllUsesTheCurrentUserAsOwner() throws Exception {
        when(request.getPathInfo()).thenReturn(null);
        when(request.getParameter("id")).thenReturn(null);
        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> TodoDB.clearNotifications(7)).thenReturn(3);

            api.doDelete(request, response);

            database.verify(() -> TodoDB.clearNotifications(7));
            assertEquals("{\"deletedCount\":3}", responseBody.toString());
        }
    }

    @Test
    public void notificationApiRequiresAuthentication() throws Exception {
        when(request.getPathInfo()).thenReturn("/");
        when(request.getSession(false)).thenReturn(null);

        api.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals("{\"error\":\"Sign in to view notifications\"}", responseBody.toString());
    }
}
