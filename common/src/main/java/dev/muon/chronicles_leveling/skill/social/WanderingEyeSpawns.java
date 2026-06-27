package dev.muon.chronicles_leveling.skill.social;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.SpeechSkill;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Wandering Eye (Speech): a per-holder roll, on a fixed interval, to spawn a wandering trader nearby. Vanilla's
 * {@code WanderingTraderSpawner} is world-global; this is the separate per-player listener the perk promises.
 */
public final class WanderingEyeSpawns {

    private WanderingEyeSpawns() {}

    public static void tick(MinecraftServer server) {
        var cfg = Configs.SKILLS.speech;
        int interval = cfg.wanderingEyeSpawnIntervalTicks.get();
        if (interval <= 0 || server.getTickCount() % interval != 0) {
            return;
        }
        double chance = cfg.wanderingEyeSpawnChance.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (SkillEffects.has(player, SpeechSkill.WANDERING_EYE) && player.getRandom().nextDouble() < chance) {
                trySpawnNear(player);
            }
        }
    }

    private static void trySpawnNear(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos origin = player.blockPosition();
        for (int attempt = 0; attempt < 5; attempt++) {
            int dx = Mth.nextInt(player.getRandom(), 8, 16) * (player.getRandom().nextBoolean() ? 1 : -1);
            int dz = Mth.nextInt(player.getRandom(), 8, 16) * (player.getRandom().nextBoolean() ? 1 : -1);
            BlockPos offset = origin.offset(dx, 0, dz);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, offset);
            // Roofed dimensions (Nether) / caves: the surface heightmap is the ceiling, so stay near the holder's level.
            BlockPos pos = surface.getY() > origin.getY() + 1 && !level.canSeeSky(origin) ? offset : surface;
            WanderingTrader trader = EntityType.WANDERING_TRADER.spawn(level, pos, EntitySpawnReason.EVENT);
            if (trader != null) {
                trader.setDespawnDelay(48000);
                return;
            }
        }
    }
}
