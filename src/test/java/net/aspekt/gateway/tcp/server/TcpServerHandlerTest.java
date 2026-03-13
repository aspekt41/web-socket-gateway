package net.aspekt.gateway.tcp.server;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import net.aspekt.gateway.AbstractConnectionEndpoint;
import org.junit.jupiter.api.Test;

class TcpServerHandlerTest {

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
    void channelActive_registersChannelWithEndpoint() {
        TcpServerEndpoint ep = new TcpServerEndpoint("srv");
        EmbeddedChannel ch = new EmbeddedChannel(new TcpServerHandler(ep));

        // Channel was registered by channelActive; send() should write to it.
        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {1});
        ep.send(buf);

        ByteBuf out = (ByteBuf) ch.readOutbound();
        assertNotNull(out);
        out.release();
        ch.close();
    }

    @Test
    void channelRead_forwardsDataToEndpointTargets() {
        TcpServerEndpoint ep = new TcpServerEndpoint("srv");
        CollectingEndpoint target = new CollectingEndpoint("tgt");
        ep.addTarget(target);

        EmbeddedChannel ch = new EmbeddedChannel(new TcpServerHandler(ep));
        ByteBuf inbound = Unpooled.copiedBuffer(new byte[] {5, 6});
        ch.writeInbound(inbound);

        assertEquals(1, target.received.size());
        target.received.get(0).release();
        ch.close();
    }

    @Test
    void channelInactive_doesNotThrow() {
        TcpServerEndpoint ep = new TcpServerEndpoint("srv");
        EmbeddedChannel ch = new EmbeddedChannel(new TcpServerHandler(ep));
        assertDoesNotThrow(() -> ch.close().sync());
    }

    @Test
    void exceptionCaught_closesChannel() {
        TcpServerEndpoint ep = new TcpServerEndpoint("srv");
        EmbeddedChannel ch = new EmbeddedChannel(new TcpServerHandler(ep));
        ch.pipeline().fireExceptionCaught(new RuntimeException("boom"));
        assertFalse(ch.isActive());
    }
}
