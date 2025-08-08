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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * MasterGuildSetup — PagedChain version with dynamic roles/categories & JS-flow parity
 * <p>
 * Pages (uniform):
 *  1) Welcome (Next)
 *  2) Pick Master Server (dropdown + Back/Next)
 *  3) Pick Roles (Mods/Players/Create + Back/Next + Role dropdown)
 *  4) Pick Admin Category (Create + Back/Done + Category dropdown)
 * <p>
 * Handlers persist user choices in ComponentContext, refresh dropdowns dynamically,
 * and on Done we save to YAML and complete.
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
                List.of(), // role placeholder; replaced on guild selection
                List.of()  // category placeholder; replaced on guild selection
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

        // Page 2 — Pick server (dropdown shows names; we translate to IDs in the handler)
        Page p1 = new Page(
                "Pick a setup Server to the master server",
                null
        ).withButton(0, Buttons.back())
                .withButton(3, Buttons.next())
                .withDropdown(Dropdowns.dropdown(ID_DD_SERVER, "Pick a Server", guildNames));

        // Page 3 — Pick roles
        Page p2 = new Page(
                "Pick a role of Mods and Players",
                "Click Mods/Players to switch ur selection"
        ).withButton(0, Buttons.back())
                .withButton(3, Buttons.next())
                .withButton(5, Buttons.buildButton(ID_BTN_MODS, "Mods", ButtonStyle.SECONDARY))
                .withButton(6, Buttons.buildButton(ID_BTN_PLAYERS, "Players", ButtonStyle.SECONDARY))
                .withButton(7, Buttons.buildButton(ID_BTN_CREATE_ROLES, "Create", ButtonStyle.SUCCESS))
                .withDropdown(Dropdowns.dropdown(ID_DD_ROLES, "Pick a Role", rolesInGuild));

        // Page 4 — Pick an admin category
        Page p3 = new Page(
                "Pick a Category for the Admin Panel.",
                null
        ).withButton(0, Buttons.back())
                .withButton(2, Buttons.buildButton(ID_BTN_CREATE_PANEL, "Create", ButtonStyle.SECONDARY))
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
                        // make them available to PageRenderer as dynamic overrides
                        ctx.put(ID_DD_ROLES + ".options", data.roles());
                        ctx.put(ID_DD_CATEGORY + ".options", data.categories());
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
                    String guildId = ctx.getString("guildId");
                    if (guildId == null || guildId.isBlank()) {
                        logger.warn("⚠️ CreateRoles clicked but guildId is missing in context", getClass().getName());
                        return;
                    }
                    JDA jda = jdaRef.get();
                    if (jda == null) {
                        logger.warn("⚠️ JDA not available; cannot create roles", getClass().getName());
                        return;
                    }
                    Guild guild = jda.getGuildById(guildId);
                    if (guild == null) {
                        logger.warn("⚠️ Guild not found for id=" + guildId, getClass().getName());
                        return;
                    }

                    try {
                        String desiredModsName = String.valueOf(ctx.getOrDefault("modsRole", "Mods"));
                        String desiredPlayersName = String.valueOf(ctx.getOrDefault("playersRole", "Players"));

                        Role mods = ensureRole(guild, desiredModsName);
                        Role players = ensureRole(guild, desiredPlayersName);

                        ctx.put("modsRole", mods.getName());
                        ctx.put("playersRole", players.getName());

                        // Refresh dropdown options so the new roles appear immediately
                        List<String> roleNames = guild.getRoles().stream()
                                .filter(r -> !r.isManaged())
                                .map(Role::getName)
                                .collect(Collectors.toList());
                        ctx.put(ID_DD_ROLES + ".options", roleNames);

                        logger.info("✅ Ensured roles exist in guild " + guild.getName() +
                                ": mods='" + mods.getName() + "', players='" + players.getName() + "'", getClass().getName());
                    } catch (Exception e) {
                        logger.warn("⚠️ Failed creating roles in guild " + guildId + ": " + e.getMessage(), getClass().getName());
                    }
                })
                .on(ID_BTN_CREATE_PANEL, ctx -> {
                    String guildId = ctx.getString("guildId");
                    if (guildId == null || guildId.isBlank()) {
                        logger.warn("⚠️ CreateAdminPanel clicked but guildId is missing in context", getClass().getName());
                        return;
                    }
                    JDA jda = jdaRef.get();
                    if (jda == null) {
                        logger.warn("⚠️ JDA not available; cannot create category", getClass().getName());
                        return;
                    }
                    Guild guild = jda.getGuildById(guildId);
                    if (guild == null) {
                        logger.warn("⚠️ Guild not found for id=" + guildId, getClass().getName());
                        return;
                    }

                    try {
                        String desiredName = String.valueOf(ctx.getOrDefault("adminCategory", "Admin Panel"));
                        Category cat = ensureCategory(guild, desiredName);
                        ctx.put("adminCategory", cat.getName());

                        // Refresh dropdown options so the new category appears immediately
                        List<String> categories = guild.getCategories().stream()
                                .map(Category::getName)
                                .collect(Collectors.toList());
                        ctx.put(ID_DD_CATEGORY + ".options", categories);

                        logger.info("✅ Ensured admin category exists in guild " + guild.getName() +
                                ": '" + cat.getName() + "'", getClass().getName());
                    } catch (Exception e) {
                        logger.warn("⚠️ Failed creating category in guild " + guildId + ": " + e.getMessage(), getClass().getName());
                    }
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

    /** Ensure a role with the given name exists in the guild; create it if missing (blocking). */
    private Role ensureRole(Guild guild, String name) {
        if (name == null || name.isBlank()) name = "Role";
        String finalName = name;
        Role existing = guild.getRoles().stream()
                .filter(r -> r.getName().equalsIgnoreCase(finalName))
                .findFirst()
                .orElse(null);
        if (existing != null) return existing;
        // Create a role with no permissions by default; admins can adjust later
        return guild.createRole()
                .setName(name)
                .complete(); // blocking is acceptable inside our deferred interaction flow
    }

    /** Ensure a category with the given name exists in the guild; create it if missing (blocking). */
    private Category ensureCategory(Guild guild, String name) {
        if (name == null || name.isBlank()) name = "Admin Panel";
        String finalName = name;
        Category existing = guild.getCategories().stream()
                .filter(c -> c.getName().equalsIgnoreCase(finalName))
                .findFirst()
                .orElse(null);
        if (existing != null) return existing;
        return guild.createCategory(name).complete(); // blocking; acceptable within deferred flow
    }

    private String mapLabelToValue(String selectedLabel, List<String> labels, List<String> values) {
        if (selectedLabel == null) return null;
        for (int i = 0; i < labels.size() && i < values.size(); i++) {
            if (selectedLabel.equals(labels.get(i))) return values.get(i);
        }
        return null;
    }
}
