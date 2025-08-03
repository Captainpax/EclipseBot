package com.darkmatterservers.eclipsebot;

import com.darkmatterservers.eclipsebot.service.CoreService;
import com.darkmatterservers.eclipsebot.service.LoggerService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Main entry point for EclipseBot.
 */
@SpringBootApplication
public class EclipseBotApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(EclipseBotApplication.class, args);

        LoggerService logger = context.getBean(LoggerService.class);
        CoreService coreService = context.getBean(CoreService.class);

        logger.info("Startup", "✅ EclipseBotApplication started successfully.");

        coreService.start();

        try {
            Thread.currentThread().join(); // Keeps app alive
        } catch (InterruptedException ignored) {
            logger.warn("Startup", "Main thread interrupted — shutting down.");
        }
    }
}
