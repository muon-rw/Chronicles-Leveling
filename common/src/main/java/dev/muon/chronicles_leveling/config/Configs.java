package dev.muon.chronicles_leveling.config;

import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;

import java.util.function.Supplier;

/**
 * Central access point and registration hook for the mod's FzzyConfig instances.
 *
 * <p>Three configs are registered on mod init:
 * <ul>
 *   <li>{@link #CLIENT} — {@link RegisterType#CLIENT}: local-only preferences.</li>
 *   <li>{@link #STATS}  — {@link RegisterType#BOTH}: stat-allocation curve, modifiers,
 *       and display defaults; server-authoritative, synced to clients.</li>
 *   <li>{@link #SKILLS} — {@link RegisterType#BOTH}: per-skill curves and
 *       XP-gain rules; same sync mode as {@code STATS}.</li>
 * </ul>
 *
 * <p>After {@link #register()} runs, read values anywhere via e.g.
 * {@code Configs.STATS.maxLevel.get()} or {@code Configs.SKILLS.weaponry.xpPerDamage}.
 */
public final class Configs {

    public static ConfigClient CLIENT;
    public static ConfigStats STATS;
    public static ConfigSkills SKILLS;

    private Configs() {}

    /**
     * Registers and loads all configs. Safe to call from common init on both loaders;
     * FzzyConfig handles dist-appropriate gating via {@link RegisterType}.
     */
    public static void register() {
        // Supplier casts disambiguate from the Kotlin Function0 overload.
        CLIENT = ConfigApi.registerAndLoadConfig((Supplier<ConfigClient>) ConfigClient::new, RegisterType.CLIENT);
        STATS  = ConfigApi.registerAndLoadConfig((Supplier<ConfigStats>) ConfigStats::new,   RegisterType.BOTH);
        SKILLS = ConfigApi.registerAndLoadConfig((Supplier<ConfigSkills>) ConfigSkills::new, RegisterType.BOTH);
    }
}
