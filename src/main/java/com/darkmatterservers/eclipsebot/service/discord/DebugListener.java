package com.darkmatterservers.eclipsebot.service.discord;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
public class DebugListener extends ListenerAdapter {
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        System.out.println("[DebugListener] onStringSelectInteraction id=" + event.getComponentId()
                + " user=" + event.getUser().getId()
                + " values=" + event.getValues());
    }
}
