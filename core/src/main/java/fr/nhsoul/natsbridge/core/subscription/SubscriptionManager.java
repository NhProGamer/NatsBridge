package fr.nhsoul.natsbridge.core.subscription;

import fr.nhsoul.natsbridge.common.annotation.NatsSubscribe;
import fr.nhsoul.natsbridge.common.exception.NatsException;
import fr.nhsoul.natsbridge.core.connection.NatsConnectionManager;
import io.nats.client.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gestionnaire des souscriptions NATS.
 * Scanne les plugins pour trouver les méthodes annotées @NatsSubscribe
 * et gère leur souscription automatique.
 */
public class SubscriptionManager {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

    private final NatsConnectionManager connectionManager;
    private final Map<String, Dispatcher> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, SubscriptionHandler> handlers = new ConcurrentHashMap<>();
    private final Map<Class<?>, Boolean> scannedClasses = new ConcurrentHashMap<>();
    private final Map<String, Integer> subscriptionCounts = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor;

    public SubscriptionManager(@NotNull NatsConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        // Utiliser un pool fixe au lieu d'un cache illimité pour éviter la création excessive de threads
        int threadCount = Math.min(4, Runtime.getRuntime().availableProcessors());
        this.asyncExecutor = Executors.newFixedThreadPool(threadCount, new NatsThreadFactory());
        logger.debug("Initialized async executor with {} threads", threadCount);
    }

    /**
     * Scanne un objet pour trouver les méthodes annotées @NatsSubscribe
     * et les enregistre automatiquement.
     *
     * @param plugin l'objet à scanner
     */
    public void scanAndRegister(@NotNull Object plugin) {
        Class<?> clazz = plugin.getClass();
        
        // Éviter de scanner plusieurs fois la même classe
        if (scannedClasses.containsKey(clazz)) {
            logger.debug("Class {} already scanned, skipping", clazz.getSimpleName());
            return;
        }
        
        logger.debug("Scanning plugin class {} for @NatsSubscribe annotations", clazz.getSimpleName());

        Method[] methods = clazz.getDeclaredMethods();
        int registeredCount = 0;

        for (Method method : methods) {
            NatsSubscribe annotation = method.getAnnotation(NatsSubscribe.class);
            if (annotation != null) {
                try {
                    registerSubscription(plugin, method, annotation);
                    registeredCount++;
                } catch (Exception e) {
                    logger.error("Failed to register subscription for method {}.{}",
                            clazz.getSimpleName(), method.getName(), e);
                }
            }
        }

        // Marquer la classe comme scannée
        scannedClasses.put(clazz, true);
        
        logger.info("Registered {} NATS subscriptions for plugin {}",
                registeredCount, clazz.getSimpleName());
    }

    /**
     * Enregistre manuellement une souscription pour une méthode spécifique.
     *
     * @param plugin     l'instance contenant la méthode
     * @param method     la méthode à appeler
     * @param annotation l'annotation contenant la configuration
     */
    public void registerSubscription(@NotNull Object plugin, @NotNull Method method, @NotNull NatsSubscribe annotation) {
        String subject = annotation.value();
        boolean async = annotation.async();

        validateSubscriptionMethod(method);

        String handlerKey = generateHandlerKey(plugin, method, subject);

        if (handlers.containsKey(handlerKey)) {
            logger.warn("Subscription already exists for {}.{} on subject '{}'",
                    plugin.getClass().getSimpleName(), method.getName(), subject);
            return;
        }

        SubscriptionHandler handler = new SubscriptionHandler(plugin, method, async);
        handlers.put(handlerKey, handler);

        // Compter les souscriptions par sujet
        subscriptionCounts.merge(subject, 1, Integer::sum);

        // S'abonner immédiatement si connecté, sinon attendre la connexion
        if (connectionManager.isConnected()) {
            subscribeToNats(subject, handler);
        } else {
            logger.debug("NATS not connected yet, subscription for '{}' will be registered on connection", subject);
        }

        logger.debug("Registered subscription handler for {}.{} on subject '{}' (total: {})",
                plugin.getClass().getSimpleName(), method.getName(), subject, subscriptionCounts.get(subject));
    }

    /**
     * S'abonne à tous les handlers enregistrés (appelé lors de la connexion).
     */
    public void subscribeAll() {
        if (!connectionManager.isConnected()) {
            logger.warn("Cannot subscribe to NATS subjects: not connected");
            return;
        }

        for (Map.Entry<String, SubscriptionHandler> entry : handlers.entrySet()) {
            String handlerKey = entry.getKey();
            SubscriptionHandler handler = entry.getValue();
            String subject = extractSubjectFromHandlerKey(handlerKey);

            if (!subscriptions.containsKey(subject)) {
                subscribeToNats(subject, handler);
            }
        }
    }

    /**
     * Désabonne toutes les souscriptions.
     */
    public void unsubscribeAll() {
        for (Map.Entry<String, Dispatcher> entry : subscriptions.entrySet()) {
            String subject = entry.getKey();
            Dispatcher dispatcher = entry.getValue();

            try {
                dispatcher.unsubscribe(subject);
                logger.debug("Unsubscribed from subject '{}'", subject);
            } catch (Exception e) {
                logger.error("Failed to unsubscribe from subject '{}'", subject, e);
            }
        }

        subscriptions.clear();
        logger.info("Unsubscribed from all NATS subjects");
    }

    /**
     * Désabonne une souscription spécifique.
     *
     * @param subject le sujet à désabonner
     */
    public void unsubscribe(@NotNull String subject) {
        Dispatcher dispatcher = subscriptions.remove(subject);
        if (dispatcher != null) {
            try {
                dispatcher.unsubscribe(subject);
                logger.debug("Unsubscribed from subject '{}'", subject);
            } catch (Exception e) {
                logger.error("Failed to unsubscribe from subject '{}'", subject, e);
            }
        }
    }

    /**
     * Ferme le gestionnaire de souscriptions.
     */
    public void shutdown() {
        unsubscribeAll();
        asyncExecutor.shutdown();
        handlers.clear();
        scannedClasses.clear();
        subscriptionCounts.clear();
    }

    private void subscribeToNats(@NotNull String subject, @NotNull SubscriptionHandler handler) {
        Connection connection = connectionManager.getConnection();
        if (connection == null) {
            throw new NatsException.SubscriptionException("NATS connection not available");
        }

        try {
            Dispatcher dispatcher = connection.createDispatcher(message -> handleMessage(message, handler));
            dispatcher.subscribe(subject);

            subscriptions.put(subject, dispatcher); // Map<String, Object> ou spécifique au type
            logger.info("Subscribed to NATS subject '{}'", subject);

        } catch (Exception e) {
            logger.error("Failed to subscribe to subject '{}'", subject, e);
            throw new NatsException.SubscriptionException("Failed to subscribe to subject: " + subject, e);
        }
    }


    private void handleMessage(@NotNull Message message, @NotNull SubscriptionHandler handler) {
        if (handler.isAsync()) {
            asyncExecutor.submit(() -> processMessage(message, handler));
        } else {
            processMessage(message, handler);
        }
    }

    private void processMessage(@NotNull Message message, @NotNull SubscriptionHandler handler) {
        try {
            Object data = convertMessageData(message, handler.getMethod());
            handler.invoke(data);

            // Optimisation : vérifier le niveau de log avant de formater le message
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully processed message on subject '{}'", message.getSubject());
            }

        } catch (Exception e) {
            // Optimisation : vérifier le niveau de log avant de formater le message
            if (logger.isErrorEnabled()) {
                logger.error("Error processing message on subject '{}' with handler {}.{}",
                        message.getSubject(),
                        handler.getPlugin().getClass().getSimpleName(),
                        handler.getMethod().getName(), e);
            }
        }
    }

    private Object convertMessageData(@NotNull Message message, @NotNull Method method) {
        Class<?> parameterType = method.getParameterTypes()[0];
        byte[] data = message.getData();

        if (parameterType == byte[].class) {
            return data;
        } else if (parameterType == String.class) {
            return data != null ? new String(data, StandardCharsets.UTF_8) : null;
        } else {
            throw new IllegalArgumentException("Unsupported parameter type: " + parameterType.getName() +
                    ". Only byte[] and String are supported.");
        }
    }

    private void validateSubscriptionMethod(@NotNull Method method) {
        // Vérifier que la méthode est publique
        if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
            throw new IllegalArgumentException("Method " + method.getName() + " must be public");
        }

        // Vérifier qu'elle n'est pas statique
        if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Method " + method.getName() + " cannot be static");
        }

        // Vérifier qu'elle a exactement un paramètre
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            throw new IllegalArgumentException("Method " + method.getName() +
                    " must have exactly one parameter, found: " + parameterTypes.length);
        }

        // Vérifier que le type de paramètre est supporté
        Class<?> parameterType = parameterTypes[0];
        if (parameterType != String.class && parameterType != byte[].class) {
            throw new IllegalArgumentException("Method " + method.getName() +
                    " parameter must be String or byte[], found: " + parameterType.getName());
        }
    }

    private String generateHandlerKey(@NotNull Object plugin, @NotNull Method method, @NotNull String subject) {
        return plugin.getClass().getName() + "." + method.getName() + "@" + subject;
    }

    private String extractSubjectFromHandlerKey(@NotNull String handlerKey) {
        int atIndex = handlerKey.lastIndexOf('@');
        return handlerKey.substring(atIndex + 1);
    }

    /**
     * Factory pour créer des threads nommés pour les tâches asynchrones.
     */
    private static class NatsThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread thread = new Thread(r, "nats-async-handler-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}