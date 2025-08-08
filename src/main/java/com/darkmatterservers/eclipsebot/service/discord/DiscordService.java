package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles the lifecycle and connection of the EclipseBot Discord client.
 */
@Service
public class DiscordService {

    private final LoggerService logger;
    private final YamlService yamlService;
    private final MessagingService messagingService;
    private final AtomicReference<JDA> jdaRef;

    private String token;
    private String botId;
    private String adminId;

    @Getter
    private volatile boolean running = false;

    public DiscordService(
            LoggerService logger,
            YamlService yamlService,
            MessagingService messagingService,
            AtomicReference<JDA> jdaRef
    ) {
        this.logger = logger;
        this.yamlService = yamlService;
        this.messagingService = messagingService;
        this.jdaRef = jdaRef;

        // Load initial credentials from YAML
        reloadCredsFromYaml();
    }

    private void reloadCredsFromYaml() {
        this.token = yamlService.getString("discord.token");
        this.botId = yamlService.getString("discord.botId");
        this.adminId = yamlService.getString("discord.adminId"); // NOTE: keep consistent key (discord.adminId)

        if (token != null && !token.isBlank()) {
            logger.info("🔍 Loaded Discord token from YAML.", String.valueOf(getClass()));
        } else {
            logger.warn("⚠️ No Discord token found in YAML.", String.valueOf(getClass()));
        }
    }

    /**
     * Attempts to start JDA. Returns true if the bot is fully connected (awaitReady), false otherwise.
     * NEVER throws; on failure, logs and returns false so CoreService can fall back to setup mode.
     */
    public boolean start() {
        // Validate creds (and treat placeholders as invalid)
        if (token == null || token.isBlank() || "your-token-here".equalsIgnoreCase(token)
                || botId == null || botId.isBlank() || "your-bot-id-here".equalsIgnoreCase(botId)) {
            logger.warn("⚠️ Discord token or botId missing/placeholder — continuing in setup mode.", String.valueOf(getClass()));
            running = false;
            return false;
        }

        // Build JDA
        try {
            logger.info("🤖 Attempting Discord login with bot ID: " + botId, String.valueOf(getClass()));

            JDABuilder builder = JDABuilder.create(token, EnumSet.of(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.DIRECT_MESSAGES
                    ))
                    .disableCache(
                            CacheFlag.ACTIVITY,
                            CacheFlag.VOICE_STATE,
                            CacheFlag.EMOJI,
                            CacheFlag.STICKER,
                            CacheFlag.CLIENT_STATUS,
                            CacheFlag.ONLINE_STATUS,
                            CacheFlag.SCHEDULED_EVENTS
                    )
                    .setActivity(Activity.watching("for /setup requests"));

            // Start connecting
            JDA jda = builder.build();

            // Wait until ready so a `true` return actually means "connected and usable"
            // (Use a sane timeout to avoid hanging forever if Discord is unreachable)
            boolean ready;
            try {
                jda.awaitReady();
                ready = true;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.error("❌ Startup interrupted while connecting to Discord.", String.valueOf(getClass()), ie);
                shutdownQuietly(jda);
                running = false;
                return false;
            } catch (Exception e) {
                logger.error("🔥 Error while waiting for Discord readiness: " + e.getMessage(), String.valueOf(getClass()), e);
                shutdownQuietly(jda);
                running = false;
                return false;
            }

            if (!ready) {
                logger.error("❌ Discord did not become ready within timeout.", String.valueOf(getClass()));
                shutdownQuietly(jda);
                running = false;
                return false;
            }

            // Success
            jdaRef.set(jda);
            running = true;

            logger.success("✅ Discord bot is online as " + jda.getSelfUser().getAsTag(), String.valueOf(getClass()));

            // Optional: greet configured admin on successful connect
            if (adminId != null && !adminId.isBlank()) {
                try {
                    messagingService.greetAdminOnStartup(adminId);
                } catch (Exception e) {
                    logger.warn("⚠️ Could not greet admin on startup: " + e.getMessage(), String.valueOf(getClass()));
                }
            }

            return true;

        } catch (InvalidTokenException e) {
            logger.error("❌ Invalid Discord token — startup failed.", String.valueOf(getClass()), e);
        } catch (Exception e) {
            logger.error("🔥 Unexpected error during Discord startup: " + e.getMessage(), String.valueOf(getClass()), e);
        }

        running = false;
        return false;
    }

    public void stop() {
        running = false;
        JDA jda = jdaRef.getAndSet(null);
        if (jda != null) {
            logger.info("🛑 Shutting down Discord bot...", String.valueOf(getClass()));
            shutdownQuietly(jda);
        }
        // Give MessagingService a chance to clean up component handlers, etc.
        try {
            messagingService.shutdown();
        } catch (Exception e) {
            logger.warn("⚠️ MessagingService shutdown encountered an issue: " + e.getMessage(), String.valueOf(getClass()));
        }
    }

    /**
     * Update credentials, persist to YAML, and attempt a restart.
     * Returns true if restart (re)connected successfully.
     */
    public boolean restartWithToken(String newToken, String newBotId, String newAdminId) {
        stop();

        this.token = newToken;
        this.botId = newBotId;
        this.adminId = newAdminId;

        // Persist under consistent keys
        yamlService.set("discord.token", newToken);
        yamlService.set("discord.botId", newBotId);
        yamlService.set("discord.adminId", newAdminId);
        yamlService.save();

        logger.info("💾 Saved Discord credentials and admin ID to config.yaml", String.valueOf(getClass()));
        return start();
    }

    @PreDestroy
    public void onShutdown() {
        stop();
    }

    public JDA getJDA() {
        return jdaRef.get();
    }

    // ===== helpers =====

    private void shutdownQuietly(JDA jda) {
        try {
            jda.shutdownNow();
            // tiny grace to let threads wind down
            jda.awaitStatus(JDA.Status.SHUTDOWN);
        } catch (Exception ignored) {
        }
    }
}
