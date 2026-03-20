package net.aspekt.gateway.util;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class BitcodeTest {

    // -----------------------------------------------------------------------
    // OfLong — construction
    // -----------------------------------------------------------------------

    @Test
    void ofLongConstructsViaFactory() {
        Bitcode bc = Bitcode.of("ACK", 0x06L);
        assertInstanceOf(Bitcode.OfLong.class, bc);
        assertEquals("ACK", bc.name());
        assertEquals(0x06L, ((Bitcode.OfLong) bc).value());
    }

    @Test
    void ofLongZeroValueIsValid() {
        assertEquals(0L, ((Bitcode.OfLong) Bitcode.of("NONE", 0L)).value());
    }

    @Test
    void ofLongNegativeValueIsValid() {
        assertEquals(-1L, ((Bitcode.OfLong) Bitcode.of("ALL_ONES", -1L)).value());
    }

    @Test
    void ofLongMaxValueIsValid() {
        assertEquals(Long.MAX_VALUE, ((Bitcode.OfLong) Bitcode.of("MAX", Long.MAX_VALUE)).value());
    }

    @Test
    void ofLongMinValueIsValid() {
        assertEquals(Long.MIN_VALUE, ((Bitcode.OfLong) Bitcode.of("MIN", Long.MIN_VALUE)).value());
    }

    // -----------------------------------------------------------------------
    // OfLong — validation
    // -----------------------------------------------------------------------

    @Test
    void ofLongNullNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> Bitcode.of(null, 0L));
    }

    @Test
    void ofLongBlankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> Bitcode.of("   ", 0L));
    }

    @Test
    void ofLongEmptyNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> Bitcode.of("", 0L));
    }

    // -----------------------------------------------------------------------
    // OfLong — record semantics
    // -----------------------------------------------------------------------

    @Test
    void ofLongEqualityByValue() {
        assertEquals(Bitcode.of("ACK", 0x06L), Bitcode.of("ACK", 0x06L));
    }

    @Test
    void ofLongInequalityOnName() {
        assertNotEquals(Bitcode.of("ACK", 0x06L), Bitcode.of("NAK", 0x06L));
    }

    @Test
    void ofLongInequalityOnValue() {
        assertNotEquals(Bitcode.of("ACK", 0x06L), Bitcode.of("ACK", 0x07L));
    }

    @Test
    void ofLongHashCodeConsistentWithEquality() {
        assertEquals(
                Bitcode.of("ACK", 0x06L).hashCode(), Bitcode.of("ACK", 0x06L).hashCode());
    }

    @Test
    void ofLongToStringContainsFields() {
        String s = Bitcode.of("ACK", 0x06L).toString();
        assertTrue(s.contains("ACK"));
        assertTrue(s.contains("6"));
    }

    // -----------------------------------------------------------------------
    // OfBig — construction
    // -----------------------------------------------------------------------

    @Test
    void ofBigConstructsViaFactory() {
        BigInteger v = BigInteger.valueOf(0x06L);
        Bitcode bc = Bitcode.of("ACK", v);
        assertInstanceOf(Bitcode.OfBig.class, bc);
        assertEquals("ACK", bc.name());
        assertEquals(v, ((Bitcode.OfBig) bc).value());
    }

    @Test
    void ofBigLargeValueIsValid() {
        BigInteger v = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
        assertEquals(v, ((Bitcode.OfBig) Bitcode.of("WIDE", v)).value());
    }

    // -----------------------------------------------------------------------
    // OfBig — validation
    // -----------------------------------------------------------------------

    @Test
    void ofBigNullNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> Bitcode.of(null, BigInteger.ONE));
    }

    @Test
    void ofBigBlankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> Bitcode.of("   ", BigInteger.ONE));
    }

    @Test
    void ofBigNullValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Bitcode.OfBig("f", null));
    }

    // -----------------------------------------------------------------------
    // OfBig — record semantics
    // -----------------------------------------------------------------------

    @Test
    void ofBigEqualityByValue() {
        assertEquals(Bitcode.of("X", BigInteger.TEN), Bitcode.of("X", BigInteger.TEN));
    }

    @Test
    void ofBigInequalityOnName() {
        assertNotEquals(Bitcode.of("A", BigInteger.TEN), Bitcode.of("B", BigInteger.TEN));
    }

    @Test
    void ofBigInequalityOnValue() {
        assertNotEquals(Bitcode.of("A", BigInteger.ONE), Bitcode.of("A", BigInteger.TEN));
    }

    @Test
    void ofBigHashCodeConsistentWithEquality() {
        assertEquals(
                Bitcode.of("X", BigInteger.TEN).hashCode(),
                Bitcode.of("X", BigInteger.TEN).hashCode());
    }

    // -----------------------------------------------------------------------
    // Cross-variant
    // -----------------------------------------------------------------------

    @Test
    void ofLongAndOfBigAreNotEqual() {
        // Different types — must not be equal even if numerically the same.
        assertNotEquals(Bitcode.of("f", 6L), Bitcode.of("f", BigInteger.valueOf(6)));
    }

    @Test
    void patternSwitchDispatchesCorrectly() {
        Bitcode longBc = Bitcode.of("a", 42L);
        Bitcode bigBc = Bitcode.of("b", BigInteger.valueOf(99));

        String longResult =
                switch (longBc) {
                    case Bitcode.OfLong bc -> "long:" + bc.value();
                    case Bitcode.OfBig bc -> "big:" + bc.value();
                };
        String bigResult =
                switch (bigBc) {
                    case Bitcode.OfLong bc -> "long:" + bc.value();
                    case Bitcode.OfBig bc -> "big:" + bc.value();
                };

        assertEquals("long:42", longResult);
        assertEquals("big:99", bigResult);
    }
}
