package fr.nhsoul.natsbridge.core.subscription;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Lifecycle manager interface for NATS subscriptions.
 * <p>
 * This interface defines the contract for managing the lifecycle of subscriptions,
 * including connection management, reconnection handling, and resource cleanup.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Managing NATS connection state</li>
 *   <li>Handling subscription registration when connected</li>
 *   <li>Managing reconnection attempts</li>
 *   <li>Cleaning up resources on shutdown</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Implementations must be thread-safe as lifecycle events may occur from
 * different threads.</p>
 */
public interface SubscriptionLifecycleManager {

    /**
     * Connection state enum.
     */
    enum ConnectionState {
        /** Connection is not established */
        DISCONNECTED,
        /** Connection is being established */
        CONNECTING,
        /** Connection is established and active */
        CONNECTED,
        /** Connection is being reconnected */
        RECONNECTING,
        /** Connection is shutting down */
        SHUTTING_DOWN
    }

    /**
     * Gets the current connection state.
     *
     * @return the current connection state
     */
    @NotNull
    ConnectionState getConnectionState();

    /**
     * Registers a subscription for automatic connection management.
     * <p>
     * The subscription will be automatically activated when the connection
     * is established and reactivated on reconnection.
     * </p>
     *
     * @param subject the subject to subscribe to
     * @param handler the handler for method-based subscriptions
     * @param consumer the consumer for consumer-based subscriptions
     * @param async whether the subscription should be processed asynchronously
     */
    void registerManagedSubscription(@NotNull String subject,
                                   @NotNull SubscriptionHandler handler,
                                   @NotNull Consumer<byte[]> consumer,
                                   boolean async);

    /**
     * Activates all registered subscriptions.
     * <p>
     * This method should be called when the NATS connection is established.
     * </p>
     */
    void activateAllSubscriptions();

    /**
     * Deactivates all active subscriptions.
     * <p>
     * This method should be called when the NATS connection is lost.
     * </p>
     */
    void deactivateAllSubscriptions();

    /**
     * Handles a connection state change.
     *
     * @param newState the new connection state
     */
    void handleConnectionStateChange(@NotNull ConnectionState newState);

    /**
     * Shuts down the lifecycle manager and cleans up all resources.
     * <p>
     * This method should be called when the application is shutting down.
     * </p>
     */
    void shutdown();

    /**
     * Checks if the manager is shutdown.
     *
     * @return {@code true} if the manager is shutdown, {@code false} otherwise
     */
    boolean isShutdown();

    /**
     * Adds a connection state listener.
     *
     * @param listener the listener to add
     */
    void addConnectionStateListener(@NotNull ConnectionStateListener listener);

    /**
     * Removes a connection state listener.
     *
     * @param listener the listener to remove
     */
    void removeConnectionStateListener(@NotNull ConnectionStateListener listener);

    /**
     * Connection state listener interface.
     */
    @FunctionalInterface
    interface ConnectionStateListener {
        /**
         * Called when the connection state changes.
         *
         * @param oldState the previous connection state
         * @param newState the new connection state
         */
        void onConnectionStateChanged(@NotNull ConnectionState oldState,
                                     @NotNull ConnectionState newState);
    }

    /**
     * Exception thrown when subscription lifecycle management fails.
     */
    class SubscriptionLifecycleException extends RuntimeException {
        public SubscriptionLifecycleException(String message) {
            super(message);
        }

        public SubscriptionLifecycleException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}