package dev.muon.chronicles_leveling.mixin;

import dev.muon.chronicles_leveling.platform.Services;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks mobs spawned by a {@code BaseSpawner} (regular or trial) for the skill
 * XP router. NeoForge gets this for free via {@code FinalizeSpawnEvent}; Fabric
 * has no equivalent event so we read the {@link EntitySpawnReason} arg straight
 * off the vanilla method.
 *
 * <p>HEAD injection is fine — we only need the spawn reason, not any state the
 * method mutates.
 */
@Mixin(Mob.class)
public class SpawnerOriginMixin {

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void chronicles_leveling$captureSpawnerOrigin(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            EntitySpawnReason reason,
            @Nullable SpawnGroupData groupData,
            CallbackInfoReturnable<SpawnGroupData> cir) {
        if (EntitySpawnReason.isSpawner(reason)) {
            Services.PLATFORM.getSpawnerOriginStore().mark((Mob) (Object) this);
        }
    }
}
