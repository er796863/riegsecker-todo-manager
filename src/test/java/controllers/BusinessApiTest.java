package controllers;

import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
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
    }

    @Test
    public void unknownResourceReturnsNotFoundJson() throws Exception {
        when(request.getPathInfo()).thenReturn("/unknown/");

        api.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
        assertEquals("{\"error\":\"Unknown resource\"}", responseBody.toString());
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
