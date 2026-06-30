package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

import static dev.muon.chronicles_leveling.skill.perk.Magnitude.flat;
import static dev.muon.chronicles_leveling.skill.perk.Magnitude.perLevel;
import static dev.muon.chronicles_leveling.skill.perk.Perks.attr;
import static dev.muon.chronicles_leveling.skill.perk.Perks.grant;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;

public final class ArcherySkill {

    private ArcherySkill() {}

    /** Summed bonus damage fraction at max range; the handler scales it by the shot's travel distance. */
    public static final SkillCapability<Double> FAR_SHOT_BONUS = SkillCapability.additive("archery_far_shot_bonus");
    /** Number of extra arrows fired per shot (summed across ranks; floor/round at the read site). */
    public static final SkillCapability<Double> MULTISHOT_ARROWS = SkillCapability.additive("archery_multishot_arrows");
    /** Number of times an arrow ricochets to a nearby target on impact (summed across ranks). On hit it always
     *  fires while {@code > 0}: the rank IS the bounce count, not a chance. */
    public static final SkillCapability<Double> RICOCHET_COUNT = SkillCapability.additive("archery_ricochet_count");
    /** Per-rank fraction of the prior shot's damage a ricochet hop carries (40/60/80%); only Ricochet grants it. */
    public static final SkillCapability<Double> RICOCHET_FRACTION = SkillCapability.additive("archery_ricochet_fraction");
    /** Arrows pass through and hit every mob in a line instead of stopping on the first. */
    public static final SkillCapability<Boolean> PIERCING_SHOT = SkillCapability.flag("archery_piercing_shot");
    /** Summed chance a struck mob is disoriented (blindness + nausea). */
    public static final SkillCapability<Double> DISORIENT_CHANCE = SkillCapability.additive("archery_disorient_chance");
    /** Summed chance a struck mob is pinned (rooted/slowed). */
    public static final SkillCapability<Double> PINNING_CHANCE = SkillCapability.additive("archery_pinning_chance");
    /** Pinning Shot's per-rank Slowness amplifier (rank 1 = amplifier 0 = Slowness I); only Pinning Shot grants it. */
    public static final SkillCapability<Double> PINNING_AMPLIFIER = SkillCapability.additive("archery_pinning_amplifier");

    private static final Identifier ARROW_VELOCITY = Identifier.fromNamespaceAndPath("combat_attributes", "arrow_velocity");
    private static final Identifier DRAW_SPEED = Identifier.fromNamespaceAndPath("combat_attributes", "draw_speed");
    private static final Identifier RANGED_CRIT_CHANCE = Identifier.fromNamespaceAndPath("combat_attributes", "ranged_crit_chance");
    private static final Identifier RANGED_CRIT_DAMAGE = Identifier.fromNamespaceAndPath("combat_attributes", "ranged_crit_damage");
    private static final Identifier ACCURACY = Identifier.fromNamespaceAndPath("combat_attributes", "accuracy");

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.ARCHERY, Component.translatable("chronicles_leveling.skill.archery"))
                .description(Component.translatable("chronicles_leveling.skill.archery.desc"))

                // Root: flatter arc + more impact, scales with skill level (passive attribute, lives immediately).
                .perk("strong_arm").cost(2)
                    .effect(attr(ARROW_VELOCITY, ADD_VALUE, perLevel(Configs.SKILLS.archery.arrowVelocityPerLevel.get(), Configs.SKILLS.archery.arrowVelocityCap.get())))

                // Handling branch: faster draw/reload, then distance & volume payoffs.
                .perk("quick_hands").requires("strong_arm").cost(2)
                    .effect(attr(DRAW_SPEED, ADD_VALUE, perLevel(Configs.SKILLS.archery.drawSpeedPerLevel.get(), Configs.SKILLS.archery.drawSpeedCap.get())))
                .perk("far_shot").requires("strong_arm").cost(4).order(10)
                    .effect(grant(FAR_SHOT_BONUS, Configs.SKILLS.archery.farShotBonus.get()))
                .perk("multishot").requires("quick_hands").cost(3).maxRank(4).order(20)
                    .effectsAtRank(rank -> List.of(grant(MULTISHOT_ARROWS, Configs.SKILLS.archery.multishotArrowsPerRank.get() * rank)))

                // Precision branch: crit then accuracy.
                .perk("bullseye").requires("strong_arm").cost(2).order(30)
                    .effect(attr(RANGED_CRIT_CHANCE, ADD_VALUE, perLevel(Configs.SKILLS.archery.rangedCritChancePerLevel.get(), Configs.SKILLS.archery.rangedCritChanceCap.get())))
                    .effect(attr(RANGED_CRIT_DAMAGE, ADD_VALUE, perLevel(Configs.SKILLS.archery.rangedCritDamagePerLevel.get(), Configs.SKILLS.archery.rangedCritDamageCap.get())))
                .perk("marksmans_eye").requires("bullseye").cost(2)
                    .effect(attr(ACCURACY, ADD_VALUE, flat(Configs.SKILLS.archery.accuracyBonus.get())))

                // Trick-shot branch: arrows that bounce, pierce, and crowd-control.
                .perk("ricochet").requires("far_shot").cost(3).maxRank(3).order(40)
                    .effectsAtRank(rank -> List.of(
                            grant(RICOCHET_COUNT, Configs.SKILLS.archery.ricochetBouncesPerRank.get() * rank),
                            grant(RICOCHET_FRACTION, Configs.SKILLS.archery.ricochetFractionBase.get()
                                    + Configs.SKILLS.archery.ricochetFractionPerRank.get() * (rank - 1))))
                .perk("piercing_shot").requires("far_shot").cost(4).order(50)
                    .effect(grant(PIERCING_SHOT, Boolean.TRUE))
                .perk("disorient").requires("bullseye").cost(2).maxRank(3).order(60)
                    .effectsAtRank(rank -> List.of(grant(DISORIENT_CHANCE, Configs.SKILLS.archery.disorientChance.get() * rank)))
                .perk("pinning_shot").requires("quick_hands").cost(2).maxRank(3).order(80)
                    .effectsAtRank(rank -> List.of(
                            grant(PINNING_CHANCE, Configs.SKILLS.archery.pinningChance.get() * rank),
                            grant(PINNING_AMPLIFIER, (double) Math.min(Configs.SKILLS.archery.pinningAmplifier.get(), rank - 1))))

                .build();
    }
}
