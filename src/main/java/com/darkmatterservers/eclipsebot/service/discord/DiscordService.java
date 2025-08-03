package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import org.springframework.stereotype.Service;

/**
 * Handles all Discord bot lifecycle and interactions.
 */
@Service
public class DiscordService {

    private final LoggerService logger;

    public DiscordService(LoggerService logger) {
        this.logger = logger;
    }

    public void start() {
        logger.info("🤖 DiscordService starting...", String.valueOf(getClass()));

        // TODO: Insert bot login and initialization logic here
        // Example: connect to Discord API, load handlers, register commands, etc.
    }
}
