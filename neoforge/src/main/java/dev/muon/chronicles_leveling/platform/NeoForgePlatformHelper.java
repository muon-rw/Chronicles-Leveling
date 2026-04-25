package dev.muon.chronicles_leveling.platform;

import dev.muon.chronicles_leveling.level.PlayerLevelStore;
import dev.muon.chronicles_leveling.level.PlayerLevelStoreNeoforge;
import dev.muon.chronicles_leveling.network.NetworkHelper;
import dev.muon.chronicles_leveling.network.NetworkHelperNeoforge;
import dev.muon.chronicles_leveling.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

public class NeoForgePlatformHelper implements IPlatformHelper {

    private static final PlayerLevelStore LEVEL_STORE = new PlayerLevelStoreNeoforge();
    private static final NetworkHelper NETWORK_HELPER = new NetworkHelperNeoforge();

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
    public NetworkHelper getNetworkHelper() {
        return NETWORK_HELPER;
    }
}
