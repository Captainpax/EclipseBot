package com.darkmatterservers.eclipsebot.service;

import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.config.builders.InitYaml;
import com.darkmatterservers.eclipsebot.service.discord.DiscordService;
import net.dv8tion.jda.api.JDA;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles the application startup and config bootstrapping.
 */
@Service
public class CoreService {

    private final LoggerService logger;
    private final InitYaml initYaml;
    private final YamlService yamlService;
    private final DiscordService discordService;
    private final AtomicReference<JDA> jdaRef;

    private static final String CONFIG_PATH = "config.yaml";

    public CoreService(
            LoggerService logger,
            InitYaml initYaml,
            YamlService yamlService,
            DiscordService discordService,
            AtomicReference<JDA> jdaRef
    ) {
        this.logger = logger;
        this.initYaml = initYaml;
        this.yamlService = yamlService;
        this.discordService = discordService;
        this.jdaRef = jdaRef;
    }

    /**
     * Starts the core service and initializes the bot if config is valid.
     */
    public void start() {
        logger.info("🚀 Starting CoreService...");

        File configFile = new File(CONFIG_PATH);
        boolean configMissingOrEmpty = !configFile.exists() || configFile.length() == 0;

        Map<String, Object> existingConfig = yamlService.getFullConfig();
        Map<String, Object> defaultConfig = initYaml.getDefaultConfig();
        Map<String, Object> mergedConfig = deepMerge(defaultConfig, existingConfig);

        if (configMissingOrEmpty) {
            logger.warn("🛠 config.yaml not found or empty — creating from defaults", getClass().toString());
            yamlService.saveToFile(CONFIG_PATH, mergedConfig);
            logger.info("📄 config.yaml created. Please review and restart the application.");
            return;
        } else {
            logger.info("📁 config.yaml found — ensuring all required fields are present");
            yamlService.saveToFile(CONFIG_PATH, mergedConfig);
        }

        // Validate discord credentials
        String token = yamlService.getString("discord.token");
        String botId = yamlService.getString("discord.botId");

        if (token == null || token.contains("your-token-here") || botId == null || botId.contains("your-bot-id-here")) {
            logger.error("❌ Discord token or botId is invalid. Please update config.yaml.", getClass().toString());
            return;
        }

        logger.info("✅ config.yaml verified. Launching DiscordService...");

        JDA jda = discordService.start(); // Updated to return JDA
        if (jda != null) {
            jdaRef.set(jda);
            logger.success("🤖 JDA is ready and reference set.", getClass().toString());
        } else {
            logger.error("❌ Failed to start DiscordService / JDA.", getClass().toString());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepMerge(Map<String, Object> defaults, Map<String, Object> existing) {
        Map<String, Object> merged = new LinkedHashMap<>(defaults);

        for (Map.Entry<String, Object> entry : existing.entrySet()) {
            String key = entry.getKey();
            Object existingValue = entry.getValue();
            Object defaultValue = merged.get(key);

            if (existingValue instanceof Map && defaultValue instanceof Map) {
                merged.put(
                        key,
                        deepMerge((Map<String, Object>) defaultValue, (Map<String, Object>) existingValue)
                );
            } else {
                merged.put(key, existingValue);
            }
        }

        return merged;
    }

    /**
     * Keeps the main thread alive indefinitely.
     */
    public void blockIndefinitely() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {}
    }
}
