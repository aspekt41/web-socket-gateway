package net.aspekt.gateway;

import net.aspekt.gateway.tcp.client.TcpClient;
import net.aspekt.gateway.tcp.client.TcpClientConfig;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import net.aspekt.gateway.websocket.WebSocketEndpoint;
import net.aspekt.gateway.websocket.WebSocketServer;
import net.aspekt.gateway.websocket.WebSocketServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test for the TCP↔WebSocket bridge.
 *
 * <p>The test wires up the full stack:
 * <ol>
 *   <li>A real {@link ServerSocket} acting as the remote TCP service.
 *   <li>A {@link GatewayConfig} parsed from a dynamically-generated XML config.
 *   <li>A real {@link WebSocketServer} and {@link TcpClient} constructed from
 *       that config with explicit {@link WebSocketEndpoint} / {@link TcpClientEndpoint}
 *       and bidirectional {@code <forward>} rules.
 *   <li>A Java {@link HttpClient}-based WebSocket client connecting to the
 *       gateway's WebSocket server.
 * </ol>
 *
 * <p>Two data flows are verified:
 * <ul>
 *   <li><b>WS → TCP</b>: a binary sequence sent by the WebSocket client is
 *       received intact at the test TCP server.
 *   <li><b>TCP → WS</b>: bytes written by the test TCP server arrive at the
 *       WebSocket client as a binary frame with identical content.
 * </ul>
 */
class BridgeIntegrationTest {

    /** Binary sequence used for WS → TCP direction. */
    private static final byte[] WS_TO_TCP = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};

    /** Binary sequence used for TCP → WS direction. */
    private static final byte[] TCP_TO_WS = {(byte) 0xA1, (byte) 0xB2, (byte) 0xC3, (byte) 0xD4};

    @Test
    @Timeout(30)
    void binaryDataFlowsInBothDirectionsAcrossBridge() throws Exception {

        // ----------------------------------------------------------------
        // 1.  Start a test TCP server on a random port so the gateway
        //     TcpClient can connect to it.
        // ----------------------------------------------------------------
        int tcpPort = findFreePort();
        int wsPort  = findFreePort();

        ServerSocket tcpServer = new ServerSocket(tcpPort);
        tcpServer.setSoTimeout(10_000);

        WebSocketServer wsServer = null;
        TcpClient tcpClient = null;

        try {
            // ------------------------------------------------------------
            // 2.  Parse a gateway config and wire endpoints + forward rules.
            // ------------------------------------------------------------
            File configFile = writeTempConfig(wsPort, tcpPort);
            GatewayConfig config = ConfigParser.parse(configFile);

            WebSocketServerConfig wsCfg  = config.getWebSocketServers().get(0);
            TcpClientConfig       tcpCfg = config.getTcpClients().get(0);

            WebSocketEndpoint wsEndpoint  = new WebSocketEndpoint(wsCfg.getLabel());
            TcpClientEndpoint tcpEndpoint = new TcpClientEndpoint(tcpCfg.getLabel());

            // Bidirectional forwarding
            wsEndpoint.addTarget(tcpEndpoint);
            tcpEndpoint.addTarget(wsEndpoint);

            wsServer  = new WebSocketServer(wsCfg, wsEndpoint);
            wsServer.start();

            tcpClient = new TcpClient(tcpCfg, tcpEndpoint);
            tcpClient.start();

            // ------------------------------------------------------------
            // 3.  Accept the TCP connection that the gateway's TcpClient
            //     initiates immediately on start().
            // ------------------------------------------------------------
            Socket tcpConn = tcpServer.accept();
            tcpConn.setSoTimeout(10_000);

            // ------------------------------------------------------------
            // 4.  Connect a WebSocket client to the gateway.
            // ------------------------------------------------------------
            CountDownLatch connected     = new CountDownLatch(1);
            CompletableFuture<byte[]> tcpToWsData = new CompletableFuture<>();

            HttpClient http = HttpClient.newHttpClient();
            WebSocket wsClient = http.newWebSocketBuilder()
                    .buildAsync(
                            URI.create("ws://127.0.0.1:" + wsPort + "/ws"),
                            new WsListener(connected, tcpToWsData))
                    .get(10, TimeUnit.SECONDS);

            assertTrue(connected.await(10, TimeUnit.SECONDS),
                    "WebSocket handshake did not complete within 10 s");

            // Brief pause so that the WS channel is fully registered in the
            // endpoint before we start sending TCP data.
            Thread.sleep(100);

            // ------------------------------------------------------------
            // 5.  WS → TCP: send binary from the WebSocket client and
            //     verify it arrives at the test TCP server.
            // ------------------------------------------------------------
            wsClient.sendBinary(ByteBuffer.wrap(WS_TO_TCP), true).get(5, TimeUnit.SECONDS);

            byte[] receivedAtTcp = readExact(tcpConn, WS_TO_TCP.length);
            assertArrayEquals(WS_TO_TCP, receivedAtTcp,
                    "Bytes forwarded WS→TCP should be identical to what was sent");

            // ------------------------------------------------------------
            // 6.  TCP → WS: send bytes from the test TCP server and
            //     verify they arrive at the WebSocket client as-is.
            // ------------------------------------------------------------
            tcpConn.getOutputStream().write(TCP_TO_WS);
            tcpConn.getOutputStream().flush();

            byte[] receivedAtWs = tcpToWsData.get(10, TimeUnit.SECONDS);
            assertArrayEquals(TCP_TO_WS, receivedAtWs,
                    "Bytes forwarded TCP→WS should be identical to what was sent");

            // ------------------------------------------------------------
            // 7.  Clean up the WebSocket client gracefully.
            // ------------------------------------------------------------
            wsClient.sendClose(WebSocket.NORMAL_CLOSURE, "test done")
                    .get(5, TimeUnit.SECONDS);
            tcpConn.close();

        } finally {
            tcpServer.close();
            if (tcpClient != null) tcpClient.stop();
            if (wsServer  != null) wsServer.stop();
        }
    }

    // -----------------------------------------------------------------------
    // WebSocket listener
    // -----------------------------------------------------------------------

    /**
     * Minimal WebSocket listener that:
     * <ul>
     *   <li>Counts down {@code connected} when the handshake completes.
     *   <li>Collects all binary message chunks and completes {@code firstBinary}
     *       with the reassembled payload when the last chunk arrives.
     * </ul>
     */
    private static final class WsListener implements WebSocket.Listener {

        private final CountDownLatch connected;
        private final CompletableFuture<byte[]> firstBinary;

        // Accumulates chunks for the current message
        private final java.io.ByteArrayOutputStream messageBuffer =
                new java.io.ByteArrayOutputStream();

        WsListener(CountDownLatch connected, CompletableFuture<byte[]> firstBinary) {
            this.connected   = connected;
            this.firstBinary = firstBinary;
        }

        @Override
        public void onOpen(WebSocket ws) {
            connected.countDown();
            ws.request(Long.MAX_VALUE);   // allow all messages without per-message flow control
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onBinary(
                WebSocket ws, ByteBuffer data, boolean last) {

            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            try {
                messageBuffer.write(chunk);
            } catch (IOException ignored) { /* ByteArrayOutputStream never throws */ }

            if (last && !firstBinary.isDone()) {
                firstBinary.complete(messageBuffer.toByteArray());
                messageBuffer.reset();
            }
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(
                WebSocket ws, int statusCode, String reason) {
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            firstBinary.completeExceptionally(error);
        }
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    /**
     * Allocates a random free TCP port by briefly binding to port 0.
     * There is an inherent TOCTOU race but it is acceptable for tests.
     */
    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /**
     * Writes a temporary XML config file with the given WS and TCP ports
     * using the new labeled-connection + forward-rule format.
     */
    private static File writeTempConfig(int wsPort, int tcpPort) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<gateway-config xmlns=\"http://github.com/web-socket-gateway/config/v1\">\n"
                + "  <websocket-server label=\"test-ws\" port=\"" + wsPort + "\"/>\n"
                + "  <tcp-client label=\"test-tcp\" host=\"127.0.0.1\" port=\"" + tcpPort
                + "\" reconnect-delay-seconds=\"1\"/>\n"
                + "  <forward from=\"test-ws\"  to=\"test-tcp\"/>\n"
                + "  <forward from=\"test-tcp\" to=\"test-ws\"/>\n"
                + "</gateway-config>\n";

        File tmp = File.createTempFile("gw-integration-", ".xml");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), xml);
        return tmp;
    }

    /**
     * Reads exactly {@code length} bytes from the socket's input stream,
     * blocking until all bytes are available or the socket times out.
     */
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
