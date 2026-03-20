package net.aspekt.gateway.util;

/**
 * Describes a named field within a bit stream.
 *
 * @param name     the field name; must not be {@code null} or blank
 * @param startBit the zero-based index of the first bit of the field; must not be negative
 * @param length   the number of bits in the field (1–64 for {@code long} extraction,
 *     or larger when used with {@link BitExtractor#extractBigBits})
 */
public record Bitfield(String name, int startBit, int length) {

    public Bitfield {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (startBit < 0) {
            throw new IllegalArgumentException("startBit must not be negative, got: " + startBit);
        }
        if (length < 1) {
            throw new IllegalArgumentException("length must be at least 1, got: " + length);
        }
    }
}
