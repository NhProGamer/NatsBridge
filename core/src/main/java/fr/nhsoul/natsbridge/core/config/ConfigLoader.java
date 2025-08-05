package fr.nhsoul.natsbridge.core.config;

import fr.nhsoul.natsbridge.common.config.NatsConfig;
import fr.nhsoul.natsbridge.common.exception.NatsException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Chargeur de configuration NATS depuis un fichier YAML.
 * Gère les valeurs par défaut et la validation de la configuration.
 */
public class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

    // Valeurs par défaut
    private static final List<String> DEFAULT_SERVERS = Arrays.asList("nats://127.0.0.1:4222");
    private static final int DEFAULT_MAX_RECONNECTS = -1; // Infini
    private static final long DEFAULT_RECONNECT_WAIT_MS = 2000;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 5000;

    /**
     * Charge la configuration depuis un fichier.
     *
     * @param configFile le fichier de configuration
     * @return la configuration NATS
     * @throws NatsException.ConfigurationException si le chargement échoue
     */
    @NotNull
    public static NatsConfig loadFromFile(@NotNull File configFile) {
        logger.info("Loading NATS configuration from: {}", configFile.getAbsolutePath());

        if (!configFile.exists()) {
            logger.warn("Configuration file not found, using default configuration");
            return createDefaultConfig();
        }

        try (FileInputStream fis = new FileInputStream(configFile)) {
            return loadFromStream(fis);
        } catch (IOException e) {
            logger.error("Failed to read configuration file: {}", configFile.getAbsolutePath(), e);
            throw new NatsException.ConfigurationException("Failed to read configuration file", e);
        }
    }

    /**
     * Charge la configuration depuis un InputStream.
     *
     * @param inputStream le flux de données
     * @return la configuration NATS
     * @throws NatsException.ConfigurationException si le chargement échoue
     */
    @NotNull
    public static NatsConfig loadFromStream(@NotNull InputStream inputStream) {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);

            if (data == null) {
                logger.warn("Empty configuration file, using defaults");
                return createDefaultConfig();
            }

            return parseConfiguration(data);

        } catch (Exception e) {
            logger.error("Failed to parse YAML configuration", e);
            throw new NatsException.ConfigurationException("Failed to parse YAML configuration", e);
        }
    }

    /**
     * Crée une configuration par défaut.
     *
     * @return la configuration par défaut
     */
    @NotNull
    public static NatsConfig createDefaultConfig() {
        logger.info("Creating default NATS configuration");

        NatsConfig.ReconnectConfig reconnect = new NatsConfig.ReconnectConfig(
                DEFAULT_MAX_RECONNECTS,
                DEFAULT_RECONNECT_WAIT_MS,
                DEFAULT_CONNECTION_TIMEOUT_MS
        );

        return new NatsConfig(
                DEFAULT_SERVERS,
                null, // Pas d'auth par défaut
                null, // Pas de TLS par défaut
                reconnect
        );
    }

    @SuppressWarnings("unchecked")
    private static NatsConfig parseConfiguration(@NotNull Map<String, Object> data) {
        Map<String, Object> natsConfig = (Map<String, Object>) data.get("nats");
        if (natsConfig == null) {
            logger.warn("No 'nats' section found in configuration, using defaults");
            return createDefaultConfig();
        }

        // Serveurs
        List<String> servers = parseServers(natsConfig);

        // Authentification
        NatsConfig.AuthConfig auth = parseAuth(natsConfig);

        // TLS
        NatsConfig.TlsConfig tls = parseTls(natsConfig);

        // Reconnexion
        NatsConfig.ReconnectConfig reconnect = parseReconnect(natsConfig);

        return new NatsConfig(servers, auth, tls, reconnect);
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseServers(@NotNull Map<String, Object> config) {
        Object serversObj = config.get("servers");
        if (serversObj instanceof List) {
            List<String> servers = (List<String>) serversObj;
            if (!servers.isEmpty()) {
                logger.debug("Loaded {} NATS servers from configuration", servers.size());
                return servers;
            }
        }

        logger.debug("Using default NATS servers");
        return DEFAULT_SERVERS;
    }

    @SuppressWarnings("unchecked")
    private static NatsConfig.AuthConfig parseAuth(@NotNull Map<String, Object> config) {
        Map<String, Object> authConfig = (Map<String, Object>) config.get("auth");
        if (authConfig == null) {
            return null;
        }

        boolean enabled = parseBoolean(authConfig, "enabled", false);
        if (!enabled) {
            return null;
        }

        String username = parseString(authConfig, "username", null);
        String password = parseString(authConfig, "password", null);
        String token = parseString(authConfig, "token", null);

        if (token == null && (username == null || password == null)) {
            logger.warn("Authentication enabled but no valid credentials provided");
            return null;
        }

        logger.debug("Authentication configured: {}",
                token != null ? "token" : "username/password");

        return new NatsConfig.AuthConfig(enabled, username, password, token);
    }

    @SuppressWarnings("unchecked")
    private static NatsConfig.TlsConfig parseTls(@NotNull Map<String, Object> config) {
        Map<String, Object> tlsConfig = (Map<String, Object>) config.get("tls");
        if (tlsConfig == null) {
            return null;
        }

        boolean enabled = parseBoolean(tlsConfig, "enabled", false);
        if (!enabled) {
            return null;
        }

        String keystore = parseString(tlsConfig, "keystore", null);
        String keystorePassword = parseString(tlsConfig, "keystore_password", null);
        String truststore = parseString(tlsConfig, "truststore", null);
        String truststorePassword = parseString(tlsConfig, "truststore_password", null);

        logger.debug("TLS configuration loaded");

        return new NatsConfig.TlsConfig(enabled, keystore, keystorePassword, truststore, truststorePassword);
    }

    @SuppressWarnings("unchecked")
    private static NatsConfig.ReconnectConfig parseReconnect(@NotNull Map<String, Object> config) {
        Map<String, Object> reconnectConfig = (Map<String, Object>) config.get("reconnect");
        if (reconnectConfig == null) {
            reconnectConfig = Map.of(); // Map vide pour utiliser les défauts
        }

        int maxReconnects = parseInt(reconnectConfig, "max_reconnects", DEFAULT_MAX_RECONNECTS);
        long reconnectWait = parseLong(reconnectConfig, "reconnect_wait", DEFAULT_RECONNECT_WAIT_MS);
        long connectionTimeout = parseLong(reconnectConfig, "connection_timeout", DEFAULT_CONNECTION_TIMEOUT_MS);

        logger.debug("Reconnect configuration: maxReconnects={}, reconnectWait={}ms, connectionTimeout={}ms",
                maxReconnects, reconnectWait, connectionTimeout);

        return new NatsConfig.ReconnectConfig(maxReconnects, reconnectWait, connectionTimeout);
    }

    private static String parseString(@NotNull Map<String, Object> config, @NotNull String key, String defaultValue) {
        Object value = config.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    private static boolean parseBoolean(@NotNull Map<String, Object> config, @NotNull String key, boolean defaultValue) {
        Object value = config.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    private static int parseInt(@NotNull Map<String, Object> config, @NotNull String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private static long parseLong(@NotNull Map<String, Object> config, @NotNull String key, long defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
}
