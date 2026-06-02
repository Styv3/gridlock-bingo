# GridlockSimulator Paper Plugin

MVP Paper plugin for Minecraft Java Edition / Paper `1.21.11`.

## Build

```powershell
cd minecraft-plugin
.\gradlew.bat clean build
```

The plugin jar is written to `minecraft-plugin/build/libs/GridlockSimulator-0.1.0-SNAPSHOT.jar`.

## Use

1. Put the jar in your Paper server `plugins/` folder.
2. Start the server once so `plugins/GridlockSimulator/` is created.
3. Copy your exported `gridlock-bingo.json` into `plugins/GridlockSimulator/`.
4. Run:

```text
/gridlock import
/gridlock team create Red
/gridlock team join Red <player>
/gridlock start
```

## Commands

- `/gridlock import [file]`: load exported web JSON from the plugin data folder.
- `/gridlock start`: start automatic detection.
- `/gridlock stop`: pause detection.
- `/gridlock reset`: clear scores and completions, keeping the loaded board.
- `/gridlock board`: show the active 5x5 grid.
- `/gridlock score`: show team scores.
- `/gridlock team create <name>`: create a team.
- `/gridlock team join <name> [player]`: assign a player to a team.
- `/gridlock team leave [player]`: remove a player from a team.
- `/gridlock complete <objective id or name>`: manually claim an objective for your team.
- `/gridlock detectors`: show how many loaded grid objectives have automatic detectors.

## Automatic detection

The plugin supports explicit detector metadata on objectives:

```json
{
  "id": "example",
  "name": "Heavy Lifting",
  "description": "Craft an Iron Block",
  "categoryId": "simple",
  "detector": {
    "type": "craft_item",
    "material": "IRON_BLOCK"
  }
}
```

Supported detector types:

- `totem_resurrect`
- `craft_item`
- `place_block`
- `place_at_height_limit`
- `break_block`
- `consume_item`
- `kill_entity`
- `kill_opponent`
- `item_frame_item`
- `damage_from_entity`
- `smelt_item`
- `obtain_item`
- `shear_entity`
- `use_item_on_entity`
- `hit_opponent_with_item`
- `projectile_hit_entity`
- `projectile_hit_opponent`
- `fishing_rod_opponent`
- `ignite_opponent`

If no `detector` is present, the plugin tries conservative heuristics from the description. It intentionally skips ambiguous goals instead of awarding false positives.

## Gridlock rules implemented

- Official category scoring: Simple `1`, Complex `2`, Team `2`, Opponent `3`, Quest `3/3/4/4/5` based on Quest order in the board.
- Gridline bonuses for completed rows, columns, and diagonals. A Gridline gives the total value of its five squares.
- Duplicate objective slots are scored independently by slot id.
- Total Gridlock early stop when a team exceeds half of the available board points.
- Keep Inventory and disabled weather cycle when the game starts.
- Animal spawn tick delay divided by `10`, monster spawn tick delay multiplied by `5`.
- Loot tweaks for Endermen, Drowned holding Tridents, and Wither Skeleton skull chance.

Guaranteed mob spawns are intentionally not implemented yet.
