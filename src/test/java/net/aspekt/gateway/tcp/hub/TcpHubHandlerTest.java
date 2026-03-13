package net.aspekt.gateway.tcp.hub;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class TcpHubHandlerTest {

    @Test
    void channelActive_registersChannelWithEndpoint() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub");
        EmbeddedChannel ch = new EmbeddedChannel(new TcpHubHandler(ep));

        // After channelActive the channel is in the hub's group; send() delivers to it.
        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {1});
        ep.send(buf);

        ByteBuf out = (ByteBuf) ch.readOutbound();
        assertNotNull(out);
        out.release();
        ch.close();
    }

    @Test
    void channelRead_forwardsDataToHubEndpointOnDataReceived() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub");
        EmbeddedChannel ch = new EmbeddedChannel(new TcpHubHandler(ep));

        // Write an inbound ByteBuf; with no peers and no targets the hub releases it.
        ByteBuf inbound = Unpooled.copiedBuffer(new byte[] {7, 8});
        ch.writeInbound(inbound);
        // No exception and buf is consumed (released by hub since no peers / targets).
        assertEquals(0, inbound.refCnt());
        ch.close();
    }

    @Test
    void channelInactive_doesNotThrow() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub");
        EmbeddedChannel ch = new EmbeddedChannel(new TcpHubHandler(ep));
        assertDoesNotThrow(() -> ch.close().sync());
    }

    @Test
    void exceptionCaught_closesChannel() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub");
        EmbeddedChannel ch = new EmbeddedChannel(new TcpHubHandler(ep));
        ch.pipeline().fireExceptionCaught(new RuntimeException("error"));
        assertFalse(ch.isActive());
    }
}
