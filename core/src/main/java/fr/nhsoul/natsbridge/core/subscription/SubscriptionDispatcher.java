package fr.nhsoul.natsbridge.core.subscription;

import io.nats.client.Message;
import org.jetbrains.annotations.NotNull;

/**
 * Dispatcher interface for handling incoming NATS messages.
 * <p>
 * This interface defines the contract for dispatching messages to their
 * appropriate handlers or consumers. Implementations should handle both
 * synchronous and asynchronous message processing.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>Implementations must be thread-safe as messages may arrive from multiple
 * threads concurrently.</p>
 *
 * <h2>Performance Considerations</h2>
 * <p>Dispatchers should be optimized for high throughput and low latency,
 * especially when dealing with high-frequency message streams.</p>
 */
public interface SubscriptionDispatcher {

    /**
     * Dispatches a message to the appropriate handler.
     *
     * @param message the NATS message to dispatch
     * @throws SubscriptionDispatchException if dispatching fails
     */
    void dispatch(@NotNull Message message);

    /**
     * Starts the dispatcher.
     * <p>
     * This method should be called when the NATS connection is established
     * and the dispatcher is ready to process messages.
     * </p>
     */
    void start();

    /**
     * Stops the dispatcher.
     * <p>
     * This method should be called when the NATS connection is closed or
     * when the dispatcher is no longer needed.
     * </p>
     */
    void stop();

    /**
     * Checks if the dispatcher is currently running.
     *
     * @return {@code true} if the dispatcher is running, {@code false} otherwise
     */
    boolean isRunning();

    /**
     * Gets the number of messages successfully processed.
     *
     * @return the count of successfully processed messages
     */
    long getProcessedMessageCount();

    /**
     * Gets the number of messages that failed to process.
     *
     * @return the count of failed messages
     */
    long getFailedMessageCount();

    /**
     * Gets the average processing time for messages.
     *
     * @return the average processing time in milliseconds
     */
    double getAverageProcessingTime();

    /**
     * Exception thrown when message dispatching fails.
     */
    class SubscriptionDispatchException extends RuntimeException {
        public SubscriptionDispatchException(String message) {
            super(message);
        }

        public SubscriptionDispatchException(String message, Throwable cause) {
            super(message, cause);
        }

        public SubscriptionDispatchException(Throwable cause) {
            super(cause);
        }
    }
}