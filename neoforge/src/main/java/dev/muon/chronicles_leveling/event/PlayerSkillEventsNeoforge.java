package dev.muon.chronicles_leveling.event;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.SkillModifierApplier;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import dev.muon.chronicles_leveling.skill.ability.runtime.HeldCastDriver;
import dev.muon.chronicles_leveling.skill.combat.CombatProcRouter;
import dev.muon.chronicles_leveling.skill.combat.QuickBladeHandler;
import dev.muon.chronicles_leveling.skill.enchant.EssenceHoarderHandler;
import dev.muon.chronicles_leveling.skill.enchant.WizardsStudyHandler;
import dev.muon.chronicles_leveling.skill.social.WanderingEyeSpawns;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The skill-keyed twin of {@link PlayerStatsEventsNeoforge}, on the GAME bus. Separate from the stat
 * lifecycle since skill recompute is independent: skill modifiers never target the stat attributes,
 * validated at registry freeze.
 */
@EventBusSubscriber(modid = ChroniclesLeveling.MOD_ID)
public final class PlayerSkillEventsNeoforge {

    private PlayerSkillEventsNeoforge() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerSkillManager.reconcile(player);
        SkillModifierApplier.recompute(player);
        PlayerSkillManager.reconcileAbilityBindings(player);
        WizardsStudyHandler.syncTarget(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SkillModifierApplier.recompute(player);
        WizardsStudyHandler.syncTarget(player);
        // The dynamic-modifier snapshots refer to the dead entity, whose transient attribute
        // modifiers don't survive respawn; drop them so the next equipment event or tick re-derives
        // onto the new entity.
        EssenceHoarderHandler.clear(player.getUUID());
        QuickBladeHandler.clear(player.getUUID());
        HeldCastDriver.clear(player.getUUID());   // an immediate respawn rebuilds the same-UUID player alive before the tick death-guard sees it
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        // NeoForge's AttachmentInternals.onPlayerClone is a NORMAL-priority @SubscribeEvent on
        // this same PlayerEvent.Clone; with equal priority its order vs. ours is unguaranteed.
        // So we read event.getOriginal() (always populated) and copy explicitly: order-independent,
        // and idempotent with the loader's own copyOnDeath copy (which reads the same source).
        if (event.getOriginal() instanceof ServerPlayer oldPlayer) {
            PlayerSkillData data = PlayerSkillManager.get(oldPlayer);
            PlayerSkillManager.set(newPlayer, data);
        }
        SkillModifierApplier.recompute(newPlayer);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillEffects.clear(player.getUUID());
            AbilityWindowStore.clear(player.getUUID());   // drop transient ability windows on logout
            HeldCastDriver.clear(player.getUUID());   // drop any in-progress held cast
            CombatProcRouter.clear(player.getUUID());   // drop the Momentum streak
            EssenceHoarderHandler.clear(player.getUUID());   // drop the dynamic-modifier snapshot
            QuickBladeHandler.clear(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        AbilityWindowStore.tick(event.getServer());
        HeldCastDriver.tick(event.getServer());
        EssenceHoarderHandler.tick(event.getServer());
        QuickBladeHandler.tick(event.getServer());
        WanderingEyeSpawns.tick(event.getServer());
    }
}
