package net.aspekt.gateway.tcp.hub;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class XmlTcpHubConfigTest {

    private static XmlTcpHubConfig build(String label, int port) throws Exception {
        XmlTcpHubConfig cfg = new XmlTcpHubConfig();
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
        assertEquals("hub", build("hub", 9091).getLabel());
    }

    @Test
    void getPort_returnsPort() throws Exception {
        assertEquals(9091, build("hub", 9091).getPort());
    }

    @Test
    void defaultBindAddress_is_0_0_0_0() throws Exception {
        assertEquals("0.0.0.0", build("hub", 9091).getBindAddress());
    }

    @Test
    void toString_containsLabel() throws Exception {
        assertTrue(build("my-hub", 9091).toString().contains("my-hub"));
    }

    @Test
    void toString_containsPort() throws Exception {
        assertTrue(build("hub", 7777).toString().contains("7777"));
    }
}
