package dev.muon.chronicles_leveling.event;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.xp.DamageXpRouter;
import dev.muon.chronicles_leveling.skill.xp.EnchantingXpHandler;
import dev.muon.chronicles_leveling.skill.xp.FarmingXpHandler;
import dev.muon.chronicles_leveling.skill.xp.FishingXpHandler;
import dev.muon.chronicles_leveling.skill.xp.JumpXpHandler;
import dev.muon.chronicles_leveling.skill.xp.MiningXpHandler;
import dev.muon.chronicles_leveling.skill.xp.SmithingXpHandler;
import dev.muon.chronicles_leveling.skill.xp.SpeechXpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

@EventBusSubscriber(modid = ChroniclesLeveling.MOD_ID)
public final class SkillXpEventsNeoforge {

    private SkillXpEventsNeoforge() {}

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        var victim = event.getEntity();
        var source = event.getSource();

        if (source.getEntity() instanceof ServerPlayer attacker) {
            DamageXpRouter.onDamageDealt(attacker, victim, source, event.getNewDamage());
        }

        if (victim instanceof ServerPlayer && !victim.isDeadOrDying()) {
            boolean parried = event.getBlockedDamage() > 0f;
            boolean reachedPool = event.getNewDamage()
                    + event.getReduction(DamageContainer.Reduction.ABSORPTION) > 0f;
            if (!parried && reachedPool) {
                DamageXpRouter.onDamageTaken(victim, source, event.getOriginalDamage());
            }
        }
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

    /** BlockDropsEvent fires post-break with the actual tool, so silk-touch detection works
     *  and creative-mode breaks (no drops) skip past us automatically. */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;
        BlockState state = event.getState();
        MiningXpHandler.onBlockBreak(player, state, event.getTool());
        FarmingXpHandler.onBlockBreak(player, state);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FarmingXpHandler.onPlant(player, event.getPlacedBlock());
    }

    @SubscribeEvent
    public static void onGrindstoneTake(GrindstoneEvent.OnTakeItem event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            EnchantingXpHandler.onGrindstoneTake(player, event.getXp());
        }
    }

    @SubscribeEvent
    public static void onAnvilTake(AnvilCraftEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EnchantingXpHandler.onAnvilTake(player, event.getMenu().getCost());
        }
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpeechXpHandler.onTrade(player, event.getMerchantOffer());
        }
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SmithingXpHandler.onCraft(player, event.getCrafting());
        }
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FishingXpHandler.onItemFished(player, event.getDrops());
        }
    }
}
