package net.aspekt.gateway.client;

import net.aspekt.gateway.tcp.client.TcpClient;
import net.aspekt.gateway.tcp.client.TcpClientConfig;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link TcpClient} lifecycle methods that do not require a live
 * TCP connection.
 *
 * <p>{@code start()} and the connection-success/failure branches are covered by
 * the integration test; this class focuses on the branches exercisable without
 * binding real sockets.
 */
class TcpClientTest {

    private static TcpClientConfig minimalConfig() throws Exception {
        TcpClientConfig cfg = new TcpClientConfig();
        setField(cfg, "host", "localhost");
        setField(cfg, "port", 9090);
        return cfg;
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    // -----------------------------------------------------------------------
    // stop() before start() — channel and eventLoopGroup are null
    // -----------------------------------------------------------------------

    @Test
    void stopBeforeStartDoesNotThrow() throws Exception {
        TcpClient client = new TcpClient(minimalConfig(), new TcpClientEndpoint("tcp-test"));
        // Neither channel nor eventLoopGroup have been set; null-checks in stop() must handle this.
        assertDoesNotThrow(client::stop);
    }

    @Test
    void closeBeforeStartDoesNotThrow() throws Exception {
        // close() delegates to stop(); verify the same null-safe behaviour via AutoCloseable.
        TcpClient client = new TcpClient(minimalConfig(), new TcpClientEndpoint("tcp-test"));
        assertDoesNotThrow(client::close);
    }

    // -----------------------------------------------------------------------
    // stop() idempotency — second call must be a no-op
    // -----------------------------------------------------------------------

    @Test
    void stopIsIdempotent() throws Exception {
        TcpClient client = new TcpClient(minimalConfig(), new TcpClientEndpoint("tcp-test"));
        client.stop(); // first call — sets stopped=true
        assertDoesNotThrow(client::stop); // second call — compareAndSet returns false, returns early
    }

    // -----------------------------------------------------------------------
    // scheduleReconnect() when already stopped
    // -----------------------------------------------------------------------

    @Test
    void scheduleReconnectIsNoOpWhenStopped() throws Exception {
        TcpClient client = new TcpClient(minimalConfig(), new TcpClientEndpoint("tcp-test"));
        client.stop(); // sets stopped=true
        // Pass null as eventLoop: if the stopped guard works, the method returns
        // before ever touching the eventLoop argument.
        assertDoesNotThrow(() -> client.scheduleReconnect(null));
    }

    // -----------------------------------------------------------------------
    // getReconnectDelaySeconds
    // -----------------------------------------------------------------------

    @Test
    void getReconnectDelaySecondsReturnsConfigValue() throws Exception {
        TcpClientConfig cfg = minimalConfig();
        setField(cfg, "reconnectDelaySeconds", 7);
        TcpClient client = new TcpClient(cfg, new TcpClientEndpoint("tcp-test"));
        assertEquals(7, client.getReconnectDelaySeconds());
    }
}
