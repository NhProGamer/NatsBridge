package fr.nhsoul.natsbridge.core;

import fr.nhsoul.natsbridge.common.api.NatsAPI;
import fr.nhsoul.natsbridge.common.config.NatsConfig;
import fr.nhsoul.natsbridge.common.exception.NatsException;
import fr.nhsoul.natsbridge.core.api.NatsAPIImpl;
import fr.nhsoul.natsbridge.core.config.ConfigLoader;
import fr.nhsoul.natsbridge.core.connection.NatsConnectionManager;
import fr.nhsoul.natsbridge.core.subscription.SubscriptionManager;
import fr.nhsoul.natsbridge.core.subscription.impl.DefaultSubscriptionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Point d'entrée principal de la librairie NATS.
 * Gère l'initialisation, la connexion et fournit l'accès à l'API.
 */
public class NatsBridge {

    private static final Logger logger = LoggerFactory.getLogger(NatsBridge.class);
    private static volatile NatsBridge instance;

    private final NatsConfig config;
    private final NatsConnectionManager connectionManager;
    private final SubscriptionManager subscriptionManager;
    private final NatsAPI api;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private NatsBridge(@NotNull NatsConfig config) {
        this.config = config;
        this.connectionManager = NatsConnectionManager.getInstance(config);
        this.subscriptionManager = new DefaultSubscriptionManager(connectionManager);
        this.api = new NatsAPIImpl(connectionManager, subscriptionManager);

        logger.info("NatsBridge initialized with configuration: servers={}, auth={}, tls={}",
                config.getServers(),
                config.getAuth() != null && config.getAuth().isEnabled(),
                config.getTls() != null && config.getTls().isEnabled());
    }

    /**
     * Initialise la librairie NATS avec un fichier de configuration.
     *
     * @param configFile le fichier de configuration
     * @return l'instance initialisée
     * @throws NatsException.ConfigurationException si l'initialisation échoue
     */
    @NotNull
    public static synchronized NatsBridge initialize(@NotNull File configFile) {
        if (instance != null) {
            logger.warn("NatsBridge already initialized, returning existing instance");
            return instance;
        }

        NatsConfig config = ConfigLoader.loadFromFile(configFile);
        return initialize(config);
    }

    /**
     * Initialise la librairie NATS avec une configuration.
     *
     * @param config la configuration NATS
     * @return l'instance initialisée
     */
    @NotNull
    public static synchronized NatsBridge initialize(@NotNull NatsConfig config) {
        if (instance != null) {
            logger.warn("NatsBridge already initialized, returning existing instance");
            return instance;
        }

        instance = new NatsBridge(config);
        return instance;
    }

    /**
     * Initialise la librairie avec la configuration par défaut.
     *
     * @return l'instance initialisée
     */
    @NotNull
    public static synchronized NatsBridge initializeDefault() {
        return initialize(ConfigLoader.createDefaultConfig());
    }

    /**
     * Obtient l'instance de NatsBridge (doit être initialisée au préalable).
     *
     * @return l'instance ou null si non initialisée
     */
    @Nullable
    public static NatsBridge getInstance() {
        return instance;
    }

    /**
     * Obtient l'instance de NatsBridge ou lève une exception si non initialisée.
     *
     * @return l'instance
     * @throws IllegalStateException si non initialisée
     */
    @NotNull
    public static NatsBridge getInstanceOrThrow() {
        NatsBridge lib = getInstance();
        if (lib == null) {
            throw new IllegalStateException("NatsBridge not initialized. Call initialize() first.");
        }
        return lib;
    }

    /**
     * Démarre la connexion NATS et les souscriptions.
     *
     * @return CompletableFuture qui se complète quand tout est démarré
     */
    public CompletableFuture<Void> start() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warn("NatsBridge already started");
            return CompletableFuture.completedFuture(null);
        }

        if (shutdown.get()) {
            throw new IllegalStateException("Cannot start NatsBridge after shutdown");
        }

        logger.info("Starting NatsBridge...");

        return connectionManager.connect()
                .thenRun(() -> {
                    subscriptionManager.subscribeAll();
                    logger.info("NatsBridge started successfully");
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to start NatsBridge", throwable);
                    initialized.set(false);
                    throw new NatsException.ConnectionException("Failed to start NatsBridge", throwable);
                });
    }

    /**
     * Arrête proprement la librairie NATS.
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            logger.warn("NatsBridge already shut down");
            return;
        }

        logger.info("Shutting down NatsBridge...");

        try {
            subscriptionManager.shutdown();
            connectionManager.disconnect();
            logger.info("NatsBridge shut down successfully");
        } catch (Exception e) {
            logger.error("Error during NatsBridge shutdown", e);
        } finally {
            initialized.set(false);
        }
    }

    /**
     * Enregistre un plugin pour scanner ses annotations @NatsSubscribe.
     *
     * @param plugin l'objet plugin à scanner
     */
    public void registerPlugin(@NotNull Object plugin) {
        if (shutdown.get()) {
            throw new IllegalStateException("Cannot register plugins after shutdown");
        }

        subscriptionManager.scanAndRegister(plugin);
    }

    /**
     * Obtient l'API NATS pour publier des messages.
     *
     * @return l'API NATS
     */
    @NotNull
    public NatsAPI getAPI() {
        return api;
    }

    /**
     * Obtient la configuration actuelle.
     *
     * @return la configuration NATS
     */
    @NotNull
    public NatsConfig getConfig() {
        return config;
    }

    /**
     * Vérifie si la librairie est initialisée et démarrée.
     *
     * @return true si initialisée et démarrée
     */
    public boolean isInitialized() {
        return initialized.get() && !shutdown.get();
    }

    /**
     * Vérifie si la connexion NATS est active.
     *
     * @return true si connecté
     */
    public boolean isConnected() {
        return connectionManager.isConnected();
    }

    /**
     * Obtient le statut de connexion.
     *
     * @return le statut actuel
     */
    @NotNull
    public String getConnectionStatus() {
        return connectionManager.getConnectionStatus();
    }

    /**
     * Reset l'instance (utile pour les tests).
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
        NatsConnectionManager.resetInstance();
    }
}