package net.aspekt.gateway.udp.multicast;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import net.aspekt.gateway.AbstractConnectionEndpoint;
import org.junit.jupiter.api.Test;

class UdpMulticastHandlerTest {

    private static final InetSocketAddress GROUP = new InetSocketAddress("230.0.0.1", 4567);
    private static final InetSocketAddress SENDER = new InetSocketAddress("192.168.1.1", 12345);

    private static class CollectingEndpoint extends AbstractConnectionEndpoint {
        final List<ByteBuf> received = new ArrayList<>();

        CollectingEndpoint(String label) {
            super(label);
        }

        @Override
        public void send(ByteBuf buf) {
            received.add(buf);
        }
    }

    @Test
    void channelRead0_forwardsRetainedPayloadToEndpointTargets() {
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("udp", GROUP);
        CollectingEndpoint target = new CollectingEndpoint("tgt");
        ep.addTarget(target);

        EmbeddedChannel ch = new EmbeddedChannel(new UdpMulticastHandler(ep));

        ByteBuf payload = Unpooled.copiedBuffer(new byte[] {0xA, 0xB});
        DatagramPacket pkt = new DatagramPacket(payload, GROUP, SENDER);
        ch.writeInbound(pkt);

        assertEquals(1, target.received.size());
        target.received.get(0).release();
        ch.close();
    }

    @Test
    void channelRead0_withNoTargets_releasesBuf() {
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint("udp", GROUP);
        EmbeddedChannel ch = new EmbeddedChannel(new UdpMulticastHandler(ep));

        ByteBuf payload = Unpooled.copiedBuffer(new byte[] {1});
        DatagramPacket pkt = new DatagramPacket(payload, GROUP, SENDER);
        ch.writeInbound(pkt);

        // No targets → endpoint releases the retained buf immediately.
        assertEquals(0, payload.refCnt());
        ch.close();
    }
}
