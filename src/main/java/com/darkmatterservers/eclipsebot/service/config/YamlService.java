package com.darkmatterservers.eclipsebot.service.config;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

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
        Representer representer = new Representer(options);

        this.yaml = new Yaml(new SafeConstructor(loaderOptions), representer, options);
        load();
    }

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
                configMap = castToStringObjectMap(loadedMap);
                logger.info("📄 Loaded config.yaml successfully.");
            } else {
                logger.warn("⚠️ config.yaml loaded but format is invalid.", getClass().toString());
                configMap = new LinkedHashMap<>();
            }
        } catch (IOException e) {
            logger.error("❌ Failed to load config.yaml: " + e.getMessage(), getClass().toString());
            configMap = new LinkedHashMap<>();
        }
    }

    public void save() {
        saveToFile(CONFIG_FILE, configMap);
    }

    public void saveToFile(String filePath, Map<String, Object> data) {
        try (FileWriter writer = new FileWriter(filePath)) {
            yaml.dump(data, writer);
            logger.info("✅ Saved YAML config to " + filePath);
        } catch (IOException e) {
            logger.error("❌ Failed to save YAML config: " + e.getMessage(), getClass().toString());
        }
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
                if (!(nested instanceof Map)) return null;
                current = castToStringObjectMap(nested);
            }
        }

        return value;
    }

    public String getString(String path) {
        Object value = get(path);
        return value != null ? String.valueOf(value) : null;
    }

    public void set(String path, Object value) {
        if (configMap == null || configMap.isEmpty()) {
            load();
        }

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
                current = castToStringObjectMap(next);
            }
        }

        logger.info("📝 Updated config.yaml field: " + path + " = " + value);
        save();
    }

    public void setMultiple(Map<String, Object> updates) {
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
    }

    public Map<String, Object> getFullConfig() {
        return configMap;
    }

    public Map<String, Object> deepMerge(Map<String, Object> defaultMap, Map<String, Object> overrideMap) {
        Map<String, Object> result = new LinkedHashMap<>(defaultMap);

        for (Map.Entry<String, Object> entry : overrideMap.entrySet()) {
            String key = entry.getKey();
            Object overrideValue = entry.getValue();
            Object baseValue = result.get(key);

            if (overrideValue instanceof Map && baseValue instanceof Map) {
                Map<String, Object> mergedChild = deepMerge(
                        castToStringObjectMap(baseValue),
                        castToStringObjectMap(overrideValue)
                );
                result.put(key, mergedChild);
            } else {
                result.put(key, overrideValue);
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToStringObjectMap(Object obj) {
        try {
            return (Map<String, Object>) obj;
        } catch (ClassCastException e) {
            logger.error("❌ Failed to cast object to Map<String, Object>: " + e.getMessage(), getClass().toString());
            return new LinkedHashMap<>();
        }
    }
}
