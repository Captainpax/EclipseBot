package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.discord.chains.MasterGuildSetup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MessagingService: thin convenience layer for DM’ing, kicking off the setup wizard,
 * and (optionally) relaying raw interactions to the Bytes bridge.
 */
@SuppressWarnings("unused")
@Service
public class MessagingService {

    private final LoggerService logger;
    private final YamlService yamlService;
    private final AtomicReference<JDA> jdaRef;
    private final Bytes bytes;                       // Paged-chain runtime (handles edit-in-place)
    private final MasterGuildSetup masterGuildSetup; // The Setup wizard chain

    public MessagingService(
            LoggerService logger,
            YamlService yamlService,
            AtomicReference<JDA> jdaRef,
            Bytes bytes,
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
    }

    @PreDestroy
    public void shutdown() {
        logger.info("🛑 MessagingService shutting down", getClass().getName());
    }

    // ========================= Public API =========================

    /** Reads admin ID from YAML and greets on startup (then launches the setup wizard if eligible). */
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

        var options = getEligibleGuildOptions(jda, adminId);
        logger.info("Eligible guild options found=" + options.size(), getClass().getName());

        if (options.isEmpty()) {
            dmUser(adminId,
                    """
                    ⚠️ I couldn't find any servers where you are **Owner** or have **Administrator** while I'm present.
                    • Invite me to your server, or
                    • Ensure you have Admin perms where I'm installed,
                    then run `/setup` or restart me."""
            );
            return;
        }

        try {
            // Kick off the paged wizard (MasterGuildSetup will call bytes.startDmPagedChain internally)
            masterGuildSetup.start(adminId, options);
        } catch (Exception e) {
            logger.error("Failed to start MasterGuildSetup: " + e.getMessage(), getClass().getName(), e);
            dmUser(adminId, "❌ Failed to start setup wizard. Check logs and try `/setup` again.");
        }
    }

    /** Optional passthrough if you route select-interactions here: delegates to Bytes. */
    public void handleDropdownInteraction(StringSelectInteractionEvent event) {
        try {
            logger.info("[MessagingService] handleDropdownInteraction id=" + event.getComponentId() +
                    " user=" + event.getUser().getId() +
                    " values=" + event.getValues(), getClass().getName());
            bytes.handleDropdownInteraction(event); // bytes defers, routes, and re-renders in-place
        } catch (Exception e) {
            logger.error("Dropdown interaction handling failed: " + e.getMessage(), getClass().getName(), e);
        }
    }

    /** Simple plain text DM via Bytes helper. */
    public void dmUser(String userId, String content) {
        logger.info("📨 DM to user [" + userId + "]", getClass().getName());
        try {
            bytes.sendPrivateMessage(userId, content);
        } catch (Exception e) {
            logger.error("Failed to DM user " + userId + ": " + e.getMessage(), getClass().getName(), e);
        }
    }

    /**
     * Ad‑hoc DM with a standalone dropdown that supports label/value pairs (outside the paged system).
     * Prefer using a PagedChain where possible.
     */
    public void dmUserWithDropdown(String userId,
                                   String message,
                                   String dropdownId,
                                   List<SelectOption> options) {
        logger.info("📨 DM (dropdown) to user [" + userId + "] id=" + dropdownId +
                " options=" + (options != null ? options.size() : 0), getClass().getName());

        JDA jda = jdaRef.get();
        if (jda == null) {
            logger.warn("❌ JDA not initialized — cannot send dropdown.", getClass().getName());
            return;
        }
        if (options == null || options.isEmpty()) {
            logger.warn("❌ No options provided for dropdown " + dropdownId, getClass().getName());
            return;
        }

        StringSelectMenu menu = StringSelectMenu.create(dropdownId)
                .addOptions(options)
                .build();

        jda.retrieveUserById(userId).queue(user ->
                user.openPrivateChannel().queue(channel ->
                        channel.sendMessage(message)
                                .setComponents(ActionRow.of(menu))
                                .queue(
                                        ok -> logger.info("✅ Sent dropdown to " + user.getAsTag(), getClass().getName()),
                                        err -> logger.error("❌ Failed to send dropdown: " + err.getMessage(), getClass().getName())
                                )
                )
        );
    }

    // ========================= Internal helpers =========================

    /**
     * Build a list of guild options (label=name, value=id) where the given admin is
     * the **owner** or has **ADMINISTRATOR** permissions — AND the bot is in that guild.
     * We try to avoid heavy caching:
     *  1) Include guilds where ownerId == adminId (cheap check).
     *  2) Attempt a REST fetch for the member to check ADMINISTRATOR (requires GUILD_MEMBERS intent).
     */
    private List<SelectOption> getEligibleGuildOptions(JDA jda, String adminId) {
        List<SelectOption> out = new ArrayList<>();

        jda.getGuilds().forEach(guild -> {
            // Owner shortcut
            if (adminId.equals(guild.getOwnerId())) {
                out.add(SelectOption.of(guild.getName(), guild.getId()));
                return;
            }

            // Best-effort member fetch (blocking once at startup is OK)
            try {
                Member m = guild.retrieveMemberById(adminId).complete();
                if (m != null && (m.isOwner() || m.hasPermission(Permission.ADMINISTRATOR))) {
                    out.add(SelectOption.of(guild.getName(), guild.getId()));
                }
            } catch (Exception ignored) {
                // If we can't fetch (no perms/intent/user missing), just skip this guild
            }
        });

        return out;
    }

    /** Optional: simple logger for regular messages. */
    public void trackIncomingMessage(MessageReceivedEvent event) {
        var msg = event.getMessage();
        if (msg.getAuthor().isBot()) return;

        final String content = msg.getContentDisplay();
        final String channelType = event.isFromGuild() ? "Guild" : "Private";
        final String channelName = event.isFromGuild()
                ? msg.getGuild().getName() + " / #" + msg.getChannel().asGuildMessageChannel().getName()
                : "DM";

        User author = msg.getAuthor();
        final String summary = String.format("[%s] <%s | %s> @ %s → %s",
                channelType,
                author.getName(),
                author.getId(),
                channelName,
                content
        );
        logger.info(summary, getClass().getName());
    }

    /** Convenience send-to-channel if needed elsewhere. */
    public void sendToChannel(MessageChannel channel, String content) {
        if (channel == null) return;
        channel.sendMessage(content).queue();
    }
}
