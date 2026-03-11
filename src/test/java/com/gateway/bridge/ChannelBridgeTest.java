package com.gateway.bridge;

import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelBridgeTest {

    @Test
    void tcpChannelIsNullInitially() {
        assertNull(new ChannelBridge("test").getTcpChannel());
    }

    @Test
    void getBridgeNameReturnsConstructorValue() {
        assertEquals("my-bridge", new ChannelBridge("my-bridge").getName());
    }

    @Test
    void setTcpChannelMakesItVisible() {
        ChannelBridge session = new ChannelBridge("test");
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
        ChannelBridge session = new ChannelBridge("test");
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
    void wsGroupIsEmptyInitially() {
        assertEquals(0, new ChannelBridge("test").getWebsocketChannels().size());
    }

    @Test
    void addedWsChannelAppearsInGroup() {
        ChannelBridge session = new ChannelBridge("test");
        EmbeddedChannel ch = new EmbeddedChannel();
        try {
            session.addWebsocketChannel(ch);
            assertEquals(1, session.getWebsocketChannels().size());
            assertTrue(session.getWebsocketChannels().contains(ch));
        } finally {
            ch.close();
        }
    }

    @Test
    void multipleWsChannelsAccumulate() {
        ChannelBridge session = new ChannelBridge("test");
        // Give each channel a distinct ChannelId so DefaultChannelGroup treats them as separate.
        EmbeddedChannel ch1 = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel ch2 = new EmbeddedChannel(DefaultChannelId.newInstance());
        try {
            session.addWebsocketChannel(ch1);
            session.addWebsocketChannel(ch2);
            assertEquals(2, session.getWebsocketChannels().size());
        } finally {
            ch1.close();
            ch2.close();
        }
    }

    @Test
    void channelsUsingSameIdAreSeenAsSame() {
        ChannelBridge session = new ChannelBridge("Test Bridge");
        // Because these channels aren't provided with an ID, they seem to use
        // the same ID which, to Netty, makes them the same channel.
        EmbeddedChannel channel1 = new EmbeddedChannel();
        EmbeddedChannel channel2 = new EmbeddedChannel();
        try {
            session.addWebsocketChannel(channel1);
            session.addWebsocketChannel(channel2);
            assertEquals(1, session.getWebsocketChannels().size());
        } finally {
            channel1.close();
            channel2.close();
        }
    }

    @Test
    void closedWsChannelIsAutoRemovedFromGroup() throws InterruptedException {
        ChannelBridge session = new ChannelBridge("test");
        EmbeddedChannel ch = new EmbeddedChannel();
        session.addWebsocketChannel(ch);
        assertEquals(1, session.getWebsocketChannels().size());

        ch.close().sync();

        assertEquals(0, session.getWebsocketChannels().size());
    }

}
