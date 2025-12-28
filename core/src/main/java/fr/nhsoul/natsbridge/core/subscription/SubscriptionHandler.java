package fr.nhsoul.natsbridge.core.subscription;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Encapsule les informations d'un handler de souscription NATS.
 * Contient la référence à l'objet, la méthode à appeler et la configuration.
 */
public class SubscriptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionHandler.class);

    private final Object plugin;
    private final Method method;
    private final boolean async;
    private final MethodHandle methodHandle;

    public SubscriptionHandler(@NotNull Object plugin, @NotNull Method method, boolean async) {
        this.plugin = plugin;
        this.method = method;
        this.async = async;

        // S'assurer que la méthode est accessible
        method.setAccessible(true);

        // Initialisation du MethodHandle pour de meilleures performances
        MethodHandle handle = null;
        try {
            handle = MethodHandles.lookup().unreflect(method);
        } catch (IllegalAccessException e) {
            logger.debug("MethodHandle not available for {}.{}, using reflection fallback", 
                    method.getDeclaringClass().getSimpleName(), method.getName());
        }
        this.methodHandle = handle;
    }

    /**
     * Invoque la méthode handler avec les données fournies.
     * Utilise MethodHandle si disponible pour de meilleures performances,
     * avec fallback sur la réflexion standard.
     *
     * @param data les données à passer à la méthode
     * @throws Exception si l'invocation échoue
     */
    public void invoke(@NotNull Object data) throws Exception {
        try {
            if (methodHandle != null) {
                // Utilisation de MethodHandle pour de meilleures performances
                methodHandle.invoke(plugin, data);
            } else {
                // Fallback sur la réflexion standard
                method.invoke(plugin, data);
            }
        } catch (InvocationTargetException e) {
            // Déballage de l'exception réelle
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Exception) {
                throw (Exception) cause;
            } else {
                throw new RuntimeException("Unexpected error during method invocation", cause);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to access handler method", e);
        } catch (Throwable e) {
            // Gestion des exceptions de MethodHandle
            throw new RuntimeException("Failed to invoke handler method", e);
        }
    }

    @NotNull
    public Object getPlugin() {
        return plugin;
    }

    @NotNull
    public Method getMethod() {
        return method;
    }

    public boolean isAsync() {
        return async;
    }

    @Override
    public String toString() {
        return String.format("SubscriptionHandler{plugin=%s, method=%s, async=%s}",
                plugin.getClass().getSimpleName(),
                method.getName(),
                async);
    }
}