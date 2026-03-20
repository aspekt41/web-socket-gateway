package net.aspekt.gateway.util;

import java.math.BigInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Rough timing comparison between the {@code long}-based and {@link BigInteger}-based extraction
 * paths. This is not a rigorous JMH benchmark — results will vary with JVM warm-up state and GC
 * pressure — but it gives a practical sense of the relative cost.
 *
 * <p>Results are written to the JUL logger so they appear in the Gradle test output when run with
 * {@code --info}.
 */
class BitExtractorPerfTest {

    private static final Logger log = Logger.getLogger(BitExtractorPerfTest.class.getName());

    private static final int WARMUP = 200_000;
    private static final int ITERATIONS = 1_000_000;

    // 16-byte source array used for all extractions.
    private static final byte[] DATA = {
        0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
        0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10
    };

    @Test
    void compareLongVsBigIntegerExtraction() {
        // --- warm up both paths so JIT compilation is not measured ---
        for (int i = 0; i < WARMUP; i++) {
            BitExtractor.extractBits(DATA, 4, 32);
            BitExtractor.extractBigBits(DATA, 4, 32);
        }

        // --- long path ---
        // Accumulate into a sink variable so the JIT cannot eliminate the loop.
        long longSink = 0L;
        long longStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            longSink ^= BitExtractor.extractBits(DATA, 4, 32);
        }
        long longNs = System.nanoTime() - longStart;

        // --- BigInteger path ---
        BigInteger bigSink = BigInteger.ZERO;
        long bigStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            bigSink = bigSink.xor(BitExtractor.extractBigBits(DATA, 4, 32));
        }
        long bigNs = System.nanoTime() - bigStart;

        // Prevent the sink variables from being optimised away.
        log.finest("sinks: " + longSink + " / " + bigSink);

        double longMs = longNs / 1_000_000.0;
        double bigMs = bigNs / 1_000_000.0;
        double ratio = (double) bigNs / longNs;

        log.info(String.format(
                "%n--- BitExtractor performance (%,d iterations) ---%n"
                        + "  long       : %8.1f ms  (%5.1f ns/op)%n"
                        + "  BigInteger : %8.1f ms  (%5.1f ns/op)%n"
                        + "  ratio      : BigInteger is %.2fx slower than long",
                ITERATIONS, longMs, (double) longNs / ITERATIONS, bigMs, (double) bigNs / ITERATIONS, ratio));
    }
}
