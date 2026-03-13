package net.aspekt.gateway.tcp.hub;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import net.aspekt.gateway.ConnectionEndpoint;
import net.aspekt.gateway.GatewayConnection;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * Netty-based raw TCP hub server.
 *
 * <p>The pipeline is minimal — no HTTP or WebSocket codecs:
 * <pre>
 *   TcpHubHandler  (registers client channel on endpoint, broadcasts inbound bytes
 *                   to all other connected clients and to forwarding targets)
 * </pre>
 *
 * <p>Call {@link #start()} to bind and begin accepting connections.
 * Call {@link #stop()} (or close via try-with-resources) to shut down.
 */
public class TcpHub implements GatewayConnection {

    private static final Logger log = Logger.getLogger(TcpHub.class.getName());

    private final XmlTcpHubConfig config;
    private final TcpHubEndpoint endpoint;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public TcpHub(XmlTcpHubConfig config, TcpHubEndpoint endpoint) {
        this.config = config;
        this.endpoint = endpoint;
    }

    public ConnectionEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * Binds to the configured address/port and starts accepting raw TCP connections.
     * Returns as soon as the socket is bound.
     *
     * @throws InterruptedException if the calling thread is interrupted while
     *                              waiting for the bind to complete
     */
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new TcpHubHandler(endpoint));
                    }
                });

        InetSocketAddress address = new InetSocketAddress(config.getBindAddress(), config.getPort());
        ChannelFuture bindFuture = bootstrap.bind(address).sync();
        serverChannel = bindFuture.channel();

        log.info("[" + endpoint.getLabel() + "] TCP hub listening on tcp://" + config.getBindAddress() + ":"
                + config.getPort());
    }

    /**
     * Blocks until the server channel is closed.  Useful in a main thread
     * when you want the JVM to stay alive.
     */
    public void awaitShutdown() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }

    /** Gracefully shuts down the hub server and releases all Netty resources. */
    public void stop() {
        log.info("[" + endpoint.getLabel() + "] Stopping TCP hub");
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
