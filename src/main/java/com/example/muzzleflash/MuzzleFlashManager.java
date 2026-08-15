package com.example.muzzleflash;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class MuzzleFlashManager {
    private static MuzzleFlashManager INSTANCE;

    private MuzzleFlashAnimation current;
    private long startTimeMs = -1;
    private ResourceLocation currentGunId;
    private String currentGunType = "rifle";

    /** 主通道 RenderType 缓存（不写深度的半透明，避免遮挡枪模） */
    private final Map<ResourceLocation, RenderType> mainRenderTypeCache = new HashMap<>();
    /** 发光通道 RenderType 缓存（不写深度的叠加发光，提升亮度） */
    private final Map<ResourceLocation, RenderType> glowRenderTypeCache = new HashMap<>();

    /** 枪类型缩放倍率 */
    private static final Map<String, Float> GUN_TYPE_SCALE = new HashMap<>();
    static {
        GUN_TYPE_SCALE.put("pistol", 0.7f);
        GUN_TYPE_SCALE.put("smg", 0.85f);
        GUN_TYPE_SCALE.put("rifle", 1.0f);
        GUN_TYPE_SCALE.put("shotgun", 1.3f);
        GUN_TYPE_SCALE.put("sniper", 1.4f);
        GUN_TYPE_SCALE.put("grenade", 1.5f);
        GUN_TYPE_SCALE.put("special", 1.1f);
        GUN_TYPE_SCALE.put("melee", 0.5f);
    }

    public static float getScaleForGunType(String gunType) {
        if (gunType == null) return 1.0f;
        return GUN_TYPE_SCALE.getOrDefault(gunType.toLowerCase(), 1.0f);
    }

    public static void init() {
        INSTANCE = new MuzzleFlashManager();
    }

    public static MuzzleFlashManager get() {
        return INSTANCE;
    }

    public void setCurrentGunType(String gunType) {
        if (gunType != null && !gunType.equals(currentGunType)) {
            this.currentGunType = gunType;
        }
    }

    /**
     * 触发枪焰动画（由 ammo 变化检测调用）。
     * 如果该枪配置了 flashDelayMs，则延迟启动动画。
     */
    public void triggerAnimation(ResourceLocation gunId) {
        if (gunId == null) return;

        if (current != null && gunId.equals(currentGunId)) {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            if (elapsed < 50) return; // 防重复
        }

        // 如果该枪有等待中的延迟任务，跳过本次触发
        if (FireDelayManager.hasPending(gunId)) {
            MuzzleFlashDebug.log("TRIGGER", String.format("skip: gun=%s already has pending delay", gunId));
            return;
        }

        MuzzleFlashAnimation anim = GunPackCompatManager.getAnimationForGun(gunId);
        if (anim == null) {
            anim = GunPackCompatManager.getFallbackAnimation();
        }
        MuzzleFlashDebug.log("TRIGGER", String.format("anim resolved: gun=%s, frames=%d, scale=%.2f",
                gunId, anim != null ? anim.frames.size() : 0, anim != null ? anim.scale : 0));

        // 检查是否需要延迟启动
        if (FireDelayManager.requestDelayedFlash(gunId, anim)) {
            currentGunId = gunId;  // 保存 gunId 供 tick() 检查
            int delayMs = GunPackCompatManager.getFlashDelayForGun(gunId);
            MuzzleFlashDebug.logDelay("queued", gunId, delayMs, delayMs);
            return;
        }

        // 无延迟，立即启动
        current = anim;
        currentGunId = gunId;
        startTimeMs = System.currentTimeMillis();
        MuzzleFlashDebug.logAnimation("started", gunId, 0, 1.0f,
                current != null ? current.frames.size() : 0);
    }

    /**
     * 每帧检查：延迟任务是否就绪、动画是否到期。
     */
    public void tick() {
        // 检查延迟任务是否就绪
        if (current == null && currentGunId != null) {
            // 找一个已就绪的延迟任务启动
            // 这里简化处理：检查当前枪是否有就绪的延迟任务
            if (currentGunId != null) {
                FireDelayManager.PendingFlash pending = FireDelayManager.consumeReadyFlash(currentGunId);
                if (pending != null) {
                    current = pending.animation;
                    startTimeMs = System.currentTimeMillis();
                    MuzzleFlashDebug.logAnimation("delayed-started", currentGunId, 0, 1.0f,
                            current != null ? current.frames.size() : 0);
                }
            }
        }

        if (current != null) {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            if (elapsed > current.getTotalDurationMs()) {
                MuzzleFlashDebug.logAnimation("expired", currentGunId, 0, 0f,
                        current.frames.size());
                current = null;
                startTimeMs = -1;
                currentGunId = null;
            }
        }
    }

    public boolean isActive() {
        return current != null;
    }

    /** 调试快照计数器 */
    private int debugSnapshotCounter = 0;

    /**
     * 输出调试快照（每 60 帧一次）
     */
    public void debugSnapshot(ResourceLocation gunId, String gunType, boolean isTmfMod) {
        debugSnapshotCounter++;
        if (debugSnapshotCounter % 60 != 0) return;

        FireDelayManager.PendingFlash pending = gunId != null ? FireDelayManager.getPending(gunId) : null;

        MuzzleFlashDebug.log("SNAPSHOT", String.format(
                "gun=%s, type=%s, tmfmod=%s, active=%s, frame=%d, currentGunId=%s, pendingDelay=%s",
                gunId, gunType, isTmfMod,
                isActive(),
                current != null ? (int) ((System.currentTimeMillis() - startTimeMs) / current.getFrameDurationMs()) : -1,
                currentGunId,
                pending != null ? pending.getRemainingMs() + "ms" : "none"));
    }

    /**
     * 延迟渲染入口（delegateRender 回调调用）。
     * 用捕获的枪口矩阵重建 PoseStack（此时传入的 poseStack 已 popPose 回根节点）。
     */
    public void renderAtMuzzleDeferred(Matrix4f pose, Matrix3f normal, int light, int overlay) {
        if (current == null) return;
        PoseStack ps = new PoseStack();
        ps.last().pose().mul(pose);
        ps.last().normal().mul(normal);
        renderAtMuzzle(ps, light, overlay);
    }

    public void renderAtMuzzle(PoseStack poseStack, int light, int overlay) {
        if (current == null) return;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        int frameIndex = (int) (elapsed / current.getFrameDurationMs());
        ResourceLocation tex = current.getFrame(frameIndex);
        if (tex == null) return;

        float scale = current.scale;

        // 1. 自动缩放：根据贴图有效内容（非透明像素包围盒）尺寸，
        //    把火焰有效内容归一化到 baseTextureSize 对应的物理大小，
        //    避免大贴图里火焰只占一小部分时被错误缩小。
        if (current.autoScale) {
            int effDim = MuzzleFlashContent.getEffectiveMaxDimension(tex);
            if (effDim > 0) {
                float autoScaleFactor = (float) current.baseTextureSize / effDim;
                scale *= autoScaleFactor;
            }
        }

        // 2. 枪类型缩放
        scale *= getScaleForGunType(currentGunType);

        // 3. 动画帧衰减：后 30% 帧线性衰减到完全透明
        float frameProgress = (float) (frameIndex + 1) / current.frames.size();
        float alpha;
        if (frameProgress < 0.7f) {
            alpha = 1.0f;
        } else {
            alpha = 1.0f - (frameProgress - 0.7f) / 0.3f;
        }
        alpha = Math.max(0, Math.min(1, alpha));

        // 调试：每 5 帧记录一次渲染
        if (MuzzleFlashDebug.isEnabled() && frameIndex % 5 == 0) {
            MuzzleFlashDebug.logAnimation("render", currentGunId, frameIndex, alpha, current.frames.size());
            MuzzleFlashDebug.log("RENDER", String.format("tex=%s, scale=%.3f, light=%d, overlay=%d, alpha=%.2f",
                    tex, scale, light, overlay, alpha));
        }

        // 必须使用我们自己的 BufferSource + RenderType（不能复用 mixin 传入的 VertexConsumer，
        // 因为它绑定了枪模纹理）。
        MultiBufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();

        // 关键修复：金色残留的真正根因是深度缓冲污染。
        // entityTranslucent 默认 COLOR_DEPTH_WRITE，即使 alpha=0 也会写入深度，
        // 导致枪模在枪焰 quad 之后渲染的几何通不过深度测试而"消失/内部透明"。
        // 这里改用自定义 RenderType：只写颜色不写深度（COLOR_WRITE），
        // 并使用 emissive 着色器（忽略光照贴图，始终最亮）解决暗淡。
        RenderType mainType = getMainRenderType(tex);
        RenderType glowType = getGlowRenderType(tex);

        // 调试：每帧记录 renderType
        if (MuzzleFlashDebug.isEnabled() && frameIndex == 0) {
            MuzzleFlashDebug.log("RENDER", String.format("mainType=%s, glowType=%s, tex=%s", mainType, glowType, tex));
        }

        VertexConsumer mainConsumer = buffer.getBuffer(mainType);
        VertexConsumer glowConsumer = buffer.getBuffer(glowType);

        poseStack.pushPose();
        try {
            // 枪焰位置偏移（相对枪口骨骼，世界单位，1.0 = 1 米）
            // 在 scale 之前应用，偏移量不受缩放倍率影响
            poseStack.translate(current.offsetX, current.offsetY, current.offsetZ);
            // 变换：只做 scale（4 顶点在 ±0.5 已居中，无需额外 translate）
            // 修复：之前有 translate(0, -0.5, 0) 会导致枪焰向上错位
            poseStack.scale(scale, scale, scale);

            // 直接用 VertexConsumer 绘制 4 顶点四边形
            // 完全绕开 SlotModel 的 bone 几何，避免被覆盖到枪械模型上
            // alpha 控制透明度（0=完全透明，1=完全不透明）
            int alphaInt = (int) (alpha * 255);
            // 发光通道用略低的 alpha，避免叠加后过曝成金黄
            int glowAlphaInt = (int) (alpha * 0.6f * 255);

            float half = 0.5f;
            int overlayVal = overlay;  // 使用方法参数 overlay
            // 关键：枪焰是自发光，必须强制最大光照！
            // mixin 传入的 light 是枪口骨骼的场景光照，阴影/暗处会很低（个位数）。
            // 硬编码 LightTexture.pack(15, 15) = 15728880，确保始终最亮。
            // （emissive 着色器本身也忽略光照贴图，双保险。）
            int lightVal = 15728880;

            // 注意：不能用 >255 的"过曝"值，VertexConsumer.color 会用 & 0xFF mask。
            // 主通道（半透明 alpha 混合，不写深度）+ 发光通道（additive 叠加，不写深度）
            renderQuad(mainConsumer, poseStack, half, 255, 255, 255, alphaInt, overlayVal, lightVal);
            renderQuad(glowConsumer, poseStack, half, 255, 255, 255, glowAlphaInt, overlayVal, lightVal);
        } finally {
            poseStack.popPose();
        }
    }

    /** 绘制一个居中的 4 顶点四边形（NEW_ENTITY 顶点格式，6 元素齐全）。 */
    private static void renderQuad(VertexConsumer consumer, PoseStack poseStack, float half,
                                   int r, int g, int b, int a, int overlay, int light) {
        consumer.vertex(poseStack.last().pose(), -half, -half, 0.0F)
                .color(r, g, b, a).uv(0.0F, 0.0F).overlayCoords(overlay).uv2(light)
                .normal(poseStack.last().normal(), 0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(poseStack.last().pose(), half, -half, 0.0F)
                .color(r, g, b, a).uv(1.0F, 0.0F).overlayCoords(overlay).uv2(light)
                .normal(poseStack.last().normal(), 0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(poseStack.last().pose(), half, half, 0.0F)
                .color(r, g, b, a).uv(1.0F, 1.0F).overlayCoords(overlay).uv2(light)
                .normal(poseStack.last().normal(), 0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(poseStack.last().pose(), -half, half, 0.0F)
                .color(r, g, b, a).uv(0.0F, 1.0F).overlayCoords(overlay).uv2(light)
                .normal(poseStack.last().normal(), 0.0F, 1.0F, 0.0F).endVertex();
    }

    private RenderType getMainRenderType(ResourceLocation tex) {
        return mainRenderTypeCache.computeIfAbsent(tex, MuzzleFlashRenderState::buildMain);
    }

    private RenderType getGlowRenderType(ResourceLocation tex) {
        return glowRenderTypeCache.computeIfAbsent(tex, MuzzleFlashRenderState::buildGlow);
    }

    /**
     * 继承 RenderStateShard 以访问其 protected 的静态 shard 常量
     * （TRANSLUCENT_TRANSPARENCY、COLOR_WRITE 等），用于构建自定义 RenderType。
     */
    private static class MuzzleFlashRenderState extends RenderStateShard {
        private MuzzleFlashRenderState() {
            super("muzzleflash_render_state", () -> {}, () -> {});
        }

        /** 主通道：半透明 alpha 混合 + emissive 着色器（始终最亮）+ 不写深度（避免遮挡枪模）。 */
        static RenderType buildMain(ResourceLocation tex) {
            return RenderType.create("muzzleflash_translucent_no_depth", DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 256, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(true));
        }

        /** 发光通道：additive 叠加（SRC_ALPHA, ONE）+ emissive 着色器 + 不写深度，提升亮度。 */
        static RenderType buildGlow(ResourceLocation tex) {
            return RenderType.create("muzzleflash_glow_no_depth", DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 256, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(true));
        }
    }
}