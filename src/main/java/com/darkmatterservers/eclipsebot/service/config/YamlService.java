package com.darkmatterservers.eclipsebot.service.config;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Service responsible for serializing configuration maps to YAML files.
 */
@Service
public class YamlService {

    private final LoggerService logger;

    public YamlService(LoggerService logger) {
        this.logger = logger;
    }

    /**
     * Writes the given config map to the specified file path as YAML.
     *
     * @param filePath Destination file name (e.g., "config.yaml")
     * @param data     Map representing the config structure
     */
    public void saveToFile(String filePath, Map<String, Object> data) {
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        Yaml yaml = new Yaml(options);

        try (FileWriter writer = new FileWriter(filePath)) {
            yaml.dump(data, writer);
            logger.info("✅ Successfully saved YAML config to " + filePath, String.valueOf(getClass()));
        } catch (IOException e) {
            logger.error("❌ Failed to save YAML config: " + e.getMessage(), String.valueOf(getClass()), e);
        }
    }
}
