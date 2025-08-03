package com.darkmatterservers.eclipsebot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple Hello World endpoint to verify HTTP server is running.
 */
@RestController
public class HelloWorldController {

    private static final Logger log = LoggerFactory.getLogger(HelloWorldController.class);

    @GetMapping("/")
    public String hello() {
        log.info("🌐 GET / requested — responding with Hello World");
        return "Hello from EclipseBot!";
    }

    @GetMapping("/ping")
    public String ping() {
        log.info("🔄 GET /ping requested — responding with pong");
        return "pong";
    }
}
