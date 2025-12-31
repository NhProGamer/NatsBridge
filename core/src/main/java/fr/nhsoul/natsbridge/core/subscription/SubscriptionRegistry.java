package fr.nhsoul.natsbridge.core.subscription;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Registry interface for managing NATS subscriptions.
 * <p>
 * This interface defines the contract for registering, retrieving, and managing
 * subscriptions in a type-safe manner. It supports both method-based and
 * consumer-based subscriptions.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>All implementations must be thread-safe as this registry may be accessed
 * from multiple threads concurrently.</p>
 */
public interface SubscriptionRegistry {

    /**
     * Registers a method-based subscription.
     *
     * @param pluginInstance the plugin instance containing the method
     * @param method the method to invoke when a message is received
     * @param subject the NATS subject to subscribe to
     * @param async {@code true} if the subscription should be processed asynchronously
     * @throws IllegalArgumentException if the method signature is invalid
     * @throws IllegalStateException if a subscription for the same method/subject already exists
     */
    void registerMethodSubscription(@NotNull Object pluginInstance,
                                   @NotNull Method method,
                                   @NotNull String subject,
                                   boolean async);

    /**
     * Registers a consumer-based subscription.
     *
     * @param subject the NATS subject to subscribe to
     * @param consumer the consumer that will process incoming messages
     * @param async {@code true} if the subscription should be processed asynchronously
     * @throws IllegalStateException if a subscription for the same subject already exists
     */
    void registerConsumerSubscription(@NotNull String subject,
                                     @NotNull Consumer<byte[]> consumer,
                                     boolean async);

    /**
     * Unregisters a subscription by subject.
     *
     * @param subject the subject to unregister
     * @return {@code true} if a subscription was removed, {@code false} otherwise
     */
    boolean unregisterSubscription(@NotNull String subject);

    /**
     * Unregisters all subscriptions.
     */
    void unregisterAll();

    /**
     * Gets all registered subjects.
     *
     * @return an unmodifiable collection of all subscribed subjects
     */
    @NotNull
    Collection<String> getAllSubjects();

    /**
     * Gets the subscription count for a specific subject.
     *
     * @param subject the subject to check
     * @return the number of subscriptions for the subject, or 0 if not subscribed
     */
    int getSubscriptionCount(@NotNull String subject);

    /**
     * Checks if a subject has any subscriptions.
     *
     * @param subject the subject to check
     * @return {@code true} if the subject has subscriptions, {@code false} otherwise
     */
    boolean hasSubscriptions(@NotNull String subject);

    /**
     * Gets the handler for a method-based subscription.
     *
     * @param subject the subject to get the handler for
     * @return the subscription handler, or {@code null} if not found or if it's a consumer subscription
     */
    @Nullable
    SubscriptionHandler getMethodHandler(@NotNull String subject);

    /**
     * Gets the consumer for a consumer-based subscription.
     *
     * @param subject the subject to get the consumer for
     * @return the consumer, or {@code null} if not found or if it's a method subscription
     */
    @Nullable
    Consumer<byte[]> getConsumer(@NotNull String subject);

    /**
     * Checks if a subscription is configured for async processing.
     *
     * @param subject the subject to check
     * @return {@code true} if the subscription is async, {@code false} if sync or not found
     */
    boolean isAsync(@NotNull String subject);

    /**
     * Checks if a class has already been scanned for annotations.
     *
     * @param clazz the class to check
     * @return {@code true} if the class has been scanned, {@code false} otherwise
     */
    boolean isClassScanned(@NotNull Class<?> clazz);

    /**
     * Marks a class as scanned to avoid duplicate processing.
     *
     * @param clazz the class that has been scanned
     * @return {@code true} if the class was not previously scanned, {@code false} otherwise
     */
    boolean markClassAsScanned(@NotNull Class<?> clazz);

    /**
     * Registers a String-based consumer subscription.
     * <p>
     * This is a convenience method that automatically converts byte[] messages
     * to String using UTF-8 encoding before passing them to the consumer.
     * </p>
     *
     * @param subject the NATS subject to subscribe to
     * @param consumer the consumer that will process incoming messages as String
     * @param async whether to process messages asynchronously
     */
    void registerStringSubject(@NotNull String subject,
                             @NotNull Consumer<String> consumer,
                             boolean async);
}