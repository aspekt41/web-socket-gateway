package net.aspekt.gateway.util;

import java.math.BigInteger;

/**
 * A named bit pattern value, backed by either a {@code long} or a {@link BigInteger}.
 *
 * <p>Use {@link #of(String, long)} for fields up to 64 bits — the value is stored
 * unboxed. Use {@link #of(String, BigInteger)} for wider fields.
 *
 * <p>Callers can branch exhaustively with a pattern-matching switch:
 *
 * <pre>{@code
 * switch (bitcode) {
 *     case Bitcode.OfLong(var name, var value) -> // long fast path
 *     case Bitcode.OfBig(var name, var value)  -> // BigInteger path
 * }
 * }</pre>
 */
public sealed interface Bitcode permits Bitcode.OfLong, Bitcode.OfBig {

    /** Returns the field name. */
    String name();

    /** A {@link Bitcode} backed by an unboxed {@code long}. */
    record OfLong(String name, long value) implements Bitcode {
        public OfLong {
            requireValidName(name);
        }
    }

    /** A {@link Bitcode} backed by a {@link BigInteger}, for fields wider than 64 bits. */
    record OfBig(String name, BigInteger value) implements Bitcode {
        public OfBig {
            requireValidName(name);
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
        }
    }

    /** Creates a {@link OfLong} bitcode. */
    static Bitcode of(String name, long value) {
        return new OfLong(name, value);
    }

    /** Creates a {@link OfBig} bitcode. */
    static Bitcode of(String name, BigInteger value) {
        return new OfBig(name, value);
    }

    private static void requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
    }
}
