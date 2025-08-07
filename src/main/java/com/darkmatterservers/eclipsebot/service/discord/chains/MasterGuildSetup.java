package com.darkmatterservers.eclipsebot.service.discord.chains;

import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.discord.Bytes;
import com.darkmatterservers.eclipsebot.service.discord.MessagingService;
import com.darkmatterservers.chain.NewChain;
import com.darkmatterservers.router.ComponentHandler;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MasterGuildSetup {

    public static final String ID = "master.guild.setup";

    private final Bytes bytes;
    private final MessagingService messaging;
    private final YamlService yamlService;

    public MasterGuildSetup(Bytes bytes, @Lazy MessagingService messaging, YamlService yamlService) {
        this.bytes = bytes;
        this.messaging = messaging;
        this.yamlService = yamlService;
    }

    public NewChain build() {
        return new NewChain()
                .entry("intro")

                .node("intro", "👋 Welcome to Eclipse Bot Setup! What do you do?",
                        Map.of(
                                "admin", ctx -> {
                                    messaging.dmUser(ctx.userId(), "Nice! Admins get the power tools. 🛠️");
                                    ctx.put("role", "admin");
                                    ctx.put("nextNode", "serverInfo");
                                },
                                "mod", ctx -> {
                                    messaging.dmUser(ctx.userId(), "Cool, mods keep the peace. 🧹");
                                    ctx.put("role", "mod");
                                    ctx.put("nextNode", "serverInfo");
                                }
                        ), "admin")

                .node("serverInfo", "🔧 What server are you setting up today?",
                        Map.of(
                                "input", ctx -> {
                                    String serverName = String.valueOf(ctx.get("value", String.class));
                                    ctx.put("serverName", serverName);
                                    messaging.dmUser(ctx.userId(), "✅ Server: " + serverName);
                                    ctx.put("nextNode", "roleSetup");
                                }
                        ), "input")

                .node("roleSetup", "🛡️ Do you want to create or reuse roles for Mod & Player?",
                        Map.of(
                                "create", ctx -> {
                                    ctx.put("useRoles", "create");
                                    messaging.dmUser(ctx.userId(), "Roles will be created.");
                                    ctx.put("nextNode", "mongoChoice");
                                },
                                "reuse", ctx -> {
                                    ctx.put("useRoles", "reuse");
                                    messaging.dmUser(ctx.userId(), "You will be prompted to select roles.");
                                    ctx.put("nextNode", "mongoChoice");
                                }
                        ), "create")

                .node("mongoChoice", "🧠 Would you like to enter a Mongo URI or set up Docker MongoDB (beta)?",
                        Map.of(
                                "uri", ctx -> {
                                    ctx.put("mongoSetup", "manual");
                                    messaging.dmUser(ctx.userId(), "Please enter your MongoDB URI:");
                                    ctx.put("nextNode", "mongoUriInput");
                                },
                                "docker", ctx -> {
                                    ctx.put("mongoSetup", "docker");
                                    messaging.dmUser(ctx.userId(), "Docker setup selected. 🐳 Attempting setup...");
                                    ctx.put("nextNode", "categorySetup");
                                }
                        ), "uri")

                .node("mongoUriInput", "🔐 Enter Mongo URI:", Map.of(
                        "input", ctx -> {
                            ctx.put("mongoUri", ctx.get("value", String.class));
                            messaging.dmUser(ctx.userId(), "Saved Mongo URI.");
                            ctx.put("nextNode", "categorySetup");
                        }
                ))

                .node("categorySetup", "📁 Please select or create a category for admin-panel:",
                        Map.of(
                                "existing", ctx -> {
                                    boolean hasChannels = true; // TODO: real check
                                    if (hasChannels) {
                                        ctx.put("nextNode", "confirmCategoryPurge");
                                    } else {
                                        ctx.put("nextNode", "fqdn");
                                    }
                                },
                                "create", ctx -> {
                                    ctx.put("createCategory", true);
                                    ctx.put("nextNode", "fqdn");
                                }
                        ))

                .node("confirmCategoryPurge", "⚠️ The selected category has channels. Delete them?",
                        Map.of(
                                "yes", ctx -> {
                                    messaging.dmUser(ctx.userId(), "Deleting channels...");
                                    // TODO: implement cleanup logic
                                    ctx.put("nextNode", "fqdn");
                                },
                                "no", ctx -> {
                                    messaging.dmUser(ctx.userId(), "Let's pick a different category then.");
                                    ctx.put("nextNode", "categorySetup");
                                }
                        ))

                .node("fqdn", "🌐 What is the Fully Qualified Domain Name (FQDN) for server connections?",
                        Map.of("input", ctx -> {
                            ctx.put("fqdn", ctx.get("value", String.class));
                            messaging.dmUser(ctx.userId(), "✅ Saved FQDN.");
                            ctx.put("nextNode", "portRange");
                        }))

                .node("portRange", "📡 What port range should the bot use? (Default: 5000–5100)",
                        Map.of("input", ctx -> {
                            ctx.put("portRange", ctx.get("value", String.class));
                            messaging.dmUser(ctx.userId(), "✅ Port range saved.");
                            ctx.put("nextNode", "summary");
                        }))

                .node("summary", "📄 Here's a summary of your setup. Ready to proceed?",
                        Map.of(
                                "confirm", ctx -> {
                                    messaging.dmUser(ctx.userId(), "🎉 Finalizing setup...");

                                    // Create config map
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
                                    messaging.dmUser(ctx.userId(), "❌ Setup cancelled.");
                                    ctx.put("nextNode", "intro"); // restart
                                }
                        ))

                .node("complete", "✅ All set! Your server is now configured. Enjoy Eclipse Bot!",
                        Map.of(), null);
    }

    public void start(String userId, List<SelectOption> guildOptions) {
        bytes.sendPrivateDropdown(
                userId,
                "👋 Welcome to Eclipse Bot! What do you do?",
                ID + ":intro",
                List.of(
                        SelectOption.of("Admin", "admin"),
                        SelectOption.of("Moderator", "mod")
                ),
                ctx -> ctx.put("nextNode", "serverInfo")
        );
    }
}
