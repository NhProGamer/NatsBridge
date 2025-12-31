package fr.nhsoul.natsbridge.core.subscription.impl;

import fr.nhsoul.natsbridge.core.subscription.SubscriptionHandler;
import fr.nhsoul.natsbridge.core.subscription.SubscriptionLifecycleManager;
import fr.nhsoul.natsbridge.core.subscription.SubscriptionRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Default implementation of the SubscriptionLifecycleManager interface.
 * <p>
 * This manager handles the lifecycle of NATS subscriptions, including automatic
 * reconnection, connection state management, and resource cleanup. It provides
 * a robust foundation for managing subscriptions in dynamic environments.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Automatic subscription activation on connection</li>
 *   <li>Graceful handling of connection state changes</li>
 *   <li>Resource cleanup and shutdown management</li>
 *   <li>Connection state monitoring and notifications</li>
 * </ul>
 */
public class DefaultSubscriptionLifecycleManager implements SubscriptionLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSubscriptionLifecycleManager.class);

    // Current connection state
    private volatile ConnectionState currentState = ConnectionState.DISCONNECTED;

    // Shutdown flag
    private volatile boolean shutdown = false;

    // Subscription registry dependency
    private final SubscriptionRegistry subscriptionRegistry;

    // Managed subscriptions (subject -> subscription info)
    private final Map<String, ManagedSubscription> managedSubscriptions = new ConcurrentHashMap<>();

    // Connection state listeners
    private final List<ConnectionStateListener> stateListeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a new SubscriptionLifecycleManager with the specified registry.
     *
     * @param subscriptionRegistry the subscription registry to use
     */
    public DefaultSubscriptionLifecycleManager(@NotNull SubscriptionRegistry subscriptionRegistry) {
        this.subscriptionRegistry = subscriptionRegistry;
        logger.info("Initialized SubscriptionLifecycleManager");
    }

    // ========================================================================
    // Public API - Connection State Management
    // ========================================================================

    @Override
    @NotNull
    public ConnectionState getConnectionState() {
        return currentState;
    }

    @Override
    public void handleConnectionStateChange(@NotNull ConnectionState newState) {
        if (shutdown) {
            logger.warn("Ignoring state change - manager is shutdown");
            return;
        }

        if (currentState == newState) {
            logger.debug("Connection state unchanged: {}", newState);
            return;
        }

        ConnectionState oldState = currentState;
        currentState = newState;

        logger.info("Connection state changed: {} -> {}", oldState, newState);

        // Handle state-specific actions
        handleStateTransition(oldState, newState);

        // Notify listeners
        notifyStateListeners(oldState, newState);
    }

    // ========================================================================
    // Public API - Subscription Management
    // ========================================================================

    @Override
    public void registerManagedSubscription(@NotNull String subject,
                                          @NotNull SubscriptionHandler handler,
                                          @NotNull Consumer<byte[]> consumer,
                                          boolean async) {
        if (shutdown) {
            throw new SubscriptionLifecycleException("Cannot register subscription - manager is shutdown");
        }

        if (managedSubscriptions.containsKey(subject)) {
            logger.warn("Subscription already managed for subject '{}'", subject);
            return;
        }

        ManagedSubscription subscription = new ManagedSubscription(handler, consumer, async);
        managedSubscriptions.put(subject, subscription);

        logger.debug("Registered managed subscription for subject '{}'", subject);

        // Activate immediately if connected
        if (currentState == ConnectionState.CONNECTED) {
            activateSubscription(subject);
        }
    }

    @Override
    public void activateAllSubscriptions() {
        if (currentState != ConnectionState.CONNECTED) {
            logger.warn("Cannot activate subscriptions - not connected");
            return;
        }

        int activatedCount = 0;
        for (String subject : managedSubscriptions.keySet()) {
            try {
                activateSubscription(subject);
                activatedCount++;
            } catch (Exception e) {
                logger.error("Failed to activate subscription for subject '{}'", subject, e);
            }
        }

        logger.info("Activated {} subscriptions", activatedCount);
    }

    @Override
    public void deactivateAllSubscriptions() {
        int deactivatedCount = 0;
        for (String subject : managedSubscriptions.keySet()) {
            try {
                deactivateSubscription(subject);
                deactivatedCount++;
            } catch (Exception e) {
                logger.error("Failed to deactivate subscription for subject '{}'", subject, e);
            }
        }

        logger.info("Deactivated {} subscriptions", deactivatedCount);
    }

    // ========================================================================
    // Public API - Lifecycle Management
    // ========================================================================

    @Override
    public void shutdown() {
        if (shutdown) {
            logger.warn("Lifecycle manager is already shutdown");
            return;
        }

        shutdown = true;
        
        // Deactivate all subscriptions
        deactivateAllSubscriptions();
        
        // Clear managed subscriptions
        managedSubscriptions.clear();
        
        // Clear listeners
        stateListeners.clear();
        
        logger.info("SubscriptionLifecycleManager shutdown complete");
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    // ========================================================================
    // Public API - Event Listeners
    // ========================================================================

    @Override
    public void addConnectionStateListener(@NotNull ConnectionStateListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        
        stateListeners.add(listener);
        logger.debug("Added connection state listener: {}", listener.getClass().getSimpleName());
    }

    @Override
    public void removeConnectionStateListener(@NotNull ConnectionStateListener listener) {
        if (listener == null) {
            return;
        }
        
        boolean removed = stateListeners.remove(listener);
        if (removed) {
            logger.debug("Removed connection state listener: {}", listener.getClass().getSimpleName());
        }
    }

    // ========================================================================
    // Internal Implementation - State Management
    // ========================================================================

    /**
     * Handles state transitions with appropriate actions.
     *
     * @param oldState the previous state
     * @param newState the new state
     */
    private void handleStateTransition(@NotNull ConnectionState oldState,
                                     @NotNull ConnectionState newState) {
        switch (newState) {
            case CONNECTED:
                handleConnectedState();
                break;
            case DISCONNECTED:
                handleDisconnectedState();
                break;
            case RECONNECTING:
                handleReconnectingState();
                break;
            case SHUTTING_DOWN:
                handleShuttingDownState();
                break;
            default:
                // No special handling needed
                break;
        }
    }

    /**
     * Handles actions when entering CONNECTED state.
     */
    private void handleConnectedState() {
        logger.info("Handling CONNECTED state - activating subscriptions");
        activateAllSubscriptions();
    }

    /**
     * Handles actions when entering DISCONNECTED state.
     */
    private void handleDisconnectedState() {
        logger.info("Handling DISCONNECTED state - deactivating subscriptions");
        deactivateAllSubscriptions();
    }

    /**
     * Handles actions when entering RECONNECTING state.
     */
    private void handleReconnectingState() {
        logger.info("Handling RECONNECTING state - preparing for reconnection");
        // Additional reconnection logic could be added here
    }

    /**
     * Handles actions when entering SHUTTING_DOWN state.
     */
    private void handleShuttingDownState() {
        logger.info("Handling SHUTTING_DOWN state - preparing for shutdown");
        // Additional shutdown preparation could be added here
    }

    /**
     * Notifies all registered listeners about state changes.
     *
     * @param oldState the previous state
     * @param newState the new state
     */
    private void notifyStateListeners(@NotNull ConnectionState oldState,
                                     @NotNull ConnectionState newState) {
        for (ConnectionStateListener listener : stateListeners) {
            try {
                listener.onConnectionStateChanged(oldState, newState);
            } catch (Exception e) {
                logger.error("Error notifying connection state listener {}", 
                            listener.getClass().getSimpleName(), e);
            }
        }
    }

    // ========================================================================
    // Internal Implementation - Subscription Activation
    // ========================================================================

    /**
     * Activates a single subscription.
     *
     * @param subject the subject to activate
     */
    private void activateSubscription(@NotNull String subject) {
        ManagedSubscription subscription = managedSubscriptions.get(subject);
        if (subscription == null) {
            logger.warn("No managed subscription found for subject '{}'", subject);
            return;
        }

        try {
            // Register the subscription with the registry
            if (subscription.handler != null) {
                subscriptionRegistry.registerMethodSubscription(
                    subscription.handler.getPlugin(),
                    subscription.handler.getMethod(),
                    subject,
                    subscription.async
                );
            } else if (subscription.consumer != null) {
                subscriptionRegistry.registerConsumerSubscription(
                    subject,
                    subscription.consumer,
                    subscription.async
                );
            }

            subscription.active = true;
            logger.debug("Activated subscription for subject '{}'", subject);

        } catch (Exception e) {
            logger.error("Failed to activate subscription for subject '{}'", subject, e);
            subscription.active = false;
        }
    }

    /**
     * Deactivates a single subscription.
     *
     * @param subject the subject to deactivate
     */
    private void deactivateSubscription(@NotNull String subject) {
        ManagedSubscription subscription = managedSubscriptions.get(subject);
        if (subscription == null) {
            return;
        }

        try {
            // Unregister the subscription
            subscriptionRegistry.unregisterSubscription(subject);
            subscription.active = false;
            logger.debug("Deactivated subscription for subject '{}'", subject);

        } catch (Exception e) {
            logger.error("Failed to deactivate subscription for subject '{}'", subject, e);
        }
    }

    // ========================================================================
    // Internal Data Structures
    // ========================================================================

    /**
     * Internal representation of a managed subscription.
     */
    private static class ManagedSubscription {
        final SubscriptionHandler handler;
        final Consumer<byte[]> consumer;
        final boolean async;
        volatile boolean active = false;

        ManagedSubscription(SubscriptionHandler handler,
                          Consumer<byte[]> consumer,
                          boolean async) {
            if (handler == null && consumer == null) {
                throw new IllegalArgumentException("Either handler or consumer must be provided");
            }
            if (handler != null && consumer != null) {
                throw new IllegalArgumentException("Only one of handler or consumer should be provided");
            }

            this.handler = handler;
            this.consumer = consumer;
            this.async = async;
        }

        @Override
        public String toString() {
            return "ManagedSubscription{" +
                   "handler=" + (handler != null ? handler.getMethod().getName() : "null") +
                   ", consumer=" + (consumer != null ? "present" : "null") +
                   ", async=" + async +
                   ", active=" + active +
                   '}';
        }
    }
}