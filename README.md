# Custom Muzzle Flash for TACZ

自定义 TACZ 枪焰 mod：动画取代贴图，按枪定制+统一回退，按"子弹是否真的射出来"触发动画。

## 工作原理

- Mixin 钩进 `MuzzleFlashRender.render()`，对比 `shootTimeStamp` 检测新开火
- `shootTimeStamp` 只在 `GunFireEvent` 真正触发（即扣扳机并消耗弹药、生成子弹）时被设置，所以"未打中/没弹药"的情况不会触发动画
- 每帧对比上次记录的 `shootTimeStamp`，变化就启动对应枪的动画
- 我们的渲染接管后取消（`callbackInfo.cancelCallback()`）原版渲染
- 帧动画来自配置文件，循环播放直到总时长结束

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
