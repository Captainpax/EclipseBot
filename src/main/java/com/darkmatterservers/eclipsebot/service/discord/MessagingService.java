package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.discord.builders.MessageBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MessagingService provides centralized control over all incoming/outgoing Discord messages,
 * including logging, formatting, DM handling, and future analytics or moderation capabilities.
 *
 * Responsibilities:
 * ✅ Logs and tracks all messages
 * ✅ Handles private and public message context
 * ✅ Sends standardized formatted messages
 * ✅ Delegates to MessageBuilder for interactive UI (buttons)
 */
@Service
public class MessagingService {

    private final LoggerService logger;
    private final MessageBuilder messageBuilder;

    public MessagingService(
            LoggerService logger,
            AtomicReference<JDA> jdaRef,
            MessageBuilder messageBuilder,
            YamlService yamlService
    ) {
        this.logger = logger;
        this.messageBuilder = messageBuilder;
    }

    /**
     * Logs all incoming messages from users for moderation or debugging purposes.
     */
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

    /**
     * Greets the admin user with a DM that includes buttons.
     */
    public void greetAdminOnStartup(String adminId) {
        if (adminId == null || adminId.isBlank()) {
            logger.warn("Admin ID not set — skipping startup greeting.", String.valueOf(getClass()));
            return;
        }

        messageBuilder.clearButtons(); // Clears buttons (not handlers)

        messageBuilder.with("setup_wizard", "Setup Wizard", event -> {
            String user = event.raw().getUser().getAsTag();
            logger.info("🧪 Setup Wizard button triggered by " + user, String.valueOf(getClass()));
            event.reply("🧙 Coming soon: Setup Wizard flow will guide you through configuration.");
        });

        String message = messageBuilder.format(
                "👋 EclipseBot Started",
                """
                Hello! I'm **EclipseBot**, your assistant for managing Archipelago servers on Discord.
    
                Click the **Setup Wizard** button below to begin configuring roles, channels, and permissions.
                """
        );

        logMessageDetails(adminId, message);
        messageBuilder.sendPrivateMessage(adminId, message);
    }

    /**
     * Sends a message to a user and logs detailed delivery info.
     */
    public void dmUser(String userId, String content) {
        logger.info("📨 DM to user [" + userId + "]", String.valueOf(getClass()));
        logMessageDetails(userId, content);
        messageBuilder.sendPrivateMessage(userId, content);
    }

    /**
     * Formats a standard message (e.g., for embeds or multi-line replies).
     */
    public String format(String header, String body) {
        return messageBuilder.format(header, body);
    }

    /**
     * Prints detailed log of outgoing message, including buttons and content.
     */
    private void logMessageDetails(String target, String message) {
        List<String> buttonIds = messageBuilder.getHandlers().stream()
                .map(MessageBuilder.ButtonHandler::id)
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
