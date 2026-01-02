package fr.nhsoul.natsbridge.bungeecord;

import fr.nhsoul.natsbridge.common.logger.NatsLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implémentation de NatsLogger pour BungeeCord utilisant
 * java.util.logging.Logger.
 */
public class BungeeCordNatsLogger implements NatsLogger {

    private final Logger logger;

    public BungeeCordNatsLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(@NotNull String message, Object... args) {
        logger.info(format(message, args));
    }

    @Override
    public void warn(@NotNull String message, Object... args) {
        logger.warning(format(message, args));
    }

    @Override
    public void error(@NotNull String message, @Nullable Throwable throwable, Object... args) {
        if (throwable != null) {
            logger.log(Level.SEVERE, format(message, args), throwable);
        } else {
            logger.severe(format(message, args));
        }
    }

    @Override
    public void debug(@NotNull String message, Object... args) {
        logger.log(Level.FINE, format(message, args));
    }
}
