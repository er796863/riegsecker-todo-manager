package controllers;

import business.User;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminPageFilterTest {
    private AdminPageFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @Before
    public void setUp() {
        filter = new AdminPageFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    public void redirectsUnauthenticatedVisitorsToLoginWithRequestedPage() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.getContextPath()).thenReturn("/todo");
        when(request.getRequestURI()).thenReturn("/todo/admin/tasks.html");

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect("/todo/account/login.html?next=%2Ftodo%2Fadmin%2Ftasks.html");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    public void deniesRegularUsers() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AccountApi.SESSION_USER))
                .thenReturn(new User(7, "jdoe", "Jane", "Doe", "jane@example.com", null, "USER"));

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    public void allowsAdministrators() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AccountApi.SESSION_USER))
                .thenReturn(new User(1, "admin", "Alex", "Rivera", "admin@example.com", null, "USER, ADMIN"));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter((ServletRequest) request, (ServletResponse) response);
    }
}
