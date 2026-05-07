package dev.muon.chronicles_leveling.event;

import dev.muon.chronicles_leveling.skill.xp.DamageXpRouter;
import dev.muon.chronicles_leveling.skill.xp.FarmingXpHandler;
import dev.muon.chronicles_leveling.skill.xp.MiningXpHandler;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric-side skill-XP routing for events that exist in the Fabric API.
 * Hooks without a Fabric API event ride mixins instead — see the
 * {@code dev.muon.chronicles_leveling.mixin} package on this side.
 */
public final class SkillXpEventsFabric {

    private SkillXpEventsFabric() {}

    public static void initLifecycle() {
        // AFTER_DAMAGE skips killing blows by API contract; dealt XP rides
        // LivingEntityActuallyHurtMixin instead, since damageTaken here isn't
        // truly post-mitigation. Dodge-style mid-pipeline zeroing isn't
        // detectable from this event — most evasion mods cancel ALLOW_DAMAGE
        // upstream, so AFTER_DAMAGE doesn't fire in those cases.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!(entity instanceof ServerPlayer)) return;
            if (blocked) return;
            DamageXpRouter.onDamageTaken(entity, source, baseDamageTaken);
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            MiningXpHandler.onBlockBreak(serverPlayer, state, serverPlayer.getMainHandItem());
            FarmingXpHandler.onBlockBreak(serverPlayer, state);
        });
    }
}
