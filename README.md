# Simply Light - Minecraft 1.21.11 Fabric Port

[![Build](https://github.com/MikeOnDeck/simplylight/actions/workflows/build.yml/badge.svg)](https://github.com/MikeOnDeck/simplylight/actions/workflows/build.yml)

**Unofficial Fabric port to Minecraft 1.21.11** by MikeOnDeck

Original mod by **Flanks255**: https://github.com/Flanks255/simplylight

## About

This is an unofficial port of Simply Light to Minecraft 1.21.11 Fabric, maintaining full compatibility with existing 1.20.1 worlds. All 40 original block IDs and blockstate properties are preserved.

### Features
- Simple decorative light sources in 16 colors
- Full blocks, slabs, panels, wall lamps, bulbs, rods, edge lights, and lamp posts  
- Inverted lamps (redstone-controlled)
- Waterloggable blocks
- **World Compatible**: Load your 1.20.1 Simply Light worlds on 1.21.11 without losing any placed blocks

### Port Status (1.21.11)

✅ **Working:**
- All 40 block types with original IDs
- Block placement, rotation, and properties
- Redstone interaction (inverted lamps)
- Waterlogging
- Recipes and loot tables
- Creative tab

⚠️ **Disabled Features:**
- Edge Light editor GUI (GUI API changed significantly, not critical)
- Block helmet equipping (Equippable API changed)
- Data generation (API changed, existing JSONs work)

## Installation

**Requirements:**
- Minecraft 1.21.11
- Fabric Loader 0.19.5+
- Fabric API 0.141.6+1.21.11

Download the latest JAR from [Releases](https://github.com/MikeOnDeck/simplylight/releases) and place in your `mods/` folder.

## Migrating from 1.20.1

⚠️ **CRITICAL: Always back up your world first!**

1. **Backup your world folder** (copy it somewhere safe)
2. Install Simply Light 1.21.11 Fabric
3. Launch Minecraft 1.21.11 with Fabric
4. Open your world
5. Verify all Simply Light blocks loaded correctly

If blocks are missing, restore from backup and report the issue.

## Building from Source

```bash
git clone https://github.com/MikeOnDeck/simplylight.git
cd simplylight
git checkout 1.21.11-fabric
./gradlew :fabric:build
```

Built JAR will be in `fabric/build/libs/`

## Credits

- **Original Author**: Flanks255
- **1.21.11 Port**: MikeOnDeck
- **License**: MIT (see LICENSE file)

## License

MIT License - Copyright (c) 2018 Flanks255

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
