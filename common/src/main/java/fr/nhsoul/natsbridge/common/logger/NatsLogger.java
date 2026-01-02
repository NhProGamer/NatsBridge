package fr.nhsoul.natsbridge.common.logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface de logging unifiée pour NatsBridge.
 * Permet d'utiliser les loggers natifs de chaque plateforme tout en gardant une
 * API commune.
 */
public interface NatsLogger {

    String PREFIX = "[NatsBridge] ";

    void info(@NotNull String message, Object... args);

    void warn(@NotNull String message, Object... args);

    void error(@NotNull String message, @Nullable Throwable throwable, Object... args);

    void debug(@NotNull String message, Object... args);

    /**
     * Formate un message avec le préfixe et remplace les {} par les arguments
     * fournis (style SLF4J).
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
