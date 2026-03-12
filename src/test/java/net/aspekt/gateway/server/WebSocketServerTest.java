package net.aspekt.gateway.server;

import net.aspekt.gateway.websocket.WebSocketEndpoint;
import net.aspekt.gateway.websocket.WebSocketServer;
import net.aspekt.gateway.websocket.WebSocketServerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for {@link WebSocketServer} lifecycle methods that do not require
 * binding a real socket.
 *
 * <p>{@code start()} and {@code awaitShutdown()} paths that need a real bound
 * channel are covered by {@link com.gateway.BridgeIntegrationTest}. This class
 * exercises the null-safe branches that execute when {@code stop()} or
 * {@code awaitShutdown()} is called on a server that was never started.
 */
class WebSocketServerTest {

    private static WebSocketServer unstartedServer() {
        // WebSocketServerConfig has all-defaults constructor; port=0 is fine for stop() tests
        // because stop() never uses the config — only the endpoint label for logging.
        WebSocketEndpoint endpoint = new WebSocketEndpoint("ws-test");
        return new WebSocketServer(new WebSocketServerConfig(), endpoint);
    }

    // -----------------------------------------------------------------------
    // stop() before start() — bossGroup, workerGroup, serverChannel all null
    // -----------------------------------------------------------------------

    @Test
    void stopBeforeStartDoesNotThrow() {
        assertDoesNotThrow(() -> unstartedServer().stop());
    }

    @Test
    void closeBeforeStartDoesNotThrow() {
        // close() is the AutoCloseable entry point and delegates to stop()
        assertDoesNotThrow(() -> unstartedServer().close());
    }

    @Test
    void tryWithResourcesBeforeStartDoesNotThrow() {
        assertDoesNotThrow(() -> {
            try (WebSocketServer server = unstartedServer()) {
                // never started; close() must handle null fields gracefully
            }
        });
    }

    // -----------------------------------------------------------------------
    // awaitShutdown() before start() — serverChannel is null, must return immediately
    // -----------------------------------------------------------------------

    @Test
    void awaitShutdownBeforeStartReturnsImmediately() {
        assertDoesNotThrow(() -> unstartedServer().awaitShutdown());
    }
}
