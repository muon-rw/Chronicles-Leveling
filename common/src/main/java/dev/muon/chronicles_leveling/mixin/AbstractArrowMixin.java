package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.ArcherySkill;
import dev.muon.chronicles_leveling.skill.combat.ArcheryHooks;
import dev.muon.chronicles_leveling.skill.combat.ChroniclesArrow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Archery tree's projectile seams. Stamps a launch position for Far Shot, fires Multishot extras and
 * sets Arrow Recovery pickup at shoot time, ricochets on entity impact, and reports a high pierce level for
 * Piercing Shot. All logic for spawning/Far-Shot lives in {@code ArcheryHooks}; this mixin only adds the
 * transient {@link ChroniclesArrow} state and the injection points. {@code remap = false} (Mojmap fork).
 *
 * <p>Disorient / Pinning are deliberately absent here; a ranged hit already routes through the damage
 * pipeline, so {@code CombatProcRouter.onHitDealt} applies them like any other on-hit proc.
 */
@Mixin(value = AbstractArrow.class, remap = false)
public abstract class AbstractArrowMixin implements ChroniclesArrow {

    @Shadow private double baseDamage;
    @Shadow private int life;

    @Unique private Vec3 chronicles_leveling$launchPos;
    @Unique private boolean chronicles_leveling$secondary;
    @Unique private int chronicles_leveling$ricochetBudget;

    @Override public Vec3 chronicles_leveling$launchPos() { return chronicles_leveling$launchPos; }
    @Override public void chronicles_leveling$setLaunchPos(Vec3 pos) { this.chronicles_leveling$launchPos = pos; }
    @Override public boolean chronicles_leveling$isSecondary() { return chronicles_leveling$secondary; }
    @Override public void chronicles_leveling$markSecondary() { this.chronicles_leveling$secondary = true; }
    @Override public double chronicles_leveling$baseDamage() { return baseDamage; }
    @Override public int chronicles_leveling$ricochetBudget() { return chronicles_leveling$ricochetBudget; }
    @Override public void chronicles_leveling$setRicochetBudget(int budget) { this.chronicles_leveling$ricochetBudget = budget; }

    @Override public void chronicles_leveling$accelerateDespawn(int groundTicks) {
        // Vanilla discards at life >= 1200 (AbstractArrow#tickDespawn), and life only advances while in-ground.
        // Math.max so we only ever shorten, never extend, a lifespan.
        this.life = Math.max(this.life, 1200 - Math.max(0, groundTicks));
    }

    @Inject(method = "shoot(DDDFF)V", at = @At("RETURN"))
    private void chronicles_leveling$onShot(double x, double y, double z, float power, float uncertainty, CallbackInfo ci) {
        ArcheryHooks.onArrowShot((AbstractArrow) (Object) this);
    }

    /**
     * QoL i-frame preservation for player arrows (config-toggled); delegates to the shared
     * {@link ArcheryHooks#preserveInvulnerability} so the arrow and {@code ThrownTridentMixin} stay in lockstep.
     */
    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean chronicles_leveling$preserveInvulnerability(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        return ArcheryHooks.preserveInvulnerability((AbstractArrow) (Object) this, target,
                () -> original.call(target, source, amount));
    }

    // @At("RETURN") (not TAIL) so ricochet also fires on onHitEntity's early returns (e.g. the Enderman
    // branch). Returns are mutually exclusive (one per call), so onArrowHit runs exactly once per hit.
    @Inject(method = "onHitEntity", at = @At("RETURN"))
    private void chronicles_leveling$onHitEntity(EntityHitResult hitResult, CallbackInfo ci) {
        ArcheryHooks.onArrowHit((AbstractArrow) (Object) this, hitResult.getEntity());
    }

    /**
     * Piercing Shot: report a high pierce level for any of a perked player's arrows. Reading off the owner's
     * perk (not a per-arrow stamp) means Multishot extras and Ricochet bounces inherit it for free: they share
     * the owner. Boosting the public getter (rather than the private {@code setPierceLevel}) keeps the read off
     * the synced field and server-authoritative; the client's arrow has no {@code ServerPlayer} owner, so it's
     * a no-op there.
     */
    @ModifyReturnValue(method = "getPierceLevel", at = @At("RETURN"))
    private byte chronicles_leveling$piercingShot(byte original) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (self.getOwner() instanceof ServerPlayer shooter
                && SkillEffects.has(shooter, ArcherySkill.PIERCING_SHOT)) {
            return (byte) Math.max(original & 0xFF, 100);
        }
        return original;
    }
}
