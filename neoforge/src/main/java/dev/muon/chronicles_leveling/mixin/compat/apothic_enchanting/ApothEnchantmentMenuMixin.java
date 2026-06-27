package dev.muon.chronicles_leveling.mixin.compat.apothic_enchanting;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.skill.enchant.EnchantingPerks;
import dev.muon.chronicles_leveling.skill.enchant.WizardsStudyHandler;
import dev.muon.chronicles_leveling.skill.xp.EnchantingXpHandler;
import dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentHelper;
import dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Apothic-Enchanting backend for the Enchanting perks (NeoForge-only; gated by the {@code compat.apothic_enchanting}
 * package via {@code MixinConfigPluginNeoforge}). {@code ApothEnchantmentMenu} overrides the vanilla enchant flow,
 * so the vanilla {@link dev.muon.chronicles_leveling.mixin.EnchantmentMenuMixin} doesn't reach it, so these thin
 * hooks route through the same {@code EnchantingPerks}:
 * <ul>
 *   <li>Prodigy: the level-requirement gate, and the cost charged (Apothic charges raw XP via getExpCostForSlot).</li>
 *   <li>Unstable/Unlimited Power: the level bumps on Apothic's own {@code getEnchantmentList} (drives both its
 *       clue preview and the actual roll, so they agree).</li>
 *   <li>Arcane Insight: Apothic already syncs extra clues per slot ({@code CluePayload}) up to {@code stats.clues()};
 *       this raises that count so more of the roll is revealed.</li>
 *   <li>Esoteric Enchanter: Apothic gates treasure on the immutable {@code EnchantmentTableStats.treasure()}
 *       record field, so the perk rebuilds the gathered stats with {@code treasure = true} (which also flips the
 *       table's UI to show treasure is enabled).</li>
 * </ul>
 *
 * <p>The {@code @Mixin} target is a string so the plugin can disable this class entirely when
 * {@code apothic_enchanting} is absent, and because it's then never applied, the {@code EnchantmentTableStats}
 * reference below (Apothic is a {@code compileOnly} dep) never loads. {@code remap = false} (NeoForge runs Mojmap).
 */
@Mixin(targets = "dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentMenu", remap = false)
public abstract class ApothEnchantmentMenuMixin {

    @Shadow @Final
    protected Player player;

    @Shadow @Final
    protected BlockPos pos;

    @Shadow
    protected EnchantmentTableStats stats;

    /** The enchantments the in-progress Apothic click is applying, captured from its enchant lambda; drives the XP grant. */
    @Unique
    private List<EnchantmentInstance> chronicles_leveling$appliedEnchants;

    /** Slot-0 item snapshot taken at {@code clickMenuButton} HEAD, so Wizard's Study only fires when the click actually changed the item (Apothic returns true on an empty roll or a failed infusion without changing it). */
    @Unique
    private ItemStack chronicles_leveling$preEnchantSnapshot;

    /** Capture the exact roll Apothic applies (post perks) so Enchanting XP can be granted (the vanilla menu mixin doesn't reach here). */
    @ModifyExpressionValue(method = "lambda$clickMenuButton$0",
            at = @At(value = "INVOKE",
                    target = "Ldev/shadowsoffire/apothic_enchanting/table/ApothEnchantmentMenu;getEnchantmentList(Lnet/minecraft/world/item/ItemStack;II)Ljava/util/List;"))
    private List<EnchantmentInstance> chronicles_leveling$captureApplied(List<EnchantmentInstance> applied) {
        this.chronicles_leveling$appliedEnchants = applied;
        return applied;
    }

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void chronicles_leveling$grantEnchantingXp(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        List<EnchantmentInstance> applied = this.chronicles_leveling$appliedEnchants;
        this.chronicles_leveling$appliedEnchants = null;
        if (cir.getReturnValueZ() && player instanceof ServerPlayer serverPlayer && applied != null && !applied.isEmpty()) {
            EnchantingXpHandler.onTableEnchant(serverPlayer, applied);
        }
    }

    /** Snapshot slot 0 before the click so Wizard's Study can tell a real enchant from a button-press that changed nothing (empty roll / failed infusion). */
    @Inject(method = "clickMenuButton", at = @At("HEAD"))
    private void chronicles_leveling$snapshotPreEnchant(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        this.chronicles_leveling$preEnchantSnapshot = ((AbstractContainerMenu) (Object) this).getSlot(0).getItem().copy();
    }

    /**
     * Wizard's Study: Apothic overrides {@code clickMenuButton}, so the vanilla {@code EnchantmentMenuMixin} hook
     * never fires for an Apothic table (or a vanilla table while Apothic is installed, since Apothic serves the menu
     * for all enchanting tables). Mirror it here: record the table use and bake the most-used bonus into the result.
     * The enchanted item is menu slot 0 (Apothic writes the result to {@code enchantSlots[0]}); {@code pos} is the
     * table. {@code broadcastChanges} pushes the baked bonus to the client. Server-only via the ServerPlayer gate.
     *
     * <p>Gated on the slot-0 item actually changing: Apothic returns true even when the roll was empty or an
     * infusion failed to match, which would otherwise record a phantom use and bake the bonus onto an untouched item.
     */
    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void chronicles_leveling$wizardsStudy(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        ItemStack before = this.chronicles_leveling$preEnchantSnapshot;
        this.chronicles_leveling$preEnchantSnapshot = null;
        if (!cir.getReturnValueZ() || before == null || this.pos == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack enchanted = ((AbstractContainerMenu) (Object) this).getSlot(0).getItem();
        if (!enchanted.isEmpty() && !ItemStack.matches(before, enchanted)) {
            WizardsStudyHandler.onEnchant(serverPlayer, GlobalPos.of(serverPlayer.level().dimension(), this.pos), enchanted);
        }
        ((AbstractContainerMenu) (Object) this).broadcastChanges();
    }

    /** Prodigy: lower the slot's level requirement; the seed {@code costs[id]} passed to the roll is untouched. */
    @ModifyExpressionValue(method = "clickMenuButton",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Player;experienceLevel:I",
                    opcode = Opcodes.GETFIELD))
    private int chronicles_leveling$prodigyGate(int realLevel, @Local(argsOnly = true) Player player) {
        return EnchantingPerks.prodigyGateLevel(player, realLevel);
    }

    /** Prodigy: cut the XP Apothic charges on a successful enchant (the {@code getExpCostForSlot} result). */
    @ModifyExpressionValue(method = "lambda$clickMenuButton$0",
            at = @At(value = "INVOKE",
                    target = "Ldev/shadowsoffire/apothic_enchanting/util/MiscUtil;getExpCostForSlot(II)I"))
    private int chronicles_leveling$prodigyXp(int xpCost, @Local(argsOnly = true) Player player) {
        return EnchantingPerks.prodigyReducedCost(player, xpCost);
    }

    /**
     * Abundance then Unstable / Unlimited Power on Apothic's roll. Abundance adds extra enchants drawn from
     * Apothic's OWN stats-aware candidate pool ({@code getPossibleEnchantments} + {@code getAvailableEnchantmentResults}),
     * so the table blacklist, the per-table treasure flag, and Apothic's cost windows are all honored, exactly the
     * pool the base Apothic roll uses. Power then bumps every level past max (skipping max-level-1). Apothic seeds
     * its roll with {@code enchantmentSeed + slot} just like vanilla, so the same deterministic seed keeps the clue
     * preview and the applied enchant in agreement.
     */
    @ModifyReturnValue(method = "getEnchantmentList", at = @At("RETURN"))
    private List<EnchantmentInstance> chronicles_leveling$abundanceAndPower(
            List<EnchantmentInstance> rolled,
            @Local(argsOnly = true) ItemStack item,
            @Local(argsOnly = true, ordinal = 0) int slot,
            @Local(argsOnly = true, ordinal = 1) int cost) {
        long seed = ((EnchantmentMenu) (Object) this).getEnchantmentSeed() + slot;
        List<EnchantmentInstance> withAbundance = rolled;
        if (EnchantingPerks.abundanceTrials(this.player) > 0 && !item.isEmpty() && cost > 0) {
            var reg = this.player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var possible = ApothEnchantmentHelper.getPossibleEnchantments(reg, item, this.stats);
            List<EnchantmentInstance> available = ApothEnchantmentHelper.getAvailableEnchantmentResults(cost, item, possible);
            withAbundance = EnchantingPerks.applyAbundance(this.player, seed, rolled, available);
        }
        return EnchantingPerks.applyPowerPerks(this.player, seed, withAbundance);
    }

    /**
     * Experimenter rank 2: relax same-group damage/protection exclusivity for the duration of Apothic's roll, so the
     * table can co-roll them. Apothic's {@code removeIncompatible} delegates to vanilla {@code Enchantment.areCompatible},
     * which the common {@code EnchantmentMixin} gate intercepts only while this flag is set. Scoped + exception-safe.
     */
    @WrapOperation(method = "getEnchantmentList",
            at = @At(value = "INVOKE",
                    target = "Ldev/shadowsoffire/apothic_enchanting/table/ApothEnchantmentHelper;selectEnchantment(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILdev/shadowsoffire/apothic_enchanting/table/EnchantmentTableStats;Lnet/minecraft/core/HolderLookup$RegistryLookup;)Ljava/util/List;"))
    private List<EnchantmentInstance> chronicles_leveling$relaxApothRoll(
            RandomSource rand, ItemStack stack, int level, EnchantmentTableStats stats, HolderLookup.RegistryLookup<Enchantment> reg,
            Operation<List<EnchantmentInstance>> original) {
        if (!EnchantingPerks.relaxesTableExclusivity(this.player)) {
            return original.call(rand, stack, level, stats, reg);
        }
        EnchantingPerks.setTableExclusivityRelaxed(true);
        try {
            return original.call(rand, stack, level, stats, reg);
        } finally {
            EnchantingPerks.setTableExclusivityRelaxed(false);
        }
    }

    /** Arcane Insight: reveal more of the roll by raising the per-slot clue budget Apothic syncs to the client. */
    @ModifyExpressionValue(method = "lambda$slotsChanged$0",
            at = @At(value = "INVOKE",
                    target = "Ldev/shadowsoffire/apothic_enchanting/table/EnchantmentTableStats;clues()I"))
    private int chronicles_leveling$arcaneInsightClues(int original) {
        int reveal = EnchantingPerks.arcaneInsightReveal(this.player);
        return reveal <= 0 ? original : original + (reveal >= 3 ? 99 : reveal);
    }

    /**
     * Esoteric Enchanter: force the gathered table stats to allow treasure (which widens Apothic's candidate
     * pool to the {@code treasure} tag and shows the treasure indicator in the table UI). Rebuilds the immutable
     * stats record with {@code treasure = true}.
     */
    @SuppressWarnings("deprecation")   // the no-arg eterna() is the record's raw stored field, which is what we copy
    @ModifyExpressionValue(method = "lambda$gatherStats$0",
            at = @At(value = "INVOKE",
                    target = "Ldev/shadowsoffire/apothic_enchanting/table/EnchantmentTableStats;gatherStats(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;I)Ldev/shadowsoffire/apothic_enchanting/table/EnchantmentTableStats;"))
    private EnchantmentTableStats chronicles_leveling$esotericTreasure(EnchantmentTableStats stats) {
        if (stats.treasure() || !EnchantingPerks.hasEsoteric(this.player)) {
            return stats;
        }
        // Force treasure on, but keep Esoteric Enchanter curse-free; unlike Apothic's own treasure shelf (which
        // rolls curses unless the player adds a filtration shelf), our node never should: blacklist the curse tag.
        Set<Holder<Enchantment>> blacklist = new HashSet<>(stats.blacklist());
        this.player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.CURSE)
                .ifPresent(curses -> curses.forEach(blacklist::add));
        return new EnchantmentTableStats(stats.eterna(), stats.quanta(), stats.arcana(), stats.clues(),
                blacklist, true, stats.stable());
    }
}
