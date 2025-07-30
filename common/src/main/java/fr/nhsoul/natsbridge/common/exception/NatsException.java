package fr.nhsoul.natsbridge.common.exception;

/**
 * Exception spécifique à la librairie NATS.
 * Encapsule toutes les erreurs liées à NATS pour un traitement uniforme.
 */
public class NatsException extends RuntimeException {

    public NatsException(String message) {
        super(message);
    }

    public NatsException(String message, Throwable cause) {
        super(message, cause);
    }

    public NatsException(Throwable cause) {
        super(cause);
    }

    /**
     * Exception levée lors des problèmes de connexion.
     */
    public static class ConnectionException extends NatsException {
        public ConnectionException(String message) {
            super(message);
        }

        public ConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception levée lors des problèmes de configuration.
     */
    public static class ConfigurationException extends NatsException {
        public ConfigurationException(String message) {
            super(message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception levée lors des problèmes de publication de messages.
     */
    public static class PublishException extends NatsException {
        public PublishException(String message) {
            super(message);
        }

        public PublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception levée lors des problèmes de souscription.
     */
    public static class SubscriptionException extends NatsException {
        public SubscriptionException(String message) {
            super(message);
        }

        public SubscriptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}