# Architecture

## Purpose

This application bridges TCP client connections to WebSocket server connections.
It is intended to allow browser-based JavaScript clients (which cannot open raw TCP
sockets) to communicate with legacy TCP services.

## Configuration

The application is driven entirely by an XML configuration file passed as the sole
command-line argument. The file is validated at startup against a bundled XSD schema
(`src/main/resources/gateway-config.xsd`) using JAXB, so malformed config is rejected
before any network work begins.

The schema defines one or more **bridges**. Each bridge pairs:
- a **WebSocket server** endpoint (bind address, port, URL path, max frame size)
- a **TCP client** endpoint (remote host, port, connect timeout, reconnect delay)

See `example-config.xml` for a minimal working example.

## Package Structure

```
com.gateway
├── Main.java                  Entry point; reads config, starts all bridges
├── config/
│   ├── GatewayConfig          JAXB root element (<gateway-config>)
│   ├── BridgeConfig           JAXB model for a single <bridge>
│   ├── WebSocketServerConfig  JAXB model for <websocket-server>
│   ├── TcpClientConfig        JAXB model for <tcp-client>
│   ├── ConfigParser           Loads and validates the XML config file
│   └── ConfigException        Checked exception for config errors
├── server/
│   ├── WebSocketServer        Netty server; binds and accepts WebSocket connections
│   └── WebSocketServerHandler Netty handler; processes inbound WebSocket frames
└── client/
    ├── TcpClient              Netty client; connects to remote TCP host with auto-reconnect
    └── TcpClientHandler       Netty handler; processes inbound bytes from the TCP server
```

## Network Layer (Netty)

[Netty](https://netty.io/) is used for all I/O. Both the server and client use the
non-blocking NIO transport.

### WebSocket server pipeline

```
NioServerSocketChannel
  └── NioSocketChannel (per accepted connection)
        ├── HttpServerCodec                    HTTP framing
        ├── HttpObjectAggregator               Assembles full HTTP request for WS upgrade
        ├── WebSocketServerCompressionHandler  Per-message deflate (RFC 7692)
        ├── WebSocketServerProtocolHandler     HTTP → WebSocket upgrade handshake
        └── WebSocketServerHandler             Application logic
```

### TCP client pipeline

```
NioSocketChannel
  └── TcpClientHandler    Application logic / raw byte logging
```

Frame decoders (e.g. line-based, length-prefixed) can be inserted into the TCP client
pipeline in a future iteration once the wire format is known.

## Reconnection

`TcpClient` automatically reconnects after a lost connection. When `TcpClientHandler`
receives a `channelInactive` event it calls back to `TcpClient.scheduleReconnect()`,
which reschedules the `connect()` call on the same Netty `EventLoop` after the
configured delay. No extra threads are involved.

## Bridging (Not Yet Implemented)

The two ends are not yet wired together. Both handlers contain `TODO` comments marking
the points where data forwarding will be added in the next iteration:

- `WebSocketServerHandler` — inbound WebSocket frames should be forwarded to the TCP
  client channel
- `TcpClientHandler` — inbound TCP bytes should be forwarded to all connected WebSocket
  clients

## Shutdown

A JVM shutdown hook (registered in `Main`) calls `stop()` on every `TcpClient` and
`WebSocketServer` in order, giving Netty a chance to close channels and release its
event loop threads gracefully before the process exits.

## Logging

`java.util.logging` (JUL) is used — no third-party logging dependencies. The default
JUL configuration writes `INFO` and above to the console. To customise log levels,
supply a `logging.properties` file at startup:

```
java -Djava.util.logging.config.file=logging.properties -jar ...
```
