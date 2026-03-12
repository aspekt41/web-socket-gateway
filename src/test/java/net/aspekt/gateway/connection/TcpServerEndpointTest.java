package net.aspekt.gateway.connection;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import net.aspekt.gateway.tcp.server.TcpServerEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TcpServerEndpoint}.
 */
class TcpServerEndpointTest {

    // -----------------------------------------------------------------------
    // send() — fan-out to connected TCP clients
    // -----------------------------------------------------------------------

    @Test
    void sendDeliversRawBytesToConnectedClient() {
        TcpServerEndpoint ep = new TcpServerEndpoint("tcp-srv-test");
        EmbeddedChannel client = new EmbeddedChannel();
        ep.addChannel(client);

        byte[] data = {0x01, 0x02, 0x03};
        ep.send(Unpooled.copiedBuffer(data));

        ByteBuf received = client.readOutbound();
        assertNotNull(received, "client should receive raw bytes");
        byte[] receivedBytes = new byte[received.readableBytes()];
        received.readBytes(receivedBytes);
        assertArrayEquals(data, receivedBytes);
        received.release();

        client.close();
    }

    @Test
    void sendToMultipleClientsDeliversToAll() {
        TcpServerEndpoint ep = new TcpServerEndpoint("tcp-srv-test");
        EmbeddedChannel client1 = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel client2 = new EmbeddedChannel(DefaultChannelId.newInstance());
        ep.addChannel(client1);
        ep.addChannel(client2);

        byte[] data = {0x0A, 0x0B, 0x0C};
        ep.send(Unpooled.copiedBuffer(data));

        ByteBuf buf1 = client1.readOutbound();
        assertNotNull(buf1, "client1 should receive the data");
        buf1.release();

        ByteBuf buf2 = client2.readOutbound();
        assertNotNull(buf2, "client2 should receive the data");
        buf2.release();

        client1.close();
        client2.close();
    }

    @Test
    void sendReleasesBufferImmediatelyWhenNoClientsConnected() {
        TcpServerEndpoint ep = new TcpServerEndpoint("tcp-srv-test");
        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {0x42});
        assertDoesNotThrow(() -> ep.send(buf));
        assertEquals(0, buf.refCnt(), "buffer should be released when no clients are connected");
    }

    // -----------------------------------------------------------------------
    // onDataReceived() — fan-out via targets
    // -----------------------------------------------------------------------

    @Test
    void onDataReceivedWithNoTargetsReleasesBuffer() {
        TcpServerEndpoint ep = new TcpServerEndpoint("tcp-srv-test");
        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {0x01});
        ep.onDataReceived(buf);
        assertEquals(0, buf.refCnt(), "buffer should be released when there are no targets");
    }

    @Test
    void onDataReceivedForwardsToRegisteredTarget() {
        TcpServerEndpoint source = new TcpServerEndpoint("tcp-srv-source");
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
        TcpServerEndpoint ep = new TcpServerEndpoint("tcp-srv-test");
        EmbeddedChannel client = new EmbeddedChannel();
        ep.addChannel(client);

        client.close().sync();

        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {0x01});
        ep.send(buf);
        assertEquals(0, buf.refCnt(), "buffer should be released when group is empty after close");
    }
}
