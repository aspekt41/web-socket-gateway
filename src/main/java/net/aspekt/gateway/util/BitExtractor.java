package net.aspekt.gateway.util;

import java.math.BigInteger;

/**
 * Utility class for extracting an arbitrary run of bits from a byte array.
 *
 * <p>All positions and lengths are measured in bits. Bits within each byte are
 * numbered in big-endian order: bit 0 is the most-significant bit of byte 0,
 * bit 7 is the least-significant bit of byte 0, bit 8 is the most-significant
 * bit of byte 1, and so on.
 */
public final class BitExtractor {

    private BitExtractor() {}

    /**
     * Extracts {@code length} bits starting at bit offset {@code startBit} from
     * {@code data} and returns them right-aligned in a {@code long}.
     *
     * <p>The maximum supported {@code length} is 64 bits.
     *
     * @param data     the source byte array; must not be {@code null}
     * @param startBit the zero-based index of the first bit to extract
     * @param length   the number of bits to extract (1–64)
     * @return the extracted bits as an unsigned value in the low-order bits of a
     *     {@code long}
     * @throws IllegalArgumentException if {@code data} is {@code null}, {@code
     *     length} is less than 1 or greater than 64, {@code startBit} is
     *     negative, or the requested range extends beyond the end of {@code data}
     */
    public static long extractBits(byte[] data, int startBit, int length) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (length < 1 || length > 64) {
            throw new IllegalArgumentException("length must be between 1 and 64, got: " + length);
        }
        if (startBit < 0) {
            throw new IllegalArgumentException("startBit must not be negative, got: " + startBit);
        }
        // Exclusive end bit position.
        long endBit = (long) startBit + length;
        long totalBits = (long) data.length * 8;
        if (endBit > totalBits) {
            throw new IllegalArgumentException("Requested range [" + startBit + ", " + endBit + ") exceeds array"
                    + " capacity of "
                    + totalBits
                    + " bits ("
                    + data.length
                    + " bytes)");
        }

        long result = 0L;
        int bitsRemaining = length;
        int currentBit = startBit;

        while (bitsRemaining > 0) {
            int byteIndex = currentBit / 8;
            // Bit position within the current byte (0 = MSB, 7 = LSB).
            int bitOffsetInByte = currentBit % 8;
            // How many bits we can take from this byte before hitting its LSB.
            int bitsAvailableInByte = 8 - bitOffsetInByte;
            int bitsToRead = Math.min(bitsAvailableInByte, bitsRemaining);

            // Isolate the relevant bits from this byte.
            int shift = bitsAvailableInByte - bitsToRead;
            int mask = (1 << bitsToRead) - 1;
            int bits = (Byte.toUnsignedInt(data[byteIndex]) >> shift) & mask;

            result = (result << bitsToRead) | bits;
            currentBit += bitsToRead;
            bitsRemaining -= bitsToRead;
        }

        return result;
    }

    /**
     * Extracts {@code length} bits starting at bit offset {@code startBit} from
     * {@code data} and returns them right-aligned in a {@link BigInteger}.
     *
     * <p>Use this method when {@code length} may exceed 64. For extractions of 64
     * bits or fewer, prefer {@link #extractBits(byte[], int, int)} which is faster.
     *
     * @param data     the source byte array; must not be {@code null}
     * @param startBit the zero-based index of the first bit to extract
     * @param length   the number of bits to extract (1 or more)
     * @return the extracted bits as a non-negative {@link BigInteger}
     * @throws IllegalArgumentException if {@code data} is {@code null}, {@code
     *     length} is less than 1, {@code startBit} is negative, or the requested
     *     range extends beyond the end of {@code data}
     */
    public static BigInteger extractBigBits(byte[] data, int startBit, int length) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (length < 1) {
            throw new IllegalArgumentException("length must be at least 1, got: " + length);
        }
        if (startBit < 0) {
            throw new IllegalArgumentException("startBit must not be negative, got: " + startBit);
        }
        long endBit = (long) startBit + length;
        long totalBits = (long) data.length * 8;
        if (endBit > totalBits) {
            throw new IllegalArgumentException("Requested range [" + startBit + ", " + endBit + ") exceeds array"
                    + " capacity of "
                    + totalBits
                    + " bits ("
                    + data.length
                    + " bytes)");
        }

        BigInteger result = BigInteger.ZERO;
        int bitsRemaining = length;
        int currentBit = startBit;

        while (bitsRemaining > 0) {
            int byteIndex = currentBit / 8;
            int bitOffsetInByte = currentBit % 8;
            int bitsAvailableInByte = 8 - bitOffsetInByte;
            int bitsToRead = Math.min(bitsAvailableInByte, bitsRemaining);

            int shift = bitsAvailableInByte - bitsToRead;
            int mask = (1 << bitsToRead) - 1;
            int bits = (Byte.toUnsignedInt(data[byteIndex]) >> shift) & mask;

            result = result.shiftLeft(bitsToRead).or(BigInteger.valueOf(bits));
            currentBit += bitsToRead;
            bitsRemaining -= bitsToRead;
        }

        return result;
    }

    /**
     * Reverses the {@code bitCount} least-significant bits of {@code value} and
     * returns the result right-aligned in a {@code long}.
     *
     * <p>Bits above position {@code bitCount - 1} in {@code value} are ignored.
     * The returned value has those same bits zeroed.
     *
     * <p>Example: {@code reverseBits(0x1A, 4)} — low 4 bits of {@code 0x1A} are
     * {@code 1010}, reversed to {@code 0101} — returns {@code 0x05}.
     *
     * @param value    the source value; only the {@code bitCount} low-order bits are used
     * @param bitCount the number of significant bits to reverse (1–64)
     * @return the reversed bits right-aligned in a {@code long}
     * @throws IllegalArgumentException if {@code bitCount} is less than 1 or greater than 64
     */
    public static long reverseBits(long value, int bitCount) {
        if (bitCount < 1 || bitCount > 64) {
            throw new IllegalArgumentException("bitCount must be between 1 and 64, got: " + bitCount);
        }
        long result = 0L;
        for (int i = 0; i < bitCount; i++) {
            result = (result << 1) | (value & 1L);
            value >>>= 1;
        }
        return result;
    }
}
