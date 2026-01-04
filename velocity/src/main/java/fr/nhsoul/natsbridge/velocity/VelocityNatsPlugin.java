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
 * Main plugin for Velocity.
 * Initializes the NATS library and makes it available to other plugins.
 */
@Plugin(id = "natsbridge", name = "NatsBridge", version = "1.0.0", description = "NATS messaging library for Minecraft servers", authors = {
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
            // Create configuration file if it doesn't exist
            setupConfigFile();

            // Initialize NATS library
            Path configPath = dataDirectory.resolve("nats-config.yml");
            File configFile = configPath.toFile();

            // Initialization of NatsBridge
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

            // Register commands
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

        NatsBridge.resetInstance();
        instance = null;

        logger.info("NatsBridge plugin disabled");
    }

    /**
     * Gets the Velocity plugin instance.
     *
     * @return the plugin instance
     */
    @NotNull
    public static VelocityNatsPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("VelocityNatsPlugin not initialized");
        }
        return instance;
    }

    /**
     * Gets the NATS API.
     *
     * @return the NATS API
     */
    @NotNull
    public static NatsAPI getNatsAPI() {
        return getInstance().getNatsBridge().getAPI();
    }

    /**
     * Gets the NATS library.
     *
     * @return the NATS library
     */
    @NotNull
    public NatsBridge getNatsBridge() {
        if (natsBridge == null) {
            throw new IllegalStateException("NatsBridge not initialized");
        }
        return natsBridge;
    }

    /**
     * Gets the proxy server.
     *
     * @return the proxy server
     */
    @NotNull
    public ProxyServer getServer() {
        return server;
    }

    /**
     * Gets the logger.
     *
     * @return the logger
     */
    @NotNull
    public Logger getLogger() {
        return logger;
    }

    /**
     * Gets the data directory.
     *
     * @return the data directory
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
