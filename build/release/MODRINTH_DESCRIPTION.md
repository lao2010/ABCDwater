# ABCDwater -- Auto Water Clutch

A client-side NeoForge mod that automatically places water when falling, saving you from fall damage.

## Features

- Smart physics prediction -- simulates next tick with gravity
- Dual placement -- look-down right-click first, useItemOn as fallback
- Auto recycle -- picks up the water source after landing
- Dimension aware -- disabled in the Nether
- Client-side only -- works on any server

## Usage

1. Place the JAR in .minecraft/mods/
2. Put a water bucket in your hotbar
3. Fall from >=4 blocks height
4. Water is placed at the predicted landing position and recycled after landing

## Technical Details

- Mod Loader: NeoForge 26.1.2
- Minecraft: 26.1.2
- Java: 25
- Client-side only
