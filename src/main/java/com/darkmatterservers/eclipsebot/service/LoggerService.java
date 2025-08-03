package com.darkmatterservers.eclipsebot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Centralized logging service for EclipseBot.
 * All modules should log through this for consistent formatting and future log handling.
 */
@Service
public class LoggerService {

    private final Logger coreLog = LoggerFactory.getLogger("EclipseBot");

    public void info(String source, String message) {
        coreLog.info("[{}] {}", source, message);
    }

    public void warn(String source, String message) {
        coreLog.warn("[{}] ⚠️ {}", source, message);
    }

    public void error(String source, String message, Throwable e) {
        coreLog.error("[{}] ❌ {}", source, message, e);
    }

    public void debug(String source, String message) {
        coreLog.debug("[{}] 🐞 {}", source, message);
    }

    // Optional convenience shorthand methods
    public void info(String message) { info("General", message); }
    public void warn(String message) { warn("General", message); }
    public void debug(String message) { debug("General", message); }
    public void error(String message, Throwable e) { error("General", message, e); }
}
