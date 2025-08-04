package com.darkmatterservers.eclipsebot.service.discord.listeners;

import com.darkmatterservers.eclipsebot.service.discord.MessagingService;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * MessageListener listens for incoming messages from JDA
 * and delegates them to the MessagingService for tracking and logging.
 *
 * This listener is lightweight and offloads actual processing to the MessagingService.
 */
@Component
public class MessageListener extends ListenerAdapter {

    private final MessagingService messagingService;

    public MessageListener(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        Message message = event.getMessage();
        User author = event.getAuthor();

        // Skip bot messages (including EclipseBot itself)
        if (author.isBot()) return;

        // Forward to MessagingService
        messagingService.trackIncomingMessage(event);
    }
}
