package com.darkmatterservers.eclipsebot.service.config.builders;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides the base structure for config.yaml during initial setup.
 */
@Component
public class InitYaml {

    private final LoggerService logger;

    public InitYaml(LoggerService logger) {
        this.logger = logger;
    }

    /**
     * Builds the default config structure as a map.
     */
    public Map<String, Object> getDefaultConfig() {
        logger.info("🛠 InitYaml: Building default config map...", String.valueOf(getClass()));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("bootstrapped", false);
        config.put("guildId", "your-guild-id-here");
        config.put("adminId", "your-admin-id-here");

        Map<String, Object> portRange = new LinkedHashMap<>();
        portRange.put("start", 5000);
        portRange.put("end", 5100);
        config.put("portRange", portRange);

        config.put("fqdn", "example.com");
        config.put("consoleChannelId", "");
        config.put("logsChannelId", "");
        config.put("waitingRoomChannelId", "");

        return config;
    }
}
