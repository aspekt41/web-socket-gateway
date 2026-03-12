package net.aspekt.gateway;

/** Prints an ASCII art banner to stdout at application startup. */
public final class StartupBanner {

    private static final String BANNER = "\n"
            + " _    _      _     ____             _        _\n"
            + "| |  | |    | |   / ___|  ___   ___| | _____| |_\n"
            + "| |/\\| | ___| |__ \\___ \\ / _ \\ / __| |/ / _ \\ __|\n"
            + "\\  /\\  // -_) '_ \\ ___) | (_) | (__|   <  __/ |_\n"
            + " \\/  \\/ \\___|_.__/|____/ \\___/ \\___|_|\\_\\___|\\__|\n"
            + "\n"
            + "  Bridges WebSocket <-> TCP <-> UDP-Multicast with configurable forwarding rules\n"
            + "  Config: %s\n";

    private StartupBanner() {}

    @SuppressWarnings("PMD.SystemPrintln")
    public static void print(String configPath) {
        System.out.printf(BANNER, configPath);
    }
}
