package com.darkmatterservers.eclipsebot.service;

import com.darkmatterservers.eclipsebot.service.config.YamlService;
import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * LoggerService handles structured logging for both console and Discord (via log channel).
 * Levels: INFO, WARN, ERROR, SUCCESS.
 * <p>
 * Improvements over the previous version:
 *  - Masks sensitive values (token/secret/password/key) when mirrored to console/Discord
 *  - Hardened Discord mirroring (null checks, error handling, non-blocking)
 *  - Supports configurable channel key: "discord.logChannelId" (falls back to "logChannelId")
 *  - Helper formatters + convenience overloads
 */
@Service
public class LoggerService {

    private final YamlService yamlService;
    private volatile JDA jda;

    // Keys that should be masked when logged
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "token", "secret", "password", "passwd", "pwd", "key", "apikey", "api_key", "client_secret"
    );

    public LoggerService(@Lazy YamlService yamlService) {
        this.yamlService = yamlService;
    }

    @PostConstruct
    public void init() {
        info("LoggerService initialized", getClass().getName());
    }

    public void setJDA(JDA jda) {
        this.jda = jda;
    }

    // ================= INFO =================
    public void info(String msg, String source) {
        log("INFO", msg, source, null);
    }
    public void info(String msg) { log("INFO", msg, "System", null); }

    // ================= WARN =================
    public void warn(String msg, String source) {
        log("WARN", msg, source, null);
    }
    public void warn(String msg) { log("WARN", msg, "System", null); }

    // ================= ERROR =================
    public void error(String msg, String source) { log("ERROR", msg, source, null); }
    public void error(String msg, String source, Throwable t) { log("ERROR", msg, source, t); }

    // ================= SUCCESS =================
    public void success(String msg, String source) { log("SUCCESS", msg, source, null); }
    public void success(String msg) { log("SUCCESS", msg, "System", null); }

    // ================= Convenience (printf-style) =================
    public void infof(String source, String fmt, Object... args) { info(String.format(fmt, args), source); }
    public void warnf(String source, String fmt, Object... args) { warn(String.format(fmt, args), source); }
    public void errorf(String source, String fmt, Object... args) { error(String.format(fmt, args), source); }
    public void successf(String source, String fmt, Object... args) { success(String.format(fmt, args), source); }

    /**
     * Core logging method. Logs to console and mirrors to Discord if configured.
     */
    private void log(String level, String msg, String source, Throwable t) {
        String line = String.format("[%s] %s — %s", level, safeSource(source), safeMsg(msg));
        System.out.println(line);
        if (t != null) t.printStackTrace();

        mirrorToDiscord(level, msg, source);
    }

    /**
     * Mirrors a message to the configured Discord text channel (non-blocking).
     */
    private void mirrorToDiscord(String level, String msg, String source) {
        JDA localJda = this.jda;
        if (localJda == null) return;

        String channelId = firstNonBlank(
                yamlService != null ? yamlService.getString("discord.logChannelId") : null,
                yamlService != null ? yamlService.getString("logChannelId") : null
        );
        if (channelId == null || channelId.isBlank()) return;

        TextChannel textChannel = localJda.getTextChannelById(channelId);
        MessageChannel channel = textChannel != null ? textChannel : localJda.getPrivateChannelById(channelId);
        if (channel == null) return;

        String masked = maskIfSensitive(source, msg);
        String payload = String.format("`[%s %s]` **%s** — %s",
                level,
                Instant.now(),
                safeSource(source),
                masked
        );
        try {
            channel.sendMessage(payload).queue(
                    ok -> {},
                    err -> System.err.println("[LoggerService] Discord mirror failed: " + err)
            );
        } catch (Throwable th) {
            System.err.println("[LoggerService] Discord mirror threw: " + th);
        }
    }

    // ================= helpers =================

    private static String safeSource(String source) {
        return source != null ? source : "System";
    }

    private static String safeMsg(String msg) {
        return msg != null ? msg : "";
    }

    /**
     * Masks values for likely-secret updates. If the *source* looks like a config write for a key
     * that contains a sensitive term (token/secret/password/key), the message will be masked.
     */
    private static String maskIfSensitive(String source, String msg) {
        if (msg == null) return null;
        String src = source != null ? source.toLowerCase(Locale.ROOT) : "";
        boolean looksSensitive = SENSITIVE_KEYS.stream().anyMatch(src::contains);
        if (looksSensitive) return mask(msg);

        // Also try to detect common patterns like: "Updated config.yaml field: discord.token = X"
        if (msg.toLowerCase(Locale.ROOT).contains("token =") || msg.toLowerCase(Locale.ROOT).contains("secret =")) {
            int eq = msg.indexOf('=');
            if (eq > 0) {
                return msg.substring(0, eq + 1) + " " + mask(msg.substring(eq + 1).trim());
            }
            return mask(msg);
        }
        return msg;
    }

    private static String mask(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.length() <= 8) return "******";
        int visible = Math.min(4, v.length());
        return "******" + v.substring(v.length() - visible);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
