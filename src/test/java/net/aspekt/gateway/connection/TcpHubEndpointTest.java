package net.aspekt.gateway.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import net.aspekt.gateway.tcp.hub.TcpHubEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TcpHubEndpoint}.
 */
class TcpHubEndpointTest {

    // -----------------------------------------------------------------------
    // onHubDataReceived() — broadcast to peers, exclude sender
    // -----------------------------------------------------------------------

    @Test
    void onHubDataReceivedSendsToOtherClientsNotSender() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub-test");
        EmbeddedChannel clientA = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel clientB = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel clientC = new EmbeddedChannel(DefaultChannelId.newInstance());
        ep.addChannel(clientA);
        ep.addChannel(clientB);
        ep.addChannel(clientC);

        byte[] data = {0x01, 0x02, 0x03};
        ep.onHubDataReceived(clientA, Unpooled.copiedBuffer(data));

        // B and C should receive the data
        ByteBuf bufB = clientB.readOutbound();
        assertNotNull(bufB, "clientB should receive data from sender A");
        byte[] receivedB = new byte[bufB.readableBytes()];
        bufB.readBytes(receivedB);
        assertArrayEquals(data, receivedB);
        bufB.release();

        ByteBuf bufC = clientC.readOutbound();
        assertNotNull(bufC, "clientC should receive data from sender A");
        byte[] receivedC = new byte[bufC.readableBytes()];
        bufC.readBytes(receivedC);
        assertArrayEquals(data, receivedC);
        bufC.release();

        // A (the sender) should NOT receive its own data
        assertNull(clientA.readOutbound(), "sender A should NOT receive its own data");

        clientA.close();
        clientB.close();
        clientC.close();
    }

    @Test
    void onHubDataReceivedForwardsToTargets() {
        TcpHubEndpoint hub = new TcpHubEndpoint("hub-test");
        TcpClientEndpoint target = new TcpClientEndpoint("tcp-target");
        EmbeddedChannel targetCh = new EmbeddedChannel();
        target.setChannel(targetCh);
        hub.addTarget(target);

        EmbeddedChannel sender = new EmbeddedChannel();
        hub.addChannel(sender);

        byte[] data = {0x11, 0x22};
        hub.onHubDataReceived(sender, Unpooled.copiedBuffer(data));

        ByteBuf received = targetCh.readOutbound();
        assertNotNull(received, "target should have received the forwarded data");
        byte[] receivedBytes = new byte[received.readableBytes()];
        received.readBytes(receivedBytes);
        assertArrayEquals(data, receivedBytes);
        received.release();

        sender.close();
        targetCh.close();
    }

    @Test
    void onHubDataReceivedSendsToOtherClientsAndTargets() {
        TcpHubEndpoint hub = new TcpHubEndpoint("hub-test");
        EmbeddedChannel sender = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel peer   = new EmbeddedChannel(DefaultChannelId.newInstance());
        hub.addChannel(sender);
        hub.addChannel(peer);

        TcpClientEndpoint target = new TcpClientEndpoint("tcp-target");
        EmbeddedChannel targetCh = new EmbeddedChannel();
        target.setChannel(targetCh);
        hub.addTarget(target);

        byte[] data = {(byte) 0xAA, (byte) 0xBB};
        hub.onHubDataReceived(sender, Unpooled.copiedBuffer(data));

        ByteBuf peerBuf = peer.readOutbound();
        assertNotNull(peerBuf, "peer should receive data");
        peerBuf.release();

        ByteBuf targetBuf = targetCh.readOutbound();
        assertNotNull(targetBuf, "target should receive data");
        targetBuf.release();

        assertNull(sender.readOutbound(), "sender should not receive its own data");

        sender.close();
        peer.close();
        targetCh.close();
    }

    @Test
    void onHubDataReceivedWithNoOtherClientsAndNoTargetsReleasesBuffer() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub-test");
        EmbeddedChannel sender = new EmbeddedChannel();
        ep.addChannel(sender);

        ByteBuf buf = Unpooled.copiedBuffer(new byte[]{0x42});
        ep.onHubDataReceived(sender, buf);
        assertEquals(0, buf.refCnt(), "buffer should be released when no peers or targets");

        sender.close();
    }

    @Test
    void onHubDataReceivedWithOnlyTargetAndNoOtherClients() {
        TcpHubEndpoint hub = new TcpHubEndpoint("hub-test");
        EmbeddedChannel sender = new EmbeddedChannel();
        hub.addChannel(sender);

        TcpClientEndpoint target = new TcpClientEndpoint("tcp-target");
        EmbeddedChannel targetCh = new EmbeddedChannel();
        target.setChannel(targetCh);
        hub.addTarget(target);

        byte[] data = {0x55};
        hub.onHubDataReceived(sender, Unpooled.copiedBuffer(data));

        ByteBuf received = targetCh.readOutbound();
        assertNotNull(received, "target should receive data even when no other hub clients");
        received.release();

        sender.close();
        targetCh.close();
    }

    // -----------------------------------------------------------------------
    // send() — broadcast to ALL connected clients
    // -----------------------------------------------------------------------

    @Test
    void sendBroadcastsToAllConnectedClients() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub-test");
        EmbeddedChannel client1 = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel client2 = new EmbeddedChannel(DefaultChannelId.newInstance());
        ep.addChannel(client1);
        ep.addChannel(client2);

        byte[] data = {0x0A, 0x0B};
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
    void sendReleasesBufferWhenNoClientsConnected() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub-test");
        ByteBuf buf = Unpooled.copiedBuffer(new byte[]{0x42});
        ep.send(buf);
        assertEquals(0, buf.refCnt(), "buffer should be released when no clients are connected");
    }

    // -----------------------------------------------------------------------
    // Closed clients are removed from the channel group automatically
    // -----------------------------------------------------------------------

    @Test
    void closedClientRemovedFromGroupAutomatically() throws InterruptedException {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub-test");
        EmbeddedChannel sender = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel client = new EmbeddedChannel(DefaultChannelId.newInstance());
        ep.addChannel(sender);
        ep.addChannel(client);

        client.close().sync();

        // Only sender in group; sender sends — no recipients, buf must be released
        ByteBuf buf = Unpooled.copiedBuffer(new byte[]{0x01});
        ep.onHubDataReceived(sender, buf);
        assertEquals(0, buf.refCnt(), "buffer should be released when only sender remains in group");

        sender.close();
    }
}
