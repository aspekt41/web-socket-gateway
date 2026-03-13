package net.aspekt.gateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime topology model for the gateway.
 *
 * <p>Owns the labeled endpoint registry and the list of wired forwarding rules.
 * All mutation goes through this class so the topology stays internally consistent.
 * This class has no Netty dependency and no config-parsing logic.
 */
public class GatewayModel {

    private final LinkedHashMap<String, ConnectionEndpoint> endpoints = new LinkedHashMap<>();
    private final List<ForwardRule> forwardRules = new ArrayList<>();

    /**
     * Registers an endpoint under the given label.
     *
     * @throws IllegalArgumentException if the label is already registered
     */
    public void addEndpoint(String label, ConnectionEndpoint endpoint) {
        if (endpoints.containsKey(label)) {
            throw new IllegalArgumentException("Duplicate endpoint label: " + label);
        }
        endpoints.put(label, endpoint);
    }

    /**
     * Removes the endpoint with the given label, and removes every forward rule that
     * references it (either as source or target), unwiring the corresponding targets.
     * No-op if the label is not registered.
     */
    public void removeEndpoint(String label) {
        ConnectionEndpoint removed = endpoints.remove(label);
        if (removed == null) {
            return;
        }
        List<ForwardRule> toRemove = new ArrayList<>();
        for (ForwardRule rule : forwardRules) {
            if (rule.from().equals(label) || rule.to().equals(label)) {
                toRemove.add(rule);
            }
        }
        for (ForwardRule rule : toRemove) {
            ConnectionEndpoint from = endpoints.get(rule.from());
            ConnectionEndpoint to = endpoints.get(rule.to());
            if (from != null && to != null) {
                from.removeTarget(to);
            }
            forwardRules.remove(rule);
        }
    }

    /**
     * Wires a forwarding rule from {@code from} to {@code to}.
     *
     * <p>Calls {@code fromEndpoint.addTarget(toEndpoint)} and records the rule.
     *
     * @throws IllegalArgumentException if either label is not registered
     */
    public void addForwardRule(String from, String to) {
        ConnectionEndpoint fromEp = endpoints.get(from);
        if (fromEp == null) {
            throw new IllegalArgumentException("Forward rule references unknown label '" + from + "'");
        }
        ConnectionEndpoint toEp = endpoints.get(to);
        if (toEp == null) {
            throw new IllegalArgumentException("Forward rule references unknown label '" + to + "'");
        }
        fromEp.addTarget(toEp);
        forwardRules.add(new ForwardRule(from, to));
    }

    /**
     * Removes the first matching forward rule and unwires the corresponding target.
     * No-op if no such rule exists.
     */
    public void removeForwardRule(String from, String to) {
        ForwardRule rule = new ForwardRule(from, to);
        if (!forwardRules.remove(rule)) {
            return;
        }
        ConnectionEndpoint fromEp = endpoints.get(from);
        ConnectionEndpoint toEp = endpoints.get(to);
        if (fromEp != null && toEp != null) {
            fromEp.removeTarget(toEp);
        }
    }

    /** Returns the endpoint registered under {@code label}, or {@code null} if absent. */
    public ConnectionEndpoint getEndpoint(String label) {
        return endpoints.get(label);
    }

    /** Returns {@code true} if a endpoint is registered under {@code label}. */
    public boolean hasEndpoint(String label) {
        return endpoints.containsKey(label);
    }

    /** Returns an unmodifiable ordered view of all registered endpoints. */
    public Map<String, ConnectionEndpoint> getEndpoints() {
        return Collections.unmodifiableMap(endpoints);
    }

    /** Returns an unmodifiable view of the current forwarding rules. */
    public List<ForwardRule> getForwardRules() {
        return Collections.unmodifiableList(forwardRules);
    }
}
