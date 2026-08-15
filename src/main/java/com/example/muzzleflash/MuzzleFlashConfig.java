package com.example.muzzleflash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public class MuzzleFlashConfig {

    public static class GlobalConfig {
        public boolean showReloadMessage = true;
    }

    private static GlobalConfig data = new GlobalConfig();

    public static GlobalConfig get() {
        return data;
    }

    public static boolean isShowReloadMessage() {
        return data != null && data.showReloadMessage;
    }

    public static void load() {
        Path path = MuzzleFlashContent.getConfigFile();
        if (!Files.exists(path)) {
            data = new GlobalConfig();
            MuzzleFlashMod.LOGGER.info("[MuzzleFlash] no config at {}, using defaults", path);
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            GlobalConfig loaded = new Gson().fromJson(reader, GlobalConfig.class);
            if (loaded == null) {
                MuzzleFlashMod.LOGGER.warn("[MuzzleFlash] config is empty, using defaults");
                data = new GlobalConfig();
                return;
            }
            data = loaded;
            if (data == null) data = new GlobalConfig();
            MuzzleFlashMod.LOGGER.info("[MuzzleFlash] config loaded from {}: showReloadMessage={}",
                    path, data.showReloadMessage);
        } catch (IOException e) {
            MuzzleFlashMod.LOGGER.error("[MuzzleFlash] failed to load config from " + path, e);
            data = new GlobalConfig();
        }
    }

    public static void saveTemplate(Path path) {
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("showReloadMessage", true);
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            MuzzleFlashMod.LOGGER.info("[MuzzleFlash] template config written to {}", path);
        } catch (IOException e) {
            MuzzleFlashMod.LOGGER.error("[MuzzleFlash] failed to write template config", e);
        }
    }
}