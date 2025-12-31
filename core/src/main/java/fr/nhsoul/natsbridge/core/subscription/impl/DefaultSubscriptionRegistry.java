package fr.nhsoul.natsbridge.core.subscription.impl;

import fr.nhsoul.natsbridge.core.subscription.SubscriptionHandler;
import fr.nhsoul.natsbridge.core.subscription.SubscriptionRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Default implementation of the SubscriptionRegistry interface.
 * <p>
 * This implementation provides a thread-safe registry for managing NATS subscriptions
 * using concurrent data structures. It supports both method-based and consumer-based
 * subscriptions with proper validation and error handling.
 * </p>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li>O(1) average time complexity for registration and lookup operations</li>
 *   <li>Minimal locking overhead using ConcurrentHashMap</li>
 *   <li>Memory efficient with lazy initialization where appropriate</li>
 * </ul>
 */
public class DefaultSubscriptionRegistry implements SubscriptionRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSubscriptionRegistry.class);

    // Method-based subscriptions: subject -> SubscriptionHandler
    private final ConcurrentMap<String, SubscriptionHandler> methodSubscriptions = new ConcurrentHashMap<>();

    // Consumer-based subscriptions: subject -> Consumer<byte[]>
    private final ConcurrentMap<String, Consumer<byte[]>> consumerSubscriptions = new ConcurrentHashMap<>();

    // Async flags: subject -> Boolean
    private final ConcurrentMap<String, Boolean> asyncFlags = new ConcurrentHashMap<>();

    // Subscription counts: subject -> Integer
    private final ConcurrentMap<String, Integer> subscriptionCounts = new ConcurrentHashMap<>();

    // Track which classes have been scanned to avoid duplicate processing
    private final Set<Class<?>> scannedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerMethodSubscription(@NotNull Object pluginInstance,
                                         @NotNull Method method,
                                         @NotNull String subject,
                                         boolean async) {
        validateMethodSubscription(method);
        validateSubject(subject);

        String handlerKey = generateHandlerKey(pluginInstance, method, subject);

        // Check for duplicates
        if (methodSubscriptions.containsKey(subject) || consumerSubscriptions.containsKey(subject)) {
            throw new IllegalStateException(
                String.format("Subscription already exists for subject '%s'", subject)
            );
        }

        // Create and register the handler
        SubscriptionHandler handler = new SubscriptionHandler(pluginInstance, method, async);
        methodSubscriptions.put(subject, handler);
        asyncFlags.put(subject, async);
        subscriptionCounts.merge(subject, 1, Integer::sum);

        logger.debug("Registered method subscription for {}.{} on subject '{}'",
                    pluginInstance.getClass().getSimpleName(),
                    method.getName(),
                    subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerConsumerSubscription(@NotNull String subject,
                                           @NotNull Consumer<byte[]> consumer,
                                           boolean async) {
        validateSubject(subject);
        Objects.requireNonNull(consumer, "Consumer cannot be null");

        // Check for duplicates
        if (methodSubscriptions.containsKey(subject) || consumerSubscriptions.containsKey(subject)) {
            throw new IllegalStateException(
                String.format("Subscription already exists for subject '%s'", subject)
            );
        }

        // Register the consumer
        consumerSubscriptions.put(subject, consumer);
        asyncFlags.put(subject, async);
        subscriptionCounts.merge(subject, 1, Integer::sum);

        logger.debug("Registered consumer subscription for subject '{}'", subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unregisterSubscription(@NotNull String subject) {
        validateSubject(subject);

        boolean removed = false;
        
        // Remove from method subscriptions
        if (methodSubscriptions.remove(subject) != null) {
            removed = true;
        }
        
        // Remove from consumer subscriptions
        if (consumerSubscriptions.remove(subject) != null) {
            removed = true;
        }
        
        // Clean up async flags and counts
        asyncFlags.remove(subject);
        subscriptionCounts.remove(subject);

        if (removed) {
            logger.debug("Unregistered subscription for subject '{}'", subject);
        }

        return removed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unregisterAll() {
        int methodCount = methodSubscriptions.size();
        int consumerCount = consumerSubscriptions.size();
        
        methodSubscriptions.clear();
        consumerSubscriptions.clear();
        asyncFlags.clear();
        subscriptionCounts.clear();
        scannedClasses.clear();

        logger.info("Unregistered all subscriptions ({} method-based, {} consumer-based)",
                   methodCount, consumerCount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NotNull
    public Collection<String> getAllSubjects() {
        Set<String> allSubjects = new HashSet<>();
        allSubjects.addAll(methodSubscriptions.keySet());
        allSubjects.addAll(consumerSubscriptions.keySet());
        return Collections.unmodifiableCollection(allSubjects);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSubscriptionCount(@NotNull String subject) {
        return subscriptionCounts.getOrDefault(subject, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasSubscriptions(@NotNull String subject) {
        return methodSubscriptions.containsKey(subject) || 
               consumerSubscriptions.containsKey(subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public SubscriptionHandler getMethodHandler(@NotNull String subject) {
        return methodSubscriptions.get(subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Consumer<byte[]> getConsumer(@NotNull String subject) {
        return consumerSubscriptions.get(subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAsync(@NotNull String subject) {
        return asyncFlags.getOrDefault(subject, false);
    }

    /**
     * Marks a class as scanned to avoid duplicate processing.
     *
     * @param clazz the class that has been scanned
     * @return {@code true} if the class was not previously scanned, {@code false} otherwise
     */
    public boolean markClassAsScanned(@NotNull Class<?> clazz) {
        return scannedClasses.add(clazz);
    }

    /**
     * Checks if a class has already been scanned.
     *
     * @param clazz the class to check
     * @return {@code true} if the class has been scanned, {@code false} otherwise
     */
    public boolean isClassScanned(@NotNull Class<?> clazz) {
        return scannedClasses.contains(clazz);
    }

    /**
     * Validates a method for use as a subscription handler.
     *
     * @param method the method to validate
     * @throws IllegalArgumentException if the method is invalid
     */
    private void validateMethodSubscription(@NotNull Method method) {
        // Check if method is public
        if (!method.canAccess(null) || !java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
            throw new IllegalArgumentException(
                String.format("Method %s must be public", method.getName())
            );
        }

        // Check if method is not static
        if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException(
                String.format("Method %s cannot be static", method.getName())
            );
        }

        // Check parameter count
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            throw new IllegalArgumentException(
                String.format("Method %s must have exactly one parameter, found: %d",
                            method.getName(), parameterTypes.length)
            );
        }

        // Check parameter type
        Class<?> parameterType = parameterTypes[0];
        if (parameterType != String.class && parameterType != byte[].class) {
            throw new IllegalArgumentException(
                String.format("Method %s parameter must be String or byte[], found: %s",
                            method.getName(), parameterType.getName())
            );
        }
    }

    /**
     * Validates a subject name.
     *
     * @param subject the subject to validate
     * @throws IllegalArgumentException if the subject is invalid
     */
    private void validateSubject(@NotNull String subject) {
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }

        if (subject.contains(" ")) {
            throw new IllegalArgumentException("Subject cannot contain spaces");
        }

        if (subject.startsWith(".") || subject.endsWith(".")) {
            throw new IllegalArgumentException("Subject cannot start or end with dot");
        }

        if (subject.contains("..")) {
            throw new IllegalArgumentException("Subject cannot contain consecutive dots");
        }
    }

    /**
     * Generates a unique key for a method subscription.
     *
     * @param pluginInstance the plugin instance
     * @param method the method
     * @param subject the subject
     * @return a unique key for the subscription
     */
    @NotNull
    private String generateHandlerKey(@NotNull Object pluginInstance,
                                    @NotNull Method method,
                                    @NotNull String subject) {
        return String.format("%s.%s@%s",
                           pluginInstance.getClass().getName(),
                           method.getName(),
                           subject);
    }
}