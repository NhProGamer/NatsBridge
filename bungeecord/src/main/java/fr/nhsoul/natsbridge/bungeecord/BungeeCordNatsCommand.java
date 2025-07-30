package fr.nhsoul.natsbridge.bungeecord;

import fr.nhsoul.natsbridge.core.NatsBridge;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commande pour gérer et diagnostiquer la librairie NATS depuis BungeeCord.
 */
public class BungeeCordNatsCommand extends Command implements TabExecutor {

    private final BungeeCordNatsPlugin plugin;
    private final NatsBridge natsBridge;

    public BungeeCordNatsCommand(@NotNull BungeeCordNatsPlugin plugin, @NotNull NatsBridge natsBridge) {
        super("nats", "natslib.admin");
        this.plugin = plugin;
        this.natsBridge = natsBridge;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("natslib.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                showStatus(sender);
                break;

            case "test":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /nats test <subject> <message>");
                    return;
                }
                testPublish(sender, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                break;

            case "rescan":
                rescanPlugins(sender);
                break;

            case "reload":
                reloadConfig(sender);
                break;

            default:
                sendHelp(sender);
                break;
        }
    }

    @Override
    public Iterable<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("natslib.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return Arrays.asList("status", "test", "rescan", "reload")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            return Arrays.asList("game.test", "server.status", "player.event");
        }

        return List.of();
    }

    private void sendHelp(@NotNull CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== NatsBridge Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/nats status" + ChatColor.WHITE + " - Show NATS connection status");
        sender.sendMessage(ChatColor.YELLOW + "/nats test <subject> <message>" + ChatColor.WHITE + " - Send a test message");
        sender.sendMessage(ChatColor.YELLOW + "/nats rescan" + ChatColor.WHITE + " - Rescan all plugins for NATS subscriptions");
        sender.sendMessage(ChatColor.YELLOW + "/nats reload" + ChatColor.WHITE + " - Reload NATS configuration");
    }

    private void showStatus(@NotNull CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== NATS Status ===");

        boolean initialized = natsBridge.isInitialized();
        boolean connected = natsBridge.isConnected();
        String status = natsBridge.getConnectionStatus();

        sender.sendMessage(ChatColor.YELLOW + "Initialized: " +
                (initialized ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Connected: " +
                (connected ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE + status);

        // Informations sur la configuration
        sender.sendMessage(ChatColor.YELLOW + "Servers: " + ChatColor.WHITE +
                String.join(", ", natsBridge.getConfig().getServers()));

        if (natsBridge.getConfig().getAuth() != null && natsBridge.getConfig().getAuth().isEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Authentication: " + ChatColor.GREEN + "Enabled");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Authentication: " + ChatColor.RED + "Disabled");
        }

        if (natsBridge.getConfig().getTls() != null && natsBridge.getConfig().getTls().isEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "TLS: " + ChatColor.GREEN + "Enabled");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "TLS: " + ChatColor.RED + "Disabled");
        }
    }

    private void testPublish(@NotNull CommandSender sender, @NotNull String subject, @NotNull String message) {
        if (!natsBridge.isConnected()) {
            sender.sendMessage(ChatColor.RED + "NATS is not connected. Cannot send test message.");
            return;
        }

        try {
            natsBridge.getAPI().publishString(subject, message);
            sender.sendMessage(ChatColor.GREEN + "Test message sent successfully!");
            sender.sendMessage(ChatColor.GRAY + "Subject: " + subject);
            sender.sendMessage(ChatColor.GRAY + "Message: " + message);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to send test message: " + e.getMessage());
            plugin.getLogger().warning("Test message failed: " + e.getMessage());
        }
    }

    private void rescanPlugins(@NotNull CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Rescanning all plugins for NATS subscriptions...");

        try {
            BungeeCordPluginScanner scanner = new BungeeCordPluginScanner(plugin, natsBridge);
            scanner.scanAllPlugins();
            sender.sendMessage(ChatColor.GREEN + "Plugin rescan completed successfully!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to rescan plugins: " + e.getMessage());
            plugin.getLogger().warning("Plugin rescan failed: " + e.getMessage());
        }
    }

    private void reloadConfig(@NotNull CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Configuration reload is not supported yet.");
        sender.sendMessage(ChatColor.YELLOW + "Please restart the proxy to apply configuration changes.");
    }
}
