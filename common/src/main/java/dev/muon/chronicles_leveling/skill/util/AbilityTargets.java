package dev.muon.chronicles_leveling.skill.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Side-effect-free area scans for ability {@code canActivate} gates, the radius-scan counterpart to the
 * look-direction {@link EntityRaycast}. A radius ability whose {@code run} no-ops on an empty area (e.g. an
 * AoE with nothing in range) should gate on the matching scan here, so a wasted cast is denied before the
 * cooldown and resource cost are spent rather than after.
 */
public final class AbilityTargets {

    private AbilityTargets() {}

    /** Whether any entity of {@code type} matching {@code filter} sits within {@code radius} of the player. */
    public static <T extends Entity> boolean anyEntityWithin(ServerPlayer player, double radius, Class<T> type, Predicate<? super T> filter) {
        return player.level() instanceof ServerLevel level
                && !level.getEntitiesOfClass(type, player.getBoundingBox().inflate(radius), filter).isEmpty();
    }

    /** Whether any block matching {@code filter} sits in the box of half-extent {@code radiusXZ}/{@code radiusY} around {@code center}. Early-exits on the first match. */
    public static boolean anyBlockWithin(ServerLevel level, BlockPos center, int radiusXZ, int radiusY, Predicate<BlockState> filter) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -radiusXZ; x <= radiusXZ; x++) {
            for (int y = -radiusY; y <= radiusY; y++) {
                for (int z = -radiusXZ; z <= radiusXZ; z++) {
                    if (filter.test(level.getBlockState(cursor.setWithOffset(center, x, y, z)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
