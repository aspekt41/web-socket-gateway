package net.aspekt.gateway.udp;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import net.aspekt.gateway.ConnectionEndpoint;
import net.aspekt.gateway.udp.multicast.UdpMulticast;
import net.aspekt.gateway.udp.multicast.UdpMulticastConfig;
import net.aspekt.gateway.udp.multicast.UdpMulticastEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UdpMulticast}.
 *
 * <p>Tests cover the lifecycle methods and private helper that can be exercised
 * without binding a real UDP socket.
 */
class UdpMulticastTest {

    private static UdpMulticastConfig config(String label, String niName) {
        return new UdpMulticastConfig() {
            public String getLabel() {
                return label;
            }

            public String getGroup() {
                return "230.0.0.1";
            }

            public int getPort() {
                return 9004;
            }

            public String getBindAddress() {
                return "0.0.0.0";
            }

            public String getNetworkInterface() {
                return niName;
            }
        };
    }

    private static UdpMulticast make(String label, String niName) {
        UdpMulticastConfig cfg = config(label, niName);
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint(label, new InetSocketAddress("230.0.0.1", 9004));
        return new UdpMulticast(cfg, ep);
    }

    // -----------------------------------------------------------------------
    // getEndpoint
    // -----------------------------------------------------------------------

    @Test
    void getEndpointReturnsEndpointSuppliedAtConstruction() {
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("mc", new InetSocketAddress("230.0.0.1", 9004));
        UdpMulticast multicast = new UdpMulticast(config("mc", null), ep);
        ConnectionEndpoint returned = multicast.getEndpoint();
        assertSame(ep, returned);
    }

    // -----------------------------------------------------------------------
    // stop() — null channel and null group (unstarted instance)
    // -----------------------------------------------------------------------

    @Test
    void stopOnUnstartedInstanceDoesNotThrow() {
        UdpMulticast multicast = make("mc", null);
        assertDoesNotThrow(multicast::stop);
    }

    // -----------------------------------------------------------------------
    // close() — delegates to stop()
    // -----------------------------------------------------------------------

    @Test
    void closeOnUnstartedInstanceDoesNotThrow() {
        UdpMulticast multicast = make("mc", null);
        assertDoesNotThrow(multicast::close);
    }

    // -----------------------------------------------------------------------
    // awaitShutdown() — null channel (unstarted instance)
    // -----------------------------------------------------------------------

    @Test
    void awaitShutdownWithNullChannelDoesNotBlock() throws InterruptedException {
        UdpMulticast multicast = make("mc", null);
        // Should return immediately since channel is null.
        multicast.awaitShutdown();
    }

    // -----------------------------------------------------------------------
    // resolveNetworkInterface() — via reflection
    // -----------------------------------------------------------------------

    private static Object invokeResolveNetworkInterface(UdpMulticast multicast) throws Exception {
        Method m = UdpMulticast.class.getDeclaredMethod("resolveNetworkInterface");
        m.setAccessible(true);
        return m.invoke(multicast);
    }

    @Test
    void resolveNetworkInterfaceReturnsNullWhenNotConfigured() throws Exception {
        UdpMulticast multicast = make("mc", null);
        Object ni = invokeResolveNetworkInterface(multicast);
        assertNull(ni, "null NI name should return null NetworkInterface");
    }

    @Test
    void resolveNetworkInterfaceThrowsForUnknownInterfaceName() {
        UdpMulticast multicast = make("mc", "nonexistent-interface-xyz-99");
        InvocationTargetException ex =
                assertThrows(InvocationTargetException.class, () -> invokeResolveNetworkInterface(multicast));
        assertInstanceOf(
                IllegalArgumentException.class, ex.getCause(), "unknown NI name should throw IllegalArgumentException");
    }

    @Test
    void resolveNetworkInterfaceReturnsInterfaceWhenNameIsValid() throws Exception {
        // Use any NI that is guaranteed to exist on this OS. On Linux 'lo' is always present.
        java.net.NetworkInterface loopback = java.net.NetworkInterface.getByName("lo");
        org.junit.jupiter.api.Assumptions.assumeTrue(loopback != null, "No 'lo' interface — skipping");

        UdpMulticast multicast = make("mc", "lo");
        Object ni = invokeResolveNetworkInterface(multicast);
        assertNotNull(ni, "valid NI name should return a NetworkInterface");
        assertInstanceOf(java.net.NetworkInterface.class, ni);
    }
}
