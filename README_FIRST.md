# PercentBoosts for Minecraft 26.3 – reconstruction project

**STATUS: SOURCE PROJECT ONLY – NOT A PLAYABLE MOD AND NOT BUILD-TESTED.**

This is a best-effort personal-use reconstruction from the user's `percentboosts-1.0.0-English(1).jar` (NeoForge 1.21.1). This archive DOES NOT include a ready-to-install 26.3 mod JAR. The Java sources have not been compiled against Minecraft 26.3 and may have API mismatches that need correction. No claim is made that the mod currently runs.

## Reconstructed functionality

- Percent item with preserved `percentboosts:percentage_value` data-component identifier.
- Boost Applier item with preserved `percentboosts:applier_balance` data-component identifier.
- Player-killed-mob drop at 25% probability, randomized percent tiers and strength-based bonuses, capped at 100%.
- Points transferred by using the applier while holding a percent item in the opposite hand.
- Player attribute boost selection and item-attribute boost selection using a recreated screen with paging, input, +, -, and free reset buttons; items get 2x the percent effect for the same point cost.
- 29 vanilla attribute types and nine custom attributes.
- XP, crit, undead/boss damage, projectile speed/damage, item-use speed and anvil-cost gameplay hooks.
- Survival-mode server-side checks for point availability; original item textures, recipes and English localization.

## Known limitations / differences from original

- No Minecraft 26.3 game run or compile completed, because this work environment has Java 21, no Gradle installation and no access to the NeoForge dependencies. Import changes in 26.3 may require edits. First Gradle build might fail; use build errors to complete the port.
- The menu has been redesigned, not pixel-identically recovered; it exposes the original actions.
- Older *player* boost records from the 1.21.1 mod are not automatically migrated: this implementation uses a 26.3 data attachment with stable English identifiers. Test in a BACKUP world.
- Other-hand item upgrades are represented as attribute modifiers on the item. Attribute effect on arbitrary item types may differ from the original. In-game validation is outstanding.
- Existing item component data IDs and assets were preserved where possible, but old save compatibility is not guaranteed.
- Original author rights remain with their owner. Personal study/testing only; do not redistribute without authorization.

## How to build on Windows

1. Install **JDK 25** and ensure `java -version` displays Java 25 (or configure Gradle to use it).
2. Download the official **NeoForge 26.3 ModDevGradle MDK** from https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle (Code -> Download ZIP).
3. Copy the Gradle wrapper (`gradlew`, `gradlew.bat`, and the entire `gradle/wrapper` folder) from the official MDK into this reconstructed project root. You can alternatively use Gradle installed on your computer.
4. Open a terminal or PowerShell **inside this reconstructed project folder**. Run `gradlew.bat build` (or `gradle build` if Gradle is installed).
5. If the build succeeds, the compiled output will be under `build/libs/`. Place its JAR into the `mods` directory of a compatible NeoForge 26.3 installation and test **in a disposable world first**.
6. If a build fails, copy the complete error log; it will identify the 26.3 API changes that require correction.

NeoForge 26.3 MDK: https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle
NeoForge documentation: https://docs.neoforged.net/

## Standalone rules test

The mob-strength and percentage-drop calculations can be tested without Minecraft:

```bash
mkdir -p test-output
javac -d test-output src/main/java/com/nom1s/percentboosts/logic/DropMath.java tools/DropMathCheck.java
java -cp test-output DropMathCheck
```

This only tests math, NOT whether the mod builds or works in Minecraft 26.3.

## Experimental cloud build option
You can attempt compilation with GitHub Actions using `.github/workflows/build-percentboosts.yml` (requires a private GitHub repository, internet access, and a GitHub account). See `HOW_TO_BUILD_WITHOUT_INSTALLING_JAVA.md`. Compilation is not guaranteed; **do not assume this is a playable mod**.
