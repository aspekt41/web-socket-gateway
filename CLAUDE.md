# Claude Notes

Java 21 application built with Gradle. Use `./gradlew` for all build tasks.

## Common commands

```bash
./gradlew build        # compile + test
./gradlew run --args="example-config.xml"   # run the application
./gradlew test         # run tests only
```

## Key facts

- Main entry point: `com.gateway.Main`
- Config file: XML validated against `src/main/resources/gateway-config.xsd`
- No Spring, no DI framework — plain Java with Netty for I/O
- Logging: `java.util.logging` (JUL) only, no SLF4J or Logback
