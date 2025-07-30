package fr.nhsoul.natsbridge.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour marquer les méthodes qui doivent écouter des sujets NATS.
 *
 * La méthode annotée doit :
 * - Être publique
 * - Accepter un seul paramètre (String pour les messages texte, byte[] pour les messages binaires)
 * - Ne pas être statique
 *
 * Exemple d'utilisation :
 * <pre>
 * {@code
 * @NatsSubscribe("game.player.join")
 * public void onPlayerJoin(String message) {
 *     // Traitement du message
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NatsSubscribe {

    /**
     * Le sujet NATS à écouter.
     *
     * @return le nom du sujet NATS
     */
    String value();

    /**
     * Indique si le traitement du message doit être asynchrone.
     * Par défaut, les messages sont traités de manière synchrone.
     *
     * @return true si le traitement doit être asynchrone
     */
    boolean async() default false;
}