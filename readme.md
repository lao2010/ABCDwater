# ABCDwater — 自动落地水

[English](#english) | [中文](#中文)

---

<a id="中文"></a>

## 自动落地水

从高处坠落时自动在脚下放水，免除摔伤伤害。只需在快捷栏放一桶水，跳崖时自动触发。

[![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2-blue)](https://neoforged.net)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-green)](https://minecraft.net)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

### 功能

- 智能预测 -- 模拟下一 tick 物理运动，仅在有摔伤风险时触发
- 双重放水 -- 先俯视右键，失败则直接交互方块
- 自动回收 -- 落地后自动用空桶收回水源，桶可重复使用
- 维度适配 -- 下界自动禁用，水中不浪费桶
- 纯客户端 -- 任何服务器都能用

### 用法

1. JAR 放入 `.minecraft/mods/`
2. 快捷栏放一桶水
3. 从 =4 格高度跳下
4. 水自动放在落地点，落地后自动回收

> 3.5 格以下不触发，日常跳跃不干扰。

### 工作原理

每 tick 检测坠落状态 -> 模拟下一 tick 物理 -> 预测落地位置 -> 计算摔伤 -> 放水 -> 落地后回收。
详细流程见下方英文版。

### 技术细节

| 项目 | 值 |
|--------|------|
| 模组加载器 | NeoForge |
| Minecraft | 26.1.2 |
| Java | 25 |
| 运行侧 | 仅客户端 |
| 依赖 | 无 |

---

<a id="english"></a>

## Auto MLG Water Clutch

A client-side NeoForge mod that automatically places water when falling, saving you from fall damage. Just keep a water bucket in your hotbar.

[![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2-blue)](https://neoforged.net)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-green)](https://minecraft.net)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

### Features

- Smart physics prediction -- simulates next tick with gravity (0.08/tick^2) + drag
- Dual placement -- look-down right-click first, direct block interaction as fallback
- Auto recycle -- picks up the water source after landing with the empty bucket
- Dimension aware -- disabled in the Nether (water evaporates)
- Client-side only -- works on any server

### Usage

1. Place the JAR in `.minecraft/mods/`
2. Put a water bucket in your hotbar
3. Fall from >=4 blocks height
4. Water is placed at the predicted landing position and recycled after landing

> Falls under 3.5 blocks are ignored -- no wasted buckets on small drops.

### How It Works

```
ClientTickEvent.Post ->
  |- Skip: on ground / creative / flying / elytra / in water / Nether
  |- Skip: fallDistance < 3.5
  |- Physics prediction:
  |   nextMY = (motionY - 0.08) x 0.98
  |   nextY  = currentY + nextMY
  |   check if block below nextY is solid
  |- Landing predicted? -> calculate cumulative fallDistance
  |   fallDistance > 3.5? -> clutch!
  |
  |- Method 1: useItem + look down
  |   -> ServerboundUseItemPacket (pitch=90)
  |   -> Server raytrace: eye -> ground face -> place water
  |   (fallback)
  |- Method 2: useItemOn on ground block
  |   -> ServerboundUseItemOnPacket (blockPos, Direction.UP)
  |
  '- Remember water position for recycling
```

#### Water Recycling

```
Player lands (onGround=true) ->
  |- Find empty bucket -> switch to its slot
  |- useItemOn(waterSourceBlock, Direction.UP)
  |   -> Client returns PASS (doesn't simulate bucket filling locally)
  |   -> But ServerboundUseItemOnPacket IS sent to server
  |   -> Server processes the water pickup! Bucket fills with water
  |- Check if water block is gone -> recycled!
  '- Retry for up to 40 ticks (~2 seconds)
```

### Technical Details

| Aspect | Value |
|--------|-------|
| Mod Loader | NeoForge |
| Minecraft | 26.1.2 |
| Java | 25 |
| Side | Client only |
| Dependencies | None |
| Event | ClientTickEvent.Post |
| API | MultiPlayerGameMode.useItem() / .useItemOn() |
| Protocol | ServerboundUseItemPacket / ServerboundUseItemOnPacket |

### Build

```bash
./gradlew build
# -> build/libs/ABCDwater-<version>.jar
```

Requires JDK 25+.

### Release Files

| File | Description |
|------|-------------|
| ABCDwater-<version>.jar | Mod JAR -> place in .minecraft/mods/ |
| ABCDwater-src-<version>.zip | Source code |

### License

MIT
