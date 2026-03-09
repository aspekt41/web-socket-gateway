package com.gateway.config;

/**
 * Thrown when the gateway configuration file cannot be loaded or is invalid.
 */
public class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
