# Project Notes

## What this is
- Lumi — Minecraft Bedrock server software (hard-fork of Nukkit-MOT). Gradle/Kotlin project.
- This branch is `native-light`: adds a C++ JNI block-light flood fill (`native/light.cpp`) with a Java fallback (`NativeLight.java`).
- Remote `origin` = https://github.com/inkrouz/Lumi-fork-by-inkro.git (auth via `~/.git-credentials`).

## Native light build
- Compile the native lib from `native/light.cpp` and place it in resources:
  - x86_64: `src/main/resources/native/linux-x64/liblumilight.so`
  - ARM64: `src/main/resources/native/linux-aarch64/liblumilight.so`
- Compile command (adjust JDK include path):
  `g++ -shared -fPIC -O2 -I$JAVA_HOME/include -I$JAVA_HOME/include/linux native/light.cpp -o <target>.so`
- `NativeLight.java` auto-detects `os.arch` and loads the matching lib; falls back to Java if missing.
- The ARM64 (.so is committed at `linux-aarch64/liblumilight.so` (built in this sandbox). The x86_64 lib must be built on an x86_64 machine.

## Build / run
- Requires Java 21+. Build with `./gradlew build` (shadowJar → `Lumi.jar`). Run: `java -jar Lumi.jar`.
- **Java cannot run in the PolyDev sandbox** (`Failed to mark memory page as executable` — proot restriction). Full Gradle build must be done on a real machine.

## Potion stacking
- Potions stack to 64 (already in this branch): `getMaxStackSize()` = 64 in `ItemPotion`/`ItemPotionSplash`/`ItemPotionLingering`, plus `max_stack_size` component injected in `RuntimeItemMapping`.
