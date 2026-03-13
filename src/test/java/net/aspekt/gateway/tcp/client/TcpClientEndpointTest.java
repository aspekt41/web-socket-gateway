package net.aspekt.gateway.tcp.client;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class TcpClientEndpointTest {

    @Test
    void getLabel_returnsConstructorLabel() {
        assertEquals("cli", new TcpClientEndpoint("cli").getLabel());
    }

    @Test
    void send_withNoChannel_releasesBuf() {
        TcpClientEndpoint ep = new TcpClientEndpoint("cli");
        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(1);
        ep.send(buf);
        assertEquals(0, buf.refCnt());
    }

    @Test
    void send_withInactiveChannel_releasesBuf() throws InterruptedException {
        TcpClientEndpoint ep = new TcpClientEndpoint("cli");
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.setChannel(ch);
        ch.close().sync();

        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(2);
        ep.send(buf);
        assertEquals(0, buf.refCnt());
    }

    @Test
    void send_withActiveChannel_writesBuf() {
        TcpClientEndpoint ep = new TcpClientEndpoint("cli");
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.setChannel(ch);

        ByteBuf payload = Unpooled.copiedBuffer(new byte[] {5, 6, 7});
        ep.send(payload);

        ByteBuf out = (ByteBuf) ch.readOutbound();
        assertNotNull(out);
        out.release();
        ch.close();
    }

    @Test
    void clearChannel_causesSubsequentSendToRelease() {
        TcpClientEndpoint ep = new TcpClientEndpoint("cli");
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.setChannel(ch);
        ep.clearChannel();

        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(3);
        ep.send(buf);
        assertEquals(0, buf.refCnt());
        ch.close();
    }
}
