package com.darkmatterservers.eclipsebot.service.discord;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
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
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MessagingService {

    private final LoggerService logger;
    private final AtomicReference<JDA> jdaRef;

    public MessagingService(
            LoggerService logger,
            AtomicReference<JDA> jdaRef,
            YamlService yamlService
    ) {
        this.logger = logger;
        this.jdaRef = jdaRef;
    }

    public void trackIncomingMessage(@NotNull MessageReceivedEvent event) {
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

        String message = format(
                "👋 EclipseBot Started",
                """
                Hello! I'm **EclipseBot**, your assistant for managing Archipelago servers on Discord.

                Use the dropdown below to choose a server where you are an **admin** or **owner** and EclipseBot is present.
                """
        );

        logMessageDetails(adminId, message, guildOptions);
        EclipseBytes.sendPrivateDropdown(
                adminId,
                message,
                "select_guild",
                guildOptions,
                event -> {
                    String guildId = event.selectedValues().getFirst();
                    Guild guild = jda.getGuildById(guildId);
                    if (guild == null) {
                        event.reply("❌ Guild not found or bot is no longer in that server.");
                        return;
                    }

                    String stats = "📊 Guild Info for **" + guild.getName() + "**\n" +
                            "• ID: ``" + guild.getId() + "``\n" +
                            "• Owner: ``" + guild.getOwnerId() + "``\n" +
                            "• Member Count: " + guild.getMemberCount() + "\n" +
                            "• Channels: " + guild.getChannels().size() + "\n" +
                            "• Roles: " + guild.getRoles().size();

                    event.reply(stats);
                }
        );
    }

    private List<SelectOption> getEligibleGuildOptions(JDA jda, String adminId) {
        return jda.getGuilds().stream()
                .filter(guild -> {
                    Member member = guild.getMemberById(adminId);
                    return member != null && (member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR));
                })
                .map(guild -> SelectOption.of(guild.getName(), guild.getId()))
                .toList();
    }

    public void dmUser(String userId, String content) {
        logger.info("📨 DM to user [" + userId + "]", String.valueOf(getClass()));
        logMessageDetails(userId, content, null);
        EclipseBytes.sendPrivateMessage(userId, content);
    }

    public String format(String header, String body) {
        return "**" + header + "**\n\n" + body;
    }

    private void logMessageDetails(String target, String message, List<SelectOption> options) {
        logger.info("📦 Outgoing message [DM] → " + target, String.valueOf(getClass()));
        logger.info("📝 Content:\n" + message, String.valueOf(getClass()));
        if (options != null && !options.isEmpty()) {
            logger.info("📎 Attachments: dropdown=" + options.size(), String.valueOf(getClass()));
        } else {
            logger.info("📎 Attachments: none", String.valueOf(getClass()));
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("🛑 MessagingService shutting down", String.valueOf(getClass()));
    }

    @PostConstruct
    public void onInit() {
        logger.info("✅ MessagingService initialized", String.valueOf(getClass()));
    }
}
