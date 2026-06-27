package dev.muon.chronicles_leveling.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * Speech "Pacify" marker. Goal-driven aggro is denied by the canAttack / setTarget / TargetingConditions mixins,
 * but brain-driven mobs (Warden, Piglin, Hoglin) read their attack target straight from brain memory. So each tick
 * this erases the relevant memories; tickEffects runs before the brain ticks within a game tick, so the memory is
 * gone before any attack behavior reads it. eraseMemory is a no-op for a brain that lacks the slot.
 */
public class PacifiedMobEffect extends MobEffect {

    public PacifiedMobEffect() {
        super(MobEffectCategory.HARMFUL, 0x9BB7D4);   // calm pale blue
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplifier) {
        if (mob instanceof Mob m) {
            Brain<?> brain = m.getBrain();
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
            brain.eraseMemory(MemoryModuleType.ROAR_TARGET);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingTicks, int amplifier) {
        return true;
    }
}
