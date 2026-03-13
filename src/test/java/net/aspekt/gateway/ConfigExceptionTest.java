package net.aspekt.gateway;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConfigExceptionTest {

    @Test
    void messageConstructor_storesMessage() {
        ConfigException ex = new ConfigException("bad config");
        assertEquals("bad config", ex.getMessage());
    }

    @Test
    void messageConstructor_hasNoCause() {
        ConfigException ex = new ConfigException("oops");
        assertNull(ex.getCause());
    }

    @Test
    void messageCauseConstructor_storesMessage() {
        RuntimeException cause = new RuntimeException("root");
        ConfigException ex = new ConfigException("wrapped", cause);
        assertEquals("wrapped", ex.getMessage());
    }

    @Test
    void messageCauseConstructor_storesCause() {
        RuntimeException cause = new RuntimeException("root");
        ConfigException ex = new ConfigException("wrapped", cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void isCheckedException() {
        assertInstanceOf(Exception.class, new ConfigException("x"));
    }
}
