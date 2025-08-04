package com.darkmatterservers.eclipsebot.service.discord.listeners;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.discord.builders.MessageBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Listens for button interactions and dispatches logic based on button ID.
 * All button callbacks are registered via MessageBuilder.
 */
@Component
public class ButtonListener extends ListenerAdapter {

    private final LoggerService logger;
    private final MessageBuilder messageBuilder;

    public ButtonListener(LoggerService logger, MessageBuilder messageBuilder) {
        this.logger = logger;
        this.messageBuilder = messageBuilder;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        event.getUser();
        String userTag = event.getUser().getAsTag();

        String context = event.isFromGuild()
                ? "Guild: " + Objects.requireNonNull(event.getGuild()).getName()
                : "Private DM";

        logger.info("🔘 Button clicked: [" + buttonId + "] by " + userTag + " (" + context + ")", String.valueOf(getClass()));

        boolean handled = false;
        try {
            handled = messageBuilder.handleButtonInteraction(buttonId, event);
        } catch (Exception e) {
            logger.error("🔥 Exception while handling button [" + buttonId + "]: " + e.getMessage(), String.valueOf(getClass()));
            event.reply("❌ An error occurred while processing your request.").setEphemeral(true).queue();
            return;
        }

        if (!handled) {
            event.reply("⚠️ That button doesn't do anything (yet).").setEphemeral(true).queue();
            logger.warn("Unhandled button click: " + buttonId, String.valueOf(getClass()));
        }
    }
}
