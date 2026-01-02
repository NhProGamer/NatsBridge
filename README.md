# NatsBridge

**NatsBridge** is a Java library that connects your **Spigot**, **Velocity**, or **BungeeCord** plugins to a **NATS** server — easily and efficiently.

## 🚀 Features

* ✅ Shared NATS **connection** across plugins
* ✅ Supports **Spigot/Paper**, **Velocity**, and **BungeeCord**
* ✅ **High-performance Consumer API** for message handling
* ✅ Clean API to **publish** messages
* ✅ **Auto-reconnect** & error handling
* ✅ **YAML configuration**
* ✅ TLS & authentication support
* ✅ Sync & async message handling

## 📦 Setup

1. **Download** the JAR:

2. Drop it into your server’s `/plugins` folder.

3. Start the server. A `nats-config.yml` file will be generated.

4. Edit the config file and restart.

## ⚙️ Usage Example

### Using high-performance Consumer API

```java
// Sync consumer
NatsAPI api = BungeeCordNatsPlugin.getNatsAPI();
api.subscribeSubject("game.player.join", message -> {
    String playerName = new String(message, StandardCharsets.UTF_8);
    System.out.println("Player joined: " + playerName);
}, false);

// Async consumer (byte[])
api.subscribeSubject("game.chat", message -> {
    // Process chat message asynchronously
    String chatMessage = new String(message, StandardCharsets.UTF_8);
    broadcastToAllServers(chatMessage);
}, true);

// Async consumer (String) - more convenient!
api.subscribeStringSubject("game.chat", chatMessage -> {
    // Directly receive as String - no need for manual conversion
    broadcastToAllServers(chatMessage);
}, true);
```

### Publish a message

Firstly you need to know when the connection is established.
There are 3 events for Velocity, Bungeecord and Spigot.
- VelocityNatsBridgeConnectedEvent
- BungeeNatsBridgeConnectedEvent
- SpigotNatsBridgeConnectedEvent

Something like
```java
@EventHandler
public void onNatsBridgeConnected(SpigotNatsBridgeConnectedEvent event) {
    //Do something here
}
```

You just need to listen these events and the event is fire when the connection to NATS is up.

```java
BungeeCordNatsPlugin.getNatsAPI().publishString("subject", "Awesome message");
BungeeCordNatsPlugin.getNatsAPI().publishRaw("subject", [something that is byte[]]);
```


## 📂 Configuration (`nats-config.yml`)

```yaml
nats:
  # List of NATS servers (can be a single server or a cluster)
  servers:
    - "nats://127.0.0.1:4222"
    - "nats://nats-cluster.local:4222"

  # Authentication configuration (optional)
  auth:
    enabled: true
    # Username/password authentication
    username: "user"
    password: "pass"
    # OR token authentication (if provided, username/password are ignored)
    # token: "your_token_here"

  # TLS configuration (optional)
  tls:
    enabled: false
    # Paths to keystores (optional)
    # keystore: "/path/to/keystore.jks"
    # keystore_password: "keystore_password"
    # truststore: "/path/to/truststore.jks"
    # truststore_password: "truststore_password"

  # Reconnection configuration
  reconnect:
    # Maximum number of reconnection attempts (-1 = unlimited)
    max_reconnects: -1
    # Delay between reconnection attempts (in milliseconds)
    reconnect_wait: 2000
    # Connection timeout (in milliseconds)
    connection_timeout: 5000
```

## 🔧 Commands

* `/nats help` – Show help menu
* `/nats status` – Check NATS connection status
* `/nats test <subject> <message>` – Send a test message
* `/nats reload` – Reload NATS configuration (planned)

Permission required: `natsbridge.admin`

## 🧩 Gradle

```gradle
repositories {
    maven {
        name = "natsbridge-repo"
        url = uri("https://repo.nhsoul.fr/releases")
    }
}
dependencies {
    //Use the latest version
    
    // Mandatory
    compileOnly("fr.nhsoul.natsbridge:core:1.0.0")
    compileOnly("fr.nhsoul.natsbridge:common:1.0.0")
    
    //Select your platform
    compileOnly("fr.nhsoul.natsbridge:spigot:1.0.0")
    compileOnly("fr.nhsoul.natsbridge:velocity:1.0.0")
    compileOnly("fr.nhsoul.natsbridge:bungeecord:1.0.0")
}
```

## 🧩 Performance Considerations

The Consumer API approach offers several advantages:

1. **No reflection overhead**: Native performance for message handling.
2. **Type safety**: Use of standard Java interfaces.
3. **Explicit control**: You control exactly when and how messages are processed.
4. **Platform integration**: Easy access to platform-specific APIs (Spigot, Velocity, BungeeCord).

## ✅ Requirements

* Java 21+
* A NATS server
* Minecraft 1.20+ server (Spigot, Velocity, or BungeeCord)

## 🤝 Contributing

1. Fork this repo
2. Create a branch
3. Submit a PR – all contributions welcome!
