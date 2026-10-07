# PVP Bot

A Minecraft Fabric mod that adds AI-controlled PVP bots with different combat modes and difficulty levels.

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## Requirements

- Minecraft Fabric mod loader
- Carpet Mod (required for fake player functionality)

## Modes

The bot supports four different combat modes, each with unique equipment and combat strategies:

### SMP
- **Armor**: Full diamond armor with Protection III and Unbreaking IV
- **Weapons**: Netherite sword and axe with Sharpness III and Unbreaking IV
- **Items**: Shield, water bucket
- **Combat Style**: Melee combat with shield/axe switching, attacks at 90% weapon charge
- **Difficulty Affected**: Yes (affects movement strafing and jump distance)

### Netherite
- **Armor**: Full netherite armor with mixed Protection III/IV and Unbreaking IV
- **Weapons**: Netherite sword and axe with Sharpness V and Unbreaking IV
- **Items**: Shield, water bucket
- **Combat Style**: Melee combat with shield/axe switching, attacks at 90% weapon charge
- **Difficulty Affected**: Yes (affects movement strafing and jump distance)

### Crystal
- **Armor**: Full netherite armor with Protection IV/V, Blast Protection, Feather Falling, and Unbreaking VI
- **Weapons**: Netherite sword with Knockback II
- **Items**: End crystals, obsidian, respawn anchors, glowstone, totems, golden apples, ender pearls
- **Combat Style**: Crystal PVP with end crystal usage, totem switching, and anchor tactics
- **Difficulty Affected**: No

<!-- ### Mace
- **Armor**: Full netherite armor with Protection IV and Unbreaking V, elytra replaces chestplate
- **Weapons**: Mace with Unbreaking V
- **Items**: Ender pearls
- **Combat Style**: Aerial combat with elytra and mace for high-damage vertical attacks
- **Difficulty Affected**: No -->

## Difficulty

Three difficulty levels are available (applies to SMP and Netherite modes only):

### Easy
- Reduced strafing intensity
- Standard jump distance (1.8 blocks)

### Middle (Default)
- Balanced strafing intensity
- Standard jump distance (1.8 blocks)

### Hard
- Increased strafing intensity for better dodging
- Longer jump distance (1.6 blocks for more aggressive approach)

## Commands

### `/pvpbot <type> [spawn]`
Sets the default bot type or spawns a new bot.

**Types:**
- `smp` - SMP mode (diamond armor + netherite weapons)
- `netherite` - Netherite mode (full netherite gear)
- `crystal` - Crystal mode (crystal PVP gear)

**Examples:**
```
/pvpbot smp              # Set default type to SMP
/pvpbot smp spawn        # Spawn an SMP bot at your location
/pvpbot netherite spawn  # Spawn a Netherite bot at your location
```

### `/pvp-target <player|clear>`
Sets the target player for all PVP bots or clears the target.

**Examples:**
```
/pvp-target Steve        # Target player named Steve
/pvp-target clear        # Clear target (bots will attack nearest player)
```

### `/pvpbot difficulty <level>`
Sets the difficulty level for all PVP bots.

**Levels:**
- `easy` - Easier movement patterns
- `middle` - Balanced difficulty (default)
- `hard` - More aggressive movement patterns

**Examples:**
```
/pvpbot difficulty easy      # Set difficulty to easy
/pvpbot difficulty hard      # Set difficulty to hard
```

## Combat Mechanics

### Weapon Charge System
SMP and Netherite modes use a weapon charge system where the bot attacks when the weapon is at least 90% charged, matching Minecraft's normal hit mechanics (90% charge = full damage).

### Critical Hits
Bots perform critical hits when:
- Distance to target is ≤ 1.8 blocks
- Bot is not on the ground
- Bot has positive fall distance

### Shield Blocking
SMP and Netherite bots use shields defensively:
- Block after critical hits
- 2-second shield cooldown
- Automatic shield activation in offhand

### Weapon Switching
SMP and Netherite bots automatically switch between:
- Sword for standard attacks
- Axe when target has a shield (shield breaking)

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
