package io.github.moyuer233.muzzleflashchanger;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 多枪口（双持/多管）模型的"额外枪口"特效渲染器。
 * <p>
 * TACZ 只把枪口特效挂在名为 {@code muzzle_flash} 的单个骨骼上；双持/多管模型
 * （骨骼如 muzzle_flash2、muzzle_flash3…）的其它枪口原版不会出火。
 * 本渲染器由 {@link io.github.moyuer233.muzzleflashchanger.mixin.BedrockGunModelMixin} 注册到这些额外骨骼：
 * 本地玩家第一人称开火时（与原版特效同一 isSelf 上下文），在该骨骼处同步渲染
 * 同一个开火动画的 quad（位置 = 该骨骼矩阵），实现多支枪口同时出火。
 */
public class ExtraMuzzleFlashRender implements IFunctionalRenderer {
    private final BedrockGunModel bedrockGunModel;

    public ExtraMuzzleFlashRender(BedrockGunModel bedrockGunModel) {
        this.bedrockGunModel = bedrockGunModel;
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay) {
        try {
            // 与原版特效一致：只有本地玩家第一人称渲染（isSelf=true）才接管，
            // 避免把本地玩家的动画误闪到第三方玩家/展示框的模型上。
            if (!MuzzleFlashRender.isSelf) {
                return;
            }
            MuzzleFlashManager mgr = MuzzleFlashManager.get();
            if (mgr == null) {
                return;
            }
            // 只有当前开火动画确属这把枪时才跟随（防御：确保不会串枪）
            if (!mgr.isActiveForGun(getCurrentGunId())) {
                return;
            }
            // 捕获本枪口骨骼矩阵，延迟到枪模几何全部渲染完后绘制
            // （与主枪口一致，避免特效与枪模几何交错造成深度/混合错位）
            Matrix4f poseCopy = new Matrix4f(poseStack.last().pose());
            Matrix3f normalCopy = new Matrix3f(poseStack.last().normal());
            bedrockGunModel.delegateRender((ps, vb, tt, l, o) -> mgr.renderAtMuzzleDeferred(poseCopy, normalCopy, l, o));
        } catch (Exception e) {
            MuzzleFlashMod.LOGGER.error("[MuzzleFlash] error in extra muzzle flash render", e);
        }
    }

    private ResourceLocation getCurrentGunId() {
        try {
            if (bedrockGunModel != null) {
                ItemStack item = bedrockGunModel.getCurrentGunItem();
                if (item != null && !item.isEmpty()) {
                    IGun iGun = IGun.getIGunOrNull(item);
                    if (iGun != null) {
                        return iGun.getGunId(item);
                    }
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }
}
