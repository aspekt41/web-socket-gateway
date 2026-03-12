package net.aspekt.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import net.aspekt.gateway.tcp.server.TcpServerConfig;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TcpServerConfig}.
 */
class TcpServerConfigTest {

    private static void set(Object obj, String fieldName, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, value);
    }

    @Test
    void defaultBindAddress() {
        assertEquals("0.0.0.0", new TcpServerConfig().getBindAddress());
    }

    @Test
    void toStringContainsKeyFields() throws Exception {
        TcpServerConfig cfg = new TcpServerConfig();
        set(cfg, "label", "my-tcp-server");
        set(cfg, "bindAddress", "127.0.0.1");
        set(cfg, "port", 7001);

        String s = cfg.toString();
        assertTrue(s.contains("my-tcp-server"));
        assertTrue(s.contains("127.0.0.1"));
        assertTrue(s.contains("7001"));
    }

    @Test
    void gettersReturnSetValues() throws Exception {
        TcpServerConfig cfg = new TcpServerConfig();
        set(cfg, "label", "srv");
        set(cfg, "bindAddress", "10.0.0.1");
        set(cfg, "port", 4000);

        assertEquals("srv", cfg.getLabel());
        assertEquals("10.0.0.1", cfg.getBindAddress());
        assertEquals(4000, cfg.getPort());
    }
}
