package fr.nhsoul.natsbridge.spigot;

import fr.nhsoul.natsbridge.common.api.NatsAPI;
import fr.nhsoul.natsbridge.core.NatsBridge;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Plugin principal pour Spigot/Paper.
 * Initialise la librairie NATS et la rend disponible aux autres plugins.
 */
public class SpigotNatsPlugin extends JavaPlugin {

    private static final Logger logger = LoggerFactory.getLogger(SpigotNatsPlugin.class);
    private static SpigotNatsPlugin instance;

    private NatsBridge natsBridge;
    private SpigotPluginScanner pluginScanner;

    @Override
    public void onEnable() {
        instance = this;

        try {
            // Créer le fichier de configuration s'il n'existe pas
            setupConfigFile();

            // Initialiser la librairie NATS
            File configFile = new File(getDataFolder(), "nats-config.yml");
            natsBridge = natsBridge.initialize(configFile);

            // Initialiser le scanner de plugins
            pluginScanner = new SpigotPluginScanner(this, natsBridge);

            // Démarrer NATS
            natsBridge.start().thenRun(() -> {
                getLogger().info("NATS library started successfully");

                // Scanner les plugins déjà chargés
                pluginScanner.scanAllPlugins();

                // Enregistrer les événements pour les nouveaux plugins
                getServer().getPluginManager().registerEvents(pluginScanner, this);
                getServer().getPluginManager().callEvent(new SpigotNatsBridgeConnectedEvent());


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

        } catch (Exception e) {
            getLogger().severe("Failed to enable NatsBridge plugin: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (pluginScanner != null) {
            pluginScanner.shutdown();
        }

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
        return getInstance().getnatsBridge().getAPI();
    }

    /**
     * Obtient la librairie NATS.
     *
     * @return la librairie NATS
     */
    @NotNull
    public NatsBridge getnatsBridge() {
        if (natsBridge == null) {
            throw new IllegalStateException("NatsBridge not initialized");
        }
        return natsBridge;
    }

    /**
     * Enregistre un plugin pour scanner ses annotations @NatsSubscribe.
     *
     * @param plugin le plugin à enregistrer
     */
    public void registerPlugin(@NotNull Object plugin) {
        if (natsBridge != null) {
            natsBridge.registerPlugin(plugin);
        }
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