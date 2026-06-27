package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.alchemy.PotionCooldown;
import dev.muon.chronicles_leveling.skill.catalog.AlchemySkill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Splash/lingering potion throwing tweaks: the anti-spam throw cooldown (vanilla has none, which lets stackable
 * potions be thrown every tick; bypassed by Deft Hands, see {@link PotionCooldown}), and the Deft Hands throw-velocity
 * boost, which makes the perk holder hurl potions farther.
 */
@Mixin(value = ThrowablePotionItem.class, remap = false)
public class ThrowablePotionItemMixin {

    @Inject(method = "use", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$throwCooldown(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer serverPlayer) {
            PotionCooldown.applyThrow(serverPlayer, serverPlayer.getItemInHand(hand));
        }
    }

    @ModifyArg(method = "use", index = 5, remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileFromRotation(Lnet/minecraft/world/entity/projectile/Projectile$ProjectileFactory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;FFF)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private float chronicles_leveling$deftHandsThrowVelocity(float velocity, @Local(argsOnly = true) Player player) {
        if (player instanceof ServerPlayer serverPlayer
                && SkillEffects.has(serverPlayer, AlchemySkill.POTION_COOLDOWN_BYPASS)) {
            return (float) (velocity * Configs.SKILLS.alchemy.deftHandsThrowMultiplier.get());
        }
        return velocity;
    }
}
