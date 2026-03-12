package net.aspekt.gateway;

import net.aspekt.gateway.tcp.hub.TcpHub;
import net.aspekt.gateway.tcp.hub.TcpHubConfig;
import net.aspekt.gateway.tcp.hub.TcpHubEndpoint;
import net.aspekt.gateway.tcp.client.TcpClient;
import net.aspekt.gateway.tcp.client.TcpClientConfig;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the TCP hub.
 *
 * <p>Verifies that data from one connected client is broadcast to all other
 * connected clients but not back to the sender, and that configured forwarding
 * targets also receive the data.
 */
class TcpHubIntegrationTest {

    private static final byte[] DATA_FROM_A = {0x01, 0x02, 0x03};
    private static final byte[] DATA_FROM_B = {(byte) 0xAA, (byte) 0xBB};

    @Test
    @Timeout(30)
    void dataFromOnClientBroadcastsToOthersNotSender() throws Exception {
        int hubPort = findFreePort();

        TcpHub hub = null;
        try {
            TcpHubConfig cfg = buildHubConfig("test-hub", hubPort);
            TcpHubEndpoint ep = new TcpHubEndpoint(cfg.getLabel());
            hub = new TcpHub(cfg, ep);
            hub.start();

            // Connect three clients
            Socket clientA = new Socket("127.0.0.1", hubPort);
            clientA.setSoTimeout(5_000);
            Socket clientB = new Socket("127.0.0.1", hubPort);
            clientB.setSoTimeout(5_000);
            Socket clientC = new Socket("127.0.0.1", hubPort);
            clientC.setSoTimeout(5_000);

            // Give Netty time to fire channelActive for all three
            Thread.sleep(150);

            // A sends data
            clientA.getOutputStream().write(DATA_FROM_A);
            clientA.getOutputStream().flush();

            // B and C should receive it
            assertArrayEquals(DATA_FROM_A, readExact(clientB, DATA_FROM_A.length),
                    "B should receive A's data");
            assertArrayEquals(DATA_FROM_A, readExact(clientC, DATA_FROM_A.length),
                    "C should receive A's data");

            // A should NOT receive its own data — verify via a follow-up send from B
            // so we can confirm A's stream is empty before that point.
            clientB.getOutputStream().write(DATA_FROM_B);
            clientB.getOutputStream().flush();

            // A and C receive B's data; A's stream should only have B's data (not its own)
            assertArrayEquals(DATA_FROM_B, readExact(clientA, DATA_FROM_B.length),
                    "A should receive B's data");
            assertArrayEquals(DATA_FROM_B, readExact(clientC, DATA_FROM_B.length),
                    "C should receive B's data");

            clientA.close();
            clientB.close();
            clientC.close();
        } finally {
            if (hub != null) hub.stop();
        }
    }

    @Test
    @Timeout(30)
    void dataIsAlsoForwardedToConfiguredTargets() throws Exception {
        int hubPort     = findFreePort();
        int backendPort = findFreePort();

        ServerSocket backendServer = new ServerSocket(backendPort);
        backendServer.setSoTimeout(10_000);

        TcpHub    hub       = null;
        TcpClient tcpClient = null;
        try {
            TcpHubConfig    hubCfg  = buildHubConfig("test-hub-fwd", hubPort);
            TcpHubEndpoint  hubEp   = new TcpHubEndpoint(hubCfg.getLabel());

            TcpClientConfig cliCfg  = buildTcpClientConfig("backend-client", backendPort);
            TcpClientEndpoint cliEp = new TcpClientEndpoint(cliCfg.getLabel());

            // Hub forwards to TCP client (backend)
            hubEp.addTarget(cliEp);

            hub       = new TcpHub(hubCfg, hubEp);
            tcpClient = new TcpClient(cliCfg, cliEp);
            hub.start();
            tcpClient.start();

            Socket backendConn = backendServer.accept();
            backendConn.setSoTimeout(5_000);

            Socket clientA = new Socket("127.0.0.1", hubPort);
            clientA.setSoTimeout(5_000);
            Socket clientB = new Socket("127.0.0.1", hubPort);
            clientB.setSoTimeout(5_000);

            Thread.sleep(150);

            // A sends data
            clientA.getOutputStream().write(DATA_FROM_A);
            clientA.getOutputStream().flush();

            // B should receive it (hub peer broadcast)
            assertArrayEquals(DATA_FROM_A, readExact(clientB, DATA_FROM_A.length),
                    "B should receive A's data via hub broadcast");

            // Backend should also receive it (forwarding target)
            assertArrayEquals(DATA_FROM_A, readExact(backendConn, DATA_FROM_A.length),
                    "Backend target should receive A's data via forward rule");

            clientA.close();
            clientB.close();
            backendConn.close();
        } finally {
            backendServer.close();
            if (tcpClient != null) tcpClient.stop();
            if (hub != null) hub.stop();
        }
    }

    // -----------------------------------------------------------------------
    // Config builders (using reflection since POJOs have no public arg constructors)
    // -----------------------------------------------------------------------

    private static TcpHubConfig buildHubConfig(String label, int port) throws Exception {
        TcpHubConfig cfg = new TcpHubConfig();
        setField(cfg, "label", label);
        setField(cfg, "bindAddress", "127.0.0.1");
        setField(cfg, "port", port);
        return cfg;
    }

    private static TcpClientConfig buildTcpClientConfig(String label, int port) throws Exception {
        TcpClientConfig cfg = new TcpClientConfig();
        setField(cfg, "label", label);
        setField(cfg, "host", "127.0.0.1");
        setField(cfg, "port", port);
        setField(cfg, "reconnectDelaySeconds", 1);
        setField(cfg, "connectTimeoutSeconds", 10);
        return cfg;
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static byte[] readExact(Socket socket, int length) throws IOException {
        byte[] buf = new byte[length];
        InputStream in = socket.getInputStream();
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n < 0) throw new EOFException(
                    "Socket closed after " + offset + " of " + length + " bytes");
            offset += n;
        }
        return buf;
    }
}
