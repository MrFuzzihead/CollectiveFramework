# CollectiveFramework

[![](https://jitpack.io/v/MrFuzzihead/CollectiveFramework.svg)](https://jitpack.io/#MrFuzzihead/CollectiveFramework)
[![Build status](https://github.com/MrFuzzihead/CollectiveFramework/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/MrFuzzihead/CollectiveFramework/actions/workflows/build-and-test.yml)
[![Latest release](https://img.shields.io/github/v/release/MrFuzzihead/CollectiveFramework?include_prereleases&sort=semver)](https://github.com/MrFuzzihead/CollectiveFramework/releases/latest)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.7.10-62a34a)](https://minecraft.wiki/w/Java_Edition_1.7.10)
[![Forge](https://img.shields.io/badge/Forge-10.13.4.1614-1e2b4f)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.7.10.html)

A Minecraft mod framework for 1.7.10 that provides shared config, networking, book, and utility systems so other mods do not have to reimplement them.

## What this is

This repository is a **port/continuation of [CollectiveFramework](https://github.com/austinv11/CollectiveFramework)** by austinv11, targeting Minecraft 1.7.10 and the [GTNH](https://www.gtnewhorizons.com/) modpack. It is a library mod first: most of its value is the public API it offers other mods, rather than features reachable from its own entry point.

Instead of each mod hand-writing config loading, server-to-client sync, interactive books, chat translation, NBT tooltips, and various helpers, CollectiveFramework ships them once. It keeps server-to-client config sync, tile-entity sync, and time-control packets on a dedicated network channel, drives the config GUI in game, and exposes utility code for NBT, colour codes, math and geometry, strings and arrays, reflection, and encryption.

See [CODE_REVIEW.md](CODE_REVIEW.md) for the full codebase audit, the bug/optimization/refactor backlog, and the test-coverage notes. The name "The Framework" (the fork this descends from) comes from the fictional project of the same name created by Dr. Holden Radcliffe in _Marvel's Agents of S.H.I.E.L.D_.

## Features

- **Annotation-driven config system** — `@Config` / `@Description` annotations, reflection-based discovery, and an INI file round trip (`ConfigRegistry`). Configs can sync server-to-client on login via `ConfigPacket` and are reverted locally on disconnect.
- **In-game config GUI** — every option is editable from the Mods list → CollectiveFramework → Config (`GuiConfig` / `CfGuiFactory`). In 1.7.10 the config GUI relies on the mod's own `MOD_ID`; a mismatched id makes the GUI list empty and unreadable (see [CODE_REVIEW.md](CODE_REVIEW.md) §1.2).
- **Networking and sync** — a `SimpleNetworkWrapper` channel (`austinv11CF`) carrying `ConfigPacket`, `TimeUpdatePacket`, and tile-entity client/server update packets for [INBTSyncable](src/main/java/com/austinv11/collectiveframework/minecraft/tiles/INBTSyncable.java) tile entities.
- **Book system** — a `Book` / `Entry` / `InteractiveEntry` / `Page` API with `GuiBook`, `ItemBook`, and text/image entries, so other mods can ship interactive, clickable books without building their own GUI. Keyboard interactivity is an FML-bus event, not a Forge-bus one (see [CODE_REVIEW.md](CODE_REVIEW.md) §1.1).
- **NBT and debug tooltips** — optional per-item NBT and misc tooltips to aid mod development, toggled by `Config.debugTooltips`.
- **On-the-fly translation** — client-side translation of chat messages and unlocalized item names through a Yandex provider (`Config.translateChat`, `Config.translateItems`, `Config.yandexApiKey`).
- **Time and key controls** — in-game time buttons (`Config.enableButtonTimeChanging`, `Config.timeChangeRate`, OP/cheats required) and a recently-pressed-keys overlay (`Config.keyOverlay`, Development).
- **Command broadcast relay** — `/say` commands emit server chat events for other mods (`Config.commandBroadcastRelay`, Tweaks).
- **Multithreading helpers** — `SimpleRunnable` / `HeavyCalculations` thread reuse (`Config.restrictThreadUsage`) and a main-thread scheduling helper for off-thread work.
- **Utilities** — NBT helpers, colour codes, math/geometry, array/string utilities, reflection helpers, encryption (`SecretValue`), a downloadable-dependency manager, and version-check helpers (Curse/for GitHub).

## Requirements

| Dependency        | Version                                            |
|-------------------|----------------------------------------------------|
| Minecraft         | 1.7.10                                             |
| Minecraft Forge   | 10.13.4.1614                                       |
| Java              | 8 (bytecode, via Jabel) — Gradle daemon on Java 25 |

There are no hard runtime dependencies beyond Minecraft and Forge. Other mods depend on CollectiveFramework as a library.

## Compiling

Everything is handled by the `com.gtnewhorizons.gtnhconvention` plugin. Load the project in IntelliJ and run `./gradlew build`, or use the wrapper from the command line:

```sh
./gradlew build              # compile, test, format check, coverage
./gradlew test               # unit tests only (Java 8 toolchain)
./gradlew jacocoTestReport   # HTML coverage -> build/reports/jacoco/test/html
./gradlew runServer          # dev server (auto-forwards to runServer25)
./gradlew runClient          # dev client (auto-forwards to runClient25)
```

The Gradle daemon is pinned to Java 25 (see `.java-version`); the mod itself targets Java 8 bytecode (via Jabel). The output jar is placed in `build/libs/`. `./gradlew test` runs the headless JUnit 4 suite (620 tests / 47 classes) and `check` enforces the coverage floor (58% line / 53% branch) via JaCoCo. There are no `@Ignore`d tests left; the suite covers all three layers: 620 unit tests, 85 server-side in-game checks, and 4 client-side in-game checks that render real book entries under Xvfb. See [CODE_REVIEW.md](CODE_REVIEW.md) §11 for the fix history (batches 1–11 closing 68 of 70 findings; the two remaining items are not defects).

### In-game tests

`src/gameTest/java` is a separate, non-shipped test-only mod. It exercises config registration/serialisation, NBT helpers, tile-entity inventories, colour codes, and (client-side) book entry rendering from inside the game:

```sh
mkdir -p run/server && echo "eula=true" > run/server/eula.txt
./gradlew gameTestServer
./gradlew runClient25 -PgameTest    # client half: opens the real config GUI and renders book entries; needs a display (Xvfb in CI)
```

### Headless smoke tests

`-PsmokeTest` makes the game shut itself down after FML reports load complete. This is used to boot a real dedicated server in CI:

```sh
mkdir -p run/server && echo "eula=true" > run/server/eula.txt
./gradlew runServer25 -PsmokeTest
```

The GitHub Actions workflow `.github/workflows/smoke-test.yml` runs the server check and the in-game game tests as required jobs, and the Xvfb client checks as non-blocking jobs. Both upload their logs as artifacts.

## Configuration

All options are in `config/collectiveframework.cfg`, generated on first launch. In game they can be edited from the Mods list → CollectiveFramework → Config. See [CODE_REVIEW.md](CODE_REVIEW.md) for the full option list.

## Contributing

Bug reports and pull requests are welcome. This is a library mod, so changes to the public API (`Config`, `ConfigRegistry`, the book API, the networking packets, and the utility classes) are the most likely to affect other mods — please keep source compatibility in mind.

Please read [CODE_REVIEW.md](CODE_REVIEW.md) before making structural changes: it records the known limitations, the test-coverage state, and what each fix batch changed.

## License

**GPLv2** — see [LICENSE](LICENSE). CollectiveFramework is licensed under the GNU General Public License, version 2.

## Credit

CollectiveFramework was created by [austinv11](https://github.com/austinv11). This port is maintained by [MrFuzzihead](https://github.com/MrFuzzihead) for Minecraft 1.7.10 / GTNH.
