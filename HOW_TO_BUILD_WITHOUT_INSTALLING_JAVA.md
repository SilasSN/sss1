# Attempt to build PercentBoosts 26.3 online

**This source is NOT compiled or in-game tested.** GitHub Actions will attempt a real NeoForge 26.3 build. It may fail with Java API errors, which will need correcting before there is a playable JAR.

1. Sign into GitHub and make a **PRIVATE** empty repository (for personal use).
2. On your PC unzip this package. On the GitHub repository page choose **Add file -> Upload files**. Upload the CONTENTS of this folder, preserving the `src/` and `.github/workflows/` structure. GitHub web upload may not preserve hidden `.github` folders easily; if the workflow doesn't appear, upload `.github/workflows/build-percentboosts.yml` using **Add file -> Create new file** and paste its contents, or use Git on your PC.
3. Once committed on the default branch, click **Actions -> Build PercentBoosts 26.3 (experimental) -> Run workflow**, or wait for the push build.
4. If successful, open the workflow run and download the artifact named `percentboosts-26.3-jar-UNTESTED-IN-GAME`. Extract the zip artifact; it contains the .jar in build/libs.
5. If failed, open the run, download `percentboosts-build-log` and send the log back here. Do not treat a failed build as a playable mod.

The workflow installs Java 25 and Gradle 9.2.1 on its own runner and downloads NeoForge dependencies during the build, so you don't need these installed on your own PC for this method.

## Local alternative (Windows)
Install Java 25 and Gradle compatible with the NeoForge MDK, then run `gradle build` in the project root; a successful build outputs under `build/libs/`. This is *not* guaranteed to succeed with the current untested reconstruction. Use a world backup when experimenting.
