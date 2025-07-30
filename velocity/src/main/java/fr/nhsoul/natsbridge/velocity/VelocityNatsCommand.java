package fr.nhsoul.natsbridge.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import fr.nhsoul.natsbridge.core.NatsBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commande pour gérer et diagnostiquer la librairie NATS depuis Velocity.
 */
public class VelocityNatsCommand implements SimpleCommand {

    private final VelocityNatsPlugin plugin;
    private final NatsBridge natsBridge;

    public VelocityNatsCommand(@NotNull VelocityNatsPlugin plugin, @NotNull NatsBridge natsBridge) {
        this.plugin = plugin;
        this.natsBridge = natsBridge;
    }

    @Override
    public void execute(@NotNull Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("natsbridge.admin")) {
            source.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return;
        }

        if (args.length == 0) {
            sendHelp(source);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                showStatus(source);
                break;

            case "test":
                if (args.length < 3) {
                    source.sendMessage(Component.text("Usage: /nats test <subject> <message>", NamedTextColor.RED));
                    return;
                }
                testPublish(source, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                break;

            case "rescan":
                rescanPlugins(source);
                break;

            case "reload":
                reloadConfig(source);
                break;

            default:
                sendHelp(source);
                break;
        }
    }

    @Override
    public List<String> suggest(@NotNull Invocation invocation) {
        String[] args = invocation.arguments();

        if (!invocation.source().hasPermission("natsbridge.admin")) {
            return List.of();
        }

        if (args.length <= 1) {
            String partial = args.length > 0 ? args[0].toLowerCase() : "";
            return Arrays.asList("status", "test", "rescan", "reload")
                    .stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            return Arrays.asList("game.test", "server.status", "player.event");
        }

        return List.of();
    }

    @Override
    public boolean hasPermission(@NotNull Invocation invocation) {
        return invocation.source().hasPermission("natsbridge.admin");
    }

    private void sendHelp(@NotNull CommandSource source) {
        source.sendMessage(Component.text("=== NatsBridge Commands ===", NamedTextColor.GOLD));
        source.sendMessage(Component.text("/nats status", NamedTextColor.YELLOW)
                .append(Component.text(" - Show NATS connection status", NamedTextColor.WHITE)));
        source.sendMessage(Component.text("/nats test <subject> <message>", NamedTextColor.YELLOW)
                .append(Component.text(" - Send a test message", NamedTextColor.WHITE)));
        source.sendMessage(Component.text("/nats rescan", NamedTextColor.YELLOW)
                .append(Component.text(" - Rescan all plugins for NATS subscriptions", NamedTextColor.WHITE)));
        source.sendMessage(Component.text("/nats reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload NATS configuration", NamedTextColor.WHITE)));
    }

    private void showStatus(@NotNull CommandSource source) {
        source.sendMessage(Component.text("=== NATS Status ===", NamedTextColor.GOLD));

        boolean initialized = natsBridge.isInitialized();
        boolean connected = natsBridge.isConnected();
        String status = natsBridge.getConnectionStatus();

        source.sendMessage(Component.text("Initialized: ", NamedTextColor.YELLOW)
                .append(Component.text(initialized ? "Yes" : "No",
                        initialized ? NamedTextColor.GREEN : NamedTextColor.RED)));
        source.sendMessage(Component.text("Connected: ", NamedTextColor.YELLOW)
                .append(Component.text(connected ? "Yes" : "No",
                        connected ? NamedTextColor.GREEN : NamedTextColor.RED)));
        source.sendMessage(Component.text("Status: ", NamedTextColor.YELLOW)
                .append(Component.text(status, NamedTextColor.WHITE)));

        // Informations sur la configuration
        source.sendMessage(Component.text("Servers: ", NamedTextColor.YELLOW)
                .append(Component.text(String.join(", ", natsBridge.getConfig().getServers()), NamedTextColor.WHITE)));

        if (natsBridge.getConfig().getAuth() != null && natsBridge.getConfig().getAuth().isEnabled()) {
            source.sendMessage(Component.text("Authentication: ", NamedTextColor.YELLOW)
                    .append(Component.text("Enabled", NamedTextColor.GREEN)));
        } else {
            source.sendMessage(Component.text("Authentication: ", NamedTextColor.YELLOW)
                    .append(Component.text("Disabled", NamedTextColor.RED)));
        }

        if (natsBridge.getConfig().getTls() != null && natsBridge.getConfig().getTls().isEnabled()) {
            source.sendMessage(Component.text("TLS: ", NamedTextColor.YELLOW)
                    .append(Component.text("Enabled", NamedTextColor.GREEN)));
        } else {
            source.sendMessage(Component.text("TLS: ", NamedTextColor.YELLOW)
                    .append(Component.text("Disabled", NamedTextColor.RED)));
        }
    }

    private void testPublish(@NotNull CommandSource source, @NotNull String subject, @NotNull String message) {
        if (!natsBridge.isConnected()) {
            source.sendMessage(Component.text("NATS is not connected. Cannot send test message.", NamedTextColor.RED));
            return;
        }

        try {
            natsBridge.getAPI().publishString(subject, message);
            source.sendMessage(Component.text("Test message sent successfully!", NamedTextColor.GREEN));
            source.sendMessage(Component.text("Subject: " + subject, NamedTextColor.GRAY));
            source.sendMessage(Component.text("Message: " + message, NamedTextColor.GRAY));
        } catch (Exception e) {
            source.sendMessage(Component.text("Failed to send test message: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warn("Test message failed: " + e.getMessage());
        }
    }

    private void rescanPlugins(@NotNull CommandSource source) {
        source.sendMessage(Component.text("Rescanning all plugins for NATS subscriptions...", NamedTextColor.YELLOW));

        try {
            VelocityPluginScanner scanner = new VelocityPluginScanner(plugin.getServer(), natsBridge);
            scanner.scanAllPlugins();
            source.sendMessage(Component.text("Plugin rescan completed successfully!", NamedTextColor.GREEN));
        } catch (Exception e) {
            source.sendMessage(Component.text("Failed to rescan plugins: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warn("Plugin rescan failed: " + e.getMessage());
        }
    }

    private void reloadConfig(@NotNull CommandSource source) {
        source.sendMessage(Component.text("Configuration reload is not supported yet.", NamedTextColor.RED));
        source.sendMessage(Component.text("Please restart the proxy to apply configuration changes.", NamedTextColor.YELLOW));
    }
}