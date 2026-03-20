package net.aspekt.gateway.util;

/**
 * A named bit pattern value.
 *
 * @param name  the code name; must not be {@code null} or blank
 * @param value the bit pattern
 */
public record Bitcode(String name, long value) {

    public Bitcode {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
    }
}
