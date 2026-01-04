package fr.nhsoul.natsbridge.common.exception;


/**
 * Exception specific to the NATS library.
 * Encapsulates all NATS-related errors for uniform handling.
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
     * Exception thrown during connection problems.
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
     * Exception thrown during configuration problems.
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
     * Exception thrown during message publication problems.
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
     * Exception thrown during subscription problems.
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