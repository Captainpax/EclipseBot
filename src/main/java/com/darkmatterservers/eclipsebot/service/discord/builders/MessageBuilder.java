package com.darkmatterservers.eclipsebot.service.discord.builders;

import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * MessageBuilder is responsible for constructing interactive messages (buttons)
 * and delegating their interactions to registered handlers.
 */
@Component
public class MessageBuilder {

    private final AtomicReference<JDA> jdaRef;
    private final List<Button> buttons = new ArrayList<>();

    @Getter
    private final List<ButtonHandler> handlers = new ArrayList<>();

    public MessageBuilder(AtomicReference<JDA> jdaRef) {
        this.jdaRef = jdaRef;
    }

    /**
     * Adds a button with a custom handler to this message.
     *
     * @param id      unique button ID
     * @param label   text shown on the button
     * @param onClick callback to invoke on press
     */
    public void with(String id, String label, Consumer<ButtonEvent> onClick) {
        if (id == null || label == null || onClick == null) return;

        buttons.add(Button.primary(id, label));
        handlers.add(new ButtonHandler(id, onClick));
    }

    /**
     * Clears only the button layout (not the handlers).
     * Call this manually before creating a new message if needed.
     */
    public void clearButtons() {
        buttons.clear();
    }

    /**
     * Clears all registered button handlers (rarely needed).
     */
    public void clearHandlers() {
        handlers.clear();
    }

    /**
     * Sends a private message to a user, optionally with attached buttons.
     *
     * @param userId  recipient user ID
     * @param content message content
     */
    public void sendPrivateMessage(String userId, String content) {
        JDA jda = jdaRef.get();
        if (jda == null || userId == null || userId.isBlank()) {
            System.err.println("❌ Cannot send DM — JDA or userId is null");
            return;
        }

        jda.retrieveUserById(userId).queue(user -> user.openPrivateChannel().queue(channel -> {
            if (!buttons.isEmpty()) {
                channel.sendMessage(content)
                        .setActionRow(buttons)
                        .queue();
            } else {
                channel.sendMessage(content).queue();
            }

            System.out.println("✅ Sent DM with " + buttons.size() + " button(s) to user: " + user.getAsTag());
            clearButtons(); // ✅ Keeps a handler list intact across messages
        }), error -> System.err.println("❌ Failed to DM user: " + userId + " — " + error.getMessage()));
    }

    /**
     * Routes button interactions based on registered handlers.
     *
     * @param id    button ID that was clicked
     * @param event interaction event object
     * @return true if the button was handled, false otherwise
     */
    public boolean handleButtonInteraction(String id, ButtonInteractionEvent event) {
        for (ButtonHandler handler : handlers) {
            if (handler.id.equals(id)) {
                try {
                    handler.action.accept(new ButtonEvent(id, event));
                    return true;
                } catch (Exception e) {
                    event.reply("❌ Error handling button: `" + id + "`").setEphemeral(true).queue();
                    System.err.println("🔥 Exception in button handler [" + id + "]: " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Utility to format a stylized bot message.
     *
     * @param header header or title
     * @param body   main message body
     * @return formatted string
     */
    public String format(String header, String body) {
        return "**" + header + "**\n" + body;
    }

    public record ButtonHandler(String id, Consumer<ButtonEvent> action) {}

    /**
         * Wrapper class for button events.
         */
        public record ButtonEvent(String id, ButtonInteractionEvent event) {

        public ButtonInteractionEvent raw() {
                return event;
            }

            public void reply(String message) {
                event.reply(message).setEphemeral(true).queue();
            }

            public User user() {
                return event.getUser();
            }
        }
}