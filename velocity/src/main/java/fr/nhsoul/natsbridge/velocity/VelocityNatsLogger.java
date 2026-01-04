package fr.nhsoul.natsbridge.velocity;

import fr.nhsoul.natsbridge.common.logger.NatsLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;


/**
 * NatsLogger implementation for Velocity using SLF4J Logger.
 */
public class VelocityNatsLogger implements NatsLogger {

    private final Logger logger;

    public VelocityNatsLogger(Logger logger) {
        this.logger = logger;
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
