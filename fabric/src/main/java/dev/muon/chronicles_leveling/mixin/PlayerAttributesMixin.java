package dev.muon.chronicles_leveling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.chronicles_leveling.stat.ModStats;
import dev.muon.chronicles_leveling.stat.ModStatsFabric;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds Chronicles' six stat attributes to the player's default attribute
 * supplier on Fabric.
 *
 * <p>NeoForge handles the same attachment via {@code EntityAttributeModificationEvent}
 * — see {@code ModStatsNeoforge}. We split the loaders rather than putting the
 * mixin in common because NeoForge's pipeline doesn't run mixins at the point
 * where adding attributes here would help.
 *
 * <p>{@link ModifyReturnValue} runs after the vanilla builder is fully populated,
 * so we layer on top without touching vanilla defaults. The first thing we do is
 * force-touch {@link ModStatsFabric} so the holder map is populated before we
 * read it: vanilla's {@code DefaultAttributes <clinit>} calls
 * {@code Player.createAttributes().build()} during game bootstrap, which
 * happens before {@code ModInitializer.onInitialize} on Fabric.
 */
@Mixin(Player.class)
public class PlayerAttributesMixin {

    @ModifyReturnValue(method = "createAttributes", at = @At("RETURN"))
    private static AttributeSupplier.Builder chronicles_leveling$addStats(AttributeSupplier.Builder original) {
        ModStatsFabric.ensureInitialized();
        for (ModStats.Entry stat : ModStats.ALL) {
            original.add(ModStats.get(stat.id()), stat.defaultValue());
        }
        return original;
    }
}
