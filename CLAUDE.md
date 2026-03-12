# Claude Notes

Java 21 application built with Gradle. Use `./gradlew` for all build tasks.

## Common commands

```bash
./gradlew build        # compile + test
./gradlew run --args="example-config.xml"   # run the application
./gradlew test         # run tests only
```

## Smoke test

After building, verify the application boots correctly:

```bash
./gradlew installDist
timeout 10 build/install/web-socket-gateway/bin/web-socket-gateway example-config.xml 2>&1; echo "Exit: $?"
```

Expected: config loads, WebSocket server and TCP server both log `ACTIVE`/listening lines, TCP client connects to localhost:9090, shutdown hook fires on timeout. Exit code 124 (killed by timeout) is normal.

## Before every commit

Always run the full test suite and confirm it passes before committing:

```bash
./gradlew test
```

Do not commit if any tests fail.

## Key facts

- Main entry point: `com.gateway.Main`
- Config file: XML validated against `src/main/resources/gateway-config.xsd`
- No Spring, no DI framework — plain Java with Netty for I/O
- Logging: `java.util.logging` (JUL) only, no SLF4J or Logback
