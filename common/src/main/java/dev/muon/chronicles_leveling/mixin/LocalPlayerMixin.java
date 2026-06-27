package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sure-Footed (Acrobatics): cancels the using/eating/drawing-item movement slowdown by neutralizing the
 * private {@code itemUseSpeedMultiplier} the local player scales its move input by. Reads the perk straight
 * off the synced client {@code PlayerSkillData}; movement is client-authoritative, so this needs no server twin.
 */
@Mixin(value = LocalPlayer.class, remap = false)
public abstract class LocalPlayerMixin {

    @ModifyReturnValue(method = "itemUseSpeedMultiplier", at = @At("RETURN"))
    private float chronicles_leveling$sureFooted(float original) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (PlayerSkillManager.get(self).get(Skills.ACROBATICS).rankOf("sure_footed") >= 1) {
            return Math.max(original, 1.0F);
        }
        return original;
    }
}
