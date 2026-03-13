package net.aspekt.gateway.udp;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import net.aspekt.gateway.udp.multicast.UdpMulticast;
import net.aspekt.gateway.udp.multicast.UdpMulticastConfig;
import net.aspekt.gateway.udp.multicast.UdpMulticastEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for {@link UdpMulticast} that exercise the full
 * {@link UdpMulticast#start()} / {@link UdpMulticast#stop()} lifecycle.
 *
 * <p>These tests bind a real UDP datagram socket and join a multicast group.
 * If the environment does not support IP multicast the tests are skipped via
 * {@link org.junit.jupiter.api.Assumptions#assumeTrue}.
 */
class UdpMulticastIntegrationTest {

    private static final String MULTICAST_GROUP = "239.255.0.1"; // organisation-local scope

    private static int findFreeUdpPort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static UdpMulticastConfig config(String label, int port, String ni) {
        return new UdpMulticastConfig() {
            public String getLabel() {
                return label;
            }

            public String getGroup() {
                return MULTICAST_GROUP;
            }

            public int getPort() {
                return port;
            }

            public String getBindAddress() {
                return "0.0.0.0";
            }

            public String getNetworkInterface() {
                return ni;
            }
        };
    }

    // -----------------------------------------------------------------------
    // start() with null network interface (covers if(ni!=null) false paths)
    // stop() with non-null channel and group (covers if(channel!=null) true path
    //         and if(group!=null) true path)
    // awaitShutdown() with non-null channel (covers if(channel!=null) true path)
    // -----------------------------------------------------------------------

    @Test
    @Timeout(20)
    void startAndStopWithNullNetworkInterface() throws Exception {
        int port = findFreeUdpPort();
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("mc-test", new InetSocketAddress(MULTICAST_GROUP, port));
        UdpMulticast multicast = new UdpMulticast(config("mc-test", port, null), ep);

        try {
            multicast.start();
        } catch (Exception e) {
            // Skip if the environment does not support multicast.
            assumeTrue(false, "Multicast not available in this environment: " + e.getMessage());
        }

        // Channel is now set — exercise send path on the endpoint.
        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {0x01, 0x02});
        ep.send(buf); // should write without throwing

        // stop() with non-null channel AND non-null group covers both true branches.
        multicast.stop();

        // awaitShutdown() with non-null (but now closed) channel — should return quickly.
        multicast.awaitShutdown();
    }

    // -----------------------------------------------------------------------
    // start() with a real network interface (covers if(ni!=null) true paths)
    // resolveNetworkInterface() valid-name branch
    // -----------------------------------------------------------------------

    @Test
    @Timeout(20)
    void startAndStopWithLoopbackNetworkInterface() throws Exception {
        // Find a real NI that supports multicast (loopback typically qualifies).
        NetworkInterface loopback = NetworkInterface.getByName("lo");
        assumeTrue(loopback != null, "No 'lo' interface available");
        assumeTrue(loopback.supportsMulticast(), "'lo' does not support multicast");

        int port = findFreeUdpPort();
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("mc-lo", new InetSocketAddress(MULTICAST_GROUP, port));
        UdpMulticast multicast = new UdpMulticast(config("mc-lo", port, "lo"), ep);

        try {
            multicast.start();
        } catch (Exception e) {
            assumeTrue(false, "Multicast with 'lo' not available: " + e.getMessage());
        }

        multicast.stop();
        multicast.awaitShutdown();
    }
}
