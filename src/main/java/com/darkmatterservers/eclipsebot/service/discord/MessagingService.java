package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.EclipseBytes;
import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.discord.chains.MasterGuildSetup;
import com.darkmatterservers.router.ComponentHandler;
import com.darkmatterservers.router.InteractionRouter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MessagingService {

    private final LoggerService logger;
    private final YamlService yamlService;
    private final AtomicReference<JDA> jdaRef;
    private final EclipseBytes bytes;              // <- using the bean from AppBeansConfig
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
        logger.info("✅ MessagingService initialized", getClass().getName());
        try {
            bytes.init(); // safe no-op if already initialized
        } catch (Throwable t) {
            logger.warn("⚠️ EclipseBytes.init() failed: " + t.getMessage(), getClass().getName());
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("🛑 MessagingService shutting down", getClass().getName());
        try {
            bytes.shutdown(); // cleans router
        } catch (Throwable ignored) {}
    }

    // ========================= Public API =========================

    /** Convenience: Reads admin ID from YAML and greets on startup. */
    public void greetAdminOnStartup() {
        String adminId = yamlService.getString("discord.adminId");
        if (adminId == null || adminId.isBlank()) {
            logger.warn("Admin ID not set (discord.adminId) — skipping startup greeting.", getClass().getName());
            return;
        }
        greetAdminOnStartup(adminId);
    }

    /** Sends a startup greeting to the admin and launches the setup chain if eligible. */
    public void greetAdminOnStartup(String adminId) {
        if (adminId == null || adminId.isBlank()) {
            logger.warn("Admin ID not provided — skipping startup greeting.", getClass().getName());
            return;
        }

        JDA jda = jdaRef.get();
        if (jda == null) {
            logger.warn("JDA not initialized — cannot send startup DM yet.", getClass().getName());
            return;
        }

        List<SelectOption> guildOptions = getEligibleGuildOptions(jda, adminId);
        logger.info("Eligible guild options found=" + guildOptions.size(), getClass().getName());

        if (guildOptions.isEmpty()) {
            dmUser(adminId,
                    "⚠️ I can't find any servers where you are **Owner** or have **Administrator** permissions while I'm present.\n" +
                            "• Invite me to your server, or\n" +
                            "• Ensure you have Admin perms where I'm installed.\n" +
                            "Then run `/setup` or restart me.");
            return;
        }

        // Start the chain with a proper label/value dropdown (guildName/guildId)
        masterGuildSetup.start(adminId, guildOptions);
    }

    /** Forwards select-menu interactions into the router via EclipseBytes helper (acks first). */
    public void handleDropdownInteraction(StringSelectInteractionEvent event) {
        try {
            logger.info("[MessagingService] handleDropdownInteraction id=" + event.getComponentId() +
                    " user=" + event.getUser().getId() +
                    " values=" + event.getValues(), getClass().getName());
            bytes.handleDropdownInteraction(event); // does deferEdit + InteractionRouter.handle(...)
        } catch (Exception e) {
            logger.error("Dropdown interaction handling failed: " + e.getMessage(), getClass().getName(), e);
        }
    }

    /** Sends a plain text DM via EclipseBytes. */
    public void dmUser(String userId, String content) {
        logger.info("📨 DM to user [" + userId + "]", getClass().getName());
        try {
            bytes.sendPrivateMessage(userId, content);
        } catch (Exception e) {
            logger.error("Failed to DM user " + userId + ": " + e.getMessage(), getClass().getName(), e);
        }
    }

    /**
     * Sends a DM with a dropdown that supports separate label/value using raw JDA,
     * while still registering the handler with InteractionRouter.
     */
    public void dmUserWithDropdown(String userId,
                                   String message,
                                   String dropdownId,
                                   List<SelectOption> options,
                                   ComponentHandler handler) {
        logger.info("📨 DM (dropdown) to user [" + userId + "] id=" + dropdownId +
                " options=" + (options != null ? options.size() : 0), getClass().getName());

        JDA jda = jdaRef.get();
        if (jda == null) {
            logger.warn("❌ JDA not initialized — cannot send dropdown.", getClass().getName());
            return;
        }

        // Register the handler for this dropdown id
        com.darkmatterservers.router.InteractionRouter.register(dropdownId, handler);

        // Build a label/value menu
        StringSelectMenu menu = StringSelectMenu.create(dropdownId)
                .addOptions(options)
                .build();

        jda.retrieveUserById(userId).queue(user ->
                user.openPrivateChannel().queue(channel -> {
                    channel.sendMessage(message)
                            .setComponents(ActionRow.of(menu))
                            .queue(
                                    ok -> logger.info("✅ Sent dropdown to " + user.getAsTag(), getClass().getName()),
                                    err -> logger.error("❌ Failed to send dropdown: " + err.getMessage(), getClass().getName())
                            );
                })
        );
    }

    // ========================= Internal helpers =========================

    /** Filters the guilds where the admin has Owner or Administrator permissions. */
    private List<SelectOption> getEligibleGuildOptions(JDA jda, String adminId) {
        return jda.getGuilds().stream()
                .map(guild -> guild.getMemberById(adminId))
                .filter(member -> member != null && (member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR)))
                .map(Member::getGuild)
                .map(guild -> SelectOption.of(guild.getName(), guild.getId())) // label=name, value=id
                .toList();
    }

    /** Optional: track regular messages for logging. */
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

        logger.info(summary, getClass().getName());
    }
}
