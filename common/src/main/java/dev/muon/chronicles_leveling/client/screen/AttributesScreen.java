package dev.muon.chronicles_leveling.client.screen;

import dev.muon.chronicles_leveling.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.List;
import java.util.Optional;

/**
 * "Attributes" tab — read-only display of a curated list of combat-relevant
 * attributes pulled from {@link dev.muon.chronicles_leveling.config.ConfigClient#attributesPageEntries}.
 *
 * <p>Rows that name an attribute that isn't currently registered (e.g.
 * Combat-Attributes entries on a server that doesn't have it loaded) are
 * silently skipped.
 *
 * <p>Same 176×166 footprint as the level-up screen.
 */
public class AttributesScreen extends Screen {

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 166;
    private static final int ROW_HEIGHT = 11;
    private static final int FIRST_ROW_Y = 24;
    /** PlayerEx ships the parchment with a 4px transparent margin baked in; we offset the blit to compensate. */
    private static final int PARCHMENT_OFFSET_X = 4;
    private static final int PARCHMENT_OFFSET_Y = 4;

    private int leftPos;
    private int topPos;

    public AttributesScreen() {
        super(Component.translatable("chronicles_leveling.screen.attributes.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - IMAGE_WIDTH) / 2;
        this.topPos = (this.height - IMAGE_HEIGHT) / 2;
        addRenderableWidget(new ChroniclesTabBar(leftPos, topPos));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ChroniclesTextures.PARCHMENT,
                leftPos + PARCHMENT_OFFSET_X, topPos + PARCHMENT_OFFSET_Y,
                0, 0,
                IMAGE_WIDTH, IMAGE_HEIGHT,
                256, 256
        );
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ChroniclesTextures.GUI,
                leftPos, topPos,
                0, 0,
                IMAGE_WIDTH, IMAGE_HEIGHT,
                256, 256
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.text(font,
                Component.translatable("chronicles_leveling.screen.attributes.title"),
                leftPos + 8, topPos + 6, 0xFF3F3F3F, false);

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        List<? extends String> entries = Configs.CLIENT.attributesPageEntries.get();
        int row = 0;
        for (String entry : entries) {
            Identifier id = Identifier.tryParse(entry);
            if (id == null) continue;

            ResourceKey<Attribute> key = ResourceKey.create(Registries.ATTRIBUTE, id);
            Optional<? extends Holder<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(key);
            if (holder.isEmpty()) continue;

            AttributeInstance instance = player.getAttribute(holder.get());
            if (instance == null) continue;

            int rowY = topPos + FIRST_ROW_Y + row * ROW_HEIGHT;
            row++;

            Component name = Component.translatable(holder.get().value().getDescriptionId());
            String value = formatAttributeValue(instance.getValue());

            graphics.text(font, name, leftPos + 8, rowY, 0xFF3F3F3F, false);
            int valueW = font.width(value);
            graphics.text(font, value,
                    leftPos + IMAGE_WIDTH - 8 - valueW, rowY,
                    0xFF8B5A2B, false);

            if (rowY + ROW_HEIGHT >= topPos + IMAGE_HEIGHT - 4) break;
        }
    }

    private static String formatAttributeValue(double value) {
        if (Math.abs(value - Math.round(value)) < 0.001) {
            return Long.toString(Math.round(value));
        }
        return String.format("%.2f", value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
