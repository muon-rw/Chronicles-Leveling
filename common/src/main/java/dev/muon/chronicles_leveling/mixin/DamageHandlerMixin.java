package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import dev.muon.chronicles_leveling.skill.combat.CombatProcRouter;
import dev.muon.combat_attributes.attribute.ModAttributes;
import dev.muon.combat_attributes.damage.DamageHandler;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Master's Focus (Weaponry capstone): forces every MELEE crit roll to succeed while the attacker's
 * {@link AbilityWindowStore.WindowKind#MASTERS_FOCUS} window is open. Combat-Attributes rolls crits in
 * {@code DamageHandler.rollCrit} before any Chronicles seam runs, and its crit chance stacks
 * probabilistically (so a flat attribute modifier can't reliably reach 100%); wrapping the roll is the
 * clean way to guarantee the crit. The {@code rollCrit} for ranged/magic is left to its natural roll.
 * The bonus true damage on a NATURAL crit is rolled and dealt separately in {@code CombatProcRouter}.
 */
@Mixin(value = DamageHandler.class, remap = false)
public class DamageHandlerMixin {

    @WrapMethod(method = "rollCrit")
    private static float chronicles_leveling$forceMastersFocusCrit(
            LivingEntity attacker, float damage, Holder<Attribute> chanceAttr, Holder<Attribute> damageAttr,
            Operation<Float> original) {
        if (attacker instanceof ServerPlayer player
                && !CombatProcRouter.isProcDamage()   // don't crit-boost the true-damage tick (or other procs)
                && chanceAttr.value() == ModAttributes.meleeCritChance().value()
                && AbilityWindowStore.isActive(player, AbilityWindowStore.WindowKind.MASTERS_FOCUS)) {
            return (float) (damage * ModAttributes.valueOrDefault(player, damageAttr));
        }
        return original.call(attacker, damage, chanceAttr, damageAttr);
    }
}
