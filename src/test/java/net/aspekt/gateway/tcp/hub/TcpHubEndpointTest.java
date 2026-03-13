package net.aspekt.gateway.tcp.hub;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import net.aspekt.gateway.AbstractConnectionEndpoint;
import org.junit.jupiter.api.Test;

class TcpHubEndpointTest {

    /** Simple endpoint that records every buffer delivered via send(). */
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

    // -----------------------------------------------------------------------
    // send() — used when forwarding targets push data into the hub
    // -----------------------------------------------------------------------

    @Test
    void send_withNoClients_releasesBuf() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub");
        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(1);
        ep.send(buf);
        assertEquals(0, buf.refCnt());
    }

    @Test
    void send_withOneClient_writesBufToClient() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub");
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.addChannel(ch);

        ByteBuf payload = Unpooled.copiedBuffer(new byte[] {1, 2});
        ep.send(payload);

        ByteBuf out = (ByteBuf) ch.readOutbound();
        assertNotNull(out);
        out.release();
        ch.close();
    }

    // -----------------------------------------------------------------------
    // onHubDataReceived() — hub-to-hub broadcast semantics
    // -----------------------------------------------------------------------

    @Test
    void onHubDataReceived_withNoPeersAndNoTargets_releasesBuf() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub");
        EmbeddedChannel sender = new EmbeddedChannel();
        ep.addChannel(sender);

        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(42);
        ep.onHubDataReceived(sender, buf);
        assertEquals(0, buf.refCnt());
        sender.close();
    }

    @Test
    void onHubDataReceived_withForwardTarget_sendsToTarget() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub");
        CollectingEndpoint target = new CollectingEndpoint("tgt");
        ep.addTarget(target);

        EmbeddedChannel sender = new EmbeddedChannel();
        ep.addChannel(sender);

        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {7, 8});
        ep.onHubDataReceived(sender, buf);

        assertEquals(1, target.received.size());
        target.received.get(0).release();
        sender.close();
    }

    @Test
    void onHubDataReceived_withPeer_senderIsExcludedFromBroadcast() {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub");
        EmbeddedChannel sender = new EmbeddedChannel();
        EmbeddedChannel peer = new EmbeddedChannel();
        ep.addChannel(sender);
        ep.addChannel(peer);

        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {9});
        // Must not throw; the hub broadcasts to peer and excludes sender.
        assertDoesNotThrow(() -> ep.onHubDataReceived(sender, buf));

        // The sender must NOT receive its own data back.
        assertNull(sender.readOutbound());

        sender.close();
        peer.close();
    }

    @Test
    void addChannel_closedChannel_isRemovedFromGroup() throws InterruptedException {
        TcpHubEndpoint ep = new TcpHubEndpoint("hub");
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.addChannel(ch);
        ch.close().sync();

        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(1);
        ep.send(buf);
        assertEquals(0, buf.refCnt());
    }

    @Test
    void onHubDataReceived_withPeerAndTarget_sendsToTargetAndDoesNotThrow() {
        // Covers the code path where hasPeers=true AND targets is non-empty.
        TcpHubEndpoint ep = new TcpHubEndpoint("hub");
        CollectingEndpoint target = new CollectingEndpoint("tgt");
        ep.addTarget(target);

        EmbeddedChannel sender = new EmbeddedChannel();
        EmbeddedChannel peer = new EmbeddedChannel();
        ep.addChannel(sender);
        ep.addChannel(peer);

        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {3, 4});
        assertDoesNotThrow(() -> ep.onHubDataReceived(sender, buf));

        // Target must have received a retained copy.
        assertEquals(1, target.received.size());
        target.received.get(0).release();

        sender.close();
        peer.close();
    }

    @Test
    void getLabel_returnsConstructorLabel() {
        assertEquals("hub", new TcpHubEndpoint("hub").getLabel());
    }
}
