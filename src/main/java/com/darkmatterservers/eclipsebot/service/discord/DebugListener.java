package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class DebugListener extends ListenerAdapter {

    private final Bytes bytes;
    private final LoggerService logger;

    public DebugListener(Bytes bytes, LoggerService logger) {
        this.bytes = bytes;
        this.logger = logger;
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (event.getUser().isBot()) return;

        logger.info(
                "[DebugListener] dropdown id=" + event.getComponentId()
                        + " user=" + event.getUser().getId()
                        + " values=" + event.getValues(),
                getClass().getName()
        );

        // hand off to the new paged system
        bytes.handleDropdownInteraction(event);
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getUser().isBot()) return;

        logger.info(
                "[DebugListener] button id=" + event.getComponentId()
                        + " user=" + event.getUser().getId(),
                getClass().getName()
        );

        // hand off to the new paged system
        bytes.handleButtonInteraction(event);
    }
}
