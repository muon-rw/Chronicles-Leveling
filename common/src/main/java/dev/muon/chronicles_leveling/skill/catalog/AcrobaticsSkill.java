package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.catalog.DashAbility;
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
 * Acrobatics skill tree: mobility, fall mitigation, and evasion.
 *
 * <p>Passive attribute bonuses live immediately via {@code SkillModifierApplier}. Behavior capabilities are
 * wired by {@link dev.muon.chronicles_leveling.skill.mobility.AcrobaticsHandler}: Roll (look-down fall-damage
 * cut) and Momentum Vault (sprint-jump speed burst) server-side, Sure-Footed client-side. Dash unlocks
 * {@link DashAbility} via an {@code AbilityUnlock} node. Catlike shrinks a sneaking holder's visibility to
 * targeting mobs (LivingEntityMixin) on top of its sneaking_speed bonus.
 */
public final class AcrobaticsSkill {

    private AcrobaticsSkill() {}

    /**
     * Fall-damage reduction (0-1) applied when the player lands looking down on impact (xRot past the
     * configured pitch). {@link dev.muon.chronicles_leveling.skill.mobility.AcrobaticsHandler} clamps it to
     * a full negate.
     */
    public static final SkillCapability<Double> ROLL_REDUCTION = SkillCapability.additive("acrobatics_roll_reduction");
    /** No movement slowdown while using/eating/drawing items; client read site. */
    public static final SkillCapability<Boolean> SURE_FOOTED = SkillCapability.flag("acrobatics_sure_footed");
    /** Per-rank fraction (0-1) by which a sneaking holder's visibility to targeting mobs shrinks; read by LivingEntityMixin. */
    public static final SkillCapability<Double> REDUCED_DETECTION = SkillCapability.additive("acrobatics_reduced_detection");
    public static final SkillCapability<Boolean> MOMENTUM_VAULT = SkillCapability.flag("acrobatics_momentum_vault");

    private static final Identifier SAFE_FALL_DISTANCE = Identifier.withDefaultNamespace("safe_fall_distance");
    private static final Identifier JUMP_STRENGTH = Identifier.withDefaultNamespace("jump_strength");
    private static final Identifier STEP_HEIGHT = Identifier.withDefaultNamespace("step_height");
    private static final Identifier SNEAKING_SPEED = Identifier.withDefaultNamespace("sneaking_speed");
    private static final Identifier EVASION = Identifier.fromNamespaceAndPath("combat_attributes", "evasion");

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.ACROBATICS, Component.translatable("chronicles_leveling.skill.acrobatics"))
                .description(Component.translatable("chronicles_leveling.skill.acrobatics.desc"))

                // Root: cushion bigger falls without damage (passive attribute, lives immediately).
                .perk("feather_fall").cost(3)
                    .effect(attr(SAFE_FALL_DISTANCE, ADD_VALUE, perLevel(Configs.SKILLS.acrobatics.safeFallPerLevel.get(), Configs.SKILLS.acrobatics.safeFallCap.get())))

                // Fall-mitigation branch: look-down Roll reduces fall damage (rank raises the cut; capstone rank fully negates).
                .perk("roll").requires("feather_fall").cost(3).maxRank(3)
                    .effectsAtRank(rank -> List.of(grant(ROLL_REDUCTION,
                            Configs.SKILLS.acrobatics.rollReductionStart.get()
                                    + Configs.SKILLS.acrobatics.rollReductionPerLevel.get() * (rank - 1))))
                .perk("sure_footed").requires("roll").cost(3)
                    .effect(grant(SURE_FOOTED, Boolean.TRUE))

                // Mobility branch.
                .perk("spring_step").requires("feather_fall").cost(3).order(10)
                    .effect(attr(JUMP_STRENGTH, ADD_MULTIPLIED_BASE, flat(Configs.SKILLS.acrobatics.jumpStrengthBonus.get())))
                    .effect(attr(STEP_HEIGHT, ADD_VALUE, flat(Configs.SKILLS.acrobatics.stepHeightBonus.get())))
                .perk("momentum_vault").requires("spring_step").cost(6)
                    .effect(grant(MOMENTUM_VAULT, Boolean.TRUE))

                // Active: a forward lunge with brief i-frames (the spine ability).
                .perk("dash").requires("spring_step").cost(6).order(40)
                    .effect(unlocks(DashAbility.ID))
                .ability(new DashAbility())

                // Evasion / stealth branch.
                .perk("dodge").requires("feather_fall").cost(5).order(20).maxRank(2)
                    .effect(attr(EVASION, ADD_VALUE, flat(Configs.SKILLS.acrobatics.evasionPerRank.get())))
                .perk("catlike").requires("feather_fall").cost(3).order(30).maxRank(3)
                    .effectsAtRank(rank -> List.of(
                            attr(SNEAKING_SPEED, ADD_MULTIPLIED_BASE, flat(Configs.SKILLS.acrobatics.sneakingSpeedBonus.get())),
                            grant(REDUCED_DETECTION, Configs.SKILLS.acrobatics.catlikeDetectionReductionPerRank.get() * rank)))

                .build();
    }
}
