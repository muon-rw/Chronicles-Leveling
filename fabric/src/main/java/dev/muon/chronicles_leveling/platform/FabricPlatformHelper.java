package dev.muon.chronicles_leveling.platform;

import dev.muon.chronicles_leveling.level.PlayerLevelStore;
import dev.muon.chronicles_leveling.level.PlayerLevelStoreFabric;
import dev.muon.chronicles_leveling.network.NetworkHelper;
import dev.muon.chronicles_leveling.network.NetworkHelperFabric;
import dev.muon.chronicles_leveling.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatformHelper implements IPlatformHelper {

    private static final PlayerLevelStore LEVEL_STORE = new PlayerLevelStoreFabric();
    private static final NetworkHelper NETWORK_HELPER = new NetworkHelperFabric();

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
    public NetworkHelper getNetworkHelper() {
        return NETWORK_HELPER;
    }
}
