package net.aspekt.gateway;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StartupBannerTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    @Test
    void print_doesNotThrow() {
        assertDoesNotThrow(() -> StartupBanner.print("test-config.xml"));
    }

    @Test
    void print_outputContainsConfigPath() {
        StartupBanner.print("my-config.xml");
        String output = captured.toString();
        org.junit.jupiter.api.Assertions.assertTrue(output.contains("my-config.xml"));
    }

    @Test
    void print_outputContainsBridgeDescription() {
        StartupBanner.print("cfg.xml");
        String output = captured.toString();
        org.junit.jupiter.api.Assertions.assertTrue(output.contains("WebSocket"));
    }
}
