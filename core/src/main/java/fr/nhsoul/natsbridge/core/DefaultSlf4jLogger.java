package fr.nhsoul.natsbridge.core;

import fr.nhsoul.natsbridge.common.logger.NatsLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implémentation par défaut de NatsLogger utilisant SLF4J.
 */
public class DefaultSlf4jLogger implements NatsLogger {

    private final Logger logger;

    public DefaultSlf4jLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    @Override
    public void info(@NotNull String message, Object... args) {
        logger.info(format(message, args));
    }

    @Override
    public void warn(@NotNull String message, Object... args) {
        logger.warn(format(message, args));
    }

    @Override
    public void error(@NotNull String message, @Nullable Throwable throwable, Object... args) {
        if (throwable != null) {
            logger.error(format(message, args), throwable);
        } else {
            logger.error(format(message, args));
        }
    }

    @Override
    public void debug(@NotNull String message, Object... args) {
        logger.debug(format(message, args));
    }
}
