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
 * NeoForge-side registration for the six stat attributes from
 * {@link ModStats#ALL}. Pattern matches Combat-Attributes' equivalent.
 *
 * <p>Each entry registers a vanilla {@link RangedAttribute} via
 * {@link DeferredRegister}, then attaches it to every living entity in
 * {@link #attachToLiving} so the stat values follow the entity rather than
 * being PLAYER-only — keeps doors open for "give a mob the strength stat
 * and it benefits from the same modifier specs" experiments down the road.
 *
 * <p>If we want stat attributes to apply only to players, change the loop
 * in {@link #attachToLiving} to {@code event.add(EntityType.PLAYER, holder)}.
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

    /** Populates the common holder map after the deferred register has fired. */
    public static void init() {
        for (int i = 0; i < ModStats.ALL.size(); i++) {
            ModStats.put(ModStats.ALL.get(i).id(), HOLDERS.get(i));
        }
    }

    @SubscribeEvent
    public static void attachToLiving(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            for (DeferredHolder<Attribute, Attribute> holder : HOLDERS) {
                event.add(type, holder);
            }
        }
    }
}
