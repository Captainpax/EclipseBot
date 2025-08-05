package com.darkmatterservers.eclipsebot.service.discord.builders;

import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * DropdownBuilder is responsible for building select menus
 * and routing interactions to registered handlers.
 */
@Component
public class DropdownBuilder {

    @Getter
    private final List<StringSelectMenu> dropdowns = new ArrayList<>();
    @Getter
    private final List<DropdownHandler> handlers = new ArrayList<>();

    public DropdownBuilder(AtomicReference<JDA> jdaRef) {
    }

    public DropdownBuilder with(String id, List<SelectOption> options, Consumer<DropdownEvent> onSelect){
        dropdowns.add(StringSelectMenu.create(id)
                .addOptions((SelectOption) options)
                .build());
        handlers.add(new DropdownHandler(id, onSelect));
        return this;
    }

    public void clearDropdowns() {
        dropdowns.clear();
        handlers.clear();
    }

    public boolean handleDropdownInteraction(String id, StringSelectInteractionEvent event) {
        for (DropdownHandler handler : handlers) {
            if (handler.id.equals(id)) {
                try {
                    handler.action.accept(new DropdownEvent(id, event));
                    return true;
                } catch (Exception e) {
                    event.reply("❌ Error handling dropdown: `" + id + "`").setEphemeral(true).queue();
                    e.printStackTrace();
                    return false;
                }
            }
        }
        return false;
    }

    public record DropdownHandler(String id, Consumer<DropdownEvent> action) {}

    public static class DropdownEvent {
        private final String id;
        private final StringSelectInteractionEvent event;

        public DropdownEvent(String id, StringSelectInteractionEvent event) {
            this.id = id;
            this.event = event;
        }

        public String id() {
            return id;
        }

        public StringSelectInteractionEvent raw() {
            return event;
        }

        public User user() {
            return event.getUser();
        }

        public void reply(String message) {
            event.reply(message).setEphemeral(true).queue();
        }

        public List<String> getSelectedValues() {
            return event.getValues();
        }
    }
}
