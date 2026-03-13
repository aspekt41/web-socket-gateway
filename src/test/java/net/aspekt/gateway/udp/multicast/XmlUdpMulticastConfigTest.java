package net.aspekt.gateway.udp.multicast;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class XmlUdpMulticastConfigTest {

    private static XmlUdpMulticastConfig build(String label, String group, int port) throws Exception {
        XmlUdpMulticastConfig cfg = new XmlUdpMulticastConfig();
        setField(cfg, "label", label);
        setField(cfg, "group", group);
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
        assertEquals("udp", build("udp", "230.0.0.1", 4567).getLabel());
    }

    @Test
    void getGroup_returnsGroup() throws Exception {
        assertEquals("230.0.0.1", build("udp", "230.0.0.1", 4567).getGroup());
    }

    @Test
    void getPort_returnsPort() throws Exception {
        assertEquals(4567, build("udp", "230.0.0.1", 4567).getPort());
    }

    @Test
    void defaultBindAddress_is_0_0_0_0() throws Exception {
        assertEquals("0.0.0.0", build("udp", "230.0.0.1", 4567).getBindAddress());
    }

    @Test
    void defaultNetworkInterface_isNull() throws Exception {
        assertNull(build("udp", "230.0.0.1", 4567).getNetworkInterface());
    }

    @Test
    void toString_containsLabel() throws Exception {
        assertTrue(build("my-udp", "230.0.0.1", 4567).toString().contains("my-udp"));
    }

    @Test
    void toString_containsGroupAndPort() throws Exception {
        String s = build("udp", "230.1.2.3", 9999).toString();
        assertTrue(s.contains("230.1.2.3"));
        assertTrue(s.contains("9999"));
    }
}
