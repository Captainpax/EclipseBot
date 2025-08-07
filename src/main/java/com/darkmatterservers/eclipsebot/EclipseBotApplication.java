package com.darkmatterservers.eclipsebot;

import com.darkmatterservers.eclipsebot.service.CoreService;
import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Main entry point for EclipseBot.
 */
@SpringBootApplication
public class EclipseBotApplication {

    public static void main(String[] args) {
        LoggerService logger = null;

        try {
            ConfigurableApplicationContext context = SpringApplication.run(EclipseBotApplication.class, args);

            logger = context.getBean(LoggerService.class);
            CoreService coreService = context.getBean(CoreService.class);
            YamlService yamlService = context.getBean(YamlService.class);

            logger.info("System", "✅ EclipseBotApplication started");

            // Prevent shutdown if token/adminId/etc. were already set
            boolean configValid = yamlService.getString("discord.token") != null &&
                    yamlService.getString("discord.adminId") != null &&
                    yamlService.getString("discord.botId") != null;

            if (!configValid) {
                logger.warn("System", "⚠️ Config missing required fields — falling back to CoreService.setup()");
            }

            coreService.start();

            logger.success("Startup", "🎉 EclipseBot is up and running.");

        } catch (Exception e) {
            if (logger != null) {
                logger.error("Startup", "🔥 Fatal error during startup: " + e.getMessage());
            } else {
                System.err.println("🔥 Fatal error during startup (Logger unavailable)");
                System.err.println("Exception: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            }
            System.exit(1);
        }
    }
}
