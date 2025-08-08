package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.builder.DropdownBuilder;
import com.darkmatterservers.builder.MessageBuilder;
import com.darkmatterservers.context.ComponentContext;
import com.darkmatterservers.eclipsebot.service.LoggerService;
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
    private final LoggerService logger;

    public Bytes(AtomicReference<JDA> jdaRef, LoggerService logger) {
        this.jdaRef = jdaRef;
        this.logger = logger;
    }

    @PostConstruct
    public void init() {
        logger.info("🧩 EclipseBytes bridge initialized", getClass().getName());
    }

    @PreDestroy
    public void shutdown() {
        InteractionRouter.clear();
        logger.info("🧹 Cleaned up EclipseBytes handlers", getClass().getName());
    }

    public void sendPrivateMessage(String userId, String content) {
        JDA jda = jdaRef.get();
        if (jda == null || userId == null || userId.isBlank()) {
            logger.warn("❌ Cannot send DM — JDA or userId is null", getClass().getName());
            return;
        }
        jda.retrieveUserById(userId).queue(user ->
                user.openPrivateChannel().queue(channel -> {
                    channel.sendMessage(content).queue();
                    logger.info("✅ Sent DM to " + user.getAsTag(), getClass().getName());
                })
        );
    }

    public void sendPrivateMessage(String userId, MessageBuilder builder) {
        JDA jda = jdaRef.get();
        if (jda == null || userId == null || userId.isBlank()) {
            logger.warn("❌ Cannot send DM (builder) — JDA or userId is null", getClass().getName());
            return;
        }
        jda.retrieveUserById(userId).queue(user ->
                user.openPrivateChannel().queue(channel -> {
                    channel.sendMessage(builder.getContent())
                            .setComponents(builder.getActionRows())
                            .queue();
                    logger.info("✅ Sent rich DM to " + user.getAsTag() + ", handlers=" + InteractionRouter.count(), getClass().getName());
                })
        );
    }

    public void sendPrivateDropdown(
            String userId,
            String message,
            String dropdownId,
            List<SelectOption> options,
            ComponentHandler handler
    ) {
        JDA jda = jdaRef.get();
        if (jda == null || userId == null || userId.isBlank()) {
            logger.warn("❌ Cannot send dropdown — JDA or userId is null", getClass().getName());
            return;
        }

        DropdownBuilder builder = new DropdownBuilder(dropdownId);
        options.forEach(opt -> builder.withOption(opt.getValue(), opt.getLabel()));
        builder.register(handler);

        StringSelectMenu menu = StringSelectMenu.create(dropdownId)
                .addOptions(builder.options().stream()
                        .map(opt -> SelectOption.of(opt.label(), opt.value()))
                        .toList())
                .build();

        jda.retrieveUserById(userId).queue(user ->
                user.openPrivateChannel().queue(channel -> {
                    channel.sendMessage(message)
                            .addComponents(ActionRow.of(menu))
                            .queue();
                    logger.info("✅ Sent dropdown DM to " + user.getAsTag() + ", routerHandlers=" + InteractionRouter.count(), getClass().getName());
                })
        );
    }

    public void handleDropdownInteraction(StringSelectInteractionEvent event) {
        if (event == null) return;
        final String id = event.getComponentId();
        final String userId = event.getUser().getId();
        logger.info("[Bytes] dropdown interaction id=" + id + " user=" + userId + " values=" + event.getValues(), getClass().getName());

        event.deferEdit().queue(
                ok -> {
                    try {
                        ComponentContext ctx = new ComponentContext(userId);
                        ctx.put("value", event.getValues().isEmpty() ? null : event.getValues().get(0));
                        ctx.put("rawEvent", event);
                        InteractionRouter.handle(id, ctx);
                    } catch (Throwable t) {
                        logger.error("[Bytes] Router handler threw: " + t.getMessage(), getClass().getName(), t);
                    }
                },
                err -> logger.error("[Bytes] deferEdit failed: " + err, getClass().getName())
        );
    }

    public static String format(String header, String body) {
        return "**" + header + "**\n" + body;
    }
}