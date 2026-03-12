package net.aspekt.gateway.tcp.hub;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import net.aspekt.gateway.AbstractConnectionEndpoint;
import net.aspekt.gateway.ConnectionEndpoint;

/**
 * Connection endpoint for a TCP hub server.
 *
 * <p>Unlike a plain TCP server, data received from a connected client is
 * broadcast to <em>all other</em> connected clients (peer-to-peer hub
 * semantics) and also forwarded to any configured {@code <forward>} targets.
 *
 * <p>A {@link DefaultChannelGroup} tracks live client channels; closed channels
 * are removed automatically.  {@link #send(ByteBuf)} writes the raw buffer to
 * every connected client — used when a forwarding target pushes data back into
 * the hub.
 *
 * <p>Call {@link #addChannel(Channel)} from {@link TcpHubHandler} whenever a
 * new TCP client connects.  Call {@link #onHubDataReceived(Channel, ByteBuf)}
 * instead of the standard {@code onDataReceived} so the sender channel can be
 * excluded from the broadcast.
 */
public class TcpHubEndpoint extends AbstractConnectionEndpoint {

    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public TcpHubEndpoint(String label) {
        super(label);
    }

    /**
     * Registers a newly-connected TCP client channel.
     * Closed channels are removed from the group automatically.
     */
    public void addChannel(Channel ch) {
        channels.add(ch);
    }

    /**
     * Writes {@code buf} directly (no framing) to all connected hub clients.
     *
     * <p>This method is used when data arrives from a forwarding target and
     * should be delivered to all hub participants.  If there are no connected
     * clients the buffer is released immediately.  Otherwise ownership is
     * transferred to the channel group, which handles per-channel retains.
     *
     * @param buf buffer to send; ownership is transferred to this method
     */
    @Override
    public void send(ByteBuf buf) {
        if (channels.isEmpty()) {
            buf.release();
            return;
        }
        // DefaultChannelGroup.writeAndFlush() calls retainedDuplicate() per channel
        // internally, so handing it the original buf is correct.
        channels.writeAndFlush(buf);
    }

    /**
     * Distributes data received from {@code sender} to all <em>other</em>
     * connected hub clients and to every configured forwarding target.
     *
     * <p>The sender itself is excluded from the broadcast so it does not
     * receive its own data back.
     *
     * <p>Reference counting: each target gets a retained copy via
     * {@code buf.retain()}.  The peer broadcast uses
     * {@link ChannelGroup#writeAndFlush(Object, io.netty.channel.group.ChannelMatcher)}
     * which handles per-channel retains internally and releases the original
     * reference.  If there are no peers, the original reference is released
     * explicitly.
     *
     * @param sender the channel that sent the data (excluded from broadcast)
     * @param buf    inbound data; ownership is transferred to this method
     */
    public void onHubDataReceived(Channel sender, ByteBuf buf) {
        // Check if there are any peer channels (any channel other than sender).
        // DefaultChannelGroup auto-removes closed channels, and the sender is
        // always present in the group, so size() > 1 means at least one peer exists.
        boolean hasPeers = channels.size() > 1;

        java.util.List<ConnectionEndpoint> targets = getTargets();

        if (!hasPeers && targets.isEmpty()) {
            buf.release();
            return;
        }

        // Retain once per target and transfer ownership.
        for (ConnectionEndpoint target : targets) {
            target.send(buf.retain());
        }

        // Broadcast to peers (excluding sender).
        // DefaultChannelGroup.writeAndFlush(msg, matcher) calls retainedDuplicate() per
        // matched channel and releases the original reference — same as send().
        if (hasPeers) {
            channels.writeAndFlush(buf, ch -> ch != sender);
        } else {
            buf.release(); // no peers; release our remaining reference
        }
    }
}
