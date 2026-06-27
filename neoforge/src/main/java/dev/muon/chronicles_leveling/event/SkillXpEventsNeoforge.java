package dev.muon.chronicles_leveling.event;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.combat.CombatProcRouter;
import dev.muon.chronicles_leveling.skill.gather.GatherProcRouter;
import dev.muon.chronicles_leveling.skill.mobility.AcrobaticsHandler;
import dev.muon.chronicles_leveling.skill.xp.DamageXpRouter;
import dev.muon.chronicles_leveling.skill.xp.EnchantingXpHandler;
import dev.muon.chronicles_leveling.skill.xp.JumpXpHandler;
import dev.muon.chronicles_leveling.skill.xp.SmithingXpHandler;
import dev.muon.chronicles_leveling.skill.xp.SpeechXpHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = ChroniclesLeveling.MOD_ID)
public final class SkillXpEventsNeoforge {

    private SkillXpEventsNeoforge() {}

    /** Pre-armor outgoing scalar; fires inside the hurt sequence, after CA's crit (Fabric parity: a
     *  {@code @WrapOperation} on {@code getDamageAfterArmorAbsorb}). */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            event.setAmount(CombatProcRouter.modifyOutgoing(
                    attacker, event.getEntity(), event.getSource(), event.getAmount()));
        }
    }

    /** Roll fall mitigation then Pain Tolerance's cap, on the post-reduction damage (Fabric parity: an
     *  {@code @ModifyExpressionValue} on {@code getDamageAfterMagicAbsorb}). */
    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer victim) {
            float reduced = AcrobaticsHandler.reduceFallDamage(victim, event.getSource(), event.getNewDamage());
            event.setNewDamage(CombatProcRouter.capIncoming(victim, event.getSource(), reduced));
        }
    }

    /** Server-thread only; entries are consumed by {@link #onDamagePost} later in the same hurt sequence. */
    private static final Set<Integer> TOTEM_SAVED = new HashSet<>();

    @SubscribeEvent
    public static void onTotemUsed(LivingUseTotemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TOTEM_SAVED.add(player.getId());
        }
    }

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        var victim = event.getEntity();
        var source = event.getSource();

        if (source.getEntity() instanceof ServerPlayer attacker) {
            DamageXpRouter.onDamageDealt(attacker, victim, source, event.getInflictedDamage());
            CombatProcRouter.onHitDealt(attacker, victim, source, event.getInflictedDamage());
        }

        if (victim instanceof ServerPlayer serverVictim && !victim.isDeadOrDying()) {
            boolean totemSaved = TOTEM_SAVED.remove(victim.getId());
            // Pre-absorption-split inflicted damage; matches the Fabric setHealth seam's value, so
            // Retribution reflects the same magnitude on both loaders.
            float realised = event.getInflictedDamage();
            if (realised > 0f) {
                // Reactive procs fire on whatever got through, even a partial shield block or a hit that
                // popped a totem: the player survived, so Last Stand and the counters react.
                CombatProcRouter.onHitTaken(serverVictim, source, realised);
                // Taken XP, like the Fabric AFTER_DAMAGE path, skips any shield-blocked hit. Totem-saved
                // hits are also skipped: taken XP is pre-mitigation, so a totem-tanked lethal hit would
                // grant runaway Defense XP.
                if (event.getBlockedDamage() <= 0f && !totemSaved) {
                    DamageXpRouter.onDamageTaken(victim, source, event.getOriginalDamage());
                }
            }
        }
    }

    /** Kill procs (Toxicologist) credited to the killer; fires after the totem revive declines, so a saved victim doesn't count. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            CombatProcRouter.onKill(killer, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JumpXpHandler.onJump(player);
            AcrobaticsHandler.onJump(player);
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
        // Mining + Herbalism block-break XP rides the common ServerPlayerGameModeMixin, shared with Fabric; this
        // event keeps only the loot procs, which genuinely need the drops list.

        // Gathering loot procs: adapt the pre-spawn ItemEntity list through the shared router (Fabric
        // wraps Block#getDrops for the same effect).
        List<ItemEntity> entities = event.getDrops();
        int originalCount = entities.size();
        List<ItemStack> stacks = new ArrayList<>(originalCount);
        for (ItemEntity entity : entities) {
            stacks.add(entity.getItem());
        }
        ServerLevel level = event.getLevel();
        BlockPos pos = event.getPos();
        if (GatherProcRouter.modifyDrops(player, level, pos, state, event.getTool(), stacks)) {
            // The router only replaces in place (Smelter's Touch) and appends (bonus copies / pool
            // items), never removes, so the prefix maps 1:1 to the original entities. Reuse those
            // (preserving their pickup delay, scatter, motion, and any foreign-mod state); only the
            // appended tail is new.
            for (int i = 0; i < originalCount; i++) {
                entities.get(i).setItem(stacks.get(i));
            }
            for (int i = originalCount; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                if (!stack.isEmpty()) {
                    ItemEntity extra = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                    extra.setDefaultPickUpDelay();
                    entities.add(extra);
                }
            }
        }
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

}
