package fr.nhsoul.natsbridge.bungeecord;

import fr.nhsoul.natsbridge.common.annotation.NatsSubscribe;
import fr.nhsoul.natsbridge.core.NatsBridge;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Scanner automatique des plugins BungeeCord pour détecter les méthodes annotées @NatsSubscribe.
 * Écoute les événements de chargement de plugins pour les enregistrer automatiquement.
 */
public class BungeeCordPluginScanner implements Listener {

    private final BungeeCordNatsPlugin natsPlugin;
    private final NatsBridge natsBridge;
    private final Logger logger;
    private final Set<String> processedPlugins = ConcurrentHashMap.newKeySet();

    public BungeeCordPluginScanner(@NotNull BungeeCordNatsPlugin natsPlugin, @NotNull NatsBridge natsBridge) {
        this.natsPlugin = natsPlugin;
        this.natsBridge = natsBridge;
        this.logger = natsPlugin.getLogger();
    }

    /**
     * Scanne tous les plugins actuellement chargés.
     */
    public void scanAllPlugins() {
        Plugin[] plugins = natsPlugin.getProxy().getPluginManager().getPlugins().toArray(new Plugin[0]);

        logger.info("Scanning " + plugins.length + " loaded plugins for @NatsSubscribe annotations");

        for (Plugin plugin : plugins) {
            if (!plugin.equals(natsPlugin)) {
                scanPlugin(plugin);
            }
        }
    }

    /**
     * Initialise le scanner et scanne immédiatement tous les plugins chargés.
     * Cette méthode doit être appelée tôt dans le cycle de vie du plugin NATS,
     * idéalement dans onLoad() ou très tôt dans onEnable().
     */
    public void initialize() {
        logger.info("Initializing NATS plugin scanner...");
        scanAllPlugins();
    }

    /**
     * Scanne un plugin spécifique pour les annotations @NatsSubscribe.
     *
     * @param plugin le plugin à scanner
     */
    public void scanPlugin(@NotNull Plugin plugin) {
        String pluginName = plugin.getDescription().getName();

        if (processedPlugins.contains(pluginName)) {
            logger.fine("Plugin " + pluginName + " already processed, skipping");
            return;
        }

        try {
            logger.fine("Scanning plugin: " + pluginName);

            // Scanner la classe principale du plugin
            int subscriptions = scanObject(plugin);

            if (subscriptions > 0) {
                logger.info("Found " + subscriptions + " NATS subscriptions in plugin " + pluginName);
            } else {
                logger.fine("No NATS subscriptions found in plugin " + pluginName);
            }

            processedPlugins.add(pluginName);

        } catch (Exception e) {
            logger.warning("Error scanning plugin " + pluginName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Scanne un objet pour les méthodes annotées @NatsSubscribe.
     *
     * @param object l'objet à scanner
     * @return le nombre de souscriptions trouvées
     */
    public int scanObject(@NotNull Object object) {
        Class<?> clazz = object.getClass();
        Method[] methods = clazz.getDeclaredMethods();
        int count = 0;
        boolean objectRegistered = false;

        for (Method method : methods) {
            if (method.isAnnotationPresent(NatsSubscribe.class)) {
                try {
                    // Enregistrer l'objet une seule fois, pas pour chaque méthode
                    if (!objectRegistered) {
                        natsBridge.registerPlugin(object);
                        objectRegistered = true;
                    }
                    count++;

                    NatsSubscribe annotation = method.getAnnotation(NatsSubscribe.class);
                    logger.fine("Registered NATS subscription: " + clazz.getSimpleName() + "." +
                            method.getName() + " -> '" + annotation.value() + "'");

                } catch (Exception e) {
                    logger.warning("Failed to register NATS subscription for " + clazz.getSimpleName() +
                            "." + method.getName() + ": " + e.getMessage());
                }
            }
        }

        return count;
    }

    /**
     * Enregistre manuellement un objet pour scanner ses annotations.
     * Utile pour les développeurs qui veulent explicitement enregistrer leurs listeners.
     *
     * @param object l'objet à enregistrer
     */
    public void registerObject(@NotNull Object object) {
        try {
            int count = scanObject(object);
            if (count > 0) {
                logger.info("Manually registered " + count + " NATS subscriptions from " +
                        object.getClass().getSimpleName());
            }
        } catch (Exception e) {
            logger.warning("Failed to register object " + object.getClass().getSimpleName() +
                    ": " + e.getMessage());
        }
    }

    /**
     * Arrête le scanner.
     */
    public void shutdown() {
        processedPlugins.clear();
        logger.fine("Plugin scanner shut down");
    }
}