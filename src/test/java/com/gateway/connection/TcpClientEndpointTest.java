package com.gateway.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TcpClientEndpoint}.
 */
class TcpClientEndpointTest {

    // -----------------------------------------------------------------------
    // send() — write to active TCP channel
    // -----------------------------------------------------------------------

    @Test
    void sendWritesToActiveTcpChannel() {
        TcpClientEndpoint ep = new TcpClientEndpoint("tcp-test");
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.setChannel(ch);

        byte[] data = {0x01, 0x02, 0x03};
        ep.send(Unpooled.copiedBuffer(data));

        ByteBuf received = ch.readOutbound();
        assertNotNull(received, "channel should have received data");
        byte[] receivedBytes = new byte[received.readableBytes()];
        received.readBytes(receivedBytes);
        assertArrayEquals(data, receivedBytes);
        received.release();

        ch.close();
    }

    @Test
    void sendReleasesBufferWhenChannelIsNull() {
        TcpClientEndpoint ep = new TcpClientEndpoint("tcp-test");
        ByteBuf buf = Unpooled.copiedBuffer(new byte[]{0x42});
        assertDoesNotThrow(() -> ep.send(buf));
        assertEquals(0, buf.refCnt(), "buffer should be released when channel is null");
    }

    @Test
    void sendReleasesBufferWhenChannelIsClosed() throws Exception {
        TcpClientEndpoint ep = new TcpClientEndpoint("tcp-test");
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.setChannel(ch);
        ch.close().sync();

        ByteBuf buf = Unpooled.copiedBuffer(new byte[]{0x42});
        assertDoesNotThrow(() -> ep.send(buf));
        assertEquals(0, buf.refCnt(), "buffer should be released when channel is inactive");
    }

    // -----------------------------------------------------------------------
    // setChannel / clearChannel
    // -----------------------------------------------------------------------

    @Test
    void setChannelAndClearChannel() {
        TcpClientEndpoint ep = new TcpClientEndpoint("tcp-test");
        EmbeddedChannel ch = new EmbeddedChannel();

        ep.setChannel(ch);
        // After setting, send should work
        ByteBuf buf1 = Unpooled.copiedBuffer(new byte[]{0x01});
        ep.send(buf1);
        ByteBuf received = ch.readOutbound();
        assertNotNull(received);
        received.release();

        ep.clearChannel();
        // After clearing, send should drop
        ByteBuf buf2 = Unpooled.copiedBuffer(new byte[]{0x02});
        ep.send(buf2);
        assertEquals(0, buf2.refCnt(), "buffer should be released after clearChannel");

        ch.close();
    }

    // -----------------------------------------------------------------------
    // onDataReceived() — fan-out
    // -----------------------------------------------------------------------

    @Test
    void onDataReceivedWithNoTargetsReleasesBuffer() {
        TcpClientEndpoint ep = new TcpClientEndpoint("tcp-test");
        ByteBuf buf = Unpooled.copiedBuffer(new byte[]{0x01});
        ep.onDataReceived(buf);
        assertEquals(0, buf.refCnt(), "buffer should be released when there are no targets");
    }

    @Test
    void onDataReceivedFansOutToMultipleTargets() throws Exception {
        TcpClientEndpoint source = new TcpClientEndpoint("tcp-source");

        TcpClientEndpoint target1 = new TcpClientEndpoint("tcp-target-1");
        EmbeddedChannel ch1 = new EmbeddedChannel();
        target1.setChannel(ch1);

        TcpClientEndpoint target2 = new TcpClientEndpoint("tcp-target-2");
        EmbeddedChannel ch2 = new EmbeddedChannel();
        target2.setChannel(ch2);

        source.addTarget(target1);
        source.addTarget(target2);

        byte[] data = {(byte) 0xA1, (byte) 0xB2};
        source.onDataReceived(Unpooled.copiedBuffer(data));

        ByteBuf received1 = ch1.readOutbound();
        assertNotNull(received1, "target1 should receive data");
        received1.release();

        ByteBuf received2 = ch2.readOutbound();
        assertNotNull(received2, "target2 should receive data");
        received2.release();

        ch1.close();
        ch2.close();
    }

    // -----------------------------------------------------------------------
    // getLabel
    // -----------------------------------------------------------------------

    @Test
    void getLabelReturnsConstructorValue() {
        assertEquals("my-tcp", new TcpClientEndpoint("my-tcp").getLabel());
    }
}
