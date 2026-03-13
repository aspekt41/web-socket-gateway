package net.aspekt.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import org.junit.jupiter.api.Test;

class ConfigParserTest {

    private File resource(String name) throws URISyntaxException {
        URL url = getClass().getClassLoader().getResource(name);
        assertNotNull(url, "Test resource not found: " + name);
        return new File(url.toURI());
    }

    @Test
    void parse_validConfig_returnsNonNull() throws Exception {
        XmlGatewayConfig cfg = ConfigParser.parse(resource("valid-config.xml"));
        assertNotNull(cfg);
    }

    @Test
    void parse_validConfig_findsWebSocketServer() throws Exception {
        XmlGatewayConfig cfg = ConfigParser.parse(resource("valid-config.xml"));
        assertEquals(1, cfg.getWebSocketServers().size());
        assertEquals("ws1", cfg.getWebSocketServers().get(0).getLabel());
    }

    @Test
    void parse_validConfig_findsTcpServer() throws Exception {
        XmlGatewayConfig cfg = ConfigParser.parse(resource("valid-config.xml"));
        assertEquals(1, cfg.getTcpServers().size());
        assertEquals("tcp-srv", cfg.getTcpServers().get(0).getLabel());
    }

    @Test
    void parse_validConfig_findsTcpClient() throws Exception {
        XmlGatewayConfig cfg = ConfigParser.parse(resource("valid-config.xml"));
        assertEquals(1, cfg.getTcpClients().size());
        assertEquals("tcp-cli", cfg.getTcpClients().get(0).getLabel());
    }

    @Test
    void parse_validConfig_findsTcpHub() throws Exception {
        XmlGatewayConfig cfg = ConfigParser.parse(resource("valid-config.xml"));
        assertEquals(1, cfg.getTcpHubs().size());
        assertEquals("hub1", cfg.getTcpHubs().get(0).getLabel());
    }

    @Test
    void parse_validConfig_findsUdpMulticast() throws Exception {
        XmlGatewayConfig cfg = ConfigParser.parse(resource("valid-config.xml"));
        assertEquals(1, cfg.getUdpMulticasts().size());
        assertEquals("udp1", cfg.getUdpMulticasts().get(0).getLabel());
    }

    @Test
    void parse_validConfig_findsForwardRules() throws Exception {
        XmlGatewayConfig cfg = ConfigParser.parse(resource("valid-config.xml"));
        assertEquals(2, cfg.getForwards().size());
    }

    @Test
    void parse_minimalEmptyConfig_returnsEmptyLists() throws Exception {
        XmlGatewayConfig cfg = ConfigParser.parse(resource("minimal-config.xml"));
        assertTrue(cfg.getWebSocketServers().isEmpty());
        assertTrue(cfg.getTcpServers().isEmpty());
        assertTrue(cfg.getTcpClients().isEmpty());
        assertTrue(cfg.getTcpHubs().isEmpty());
        assertTrue(cfg.getUdpMulticasts().isEmpty());
        assertTrue(cfg.getForwards().isEmpty());
    }

    @Test
    void parse_invalidConfig_throwsConfigException() throws URISyntaxException {
        File f = resource("invalid-config.xml");
        assertThrows(ConfigException.class, () -> ConfigParser.parse(f));
    }

    @Test
    void parse_nonExistentFile_throwsException() {
        // JAXB RI may throw IllegalArgumentException rather than JAXBException for
        // a non-existent file; the important contract is that some exception is raised.
        File missing = new File("/no/such/file.xml");
        assertThrows(Exception.class, () -> ConfigParser.parse(missing));
    }
}
