package controllers;

import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
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
        when(request.getReader()).thenReturn(new java.io.BufferedReader(new StringReader(
                "{\"username\":\"ab\",\"firstName\":\"Jane\",\"lastName\":\"Doe\","
                        + "\"email\":\"jane@example.com\",\"password\":\"a-long-password\"}")));

        api.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertEquals(
                "{\"error\":\"Username must be 3-50 characters using letters, numbers, dots, underscores, or hyphens\"}",
                responseBody.toString());
    }
}
