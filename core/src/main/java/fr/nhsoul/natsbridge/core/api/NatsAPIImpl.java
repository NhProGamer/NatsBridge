package fr.nhsoul.natsbridge.core.api;

import fr.nhsoul.natsbridge.common.annotation.NatsSubscribe;
import fr.nhsoul.natsbridge.common.api.NatsAPI;
import fr.nhsoul.natsbridge.common.exception.NatsException;
import fr.nhsoul.natsbridge.core.connection.NatsConnectionManager;
import fr.nhsoul.natsbridge.core.subscription.SubscriptionManager;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Implémentation thread-safe de l'API NATS.
 * Utilise le gestionnaire de connexion partagé pour publier des messages.
 */
public class NatsAPIImpl implements NatsAPI {

    private static final Logger logger = LoggerFactory.getLogger(NatsAPIImpl.class);
    private final NatsConnectionManager connectionManager;
    private final SubscriptionManager subscriptionManager;

    public NatsAPIImpl(@NotNull NatsConnectionManager connectionManager, SubscriptionManager subscriptionManager) {
        this.connectionManager = connectionManager;
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public void publishRaw(@NotNull String subject, @Nullable byte[] data) {
        validateSubject(subject);

        Connection connection = getConnectionOrThrow();

        try {
            if (data == null) {
                connection.publish(subject, null);
            } else {
                connection.publish(subject, data);
            }

            logger.debug("Published raw message to subject '{}' ({} bytes)", subject,
                    data != null ? data.length : 0);

        } catch (Exception e) {
            logger.error("Failed to publish raw message to subject '{}'", subject, e);
            throw new NatsException.PublishException("Failed to publish raw message to subject: " + subject, e);
        }
    }

    @Override
    public void publishString(@NotNull String subject, @Nullable String data) {
        validateSubject(subject);

        byte[] bytes = data != null ? data.getBytes(StandardCharsets.UTF_8) : null;
        publishRaw(subject, bytes);

        logger.debug("Published string message to subject '{}': {}", subject,
                data != null ? data.substring(0, Math.min(data.length(), 100)) + "..." : "null");
    }

    @Override
    public CompletableFuture<Void> publishRawAsync(@NotNull String subject, @Nullable byte[] data) {
        return CompletableFuture.runAsync(() -> publishRaw(subject, data));
    }

    @Override
    public CompletableFuture<Void> publishStringAsync(@NotNull String subject, @Nullable String data) {
        return CompletableFuture.runAsync(() -> publishString(subject, data));
    }

    @Override
    public void subscribeSubject(@NotNull Object classInstance, @NotNull Method method, @NotNull String subject, boolean async) {
        subscriptionManager.registerSubscription(classInstance, method, subject, async);
    }

    @Override
    public void unsubscribeSubject(@NotNull String subject) {
        subscriptionManager.unsubscribe(subject);
    }

    @Override
    public boolean isConnected() {
        return connectionManager.isConnected();
    }

    @Override
    @NotNull
    public String getConnectionStatus() {
        return connectionManager.getConnectionStatus();
    }

    private Connection getConnectionOrThrow() {
        Connection connection = connectionManager.getConnection();
        if (connection == null || connection.getStatus() != Connection.Status.CONNECTED) {
            throw new IllegalStateException("NATS connection is not available. Status: " +
                    connectionManager.getConnectionStatus());
        }
        return connection;
    }

    private void validateSubject(@NotNull String subject) {
        if (subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be empty or blank");
        }

        // Validation basique du format NATS
        if (subject.contains(" ")) {
            throw new IllegalArgumentException("Subject cannot contain spaces: " + subject);
        }

        if (subject.startsWith(".") || subject.endsWith(".")) {
            throw new IllegalArgumentException("Subject cannot start or end with dot: " + subject);
        }

        if (subject.contains("..")) {
            throw new IllegalArgumentException("Subject cannot contain consecutive dots: " + subject);
        }
    }
}