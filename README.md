# Townfolk 0.3.0 – Minecraft Java 26.3 / NeoForge 26.3

**Status: source-code prototype — NOT compiled or tested in Minecraft.**

This project extends the Townfolk 0.1 prototype with hireable workers, worker upgrades, a Town Hall level cap, real worker food consumption, and **paid warehouse expansions**.

## Mechanics

1. Craft or `/give @s townfolk:town_hall_kit` and right-click **on top of a solid block** in a clear 9×9×5 area.
2. The charter builds a 9×9 town hall, a **chest**, a **bell** and a crafting table. It starts with **one lumberjack, one miner, one farmer and one guard**.
3. **Right-click the Town Hall's bell** for a *clickable chat management menu*. It is not a custom full-screen GUI. Use `/townfolk` if the chat links don't work.
4. Gather real materials, put them in the Town Hall's **chest** or any added storage barrel, then hire/upgrade.
5. Workers collect from actual reachable loaded terrain and crops, place drops in the town warehouse, and use food stored in its real containers.

### Town Hall worker cap

| Town Hall level | Maximum workers | Maximum warehouse tier allowed |
|---:|---:|---:|
| 1 | 4 | 1 |
| 2 | 8 | 2 |
| 3 | 12 | 3 |
| 4 | 16 | 4 |
| 5 | 20 | 5 |
| 6 | 24 | 6 |
| 7 | 28 | 7 |
| 8 | 32 | 8 |
| 9 | 36 | 9 |
| 10 | 40 | 10 |

Upgrade Hall level N -> N+1 by paying **N×(8 emeralds + 16 oak logs + 16 cobblestone)** from town warehouse.

### Warehouse upgrades — NEW IN 0.3

**The storage level is SEPARATE from the Town Hall level**. The Hall's level is a ceiling: you must upgrade the Hall before you can upgrade your warehouse further.

| Warehouse level | Total storage slots | Physical storage blocks |
|---:|---:|---|
| 1 | 27 | Original vanilla chest |
| 2 | 54 | Chest + 1 barrel |
| 3 | 81 | Chest + 2 barrels |
| 4 | 108 | Chest + 3 barrels |
| 5 | 135 | Chest + 4 barrels |
| 6 | 162 | Chest + 5 barrels |
| 7 | 189 | Chest + 6 barrels |
| 8 | 216 | Chest + 7 barrels |
| 9 | 243 | Chest + 8 barrels |
| 10 | 270 | Chest + 9 barrels |

Upgrade warehouse level N -> N+1 by paying **N×(32 oak planks + 8 iron ingots + 4 emeralds)**. These are real vanilla materials and are removed from the **entire connected warehouse** on success.

Click **Upgrade warehouse** in the bell's management chat menu or use `/townfolk storageupgrade`. Each upgrade adds a real **barrel within the Town Hall**, which you can open individually. The first chest and all barrels form one *logical warehouse* for NPC deposits, eating, and payment.

**Important limitation:** These are multiple ordinary vanilla inventories, **not one huge 270-slot chest GUI**. This avoids unstable custom inventory/network code for a first prototype. If players destroy a barrel, it drops its contents normally and its 27 slots become inaccessible until they put a barrel back at the *exact original location*. The save keeps the purchased warehouse level. Do not move or destroy your Town Hall's main chest or controller bell.

### Workers: real-world activities

- **Lumberjack:** navigates toward naturally existing logs and harvests them with the worker's axe.
- **Miner:** mines reachable existing stone/ores and digs natural ground in a small dedicated quarry patch (town + X 8..14, Z 8..14). Never fabricates minerals.
- **Farmer:** harvests real mature wheat, carrots, potatoes and beets, then replants where seed material was obtained from the actual drop.
- **Guard:** moves toward hostile monsters and attacks in melee range. Never deliberately targets players or passive animals.
- **Storage:** actual block loot goes into the available real warehouse slots. Surplus drops onto the ground (nothing is deleted when full).

Workers stay around their Town Hall, avoid altering the Hall footprint, and never deliberately scan unloaded chunks. They can break your nearby existing natural blocks. **Test in a newly created world first.**

### Hiring and upgrades

| Role | Hiring price | Upgrade from worker Lv.N to Lv.N+1 |
|---|---|---|
| Lumberjack | 32 oak logs + 8 emeralds | N×(8 oak logs + 4 emeralds) |
| Miner | 32 cobblestone + 12 emeralds | N×(8 cobblestone + 4 emeralds) |
| Farmer | 32 wheat + 6 emeralds | N×(8 wheat + 4 emeralds) |
| Guard | 16 iron ingots + 16 emeralds | N×(8 iron ingots + 4 emeralds) |

The chat menu offers **Hire** and **Upgrade nearest [job]**. Stand within 8 blocks of the worker to choose the closest qualifying NPC of that job. Individual workers have 10 levels. Upgrades increase extraction speed, their search radius and (at level 6) tool tier. Guards deal more damage at higher level.

A worker's death frees its worker slot when the Town Hall is loaded. Capacity counts saved hired workers rather than merely the villagers currently visible.

### Food and hunger

Each worker starts with 100 hunger, loses about **10 hunger every 60 seconds**, and consumes one food item from the connected warehouse whenever hunger falls to 50 or less. Supported foods include bread, carrots, potatoes, baked potatoes, beetroot and cooked meats. Food is never fabricated.

Workers with no hunger stop resource jobs. Hungry workers work more slowly; starving guards still defend but with lower damage. Farmers deliver their actual harvested food to the warehouse, rather than automatically growing crops from nowhere. If there is no food in storage, workers cannot eat. The Bell menu shows the amount of recognized food available.

## In-game commands (within 32 blocks of your Town Hall)

```
/townfolk
/townfolk status
/townfolk workers
/townfolk food
/townfolk storage
/townfolk hallupgrade
/townfolk storageupgrade
/townfolk hire lumberjack
/townfolk hire miner
/townfolk hire farmer
/townfolk hire guard
/townfolk upgrade lumberjack
/townfolk upgrade miner
/townfolk upgrade farmer
/townfolk upgrade guard
```

Hire, upgrades and warehouse expansion are **founder-only**. Workers can be selected and storage information viewed by visitors.

## Build the JAR on GitHub (without installing Java locally)

1. Create a **new private repository** for Townfolk (different from the PercentBoosts repository).
2. Unzip this archive and upload **all its contents into the repository root**, preserving `.github/workflows/build.yml`, `src/main/java`, `src/main/resources`, `build.gradle`, `settings.gradle` and `gradle.properties`.
3. In GitHub go to **Actions → Build Townfolk 26.3 → Run workflow**. A push to `main` will also run the workflow automatically.
4. If it succeeds, download the artifact **Townfolk-26.3-JAR-UNTESTED** and extract its `.jar` file.
5. Install that JAR only on a Minecraft Java **26.3 + matching NeoForge 26.3** profile, preferably in a fresh test world.
6. If it fails, download the **Townfolk-Build-Log** artifact and send it for correction. The source has **not** been compiled in this environment, so API fixes may still be needed.

## Technical caveats

- Vanilla villagers' native daily schedule, fleeing and pathfinding can interfere with assigned work; guards use an experimental server-side attack loop rather than a fully custom hostile-combat AI.
- Chunk unloading or deliberate removal of the controller bell will interrupt work. No force-loaded terrain and no phantom resources.
- The miner excavates real blocks; it does not dig full cave networks or mine through lava or block entities.
- Full game tests, performance measurements with 40 villagers, multiplayer protection claims, hopper integration, and migration from old save formats **have not been performed**.
- Do not mix the untested mod with your main survival world. **Back up the world first.**

## Project layout

- `TownStorage.java`: expanded warehouse, atomic payments, global item insertion and food retrieval
- `TownController.java`: clickable bell/commands for hall, workers, food and storage
- `TownManager.java`: persistent Town Hall metadata, hiring, spawn and upgrade prices
- `WorkerAi.java`: block-based extraction, vanilla loot, worker nutrition and fighting
- `.github/workflows/build.yml`: GitHub Actions build workflow using Java 25 and Gradle 9.2.1
