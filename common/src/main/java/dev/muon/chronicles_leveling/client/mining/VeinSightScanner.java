package dev.muon.chronicles_leveling.client.mining;

import dev.muon.chronicles_leveling.client.AbilityWindowStoreClient;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side cached scan of nearby ores while Vein Sight is active. Rescans periodically, when the player crosses
 * into a new section, or when newly active; the renderer only reads the cached buckets so per-frame cost is just the
 * line emission. Positions are bucketed by outline color so the renderer flushes one render type.
 */
public final class VeinSightScanner {

    private VeinSightScanner() {}

    private static final Map<Integer, List<BlockPos>> BUCKETS = new HashMap<>();
    private static long lastScanTick = Long.MIN_VALUE;
    private static long lastSection = Long.MIN_VALUE;

    public static Map<Integer, List<BlockPos>> buckets() {
        return BUCKETS;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            clear();
            return;
        }
        long now = level.getGameTime();
        if (!AbilityWindowStoreClient.isActive(AbilityWindowStore.WindowKind.VEIN_SIGHT, now)) {
            clear();
            return;
        }
        long section = SectionPos.of(player.blockPosition()).asLong();
        int interval = Configs.SKILLS.mining.veinSightRescanIntervalTicks.get();
        boolean due = BUCKETS.isEmpty() || section != lastSection || now - lastScanTick >= interval;
        if (!due) {
            return;
        }
        lastScanTick = now;
        lastSection = section;
        scan(level, player.blockPosition());
    }

    private static void scan(ClientLevel level, BlockPos center) {
        BUCKETS.clear();
        int radius = Configs.SKILLS.mining.veinSightRadius.get();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    cursor.set(cx + dx, cy + dy, cz + dz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (VeinSightColors.isOre(state)) {
                        BUCKETS.computeIfAbsent(VeinSightColors.colorFor(state), k -> new ArrayList<>())
                                .add(cursor.immutable());
                    }
                }
            }
        }
    }

    private static void clear() {
        BUCKETS.clear();
        lastScanTick = Long.MIN_VALUE;
        lastSection = Long.MIN_VALUE;
    }

    public static void invalidateColors() {
        VeinSightColors.invalidate();
        clear();
    }
}
