package com.example.muzzleflash;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端事件：
 * 1. 监听 GunFireEvent 触发枪焰动画（与 TACZ 原版一致）。
 * 2. 每帧 tick 检查动画是否到期。
 */
@Mod.EventBusSubscriber(modid = MuzzleFlashMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MuzzleFlashClientEvents {

    /** 开火事件触发枪焰动画（与 TACZ 原版一致：监听 GunFireEvent 而非检测弹药变化）。 */
    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (!event.getLogicalSide().isClient()) return;
        LivingEntity shooter = event.getShooter();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !shooter.equals(player)) return;
        ItemStack gunItem = event.getGunItemStack();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return;
        ResourceLocation gunId = iGun.getGunId(gunItem);
        MuzzleFlashManager mgr = MuzzleFlashManager.get();
        if (mgr != null) {
            mgr.triggerAnimation(gunId);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MuzzleFlashManager mgr = MuzzleFlashManager.get();
        if (mgr == null) return;
        mgr.tick();
    }
}
