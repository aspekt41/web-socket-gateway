package com.gateway.bridge;

import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BridgeSessionTest {

    @Test
    void tcpChannelIsNullInitially() {
        assertNull(new BridgeSession("test").getTcpChannel());
    }

    @Test
    void getBridgeNameReturnsConstructorValue() {
        assertEquals("my-bridge", new BridgeSession("my-bridge").getBridgeName());
    }

    @Test
    void setTcpChannelMakesItVisible() {
        BridgeSession session = new BridgeSession("test");
        EmbeddedChannel ch = new EmbeddedChannel();
        try {
            session.setTcpChannel(ch);
            assertSame(ch, session.getTcpChannel());
        } finally {
            ch.close();
        }
    }

    @Test
    void clearTcpChannelSetsNull() {
        BridgeSession session = new BridgeSession("test");
        EmbeddedChannel ch = new EmbeddedChannel();
        try {
            session.setTcpChannel(ch);
            session.clearTcpChannel();
            assertNull(session.getTcpChannel());
        } finally {
            ch.close();
        }
    }

    @Test
    void addedWsChannelAppearsInGroup() {
        BridgeSession session = new BridgeSession("test");
        EmbeddedChannel ch = new EmbeddedChannel();
        try {
            session.addWsChannel(ch);
            assertEquals(1, session.getWsChannels().size());
            assertTrue(session.getWsChannels().contains(ch));
        } finally {
            ch.close();
        }
    }

    @Test
    void multipleWsChannelsAccumulate() {
        BridgeSession session = new BridgeSession("test");
        // Give each channel a distinct ChannelId so DefaultChannelGroup treats them as separate.
        EmbeddedChannel ch1 = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel ch2 = new EmbeddedChannel(DefaultChannelId.newInstance());
        try {
            session.addWsChannel(ch1);
            session.addWsChannel(ch2);
            assertEquals(2, session.getWsChannels().size());
        } finally {
            ch1.close();
            ch2.close();
        }
    }

    @Test
    void closedWsChannelIsAutoRemovedFromGroup() throws InterruptedException {
        BridgeSession session = new BridgeSession("test");
        EmbeddedChannel ch = new EmbeddedChannel();
        session.addWsChannel(ch);
        assertEquals(1, session.getWsChannels().size());

        ch.close().sync();

        assertEquals(0, session.getWsChannels().size());
    }

    @Test
    void wsGroupIsEmptyInitially() {
        assertEquals(0, new BridgeSession("test").getWsChannels().size());
    }
}
