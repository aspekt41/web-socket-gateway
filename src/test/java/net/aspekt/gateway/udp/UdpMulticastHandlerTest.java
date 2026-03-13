package net.aspekt.gateway.udp;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import java.net.InetSocketAddress;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import net.aspekt.gateway.udp.multicast.UdpMulticastEndpoint;
import net.aspekt.gateway.udp.multicast.UdpMulticastHandler;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UdpMulticastHandler}.
 *
 * <p>Uses Netty's {@link EmbeddedChannel} to drive {@code channelRead0} without
 * any real network activity.
 */
class UdpMulticastHandlerTest {

    private static final InetSocketAddress GROUP = new InetSocketAddress("230.0.0.1", 9004);
    private static final InetSocketAddress SENDER = new InetSocketAddress("127.0.0.1", 12345);

    // -----------------------------------------------------------------------
    // channelRead0 — retained buffer forwarded to endpoint targets
    // -----------------------------------------------------------------------

    @Test
    void channelRead0ForwardsPayloadToEndpointTargets() {
        UdpMulticastEndpoint endpoint = new UdpMulticastEndpoint("udp-test", GROUP);

        // Wire a downstream TCP target so we can observe fan-out.
        TcpClientEndpoint downstream = new TcpClientEndpoint("tcp-out");
        EmbeddedChannel tcpCh = new EmbeddedChannel();
        downstream.setChannel(tcpCh);
        endpoint.addTarget(downstream);

        // Build an EmbeddedChannel with the handler under test.
        EmbeddedChannel udpCh = new EmbeddedChannel(new UdpMulticastHandler(endpoint));

        byte[] data = {0x10, 0x20, 0x30};
        ByteBuf payload = Unpooled.copiedBuffer(data);
        // DatagramPacket takes ownership of the buffer.
        DatagramPacket packet = new DatagramPacket(payload, GROUP, SENDER);
        udpCh.writeInbound(packet);

        // The downstream TCP channel should have received the data.
        ByteBuf received = tcpCh.readOutbound();
        assertNotNull(received, "downstream target should have received data");
        byte[] receivedBytes = new byte[received.readableBytes()];
        received.readBytes(receivedBytes);
        assertArrayEquals(data, receivedBytes, "forwarded payload must match original");
        received.release();

        udpCh.finishAndReleaseAll();
        tcpCh.close();
    }

    @Test
    void channelRead0WithNoTargetsReleasesBuffer() {
        UdpMulticastEndpoint endpoint = new UdpMulticastEndpoint("udp-test", GROUP);
        // No targets registered — onDataReceived releases the buffer immediately.

        EmbeddedChannel udpCh = new EmbeddedChannel(new UdpMulticastHandler(endpoint));

        byte[] data = {0x42};
        ByteBuf payload = Unpooled.copiedBuffer(data);
        DatagramPacket packet = new DatagramPacket(payload, GROUP, SENDER);
        // Should not throw; buffer must be released cleanly.
        assertDoesNotThrow(() -> udpCh.writeInbound(packet));

        udpCh.finishAndReleaseAll();
    }
}
