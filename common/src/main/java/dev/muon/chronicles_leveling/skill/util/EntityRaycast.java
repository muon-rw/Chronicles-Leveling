package dev.muon.chronicles_leveling.skill.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Centralized look-direction entity raycast (modeled on Otherworld-Origins' {@code SpellCastUtil.findTarget}):
 * clips through passable blocks (grass, flowers, glass-less foliage) but stops on solid collision, then picks the
 * first matching entity along the remaining segment. Reuse for any "what am I looking at" ability.
 */
public final class EntityRaycast {

    private EntityRaycast() {}

    /** The first living entity matching {@code filter} in {@code caster}'s look direction within {@code distance}, or null. */
    public static LivingEntity lookingAt(LivingEntity caster, double distance, Predicate<Entity> filter) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 end = eye.add(look.scale(distance));

        // Stop at the first solid block (passable blocks are ignored so foliage doesn't block the pick).
        HitResult block = caster.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double reach = block.getType() == HitResult.Type.MISS ? distance : block.getLocation().distanceTo(eye);
        Vec3 searchEnd = eye.add(look.scale(reach));
        AABB box = caster.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, searchEnd, box,
                e -> e != caster && !e.isSpectator() && e.isPickable() && filter.test(e), reach * reach);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
}
