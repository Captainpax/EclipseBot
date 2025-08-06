package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.EclipseBytes.EclipseBytes;
import com.darkmatterservers.eclipsebytes.interactions.DropdownEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Bridge between EclipseBot and EclipseBytes.
 * Handles interaction registration and high-level utilities.
 */
@Component
public class Bytes {

    private static EclipseBytes eclipseBytes;

    public Bytes(AtomicReference<JDA> jdaRef) {
        eclipseBytes = new EclipseBytes(jdaRef);
    }

    @PostConstruct
    public void init() {
        eclipseBytes.init(); // Optional: Run any internal setup
    }

    @PreDestroy
    public void shutdown() {
        eclipseBytes.shutdown(); // Optional: Clean up listeners or handlers
    }

    public static void sendPrivateMessage(String userId, String content) {
        eclipseBytes.sendPrivateMessage(userId, content);
    }

    public static void sendPrivateDropdown(
            String userId,
            String message,
            String dropdownId,
            java.util.List<com.darkmatterservers.eclipsebytes.model.SelectOption> options,
            java.util.function.Consumer<DropdownEvent> handler
    ) {
        eclipseBytes.sendPrivateDropdown(userId, message, dropdownId, options, handler);
    }

    public static void handleDropdownInteraction(StringSelectInteractionEvent event) {
        eclipseBytes.handleDropdownInteraction(event);
    }

    public static String format(String header, String body) {
        return eclipseBytes.formatMessage(header, body);
    }
}
