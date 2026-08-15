package com.example.muzzleflash;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 调试信息收集器。
 * <p>
 * 启用后会在关键事件（子弹减少、动画触发、延迟启动等）时输出到聊天栏和日志文件。
 * 使用方式：/muzzleflash debug 切换开关；/muzzleflash debug clear 清空日志。
 */
public class MuzzleFlashDebug {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** 调试开关 */
    private static boolean enabled = false;

    /** 日志文件路径（相对游戏根目录） */
    private static final Path LOG_PATH = Paths.get("tacz", "muzzleflashlog.txt");

    /**
     * 是否启用调试
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 切换调试状态
     */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    /**
     * 设置调试状态
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * 输出调试信息到聊天栏和日志文件。
     */
    public static void log(String category, String message) {
        if (!enabled) return;

        String time = LocalDateTime.now().format(TIME_FORMAT);
        String fullMsg = String.format("[%s][%s] %s", time, category, message);

        // 输出到聊天栏
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("\u00a77\u00a7o" + fullMsg));
            }
        } catch (Exception ignored) {}

        // 输出到日志文件
        writeToFile(fullMsg);
    }

    /**
     * 输出到日志文件（即使调试关闭也可调用，仅写入文件）
     */
    public static void logAlways(String category, String message) {
        String time = LocalDateTime.now().format(TIME_FORMAT);
        String fullMsg = String.format("[%s][%s] %s", time, category, message);
        writeToFile(fullMsg);
    }

    /**
     * 清空日志文件
     */
    public static void clearLog() {
        try {
            Path absPath = LOG_PATH.toAbsolutePath();
            if (Files.exists(absPath)) {
                Files.delete(absPath);
            }
        } catch (IOException e) {
            MuzzleFlashMod.LOGGER.error("[MuzzleFlashDebug] failed to clear log", e);
        }
    }

    /**
     * 获取当前状态摘要
     */
    public static String getStatusSummary() {
        return String.format("enabled=%s, logPath=%s", enabled, LOG_PATH.toAbsolutePath());
    }

    private static synchronized void writeToFile(String line) {
        try {
            Path absPath = LOG_PATH.toAbsolutePath();
            Path parent = absPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(absPath, line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            MuzzleFlashMod.LOGGER.error("[MuzzleFlashDebug] failed to write log", e);
        }
    }

    /**
     * 输出枪械信息快照
     */
    public static void logGunInfo(String event, ResourceLocation gunId, int ammoCount, String gunType) {
        log("GUN", String.format("%s: id=%s, ammo=%d, type=%s", event, gunId, ammoCount, gunType));
    }

    /**
     * 输出动画状态快照
     */
    public static void logAnimation(String event, ResourceLocation gunId, int frameIndex, float alpha, int totalFrames) {
        log("ANIM", String.format("%s: id=%s, frame=%d/%d, alpha=%.2f", event, gunId, frameIndex, totalFrames, alpha));
    }

    /**
     * 输出延迟状态快照
     */
    public static void logDelay(String event, ResourceLocation gunId, int delayMs, long remainingMs) {
        log("DELAY", String.format("%s: id=%s, delayMs=%d, remainingMs=%d", event, gunId, delayMs, remainingMs));
    }
}
