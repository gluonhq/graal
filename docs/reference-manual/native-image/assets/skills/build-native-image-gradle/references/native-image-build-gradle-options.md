# Gradle Native Image Build Options

## DSL Structure

```groovy
graalvmNative {
    binaries {
        main { /* application binary options */ }
        test { /* test binary options */ }
        all  { /* shared options */ }
    }
}
```

## Binary Properties

| Property      | Type        | Default         | Description |
|-------------- |------------ |-----------------|-------------|
| `imageName`   | String      | project name    | Output executable name |
| `mainClass`   | String      | `application.mainClass` | Main entry point class |
| `debug`       | boolean     | `false`         | Enable debug info (or use `--debug-native`) |
| `verbose`     | boolean     | `false`         | Verbose build output |
| `sharedLibrary` | boolean   | `false`         | Build a shared library |
| `quickBuild`  | boolean     | `false`         | Faster build, lower runtime performance |
| `richOutput`  | boolean     | `false`         | Rich console output |
| `jvmArgs`     | ListProperty| empty           | JVM arguments for native-image builder |
| `buildArgs`   | ListProperty| empty           | Arguments for native-image |
| `runtimeArgs` | ListProperty| empty           | Arguments for the application at runtime |
| `javaLauncher`| Property    | auto-detected   | GraalVM toolchain launcher |


## Binary Configuration

debug = true

**Rename the output binary:**
```groovy
imageName = 'myapp'
```

**Set the entry point:**
```groovy
mainClass = 'com.example.Main'
```

**Enable debug info:**
```groovy
debug = true
// or use --debug-native
```

**Verbose build output:**
```groovy
verbose = true
```

**Faster builds (development):**
```groovy
quickBuild = true
// or use -Ob buildArg for maximum speed
buildArgs.add('-Ob')
```

**Build a shared library instead of an executable:**
```groovy
sharedLibrary = true
```


## Build Failures and Errors


**Increase build memory:**
```groovy
jvmArgs.add('-Xmx8g')
```

**Force runtime initialization for a class:**
```groovy
buildArgs.add('--initialize-at-run-time=com.example.LazyClass')
```

**Force build-time initialization for a class:**
```groovy
buildArgs.add('--initialize-at-build-time=com.example.EagerClass')
```

**Inspect build diagnostics:**
```groovy
buildArgs.add('--diagnostics-mode')
```

## Resources


**Include resource files at runtime:**
```groovy
buildArgs.add('-H:IncludeResources=.*\\.(properties|xml)$')
```


## Runtime Arguments


**Pass arguments to the application at startup:**
```groovy
runtimeArgs.add('--server.port=8080')
```


## Full Example

### Groovy DSL

```groovy
graalvmNative {
    binaries {
        main {
            imageName = 'myapp'
            mainClass = 'com.example.Main'
            verbose = true
            buildArgs.addAll(
                '--initialize-at-run-time=com.example.Lazy',
                '-H:IncludeResources=.*\\.properties$',
                '-O3'
            )
            jvmArgs.add('-Xmx8g')
        }
        test {
            imageName = 'myapp-tests'
        }
        all {
            javaLauncher = javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }
}
```

### Kotlin DSL

```kotlin
graalvmNative {
    binaries {
        named("main") {
            imageName.set("myapp")
            mainClass.set("com.example.Main")
            verbose.set(true)
            buildArgs.addAll(
                "--initialize-at-run-time=com.example.Lazy",
                "-H:IncludeResources=.*\\.properties$",
                "-O3"
            )
            jvmArgs.add("-Xmx8g")
        }
        named("test") {
            imageName.set("myapp-tests")
        }
    }
}
```
