package com.adoptimizer.security;

import com.adoptimizer.model.User;
import com.adoptimizer.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Ends the session of a user whose account was deactivated (or deleted) by an admin while
 * they were signed in. Without this, a deactivated advertiser could keep using an open tab.
 */
public class ActiveAccountFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public ActiveAccountFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.equals("/favicon.svg") || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal principal) {
            Optional<User> user = userRepository.findById(principal.getId());
            if (user.isEmpty() || !user.get().isActive()) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                if (JsonResponses.isApiRequest(request)) {
                    JsonResponses.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                            "This account has been deactivated. Contact support for help.");
                } else {
                    String target = principal.isAdmin() ? "/admin/login?deactivated" : "/?deactivated";
                    response.sendRedirect(request.getContextPath() + target);
                }
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
