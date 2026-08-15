# Muzzle Flash Changer

**自定义 TACZ 枪口火焰的模组**：动画取代原版贴图，拥有丰富的自定义配置。

## 功能

- **动画枪焰**：多帧贴图逐帧播放，取代原版单张贴图
- **按枪定制**：可为每把枪单独配置枪焰（帧序列、时长、缩放、偏移、延迟）
- **统一回退**：未单独配置的枪使用默认枪焰
- **自动缩放**：按贴图有效内容（非透明区域）自动调整大小
- **热重载**：支持 `/reload`（或 F3+T）重新加载配置和贴图

## 环境要求

- Minecraft **1.20.1** + Forge **47.4.x**
- 前置 mod：[TACZ](https://github.com/MCModderAnchor/TACZ) **1.1.8+**

## 配置详解

- 首次启动会自动向 TACZ 默认枪包（`.minecraft/tacz/tacz_default_gun/`）注入 `muzzleflash_compat.json` 和枪焰贴图。
- 配置文件为枪包根目录下的 **`muzzleflash_compat.json`**。

### 总开关

| `muzzle_flash_mode`| 说明 |
|---|---|
| `tmfmod` | 本模组枪焰渲染 |
| `default` | 使用原版 TACZ 枪焰 |

### 动画字段（`defaultAnimation` 与 `guns.<gunId>` 通用）

| 字段 | 默认值 | 说明 |
|---|---|---|
| `defaultmuzzleflashframes` | `见示例` | 默认帧列表，资源路径数组（按顺序播放） |
| `muzzleframes` | `无` | 单枪专用帧列表（配置在 `guns` 下时优先使用） |
| `FrameDurationMs` | `50` | 动画总显示时长（毫秒） |
| `scale` | `1.5` | 缩放倍率 |
| `autoScale` | `true` | 按贴图有效内容自动缩放 |
| `flashDelayMs` | `0` | 枪焰延迟启动（毫秒） |
| `offsetX` / `offsetY` / `offsetZ` | `0` | 枪焰位置偏移（世界单位，1.0=1米；X 右、Y 上、Z 前） |
| `disableFlash` | `false` | `true` 时不渲染枪焰 |

### 完整示例

```json
{
  "muzzle_flash_mode": "tmfmod",
  "defaultAnimation": {
    "defaultmuzzleflashframes": [
      "muzzleflash:tacz/textures/muzzle/default/frame_1",
      "muzzleflash:tacz/textures/muzzle/default/frame_2",
      "muzzleflash:tacz/textures/muzzle/default/frame_3",
      "muzzleflash:tacz/textures/muzzle/default/frame_4",
      "muzzleflash:tacz/textures/muzzle/default/frame_5",
      "muzzleflash:tacz/textures/muzzle/default/frame_6",
      "muzzleflash:tacz/textures/muzzle/default/frame_7",
      "muzzleflash:tacz/textures/muzzle/default/frame_8"
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
