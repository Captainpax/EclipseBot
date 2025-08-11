package com.darkmatterservers.eclipsebot.service.config;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Robust YAML config service for reading/writing config.yaml.
 * - Safe loading with SnakeYAML SafeConstructor
 * - Defensive casting and deep-set helpers
 * - Optional typed getters (String, Boolean, Int, Long)
 * - Atomic save to avoid partial writes
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
        loaderOptions.setAllowDuplicateKeys(false);
        Representer representer = new Representer(options);

        this.yaml = new Yaml(new SafeConstructor(loaderOptions), representer, options);
        load();
    }

    // -------------------- Loading / Saving --------------------

    public synchronized void load() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            logger.warn("⚠️ config.yaml not found. Starting with empty config.", getClass().getName());
            configMap = new LinkedHashMap<>();
            return;
        }
        try (InputStream in = new FileInputStream(file)) {
            Object data = yaml.load(in);
            if (data instanceof Map<?, ?> loaded) {
                configMap = castToStringObjectMap(loaded);
                logger.info("📄 Loaded config.yaml successfully.", getClass().getName());
            } else {
                logger.warn("⚠️ config.yaml loaded but format is invalid; starting empty.", getClass().getName());
                configMap = new LinkedHashMap<>();
            }
        } catch (IOException e) {
            logger.error("❌ Failed to load config.yaml: " + e.getMessage(), getClass().getName());
            configMap = new LinkedHashMap<>();
        }
    }

    public synchronized void save() {
        saveToFile(CONFIG_FILE, configMap);
    }

    /** Write atomically to avoid truncated files. */
    public synchronized void saveToFile(String filePath, Map<String, Object> data) {
        Path path = Path.of(filePath);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            yaml.dump(data, w);
        } catch (IOException e) {
            logger.error("❌ Failed to write temp YAML: " + e.getMessage(), getClass().getName());
            return;
        }
        try {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            logger.info("✅ Saved YAML config to " + filePath, getClass().getName());
        } catch (IOException e) {
            logger.error("❌ Failed to finalize YAML save: " + e.getMessage(), getClass().getName());
        }
    }

    // -------------------- Getters --------------------

    public Object get(String path) {
        if (path == null || path.isBlank()) return null;
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
        Object v = get(path);
        return v == null ? null : String.valueOf(v);
    }

    public boolean getBoolean(String path, boolean def) {
        Object v = get(path);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return def;
    }

    public int getInt(String path, int def) {
        Object v = get(path);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public long getLong(String path, long def) {
        Object v = get(path);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    // -------------------- Mutators --------------------

    public synchronized void set(String path, Object value) {
        if (configMap == null || configMap.isEmpty()) load();
        if (path == null || path.isBlank()) return;
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
        logger.info("📝 Updated config: " + path + " = " + value, getClass().getName());
    }

    /** Bulk set without saving on each set; call save() once after. */
    public synchronized void setMultiple(Map<String, Object> updates) {
        if (updates == null) return;
        updates.forEach(this::set);
    }

    /** Put a sub-map under a base path (e.g., put("guilds.123", map)). */
    public synchronized void put(String basePath, Map<String, Object> map) {
        if (map == null) return;
        map.forEach((k, v) -> set(basePath + "." + k, v));
    }

    public Map<String, Object> getFullConfig() {
        return configMap;
    }

    // -------------------- Merge helpers --------------------

    public Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> e : override.entrySet()) {
            String key = e.getKey();
            Object ov = e.getValue();
            Object bv = result.get(key);
            if (ov instanceof Map && bv instanceof Map) {
                result.put(key, deepMerge(castToStringObjectMap(bv), castToStringObjectMap(ov)));
            } else {
                result.put(key, ov);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToStringObjectMap(Object obj) {
        try {
            return (Map<String, Object>) obj;
        } catch (ClassCastException e) {
            logger.error("❌ Failed to cast object to Map<String, Object>: " + e.getMessage(), getClass().getName());
            return new LinkedHashMap<>();
        }
    }
}
