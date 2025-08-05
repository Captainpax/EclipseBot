package com.darkmatterservers.eclipsebot.service.discord.builders;

import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * MessageBuilder is responsible for constructing interactive messages (buttons and dropdowns)
 * and delegating their interactions to registered handlers.
 */
@Component
public class MessageBuilder {

    private final AtomicReference<JDA> jdaRef;
    private final List<Button> buttons = new ArrayList<>();
    private final List<StringSelectMenu> dropdowns = new ArrayList<>();

    @Getter
    private final List<ButtonHandler> buttonHandlers = new ArrayList<>();
    @Getter
    private final List<DropdownHandler> dropdownHandlers = new ArrayList<>();

    public MessageBuilder(AtomicReference<JDA> jdaRef) {
        this.jdaRef = jdaRef;
    }

    public void with(String id, String label, Consumer<ButtonEvent> onClick) {
        if (id == null || label == null || onClick == null) return;
        buttons.add(Button.primary(id, label));
        buttonHandlers.add(new ButtonHandler(id, onClick));
    }

    public void withDropdown(String id, List<String> options, Consumer<DropdownEvent> onSelect) {
        if (id == null || options == null || onSelect == null || options.isEmpty()) return;

        List<SelectOption> menuOptions = options.stream()
                .map(opt -> SelectOption.of(opt, opt))
                .toList();

        dropdowns.add(StringSelectMenu.create(id).addOptions(menuOptions).build());
        dropdownHandlers.add(new DropdownHandler(id, onSelect));
    }

    public void clearButtons() {
        buttons.clear();
    }

    public void clearDropdowns() {
        dropdowns.clear();
    }

    public void clearHandlers() {
        buttonHandlers.clear();
        dropdownHandlers.clear();
    }

    public void sendPrivateMessage(String userId, String content) {
        JDA jda = jdaRef.get();
        if (jda == null || userId == null || userId.isBlank()) {
            System.err.println("❌ Cannot send DM — JDA or userId is null");
            return;
        }

        jda.retrieveUserById(userId).queue(user -> user.openPrivateChannel().queue(channel -> {
            var message = channel.sendMessage(content);
            List<LayoutComponent> components = new ArrayList<>();

            if (!buttons.isEmpty()) {
                components.addAll((Collection<? extends LayoutComponent>) buttons);
            }
            if (!dropdowns.isEmpty()) {
                components.add((LayoutComponent) dropdowns.get(0));
            }

            if (!components.isEmpty()) {
                message.addActionRow((Collection<? extends ItemComponent>) components).queue();
            } else {
                message.queue();
            }

            System.out.println("✅ Sent DM with components to user: " + user.getAsTag());
            clearButtons();
            clearDropdowns();
        }), error -> System.err.println("❌ Failed to DM user: " + userId + " — " + error.getMessage()));
    }

    public boolean handleButtonInteraction(String id, ButtonInteractionEvent event) {
        for (ButtonHandler handler : buttonHandlers) {
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

    public boolean handleDropdownInteraction(String id, StringSelectInteractionEvent event) {
        for (DropdownHandler handler : dropdownHandlers) {
            if (handler.id.equals(id)) {
                try {
                    handler.action.accept(new DropdownEvent(id, event));
                    return true;
                } catch (Exception e) {
                    event.reply("❌ Error handling dropdown: `" + id + "`").setEphemeral(true).queue();
                    System.err.println("🔥 Exception in dropdown handler [" + id + "]: " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }
        }
        return false;
    }

    public String format(String header, String body) {
        return "**" + header + "**\n" + body;
    }

    public record ButtonHandler(String id, Consumer<ButtonEvent> action) {}
    public record DropdownHandler(String id, Consumer<DropdownEvent> action) {}

    public record ButtonEvent(String id, ButtonInteractionEvent event) {
        public ButtonInteractionEvent raw() { return event; }
        public void reply(String message) { event.reply(message).setEphemeral(true).queue(); }
        public User user() { return event.getUser(); }
    }

    public record DropdownEvent(String id, StringSelectInteractionEvent event) {
        public StringSelectInteractionEvent raw() { return event; }
        public void reply(String message) { event.reply(message).setEphemeral(true).queue(); }
        public List<String> selectedValues() { return event.getValues(); }
        public User user() { return event.getUser(); }
    }
}
