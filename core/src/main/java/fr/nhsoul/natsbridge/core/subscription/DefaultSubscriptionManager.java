package fr.nhsoul.natsbridge.core.subscription;

import fr.nhsoul.natsbridge.common.logger.NatsLogger;
import fr.nhsoul.natsbridge.core.DefaultSlf4jLogger;
import fr.nhsoul.natsbridge.core.NatsBridge;
import fr.nhsoul.natsbridge.core.connection.NatsConnectionManager;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Implementation simplifiée de SubscriptionManager utilisant directement jnats.
 */
public class DefaultSubscriptionManager {

    private NatsLogger getLogger() {
        NatsBridge bridge = NatsBridge.getInstance();
        return bridge != null ? bridge.getLogger() : new DefaultSlf4jLogger(DefaultSubscriptionManager.class);
    }

    private final NatsConnectionManager connectionManager;

    // Maintain a list of registered subscription definitions to re-apply on
    // connect/reconnect
    private final List<SubscriptionDefinition> registeredSubscriptions = new CopyOnWriteArrayList<>();

    // Active dispatcher (recreated on connection)
    private volatile Dispatcher dispatcher;

    public DefaultSubscriptionManager(@NotNull NatsConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void registerConsumerSubscription(@NotNull String subject,
            @NotNull Consumer<byte[]> consumer,
            boolean async) {
        SubscriptionDefinition def = new SubscriptionDefinition(subject, msg -> {
            try {
                consumer.accept(msg.getData());
            } catch (Exception e) {
                getLogger().error("Error processing NATS message for subject {}", e, subject);
            }
        });

        addSubscription(def);
    }

    private void addSubscription(SubscriptionDefinition def) {
        registeredSubscriptions.add(def);
        // If already connected and dispatcher exists, subscribe immediately
        if (dispatcher != null && dispatcher.isActive()) {
            dispatcher.subscribe(def.subject, def.handler);
        }
    }

    public void subscribeAll() {
        Connection conn = connectionManager.getConnection();
        if (conn == null || conn.getStatus() != Connection.Status.CONNECTED) {
            getLogger().warn("Cannot subscribe: NATS not connected");
            return;
        }

        // Create a new dispatcher
        this.dispatcher = conn.createDispatcher();

        for (SubscriptionDefinition def : registeredSubscriptions) {
            try {
                this.dispatcher.subscribe(def.subject, def.handler);
                getLogger().debug("Subscribed to {}", def.subject);
            } catch (Exception e) {
                getLogger().error("Failed to subscribe to {}", e, def.subject);
            }
        }
        getLogger().info("Activated {} subscriptions", registeredSubscriptions.size());
    }

    public void unsubscribe(@NotNull String subject) {
        if (dispatcher != null && dispatcher.isActive()) {
            dispatcher.unsubscribe(subject);
        }
        registeredSubscriptions.removeIf(def -> def.subject.equals(subject));
    }

    public void unsubscribeAll() {
        if (dispatcher != null && dispatcher.isActive()) {
            for (SubscriptionDefinition def : registeredSubscriptions) {
                dispatcher.unsubscribe(def.subject);
            }
        }
        registeredSubscriptions.clear();
    }

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