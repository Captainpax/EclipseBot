package com.darkmatterservers.eclipsebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for EclipseBot.
 */
@SpringBootApplication
public class EclipseBotApplication {

    private static final Logger log = LoggerFactory.getLogger(EclipseBotApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(EclipseBotApplication.class, args);
        log.info("✅ EclipseBotApplication started successfully.");
        try {
            Thread.currentThread().join(); // Keeps app alive
        } catch (InterruptedException ignored) {}
    }
}
