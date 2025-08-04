package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.discord.builders.*;
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
 * Handles all Discord bot lifecycle and interactions.
 */
@Service
public class DiscordService {

    private final LoggerService logger;
    private final YamlService yamlService;
    private final MessageBuilder messageBuilder;
    private final AtomicReference<JDA> jdaRef;

    private String token;
    private String botId;
    private String adminId;

    private JDA jda;
    @Getter
    private boolean running = false;

    public DiscordService(
            LoggerService logger,
            YamlService yamlService,
            MessageBuilder messageBuilder,
            AtomicReference<JDA> jdaRef
    ) {
        this.logger = logger;
        this.yamlService = yamlService;
        this.messageBuilder = messageBuilder;
        this.jdaRef = jdaRef;

        this.token = yamlService.getString("discord.token");
        this.botId = yamlService.getString("discord.botId");
        this.adminId = yamlService.getString("adminId");

        if (token != null && !token.isBlank()) {
            logger.info("🔍 Loaded Discord token from YAML.", String.valueOf(getClass()));
        }
    }

    /**
     * Starts the Discord bot if the token is valid.
     */
    public void start() {
        if (token == null || token.isBlank()) {
            logger.warn("⚠️ DiscordService start skipped — token not set", String.valueOf(getClass()));
            return;
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

            jdaRef.set(jda); // Provide live JDA to other services
            logger.setJDA(jda); // Enable logger Discord mirroring
            running = true;

            logger.success("✅ Discord bot is online as " + jda.getSelfUser().getAsTag(), String.valueOf(getClass()));

            // DM the admin
            if (adminId != null && !adminId.isBlank()) {
                String msg = messageBuilder.format(
                        "👋 EclipseBot Started",
                        "The bot is up and running and ready to serve.\nUse `/setup` in a server to configure roles and channels."
                );
                messageBuilder.sendPrivateMessage(adminId, msg);
            }

        } catch (InvalidTokenException e) {
            logger.error("❌ Invalid Discord token — startup failed.", String.valueOf(getClass()));
        } catch (InterruptedException e) {
            logger.error("❌ Startup interrupted while connecting to Discord.", String.valueOf(getClass()));
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("🔥 Unexpected error during Discord startup: " + e.getMessage(), String.valueOf(getClass()));
        }
    }

    /**
     * Stops the Discord bot cleanly.
     */
    public void stop() {
        if (running && jda != null) {
            logger.info("🛑 Shutting down Discord bot...", String.valueOf(getClass()));
            jda.shutdownNow();
            running = false;
        }
    }

    /**
     * Restarts the bot with new credentials and writes them to YAML.
     */
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
