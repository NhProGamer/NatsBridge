package fr.nhsoul.natsbridge.core.subscription.impl;

import fr.nhsoul.natsbridge.common.annotation.NatsSubscribe;
import fr.nhsoul.natsbridge.common.exception.NatsException;
import fr.nhsoul.natsbridge.core.connection.NatsConnectionManager;
import fr.nhsoul.natsbridge.core.subscription.*;
import fr.nhsoul.natsbridge.core.subscription.optimized.OptimizedSubscriptionLoader;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
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
 * Refactored SubscriptionManager that delegates responsibilities to specialized components.
 * <p>
 * This class now acts as a facade that coordinates between:
 * <ul>
 *   <li>SubscriptionRegistry - for subscription management</li>
 *   <li>SubscriptionDispatcher - for message dispatching</li>
 *   <li>SubscriptionLifecycleManager - for lifecycle management</li>
 * </ul>
 * </p>
 *
 * <h2>Design Improvements</h2>
 * <ul>
 *   <li>Single Responsibility Principle - each component has a clear responsibility</li>
 *   <li>Better Separation of Concerns - registry, dispatching, and lifecycle are separate</li>
 *   <li>Improved Testability - components can be mocked and tested independently</li>
 *   <li>Enhanced Maintainability - clearer code organization</li>
 * </ul>
 */
public class DefaultSubscriptionManager implements SubscriptionManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSubscriptionManager.class);

    // Delegated components
    private final SubscriptionRegistry subscriptionRegistry;
    private final SubscriptionDispatcher subscriptionDispatcher;
    private final SubscriptionLifecycleManager lifecycleManager;
    private final GeneratedSubscriptionsLoader generatedLoader;
    private final OptimizedSubscriptionLoader optimizedLoader;
    
    // Connection management
    private final NatsConnectionManager connectionManager;
    
    // Active NATS subscriptions (subject -> Dispatcher)
    private final Map<String, Dispatcher> activeSubscriptions = new ConcurrentHashMap<>();
    
    // Async execution
    private final ExecutorService asyncExecutor;

    /**
     * Creates a new SubscriptionManager with the specified connection manager.
     *
     * @param connectionManager the NATS connection manager
     */
    public DefaultSubscriptionManager(@NotNull NatsConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.subscriptionRegistry = new DefaultSubscriptionRegistry();
        this.subscriptionDispatcher = new DefaultSubscriptionDispatcher(this.subscriptionRegistry);
        this.lifecycleManager = new DefaultSubscriptionLifecycleManager(this.subscriptionRegistry);
        this.generatedLoader = new GeneratedSubscriptionsLoader();
        this.optimizedLoader = new OptimizedSubscriptionLoader(this.subscriptionRegistry);
        
        // Initialize async executor with bounded thread pool
        int threadCount = Math.min(4, Runtime.getRuntime().availableProcessors());
        this.asyncExecutor = Executors.newFixedThreadPool(threadCount, new NatsThreadFactory());
        
        logger.info("Initialized SubscriptionManager with {} async threads", threadCount);
    }

    // ========================================================================
    // Public API - Subscription Management
    // ========================================================================

    @Override
    public void scanAndRegister(@NotNull Object plugin) {
        Class<?> clazz = plugin.getClass();
        
        // Check if already scanned
        if (subscriptionRegistry.isClassScanned(clazz)) {
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

        // Mark class as scanned
        subscriptionRegistry.markClassAsScanned(clazz);
        
        logger.info("Registered {} NATS subscriptions for plugin {}",
                   registeredCount, clazz.getSimpleName());
    }

    @Override
    public void registerSubscription(@NotNull Object pluginInstance,
                                   @NotNull Method method,
                                   @NotNull String subject,
                                   boolean async) {
        try {
            subscriptionRegistry.registerMethodSubscription(pluginInstance, method, subject, async);
            
            // Register with lifecycle manager for automatic connection handling
            lifecycleManager.registerManagedSubscription(
                subject,
                subscriptionRegistry.getMethodHandler(subject),
                null, // No consumer for method-based subscriptions
                async
            );
            
            // Subscribe immediately if connected
            if (connectionManager.isConnected()) {
                subscribeToNats(subject);
            }

        } catch (Exception e) {
            logger.error("Failed to register method subscription for subject '{}'", subject, e);
            throw new NatsException.SubscriptionException(
                "Failed to register subscription: " + subject, e
            );
        }
    }

    @Override
    public void registerConsumerSubscription(@NotNull String subject,
                                           @NotNull Consumer<byte[]> consumer,
                                           boolean async) {
        try {
            subscriptionRegistry.registerConsumerSubscription(subject, consumer, async);
            
            // Register with lifecycle manager
            lifecycleManager.registerManagedSubscription(
                subject,
                null, // No handler for consumer-based subscriptions
                consumer,
                async
            );
            
            // Subscribe immediately if connected
            if (connectionManager.isConnected()) {
                subscribeToNats(subject);
            }

        } catch (Exception e) {
            logger.error("Failed to register consumer subscription for subject '{}'", subject, e);
            throw new NatsException.SubscriptionException(
                "Failed to register consumer subscription: " + subject, e
            );
        }
    }

    @Override
    public void loadGeneratedSubscriptions(@NotNull Map<Class<?>, Object> pluginRegistry) {
        // Try optimized loader first (JSON index from annotation processor)
        if (optimizedLoader.hasOptimizedIndex()) {
            int loadedCount = optimizedLoader.loadSubscriptions(pluginRegistry);
            if (loadedCount > 0) {
                logger.info("Using optimized subscription loading: {} subscriptions loaded", loadedCount);
                return;
            }
        }

        // Fallback to generated subscriptions loader (legacy format)
        if (generatedLoader.hasGeneratedSubscriptions()) {
            generatedLoader.loadGeneratedSubscriptions(this, pluginRegistry);
        } else {
            logger.debug("No generated subscriptions found, using runtime scanning");
        }
    }

    // ========================================================================
    // Public API - Lifecycle Management
    // ========================================================================

    @Override
    public void subscribeAll() {
        if (!connectionManager.isConnected()) {
            logger.warn("Cannot subscribe to NATS subjects: not connected");
            return;
        }

        // Delegate to lifecycle manager
        lifecycleManager.activateAllSubscriptions();
    }

    @Override
    public void unsubscribe(@NotNull String subject) {
        try {
            // Unsubscribe from NATS
            Dispatcher dispatcher = activeSubscriptions.remove(subject);
            if (dispatcher != null) {
                dispatcher.unsubscribe(subject);
            }

            // Unregister from registry
            subscriptionRegistry.unregisterSubscription(subject);

            logger.debug("Unsubscribed from subject '{}'", subject);

        } catch (Exception e) {
            logger.error("Failed to unsubscribe from subject '{}'", subject, e);
        }
    }

    @Override
    public void unsubscribeAll() {
        // Unsubscribe all active subscriptions
        for (Map.Entry<String, Dispatcher> entry : activeSubscriptions.entrySet()) {
            try {
                entry.getValue().unsubscribe(entry.getKey());
            } catch (Exception e) {
                logger.error("Failed to unsubscribe from subject '{}'", entry.getKey(), e);
            }
        }

        activeSubscriptions.clear();
        subscriptionRegistry.unregisterAll();
        
        logger.info("Unsubscribed from all NATS subjects");
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down SubscriptionManager...");
        
        // Stop dispatcher
        subscriptionDispatcher.stop();
        
        // Shutdown lifecycle manager
        lifecycleManager.shutdown();
        
        // Clean up resources
        unsubscribeAll();
        asyncExecutor.shutdown();
        
        logger.info("SubscriptionManager shutdown complete");
    }

    // ========================================================================
    // Internal Implementation - NATS Subscription Management
    // ========================================================================

    /**
     * Subscribes to a NATS subject if not already subscribed.
     *
     * @param subject the subject to subscribe to
     */
    private void subscribeToNats(@NotNull String subject) {
        if (activeSubscriptions.containsKey(subject)) {
            logger.debug("Already subscribed to subject '{}'", subject);
            return;
        }

        try {
            Connection connection = connectionManager.getConnection();
            if (connection == null) {
                throw new NatsException.SubscriptionException("NATS connection not available");
            }

            // Create dispatcher based on subscription type
            Dispatcher dispatcher = createDispatcherForSubject(subject);
            dispatcher.subscribe(subject);

            activeSubscriptions.put(subject, dispatcher);
            logger.info("Subscribed to NATS subject '{}'", subject);

        } catch (Exception e) {
            logger.error("Failed to subscribe to subject '{}'", subject, e);
            throw new NatsException.SubscriptionException(
                "Failed to subscribe to subject: " + subject, e
            );
        }
    }

    /**
     * Creates an appropriate dispatcher for a subject based on subscription type.
     *
     * @param subject the subject to create dispatcher for
     * @return the configured dispatcher
     */
    private Dispatcher createDispatcherForSubject(@NotNull String subject) {
        Connection connection = connectionManager.getConnection();
        
        if (subscriptionRegistry.getMethodHandler(subject) != null) {
            // Method-based subscription
            SubscriptionHandler handler = subscriptionRegistry.getMethodHandler(subject);
            return connection.createDispatcher(message -> 
                subscriptionDispatcher.dispatch(message)
            );
        } else if (subscriptionRegistry.getConsumer(subject) != null) {
            // Consumer-based subscription
            return connection.createDispatcher(message -> 
                subscriptionDispatcher.dispatch(message)
            );
        } else {
            throw new IllegalStateException("No handler or consumer found for subject: " + subject);
        }
    }

    // ========================================================================
    // Thread Factory for Async Processing
    // ========================================================================

    /**
     * Thread factory for creating named threads for async message processing.
     */
    private static class NatsThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread thread = new Thread(r, "nats-async-handler-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> 
                logger.error("Uncaught exception in NATS async handler thread {}", t.getName(), e)
            );
            return thread;
        }
    }
}