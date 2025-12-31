package fr.nhsoul.natsbridge.core.subscription;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Chargeur des subscriptions générées par l'annotation processor.
 * Lit le fichier généré à la compilation et crée les handlers correspondants.
 */
public class GeneratedSubscriptionsLoader {

    private static final Logger logger = LoggerFactory.getLogger(GeneratedSubscriptionsLoader.class);
    private static final String SUBSCRIPTIONS_FILE = "META-INF/natsbridge/subscriptions.list";

    /**
     * Charge les subscriptions générées et les enregistre via le SubscriptionManager.
     *
     * @param subscriptionManager le gestionnaire de subscriptions
     * @param pluginRegistry un registre des instances de plugins disponibles
     */
    public void loadGeneratedSubscriptions(@NotNull SubscriptionManager subscriptionManager,
                                          @NotNull Map<Class<?>, Object> pluginRegistry) {
        
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(SUBSCRIPTIONS_FILE);
        
        if (inputStream == null) {
            logger.debug("No generated subscriptions file found at {}", SUBSCRIPTIONS_FILE);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            int loadedCount = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    String[] parts = line.split("\\|", 4);
                    if (parts.length != 4) {
                        logger.warn("Invalid subscription line format: {}", line);
                        continue;
                    }

                    String className = parts[0];
                    String methodName = parts[1];
                    String subject = parts[2];
                    boolean async = Boolean.parseBoolean(parts[3]);

                    // Trouver l'instance du plugin
                    Class<?> clazz = Class.forName(className);
                    Object pluginInstance = pluginRegistry.get(clazz);

                    if (pluginInstance == null) {
                        logger.warn("No plugin instance found for class: {}", className);
                        continue;
                    }

                    // Trouver la méthode
                    Method method = findMethod(clazz, methodName);
                    if (method == null) {
                        logger.warn("Method {} not found in class {}", methodName, className);
                        continue;
                    }

                    // Enregistrer la subscription
                    subscriptionManager.registerSubscription(pluginInstance, method, subject, async);
                    loadedCount++;

                } catch (Exception e) {
                    logger.error("Failed to process subscription line: {}", line, e);
                }
            }

            logger.info("Loaded {} generated subscriptions", loadedCount);

        } catch (IOException e) {
            logger.error("Failed to read generated subscriptions file", e);
        }
    }

    /**
     * Trouve une méthode dans une classe en tenant compte de l'héritage.
     */
    private Method findMethod(Class<?> clazz, String methodName) {
        try {
            return clazz.getMethod(methodName, String.class);
        } catch (NoSuchMethodException e) {
            try {
                return clazz.getMethod(methodName, byte[].class);
            } catch (NoSuchMethodException ex) {
                logger.debug("Method {} not found with expected signatures in class {}", 
                    methodName, clazz.getName());
                return null;
            }
        }
    }

    /**
     * Vérifie si le fichier de subscriptions générées existe.
     *
     * @return true si le fichier existe, false sinon
     */
    public boolean hasGeneratedSubscriptions() {
        return getClass().getClassLoader().getResource(SUBSCRIPTIONS_FILE) != null;
    }
}