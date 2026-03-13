package net.aspekt.gateway.config;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import net.aspekt.gateway.udp.multicast.XmlUdpMulticastConfig;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link XmlUdpMulticastConfig} getters, defaults, and {@code toString()}.
 */
class XmlUdpMulticastConfigTest {

    private static void set(Object obj, String fieldName, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, value);
    }

    // -----------------------------------------------------------------------
    // Default values
    // -----------------------------------------------------------------------

    @Test
    void defaultBindAddressIsAllInterfaces() {
        XmlUdpMulticastConfig cfg = new XmlUdpMulticastConfig();
        assertEquals("0.0.0.0", cfg.getBindAddress(), "bind-address should default to 0.0.0.0");
    }

    @Test
    void defaultNetworkInterfaceIsNull() {
        XmlUdpMulticastConfig cfg = new XmlUdpMulticastConfig();
        assertNull(cfg.getNetworkInterface(), "network-interface should default to null");
    }

    // -----------------------------------------------------------------------
    // Getters return injected values
    // -----------------------------------------------------------------------

    @Test
    void gettersReturnSetValues() throws Exception {
        XmlUdpMulticastConfig cfg = new XmlUdpMulticastConfig();
        set(cfg, "label", "mc-feed");
        set(cfg, "group", "230.1.2.3");
        set(cfg, "port", 5004);
        set(cfg, "bindAddress", "192.168.1.1");
        set(cfg, "networkInterface", "eth0");

        assertEquals("mc-feed", cfg.getLabel());
        assertEquals("230.1.2.3", cfg.getGroup());
        assertEquals(5004, cfg.getPort());
        assertEquals("192.168.1.1", cfg.getBindAddress());
        assertEquals("eth0", cfg.getNetworkInterface());
    }

    // -----------------------------------------------------------------------
    // toString()
    // -----------------------------------------------------------------------

    @Test
    void toStringContainsAllFields() throws Exception {
        XmlUdpMulticastConfig cfg = new XmlUdpMulticastConfig();
        set(cfg, "label", "mc-test");
        set(cfg, "group", "230.0.0.5");
        set(cfg, "port", 9999);
        set(cfg, "bindAddress", "0.0.0.0");
        set(cfg, "networkInterface", "lo");

        String s = cfg.toString();
        assertTrue(s.contains("mc-test"), "toString should contain label");
        assertTrue(s.contains("230.0.0.5"), "toString should contain group");
        assertTrue(s.contains("9999"), "toString should contain port");
        assertTrue(s.contains("0.0.0.0"), "toString should contain bind-address");
        assertTrue(s.contains("lo"), "toString should contain network-interface");
    }

    @Test
    void toStringWithNullNetworkInterfaceDoesNotThrow() throws Exception {
        XmlUdpMulticastConfig cfg = new XmlUdpMulticastConfig();
        set(cfg, "label", "mc-test");
        set(cfg, "group", "230.0.0.1");
        set(cfg, "port", 1234);
        // bindAddress keeps default; networkInterface stays null

        assertDoesNotThrow(() -> cfg.toString());
        assertTrue(cfg.toString().contains("mc-test"));
    }
}
