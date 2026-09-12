<div align="center">

# Muzzle Flash Changer

_✨ 自定义 TACZ 枪口火焰：多帧动画取代原版单张贴图，支持按枪配置 ✨_

</div>

<p align="center">
  <img src="https://img.shields.io/github/license/moyuer233/Muzzle-Flash-Changer" alt="license">
  <img src="https://img.shields.io/github/v/release/moyuer233/Muzzle-Flash-Changer" alt="release">
  <img src="https://img.shields.io/github/downloads/moyuer233/Muzzle-Flash-Changer/total" alt="downloads">
  <img src="https://img.shields.io/badge/Minecraft-1.20.1-62B47A" alt="Minecraft">
  <img src="https://img.shields.io/badge/Forge-47.x-orange" alt="Forge">
  <img src="https://img.shields.io/badge/Java-17-ED8B00" alt="Java">
</p>

## 功能

- 动画枪焰：多帧贴图逐帧播放，取代原版单张贴图
- 按枪定制：可给每把枪单独配置枪焰（帧序列、时长、缩放、偏移、延迟）
- 统一回退：未单独配置的枪使用默认枪焰
- 自动缩放：按贴图有效内容（非透明区域）自动调整大小
- 热重载：`/reload`（或 F3+T）重新加载配置与贴图

## 环境要求

- Minecraft 1.20.1 + Forge 47.4.x
- 前置 mod：[TACZ](https://github.com/MCModderAnchor/TACZ) 1.1.8+

## 配置

首次启动会自动向 TACZ 默认枪包（`.minecraft/tacz/tacz_default_gun/`）注入 `muzzleflash_compat.json` 与枪焰贴图。
配置文件是枪包根目录下的 `muzzleflash_compat.json`。

### 字段

| 字段 | 说明 |
|---|---|
| `muzzle_flash_mode` | `tmfmod` 用本模组渲染枪焰；`default` 用原版 TACZ 枪焰 |
| `defaultmuzzleflashframes` | 默认帧列表，资源路径数组（按顺序播放） |
| `muzzleframes` | 单枪专用帧列表（配在 `guns` 下时优先使用） |
| `frameDurationMs` | 动画总显示时长（毫秒） |
| `scale` | 缩放倍率（最终渲染缩放 = scale × 枪型系数，见下） |
| `autoScale` | 按贴图有效内容自动缩放 |
| `flashDelayMs` | 枪焰延迟启动（毫秒），仅 `guns` 下生效（`defaultAnimation` 里的该字段无效） |
| `offsetX` / `offsetY` / `offsetZ` | 枪焰位置偏移（世界单位，1.0 = 1 米；X 右、Y 上、Z 前） |
| `disableFlash` | `true` 时不渲染枪焰（含回退帧）；放在 `defaultAnimation` 可整包禁用 |
| `autoScaleFromDisplay` | 自动匹配原版特效大小，见「自动匹配原版特效」 |

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
- 枪型缩放系数：最终渲染缩放 = `scale` × 枪型系数。枪型取 TACZ 枪数据的 `type`，
  pistol 0.7、smg 0.85、rifle 1.0、shotgun 1.3、sniper 1.4、grenade 1.5、special 1.1、melee 0.5，
  未知按 1.0。想让某把枪的最终大小等于 `scale`，把 `scale` 设为 `期望值 ÷ 枪型系数`。

### 自动匹配原版特效（第三方枪包适配）

TACZ 原版枪口特效挂在枪模 `muzzle_flash` 骨骼上，是一个 16px 画面（`SlotModel`），
画面中心即骨骼点，总宽 = `0.5 × display.muzzle_flash.scale` 格。枪包作者建模时就是按
"原版特效落在枪口的样子"摆放该骨骼、逐枪调 `muzzle_flash.scale` 的。

本模组渲染时（默认 `autoScaleFromDisplay: true`）：

- 尺寸：画面总宽直接取 `0.5 × display.muzzle_flash.scale` 格，与原版 1:1，
  不再叠加 `scale × 枪型系数`（避免双重缩放）。所以第三方枪包上火焰会自动贴合枪口、大小与原版相同，
  无需逐枪手调。
- 位置：画面中心即枪口骨骼点，与原版同锚点（`offsetX/Y/Z` 仍可作额外微调）。
- 当某枪的 `display` 里没写 `muzzle_flash`、或 `scale ≤ 0`（枪包作者用 0 关掉了原版特效）时，
  自动匹配不生效，回退到 `scale × 枪型系数`（`autoScale` 同样生效）。

想让某把枪恢复自定义大小，把该枪或 `defaultAnimation` 的 `autoScaleFromDisplay` 设为 `false`。

### 多枪口（双持 / 多管械）

TACZ 原版只在名为 `muzzle_flash` 的单个骨骼上渲染枪口特效。双持与多管枪械的模型常在每支枪口各放一个挂点
（`muzzle_flash2`、`muzzle_flash3`…），原版只闪主枪口那一支。

本模组会把这类额外挂点也纳入渲染：开火时每支枪口按各自位置同步闪出同一动画
（闪电鹰 X 双持、RSH12 双持、wingshooter 双持模式等开箱即用，无需配置）。普通单枪口模型不受影响。

### 帧资源路径

贴图放在 `assets/<namespace>/textures/muzzle/<名称>/frame_*.png`，配置里的路径格式是
`<模组id>:<namespace>/textures/muzzle/<名称>/frame_<序号>`。

例：`muzzleflash:tacz/textures/muzzle/default/frame_1` 对应
`assets/tacz/textures/muzzle/default/frame_1.png`。

### 怎么查某把枪的 gunId

游戏里按 F3 + H 打开高级提示，悬停枪械即可看到 ID；或看枪包数据文件
`data/<namespace>/guns/<name>.json`，`<namespace>:<name>` 就是 gunId。

### muzzleframes 自动生成

不用手写帧列表。把贴图放到正确目录，reload 时本模组会自动扫描并生成帧配置：

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

- 其它子目录名对应枪 id 的 path 部分（如 `rsh12` 对应 `re:rsh12`）
- 每帧路径按 `muzzleflash:<namespace>/textures/muzzle/<目录名>/frame_<序号>` 生成，按文件名数字排序
- 自动为新枪补上枪焰持续时长（默认 50ms）
- reload 时会删掉配置里已不存在的帧、补上新增的帧，并回写 JSON，删贴图后不用手工清配置
- 触发方式：F3 + T、`/reload`，或 `/muzzleflash reload`

所以定制一把枪的枪焰只要两步：把 `frame_N.png` 放进 `textures/muzzle/<枪id>/`，然后 reload。

## 指令

| 指令 | 功能 |
|---|---|
| `/muzzleflash reload` | 重新扫描所有枪包的 `muzzleflash_compat.json` 并清空延迟任务 |
| `/muzzleflash debug` | 开关调试模式，开启后输出详细日志到 `tacz/muzzleflashlog.txt`（类别：GUN、AMMO、TRIGGER、DELAY、ANIM、RENDER、SNAPSHOT） |
| `/muzzleflash loginfo` | 输出状态汇总：调试状态、枪包配置数、当前持枪的子弹数 / 枪焰延迟 / tmfmod 模式 / 动画帧数 / scale / 延迟任务 / 枪焰是否活跃 |
| `/muzzleflash delay` | 查看当前持枪的枪焰延迟状态（配置延迟、进度、剩余时间、是否活跃） |
| `/muzzleflash delay info` | 查看当前持枪的详细延迟信息（触发时间、延迟时长、动画帧数等） |

## 更新日志

### v1.1.0

- 新增：枪焰自动匹配原版特效大小。以该枪 `display.muzzle_flash.scale` 为基准渲染（画面总宽 `0.5 × scale` 格，中心即枪口骨骼点），第三方枪包的火光自动贴合枪口，不必再逐枪手调 `offsetX/Y/Z`；不想要可用 `autoScaleFromDisplay: false` 关掉。
- 新增：多枪口（双持/多管）支持。模型里的 `muzzle_flash2`、`muzzle_flash3`… 额外枪口骨骼开火时同步出火，闪电鹰 X 双持、RSH12 双持、wingshooter 双持模式等开箱即用。
- 修复：切枪后延迟任务泄漏，导致该枪枪焰再也不触发（切走再切回来打不响火）。
- 修复：整目录删除枪焰贴图后，残留配置仍引用缺失贴图导致紫黑格。
- 修复：`/muzzleflash reload` 现在与 F3+T 一致，会完整重载贴图与配置。
- 修复：多人游戏时第三方玩家的枪模不再干扰本地枪焰状态。
- 修复：`disableFlash` 配置真正生效（此前只在文档里存在）。

### v1.0.0

- 首个版本：多帧枪焰动画、按枪配置、自动缩放、热重载。

---

如果觉得好用，欢迎给个 Star，也欢迎提 Issue 和 Pull Request。

Agent太好用了，就是太费token了(200m)
