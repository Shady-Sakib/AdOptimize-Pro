package com.adoptimizer.security;

import com.adoptimizer.model.Role;
import com.adoptimizer.model.User;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * The authenticated user stored in the HTTP session. Controllers use {@link #getId()};
 * the email is only used as the login name, so profile email changes are safe.
 */
@Getter
public class AppUserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String name;
    private final Role role;
    private final boolean active;
    private String passwordHash;

    public AppUserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.role = user.getRole();
        this.active = user.isActive();
        this.passwordHash = user.getPasswordHash();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Always {@code true}: the active flag is checked by {@code AuthService} only after the password has been
     * verified (so a wrong password never reveals that an account exists but is deactivated), and by
     * {@code ActiveAccountFilter} on every request of an existing session.
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
