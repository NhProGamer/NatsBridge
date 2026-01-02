package fr.nhsoul.natsbridge.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.nhsoul.natsbridge.common.api.NatsAPI;
import fr.nhsoul.natsbridge.core.NatsBridge;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Plugin principal pour Velocity.
 * Initialise la librairie NATS et la rend disponible aux autres plugins.
 */
@Plugin(id = "natsbridge", name = "NatsBridge", version = "1.0.0-SNAPSHOT", description = "NATS messaging library for Minecraft servers", authors = {
        "NhPro" })
public class VelocityNatsPlugin {

    private static VelocityNatsPlugin instance;

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private NatsBridge natsBridge;

    @Inject
    public VelocityNatsPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(@NotNull ProxyInitializeEvent event) {
        instance = this;
        server.getEventManager().register(instance, new VelocityNatsBridgeConnectedEvent());

        try {
            // Créer le fichier de configuration s'il n'existe pas
            setupConfigFile();

            // Initialiser la librairie NATS
            Path configPath = dataDirectory.resolve("nats-config.yml");
            File configFile = configPath.toFile();

            // Initialisation de NatsBridge
            try {
                NatsBridge.initialize(configFile, new VelocityNatsLogger(logger));
                natsBridge = NatsBridge.getInstance();
                natsBridge.start().thenRun(() -> {
                    logger.info("NATS library started successfully");
                    server.getEventManager().fire(new VelocityNatsBridgeConnectedEvent());
                }).exceptionally(throwable -> {
                    logger.error("Failed to start NATS library", throwable);
                    return null;
                });
            } catch (Exception e) {
                logger.error("Could not initialize NatsBridge", e);
            }

            // Enregistrer les commandes
            VelocityNatsCommand command = new VelocityNatsCommand(this, natsBridge);
            server.getCommandManager().register("nats", command);

            logger.info("NatsBridge plugin enabled successfully");

        } catch (Exception e) {
            logger.error("Failed to enable NatsBridge plugin", e);
        }
    }

    @Subscribe
    public void onProxyShutdown(@NotNull ProxyShutdownEvent event) {

        if (natsBridge != null) {
            logger.info("Shutting down NATS library...");
            natsBridge.shutdown();
        }

        natsBridge.resetInstance();
        instance = null;

        logger.info("NatsBridge plugin disabled");
    }

    /**
     * Obtient l'instance du plugin Velocity.
     *
     * @return l'instance du plugin
     */
    @NotNull
    public static VelocityNatsPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("VelocityNatsPlugin not initialized");
        }
        return instance;
    }

    /**
     * Obtient l'API NATS.
     *
     * @return l'API NATS
     */
    @NotNull
    public static NatsAPI getNatsAPI() {
        return getInstance().getNatsBridge().getAPI();
    }

    /**
     * Obtient la librairie NATS.
     *
     * @return la librairie NATS
     */
    @NotNull
    public NatsBridge getNatsBridge() {
        if (natsBridge == null) {
            throw new IllegalStateException("NatsBridge not initialized");
        }
        return natsBridge;
    }

    /**
     * Obtient le serveur proxy.
     *
     * @return le serveur proxy
     */
    @NotNull
    public ProxyServer getServer() {
        return server;
    }

    /**
     * Obtient le logger.
     *
     * @return le logger
     */
    @NotNull
    public Logger getLogger() {
        return logger;
    }

    /**
     * Obtient le répertoire de données.
     *
     * @return le répertoire de données
     */
    @NotNull
    public Path getDataDirectory() {
        return dataDirectory;
    }

    private void setupConfigFile() throws IOException {
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }

        Path configFile = dataDirectory.resolve("nats-config.yml");
        if (!Files.exists(configFile)) {
            try (InputStream defaultConfig = getClass().getClassLoader().getResourceAsStream("nats-config.yml")) {
                if (defaultConfig != null) {
                    Files.copy(defaultConfig, configFile, StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Created default NATS configuration file");
                } else {
                    logger.warn("Default configuration file not found in resources");
                }
            }
        }
    }
}
