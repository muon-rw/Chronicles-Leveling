package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.chronicles_leveling.client.AbilityWindowStoreClient;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Superbreaker (Mining active): while the {@code SUPERBREAKER} window is open, forces the player's dig speed
 * on pickaxe-mineable blocks high enough that the server breaks them in a single tick (instant). Server-only
 * (gated to {@link ServerPlayer}); the client predicts normal mining, so on a dedicated server the break
 * lands a tick behind the swing rather than being client-predicted.
 */
@Mixin(value = Player.class, remap = false)
public abstract class PlayerMixin {

    @ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"), remap = false)
    private float chronicles_leveling$superbreakerInstantBreak(float original, @Local(argsOnly = true) BlockState state) {
        Player self = (Player) (Object) this;
        if (!self.hasCorrectToolForDrops(state)) {   // tier-correct: obsidian only with diamond+, etc.
            return original;
        }
        // Server reads the live window; the client predicts from the synced mirror, so the break (and its
        // particles + sound) happens locally instead of lagging a tick behind the server.
        boolean active = self instanceof ServerPlayer serverPlayer
                ? AbilityWindowStore.isActive(serverPlayer, AbilityWindowStore.WindowKind.SUPERBREAKER)
                : AbilityWindowStoreClient.isActive(AbilityWindowStore.WindowKind.SUPERBREAKER, self.level().getGameTime());
        return active ? Math.max(original, 1_000_000f) : original;   // dig speed >> hardness*30 → instant
    }
}
