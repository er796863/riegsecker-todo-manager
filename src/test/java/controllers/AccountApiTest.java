package controllers;

import business.AccountCredentials;
import business.PasswordHasher;
import business.User;
import data.TodoDB;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.SQLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AccountApiTest {
    private AccountApi api;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseBody;

    @Before
    public void setUp() throws Exception {
        api = new AccountApi();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Test
    public void sessionEndpointReportsAnonymousState() throws Exception {
        when(request.getPathInfo()).thenReturn("/session");

        api.doGet(request, response);

        assertEquals("{\"authenticated\":false}", responseBody.toString());
    }

    @Test
    public void registrationRejectsInvalidUsernameBeforeDatabaseAccess() throws Exception {
        when(request.getPathInfo()).thenReturn("/register");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new java.io.BufferedReader(
            new StringReader(
                "{\"username\":\"ab\",\"firstName\":\"Jane\",\"lastName\":\"Doe\","
                + "\"email\":\"jane@example.com\",\"password\":\"a-long-password\"}"
            )
        ));

        api.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertEquals(
            "{\"error\":\"Username must be 3-50 characters using letters, numbers, "
                    + "dots, underscores, or hyphens\"}",
            responseBody.toString()
        );
    }

    @Test
    public void profileUpdateRequiresAnAuthenticatedSession() throws Exception {
        when(request.getPathInfo()).thenReturn("/profile");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new java.io.BufferedReader(new StringReader(
                "{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"jane@example.com\"}")));

        api.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals("{\"error\":\"Sign in to edit your profile\"}", responseBody.toString());
    }

    @Test
    public void profileUpdateUsesSessionUserRatherThanSubmittedUserId() throws Exception {
        when(request.getPathInfo()).thenReturn("/profile");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new java.io.BufferedReader(new StringReader(
                "{\"userId\":99,\"role\":\"ADMIN\",\"username\":\"janet\","
                        + "\"firstName\":\" Janet \",\"lastName\":\"Doe\","
                        + "\"email\":\"JANET@example.com\"}")));
        HttpSession session = mock(HttpSession.class);
        User signedInUser = new User(7, "jdoe", "Jane", "Doe", "jane@example.com", null, "USER");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AccountApi.SESSION_USER)).thenReturn(signedInUser);

        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> TodoDB.updateUserProfile(
                    7, "janet", "Janet", "Doe", "janet@example.com", null)).thenReturn(true);

            api.doPost(request, response);

            database.verify(() -> TodoDB.updateUserProfile(
                    7, "janet", "Janet", "Doe", "janet@example.com", null));
        }

        assertEquals("janet", signedInUser.getUserName());
        assertEquals("Janet", signedInUser.getFirstName());
        assertEquals("Doe", signedInUser.getLastName());
        assertEquals("janet@example.com", signedInUser.getEmail());
        assertEquals("USER", signedInUser.getRole());
        verify(session).setAttribute(AccountApi.SESSION_USER, signedInUser);
        assertTrue(responseBody.toString().contains("\"firstName\":\"Janet\""));
        assertFalse(responseBody.toString().contains("\"password\""));
    }

    @Test
    public void passwordChangeRequiresTheCurrentPassword() throws Exception {
        when(request.getPathInfo()).thenReturn("/profile");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new java.io.BufferedReader(new StringReader(
                "{\"username\":\"jdoe\",\"firstName\":\"Jane\",\"lastName\":\"Doe\","
                        + "\"email\":\"jane@example.com\",\"newPassword\":\"a-new-password\"}")));
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AccountApi.SESSION_USER))
                .thenReturn(new User(7, "jdoe", "Jane", "Doe", "jane@example.com", null, "USER"));

        api.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertEquals("{\"error\":\"Enter your current password to change it\"}", responseBody.toString());
    }

    @Test
    public void passwordChangeRejectsReusingTheCurrentPassword() throws Exception {
        String oldPassword = "previous-password";
        when(request.getPathInfo()).thenReturn("/profile");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new java.io.BufferedReader(new StringReader(
                "{\"username\":\"jdoe\",\"firstName\":\"Jane\",\"lastName\":\"Doe\","
                        + "\"email\":\"jane@example.com\",\"currentPassword\":\"" + oldPassword
                        + "\",\"newPassword\":\"" + oldPassword + "\"}")));
        HttpSession session = mock(HttpSession.class);
        User user = new User(7, "jdoe", "Jane", "Doe", "jane@example.com", null, "USER");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AccountApi.SESSION_USER)).thenReturn(user);

        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> TodoDB.findAccountByUserId(7))
                    .thenReturn(new AccountCredentials(user, PasswordHasher.hash(oldPassword)));

            api.doPost(request, response);

            verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
            assertEquals(
                    "{\"error\":\"New password must be different from your current password\"}",
                    responseBody.toString());
            database.verify(() -> TodoDB.updateUserProfile(
                    7, "jdoe", "Jane", "Doe", "jane@example.com", null), never());
        }
    }

    @Test
    public void passwordChangePersistsOnlyTheNewPasswordHash() throws Exception {
        String oldPassword = "previous-password";
        String newPassword = "a-new-long-password";
        when(request.getPathInfo()).thenReturn("/profile");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new java.io.BufferedReader(new StringReader(
                "{\"username\":\"jdoe\",\"firstName\":\"Jane\",\"lastName\":\"Doe\","
                        + "\"email\":\"jane@example.com\",\"currentPassword\":\"" + oldPassword
                        + "\",\"newPassword\":\"" + newPassword + "\"}")));
        HttpSession session = mock(HttpSession.class);
        User user = new User(7, "jdoe", "Jane", "Doe", "jane@example.com", null, "USER");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AccountApi.SESSION_USER)).thenReturn(user);

        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> TodoDB.findAccountByUserId(7))
                    .thenReturn(new AccountCredentials(user, PasswordHasher.hash(oldPassword)));
            database.when(() -> TodoDB.updateUserProfile(
                    eq(7), eq("jdoe"), eq("Jane"), eq("Doe"), eq("jane@example.com"), anyString()))
                    .thenAnswer(invocation -> {
                        String savedHash = invocation.getArgument(5);
                        assertTrue(PasswordHasher.verify(newPassword, savedHash));
                        assertFalse(savedHash.equals(newPassword));
                        return true;
                    });

            api.doPost(request, response);
        }

        assertTrue(responseBody.toString().contains("\"userName\":\"jdoe\""));
        assertFalse(responseBody.toString().contains(newPassword));
    }

    @Test
    public void usernameCollisionReturnsConflict() throws Exception {
        when(request.getPathInfo()).thenReturn("/profile");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new java.io.BufferedReader(new StringReader(
                "{\"username\":\"taken\",\"firstName\":\"Jane\",\"lastName\":\"Doe\","
                        + "\"email\":\"jane@example.com\"}")));
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AccountApi.SESSION_USER))
                .thenReturn(new User(7, "jdoe", "Jane", "Doe", "jane@example.com", null, "USER"));

        try (var database = mockStatic(TodoDB.class)) {
            database.when(() -> TodoDB.updateUserProfile(
                    7, "taken", "Jane", "Doe", "jane@example.com", null))
                    .thenThrow(new SQLException("Duplicate username", "23000"));

            api.doPost(request, response);
        }

        verify(response).setStatus(HttpServletResponse.SC_CONFLICT);
        assertEquals("{\"error\":\"Username or email is already in use\"}", responseBody.toString());
    }
}
