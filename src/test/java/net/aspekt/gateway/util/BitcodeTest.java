package net.aspekt.gateway.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BitcodeTest {

    // -----------------------------------------------------------------------
    // Construction — happy path
    // -----------------------------------------------------------------------

    @Test
    void constructsWithValidArguments() {
        Bitcode bc = new Bitcode("ACK", 0x06L);
        assertEquals("ACK", bc.name());
        assertEquals(0x06L, bc.value());
    }

    @Test
    void zeroValueIsValid() {
        assertEquals(0L, new Bitcode("NONE", 0L).value());
    }

    @Test
    void negativeValueIsValid() {
        // long can hold any bit pattern, including those with the sign bit set
        assertEquals(-1L, new Bitcode("ALL_ONES", -1L).value());
    }

    @Test
    void maxLongValueIsValid() {
        assertEquals(Long.MAX_VALUE, new Bitcode("MAX", Long.MAX_VALUE).value());
    }

    @Test
    void minLongValueIsValid() {
        assertEquals(Long.MIN_VALUE, new Bitcode("MIN", Long.MIN_VALUE).value());
    }

    // -----------------------------------------------------------------------
    // Construction — validation
    // -----------------------------------------------------------------------

    @Test
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Bitcode(null, 0L));
    }

    @Test
    void blankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Bitcode("   ", 0L));
    }

    @Test
    void emptyNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Bitcode("", 0L));
    }

    // -----------------------------------------------------------------------
    // Record semantics
    // -----------------------------------------------------------------------

    @Test
    void equalityByValue() {
        assertEquals(new Bitcode("ACK", 0x06L), new Bitcode("ACK", 0x06L));
    }

    @Test
    void inequalityOnName() {
        assertNotEquals(new Bitcode("ACK", 0x06L), new Bitcode("NAK", 0x06L));
    }

    @Test
    void inequalityOnValue() {
        assertNotEquals(new Bitcode("ACK", 0x06L), new Bitcode("ACK", 0x07L));
    }

    @Test
    void hashCodeConsistentWithEquality() {
        assertEquals(new Bitcode("ACK", 0x06L).hashCode(), new Bitcode("ACK", 0x06L).hashCode());
    }

    @Test
    void toStringContainsFields() {
        String s = new Bitcode("ACK", 0x06L).toString();
        assertTrue(s.contains("ACK"));
        assertTrue(s.contains("6"));
    }
}
