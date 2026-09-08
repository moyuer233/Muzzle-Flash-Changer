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

### **`muzzleflash_compat.json`** 配置

| 字段 | 说明 |
|---|---|
| `muzzle_flash_mode`|`tmfmod` 本模组枪焰渲染 `default` 使用原版 TACZ 枪焰 |
| `defaultmuzzleflashframes` | 默认帧列表，资源路径数组（按顺序播放） |
| `muzzleframes` | 单枪专用帧列表（配置在 `guns` 下时优先使用） |
| `FrameDurationMs` | 动画总显示时长（毫秒） |
| `scale` | 缩放倍率（最终渲染缩放 = scale × 枪型系数，见下文） |
| `autoScale` | 按贴图有效内容自动缩放 |
| `flashDelayMs` | 枪焰延迟启动（毫秒），**仅 `guns` 下生效**（`defaultAnimation` 中的该字段无效） |
| `offsetX` / `offsetY` / `offsetZ` | 枪焰位置偏移（世界单位，1.0=1米；X 右、Y 上、Z 前） |
| `disableFlash` | `true` 时不渲染枪焰（含回退帧）；放在 `defaultAnimation` 可整包禁用 |
| `autoScaleFromDisplay` | 自动匹配原版特效大小，见下方"自动匹配原版特效" |

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
- **枪型缩放系数**：最终渲染缩放 = `scale` × 枪型系数。枪型由 TACZ 枪数据的 `type` 决定：pistol=0.7、smg=0.85、rifle=1.0、shotgun=1.3、sniper=1.4、grenade=1.5、special=1.1、melee=0.5（未知枪型按 1.0）。想让某把枪的最终大小完全等于 `scale`，可把 `scale` 设为 `期望值 ÷ 枪型系数`。

### 自动匹配原版特效（第三方枪包位置/大小适配）

TACZ 原版枪口特效是挂在枪模 `muzzle_flash` 骨骼上的一个 16px 画面（`SlotModel`），画面中心就是骨骼点，总宽 = `0.5 × display.muzzle_flash.scale` 格。枪包作者（尤其是第三方枪包）在建模时就是按"原版特效落在枪口的样子"来摆放该骨骼、逐枪调 `muzzle_flash.scale` 的。

本模组渲染时（默认 `autoScaleFromDisplay: true`）：
- **尺寸**：画面总宽直接取 `0.5 × display.muzzle_flash.scale` 格，与原版特效 1:1 一致，不再叠加 `scale × 枪型系数`（避免双重缩放）——所以第三方枪包上火焰会自动贴合枪口、大小与原版相同，无需逐枪手调；
- **位置**：画面中心即枪口骨骼点，与原版特效同锚点（`offsetX/Y/Z` 仍可用作额外微调）；
- 当某枪 `display` 里没写 `muzzle_flash`、或 `scale ≤ 0`（如枪包作者用 0 关闭了原版特效）时，自动匹配不生效，回退到 `scale × 枪型系数` 体系（`autoScale` 同样生效）。

想让某把枪恢复旧的"自定义大小"渲染，把该枪（或 `defaultAnimation`）的 `autoScaleFromDisplay` 设为 `false` 即可。

### 多枪口（双持/多管枪械）支持

TACZ 原版只会在名为 `muzzle_flash` 的**单个**骨骼上渲染枪口特效。双持/多管枪械的模型常在每支枪口都放一个挂点（`muzzle_flash2`、`muzzle_flash3`…），原版只闪主枪口那一支。

本模组会自动把这类**额外枪口挂点**也纳入渲染：开火时每一支枪口都按各自位置同步闪出同一个火焰动画（闪电鹰 X 双持、RSH12 双持、wingshooter 双持模式等开箱即用，无需任何配置）。普通单枪口模型不受影响。

### 帧资源路径

贴图位于 `assets/<namespace>/textures/muzzle/<名称>/frame_*.png`，配置中的路径格式为：
`<模组id>:<namespace>/textures/muzzle/<名称>/frame_<序号>`

例：`muzzleflash:tacz/textures/muzzle/default/frame_1` 对应 `assets/tacz/textures/muzzle/default/frame_1.png`。

### 怎么知道某把枪的 gunId

游戏中按 **F3 + H** 打开高级提示，悬停枪械即可看到 ID；或查看枪包数据文件 `data/<namespace>/guns/<name>.json`，`<namespace>:<name>` 即为 gunId。

### muzzleframes 自动生成机制

无需手动在 JSON 中编写帧列表。**只要把贴图放到正确目录，reload 时本模组会自动扫描并生成帧配置**：

```
.minecraft/tacz/<枪包>/assets/<namespace>/textures/muzzle/
├── default/                  → 自动生成 defaultAnimation.defaultmuzzleflashframes
│   ├── frame_1.png
│   ├── frame_2.png
│   └── ...
└── <枪id的path部分>/          → 自动生成 guns.<namespace>:<枪id>.muzzleframes
    ├── frame_1.png           （例如 rsh12/ → re:rsh12 的帧）
    └── frame_2.png
```

- 其它子目录名对应枪 id 的 path 部分（如 `rsh12` 对应 `re:rsh12`）→ 自动为该枪生成 `muzzleframes` 配置
- 每帧自动生成：`muzzleflash:<namespace>/textures/muzzle/<目录名>/frame_<序号>`（按文件名数字排序）
- 自动为新枪补上枪焰持续时长（默认 50ms）
- **自动同步**：reload 时会删除配置中已不存在的贴图帧、补充新增的贴图帧，并回写 JSON——删贴图后无需手动清理配置
- 用 `F3 + T`、`/reload` 或模组指令 `/muzzleflash reload` 触发扫描

因此定制一把枪的枪焰只需两步：① 在 `textures/muzzle/<枪id>/` 放好 `frame_N.png`；② reload 即可生效，无需手写 `muzzleframes`。

## 指令

本模组提供客户端指令：

| 指令 | 功能 |
|---|---|
| `/muzzleflash reload` | 重新扫描所有枪包的 `muzzleflash_compat.json` 配置并清空延迟任务 |
| `/muzzleflash debug` | 开关调试模式。开启后输出详细日志到 `tacz/muzzleflashlog.txt`（信息类别：GUN、AMMO、TRIGGER、DELAY、ANIM、RENDER、SNAPSHOT） |
| `/muzzleflash loginfo` | 输出状态汇总：调试状态、枪包配置数、当前持枪的子弹数 / 枪焰延迟 / tmfmod 模式 / 动画帧数 / scale / 延迟任务 / 枪焰是否活跃 |
| `/muzzleflash delay` | 查看当前持枪的枪焰延迟状态（配置延迟、进度、剩余时间、是否活跃） |
| `/muzzleflash delay info` | 查看当前持枪的详细延迟信息（触发时间、延迟时长、动画帧数等） |

---

如果觉得好用，请给个 ⭐ Star 支持一下！欢迎提交 Issue 和 Pull Request。

---

Agent太好用了，就是太费token了(200m)
