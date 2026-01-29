# MQTT Plugin Implementer Notes

This document is for developers extending or integrating the Red5 MQTT plugin. It focuses on internal architecture, extension points, and configuration behavior as implemented in this module.

## Architecture Overview

The plugin embeds a Moquette-derived broker implementation (source is included under `org.eclipse.moquette.*`). Networking is handled by Apache MINA, and broker processing is serialized through a Disruptor ring buffer.

High-level flow:

1) Spring creates one or more `MQTTTransport` beans (listen sockets).
2) Spring creates `MQTTBroker`, which wires auth + persistence into `SimpleMessaging`.
3) `MQTTBroker` injects `SimpleMessaging` into every `MQTTTransport` handler.
4) MINA accepts MQTT connections, decodes protocol frames, and hands messages to `MQTTHandler`.
5) `MQTTHandler` forwards protocol messages to `SimpleMessaging`, which drives `ProtocolProcessor` via the Disruptor ring buffer.

Architecture sketch:

```ascii
MQTT Client
   |
   v
MQTTTransport (MINA acceptor + codec)
   |
   v
MQTTHandler (session -> MinaChannel)
   |
   v
SimpleMessaging --(Disruptor)--> ProtocolProcessor
   |                                   |
   |                                   v
   +--> SubscriptionsStore         Persistence (MapDB)
```

## Key Components (and where to extend)

- `org.red5.server.mqtt.MQTTBroker`
  - Spring entry point.
  - Builds `MapDBPersistentStore` and an `IAuthenticator`, then calls `SimpleMessaging.init()`.
  - Propagates the `SimpleMessaging` instance into all `MQTTTransport` handlers.

- `org.red5.server.mqtt.net.MQTTTransport`
  - Owns MINA `NioSocketAcceptor`, binds ports/addresses, and installs the MQTT codec.
  - Extension points: custom `IoFilter`s, buffer sizes, I/O threads, secure config.

- `org.red5.server.mqtt.net.MQTTHandler`
  - Translates MINA sessions into `MinaChannel` and forwards messages to `IMessaging`.
  - Extension points: protocol logging, session lifecycle hooks, metrics.

- `org.eclipse.moquette.spi.impl.SimpleMessaging`
  - Core broker logic entry point.
  - Manages `SubscriptionsStore`, `ProtocolProcessor`, persistence, and ring buffer.

- `org.eclipse.moquette.spi.impl.ProtocolProcessor`
  - Protocol state machine; handles CONNECT/SUBSCRIBE/PUBLISH, etc.

## Persistence (MapDB)

Persistence is handled by `MapDBPersistentStore`:

- In-memory store when `dbStorePath` is empty.
- File-backed store when `dbStorePath` points to a file.
- Retained messages, QoS in-flight state, and subscriptions are persisted.

Configuration:

- `MQTTBroker.dbStorePath` (defaults to `${user.home}/mqtt_store.mapdb`)

Notes:

- `MapDBPersistentStore.close()` commits and closes the DB; broker shutdown calls this.
- Values are serialized using MapDB's Java serializer.

## Authentication

Authentication is provided by `IAuthenticator`:

- If `passwdFileName` is empty, `AcceptAllAuthenticator` is used.
- If set, `FileAuthenticator` reads a username/password file from `${user.home}/${passwdFileName}`.
- File format is `username:password` per line; `#` starts a comment.

Extension ideas:

- Implement `IAuthenticator` for LDAP, OAuth, or database-backed auth.
- Replace the authenticator in `MQTTBroker` before `SimpleMessaging.init()`.

## Subscriptions and Matching

Subscriptions are stored in `SubscriptionsStore`:

- Supports MQTT wildcards `+` and `#`.
- Matching logic lives in `SubscriptionsStore.matchTopics` and `TreeNode.matches`.

Extension ideas:

- Add ACL checks in `SubscriptionsStore` (or in `ProtocolProcessor`) before storing a subscription.
- Persist additional metadata per subscription (needs schema changes in MapDB store).

## Threading Model

- MINA manages the network I/O threads.
- Broker logic is serialized through a Disruptor ring buffer in `SimpleMessaging` / `ProtocolProcessor`.
- The Disruptor thread uses the default Java `ThreadFactory`.

Implication:

- Protocol handling is single-threaded by design; avoid blocking operations inside `ProtocolProcessor`.

## Configuration Reference (Spring beans)

Minimal broker + transport:

```xml
<bean id="mqttTransport" class="org.red5.server.mqtt.net.MQTTTransport">
    <property name="port" value="1883"/>
</bean>

<bean id="mqttBroker" class="org.red5.server.mqtt.MQTTBroker" depends-on="mqttTransport">
    <property name="dbStorePath" value="/opt/red5/mqtt_store.mapdb"/>
    <property name="passwdFileName" value=""/>
</bean>
```

Secure transport:

```xml
<bean id="mqttTransportSecure" class="org.red5.server.mqtt.net.MQTTTransport">
    <property name="secureConfig">
        <bean id="mqttSecureConfig" class="org.red5.server.mqtt.SecureMQTTConfiguration">
            <property name="keystoreType" value="JKS"/>
            <property name="keystoreFile" value="conf/keystore"/>
            <property name="keystorePassword" value="password"/>
            <property name="truststoreFile" value="conf/truststore"/>
            <property name="truststorePassword" value="password"/>
        </bean>
    </property>
    <property name="addresses">
        <list>
            <value>0.0.0.0:8883</value>
        </list>
    </property>
</bean>
```

## Tests

Unit tests live under `mqtt/src/test/java`:

- `SubscriptionsStoreTest` covers topic wildcard matching.
- `MapDBPersistentStoreTest` exercises retained messages and session/QoS storage.

Note: Maven may be configured to skip tests in this environment. Use `mvn -DskipTests=false test` if needed.

## Known Limitations

- MQTT 5.0 is not supported; MQTT 3.1.1 only.
- Password authentication is plaintext (no hashing).
- No ACL layer is included; implementers should add authorization checks if required.
