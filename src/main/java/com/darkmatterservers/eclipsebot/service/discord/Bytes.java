package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.builder.DropdownBuilder;
import com.darkmatterservers.context.ComponentContext;
import com.darkmatterservers.router.ComponentHandler;
import com.darkmatterservers.router.InteractionRouter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class Bytes {

    private final AtomicReference<JDA> jdaRef;

    public Bytes(AtomicReference<JDA> jdaRef) {
        this.jdaRef = jdaRef;
    }

    @PostConstruct
    public void init() {
        System.out.println("🧩 EclipseBytes bridge initialized");
    }

    @PreDestroy
    public void shutdown() {
        InteractionRouter.clear();
        System.out.println("🧹 Cleaned up EclipseBytes handlers");
    }

    public void sendPrivateMessage(String userId, String content) {
        JDA jda = jdaRef.get();
        if (jda == null || userId == null || userId.isBlank()) {
            System.err.println("❌ Cannot send DM — JDA or userId is null");
            return;
        }

        jda.retrieveUserById(userId).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage(content).queue();
                System.out.println("✅ Sent DM to " + user.getAsTag());
            });
        });
    }

    public void sendPrivateDropdown(String userId, String message, String dropdownId, List<SelectOption> options, ComponentHandler handler) {
        JDA jda = jdaRef.get();
        if (jda == null) {
            System.err.println("❌ JDA not initialized");
            return;
        }

        DropdownBuilder builder = new DropdownBuilder(dropdownId);
        options.forEach(opt -> builder.withOption(opt.getLabel(), opt.getValue()));
        builder.register(handler);

        StringSelectMenu menu = StringSelectMenu.create(dropdownId)
                .addOptions(builder.options().stream()
                        .map(opt -> SelectOption.of(opt.label(), opt.value()))
                        .toList())
                .build();

        jda.retrieveUserById(userId).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage(message)
                        .setComponents(ActionRow.of(menu))
                        .queue();
                System.out.println("✅ Sent dropdown DM to " + user.getAsTag());
            });
        });
    }

    public void handleDropdownInteraction(StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        String userId = event.getUser().getId();

        ComponentContext ctx = new ComponentContext(userId);
        ctx.put("value", event.getValues().isEmpty() ? null : event.getValues().get(0));
        ctx.put("rawEvent", event);

        InteractionRouter.handle(id, ctx);
        event.deferEdit().queue(); // acknowledge interaction
    }

    public static String format(String header, String body) {
        return "**" + header + "**\n" + body;
    }
}
