package net.aspekt.gateway.tcp.server;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class TcpServerEndpointTest {

    @Test
    void getLabel_returnsConstructorLabel() {
        assertEquals("srv", new TcpServerEndpoint("srv").getLabel());
    }

    @Test
    void send_withNoClients_releasesBuf() {
        TcpServerEndpoint ep = new TcpServerEndpoint("srv");
        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(42);
        ep.send(buf);
        assertEquals(0, buf.refCnt());
    }

    @Test
    void send_withOneClient_writesBufToChannel() {
        TcpServerEndpoint ep = new TcpServerEndpoint("srv");
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.addChannel(ch);

        ByteBuf payload = Unpooled.copiedBuffer(new byte[] {10, 20});
        ep.send(payload);

        ByteBuf out = (ByteBuf) ch.readOutbound();
        assertNotNull(out);
        out.release();
        ch.close();
    }

    @Test
    void addChannel_closedChannel_isRemovedFromGroup() throws InterruptedException {
        TcpServerEndpoint ep = new TcpServerEndpoint("srv");
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.addChannel(ch);
        ch.close().sync();

        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(1);
        ep.send(buf);
        assertEquals(0, buf.refCnt());
    }
}
