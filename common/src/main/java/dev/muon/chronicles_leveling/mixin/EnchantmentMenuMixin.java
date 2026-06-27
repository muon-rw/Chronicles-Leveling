package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.network.NetworkDispatcher;
import dev.muon.chronicles_leveling.skill.enchant.EnchantingPerks;
import dev.muon.chronicles_leveling.skill.enchant.WizardsStudyHandler;
import dev.muon.chronicles_leveling.skill.xp.EnchantingXpHandler;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Vanilla enchanting-table backend for the enchanting perks (routes to {@code EnchantingPerks}). Covers both
 * loaders; on NeoForge with Apothic-Enchanting installed the table uses {@code ApothEnchantmentMenu}, which
 * overrides {@code clickMenuButton}/{@code slotsChanged}, so that backend gets its own gated mixin.
 *
 * <p>{@code clickMenuButton} runs on both sides: the client calls it as a pre-send affordability gate (its
 * container access is NULL, so the enchant lambda no-ops there) and the server calls it authoritatively. So
 * the Prodigy gate read below applies on both; they agree because {@code EnchantingPerks} derives the same
 * discount from synced skill data either way.
 */
@Mixin(value = EnchantmentMenu.class, remap = false)
public abstract class EnchantmentMenuMixin {

    @Shadow @Final
    public int[] costs;

    @Shadow @Final
    private DataSlot enchantmentSeed;

    @Shadow @Final
    private ContainerLevelAccess access;

    @Shadow @Final
    private Container enchantSlots;

    /** The enchanting player, captured from the menu's inventory; the menu itself holds no player reference. */
    @Unique
    private Player chronicles_leveling$player;

    /** The enchantments the in-progress click is applying (captured from the enchant lambda's roll); drives the XP grant. */
    @Unique
    private List<EnchantmentInstance> chronicles_leveling$appliedEnchants;

    /** Slot-0 item snapshot taken at {@code clickMenuButton} HEAD, so Wizard's Study only fires when the click actually changed the item (the button can return true on an empty roll without enchanting anything). */
    @Unique
    private ItemStack chronicles_leveling$preEnchantSnapshot;

    /**
     * Re-rolls a slot's would-be list through the (perk-injected) private {@code getEnchantmentList}, so the
     * Arcane Insight sync below sees the same Esoteric pool and Power-perk bumps the actual enchant will.
     */
    @Invoker("getEnchantmentList")
    abstract List<EnchantmentInstance> chronicles_leveling$rollSlot(RegistryAccess access, ItemStack itemStack, int slot, int cost);

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("TAIL"), remap = false)
    private void chronicles_leveling$capturePlayer(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        this.chronicles_leveling$player = inventory.player;
    }

    /** Capture the exact enchantments the click is applying (post Esoteric/Power perks) so the XP grant can read them. */
    @ModifyExpressionValue(method = "lambda$clickMenuButton$0",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/EnchantmentMenu;getEnchantmentList(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/item/ItemStack;II)Ljava/util/List;"),
            remap = false)
    private List<EnchantmentInstance> chronicles_leveling$captureApplied(List<EnchantmentInstance> applied) {
        this.chronicles_leveling$appliedEnchants = applied;
        return applied;
    }

    @Inject(method = "clickMenuButton", at = @At("RETURN"), remap = false)
    private void chronicles_leveling$grantEnchantingXp(
            Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        List<EnchantmentInstance> applied = this.chronicles_leveling$appliedEnchants;
        this.chronicles_leveling$appliedEnchants = null;
        if (!cir.getReturnValueZ() || !(player instanceof ServerPlayer serverPlayer)
                || applied == null || applied.isEmpty()) {
            return;
        }
        EnchantingXpHandler.onTableEnchant(serverPlayer, applied);
    }

    /**
     * Prodigy: lowers the slot's level <i>requirement</i>. The gate compares the player's level against
     * {@code costs[buttonId]} (and the slot index + 1); inflating the level the gate sees lets a slot whose
     * requirement is {@code R} be used at real level {@code R * (1 - discount)}. The seed {@code costs[buttonId]}
     * passed to {@code getEnchantmentList} (inside the lambda) is untouched, so the rolled enchant is unchanged.
     */
    @ModifyExpressionValue(method = "clickMenuButton",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Player;experienceLevel:I",
                    opcode = Opcodes.GETFIELD),
            remap = false)
    private int chronicles_leveling$prodigyGate(int realLevel, @Local(argsOnly = true) Player player) {
        return EnchantingPerks.prodigyGateLevel(player, realLevel);
    }

    /**
     * Prodigy: lowers the levels actually deducted on a successful enchant (the {@code onEnchantmentPerformed}
     * cost, which vanilla sets to the slot index + 1). Runs in the server-only enchant lambda; lapis (a
     * separate {@code consume} call) is intentionally untouched.
     */
    @ModifyArg(method = "lambda$clickMenuButton$0",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;onEnchantmentPerformed(Lnet/minecraft/world/item/ItemStack;I)V"),
            index = 1,
            remap = false)
    private int chronicles_leveling$prodigyLevelsTaken(int levels, @Local(argsOnly = true) Player player) {
        return EnchantingPerks.prodigyLevelsTaken(player, levels);
    }

    /**
     * Esoteric Enchanter: widen the candidate pool with non-curse treasure enchants before the roll. Wraps the
     * selection so both the slot clues ({@code slotsChanged}) and the actual enchant ({@code clickMenuButton})
     * draw from the same widened set. Server-only ({@code getEnchantmentList} runs inside {@code access.execute}).
     *
     * <p>Experimenter rank 2 piggybacks on the same wrap: it relaxes same-group damage/protection exclusivity for the
     * duration of {@code selectEnchantment} (read by the {@code Enchantment.areCompatible} gate), so the roll can
     * co-roll them. Scoped + exception-safe via the finally, so the relaxation never leaks past this one roll.
     */
    @WrapOperation(method = "getEnchantmentList",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;selectEnchantment(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILjava/util/stream/Stream;)Ljava/util/List;"),
            remap = false)
    private List<EnchantmentInstance> chronicles_leveling$widenAndRelaxRoll(
            RandomSource random, ItemStack itemStack, int cost, Stream<Holder<Enchantment>> source,
            Operation<List<EnchantmentInstance>> original, @Local(argsOnly = true) RegistryAccess access) {
        Player player = this.chronicles_leveling$player;
        Stream<Holder<Enchantment>> widened = EnchantingPerks.esotericSource(player, access, source);
        if (!EnchantingPerks.relaxesTableExclusivity(player)) {
            return original.call(random, itemStack, cost, widened);
        }
        EnchantingPerks.setTableExclusivityRelaxed(true);
        try {
            return original.call(random, itemStack, cost, widened);
        } finally {
            EnchantingPerks.setTableExclusivityRelaxed(false);
        }
    }

    /**
     * Abundance then Unstable / Unlimited Power on the final roll: Abundance adds extra compatible enchants, then
     * Power bumps every enchant's level past its max (skipping max-level-1). Both are seeded by the slot's
     * enchantment seed, so the clue preview and the applied enchant agree. Order matters: Abundance first so the
     * newly-added enchants also get the Power bump.
     */
    @ModifyReturnValue(method = "getEnchantmentList", at = @At("RETURN"), remap = false)
    private List<EnchantmentInstance> chronicles_leveling$abundanceAndPower(
            List<EnchantmentInstance> rolled,
            @Local(argsOnly = true) RegistryAccess access,
            @Local(argsOnly = true) ItemStack itemStack,
            @Local(argsOnly = true, ordinal = 0) int slot,
            @Local(argsOnly = true, ordinal = 1) int cost) {
        long seed = this.enchantmentSeed.get() + slot;
        Player player = this.chronicles_leveling$player;
        List<EnchantmentInstance> available = EnchantingPerks.vanillaAbundanceCandidates(player, itemStack, cost, access);
        List<EnchantmentInstance> withAbundance = EnchantingPerks.applyAbundance(player, seed, rolled, available);
        return EnchantingPerks.applyPowerPerks(player, seed, withAbundance);
    }

    /**
     * Arcane Insight: push the full per-slot would-be lists to the owning client. The client can't replay the
     * roll (the enchant seed is truncated to 16 bits over the data-slot sync), so the server sends the exact
     * lists; only a perk-holder gets them. Runs in {@code access.execute}, which no-ops on the client, so this
     * is inherently server-side. Re-rolling here mutates {@code random}, harmless at TAIL (every roll re-seeds).
     */
    @Inject(method = "slotsChanged", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$syncArcaneInsightClues(Container container, CallbackInfo ci) {
        if (container != this.enchantSlots || !(this.chronicles_leveling$player instanceof ServerPlayer serverPlayer)
                || EnchantingPerks.arcaneInsightReveal(serverPlayer) <= 0) {
            return;
        }
        this.access.execute((level, pos) -> {
            ItemStack item = this.enchantSlots.getItem(0);
            List<List<EnchantmentInstance>> slots = new ArrayList<>(this.costs.length);
            for (int slot = 0; slot < this.costs.length; slot++) {
                slots.add(this.costs[slot] > 0 && !item.isEmpty() && item.isEnchantable()
                        ? List.copyOf(this.chronicles_leveling$rollSlot(level.registryAccess(), item, slot, this.costs[slot]))
                        : List.of());
            }
            NetworkDispatcher.sendArcaneInsightClues(serverPlayer,
                    ((AbstractContainerMenu) (Object) this).containerId, slots);
        });
    }

    /** Snapshot slot 0 before the click so Wizard's Study can tell an actual enchant from a button-press that changed nothing. */
    @Inject(method = "clickMenuButton", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$snapshotPreEnchant(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        this.chronicles_leveling$preEnchantSnapshot = this.enchantSlots.getItem(0).copy();
    }

    /**
     * Wizard's Study: on a successful enchant, record the table use and (if this is the player's most-used
     * table) bake the +10% base-stat bonus into the result. Runs server-side via {@code access.execute}; the
     * bonus is applied to the freshly-enchanted item in slot 0, then re-broadcast so the client sees it.
     *
     * <p>Gated on the slot-0 item actually changing: {@code clickMenuButton} returns true even on an empty roll
     * (nothing enchanted), which would otherwise record a phantom use and bake the bonus onto an untouched item.
     */
    @Inject(method = "clickMenuButton", at = @At("RETURN"), remap = false)
    private void chronicles_leveling$wizardsStudy(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        ItemStack before = this.chronicles_leveling$preEnchantSnapshot;
        this.chronicles_leveling$preEnchantSnapshot = null;
        if (!cir.getReturnValueZ() || before == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        this.access.execute((level, pos) -> {
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }
            ItemStack enchanted = this.enchantSlots.getItem(0);
            if (!enchanted.isEmpty() && !ItemStack.matches(before, enchanted)) {
                WizardsStudyHandler.onEnchant(serverPlayer, GlobalPos.of(serverLevel.dimension(), pos), enchanted);
            }
        });
        ((AbstractContainerMenu) (Object) this).broadcastChanges();
    }
}
