package dev.muon.chronicles_leveling.item;

import dev.muon.chronicles_leveling.config.ConfigStats;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import dev.muon.chronicles_leveling.sounds.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Single-use book that grants 1 character level and 1 unspent stat point per
 * use. No XP cost, no curve, no confirmation — direct admin-style progression
 * the player can stack and chew through whenever they want.
 *
 * <p>Respects {@link ConfigStats#maxLevel}:
 * at or above the cap the use is rejected with an action-bar message and the
 * tome is not consumed.
 */
public class TomeItem extends Item {

    public TomeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    @NotNull
    public InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        PlayerLevelData data = PlayerLevelManager.get(serverPlayer);
        int maxLevel = Configs.STATS.maxLevel.get();
        if (maxLevel > 0 && data.level() >= maxLevel) {
            serverPlayer.sendOverlayMessage(
                    Component.translatable("item.chronicles_leveling.tome.max_level", maxLevel)
                            .withStyle(ChatFormatting.YELLOW)
            );
            return InteractionResult.FAIL;
        }

        int newLevel = data.level() + 1;
        int newPoints = data.unspentPoints() + 1;
        PlayerLevelManager.set(serverPlayer, new PlayerLevelData(newLevel, newPoints, data.allocations()));

        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            held.shrink(1);
        }

        serverPlayer.sendOverlayMessage(
                Component.translatable("item.chronicles_leveling.tome.success", newLevel)
                        .withStyle(ChatFormatting.GREEN)
        );
        serverPlayer.level().playSound(null,
                serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                ModSounds.LEVEL_UP.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
        return InteractionResult.CONSUME;
    }

    @Override
    @Deprecated
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context,
                                @NotNull TooltipDisplay tooltipDisplay,
                                @NotNull Consumer<Component> tooltipAdder, @NotNull TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.chronicles_leveling.tome.tooltip.line1")
                .withStyle(ChatFormatting.GRAY));
    }
}
