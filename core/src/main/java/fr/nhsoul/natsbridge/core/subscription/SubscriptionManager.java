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
import java.util.function.Consumer;

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
    private final Map<String, Consumer<byte[]>> consumerSubscriptions = new ConcurrentHashMap<>();
    private final GeneratedSubscriptionsLoader generatedLoader = new GeneratedSubscriptionsLoader();
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
                    registerSubscription(plugin, method, annotation.value(), annotation.async());
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
     * Charge les subscriptions générées par l'annotation processor.
     *
     * @param pluginRegistry un registre des instances de plugins disponibles
     */
    public void loadGeneratedSubscriptions(@NotNull Map<Class<?>, Object> pluginRegistry) {
        if (generatedLoader.hasGeneratedSubscriptions()) {
            generatedLoader.loadGeneratedSubscriptions(this, pluginRegistry);
        } else {
            logger.debug("No generated subscriptions found, using runtime scanning");
        }
    }

    /**
     * Enregistre manuellement une souscription pour une méthode spécifique.
     *
     * @param plugin     l'instance contenant la méthode
     * @param method     la méthode à appeler
     * @param subject
     * @param async
     */
    public void registerSubscription(@NotNull Object plugin, @NotNull Method method, @NotNull String subject, boolean async) {

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
     * Enregistre une souscription bas niveau avec un Consumer.
     * Plus performant que l'approche par annotation car évite la réflexion.
     *
     * @param subject le sujet NATS
     * @param consumer le Consumer qui traitera les messages
     * @param async true pour un traitement asynchrone
     */
    public void registerConsumerSubscription(@NotNull String subject, @NotNull Consumer<byte[]> consumer, boolean async) {
        if (consumerSubscriptions.containsKey(subject)) {
            logger.warn("Consumer subscription already exists for subject '{}'", subject);
            return;
        }

        consumerSubscriptions.put(subject, consumer);
        subscriptionCounts.merge(subject, 1, Integer::sum);

        if (connectionManager.isConnected()) {
            subscribeConsumerToNats(subject, consumer, async);
        } else {
            logger.debug("NATS not connected yet, consumer subscription for '{}' will be registered on connection", subject);
        }

        logger.debug("Registered consumer subscription for subject '{}' (total: {})", subject, subscriptionCounts.get(subject));
    }

    /**
     * S'abonne à tous les handlers enregistrés (appelé lors de la connexion).
     */
    public void subscribeAll() {
        if (!connectionManager.isConnected()) {
            logger.warn("Cannot subscribe to NATS subjects: not connected");
            return;
        }

        // Subscribe method-based handlers
        for (Map.Entry<String, SubscriptionHandler> entry : handlers.entrySet()) {
            String handlerKey = entry.getKey();
            SubscriptionHandler handler = entry.getValue();
            String subject = extractSubjectFromHandlerKey(handlerKey);

            if (!subscriptions.containsKey(subject)) {
                subscribeToNats(subject, handler);
            }
        }

        // Subscribe consumer-based handlers
        for (Map.Entry<String, Consumer<byte[]>> entry : consumerSubscriptions.entrySet()) {
            String subject = entry.getKey();
            Consumer<byte[]> consumer = entry.getValue();

            if (!subscriptions.containsKey(subject)) {
                // Determine async status - for now we'll assume sync for consumers
                // In a real implementation, we might need to track this separately
                subscribeConsumerToNats(subject, consumer, false);
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
        handlers.clear();
        consumerSubscriptions.clear();
        subscriptionCounts.clear();
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
        
        // Nettoyer les handlers et consumers associés
        handlers.keySet().removeIf(key -> extractSubjectFromHandlerKey(key).equals(subject));
        consumerSubscriptions.remove(subject);
        subscriptionCounts.remove(subject);
    }

    /**
     * Ferme le gestionnaire de souscriptions.
     */
    public void shutdown() {
        unsubscribeAll();
        asyncExecutor.shutdown();
        scannedClasses.clear();
        // handlers, consumerSubscriptions et subscriptionCounts sont déjà nettoyés dans unsubscribeAll()
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

    private void subscribeConsumerToNats(@NotNull String subject, @NotNull Consumer<byte[]> consumer, boolean async) {
        Connection connection = connectionManager.getConnection();
        if (connection == null) {
            throw new NatsException.SubscriptionException("NATS connection not available");
        }

        try {
            Dispatcher dispatcher = connection.createDispatcher(message -> handleConsumerMessage(message, consumer, async));
            dispatcher.subscribe(subject);

            subscriptions.put(subject, dispatcher);
            logger.info("Subscribed consumer to NATS subject '{}'", subject);

        } catch (Exception e) {
            logger.error("Failed to subscribe consumer to subject '{}'", subject, e);
            throw new NatsException.SubscriptionException("Failed to subscribe consumer to subject: " + subject, e);
        }
    }


    private void handleMessage(@NotNull Message message, @NotNull SubscriptionHandler handler) {
        if (handler.isAsync()) {
            asyncExecutor.submit(() -> processMessage(message, handler));
        } else {
            processMessage(message, handler);
        }
    }

    private void handleConsumerMessage(@NotNull Message message, @NotNull Consumer<byte[]> consumer, boolean async) {
        byte[] data = message.getData();
        
        if (async) {
            asyncExecutor.submit(() -> safeConsume(consumer, data));
        } else {
            safeConsume(consumer, data);
        }
    }

    private void safeConsume(@NotNull Consumer<byte[]> consumer, byte[] data) {
        try {
            consumer.accept(data);
            
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully processed message with consumer ({} bytes)", 
                    data != null ? data.length : 0);
            }
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error("Error processing message with consumer", e);
            }
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