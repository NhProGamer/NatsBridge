package fr.nhsoul.natsbridge.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.meta.PluginDependency;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.nhsoul.natsbridge.common.annotation.NatsSubscribe;
import fr.nhsoul.natsbridge.core.NatsBridge;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scanner automatique des plugins Velocity pour détecter les méthodes annotées @NatsSubscribe.
 * Écoute les événements de chargement de plugins pour les enregistrer automatiquement.
 */
public class VelocityPluginScanner {

    private static final Logger logger = LoggerFactory.getLogger(VelocityPluginScanner.class);

    private final ProxyServer server;
    private final NatsBridge natsBridge;
    private final Set<String> processedPlugins = ConcurrentHashMap.newKeySet();

    public VelocityPluginScanner(@NotNull ProxyServer server, @NotNull NatsBridge natsBridge) {
        this.server = server;
        this.natsBridge = natsBridge;
    }

    /**
     * Scanne tous les plugins actuellement chargés.
     */
    public void scanAllPlugins() {
        for (PluginContainer plugin : server.getPluginManager().getPlugins()) {
            Collection<PluginDependency> dependencies = plugin.getDescription().getDependencies();
            for (PluginDependency dep : dependencies) {
                if (dep.getId().equalsIgnoreCase("natsbridge")) {
                    logger.info("[NatsBridge] Detected dependent Velocity plugin: {}", plugin.getDescription().getId());
                    if (plugin.getInstance().isPresent() && !isnatsBridgePlugin(plugin)) {
                        scanPlugin(plugin);
                    }
                    break;
                }
            }
        }
        /*logger.info("Scanning {} loaded plugins for @NatsSubscribe annotations",
                server.getPluginManager().getPlugins().size());*/
    }

    /**
     * Événement déclenché lors de l'initialisation du proxy.
     * Peut être utilisé pour scanner les plugins chargés après nous.
     */
    @Subscribe
    public void onProxyInitialize(@NotNull ProxyInitializeEvent event) {
        // Les plugins sont déjà chargés à ce moment, le scan principal
        // est fait dans la méthode scanAllPlugins() appelée depuis le plugin principal
    }

    /**
     * Scanne un plugin spécifique pour les annotations @NatsSubscribe.
     *
     * @param pluginContainer le conteneur du plugin à scanner
     */
    public void scanPlugin(@NotNull PluginContainer pluginContainer) {
        String pluginId = pluginContainer.getDescription().getId();

        if (processedPlugins.contains(pluginId)) {
            logger.debug("Plugin {} already processed, skipping", pluginId);
            return;
        }

        if (!pluginContainer.getInstance().isPresent()) {
            logger.debug("Plugin {} has no instance, skipping", pluginId);
            return;
        }

        try {
            Object pluginInstance = pluginContainer.getInstance().get();
            logger.debug("Scanning plugin: {}", pluginId);

            int subscriptions = scanObject(pluginInstance);

            if (subscriptions > 0) {
                logger.info("Found {} NATS subscriptions in plugin {}", subscriptions, pluginId);
            } else {
                logger.debug("No NATS subscriptions found in plugin {}", pluginId);
            }

            processedPlugins.add(pluginId);

        } catch (Exception e) {
            logger.error("Error scanning plugin {}", pluginId, e);
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
     * Enregistre manuellement un objet pour scanner ses annotations.
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
     * Vérifie si un plugin est le plugin NatsBridge lui-même.
     *
     * @param plugin le plugin à vérifier
     * @return true si c'est le plugin NatsBridge
     */
    private boolean isnatsBridgePlugin(@NotNull PluginContainer plugin) {
        return "natsbridge".equals(plugin.getDescription().getId());
    }

    /**
     * Arrête le scanner.
     */
    public void shutdown() {
        processedPlugins.clear();
        logger.debug("Plugin scanner shut down");
    }
}
