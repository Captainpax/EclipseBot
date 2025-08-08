package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
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

    // ✅ All JDA listeners discovered via Spring (e.g., DebugListener, your command listeners, etc.)
    private final List<EventListener> jdaListeners;

    private String token;
    private String botId;
    private String adminId;

    @Getter
    private volatile boolean running = false;

    public DiscordService(
            LoggerService logger,
            YamlService yamlService,
            MessagingService messagingService,
            AtomicReference<JDA> jdaRef,
            List<EventListener> jdaListeners // Spring injects all beans implementing EventListener
    ) {
        this.logger = logger;
        this.yamlService = yamlService;
        this.messagingService = messagingService;
        this.jdaRef = jdaRef;
        this.jdaListeners = jdaListeners;

        // Load initial credentials from YAML
        reloadCredsFromYaml();
    }

    private void reloadCredsFromYaml() {
        this.token = yamlService.getString("discord.token");
        this.botId = yamlService.getString("discord.botId");
        this.adminId = yamlService.getString("discord.adminId"); // keep key consistent

        if (token != null && !token.isBlank()) {
            logger.info("🔍 Loaded Discord token from YAML.", getClass().getName());
        } else {
            logger.warn("⚠️ No Discord token found in YAML.", getClass().getName());
        }
    }

    /**
     * Attempts to start JDA. Returns true if the bot is fully connected (awaitReady), false otherwise.
     */
    public boolean start() {
        // Validate creds (and treat placeholders as invalid)
        if (token == null || token.isBlank() || "your-token-here".equalsIgnoreCase(token)
                || botId == null || botId.isBlank() || "your-bot-id-here".equalsIgnoreCase(botId)) {
            logger.warn("⚠️ Discord token or botId missing/placeholder — continuing in setup mode.", getClass().getName());
            running = false;
            return false;
        }

        try {
            logger.info("🤖 Attempting Discord login with bot ID: " + botId, getClass().getName());

            EnumSet<GatewayIntent> intents = EnumSet.of(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.DIRECT_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT // keep only if needed and enabled in Dev Portal
            );

            JDABuilder builder = JDABuilder.create(token, intents)
                    .disableCache(
                            CacheFlag.ACTIVITY,
                            CacheFlag.VOICE_STATE,
                            CacheFlag.EMOJI,
                            CacheFlag.STICKER,
                            CacheFlag.CLIENT_STATUS,
                            CacheFlag.SCHEDULED_EVENTS
                    )
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .setActivity(Activity.watching("for /setup requests"));

            // 👉 Register every Spring-managed EventListener (ListenerAdapter implements EventListener)
            if (jdaListeners != null && !jdaListeners.isEmpty()) {
                logger.info("🔗 Registering " + jdaListeners.size() + " JDA listener(s).", getClass().getName());
                builder.addEventListeners(jdaListeners.toArray());
            } else {
                logger.warn("⚠️ No JDA listeners found. Did you annotate them with @Component?", getClass().getName());
            }

            // Start connecting
            JDA jda = builder.build();

            // Wait until ready
            try {
                jda.awaitReady();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.error("❌ Startup interrupted while connecting to Discord.", getClass().getName(), ie);
                shutdownQuietly(jda);
                running = false;
                return false;
            } catch (Exception e) {
                logger.error("🔥 Error while waiting for Discord readiness: " + e.getMessage(), getClass().getName(), e);
                shutdownQuietly(jda);
                running = false;
                return false;
            }

            // Success
            jdaRef.set(jda);
            running = true;

            logger.success("✅ Discord bot is online as " + jda.getSelfUser().getAsTag(), getClass().getName());

            // Optional: greet configured admin on successful connect
            if (adminId != null && !adminId.isBlank()) {
                try {
                    messagingService.greetAdminOnStartup(adminId);
                } catch (Exception e) {
                    logger.warn("⚠️ Could not greet admin on startup: " + e.getMessage(), getClass().getName());
                }
            }

            return true;

        } catch (InvalidTokenException e) {
            logger.error("❌ Invalid Discord token — startup failed.", getClass().getName(), e);
        } catch (Exception e) {
            logger.error("🔥 Unexpected error during Discord startup: " + e.getMessage(), getClass().getName(), e);
        }

        running = false;
        return false;
    }

    public void stop() {
        running = false;
        JDA jda = jdaRef.getAndSet(null);
        if (jda != null) {
            logger.info("🛑 Shutting down Discord bot...", getClass().getName());
            shutdownQuietly(jda);
        }
        try {
            messagingService.shutdown();
        } catch (Exception e) {
            logger.warn("⚠️ MessagingService shutdown encountered an issue: " + e.getMessage(), getClass().getName());
        }
    }

    /**
     * Update credentials, persist to YAML, and attempt a restart.
     * Returns true if restart (re)connected successfully.
     */
    public void restartWithToken(String newToken, String newBotId, String newAdminId) {
        stop();

        this.token = newToken;
        this.botId = newBotId;
        this.adminId = newAdminId;

        yamlService.set("discord.token", newToken);
        yamlService.set("discord.botId", newBotId);
        yamlService.set("discord.adminId", newAdminId);
        yamlService.save();

        logger.info("💾 Saved Discord credentials and admin ID to config.yaml", getClass().getName());
        start();
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
            jda.awaitStatus(JDA.Status.SHUTDOWN);
        } catch (Exception ignored) {
        }
    }
}
