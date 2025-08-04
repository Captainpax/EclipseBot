package com.darkmatterservers.eclipsebot.service.config;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Service responsible for reading, writing, and modifying config.yaml
 */
@Service
public class YamlService {

    private static final String CONFIG_FILE = "config.yaml";

    private final LoggerService logger;
    private final Yaml yaml;
    private Map<String, Object> configMap = new LinkedHashMap<>();

    public YamlService(@Lazy LoggerService logger) {
        this.logger = logger;

        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        LoaderOptions loaderOptions = new LoaderOptions();
        this.yaml = new Yaml(new SafeConstructor(loaderOptions).getLoadingConfig(), options);

        load();
    }

    /**
     * Loads config.yaml into memory.
     */
    public void load() {
        File file = new File(CONFIG_FILE);

        if (!file.exists()) {
            logger.warn("⚠️ config.yaml not found. Starting with empty config.", getClass().toString());
            configMap = new LinkedHashMap<>();
            return;
        }

        try (FileInputStream input = new FileInputStream(file)) {
            Object data = yaml.load(input);
            if (data instanceof Map<?, ?> loadedMap) {
                configMap = (Map<String, Object>) loadedMap;
                logger.info("📄 Loaded config.yaml successfully.", getClass().toString());
            } else {
                logger.warn("⚠️ config.yaml loaded but format is invalid.", getClass().toString());
                configMap = new LinkedHashMap<>();
            }
        } catch (IOException e) {
            logger.error("❌ Failed to load config.yaml: " + e.getMessage(), getClass().toString(), e);
            configMap = new LinkedHashMap<>();
        }
    }

    /**
     * Saves current in-memory configMap to config.yaml
     */
    public void save() {
        saveToFile(CONFIG_FILE, configMap);
    }

    /**
     * Writes arbitrary data to a YAML file.
     */
    public void saveToFile(String filePath, Map<String, Object> data) {
        try (FileWriter writer = new FileWriter(filePath)) {
            yaml.dump(data, writer);
            logger.info("✅ Saved YAML config to " + filePath, getClass().toString());
        } catch (IOException e) {
            logger.error("❌ Failed to save YAML config: " + e.getMessage(), getClass().toString(), e);
        }
    }

    /**
     * Get a nested config value by dot-path (e.g., "discord.token")
     */
    public String getString(String path) {
        Object value = get(path);
        return value != null ? String.valueOf(value) : null;
    }

    public Object get(String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = configMap;
        Object value = null;

        for (int i = 0; i < parts.length; i++) {
            String key = parts[i];
            if (i == parts.length - 1) {
                value = current.get(key);
            } else {
                Object nested = current.get(key);
                if (!(nested instanceof Map<?, ?>)) return null;
                current = (Map<String, Object>) nested;
            }
        }

        return value;
    }

    /**
     * Set a nested value using dot-path (e.g., "discord.token") and create structure if needed
     */
    public void set(String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = configMap;

        for (int i = 0; i < parts.length; i++) {
            String key = parts[i];
            if (i == parts.length - 1) {
                current.put(key, value);
            } else {
                Object next = current.get(key);
                if (!(next instanceof Map)) {
                    next = new LinkedHashMap<String, Object>();
                    current.put(key, next);
                }
                current = (Map<String, Object>) next;
            }
        }

        logger.info("📝 Updated config.yaml field: " + path + " = " + value, getClass().toString());
    }

    public Map<String, Object> getFullConfig() {
        return configMap;
    }
}
