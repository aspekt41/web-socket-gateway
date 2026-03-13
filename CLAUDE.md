# Claude Notes

Java 21 application built with Gradle. Use `./gradlew` for all build tasks.

Always pass `--no-daemon` to every Gradle invocation to avoid conflicts with
background daemon processes in CI-like environments.

## Common commands

```bash
./gradlew --no-daemon build        # compile + test
./gradlew --no-daemon run --args="example-config.xml"   # run the application
./gradlew --no-daemon test         # run tests only
./gradlew --no-daemon spotlessApply   # reformat code and imports (Palantir style)
```

## Smoke test

After building, verify the application boots correctly:

```bash
./gradlew --no-daemon installDist
timeout 10 build/install/web-socket-gateway/bin/web-socket-gateway example-config.xml 2>&1; echo "Exit: $?"
```

Expected: config loads, WebSocket server and TCP server both log `ACTIVE`/listening lines, TCP client connects to localhost:9090, shutdown hook fires on timeout. Exit code 124 (killed by timeout) is normal.

## Before every commit

Always reformat code and run a build before committing:

```bash
./gradlew --no-daemon spotlessApply   # reformat code and imports (Palantir style)
./gradlew --no-daemon build
```

Do not commit if any tests fail. Run `spotlessApply` before `test` so that
formatting changes are included in the same commit as the code changes.

## Architecture documentation

Always update `ARCHITECTURE.md` and `ARCHITECTURE.puml` when making changes that
affect the structure, startup behaviour, endpoint types, data flow, or wiring of
the application.  Keep both files in sync with the code.

## Key facts

- Main entry point: `net.aspekt.gateway.Main`
- Config file: XML validated against `src/main/resources/gateway-config.xsd`
- No Spring, no DI framework — plain Java with Netty for I/O
- Logging: `java.util.logging` (JUL) only, no SLF4J or Logback
