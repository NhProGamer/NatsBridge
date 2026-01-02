package fr.nhsoul.natsbridge.core.connection;

import fr.nhsoul.natsbridge.common.config.NatsConfig;
import fr.nhsoul.natsbridge.common.exception.NatsException;
import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.time.Duration;

/**
 * Gestionnaire de connexion NATS simplifié.
 * Délègue la gestion de la connexion et de la reconnexion à la librairie native
 * NATS.
 */
public class NatsConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(NatsConnectionManager.class);

    private final NatsConfig config;
    private Connection connection;

    public NatsConnectionManager(@NotNull NatsConfig config) {
        this.config = config;
    }

    /**
     * Établit la connexion NATS de manière synchrone.
     * La reconnexion est gérée automatiquement par la librairie NATS.
     */
    public void connect() {
        if (isConnected()) {
            return;
        }

        try {
            logger.info("Connecting to NATS servers: {}", config.getServers());

            Options.Builder optionsBuilder = new Options.Builder()
                    .servers(config.getServers().toArray(new String[0]))
                    .maxReconnects(config.getReconnect().getMaxReconnects())
                    .reconnectWait(Duration.ofMillis(config.getReconnect().getReconnectWaitMs()))
                    .connectionTimeout(Duration.ofMillis(config.getReconnect().getConnectionTimeoutMs()))
                    .connectionListener((conn, type) -> {
                        if (type == ConnectionListener.Events.CONNECTED) {
                            logger.info("NATS Connected to {}:{}", conn.getServerInfo().getHost(),
                                    conn.getServerInfo().getPort());
                        } else if (type == ConnectionListener.Events.DISCONNECTED) {
                            logger.warn("NATS Disconnected");
                        } else if (type == ConnectionListener.Events.RECONNECTED) {
                            logger.info("NATS Reconnected to {}:{}", conn.getServerInfo().getHost(),
                                    conn.getServerInfo().getPort());
                        } else if (type == ConnectionListener.Events.CLOSED) {
                            logger.info("NATS Connection Closed");
                        }
                    });

            setupAuthentication(optionsBuilder);
            setupTls(optionsBuilder);

            this.connection = Nats.connect(optionsBuilder.build());
            // Nats.connect is blocking until connected or timeout

        } catch (Exception e) {
            throw new NatsException.ConnectionException("Failed to connect to NATS", e);
        }
    }

    public void disconnect() {
        if (connection != null && connection.getStatus() != Connection.Status.CLOSED) {
            try {
                logger.info("Closing NATS connection...");
                connection.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Nullable
    public Connection getConnection() {
        return connection;
    }

    public boolean isConnected() {
        return connection != null && connection.getStatus() == Connection.Status.CONNECTED;
    }

    public String getConnectionStatus() {
        return connection != null ? connection.getStatus().toString() : "DISCONNECTED";
    }

    private void setupAuthentication(Options.Builder builder) {
        NatsConfig.AuthConfig auth = config.getAuth();
        if (auth != null && auth.isEnabled()) {
            if (auth.getToken() != null) {
                builder.token(auth.getToken().toCharArray());
            } else if (auth.getUsername() != null && auth.getPassword() != null) {
                builder.userInfo(auth.getUsername(), auth.getPassword());
            }
        }
    }

    private void setupTls(Options.Builder builder) throws Exception {
        NatsConfig.TlsConfig tls = config.getTls();
        if (tls != null && tls.isEnabled()) {
            builder.sslContext(SSLContext.getDefault());
        }
    }
}
