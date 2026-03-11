package com.gateway.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigException} — both constructors.
 */
class ConfigExceptionTest {

    @Test
    void messageOnlyConstructorPreservesMessage() {
        ConfigException ex = new ConfigException("bad config");
        assertEquals("bad config", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void messagePlusCauseConstructorPreservesBoth() {
        Throwable cause = new RuntimeException("root cause");
        ConfigException ex = new ConfigException("parse failed", cause);
        assertEquals("parse failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void configExceptionIsCheckedException() {
        // ConfigException extends Exception (checked), not RuntimeException
        assertTrue(Exception.class.isAssignableFrom(ConfigException.class));
        assertFalse(RuntimeException.class.isAssignableFrom(ConfigException.class));
    }
}
