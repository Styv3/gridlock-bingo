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

- `craft_item`
- `place_block`
- `break_block`
- `consume_item`
- `kill_entity`
- `item_frame_item`
- `damage_from_entity`

If no `detector` is present, the plugin tries conservative heuristics from the description. It intentionally skips ambiguous goals instead of awarding false positives.
