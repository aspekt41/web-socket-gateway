package net.aspekt.gateway.udp.multicast;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class UdpMulticastEndpointTest {

    private static final InetSocketAddress GROUP = new InetSocketAddress("230.0.0.1", 4567);

    @Test
    void getLabel_returnsConstructorLabel() {
        assertEquals("udp", new UdpMulticastEndpoint("udp", GROUP).getLabel());
    }

    @Test
    void send_withNoChannel_releasesBuf() {
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("udp", GROUP);
        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(1);
        ep.send(buf);
        assertEquals(0, buf.refCnt());
    }

    @Test
    void send_withInactiveChannel_releasesBuf() throws InterruptedException {
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("udp", GROUP);
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.setChannel(ch);
        ch.close().sync();

        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(2);
        ep.send(buf);
        assertEquals(0, buf.refCnt());
    }

    @Test
    void send_withActiveChannel_wrapsInDatagramPacketAddressedToGroup() {
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("udp", GROUP);
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.setChannel(ch);

        ByteBuf payload = Unpooled.copiedBuffer(new byte[] {1, 2, 3});
        ep.send(payload);

        DatagramPacket pkt = (DatagramPacket) ch.readOutbound();
        assertNotNull(pkt);
        assertEquals(GROUP, pkt.recipient());
        pkt.release();
        ch.close();
    }
}
