package com.darkmatterservers.eclipsebot.service;

import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.config.builders.InitYaml;
import com.darkmatterservers.eclipsebot.service.discord.DiscordService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;

@Service
public class CoreService {

    private final LoggerService logger;
    private final InitYaml initYaml;
    private final YamlService yamlService;
    private final DiscordService discordService;

    private static final String CONFIG_PATH = "config.yaml";

    public CoreService(
            LoggerService logger,
            InitYaml initYaml,
            YamlService yamlService,
            DiscordService discordService
    ) {
        this.logger = logger;
        this.initYaml = initYaml;
        this.yamlService = yamlService;
        this.discordService = discordService;
    }

    public void start() {
        logger.info("🚀 Starting CoreService...", getClass().toString());

        File configFile = new File(CONFIG_PATH);
        if (!configFile.exists() || configFile.length() == 0) {
            logger.warn("🛠 config.yaml not found or empty — generating default config...", getClass().toString());

            Map<String, Object> defaultConfig = initYaml.getDefaultConfig();
            yamlService.saveToFile(CONFIG_PATH, defaultConfig);

            logger.info("📄 config.yaml created. Please review and restart the application.", getClass().toString());
        } else {
            logger.info("🧩 config.yaml found. Launching DiscordService...", getClass().toString());
            discordService.start();
        }
    }

    public void blockIndefinitely() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {
        }
    }
}
