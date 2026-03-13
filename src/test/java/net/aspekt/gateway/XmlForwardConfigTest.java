package net.aspekt.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class XmlForwardConfigTest {

    private XmlForwardConfig build(String from, String to) throws Exception {
        XmlForwardConfig cfg = new XmlForwardConfig();
        set(cfg, "from", from);
        set(cfg, "to", to);
        return cfg;
    }

    private static void set(Object obj, String fieldName, String value) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, value);
    }

    @Test
    void getFrom_returnsFrom() throws Exception {
        assertEquals("src", build("src", "dst").getFrom());
    }

    @Test
    void getTo_returnsTo() throws Exception {
        assertEquals("dst", build("src", "dst").getTo());
    }

    @Test
    void toString_containsFromAndTo() throws Exception {
        String s = build("alpha", "beta").toString();
        assertTrue(s.contains("alpha"));
        assertTrue(s.contains("beta"));
    }

    @Test
    void implementsForwardConfig() throws Exception {
        assertInstanceOf(ForwardConfig.class, build("a", "b"));
    }
}
