package com.example.muzzleflash;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 枪焰延迟管理器。
 * <p>
 * 支持为特定枪械配置枪焰特效延迟时间（毫秒），实现开火后枪焰不立即显示，而是延迟一段时间后才出现。
 * 使用方式：在 muzzleflash_compat.json 的 guns 配置中为枪添加 flashDelayMs 字段。
 * <p>
 * 示例配置：
 * <pre>
 * "guns": {
 *   "re:rsh12": {
 *     "flashDelayMs": 300
 *   }
 * }
 * </pre>
 */
public class FireDelayManager {

    /** 每把枪的延迟启动任务（key=gunId） */
    private static final Map<ResourceLocation, PendingFlash> pendingFlashes = new HashMap<>();

    /** 待启动的枪焰动画 */
    public static class PendingFlash {
        public final ResourceLocation gunId;
        public final MuzzleFlashAnimation animation;
        public final long triggerTimeMs;  // 开火时刻
        public final int delayMs;         // 延迟时间

        public PendingFlash(ResourceLocation gunId, MuzzleFlashAnimation animation, int delayMs) {
            this.gunId = gunId;
            this.animation = animation;
            this.triggerTimeMs = System.currentTimeMillis();
            this.delayMs = delayMs;
        }

        /** 延迟是否已完成，可以启动动画 */
        public boolean isReady() {
            long elapsed = System.currentTimeMillis() - triggerTimeMs;
            return elapsed >= delayMs;
        }

        /** 延迟进度 0.0 ~ 1.0 */
        public float getProgress() {
            long elapsed = System.currentTimeMillis() - triggerTimeMs;
            return Math.max(0, Math.min(1, (float) elapsed / delayMs));
        }

        /** 剩余毫秒 */
        public long getRemainingMs() {
            long elapsed = System.currentTimeMillis() - triggerTimeMs;
            return Math.max(0, delayMs - elapsed);
        }
    }

    /**
     * 请求延迟启动枪焰。
     * 如果该枪配置了延迟，创建 PendingFlash 并返回 true。
     * 如果没有配置延迟，返回 false。
     */
    public static boolean requestDelayedFlash(ResourceLocation gunId, MuzzleFlashAnimation animation) {
        if (gunId == null || animation == null) return false;

        int delayMs = GunPackCompatManager.getFlashDelayForGun(gunId);
        if (delayMs <= 0) return false;

        // 已存在 pending 时：
        // - 未就绪（延迟仍在走）：保持原 triggerTimeMs 不重置（防连发把延迟无限往后推）；
        // - 已就绪（延迟早已结束但没被消费，例如期间切枪/收枪导致渲染断档）：
        //   视为过期孤儿任务，作废重排，避免该枪后续开火被永久吞掉。
        PendingFlash existing = pendingFlashes.get(gunId);
        if (existing != null) {
            if (existing.isReady()) {
                pendingFlashes.remove(gunId);
            } else {
                return true;
            }
        }

        // 创建新的延迟任务
        PendingFlash pending = new PendingFlash(gunId, animation, delayMs);
        pendingFlashes.put(gunId, pending);

        MuzzleFlashDebug.logDelay("request", gunId, delayMs, delayMs);
        return true;
    }

    /**
     * 检查并消费已就绪的延迟任务。
     * 如果延迟已完成，返回 PendingFlash 并移除；否则返回 null。
     */
    public static PendingFlash consumeReadyFlash(ResourceLocation gunId) {
        if (gunId == null) return null;
        PendingFlash pending = pendingFlashes.get(gunId);
        if (pending != null && pending.isReady()) {
            pendingFlashes.remove(gunId);
            MuzzleFlashDebug.logDelay("consume", gunId, pending.delayMs, 0);
            return pending;
        }
        return null;
    }

    /**
     * 取消指定枪的延迟任务。
     */
    public static void cancelPending(ResourceLocation gunId) {
        if (gunId != null && pendingFlashes.containsKey(gunId)) {
            MuzzleFlashDebug.logDelay("cancel", gunId, 0, 0);
            pendingFlashes.remove(gunId);
        }
    }

    /**
     * 清除所有延迟任务。
     */
    public static void clearAll() {
        pendingFlashes.clear();
    }

    /**
     * 获取指定枪的当前待启动状态（只读）。
     */
    public static PendingFlash getPending(ResourceLocation gunId) {
        if (gunId == null) return null;
        return pendingFlashes.get(gunId);
    }

    /**
     * 回收不属于当前渲染枪的孤儿延迟任务（切枪后旧枪的 pending 无人消费，
     * 会一直卡住该枪后续开火，必须清理）。
     */
    public static void cancelForeign(ResourceLocation keepGunId) {
        if (keepGunId == null) return;
        if (pendingFlashes.isEmpty()) return;
        pendingFlashes.entrySet().removeIf(entry -> {
            if (entry.getKey().equals(keepGunId)) return false;
            MuzzleFlashDebug.logDelay("cancel-foreign", entry.getKey(), entry.getValue().delayMs, 0);
            return true;
        });
    }
}
