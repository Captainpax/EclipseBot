package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.discord.chains.MasterGuildSetup;
import com.darkmatterservers.EclipseBytes;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MessagingService {

    private final LoggerService logger;
    private final YamlService yamlService;
    private final AtomicReference<JDA> jdaRef;
    private final EclipseBytes bytes; // Bridge to EclipseBytes (MessageBuilder/DropdownBuilder/etc.)
    private final MasterGuildSetup masterGuildSetup;

    public MessagingService(
            LoggerService logger,
            YamlService yamlService,
            AtomicReference<JDA> jdaRef,
            EclipseBytes bytes,
            MasterGuildSetup masterGuildSetup
    ) {
        this.logger = logger;
        this.yamlService = yamlService;
        this.jdaRef = jdaRef;
        this.bytes = bytes;
        this.masterGuildSetup = masterGuildSetup;
    }

    @PostConstruct
    public void onInit() {
        logger.info("✅ MessagingService initialized", String.valueOf(getClass()));
        try {
            bytes.init();
        } catch (Throwable t) {
            logger.warn("⚠️ EclipseBytes.init() failed: " + t.getMessage(), String.valueOf(getClass()));
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("🛑 MessagingService shutting down", String.valueOf(getClass()));
        try {
            bytes.shutdown();
        } catch (Throwable ignored) {
        }
    }

    // =========================
    // Public API
    // =========================

    /**
     * Convenience: Reads admin ID from YAML and greets on startup.
     * Safe to call even if Discord isn't connected; it will just log and return.
     */
    public void greetAdminOnStartup() {
        String adminId = yamlService.getString("discord.adminId");
        if (adminId == null || adminId.isBlank()) {
            logger.warn("Admin ID not set (discord.adminId) — skipping startup greeting.", String.valueOf(getClass()));
            return;
        }
        greetAdminOnStartup(adminId);
    }

    /**
     * Sends a startup greeting to the admin and launches the setup chain if eligible.
     */
    public void greetAdminOnStartup(String adminId) {
        if (adminId == null || adminId.isBlank()) {
            logger.warn("Admin ID not provided — skipping startup greeting.", String.valueOf(getClass()));
            return;
        }

        JDA jda = jdaRef.get();
        if (jda == null) {
            logger.warn("JDA not initialized — cannot send startup DM yet.", String.valueOf(getClass()));
            return;
        }

        List<SelectOption> guildOptions = getEligibleGuildOptions(jda, adminId);
        if (guildOptions.isEmpty()) {
            dmUser(adminId,
                    "⚠️ I can't find any servers where you are **Owner** or have **Administrator** permissions while I'm present.\n" +
                            "• Invite me to your server, or\n" +
                            "• Ensure you have Admin perms where I'm installed.\n" +
                            "Then run `/setup` or restart me.");
            return;
        }

        // Launch the MasterGuildSetup chain (uses Bytes UI under the hood)
        masterGuildSetup.start(adminId, guildOptions);
    }

    /**
     * Tracks incoming messages and logs them to the console. Skips bot messages.
     */
    public void trackIncomingMessage(MessageReceivedEvent event) {
        Message message = event.getMessage();
        if (message.getAuthor().isBot()) return;

        final String content = message.getContentDisplay();
        final String channelType = event.isFromGuild() ? "Guild" : "Private";
        final String channelName = event.isFromGuild()
                ? message.getGuild().getName() + " / #" + message.getChannel().asGuildMessageChannel().getName()
                : "DM";

        final String summary = String.format("[%s] <%s#%s | %s> @ %s → %s",
                channelType,
                message.getAuthor().getName(),
                message.getAuthor().getDiscriminator(),
                message.getAuthor().getId(),
                channelName,
                content
        );

        logger.info(summary, String.valueOf(getClass()));
    }

    /**
     * Pass through dropdown interactions to EclipseBytes' router.
     * Call this from your JDA listener: on StringSelectInteractionEvent → messagingService.handleDropdownInteraction(event)
     */
    public void handleDropdownInteraction(StringSelectInteractionEvent event) {
        try {
            bytes.handleDropdownInteraction(event);
        } catch (Exception e) {
            logger.error("Dropdown interaction handling failed: " + e.getMessage(), String.valueOf(getClass()), e);
        }
    }

    /**
     * Sends a plain text DM to the user via EclipseBytes bridge.
     */
    public void dmUser(String userId, String content) {
        logger.info("📨 DM to user [" + userId + "]", String.valueOf(getClass()));
        logMessageDetails(userId, content, null);
        try {
            bytes.sendPrivateMessage(userId, content);
        } catch (Exception e) {
            logger.error("Failed to DM user " + userId + ": " + e.getMessage(), String.valueOf(getClass()), e);
        }
    }

    /**
     * Sends a DM with a dropdown via EclipseBytes bridge.
     */
    public void dmUserWithDropdown(String userId, String message, String dropdownId, List<String> options,
                                   com.darkmatterservers.router.ComponentHandler handler) {
        logger.info("📨 DM (dropdown) to user [" + userId + "] id=" + dropdownId, String.valueOf(getClass()));
        try {
            bytes.sendPrivateDropdown(userId, message, dropdownId, options, handler);
        } catch (Exception e) {
            logger.error("Failed to DM dropdown to user " + userId + ": " + e.getMessage(), String.valueOf(getClass()), e);
        }
    }

    // =========================
    // Internal helpers
    // =========================

    /**
     * Filters the guilds where the admin has Owner or Administrator permissions.
     */
    private List<SelectOption> getEligibleGuildOptions(JDA jda, String adminId) {
        return jda.getGuilds().stream()
                .map(guild -> guild.getMemberById(adminId))
                .filter(member -> member != null && (member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR)))
                .map(Member::getGuild)
                .map(guild -> SelectOption.of(guild.getName(), guild.getId()))
                .toList();
    }

    /**
     * Logs the outgoing message and any attached dropdown metadata.
     */
    private void logMessageDetails(String target, String message, List<SelectOption> options) {
        logger.info("📦 Outgoing message [DM] → " + target, String.valueOf(getClass()));
        logger.info("📝 Content:\n" + message, String.valueOf(getClass()));
        if (options != null && !options.isEmpty()) {
            logger.info("📎 Attachments: dropdown=" + options.size(), String.valueOf(getClass()));
        } else {
            logger.info("📎 Attachments: none", String.valueOf(getClass()));
        }
    }
}
