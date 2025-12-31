package fr.nhsoul.natsbridge.common.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
     * @throws IllegalStateException si la connexion NATS n'est pas disponible
     */
    CompletableFuture<Void> publishRawAsync(@NotNull String subject, @Nullable byte[] data);

    /**
     * Envoie une chaîne UTF-8 de manière asynchrone.
     *
     * @param subject le sujet NATS sur lequel publier
     * @param data la chaîne à envoyer (peut être null pour un message vide)
     * @return un CompletableFuture qui se complète quand le message est envoyé
     * @throws IllegalStateException si la connexion NATS n'est pas disponible
     */
    CompletableFuture<Void> publishStringAsync(@NotNull String subject, @Nullable String data);

    /**
     * Souscrit un Consumer à un sujet NATS pour un traitement bas niveau.
     * <p>
     * Cette méthode est plus performante que l'approche par annotation car elle évite
     * la réflexion et permet un contrôle direct sur le traitement des messages.
     *
     * @param subject le sujet NATS sur lequel s'abonner
     * @param consumer le Consumer qui traitera les messages (reçoit les données brutes en byte[])
     * @param async   {@code true} pour traiter les messages de manière asynchrone,
     *                {@code false} pour un traitement synchrone
     * @throws IllegalStateException si la connexion NATS n'est pas disponible
     */
    void subscribeSubject(@NotNull String subject,
                         @NotNull Consumer<byte[]> consumer,
                         boolean async);

    /**
     * Souscrit un Consumer à un sujet NATS pour un traitement de messages texte.
     * <p>
     * Cette méthode est plus pratique que la version byte[] pour les cas courants
     * où les messages sont des chaînes de caractères UTF-8.
     *
     * @param subject le sujet NATS sur lequel s'abonner
     * @param consumer le Consumer qui traitera les messages (reçoit les messages comme String)
     * @param async   {@code true} pour traiter les messages de manière asynchrone,
     *                {@code false} pour un traitement synchrone
     * @throws IllegalStateException si la connexion NATS n'est pas disponible
     */
    void subscribeStringSubject(@NotNull String subject,
                               @NotNull Consumer<String> consumer,
                               boolean async);

    /**
     * Annule l'abonnement actif sur un sujet NATS donné.
     * <p>
     * Si aucun abonnement n'existe pour ce sujet, cet appel est sans effet.
     *
     * @param subject le sujet NATS à désabonner
     */
    void unsubscribeSubject(@NotNull String subject);

    /**
     * Vérifie si la connexion NATS est active et disponible.
     *
     * @return {@code true} si la connexion est active, {@code false} sinon
     */
    boolean isConnected();

    /**
     * Obtient le statut de connexion sous forme lisible.
     *
     * @return le statut actuel de la connexion (par exemple "CONNECTED", "DISCONNECTED", etc.)
     */
    @NotNull
    String getConnectionStatus();
}
