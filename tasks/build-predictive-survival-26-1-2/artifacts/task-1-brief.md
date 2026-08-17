# Task 1 Brief — Bootstrap exact Fabric 26.1.2, split client sources, unit tests, and CI

Read this first — it is the requirements for Task 1, with exact values to use verbatim.

## Global constraints

- Target exactly Minecraft Java Edition `26.1.2` and Java `25`.
- Use Fabric Loader `0.19.3`, Fabric Loom `1.17-SNAPSHOT`, Fabric API `0.155.2+26.1.2`, and plugin id `net.fabricmc.fabric-loom`.
- Do not add Yarn mappings or the legacy remapping Loom plugin.
- Production code is client-only.
- Treat exact 26.1.2 Minecraft source as authoritative.
- Never treat client-only desync, ghost inventory state, impossible movement, or packet flooding as protection.
- Every implementation task follows red-green-refactor discipline and ends with a focused commit.

## Files

Create the project root files under `projects/predictive-survival-26-1-2/`, `PredictiveSurvivalClient.java`, `ModConstants.java`, `BuildContractTest.java`, and `.github/workflows/predictive-survival-26-1-2-ci.yml`.

## Step 1 — RED

Write the failing mod-id contract test:

```java
@Test void modIdIsStable() { assertEquals("predictive_survival", ModConstants.MOD_ID); }
```

The first CI/build attempt must fail because `ModConstants` does not yet exist. Do not add production Java before this red verification.

## Step 2 — exact build baseline

Use these exact Gradle properties:

```properties
minecraft_version=26.1.2
loader_version=0.19.3
loom_version=1.17-SNAPSHOT
fabric_api_version=0.155.2+26.1.2
junit_version=5.12.2
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.configuration-cache=false
```

The Gradle build must use:

```groovy
plugins { id 'net.fabricmc.fabric-loom' version "${loom_version}" }
loom {
    splitEnvironmentSourceSets()
    mods {
        predictive_survival {
            sourceSet sourceSets.main
            sourceSet sourceSets.client
        }
    }
}
dependencies {
    minecraft "com.mojang:minecraft:${minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${fabric_api_version}"
    testImplementation "net.fabricmc:fabric-loader-junit:${loader_version}"
    testImplementation "org.junit.jupiter:junit-jupiter:${junit_version}"
}
sourceSets.test.compileClasspath += sourceSets.client.output
sourceSets.test.runtimeClasspath += sourceSets.client.output
test { useJUnitPlatform() }
tasks.withType(JavaCompile).configureEach { options.release = 25 }
java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
```

Use the official Fabric `26.1.2` example branch conventions for plugin repositories and Fabric metadata. Do not add mappings.

## Step 3 — GREEN

Add:

```java
public final class ModConstants {
    public static final String MOD_ID = "predictive_survival";
    private ModConstants() {}
}
```

and:

```java
public final class PredictiveSurvivalClient implements ClientModInitializer {
    @Override public void onInitializeClient() {}
}
```

`fabric.mod.json` must set `"environment": "client"` and register the `client` entrypoint.

## Step 4 — verify

The intended verification is:

```bash
./gradlew clean test build
```

In this execution environment the local container cannot reach GitHub/Maven, so GitHub Actions is the authoritative runner. CI must use Java 25, execute the equivalent clean/test/build tasks, and expose enough logs to verify failures and success. Do not claim success without fresh CI evidence.

## Deliverable

A minimal standalone Fabric 26.1.2 client mod scaffold, unit-test baseline, and dedicated CI workflow. No survival-engine behavior beyond the stable mod id/entrypoint belongs in Task 1.
