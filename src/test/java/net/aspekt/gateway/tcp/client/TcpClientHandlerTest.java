package net.aspekt.gateway.tcp.client;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import net.aspekt.gateway.AbstractConnectionEndpoint;
import org.junit.jupiter.api.Test;

class TcpClientHandlerTest {

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

    /** Stub TcpClientConfig for constructing a TcpClient without starting it. */
    private static TcpClientConfig stubConfig() {
        return new TcpClientConfig() {
            public String getLabel() {
                return "cli";
            }

            public String getHost() {
                return "localhost";
            }

            public int getPort() {
                return 19999;
            }

            public int getReconnectDelaySeconds() {
                return 5;
            }

            public int getConnectTimeoutSeconds() {
                return 10;
            }
        };
    }

    @Test
    void channelActive_setsChannelOnEndpoint() {
        TcpClientEndpoint ep = new TcpClientEndpoint("cli");
        TcpClient client = new TcpClient(stubConfig(), ep);
        EmbeddedChannel ch = new EmbeddedChannel(new TcpClientHandler(client, ep));

        // channelActive fires during EmbeddedChannel construction; endpoint has a channel.
        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {1, 2});
        ep.send(buf);

        ByteBuf out = (ByteBuf) ch.readOutbound();
        assertNotNull(out);
        out.release();
        ch.close();
    }

    @Test
    void channelRead_forwardsDataToEndpointTargets() {
        TcpClientEndpoint ep = new TcpClientEndpoint("cli");
        CollectingEndpoint target = new CollectingEndpoint("tgt");
        ep.addTarget(target);

        TcpClient client = new TcpClient(stubConfig(), ep);
        EmbeddedChannel ch = new EmbeddedChannel(new TcpClientHandler(client, ep));

        ch.writeInbound(Unpooled.copiedBuffer(new byte[] {9}));
        assertEquals(1, target.received.size());
        target.received.get(0).release();
        ch.close();
    }

    @Test
    void channelInactive_clearsChannelOnEndpoint() {
        TcpClientEndpoint ep = new TcpClientEndpoint("cli");
        TcpClient client = new TcpClient(stubConfig(), ep);
        EmbeddedChannel ch = new EmbeddedChannel(new TcpClientHandler(client, ep));

        // Stop the client so scheduleReconnect() returns early (avoids NPE on null bootstrap).
        client.stop();
        ch.close();

        // Endpoint channel should be cleared; send() releases buf immediately.
        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(1);
        ep.send(buf);
        assertEquals(0, buf.refCnt());
    }

    @Test
    void exceptionCaught_closesChannel() {
        TcpClientEndpoint ep = new TcpClientEndpoint("cli");
        TcpClient client = new TcpClient(stubConfig(), ep);
        EmbeddedChannel ch = new EmbeddedChannel(new TcpClientHandler(client, ep));
        ch.pipeline().fireExceptionCaught(new RuntimeException("oops"));
        assertFalse(ch.isActive());
        ch.close();
    }
}
