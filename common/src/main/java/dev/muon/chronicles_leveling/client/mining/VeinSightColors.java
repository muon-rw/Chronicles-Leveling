package dev.muon.chronicles_leveling.client.mining;

import com.mojang.blaze3d.platform.NativeImage;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.config.ConfigSkills;
import dev.muon.chronicles_leveling.skill.SkillTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Client-side: which blocks Vein Sight highlights and the outline color for each. Precedence is config override,
 * then a color derived from the block's particle sprite (its dominant chromatic pixel), then the config fallback.
 * Derived colors are cached per block and cleared on resource reload (the sprite atlas is re-stitched).
 */
public final class VeinSightColors {

    private VeinSightColors() {}

    private static final Map<Block, Integer> COLOR_CACHE = new IdentityHashMap<>();
    private static Map<Identifier, Integer> overrideCache;

    private static final double MIN_SATURATION = 0.18;
    private static final double MIN_VALUE = 0.12;
    private static final double MAX_VALUE = 0.97;
    private static final int MIN_ALPHA = 128;

    public static boolean isOre(BlockState state) {
        return state.is(SkillTags.ORES) || overrides().containsKey(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public static int colorFor(BlockState state) {
        Block block = state.getBlock();
        Integer cached = COLOR_CACHE.get(block);
        if (cached != null) {
            return cached;
        }
        int color = resolve(state);
        COLOR_CACHE.put(block, color);
        return color;
    }

    private static int resolve(BlockState state) {
        Integer override = overrides().get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (override != null) {
            return override;
        }
        Integer derived = deriveFromSprite(state);
        return derived != null ? derived : fallbackColor();
    }

    private static Integer deriveFromSprite(BlockState state) {
        try {
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
                    .getBlockStateModelSet().getParticleMaterial(state).sprite();
            SpriteContents contents = sprite.contents();
            NativeImage image = contents.originalImage;
            int w = Math.min(contents.width(), image.getWidth());
            int h = Math.min(contents.height(), image.getHeight());
            double sumR = 0, sumG = 0, sumB = 0, total = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = image.getPixel(x, y);
                    int a = ARGB.alpha(argb);
                    if (a < MIN_ALPHA) {
                        continue;
                    }
                    int r = ARGB.red(argb), g = ARGB.green(argb), b = ARGB.blue(argb);
                    int max = Math.max(r, Math.max(g, b));
                    int min = Math.min(r, Math.min(g, b));
                    double sat = (max - min) / 255.0;
                    double val = max / 255.0;
                    if (sat < MIN_SATURATION || val < MIN_VALUE || val > MAX_VALUE) {
                        continue;
                    }
                    double weight = sat * (a / 255.0);
                    sumR += r * weight;
                    sumG += g * weight;
                    sumB += b * weight;
                    total += weight;
                }
            }
            if (total <= 0) {
                return null;
            }
            return ARGB.color(255, (int) Math.round(sumR / total), (int) Math.round(sumG / total), (int) Math.round(sumB / total));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int fallbackColor() {
        return parseHex(Configs.SKILLS.mining.veinSightFallbackColor.get(), 0xFFAAAAAA);
    }

    private static Map<Identifier, Integer> overrides() {
        if (overrideCache == null) {
            Map<Identifier, Integer> map = new HashMap<>();
            for (ConfigSkills.OreColor row : Configs.SKILLS.mining.veinSightColorOverrides) {
                map.put(row.block.get(), parseHex(row.hex.get(), 0xFFFFFFFF));
            }
            overrideCache = map;
        }
        return overrideCache;
    }

    private static int parseHex(String hex, int fallback) {
        try {
            return (int) Long.parseLong(hex.trim(), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void invalidate() {
        COLOR_CACHE.clear();
        overrideCache = null;
    }
}
