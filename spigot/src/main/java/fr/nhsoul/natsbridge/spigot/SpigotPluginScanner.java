package fr.nhsoul.natsbridge.spigot;

import fr.nhsoul.natsbridge.common.annotation.NatsSubscribe;
import fr.nhsoul.natsbridge.core.NatsBridge;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scanner automatique des plugins Spigot pour détecter les méthodes annotées @NatsSubscribe.
 * Écoute les événements de chargement de plugins pour les enregistrer automatiquement.
 */
public class SpigotPluginScanner implements Listener {

    private static final Logger logger = LoggerFactory.getLogger(SpigotPluginScanner.class);

    private final SpigotNatsPlugin natsPlugin;
    private final NatsBridge natsBridge;
    private final Set<String> processedPlugins = ConcurrentHashMap.newKeySet();

    public SpigotPluginScanner(@NotNull SpigotNatsPlugin natsPlugin, @NotNull NatsBridge natsBridge) {
        this.natsPlugin = natsPlugin;
        this.natsBridge = natsBridge;
    }

    /**
     * Scanne tous les plugins actuellement chargés.
     */
    public void scanAllPlugins() {
        //logger.info("Scanning {} loaded plugins for @NatsSubscribe annotations", plugins.length);

        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            for (String dep: plugin.getDescription().getDepend()) {
                if (dep.equalsIgnoreCase("natsbridge")) {
                    scanPlugin(plugin);
                }
            }
            for (String dep: plugin.getDescription().getSoftDepend()) {
                if (dep.equalsIgnoreCase("natsbridge")) {
                    scanPlugin(plugin);
                }
            }
        }
    }

    /**
     * Événement déclenché quand un plugin est activé.
     * Scanne automatiquement le plugin pour les annotations NATS.
     */
    @EventHandler
    public void onPluginEnable(@NotNull PluginEnableEvent event) {
        Plugin plugin = event.getPlugin();

        // Ne pas scanner notre propre plugin
        if (plugin.equals(natsPlugin)) {
            return;
        }

        // Attendre un peu que le plugin soit complètement initialisé
        Bukkit.getScheduler().runTaskLater(natsPlugin, () -> scanPlugin(plugin), 1L);
    }

    /**
     * Scanne un plugin spécifique pour les annotations @NatsSubscribe.
     *
     * @param plugin le plugin à scanner
     */
    public void scanPlugin(@NotNull Plugin plugin) {
        String pluginName = plugin.getName();

        if (processedPlugins.contains(pluginName)) {
            logger.debug("Plugin {} already processed, skipping", pluginName);
            return;
        }

        try {
            logger.debug("Scanning plugin: {}", pluginName);

            // Scanner la classe principale du plugin
            int subscriptions = scanObject(plugin);

            // Scanner les listeners enregistrés du plugin (si possible)
            subscriptions += scanPluginListeners(plugin);

            if (subscriptions > 0) {
                logger.info("Found {} NATS subscriptions in plugin {}", subscriptions, pluginName);
            } else {
                logger.debug("No NATS subscriptions found in plugin {}", pluginName);
            }

            processedPlugins.add(pluginName);

        } catch (Exception e) {
            logger.error("Error scanning plugin {}", pluginName, e);
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

        for (Method method : methods) {
            if (method.isAnnotationPresent(NatsSubscribe.class)) {
                try {
                    natsBridge.registerPlugin(object);
                    count++;

                    NatsSubscribe annotation = method.getAnnotation(NatsSubscribe.class);
                    logger.debug("Registered NATS subscription: {}.{} -> '{}'",
                            clazz.getSimpleName(), method.getName(), annotation.value());

                } catch (Exception e) {
                    logger.error("Failed to register NATS subscription for {}.{}",
                            clazz.getSimpleName(), method.getName(), e);
                }
            }
        }

        return count;
    }

    /**
     * Tente de scanner les listeners d'un plugin (approche heuristique).
     *
     * @param plugin le plugin
     * @return le nombre de souscriptions trouvées
     */
    private int scanPluginListeners(@NotNull Plugin plugin) {
        int count = 0;

        try {
            // Cette approche est limitée car Bukkit ne fournit pas d'API
            // pour lister les listeners d'un plugin spécifique.
            // Les développeurs devront enregistrer manuellement leurs objets
            // ou utiliser la méthode scanObject() explicitement.

            logger.debug("Listener scanning for plugin {} completed (limited scope)", plugin.getName());

        } catch (Exception e) {
            logger.debug("Could not scan listeners for plugin {}: {}", plugin.getName(), e.getMessage());
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
                logger.info("Manually registered {} NATS subscriptions from {}",
                        count, object.getClass().getSimpleName());
            }
        } catch (Exception e) {
            logger.error("Failed to register object {}", object.getClass().getSimpleName(), e);
        }
    }

    /**
     * Arrête le scanner.
     */
    public void shutdown() {
        processedPlugins.clear();
        logger.debug("Plugin scanner shut down");
    }
}
