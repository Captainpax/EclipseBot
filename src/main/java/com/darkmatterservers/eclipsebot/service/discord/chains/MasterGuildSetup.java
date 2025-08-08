package com.darkmatterservers.eclipsebot.service.discord.chains;

import com.darkmatterservers.builder.Buttons;
import com.darkmatterservers.builder.Dropdowns;
import com.darkmatterservers.builder.PageRenderer;
import com.darkmatterservers.chain.Page;
import com.darkmatterservers.chain.PagedChain;
import com.darkmatterservers.context.ComponentContext;
import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.discord.Bytes;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MasterGuildSetup — PagedChain version
 *
 * Flow (4 uniform pages as requested):
 *  1) Welcome (Next)
 *  2) Pick Server (dropdown + Back/Next)
 *  3) Pick Roles (Mods/Players/Create + Back/Next + Role dropdown)
 *  4) Pick Admin Category (Create + Back/Done + Category dropdown)
 *
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

    public MasterGuildSetup(Bytes bytes,
                            @Lazy YamlService yamlService,
                            LoggerService logger) {
        this.bytes = bytes;
        this.yamlService = yamlService;
        this.logger = logger;
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

        // Build the chain with initial dropdown options
        var chain = buildChain(guildLabels, guildValues,
                List.of("Mods", "Players", "Admin"), // placeholder roles (replace with real list per guild later)
                List.of("Admin Panel", "Logs", "General") // placeholder categories
        );

        // Seed context with mapping from label->value for server selection, so we can persist guildId
        var ctx = new ComponentContext(userId);
        for (int i = 0; i < guildLabels.size(); i++) {
            ctx.put("server.label." + i, guildLabels.get(i));
            ctx.put("server.value." + i, guildValues.get(i));
        }

        // Start the chain in DMs
        bytes.startDmPagedChain(userId, chain);
    }

    /** Convenience: Start the wizard with simple string lists. */
    public void start(String userId, List<String> guildNames, List<String> guildIds,
                      List<String> rolesInGuild, List<String> categoriesInGuild) {
        var chain = buildChain(guildNames, guildIds, rolesInGuild, categoriesInGuild);
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

        // Build chain + wire handlers
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
                    // Find matching label index and store the real guildId (value)
                    String guildId = mapLabelToValue(ctx, selectedLabel);
                    if (guildId != null) {
                        ctx.put("guildId", guildId);
                        ctx.put("guildName", selectedLabel);
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

            // Persist under guild key; adjust to your desired shape
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

    private String mapLabelToValue(ComponentContext ctx, String selectedLabel) {
        if (selectedLabel == null) return null;
        // Walk stored label/value pairs to find the id
        for (int i = 0; ; i++) {
            String label = ctx.getString("server.label." + i);
            String value = ctx.getString("server.value." + i);
            if (label == null && value == null) break;
            if (selectedLabel.equals(label)) return value;
        }
        return null;
    }
}
