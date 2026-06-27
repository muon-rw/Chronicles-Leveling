package dev.muon.chronicles_leveling.skill.combat;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.ArcherySkill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The fire-time and impact-time logic for the Archery tree's projectile perks. Called from
 * {@code AbstractArrowMixin}; reads the shooter's {@link SkillEffects} capabilities off the arrow's
 * owner. Disorient / Pinning are NOT here; they ride {@code CombatProcRouter.onHitDealt} (a ranged hit
 * already routes through the damage pipeline), and Far Shot's distance is read in
 * {@code CombatProcRouter.modifyOutgoing} from the {@link ChroniclesArrow#chronicles_leveling$launchPos() launch stamp}.
 *
 * <p>Multishot and Ricochet spawn fresh projectiles of the SAME entity type as the source (a spectral arrow
 * volleys spectral arrows, a thrown trident throws tridents), {@link ChroniclesArrow#chronicles_leveling$markSecondary()
 * secondary-flagged} with their velocity set directly (never re-entering {@code shoot}), so they neither
 * recurse nor fight the source's hit/despawn lifecycle. Extras are {@code CREATIVE_ONLY} pickup so they can't
 * be farmed for ammo. Ricochet is a budget the chain decrements: the perk rank IS the bounce count, and a
 * bounce always fires while the budget is positive (no chance roll). Tipped-potion/weapon enchants are not
 * carried onto extras.
 */
public final class ArcheryHooks {

    private ArcheryHooks() {}

    // Tuning lives in ConfigSkills.archery (multishot spread, ricochet range/speed/damage), read live.

    /** At fire time: stamp the launch position + ricochet budget, then apply Arrow Recovery / Multishot. */
    public static void onArrowShot(AbstractArrow arrow) {
        ChroniclesArrow ext = (ChroniclesArrow) arrow;
        ext.chronicles_leveling$setLaunchPos(arrow.position());   // cheap; powers Far Shot's travel-distance read

        if (ext.chronicles_leveling$isSecondary()) {
            return;   // a Multishot/Ricochet bolt doesn't itself spawn more
        }
        if (!(arrow.getOwner() instanceof ServerPlayer shooter) || !(arrow.level() instanceof ServerLevel level)) {
            return;
        }

        // Ricochet rank IS the bounce count; carried as a budget the chain decrements (Piercing Shot is read
        // off getPierceLevel() in the mixin, not stamped here). Multishot extras inherit this same budget so a
        // volley ricochets like the main shot; safe because giving an extra a budget can't re-trigger Multishot
        // (onArrowShot skips secondaries, and extras never call shoot()); it only starts their own bounded chain.
        int ricochet = (int) Math.floor(SkillEffects.get(shooter, ArcherySkill.RICOCHET_COUNT));
        ext.chronicles_leveling$setRicochetBudget(ricochet);

        int extra = (int) Math.floor(SkillEffects.get(shooter, ArcherySkill.MULTISHOT_ARROWS));
        double spreadDeg = Configs.SKILLS.archery.multishotSpreadDegrees.get();
        Vec3 baseVelocity = arrow.getDeltaMovement();
        for (int i = 1; i <= extra; i++) {
            int side = (i % 2 == 1) ? (i + 1) / 2 : -(i / 2);   // +1, -1, +2, -2, ...
            Vec3 velocity = rotateAroundY(baseVelocity, Math.toRadians(spreadDeg * side));
            spawnSecondary(level, arrow, velocity, 1.0, ricochet);   // extras inherit the ricochet chain
        }
    }

    /** At impact: if the arrow has ricochet budget, bounce a weakened bolt at a nearby target (chain decrements). */
    public static void onArrowHit(AbstractArrow arrow, Entity directVictim) {
        ChroniclesArrow ext = (ChroniclesArrow) arrow;
        int budget = ext.chronicles_leveling$ricochetBudget();
        if (budget <= 0) {
            return;
        }
        ext.chronicles_leveling$setRicochetBudget(0);   // this arrow bounces once per life, even if it pierces several
        if (!(arrow.getOwner() instanceof ServerPlayer shooter) || !(arrow.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = nearestOther(level, arrow, directVictim, shooter);
        if (target == null) {
            return;
        }
        var archery = Configs.SKILLS.archery;
        Vec3 dir = target.getEyePosition().subtract(arrow.position()).normalize().scale(archery.ricochetSpeed.get());
        spawnSecondary(level, arrow, dir, archery.ricochetDamageFraction.get(), budget - 1);
    }

    /**
     * QoL i-frame preservation for player projectiles (config-toggled): run the projectile's damage call,
     * then restore the target's invulnerability window to its pre-hit value. The shot neither ADDS its own
     * ~20-tick window nor CLEARS one already running from another source, so a Multishot/rapid volley (or a
     * second trident into an already-hit target) all connects, while a window in progress elsewhere is respected.
     * (Vanilla only blocks a fresh hit once {@code invulnerableTime > 10}, hence preserve-not-zero.)
     * Mob-shot projectiles are left vanilla. Shared by {@code AbstractArrowMixin} and {@code ThrownTridentMixin}
     * (a trident overrides {@code onHitEntity}, so the superclass wrap can't reach it).
     */
    public static boolean preserveInvulnerability(AbstractArrow projectile, Entity target, BooleanSupplier dealDamage) {
        if (Configs.SKILLS.arrowsIgnoreInvulnerability.get() && projectile.getOwner() instanceof Player) {
            int previous = target.invulnerableTime;
            boolean result = dealDamage.getAsBoolean();
            target.invulnerableTime = previous;
            return result;
        }
        return dealDamage.getAsBoolean();
    }

    private static void spawnSecondary(ServerLevel level, AbstractArrow source, Vec3 velocity,
                                       double damageFraction, int ricochetBudget) {
        if (!(source.getOwner() instanceof LivingEntity owner)) {
            return;
        }
        // Spawn the SAME projectile type as the source (spectral stays spectral, trident stays trident): a
        // fresh default instance; copy owner + base damage and force CREATIVE_ONLY pickup so the volley can't
        // be farmed for ammo. Tipped-potion contents / weapon enchants are not carried onto extras.
        Entity created = source.getType().create(level, EntitySpawnReason.TRIGGERED);
        if (!(created instanceof AbstractArrow extra)) {
            return;
        }
        extra.setOwner(owner);
        ChroniclesArrow ext = (ChroniclesArrow) extra;
        ext.chronicles_leveling$markSecondary();
        ext.chronicles_leveling$setRicochetBudget(ricochetBudget);
        extra.setBaseDamage(((ChroniclesArrow) source).chronicles_leveling$baseDamage() * damageFraction);
        extra.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        extra.setPos(source.getX(), source.getY(), source.getZ());
        extra.setDeltaMovement(velocity);
        extra.setYRot((float) (Math.atan2(velocity.x, velocity.z) * (180.0 / Math.PI)));
        extra.setXRot((float) (Math.atan2(velocity.y, velocity.horizontalDistance()) * (180.0 / Math.PI)));
        // Un-pickup-able clones shouldn't litter; pre-age them so they vanish soon after they stick.
        ext.chronicles_leveling$accelerateDespawn(Configs.SKILLS.archery.clonedProjectileDespawnTicks.get());
        level.addFreshEntity(extra);
    }

    private static LivingEntity nearestOther(ServerLevel level, AbstractArrow arrow, Entity exclude, Entity shooter) {
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                arrow.getBoundingBox().inflate(Configs.SKILLS.archery.ricochetRange.get()),
                e -> e != exclude && TargetAllegiance.isHostileTarget(shooter, e) && clearShot(level, arrow, e));
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double d = candidate.distanceToSqr(arrow);
            if (d < best) {
                best = d;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** A clear bounce path: no solid block between the arrow and the candidate's center (no ricocheting through walls). */
    private static boolean clearShot(ServerLevel level, AbstractArrow arrow, Entity target) {
        Vec3 to = target.getBoundingBox().getCenter();
        return level.clip(new ClipContext(arrow.position(), to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow)).getType() != HitResult.Type.BLOCK;
    }

    private static Vec3 rotateAroundY(Vec3 v, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }
}
