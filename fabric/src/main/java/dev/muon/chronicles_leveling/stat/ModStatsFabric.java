package dev.muon.chronicles_leveling.stat;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Fabric-side registration for the six stat attributes from {@link ModStats#ALL}.
 * Pattern matches Combat-Attributes' {@code ModAttributesFabric}.
 *
 * <p>Vanilla {@link RangedAttribute} is used (rather than the diminishing
 * variant) because stat values are integer skill points, not a curve. The
 * description id is the conventional {@code attribute.<modid>.<id>} form so
 * lang entries pick up automatically.
 */
public final class ModStatsFabric {

    private ModStatsFabric() {}

    public static void init() {
        for (ModStats.Entry entry : ModStats.ALL) {
            String descriptionId = "attribute." + ChroniclesLeveling.MOD_ID + "." + entry.id();
            Attribute attribute = new RangedAttribute(
                    descriptionId,
                    entry.defaultValue(),
                    entry.minValue(),
                    entry.maxValue()
            ).setSyncable(true);

            Holder<Attribute> holder = Registry.registerForHolder(
                    BuiltInRegistries.ATTRIBUTE,
                    ChroniclesLeveling.id(entry.id()),
                    attribute
            );
            ModStats.put(entry.id(), holder);
        }
    }
}
