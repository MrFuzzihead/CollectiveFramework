# The Framework 
Release: [![Release](https://travis-ci.org/rolandoislas/TheFramework.svg?branch=master)](https://travis-ci.org/rolandoislas/TheFramework) 
Dev: [![Dev](https://travis-ci.org/rolandoislas/TheFramework.svg?branch=develop)](https://travis-ci.org/rolandoislas/TheFramework)

A Minecraft Mod Framework

Fork of [CollectiveFramework] by austinv11

It adds useful features to aid mod development (NBT tooltips) and features for users such as on the fly chat
 translation.

## Name

"The Framework" gets it name from the fictional project with the same name created by Dr. Holden Radcliffe from the
 television show _Marvel's Agents of S.H.I.E.L.D_.
 
# Download

## CurseForge

Downloads are available on [CurseForge].

## Maven

Bintray hosts the [maven repo] for "The Framework".

```
repositories {
    maven {
        url "https://dl.bintray.com/rolandoislas/The-Framework"
    }
}
dependencies {
    compile "com.rolandoislas.theframework:TheFramework:$VERSION"
}
```

# License

"The Framework" is licensed under the GPLv2.

## Credits

austinv11 for the original [Collective Framework]


[CollectiveFramework]: https://github.com/austinv11/CollectiveFramework
[CurseForge]: https://minecraft.curseforge.com/projects/the-framework
[maven repo]: https://bintray.com/rolandoislas/The-Framework/TheFramework

## Building

```
./gradlew build
```

## Testing

This project ships three layers of automated checks, wired into `./gradlew check` and CI.

| Layer | Command | What it covers |
|---|---|---|
| Unit tests (JUnit 4 + Mockito) | `./gradlew test` | Pure-logic code: config (de)serialisation, the INI file round trip, array/string/math/geometry utilities, reflection helpers, encryption helpers |
| Coverage gate (JaCoCo) | `./gradlew jacocoTestReport` / `jacocoTestCoverageVerification` | HTML report in `build/reports/jacoco/test/html/index.html`; `check` fails if line or branch coverage drops below the floor in `addon.gradle.kts` |
| In-game tests (`gameTest` source set) | `./gradlew gameTestServer` | Boots a real dedicated server, asserts config registration/serialisation, NBT helpers, tile-entity inventories and colour codes from inside the game, then exits with a status code |
| Server smoke test | `./gradlew runServer25 -PsmokeTest` | Boots a real dedicated server, logs `COLLECTIVEFRAMEWORK_SMOKE_TEST_OK` once FML finished loading, then exits |

The `gameTest` source set and `src/test` are never included in the published jar.

### Known-defect tests

`CODE_REVIEW.md` documents the defects found in this codebase. Each one that can be expressed as an
assertion has a matching **`@Ignore`d** test (or a `knownDefect(...)` check in the in-game test) that
states the *correct* behaviour and names the review section. To fix a defect: implement the fix,
delete the `@Ignore`, and the test becomes the regression guard.
