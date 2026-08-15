package com.example.muzzleflash;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MuzzleFlashMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MuzzleFlashCommand {

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(
            Commands.literal("muzzleflash")
                .then(Commands.literal("open")
                    .executes(ctx -> {
                        ctx.getSource().sendSystemMessage(Component.literal(
                            "\u00a7a[MuzzleFlash] \u00a7fcontent folder: " + MuzzleFlashContent.getContentRoot()));
                        return 1;
                    }))
                .then(Commands.literal("reload")
                    .executes(ctx -> {
                        GunPackCompatManager.scan();
                        FireDelayManager.clearAll();
                        ctx.getSource().sendSystemMessage(Component.literal(
                            "\u00a7a[MuzzleFlash] \u00a7f配置已重新扫描"));
                        return 1;
                    }))
                .then(Commands.literal("debug")
                    .executes(ctx -> {
                        boolean nowEnabled = MuzzleFlashDebug.toggle();
                        if (nowEnabled) {
                            MuzzleFlashDebug.clearLog();
                            ctx.getSource().sendSystemMessage(Component.literal(
                                "\u00a7a[MuzzleFlash] \u00a7f\u00a7l调试模式已开启"));
                            ctx.getSource().sendSystemMessage(Component.literal(
                                "\u00a7a[MuzzleFlash] \u00a7f日志文件: tacz/muzzleflashlog.txt"));
                            ctx.getSource().sendSystemMessage(Component.literal(
                                "\u00a7a[MuzzleFlash] \u00a7f信息类别: GUN, AMMO, TRIGGER, DELAY, ANIM, RENDER, SNAPSHOT"));
                        } else {
                            ctx.getSource().sendSystemMessage(Component.literal(
                                "\u00a7c[MuzzleFlash] \u00a7f调试模式已关闭"));
                        }
                        return 1;
                    }))
                .then(Commands.literal("loginfo")
                    .executes(ctx -> {
                        StringBuilder sb = new StringBuilder("\u00a7a[MuzzleFlash] \u00a7f状态汇总:\n");
                        sb.append("  调试状态: ").append(MuzzleFlashDebug.isEnabled() ? "\u00a7a开启" : "\u00a7c关闭").append("\n");
                        sb.append("  枪包配置数: ").append(GunPackCompatManager.getConfigCount()).append("\n");
                        sb.append("  ").append(MuzzleFlashDebug.getStatusSummary()).append("\n");

                        var player = net.minecraft.client.Minecraft.getInstance().player;
                        if (player != null) {
                            var itemStack = player.getMainHandItem();
                            if (itemStack.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
                                var gunId = iGun.getGunId(itemStack);
                                if (gunId != null) {
                                    int delay = GunPackCompatManager.getFlashDelayForGun(gunId);
                                    int ammo = iGun.getCurrentAmmoCount(itemStack);
                                    boolean tmfmod = GunPackCompatManager.isTmfModMode(gunId);

                                    sb.append("  \u00a7e[当前枪]").append(gunId).append("\n");
                                    sb.append("    子弹数: ").append(ammo).append("\n");
                                    sb.append("    枪焰延迟: ").append(delay).append("ms\n");
                                    sb.append("    tmfmod模式: ").append(tmfmod ? "\u00a7a是" : "\u00a7c否").append("\n");

                                    var anim = GunPackCompatManager.getAnimationForGun(gunId);
                                    sb.append("    动画帧数: ").append(anim != null ? anim.frames.size() : 0).append("\n");
                                    sb.append("    动画scale: ").append(anim != null ? anim.scale : 0).append("\n");

                                    var pending = FireDelayManager.getPending(gunId);
                                    if (pending != null) {
                                        sb.append("    延迟任务: \u00a7e等待中 ").append(pending.getRemainingMs()).append("ms\n");
                                    } else {
                                        sb.append("    延迟任务: 无\n");
                                    }

                                    var muzzle = MuzzleFlashManager.get();
                                    sb.append("    枪焰活跃: ").append(muzzle.isActive() ? "\u00a7a是" : "\u00a7c否").append("\n");
                                } else {
                                    sb.append("  \u00a7c当前手持物品不是枪\n");
                                }
                            } else {
                                sb.append("  \u00a7c当前手持物品不是枪\n");
                            }
                        }
                        ctx.getSource().sendSystemMessage(Component.literal(sb.toString()));
                        return 1;
                    }))
                .then(Commands.literal("delay")
                    .executes(ctx -> {
                        StringBuilder sb = new StringBuilder("\u00a7a[FlashDelay] \u00a7f枪焰延迟状态:\n");
                        int configCount = GunPackCompatManager.getConfigCount();
                        sb.append("  枪包配置数: ").append(configCount).append("\n");
                        var player = net.minecraft.client.Minecraft.getInstance().player;
                        if (player != null) {
                            var itemStack = player.getMainHandItem();
                            if (itemStack.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
                                var gunId = iGun.getGunId(itemStack);
                                if (gunId != null) {
                                    int delay = GunPackCompatManager.getFlashDelayForGun(gunId);
                                    sb.append("  当前枪: ").append(gunId).append("\n");
                                    sb.append("  枪焰延迟: ").append(delay).append("ms\n");
                                    var pending = FireDelayManager.getPending(gunId);
                                    if (pending != null) {
                                        sb.append("  等待中: 进度=").append(String.format("%.0f%%", pending.getProgress() * 100))
                                                .append(", 剩余=").append(pending.getRemainingMs()).append("ms\n");
                                    } else {
                                        sb.append("  状态: 就绪\n");
                                    }
                                    var muzzle = MuzzleFlashManager.get();
                                    sb.append("  枪焰活跃: ").append(muzzle.isActive()).append("\n");
                                }
                            }
                        }
                        ctx.getSource().sendSystemMessage(Component.literal(sb.toString()));
                        return 1;
                    })
                    .then(Commands.literal("info")
                        .executes(ctx -> {
                            // 详细延迟信息（与 loginfo 类似，但专注于延迟）
                            StringBuilder sb = new StringBuilder("\u00a7a[FlashDelay] \u00a7f详细延迟信息:\n");
                            var player = net.minecraft.client.Minecraft.getInstance().player;
                            if (player != null) {
                                var itemStack = player.getMainHandItem();
                                if (itemStack.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
                                    var gunId = iGun.getGunId(itemStack);
                                    if (gunId != null) {
                                        int delay = GunPackCompatManager.getFlashDelayForGun(gunId);
                                        sb.append("  枪: ").append(gunId).append("\n");
                                        sb.append("  配置延迟: ").append(delay).append("ms\n");
                                        var pending = FireDelayManager.getPending(gunId);
                                        if (pending != null) {
                                            sb.append("  \u00a7e延迟任务活跃:\n");
                                            sb.append("    - 进度: ").append(String.format("%.1f%%", pending.getProgress() * 100)).append("\n");
                                            sb.append("    - 剩余: ").append(pending.getRemainingMs()).append("ms\n");
                                            sb.append("    - 触发时间: ").append(pending.triggerTimeMs).append("\n");
                                            sb.append("    - 延迟时长: ").append(pending.delayMs).append("ms\n");
                                            sb.append("    - 动画帧数: ").append(pending.animation != null ? pending.animation.frames.size() : 0).append("\n");
                                        } else {
                                            sb.append("  \u00a7a当前无延迟任务\n");
                                        }
                                    }
                                }
                            }
                            ctx.getSource().sendSystemMessage(Component.literal(sb.toString()));
                            return 1;
                        })))
        );
    }
}