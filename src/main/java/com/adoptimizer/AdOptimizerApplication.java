package com.adoptimizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Online Advertisement Optimizer (AdOptimize Pro). */
@SpringBootApplication
@EnableScheduling
public class AdOptimizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdOptimizerApplication.class, args);
    }
}
