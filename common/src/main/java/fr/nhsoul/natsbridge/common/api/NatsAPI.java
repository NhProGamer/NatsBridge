package fr.nhsoul.natsbridge.common.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Interface principale pour interagir avec NATS.
 * Fournit des méthodes thread-safe pour publier des messages.
 */
public interface NatsAPI {

    /**
     * Envoie un message brut (byte array) sur un sujet donné.
     *
     * @param subject le sujet NATS sur lequel publier
     * @param data les données à envoyer (peut être null pour un message vide)
     * @throws IllegalStateException si la connexion NATS n'est pas disponible
     */
    void publishRaw(@NotNull String subject, @Nullable byte[] data);

    /**
     * Envoie une chaîne UTF-8 sous forme de message sur un sujet donné.
     *
     * @param subject le sujet NATS sur lequel publier
     * @param data la chaîne à envoyer (peut être null pour un message vide)
     * @throws IllegalStateException si la connexion NATS n'est pas disponible
     */
    void publishString(@NotNull String subject, @Nullable String data);

    /**
     * Envoie un message brut de manière asynchrone.
     *
     * @param subject le sujet NATS sur lequel publier
     * @param data les données à envoyer (peut être null pour un message vide)
     * @return un CompletableFuture qui se complète quand le message est envoyé
     */
    CompletableFuture<Void> publishRawAsync(@NotNull String subject, @Nullable byte[] data);

    /**
     * Envoie une chaîne UTF-8 de manière asynchrone.
     *
     * @param subject le sujet NATS sur lequel publier
     * @param data la chaîne à envoyer (peut être null pour un message vide)
     * @return un CompletableFuture qui se complète quand le message est envoyé
     */
    CompletableFuture<Void> publishStringAsync(@NotNull String subject, @Nullable String data);

    void subscribeSubject(@NotNull Object plugin, @NotNull Method method, @NotNull String subject, boolean async);

    /**
     * Vérifie si la connexion NATS est active et disponible.
     *
     * @return true si la connexion est active
     */
    boolean isConnected();

    /**
     * Obtient le statut de connexion sous forme lisible.
     *
     * @return le statut actuel de la connexion
     */
    @NotNull
    String getConnectionStatus();
}