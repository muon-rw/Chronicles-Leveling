package dev.muon.chronicles_leveling.skill.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * Centralized friend-or-foe check. The mod has no hostile/passive classification; its only notion of "enemy" is
 * "a living, non-spectator entity not allied to the source" (vanilla {@code isAlliedTo}: team, or same owner for
 * tamed mobs). Routed through here by Ricochet, Seismic Slam, Kindred Fury, and Pacify so there is one definition.
 */
public final class TargetAllegiance {

    private TargetAllegiance() {}

    /** A valid hostile target for {@code source}: a different, living, non-spectator entity not allied to it. */
    public static boolean isHostileTarget(Entity source, Entity candidate) {
        return candidate != source && candidate.isAlive() && !candidate.isSpectator() && !source.isAlliedTo(candidate);
    }

    /** A mob Pacify can quiet: a non-allied {@link Mob} currently engaged (a live target, or brain anger/roar windup). */
    public static boolean isPacifiable(Entity source, Entity candidate) {
        return candidate instanceof Mob mob && isHostileTarget(source, mob) && isEngaged(mob);
    }

    /** Brain mobs (Warden, Piglin) aggro through memory before {@code getTarget()} resolves; catch those windup states too. */
    private static boolean isEngaged(Mob mob) {
        if (mob.getTarget() != null) {
            return true;
        }
        Brain<?> brain = mob.getBrain();
        return brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)
                || brain.hasMemoryValue(MemoryModuleType.ROAR_TARGET);
    }
}
