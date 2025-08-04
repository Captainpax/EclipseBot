package com.darkmatterservers.eclipsebot.service;

import com.darkmatterservers.eclipsebot.service.config.YamlService;
import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * LoggerService handles structured logging for both console and Discord (via log channel).
 * It supports multiple levels: INFO, WARN, ERROR, SUCCESS.
 * Messages are optionally mirrored to Discord if JDA is active.
 */
@Service
public class LoggerService {

    private final YamlService yamlService;
    private JDA jda;

    public LoggerService(@Lazy YamlService yamlService) {
        this.yamlService = yamlService;
    }

    @PostConstruct
    public void init() {
        info("🔧 LoggerService initialized", String.valueOf(getClass()));
    }

    public void setJDA(JDA jda) {
        this.jda = jda;
    }

    // === INFO ===
    public void info(String msg, String source) {
        log("INFO", msg, source, null);
    }

    public void info(String msg) {
        log("INFO", msg, "System", null);
    }

    // === WARN ===
    public void warn(String msg, String source) {
        log("WARN", msg, source, null);
    }

    public void warn(String msg) {
        log("WARN", msg, "System", null);
    }

    // === ERROR ===
    public void error(String msg, String source) {
        log("ERROR", msg, source, null);
    }

    public void error(String msg, String source, Throwable t) {
        log("ERROR", msg, source, t);
    }

    // === SUCCESS ===
    public void success(String msg, String source) {
        log("SUCCESS", msg, source, null);
    }

    public void success(String msg) {
        log("SUCCESS", msg, "System", null);
    }

    /**
     * Core logging method.
     * Logs to console and optionally mirrors to a Discord log channel.
     */
    private void log(String level, String msg, String source, Throwable t) {
        System.out.printf("[%s] %s — %s%n", level, source, msg);
        if (t != null) t.printStackTrace();

        // Discord mirror
        if (jda != null && yamlService != null) {
            String channelId = yamlService.getString("logChannelId");
            if (channelId != null) {
                MessageChannel channel = jda.getTextChannelById(channelId);
                if (channel != null) {
                    String formatted = String.format("`[%s]` **%s** — %s", level, source, msg);
                    channel.sendMessage(formatted).queue();
                }
            }
        }
    }
}
