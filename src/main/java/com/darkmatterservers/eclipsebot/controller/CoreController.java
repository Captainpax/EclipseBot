package com.darkmatterservers.eclipsebot.controller;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Core controller for EclipseBot status and health endpoints.
 */
@RestController
public class CoreController {

    private final LoggerService logger;

    public CoreController(LoggerService logger) {
        this.logger = logger;
    }

    @GetMapping("/")
    public String root() {
        logger.info("🌐 GET / requested — responding with Hello World", String.valueOf(getClass()));
        return "Hello from EclipseBot!";
    }

    @GetMapping("/ding")
    public String ping() {
        logger.info("🔄 GET /ding requested — responding with dong", String.valueOf(getClass()));
        return "dong";
    }

    @GetMapping("/status")
    public String status() {
        logger.info("📈 GET /status requested — responding with OK", String.valueOf(getClass()));
        return "EclipseBot is running ✅";
    }
}
