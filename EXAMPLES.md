# NatsBridge Usage Examples

This document provides practical examples of using both the annotation-based and Consumer-based approaches.

## Example 1: Using @NatsSubscribe Annotation

```java
package com.example.myplugin;

import fr.nhsoul.natsbridge.common.annotation.NatsSubscribe;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Register this plugin for annotation scanning
        SpigotNatsPlugin.getInstance().registerPlugin(this);
    }

    // Simple string message handler
    @NatsSubscribe("game.player.join")
    public void onPlayerJoin(String playerName) {
        getLogger().info("Player joined via NATS: " + playerName);
        // Broadcast to all players
        getServer().broadcastMessage("§aWelcome " + playerName + "!");
    }

    // Binary message handler with async processing
    @NatsSubscribe(value = "game.chat.global", async = true)
    public void onGlobalChat(byte[] messageData) {
        String message = new String(messageData, StandardCharsets.UTF_8);
        
        // Process the chat message asynchronously
        String formattedMessage = formatChatMessage(message);
        
        // Send to all servers via NATS
        NatsAPI api = SpigotNatsPlugin.getNatsAPI();
        api.publishString("game.chat.broadcast", formattedMessage);
    }

    private String formatChatMessage(String rawMessage) {
        // Add server prefix, colors, etc.
        return "[Global] " + rawMessage;
    }
}
```

## Example 2: Using Consumer API

```java
package com.example.myplugin;

import fr.nhsoul.natsbridge.common.api.NatsAPI;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.charset.StandardCharsets;

public class HighPerformancePlugin extends JavaPlugin {

    private NatsAPI natsAPI;
    
    @Override
    public void onEnable() {
        natsAPI = SpigotNatsPlugin.getNatsAPI();
        
        // Register high-performance consumers
        registerConsumers();
    }

    private void registerConsumers() {
        // Sync consumer for player data
        natsAPI.subscribeSubject("player.data.update", this::handlePlayerDataUpdate, false);
        
        // Async consumer for analytics
        natsAPI.subscribeSubject("analytics.events", this::handleAnalyticsEvent, true);
        
        // Complex consumer with state
        natsAPI.subscribeSubject("game.state", this::handleGameState, false);
    }

    private void handlePlayerDataUpdate(byte[] data) {
        String jsonData = new String(data, StandardCharsets.UTF_8);
        PlayerData playerData = parsePlayerData(jsonData);
        
        // Update player data in database
        getDatabase().updatePlayerData(playerData);
    }

    private void handleAnalyticsEvent(byte[] data) {
        // Process analytics asynchronously
        AnalyticsEvent event = AnalyticsEvent.fromBytes(data);
        
        // Send to analytics service
        getAnalyticsService().trackEvent(event);
    }

    private void handleGameState(byte[] data) {
        GameState state = GameState.deserialize(data);
        
        // Update local game state
        getGameManager().updateState(state);
        
        // Broadcast to players
        broadcastGameStateUpdate(state);
    }

    @Override
    public void onDisable() {
        // Clean up - unsubscribe if needed
        // Note: In most cases, this is handled automatically
    }
}
```

## Example 3: Hybrid Approach

```java
package com.example.myplugin;

import fr.nhsoul.natsbridge.common.annotation.NatsSubscribe;
import fr.nhsoul.natsbridge.common.api.NatsAPI;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.charset.StandardCharsets;

public class HybridPlugin extends JavaPlugin {

    private NatsAPI natsAPI;
    private final Map<String, PlayerSession> playerSessions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        natsAPI = SpigotNatsPlugin.getNatsAPI();
        
        // Register for annotation scanning
        SpigotNatsPlugin.getInstance().registerPlugin(this);
        
        // Register high-performance consumers
        registerPerformanceCriticalConsumers();
    }

    // Annotation-based handler for simple messages
    @NatsSubscribe("player.session.start")
    public void onSessionStart(String playerId) {
        playerSessions.computeIfAbsent(playerId, id -> new PlayerSession(id));
    }

    // Annotation-based handler for async processing
    @NatsSubscribe(value = "player.session.end", async = true)
    public void onSessionEnd(String playerId) {
        PlayerSession session = playerSessions.remove(playerId);
        if (session != null) {
            saveSessionData(session);
        }
    }

    private void registerPerformanceCriticalConsumers() {
        // High-frequency game events
        natsAPI.subscribeSubject("game.tick", this::handleGameTick, false);
        
        // Real-time player position updates
        natsAPI.subscribeSubject("player.position", this::handlePositionUpdate, true);
    }

    private void handleGameTick(byte[] data) {
        GameTick tick = GameTick.deserialize(data);
        getGameEngine().processTick(tick);
    }

    private void handlePositionUpdate(byte[] data) {
        PlayerPosition position = PlayerPosition.deserialize(data);
        updatePlayerPosition(position);
    }

    // ... rest of the plugin code
}
```

## Performance Comparison

### Annotation Approach (@NatsSubscribe)

**Pros:**
- Simple and declarative
- Automatic registration
- Good for infrequent messages
- Easy to read and maintain

**Cons:**
- Reflection overhead (~10-15% slower)
- Less control over processing
- Runtime scanning (if not using annotation processor)

### Consumer API Approach

**Pros:**
- No reflection overhead
- Better performance for high-frequency messages
- More control over processing
- Type-safe lambda expressions

**Cons:**
- More verbose
- Manual registration required
- Slightly more complex

## Best Practices

1. **Use @NatsSubscribe for:**
   - Simple message handlers
   - Infrequent events
   - Rapid prototyping
   - Readability-focused code

2. **Use Consumer API for:**
   - High-frequency messages (>100/sec)
   - Performance-critical paths
   - Complex processing logic
   - State management

3. **Mix both approaches** as needed in your application

4. **Consider async processing** for:
   - Database operations
   - Network calls
   - Heavy computations
   - Non-critical updates

5. **Use sync processing** for:
   - Real-time game events
   - Player interactions
   - Critical state updates
   - Low-latency requirements