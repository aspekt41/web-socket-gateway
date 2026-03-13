package net.aspekt.gateway.tcp.server;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class XmlTcpServerConfigTest {

    private static XmlTcpServerConfig build(String label, int port) throws Exception {
        XmlTcpServerConfig cfg = new XmlTcpServerConfig();
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
        assertEquals("srv", build("srv", 9090).getLabel());
    }

    @Test
    void getPort_returnsPort() throws Exception {
        assertEquals(9090, build("srv", 9090).getPort());
    }

    @Test
    void defaultBindAddress_is_0_0_0_0() throws Exception {
        assertEquals("0.0.0.0", build("srv", 9090).getBindAddress());
    }

    @Test
    void toString_containsLabel() throws Exception {
        assertTrue(build("my-srv", 9090).toString().contains("my-srv"));
    }

    @Test
    void toString_containsPort() throws Exception {
        assertTrue(build("srv", 1234).toString().contains("1234"));
    }
}
