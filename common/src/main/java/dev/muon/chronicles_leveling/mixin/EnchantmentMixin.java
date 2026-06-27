package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.skill.enchant.EnchantingPerks;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Experimenter rank 2 (Enchanting): relaxes the static enchantment-exclusivity check so same-group damage /
 * protection enchantments may co-roll at the enchanting table. Both backends route here: vanilla
 * {@code selectEnchantment} and Apothic's {@code removeIncompatible} both delegate to {@code Enchantment.areCompatible}.
 * Scoped by {@link EnchantingPerks#isTableExclusivityRelaxed}: only true inside a table roll the table mixins opened
 * for a rank-2 holder, so the anvil, tooltips, and all gameplay compatibility checks stay strict.
 */
@Mixin(value = Enchantment.class, remap = false)
public class EnchantmentMixin {

    @ModifyReturnValue(method = "areCompatible", at = @At("RETURN"), remap = false)
    private static boolean chronicles_leveling$relaxTableExclusivity(boolean original,
            @Local(argsOnly = true, ordinal = 0) Holder<Enchantment> first,
            @Local(argsOnly = true, ordinal = 1) Holder<Enchantment> second) {
        // Never relax the same-enchantment case: vanilla/Apothic strip a just-picked enchant from the candidate
        // pool via areCompatible(x, x) == false (an enchant is a member of its own exclusive-set tag), so relaxing
        // it would let one enchant roll twice (duplicate entry + double XP).
        if (original || first.equals(second) || !EnchantingPerks.isTableExclusivityRelaxed()) {
            return original;
        }
        return EnchantingPerks.sameExclusiveGroup(first, second);
    }
}
