package dev.muon.chronicles_leveling.stat;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Fabric-side registration for the six stat attributes from {@link ModStats#ALL}.
 *
 * <p>Registration runs from {@code <clinit>} so the holder map is populated the
 * first time anyone touches this class. {@link #ensureInitialized()} is a no-op
 * trampoline that exists only to force class loading from callers that need the
 * holders ready before they look them up — most importantly the
 * {@code PlayerAttributesMixin}, which can fire from {@code DefaultAttributes}'s
 * static initializer (during game bootstrap, well before {@code onInitialize})
 * and would otherwise iterate an empty {@code ModStats.HOLDERS}.
 *
 * <p>Pattern lifted from {@code Combat-Attributes}' {@code ModAttributesFabric}.
 * Vanilla {@link RangedAttribute} is used (rather than the diminishing variant)
 * because stat values are integer skill points, not a curve.
 */
public final class ModStatsFabric {

    static {
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

    private ModStatsFabric() {}

    /**
     * No-op trampoline. Calling it forces this class's {@code <clinit>}, which
     * registers every stat attribute exactly once. Called from {@code
     * ModInitializer.onInitialize} (primary path) and from
     * {@code PlayerAttributesMixin} (defensive — the mixin can fire from
     * {@code DefaultAttributes <clinit>} before mod init).
     */
    public static void ensureInitialized() {}
}
