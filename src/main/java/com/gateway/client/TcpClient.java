package com.gateway.client;

import com.gateway.config.TcpClientConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty-based outbound TCP client.
 *
 * <p>The client connects to the configured remote host:port and automatically
 * reconnects after a configurable delay whenever the connection is lost.
 *
 * <p>Pipeline (first iteration — plain bytes, no framing):
 * <pre>
 *   TcpClientHandler  (application logic / logging)
 * </pre>
 * Frame decoders (line, length-prefixed, etc.) can be inserted into the
 * pipeline in a later iteration.
 *
 * <p>Call {@link #start()} to initiate the first connection attempt.
 * Call {@link #stop()} (or close via try-with-resources) to disconnect and
 * release resources.
 */
public class TcpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TcpClient.class);

    private final String bridgeName;
    private final TcpClientConfig config;

    private EventLoopGroup eventLoopGroup;
    private Bootstrap bootstrap;
    private volatile Channel channel;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public TcpClient(String bridgeName, TcpClientConfig config) {
        this.bridgeName = bridgeName;
        this.config = config;
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
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        config.getConnectTimeoutSeconds() * 1000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        // TODO (bridge iteration): add frame decoder here, e.g.
                        //   p.addLast(new LineBasedFrameDecoder(8192));
                        p.addLast(new TcpClientHandler(bridgeName, TcpClient.this));
                    }
                });

        connect();
    }

    /** Gracefully closes the connection and shuts down the event loop group. */
    @Override
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            log.info("[{}] Stopping TCP client", bridgeName);
            if (channel != null) {
                channel.close();
            }
            if (eventLoopGroup != null) {
                eventLoopGroup.shutdownGracefully();
            }
        }
    }

    @Override
    public void close() {
        stop();
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
        log.info("[{}] Reconnecting to {}:{} in {}s",
                bridgeName, config.getHost(), config.getPort(), delaySeconds);
        eventLoop.schedule(this::connect, delaySeconds, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private void connect() {
        if (stopped.get()) {
            return;
        }
        log.info("[{}] Connecting to {}:{}", bridgeName, config.getHost(), config.getPort());

        bootstrap.connect(config.getHost(), config.getPort())
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        channel = future.channel();
                        log.info("[{}] Connected to {}:{}",
                                bridgeName, config.getHost(), config.getPort());
                    } else {
                        log.warn("[{}] Connection to {}:{} failed: {}. Retrying in {}s",
                                bridgeName,
                                config.getHost(),
                                config.getPort(),
                                future.cause().getMessage(),
                                config.getReconnectDelaySeconds());
                        scheduleReconnect(future.channel().eventLoop());
                    }
                });
    }
}
