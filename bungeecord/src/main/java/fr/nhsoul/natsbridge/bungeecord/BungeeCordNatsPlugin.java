package fr.nhsoul.natsbridge.bungeecord;

import fr.nhsoul.natsbridge.common.api.NatsAPI;
import fr.nhsoul.natsbridge.core.NatsBridge;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;


/**
 * Main plugin for BungeeCord.
 * Initializes the NATS library and makes it available to other plugins.
 */
public class BungeeCordNatsPlugin extends Plugin {

    private static BungeeCordNatsPlugin instance;

    private NatsBridge natsBridge;

    @Override
    public void onEnable() {
        instance = this;

        try {
            // Create configuration file if it doesn't exist
            setupConfigFile();

            // Initialize NATS library
            File configFile = new File(getDataFolder(), "nats-config.yml");

            try {
                NatsBridge.initialize(configFile, new BungeeCordNatsLogger(getLogger()));
                natsBridge = NatsBridge.getInstance();
                natsBridge.start().thenRun(() -> {
                    getLogger().info("NATS library started successfully");
                    // Call the event for new plugins
                    getProxy().getPluginManager().callEvent(new BungeeNatsBridgeConnectedEvent());
                }).exceptionally(throwable -> {
                    getLogger().log(Level.SEVERE, "Failed to start NATS library", throwable);
                    return null;
                });
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Could not initialize NatsBridge", e);
            }

            // Register commands
            BungeeCordNatsCommand command = new BungeeCordNatsCommand(this, natsBridge);
            getProxy().getPluginManager().registerCommand(this, command);

            getLogger().info("NatsBridge plugin enabled successfully");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable NatsBridge plugin", e);
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        if (natsBridge != null) {
            getLogger().info("Shutting down NATS library...");
            natsBridge.shutdown();
        }

        NatsBridge.resetInstance();
        instance = null;

        getLogger().info("NatsBridge plugin disabled");
    }

    /**
     * Gets the BungeeCord plugin instance.
     *
     * @return the plugin instance
     */
    @NotNull
    public static BungeeCordNatsPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BungeeCordNatsPlugin not initialized");
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

    private void setupConfigFile() throws IOException {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        File configFile = new File(getDataFolder(), "nats-config.yml");
        if (!configFile.exists()) {
            try (InputStream defaultConfig = getResourceAsStream("nats-config.yml")) {
                if (defaultConfig != null) {
                    Files.copy(defaultConfig, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    getLogger().info("Created default NATS configuration file");
                } else {
                    getLogger().warning("Default configuration file not found in resources");
                }
            }
        }
    }
}