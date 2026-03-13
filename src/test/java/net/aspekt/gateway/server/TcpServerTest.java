package net.aspekt.gateway.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import net.aspekt.gateway.tcp.server.TcpServer;
import net.aspekt.gateway.tcp.server.TcpServerEndpoint;
import net.aspekt.gateway.tcp.server.XmlTcpServerConfig;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TcpServer} lifecycle methods that do not require
 * binding a real socket.
 *
 * <p>{@code start()} paths that need a real bound channel are covered by
 * {@link com.gateway.TcpServerIntegrationTest}. This class exercises the
 * null-safe branches that execute when {@code stop()} or {@code awaitShutdown()}
 * is called on a server that was never started.
 */
class TcpServerTest {

    private static TcpServer unstartedServer() {
        TcpServerEndpoint endpoint = new TcpServerEndpoint("tcp-srv-test");
        return new TcpServer(new XmlTcpServerConfig(), endpoint);
    }

    @Test
    void stopBeforeStartDoesNotThrow() {
        assertDoesNotThrow(() -> unstartedServer().stop());
    }

    @Test
    void closeBeforeStartDoesNotThrow() {
        assertDoesNotThrow(() -> unstartedServer().close());
    }

    @Test
    void tryWithResourcesBeforeStartDoesNotThrow() {
        assertDoesNotThrow(() -> {
            try (TcpServer server = unstartedServer()) {
                // never started; close() must handle null fields gracefully
            }
        });
    }

    @Test
    void awaitShutdownBeforeStartReturnsImmediately() {
        assertDoesNotThrow(() -> unstartedServer().awaitShutdown());
    }
}
