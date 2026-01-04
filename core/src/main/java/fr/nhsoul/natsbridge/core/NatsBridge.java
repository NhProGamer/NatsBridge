package fr.nhsoul.natsbridge.core;

import fr.nhsoul.natsbridge.common.api.NatsAPI;
import fr.nhsoul.natsbridge.common.config.NatsConfig;
import fr.nhsoul.natsbridge.common.exception.NatsException;
import fr.nhsoul.natsbridge.common.logger.NatsLogger;
import fr.nhsoul.natsbridge.core.api.NatsAPIImpl;
import fr.nhsoul.natsbridge.core.config.ConfigLoader;
import fr.nhsoul.natsbridge.core.connection.NatsConnectionManager;
import fr.nhsoul.natsbridge.core.subscription.DefaultSubscriptionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Main entry point of the NATS library.
 * Handles initialization, connection, and provides access to the API.
 */
public class NatsBridge {

    private static NatsLogger logger = new DefaultSlf4jLogger(NatsBridge.class);
    private static volatile NatsBridge instance;

    private final NatsConfig config;
    private final NatsConnectionManager connectionManager;
    private final DefaultSubscriptionManager subscriptionManager;
    private final NatsAPI api;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private NatsBridge(@NotNull NatsConfig config) {
        this.config = config;
        this.connectionManager = new NatsConnectionManager(config);
        this.subscriptionManager = new DefaultSubscriptionManager(connectionManager);
        this.api = new NatsAPIImpl(connectionManager, subscriptionManager);

        logger.info("NatsBridge initialized with configuration: servers={}, auth={}, tls={}",
                config.getServers(),
                config.getAuth() != null && config.getAuth().isEnabled(),
                config.getTls() != null && config.getTls().isEnabled());
    }

    /**
     * Initializes the NATS library with a configuration file.
     *
     * @param configFile the configuration file
     * @return the initialized instance
     * @throws NatsException.ConfigurationException if initialization fails
     */
    @NotNull
    public static synchronized NatsBridge initialize(@NotNull File configFile, @Nullable NatsLogger natsLogger) {
        if (instance != null) {
            instance.getLogger().warn("NatsBridge already initialized, returning existing instance");
            return instance;
        }

        if (natsLogger != null) {
            logger = natsLogger;
        }

        NatsConfig config = ConfigLoader.loadFromFile(configFile);
        return initialize(config, logger);
    }

    /**
     * Initializes the NATS library with a configuration.
     *
     * @param config     the NATS configuration
     * @param natsLogger the logger to use (optional)
     * @return the initialized instance
     */
    @NotNull
    public static synchronized NatsBridge initialize(@NotNull NatsConfig config, @Nullable NatsLogger natsLogger) {
        if (instance != null) {
            instance.getLogger().warn("NatsBridge already initialized, returning existing instance");
            return instance;
        }

        if (natsLogger != null) {
            logger = natsLogger;
        }

        instance = new NatsBridge(config);
        return instance;
    }

    /**
     * Initializes the NATS library with a configuration file.
     *
     * @param configFile the configuration file
     * @return the initialized instance
     */
    @NotNull
    public static synchronized NatsBridge initialize(@NotNull File configFile) {
        return initialize(configFile, null);
    }

    /**
     * Initializes the library with the default configuration.
     *
     * @return the initialized instance
     */
    @NotNull
    public static synchronized NatsBridge initializeDefault() {
        return initialize(ConfigLoader.createDefaultConfig(), null);
    }

    /**
     * Gets the NatsBridge instance (must be initialized beforehand).
     *
     * @return the instance or null if not initialized
     */
    @Nullable
    public static NatsBridge getInstance() {
        return instance;
    }

    /**
     * Gets the NatsBridge instance or throws an exception if not initialized.
     *
     * @return the instance
     * @throws IllegalStateException if not initialized
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
     * Starts the NATS connection and subscriptions.
     *
     * @return CompletableFuture that completes when everything is started
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

        return CompletableFuture.runAsync(() -> {
            try {
                connectionManager.connect();
                subscriptionManager.subscribeAll();
                logger.info("NatsBridge started successfully");
            } catch (Exception e) {
                logger.error("Failed to start NatsBridge", e);
                initialized.set(false);
                throw (e instanceof RuntimeException) ? (RuntimeException) e
                        : new NatsException.ConnectionException("Failed to start NatsBridge", e);
            }
        });
    }

    /**
     * Properly shuts down the NATS library.
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
     * Gets the NATS API to publish messages.
     *
     * @return the NATS API
     */
    @NotNull
    public NatsAPI getAPI() {
        return api;
    }

    /**
     * Gets the SubscriptionManager for advanced subscription management.
     * This method is mainly used by plugin scanners to load subscriptions
     * generated by the annotation processor.
     *
     * @return the SubscriptionManager
     */
    @NotNull
    public DefaultSubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }

    /**
     * Gets the current configuration.
     *
     * @return the NATS configuration
     */
    @NotNull
    public NatsConfig getConfig() {
        return config;
    }

    /**
     * Gets the logger used by the library.
     *
     * @return the logger
     */
    @NotNull
    public NatsLogger getLogger() {
        return logger;
    }

    /**
     * Checks if the library is initialized and started.
     *
     * @return true if initialized and started
     */
    public boolean isInitialized() {
        return initialized.get() && !shutdown.get();
    }

    /**
     * Checks if the NATS connection is active.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connectionManager.isConnected();
    }

    /**
     * Gets the connection status.
     *
     * @return the current status
     */
    @NotNull
    public String getConnectionStatus() {
        return connectionManager.getConnectionStatus();
    }

    /**
     * Resets the instance (useful for tests).
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }
}