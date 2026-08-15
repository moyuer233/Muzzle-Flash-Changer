# Muzzle Flash Changer

自定义 TACZ 枪口火焰（muzzle flash）的模组：动画取代原版贴图，按枪定制 + 统一回退，通过 `GunFireEvent` 触发（与 TACZ 原版一致）。

## 功能

- **动画枪焰**：多帧贴图逐帧播放，取代原版单张贴图
- **按枪定制**：可为每把枪单独配置枪焰（帧序列、时长、缩放、偏移、延迟）
- **统一回退**：未单独配置的枪使用默认枪焰
- **触发同步**：监听 `GunFireEvent`，真正扣扳机消耗弹药时才触发，空枪/没弹药不触发
- **消音枪支持**：可配置指定枪完全无枪焰（`disableFlash`）
- **发光渲染**：emissive 着色器 + 不写深度，暗处也清晰、不与枪模遮挡
- **自动缩放**：按贴图有效内容（非透明区域）自动调整大小
- **热重载**：支持 `/reload`（或 F3+T）重新加载配置和贴图

## 环境要求

- Minecraft **1.20.1** + Forge **47.4.x**
- JDK **17**
- 前置 mod：[TACZ](https://github.com/MCModderAnchor/TACZ) **1.1.8+**

## 依赖说明

依赖已配置为 **Gradle 构建时自动下载**，无需手动准备：

- **tacz**：CurseForge Maven（`curse.maven:timeless-and-classics-zero-1028108:8141310`，1.1.8-hotfix）
- **mixinextras**：Maven Central（`io.github.llamalad7:mixinextras-*:0.4.1`）

首次构建自动拉取（需联网）。

## 安装与编译

```
gradlew build
```

输出：`build/libs/muzzleflash-1.0.0.jar`，放入 `mods/` 目录。

首次启动会自动向 TACZ 默认枪包（`.minecraft/tacz/tacz_default_gun/`）注入 `muzzleflash_compat.json` 和枪焰贴图。修改配置后执行 `/reload` 即可生效。

## 配置详解

配置文件为枪包根目录下的 **`muzzleflash_compat.json`**（本模组会自动生成默认模板）。

### 总开关

| 字段 | 值 | 说明 |
|---|---|---|
| `muzzle_flash_mode` | `tmfmod` | 本模组接管枪焰渲染 |
| | `default` | 使用原版 TACZ 枪焰（未启用本模组） |

### 动画字段（`defaultAnimation` 与 `guns.<gunId>` 通用）

| 字段 | 默认值 | 说明 |
|---|---|---|
| `defaultmuzzleflashframes` | `[]` | 默认帧列表，资源路径数组（按顺序播放） |
| `muzzleframes` | `[]` | 单枪专用帧列表（配置在 `guns` 下时优先使用） |
| `frameDurationMs` | `30` | 每帧停留毫秒数 |
| `FrameDuration` | `50` | 动画总显示时长（毫秒） |
| `scale` | `1.5` | 缩放倍率 |
| `autoScale` | `true` | 按贴图有效内容自动缩放（大图里火焰占小区域时防止被缩小） |
| `baseTextureSize` | `300` | 自动缩放的参考像素尺寸 |
| `flashDelayMs` | `0` | 枪焰延迟启动（毫秒） |
| `offsetX` / `offsetY` / `offsetZ` | `0` | 枪焰位置偏移（世界单位，1.0=1米；X 右、Y 上、Z 前） |
| `disableFlash` | `false` | `true` 时该枪完全不渲染枪焰（消音枪等） |

### 完整示例

```json
{
  "muzzle_flash_mode": "tmfmod",
  "defaultAnimation": {
    "defaultmuzzleflashframes": [
      "muzzleflash:tacz/textures/muzzle/default/frame_1",
      "muzzleflash:tacz/textures/muzzle/default/frame_2"
    ],
    "frameDurationMs": 30,
    "scale": 1.5,
    "autoScale": true
  },
  "guns": {
    "tacz:ak47": {
      "muzzleframes": [
        "muzzleflash:tacz/textures/muzzle/ak47/frame_1",
        "muzzleflash:tacz/textures/muzzle/ak47/frame_2"
      ],
      "frameDurationMs": 25,
      "scale": 1.2
    },
    "re:dragoon": {
      "disableFlash": true
    },
    "tacz:rpg7": {
      "flashDelayMs": 300,
      "offsetZ": -0.1
    }
  }
}
```

- `defaultAnimation`：所有未单独配置的枪使用
- `guns.<gunId>`：覆盖指定枪（`<命名空间>:<枪id>`，如 `tacz:ak47`、`re:rsh12`）

### 帧资源路径

贴图位于 `assets/<namespace>/textures/muzzle/<名称>/frame_*.png`，配置中的路径格式为：
`<模组id>:<namespace>/textures/muzzle/<名称>/frame_<序号>`

例：`muzzleflash:tacz/textures/muzzle/default/frame_1` 对应 `assets/tacz/textures/muzzle/default/frame_1.png`。

### 怎么知道某把枪的 gunId

游戏中按 **F3 + H** 打开高级提示，悬停枪械即可看到 ID；或查看枪包数据文件 `data/<namespace>/guns/<name>.json`，`<namespace>:<name>` 即为 gunId。
