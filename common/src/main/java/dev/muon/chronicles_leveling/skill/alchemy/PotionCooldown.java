package dev.muon.chronicles_leveling.skill.alchemy;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.AlchemySkill;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Anti-spam cooldown applied after a player drinks or throws a potion, skipped for holders of the Deft Hands perk
 * ({@link AlchemySkill#POTION_COOLDOWN_BYPASS}) and for creative players. The cooldown group is the potion item, so
 * drinking and throwing cool independently, but all potions of one item type share the lock. No-effect potions (water,
 * awkward) are exempt from the drink cooldown. Server-side only; the vanilla cooldown packet syncs it to the client,
 * and the generic use gate ({@code ServerPlayerGameMode.useItem}) enforces it.
 */
public final class PotionCooldown {

    private PotionCooldown() {}

    public static void applyThrow(ServerPlayer player, ItemStack stack) {
        apply(player, stack, Configs.SKILLS.alchemy.potionThrowCooldownTicks.get());
    }

    public static void applyDrink(ServerPlayer player, ItemStack stack) {
        if (!hasEffects(stack)) {
            return;
        }
        apply(player, stack, Configs.SKILLS.alchemy.potionDrinkCooldownTicks.get());
    }

    private static void apply(ServerPlayer player, ItemStack stack, int ticks) {
        if (ticks <= 0 || stack.isEmpty() || player.getAbilities().instabuild) {
            return;
        }
        if (SkillEffects.has(player, AlchemySkill.POTION_COOLDOWN_BYPASS)) {
            return;
        }
        player.getCooldowns().addCooldown(stack, ticks);
    }

    private static boolean hasEffects(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.getAllEffects().iterator().hasNext();
    }
}
