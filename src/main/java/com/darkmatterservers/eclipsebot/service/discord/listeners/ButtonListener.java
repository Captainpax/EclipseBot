package com.darkmatterservers.eclipsebot.service.discord.listeners;

import com.darkmatterservers.eclipsebot.service.discord.builders.ButtonBuilder;
import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Listens for button interactions and delegates to ButtonBuilder.
 */
@Component
public class ButtonListener extends ListenerAdapter {

    private final JDA jda;
    private final ButtonBuilder buttonBuilder;

    public ButtonListener(JDA jda, ButtonBuilder buttonBuilder) {
        this.jda = jda;
        this.buttonBuilder = buttonBuilder;
    }

    @PostConstruct
    public void register() {
        jda.addEventListener(this);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        buttonBuilder.handle(new ButtonBuilder.ButtonEvent(buttonId, event));
    }
}
