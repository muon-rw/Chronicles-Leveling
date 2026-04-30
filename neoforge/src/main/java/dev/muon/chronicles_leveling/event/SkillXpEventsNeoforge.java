package dev.muon.chronicles_leveling.event;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.xp.DamageXpRouter;
import dev.muon.chronicles_leveling.skill.xp.JumpXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * NeoForge-side skill-XP routing. Three event sources feed into common
 * helpers ({@link DamageXpRouter}, {@link JumpXpHandler},
 * {@link Services#PLATFORM SpawnerOriginStore}); per-loader subscription is the
 * only thing that has to live here.
 */
@EventBusSubscriber(modid = ChroniclesLeveling.MOD_ID)
public final class SkillXpEventsNeoforge {

    private SkillXpEventsNeoforge() {}

    /**
     * Pre-mitigation damage hook. NeoForge fires this before armor/resistance
     * reductions apply, so {@link LivingIncomingDamageEvent#getAmount} is the
     * raw damage we want for both combat XP (dealt) and armor XP (taken).
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageXpRouter.onDamage(event.getEntity(), event.getSource(), event.getAmount());
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JumpXpHandler.onJump(player);
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (EntitySpawnReason.isSpawner(event.getSpawnType())) {
            Services.PLATFORM.getSpawnerOriginStore().mark(event.getEntity());
        }
    }
}
