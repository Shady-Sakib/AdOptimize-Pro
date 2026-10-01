package com.adoptimizer.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/** Not signed in: JSON 401 for API calls, redirect to the matching sign-in page for pages. */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        if (JsonResponses.isApiRequest(request)) {
            JsonResponses.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                    "Your session has ended. Sign in again to continue.");
            return;
        }
        String target = request.getServletPath().startsWith("/admin") ? "/admin/login" : "/";
        response.sendRedirect(request.getContextPath() + target);
    }
}
