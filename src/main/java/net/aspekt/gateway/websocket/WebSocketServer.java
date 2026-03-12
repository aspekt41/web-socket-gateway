package net.aspekt.gateway.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * Netty-based WebSocket server.
 *
 * <p>The pipeline is:
 * <pre>
 *   HttpServerCodec
 *   → HttpObjectAggregator        (assembles full HTTP requests)
 *   → WebSocketServerCompressionHandler  (per-message deflate, optional)
 *   → WebSocketServerProtocolHandler     (HTTP→WS upgrade handshake)
 *   → WebSocketServerHandler             (application logic)
 * </pre>
 *
 * <p>Call {@link #start()} to bind and begin accepting connections.
 * Call {@link #stop()} (or close via try-with-resources) to shut down.
 */
public class WebSocketServer implements AutoCloseable {

    private static final Logger log = Logger.getLogger(WebSocketServer.class.getName());

    /** Netty limits for the HTTP upgrade request (not the WS frames). */
    private static final int HTTP_MAX_CONTENT_LENGTH = 65536;

    private final WebSocketServerConfig config;
    private final WebSocketEndpoint endpoint;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public WebSocketServer(WebSocketServerConfig config, WebSocketEndpoint endpoint) {
        this.config = config;
        this.endpoint = endpoint;
    }

    /**
     * Binds to the configured address/port and starts accepting WebSocket
     * connections.  Returns as soon as the socket is bound.
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
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpServerCodec());
                        p.addLast(new HttpObjectAggregator(HTTP_MAX_CONTENT_LENGTH));
                        p.addLast(new WebSocketServerCompressionHandler());
                        p.addLast(new WebSocketServerProtocolHandler(
                                config.getPath(),
                                /*subprotocols=*/ null,
                                /*allowExtensions=*/ true,
                                config.getMaxFrameBytes()));
                        p.addLast(new WebSocketServerHandler(endpoint));
                    }
                });

        InetSocketAddress address = new InetSocketAddress(config.getBindAddress(), config.getPort());
        ChannelFuture bindFuture = bootstrap.bind(address).sync();
        serverChannel = bindFuture.channel();

        log.info("[" + endpoint.getLabel() + "] WebSocket server listening on ws://"
                + config.getBindAddress() + ":" + config.getPort() + config.getPath()
                + " (max frame " + config.getMaxFrameBytes() + " bytes)");
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

    /** Gracefully shuts down the server and releases all Netty resources. */
    public void stop() {
        log.info("[" + endpoint.getLabel() + "] Stopping WebSocket server");
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
