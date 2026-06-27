package dev.muon.chronicles_leveling.stat;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Each {@link ModStats#ALL} entry registers a vanilla {@link RangedAttribute} via
 * {@link DeferredRegister}, then attaches it to the player in {@link #attachToPlayer}. Player-only, matching
 * the Fabric side ({@code PlayerMixinFabric} adds the stat attributes to {@code Player.createAttributes} only).
 * A future mob-stat feature ("give a mob the strength stat") would widen the attach to the relevant entity
 * types on BOTH loaders.
 */
@EventBusSubscriber(modid = ChroniclesLeveling.MOD_ID)
public final class ModStatsNeoforge {

    public static final DeferredRegister<Attribute> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.ATTRIBUTE, ChroniclesLeveling.MOD_ID);

    private static final List<DeferredHolder<Attribute, Attribute>> HOLDERS = new ArrayList<>();

    static {
        for (ModStats.Entry entry : ModStats.ALL) {
            String descriptionId = "attribute." + ChroniclesLeveling.MOD_ID + "." + entry.id();
            Supplier<Attribute> factory = () -> new RangedAttribute(
                    descriptionId,
                    entry.defaultValue(),
                    entry.minValue(),
                    entry.maxValue()
            ).setSyncable(true);
            HOLDERS.add(REGISTRY.register(entry.id(), factory));
        }
    }

    private ModStatsNeoforge() {}

    /** Call after the deferred register has fired. */
    public static void init() {
        for (int i = 0; i < ModStats.ALL.size(); i++) {
            ModStats.put(ModStats.ALL.get(i).id(), HOLDERS.get(i));
        }
    }

    @SubscribeEvent
    public static void attachToPlayer(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            if (type != EntityType.PLAYER) {
                continue;
            }
            for (DeferredHolder<Attribute, Attribute> holder : HOLDERS) {
                event.add(type, holder);
            }
        }
    }
}
