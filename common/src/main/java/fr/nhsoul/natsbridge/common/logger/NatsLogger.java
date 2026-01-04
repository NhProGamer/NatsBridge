package fr.nhsoul.natsbridge.common.logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Unified logging interface for NatsBridge.
 * Allows using each platform's native loggers while maintaining a common API.
 */
public interface NatsLogger {

    String PREFIX = "[NatsBridge] ";

    void info(@NotNull String message, Object... args);

    void warn(@NotNull String message, Object... args);

    void error(@NotNull String message, @Nullable Throwable throwable, Object... args);

    void debug(@NotNull String message, Object... args);

    /**
     * Formats a message with the prefix and replaces {} with the provided arguments
     * (SLF4J style).
     */
    default String format(@NotNull String message, Object... args) {
        String formatted = message;
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                formatted = formatted.replaceFirst("\\{}", String.valueOf(arg));
            }
        }
        return PREFIX + formatted;
    }
}
