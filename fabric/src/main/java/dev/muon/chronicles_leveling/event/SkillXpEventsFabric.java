package dev.muon.chronicles_leveling.event;

import dev.muon.chronicles_leveling.skill.xp.DamageXpRouter;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

/**
 * Fabric-side skill-XP routing. Damage routes through Fabric's
 * {@code AFTER_DAMAGE} (post-shield, pre-armor — see {@link DamageXpRouter}'s
 * comments for the cross-loader timing trade-off).
 *
 * <p>Per-jump acrobatics XP and spawner-origin marking are mixin-driven on
 * Fabric (see {@code PlayerJumpMixin}, {@code SpawnerOriginMixin}) because
 * Fabric ships no equivalent event for either; nothing to wire here for them.
 */
public final class SkillXpEventsFabric {

    private SkillXpEventsFabric() {}

    public static void initLifecycle() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) ->
                DamageXpRouter.onDamage(entity, source, baseDamageTaken)
        );
    }
}
