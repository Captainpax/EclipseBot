package com.darkmatterservers.eclipsebot.service.config.builders;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides the base structure for config.yaml during initial setup.
 */
@Component
public class InitYaml {

    private final LoggerService logger;
    private final YamlService yamlService;

    public InitYaml(LoggerService logger, YamlService yamlService) {
        this.logger = logger;
        this.yamlService = yamlService;
    }

    /**
     * Builds the default config structure merged with existing fields.
     */
    public Map<String, Object> getDefaultConfig() {
        logger.info("🛠 InitYaml: Building merged config map...");

        Map<String, Object> defaults = new LinkedHashMap<>();

        // Discord block
        Map<String, Object> discord = new LinkedHashMap<>();
        discord.put("token", "your-token-here");
        discord.put("botId", "your-bot-id-here");
        defaults.put("discord", discord);

        // Top-level metadata
        defaults.put("bootstrapped", false);
        defaults.put("guildId", "your-guild-id-here");
        defaults.put("adminId", "your-admin-id-here");

        // Port range
        Map<String, Object> portRange = new LinkedHashMap<>();
        portRange.put("start", 5000);
        portRange.put("end", 5100);
        defaults.put("portRange", portRange);

        // Server FQDN
        defaults.put("fqdn", "example.com");

        // Channel IDs
        defaults.put("consoleChannelId", "");
        defaults.put("logsChannelId", "");
        defaults.put("waitingRoomChannelId", "");

        // Merge with the current config to avoid overwriting real values
        Map<String, Object> current = yamlService.getFullConfig();
        Map<String, Object> merged = yamlService.deepMerge(defaults, current);

        logger.info("✅ Merged default config with existing values.");
        return merged;
    }
}
