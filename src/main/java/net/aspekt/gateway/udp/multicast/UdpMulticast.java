package net.aspekt.gateway.udp.multicast;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import net.aspekt.gateway.ConnectionEndpoint;
import net.aspekt.gateway.GatewayConnection;

import java.net.*;
import java.util.logging.Logger;

/**
 * Netty-based UDP multicast endpoint.
 *
 * <p>Binds a {@link NioDatagramChannel} to the configured address/port, then
 * joins the multicast group so that the channel both receives datagrams sent to
 * the group and can send datagrams back to it.
 *
 * <p>Pipeline:
 * <pre>
 *   UdpMulticastHandler  (extracts datagram payload, forwards to endpoint targets)
 * </pre>
 *
 * <p>Call {@link #start()} to bind and join the group.
 * Call {@link #stop()} (or close via try-with-resources) to leave the group and
 * release resources.
 */
public class UdpMulticast implements GatewayConnection {

    private static final Logger log = Logger.getLogger(UdpMulticast.class.getName());

    private final UdpMulticastConfig config;
    private final UdpMulticastEndpoint endpoint;

    private EventLoopGroup group;
    private NioDatagramChannel channel;

    public UdpMulticast(UdpMulticastConfig config, UdpMulticastEndpoint endpoint) {
        this.config = config;
        this.endpoint = endpoint;
    }

    public ConnectionEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * Binds the datagram channel and joins the multicast group.
     * Returns as soon as the socket is bound and the group is joined.
     *
     * @throws InterruptedException if interrupted while waiting for bind/join
     * @throws Exception            if the network interface name is invalid or
     *                              the multicast group address cannot be resolved
     */
    public void start() throws SocketException, InterruptedException, UnknownHostException {
        group = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ch.pipeline().addLast(new UdpMulticastHandler(endpoint));
                    }
                });

        NetworkInterface ni = resolveNetworkInterface();
        if (ni != null) {
            bootstrap.option(ChannelOption.IP_MULTICAST_IF, ni);
        }

        ChannelFuture bindFuture = bootstrap
                .bind(new InetSocketAddress(config.getBindAddress(), config.getPort()))
                .sync();
        channel = (NioDatagramChannel) bindFuture.channel();

        InetAddress groupAddr = InetAddress.getByName(config.getGroup());
        if (ni != null) {
            channel.joinGroup(groupAddr, ni, null).sync();
        } else {
            channel.joinGroup(groupAddr).sync();
        }

        endpoint.setChannel(channel);

        log.info("[" + endpoint.getLabel() + "] UDP multicast ACTIVE on "
                + config.getBindAddress() + ":" + config.getPort()
                + " group=" + config.getGroup());
    }

    /**
     * Blocks until the datagram channel is closed.  Useful in a main thread
     * to keep the JVM alive.
     */
    public void awaitShutdown() throws InterruptedException {
        if (channel != null) {
            channel.closeFuture().sync();
        }
    }

    /** Closes the channel and shuts down the event loop group. */
    public void stop() {
        log.info("[" + endpoint.getLabel() + "] Stopping UDP multicast");
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    @Override
    public void close() {
        stop();
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private NetworkInterface resolveNetworkInterface() throws SocketException {
        String niName = config.getNetworkInterface();
        if (niName == null) {
            return null;
        }
        NetworkInterface ni = NetworkInterface.getByName(niName);
        if (ni == null) {
            throw new IllegalArgumentException(
                    "Network interface not found: '" + niName + "' (label=" + config.getLabel() + ")");
        }
        return ni;
    }
}
