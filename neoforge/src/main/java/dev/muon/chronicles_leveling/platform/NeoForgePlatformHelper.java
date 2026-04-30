package dev.muon.chronicles_leveling.platform;

import dev.muon.chronicles_leveling.level.PlayerLevelStore;
import dev.muon.chronicles_leveling.level.PlayerLevelStoreNeoforge;
import dev.muon.chronicles_leveling.network.NetworkHelper;
import dev.muon.chronicles_leveling.network.NetworkHelperNeoforge;
import dev.muon.chronicles_leveling.platform.services.IPlatformHelper;
import dev.muon.chronicles_leveling.skill.PlayerSkillStore;
import dev.muon.chronicles_leveling.skill.PlayerSkillStoreNeoforge;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationStore;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationStoreNeoforge;
import dev.muon.chronicles_leveling.skill.xp.SpawnerOriginStore;
import dev.muon.chronicles_leveling.skill.xp.SpawnerOriginStoreNeoforge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.PercentageAttribute;

import java.lang.reflect.Field;
import java.util.Optional;

public class NeoForgePlatformHelper implements IPlatformHelper {

    private static final PlayerLevelStore LEVEL_STORE = new PlayerLevelStoreNeoforge();
    private static final PlayerSkillStore SKILL_STORE = new PlayerSkillStoreNeoforge();
    private static final BrewingStationStore BREWING_STORE = new BrewingStationStoreNeoforge();
    private static final SpawnerOriginStore SPAWNER_ORIGIN_STORE = new SpawnerOriginStoreNeoforge();
    private static final NetworkHelper NETWORK_HELPER = new NetworkHelperNeoforge();

    /**
     * Reflective accessor for {@link PercentageAttribute#scaleFactor} so we can
     * return the actual configured scale instead of assuming 100. The field is
     * {@code protected final} on a public class — reflection succeeds without
     * needing an access transformer. Cached in a static so we pay setup cost
     * once. Null if the field disappears in a future NeoForge release; we fall
     * back to the standard 100x scale in that case.
     */
    private static final Field SCALE_FACTOR_FIELD = lookupScaleFactorField();

    private static Field lookupScaleFactorField() {
        try {
            Field f = PercentageAttribute.class.getDeclaredField("scaleFactor");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
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
    public BrewingStationStore getBrewingStationStore() {
        return BREWING_STORE;
    }

    @Override
    public SpawnerOriginStore getSpawnerOriginStore() {
        return SPAWNER_ORIGIN_STORE;
    }

    @Override
    public NetworkHelper getNetworkHelper() {
        return NETWORK_HELPER;
    }

    @Override
    public Optional<Component> modifierComponent(Holder<Attribute> holder, AttributeModifier modifier) {
        // IAttributeExtension is mixed into Attribute on NeoForge; the toComponent default
        // routes percent-aware formatting through PercentageAttribute / BooleanAttribute as needed.
        return Optional.of(holder.value().toComponent(modifier, TooltipFlag.NORMAL));
    }

    @Override
    public Optional<Component> baseValueComponent(Holder<Attribute> holder, double value) {
        // entityBase only matters for the F3+H "advanced" debug breakdown — we render with
        // the normal flag, so any value is fine. merged=false: we don't offer the merge UX here.
        // toBaseComponent emits an unstyled component (vanilla relies on the item-tooltip
        // pipeline to colorize); we color it green to match the modifier-list convention.
        MutableComponent base = holder.value().toBaseComponent(value, 0.0, false, TooltipFlag.NORMAL);
        return Optional.of(base.withStyle(ChatFormatting.DARK_GREEN));
    }

    @Override
    public Optional<Double> percentScaleForAttribute(Holder<Attribute> holder) {
        if (!(holder.value() instanceof PercentageAttribute pa)) return Optional.empty();
        if (SCALE_FACTOR_FIELD != null) {
            try {
                return Optional.of(SCALE_FACTOR_FIELD.getDouble(pa));
            } catch (IllegalAccessException ignored) {}
        }
        return Optional.of(100.0);
    }
}
