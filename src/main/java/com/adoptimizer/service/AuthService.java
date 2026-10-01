package com.adoptimizer.service;

import com.adoptimizer.config.AppProperties;
import com.adoptimizer.dto.request.AdminLoginRequest;
import com.adoptimizer.dto.request.AdminRegisterRequest;
import com.adoptimizer.dto.request.LoginRequest;
import com.adoptimizer.dto.request.RegisterRequest;
import com.adoptimizer.dto.response.AuthResponse;
import com.adoptimizer.exception.ApiException;
import com.adoptimizer.exception.FieldValidationException;
import com.adoptimizer.model.NotificationType;
import com.adoptimizer.model.Role;
import com.adoptimizer.model.User;
import com.adoptimizer.repository.UserRepository;
import com.adoptimizer.security.AppUserPrincipal;
import com.adoptimizer.security.LoginAttemptService;
import com.adoptimizer.util.TimeUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;
    private final NotificationService notificationService;
    private final AppProperties properties;

    // ------------------------------------------------------------------ sign in

    public AuthResponse loginAdvertiser(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AppUserPrincipal principal = authenticate(request.getEmail(), request.getPassword());
        if (principal.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Administrator accounts sign in through the admin portal.");
        }
        startSession(principal, httpRequest, httpResponse);
        return new AuthResponse("Welcome back, " + principal.getName() + "!", principal.getName(), "advertiser", "/dashboard");
    }

    public AuthResponse loginAdmin(AdminLoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        ensureNotLocked(request.getEmail());
        if (!groupCodeMatches(request.getGroupCode())) {
            loginAttemptService.recordFailure(request.getEmail());
            throw new FieldValidationException(HttpStatus.FORBIDDEN, "groupCode", "Invalid group code. Access denied.");
        }
        AppUserPrincipal principal = authenticate(request.getEmail(), request.getPassword());
        if (!principal.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "This is not an administrator account. Use the advertiser sign-in instead.");
        }
        startSession(principal, httpRequest, httpResponse);
        return new AuthResponse("Welcome back, " + principal.getName() + "!", principal.getName(), "admin", "/admin");
    }

    // ------------------------------------------------------------------ sign up

    public AuthResponse registerAdvertiser(RegisterRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = createUser(request, Role.ADVERTISER, blankToNull(request.getCompany()));
        notificationService.notify(user.getId(), NotificationType.INFO, "Welcome to AdOptimize Pro",
                "Add funds in Payments, then create your first campaign. Our optimizer will suggest improvements once it is live.");
        startSession(new AppUserPrincipal(user), httpRequest, httpResponse);
        return new AuthResponse("Account created. Welcome, " + user.getName() + "!", user.getName(), "advertiser", "/dashboard");
    }

    public AuthResponse registerAdmin(AdminRegisterRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (!groupCodeMatches(request.getGroupCode())) {
            throw new FieldValidationException(HttpStatus.FORBIDDEN, "groupCode",
                    "Invalid group code. Admin account creation denied.");
        }
        String department = blankToNull(request.getCompany());
        User user = createUser(request, Role.ADMIN, department == null ? "AdOptimize Pro" : department);
        log.info("New administrator account created: {}", user.getEmail());
        startSession(new AppUserPrincipal(user), httpRequest, httpResponse);
        return new AuthResponse("Admin account created. Welcome, " + user.getName() + "!", user.getName(), "admin", "/admin");
    }

    // ------------------------------------------------------------------ internals

    private User createUser(RegisterRequest request, Role role, String company) {
        String email = UserRepository.normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new FieldValidationException(HttpStatus.CONFLICT, "email", "An account with this email already exists.");
        }
        User user = User.builder()
                .name(request.getName().trim().replaceAll("\\s+", " "))
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .company(company)
                .role(role)
                .active(true)
                .createdAt(TimeUtils.now())
                .build();
        try {
            user.setId(userRepository.insert(user));
        } catch (DuplicateKeyException e) {
            throw new FieldValidationException(HttpStatus.CONFLICT, "email", "An account with this email already exists.");
        }
        userRepository.updateLastLogin(user.getId(), TimeUtils.now());
        return user;
    }

    private AppUserPrincipal authenticate(String email, String password) {
        ensureNotLocked(email);
        try {
            Authentication result = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(UserRepository.normalizeEmail(email), password));
            AppUserPrincipal principal = (AppUserPrincipal) result.getPrincipal();
            loginAttemptService.recordSuccess(email);
            // Checked only after the password is verified, so the message never reveals which emails exist.
            if (!principal.isActive()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "This account has been deactivated. Contact support for help.");
            }
            userRepository.updateLastLogin(principal.getId(), TimeUtils.now());
            return principal;
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        } catch (AuthenticationException e) {
            log.warn("Authentication failed for {}: {}", email, e.getMessage());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign-in failed. Try again.");
        }
    }

    private void ensureNotLocked(String email) {
        Duration lock = loginAttemptService.remainingLock(email);
        if (!lock.isZero()) {
            long minutes = Math.max(1, (lock.getSeconds() + 59) / 60);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed sign-in attempts. Try again in "
                    + minutes + (minutes == 1 ? " minute." : " minutes."));
        }
    }

    /** Stores the authenticated user in a fresh session (new session id prevents session fixation). */
    private void startSession(AppUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();
        SecurityContextHolderStrategy strategy = SecurityContextHolder.getContextHolderStrategy();
        SecurityContext context = strategy.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        strategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /** Case-insensitive, whitespace-tolerant, constant-time comparison with the configured group code. */
    boolean groupCodeMatches(String input) {
        byte[] expected = normalizeCode(properties.getAdmin().getGroupCode()).getBytes(StandardCharsets.UTF_8);
        byte[] actual = normalizeCode(input).getBytes(StandardCharsets.UTF_8);
        return expected.length > 0 && MessageDigest.isEqual(expected, actual);
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
