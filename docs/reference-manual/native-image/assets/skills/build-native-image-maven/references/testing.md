# Native Image Testing (Maven)

## Contents
- Required dependencies
- Plugin configuration for testing
- Running native tests
- Collecting test metadata
- Skip options
- Troubleshooting test failures

## Required Dependencies

```xml
<dependencies>
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.0</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```


Also ensure you configure `maven-surefire-plugin` 3.0+:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version><version-3.0+></version>
</plugin>
```


## Plugin Configuration for Testing


Add the `test` goal execution to the native-maven-plugin:

```xml
<execution>
  <id>test-native</id>
  <goals>
    <goal>test</goal>
  </goals>
  <phase>test</phase>
</execution>
```

## Running Native Tests

```bash
./mvnw -Pnative test
```

This compiles a native test binary and executes all discovered JUnit tests.
For plugin-specific testing options, see the [Native Build Tools Maven testing support](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#testing-support).
For debugger-based workflows, see [Debug Native Tests in Maven and Gradle Projects](../../../../guides/debug-native-tests-with-maven-and-gradle.md).

## Collecting Test Metadata


If `nativeTest` fails due to missing reflection or resource metadata, collect it with the tracing agent:

```bash
./mvnw -Pnative -Dagent=true test
./mvnw -Pnative native:metadata-copy
./mvnw -Pnative test
```

Configure the metadata copy to use the test output:

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


## Skip Options

| Flag | Effect |
|------|--------|
| `-DskipTests` | Skip all tests (JVM and native) |
| `-DskipNativeTests` | Run JVM tests only, skip native test compilation and execution |


## Troubleshooting Test Failures


**"No tests found" in native test**
Ensure you declare `maven-surefire-plugin` 3.0+ in your build. If you use Maven Surefire prior to 3.0 M4 or your build forces an older JUnit Platform version, add `junit-platform-launcher` to test dependencies.


**Tests pass on JVM but fail as native image**
Your test framework or dependencies use reflection not captured by metadata. Run `./mvnw -Pnative -Dagent=true test`, then `native:metadata-copy`, then retry.
