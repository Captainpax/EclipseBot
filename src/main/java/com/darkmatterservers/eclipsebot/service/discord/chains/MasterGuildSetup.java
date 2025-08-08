package com.darkmatterservers.eclipsebot.service.discord.chains;

import com.darkmatterservers.builder.MessageBuilder;
import com.darkmatterservers.chain.NewChain;
import com.darkmatterservers.context.ComponentContext;
import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.discord.Bytes;
import com.darkmatterservers.eclipsebot.service.discord.MessagingService;
import com.darkmatterservers.router.InteractionRouter;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class MasterGuildSetup {

    public static final String ID = "master.guild.setup";

    private final Bytes bytes;
    private final MessagingService messaging;
    private final YamlService yamlService;
    private final LoggerService logger;

    public MasterGuildSetup(Bytes bytes,
                            @Lazy MessagingService messaging,
                            YamlService yamlService,
                            LoggerService logger) {
        this.bytes = bytes;
        this.messaging = messaging;
        this.yamlService = yamlService;
        this.logger = logger;
    }

    /**
     * Build the interaction chain. Each action logs its step for easier debugging.
     */
    public NewChain build() {
        logger.info("[MasterGuildSetup] Building chain...", getClass().getName());
        return new NewChain()
                // Actual entry point
                .entry("welcome")

                // Step 0: Welcome & Get Started button
                .node("welcome",
                        "👋 **Welcome to EclipseBot Setup!**\n\n" +
                                "EclipseBot helps you manage your Archipelago game servers right from Discord — " +
                                "including automated role setup, server category creation, and integration with MongoDB.\n\n" +
                                "Click **Get Started** to begin the guided setup.",
                        Map.of("getStarted", ctx -> {
                            logger.info("[MasterGuildSetup] getStarted clicked by=" + ctx.userId(), getClass().getName());
                            ctx.put("nextNode", "chooseGuildOrIntro");
                        }),
                        "getStarted"
                )

                // Step 0.5: If guildId already chosen, skip; otherwise prompt
                .node("chooseGuildOrIntro", "Select a server to continue.",
                        Map.of(
                                "haveGuild", ctx -> {
                                    if (ctx.has("guildId")) {
                                        logger.info("[MasterGuildSetup] guildId present=" + ctx.getString("guildId"), getClass().getName());
                                        ctx.put("nextNode", "intro");
                                    } else {
                                        logger.warn("[MasterGuildSetup] guildId missing; user should have selected from dropdown.", getClass().getName());
                                        messaging.dmUser(ctx.userId(), "Please pick a server from the dropdown I sent.");
                                        // stay here or re-send dropdown (caller sends dropdown initially)
                                    }
                                }
                        )
                )

                // Step 1: Ask a user role
                .node("intro", "What is your role on this server?",
                        Map.of(
                                "admin", ctx -> {
                                    logger.info("[MasterGuildSetup] role=admin user=" + ctx.userId(), getClass().getName());
                                    messaging.dmUser(ctx.userId(), "Nice! Admins get the power tools. 🛠️");
                                    ctx.put("role", "admin");
                                    ctx.put("nextNode", "serverInfo");
                                },
                                "mod", ctx -> {
                                    logger.info("[MasterGuildSetup] role=mod user=" + ctx.userId(), getClass().getName());
                                    messaging.dmUser(ctx.userId(), "Cool, mods keep the peace. 🧹");
                                    ctx.put("role", "mod");
                                    ctx.put("nextNode", "serverInfo");
                                }
                        ),
                        "admin"
                )

                // Step 2: Server name
                .node("serverInfo", "🔧 What server are you setting up today?",
                        Map.of("input", ctx -> {
                            String serverName = String.valueOf(ctx.get("value", String.class));
                            logger.info("[MasterGuildSetup] serverInfo name=" + serverName + " user=" + ctx.userId(), getClass().getName());
                            ctx.put("serverName", serverName);
                            messaging.dmUser(ctx.userId(), "✅ Server: " + serverName);
                            ctx.put("nextNode", "roleSetup");
                        }),
                        "input"
                )

                // Step 3: Role setup
                .node("roleSetup", "🛡️ Do you want to create or reuse roles for Mod & Player?",
                        Map.of(
                                "create", ctx -> {
                                    logger.info("[MasterGuildSetup] roleSetup=create user=" + ctx.userId(), getClass().getName());
                                    ctx.put("useRoles", "create");
                                    messaging.dmUser(ctx.userId(), "Roles will be created.");
                                    ctx.put("nextNode", "mongoChoice");
                                },
                                "reuse", ctx -> {
                                    logger.info("[MasterGuildSetup] roleSetup=reuse user=" + ctx.userId(), getClass().getName());
                                    ctx.put("useRoles", "reuse");
                                    messaging.dmUser(ctx.userId(), "You will be prompted to select roles.");
                                    ctx.put("nextNode", "mongoChoice");
                                }
                        ),
                        "create"
                )

                // Step 4: Mongo setup choice
                .node("mongoChoice", "🧠 Would you like to enter a Mongo URI or set up Docker MongoDB (beta)?",
                        Map.of(
                                "uri", ctx -> {
                                    logger.info("[MasterGuildSetup] mongoChoice=uri user=" + ctx.userId(), getClass().getName());
                                    ctx.put("mongoSetup", "manual");
                                    messaging.dmUser(ctx.userId(), "Please enter your MongoDB URI:");
                                    ctx.put("nextNode", "mongoUriInput");
                                },
                                "docker", ctx -> {
                                    logger.info("[MasterGuildSetup] mongoChoice=docker user=" + ctx.userId(), getClass().getName());
                                    ctx.put("mongoSetup", "docker");
                                    messaging.dmUser(ctx.userId(), "Docker setup selected. 🐳 Attempting setup...");
                                    ctx.put("nextNode", "categorySetup");
                                }
                        ),
                        "uri"
                )

                // Step 5: URI input
                .node("mongoUriInput", "🔐 Enter Mongo URI:",
                        Map.of("input", ctx -> {
                            String uri = String.valueOf(ctx.get("value", String.class));
                            logger.info("[MasterGuildSetup] mongoUriInput uriPresent=" + (uri != null && !uri.isBlank()) + " user=" + ctx.userId(), getClass().getName());
                            ctx.put("mongoUri", uri);
                            messaging.dmUser(ctx.userId(), "Saved Mongo URI.");
                            ctx.put("nextNode", "categorySetup");
                        })
                )

                // Step 6: Category setup
                .node("categorySetup", "📁 Please select or create a category for admin-panel:",
                        Map.of(
                                "existing", ctx -> {
                                    boolean hasChannels = true; // TODO: real check
                                    logger.info("[MasterGuildSetup] categorySetup=existing hasChannels=" + hasChannels + " user=" + ctx.userId(), getClass().getName());
                                    ctx.put("nextNode", hasChannels ? "confirmCategoryPurge" : "fqdn");
                                },
                                "create", ctx -> {
                                    logger.info("[MasterGuildSetup] categorySetup=create user=" + ctx.userId(), getClass().getName());
                                    ctx.put("createCategory", true);
                                    ctx.put("nextNode", "fqdn");
                                }
                        )
                )

                // Step 7: Purge confirmation
                .node("confirmCategoryPurge", "⚠️ The selected category has channels. Delete them?",
                        Map.of(
                                "yes", ctx -> {
                                    logger.info("[MasterGuildSetup] confirmCategoryPurge=yes user=" + ctx.userId(), getClass().getName());
                                    messaging.dmUser(ctx.userId(), "Deleting channels...");
                                    ctx.put("nextNode", "fqdn");
                                },
                                "no", ctx -> {
                                    logger.info("[MasterGuildSetup] confirmCategoryPurge=no user=" + ctx.userId(), getClass().getName());
                                    messaging.dmUser(ctx.userId(), "Let's pick a different category then.");
                                    ctx.put("nextNode", "categorySetup");
                                }
                        )
                )

                // Step 8: FQDN input
                .node("fqdn", "🌐 What is the Fully Qualified Domain Name (FQDN) for server connections?",
                        Map.of("input", ctx -> {
                            String fqdn = String.valueOf(ctx.get("value", String.class));
                            logger.info("[MasterGuildSetup] fqdn value=" + fqdn + " user=" + ctx.userId(), getClass().getName());
                            ctx.put("fqdn", fqdn);
                            messaging.dmUser(ctx.userId(), "✅ Saved FQDN.");
                            ctx.put("nextNode", "portRange");
                        })
                )

                // Step 9: Port range
                .node("portRange", "📡 What port range should the bot use? (Default: 5000–5100)",
                        Map.of("input", ctx -> {
                            String range = String.valueOf(ctx.get("value", String.class));
                            logger.info("[MasterGuildSetup] portRange value=" + range + " user=" + ctx.userId(), getClass().getName());
                            ctx.put("portRange", range);
                            messaging.dmUser(ctx.userId(), "✅ Port range saved.");
                            ctx.put("nextNode", "summary");
                        })
                )

                // Step 10: Summary
                .node("summary", "📄 Here's a summary of your setup. Ready to proceed?",
                        Map.of(
                                "confirm", ctx -> {
                                    logger.info("[MasterGuildSetup] summary=confirm user=" + ctx.userId(), getClass().getName());
                                    messaging.dmUser(ctx.userId(), "🎉 Finalizing setup...");

                                    Map<String, Object> config = Map.of(
                                            "serverName", ctx.getString("serverName"),
                                            "role", ctx.getString("role"),
                                            "useRoles", ctx.getString("useRoles"),
                                            "mongoSetup", ctx.getString("mongoSetup"),
                                            "mongoUri", ctx.getString("mongoUri"),
                                            "fqdn", ctx.getString("fqdn"),
                                            "portRange", ctx.getString("portRange")
                                    );

                                    yamlService.put("guilds." + ctx.userId(), config);
                                    yamlService.save();

                                    messaging.dmUser(ctx.userId(), "✅ Configuration saved to config.yaml.");
                                    ctx.put("nextNode", "complete");
                                },
                                "cancel", ctx -> {
                                    logger.info("[MasterGuildSetup] summary=cancel user=" + ctx.userId(), getClass().getName());
                                    messaging.dmUser(ctx.userId(), "❌ Setup cancelled.");
                                    ctx.put("nextNode", "welcome");
                                }
                        )
                )

                // Step 11: Completion
                .node("complete", "✅ All set! Your server is now configured. Enjoy Eclipse Bot!", Map.of(), null);
    }

    // ===================== Launchers / Respond logic =====================

    /**
     * Starts with a welcome message + button.
     */
    public void start(String userId) {
        logger.info("[MasterGuildSetup] start(user) user=" + userId, getClass().getName());
        MessageBuilder mb = new MessageBuilder()
                .withContent("👋 **Welcome to EclipseBot!**\n\n" +
                        "I help you manage your Archipelago/game servers directly from Discord.\n\n" +
                        "Click the button below to begin the setup process.")
                .withButton(ID + ":welcome", "Get Started", ctx -> ctx.put("nextNode", "chooseGuildOrIntro"));

        bytes.sendPrivateMessage(userId, mb);

        // Optional debug: how many handlers are registered
        logger.info("[MasterGuildSetup] Router handlers registered=" + com.darkmatterservers.router.InteractionRouter.count(), getClass().getName());
    }

    /**
     * Starts with a dropdown of guilds to choose from. Preserves guildId as option value.
     */
    public void start(String userId, List<SelectOption> guildOptions) {
        int count = guildOptions != null ? guildOptions.size() : 0;
        logger.info("[MasterGuildSetup] start(user, guildOptions) user=" + userId + " options=" + count, getClass().getName());

        if (guildOptions == null || guildOptions.isEmpty()) {
            // fallback to simple welcome
            start(userId);
            return;
        }

        // (a) Send dropdown with value=GUILD_ID, label=GUILD_NAME
        bytes.sendPrivateDropdown(
                userId,
                "👋 **Welcome to EclipseBot!**\n\nSelect the server you want to configure from the list below.",
                ID + ":guildSelect",
                guildOptions,
                ctx -> {
                    String selectedGuildId = String.valueOf(ctx.get("value", String.class));
                    logger.info("[MasterGuildSetup] guild selected id=" + selectedGuildId + " user=" + ctx.userId(), getClass().getName());
                    if (selectedGuildId == null || selectedGuildId.isBlank()) {
                        messaging.dmUser(ctx.userId(), "❌ I didn't receive a guild selection. Please try again.");
                        return;
                    }
                    ctx.put("guildId", selectedGuildId);
                    messaging.dmUser(ctx.userId(), "✅ Guild selected: ``" + selectedGuildId + "``");

                    // (b) Immediately continue the chain: show role question via a button prompt
                    MessageBuilder next = new MessageBuilder()
                            .withContent("What is your role on this server?")
                            .withButton(ID + ":intro:admin", "I'm an Admin", c -> {
                                c.put("role", "admin");
                                c.put("nextNode", "serverInfo");
                            })
                            .withButton(ID + ":intro:mod", "I'm a Moderator", c -> {
                                c.put("role", "mod");
                                c.put("nextNode", "serverInfo");
                            });
                    bytes.sendPrivateMessage(ctx.userId(), next);
                }
        );

        logger.info("[MasterGuildSetup] Router handlers registered=" + InteractionRouter.count(), getClass().getName());
    }
}
