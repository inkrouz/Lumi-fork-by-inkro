# Project Notes

## What this is
- Lumi — Minecraft Bedrock server software (hard-fork of Nukkit-MOT). Gradle/Kotlin project.
- This branch is `native-light`: adds a C++ JNI block-light flood fill (`native/light.cpp`) with a Java fallback (`NativeLight.java`).
- Remote `origin` = https://github.com/inkrouz/Lumi-fork-by-inkro.git (auth via `~/.git-credentials`).

## Native light build
- Compile the native lib from `native/light.cpp` and place it in resources:
  - x86_64: `src/main/resources/native/linux-x64/liblumilight.so`
  - ARM64: `src/main/resources/native/linux-aarch64/liblumilight.so`
- Compile command (adjust JDK include path; use `-static-libstdc++ -static-libgcc` like CI for portability):
  `g++ -shared -fPIC -O2 -std=c++17 -static-libstdc++ -static-libgcc -I$JAVA_HOME/include -I$JAVA_HOME/include/linux native/light.cpp -o <target>.so`
- `NativeLight.java` auto-detects `os.arch` and loads the matching lib; falls back to Java if missing.
- The ARM64 `.so` committed at `linux-aarch64/liblumilight.so` was built on Alpine (musl libc). It only loads on musl/glibc-free ARM64 systems; on glibc systems (Ubuntu/Debian) rebuild it there with the command above. The x86_64 lib must be built on an x86_64 machine.

## Build / run
- Requires Java 21+. Build with `./gradlew build` (shadowJar → `Lumi.jar`). Run: `java -jar Lumi.jar`.
- **Java cannot run in the PolyDev sandbox** (`Failed to mark memory page as executable` — proot restriction). Full Gradle build must be done on a real machine.
- Fixed compile error: `LevelDBConstantsTest.java` had a missing `;` after `package cn.nukkit.level.format.leveldb` (now compiles). The CI workflow still deletes non-PaletteRemapTest tests by design.

## Potion stacking
- Potions stack to 64: `getMaxStackSize()` = 64 in `ItemPotion`/`ItemPotionSplash`/`ItemPotionLingering`.
- Client side (protocol >= 1.21.60): `RuntimeItemMapping` marks potions `componentBased=true` and injects `components.item_properties.max_stack_size=64`. Vanilla potions are `componentBased=false`, so the client would otherwise ignore server components and fall back to max stack 1.
- Creative menu shows full stacks: `CreativeItemRegistry.getCreativeItemData` sets count = maxStackSize for stackable items.
