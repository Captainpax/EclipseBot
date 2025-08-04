package com.darkmatterservers.eclipsebot.service.discord.listeners;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class MessageListener extends ListenerAdapter {

    private final LoggerService logger;

    public MessageListener(LoggerService logger) {
        this.logger = logger;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        Message message = event.getMessage();
        User author = event.getAuthor();

        // Ignore bot's own messages
        if (author.isBot()) return;

        String content = message.getContentDisplay();
        String channelType = event.isFromGuild() ? "Guild" : "Private";
        String channelName = event.isFromGuild()
                ? message.getGuild().getName() + " / #" + message.getChannel().getName()
                : "Direct Message";

        String msgType = message.isWebhookMessage() ? "Webhook"
                : message.isEphemeral() ? "Ephemeral"
                : message.getType().name();

        String tag = String.format("[%s] (%s) <%s#%s | %s> → %s",
                channelType,
                msgType,
                author.getName(),
                author.getDiscriminator(),
                author.getId(),
                content
        );

        logger.info("MessageListener", tag);
    }
}
