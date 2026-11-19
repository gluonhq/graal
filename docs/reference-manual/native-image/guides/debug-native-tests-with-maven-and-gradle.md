---
layout: ni-docs
toc_group: how-to-guides
link_title: Debug Native Tests in Maven and Gradle Projects
permalink: /reference-manual/native-image/guides/debug-native-tests-maven-gradle/
---

# Debug Native Tests in Maven and Gradle Projects

This guide shows you how to set up and debug native tests in Java projects using the [Maven or Gradle plugin for Native Image](https://graalvm.github.io/native-build-tools/latest/index.html).

It focuses on debugging native test executables with GDB (best suited for Linux environments).

Using a small demo application and a JUnit test, the guide explains how to:

- run tests as native executables
- build native test executables with debug information
- troubleshoot missing metadata
- open a native test executable in GDB

## Prepare a Demo Application

### Prerequisites

Make sure you have installed a GraalVM JDK and that `JAVA_HOME` points to it.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

The application and test sources below are the same for both the Maven and Gradle examples.

1. Add the application class in _src/main/java/org/graalvm/example/GreetingFormatter.java_:
    ```java
    package org.graalvm.example;

    public final class GreetingFormatter {

        private GreetingFormatter() {
        }

        public static String format(String name) {
            return "Hello, " + name.strip() + "!";
        }
    }
    ```

2. Add the test class in the test source set, _src/test/java/org/graalvm/example/GreetingFormatterTest.java_:
    ```java
    package org.graalvm.example;

    import org.junit.jupiter.api.Test;

    import static org.junit.jupiter.api.Assertions.assertEquals;

    class GreetingFormatterTest {

        @Test
        void formatsGreeting() {
            assertEquals("Hello, Native Image!", GreetingFormatter.format(" Native Image "));
        }
    }
    ```

    This test is intentionally simple so you can set breakpoints either in the test method or in `GreetingFormatter.format()`.

## Debug Maven Native Tests

The Maven plugin can build a native test executable and run JUnit Platform tests in that executable.

### Configure Native Tests

Add the JUnit dependency, the Surefire plugin, and the Native Build Tools `test` goal to your _pom.xml_:

```xml
<properties>
    <native.maven.plugin.version>0.11.5</native.maven.plugin.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.0.0</version>
        </plugin>
    </plugins>
</build>

<profiles>
    <profile>
        <id>native</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.graalvm.buildtools</groupId>
                    <artifactId>native-maven-plugin</artifactId>
                    <version>${native.maven.plugin.version}</version>
                    <extensions>true</extensions>
                    <executions>
                        <execution>
                            <id>test-native</id>
                            <goals>
                                <goal>test</goal>
                            </goals>
                            <phase>test</phase>
                        </execution>
                    </executions>
                    <configuration>
                        <debug>true</debug>
                        <verbose>true</verbose>
                        <buildArgs>
                            <buildArg>-O0</buildArg>
                        </buildArgs>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

The `test` goal builds a native test executable and runs the discovered JUnit Platform tests in that executable.
Setting `<debug>true</debug>` enables debug information, and `-O0` improves the stepping experience in GDB by disabling compiler optimizations.

### Run the Native Tests

Run the test suite as a native executable:

```shell
mvn -Pnative test
```

The command builds the native test executable, runs the discovered tests, and stores the generated debug artifacts, _gdb-debughelpers.py_, next to that executable.
Keep the build output so you can reopen the same binary in GDB after the test run completes.

### Troubleshoot Missing Metadata

If the native test fails because of missing reflection, resource, serialization, or JNI metadata, add the following agent configuration to the Native Build Tools plugin `<configuration>` block:

```xml
<agent>
    <enabled>true</enabled>
    <metadataCopy>
        <outputDirectory>META-INF/native-image</outputDirectory>
        <merge>true</merge>
        <disabledStages>
            <stage>main</stage>
        </disabledStages>
    </metadataCopy>
</agent>
```

Then collect metadata with the tracing agent and rerun the native test:

```shell
mvn -Pnative -Dagent=true test
mvn -Pnative native:metadata-copy
mvn -Pnative test
```

The `native:metadata-copy` goal copies the collected test metadata into the location configured in `<metadataCopy>`.
This is the recommended path when the JVM test passes but the native test fails during startup or when exercising dynamic features.

### Open the Native Test Executable in GDB

> Native Image debugging with GDB works best on Linux.
On other platforms, especially macOS, it may require extra setup.

With debug info enabled, the build writes the native test executable to _target/native-tests_.
Depending on the platform and debugger support, the build can also generate additional debug-related artifacts in the _target/_ directory.

Open it in GDB:

```shell
gdb target/native-tests
```

Set a breakpoint in the test or in the application code:

```text
(gdb) break GreetingFormatterTest.java:10
(gdb) break GreetingFormatter.java:8
(gdb) run
```


## Debug Gradle Native Tests

The Gradle plugin can build a native test executable and lets you configure that test binary separately from the main application binary.

### Configure Native Tests

Add JUnit support and a `graalvmNative` configuration for the `test` binary in _build.gradle_:

```groovy
plugins {
    id 'java'
    id 'org.graalvm.buildtools.native' version '0.11.5'
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.0'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.0'
}

test {
    useJUnitPlatform()
}

graalvmNative {
    binaries {
        test {
            imageName = 'greeting-tests'
            debug = true
            verbose = true
            buildArgs.add('-O0')
        }
    }
}
```

The `nativeTest` task builds and runs the native test binary.
The `debug = true` setting enables debug information, and `-O0` improves source-level stepping in GDB.

### Run the Native Tests

Compile and run the test suite as a native executable:

```shell
./gradlew nativeTest
```

The native test executable is created in _build/native/nativeTestCompile/greeting-tests_.

If you want to build the binary first and inspect it manually, run:

```shell
./gradlew nativeTestCompile
```

### Troubleshoot Missing Metadata

If the native test fails because of missing reflection, resource, serialization, or JNI metadata, enable the tracing agent for the JVM test run.
You can do that on the command line with `-Pagent=standard`, or declare it in the `graalvmNative` block:

```groovy
graalvmNative {
    agent {
        defaultMode = 'standard'
    }
}
```

Then run the JVM tests with the agent and copy the generated metadata into the test resources:

```shell
./gradlew -Pagent=standard test
./gradlew metadataCopy --task test --dir src/test/resources/META-INF/native-image
./gradlew nativeTest
```

For test-only metadata, copy the generated files into _src/test/resources/META-INF/native-image/_.
If the metadata is needed by the main application instead, copy it into _src/main/resources/META-INF/native-image/_.

### Open the Native Test Executable in GDB

> Native Image debugging with GDB works best on Linux.
On other platforms, especially macOS, it may require extra setup.

Once the test binary is compiled, open it in GDB:

```shell
gdb build/native/nativeTestCompile/greeting-tests
```

Then set breakpoints and start the test binary:

```text
(gdb) break GreetingFormatterTest.java:10
(gdb) break GreetingFormatter.java:8
(gdb) run
```

If you use a custom test suite such as `integrationTest`, register a dedicated test binary for it and build it with the matching compile task, for example `nativeIntegrationTestCompile`.
You can then open the generated binary in GDB and debug it the same way as the default `test` binary.

### Summary

This guide showed how to configure native JUnit tests for Maven and Gradle projects, rebuild them with debug information, collect missing metadata with the tracing agent, and inspect the resulting test binaries in GDB.

Use GDB when you want to inspect a native executable at the symbol and source level, especially on Linux.
Use JDWP when you want to attach a Java debugger to a native executable and work with familiar IDE debugging workflows.

For GDB-based workflows, see:

- [Debug Native Executables with GDB](debug-native-executables-with-gdb.md)
- [Debug Info Feature](../DebugInfo.md)

For JDWP-based workflows, see:

- [Java Debug Wire Protocol (JDWP) with Native Image](../JDWP.md)

For plugin-specific testing options, see the Native Build Tools testing support for [Maven](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#testing-support) and [Gradle](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#testing-support).