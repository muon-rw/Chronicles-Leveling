package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.client.mining.VeinSightRenderer;
import dev.muon.chronicles_leveling.client.mining.VeinSightScanner;
import dev.muon.chronicles_leveling.skill.enchant.WizardsStudyHandler;
import dev.muon.chronicles_leveling.skill.gather.GardenersInfusionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * The newer NeoForge {@code EventBusSubscriber} has no explicit {@code bus} field; the dispatcher
 * routes by event type, so mod-bus events ({@code RegisterKeyMappingsEvent}) and game-bus events
 * ({@code ClientTickEvent.Post}) can sit in one class.
 */
@EventBusSubscriber(modid = ChroniclesLeveling.MOD_ID, value = Dist.CLIENT)
public final class ClientEventsNeoforge {

    private ClientEventsNeoforge() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ChroniclesKeybinds.OPEN_STATS);
        for (var slotKey : ChroniclesKeybinds.ABILITY_SLOTS) {
            event.register(slotKey);
        }
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // Free-standing layer above the health row (mirrors how DRB registers its mana bar); the
        // renderer positions the strip itself, centered above the hotbar.
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH,
                ChroniclesLeveling.id("ability_hud"), AbilityHudRenderer::render);
    }

    @SubscribeEvent
    public static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        // Audit skill/perk/ability assets each time client resources finish (re)loading.
        Identifier id = ChroniclesLeveling.id("skill_asset_audit");
        event.addListener(id, (ResourceManagerReloadListener) manager -> {
            SkillTranslationAudit.run();
            SkillSpriteAudit.run(manager);
        });
        // Sort after every vanilla listener so the language reload (I18n) has populated first.
        event.addDependency(event.getNameLookup().apply(event.getLastVanillaListener()), id);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ChroniclesKeybinds.tick();
        XpAffordabilityNotifier.tick();
        VeinSightScanner.tick();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            VeinSightRenderer.render(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                    mc.gameRenderer.getMainCamera().position(), mc.level.getGameTime());
        }
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent.CanRender event) {
        if (event.getEntity() instanceof Player player) {
            event.setContent(PlayerNameplateRenderer.decorate(event.getContent(), player));
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (WizardsStudyHandler.isMagicallyInfused(event.getItemStack())) {
            List<Component> tooltip = event.getToolTip();
            tooltip.add(Math.min(1, tooltip.size()), WizardsStudyHandler.magicallyInfusedLine());
        }
        if (GardenersInfusionHandler.isInfused(event.getItemStack())) {
            List<Component> tooltip = event.getToolTip();
            tooltip.add(Math.min(1, tooltip.size()), GardenersInfusionHandler.infusedLine());
        }
    }
}
