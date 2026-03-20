package net.aspekt.gateway.util;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class BitExtractorTest {

    // -----------------------------------------------------------------------
    // Convenience: build a byte array from an explicit list of int values so
    // test cases remain readable without repeated (byte) casts.
    // -----------------------------------------------------------------------
    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    // -----------------------------------------------------------------------
    // Argument validation
    // -----------------------------------------------------------------------

    @Test
    void nullDataThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.extractBits(null, 0, 8));
    }

    @Test
    void zeroLengthThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.extractBits(bytes(0xFF), 0, 0));
    }

    @Test
    void lengthOver64Throws() {
        byte[] data = new byte[9];
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.extractBits(data, 0, 65));
    }

    @Test
    void negativeStartBitThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.extractBits(bytes(0xFF), -1, 4));
    }

    @Test
    void rangeExceedsArrayThrows() {
        // 1-byte array has 8 bits; starting at bit 1 and reading 8 bits needs bit 8
        // which doesn't exist.
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.extractBits(bytes(0xFF), 1, 8));
    }

    @Test
    void startBitAtExactEndThrows() {
        // Starting at bit 8 of a 1-byte array is already out of range for any length.
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.extractBits(bytes(0xFF), 8, 1));
    }

    // -----------------------------------------------------------------------
    // Byte-aligned reads
    // -----------------------------------------------------------------------

    @Test
    void readFullByte() {
        assertEquals(0xABL, BitExtractor.extractBits(bytes(0xAB), 0, 8));
    }

    @Test
    void readSecondFullByte() {
        assertEquals(0xCDL, BitExtractor.extractBits(bytes(0xAB, 0xCD), 8, 8));
    }

    @Test
    void readAllBytesAs64Bits() {
        // 8 bytes → 64 bits; result must equal the big-endian long.
        byte[] data = bytes(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);
        long expected = 0x0102030405060708L;
        assertEquals(expected, BitExtractor.extractBits(data, 0, 64));
    }

    @Test
    void readSingleByteLowNibble() {
        // Bits 4–7 of 0xAB (= 1010 1011) are 1011 = 0xB.
        assertEquals(0xBL, BitExtractor.extractBits(bytes(0xAB), 4, 4));
    }

    @Test
    void readSingleByteHighNibble() {
        // Bits 0–3 of 0xAB are 1010 = 0xA.
        assertEquals(0xAL, BitExtractor.extractBits(bytes(0xAB), 0, 4));
    }

    // -----------------------------------------------------------------------
    // Single-bit reads
    // -----------------------------------------------------------------------

    @Test
    void readSingleBitSetInMSB() {
        // 0x80 = 1000 0000; bit 0 is 1.
        assertEquals(1L, BitExtractor.extractBits(bytes(0x80), 0, 1));
    }

    @Test
    void readSingleBitClearInMSB() {
        // 0x7F = 0111 1111; bit 0 is 0.
        assertEquals(0L, BitExtractor.extractBits(bytes(0x7F), 0, 1));
    }

    @Test
    void readSingleBitInLSB() {
        // 0x01 = 0000 0001; bit 7 (LSB) is 1.
        assertEquals(1L, BitExtractor.extractBits(bytes(0x01), 7, 1));
    }

    @Test
    void readSingleBitAcrossByteInSecondByte() {
        // data = {0x00, 0x80}: bit 8 is the MSB of the second byte → 1.
        assertEquals(1L, BitExtractor.extractBits(bytes(0x00, 0x80), 8, 1));
    }

    // -----------------------------------------------------------------------
    // Cross-byte reads (not on byte boundaries)
    // -----------------------------------------------------------------------

    @Test
    void readCrossByteTwoBitsStraddlingBoundary() {
        // data = {0x01, 0x80}: bits 7–8 are: (LSB of byte 0)=1, (MSB of byte 1)=1
        // → binary 11 = 3.
        assertEquals(3L, BitExtractor.extractBits(bytes(0x01, 0x80), 7, 2));
    }

    @Test
    void readCrossThreeBytesUnaligned() {
        // data = {0xFF, 0x00, 0xFF}
        // Extract bits 4..19 (16 bits):
        //   byte 0 bits 4-7 = 0xF  (4 bits)
        //   byte 1 bits 0-7 = 0x00 (8 bits)
        //   byte 2 bits 0-3 = 0xF  (4 bits)
        // Result = 0xF00F.
        assertEquals(0xF00FL, BitExtractor.extractBits(bytes(0xFF, 0x00, 0xFF), 4, 16));
    }

    @Test
    void readUnaligned31Bits() {
        // Example from the specification: extractBits(data, 2, 31).
        // data = {0xFF, 0xFF, 0xFF, 0xFF, 0x80}
        // bit layout across the first 4 bytes (32 bits total):
        //   1111 1111  1111 1111  1111 1111  1111 1111
        // Bits 2..32 (31 bits) = 31 ones = 0x7FFFFFFF.
        byte[] data = bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x80);
        assertEquals(0x7FFFFFFFL, BitExtractor.extractBits(data, 2, 31));
    }

    @Test
    void readUnaligned31BitsWithKnownPattern() {
        // data bytes (hex): 0x1A 0x2B 0x3C 0x4D 0x5E
        //   = 0001 1010 | 0010 1011 | 0011 1100 | 0100 1101 | 0101 1110  (40 bits)
        // startBit=2, length=31 → bits [2..32] inclusive.
        //   bit2..7  = 011010                       (6 bits from byte 0)
        //   bit8..15 = 00101011                     (8 bits = byte 1)
        //   bit16..23 = 00111100                    (8 bits = byte 2)
        //   bit24..31 = 01001101                    (8 bits = byte 3)
        //   bit32     = 0                           (1 bit = MSB of byte 4)
        //   concatenated: 0110 1000 1010 1100 1111 0001 0011 010
        //   padded to 32: 0011 0100 0101 0110 0111 1000 1001 1010 = 0x3456789A
        byte[] data = bytes(0x1A, 0x2B, 0x3C, 0x4D, 0x5E);
        assertEquals(0x3456789AL, BitExtractor.extractBits(data, 2, 31));
    }

    @Test
    void readAcrossFourBytesOddStartAndLength() {
        // data = {0x00, 0xFF, 0xFF, 0x00}
        // Extract bits 6..21 (16 bits):
        //   byte 0 bits 6-7: 00 (2 bits)
        //   byte 1 bits 0-7: 11111111 (8 bits)
        //   byte 2 bits 0-5: 111111 (6 bits)
        //   result = 00_11111111_111111 = 0b0011111111111111 = 0x3FFF
        assertEquals(0x3FFFL, BitExtractor.extractBits(bytes(0x00, 0xFF, 0xFF, 0x00), 6, 16));
    }

    @Test
    void readLastBitOfArray() {
        // 2-byte array; bit 15 is the LSB of the second byte.
        // 0x01 = 0000 0001; LSB is 1.
        assertEquals(1L, BitExtractor.extractBits(bytes(0x00, 0x01), 15, 1));
    }

    @Test
    void readMaxLength64BitsFromLargerArray() {
        // Extract 64 bits starting at bit 8 (= byte 1 through byte 8).
        byte[] data = bytes(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x00);
        long expected = 0x0102030405060708L;
        assertEquals(expected, BitExtractor.extractBits(data, 8, 64));
    }

    @Test
    void readUnalignedStartIn64BitExtraction() {
        // data = {0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80}
        // Extract 64 bits starting at bit 1:
        //   byte 0 bits 1-7: 1111111 (7 bits) → occupies bits 63..57 of result
        //   bytes 1-7: all zeros (56 bits)    → bits 56..1 of result = 0
        //   byte 8 bit 0 (MSB of 0x80): 1    → bit 0 of result = 1
        //   result = 1111 1110 0000...0001 = 0xFE00000000000001L
        byte[] data = bytes(0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80);
        assertEquals(0xFE00000000000001L, BitExtractor.extractBits(data, 1, 64));
    }

    @Test
    void readHighBitPatternPreservesSignlessness() {
        // The MSB of the returned long will be 1; ensure we don't sign-extend.
        // data = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF}; read all 64 bits.
        byte[] data = bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
        assertEquals(0xFFFFFFFFFFFFFFFFL, BitExtractor.extractBits(data, 0, 64));
    }

    @Test
    void readSingleBitLength1FromOddOffset() {
        // data = {0b00010000} = 0x10; bit 3 = 1.
        assertEquals(1L, BitExtractor.extractBits(bytes(0x10), 3, 1));
        // bit 4 = 0
        assertEquals(0L, BitExtractor.extractBits(bytes(0x10), 4, 1));
    }

    @Test
    void readBitsExactlyAtArrayBoundary() {
        // 3-byte array; read all 24 bits starting at 0.
        byte[] data = bytes(0xDE, 0xAD, 0xBE);
        assertEquals(0xDEADBEL, BitExtractor.extractBits(data, 0, 24));
    }

    // -----------------------------------------------------------------------
    // extractBigBits — argument validation
    // -----------------------------------------------------------------------

    @Test
    void bigNullDataThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.extractBigBits(null, 0, 8));
    }

    @Test
    void bigZeroLengthThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.extractBigBits(bytes(0xFF), 0, 0));
    }

    @Test
    void bigNegativeStartBitThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.extractBigBits(bytes(0xFF), -1, 4));
    }

    @Test
    void bigRangeExceedsArrayThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.extractBigBits(bytes(0xFF), 1, 8));
    }

    // -----------------------------------------------------------------------
    // extractBigBits — parity with extractBits for ≤64-bit cases
    // -----------------------------------------------------------------------

    @Test
    void bigReadFullByte() {
        assertEquals(BigInteger.valueOf(0xABL), BitExtractor.extractBigBits(bytes(0xAB), 0, 8));
    }

    @Test
    void bigReadAllBytesAs64Bits() {
        byte[] data = bytes(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);
        assertEquals(new BigInteger("0102030405060708", 16), BitExtractor.extractBigBits(data, 0, 64));
    }

    @Test
    void bigReadCrossByteTwoBitsStraddlingBoundary() {
        assertEquals(BigInteger.valueOf(3L), BitExtractor.extractBigBits(bytes(0x01, 0x80), 7, 2));
    }

    @Test
    void bigReadHighBitPattern() {
        // All 64 bits set — value is 2^64 - 1, which BigInteger handles without sign issues.
        byte[] data = bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
        BigInteger expected = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        assertEquals(expected, BitExtractor.extractBigBits(data, 0, 64));
    }

    // -----------------------------------------------------------------------
    // extractBigBits — >64-bit extractions
    // -----------------------------------------------------------------------

    @Test
    void bigRead72BitsAligned() {
        // 9 bytes, all read → 72-bit value 0x010203040506070809.
        byte[] data = bytes(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09);
        BigInteger expected = new BigInteger("010203040506070809", 16);
        assertEquals(expected, BitExtractor.extractBigBits(data, 0, 72));
    }

    @Test
    void bigRead128BitsAligned() {
        // 16 bytes of known pattern.
        byte[] data =
                bytes(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F);
        BigInteger expected = new BigInteger("000102030405060708090A0B0C0D0E0F", 16);
        assertEquals(expected, BitExtractor.extractBigBits(data, 0, 128));
    }

    @Test
    void bigRead80BitsUnaligned() {
        // 11 bytes; extract 80 bits starting at bit 8 (= bytes 1–10).
        byte[] data = bytes(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A);
        BigInteger expected = new BigInteger("0102030405060708090A", 16);
        assertEquals(expected, BitExtractor.extractBigBits(data, 8, 80));
    }

    @Test
    void bigRead65BitsStraddling64BitBoundary() {
        // 9-byte array: first 8 bytes = 0xFF, last byte = 0x80 (MSB set).
        // Extract 65 bits from bit 0: 64 ones followed by a 1 → value = 2^65 - 1.
        byte[] data = bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x80);
        BigInteger expected = BigInteger.ONE.shiftLeft(65).subtract(BigInteger.ONE);
        assertEquals(expected, BitExtractor.extractBigBits(data, 0, 65));
    }

    // -----------------------------------------------------------------------
    // reverseBits — argument validation
    // -----------------------------------------------------------------------

    @Test
    void reverseBitsZeroCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.reverseBits(0xFF, 0));
    }

    @Test
    void reverseBitsOver64Throws() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.reverseBits(0xFF, 65));
    }

    // -----------------------------------------------------------------------
    // reverseBits — correctness
    // -----------------------------------------------------------------------

    @Test
    void reverseBitsExampleFromSpec() {
        // low 4 bits of 0x1A = 1010, reversed = 0101 = 0x05
        assertEquals(0x05L, BitExtractor.reverseBits(0x1A, 4));
    }

    @Test
    void reverseBitsSingleBitIsIdentity() {
        assertEquals(0L, BitExtractor.reverseBits(0L, 1));
        assertEquals(1L, BitExtractor.reverseBits(1L, 1));
    }

    @Test
    void reverseBitsPalindrome() {
        // 0b1001 reversed over 4 bits is still 0b1001
        assertEquals(0b1001L, BitExtractor.reverseBits(0b1001L, 4));
    }

    @Test
    void reverseBitsFullByte() {
        // 0xAB = 1010 1011, reversed = 1101 0101 = 0xD5
        assertEquals(0xD5L, BitExtractor.reverseBits(0xAB, 8));
    }

    @Test
    void reverseBitsIgnoresHighBits() {
        // High bits of value beyond bitCount must not affect the result.
        // 0xFF_1A: low 4 bits = 1010, reversed = 0101 = 0x05, same as plain 0x1A.
        assertEquals(0x05L, BitExtractor.reverseBits(0xFF_1AL, 4));
    }

    @Test
    void reverseBits64AllOnes() {
        // All 64 bits set, reversed = all 64 bits set.
        assertEquals(0xFFFFFFFFFFFFFFFFL, BitExtractor.reverseBits(0xFFFFFFFFFFFFFFFFL, 64));
    }

    @Test
    void reverseBits64KnownPattern() {
        // 0x8000000000000001L = 1000...0001 (64 bits), reversed = 1000...0001 (palindrome)
        assertEquals(0x8000000000000001L, BitExtractor.reverseBits(0x8000000000000001L, 64));
    }

    @Test
    void reverseBitsIsItsOwnInverse() {
        // reverseBits(reverseBits(v, n), n) == low n bits of v
        long value = 0x1234567890ABCDEFL;
        int n = 40;
        long mask = (1L << n) - 1;
        assertEquals(value & mask, BitExtractor.reverseBits(BitExtractor.reverseBits(value, n), n));
    }

    // -----------------------------------------------------------------------
    // reverseBigBits — argument validation
    // -----------------------------------------------------------------------

    @Test
    void reverseBigBitsNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.reverseBigBits(null, 4));
    }

    @Test
    void reverseBigBitsNegativeValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.reverseBigBits(BigInteger.valueOf(-1), 4));
    }

    @Test
    void reverseBigBitsZeroCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.reverseBigBits(BigInteger.ONE, 0));
    }

    // -----------------------------------------------------------------------
    // reverseBigBits — parity with reverseBits for ≤64-bit cases
    // -----------------------------------------------------------------------

    @Test
    void reverseBigBitsExampleFromSpec() {
        // low 4 bits of 0x1A = 1010, reversed = 0101 = 0x05
        assertEquals(BigInteger.valueOf(0x05L), BitExtractor.reverseBigBits(BigInteger.valueOf(0x1A), 4));
    }

    @Test
    void reverseBigBitsFullByte() {
        // 0xAB = 1010 1011, reversed over 8 bits = 1101 0101 = 0xD5
        assertEquals(BigInteger.valueOf(0xD5L), BitExtractor.reverseBigBits(BigInteger.valueOf(0xAB), 8));
    }

    @Test
    void reverseBigBitsIgnoresHighBits() {
        assertEquals(BigInteger.valueOf(0x05L), BitExtractor.reverseBigBits(BigInteger.valueOf(0xFF_1AL), 4));
    }

    // -----------------------------------------------------------------------
    // reverseBigBits — >64-bit cases
    // -----------------------------------------------------------------------

    @Test
    void reverseBigBits72Bits() {
        // 72-bit value: 0x800000000000000001 (bit 72 and bit 0 set)
        // reversed over 72 bits = same value (palindrome)
        BigInteger value = BigInteger.ONE.shiftLeft(71).or(BigInteger.ONE);
        assertEquals(value, BitExtractor.reverseBigBits(value, 72));
    }

    @Test
    void reverseBigBits128AllOnes() {
        // All 128 bits set, reversed = all 128 bits set.
        BigInteger allOnes = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
        assertEquals(allOnes, BitExtractor.reverseBigBits(allOnes, 128));
    }

    @Test
    void reverseBigBits128KnownPattern() {
        // 128-bit value with only the MSB set: 1 followed by 127 zeros.
        // Reversed over 128 bits = only the LSB set.
        BigInteger msb = BigInteger.ONE.shiftLeft(127);
        assertEquals(BigInteger.ONE, BitExtractor.reverseBigBits(msb, 128));
    }

    @Test
    void reverseBigBitsIsItsOwnInverse() {
        BigInteger value = new BigInteger("0102030405060708090A0B0C0D0E0F", 16);
        int n = 100;
        BigInteger mask = BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE);
        assertEquals(value.and(mask), BitExtractor.reverseBigBits(BitExtractor.reverseBigBits(value, n), n));
    }

    // -----------------------------------------------------------------------
    // extract(byte[], List<Bitfield>) — argument validation
    // -----------------------------------------------------------------------

    @Test
    void extractListNullDataThrows() {
        assertThrows(
                IllegalArgumentException.class, () -> BitExtractor.extract(null, List.of(new Bitfield("f", 0, 8))));
    }

    @Test
    void extractListNullFieldsThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitExtractor.extract(bytes(0xFF), null));
    }

    @Test
    void extractListFieldOutOfRangeThrows() {
        // 1-byte array; field requests bits 0–15 which exceeds the array.
        assertThrows(
                IllegalArgumentException.class,
                () -> BitExtractor.extract(bytes(0xFF), List.of(new Bitfield("f", 0, 16))));
    }

    // -----------------------------------------------------------------------
    // extract(byte[], List<Bitfield>) — correctness
    // -----------------------------------------------------------------------

    @Test
    void extractEmptyFieldListReturnsEmptyList() {
        assertTrue(BitExtractor.extract(bytes(0xFF), List.of()).isEmpty());
    }

    @Test
    void extractSingleField() {
        // 0xAB = 1010 1011; high nibble (bits 0–3) = 0xA, low nibble (bits 4–7) = 0xB
        byte[] data = bytes(0xAB);
        List<Bitcode> result = BitExtractor.extract(data, List.of(new Bitfield("high", 0, 4)));
        assertEquals(1, result.size());
        assertEquals(Bitcode.of("high", 0xAL), result.get(0));
    }

    @Test
    void extractMultipleFieldsRetainsOrder() {
        // data = {0xAB}: split into high nibble "hi" and low nibble "lo"
        byte[] data = bytes(0xAB);
        List<Bitfield> fields = List.of(new Bitfield("hi", 0, 4), new Bitfield("lo", 4, 4));
        List<Bitcode> result = BitExtractor.extract(data, fields);
        assertEquals(2, result.size());
        assertEquals(Bitcode.of("hi", 0xAL), result.get(0));
        assertEquals(Bitcode.of("lo", 0xBL), result.get(1));
    }

    @Test
    void extractFieldsWithDifferentSizes() {
        // data = {0xDE, 0xAD, 0xBE, 0xEF}
        // "word"  : bits  0–15 = 0xDEAD
        // "byte2" : bits 16–23 = 0xBE
        // "nibble": bits 24–27 = 0xE
        byte[] data = bytes(0xDE, 0xAD, 0xBE, 0xEF);
        List<Bitfield> fields =
                List.of(new Bitfield("word", 0, 16), new Bitfield("byte2", 16, 8), new Bitfield("nibble", 24, 4));
        List<Bitcode> result = BitExtractor.extract(data, fields);
        assertEquals(Bitcode.of("word", 0xDEADL), result.get(0));
        assertEquals(Bitcode.of("byte2", 0xBEL), result.get(1));
        assertEquals(Bitcode.of("nibble", 0xEL), result.get(2));
    }

    @Test
    void extractNamesAreRetained() {
        byte[] data = bytes(0xFF);
        List<Bitfield> fields = List.of(new Bitfield("alpha", 0, 4), new Bitfield("beta", 4, 4));
        List<Bitcode> result = BitExtractor.extract(data, fields);
        assertEquals("alpha", result.get(0).name());
        assertEquals("beta", result.get(1).name());
    }

    @Test
    void extractResultIsUnmodifiable() {
        List<Bitcode> result = BitExtractor.extract(bytes(0xFF), List.of(new Bitfield("f", 0, 8)));
        assertThrows(UnsupportedOperationException.class, () -> result.add(Bitcode.of("x", 0)));
    }
}
