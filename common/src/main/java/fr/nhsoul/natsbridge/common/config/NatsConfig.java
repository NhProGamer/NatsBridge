package fr.nhsoul.natsbridge.common.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Configuration pour la connexion NATS.
 * Contient tous les paramètres nécessaires pour établir et maintenir une connexion.
 */
public class NatsConfig {

    private final List<String> servers;
    private final AuthConfig auth;
    private final TlsConfig tls;
    private final ReconnectConfig reconnect;
    private final String loggingLevel;

    public NatsConfig(@NotNull List<String> servers,
                      @Nullable AuthConfig auth,
                      @Nullable TlsConfig tls,
                      @NotNull ReconnectConfig reconnect,
                      @NotNull String loggingLevel) {
        this.servers = Objects.requireNonNull(servers, "servers cannot be null");
        this.auth = auth;
        this.tls = tls;
        this.reconnect = Objects.requireNonNull(reconnect, "reconnect config cannot be null");
        this.loggingLevel = Objects.requireNonNull(loggingLevel, "logging level cannot be null");
    }

    @NotNull
    public List<String> getServers() {
        return servers;
    }

    @Nullable
    public AuthConfig getAuth() {
        return auth;
    }

    @Nullable
    public TlsConfig getTls() {
        return tls;
    }

    @NotNull
    public ReconnectConfig getReconnect() {
        return reconnect;
    }

    @NotNull
    public String getLoggingLevel() {
        return loggingLevel;
    }

    /**
     * Configuration d'authentification NATS.
     */
    public static class AuthConfig {
        private final boolean enabled;
        private final String username;
        private final String password;
        private final String token;

        public AuthConfig(boolean enabled, @Nullable String username, @Nullable String password, @Nullable String token) {
            this.enabled = enabled;
            this.username = username;
            this.password = password;
            this.token = token;
        }

        public boolean isEnabled() {
            return enabled;
        }

        @Nullable
        public String getUsername() {
            return username;
        }

        @Nullable
        public String getPassword() {
            return password;
        }

        @Nullable
        public String getToken() {
            return token;
        }
    }

    /**
     * Configuration TLS pour la connexion NATS.
     */
    public static class TlsConfig {
        private final boolean enabled;
        private final String keystore;
        private final String keystorePassword;
        private final String truststore;
        private final String truststorePassword;

        public TlsConfig(boolean enabled, @Nullable String keystore, @Nullable String keystorePassword,
                         @Nullable String truststore, @Nullable String truststorePassword) {
            this.enabled = enabled;
            this.keystore = keystore;
            this.keystorePassword = keystorePassword;
            this.truststore = truststore;
            this.truststorePassword = truststorePassword;
        }

        public boolean isEnabled() {
            return enabled;
        }

        @Nullable
        public String getKeystore() {
            return keystore;
        }

        @Nullable
        public String getKeystorePassword() {
            return keystorePassword;
        }

        @Nullable
        public String getTruststore() {
            return truststore;
        }

        @Nullable
        public String getTruststorePassword() {
            return truststorePassword;
        }
    }

    /**
     * Configuration de reconnexion automatique.
     */
    public static class ReconnectConfig {
        private final int maxReconnects;
        private final long reconnectWaitMs;
        private final long connectionTimeoutMs;

        public ReconnectConfig(int maxReconnects, long reconnectWaitMs, long connectionTimeoutMs) {
            this.maxReconnects = maxReconnects;
            this.reconnectWaitMs = reconnectWaitMs;
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public int getMaxReconnects() {
            return maxReconnects;
        }

        public long getReconnectWaitMs() {
            return reconnectWaitMs;
        }

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }
    }
}
