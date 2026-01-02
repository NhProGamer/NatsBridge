# NatsBridge Usage Examples

This document provides practical examples of using the high-performance Consumer-based API.

## Example: Using Consumer API (Spigot)

```java
package com.example.myplugin;

import fr.nhsoul.natsbridge.common.api.NatsAPI;
import fr.nhsoul.natsbridge.spigot.SpigotNatsPlugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.charset.StandardCharsets;

public class MySpigotPlugin extends JavaPlugin {

    private NatsAPI natsAPI;
    
    @Override
    public void onEnable() {
        // Access the NATS API from the bridge plugin
        natsAPI = SpigotNatsPlugin.getNatsAPI();
        
        // Register consumers
        registerConsumers();
    }

    private void registerConsumers() {
        // Sync consumer for player data (directly on the NATS thread)
        natsAPI.subscribeSubject("player.data.update", this::handlePlayerDataUpdate, false);
        
        // Async consumer for analytics (runs in a separate internal thread pool)
        natsAPI.subscribeSubject("analytics.events", this::handleAnalyticsEvent, true);
        
        // Alternative: String consumer for convenience
        natsAPI.subscribeStringSubject("player.chat", chatMessage -> {
            // Directly receive as String - no manual conversion needed
            getServer().broadcastMessage("§7[NATS] " + chatMessage);
        }, false);
    }

    private void handlePlayerDataUpdate(byte[] data) {
        String jsonData = new String(data, StandardCharsets.UTF_8);
        // Process message...
    }

    private void handleAnalyticsEvent(byte[] data) {
        // Process analytics...
    }
}
```

## Example: Publishing Messages

```java
// String publishing
natsAPI.publishString("game.broadcast", "Hello from this server!");

// Raw publishing
byte[] data = ...;
natsAPI.publishRaw("game.data", data);
```

## Best Practices

1. **Consider async processing** for:
   - Database operations
   - Network calls
   - Heavy computations
   - Non-critical updates

2. **Use sync processing** for:
   - Real-time game events requiring order guarantee
   - Low-latency requirements

3. **Check connection status** before publishing:
```java
if (NatsBridge.getInstance().isConnected()) {
    // Publish...
}
```