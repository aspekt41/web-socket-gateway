package net.aspekt.gateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import net.aspekt.gateway.tcp.client.TcpClient;
import net.aspekt.gateway.tcp.hub.TcpHub;
import net.aspekt.gateway.tcp.server.TcpServer;
import net.aspekt.gateway.udp.multicast.UdpMulticast;
import net.aspekt.gateway.websocket.WebSocketServer;

/**
 * Runtime topology model for the gateway.
 *
 * <p>Owns the labeled connection registry and the list of wired forwarding rules. All mutation goes
 * through this class so the topology stays internally consistent. This class holds references to the
 * Netty-backed {@link GatewayConnection} instances but uses no Netty APIs directly.
 */
public class GatewayModel {

    private final LinkedHashMap<String, WebSocketServer> wsServers = new LinkedHashMap<>();
    private final LinkedHashMap<String, TcpServer> tcpServers = new LinkedHashMap<>();
    private final LinkedHashMap<String, TcpHub> tcpHubs = new LinkedHashMap<>();
    private final LinkedHashMap<String, TcpClient> tcpClients = new LinkedHashMap<>();
    private final LinkedHashMap<String, UdpMulticast> udpMulticasts = new LinkedHashMap<>();
    private final List<ForwardRule> forwardRules = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Add methods
    // -----------------------------------------------------------------------

    /**
     * Registers a {@link WebSocketServer} under the given label.
     *
     * @throws IllegalArgumentException if the label is already registered
     */
    public void addWebSocketServer(String label, WebSocketServer server) {
        checkDuplicate(label);
        wsServers.put(label, server);
    }

    /**
     * Registers a {@link TcpServer} under the given label.
     *
     * @throws IllegalArgumentException if the label is already registered
     */
    public void addTcpServer(String label, TcpServer server) {
        checkDuplicate(label);
        tcpServers.put(label, server);
    }

    /**
     * Registers a {@link TcpHub} under the given label.
     *
     * @throws IllegalArgumentException if the label is already registered
     */
    public void addTcpHub(String label, TcpHub hub) {
        checkDuplicate(label);
        tcpHubs.put(label, hub);
    }

    /**
     * Registers a {@link TcpClient} under the given label.
     *
     * @throws IllegalArgumentException if the label is already registered
     */
    public void addTcpClient(String label, TcpClient client) {
        checkDuplicate(label);
        tcpClients.put(label, client);
    }

    /**
     * Registers a {@link UdpMulticast} under the given label.
     *
     * @throws IllegalArgumentException if the label is already registered
     */
    public void addUdpMulticast(String label, UdpMulticast multicast) {
        checkDuplicate(label);
        udpMulticasts.put(label, multicast);
    }

    // -----------------------------------------------------------------------
    // Typed list accessors
    // -----------------------------------------------------------------------

    /** Returns an unmodifiable ordered list of all registered {@link WebSocketServer} instances. */
    public List<WebSocketServer> getWebSocketServers() {
        return Collections.unmodifiableList(new ArrayList<>(wsServers.values()));
    }

    /** Returns an unmodifiable ordered list of all registered {@link TcpServer} instances. */
    public List<TcpServer> getTcpServers() {
        return Collections.unmodifiableList(new ArrayList<>(tcpServers.values()));
    }

    /** Returns an unmodifiable ordered list of all registered {@link TcpHub} instances. */
    public List<TcpHub> getTcpHubs() {
        return Collections.unmodifiableList(new ArrayList<>(tcpHubs.values()));
    }

    /** Returns an unmodifiable ordered list of all registered {@link TcpClient} instances. */
    public List<TcpClient> getTcpClients() {
        return Collections.unmodifiableList(new ArrayList<>(tcpClients.values()));
    }

    /** Returns an unmodifiable ordered list of all registered {@link UdpMulticast} instances. */
    public List<UdpMulticast> getUdpMulticasts() {
        return Collections.unmodifiableList(new ArrayList<>(udpMulticasts.values()));
    }

    /**
     * Returns an unmodifiable collection of all registered connections across all types, in
     * insertion order (WebSocket servers, then TCP servers, hubs, clients, UDP multicasts).
     */
    public Collection<GatewayConnection> getConnections() {
        List<GatewayConnection> all = new ArrayList<>();
        all.addAll(wsServers.values());
        all.addAll(tcpServers.values());
        all.addAll(tcpHubs.values());
        all.addAll(tcpClients.values());
        all.addAll(udpMulticasts.values());
        return Collections.unmodifiableList(all);
    }

    // -----------------------------------------------------------------------
    // Endpoint accessors (derived from connections)
    // -----------------------------------------------------------------------

    /** Returns the endpoint for the connection registered under {@code label}, or {@code null}. */
    public ConnectionEndpoint getEndpoint(String label) {
        GatewayConnection conn = findConnection(label);
        return conn != null ? conn.getEndpoint() : null;
    }

    /** Returns {@code true} if a connection is registered under {@code label}. */
    public boolean hasEndpoint(String label) {
        return findConnection(label) != null;
    }

    // -----------------------------------------------------------------------
    // Remove
    // -----------------------------------------------------------------------

    /**
     * Removes the connection with the given label, and removes every forward rule that references
     * it (either as source or target), unwiring the corresponding targets. No-op if the label is
     * not registered.
     */
    public void removeEndpoint(String label) {
        if (findConnection(label) == null) {
            return;
        }
        List<ForwardRule> toRemove = new ArrayList<>();
        for (ForwardRule rule : forwardRules) {
            if (rule.from().equals(label) || rule.to().equals(label)) {
                toRemove.add(rule);
            }
        }
        for (ForwardRule rule : toRemove) {
            // Resolve endpoints before removal so that the label being removed is still found.
            ConnectionEndpoint from = getEndpoint(rule.from());
            ConnectionEndpoint to = getEndpoint(rule.to());
            if (from != null && to != null) {
                from.removeTarget(to);
            }
            forwardRules.remove(rule);
        }
        wsServers.remove(label);
        tcpServers.remove(label);
        tcpHubs.remove(label);
        tcpClients.remove(label);
        udpMulticasts.remove(label);
    }

    // -----------------------------------------------------------------------
    // Forward rules
    // -----------------------------------------------------------------------

    /**
     * Wires a forwarding rule from {@code from} to {@code to}.
     *
     * <p>Calls {@code fromEndpoint.addTarget(toEndpoint)} and records the rule.
     *
     * @throws IllegalArgumentException if either label is not registered
     */
    public void addForwardRule(String from, String to) {
        ConnectionEndpoint fromEp = getEndpoint(from);
        if (fromEp == null) {
            throw new IllegalArgumentException("Forward rule references unknown label '" + from + "'");
        }
        ConnectionEndpoint toEp = getEndpoint(to);
        if (toEp == null) {
            throw new IllegalArgumentException("Forward rule references unknown label '" + to + "'");
        }
        fromEp.addTarget(toEp);
        forwardRules.add(new ForwardRule(from, to));
    }

    /**
     * Removes the first matching forward rule and unwires the corresponding target. No-op if no
     * such rule exists.
     */
    public void removeForwardRule(String from, String to) {
        ForwardRule rule = new ForwardRule(from, to);
        if (!forwardRules.remove(rule)) {
            return;
        }
        ConnectionEndpoint fromEp = getEndpoint(from);
        ConnectionEndpoint toEp = getEndpoint(to);
        if (fromEp != null && toEp != null) {
            fromEp.removeTarget(toEp);
        }
    }

    /** Returns an unmodifiable view of the current forwarding rules. */
    public List<ForwardRule> getForwardRules() {
        return Collections.unmodifiableList(forwardRules);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void checkDuplicate(String label) {
        if (labelExists(label)) {
            throw new IllegalArgumentException("Duplicate connection label: " + label);
        }
    }

    private boolean labelExists(String label) {
        return wsServers.containsKey(label)
                || tcpServers.containsKey(label)
                || tcpHubs.containsKey(label)
                || tcpClients.containsKey(label)
                || udpMulticasts.containsKey(label);
    }

    private GatewayConnection findConnection(String label) {
        GatewayConnection c;
        if ((c = wsServers.get(label)) != null) return c;
        if ((c = tcpServers.get(label)) != null) return c;
        if ((c = tcpHubs.get(label)) != null) return c;
        if ((c = tcpClients.get(label)) != null) return c;
        if ((c = udpMulticasts.get(label)) != null) return c;
        return null;
    }
}
