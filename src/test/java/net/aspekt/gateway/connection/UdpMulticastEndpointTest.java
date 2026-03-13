package net.aspekt.gateway.connection;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import java.net.InetSocketAddress;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import net.aspekt.gateway.udp.multicast.UdpMulticastEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UdpMulticastEndpoint}.
 */
class UdpMulticastEndpointTest {

    private static final InetSocketAddress GROUP = new InetSocketAddress("230.0.0.1", 9004);

    // -----------------------------------------------------------------------
    // getLabel
    // -----------------------------------------------------------------------

    @Test
    void getLabelReturnsConstructorValue() {
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("udp-test", GROUP);
        assertEquals("udp-test", ep.getLabel());
    }

    // -----------------------------------------------------------------------
    // send() — channel absent or inactive
    // -----------------------------------------------------------------------

    @Test
    void sendReleasesBufferWhenChannelIsNull() {
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("udp-test", GROUP);
        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {0x01});
        assertDoesNotThrow(() -> ep.send(buf));
        assertEquals(0, buf.refCnt(), "buffer should be released when channel is null");
    }

    @Test
    void sendReleasesBufferWhenChannelIsInactive() throws Exception {
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("udp-test", GROUP);
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.setChannel(ch);
        ch.close().sync();

        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {0x02});
        assertDoesNotThrow(() -> ep.send(buf));
        assertEquals(0, buf.refCnt(), "buffer should be released when channel is inactive");
    }

    // -----------------------------------------------------------------------
    // send() — active channel
    // -----------------------------------------------------------------------

    @Test
    void sendWritesDatagramPacketToActiveChannel() {
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("udp-test", GROUP);
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.setChannel(ch);

        byte[] data = {0x0A, 0x0B, 0x0C};
        ep.send(Unpooled.copiedBuffer(data));

        DatagramPacket packet = ch.readOutbound();
        assertNotNull(packet, "channel should have received a DatagramPacket");
        assertEquals(GROUP, packet.recipient(), "packet should be addressed to multicast group");

        byte[] received = new byte[packet.content().readableBytes()];
        packet.content().readBytes(received);
        assertArrayEquals(data, received, "packet payload should match sent data");
        packet.release();

        ch.close();
    }

    // -----------------------------------------------------------------------
    // setChannel — replaces previous channel
    // -----------------------------------------------------------------------

    @Test
    void setChannelReplacesExistingChannel() {
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("udp-test", GROUP);
        EmbeddedChannel ch1 = new EmbeddedChannel();
        EmbeddedChannel ch2 = new EmbeddedChannel();

        ep.setChannel(ch1);
        ep.setChannel(ch2);

        // After replacing, send goes to ch2.
        byte[] data = {0x42};
        ep.send(Unpooled.copiedBuffer(data));

        assertNull(ch1.readOutbound(), "ch1 should not receive data after replacement");
        assertNotNull(ch2.readOutbound(), "ch2 should receive data after replacement");

        ch1.close();
        ch2.close();
    }

    // -----------------------------------------------------------------------
    // onDataReceived() — fan-out (exercised via AbstractConnectionEndpoint)
    // -----------------------------------------------------------------------

    @Test
    void onDataReceivedWithNoTargetsReleasesBuffer() {
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("udp-test", GROUP);
        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {0x01});
        ep.onDataReceived(buf);
        assertEquals(0, buf.refCnt(), "buffer should be released when there are no targets");
    }

    @Test
    void onDataReceivedFansOutToRegisteredTargets() {
        UdpMulticastEndpoint source = new UdpMulticastEndpoint("udp-src", GROUP);

        TcpClientEndpoint target1 = new TcpClientEndpoint("tcp-1");
        EmbeddedChannel ch1 = new EmbeddedChannel();
        target1.setChannel(ch1);

        TcpClientEndpoint target2 = new TcpClientEndpoint("tcp-2");
        EmbeddedChannel ch2 = new EmbeddedChannel();
        target2.setChannel(ch2);

        source.addTarget(target1);
        source.addTarget(target2);

        byte[] data = {(byte) 0xAB, (byte) 0xCD};
        source.onDataReceived(Unpooled.copiedBuffer(data));

        assertNotNull(ch1.readOutbound(), "target1 should receive data");
        assertNotNull(ch2.readOutbound(), "target2 should receive data");

        ch1.close();
        ch2.close();
    }
}
