package dev.muon.chronicles_leveling.client.screen;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.client.screen.ChroniclesSprites.IconCoord;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * "Attributes" tab — categorized read-only display of attributes, grouped into
 * Melee / Ranged / Defense / Magic cards. Each card's attribute list is
 * configurable via {@link dev.muon.chronicles_leveling.config.ConfigClient.AttributePages}.
 *
 * <p>Unregistered ids (e.g. Combat-Attributes entries on a server that doesn't
 * have it loaded) are silently skipped and logged once per JVM session. Cards
 * whose rows all skip are omitted entirely.
 *
 * <p>Same 176×166 footprint as the level-up screen.
 *
 * <p>Design based off of PlayerEx, licensed MIT.
 */
public class AttributesScreen extends Screen {

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 166;
    private static final int PARCHMENT_OFFSET_X = 6;
    private static final int PARCHMENT_OFFSET_Y = 6;

    private static final int TITLE_Y = 12;
    private static final float TITLE_SCALE = 1.2f;
    private static final int TITLE_DIVIDER_GAP = 2;
    private static final int TITLE_DIVIDER_LEFT_INSET = 9;
    private static final int TITLE_DIVIDER_RIGHT_INSET = 9;

    private static final int CARD_LEFT_X = 8;
    private static final int CARD_RIGHT_X = 168;
    private static final int CARD_GAP = 2;
    private static final int CARD_COLUMN_GAP = 2;
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

    /**
     * Label overrides keyed by attribute id. Entries here use Chronicles' own
     * lang keys (shorter / context-trimmed names like "Crit. Chance" instead of
     * "Melee Critical Chance"). Unmapped ids fall back to the attribute's own
     * description id, so user-added attributes still render with a sensible name.
     */
    private static final Map<Identifier, String> LABEL_OVERRIDES = Map.ofEntries(
            label("minecraft", "armor_toughness", "armor_toughness"),
            label("minecraft", "knockback_resistance", "knockback_resistance"),
            label("minecraft", "block_interaction_range", "block_reach"),
            label("minecraft", "entity_interaction_range", "attack_range"),
            label("combat_attributes", "arrow_velocity", "velocity"),
            label("combat_attributes", "melee_crit_chance", "crit_chance"),
            label("combat_attributes", "melee_crit_damage", "crit_damage"),
            label("combat_attributes", "ranged_crit_chance", "crit_chance"),
            label("combat_attributes", "ranged_crit_damage", "crit_damage"),
            label("combat_attributes", "magic_crit_chance", "crit_chance"),
            label("combat_attributes", "magic_crit_damage", "crit_damage")
    );

    /** One log entry per missing-id per JVM session — render fires every frame. */
    private static final Set<Identifier> LOGGED_MISSING = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final CategoryDef MELEE = new CategoryDef(
            "chronicles_leveling.screen.attributes.category.melee",
            new IconCoord(ChroniclesSprites.SWORD_U, ChroniclesSprites.SWORD_V),
            () -> Configs.CLIENT.attributePages.melee.get()
    );

    private static final CategoryDef RANGED = new CategoryDef(
            "chronicles_leveling.screen.attributes.category.ranged",
            new IconCoord(ChroniclesSprites.BOW_U, ChroniclesSprites.BOW_V),
            () -> Configs.CLIENT.attributePages.ranged.get()
    );

    private static final CategoryDef DEFENSE = new CategoryDef(
            "chronicles_leveling.screen.attributes.category.defense",
            new IconCoord(ChroniclesSprites.SHIELD_U, ChroniclesSprites.SHIELD_V),
            () -> Configs.CLIENT.attributePages.defense.get()
    );

    private static final CategoryDef MAGIC = new CategoryDef(
            "chronicles_leveling.screen.attributes.category.magic",
            new IconCoord(ChroniclesSprites.MOON_U, ChroniclesSprites.MOON_V),
            () -> Configs.CLIENT.attributePages.magic.get()
    );

    private static final CategoryDef MISC = new CategoryDef(
            "chronicles_leveling.screen.attributes.category.misc",
            new IconCoord(ChroniclesSprites.PICKAXE_U, ChroniclesSprites.PICKAXE_V),
            () -> Configs.CLIENT.attributePages.misc.get()
    );

    /**
     * Two-column layout: cards in each column stack independently, so the second
     * row's y in a given column is driven by that column's first-row card height
     * (left and right columns can end up at different vertical positions).
     */
    private static final List<List<CategoryDef>> COLUMNS = List.of(
            List.of(MELEE, DEFENSE),
            List.of(RANGED, MAGIC, MISC)
    );

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

        renderTitle(graphics);

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) renderCards(graphics, player, mouseX, mouseY);
    }

    private void renderTitle(GuiGraphicsExtractor graphics) {
        Component title = Component.translatable("chronicles_leveling.screen.attributes.title");
        int textW = font.width(title);
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos + IMAGE_WIDTH / 2f, topPos + TITLE_Y);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.text(font, title, -textW / 2, 0, COLOR_TITLE, false);
        graphics.pose().popMatrix();

        int dividerY = topPos + TITLE_Y + scaledTitleHeight() + TITLE_DIVIDER_GAP;
        graphics.fill(
                leftPos + TITLE_DIVIDER_LEFT_INSET, dividerY,
                leftPos + IMAGE_WIDTH - TITLE_DIVIDER_RIGHT_INSET, dividerY + 1,
                COLOR_BORDER
        );
    }

    private static int scaledTitleHeight() {
        return (int) Math.ceil(TEXT_HEIGHT * TITLE_SCALE);
    }

    private void renderCards(GuiGraphicsExtractor graphics, LocalPlayer player, int mouseX, int mouseY) {
        int dividerBottom = topPos + TITLE_Y + scaledTitleHeight() + TITLE_DIVIDER_GAP + 1;
        int startY = dividerBottom + CARDS_TOP_OFFSET;

        int innerLeft = leftPos + CARD_LEFT_X;
        int innerRight = leftPos + CARD_RIGHT_X;
        int columnWidth = (innerRight - innerLeft - CARD_COLUMN_GAP) / 2;

        for (int col = 0; col < COLUMNS.size(); col++) {
            int cardLeft = innerLeft + col * (columnWidth + CARD_COLUMN_GAP);
            int cardRight = cardLeft + columnWidth;
            int y = startY;

            for (CategoryDef cat : COLUMNS.get(col)) {
                List<RowSnapshot> snapshots = collectRows(cat, player);
                if (snapshots.isEmpty()) continue;

                int height = CARD_BORDER + CARD_HEADER_H + snapshots.size() * ROW_PITCH + CARD_BORDER;
                renderCard(graphics, cat, snapshots, cardLeft, cardRight, y, height, mouseX, mouseY);
                y += height + CARD_GAP;
            }
        }
    }

    private static List<RowSnapshot> collectRows(CategoryDef cat, LocalPlayer player) {
        List<? extends Identifier> ids = cat.idsSource().get();
        List<RowSnapshot> out = new ArrayList<>(ids.size());
        for (Identifier id : ids) {
            resolveRow(id, player).ifPresent(out::add);
        }
        return out;
    }

    private static Optional<RowSnapshot> resolveRow(Identifier id, LocalPlayer player) {
        // Prefer the player's own live AttributeInstance if it's been touched
        // (e.g. equipment modifiers applied). AttributeMap keys by holder identity,
        // and the holder the modifier was applied with may differ from the one
        // BuiltInRegistries returns to us — id-matching is the reliable bridge.
        AttributeInstance instance = findInstanceById(player, id);
        if (instance == null) {
            ResourceKey<Attribute> key = ResourceKey.create(Registries.ATTRIBUTE, id);
            Optional<? extends Holder<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(key);
            if (holder.isEmpty()) {
                if (LOGGED_MISSING.add(id)) {
                    ChroniclesLeveling.LOG.warn("AttributesScreen: skipping unregistered attribute {}", id);
                }
                return Optional.empty();
            }
            instance = player.getAttribute(holder.get());
            if (instance == null) return Optional.empty();
        }

        Holder<Attribute> attr = instance.getAttribute();
        Optional<Double> percentScale = Services.PLATFORM.percentScaleForAttribute(attr);
        Component fullName = Component.translatable(attr.value().getDescriptionId());
        return Optional.of(new RowSnapshot(
                id, labelFor(id, attr), fullName,
                instance.getValue(), percentScale,
                attr, instance.getBaseValue(),
                new ArrayList<>(instance.getModifiers())));
    }

    private static AttributeInstance findInstanceById(LocalPlayer player, Identifier id) {
        AttributeMap map = player.getAttributes();
        for (AttributeInstance inst : map.getSyncableAttributes()) {
            if (inst.getAttribute().is(id)) {
                return inst;
            }
        }
        return null;
    }

    private static Component labelFor(Identifier id, Holder<Attribute> holder) {
        String overrideKey = LABEL_OVERRIDES.get(id);
        if (overrideKey != null) return Component.translatable(overrideKey);
        return Component.translatable(holder.value().getDescriptionId());
    }

    private void renderCard(GuiGraphicsExtractor graphics, CategoryDef cat, List<RowSnapshot> rows,
                            int cardLeft, int cardRight, int cardTop, int cardHeight,
                            int mouseX, int mouseY) {
        graphics.outline(cardLeft, cardTop, cardRight - cardLeft, cardHeight, COLOR_BORDER);

        renderCardHeader(graphics, cat, cardLeft, cardTop);
        renderCardRows(graphics, rows, cardLeft, cardRight, cardTop, mouseX, mouseY);
    }

    private void renderCardHeader(GuiGraphicsExtractor graphics, CategoryDef cat, int cardLeft, int cardTop) {
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

    private void renderCardRows(GuiGraphicsExtractor graphics, List<RowSnapshot> rows, int cardLeft, int cardRight, int cardTop,
                                int mouseX, int mouseY) {
        int nameX = cardLeft + NAME_INSET;
        int valueRightX = cardRight - VALUE_INSET;
        int rowY = cardTop + CARD_BORDER + CARD_HEADER_H;
        int hoverX = cardLeft + CARD_BORDER;
        int hoverW = cardRight - cardLeft - 2 * CARD_BORDER;

        for (RowSnapshot row : rows) {
            renderScaledRow(graphics, row, nameX, valueRightX, rowY);
            if (isHovered(mouseX, mouseY, hoverX, rowY, hoverW, ROW_PITCH)) {
                int valueScreenW = scaledValueWidth(row);
                int valueLeft = valueRightX - valueScreenW;
                if (mouseX >= valueLeft) {
                    graphics.setComponentTooltipForNextFrame(font,
                            AttributeLineRenderer.valueBreakdown(row.attribute(), row.value(), row.baseValue(), row.modifiers(), row.percentScale()),
                            mouseX, mouseY);
                } else {
                    descriptionFor(row.id()).ifPresent(desc ->
                            graphics.setComponentTooltipForNextFrame(font,
                                    List.of(row.fullName(), desc),
                                    mouseX, mouseY));
                }
            }
            rowY += ROW_PITCH;
        }
    }

    private int scaledValueWidth(RowSnapshot row) {
        String value = formatValue(row.value(), row.percentScale());
        return (int) Math.ceil(font.width(value) * ROW_SCALE);
    }

    private static boolean isHovered(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /**
     * Best-effort lookup across known attribute-description key conventions.
     * Stops at the first key that's actually present in the active language so we
     * never render a raw {@code attribute.foo.bar.desc} string. Returns empty
     * when no convention has a translation, suppressing the tooltip entirely.
     *
     * <ul>
     *   <li>Chronicles / Combat-Attributes: {@code attribute.<ns>.<path>.desc}</li>
     *   <li>Additional Entity Attributes:  {@code attribute.name.<ns>.<path>.desc}</li>
     *   <li>Spell Engine:                  {@code description.attribute.name.<ns>.<path>}</li>
     * </ul>
     */
    private static Optional<Component> descriptionFor(Identifier id) {
        String ns = id.getNamespace();
        String path = id.getPath();
        String[] keys = {
                "attribute." + ns + "." + path + ".desc",
                "attribute.name." + ns + "." + path + ".desc",
                "description.attribute.name." + ns + "." + path
        };
        Language lang = Language.getInstance();
        for (String key : keys) {
            if (lang.has(key)) {
                return Optional.of(Component.translatable(key)
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
        return Optional.empty();
    }

    private void renderScaledRow(GuiGraphicsExtractor graphics, RowSnapshot row, int nameX, int valueRightX, int y) {
        String value = formatValue(row.value(), row.percentScale());
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

    private static String formatValue(double value, Optional<Double> percentScale) {
        if (percentScale.isPresent()) {
            double scaled = value * percentScale.get();
            if (Math.abs(scaled - Math.round(scaled)) < 0.05) {
                return Math.round(scaled) + "%";
            }
            return String.format("%.1f%%", scaled);
        }
        if (Math.abs(value - Math.round(value)) < 0.001) {
            return Long.toString(Math.round(value));
        }
        return String.format("%.2f", value);
    }

    private static Map.Entry<Identifier, String> label(String namespace, String path, String labelKeySuffix) {
        return Map.entry(
                Identifier.fromNamespaceAndPath(namespace, path),
                "chronicles_leveling.attribute." + labelKeySuffix
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record CategoryDef(String titleKey, IconCoord icon, Supplier<List<? extends Identifier>> idsSource) {}

    private record RowSnapshot(
            Identifier id,
            Component label,
            Component fullName,
            double value,
            Optional<Double> percentScale,
            Holder<Attribute> attribute,
            double baseValue,
            List<AttributeModifier> modifiers
    ) {}
}
