package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.muon.chronicles_leveling.skill.social.SpeechTamingHandler;
import dev.muon.chronicles_leveling.skill.xp.SpeechXpHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Speech breeding hooks: Husbandry shortens the post-breeding cooldown for a pair the holder bred, and breeding
 * grants Speech XP. The love cause is captured at HEAD because vanilla resets it before the method returns.
 */
@Mixin(value = Animal.class, remap = false)
public abstract class AnimalMixin {

    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$captureBreedCause(ServerLevel level, Animal partner, AgeableMob offspring,
            CallbackInfo ci, @Share("chronicles_leveling$breedCause") LocalRef<ServerPlayer> cause) {
        ServerPlayer own = ((Animal) (Object) this).getLoveCause();
        cause.set(own != null ? own : partner.getLoveCause());
    }

    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("TAIL"), remap = false)
    private void chronicles_leveling$shortenBreedCooldown(ServerLevel level, Animal partner, AgeableMob offspring,
            CallbackInfo ci, @Share("chronicles_leveling$breedCause") LocalRef<ServerPlayer> cause) {
        SpeechTamingHandler.shortenBreedingCooldown((Animal) (Object) this, partner, cause.get());
        SpeechXpHandler.onBreed(cause.get());
    }
}
