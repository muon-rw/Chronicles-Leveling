package dev.muon.chronicles_leveling.skill.gather;

import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mycologist (Herbalism) seams, gated by perk rank:
 * <ul>
 *   <li><b>Rank 1</b> places mushrooms off nylium: a thread-local {@code PLACE_ANYWHERE} flag, set only while a holder
 *       places a mushroom item, relaxes {@code MushroomBlock.canSurvive} for that placement ({@code MushroomBlockMixin});
 *       a config-gated, global {@code VegetationBlockMixin} then keeps the placed mushroom from popping off in light.</li>
 *   <li><b>Rank 2</b> grows fungi anywhere with bone meal: a thread-local {@code GROW_ANYWHERE} flag read by
 *       {@code FungusGrowthMixin}.</li>
 *   <li><b>Rank 3</b> spreads mycelium when a holder bonemeals a mushroom ({@link #spreadMycelium}) and adds the fungal
 *       extra-drop chance (read in {@code GatherProcRouter}).</li>
 * </ul>
 * Both flags are scoped tightly around the triggering item use, so no other actor or block is affected.
 */
public final class MycologyHandler {

    private MycologyHandler() {}

    private static final ThreadLocal<Boolean> GROW_ANYWHERE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> PLACE_ANYWHERE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static int rank(ServerPlayer player) {
        return PlayerSkillManager.get(player).get(Skills.HERBALISM).rankOf("mycologist");
    }

    public static boolean isGrowAnywhere() {
        return GROW_ANYWHERE.get();
    }

    public static void setGrowAnywhere(boolean relaxed) {
        setFlag(GROW_ANYWHERE, relaxed);
    }

    public static boolean isPlaceAnywhere() {
        return PLACE_ANYWHERE.get();
    }

    public static void setPlaceAnywhere(boolean relaxed) {
        setFlag(PLACE_ANYWHERE, relaxed);
    }

    private static void setFlag(ThreadLocal<Boolean> flag, boolean relaxed) {
        if (relaxed) {
            flag.set(Boolean.TRUE);
        } else {
            flag.remove();
        }
    }

    /** Rank 3: converts exposed dirt-like blocks around a bonemealed mushroom to mycelium, moss-spread style. */
    public static void spreadMycelium(ServerLevel level, BlockPos mushroomPos, int radius) {
        if (radius <= 0) {
            return;
        }
        BlockPos ground = mushroomPos.below();
        for (BlockPos pos : BlockPos.betweenClosed(
                ground.offset(-radius, -1, -radius), ground.offset(radius, 1, radius))) {
            BlockState state = level.getBlockState(pos);
            // Convert exposed dirt-like soil to mycelium. SNIFFER_DIGGABLE_BLOCK is the broad soil set (grass_block,
            // dirt variants, podzol, mud, moss); BlockTags.DIRT no longer contains grass_block in this version. The
            // block above must not be a full solid (so grass topped with short grass / flowers still counts), which
            // also skips buried soil.
            if (state.is(BlockTags.SNIFFER_DIGGABLE_BLOCK) && !state.is(Blocks.MYCELIUM)
                    && !level.getBlockState(pos.above()).isSolidRender()) {
                level.setBlockAndUpdate(pos.immutable(), Blocks.MYCELIUM.defaultBlockState());
            }
        }
    }
}
