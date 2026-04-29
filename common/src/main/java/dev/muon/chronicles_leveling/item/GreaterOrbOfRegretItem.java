package dev.muon.chronicles_leveling.item;

import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import dev.muon.chronicles_leveling.sounds.ModSounds;
import dev.muon.chronicles_leveling.stat.StatModifierApplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Right-click to wipe every stat allocation and refund the lot. Two-step
 * confirmation: the first use arms a 5-second window and shows the refund
 * preview in the action bar; a second use inside that window commits the
 * reset and consumes the orb.
 *
 * <p>Confirmation state lives in a transient server-side {@link #ARMED_UNTIL}
 * map keyed by player UUID — no item-stack data component, no persistence.
 * Logging out / dying / waiting it out clears it. Each player can only have
 * one armed orb at a time (any second use commits, regardless of which orb
 * stack it came from), which is fine — the gesture is "I want to do this
 * irreversible thing twice on purpose," not "this specific stack is primed."
 */
public class GreaterOrbOfRegretItem extends Item {

    private static final long CONFIRM_WINDOW_MS = 5_000L;

    /**
     * UUID → wall-clock ms after which the armed window expires.
     * Entries are server-thread-only; a single-player save and a dedicated
     * server both hit this from the main server thread, so a plain HashMap is fine.
     */
    private static final Map<UUID, Long> ARMED_UNTIL = new HashMap<>();

    public GreaterOrbOfRegretItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    @NotNull
    public InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        long now = System.currentTimeMillis();
        UUID uuid = serverPlayer.getUUID();
        Long armedUntil = ARMED_UNTIL.get(uuid);

        if (armedUntil != null && armedUntil >= now) {
            commitReset(serverPlayer, hand);
            ARMED_UNTIL.remove(uuid);
            return InteractionResult.CONSUME;
        }

        // Either not armed or the previous window expired — arm now.
        int totalRefund = totalAllocation(serverPlayer);
        if (totalRefund <= 0) {
            serverPlayer.sendOverlayMessage(
                    Component.translatable("item.chronicles_leveling.greater_orb_of_regret.nothing_to_reset")
                            .withStyle(ChatFormatting.YELLOW)
            );
            return InteractionResult.CONSUME;
        }
        ARMED_UNTIL.put(uuid, now + CONFIRM_WINDOW_MS);
        serverPlayer.sendOverlayMessage(
                Component.translatable("item.chronicles_leveling.greater_orb_of_regret.confirm_prompt", totalRefund)
                        .withStyle(ChatFormatting.YELLOW)
        );
        return InteractionResult.CONSUME;
    }

    private static void commitReset(ServerPlayer player, InteractionHand hand) {
        // Re-validate at commit time: the player could have shifted things around
        // mid-window. We trust the held stack is still a Greater Orb because the
        // path that calls this is `use()` of this exact item, but allocation totals
        // can change (admin /chronicles command, another mod's modifier), so refund
        // off the current data, not what we showed in the prompt.
        PlayerLevelData data = PlayerLevelManager.get(player);
        int refund = data.allocations().values().stream().mapToInt(Integer::intValue).sum();

        PlayerLevelManager.set(player, data
                .withAllocationsCleared()
                .withUnspentPoints(data.unspentPoints() + refund));
        StatModifierApplier.recompute(player);

        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            held.shrink(1);
        }

        player.sendOverlayMessage(
                Component.translatable("item.chronicles_leveling.greater_orb_of_regret.success", refund)
                        .withStyle(ChatFormatting.GREEN)
        );
        player.level().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                ModSounds.LEVEL_UP.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static int totalAllocation(ServerPlayer player) {
        return PlayerLevelManager.get(player).allocations().values().stream()
                .mapToInt(Integer::intValue).sum();
    }

    @Override
    @Deprecated
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context,
                                @NotNull TooltipDisplay tooltipDisplay,
                                @NotNull Consumer<Component> tooltipAdder, @NotNull TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.chronicles_leveling.greater_orb_of_regret.tooltip.line1")
                .withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.translatable("item.chronicles_leveling.greater_orb_of_regret.tooltip.line2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
