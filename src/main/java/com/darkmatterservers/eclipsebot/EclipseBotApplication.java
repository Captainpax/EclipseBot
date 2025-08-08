package com.darkmatterservers.eclipsebot;

import com.darkmatterservers.eclipsebot.service.CoreService;
import com.darkmatterservers.eclipsebot.service.LoggerService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.concurrent.CompletableFuture;

@SpringBootApplication
public class EclipseBotApplication {

    private final LoggerService logger;
    private final CoreService coreService;

    public EclipseBotApplication(LoggerService logger, CoreService coreService) {
        this.logger = logger;
        this.coreService = coreService;
    }

    public static void main(String[] args) {
        SpringApplication.run(EclipseBotApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        logger.info("✅ EclipseBotApplication started successfully", String.valueOf(getClass()));
        // Start CoreService without blocking the web server startup
        CompletableFuture.runAsync(coreService::start);
    }
}
