# Native Image Testing (Gradle)

## Contents
- JUnit dependencies
- Running native tests
- Custom test suites

## JUnit Dependencies

```groovy
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.1'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.1'
    testImplementation 'junit:junit:4.13.2'
}

test {
    useJUnitPlatform()
}
```


## Running Native Tests

```bash
./gradlew nativeTest
```


The output binary is located at:
`build/native/nativeTestCompile/<imageName>`
For plugin-specific testing options, see the [Native Build Tools Gradle testing support](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#testing-support).
For debugger-based workflows, see [Debug Native Tests in Maven and Gradle Projects](../../../../guides/debug-native-tests-with-maven-and-gradle.md).



## Custom Test Suites


Register additional test binaries for integration tests or other test source sets:

```groovy
graalvmNative {
    registerTestBinary("integTest") {
        usingSourceSet(sourceSets.integTest)
        forTestTask(tasks.named('integTest'))
    }
}
```

This creates two tasks:
- `nativeIntegTestCompile` — builds the native test binary
- `nativeIntegTest` — runs it
