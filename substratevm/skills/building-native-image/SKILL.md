---
name: building-native-image
description: Build and troubleshoot GraalVM Native Image applications. Use this skill to build Java applications with GraalVM Native Image, configure CLI options, or resolve build or runtime issues.
---

​# Building Native Image

## Prerequisites
- Set `JAVA_HOME` to a GraalVM distribution if your Java program uses the Native Image SDK. If you do not know the path, ask the user to provide it.
​

## Build and Run
1. Compile your Java file with `javac`.
2. Build the Native Image:
	```bash
	$JAVA_HOME/bin/native-image <app-name>
	```
3. Run the resulting executable:
	```bash
	./app-name
	```
4. If Native Image cannot find your class file, set the classpath explicitly with the `-cp` option.
​

## Troubleshooting

### Reachability Metadata
If you encounter runtime errors related to reflection, JNI, resources, serialization, or dynamic proxies, consult [`references/reachability-metadata.md`](references/reachability-metadata.md) before attempting a fix. Use this reference for:
- `NoClassDefFoundError` or `MissingReflectionRegistrationError`
- `MissingJNIRegistrationError`
- `MissingResourceException` (missing resource bundle)
- Any user question about reflection, JNI, proxies, resources, resource bundles, or serialization in Native Image.
​

## Native Image Options
To configure classpath, optimization level, output name, platform target, monitoring, or other CLI flags, see [`references/native-image-options.md`](references/native-image-options.md).


## Reference Files
| Topic | File |
|-------|------|
| Native Image CLI options | [references/native-image-options.md](references/native-image-options.md) |
| Missing reachability metadata | [references/reachability-metadata.md](references/reachability-metadata.md) |
