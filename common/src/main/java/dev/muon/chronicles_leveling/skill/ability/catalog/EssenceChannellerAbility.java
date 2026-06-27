package dev.muon.chronicles_leveling.skill.ability.catalog;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.level.VanillaXp;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbstractAbility;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Essence Channeller (Enchanting): drains the player's XP to repair gear on demand, like Mending (2 durability
 * per XP point). The perk rank sets the scope: rank 1 the held item, rank 2 worn + held gear, rank 3 the whole
 * inventory. Costs no Combat-Attributes resource; XP is the cost, spent in {@link #run}.
 */
public final class EssenceChannellerAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/essence_channeller");

    private static final String PERK_ID = "essence_channeller";

    /** Worn + held slots repaired at rank 2 (rank 3 widens to the whole inventory, which already includes these). */
    private static final EquipmentSlot[] WORN_AND_HELD = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public EssenceChannellerAbility() {
        super(ID, Skills.ENCHANTING, 200, AbilityCost.none());   // ~10s cooldown; XP is the real cost
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (VanillaXp.availableExperiencePoints(player) <= 0) {
            return false;
        }
        for (ItemStack item : scope(player)) {
            if (item.isDamageableItem() && item.getDamageValue() > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Component activationError(ServerPlayer player) {
        return Component.translatable(VanillaXp.availableExperiencePoints(player) <= 0
                ? "chronicles_leveling.ability.error.no_xp"
                : "chronicles_leveling.ability.error.nothing_to_repair");
    }

    @Override
    public void run(ServerPlayer player) {
        int budget = VanillaXp.availableExperiencePoints(player);
        int spent = 0;
        for (ItemStack item : scope(player)) {
            int remaining = budget - spent;
            if (remaining <= 0) {
                break;
            }
            if (!item.isDamageableItem() || item.getDamageValue() <= 0) {
                continue;
            }
            int repaired = Math.min(item.getDamageValue(), remaining * 2);   // 2 durability per XP point (Mending)
            item.setDamageValue(item.getDamageValue() - repaired);
            spent += (repaired + 1) / 2;
        }
        if (spent > 0) {
            player.giveExperiencePoints(-spent);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }

    /** The items in repair scope for the player's current Essence Channeller rank. */
    private static List<ItemStack> scope(ServerPlayer player) {
        int rank = PlayerSkillManager.get(player).get(Skills.ENCHANTING).rankOf(PERK_ID);
        List<ItemStack> items = new ArrayList<>();
        if (rank >= 3) {
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                items.add(inventory.getItem(i));
            }
        } else if (rank == 2) {
            for (EquipmentSlot slot : WORN_AND_HELD) {
                items.add(player.getItemBySlot(slot));
            }
        } else {
            items.add(player.getMainHandItem());
        }
        return items;
    }
}
