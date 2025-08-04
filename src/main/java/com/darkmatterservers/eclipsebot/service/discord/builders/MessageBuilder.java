package com.darkmatterservers.eclipsebot.service.discord.builders;

import net.dv8tion.jda.api.JDA;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility for building and sending Discord messages.
 */
@Component
public class MessageBuilder {

    private final AtomicReference<JDA> jdaRef;

    public MessageBuilder(AtomicReference<JDA> jdaRef) {
        this.jdaRef = jdaRef;
    }

    /**
     * Sends a DM to a specific user by their ID.
     *
     * @param userId  Discord user ID
     * @param content Message content to send
     */
    public void sendPrivateMessage(String userId, String content) {
        JDA jda = jdaRef.get();
        if (jda == null) return;

        jda.retrieveUserById(userId)
                .queue(user -> user.openPrivateChannel()
                                .flatMap(channel -> channel.sendMessage(content))
                                .queue(),
                        error -> System.err.println("❌ Failed to DM user: " + userId));
    }

    /**
     * Formats a standard system message.
     *
     * @param header title or prefix (e.g., "✅ Success")
     * @param body   body of the message
     * @return formatted message string
     */
    public String format(String header, String body) {
        return "**" + header + "**\n" + body;
    }
}
