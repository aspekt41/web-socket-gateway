package net.aspekt.gateway.tcp.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import net.aspekt.gateway.ConnectionEndpoint;
import net.aspekt.gateway.GatewayConnection;

/**
 * Netty-based outbound TCP client.
 *
 * <p>The client connects to the configured remote host:port and automatically
 * reconnects after a configurable delay whenever the connection is lost.
 *
 * <p>Pipeline (plain bytes, no framing):
 * <pre>
 *   TcpClientHandler  (registers channel on endpoint, forwards inbound data)
 * </pre>
 * Frame decoders (line, length-prefixed, etc.) can be inserted into the
 * pipeline as needed.
 *
 * <p>Call {@link #start()} to initiate the first connection attempt.
 * Call {@link #stop()} (or close via try-with-resources) to disconnect and
 * release resources.
 */
public class TcpClient implements GatewayConnection {

    private static final Logger log = Logger.getLogger(TcpClient.class.getName());

    private final TcpClientConfig config;
    private final TcpClientEndpoint endpoint;

    private EventLoopGroup eventLoopGroup;
    private Bootstrap bootstrap;
    private volatile Channel channel;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public TcpClient(TcpClientConfig config, TcpClientEndpoint endpoint) {
        this.config = config;
        this.endpoint = endpoint;
    }

    public ConnectionEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * Starts the TCP client and initiates the first connection attempt.
     * Returns immediately; connection is established asynchronously.
     */
    public void start() {
        eventLoopGroup = new NioEventLoopGroup();

        bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutSeconds() * 1000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new TcpClientHandler(TcpClient.this, endpoint));
                    }
                });

        connect();
    }

    /** Gracefully closes the connection and shuts down the event loop group. */
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            log.info("[" + endpoint.getLabel() + "] Stopping TCP client");
            if (channel != null) {
                channel.close();
            }
            if (eventLoopGroup != null) {
                eventLoopGroup.shutdownGracefully();
            }
        }
    }

    /** Returns the delay (in seconds) between reconnection attempts. */
    public int getReconnectDelaySeconds() {
        return config.getReconnectDelaySeconds();
    }

    /**
     * Schedules a reconnection attempt on the given {@link EventLoop}.
     * Called by {@link TcpClientHandler} when the channel becomes inactive.
     *
     * @param eventLoop the event loop of the closed channel
     */
    public void scheduleReconnect(EventLoop eventLoop) {
        if (stopped.get()) {
            return;
        }
        long delaySeconds = config.getReconnectDelaySeconds();
        log.info("[" + endpoint.getLabel() + "] Reconnecting to " + config.getHost() + ":" + config.getPort() + " in "
                + delaySeconds + "s");
        eventLoop.schedule(this::connect, delaySeconds, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private void connect() {
        if (stopped.get()) {
            return;
        }
        log.info("[" + endpoint.getLabel() + "] Connecting to " + config.getHost() + ":" + config.getPort());

        bootstrap.connect(config.getHost(), config.getPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                channel = future.channel();
                log.info("[" + endpoint.getLabel() + "] Connected to " + config.getHost() + ":" + config.getPort());
            } else {
                log.warning("[" + endpoint.getLabel() + "] Connection to "
                        + config.getHost() + ":" + config.getPort()
                        + " failed: " + future.cause().getMessage()
                        + ". Retrying in " + config.getReconnectDelaySeconds() + "s");
                scheduleReconnect(future.channel().eventLoop());
            }
        });
    }
}
