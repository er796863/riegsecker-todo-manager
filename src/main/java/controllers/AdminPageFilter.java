package controllers;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.naming.NamingException;
import java.sql.SQLException;

import data.TodoDB;

public class AdminPageFilter implements Filter {
    private ServletContext servletContext;

    @Override
    public void init(FilterConfig filterConfig) {
        servletContext = filterConfig.getServletContext();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setHeader("Cache-Control", "no-store");

        var user = AccountApi.getSessionUser(httpRequest);
        if (user == null) {
            String next = httpRequest.getRequestURI();
            if (httpRequest.getQueryString() != null) {
                next += "?" + httpRequest.getQueryString();
            }
            String login = httpRequest.getContextPath() + "/account/login.html?next="
                    + URLEncoder.encode(next, StandardCharsets.UTF_8);
            httpResponse.sendRedirect(login);
            return;
        }
        try {
            if (user.getUserId() == null || !TodoDB.isAdministrator(user.getUserId())) {
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        } catch (NamingException | SQLException exception) {
            servletContext.log("Unable to verify administrator access", exception);
            httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
