# Refactor Plan: GatewayModel

## Goal

Introduce a `GatewayModel` that owns the runtime topology — the labeled endpoint
registry and the wired forwarding rules — so this state is no longer scattered as
local variables inside `Main`.  The model is a plain data/logic class; all Netty
construction stays outside it.

---

## Motivation

`Main.java` currently holds:
- Five separate `List<XxxServer/Client>` component lists
- A `LinkedHashMap<String, ConnectionEndpoint>` registry
- Inline duplicate-label and unknown-label validation
- The loop that calls `endpoint.addTarget(other)` for every `<forward>` rule

Moving the registry and forwarding rules into a model makes the runtime topology
inspectable and mutable after startup, which is the foundation needed for a future
GUI that dynamically adds/removes connections without touching the XML config.

---

## New files

### `net.aspekt.gateway.ForwardRule`

A simple value record — the runtime counterpart to `ForwardConfig` (which is a
JAXB XML model and should stay that way).

```
record ForwardRule(String from, String to)
```

No Netty, no JAXB.

---

### `net.aspekt.gateway.GatewayModel`

Owns the registry and forward-rule list.  All mutation goes through this class so
it stays internally consistent.

**Fields (private)**
- `LinkedHashMap<String, ConnectionEndpoint> endpoints` — preserves insertion
  order, which is the startup order
- `List<ForwardRule> forwardRules`

**Mutation methods** — these are the only way to add/remove topology:

| Method | Effect |
|---|---|
| `addEndpoint(label, endpoint)` | Registers endpoint; throws `IllegalArgumentException` on duplicate label |
| `removeEndpoint(label)` | Removes endpoint *and* every forward rule that references it (calls `removeTarget` as needed); no-op if absent |
| `addForwardRule(from, to)` | Validates both labels exist, calls `fromEndpoint.addTarget(toEndpoint)`, appends `ForwardRule` to list |
| `removeForwardRule(from, to)` | Removes the first matching `ForwardRule`, calls `fromEndpoint.removeTarget(toEndpoint)` |

**Query methods**

| Method | Returns |
|---|---|
| `getEndpoint(label)` | `ConnectionEndpoint` or `null` |
| `hasEndpoint(label)` | `boolean` |
| `getEndpoints()` | Unmodifiable ordered map view |
| `getForwardRules()` | Unmodifiable list |

No Netty, no config parsing, no component lifecycle logic.

---

### `net.aspekt.gateway.GatewayModelBuilder`

A one-shot builder that translates a `GatewayConfig` into a populated
`GatewayModel` and retains the Netty component lists for `Main` to consume.

**Constructor:** `GatewayModelBuilder(GatewayConfig config)`

**`GatewayModel build() throws ConfigException`**
- Iterates all five endpoint types in config order, constructing the endpoint
  object and its paired server/client object, calling `model.addEndpoint()`.
  Wraps the `IllegalArgumentException` on duplicate label into `ConfigException`.
- Iterates all `<forward>` rules, calling `model.addForwardRule()`.
  Throws `ConfigException` for unknown labels.
- Returns the populated `GatewayModel`.

**Component accessors** (call after `build()`):
- `List<WebSocketServer> getWsServers()`
- `List<TcpServer> getTcpServers()`
- `List<TcpHub> getTcpHubs()`
- `List<TcpClient> getTcpClients()`
- `List<UdpMulticast> getUdpMulticasts()`

`Main` calls these to get the lists it needs to start components and wire the
shutdown hook.  The builder holds only these references — no other logic.

---

## Modified files

### `net.aspekt.gateway.ConnectionEndpoint` (interface)

Add one new method:

```java
void removeTarget(ConnectionEndpoint target);
```

Required so `GatewayModel.removeForwardRule` and `removeEndpoint` can unwire
targets dynamically.

---

### `net.aspekt.gateway.AbstractConnectionEndpoint`

Implement `removeTarget`:

```java
public void removeTarget(ConnectionEndpoint target) {
    targets.remove(target);
}
```

`targets` is already a `CopyOnWriteArrayList`, so this is thread-safe.

---

### `net.aspekt.gateway.Main`

Replace the five inline loops that create endpoints/servers and the `registry`
map with:

```java
GatewayModelBuilder builder = new GatewayModelBuilder(config);
GatewayModel model;
try {
    model = builder.build();
} catch (ConfigException e) {
    log.log(Level.SEVERE, "Failed to build gateway model: " + e.getMessage(), e);
    System.exit(1);
    return;
}

List<WebSocketServer>  wsServers     = builder.getWsServers();
List<TcpServer>        tcpServers    = builder.getTcpServers();
List<TcpHub>           tcpHubs       = builder.getTcpHubs();
List<TcpClient>        tcpClients    = builder.getTcpClients();
List<UdpMulticast>     udpMulticasts = builder.getUdpMulticasts();
```

Everything from "3. Start all components" downward in Main stays unchanged.

The `registry.isEmpty()` guard becomes `model.getEndpoints().isEmpty()`.

---

### `ARCHITECTURE.md` and `ARCHITECTURE.puml`

Update the package-structure table and data-flow description to reflect:
- `GatewayModel` — runtime topology: endpoint registry + forward rules
- `ForwardRule` — value record for a wired forwarding rule
- `GatewayModelBuilder` — translates `GatewayConfig` into a `GatewayModel`
- `Main` — now delegates construction to `GatewayModelBuilder`; retains lifecycle responsibility

---

## What does NOT change

- `ConfigParser` — still returns `GatewayConfig`; no changes
- `GatewayConfig` / `ForwardConfig` and all JAXB models — unchanged
- All endpoint classes, server/client classes, handlers — unchanged (except the
  one-line `removeTarget` addition in `AbstractConnectionEndpoint`)
- Netty pipeline, threading, buffer ownership — unchanged
- Shutdown hook order — unchanged

---

## File summary

| File | Action |
|---|---|
| `ForwardRule.java` | **New** |
| `GatewayModel.java` | **New** |
| `GatewayModelBuilder.java` | **New** |
| `ConnectionEndpoint.java` | **Modified** — add `removeTarget` |
| `AbstractConnectionEndpoint.java` | **Modified** — implement `removeTarget` |
| `Main.java` | **Modified** — use builder, simplify |
| `ARCHITECTURE.md` | **Modified** — update docs |
| `ARCHITECTURE.puml` | **Modified** — update diagram |
