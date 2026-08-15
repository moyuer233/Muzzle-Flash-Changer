package com.example.muzzleflash.mixin;

import com.example.muzzleflash.GunPackCompatManager;
import com.example.muzzleflash.MuzzleFlashDebug;
import com.example.muzzleflash.MuzzleFlashManager;
import com.example.muzzleflash.MuzzleFlashMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 钩进 TACZ 的 MuzzleFlashRender。
 *
 * 渲染流水线（关键）：
 * 1. BedrockModel.render() 遍历骨骼时，FunctionalBedrockPart.render() 会先把 poseStack
 *    定位到枪口骨骼，再调用 functionalRenderer.render()（即 MuzzleFlashRender.render）。
 * 2. 原版 render() 通过 bedrockModel.delegateRender(...) 把真正的渲染延迟到
 *    所有骨骼渲染完成后（BedrockModel.render() 末尾统一执行 delegateRenderers）。
 * 3. 若在 render() HEAD 直接渲染枪焰（而不是延迟），枪焰 quad 会和枪模几何渲染交错，
 *    且独立 BufferSource 的 flush 时机与枪模不同，产生深度/混合错位 → 金色残留。
 *
 * 我们的策略（模仿原版 delegateRender）：
 * - render() HEAD：ci.cancel() 取消原版，捕获当前枪口矩阵（每帧更新，避免原版
 *   muzzleFlashPose 只在开火后第一帧更新的位置偏差），再 delegateRender 延迟渲染。
 * - 延迟回调里用捕获的矩阵重建 PoseStack，交给 MuzzleFlashManager 渲染枪焰。
 * - 唯一触发路径：GunFireEvent 开火事件（MuzzleFlashClientEvents.onGunFire）。
 */
@Mixin(value = MuzzleFlashRender.class, remap = false)
public abstract class MuzzleFlashRenderMixin {

    @Shadow(remap = false)
    private BedrockGunModel bedrockGunModel;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void muzzleflash$onRender(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay, CallbackInfo ci) {
        try {
            MuzzleFlashManager mgr = MuzzleFlashManager.get();
            if (mgr == null) return;

            // 获取当前枪械信息
            ResourceLocation gunId = null;
            String gunType = null;
            try {
                if (bedrockGunModel != null) {
                    ItemStack currentGunItem = bedrockGunModel.getCurrentGunItem();
                    if (currentGunItem != null && !currentGunItem.isEmpty()) {
                        IGun iGun = IGun.getIGunOrNull(currentGunItem);
                        if (iGun != null) {
                            gunId = iGun.getGunId(currentGunItem);

                            if (gunId != null) {
                                ClientGunIndex index = TimelessAPI.getClientGunIndex(gunId).orElse(null);
                                if (index != null) {
                                    gunType = index.getType();
                                    mgr.setCurrentGunType(gunType);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略
            }

            boolean isTmfMod = GunPackCompatManager.isTmfModMode(gunId);
            if (!isTmfMod) {
                // 非 tmfmod 模式：放行原版
                if (MuzzleFlashDebug.isEnabled()) {
                    MuzzleFlashDebug.log("RENDER", String.format("pass-through: non-tmfmod, gun=%s", gunId));
                }
                return;
            }

            // tmfmod 模式：取消原版，完全由我们自己控制
            ci.cancel();

            // 阻止 TACZ 原版在其他路径再次渲染（isSelf=false 会让原版 render 提前返回）
            try {
                MuzzleFlashRender.isSelf = false;
            } catch (Exception e) {
                // 忽略
            }

            if (MuzzleFlashDebug.isEnabled()) {
                mgr.debugSnapshot(gunId, gunType, isTmfMod);
            }

            // 检查动画是否过期
            mgr.tick();

            // 如果动画活跃，捕获枪口矩阵并延迟到枪模渲染完成后渲染
            if (mgr.isActive()) {
                // 每帧都捕获当前枪口矩阵（避免原版只在开火第一帧更新导致的位置偏差）
                Matrix4f poseCopy = new Matrix4f(poseStack.last().pose());
                Matrix3f normalCopy = new Matrix3f(poseStack.last().normal());

                bedrockGunModel.delegateRender((ps, vb, tt, l, o) -> {
                    mgr.renderAtMuzzleDeferred(poseCopy, normalCopy, l, o);
                });
            }
        } catch (Exception e) {
            MuzzleFlashMod.LOGGER.error("[MuzzleFlash] error in render mixin", e);
        }
    }
}
