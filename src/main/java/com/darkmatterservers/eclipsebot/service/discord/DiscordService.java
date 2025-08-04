package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

/**
 * Handles all Discord bot lifecycle and interactions.
 */
@Service
public class DiscordService {

    private final LoggerService logger;
    private final YamlService yamlService;

    private String token;
    private String botId;
    private String adminId;

    private JDA jda;
    private boolean running = false;

    public DiscordService(LoggerService logger, YamlService yamlService) {
        this.logger = logger;
        this.yamlService = yamlService;

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
                    .setActivity(Activity.watching("for /setup requests"))
                    .build()
                    .awaitReady();

            logger.setJDA(jda); // enable logger's Discord mirroring
            running = true;

            logger.success("✅ Discord bot is online as " + jda.getSelfUser().getAsTag(), String.valueOf(getClass()));

            // DM the admin on startup
            if (adminId != null && !adminId.isBlank()) {
                jda.retrieveUserById(adminId).queue(
                        user -> sendAdminDM(user),
                        error -> logger.warn("⚠️ Failed to find admin user (ID: " + adminId + "): " + error.getMessage(), String.valueOf(getClass()))
                );
            }

        } catch (InvalidTokenException e) {
            logger.error("❌ Invalid Discord token — startup failed.", String.valueOf(getClass()), e);
        } catch (InterruptedException e) {
            logger.error("❌ Startup interrupted while connecting to Discord.", String.valueOf(getClass()), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("🔥 Unexpected error during Discord startup: " + e.getMessage(), String.valueOf(getClass()), e);
        }
    }

    private void sendAdminDM(User user) {
        user.openPrivateChannel().queue(
                channel -> channel.sendMessage("👋 I'm up and running! Ready to serve.").queue(),
                error -> logger.warn("⚠️ Failed to open DM with admin: " + error.getMessage(), String.valueOf(getClass()))
        );
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

    public String getToken() {
        return token;
    }

    public String getBotId() {
        return botId;
    }

    public String getAdminId() {
        return adminId;
    }

    public boolean isRunning() {
        return running;
    }

    public JDA getJDA() {
        return jda;
    }
}
