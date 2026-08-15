# Muzzle Flash Changer

自定义 TACZ 枪焰 mod：动画取代贴图，按枪定制+统一回退，通过 `GunFireEvent` 触发（与 TACZ 原版一致）。

## 工作原理

- 监听 TACZ 的 `GunFireEvent` 开火事件触发枪焰动画（与 TACZ 原版触发逻辑一致：真正扣扳机并消耗弹药、生成子弹时才触发，未打中/没弹药不会触发）
- Mixin 钩进 `MuzzleFlashRender.render()`，在 tmfmod 模式下取消原版枪焰渲染，改由本模组接管
- 每帧 tick 检查动画是否到期，到期自动结束
- 帧动画来自内置配置（按枪定制 + 统一回退），循环播放直到总时长结束

## 文件结构

```
TACZ-MuzzleFlash/
  build.gradle
  gradle.properties
  settings.gradle
  src/main/
    java/com/example/muzzleflash/
      MuzzleFlashMod.java          // Mod 入口
      MuzzleFlashConfig.java       // 配置加载（config/muzzleflash.json）
      MuzzleFlashAnimation.java    // 动画数据
      MuzzleFlashManager.java      // 运行时管理器
      MuzzleFlashClientEvents.java // 客户端 tick
      mixin/
        MuzzleFlashRenderMixin.java// 钩进 TACZ
    resources/
      META-INF/mods.toml
      muzzleflash.mixins.json
      pack.mcmeta
```

## 环境要求

- Minecraft **1.20.1** + Forge **47.4.x**
- JDK **17**
- Gradle **7.6.6**（项目自带 `gradlew`）
- 前置 mod：[TACZ (Timeless and Classics Zero)](https://github.com/MCModderAnchor/TACZ) **1.1.8+**

## 依赖说明

依赖已配置为 **Gradle 构建时自动下载**，无需手动准备：

- **tacz**：从 CurseForge Maven 自动下载（`curse.maven:timeless-and-classics-zero-1028108:8141310`，对应 1.1.8-hotfix）
- **mixinextras**：从 Maven Central 自动下载（`io.github.llamalad7:mixinextras-*:0.4.1`）

首次构建会自动拉取这些依赖（需联网）。

## 用法

### 1. 编译

```
cd TACZ-MuzzleFlash
gradlew build
```

输出在 `build/libs/muzzleflash-1.0.0.jar`。

### 2. 首次启动会生成默认配置

路径：`config/muzzleflash.json`

```json
{
  "defaultAnimation": {
    "frames": ["muzzleflash:textures/muzzle/default/frame_0", ...],
    "frameDurationMs": 30,
    "scale": 1.0
  },
  "guns": {
    "tacz:ak47": {
      "frames": ["muzzleflash:textures/muzzle/ak47/frame_0", ...],
      "frameDurationMs": 25,
      "scale": 1.2
    }
  }
}
```

- `defaultAnimation`：所有未列出的枪都用这个
- `guns.<gunId>`：覆盖指定枪的动画（gunId 一般是 `tacz:ak47` 这种）
- `frames`：贴图资源路径列表（按顺序播放）
- `frameDurationMs`：每帧停留毫秒数
- `scale`：渲染缩放

### 3. 放你的帧贴图

把你的逐帧贴图放进资源包或本 mod 的 `src/main/resources/assets/<namespace>/textures/muzzle/<name>/frame_0.png`、`frame_1.png` 

`namespace` 用你自己的，比如 `mymod`，那么配置写：
```json
"frames": ["mymod:textures/muzzle/ak47/frame_0", "mymod:textures/muzzle/ak47/frame_1", ...]
```

### 4. 怎么知道某把枪的 gunId

启动游戏，启用 mod 后查看日志，触发开火时会打印当前枪的 id。或者打开你的 gun datapack，看 `data/<namespace>/guns/<name>.json`  `<namespace>:<name>` 就是 gunId。

## 自定义行为

- 想让特定枪完全不显示枪焰：给一个只有 1 帧透明贴图的配置
- 想在动画里加额外效果（光、粒子）：编辑 `MuzzleFlashManager.render()`，在那里发 particle 即可
- 想把动画改成立体模型：把 `SlotQuad` 换成你的 `EntityModel`，按 keyframe 平移/缩放骨骼
