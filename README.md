<h1 align="center">
    ✨ Ruppim
</h1>
<p align="center">High-performance extensible Minecraft Bedrock server software</p>

## Introduction
**Ruppim** is a high-performance Minecraft Bedrock server core, developed and maintained by **[inkrouz](https://github.com/inkrouz)**.
It is a fork of [Lumi](https://github.com/KoshakMineDEV/Lumi), which is itself hard-forked from [Nukkit-MOT](https://github.com/MemoriesOfTime/Nukkit-MOT/). Ruppim keeps Lumi's API and plugin compatibility and adds its own improvements on top.

### What Ruppim adds on top of Lumi
1. **Stackable potions (up to 64)** with the original vanilla icons for every effect, including splash and lingering potions
2. **Built-in mob AI** (wandering, fleeing, chasing, melee and ranged attacks, creeper explosions, jumping over obstacles) for vanilla mob types; plugin mobs, NPCs and bosses keep their own behaviour. Disable with `-Druppim.mobai=false`
3. **Dupe and item-loss fixes**: multishot, loom, enchanting, brewing stand stacks, hoppers and inventory checks
4. **Enchantment validation** on the server side
5. Ruppim branding and its own build pipeline (GitHub Actions, builds published to Releases)

### Inherited from Lumi
1. Support for 1.20.0 – 1.26.60 clients (the minimum protocol can be set in the config)
2. More vanilla features
3. Custom Blocks and Custom Enchantments support
4. Better AntiXray system
5. Better API (effects, food, projectiles and etc.)
6. Mixin support (changing any method) through [Ignite](https://github.com/KoshakMineDEV/ignite-lumi)
7. Mobs support (with [MobPluginLumi](https://github.com/LuminiaDev/MobPluginLumi))

### How to install?
1. Install Java 21 or higher (recommended is Azul Zulu 21)
2. Download the latest `.jar` from the [Releases](https://github.com/inkrouz/Lumi-fork-by-inkro/releases) page of this repository
3. Run it: `java -jar <downloaded-file>.jar`

### Plugin Development
Ruppim is API-compatible with Lumi, so Lumi plugins and the [ExamplePlugin template](https://github.com/LuminiaDev/ExamplePlugin) work as a starting point.

### Building from source
```
./gradlew build
```
The GitHub Actions workflow `Build Project` builds the jar on every push.

### Need support?
Open an issue in this repository or contact the owner: [inkrouz](https://github.com/inkrouz).

## Credits
[<img src="https://raw.githubusercontent.com/CloudburstMC/Nukkit/master/.github/images/logo.png" width="18"/>]() [Nukkit](https://github.com/CloudburstMC/Nukkit)  
[<img src="https://github.com/AllayMC/Allay/raw/master/docs/assets/logo/allay-chan-40x.png" width="18"/>]() [AllayMC](https://github.com/AllayMC/Allay)  
[<img src="https://avatars.githubusercontent.com/u/62042238?s=200&v=4" width="18"/>]() [Nukkit-MOT](https://github.com/MemoriesOfTime/Nukkit-MOT)  
[<img src="https://avatars.githubusercontent.com/u/99014792?s=200&v=4" width="18"/>]() [PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX)  
[<img src="https://avatars.githubusercontent.com/u/20168691?s=200&v=4" width="18"/>]() [EaseCation Nukkit](https://github.com/EaseCation/Nukkit)  
[<img src="https://avatars.githubusercontent.com/u/26197131?v=4" width="18"/>]() [NukkitPetteriM1Edition](https://github.com/PetteriM1/NukkitPetteriM1Edition)
