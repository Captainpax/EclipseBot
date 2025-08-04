// Updated: MessageListener.java
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

        if (author.isBot()) return;

        String content = message.getContentDisplay();
        String channelType = event.isFromGuild() ? "Guild" : "Private";
        String channelName = event.isFromGuild()
                ? message.getGuild().getName() + " / #" + message.getChannel().getName()
                : "DM";

        String summary = String.format("[%s] <%s#%s | %s> @ %s → %s",
                channelType,
                author.getName(),
                author.getDiscriminator(),
                author.getId(),
                channelName,
                content
        );

        logger.info("MessageListener", summary);
    }
}
