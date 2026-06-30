package dev.muon.chronicles_leveling.skill.catalog;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.catalog.BulwarkAbility;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

import static dev.muon.chronicles_leveling.skill.perk.Magnitude.perLevel;
import static dev.muon.chronicles_leveling.skill.perk.Perks.attr;
import static dev.muon.chronicles_leveling.skill.perk.Perks.grant;
import static dev.muon.chronicles_leveling.skill.perk.Perks.unlocks;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;

/**
 * Defense skill tree: mechanics first, with one modest stat root.
 *
 * <p>The two light attribute passives (Iron Skin, Magic Ward) live immediately via
 * {@code SkillModifierApplier}; everything else is a capability grant {@code CombatProcRouter} reads at its
 * {@code onHitTaken} entry point. Bulwark (an armed parry active) unlocks {@link BulwarkAbility} via an
 * {@code AbilityUnlock} node. The attribute lanes are deliberately thin; armor/HP/KB are the stat system's job.
 */
public final class DefenseSkill {

    private DefenseSkill() {}

    /**
     * Fraction of max-health that caps how much any single hit may remove (anti-one-shot); lower = stronger.
     * The read site floors it at {@code defense.painToleranceFloor}.
     */
    public static final SkillCapability<Double> MAX_HIT_FRACTION = SkillCapability.additive("defense_max_hit_fraction");
    /** Immune to knockback and movement-slowing effects while blocking or sneaking. */
    public static final SkillCapability<Boolean> STALWART = SkillCapability.flag("defense_stalwart");
    /**
     * Total frontal block arc in degrees the player can block within. Vanilla shields cover a 180-degree front
     * hemisphere (a 90-degree {@code horizontal_blocking_angle} to each side); 360 = omnidirectional. The read
     * site halves this into the {@code BlocksAttacks} horizontal_blocking_angle.
     */
    public static final SkillCapability<Double> WIDE_BLOCK_ARC = SkillCapability.additive("defense_wide_block_arc");
    /** Summed chance, on a blocked hit, to fully negate it and stun the attacker (shield bash). */
    public static final SkillCapability<Double> SHIELD_BASH_CHANCE = SkillCapability.additive("defense_shield_bash_chance");
    /** Summed fraction of incoming melee damage reflected back to the attacker (thorns). */
    public static final SkillCapability<Double> RETRIBUTION_REFLECT = SkillCapability.additive("defense_retribution_reflect");
    /** Below a health threshold, grant temporary absorption + damage reduction (cooldown-gated at the read site). */
    public static final SkillCapability<Boolean> LAST_STAND = SkillCapability.flag("defense_last_stand");

    private static final Identifier ARMOR = Identifier.withDefaultNamespace("armor");
    private static final Identifier MAGIC_DEFENSE = Identifier.fromNamespaceAndPath("combat_attributes", "magic_defense");

    public static SkillDefinition define() {
        return SkillDefinition.builder(Skills.DEFENSE, Component.translatable("chronicles_leveling.skill.defense"))
                .description(Component.translatable("chronicles_leveling.skill.defense.desc"))

                // Root: Iron Skin, a modest armor passive scaling with level, multiplied by rank (0.1/0.2/0.3 per level).
                // The rest of the tree is mechanics, not stat stacking.
                .perk("iron_skin").cost(2).maxRank(3)
                    .effect(attr(ARMOR, ADD_VALUE, perLevel(Configs.SKILLS.defense.ironSkinArmorPerLevel.get(), Configs.SKILLS.defense.ironSkinArmorCap.get())))

                // Mitigation branch.
                .perk("magic_ward").requires("iron_skin").cost(2).order(10).maxRank(3)
                    .effect(attr(MAGIC_DEFENSE, ADD_VALUE, perLevel(Configs.SKILLS.defense.magicWardPerLevel.get(), Configs.SKILLS.defense.magicWardCap.get())))
                .perk("pain_tolerance").requires("iron_skin").cost(3).maxRank(2).order(20)
                    .effectsAtRank(rank -> List.of(grant(MAX_HIT_FRACTION, Configs.SKILLS.defense.painToleranceCapFractionBase.get() / rank)))
                .perk("last_stand").requires("pain_tolerance").cost(5).order(20)
                    .effect(grant(LAST_STAND, Boolean.TRUE))

                // Shield / blocking branch.
                .perk("stalwart").requires("iron_skin").cost(2).order(30)
                    .effect(grant(STALWART, Boolean.TRUE))
                .perk("shield_master").requires("stalwart").cost(3).maxRank(3).order(30)
                    .effectsAtRank(rank -> List.of(
                            grant(WIDE_BLOCK_ARC, Configs.SKILLS.defense.wideBlockArcBaseDegrees.get() + Configs.SKILLS.defense.wideBlockArcPerRankDegrees.get() * rank),   // 240 / 300 / 360 degrees (vanilla is 180)
                            grant(SHIELD_BASH_CHANCE, Configs.SKILLS.defense.shieldBashChancePerRank.get() * rank)))
                .perk("retribution").requires("stalwart").cost(4).maxRank(3).order(40)
                    .effectsAtRank(rank -> List.of(grant(RETRIBUTION_REFLECT, Configs.SKILLS.defense.retributionReflectPerRank.get() * rank)))

                // Active: arm a brief parry that fully negates the next incoming hit.
                .perk("bulwark").requires("stalwart").cost(4).order(50)
                    .effect(unlocks(BulwarkAbility.ID))
                .ability(new BulwarkAbility())

                .build();
    }
}
