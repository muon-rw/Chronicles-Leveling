package dev.muon.chronicles_leveling.client.screen;

import dev.muon.chronicles_leveling.level.LevelingCurve;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import dev.muon.chronicles_leveling.network.NetworkDispatcher;
import dev.muon.chronicles_leveling.stat.ModStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * "Stats" tab — six rows, each showing the stat name, current value, and a
 * {@code +} button. Clicking {@code +} fires
 * {@link dev.muon.chronicles_leveling.network.message.AllocateStatPacket}; the
 * row updates as soon as the loader's auto-synced
 * {@link PlayerLevelData} attachment lands.
 *
 * <p>Sized 176×166 so it lines up with the inventory texture (from PlayerEx, licensed MIT)
 */
public class LevelUpScreen extends Screen {

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 166;
    private static final int ROW_HEIGHT = 18;
    private static final int FIRST_ROW_Y = 28;
    private static final int PARCHMENT_OFFSET_X = 4;
    private static final int PARCHMENT_OFFSET_Y = 4;

    private int leftPos;
    private int topPos;

    public LevelUpScreen() {
        super(Component.translatable("chronicles_leveling.screen.stats.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - IMAGE_WIDTH) / 2;
        this.topPos = (this.height - IMAGE_HEIGHT) / 2;

        addRenderableWidget(new ChroniclesTabBar(leftPos, topPos));

        // Per-stat allocation rows: one "+" button each, lined up down the right.
        for (int i = 0; i < ModStats.ALL.size(); i++) {
            ModStats.Entry stat = ModStats.ALL.get(i);
            int rowY = topPos + FIRST_ROW_Y + i * ROW_HEIGHT;
            int buttonX = leftPos + IMAGE_WIDTH - 24;

            addRenderableWidget(Button.builder(
                            Component.literal("+"),
                            btn -> NetworkDispatcher.sendAllocateStat(stat.id()))
                    .bounds(buttonX, rowY, 16, 14)
                    .build());
        }
    }

    /**
     * Render order for textured screens:
     * <ol>
     *   <li>{@code super.extractBackground} — vanilla dim/transparent fill.</li>
     *   <li>Our parchment + frame here, so widgets land on top of the paper.</li>
     *   <li>{@code super.extractRenderState} (handled by the framework) — buttons,
     *       tab bar, tooltips.</li>
     * </ol>
     * Blitting the panel inside {@code extractRenderState} would put it above
     * widgets
     */
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
        // gui.png contains the inventory-style frame with a transparent center;
        // layered on top of the parchment so the border reads cleanly.
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
                Component.translatable("chronicles_leveling.screen.stats.title"),
                leftPos + 8, topPos + 6, 0xFF3F3F3F, false);

        renderHeader(graphics);
        renderRows(graphics);
    }

    private void renderHeader(GuiGraphicsExtractor graphics) {
        var player = Minecraft.getInstance().player;
        PlayerLevelData data = player == null ? PlayerLevelData.DEFAULT : PlayerLevelManager.get(player);
        int rung = LevelingCurve.xpToNext(data.level());
        Component header = Component.translatable(
                "chronicles_leveling.screen.stats.header",
                data.level(), data.xp(), rung, data.unspentPoints()
        );
        graphics.text(font, header, leftPos + 8, topPos + FIRST_ROW_Y - 10, 0xFF5A5A5A, false);
    }

    private void renderRows(GuiGraphicsExtractor graphics) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        for (int i = 0; i < ModStats.ALL.size(); i++) {
            ModStats.Entry stat = ModStats.ALL.get(i);
            int rowY = topPos + FIRST_ROW_Y + i * ROW_HEIGHT;

            AttributeInstance instance = player.getAttribute(ModStats.get(stat.id()));
            int value = instance == null ? 0 : (int) Math.floor(instance.getValue());

            graphics.text(font,
                    Component.translatable("chronicles_leveling.stat." + stat.id()),
                    leftPos + 8, rowY + 3, 0xFF3F3F3F, false);

            String valueStr = Integer.toString(value);
            int valueW = font.width(valueStr);
            graphics.text(font, valueStr,
                    leftPos + IMAGE_WIDTH - 32 - valueW, rowY + 3,
                    0xFF8B5A2B, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
