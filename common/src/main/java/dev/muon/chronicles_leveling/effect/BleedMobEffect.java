package dev.muon.chronicles_leveling.effect;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.combat.CombatProcRouter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Weaponry "Rend": a stacking bleed damage-over-time. Each stack is the effect's amplifier
 * (amplifier 0 = one stack), so a slashing hit that re-applies the effect at a higher amplifier
 * deepens the bleed; {@link CombatProcRouter} owns the stack-on-reapply logic and the
 * applier bookkeeping that lets each tick credit the player who inflicted the bleed.
 *
 * <p>Ticks once per second and deals {@code BASE + PER_STACK * amplifier} damage. Unlike vanilla
 * poison it CAN kill, and a lethal tick is credited to the applying player via the attacker-carrying
 * source from {@link CombatProcRouter#bleedSource} (loot, advancements, kill tracking all attribute
 * correctly). The source is generic-typed, so bleed is still mitigated by armor.
 */
public class BleedMobEffect extends MobEffect {

    private static final int TICK_INTERVAL = 20;         // once per second

    public BleedMobEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B1A1A);      // dark blood red (HUD/particle tint)
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplifier) {
        var weaponry = Configs.SKILLS.weaponry;
        float damage = (float) (weaponry.rendBleedDamageBase.get() + weaponry.rendBleedDamagePerStack.get() * amplifier);
        DamageSource source = CombatProcRouter.bleedSource(level, mob);
        mob.hurtServer(level, source, damage);
        if (mob.isDeadOrDying()) {
            CombatProcRouter.clearBleedAttacker(mob);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingTicks, int amplifier) {
        return remainingTicks % TICK_INTERVAL == 0;
    }
}
