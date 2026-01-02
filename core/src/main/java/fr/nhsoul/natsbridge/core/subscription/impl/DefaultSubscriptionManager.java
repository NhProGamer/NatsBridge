package fr.nhsoul.natsbridge.core.subscription.impl;

import fr.nhsoul.natsbridge.common.annotation.NatsSubscribe;
import fr.nhsoul.natsbridge.core.connection.NatsConnectionManager;
import fr.nhsoul.natsbridge.core.subscription.GeneratedSubscriptionsLoader;
import fr.nhsoul.natsbridge.core.subscription.SubscriptionManager;
import fr.nhsoul.natsbridge.core.subscription.optimized.OptimizedSubscriptionLoader;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Implementation simplifiée de SubscriptionManager utilisant directement jnats.
 * Optimisée avec MethodHandles pour l'invocation des handlers.
 */
public class DefaultSubscriptionManager implements SubscriptionManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSubscriptionManager.class);

    private final NatsConnectionManager connectionManager;
    private final GeneratedSubscriptionsLoader generatedLoader;
    private final OptimizedSubscriptionLoader optimizedLoader;

    // Maintain a list of registered subscription definitions to re-apply on
    // connect/reconnect
    private final List<SubscriptionDefinition> registeredSubscriptions = new CopyOnWriteArrayList<>();

    // Active dispatcher (recreated on connection)
    private volatile Dispatcher dispatcher;

    // Track processed classes
    private final Collection<Class<?>> scannedClasses = ConcurrentHashMap.newKeySet();

    // Lookup for MethodHandles
    private final MethodHandles.Lookup lookup = MethodHandles.lookup();

    public DefaultSubscriptionManager(@NotNull NatsConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.generatedLoader = new GeneratedSubscriptionsLoader();
        this.optimizedLoader = new OptimizedSubscriptionLoader(this);
    }

    @Override
    public void scanAndRegister(@NotNull Object plugin) {
        Class<?> clazz = plugin.getClass();

        if (!scannedClasses.add(clazz)) {
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

        logger.info("Registered {} NATS subscriptions for plugin {}",
                registeredCount, clazz.getSimpleName());
    }

    @Override
    public void registerSubscription(@NotNull Object pluginInstance,
            @NotNull Method method,
            @NotNull String subject,
            boolean async) {

        try {
            method.setAccessible(true);
            MethodHandle handle = lookup.unreflect(method).bindTo(pluginInstance);
            Class<?>[] paramTypes = method.getParameterTypes();
            boolean isStringParam = paramTypes.length == 1 && paramTypes[0] == String.class;

            SubscriptionDefinition def = new SubscriptionDefinition(subject, msg -> {
                try {
                    if (isStringParam) {
                        handle.invoke(new String(msg.getData(), StandardCharsets.UTF_8));
                    } else {
                        handle.invoke(msg.getData());
                    }
                } catch (Throwable e) {
                    logger.error("Error processing NATS message for subject {}", subject, e);
                }
            });

            addSubscription(def);

        } catch (IllegalAccessException e) {
            logger.error("Failed to access method for subscription: {}", method.getName(), e);
        }
    }

    @Override
    public void registerConsumerSubscription(@NotNull String subject,
            @NotNull Consumer<byte[]> consumer,
            boolean async) {
        SubscriptionDefinition def = new SubscriptionDefinition(subject, msg -> {
            try {
                consumer.accept(msg.getData());
            } catch (Exception e) {
                logger.error("Error processing NATS message for subject {}", subject, e);
            }
        });

        addSubscription(def);
    }

    @Override
    public void registerStringSubject(@NotNull String subject,
            @NotNull Consumer<String> consumer,
            boolean async) {
        registerConsumerSubscription(subject,
                bytes -> consumer.accept(new String(bytes, StandardCharsets.UTF_8)),
                async);
    }

    private void addSubscription(SubscriptionDefinition def) {
        registeredSubscriptions.add(def);
        // If already connected and dispatcher exists, subscribe immediately
        if (dispatcher != null && dispatcher.isActive()) {
            dispatcher.subscribe(def.subject, def.handler);
        }
    }

    @Override
    public void loadGeneratedSubscriptions(@NotNull Map<Class<?>, Object> pluginRegistry) {
        if (optimizedLoader.hasOptimizedIndex()) {
            optimizedLoader.loadSubscriptions(pluginRegistry);
        } else if (generatedLoader.hasGeneratedSubscriptions()) {
            generatedLoader.loadGeneratedSubscriptions(this, pluginRegistry);
        }
    }

    @Override
    public void subscribeAll() {
        Connection conn = connectionManager.getConnection();
        if (conn == null || conn.getStatus() != Connection.Status.CONNECTED) {
            logger.warn("Cannot subscribe: NATS not connected");
            return;
        }

        // Create a new dispatcher
        this.dispatcher = conn.createDispatcher(msg -> {
        });

        for (SubscriptionDefinition def : registeredSubscriptions) {
            try {
                this.dispatcher.subscribe(def.subject, def.handler);
                logger.debug("Subscribed to {}", def.subject);
            } catch (Exception e) {
                logger.error("Failed to subscribe to {}", def.subject, e);
            }
        }
        logger.info("Activated {} subscriptions", registeredSubscriptions.size());
    }

    @Override
    public void unsubscribe(@NotNull String subject) {
        if (dispatcher != null && dispatcher.isActive()) {
            dispatcher.unsubscribe(subject);
        }
        registeredSubscriptions.removeIf(def -> def.subject.equals(subject));
    }

    @Override
    public void unsubscribeAll() {
        if (dispatcher != null && dispatcher.isActive()) {
            for (SubscriptionDefinition def : registeredSubscriptions) {
                dispatcher.unsubscribe(def.subject);
            }
        }
        registeredSubscriptions.clear();
        scannedClasses.clear();
    }

    @Override
    public void shutdown() {
        unsubscribeAll();
    }

    private static class SubscriptionDefinition {
        final String subject;
        final io.nats.client.MessageHandler handler;

        SubscriptionDefinition(String subject, io.nats.client.MessageHandler handler) {
            this.subject = subject;
            this.handler = handler;
        }
    }
}