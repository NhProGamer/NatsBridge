package fr.nhsoul.natsbridge.common.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;


/**
 * Main interface for interacting with NATS.
 * Provides thread-safe methods for publishing messages.
 */
public interface NatsAPI {

    /**
     * Sends a raw message (byte array) on a given subject.
     *
     * @param subject the NATS subject to publish on
     * @param data    the data to send (can be null for an empty message)
     * @throws IllegalStateException if the NATS connection is not available
     */
    void publishRaw(@NotNull String subject, @Nullable byte[] data);

    /**
     * Sends a UTF-8 string as a message on a given subject.
     *
     * @param subject the NATS subject to publish on
     * @param data    the string to send (can be null for an empty message)
     * @throws IllegalStateException if the NATS connection is not available
     */
    void publishString(@NotNull String subject, @Nullable String data);

    /**
     * Sends a raw message asynchronously.
     *
     * @param subject the NATS subject to publish on
     * @param data    the data to send (can be null for an empty message)
     * @return a CompletableFuture that completes when the message is sent
     * @throws IllegalStateException if the NATS connection is not available
     */
    CompletableFuture<Void> publishRawAsync(@NotNull String subject, @Nullable byte[] data);

    /**
     * Sends a UTF-8 string asynchronously.
     *
     * @param subject the NATS subject to publish on
     * @param data    the string to send (can be null for an empty message)
     * @return a CompletableFuture that completes when the message is sent
     * @throws IllegalStateException if the NATS connection is not available
     */
    CompletableFuture<Void> publishStringAsync(@NotNull String subject, @Nullable String data);

    /**
     * Subscribes a Consumer to a NATS subject for low-level processing.
     * <p>
     * This method is more performant than the annotation approach as it avoids
     * reflection and allows direct control over message processing.
     *
     * @param subject  the NATS subject to subscribe to
     * @param consumer the Consumer that will process the messages (receives raw
     *                 data as byte[])
     * @param async    {@code true} to process messages asynchronously,
     *                 {@code false} for synchronous processing
     * @throws IllegalStateException if the NATS connection is not available
     */
    void subscribeSubject(@NotNull String subject,
            @NotNull Consumer<byte[]> consumer,
            boolean async);

    /**
     * Subscribes a Consumer to a NATS subject for text message processing.
     * <p>
     * This method is more convenient than the byte[] version for common cases
     * where messages are UTF-8 strings.
     *
     * @param subject  the NATS subject to subscribe to
     * @param consumer the Consumer that will process the messages (receives messages
     *                 as String)
     * @param async    {@code true} to process messages asynchronously,
     *                 {@code false} for synchronous processing
     * @throws IllegalStateException if the NATS connection is not available
     */
    void subscribeStringSubject(@NotNull String subject,
            @NotNull Consumer<String> consumer,
            boolean async);

    /**
     * Cancels the active subscription on a given NATS subject.
     * <p>
     * If no subscription exists for this subject, this call has no effect.
     *
     * @param subject the NATS subject to unsubscribe
     */
    void unsubscribeSubject(@NotNull String subject);

    /**
     * Checks if the NATS connection is active and available.
     *
     * @return {@code true} if the connection is active, {@code false} otherwise
     */
    boolean isConnected();

    /**
     * Gets the connection status in a readable form.
     *
     * @return the current connection status (e.g. "CONNECTED",
     *         "DISCONNECTED", etc.)
     */
    @NotNull
    String getConnectionStatus();
}
