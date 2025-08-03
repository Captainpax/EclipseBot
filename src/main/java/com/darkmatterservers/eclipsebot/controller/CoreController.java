package com.darkmatterservers.eclipsebot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Core controller for EclipseBot status and health endpoints.
 */
@RestController
public class CoreController {

    private static final Logger log = LoggerFactory.getLogger(CoreController.class);

    @GetMapping("/")
    public String root() {
        log.info("🌐 GET / requested — responding with Hello World");
        return "Hello from EclipseBot!";
    }

    @GetMapping("/ding")
    public String ping() {
        log.info("🔄 GET /ping requested — responding with dong");
        return "dong";
    }

    @GetMapping("/status")
    public String status() {
        log.info("📈 GET /status requested — responding with OK");
        return "EclipseBot is running ✅";
    }
}
