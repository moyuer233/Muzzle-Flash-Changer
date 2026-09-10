package io.github.moyuer233.muzzleflashchanger.mixin;

import io.github.moyuer233.muzzleflashchanger.ExtraMuzzleFlashRender;
import io.github.moyuer233.muzzleflashchanger.MuzzleFlashMod;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为双持/多管枪械模型的"额外枪口"骨骼注册特效挂点。
 * <p>
 * TACZ 只在名为 {@code muzzle_flash} 的单个骨骼上注册 MuzzleFlashRender；
 * 模型里额外的 {@code muzzle_flash2..9} 骨骼（如闪电鹰 X 双持、wingshooter 双持模式等）
 * 不会被渲染任何特效。这里在 BedrockGunModel 构造完成后，把同样的特效挂点注册到
 * 这些额外骨骼上——骨骼不存在的枪注册出的节点不在渲染树中，永不执行、无副作用，
 * 因此对普通单枪口模型也完全无害（通用适配，无需逐个枪包特例）。
 */
@Mixin(value = BedrockGunModel.class, remap = false)
public abstract class BedrockGunModelMixin {

    /** 最多支持的额外枪口数（muzzle_flash2 ~ muzzle_flash9） */
    private static final int MAX_EXTRA_MUZZLE_NODES = 8;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void muzzleflash$registerExtraMuzzleFlashNodes(BedrockModelPOJO pojo, BedrockVersion version, CallbackInfo ci) {
        try {
            BedrockGunModel self = (BedrockGunModel) (Object) this;
            ExtraMuzzleFlashRender renderer = new ExtraMuzzleFlashRender(self);
            for (int i = 2; i <= MAX_EXTRA_MUZZLE_NODES + 1; i++) {
                String node = "muzzle_flash" + i;
                self.setFunctionalRenderer(node, bedrockPart -> renderer);
            }
        } catch (Exception e) {
            MuzzleFlashMod.LOGGER.error("[MuzzleFlash] failed to register extra muzzle flash nodes", e);
        }
    }
}
