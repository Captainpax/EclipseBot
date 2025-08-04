package com.darkmatterservers.eclipsebot.service.discord.builders;

import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Handles the creation and interaction mapping of buttons.
 */
@Component
public class ButtonBuilder {

    private final Map<String, Consumer<ButtonEvent>> buttonHandlers = new HashMap<>();

    /**
     * Creates a new Discord button and registers an interaction handler.
     *
     * @param id       unique button ID
     * @param label    text to display on the button
     * @param onClick  code to execute when the button is pressed
     * @return the built Button instance
     */
    public Button create(String id, String label, Consumer<ButtonEvent> onClick) {
        buttonHandlers.put(id, onClick);
        return Button.primary(id, label);
    }

    /**
     * Handles button clicks by executing the registered logic.
     *
     * @param event the ButtonEvent
     */
    public void handle(ButtonEvent event) {
        Consumer<ButtonEvent> handler = buttonHandlers.get(event.id());
        if (handler != null) {
            handler.accept(event);
        } else {
            event.reply("❌ No action assigned to this button.");
        }
    }

    /**
     * Represents a button interaction event.
     */
    public record ButtonEvent(String id, net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent raw) {
        public void reply(String content) {
            raw.reply(content).setEphemeral(true).queue();
        }

        public void defer() {
            raw.deferEdit().queue();
        }

        public String userId() {
            return raw.getUser().getId();
        }

        public String guildId() {
            return raw.getGuild() != null ? raw.getGuild().getId() : null;
        }

        public String channelId() {
            return raw.getChannel().getId();
        }
    }
}
