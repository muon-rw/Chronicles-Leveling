package dev.muon.chronicles_leveling.skill.ability;

import net.minecraft.resources.Identifier;

/**
 * Zero-boilerplate base for a {@link SkillAbility}: carries the four immutable
 * metadata fields and the permissive {@link SkillAbility#canActivate} default, so a
 * concrete ability is one constructor call plus a {@link #run} body.
 *
 * <p>Deliberately does NOT host the cast gate sequence (unlock / cooldown / cost):
 * those checks need per-player state a shared ability singleton must not hold. The
 * caster owns the sequence; this base supplies only the contract values.
 */
public abstract class AbstractAbility implements SkillAbility {

    private final Identifier id;
    private final String owningSkill;
    private final int baseCooldownTicks;
    private final AbilityCost cost;

    protected AbstractAbility(Identifier id, String owningSkill, int baseCooldownTicks, AbilityCost cost) {
        this.id = id;
        this.owningSkill = owningSkill;
        this.baseCooldownTicks = baseCooldownTicks;
        this.cost = cost;
    }

    @Override
    public final Identifier id() {
        return id;
    }

    @Override
    public final String owningSkill() {
        return owningSkill;
    }

    @Override
    public final int baseCooldownTicks() {
        return baseCooldownTicks;
    }

    @Override
    public final AbilityCost cost() {
        return cost;
    }
}
