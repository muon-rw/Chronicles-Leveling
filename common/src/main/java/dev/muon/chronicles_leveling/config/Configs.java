package dev.muon.chronicles_leveling.config;

import dev.muon.chronicles_leveling.config.skill.AcrobaticsSkillConfig;
import dev.muon.chronicles_leveling.config.skill.AlchemySkillConfig;
import dev.muon.chronicles_leveling.config.skill.ArcherySkillConfig;
import dev.muon.chronicles_leveling.config.skill.ArmorSkillConfig;
import dev.muon.chronicles_leveling.config.skill.MagicSkillConfig;
import dev.muon.chronicles_leveling.config.skill.SkillConfig;
import dev.muon.chronicles_leveling.config.skill.WeaponrySkillConfig;
import dev.muon.chronicles_leveling.skill.Skills;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Central access point and registration hook for the mod's FzzyConfig instances.
 *
 * <p>Three top-level configs are registered on mod init, split by how each is
 * loaded and synced:
 * <ul>
 *   <li>{@link #CLIENT} — {@link RegisterType#CLIENT}: local-only preferences</li>
 *   <li>{@link #SERVER} — {@link RegisterType#SERVER}: server-only, never synced</li>
 *   <li>{@link #SYNC}   — {@link RegisterType#BOTH}: server-authoritative, synced to clients</li>
 * </ul>
 *
 * <p>Skills get one synced config each, written to
 * {@code config/chronicles_leveling/skills/<id>.toml}. Left-column skills
 * carry skill-specific knobs (xp-per-damage formulas, brewing tables) on
 * dedicated subclasses; right-column skills use the bare {@link SkillConfig}
 * for now and gain specialized fields when their gain hooks land.
 *
 * <p>After {@link #register()} runs, read values anywhere via e.g.
 * {@code Configs.SYNC.featureEnabled.get()} or
 * {@code Configs.skill(Skills.WEAPONRY).xpCurve.eval(...)}.
 */
public final class Configs {

    public static ConfigClient CLIENT;
    public static ConfigServer SERVER;
    public static ConfigSync SYNC;

    public static WeaponrySkillConfig WEAPONRY;
    public static ArcherySkillConfig ARCHERY;
    public static MagicSkillConfig MAGIC;
    public static ArmorSkillConfig ARMOR;
    public static AcrobaticsSkillConfig ACROBATICS;
    public static AlchemySkillConfig ALCHEMY;

    /** All skill configs, keyed by skill id. Iteration follows {@link Skills#ALL} order. */
    public static Map<String, SkillConfig> SKILLS;

    private Configs() {}

    /**
     * Registers and loads all configs. Safe to call from common init on both loaders;
     * FzzyConfig handles dist-appropriate gating via {@link RegisterType}.
     */
    public static void register() {
        // Supplier casts disambiguate from the Kotlin Function0 overload.
        CLIENT = ConfigApi.registerAndLoadConfig((Supplier<ConfigClient>) ConfigClient::new, RegisterType.CLIENT);
        SERVER = ConfigApi.registerAndLoadConfig((Supplier<ConfigServer>) ConfigServer::new, RegisterType.SERVER);
        SYNC = ConfigApi.registerAndLoadConfig((Supplier<ConfigSync>) ConfigSync::new, RegisterType.BOTH);

        WEAPONRY   = registerSkill((Supplier<WeaponrySkillConfig>) WeaponrySkillConfig::new);
        ARCHERY    = registerSkill((Supplier<ArcherySkillConfig>) ArcherySkillConfig::new);
        MAGIC      = registerSkill((Supplier<MagicSkillConfig>) MagicSkillConfig::new);
        ARMOR      = registerSkill((Supplier<ArmorSkillConfig>) ArmorSkillConfig::new);
        ACROBATICS = registerSkill((Supplier<AcrobaticsSkillConfig>) AcrobaticsSkillConfig::new);
        ALCHEMY    = registerSkill((Supplier<AlchemySkillConfig>) AlchemySkillConfig::new);

        Map<String, SkillConfig> skills = new LinkedHashMap<>();
        skills.put(Skills.WEAPONRY,   WEAPONRY);
        skills.put(Skills.ARCHERY,    ARCHERY);
        skills.put(Skills.MAGIC,      MAGIC);
        skills.put(Skills.ARMOR,      ARMOR);
        skills.put(Skills.ACROBATICS, ACROBATICS);
        skills.put(Skills.ALCHEMY,    ALCHEMY);
        for (String id : Skills.RIGHT_COL) {
            skills.put(id, registerSkill((Supplier<SkillConfig>) () -> new SkillConfig(id)));
        }
        SKILLS = Map.copyOf(skills);
    }

    /** Per-skill config lookup. Returns {@code null} for unknown skill ids. */
    public static SkillConfig skill(String skillId) {
        return SKILLS == null ? null : SKILLS.get(skillId);
    }

    private static <T extends SkillConfig> T registerSkill(Supplier<T> ctor) {
        return ConfigApi.registerAndLoadConfig(ctor, RegisterType.BOTH);
    }
}
