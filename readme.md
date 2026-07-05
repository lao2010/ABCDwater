# ABCDwater

**Auto MLG Water Clutch** · *自动落地水*

A client-side NeoForge mod that automatically places water when you fall, saving you from fall damage. Just keep a water bucket in your hotbar.

[![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2-1e6f9f)](https://neoforged.net)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-green)](https://minecraft.net)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

---

## Features

- **Auto MLG** — Detects falls ≥3.5 blocks, scans for ground, places water automatically
- **Dual placement** — Tries look-down right-click first; falls back to direct block interaction
- **Smart detection** — Won't waste water in the Nether, or if landing in water/lava already
- **Auto recycle** — After landing, picks the water source back up with the empty bucket
- **Motion-aware** — Works even with horizontal mid-air movement (running jumps, cliff edges)
- **No slot hassle** — Water bucket can be in any hotbar slot (or already held)
- **Client-side only** — Works on any server, no special permissions required

## Usage

1. Place the JAR in `.minecraft/mods/`
2. Put a water bucket anywhere in your hotbar (slots 1–9)
3. Fall from ≥4 blocks height
4. ⛲ Water is automatically placed at your predicted landing position
5. 🪣 The water is recycled back into a bucket after you land

> Falls under 3.5 blocks are ignored — no wasted buckets on small drops.

## How It Works

```
ClientTickEvent.Post
  │
  ├─ Skip if: on ground / creative / spectator / flying / elytra / in water
  ├─ Skip if: fallDistance < 3.5
  ├─ Skip if: in the Nether
  │
  ├─ Scan downward (up to 16 blocks) for ground
  │   ├─ Water/lava below? → Skip, no clutch needed
  │   └─ Solid ground? → Calculate eye-to-face distance
  │
  ├─ Is ground within reach (≤4.5 blocks)? → Proceed
  │
  ├─ Switch to water bucket → look straight down → useItem()
  │   └─ Sends ServerboundUseItemPacket with pitch=90°
  │       → Server raytraces: eye → ground face → places water
  │
  ├─ If useItem fails → useItemOn() directly on the ground block
  │
  └─ On landing → wait 3 ticks → use empty bucket on water source
                    → Water recycled, ready for next fall
```

### Why two placement methods?

| Method | Mechanism | Succeeds when |
|--------|-----------|---------------|
| `useItem` (look-down) | Server raytraces from eye → block face | Face within 4.5 blocks |
| `useItemOn` (fallback) | Server checks distance to block center | Center within 4.5 blocks |

The raytrace distance (face) is shorter than the block-interaction distance (center), so `useItem` triggers first. If it misses (e.g., server-side position mismatch), `useItemOn` catches it.

## Technical Details

| Aspect | Value |
|--------|-------|
| **Mod Loader** | NeoForge |
| **Minecraft** | 26.1.2 |
| **Java** | 25 |
| **Side** | Client only |
| **Dependencies** | None |
| **Event** | `ClientTickEvent.Post` |
| **API** | `MultiPlayerGameMode.useItem()` / `useItemOn()` |

## Building from Source

```bash
./gradlew build
# Output: build/libs/ABCDwater-<version>.jar
```

Requires JDK 25+.

## Release Files

| File | Description |
|------|-------------|
| `ABCDwater-<version>.jar` | Mod JAR — place in `.minecraft/mods/` |
| `ABCDwater-src-<version>.zip` | Complete source code |

## License

MIT
