package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.combat.ArcheryHooks;
import dev.muon.chronicles_leveling.skill.fishing.FishingHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ThrownTrident twin of {@link AbstractArrowMixin}'s impact seams. A thrown trident IS an
 * {@link AbstractArrow}, but it overrides {@code onHitEntity} with its own damage path, so the superclass
 * mixin's {@code onHitEntity} injectors never run for it. This re-applies the two that matter at trident
 * impact, both delegating to the same {@link ArcheryHooks} the arrow uses:
 * <ul>
 *   <li><b>i-frame preserve</b>: wraps the trident's {@code hurtOrSimulate} so a second trident, or one
 *       landing right after another hit, isn't swallowed by an existing invuln window (the ask this fixes).</li>
 *   <li><b>Ricochet</b>: {@code onArrowHit} bounces a weakened trident; {@code spawnSecondary} already
 *       spawns same-type projectiles ("a thrown trident throws tridents"), but the bounce was dead for
 *       tridents until this hook fired.</li>
 * </ul>
 *
 * <p>The {@code shoot}/launch-stamp (Far Shot) and the Piercing-Shot {@code getPierceLevel} getter are
 * inherited unchanged from {@code AbstractArrow}, so they already apply via {@link AbstractArrowMixin} and
 * are intentionally not re-declared here. {@code remap = false} (Mojmap fork), mirroring the superclass mixin.
 */
@Mixin(value = ThrownTrident.class, remap = false)
public abstract class ThrownTridentMixin {

    @Shadow private boolean dealtDamage;

    // Worthy rank 3: a thrown trident that has hit nothing returns on its own after a delay (also void-proof via EntityMixin).
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void chronicles_leveling$worthyAutoReturn(CallbackInfo ci) {
        ThrownTrident self = (ThrownTrident) (Object) this;
        if (dealtDamage || !(self.getOwner() instanceof Player player) || !FishingHooks.isTridentAutoReturn(player)) {
            return;
        }
        if (self.tickCount > Configs.SKILLS.fishing.worthyAutoReturnTicks.get()) {
            dealtDamage = true;                  // satisfies the return gate, the way a stuck/hit trident already returns
            self.setDeltaMovement(Vec3.ZERO);    // enter the return from rest so the instant snap is clean, not an overshoot
        }
    }

    // Worthy rank 3: force the Loyalty>0 return gate even with no Loyalty enchant (the accel hook drives the snap-back).
    @ModifyVariable(method = "tick", at = @At("STORE"), name = "loyalty", remap = false)
    private int chronicles_leveling$worthyLoyalty(int loyalty) {
        Entity owner = ((ThrownTrident) (Object) this).getOwner();
        return owner instanceof Player player && FishingHooks.isTridentAutoReturn(player) ? Math.max(loyalty, 1) : loyalty;
    }

    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean chronicles_leveling$preserveInvulnerability(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        return ArcheryHooks.preserveInvulnerability((AbstractArrow) (Object) this, target,
                () -> original.call(target, source, amount));
    }

    // @At("RETURN") (not TAIL) so the bounce also fires on onHitEntity's early returns (the Enderman branch),
    // matching AbstractArrowMixin. Returns are mutually exclusive, so onArrowHit runs exactly once per hit.
    @Inject(method = "onHitEntity", at = @At("RETURN"))
    private void chronicles_leveling$onHitEntity(EntityHitResult hitResult, CallbackInfo ci) {
        ArcheryHooks.onArrowHit((AbstractArrow) (Object) this, hitResult.getEntity());
        FishingHooks.reelTarget(((AbstractArrow) (Object) this).getOwner(), hitResult.getEntity());
        FishingHooks.stormGodLightning((ThrownTrident) (Object) this, hitResult.getEntity());
    }

    /** Worthy: scale (rank 1) or snap to the owner (rank 2+) the loyalty-return acceleration (the sole double local in {@code tick}). */
    @ModifyVariable(method = "tick", at = @At("STORE"), name = "accel", remap = false)
    private double chronicles_leveling$tridentReturn(double accel) {
        return FishingHooks.tridentReturnAccel((ThrownTrident) (Object) this, accel);
    }
}
