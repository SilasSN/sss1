#!/usr/bin/env python3
"""Static project smoke checks, NOT a replacement for compiling and testing in Minecraft."""
from pathlib import Path
import json
import re

root = Path(__file__).resolve().parents[1]
source = root / "src/main/java/com/example/townfolk"
java_files = ["Townfolk", "TownHallKitItem", "TownManager", "TownController", "WorkerAi", "TownStorage"]
for name in java_files:
    content = (source / (name + ".java")).read_text()
    assert content.count("{") == content.count("}"), f"brace count mismatch: {name}"
    assert "package com.example.townfolk;" in content
for p in (root / "src/main/resources").rglob("*.json"):
    json.loads(p.read_text())

manager = (source / "TownManager.java").read_text()
warehouse = (source / "TownStorage.java").read_text()
controller = (source / "TownController.java").read_text()
ai = (source / "WorkerAi.java").read_text()
assert "BASE_CAPACITY = 4" in manager and "MAX_LEVEL = 10" in manager
assert "SLOTS_PER_TIER = 27" in warehouse and "MAX_STORAGE_LEVEL = 10" in warehouse
assert "current >= TownManager.level(world, home)" in warehouse
assert "tf_storage_level" in warehouse and "tf_storage_level" in manager
assert "TownStorage.insert(level, home, stack)" in ai
assert "TownStorage.consumeFood(level, home)" in ai
assert "TownStorage.charge(world, home, cost)" in controller
assert "storageupgrade" in controller and "hallupgrade" in controller
positions = re.findall(r"\{\s*(-?\d+),\s*(\d+),\s*(-?\d+)\s*\}", warehouse.split("BARREL_LOCATIONS")[1].split("};")[0])
assert len(positions) == 9 and len(set(positions)) == 9
assert all(-3 <= int(x) <= 3 and -3 <= int(z) <= 3 and int(y) == 1 for x, y, z in positions)
assert not {tuple(map(int,p)) for p in positions}.intersection({(2,1,2),(0,1,2),(-2,1,2)})
print("STATIC SMOKE TESTS PASSED: metadata, project JSON, storage tiers, deposits and upgrade hooks")
print("IMPORTANT: This does not verify Minecraft/NeoForge compilation or runtime behavior.")
