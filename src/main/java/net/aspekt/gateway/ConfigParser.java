package net.aspekt.gateway;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.File;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.SAXException;

/**
 * Parses a gateway configuration XML file into a {@link GatewayConfig} model.
 *
 * <p>The file is validated against the bundled XSD schema
 * ({@code gateway-config.xsd}) before unmarshalling, so invalid config
 * files are rejected early with a descriptive error.
 *
 * <p>Usage:
 * <pre>{@code
 *   GatewayConfig config = ConfigParser.parse(new File("example-config.xml"));
 * }</pre>
 */
public final class ConfigParser {

    private static final Logger log = Logger.getLogger(ConfigParser.class.getName());
    private static final String SCHEMA_RESOURCE = "/gateway-config.xsd";

    private ConfigParser() {}

    /**
     * Parse and validate a gateway configuration file.
     *
     * @param configFile path to the XML configuration file
     * @return populated {@link GatewayConfig}
     * @throws ConfigException if the file cannot be read, fails schema
     *                         validation, or cannot be unmarshalled
     */
    public static GatewayConfig parse(File configFile) throws ConfigException {
        log.log(Level.INFO, "Loading gateway config from: {0}", configFile.getAbsolutePath());

        Schema schema = loadSchema();
        JAXBContext ctx = createJaxbContext();

        try {
            Unmarshaller um = ctx.createUnmarshaller();
            um.setSchema(schema); // validation happens during unmarshal
            GatewayConfig config = (GatewayConfig) um.unmarshal(configFile);
            log.log(Level.INFO, "Loaded {0} websocket-server(s), {1} tcp-client(s), {2} forward rule(s)", new Object[] {
                config.getWebSocketServers().size(),
                config.getTcpClients().size(),
                config.getForwards().size()
            });
            return config;
        } catch (JAXBException e) {
            throw new ConfigException("Failed to parse config file: " + configFile, e);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Schema loadSchema() throws ConfigException {
        log.log(Level.FINE, "Loading XSD schema from classpath: {0}", SCHEMA_RESOURCE);
        URL schemaUrl = ConfigParser.class.getResource(SCHEMA_RESOURCE);
        if (schemaUrl == null) {
            throw new ConfigException("Bundled XSD schema not found on classpath: " + SCHEMA_RESOURCE);
        }
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            Schema schema = sf.newSchema(schemaUrl);
            log.log(Level.FINE, "XSD schema loaded successfully");
            return schema;
        } catch (SAXException e) {
            throw new ConfigException("Failed to load XSD schema", e);
        }
    }

    private static JAXBContext createJaxbContext() throws ConfigException {
        log.log(Level.FINE, "Creating JAXB context for {0}", GatewayConfig.class.getName());
        try {
            JAXBContext ctx = JAXBContext.newInstance(GatewayConfig.class);
            log.log(Level.FINE, "JAXB context created successfully");
            return ctx;
        } catch (JAXBException e) {
            throw new ConfigException("Failed to create JAXB context", e);
        }
    }
}
