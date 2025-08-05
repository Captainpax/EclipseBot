package com.darkmatterservers.eclipsebot.service.discord.listeners;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.discord.builders.DropdownBuilder;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Listens for dropdown (select menu) interactions and dispatches logic based on menu ID.
 */
@Component
public class DropdownListener extends ListenerAdapter {

    private final LoggerService logger;
    private final DropdownBuilder dropdownBuilder;

    public DropdownListener(LoggerService logger, DropdownBuilder dropdownBuilder) {
        this.logger = logger;
        this.dropdownBuilder = dropdownBuilder;
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        String menuId = event.getComponentId();
        String userTag = event.getUser().getAsTag();

        logger.info("📩 Dropdown selected: [" + menuId + "] by " + userTag, String.valueOf(getClass()));

        boolean handled = dropdownBuilder.handleDropdownInteraction(menuId, event);

        if (!handled) {
            event.reply("⚠️ No handler registered for this dropdown.").setEphemeral(true).queue();
            logger.warn("Unhandled dropdown: " + menuId, String.valueOf(getClass()));
        }
    }
}
