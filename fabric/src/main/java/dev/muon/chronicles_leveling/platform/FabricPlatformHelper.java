package dev.muon.chronicles_leveling.platform;

import dev.muon.chronicles_leveling.level.PlayerLevelStore;
import dev.muon.chronicles_leveling.level.PlayerLevelStoreFabric;
import dev.muon.chronicles_leveling.network.NetworkHelper;
import dev.muon.chronicles_leveling.network.NetworkHelperFabric;
import dev.muon.chronicles_leveling.platform.services.IPlatformHelper;
import dev.muon.chronicles_leveling.skill.PlayerSkillStore;
import dev.muon.chronicles_leveling.skill.PlayerSkillStoreFabric;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Optional;

public class FabricPlatformHelper implements IPlatformHelper {

    private static final PlayerLevelStore LEVEL_STORE = new PlayerLevelStoreFabric();
    private static final PlayerSkillStore SKILL_STORE = new PlayerSkillStoreFabric();
    private static final NetworkHelper NETWORK_HELPER = new NetworkHelperFabric();

    // Mod ID has no underscore — matches Combat-Attributes' own check.
    private static final String DT_MOD_ID = "dynamictooltips";
    private static final boolean DT_LOADED = FabricLoader.getInstance().isModLoaded(DT_MOD_ID);

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public PlayerLevelStore getPlayerLevelStore() {
        return LEVEL_STORE;
    }

    @Override
    public PlayerSkillStore getPlayerSkillStore() {
        return SKILL_STORE;
    }

    @Override
    public NetworkHelper getNetworkHelper() {
        return NETWORK_HELPER;
    }

    @Override
    public Optional<Component> modifierComponent(Holder<Attribute> holder, AttributeModifier modifier) {
        return DT_LOADED ? Optional.of(DTLookup.modifierComponent(holder.value(), modifier)) : Optional.empty();
    }

    @Override
    public Optional<Component> baseValueComponent(Holder<Attribute> holder, double value) {
        return DT_LOADED ? Optional.of(DTLookup.baseValueComponent(holder.value(), value)) : Optional.empty();
    }

    @Override
    public Optional<Double> percentScaleForAttribute(Holder<Attribute> holder) {
        if (!DT_LOADED) return Optional.empty();
        return holder.unwrapKey()
                .flatMap(key -> DTLookup.scaleFor(key.identifier()));
    }

    /**
     * JVM class loading is lazy: this nested class only resolves Dynamic-Tooltips
     * classes the first time a method on it runs, so {@code NoClassDefFoundError}
     * can't fire when the mod isn't on the classpath.
     */
    private static final class DTLookup {
        static Optional<Double> scaleFor(net.minecraft.resources.Identifier id) {
            dev.muon.dynamictooltips.api.DynamicTooltipsAPI.PercentRule rule =
                    dev.muon.dynamictooltips.api.DynamicTooltipsAPI.percentAttributes().get(id);
            if (rule == null) return Optional.empty();
            double s = rule.scaleFactor();
            return s > 0 ? Optional.of(s) : Optional.empty();
        }

        static Component baseValueComponent(Attribute attribute, double value) {
            return dev.muon.dynamictooltips.api.DynamicTooltipsAPI.createBaseValueComponent(attribute, value);
        }

        static Component modifierComponent(Attribute attribute, AttributeModifier modifier) {
            return dev.muon.dynamictooltips.api.DynamicTooltipsAPI.createModifierComponent(attribute, modifier);
        }
    }
}
