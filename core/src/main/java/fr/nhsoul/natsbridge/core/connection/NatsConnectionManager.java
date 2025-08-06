package fr.nhsoul.natsbridge.core.connection;

import fr.nhsoul.natsbridge.common.config.NatsConfig;
import fr.nhsoul.natsbridge.common.exception.NatsException;
import io.nats.client.*;
import io.nats.client.api.ServerInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.nats.client.Connection.Status.CONNECTED;

/**
 * Gestionnaire de connexion NATS thread-safe avec reconnexion automatique.
 * Implémente le pattern singleton pour partager une seule connexion.
 */
public class NatsConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(NatsConnectionManager.class);
    private static volatile NatsConnectionManager instance;

    private final AtomicReference<Connection> connection = new AtomicReference<>();
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final NatsConfig config;

    private NatsConnectionManager(@NotNull NatsConfig config) {
        this.config = config;
    }

    /**
     * Obtient l'instance singleton du gestionnaire de connexion.
     *
     * @param config la configuration NATS
     * @return l'instance du gestionnaire
     */
    public static NatsConnectionManager getInstance(@NotNull NatsConfig config) {
        if (instance == null) {
            synchronized (NatsConnectionManager.class) {
                if (instance == null) {
                    instance = new NatsConnectionManager(config);
                }
            }
        }
        return instance;
    }

    /**
     * Obtient l'instance existante du gestionnaire (doit être initialisée au préalable).
     *
     * @return l'instance du gestionnaire
     * @throws IllegalStateException si le gestionnaire n'a pas été initialisé
     */
    public static NatsConnectionManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("NatsConnectionManager must be initialized with config first");
        }
        return instance;
    }

    /**
     * Établit la connexion NATS avec la configuration fournie.
     *
     * @return CompletableFuture qui se complète quand la connexion est établie
     */
    public CompletableFuture<Void> connect() {
        if (isConnected()) {
            return CompletableFuture.completedFuture(null);
        }

        if (!isConnecting.compareAndSet(false, true)) {
            // Une connexion est déjà en cours
            return waitForConnection();
        }

        return CompletableFuture.runAsync(() -> {
            try {
                logger.info("Connecting to NATS servers: {}", config.getServers());

                Options.Builder optionsBuilder = new Options.Builder()
                        .servers(config.getServers().toArray(new String[0]))
                        .maxReconnects(config.getReconnect().getMaxReconnects())
                        .reconnectWait(Duration.ofMillis(config.getReconnect().getReconnectWaitMs()))
                        .connectionTimeout(Duration.ofMillis(config.getReconnect().getConnectionTimeoutMs()))
                        .connectionListener(this::onConnectionEvent);
                        //.errorListener(this::onError);

                // Configuration de l'authentification
                setupAuthentication(optionsBuilder);

                // Configuration TLS
                setupTls(optionsBuilder);

                Connection newConnection = Nats.connect(optionsBuilder.build());
                connection.set(newConnection);

                logger.info("Successfully connected to NATS server: {}", getServerInfo());

            } catch (Exception e) {
                logger.error("Failed to connect to NATS", e);
                throw new NatsException.ConnectionException("Failed to connect to NATS", e);
            } finally {
                isConnecting.set(false);
            }
        });
    }

    /**
     * Ferme la connexion NATS proprement.
     */
    public void disconnect() {
        Connection conn = connection.getAndSet(null);
        if (conn != null && conn.getStatus() != Connection.Status.CLOSED) {
            try {
                logger.info("Disconnecting from NATS server");
                conn.close();
                logger.info("Successfully disconnected from NATS");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while disconnecting from NATS", e);
            }
        }
    }

    /**
     * Obtient la connexion NATS active.
     *
     * @return la connexion active ou null si non connecté
     */
    @Nullable
    public Connection getConnection() {
        return connection.get();
    }

    /**
     * Vérifie si la connexion est active.
     *
     * @return true si connecté
     */
    public boolean isConnected() {
        Connection conn = connection.get();
        return conn != null && conn.getStatus() == CONNECTED;
    }

    /**
     * Obtient le statut de connexion.
     *
     * @return le statut actuel
     */
    @NotNull
    public String getConnectionStatus() {
        Connection conn = connection.get();
        if (conn == null) {
            return "DISCONNECTED";
        }
        return conn.getStatus().toString();
    }

    private void setupAuthentication(Options.Builder builder) {
        NatsConfig.AuthConfig auth = config.getAuth();
        if (auth != null && auth.isEnabled()) {
            if (auth.getToken() != null) {
                builder.token(auth.getToken().toCharArray());
                logger.debug("Using token authentication");
            } else if (auth.getUsername() != null && auth.getPassword() != null) {
                builder.userInfo(auth.getUsername(), auth.getPassword());
                logger.debug("Using username/password authentication");
            }
        }
    }

    private void setupTls(Options.Builder builder) throws Exception {
        NatsConfig.TlsConfig tls = config.getTls();
        if (tls != null && tls.isEnabled()) {
            // Configuration TLS basique
            SSLContext sslContext = SSLContext.getDefault();
            builder.sslContext(sslContext);
            logger.debug("TLS enabled for NATS connection");
        }
    }

    private CompletableFuture<Void> waitForConnection() {
        return CompletableFuture.runAsync(() -> {
            while (isConnecting.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NatsException.ConnectionException("Interrupted while waiting for connection");
                }
            }
            if (!isConnected()) {
                throw new NatsException.ConnectionException("Failed to establish connection");
            }
        });
    }

    private void onConnectionEvent(Connection conn, ConnectionListener.Events event) {
        switch (event) {
            case CONNECTED:
                logger.info("NATS connection established: {}", getServerInfo());
                break;
            case RECONNECTED:
                logger.info("NATS connection reestablished: {}", getServerInfo());
                break;
            case DISCONNECTED:
                logger.warn("NATS connection lost");
                break;
            case CLOSED:
                logger.info("NATS connection closed");
                break;
        }
    }

    private void onError(Connection conn, Consumer consumer, boolean pullMode, Exception exception) {
        logger.error("NATS error occurred", exception);
    }

    private String getServerInfo() {
        Connection conn = connection.get();
        if (conn != null) {
            ServerInfo info = conn.getServerInfo();
            if (info != null) {
                return String.format("%s:%d (v%S)", info.getHost(), info.getPort(), info.getVersion());
            }
        }
        return "unknown";
    }

    /**
     * Reset l'instance (utile pour les tests).
     */
    public static void resetInstance() {
        synchronized (NatsConnectionManager.class) {
            if (instance != null) {
                instance.disconnect();
                instance = null;
            }
        }
    }
}
