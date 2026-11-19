---
name: build-native-image-gradle
description: Build GraalVM native images using Gradle Native Build Tools. Use this skill to build Java applications with Gradle, configure native-image build.gradle settings, or resolve build or runtime issues.
---

# Gradle Native Image Build

## Prerequisites
- Set `JAVA_HOME` to a GraalVM JDK installation so Gradle Native Build Tools can find `native-image`.
- Do not require `GRAALVM_HOME` in the normal case. Only mention it if the user already relies on it or their environment needs an explicit override.
- Apply the `application`, `java-library`, or `java` plugin along with `org.graalvm.buildtools.native`.


### Plugin Setup
Groovy DSL:
```groovy
plugins {
    id 'application'
    id 'org.graalvm.buildtools.native' version '0.11.1'
}
```

Kotlin DSL:
```kotlin
plugins {
    application
    id("org.graalvm.buildtools.native") version "0.11.1"
}
```


## Build and Run
```bash
./gradlew nativeCompile   # Build to build/native/nativeCompile/
./gradlew nativeRun       # Build and run the native executable
./gradlew nativeTest      # Build and run JUnit tests as a native image
```


## Build or Runtime Failures
If the build fails with class initialization, linking errors, memory issues, or the binary behaves incorrectly at runtime, see [references/native-image-build-gradle-options.md](references/native-image-build-gradle-options.md).


## Native Testing
If `nativeTest` fails or you need to configure native JUnit tests or custom test suites, see [references/testing.md](references/testing.md).


## Missing Reachability Metadata
If a build or runtime error reports missing reflection, resource, serialization, or JNI registrations, see [references/reachability-metadata.md](references/reachability-metadata.md).


## Reference Files
| Topic | File |
|-------|------|
| DSL options and build arguments | [references/native-image-build-gradle-options.md](references/native-image-build-gradle-options.md) |
| Missing reachability metadata | [references/reachability-metadata.md](references/reachability-metadata.md) |
| Native testing | [references/testing.md](references/testing.md) |
