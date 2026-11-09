// Extra build logic, applied on top of build.gradle.kts (see dependencies.gradle for the main dependency list).
// Accessors such as testImplementation(...) are not generated for addon scripts, so configurations are
// addressed by name when needed.
//
// This file mirrors the test/coverage/game-test setup used by the MrFuzzihead/OpenPeripheral fork so that both
// repos can be reasoned about (and fixed) the same way.

import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

// Coverage is produced by every `test` run. `./gradlew jacocoTestReport` only assembles the HTML/XML report,
// `./gradlew jacocoTestCoverageVerification` additionally enforces the floor defined below.
apply(plugin = "jacoco")

project.extensions.configure(JacocoPluginExtension::class.java) {
    toolVersion = "0.8.16"
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class.java) {
        isEnabled = true
        // Only the main source set is reported on. Instrumenting the test classes as well injects a
        // synthetic `$jacocoData` field into every fixture class, which the framework's own reflection-based
        // config discovery then treats as a config entry (see CODE_REVIEW §3.18). Keeping fixtures clean keeps
        // the tests deterministic and representative of a real, un-instrumented user class.
        excludes = listOf(
            "com.austinv11.collectiveframework.*Test",
            "com.austinv11.collectiveframework.*Test$*",
        )
    }
}

val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
val mainSourceSet = sourceSets.getByName("main")

// A test-only mod + in-game assertions. It is compiled into its own source set and only added to the
// run classpath when -PgameTest (or the gameTestServer task) is used, so it never ships in the mod jar.
val gameTestSourceSet = sourceSets.create("gameTest")
configurations.getByName(gameTestSourceSet.compileClasspathConfigurationName)
    .extendsFrom(configurations.getByName("compileClasspath"))
configurations.getByName(gameTestSourceSet.runtimeClasspathConfigurationName)
    .extendsFrom(configurations.getByName("runtimeClasspath"))
gameTestSourceSet.compileClasspath += mainSourceSet.output
gameTestSourceSet.compileClasspath += mainSourceSet.compileClasspath
gameTestSourceSet.runtimeClasspath += mainSourceSet.output
gameTestSourceSet.runtimeClasspath += mainSourceSet.runtimeClasspath
// The game-test mod is plain Java 8. Leaving Jabel's compiler plugin on this source set's classpath
// makes javac auto-load it through ServiceLoader, which then needs ByteBuddy's agent (not present here).
gameTestSourceSet.compileClasspath = gameTestSourceSet.compileClasspath
    .filter { !it.name.contains("jabel") }

tasks.named("check").configure {
    dependsOn(gameTestSourceSet.classesTaskName)
}

val isGameTestRun = project.hasProperty("gameTest") ||
    gradle.startParameter.taskNames.any { it.contains("gameTestServer") }
if (isGameTestRun) {
    tasks.withType<JavaExec>().configureEach {
        if (name.startsWith("runServer") || name.startsWith("runClient")) {
            classpath += gameTestSourceSet.output
            systemProperty("collectiveframework.gameTest", "true")
            // The game test registers a block, which is then recorded in level.dat. A later run *without* the
            // game test source set (the smoke test) sees it as missing and FML blocks on an interactive
            // "/fml confirm" prompt for ten minutes. Answer it up front so no run can ever hang on it.
            systemProperty("fml.queryResult", "confirm")
        }
    }
}

tasks.register("gameTestServer") {
    group = "verification"
    description = "Boots a dedicated server, runs the in-game game test mod, and exits with its result"
    if (isGameTestRun) dependsOn(tasks.named("runServer25"))
}

fun jacocoClassDirs() = project.files(mainSourceSet.output.classesDirs).asFileTree.matching {
    // Generated token holder only contains build version constants; measuring it would be noise.
    exclude("com/austinv11/collectiveframework/Tags.class")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates HTML and XML JaCoCo coverage reports from the unit test run"
    dependsOn(tasks.named("test"))

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    classDirectories.setFrom(jacocoClassDirs())
    sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    group = "verification"
    description = "Fails the build when unit test line coverage drops below the configured floor"
    dependsOn(tasks.named("test"))

    classDirectories.setFrom(jacocoClassDirs())
    sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                // Deliberately conservative: this is a regression guard, not a target. Most of this mod is
                // Minecraft-coupled (GUIs, packets, tile entities) and only reachable from the gameTest source
                // set, which JaCoCo does not instrument. Current baseline: 38.2% line / 42.6% branch /
                // 42.4% instruction. Raise these as the pure-logic surface grows.
                minimum = "0.37".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.41".toBigDecimal()
            }
        }
    }
}

// Make the normal `check` lifecycle enforce the floor, so CI catches coverage regressions.
tasks.named("check").configure {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// Mockito 1.x/4.x generates mocks with a shaded cglib / ByteBuddy agent that only works up to Java 8. The mod
// targets Java 8 anyway, so run the tests on a Java 8 toolchain instead of the Gradle daemon's JVM.
tasks.withType(Test::class.java).configureEach {
    javaLauncher = project.extensions.getByType(JavaToolchainService::class.java)
        .launcherFor {
            languageVersion = JavaLanguageVersion.of(8)
        }
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// `./gradlew runServer25 -PsmokeTest` (or runClient25) makes the forked game exit itself once FML finishes
// loading. CI uses this to boot a real dedicated server without an operator. See SmokeTestHook.
if (project.hasProperty("smokeTest")) {
    val smokeTestDelay = (findProperty("smokeTestDelay") as String?) ?: "2000"
    tasks.withType(JavaExec::class.java).configureEach {
        systemProperty("collectiveframework.smokeTest", "true")
        systemProperty("collectiveframework.smokeTest.delay", smokeTestDelay)
        // Never block on FML's missing-block prompt; a CI job has no operator to answer it.
        systemProperty("fml.queryResult", "confirm")
    }
}
