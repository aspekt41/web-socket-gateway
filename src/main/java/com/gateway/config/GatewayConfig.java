package com.gateway.config;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Root JAXB model for {@code <gateway-config>}.
 *
 * <p>Maps to the {@code GatewayConfigType} complex type in
 * {@code gateway-config.xsd}.
 */
@XmlRootElement(name = "gateway-config",
                namespace = GatewayConfig.NAMESPACE)
@XmlAccessorType(XmlAccessType.FIELD)
public class GatewayConfig {

    public static final String NAMESPACE =
            "http://github.com/web-socket-gateway/config/v1";

    @XmlElement(name = "bridge", namespace = NAMESPACE, required = true)
    private List<BridgeConfig> bridges = new ArrayList<>();

    /** Returns an unmodifiable view of the configured bridges. */
    public List<BridgeConfig> getBridges() {
        return Collections.unmodifiableList(bridges);
    }

    @Override
    public String toString() {
        return "GatewayConfig{bridges=" + bridges + "}";
    }
}
