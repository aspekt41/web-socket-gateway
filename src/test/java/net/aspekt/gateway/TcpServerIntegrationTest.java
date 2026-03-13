package net.aspekt.gateway;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import net.aspekt.gateway.tcp.client.TcpClient;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import net.aspekt.gateway.tcp.client.XmlTcpClientConfig;
import net.aspekt.gateway.tcp.server.TcpServer;
import net.aspekt.gateway.tcp.server.TcpServerEndpoint;
import net.aspekt.gateway.tcp.server.XmlTcpServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * End-to-end integration test for a raw TCP client → TCP server bridge.
 *
 * <p>Wires: {@link TcpServer} (gateway inbound) → {@link TcpClient} (gateway outbound)
 * → backend {@link ServerSocket}, with bidirectional forwarding rules.
 * A plain {@link Socket} connects to the gateway's TCP server port and
 * data flow in both directions is verified.
 */
class TcpServerIntegrationTest {

    private static final byte[] CLIENT_TO_BACKEND = {0x01, 0x02, 0x03, 0x04};
    private static final byte[] BACKEND_TO_CLIENT = {(byte) 0xAA, (byte) 0xBB};

    @Test
    @Timeout(30)
    void rawDataFlowsThroughTcpServerToTcpClientBridge() throws Exception {
        int tcpServerPort = findFreePort(); // gateway listens here
        int backendPort = findFreePort(); // "backend" ServerSocket

        ServerSocket backendServer = new ServerSocket(backendPort);
        backendServer.setSoTimeout(10_000);

        TcpServer tcpServer = null;
        TcpClient tcpClient = null;

        try {
            XmlTcpServerConfig srvCfg = buildTcpServerConfig("gw-server", tcpServerPort);
            XmlTcpClientConfig cliCfg = buildTcpClientConfig("gw-client", backendPort);

            TcpServerEndpoint srvEp = new TcpServerEndpoint(srvCfg.getLabel());
            TcpClientEndpoint cliEp = new TcpClientEndpoint(cliCfg.getLabel());

            srvEp.addTarget(cliEp); // inbound TCP → backend
            cliEp.addTarget(srvEp); // backend reply → inbound TCP clients

            tcpServer = new TcpServer(srvCfg, srvEp);
            tcpServer.start();

            tcpClient = new TcpClient(cliCfg, cliEp);
            tcpClient.start();

            // Accept the connection that the gateway's TcpClient initiates on start().
            Socket backendConn = backendServer.accept();
            backendConn.setSoTimeout(10_000);

            // Connect a raw TCP client to the gateway server.
            Socket clientConn = new Socket("127.0.0.1", tcpServerPort);
            clientConn.setSoTimeout(10_000);
            // Brief pause so channelActive fires and registers the channel in the ChannelGroup.
            Thread.sleep(100);

            // client → backend
            clientConn.getOutputStream().write(CLIENT_TO_BACKEND);
            clientConn.getOutputStream().flush();
            assertArrayEquals(
                    CLIENT_TO_BACKEND,
                    readExact(backendConn, CLIENT_TO_BACKEND.length),
                    "Bytes forwarded client→backend should be identical to what was sent");

            // backend → client
            backendConn.getOutputStream().write(BACKEND_TO_CLIENT);
            backendConn.getOutputStream().flush();
            assertArrayEquals(
                    BACKEND_TO_CLIENT,
                    readExact(clientConn, BACKEND_TO_CLIENT.length),
                    "Bytes forwarded backend→client should be identical to what was sent");

            clientConn.close();
            backendConn.close();

        } finally {
            backendServer.close();
            if (tcpClient != null) tcpClient.stop();
            if (tcpServer != null) tcpServer.stop();
        }
    }

    // -----------------------------------------------------------------------
    // Config builders (using reflection since POJOs have no public arg constructors)
    // -----------------------------------------------------------------------

    private static XmlTcpServerConfig buildTcpServerConfig(String label, int port) throws Exception {
        XmlTcpServerConfig cfg = new XmlTcpServerConfig();
        setField(cfg, "label", label);
        setField(cfg, "bindAddress", "127.0.0.1");
        setField(cfg, "port", port);
        return cfg;
    }

    private static XmlTcpClientConfig buildTcpClientConfig(String label, int port) throws Exception {
        XmlTcpClientConfig cfg = new XmlTcpClientConfig();
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
            if (n < 0) throw new EOFException("Socket closed after " + offset + " of " + length + " bytes");
            offset += n;
        }
        return buf;
    }
}
