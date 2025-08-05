package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.discord.builders.MessageBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
public class MessagingService {

    private final LoggerService logger;
    private final MessageBuilder messageBuilder;
    private final AtomicReference<JDA> jdaRef;

    public MessagingService(
            LoggerService logger,
            AtomicReference<JDA> jdaRef,
            MessageBuilder messageBuilder,
            YamlService yamlService
    ) {
        this.logger = logger;
        this.messageBuilder = messageBuilder;
        this.jdaRef = jdaRef;
    }

    public void trackIncomingMessage(@NotNull MessageReceivedEvent event) {
        Message message = event.getMessage();
        User author = event.getAuthor();

        if (author.isBot()) return;

        String content = message.getContentDisplay();
        String channelType = event.isFromGuild() ? "Guild" : "Private";
        String channelName = event.isFromGuild()
                ? message.getGuild().getName() + " / #" + message.getChannel().asGuildMessageChannel().getName()
                : "DM";

        String summary = String.format("[%s] <%s#%s | %s> @ %s → %s",
                channelType,
                author.getName(),
                author.getDiscriminator(),
                author.getId(),
                channelName,
                content
        );

        logger.info(summary, String.valueOf(getClass()));
    }

    public void greetAdminOnStartup(String adminId) {
        if (adminId == null || adminId.isBlank()) {
            logger.warn("Admin ID not set — skipping startup greeting.", String.valueOf(getClass()));
            return;
        }

        JDA jda = jdaRef.get();
        if (jda == null) {
            logger.error("❌ JDA not initialized — cannot send startup DM.", String.valueOf(getClass()));
            return;
        }

        messageBuilder.clearButtons();

        messageBuilder.withDropdown("select_guild", "Select a Guild to Setup", getEligibleGuildOptions(jda, adminId), event -> {
            String guildId = event.selected();
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                event.reply("❌ Guild not found or bot is no longer in that server.");
                return;
            }

            String stats = "📊 Guild Info for **" + guild.getName() + "**\n" +
                    "• ID: ``" + guild.getId() + "``\n" +
                    "• Owner: ``" + guild.getOwnerId() + "``\n" +
                    "• Member Count: " + guild.getMemberCount() + "\n" +
                    "• Channels: " + guild.getChannels().size() + "\n" +
                    "• Roles: " + guild.getRoles().size();

            event.reply(stats);
        });

        String message = messageBuilder.format(
                "👋 EclipseBot Started",
                """
                Hello! I'm **EclipseBot**, your assistant for managing Archipelago servers on Discord.

                Use the dropdown below to choose a server where you are an **admin** or **owner** and EclipseBot is present.
                """
        );

        logMessageDetails(adminId, message);
        messageBuilder.sendPrivateMessage(adminId, message);
    }

    private List<SelectOption> getEligibleGuildOptions(JDA jda, String adminId) {
        return jda.getGuilds().stream()
                .filter(guild -> {
                    Member member = guild.getMemberById(adminId);
                    return member != null && (member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR));
                })
                .map(guild -> SelectOption.of(guild.getName(), guild.getId()))
                .toList();
    }

    public void dmUser(String userId, String content) {
        logger.info("📨 DM to user [" + userId + "]", String.valueOf(getClass()));
        logMessageDetails(userId, content);
        messageBuilder.sendPrivateMessage(userId, content);
    }

    public String format(String header, String body) {
        return messageBuilder.format(header, body);
    }

    private void logMessageDetails(String target, String message) {
        List<String> buttonIds = messageBuilder.getHandlers().stream()
                .map(handler -> handler.id())
                .toList();

        String attachments = buttonIds.isEmpty()
                ? "none"
                : "buttons=" + buttonIds;

        logger.info("📦 Outgoing message [DM] → " + target, String.valueOf(getClass()));
        logger.info("📝 Content:\n" + message, String.valueOf(getClass()));
        logger.info("📎 Attachments: " + attachments, String.valueOf(getClass()));
    }

    @PreDestroy
    public void shutdown() {
        logger.info("🛑 MessagingService shutting down", String.valueOf(getClass()));
    }

    @PostConstruct
    public void onInit() {
        logger.info("✅ MessagingService initialized", String.valueOf(getClass()));
    }
}
