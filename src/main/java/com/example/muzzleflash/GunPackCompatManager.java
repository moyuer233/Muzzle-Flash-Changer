package com.example.muzzleflash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 枪包兼容性管理器。
 * <p>
 * 扫描 .minecraft/tacz/ 下的所有枪包文件夹，查找 muzzleflash_compat.json 文件。
 * 每个枪包的 muzzleflash_compat.json 包含：
 *  - muzzle_flash_mode: "tmfmod" 或 "default"
 *  - defaultAnimation: { frameDurationMs, scale, autoScale, baseTextureSize, frames[] }
 *  - guns: { "namespace:gunid": { 自定义动画覆盖 } }
 * <p>
 * 如果 frames 列表为空，自动扫描 assets/{namespace}/textures/muzzle/ 目录生成。
 */
public class GunPackCompatManager {

    /** 每把枪的动画配置（支持字段：defaultmuzzleflashframes、muzzleframes、frameDurationMs、FrameDuration、scale、autoScale、baseTextureSize、flashDelayMs、offsetX、offsetY、offsetZ） */
    public static class GunAnimationDef {
        @SerializedName("_comment")
        public String comment = "枪焰动画字段说明: defaultmuzzleflashframes=默认帧列表; muzzleframes=单枪专用帧列表; frameDurationMs=每帧时长(毫秒); FrameDuration=总显示时长(毫秒, 0=未指定); scale=缩放倍率; autoScale=是否自动缩放; baseTextureSize=自动缩放参考像素尺寸; flashDelayMs=枪焰延迟启动(毫秒); offsetX/offsetY/offsetZ=枪焰位置偏移(世界单位, 1.0=1米, X右/Y上/Z前)。";
        /** 默认枪焰帧（原 frames 字段改名） */
        @SerializedName("defaultmuzzleflashframes")
        public String[] defaultmuzzleflashframes = new String[0];
        /** 单枪专用枪焰帧（textures/muzzle/<枪path> 目录匹配时生成） */
        @SerializedName("muzzleframes")
        public String[] muzzleframes = new String[0];
        /** 每帧时长（毫秒）。当 FrameDuration 未指定时才用于计算总时长 */
        public int frameDurationMs = 30;
        /** 枪焰总显示时长（毫秒）。0 表示未指定，回退到 frames.size() * frameDurationMs。默认 50ms 对齐原版 */
        @SerializedName("FrameDuration")
        public int frameDuration = 50;
        public float scale = 1.5f;
        public boolean autoScale = true;
        public int baseTextureSize = 300;
        /** 枪焰延迟启动时间（毫秒），0 表示无延迟 */
        public int flashDelayMs = 0;
        /** 枪焰位置偏移（世界单位，1.0 = 1 米），相对枪口骨骼：X 右、Y 上、Z 前 */
        public float offsetX = 0.0f;
        public float offsetY = 0.0f;
        public float offsetZ = 0.0f;

        /** 用给定的帧列表和总时长构建动画。 */
        public MuzzleFlashAnimation toAnimation(String[] resolvedFrames, int resolvedDuration) {
            List<ResourceLocation> list = new ArrayList<>();
            for (String s : resolvedFrames) {
                if (s == null || s.isEmpty()) continue;
                ResourceLocation loc = ResourceLocation.tryParse(s);
                if (loc != null) list.add(loc);
            }
            if (list.isEmpty()) return null;
            return new MuzzleFlashAnimation(list, frameDurationMs, resolvedDuration, scale, autoScale, baseTextureSize, offsetX, offsetY, offsetZ);
        }
    }

    /** 枪包的完整配置 */
    public static class GunPackConfig {
        @SerializedName("_comment")
        public String comment = "枪口火焰配置文件。muzzle_flash_mode: tmfmod=本模组接管枪焰, default=使用原版TACZ枪焰。defaultAnimation: 所有枪的默认枪焰动画。guns: 单枪覆盖配置, 键为 命名空间:枪id, 值字段与 defaultAnimation 相同。";
        public String muzzle_flash_mode = "default";
        public GunAnimationDef defaultAnimation = new GunAnimationDef();
        /** 按枪ID配置（直接使用 GunAnimationDef，包含 flashDelayMs 字段） */
        public Map<String, GunAnimationDef> guns = new HashMap<>();
    }

    /** namespace → 是否 tmfmod 模式 */
    private static final Map<String, Boolean> tmfModCache = new HashMap<>();

    /** namespace → 枪包配置（包含动画定义） */
    private static final Map<String, GunPackConfig> configCache = new HashMap<>();

    /** namespace → 已解析的默认动画 */
    private static final Map<String, MuzzleFlashAnimation> animationCache = new HashMap<>();

    /** namespace → 已解析的单枪动画（带缓存） */
    private static final Map<String, Map<String, MuzzleFlashAnimation>> gunAnimationCache = new HashMap<>();

    /** namespace → 已解析的单枪延迟（带缓存） */
    private static final Map<String, Map<String, Integer>> gunDelayCache = new HashMap<>();

    /** 全局 fallback 动画，当任何枪包都没有配置时使用 */
    private static MuzzleFlashAnimation fallbackAnimation = null;

    private static boolean scanned = false;

    /**
     * 扫描 .minecraft/tacz/ 下的所有枪包。
     */
    public static synchronized void scan() {
        tmfModCache.clear();
        configCache.clear();
        animationCache.clear();
        gunAnimationCache.clear();
        gunDelayCache.clear();
        scanned = false;

        Path taczPath = FMLPaths.GAMEDIR.get().resolve("tacz");
        if (!Files.isDirectory(taczPath)) {
            MuzzleFlashMod.LOGGER.info("[GunPackCompat] tacz directory not found: {}", taczPath);
            scanned = true;
            return;
        }

        Gson gson = new Gson();
        try (DirectoryStream<Path> gunPacks = Files.newDirectoryStream(taczPath)) {
            for (Path gunPackDir : gunPacks) {
                if (!Files.isDirectory(gunPackDir)) continue;

                Path compatFile = gunPackDir.resolve("muzzleflash_compat.json");
                if (!Files.exists(compatFile)) continue;

                GunPackConfig config = loadOrCreateConfig(compatFile, gunPackDir);
                String mode = config.muzzle_flash_mode;
                boolean isTmfMod = "tmfmod".equals(mode);

                MuzzleFlashMod.LOGGER.info("[GunPackCompat] gun pack {}: muzzle_flash_mode={} -> tmfmod={}",
                        gunPackDir.getFileName(), mode, isTmfMod);

                // 扫描 assets/ 子目录获取命名空间
                Path assetsDir = gunPackDir.resolve("assets");
                if (!Files.isDirectory(assetsDir)) continue;

                try (DirectoryStream<Path> nsDirs = Files.newDirectoryStream(assetsDir)) {
                    for (Path nsDir : nsDirs) {
                        if (!Files.isDirectory(nsDir)) continue;
                        String namespace = nsDir.getFileName().toString();

                        if (isTmfMod) {
                            tmfModCache.put(namespace, true);
                        }

                        // 保存该 namespace 的配置
                        configCache.put(namespace, config);

                        boolean needsSave = false;

                        // 1. 默认枪焰帧（defaultmuzzleflashframes）：来自 textures/muzzle/default/
                        List<String> defaultDiskFrames = scanMuzzleFolder(nsDir, namespace, "default");
                        String[] reconciledDefault = reconcileFrames(config.defaultAnimation.defaultmuzzleflashframes, defaultDiskFrames);
                        if (!arraysEqual(reconciledDefault, config.defaultAnimation.defaultmuzzleflashframes)) {
                            config.defaultAnimation.defaultmuzzleflashframes = reconciledDefault;
                            needsSave = true;
                        }

                        // 2. 单枪枪焰帧（muzzleframes）：扫描 textures/muzzle/ 下除 default 外的子目录，
                        //    子目录名与枪 id 的 path 部分匹配（如 rsh12 → re:rsh12）
                        Map<String, List<String>> gunFolderFrames = scanGunMuzzleFolders(nsDir, namespace);
                        for (Map.Entry<String, List<String>> entry : gunFolderFrames.entrySet()) {
                            String gunPath = entry.getKey();
                            String gunId = namespace + ":" + gunPath;
                            GunAnimationDef def = config.guns.computeIfAbsent(gunId, k -> new GunAnimationDef());
                            String[] reconciledGun = reconcileFrames(def.muzzleframes, entry.getValue());
                            if (!arraysEqual(reconciledGun, def.muzzleframes)) {
                                def.muzzleframes = reconciledGun;
                                needsSave = true;
                            }
                            // 自动生成该枪单独的 FrameDuration（默认 50ms，用户可改）
                            if (def.frameDuration <= 0) {
                                def.frameDuration = 50;
                                needsSave = true;
                            }
                        }

                        if (needsSave) {
                            saveConfig(compatFile, config);
                        }

                        MuzzleFlashMod.LOGGER.info("[GunPackCompat]  namespace '{}' -> tmfmod={}, defaultFrames={}, gunFolders={}",
                                namespace, isTmfMod,
                                config.defaultAnimation.defaultmuzzleflashframes != null ? config.defaultAnimation.defaultmuzzleflashframes.length : 0,
                                gunFolderFrames.size());
                    }
                }
            }
        } catch (IOException e) {
            MuzzleFlashMod.LOGGER.error("[GunPackCompat] error scanning gun packs", e);
        }

        scanned = true;
        MuzzleFlashMod.LOGGER.info("[GunPackCompat] scan complete: {} tmfmod namespaces, {} configs",
                tmfModCache.size(), configCache.size());
    }

    /**
     * 加载或创建枪包配置。如果 muzzleflash_compat.json 缺少动画字段，生成默认值并回写。
     */
    private static GunPackConfig loadOrCreateConfig(Path compatFile, Path gunPackDir) {
        Gson gson = new Gson();
        GunPackConfig config;
        try (Reader reader = Files.newBufferedReader(compatFile)) {
            config = gson.fromJson(reader, GunPackConfig.class);
            if (config == null) {
                config = new GunPackConfig();
            }
        } catch (Exception e) {
            MuzzleFlashMod.LOGGER.warn("[GunPackCompat] failed to parse {}, using defaults", compatFile);
            config = new GunPackConfig();
        }

        // 确保非空
        if (config.muzzle_flash_mode == null) config.muzzle_flash_mode = "default";
        if (config.defaultAnimation == null) config.defaultAnimation = new GunAnimationDef();
        if (config.guns == null) config.guns = new HashMap<>();

        // 如果没有动画帧，先留空（稍后由 scanMuzzleFolder 填充）
        boolean needsWrite = false;
        if (config.defaultAnimation.defaultmuzzleflashframes == null) {
            config.defaultAnimation.defaultmuzzleflashframes = new String[0];
            needsWrite = true;
        }
        if (config.defaultAnimation.muzzleframes == null) {
            config.defaultAnimation.muzzleframes = new String[0];
            needsWrite = true;
        }

        if (needsWrite) {
            saveConfig(compatFile, config);
        }

        return config;
    }

    /**
     * 将配置回写到 muzzleflash_compat.json。
     */
    private static void saveConfig(Path compatFile, GunPackConfig config) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(compatFile, gson.toJson(config));
        } catch (IOException e) {
            MuzzleFlashMod.LOGGER.error("[GunPackCompat] failed to write config to {}", compatFile, e);
        }
    }

    /**
     * 扫描 textures/muzzle/<folderName>/ 目录，生成帧列表。
     * 资源 ID 格式：muzzleflash:{namespace}/textures/muzzle/{folderName}/{相对路径}
     */
    private static List<String> scanMuzzleFolder(Path nsDir, String namespace, String folderName) {
        List<String> frames = new ArrayList<>();
        Path folder = nsDir.resolve("textures/muzzle").resolve(folderName);
        if (!Files.isDirectory(folder)) return frames;

        try {
            List<Path> pngFiles = new ArrayList<>();
            try (var stream = Files.walk(folder)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                        .forEach(pngFiles::add);
            }

            // 按文件名排序（frame_1, frame_2, ...）
            pngFiles.sort((a, b) -> {
                String nameA = a.getFileName().toString();
                String nameB = b.getFileName().toString();
                int numA = extractNumber(nameA);
                int numB = extractNumber(nameB);
                if (numA != -1 && numB != -1) {
                    return Integer.compare(numA, numB);
                }
                return nameA.compareTo(nameB);
            });

            for (Path file : pngFiles) {
                String rel = folder.relativize(file).toString().replace('\\', '/');
                String relNoExt = rel.substring(0, rel.length() - 4);
                String frameId = MuzzleFlashMod.MODID + ":" + namespace + "/textures/muzzle/" + folderName + "/" + relNoExt;
                frames.add(frameId);
            }
        } catch (IOException e) {
            MuzzleFlashMod.LOGGER.warn("[GunPackCompat] error scanning textures in {}/{}", namespace, folderName);
        }

        return frames;
    }

    /**
     * 扫描 textures/muzzle/ 下除 default 外的子目录，返回 子目录名(枪 path) → 帧列表。
     */
    private static Map<String, List<String>> scanGunMuzzleFolders(Path nsDir, String namespace) {
        Map<String, List<String>> result = new HashMap<>();
        Path muzzleDir = nsDir.resolve("textures/muzzle");
        if (!Files.isDirectory(muzzleDir)) return result;

        try (DirectoryStream<Path> subDirs = Files.newDirectoryStream(muzzleDir)) {
            for (Path sub : subDirs) {
                if (!Files.isDirectory(sub)) continue;
                String folderName = sub.getFileName().toString();
                if ("default".equals(folderName)) continue;
                List<String> frames = scanMuzzleFolder(nsDir, namespace, folderName);
                if (!frames.isEmpty()) {
                    result.put(folderName, frames);
                }
            }
        } catch (IOException e) {
            MuzzleFlashMod.LOGGER.warn("[GunPackCompat] error scanning muzzle folders in namespace {}", namespace);
        }

        return result;
    }

    /**
     * 校验现有帧（过滤磁盘上已删除的）+ 补充磁盘上新增的帧。
     */
    private static String[] reconcileFrames(String[] existing, List<String> diskFrames) {
        List<String> valid = new ArrayList<>();
        Set<String> existingIds = new HashSet<>();
        if (existing != null) {
            for (String f : existing) {
                if (frameTextureExists(f)) {
                    valid.add(f);
                    existingIds.add(f);
                }
            }
        }
        for (String f : diskFrames) {
            if (!existingIds.contains(f)) {
                valid.add(f);
            }
        }
        return valid.toArray(new String[0]);
    }

    private static boolean arraysEqual(String[] a, String[] b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(b[i])) return false;
        }
        return true;
    }

    /**
     * 从文件名提取数字（如 frame_1.png → 1），用于排序。
     */
    private static int extractNumber(String name) {
        int start = name.indexOf('_');
        if (start == -1) return -1;
        StringBuilder num = new StringBuilder();
        for (int i = start + 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isDigit(c)) {
                num.append(c);
            } else if (num.length() > 0) {
                break;
            }
        }
        if (num.length() == 0) return -1;
        try {
            return Integer.parseInt(num.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 判断指定 frame 资源 ID 对应的贴图文件是否真实存在于磁盘上。
     * frameId 格式："muzzleflash:{namespace}/textures/muzzle/{path}"（无 .png 后缀）。
     * 检查路径：tacz/{gunPack}/assets/{namespace}/textures/muzzle/{path}.png
     */
    private static boolean frameTextureExists(String frameId) {
        if (frameId == null || frameId.isEmpty()) return false;
        int colonIdx = frameId.indexOf(':');
        if (colonIdx == -1) return false;
        String modid = frameId.substring(0, colonIdx);
        String fullPath = frameId.substring(colonIdx + 1);

        // 非本模组的贴图（如 tacz:...）由资源系统从其它模组 jar 加载，视为存在，不删除
        if (!MuzzleFlashMod.MODID.equals(modid)) {
            return true;
        }

        // 本模组帧格式：muzzleflash:<真实命名空间>/textures/muzzle/<相对路径>
        // 真实贴图文件位于枪包 assets/<真实命名空间>/textures/muzzle/<相对路径>.png
        int slashIdx = fullPath.indexOf('/');
        String realNs = slashIdx == -1 ? fullPath : fullPath.substring(0, slashIdx);
        String relPath = slashIdx == -1 ? "" : fullPath.substring(slashIdx + 1);

        Path taczPath = FMLPaths.GAMEDIR.get().resolve("tacz");
        if (!Files.isDirectory(taczPath)) return false;

        try (DirectoryStream<Path> gunPacks = Files.newDirectoryStream(taczPath)) {
            for (Path gunPackDir : gunPacks) {
                if (!Files.isDirectory(gunPackDir)) continue;
                // 检查 muzzleflash_compat.json 存在
                if (!Files.exists(gunPackDir.resolve("muzzleflash_compat.json"))) continue;

                Path targetFile = gunPackDir.resolve("assets").resolve(realNs).resolve(relPath + ".png");
                if (Files.isRegularFile(targetFile)) {
                    return true;
                }
            }
        } catch (IOException e) {
            MuzzleFlashMod.LOGGER.warn("[GunPackCompat] error checking texture existence for {}", frameId);
        }
        return false;
    }

    /**
     * 判断指定枪械是否应由本模组控制枪焰渲染。
     */
    public static boolean isTmfModMode(ResourceLocation gunId) {
        if (gunId == null) return false;
        if (!scanned) {
            scan();
        }
        return tmfModCache.getOrDefault(gunId.getNamespace(), false);
    }

    /**
     * 获取指定 namespace 的默认动画。
     * 如果没有配置或没有帧，返回 null。
     */
    public static MuzzleFlashAnimation getAnimationForNamespace(String namespace) {
        if (namespace == null) return null;
        if (!scanned) scan();

        // 先查缓存
        if (animationCache.containsKey(namespace)) {
            return animationCache.get(namespace);
        }

        GunPackConfig config = configCache.get(namespace);
        if (config == null) {
            return null;
        }

        GunAnimationDef def = config.defaultAnimation;
        MuzzleFlashAnimation anim = def.toAnimation(def.defaultmuzzleflashframes, def.frameDuration);
        animationCache.put(namespace, anim);
        return anim;
    }

    /**
     * 获取指定枪械的动画（优先使用枪级覆盖，回退到默认动画，最后回退到全局 fallback）。
     */
    public static MuzzleFlashAnimation getAnimationForGun(ResourceLocation gunId) {
        if (gunId == null) return getFallbackAnimation();
        if (!scanned) scan();

        String namespace = gunId.getNamespace();
        String gunKey = gunId.toString();

        // 查枪级缓存
        Map<String, MuzzleFlashAnimation> gunCache = gunAnimationCache.computeIfAbsent(namespace, k -> new HashMap<>());
        if (gunCache.containsKey(gunKey)) {
            return gunCache.get(gunKey);
        }

        GunPackConfig config = configCache.get(namespace);
        if (config != null) {
            // 先尝试枪级覆盖
            if (config.guns.containsKey(gunKey)) {
                GunAnimationDef def = config.guns.get(gunKey);
                // 帧：优先单枪 muzzleframes，否则回退默认 defaultmuzzleflashframes
                String[] frames = (def.muzzleframes != null && def.muzzleframes.length > 0)
                        ? def.muzzleframes
                        : config.defaultAnimation.defaultmuzzleflashframes;
                // 时长：优先单枪 FrameDuration，否则回退默认
                int duration = def.frameDuration > 0 ? def.frameDuration : config.defaultAnimation.frameDuration;
                MuzzleFlashAnimation anim = def.toAnimation(frames, duration);
                if (anim != null) {
                    gunCache.put(gunKey, anim);
                    return anim;
                }
            }

            // 回退到该 namespace 的默认动画
            MuzzleFlashAnimation nsAnim = getAnimationForNamespace(namespace);
            if (nsAnim != null) {
                gunCache.put(gunKey, nsAnim);
                return nsAnim;
            }
        }

        // 最后回退到全局 fallback
        return getFallbackAnimation();
    }

    /**
     * 获取全局 fallback 动画。
     * 使用 muzzleflash:tacz/textures/muzzle/default/frame_1..8 作为默认帧。
     */
    public static MuzzleFlashAnimation getFallbackAnimation() {
        if (fallbackAnimation != null) return fallbackAnimation;

        List<ResourceLocation> frames = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            frames.add(new ResourceLocation(MuzzleFlashMod.MODID, "tacz/textures/muzzle/default/frame_" + i));
        }
        fallbackAnimation = new MuzzleFlashAnimation(frames, 30, 50, 1.0f, true, 300);
        return fallbackAnimation;
    }

    /**
     * 获取缓存快照大小（用于调试/日志）
     */
    public static int getCacheSize() {
        return tmfModCache.size();
    }

    /**
     * 获取所有枪包配置数量
     */
    public static int getConfigCount() {
        return configCache.size();
    }

    /**
     * 获取指定枪械的枪焰延迟时间（毫秒）。
     * 返回 0 表示无延迟，枪焰立即显示。
     */
    public static int getFlashDelayForGun(ResourceLocation gunId) {
        if (gunId == null) return 0;
        if (!scanned) scan();

        String namespace = gunId.getNamespace();
        String gunKey = gunId.toString();

        // 查缓存
        Map<String, Integer> delayCache = gunDelayCache.computeIfAbsent(namespace, k -> new HashMap<>());
        if (delayCache.containsKey(gunKey)) {
            return delayCache.get(gunKey);
        }

        int delay = 0;
        GunPackConfig config = configCache.get(namespace);
        if (config != null && config.guns != null) {
            GunAnimationDef def = config.guns.get(gunKey);
            if (def != null) {
                delay = def.flashDelayMs;
            }
        }

        delayCache.put(gunKey, delay);
        return delay;
    }

    /**
     * 解析 guns 配置中的动画定义。
     */
    public static GunAnimationDef getGunAnimationDef(ResourceLocation gunId) {
        if (gunId == null) return null;
        if (!scanned) scan();

        String namespace = gunId.getNamespace();
        String gunKey = gunId.toString();

        GunPackConfig config = configCache.get(namespace);
        if (config == null || config.guns == null) return null;

        return config.guns.get(gunKey);
    }
}