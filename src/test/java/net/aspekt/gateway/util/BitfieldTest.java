package net.aspekt.gateway.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BitfieldTest {

    // -----------------------------------------------------------------------
    // Construction — happy path
    // -----------------------------------------------------------------------

    @Test
    void constructsWithValidArguments() {
        Bitfield bf = new Bitfield("flags", 4, 8);
        assertEquals("flags", bf.name());
        assertEquals(4, bf.startBit());
        assertEquals(8, bf.length());
    }

    @Test
    void startBitZeroIsValid() {
        Bitfield bf = new Bitfield("header", 0, 1);
        assertEquals(0, bf.startBit());
    }

    @Test
    void lengthOneIsValid() {
        Bitfield bf = new Bitfield("bit", 0, 1);
        assertEquals(1, bf.length());
    }

    @Test
    void largeLengthIsValid() {
        // lengths above 64 are permitted for use with extractBigBits
        Bitfield bf = new Bitfield("wide", 0, 128);
        assertEquals(128, bf.length());
    }

    // -----------------------------------------------------------------------
    // Construction — validation
    // -----------------------------------------------------------------------

    @Test
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Bitfield(null, 0, 8));
    }

    @Test
    void blankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Bitfield("   ", 0, 8));
    }

    @Test
    void emptyNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Bitfield("", 0, 8));
    }

    @Test
    void negativeStartBitThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Bitfield("f", -1, 8));
    }

    @Test
    void zeroLengthThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Bitfield("f", 0, 0));
    }

    @Test
    void negativeLengthThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Bitfield("f", 0, -1));
    }

    // -----------------------------------------------------------------------
    // Record semantics
    // -----------------------------------------------------------------------

    @Test
    void equalityByValue() {
        assertEquals(new Bitfield("crc", 16, 8), new Bitfield("crc", 16, 8));
    }

    @Test
    void inequalityOnName() {
        assertNotEquals(new Bitfield("a", 0, 8), new Bitfield("b", 0, 8));
    }

    @Test
    void inequalityOnStartBit() {
        assertNotEquals(new Bitfield("f", 0, 8), new Bitfield("f", 1, 8));
    }

    @Test
    void inequalityOnLength() {
        assertNotEquals(new Bitfield("f", 0, 4), new Bitfield("f", 0, 8));
    }

    @Test
    void hashCodeConsistentWithEquality() {
        Bitfield a = new Bitfield("crc", 16, 8);
        Bitfield b = new Bitfield("crc", 16, 8);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toStringContainsFields() {
        String s = new Bitfield("crc", 16, 8).toString();
        assertTrue(s.contains("crc"));
        assertTrue(s.contains("16"));
        assertTrue(s.contains("8"));
    }
}
