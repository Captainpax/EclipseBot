package com.darkmatterservers.eclipsebot.service;

import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.config.builders.InitYaml;
import com.darkmatterservers.eclipsebot.service.discord.DiscordService;
import com.darkmatterservers.eclipsebot.service.discord.MessagingService;
import net.dv8tion.jda.api.JDA;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@Service
public class CoreService {

    private final LoggerService logger;
    private final InitYaml initYaml;
    private final YamlService yamlService;
    private final DiscordService discordService;
    private final MessagingService messagingService;

    private static final String CONFIG_PATH = "config.yaml";

    // Keeps the JVM alive, even if Discord fails to log in
    private final CountDownLatch keepAlive = new CountDownLatch(1);

    public CoreService(
            LoggerService logger,
            InitYaml initYaml,
            YamlService yamlService,
            DiscordService discordService,
            MessagingService messagingService
    ) {
        this.logger = logger;
        this.initYaml = initYaml;
        this.yamlService = yamlService;
        this.discordService = discordService;
        this.messagingService = messagingService;
    }

    public void start() {
        logger.info("🚀 Starting CoreService...", String.valueOf(getClass()));

        ensureConfigMerged();

        // Validate minimal Discord config presence
        String token = yamlService.getString("discord.token");
        String botId = yamlService.getString("discord.botId");

        boolean missingOrPlaceholder =
                token == null || token.isBlank() || "your-token-here".equalsIgnoreCase(token) ||
                        botId == null || botId.isBlank() || "your-bot-id-here".equalsIgnoreCase(botId);

        if (missingOrPlaceholder) {
            logger.warn("⚠️ Discord token or botId missing/placeholder — continuing in setup mode.", String.valueOf(getClass()));
        } else {
            logger.info("✅ Discord credentials detected — attempting login...", String.valueOf(getClass()));
        }

        // Try to start Discord; do NOT exit app if it fails
        boolean loggedIn = false;
        try {
            loggedIn = discordService.start(); // should return false on InvalidToken / failures
        } catch (Exception e) {
            logger.error("❌ Unexpected error while starting DiscordService", String.valueOf(getClass()), e);
        }

        if (!loggedIn) {
            logger.warn("⚠️ Discord login failed — entering setup flow (staying alive).", String.valueOf(getClass()));
            try {
                // Non-blocking: fire the greeting/setup chain to guide the admin
                messagingService.greetAdminOnStartup();
            } catch (Exception e) {
                logger.error("Failed to start setup messaging flow", String.valueOf(getClass()), e);
            }
        } else {
            logger.success("🤖 Discord connected — CoreService running.", String.valueOf(getClass()));
        }
    }

    private void ensureConfigMerged() {
        File configFile = new File(CONFIG_PATH);
        boolean configMissingOrEmpty = !configFile.exists() || configFile.length() == 0;

        logger.info("🧩 InitYaml: Building merged config map...", String.valueOf(getClass()));

        Map<String, Object> existingConfig = yamlService.getFullConfig();
        Map<String, Object> defaultConfig = initYaml.getDefaultConfig();
        Map<String, Object> mergedConfig = deepMerge(defaultConfig, existingConfig);

        if (configMissingOrEmpty) {
            logger.warn("🛠 config.yaml not found or empty — creating from defaults", String.valueOf(getClass()));
            yamlService.saveToFile(CONFIG_PATH, mergedConfig);
            logger.info("📄 config.yaml created/updated from defaults.", String.valueOf(getClass()));
        } else {
            logger.info("📁 config.yaml found — ensuring all required fields are present", String.valueOf(getClass()));
            yamlService.saveToFile(CONFIG_PATH, mergedConfig);
            logger.info("💾 Saved YAML config to " + CONFIG_PATH, String.valueOf(getClass()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepMerge(Map<String, Object> defaults, Map<String, Object> existing) {
        Map<String, Object> merged = new LinkedHashMap<>(defaults);

        if (existing == null) return merged;

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

    /** Blocks forever to keep the JVM alive (paired with EclipseBotApplication @PostConstruct). */
    public void blockIndefinitely() {
        try {
            keepAlive.await();
        } catch (InterruptedException ignored) {}
    }
}
