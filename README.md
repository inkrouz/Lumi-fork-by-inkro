<h1 align="center">
    ✨ Ruppim
</h1>
<p align="center">Высокопроизводительное расширяемое ядро для серверов Minecraft Bedrock</p>

## О проекте
**Ruppim** — высокопроизводительное ядро для серверов Minecraft Bedrock. Разработчик и владелец — **[inkrouz](https://github.com/inkrouz)**.
Это форк [Lumi](https://github.com/KoshakMineDEV/Lumi), который, в свою очередь, является hard-форком [Nukkit-MOT](https://github.com/MemoriesOfTime/Nukkit-MOT/). Ruppim сохраняет API и совместимость с плагинами Lumi и добавляет собственные улучшения.

### Что Ruppim добавляет к Lumi
1. **Стак зелий до 64** (лимит на стороне сервера), включая взрывные и туманные зелья
2. **Встроенный ИИ мобов**: блуждание, бегство, преследование, ближние и дальние атаки, взрыв крипера, перепрыгивание препятствий. Работает для ванильных мобов; мобы, NPC и боссы из плагинов сохраняют своё поведение. Отключается флагом `-Druppim.mobai=false`
3. **Исправления дюпов и потери предметов**: multishot, ткацкий станок, зачарование, стаки в зельеварке, воронки, проверки инвентаря
4. **Серверная проверка зачарований**
5. Собственное оформление Ruppim и своя сборка (GitHub Actions, готовый `.jar` публикуется в Releases)

### Что унаследовано от Lumi
1. Поддержка клиентов 1.20.0 – 1.26.60 (минимальный протокол задаётся в конфиге)
2. Больше ванильных возможностей
3. Поддержка кастомных блоков и кастомных зачарований
4. Улучшенная система AntiXray
5. Улучшенный API (эффекты, еда, снаряды и т. д.)
6. Поддержка Mixin (изменение любого метода) через [Ignite](https://github.com/KoshakMineDEV/ignite-lumi)
7. Поддержка мобов через [MobPluginLumi](https://github.com/LuminiaDev/MobPluginLumi)

### Как установить?
1. Установите Java 21 или новее (рекомендуется Azul Zulu 21)
2. Скачайте последний `.jar` на странице [Releases](https://github.com/inkrouz/Lumi-fork-by-inkro/releases) этого репозитория
3. Запустите: `java -jar <скачанный-файл>.jar`

### Разработка плагинов
Ruppim совместим по API с Lumi, поэтому плагины для Lumi и [шаблон ExamplePlugin](https://github.com/LuminiaDev/ExamplePlugin) подходят как отправная точка.

### Сборка из исходников
```
./gradlew build
```
Workflow `Build Project` собирает `.jar` при каждом push. Архив с исходниками делается отдельным workflow `Source Archive`.

### Нужна помощь?
Создайте issue в этом репозитории или напишите владельцу: [inkrouz](https://github.com/inkrouz).

## Благодарности
[<img src="https://raw.githubusercontent.com/CloudburstMC/Nukkit/master/.github/images/logo.png" width="18"/>]() [Nukkit](https://github.com/CloudburstMC/Nukkit)  
[<img src="https://github.com/AllayMC/Allay/raw/master/docs/assets/logo/allay-chan-40x.png" width="18"/>]() [AllayMC](https://github.com/AllayMC/Allay)  
[<img src="https://avatars.githubusercontent.com/u/62042238?s=200&v=4" width="18"/>]() [Nukkit-MOT](https://github.com/MemoriesOfTime/Nukkit-MOT)  
[<img src="https://avatars.githubusercontent.com/u/99014792?s=200&v=4" width="18"/>]() [PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX)  
[<img src="https://avatars.githubusercontent.com/u/20168691?s=200&v=4" width="18"/>]() [EaseCation Nukkit](https://github.com/EaseCation/Nukkit)  
[<img src="https://avatars.githubusercontent.com/u/26197131?v=4" width="18"/>]() [NukkitPetteriM1Edition](https://github.com/PetteriM1/NukkitPetteriM1Edition)
