package com.example.muzzleflash;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MuzzleFlashMod.MODID)
public class MuzzleFlashMod {
    public static final String MODID = "muzzleflash";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public MuzzleFlashMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MuzzleFlashManager.init();
            modBus.addListener(this::onClientSetup);
            modBus.addListener(this::onRegisterReloadListeners);
        }

        LOGGER.info("[MuzzleFlash] mod loaded");
    }

    private void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MuzzleFlashContent.ensureFirstLaunchTemplate();
            MuzzleFlashContent.reload();
        });
    }

    /**
     * 把自己注册成 /reload 的监听器，MC 自带 reload（F3+T 或 /reload）时会调用 reload
     */
    private void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new MuzzleFlashContent.ReloadListener());
    }
}