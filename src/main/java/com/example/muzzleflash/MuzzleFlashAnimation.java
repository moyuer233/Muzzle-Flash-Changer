package com.example.muzzleflash;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个枪的枪焰动画定义
 */
public class MuzzleFlashAnimation {
    public final List<ResourceLocation> frames;
    public final int frameDurationMs;
    /** 总显示时长（毫秒）。0 表示未指定，回退到 frames.size() * frameDurationMs */
    public final int totalDurationMs;
    /** 配置文件中的 scale 值，作为最终缩放倍率 */
    public final float scale;
    /** 是否启用自动缩放（根据贴图实际像素尺寸动态计算） */
    public final boolean autoScale;
    /** 参考像素尺寸：自动缩放时，将贴图归一化到该尺寸对应的世界大小 */
    public final int baseTextureSize;
    /** 枪焰位置偏移（世界单位，1.0 = 1 米），相对枪口骨骼：X 右、Y 上、Z 前（枪管方向） */
    public final float offsetX;
    public final float offsetY;
    public final float offsetZ;

    public MuzzleFlashAnimation(List<ResourceLocation> frames, int frameDurationMs, int totalDurationMs, float scale, boolean autoScale, int baseTextureSize) {
        this(frames, frameDurationMs, totalDurationMs, scale, autoScale, baseTextureSize, 0.0f, 0.0f, 0.0f);
    }

    public MuzzleFlashAnimation(List<ResourceLocation> frames, int frameDurationMs, int totalDurationMs, float scale, boolean autoScale, int baseTextureSize, float offsetX, float offsetY, float offsetZ) {
        this.frames = frames;
        this.frameDurationMs = Math.max(1, frameDurationMs);
        this.totalDurationMs = Math.max(0, totalDurationMs);
        this.scale = scale;
        this.autoScale = autoScale;
        this.baseTextureSize = Math.max(1, baseTextureSize);
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public int getTotalDurationMs() {
        return totalDurationMs > 0 ? totalDurationMs : frames.size() * frameDurationMs;
    }

    /** 单帧实际播放时长（毫秒）。指定 totalDurationMs 时按总时长均分到每帧。 */
    public int getFrameDurationMs() {
        if (totalDurationMs > 0) {
            return Math.max(1, totalDurationMs / Math.max(1, frames.size()));
        }
        return frameDurationMs;
    }

    public ResourceLocation getFrame(int index) {
        if (frames.isEmpty()) return null;
        return frames.get(Math.floorMod(index, frames.size()));
    }
}
