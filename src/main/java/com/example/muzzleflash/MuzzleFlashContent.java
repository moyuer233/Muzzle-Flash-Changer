package com.example.muzzleflash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 管理 .minecraft/tacz/ 下的配置和贴图。
 * 贴图从 jar 复制到 tacz_default_gun，reload 时扫描所有枪包的
 * assets/<namespace>/textures/muzzle/ 目录注册贴图。
 */
public class MuzzleFlashContent {
    private static final Set<ResourceLocation> registeredTextures = new HashSet<>();
    private static final Map<ResourceLocation, int[]> TEXTURE_SIZES = new HashMap<>();

    /** Returns [width, height, effectiveWidth, effectiveHeight] for a registered texture, or null if unknown. */
    public static int[] getTextureSize(ResourceLocation loc) {
        return TEXTURE_SIZES.get(loc);
    }

    /** 返回贴图有效内容（非透明像素包围盒）的最大维度；无有效内容时回退到整张贴图最大维度。 */
    public static int getEffectiveMaxDimension(ResourceLocation loc) {
        int[] size = TEXTURE_SIZES.get(loc);
        if (size == null) return -1;
        if (size.length >= 4 && size[2] > 0 && size[3] > 0) {
            return Math.max(size[2], size[3]);
        }
        return Math.max(size[0], size[1]);
    }

    public static Path getContentRoot() {
        return FMLPaths.GAMEDIR.get().resolve("tacz");
    }

    public static Path getConfigFile() {
        return getContentRoot().resolve("muzzleflash.json");
    }

    /**
     * 缺啥补啥：模板 config + 将 jar 内置贴图复制到 tacz_default_gun。
     * 如果 tacz_default_gun 尚未生成则跳过贴图复制（下次 reload 会重试）。
     */
    public static void ensureFirstLaunchTemplate() {
        try {
            Path root = getContentRoot();
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }
            Path config = getConfigFile();
            if (!Files.exists(config)) {
                MuzzleFlashConfig.saveTemplate(config);
            }
            // 向 TACZ 默认枪包注入本模组内容（含贴图复制）
            ensureDefaultGunPackCompat();
        } catch (IOException e) {
            MuzzleFlashMod.LOGGER.error("[MuzzleFlash] failed to ensure template", e);
        }
    }

    /**
     * 向 TACZ 默认枪包（tacz_default_gun）注入本模组内容：
     *  - 贴图 → 复制到 assets/tacz/textures/muzzle/default/
     *  - muzzleflash_compat.json → 完整动画配置
     *  - README.txt → 追加说明
     */
    private static void ensureDefaultGunPackCompat() throws IOException {
        Path defaultPack = getContentRoot().resolve("tacz_default_gun");
        if (!Files.isDirectory(defaultPack)) {
            MuzzleFlashMod.LOGGER.debug("[MuzzleFlash] tacz_default_gun not found, skip compat injection");
            return;
        }

        // 1. 贴图：从 jar 复制到 tacz_default_gun/assets/tacz/textures/muzzle/default/
        Path texTarget = defaultPack.resolve("assets/tacz/textures/muzzle/default");
        Files.createDirectories(texTarget);
        copyBuiltinTextures(texTarget);

        // 2. muzzleflash_compat.json — 生成完整配置（包含 defaultAnimation）
        Path compatTarget = defaultPack.resolve("muzzleflash_compat.json");
        if (!Files.exists(compatTarget)) {
            writeDefaultCompatJson(compatTarget);
            MuzzleFlashMod.LOGGER.info("[MuzzleFlash] injected muzzleflash_compat.json into tacz_default_gun");
        }

        // 3. README.txt 追加内容
        Path readme = defaultPack.resolve("README.txt");
        if (Files.exists(readme)) {
            String content = Files.readString(readme);
            if (!content.contains("MuzzleFlash")) {
                String append = "\n\n============================================\n" +
                        "  MuzzleFlash Mod - 枪口火焰增强\n" +
                        "============================================\n" +
                        "本枪包已启用 MuzzleFlash 模组的枪口火焰控制（muzzleflash_compat.json）。\n" +
                        "自定义枪口火焰贴图位于：assets/tacz/textures/muzzle/default/\n" +
                        "在游戏中输入 /reload 可重载枪口火焰配置。\n\n" +
                        "如需关闭，删除 muzzleflash_compat.json 即可恢复原版 TACZ 枪口火焰。\n";
                Files.writeString(readme, content + append);
                MuzzleFlashMod.LOGGER.info("[MuzzleFlash] appended README.txt for tacz_default_gun");
            }
        }
    }

    /**
     * 写入默认枪包的 muzzleflash_compat.json，包含 defaultAnimation 配置。
     */
    private static void writeDefaultCompatJson(Path compatTarget) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", new GunPackCompatManager.GunPackConfig().comment);
        root.addProperty("muzzle_flash_mode", "tmfmod");

        // defaultAnimation
        JsonObject defAnim = new JsonObject();
        defAnim.addProperty("_comment", new GunPackCompatManager.GunAnimationDef().comment);
        defAnim.addProperty("frameDurationMs", 30);
        defAnim.addProperty("FrameDuration", 50);
        defAnim.addProperty("scale", 1.5f);
        defAnim.addProperty("autoScale", true);
        defAnim.addProperty("baseTextureSize", 300);
        defAnim.addProperty("flashDelayMs", 0);
        defAnim.addProperty("offsetX", 0.0f);
        defAnim.addProperty("offsetY", 0.0f);
        defAnim.addProperty("offsetZ", 0.0f);

        // 生成默认枪焰帧列表（引用 muzzleflash:tacz/textures/muzzle/default/frame_1..8）
        JsonArray frames = new JsonArray();
        for (int i = 1; i <= 8; i++) {
            frames.add(new JsonPrimitive(MuzzleFlashMod.MODID + ":tacz/textures/muzzle/default/frame_" + i));
        }
        defAnim.add("defaultmuzzleflashframes", frames);
        root.add("defaultAnimation", defAnim);

        // guns 留空
        root.add("guns", new JsonObject());

        Files.writeString(compatTarget, new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    public static synchronized void reload() {
        ensureFirstLaunchTemplate();
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        if (tm == null) {
            MuzzleFlashMod.LOGGER.warn("[MuzzleFlash] TextureManager not available, skipping texture reload");
            return;
        }

        // 释放旧贴图
        for (ResourceLocation loc : registeredTextures) {
            try {
                tm.release(loc);
            } catch (Exception ignored) {}
        }
        registeredTextures.clear();
        TEXTURE_SIZES.clear();

        // 扫描所有枪包的贴图
        int count = scanAllGunPackTextures(tm);

        // 加载全局配置
        MuzzleFlashConfig.load();

        // 扫描枪包配置（包含自动生成 frames）
        GunPackCompatManager.scan();

        MuzzleFlashMod.LOGGER.info("[MuzzleFlash] reload: {} textures registered, {} tmfmod namespaces, {} configs",
                count, GunPackCompatManager.getCacheSize(), GunPackCompatManager.getConfigCount());

        if (MuzzleFlashConfig.isShowReloadMessage()) {
            int tmfmodCount = GunPackCompatManager.getCacheSize();
            int configCount = GunPackCompatManager.getConfigCount();
            Component msg = Component.literal(
                    "\u00a7a[MuzzleFlash] \u00a7freloaded \u00a7e" + count + " \u00a7ftextures, \u00a7e" + tmfmodCount + " \u00a7ftmfmod namespaces, \u00a7e" + configCount + " \u00a7fgun pack configs"
            );
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(msg);
            } else if (mc.gui != null) {
                ChatComponent chat = mc.gui.getChat();
                if (chat != null) chat.addMessage(msg);
            }
        }
    }

    /**
     * 检查枪包根目录是否存在 muzzleflash_compat.json 且 mode 为 tmfmod。
     */
    private static boolean isTmfModPack(Path gunPackDir) {
        Path compatFile = gunPackDir.resolve("muzzleflash_compat.json");
        if (!Files.exists(compatFile)) return false;
        try (Reader reader = Files.newBufferedReader(compatFile)) {
            JsonObject json = new Gson().fromJson(reader, JsonObject.class);
            return json != null && "tmfmod".equals(json.get("muzzle_flash_mode") != null ? json.get("muzzle_flash_mode").getAsString() : null);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 扫描 tacz/ 下已启用 tmfmod 模式的枪包，查找 assets/<namespace>/textures/muzzle/ 目录，
     * 将其中的 PNG 注册为 muzzleflash 贴图。
     */
    private static int scanAllGunPackTextures(TextureManager tm) {
        Path taczDir = getContentRoot();
        if (!Files.isDirectory(taczDir)) return 0;

        int[] total = {0};
        try (Stream<Path> gunPacks = Files.list(taczDir)) {
            gunPacks.filter(Files::isDirectory).forEach(gunPack -> {
                if (!isTmfModPack(gunPack)) return;

                Path assetsDir = gunPack.resolve("assets");
                if (!Files.isDirectory(assetsDir)) return;
                try (Stream<Path> namespaces = Files.list(assetsDir)) {
                    namespaces.filter(Files::isDirectory).forEach(ns -> {
                        String namespace = ns.getFileName().toString();
                        Path muzzleDir = ns.resolve("textures/muzzle");
                        if (!Files.isDirectory(muzzleDir)) return;
                        try (Stream<Path> files = Files.walk(muzzleDir)) {
                            var iter = files.filter(Files::isRegularFile)
                                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                                    .iterator();
                            while (iter.hasNext()) {
                                if (registerGunPackTexture(tm, muzzleDir, namespace, iter.next())) {
                                    total[0]++;
                                }
                            }
                        } catch (IOException e) {
                            MuzzleFlashMod.LOGGER.warn("[MuzzleFlash] error scanning textures in gun pack {}/{}", gunPack.getFileName(), namespace);
                        }
                    });
                } catch (IOException e) {
                    MuzzleFlashMod.LOGGER.warn("[MuzzleFlash] error scanning assets in {}", gunPack.getFileName());
                }
            });
        } catch (IOException e) {
            MuzzleFlashMod.LOGGER.error("[MuzzleFlash] failed to scan gun packs", e);
        }
        return total[0];
    }

    /**
     * 注册一张来自枪包的 muzzle 贴图。
     */
    private static boolean registerGunPackTexture(TextureManager tm, Path muzzleDir, String namespace, Path file) {
        try {
            String rel = muzzleDir.relativize(file).toString().replace('\\', '/');
            String relNoExt = rel.substring(0, rel.length() - 4);
            ResourceLocation loc = new ResourceLocation(MuzzleFlashMod.MODID, namespace + "/textures/muzzle/" + relNoExt);
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage img = NativeImage.read(in);
                DynamicTexture tex = new DynamicTexture(img);
                tm.register(loc, tex);
                int[] eff = computeEffectiveBounds(img);
                TEXTURE_SIZES.put(loc, new int[]{img.getWidth(), img.getHeight(), eff[0], eff[1]});
                registeredTextures.add(loc);
                return true;
            }
        } catch (Exception e) {
            MuzzleFlashMod.LOGGER.error("[MuzzleFlash] failed to register gun pack texture: {}", file, e);
            return false;
        }
    }

    /**
     * 计算贴图有效内容（非透明像素包围盒）的宽高。
     * 用于 autoScale：大贴图里火焰只占一小部分时，按有效内容缩放而非整张贴图。
     */
    private static int[] computeEffectiveBounds(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getPixelRGBA(x, y);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha > 8) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return new int[]{w, h};
        }
        return new int[]{maxX - minX + 1, maxY - minY + 1};
    }

    /**
     * 从 jar 内置资源复制贴图到指定目录。
     */
    private static void copyBuiltinTextures(Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        for (int i = 1; i <= 8; i++) {
            Path target = targetDir.resolve("frame_" + i + ".png");
            if (Files.exists(target)) continue;
            String resourcePath = "/assets/" + MuzzleFlashMod.MODID + "/textures/muzzle/default/frame_" + i + ".png";
            try (InputStream in = MuzzleFlashContent.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    MuzzleFlashMod.LOGGER.warn("[MuzzleFlash] built-in texture not found in jar: {}", resourcePath);
                    continue;
                }
                Files.copy(in, target);
                MuzzleFlashMod.LOGGER.debug("[MuzzleFlash] copied built-in texture: {}", target);
            }
        }
    }

    public static class ReloadListener extends SimplePreparableReloadListener<Void> {
        @Override
        protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void unused, ResourceManager resourceManager, ProfilerFiller profiler) {
            MuzzleFlashContent.reload();
        }
    }
}