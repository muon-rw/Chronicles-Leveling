package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.catalog.SmeltersTouchAbility;
import dev.muon.chronicles_leveling.skill.ability.catalog.SuperbreakerAbility;
import dev.muon.chronicles_leveling.skill.ability.catalog.VeinSightAbility;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

import static dev.muon.chronicles_leveling.skill.perk.Magnitude.flat;
import static dev.muon.chronicles_leveling.skill.perk.Magnitude.perLevel;
import static dev.muon.chronicles_leveling.skill.perk.Perks.attr;
import static dev.muon.chronicles_leveling.skill.perk.Perks.grant;
import static dev.muon.chronicles_leveling.skill.perk.Perks.unlocks;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;

/**
 * Mining skill tree: the exemplar every per-skill catalog file follows.
 *
 * <p>Conventions for authoring a tree file:
 * <ul>
 *   <li>Capability keys are {@code public static final} on this class; the handler that reads
 *       a key imports it from here. Prefix the capability id with the skill id
 *       ({@code "mining_..."}) so two skills can't collide on the same id (freeze rejects that).</li>
 *   <li>Pure scaling-by-level passives are one node ({@code maxRank} 1) with a {@code perLevel}
 *       magnitude. Use {@code maxRank > 1} + {@code effectsAtRank} only where the catalog says
 *       "per rank" (the applier multiplies an {@code AttributeEffect}'s amount by rank, so don't
 *       also scale rank inside the magnitude).</li>
 *   <li>Never target the six stat attributes (freeze rejects it); skills own behaviors + the
 *       attribute lanes stats under-use, not the stat lanes.</li>
 *   <li>Topology is the prerequisite DAG; {@code .order(n)} is a soft within-tier sort hint.</li>
 * </ul>
 */
public final class MiningSkill {

    private MiningSkill() {}

    /** Summed chance (clamp at the read site) to drop extra raw resource from an ore. */
    public static final SkillCapability<Double> EXTRA_ORE_DROP = SkillCapability.additive("mining_extra_ore_drop");
    /** Effective Fortune level applied to ore drops without the enchantment. */
    public static final SkillCapability<Double> NATURAL_FORTUNE = SkillCapability.additive("mining_natural_fortune");
    /** Summed chance for a bonus roll of a rare-ore (diamond/emerald-tier) drop; clamped at the read site.
     *  A true odds-multiplier variant is deferred; for now this reads as an additive bonus-drop chance. */
    public static final SkillCapability<Double> RARE_ORE_BONUS = SkillCapability.additive("mining_rare_ore_bonus");
    /** Fraction of mining-tool durability damage avoided. */
    public static final SkillCapability<Double> TOOL_DURABILITY_SAVE = SkillCapability.additive("mining_tool_durability_save");
    /** Weak night vision while not exposed to the sky; the client lightmap reads the perk rank (synced) directly. */
    public static final SkillCapability<Boolean> CAVE_EYES = SkillCapability.flag("mining_cave_eyes");

    private static final Identifier MINING_EFFICIENCY = Identifier.withDefaultNamespace("mining_efficiency");
    private static final Identifier SUBMERGED_MINING_SPEED = Identifier.withDefaultNamespace("submerged_mining_speed");

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.MINING, Component.translatable("chronicles_leveling.skill.mining"))
                .description(Component.translatable("chronicles_leveling.skill.mining.desc"))

                // Root: break speed scales with skill level (passive attribute, lives immediately).
                .perk("vein_sense").cost(2)
                    .effect(attr(MINING_EFFICIENCY, ADD_VALUE, perLevel(Configs.SKILLS.mining.veinSenseEfficiencyPerLevel.get(), Configs.SKILLS.mining.veinSenseEfficiencyCap.get())))

                // Loot branch.
                .perk("mother_lode").requires("vein_sense").cost(2).maxRank(3)
                    .effectsAtRank(rank -> List.of(grant(EXTRA_ORE_DROP, Configs.SKILLS.mining.extraOreDropPerRank.get() * rank)))
                .perk("smelters_touch").requires("mother_lode").cost(4)
                    .effect(unlocks(SmeltersTouchAbility.ID))
                .ability(new SmeltersTouchAbility())
                .perk("fortunate_strikes").requires("vein_sense").cost(2).maxRank(3).order(10)
                    .effectsAtRank(rank -> List.of(grant(NATURAL_FORTUNE, Configs.SKILLS.mining.naturalFortunePerRank.get() * rank)))
                .perk("gem_hunter").requires("fortunate_strikes").cost(4)
                    .effect(grant(RARE_ORE_BONUS, Configs.SKILLS.mining.rareOreBonus.get()))

                // Utility branch. (Perk id 'superbreaker' is the leveled instant-break active.)
                .perk("superbreaker").requires("vein_sense").cost(4).maxRank(3).order(20)
                    .effect(unlocks(SuperbreakerAbility.ID))
                .ability(new SuperbreakerAbility())
                .perk("deep_diver").requires("vein_sense").cost(2).maxRank(2).order(30)
                    .effect(attr(SUBMERGED_MINING_SPEED, ADD_MULTIPLIED_BASE, flat(Configs.SKILLS.mining.deepDiverSubmergedSpeed.get())))
                .perk("cave_eyes").requires("deep_diver").cost(4).order(35)
                    .effect(grant(CAVE_EYES, Boolean.TRUE))
                .perk("sturdy_tools").requires("vein_sense").cost(2).order(40)
                    .effect(grant(TOOL_DURABILITY_SAVE, Configs.SKILLS.mining.toolDurabilitySave.get()))

                // Capstone: the ore-sight active "Vein Sense" (perk id 'spelunker'), converging Smelter's Touch + Gem Hunter + Cave Eyes.
                .perk("spelunker").requires("smelters_touch", "gem_hunter", "cave_eyes").cost(6).order(50)
                    .effect(unlocks(VeinSightAbility.ID))
                .ability(new VeinSightAbility())

                .build();
    }
}
