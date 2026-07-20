# ABCDwater -- Auto Water Clutch

A client-side NeoForge mod that automatically places water when falling, saving you from fall damage.

[![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2-blue)](https://neoforged.net)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-green)](https://minecraft.net)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## Features

- Smart physics prediction -- simulates next tick with gravity (0.08/tick^2) + drag
- Dual placement -- look-down right-click first, direct block interaction as fallback
- Auto recycle -- picks up the water source after landing with empty bucket
- Dimension aware -- disabled in the Nether (water evaporates)
- Client-side only -- works on any server

## Usage

1. Place the JAR in `.minecraft/mods/`
2. Put a water bucket in your hotbar
3. Fall from >=4 blocks height
4. Water is automatically placed at your predicted landing position and recycled after landing

> Falls under 3.5 blocks are ignored -- no wasted buckets on small drops.

## How It Works

### Fall Detection

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

### Water Recycling

```
Player lands (onGround=true) ->
  |- Find empty bucket -> switch to its slot
  |- useItemOn(waterSourceBlock, Direction.UP)
  |   -> Client returns PASS (doesn't simulate bucket filling locally)
  |   -> But ServerboundUseItemOnPacket IS sent to server
  |   -> Server processes the water pickup! Bucket fills with water
  |- Check if water block is gone (server picked it up)
  '- Retry for up to 40 ticks (~2 seconds)
```

## Technical Details

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

## Build

```bash
./gradlew build
# -> build/libs/ABCDwater-<version>.jar
```

Requires JDK 25+.

## Release Files

| File | Description |
|------|-------------|
| ABCDwater-<version>.jar | Mod JAR -> place in .minecraft/mods/ |
| ABCDwater-src-<version>.zip | Source code |

## License

MIT
