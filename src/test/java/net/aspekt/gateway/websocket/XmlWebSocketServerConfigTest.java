package net.aspekt.gateway.websocket;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class XmlWebSocketServerConfigTest {

    private static XmlWebSocketServerConfig build(String label, int port) throws Exception {
        XmlWebSocketServerConfig cfg = new XmlWebSocketServerConfig();
        setField(cfg, "label", label);
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
        assertEquals("ws1", build("ws1", 8080).getLabel());
    }

    @Test
    void getPort_returnsPort() throws Exception {
        assertEquals(8080, build("ws", 8080).getPort());
    }

    @Test
    void defaultBindAddress_is_0_0_0_0() throws Exception {
        assertEquals("0.0.0.0", build("ws", 8080).getBindAddress());
    }

    @Test
    void defaultPath_isSlashWs() throws Exception {
        assertEquals("/ws", build("ws", 8080).getPath());
    }

    @Test
    void defaultMaxFrameBytes_is65536() throws Exception {
        assertEquals(65536, build("ws", 8080).getMaxFrameBytes());
    }

    @Test
    void toString_containsLabel() throws Exception {
        assertTrue(build("my-ws", 8080).toString().contains("my-ws"));
    }

    @Test
    void toString_containsPort() throws Exception {
        assertTrue(build("ws", 9999).toString().contains("9999"));
    }
}
