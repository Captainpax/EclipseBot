package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.discord.chains.MasterGuildSetup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MessagingService {

    private final LoggerService logger;
    private final AtomicReference<JDA> jdaRef;
    private final Bytes bytes;
    private final MasterGuildSetup masterGuildSetup;

    public MessagingService(
            LoggerService logger,
            AtomicReference<JDA> jdaRef,
            Bytes bytes,
            MasterGuildSetup masterGuildSetup
    ) {
        this.logger = logger;
        this.jdaRef = jdaRef;
        this.bytes = bytes;
        this.masterGuildSetup = masterGuildSetup;
    }

    /**
     * Tracks incoming messages and logs them to the console.
     * Skips bot messages.
     */
    public void trackIncomingMessage(MessageReceivedEvent event) {
        Message message = event.getMessage();
        User author = event.getAuthor();

        if (author.isBot()) return;

        String content = message.getContentDisplay();
        String channelType = event.isFromGuild() ? "Guild" : "Private";
        String channelName = event.isFromGuild()
                ? message.getGuild().getName() + " / #" + message.getChannel().asGuildMessageChannel().getName()
                : "DM";

        String summary = String.format("[%s] <%s#%s | %s> @ %s → %s",
                channelType,
                author.getName(),
                author.getDiscriminator(),
                author.getId(),
                channelName,
                content
        );

        logger.info(summary, String.valueOf(getClass()));
    }

    /**
     * Sends a startup greeting to the admin and launches the setup chain if eligible.
     */
    public void greetAdminOnStartup(String adminId) {
        if (adminId == null || adminId.isBlank()) {
            logger.warn("Admin ID not set — skipping startup greeting.", String.valueOf(getClass()));
            return;
        }

        JDA jda = jdaRef.get();
        if (jda == null) {
            logger.error("❌ JDA not initialized — cannot send startup DM.", String.valueOf(getClass()));
            return;
        }

        List<SelectOption> guildOptions = getEligibleGuildOptions(jda, adminId);
        if (guildOptions.isEmpty()) {
            dmUser(adminId, "⚠️ You don't have admin or owner permissions in any servers where EclipseBot is present.");
            return;
        }

        // Launch the MasterGuildSetup chain
        masterGuildSetup.start(adminId, guildOptions);
    }

    /**
     * Filters the guilds where the admin has Owner or Administrator permissions.
     */
    private List<SelectOption> getEligibleGuildOptions(JDA jda, String adminId) {
        return jda.getGuilds().stream()
                .filter(guild -> {
                    Member member = guild.getMemberById(adminId);
                    return member != null && (member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR));
                })
                .map(guild -> SelectOption.of(guild.getName(), guild.getId()))
                .toList();
    }

    /**
     * Sends a plain text DM to the user.
     */
    public void dmUser(String userId, String content) {
        logger.info("📨 DM to user [" + userId + "]", String.valueOf(getClass()));
        logMessageDetails(userId, content, null);
        bytes.sendPrivateMessage(userId, content);
    }

    /**
     * Logs the outgoing message and any attached dropdown metadata.
     */
    private void logMessageDetails(String target, String message, List<SelectOption> options) {
        logger.info("📦 Outgoing message [DM] → " + target, String.valueOf(getClass()));
        logger.info("📝 Content:\n" + message, String.valueOf(getClass()));
        if (options != null && !options.isEmpty()) {
            logger.info("📎 Attachments: dropdown=" + options.size(), String.valueOf(getClass()));
        } else {
            logger.info("📎 Attachments: none", String.valueOf(getClass()));
        }
    }

    @PostConstruct
    public void onInit() {
        logger.info("✅ MessagingService initialized", String.valueOf(getClass()));
    }

    @PreDestroy
    public void shutdown() {
        logger.info("🛑 MessagingService shutting down", String.valueOf(getClass()));
    }
}
