package dev.muon.chronicles_leveling.skill.ability.catalog;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.component.BrewPotency;
import dev.muon.chronicles_leveling.component.ModComponents;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbstractAbility;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;

/**
 * Alchemical Battery (Alchemy): consumes the drinkable potion in the player's main hand and discharges its effects to
 * every player within range as a burst aura (the caster included). The shared brew keeps its own empowerment: the
 * carried {@link BrewPotency} amplifier and {@code POTION_DURATION_SCALE} are applied just like drinking it. The
 * consumed potion is the cost; a short cooldown gates spam. Placeholder tuning.
 */
public final class AlchemicalBatteryAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/alchemical_battery");

    private static final double RADIUS = 8.0;   // placeholder tuning

    public AlchemicalBatteryAbility() {
        super(ID, Skills.ALCHEMY, 200, AbilityCost.none());   // ~10s cooldown; the consumed potion is the cost
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        return isShareablePotion(player.getMainHandItem());
    }

    @Override
    public Component activationError(ServerPlayer player) {
        return Component.translatable("chronicles_leveling.ability.error.no_potion");
    }

    @Override
    public void run(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        PotionContents contents = held.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return;
        }
        PotionContents shared = BrewPotency.boosted(contents, held.get(ModComponents.BREW_POTENCY));
        float durationScale = held.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0f);
        AABB area = player.getBoundingBox().inflate(RADIUS);
        for (Player ally : player.level().getEntitiesOfClass(Player.class, area)) {
            shared.applyToLivingEntity(ally, durationScale);
        }
        held.shrink(1);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    private static boolean isShareablePotion(ItemStack stack) {
        if (!(stack.getItem() instanceof PotionItem) || stack.getItem() instanceof ThrowablePotionItem) {
            return false;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.getAllEffects().iterator().hasNext();
    }
}
