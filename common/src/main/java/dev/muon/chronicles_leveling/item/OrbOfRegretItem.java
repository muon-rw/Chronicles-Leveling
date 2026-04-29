package dev.muon.chronicles_leveling.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Right-click in air opens the level-up screen in <i>reset mode</i>: the player
 * picks one stat, hits ENTER, and gets every point spent on that stat refunded
 * into their unspent pool. The orb is consumed only on confirm — ESC / closing
 * the screen / picking a different stat all leave the orb intact.
 *
 * <p>Stacks to 1 by design — these are meant to be a deliberate, costly
 * inventory commitment, not a reservoir of free respecs.
 *
 * <p>The actual mutation is server-side: see
 * {@link dev.muon.chronicles_leveling.network.message.ResetStatPacket}. This
 * class only opens the screen on the client and consumes no input on the
 * server side. The server validates that the player still holds an orb in the
 * declared hand at packet time, so swapping the orb away after right-clicking
 * but before confirming is safely rejected.
 */
public class OrbOfRegretItem extends Item {

    public OrbOfRegretItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    @NotNull
    public InteractionResult use(@NotNull net.minecraft.world.level.Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (level.isClientSide()) {
            openResetScreen(hand);
        }
        // CONSUME (not SUCCESS) — no swing animation; we're entering a modal flow,
        // not performing a flashy ack.
        return InteractionResult.CONSUME;
    }

    /**
     * Client-only screen open. Kept as a private static method so loading
     * {@link OrbOfRegretItem} on a dedicated server doesn't resolve the
     * client-only {@code LevelUpScreen} symbol — the JVM only verifies this
     * method when it's first invoked, which only happens on the client.
     */
    private static void openResetScreen(InteractionHand hand) {
        Minecraft.getInstance().setScreen(
                new dev.muon.chronicles_leveling.client.screen.LevelUpScreen(
                        new dev.muon.chronicles_leveling.client.screen.LevelUpScreen.ResetContext(hand)));
    }

    @Override
    @Deprecated
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context,
                                @NotNull TooltipDisplay tooltipDisplay,
                                @NotNull Consumer<Component> tooltipAdder, @NotNull TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.chronicles_leveling.lesser_orb_of_regret.tooltip.line1")
                .withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.translatable("item.chronicles_leveling.lesser_orb_of_regret.tooltip.line2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
