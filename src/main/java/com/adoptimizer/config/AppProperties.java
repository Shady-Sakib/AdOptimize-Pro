package com.adoptimizer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Type-safe binding of the {@code app.*} entries in application.properties. */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Admin admin = new Admin();
    private final Seed seed = new Seed();
    private final Simulation simulation = new Simulation();
    private final Security security = new Security();

    @Data
    public static class Admin {
        /** Code required to sign in to, or create, an admin account. */
        private String groupCode = "group 5";
    }

    @Data
    public static class Seed {
        private boolean enabled = true;
    }

    @Data
    public static class Simulation {
        private long intervalMs = 20000;
        private long initialDelayMs = 15000;
    }

    @Data
    public static class Security {
        private int maxLoginAttempts = 5;
        private int lockoutMinutes = 5;
    }
}
