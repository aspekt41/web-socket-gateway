package net.aspekt.gateway.util;

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
}
