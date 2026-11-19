# AGENTS.md

## Overview

This suite contains GraalWasm, the Truffle implementation of WebAssembly.

Run `mx` from this `wasm/` suite directory. Start with `README.md`, `docs/contributor/Building.md`, and `docs/contributor/TestsAndBenchmarks.md` for setup and detailed test instructions.

## Structure

```text
./
├── mx.wasm/                         # suite.py, mx commands, gate/test wiring
├── src/org.graalvm.wasm/            # Core runtime, parser, linker, options, built-ins
├── src/org.graalvm.wasm.memory/     # Memory implementations
├── src/org.graalvm.wasm.launcher/   # Native/JVM launcher
├── src/org.graalvm.wasm.test/       # JUnit tests and Wasm test suites
├── src/org.graalvm.wasm.testcases/  # Test case resources
├── src/org.graalvm.wasm.benchmark/  # Benchmark harness
├── src/org.graalvm.wasm.benchcases/ # Benchmark case resources
├── docs/                            # User and contributor documentation
└── mxbuild/                         # Generated build output; do not edit
```

## Where To Look

| Task | Location | Notes |
|------|----------|-------|
| Build and test commands | `docs/contributor/Building.md`, `docs/contributor/TestsAndBenchmarks.md` | Keep detailed command help there instead of duplicating it here. |
| Suite metadata and mx commands | `mx.wasm/suite.py`, `mx.wasm/mx_wasm.py`, `mx.wasm/mx_wasm_benchmark.py` | Read these before changing project names, distributions, gate tasks, or benchmark wiring. |
| Runtime and semantics | `src/org.graalvm.wasm/src/org/graalvm/wasm/` | Main language, module, instance, store, linker, parser, option, and built-in code. |
| Memory support | `src/org.graalvm.wasm.memory/` | Linear memory implementations and memory-related helpers. |
| WASI and built-in modules | `src/org.graalvm.wasm/src/org/graalvm/wasm/predefined/` | Keep WASI capability and filesystem semantics separate from JS embedding behavior. |
| Unit tests | `src/org.graalvm.wasm.test/` | JUnit suites such as `WasmTestSuite`, `WasmJsApiSuite`, `LinkerSuite`, `ExceptionSuite`, `WasiSuite`, and bytecode tests. |
| C/WAT test cases | `src/org.graalvm.wasm.testcases/` | Additional test resources used by `CSuite` and `WatSuite`. |
| Benchmarks | `src/org.graalvm.wasm.benchmark/`, `src/org.graalvm.wasm.benchcases/`, `benchmarks/` | Warm up benchmarks and compare against noise before drawing conclusions. |

## Commands

```bash
# Build this suite
mx build

# Main Wasm tests after setting WABT_DIR to a wabt bin directory
mx --dy /compiler unittest -Dwasmtest.watToWasmExecutable=$WABT_DIR/wat2wasm WasmTestSuite

# Extra C/WAT suites require the additional test-case build described in TestsAndBenchmarks.md
mx --dy /compiler unittest CSuite WatSuite
```

Build after editing Java sources and wait for the build to finish before running tests. If a change touches bytecode format, parser metadata, interpreter control flow, or Truffle compilation behavior, run both targeted functional tests and compiler-enabled validation.

## Cross-Repo Boundaries

- Keep core Wasm runtime semantics in `graal/wasm`.
- Keep JavaScript API coercions, `WebAssembly.*` object behavior, V8 harness shims, and `testV8.json` changes in `js/graal-js`.
- If a GraalWasm fix changes Graal.js TestV8 outcomes, update `testV8.json` only after `js` imports the fixed `graal` revision.

## Notes

- Either `wat2wasm` must be on the `PATH` or `WABT_DIR` must point to a wabt `bin/` directory for `.wat`-based tests.
- Do not hide invariant violations with broad crash guards when the root cause can be fixed.
- If flags appear ignored, inspect suite defaults and mx test wiring before adding ad hoc command-line workarounds.
