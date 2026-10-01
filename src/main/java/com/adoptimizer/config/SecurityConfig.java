package com.adoptimizer.config;

import com.adoptimizer.repository.UserRepository;
import com.adoptimizer.security.ActiveAccountFilter;
import com.adoptimizer.security.AppUserDetailsService;
import com.adoptimizer.security.AppUserPrincipal;
import com.adoptimizer.security.RestAccessDeniedHandler;
import com.adoptimizer.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Session-based security for a server-rendered app with a JSON API:
 * <ul>
 *   <li>Sign-in happens through {@code /api/auth/**} (so the UI can show inline errors and enforce the admin group code);
 *       the authenticated context is stored in the HTTP session.</li>
 *   <li>CSRF protection stays enabled; pages expose the token in a meta tag and JavaScript sends it as a header.</li>
 *   <li>URL rules separate advertisers ({@code /dashboard}, {@code /api/advertiser/**}) from admins
 *       ({@code /admin}, {@code /api/admin/**}).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository,
                                                   UserRepository userRepository) throws Exception {
        http
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/admin/login", "/error", "/favicon.svg").permitAll()
                        .requestMatchers("/css/**", "/js/**").permitAll()
                        .requestMatchers("/index.html", "/Index.html", "/admin-login.html",
                                "/dashboard.html", "/admin.html", "/Admin.html").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/dashboard", "/api/advertiser/**").hasRole("ADVERTISER")
                        .requestMatchers("/admin", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                        .accessDeniedHandler(new RestAccessDeniedHandler()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler())
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .addFilterBefore(new ActiveAccountFilter(userRepository), AuthorizationFilter.class);
        return http.build();
    }

    /** Admins return to the admin portal after signing out; advertisers return to the main sign-in page. */
    private LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            boolean admin = authentication != null
                    && authentication.getPrincipal() instanceof AppUserPrincipal principal
                    && principal.isAdmin();
            response.sendRedirect(request.getContextPath() + (admin ? "/admin/login?logout" : "/?logout"));
        };
    }
}
