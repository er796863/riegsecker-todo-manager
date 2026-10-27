package controllers;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class AdminPageFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {
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
        if (!BusinessApi.isAdmin(user)) {
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
