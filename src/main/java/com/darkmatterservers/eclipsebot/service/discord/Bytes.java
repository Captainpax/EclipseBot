package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.builder.PageRenderer;
import com.darkmatterservers.chain.Page;
import com.darkmatterservers.chain.PagedChain;
import com.darkmatterservers.context.ComponentContext;
import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.router.InteractionRouter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bytes: EclipseBot bridge around EclipseBytes paged chain system.
 *
 * - Start chains in DMs or guild channels
 * - Handle dropdown/button interactions -> route -> re-render current page
 * - In-memory sessions keyed by userId (adjust scoping if you need per-guild/channel)
 */
@Component
public class Bytes {

    private final AtomicReference<JDA> jdaRef;
    private final LoggerService logger;

    /** userId -> active session */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

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
        sessions.clear();
        InteractionRouter.clear();
        logger.info("🧹 Cleaned up EclipseBytes handlers & sessions", getClass().getName());
    }

    // ---------------------------------------------------------------------
    // Kick off chains
    // ---------------------------------------------------------------------

    /** Start a paged chain in the user's DMs. */
    public void startDmPagedChain(String userId, PagedChain chain) {
        JDA jda = jdaRef.get();
        if (validateJdaAndUser(jda, userId)) return;

        ComponentContext ctx = new ComponentContext(userId);
        ctx.put("pageIndex", 0);
        ctx.put("totalPages", chain.totalPages());

        Session session = new Session(chain, ctx);
        sessions.put(userId, session);

        jda.retrieveUserById(userId).queue(user ->
                user.openPrivateChannel().queue(channel -> renderCurrentPage(session, channel))
        );
    }

    /** Start a paged chain in any message channel (guild text, thread, etc.). */
    public void startChannelPagedChain(String userId, MessageChannel channel, PagedChain chain) {
        ComponentContext ctx = new ComponentContext(userId);
        ctx.put("pageIndex", 0);
        ctx.put("totalPages", chain.totalPages());

        Session session = new Session(chain, ctx);
        sessions.put(userId, session);

        renderCurrentPage(session, channel);
    }

    // ---------------------------------------------------------------------
    // Simple plain DM utility (non-chain)
    // ---------------------------------------------------------------------

    public void sendPrivateMessage(String userId, String content) {
        JDA jda = jdaRef.get();
        if (validateJdaAndUser(jda, userId)) return;

        jda.retrieveUserById(userId).queue(user ->
                user.openPrivateChannel().queue(channel -> {
                    channel.sendMessage(content).queue();
                    logger.info("✅ Sent DM to " + user.getAsTag(), getClass().getName());
                })
        );
    }

    // ---------------------------------------------------------------------
    // Interaction handlers (call from your JDA listeners)
    // ---------------------------------------------------------------------

    public void handleDropdownInteraction(StringSelectInteractionEvent event) {
        if (event == null) return;

        String userId = event.getUser().getId();
        Session session = sessions.get(userId);
        if (session == null) {
            event.deferEdit().queue();
            return; // no active session for this user
        }

        try {
            String componentId = event.getComponentId();
            String selected = event.getValues().isEmpty() ? null : event.getValues().get(0);

            ComponentContext ctx = session.ctx();
            ctx.put("value", selected);              // legacy-friendly
            ctx.put("interactionValue", selected);   // modern-friendly
            ctx.put("rawEvent", event);

            InteractionRouter.handle(componentId, ctx);
            event.deferEdit().queue();
        } catch (Throwable t) {
            logger.error("[Bytes] Dropdown handler error: " + t.getMessage(), getClass().getName(), t);
        }

        renderPostInteraction(userId, event.getChannel()); // MessageChannelUnion implements MessageChannel
    }

    public void handleButtonInteraction(ButtonInteractionEvent event) {
        if (event == null) return;

        String userId = event.getUser().getId();
        Session session = sessions.get(userId);
        if (session == null) {
            event.deferEdit().queue();
            return; // no active session
        }

        try {
            String componentId = event.getComponentId();

            ComponentContext ctx = session.ctx();
            ctx.put("buttonId", componentId);
            ctx.put("rawEvent", event);

            InteractionRouter.handle(componentId, ctx);
            event.deferEdit().queue();
        } catch (Throwable t) {
            logger.error("[Bytes] Button handler error: " + t.getMessage(), getClass().getName(), t);
        }

        renderPostInteraction(userId, event.getChannel());
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private void renderPostInteraction(String userId, MessageChannel channel) {
        Session session = sessions.get(userId);
        if (session == null) return;

        if (session.ctx().isComplete()) {
            channel.sendMessage("✅ Setup complete!").queue();
            sessions.remove(userId);
            return;
        }

        renderCurrentPage(session, channel);
    }

    private void renderCurrentPage(Session session, MessageChannel channel) {
        PagedChain chain = session.chain();
        ComponentContext ctx = session.ctx();

        int total = chain.totalPages();
        ctx.put("totalPages", total);

        Object rawIdx = ctx.getOrDefault("pageIndex", 0);
        int idx = (rawIdx instanceof Number) ? ((Number) rawIdx).intValue() : 0;
        idx = chain.clampIndex(idx);
        ctx.put("pageIndex", idx);

        Page page = chain.page(idx);
        PageRenderer.Rendered rendered = PageRenderer.render(chain.chainId(), idx, total, page);

        channel.sendMessageEmbeds(rendered.embed())
                .setComponents(rendered.rows().toArray(ActionRow[]::new))
                .queue();
    }

    private boolean validateJdaAndUser(JDA jda, String userId) {
        if (jda == null) {
            logger.warn("❌ Cannot send message — JDA is null", getClass().getName());
            return true;
        }
        if (userId == null || userId.isBlank()) {
            logger.warn("❌ Cannot send message — userId is null/blank", getClass().getName());
            return true;
        }
        return false;
    }

    private record Session(PagedChain chain, ComponentContext ctx) {
        public Session {
            Objects.requireNonNull(chain, "chain");
            Objects.requireNonNull(ctx, "ctx");
        }
    }

    // Kept for parity with previous API
    public static String format(String header, String body) {
        return "**" + header + "**\n" + body;
    }
}
