package com.darkmatterservers.eclipsebot.service.discord.chains;

import com.darkmatterservers.builder.Buttons;
import com.darkmatterservers.builder.Dropdowns;
import com.darkmatterservers.chain.Page;
import com.darkmatterservers.chain.PagedChain;
import com.darkmatterservers.chain.PagedChain.Keys;
import com.darkmatterservers.context.ComponentContext;
import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.config.YamlService;
import com.darkmatterservers.eclipsebot.service.discord.Bytes;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * MasterGuildSetup — PagedChain version with dynamic roles/categories & JS-flow parity.
 * <p>
 * Pages:
 *  1) Welcome (Next)
 *  2) Pick Master Server (dropdown + Back/Next) [auto-advance on select]
 *  3) Pick Roles (Mods/Players/Create + Back/Next and Role dropdown)
 *  4) Pick Admin Category (Create + Back/Done + Category dropdown)
 * <p>
 * Dropdown behavior:
 *  - Options can be overridden at runtime via ctx key "<id>.options" (handled via Dropdowns.overrideOptions).
 *  - Selected item is highlighted via ctx key "<id>.selected".
 *  - We auto-advance on server select AND role select to reduce friction.
 * <p>
 * On Done: configuration is saved to YAML.
 */
@Component
public class MasterGuildSetup {

    public static final String CHAIN_TITLE = "Setup Wizard";

    // Component IDs (buttons / dropdowns)
    public static final String ID_DD_SERVER        = "MasterServerPick";
    public static final String ID_BTN_MODS         = "ModsRoleButton";
    public static final String ID_BTN_PLAYERS      = "PlayersRoleButton";
    public static final String ID_BTN_CREATE_ROLES = "CreateRoleButton";
    public static final String ID_DD_ROLES         = "RolePicker";
    public static final String ID_DD_CATEGORY      = "CategoryPicker";
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

    /**
     * Start the wizard for a specific admin. We compute the REAL list of guilds where
     * (a) the bot is present, and (b) the admin is owner or has Administrator permission.
     * The dropdown on page 2 is populated from that list.
     */
    public void start(String adminUserId) {
        List<SelectOption> eligible = getEligibleGuildOptions(adminUserId);
        if (eligible.isEmpty()) {
            bytes.sendPrivateMessage(adminUserId,
                    """
                            ⚠️ I can't find any servers where you are **Owner** or have **Administrator** permissions while I'm present.
                            • Invite me to your server, or
                            • Ensure you have Admin perms where I'm installed.
                            Then run `/setup` or restart me.""");
            return;
        }

        // Split to label/value lists for our chain builder
        var guildLabels = new ArrayList<String>(eligible.size());
        var guildValues = new ArrayList<String>(eligible.size());
        eligible.forEach(opt -> { guildLabels.add(opt.getLabel()); guildValues.add(opt.getValue()); });

        var chain = buildChain(
                guildLabels,
                guildValues,
                List.of(), // role placeholder; replaced on guild selection
                List.of()  // category placeholder; replaced on guild selection
        );

        bytes.startDmPagedChain(adminUserId, chain);
    }

    /** Keep the method for previous callers that already build the option list upstream. */
    public void start(String userId, List<SelectOption> eligibleGuildOptions) {
        if (eligibleGuildOptions == null || eligibleGuildOptions.isEmpty()) {
            start(userId); // fallback to self-computed list
            return;
        }
        var guildLabels = new ArrayList<String>(eligibleGuildOptions.size());
        var guildValues = new ArrayList<String>(eligibleGuildOptions.size());
        eligibleGuildOptions.forEach(opt -> { guildLabels.add(opt.getLabel()); guildValues.add(opt.getValue()); });
        var chain = buildChain(guildLabels, guildValues, List.of(), List.of());
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

        // Page 3 — Pick roles (dropdown appears only after Mods/Players/Create)
        Page p2 = new Page(
                "Pick a role of Mods and Players",
                "Click Mods/Players to switch your selection"
        ).withButton(0, Buttons.back())
                .withButton(3, Buttons.next())
                .withButton(5, Buttons.buildButton(ID_BTN_MODS, "Mods", ButtonStyle.SECONDARY))
                .withButton(6, Buttons.buildButton(ID_BTN_PLAYERS, "Players", ButtonStyle.SECONDARY))
                .withButton(7, Buttons.buildButton(ID_BTN_CREATE_ROLES, "Create", ButtonStyle.SUCCESS));
        // Role dropdown will be injected dynamically by handlers when appropriate

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

                // ---------------- Handlers ----------------

                // Server selection: map label -> id, refresh roles/categories, highlight selection, auto-advance
                .on(ID_DD_SERVER, ctx -> {
                    String selectedLabel = ctx.interactionValue();
                    String guildId = mapLabelToValue(selectedLabel, guildNames, guildIds);
                    if (guildId == null) return;

                    ctx.put(Keys.GUILD_ID, guildId);
                    ctx.put("guildId", guildId);      // keep both for convenience
                    ctx.put("guildName", selectedLabel);

                    // highlight selection
                    ctx.put(ID_DD_SERVER + ".selected", selectedLabel);

                    // refresh roles/categories from JDA and override the dropdowns
                    try {
                        var data = fetchGuildData(guildId);
                        Dropdowns.overrideOptions(ctx, ID_DD_ROLES, data.roles());
                        Dropdowns.overrideOptions(ctx, ID_DD_CATEGORY, data.categories());
                    } catch (Exception e) {
                        logger.warn("⚠️ Failed to refresh guild data for " + guildId + ": " + e.getMessage(), getClass().getName());
                    }

                    // Optional UX: auto-advance to the next page after picking the server
                    PagedChain.advancePage(ctx, +1);
                })

                // Role selection (respects active mode "mods" | "players") + highlight + auto-advance
                .on(ID_DD_ROLES, ctx -> {
                    String mode = String.valueOf(ctx.getOrDefault("roleMode", "mods"));
                    String roleName = ctx.interactionValue();
                    if ("mods".equalsIgnoreCase(mode)) ctx.put("modsRole", roleName);
                    else ctx.put("playersRole", roleName);

                    // highlight in dropdown
                    ctx.put(ID_DD_ROLES + ".selected", roleName);

                    // move forward automatically
                    PagedChain.advancePage(ctx, +1);
                })

                // Category selection and highlight (we don't auto-next here; final page uses Done)
                .on(ID_DD_CATEGORY, ctx -> {
                    String catName = ctx.interactionValue();
                    ctx.put("adminCategory", catName);
                    ctx.put(ID_DD_CATEGORY + ".selected", catName);
                })

                // Mode buttons — clicking Mods/Players makes the role dropdown appear (dynamic populate)
                .on(ID_BTN_MODS, ctx -> {
                    ctx.put("roleMode", "mods");
                    injectRoleDropdownIfMissing(ctx);
                })
                .on(ID_BTN_PLAYERS, ctx -> {
                    ctx.put("roleMode", "players");
                    injectRoleDropdownIfMissing(ctx);
                })

                // Create roles in guild if missing; then refresh role dropdown and inject if needed
                .on(ID_BTN_CREATE_ROLES, ctx -> {
                    String guildId = ctx.getString("guildId");
                    if (guildId == null || guildId.isBlank()) {
                        logger.warn("⚠️ CreateRoles clicked but guildId is missing in context", getClass().getName());
                        return;
                    }
                    Guild guild = getGuild(guildId);
                    if (guild == null) return;

                    try {
                        String desiredModsName    = String.valueOf(ctx.getOrDefault("modsRole", "Mods"));
                        String desiredPlayersName = String.valueOf(ctx.getOrDefault("playersRole", "Players"));

                        Role mods    = ensureRole(guild, desiredModsName);
                        Role players = ensureRole(guild, desiredPlayersName);

                        ctx.put("modsRole", mods.getName());
                        ctx.put("playersRole", players.getName());

                        // refresh dropdown immediately
                        List<String> roleNames = guild.getRoles().stream()
                                .filter(r -> !r.isManaged())
                                .map(Role::getName)
                                .collect(Collectors.toList());
                        Dropdowns.overrideOptions(ctx, ID_DD_ROLES, roleNames);
                        injectRoleDropdownIfMissing(ctx);

                        logger.info("✅ Ensured roles in guild " + guild.getName() +
                                " | mods='" + mods.getName() + "', players='" + players.getName() + "'", getClass().getName());
                    } catch (Exception e) {
                        logger.warn("⚠️ Failed creating roles in guild " + guildId + ": " + e.getMessage(), getClass().getName());
                    }
                })

                // Create a category if missing; then refresh the category dropdown
                .on(ID_BTN_CREATE_PANEL, ctx -> {
                    String guildId = ctx.getString("guildId");
                    if (guildId == null || guildId.isBlank()) {
                        logger.warn("⚠️ CreateAdminPanel clicked but guildId is missing in context", getClass().getName());
                        return;
                    }
                    Guild guild = getGuild(guildId);
                    if (guild == null) return;

                    try {
                        String desiredName = String.valueOf(ctx.getOrDefault("adminCategory", "Admin Panel"));
                        Category cat = ensureCategory(guild, desiredName);
                        ctx.put("adminCategory", cat.getName());

                        // refresh dropdown immediately
                        List<String> categories = guild.getCategories().stream()
                                .map(Category::getName)
                                .collect(Collectors.toList());
                        Dropdowns.overrideOptions(ctx, ID_DD_CATEGORY, categories);

                        logger.info("✅ Ensured admin category in guild " + guild.getName() +
                                " | '" + cat.getName() + "'", getClass().getName());
                    } catch (Exception e) {
                        logger.warn("⚠️ Failed creating category in guild " + guildId + ": " + e.getMessage(), getClass().getName());
                    }
                })

                // Done -> persist to YAML then complete
                .on(Buttons.ID_DONE, this::persistAndComplete)
                .build();
    }

    // -------------------------- Dynamic helpers for page 3 --------------------------

    /**
     * Ensures the role dropdown is injected and populated for page 3 when the user
     * has chosen Mods/Players or created roles. We store a flag in ctx so other
     * parts (renderer) can pick the dynamic options and highlight selected state.
     */
    private void injectRoleDropdownIfMissing(ComponentContext ctx) {
        // Nothing to do here for structure since Page is static; the renderer already
        // looks up dynamic options by ID. We just ensure options are present.
        String guildId = ctx.getString("guildId");
        Guild guild = guildId != null ? getGuild(guildId) : null;
        if (guild == null) return;
        List<String> roleNames = guild.getRoles().stream()
                .filter(r -> !r.isManaged())
                .map(Role::getName)
                .collect(Collectors.toList());
        Dropdowns.overrideOptions(ctx, ID_DD_ROLES, roleNames);
    }

    // -------------------------- Persist --------------------------

    private void persistAndComplete(ComponentContext ctx) {
        try {
            String userId        = ctx.userId();
            String guildId       = ctx.getString("guildId");
            String guildName     = ctx.getString("guildName");
            String modsRole      = ctx.getString("modsRole");
            String playersRole   = ctx.getString("playersRole");
            String adminCategory = ctx.getString("adminCategory");

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("guildId", guildId);
            config.put("guildName", guildName);
            config.put("modsRole", modsRole);
            config.put("playersRole", playersRole);
            config.put("adminCategory", adminCategory);

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

    // -------------------------- Guild data helpers --------------------------

    private record GuildData(List<String> roles, List<String> categories) {}

    private GuildData fetchGuildData(String guildId) {
        Guild guild = getGuild(guildId);
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

    private Guild getGuild(String guildId) {
        JDA jda = jdaRef.get();
        if (jda == null) {
            logger.warn("⚠️ JDA not available", getClass().getName());
            return null;
        }
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            logger.warn("⚠️ Guild not found for id=" + guildId, getClass().getName());
        }
        return guild;
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

    // -------------------------- Eligibility (real guilds for this admin) --------------------------

    /**
     * Returns guild options where the bot is present AND the admin is Owner or has ADMINISTRATOR.
     */
    private List<SelectOption> getEligibleGuildOptions(String adminId) {
        JDA jda = jdaRef.get();
        if (jda == null) return List.of();
        List<SelectOption> out = new ArrayList<>();

        for (Guild guild : jda.getGuilds()) {
            try {
                // fast-path: owner
                if (adminId.equals(guild.getOwnerId())) {
                    out.add(SelectOption.of(guild.getName(), guild.getId()));
                    continue;
                }
                // try fetch member and check ADMINISTRATOR
                Member m = guild.retrieveMemberById(adminId).submit().get();
                if (m != null && (m.isOwner() || m.hasPermission(Permission.ADMINISTRATOR))) {
                    out.add(SelectOption.of(guild.getName(), guild.getId()));
                }
            } catch (Exception ignored) {
                // If fetch fails (no member or missing intent), we skip that guild
            }
        }
        return out;
    }
}
