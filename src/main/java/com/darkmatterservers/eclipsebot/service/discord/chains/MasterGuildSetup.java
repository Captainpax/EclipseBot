package com.darkmatterservers.eclipsebot.service.discord.chains;

import com.darkmatterservers.builder.Buttons;
import com.darkmatterservers.builder.Dropdowns;
import com.darkmatterservers.chain.Page;
import com.darkmatterservers.chain.PagedChain;
import com.darkmatterservers.context.ComponentContext;
import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.discord.Bytes;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * MasterGuildSetup — PagedChain version with dynamic roles/categories
 * <p>
 * Flow (4 uniform pages):
 *  1) Welcome (Next)
 *  2) Pick Server (dropdown + Back/Next)
 *  3) Pick Roles (Mods/Players/Create + Back/Next + Role dropdown)
 *  4) Pick Admin Category (Create + Back/Done + Category dropdown)
 * <p>
 * Handlers persist user choices in ComponentContext, and on Done we save to YAML.
 */
@Component
public class MasterGuildSetup {

    public static final String CHAIN_TITLE = "Setup Wizard";

    // Component IDs (buttons / dropdowns)
    public static final String ID_DD_SERVER = "MasterServerPick";
    public static final String ID_BTN_MODS = "ModsRoleButton";
    public static final String ID_BTN_PLAYERS = "PlayersRoleButton";
    public static final String ID_BTN_CREATE_ROLES = "CreateRoleButton";
    public static final String ID_DD_ROLES = "RolePicker";
    public static final String ID_DD_CATEGORY = "CategoryPicker";
    public static final String ID_BTN_CREATE_PANEL = "CreateAdminPanel";

    private final Bytes bytes;
    private final YamlService yamlService;
    private final LoggerService logger;
    private final AtomicReference<JDA> jdaRef;

    public MasterGuildSetup(Bytes bytes,
                            @Lazy YamlService yamlService,
                            LoggerService logger,
                            AtomicReference<JDA> jdaRef) {
        this.bytes = bytes;
        this.yamlService = yamlService;
        this.logger = logger;
        this.jdaRef = jdaRef;
    }

    // -------------------------- Public API --------------------------

    /** Start the wizard with a computed list of eligible guild options (label=name, value=id). */
    public void start(String userId, List<SelectOption> eligibleGuildOptions) {
        var guildLabels = new ArrayList<String>(eligibleGuildOptions.size());
        var guildValues = new ArrayList<String>(eligibleGuildOptions.size());
        eligibleGuildOptions.forEach(opt -> {
            guildLabels.add(opt.getLabel());
            guildValues.add(opt.getValue());
        });

        // Build the chain with initial dropdown options (roles/categories will refresh after guild select)
        var chain = buildChain(guildLabels,
                guildValues,
                List.of(), // roles placeholder; replaced on guild selection
                List.of()  // categories placeholder; replaced on guild selection
        );

        // Start the chain in DMs
        bytes.startDmPagedChain(userId, chain);
    }

    // -------------------------- Chain Composition --------------------------

    private PagedChain buildChain(List<String> guildNames, List<String> guildIds,
                                  List<String> rolesInGuild, List<String> categoriesInGuild) {
        // Page 1 — Welcome
        Page p0 = new Page(
                "Welcome to the Setup Wizard!",
                "Please click continue to goto the next page"
        ).withButton(3, Buttons.next());

        // Page 2 — Pick server (dropdown shows names; we translate to IDs in handler)
        Page p1 = new Page(
                "Pick a setup Server to the master server",
                null
        ).withButton(0, Buttons.back())
                .withButton(3, Buttons.next())
                .withDropdown(Dropdowns.dropdown(ID_DD_SERVER, "Pick a Server", guildNames));

        // Page 3 — Pick roles
        Page p2 = new Page(
                "Pick a role of Mods and Players",
                "Click Mods/Players to switch ur selction"
        ).withButton(0, Buttons.back())
                .withButton(3, Buttons.next())
                .withButton(5, Buttons.buildButton(ID_BTN_MODS, "Mods"))
                .withButton(6, Buttons.buildButton(ID_BTN_PLAYERS, "Players"))
                .withButton(7, Buttons.buildButton(ID_BTN_CREATE_ROLES, "Create"))
                .withDropdown(Dropdowns.dropdown(ID_DD_ROLES, "Pick a Role", rolesInGuild));

        // Page 4 — Pick admin category
        Page p3 = new Page(
                "Pick a Category for the Admin Panle.",
                null
        ).withButton(0, Buttons.back())
                .withButton(2, Buttons.buildButton(ID_BTN_CREATE_PANEL, "Create"))
                .withButton(3, Buttons.done())
                .withDropdown(Dropdowns.dropdown(ID_DD_CATEGORY, "Pick a Category", categoriesInGuild));

        return new PagedChain.Builder()
                .chainId(CHAIN_TITLE)
                .addPage(p0)
                .addPage(p1)
                .addPage(p2)
                .addPage(p3)
                .wireNavigation(Buttons.ID_BACK, Buttons.ID_NEXT, Buttons.ID_DONE)

                // Dropdown handlers
                .on(ID_DD_SERVER, ctx -> {
                    String selectedLabel = ctx.interactionValue();
                    String guildId = mapLabelToValue(selectedLabel, guildNames, guildIds);
                    if (guildId == null) return;

                    ctx.put("guildId", guildId);
                    ctx.put("guildName", selectedLabel);

                    // Refresh roles/categories from JDA for this guild and update dropdowns in-place
                    try {
                        var data = fetchGuildData(guildId);
                        ctx.put("roles.options", data.roles());
                        ctx.put("categories.options", data.categories());
                    } catch (Exception e) {
                        logger.warn("⚠️ Failed to refresh guild data for " + guildId + ": " + e.getMessage(), getClass().getName());
                    }
                })
                .on(ID_DD_ROLES, ctx -> {
                    String mode = (String) ctx.getOrDefault("roleMode", "mods");
                    String roleName = ctx.interactionValue();
                    if ("mods".equalsIgnoreCase(mode)) ctx.put("modsRole", roleName);
                    else ctx.put("playersRole", roleName);
                })
                .on(ID_DD_CATEGORY, ctx -> ctx.put("adminCategory", ctx.interactionValue()))

                // Button handlers
                .on(ID_BTN_MODS, ctx -> ctx.put("roleMode", "mods"))
                .on(ID_BTN_PLAYERS, ctx -> ctx.put("roleMode", "players"))
                .on(ID_BTN_CREATE_ROLES, ctx -> {
                    // TODO: JDA role creation in selected guild; for now mark created
                    ctx.put("modsRole", ctx.getOrDefault("modsRole", "Created:Mods"));
                    ctx.put("playersRole", ctx.getOrDefault("playersRole", "Created:Players"));
                })
                .on(ID_BTN_CREATE_PANEL, ctx -> {
                    // TODO: JDA category creation in selected guild; for now mark created
                    ctx.put("adminCategory", ctx.getOrDefault("adminCategory", "Created:AdminPanel"));
                })

                // Done -> persist to YAML then complete
                .on(Buttons.ID_DONE, this::persistAndComplete)
                .build();
    }

    // -------------------------- Handlers --------------------------

    private void persistAndComplete(ComponentContext ctx) {
        try {
            String userId = ctx.userId();
            String guildId = ctx.getString("guildId");
            String guildName = ctx.getString("guildName");
            String modsRole = ctx.getString("modsRole");
            String playersRole = ctx.getString("playersRole");
            String adminCategory = ctx.getString("adminCategory");

            Map<String, Object> config = Map.of(
                    "guildId", guildId,
                    "guildName", guildName,
                    "modsRole", modsRole,
                    "playersRole", playersRole,
                    "adminCategory", adminCategory
            );

            yamlService.put("guilds." + guildId, config);
            yamlService.save();

            logger.success("✅ Saved master setup for guild=" + guildName + " (" + guildId + ") by user=" + userId,
                    getClass().getName());
        } catch (Exception e) {
            logger.error("❌ Failed to persist setup: " + e.getMessage(), getClass().getName(), e);
        } finally {
            ctx.complete();
        }
    }

    // -------------------------- Helpers --------------------------

    private record GuildData(List<String> roles, List<String> categories) {}

    private GuildData fetchGuildData(String guildId) {
        JDA jda = jdaRef.get();
        if (jda == null) return new GuildData(List.of(), List.of());
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return new GuildData(List.of(), List.of());

        List<String> roles = guild.getRoles().stream()
                .filter(r -> !r.isManaged())
                .map(Role::getName)
                .collect(Collectors.toList());

        List<String> categories = guild.getCategories().stream()
                .map(Category::getName)
                .collect(Collectors.toList());

        return new GuildData(roles, categories);
    }

    private String mapLabelToValue(String selectedLabel, List<String> labels, List<String> values) {
        if (selectedLabel == null) return null;
        for (int i = 0; i < labels.size() && i < values.size(); i++) {
            if (selectedLabel.equals(labels.get(i))) return values.get(i);
        }
        return null;
    }
}
