package com.adoptimizer.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;

/**
 * Signed in but not allowed (wrong role), or the CSRF token is missing/expired.
 * API calls get JSON 403; pages redirect the user to their own area.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        boolean csrf = exception instanceof CsrfException;
        if (JsonResponses.isApiRequest(request)) {
            String message = csrf
                    ? "Your session has expired. Refresh the page and try again."
                    : "You don't have permission to perform this action.";
            JsonResponses.write(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", message);
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String target = "/";
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal principal) {
            target = principal.isAdmin() ? "/admin" : "/dashboard";
        }
        response.sendRedirect(request.getContextPath() + target);
    }
}
