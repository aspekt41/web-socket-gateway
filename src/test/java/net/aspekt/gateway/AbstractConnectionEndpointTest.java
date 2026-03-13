package net.aspekt.gateway;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbstractConnectionEndpointTest {

    /** Minimal concrete subclass — send() records received buffers. */
    private static class RecordingEndpoint extends AbstractConnectionEndpoint {
        final List<ByteBuf> received = new ArrayList<>();

        RecordingEndpoint(String label) {
            super(label);
        }

        @Override
        public void send(ByteBuf buf) {
            received.add(buf);
        }
    }

    @Test
    void getLabel_returnsConstructorLabel() {
        RecordingEndpoint ep = new RecordingEndpoint("my-label");
        assertEquals("my-label", ep.getLabel());
    }

    @Test
    void addTarget_appearsInGetTargets() {
        RecordingEndpoint ep = new RecordingEndpoint("a");
        RecordingEndpoint target = new RecordingEndpoint("b");
        ep.addTarget(target);
        assertTrue(ep.getTargets().contains(target));
    }

    @Test
    void removeTarget_disappearsFromGetTargets() {
        RecordingEndpoint ep = new RecordingEndpoint("a");
        RecordingEndpoint target = new RecordingEndpoint("b");
        ep.addTarget(target);
        ep.removeTarget(target);
        assertFalse(ep.getTargets().contains(target));
    }

    @Test
    void getTargets_emptyByDefault() {
        assertTrue(new RecordingEndpoint("a").getTargets().isEmpty());
    }

    @Test
    void onDataReceived_withNoTargets_releasesBuf() {
        RecordingEndpoint ep = new RecordingEndpoint("a");
        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(42);
        ep.onDataReceived(buf);
        assertEquals(0, buf.refCnt());
    }

    @Test
    void onDataReceived_withOneTarget_sendsBufToTarget() {
        RecordingEndpoint source = new RecordingEndpoint("src");
        RecordingEndpoint target = new RecordingEndpoint("tgt");
        source.addTarget(target);

        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(99);
        source.onDataReceived(buf);

        assertEquals(1, target.received.size());
        // target.received holds the retained copy; release it
        target.received.get(0).release();
    }

    @Test
    void onDataReceived_withOneTarget_releasesSourceRefAfterFanout() {
        RecordingEndpoint source = new RecordingEndpoint("src");
        RecordingEndpoint target = new RecordingEndpoint("tgt");
        source.addTarget(target);

        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(1);
        // refCnt starts at 1; onDataReceived retains once for target then releases own ref,
        // leaving exactly 1 outstanding reference owned by target.
        source.onDataReceived(buf);
        assertEquals(1, buf.refCnt());
        target.received.get(0).release();
        assertEquals(0, buf.refCnt());
    }

    @Test
    void onDataReceived_withTwoTargets_retainsOncePerTarget() {
        RecordingEndpoint source = new RecordingEndpoint("src");
        RecordingEndpoint t1 = new RecordingEndpoint("t1");
        RecordingEndpoint t2 = new RecordingEndpoint("t2");
        source.addTarget(t1);
        source.addTarget(t2);

        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(7);
        source.onDataReceived(buf);

        // refCnt = 1 + 2 retained - 1 released = 2
        assertEquals(2, buf.refCnt());
        t1.received.get(0).release();
        t2.received.get(0).release();
        assertEquals(0, buf.refCnt());
    }
}
