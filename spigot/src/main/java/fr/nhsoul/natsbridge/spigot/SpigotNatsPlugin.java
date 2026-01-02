package fr.nhsoul.natsbridge.spigot;

import fr.nhsoul.natsbridge.common.api.NatsAPI;
import fr.nhsoul.natsbridge.core.NatsBridge;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * Plugin principal pour Spigot/Paper.
 * Initialise la librairie NATS et la rend disponible aux autres plugins.
 */
public class SpigotNatsPlugin extends JavaPlugin {

    private static SpigotNatsPlugin instance;

    private NatsBridge natsBridge;

    @Override
    public void onEnable() {
        instance = this;

        try {
            // Créer le fichier de configuration s'il n'existe pas
            setupConfigFile();

            // Initialiser la librairie NATS
            File configFile = new File(getDataFolder(), "nats-config.yml");
            natsBridge = natsBridge.initialize(configFile);

            // Démarrer NATS
            natsBridge.start().thenRun(() -> {
                Bukkit.getScheduler().runTask(this, () -> {
                    getLogger().info("NATS library started successfully");

                    getServer().getPluginManager().callEvent(new SpigotNatsBridgeConnectedEvent());
                });
            }).exceptionally(throwable -> {
                getLogger().severe("Failed to start NATS library: " + throwable.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return null;
            });

            // Enregistrer les commandes
            SpigotNatsCommand command = new SpigotNatsCommand(this, natsBridge);
            getCommand("nats").setExecutor(command);
            getCommand("nats").setTabCompleter(command);

            getLogger().info("NatsBridge plugin enabled successfully");

            // Initialisation de NatsBridge
            try {
                NatsBridge.initialize(configFile, new SpigotNatsLogger(getLogger()));
                natsBridge = NatsBridge.getInstance();
                natsBridge.start().thenRun(() -> { // Re-added async start for consistency with original behavior
                    Bukkit.getScheduler().runTask(this, () -> {
                        getLogger().info("NATS library started successfully");
                        getServer().getPluginManager().callEvent(new SpigotNatsBridgeConnectedEvent());
                    });
                }).exceptionally(throwable -> {
                    getLogger().severe("Failed to start NATS library: " + throwable.getMessage());
                    getServer().getPluginManager().disablePlugin(this);
                    return null;
                });
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Could not initialize NatsBridge", e);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

        } catch (Exception e) {
            getLogger().severe("Failed to enable NatsBridge plugin: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {

        if (natsBridge != null) {
            getLogger().info("Shutting down NATS library...");
            natsBridge.shutdown();
        }

        natsBridge.resetInstance();
        instance = null;

        getLogger().info("NatsBridge plugin disabled");
    }

    /**
     * Obtient l'instance du plugin Spigot.
     *
     * @return l'instance du plugin
     */
    @NotNull
    public static SpigotNatsPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SpigotNatsPlugin not initialized");
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

    private void setupConfigFile() throws IOException {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        File configFile = new File(getDataFolder(), "nats-config.yml");
        if (!configFile.exists()) {
            try (InputStream defaultConfig = getResource("nats-config.yml")) {
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