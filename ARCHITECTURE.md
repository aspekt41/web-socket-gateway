# Architecture

## Purpose

This application is a flexible, configuration-driven network gateway that routes
raw binary data between heterogeneous transport endpoints — WebSocket servers, raw
TCP servers, outbound TCP clients, TCP hub servers, and UDP multicast endpoints.
Any endpoint can forward data to any other endpoint; fan-out to multiple targets is
supported.

A common use-case is bridging browser-based JavaScript clients (which cannot open
raw TCP sockets) to legacy TCP services, but the gateway is not limited to that
topology.

## Configuration

The application is driven entirely by an XML configuration file passed as the sole
command-line argument.  The file is validated at startup against a bundled XSD
schema (`src/main/resources/gateway-config.xsd`) using JAXB, so malformed config
is rejected before any network work begins.

The schema (namespace `http://github.com/web-socket-gateway/config/v1`) defines
five kinds of **labeled endpoints** and a **forwarding rule**:

| Element | Description |
|---|---|
| `<websocket-server>` | Binds a Netty WebSocket server; browser/JS clients connect here |
| `<tcp-server>` | Binds a raw TCP server; plain TCP clients connect here |
| `<tcp-client>` | Opens an outbound TCP connection to a remote host with auto-reconnect |
| `<tcp-hub>` | Binds a TCP server where inbound data is broadcast to all other connected peers |
| `<udp-multicast>` | Joins a UDP multicast group; receives datagrams and can send to the group |
| `<forward from="A" to="B"/>` | Routes all data arriving at endpoint `A` to endpoint `B` |

Multiple `<forward>` rules with the same `from` achieve fan-out.  There is no
built-in concept of a "bridge" — bidirectional bridges are expressed as two
forwarding rules:

```xml
<forward from="ws-endpoint"  to="tcp-endpoint"/>
<forward from="tcp-endpoint" to="ws-endpoint"/>
```

See `example-config.xml` for a working example.

### WebSocket server attributes

| Attribute | Default | Description |
|---|---|---|
| `label` | *(required)* | Unique name used in `<forward>` rules |
| `port` | *(required)* | TCP port to listen on |
| `bind-address` | `0.0.0.0` | IP address to bind to |
| `path` | `/ws` | WebSocket upgrade URL path |
| `max-frame-bytes` | `65536` | Maximum WebSocket frame payload size |

### TCP server attributes

| Attribute | Default | Description |
|---|---|---|
| `label` | *(required)* | Unique name used in `<forward>` rules |
| `port` | *(required)* | TCP port to listen on |
| `bind-address` | `0.0.0.0` | IP address to bind to |

### TCP client attributes

| Attribute | Default | Description |
|---|---|---|
| `label` | *(required)* | Unique name used in `<forward>` rules |
| `host` | *(required)* | Remote hostname or IP address |
| `port` | *(required)* | Remote TCP port |
| `reconnect-delay-seconds` | `5` | Delay before reconnect after a lost connection |
| `connect-timeout-seconds` | `10` | Timeout for the initial TCP handshake |

### TCP hub attributes

| Attribute | Default | Description |
|---|---|---|
| `label` | *(required)* | Unique name used in `<forward>` rules |
| `port` | *(required)* | TCP port to listen on |
| `bind-address` | `0.0.0.0` | IP address to bind to |

The TCP hub is similar to a TCP server but with a key difference: inbound data
from any connected client is broadcast to **all other** connected clients (peer
broadcast), as well as forwarded to any configured `<forward>` targets.  The
sender is excluded from the peer broadcast to prevent echo-back.

### UDP multicast attributes

| Attribute | Default | Description |
|---|---|---|
| `label` | *(required)* | Unique name used in `<forward>` rules |
| `group` | *(required)* | Multicast group IP address (e.g. `239.0.0.1`) |
| `port` | *(required)* | UDP port for the multicast group |
| `bind-address` | `0.0.0.0` | Local IP address to bind to |
| `network-interface` | *(none)* | Network interface name to use on multi-homed hosts |

## Package Structure

```
net.aspekt.gateway
├── Main.java                        Entry point; reads config, wires endpoints, starts all components
├── GatewayConfig                    JAXB root element (<gateway-config>)
├── ConfigParser                     Loads and validates the XML config file
├── ConfigException                  Checked exception for config errors
├── ForwardConfig                    JAXB model for <forward from="…" to="…"/>
├── ConnectionEndpoint               Interface: send, addTarget, onDataReceived
├── AbstractConnectionEndpoint       Base class: label, thread-safe target list, fan-out logic
├── websocket/
│   ├── WebSocketServerConfig        JAXB model for <websocket-server>
│   ├── WebSocketServer              Netty server; binds and accepts WebSocket connections
│   ├── WebSocketEndpoint            Broadcasts to all connected WebSocket clients (ChannelGroup)
│   └── WebSocketServerHandler       Netty handler; processes inbound WebSocket frames
├── tcp/server/
│   ├── TcpServerConfig              JAXB model for <tcp-server>
│   ├── TcpServer                    Netty server; binds and accepts raw TCP connections
│   ├── TcpServerEndpoint            Broadcasts raw bytes to all connected TCP server clients
│   └── TcpServerHandler             Netty handler; processes inbound bytes from TCP clients
├── tcp/client/
│   ├── TcpClientConfig              JAXB model for <tcp-client>
│   ├── TcpClient                    Netty client; connects to remote TCP host with auto-reconnect
│   ├── TcpClientEndpoint            Writes to the single outbound TCP channel
│   └── TcpClientHandler             Netty handler; processes inbound bytes from the TCP server
├── tcp/hub/
│   ├── TcpHubConfig                 JAXB model for <tcp-hub>
│   ├── TcpHub                       Netty server; binds and accepts hub TCP connections
│   ├── TcpHubEndpoint               Broadcasts to all peers (excluding sender) + forwards to targets
│   └── TcpHubHandler                Netty handler; processes inbound bytes from hub clients
└── udp/multicast/
    ├── UdpMulticastConfig           JAXB model for <udp-multicast>
    ├── UdpMulticast                 Netty UDP channel; joins multicast group, manages lifecycle
    ├── UdpMulticastEndpoint         Sends DatagramPackets to the multicast group address
    └── UdpMulticastHandler          Netty handler; processes inbound datagrams
```

## Data Flow

At startup `Main` creates a `ConnectionEndpoint` for every labeled element in the
config and registers it in a map keyed by label.  It then walks the `<forward>`
rules and calls `endpoint.addTarget(other)` to wire the graph.  After wiring, all
servers and clients are started.  If no connection entries of any kind are present
in the config, the gateway logs a warning and exits with code 0.

At runtime, when bytes arrive at any endpoint its handler calls
`endpoint.onDataReceived(buf)`.  `AbstractConnectionEndpoint.onDataReceived` fans
the buffer out to every registered target by retaining it once per target and
calling `target.send(buf)`.  Each endpoint's `send` implementation delivers to its
specific transport:

- **`WebSocketEndpoint.send`** — wraps the buffer in a `BinaryWebSocketFrame` and
  broadcasts it to all connected WebSocket clients via a `DefaultChannelGroup`.
- **`TcpServerEndpoint.send`** — writes the raw buffer to all connected raw-TCP
  clients via a `DefaultChannelGroup` (no framing added).
- **`TcpClientEndpoint.send`** — writes the raw buffer to the single outbound TCP
  channel, or discards it if the channel is absent or inactive.
- **`TcpHubEndpoint.send`** — broadcasts the raw buffer to all connected hub peers
  (excluding the originating channel) and forwards to configured targets.
- **`UdpMulticastEndpoint.send`** — wraps the buffer in a `DatagramPacket` and
  writes it to the multicast group address on the UDP channel.

Buffer ownership follows a clear contract: `onDataReceived` takes ownership of the
caller's reference; each `send` implementation takes ownership of the reference
passed to it.

## Network Layer (Netty)

[Netty](https://netty.io/) is used for all I/O.  All servers and clients use the
non-blocking NIO transport.

### WebSocket server pipeline

```
NioServerSocketChannel
  └── NioSocketChannel (per accepted connection)
        ├── HttpServerCodec                    HTTP framing
        ├── HttpObjectAggregator               Assembles full HTTP request for WS upgrade
        ├── WebSocketServerCompressionHandler  Per-message deflate (RFC 7692)
        ├── WebSocketServerProtocolHandler     HTTP → WebSocket upgrade handshake
        └── WebSocketServerHandler             Forwards frames to endpoint targets
```

Binary and text WebSocket frames are both forwarded as raw bytes.  Ping frames
receive a Pong reply.  Close frames close the channel.

### TCP server pipeline

```
NioServerSocketChannel
  └── NioSocketChannel (per accepted connection)
        └── TcpServerHandler    Registers client channel on endpoint; forwards inbound bytes
```

### TCP client pipeline

```
NioSocketChannel
  └── TcpClientHandler    Registers/clears channel on endpoint; forwards inbound bytes
```

Frame decoders (e.g. line-based, length-prefixed) can be inserted into either TCP
pipeline when the wire format requires it.

### TCP hub pipeline

```
NioServerSocketChannel
  └── NioSocketChannel (per accepted connection)
        └── TcpHubHandler    Registers channel; broadcasts inbound bytes to peers + targets
```

The hub handler uses a `ChannelMatcher` to exclude the originating channel from
peer broadcasts, preventing echo-back without requiring an O(n) scan.

### UDP multicast pipeline

```
NioDatagramChannel (single shared channel, not per-client)
  └── UdpMulticastHandler    Extracts datagram payload; forwards to configured targets
```

The channel joins the configured multicast group at startup (optionally binding to
a specific network interface) and leaves the group on shutdown.  Outbound data
sent to a `UdpMulticastEndpoint` is wrapped in a `DatagramPacket` addressed to the
group and delivered via the same shared channel.

## Reconnection

`TcpClient` automatically reconnects after a lost connection.  When
`TcpClientHandler` receives a `channelInactive` event it calls
`TcpClient.scheduleReconnect()`, which reschedules `connect()` on the same Netty
`EventLoop` after the configured delay.  No extra threads are involved.
`TcpClientEndpoint` clears its channel reference on disconnect so that any data
arriving during the gap is dropped cleanly rather than sent to an inactive channel.

## Shutdown

A JVM shutdown hook (registered in `Main`) calls `stop()` on every component in
order, giving Netty a chance to close channels and release event loop threads
gracefully before the process exits:

1. All `TcpClient` instances — stops reconnect scheduling and closes channels
2. All `UdpMulticast` instances — leaves multicast group, closes datagram channel
3. All `WebSocketServer` instances — closes WebSocket channels and server socket
4. All `TcpServer` instances — closes TCP client channels and server socket
5. All `TcpHub` instances — closes hub client channels and server socket

## Logging

`java.util.logging` (JUL) is used — no third-party logging dependencies.  The
default JUL configuration writes `INFO` and above to the console.  To customise
log levels, supply a `logging.properties` file at startup:

```
java -Djava.util.logging.config.file=logging.properties -jar web-socket-gateway.jar config.xml
```
