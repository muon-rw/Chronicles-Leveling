package dev.muon.chronicles_leveling.client.screen;

import dev.muon.chronicles_leveling.client.screen.ChroniclesSprites.IconCoord;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "Attributes" tab — categorized read-only display of combat-relevant attributes,
 * grouped into Melee / Ranged / Defense / Magic cards.
 *
 * <p>Rows whose attribute isn't currently registered (e.g. Combat-Attributes
 * entries on a server that doesn't have the mod) are silently skipped, and any
 * card whose rows all skip is omitted entirely.
 *
 * <p>Same 176×166 footprint as the level-up screen.
 *
 * <p>Design based off of PlayerEx, licensed MIT.
 */
public class AttributesScreen extends Screen {

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 166;
    private static final int PARCHMENT_OFFSET_X = 5;
    private static final int PARCHMENT_OFFSET_Y = 5;

    private static final int TITLE_Y = 12;
    private static final int TITLE_DIVIDER_GAP = 1;
    private static final int TITLE_DIVIDER_INSET = 10;

    private static final int CARD_LEFT_X = 8;
    private static final int CARD_RIGHT_X = 168;
    private static final int CARD_GAP = 1;
    private static final int CARD_BORDER = 1;
    private static final int CARD_HEADER_H = 9;
    private static final int CARDS_TOP_OFFSET = 2;

    private static final int ROW_PITCH = 6;
    private static final float ROW_SCALE = 0.75f;

    private static final int NAME_INSET = 2;
    private static final int VALUE_INSET = 2;
    private static final int HEADER_ICON_X = 4;
    private static final int HEADER_TEXT_GAP = 3;

    private static final int TEXT_HEIGHT = 8;

    private static final int COLOR_TITLE = 0xFF3F3F3F;
    private static final int COLOR_NAME = 0xFF3F3F3F;
    private static final int COLOR_VALUE = 0xFF8B5A2B;
    private static final int COLOR_BORDER = 0xFF8B5A2B;

    private int leftPos;
    private int topPos;

    private static final List<Category> CATEGORIES = List.of(
            new Category(
                    "chronicles_leveling.screen.attributes.category.melee",
                    new IconCoord(ChroniclesSprites.SWORD_U, ChroniclesSprites.SWORD_V),
                    List.of(
                            attr("minecraft:generic.attack_damage", "chronicles_leveling.attribute.attack_damage"),
                            attr("minecraft:generic.attack_speed", "chronicles_leveling.attribute.attack_speed"),
                            attr("combat_attributes:melee_crit_chance", "chronicles_leveling.attribute.crit_chance"),
                            attr("combat_attributes:melee_crit_damage", "chronicles_leveling.attribute.crit_damage")
                    )
            ),
            new Category(
                    "chronicles_leveling.screen.attributes.category.ranged",
                    new IconCoord(ChroniclesSprites.BOW_U, ChroniclesSprites.BOW_V),
                    List.of(
                            attr("combat_attributes:ranged_damage", "chronicles_leveling.attribute.ranged_damage"),
                            attr("combat_attributes:draw_speed", "chronicles_leveling.attribute.pull_time"),
                            attr("combat_attributes:arrow_velocity", "chronicles_leveling.attribute.velocity"),
                            attr("combat_attributes:ranged_crit_chance", "chronicles_leveling.attribute.crit_chance"),
                            attr("combat_attributes:ranged_crit_damage", "chronicles_leveling.attribute.crit_damage")
                    )
            ),
            new Category(
                    "chronicles_leveling.screen.attributes.category.defense",
                    new IconCoord(ChroniclesSprites.SHIELD_U, ChroniclesSprites.SHIELD_V),
                    List.of(
                            health(),
                            attr("minecraft:generic.max_health", "chronicles_leveling.attribute.max_health"),
                            attr("minecraft:generic.armor", "chronicles_leveling.attribute.armor"),
                            attr("minecraft:generic.armor_toughness", "chronicles_leveling.attribute.armor_toughness")
                    )
            ),
            new Category(
                    "chronicles_leveling.screen.attributes.category.magic",
                    new IconCoord(ChroniclesSprites.MOON_U, ChroniclesSprites.MOON_V),
                    List.of(
                            attr("combat_attributes:magic_crit_chance", "chronicles_leveling.attribute.crit_chance"),
                            attr("combat_attributes:magic_crit_damage", "chronicles_leveling.attribute.crit_damage")
                    )
            )
    );

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

        renderTitle(graphics);

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) renderCards(graphics, player);
    }

    private void renderTitle(GuiGraphicsExtractor graphics) {
        Component title = Component.translatable("chronicles_leveling.screen.attributes.title");
        int textW = font.width(title);
        int textX = leftPos + (IMAGE_WIDTH - textW) / 2;
        int textY = topPos + TITLE_Y;
        graphics.text(font, title, textX, textY, COLOR_TITLE, false);

        int dividerY = textY + TEXT_HEIGHT + TITLE_DIVIDER_GAP;
        graphics.fill(
                leftPos + TITLE_DIVIDER_INSET, dividerY,
                leftPos + IMAGE_WIDTH - TITLE_DIVIDER_INSET, dividerY + 1,
                COLOR_BORDER
        );
    }

    private void renderCards(GuiGraphicsExtractor graphics, LocalPlayer player) {
        int dividerBottom = topPos + TITLE_Y + TEXT_HEIGHT + TITLE_DIVIDER_GAP + 1;
        int y = dividerBottom + CARDS_TOP_OFFSET;

        for (Category cat : CATEGORIES) {
            List<RowSnapshot> snapshots = collectRows(cat, player);
            if (snapshots.isEmpty()) continue;

            int height = CARD_BORDER + CARD_HEADER_H + snapshots.size() * ROW_PITCH + CARD_BORDER;
            renderCard(graphics, cat, snapshots, y, height);
            y += height + CARD_GAP;
        }
    }

    private static List<RowSnapshot> collectRows(Category cat, LocalPlayer player) {
        List<RowSnapshot> out = new ArrayList<>(cat.rows().size());
        for (AttrRow row : cat.rows()) {
            row.value(player).ifPresent(v ->
                    out.add(new RowSnapshot(Component.translatable(row.labelKey()), v)));
        }
        return out;
    }

    private void renderCard(GuiGraphicsExtractor graphics, Category cat, List<RowSnapshot> rows, int cardTop, int cardHeight) {
        int cardLeft = leftPos + CARD_LEFT_X;
        int cardRight = leftPos + CARD_RIGHT_X;
        int cardWidth = cardRight - cardLeft;

        graphics.outline(cardLeft, cardTop, cardWidth, cardHeight, COLOR_BORDER);

        renderCardHeader(graphics, cat, cardLeft, cardTop);
        renderCardRows(graphics, rows, cardLeft, cardRight, cardTop);
    }

    private void renderCardHeader(GuiGraphicsExtractor graphics, Category cat, int cardLeft, int cardTop) {
        int iconX = cardLeft + HEADER_ICON_X;
        int iconY = cardTop + CARD_BORDER;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ChroniclesTextures.GUI,
                iconX, iconY,
                cat.icon().u(), cat.icon().v(),
                ChroniclesSprites.ICON_SIZE, ChroniclesSprites.ICON_SIZE,
                ChroniclesSprites.SHEET_SIZE, ChroniclesSprites.SHEET_SIZE
        );

        Component title = Component.translatable(cat.titleKey());
        int titleX = iconX + ChroniclesSprites.ICON_SIZE + HEADER_TEXT_GAP;
        // 9px icon + 8px text — bias 1px down so text baselines line up with the icon's center.
        int titleY = iconY + (ChroniclesSprites.ICON_SIZE - TEXT_HEIGHT) / 2 + 1;
        graphics.text(font, title, titleX, titleY, COLOR_NAME, false);
    }

    private void renderCardRows(GuiGraphicsExtractor graphics, List<RowSnapshot> rows, int cardLeft, int cardRight, int cardTop) {
        int nameX = cardLeft + NAME_INSET;
        int valueRightX = cardRight - VALUE_INSET;
        int rowY = cardTop + CARD_BORDER + CARD_HEADER_H;

        for (RowSnapshot row : rows) {
            renderScaledRow(graphics, row, nameX, valueRightX, rowY);
            rowY += ROW_PITCH;
        }
    }

    private void renderScaledRow(GuiGraphicsExtractor graphics, RowSnapshot row, int nameX, int valueRightX, int y) {
        String value = formatValue(row.value());
        int valueW = font.width(value);

        graphics.pose().pushMatrix();
        graphics.pose().translate(nameX, y);
        graphics.pose().scale(ROW_SCALE, ROW_SCALE);
        graphics.text(font, row.label(), 0, 0, COLOR_NAME, false);
        graphics.pose().popMatrix();

        graphics.pose().pushMatrix();
        graphics.pose().translate(valueRightX, y);
        graphics.pose().scale(ROW_SCALE, ROW_SCALE);
        graphics.text(font, value, -valueW, 0, COLOR_VALUE, false);
        graphics.pose().popMatrix();
    }

    private static String formatValue(double value) {
        if (Math.abs(value - Math.round(value)) < 0.001) {
            return Long.toString(Math.round(value));
        }
        return String.format("%.2f", value);
    }

    private static AttrRow attr(String attrId, String labelKey) {
        Identifier id = Identifier.tryParse(attrId);
        ResourceKey<Attribute> key = id == null ? null : ResourceKey.create(Registries.ATTRIBUTE, id);
        return new AttrRow(labelKey, player -> {
            if (key == null) return Optional.empty();
            Optional<? extends Holder<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(key);
            if (holder.isEmpty()) return Optional.empty();
            AttributeInstance instance = player.getAttribute(holder.get());
            return instance == null ? Optional.empty() : Optional.of(instance.getValue());
        });
    }

    private static AttrRow health() {
        return new AttrRow(
                "chronicles_leveling.attribute.health",
                player -> Optional.of((double) player.getHealth())
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Category(String titleKey, IconCoord icon, List<AttrRow> rows) {}

    private record AttrRow(String labelKey, ValueProvider valueProvider) {
        Optional<Double> value(LocalPlayer player) {
            return valueProvider.get(player);
        }
    }

    @FunctionalInterface
    private interface ValueProvider {
        Optional<Double> get(LocalPlayer player);
    }

    private record RowSnapshot(Component label, double value) {}
}
