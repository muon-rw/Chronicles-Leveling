package dev.muon.chronicles_leveling.skill.alchemy;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.AlchemySkill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Toxicologist: a player-credited kill on a victim afflicted by HARMFUL effects spreads those effects to the
 * nearest enemies. Instant harm (e.g. a Harming splash) leaves no active effect on the corpse, so the splash/
 * cloud hooks stamp the victim's last instant harmful hit here ({@link #recordInstantHarm}); a kill landing
 * within the stamp window counts as afflicted and re-deals the instant effect to the spread targets.
 */
public final class ToxicologistSpread {

    private ToxicologistSpread() {}

    private record InstantHarm(MobEffect effect, int amplifier, long tick) {}

    /** Victim entity id -> last instant harmful hit by a player. Server thread only; pruned on every record. */
    private static final Map<Integer, InstantHarm> RECENT_INSTANT_HARM = new HashMap<>();

    /** Instant damage kills inside the applying call stack, so the stamp only needs to survive the same tick. */
    private static final long STAMP_WINDOW_TICKS = 2;

    public static void recordInstantHarm(LivingEntity victim, MobEffect effect, int amplifier) {
        long now = victim.level().getGameTime();
        RECENT_INSTANT_HARM.values().removeIf(hit -> now - hit.tick() > STAMP_WINDOW_TICKS);
        RECENT_INSTANT_HARM.put(victim.getId(), new InstantHarm(effect, amplifier, now));
    }

    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        InstantHarm instant = RECENT_INSTANT_HARM.remove(victim.getId());
        if (!SkillEffects.has(killer, AlchemySkill.TOXICOLOGIST) || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        if (instant != null && level.getGameTime() - instant.tick() > STAMP_WINDOW_TICKS) {
            instant = null;
        }
        List<MobEffectInstance> active = victim.getActiveEffects().stream()
                .filter(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                .toList();
        if (active.isEmpty() && instant == null) {
            return;
        }

        var cfg = Configs.SKILLS.alchemy;
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                        victim.getBoundingBox().inflate(cfg.toxicologistSpreadRadius.get())).stream()
                .filter(entity -> entity != victim && entity != killer && entity.isAlive()
                        && entity.attackable() && !(entity instanceof Player))
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(victim)))
                .limit(cfg.toxicologistSpreadTargets.get())
                .toList();
        for (LivingEntity target : targets) {
            for (MobEffectInstance effect : active) {
                target.addEffect(new MobEffectInstance(effect.getEffect(), effect.getDuration(),
                        effect.getAmplifier()), killer);
            }
            if (instant != null) {
                instant.effect().applyInstantenousEffect(level, null, killer, target, instant.amplifier(), 1.0);
            }
        }
    }
}
