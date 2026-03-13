package net.aspekt.gateway;

/**
 * Runtime value record for a wired forwarding rule.
 *
 * <p>This is the runtime counterpart to {@link XmlForwardConfig} (the JAXB XML model).
 * It carries no JAXB annotations and has no dependency on the config layer.
 */
public record ForwardRule(String from, String to) {}
