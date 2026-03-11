package com.gateway.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WebSocketEndpoint}.
 */
class WebSocketEndpointTest {

    // -----------------------------------------------------------------------
    // send() — fan-out to connected WS clients
    // -----------------------------------------------------------------------

    @Test
    void sendDeliversBinaryFrameToConnectedClient() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws-test");
        EmbeddedChannel client = new EmbeddedChannel();
        ep.addChannel(client);

        byte[] data = {0x01, 0x02, 0x03};
        ep.send(Unpooled.copiedBuffer(data));

        BinaryWebSocketFrame frame = client.readOutbound();
        assertNotNull(frame, "client should receive a BinaryWebSocketFrame");
        byte[] received = new byte[frame.content().readableBytes()];
        frame.content().readBytes(received);
        assertArrayEquals(data, received);
        frame.release();

        client.close();
    }

    @Test
    void sendToMultipleClientsDeliversToAll() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws-test");
        // Distinct ChannelIds required so DefaultChannelGroup treats them as separate.
        EmbeddedChannel client1 = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel client2 = new EmbeddedChannel(DefaultChannelId.newInstance());
        ep.addChannel(client1);
        ep.addChannel(client2);

        byte[] data = {0x0A, 0x0B, 0x0C};
        ep.send(Unpooled.copiedBuffer(data));

        BinaryWebSocketFrame frame1 = client1.readOutbound();
        assertNotNull(frame1, "client1 should receive a frame");
        frame1.release();

        BinaryWebSocketFrame frame2 = client2.readOutbound();
        assertNotNull(frame2, "client2 should receive a frame");
        frame2.release();

        client1.close();
        client2.close();
    }

    @Test
    void sendReleasesBufferImmediatelyWhenNoClientsConnected() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws-test");
        ByteBuf buf = Unpooled.copiedBuffer(new byte[]{0x42});
        assertDoesNotThrow(() -> ep.send(buf));
        // buf.refCnt() should be 0 after release
        assertEquals(0, buf.refCnt(), "buffer should be released when no clients are connected");
    }

    // -----------------------------------------------------------------------
    // onDataReceived() — fan-out via targets
    // -----------------------------------------------------------------------

    @Test
    void onDataReceivedWithNoTargetsReleasesBuffer() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws-test");
        ByteBuf buf = Unpooled.copiedBuffer(new byte[]{0x01});
        ep.onDataReceived(buf);
        assertEquals(0, buf.refCnt(), "buffer should be released when there are no targets");
    }

    @Test
    void onDataReceivedForwardsToRegisteredTarget() {
        WebSocketEndpoint source = new WebSocketEndpoint("ws-source");
        TcpClientEndpoint target = new TcpClientEndpoint("tcp-target");
        EmbeddedChannel tcpCh = new EmbeddedChannel();
        target.setChannel(tcpCh);

        source.addTarget(target);

        byte[] data = {0x11, 0x22};
        source.onDataReceived(Unpooled.copiedBuffer(data));

        ByteBuf received = tcpCh.readOutbound();
        assertNotNull(received, "target should have received the data");
        byte[] receivedBytes = new byte[received.readableBytes()];
        received.readBytes(receivedBytes);
        assertArrayEquals(data, receivedBytes);
        received.release();

        tcpCh.close();
    }

    // -----------------------------------------------------------------------
    // Closed clients are removed from channel group automatically
    // -----------------------------------------------------------------------

    @Test
    void closedClientIsRemovedFromGroupAutomatically() throws InterruptedException {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws-test");
        EmbeddedChannel client = new EmbeddedChannel();
        ep.addChannel(client);

        client.close().sync();

        // After close, the channel group should be empty; send() should release
        ByteBuf buf = Unpooled.copiedBuffer(new byte[]{0x01});
        ep.send(buf);
        assertEquals(0, buf.refCnt(), "buffer should be released when group is empty after close");
    }
}
