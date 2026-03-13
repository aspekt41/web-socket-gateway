package net.aspekt.gateway.tcp.client;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class XmlTcpClientConfigTest {

    private static XmlTcpClientConfig build(String label, String host, int port) throws Exception {
        XmlTcpClientConfig cfg = new XmlTcpClientConfig();
        setField(cfg, "label", label);
        setField(cfg, "host", host);
        setField(cfg, "port", port);
        return cfg;
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    @Test
    void getLabel_returnsLabel() throws Exception {
        assertEquals("cli", build("cli", "localhost", 9090).getLabel());
    }

    @Test
    void getHost_returnsHost() throws Exception {
        assertEquals("localhost", build("cli", "localhost", 9090).getHost());
    }

    @Test
    void getPort_returnsPort() throws Exception {
        assertEquals(9090, build("cli", "localhost", 9090).getPort());
    }

    @Test
    void defaultReconnectDelaySeconds_is5() throws Exception {
        assertEquals(5, build("cli", "localhost", 9090).getReconnectDelaySeconds());
    }

    @Test
    void defaultConnectTimeoutSeconds_is10() throws Exception {
        assertEquals(10, build("cli", "localhost", 9090).getConnectTimeoutSeconds());
    }

    @Test
    void toString_containsLabel() throws Exception {
        assertTrue(build("my-cli", "host", 1234).toString().contains("my-cli"));
    }

    @Test
    void toString_containsHostAndPort() throws Exception {
        String s = build("cli", "remotehost", 5555).toString();
        assertTrue(s.contains("remotehost"));
        assertTrue(s.contains("5555"));
    }
}
