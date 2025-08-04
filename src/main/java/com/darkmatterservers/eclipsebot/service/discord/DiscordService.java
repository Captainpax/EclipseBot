package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.discord.builders.MessageBuilder;
import com.darkmatterservers.eclipsebot.service.discord.listeners.ButtonListener;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
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
    private final ButtonListener buttonListener;

    private String token;
    private String botId;
    private String adminId;

    private JDA jda;

    @Getter
    private boolean running = false;

    public DiscordService(
            LoggerService logger,
            YamlService yamlService,
            MessagingService messagingService,
            MessageBuilder messageBuilder,
            AtomicReference<JDA> jdaRef,
            ButtonListener buttonListener
    ) {
        this.logger = logger;
        this.yamlService = yamlService;
        this.messagingService = messagingService;
        this.jdaRef = jdaRef;
        this.buttonListener = buttonListener;

        this.token = yamlService.getString("discord.token");
        this.botId = yamlService.getString("discord.botId");
        this.adminId = yamlService.getString("adminId");

        if (token != null && !token.isBlank()) {
            logger.info("🔍 Loaded Discord token from YAML.", String.valueOf(getClass()));
        }
    }

    public JDA start() {
        if (token == null || token.isBlank()) {
            logger.warn("⚠️ DiscordService start skipped — token not set", String.valueOf(getClass()));
            return null;
        }

        try {
            logger.info("🤖 Attempting Discord login with bot ID: " + botId, String.valueOf(getClass()));

            jda = JDABuilder.create(token, EnumSet.of(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS
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
                    .setActivity(Activity.watching("for /setup requests"))
                    .build()
                    .awaitReady();

            jdaRef.set(jda);         // Global access
            logger.setJDA(jda);      // Log to Discord if enabled
            running = true;

            logger.success("✅ Discord bot is online as " + jda.getSelfUser().getAsTag(), String.valueOf(getClass()));

            jda.addEventListener(buttonListener);
            logger.info("✅ ButtonListener registered with JDA", String.valueOf(getClass()));

            if (adminId != null && !adminId.isBlank()) {
                messagingService.greetAdminOnStartup(adminId);
            }

            return jda;

        } catch (InvalidTokenException e) {
            logger.error("❌ Invalid Discord token — startup failed.", String.valueOf(getClass()));
        } catch (InterruptedException e) {
            logger.error("❌ Startup interrupted while connecting to Discord.", String.valueOf(getClass()));
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("🔥 Unexpected error during Discord startup: " + e.getMessage(), String.valueOf(getClass()));
        }

        return null;
    }

    public void stop() {
        if (running && jda != null) {
            logger.info("🛑 Shutting down Discord bot...", String.valueOf(getClass()));
            jda.shutdownNow();
            running = false;
        }
    }

    public void restartWithToken(String token, String botId, String adminId) {
        stop();

        this.token = token;
        this.botId = botId;
        this.adminId = adminId;

        yamlService.set("discord.token", token);
        yamlService.set("discord.botId", botId);
        yamlService.set("adminId", adminId);
        yamlService.save();

        logger.info("💾 Saved Discord credentials and admin ID to config.yaml", String.valueOf(getClass()));
        start();
    }

    @PreDestroy
    public void onShutdown() {
        stop();
    }

    public JDA getJDA() {
        return jda;
    }
}
