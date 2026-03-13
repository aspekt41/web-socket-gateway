package net.aspekt.gateway;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * JAXB model for {@code <forward from="..." to="..."/>}.
 *
 * <p>Declares a unidirectional forwarding rule: data arriving at the endpoint
 * identified by {@code from} will be sent to the endpoint identified by {@code to}.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlForwardConfig implements ForwardConfig {

    @XmlAttribute(name = "from", required = true)
    private String from;

    @XmlAttribute(name = "to", required = true)
    private String to;

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    @Override
    public String toString() {
        return "ForwardConfig{from='" + from + "', to='" + to + "'}";
    }
}
