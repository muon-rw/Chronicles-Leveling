package dev.muon.chronicles_leveling.compat;

import dev.muon.chronicles_leveling.platform.Services;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Direct read of Combat-Attributes' percent-scale metadata.
 *
 * <p>Combat-Attributes only hands its attributes off to Dynamic-Tooltips on
 * Fabric Client (see {@code CombatAttributesFabricClient}); the NeoForge build
 * skips that step, so DT's runtime registry is empty for these attrs. We pull
 * the same data ourselves from {@code ModAttributes.ALL} so percent rendering
 * works on both loaders without relying on DT being populated by a third party.
 *
 * <p>Lazy-loaded via the inner {@code Lookup} class — Combat-Attributes API
 * references don't resolve unless the mod is detected.
 */
public final class CombatAttributesCompat {

    private static final String MOD_ID = "combat_attributes";
    private static final boolean LOADED = Services.PLATFORM.isModLoaded(MOD_ID);

    private static volatile Map<Identifier, Double> CACHE;

    private CombatAttributesCompat() {}

    public static Optional<Double> scaleFor(Identifier id) {
        if (!LOADED) return Optional.empty();
        Map<Identifier, Double> cache = CACHE;
        if (cache == null) {
            cache = Lookup.buildCache();
            CACHE = cache;
        }
        Double s = cache.get(id);
        return s == null ? Optional.empty() : Optional.of(s);
    }

    private static final class Lookup {
        static Map<Identifier, Double> buildCache() {
            Map<Identifier, Double> map = new HashMap<>();
            for (dev.muon.combat_attributes.attribute.ModAttributes.Entry entry :
                    dev.muon.combat_attributes.attribute.ModAttributes.ALL) {
                java.util.OptionalDouble scale = entry.percentScale();
                if (scale.isPresent()) {
                    Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, entry.id());
                    map.put(id, scale.getAsDouble());
                }
            }
            return Map.copyOf(map);
        }
    }
}
