package fr.epistudio.epysia.net.session;

import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.net.diagnostics.NetworkStats;
import fr.epistudio.epysia.net.protocol.MalformedPacketException;
import fr.epistudio.epysia.net.protocol.MessageType;
import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;
import fr.epistudio.epysia.net.security.ConnectionSecurity;
import fr.epistudio.epysia.net.security.MessageAuthenticationException;
import fr.epistudio.epysia.net.transport.LatencySimulator;
import fr.epistudio.epysia.net.transport.LoopbackTransport;
import fr.epistudio.epysia.net.transport.SteamTransport;
import fr.epistudio.epysia.net.transport.NetChannel;
import fr.epistudio.epysia.net.transport.Transport;
import fr.epistudio.epysia.net.transport.TransportListener;
import fr.epistudio.epysia.net.transport.UdpTransport;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;

public final class NetworkSession implements TransportListener {
    private static final int LISTEN_SERVER_LOCAL_PEER = 1;
    private static final int NO_CONNECTION = -1;
    private static final int HARD_RESYNC_TICKS = 90;

    private final NetworkStats stats;
    private final Logger logger;
    private final Map<Integer, NetworkPeer> peersById = new LinkedHashMap<>();
    private final Map<Integer, String> receivedRoster = new LinkedHashMap<>();
    private final Map<Integer, Integer> peerIdByConnection = new LinkedHashMap<>();
    private NetworkEvents events = new NoNetworkEvents();
    private NetworkConfig config = new NetworkConfig();
    private SessionIdentity identity = SessionIdentity.of(0, 0, "", "player", "");
    private NetworkRole role = NetworkRole.OFFLINE;
    private Transport transport = new LoopbackTransport();
    private int localPeer = PeerIds.NONE;
    private int serverConnection = NO_CONNECTION;
    private int nextPeerId = LISTEN_SERVER_LOCAL_PEER;
    private int tick;
    private boolean transportOpen;
    private boolean polling;
    private Optional<DisconnectReason> deferredStop = Optional.empty();
    private float secondsSinceServerPacket;
    private int pendingTickAdjustment;
    private long packetArrivalNanos;
    private NetWriter scratchWriter = NetWriter.allocate(new NetworkConfig().snapshotByteCeiling()
            + new NetworkConfig().transmissionUnit());
    private boolean writerBusy;
    private final Map<Integer, ConnectionBudget> budgetByConnection = new LinkedHashMap<>();
    private final Map<Integer, Float> handshakeAgeByConnection = new LinkedHashMap<>();
    private final Set<String> bannedAddresses = new LinkedHashSet<>();
    private final Map<Integer, ConnectionSecurity> securityByConnection = new LinkedHashMap<>();
    private final Map<Integer, SessionIdentity> pendingIdentities = new LinkedHashMap<>();
    private final ReconnectRegistry reconnects = new ReconnectRegistry();
    private long localReconnectToken;

    public NetworkSession(NetworkStats stats, Logger logger) {
        this.stats = stats;
        this.logger = logger;
    }

    public void setEvents(NetworkEvents value) {
        this.events = value == null ? new NoNetworkEvents() : value;
    }

    public NetworkRole role() {
        return role;
    }

    public NetworkConfig config() {
        return config;
    }

    public int localPeer() {
        return localPeer;
    }

    public int tick() {
        return tick;
    }

    public void advanceTick() {
        tick++;
    }

    public void synchroniseTick(int desiredTick) {
        int drift = desiredTick - tick;
        if (Math.abs(drift) >= HARD_RESYNC_TICKS) {
            logger.info("[net] tick resynchronised by " + drift + " to " + desiredTick);
            tick = desiredTick;
            pendingTickAdjustment = 0;
            events.onTickResynchronised();
            return;
        }
        pendingTickAdjustment = drift;
    }

    public int pendingTickAdjustment() {
        return pendingTickAdjustment;
    }

    public void consumeTickAdjustment(int appliedSteps) {
        pendingTickAdjustment -= appliedSteps;
    }

    public Map<Integer, String> roster() {
        return role.isServer() ? serverRoster() : Map.copyOf(receivedRoster);
    }

    private Map<Integer, String> serverRoster() {
        Map<Integer, String> names = new LinkedHashMap<>();
        if (role.isClient()) {
            names.put(localPeer, config.displayName());
        }
        for (NetworkPeer peer : peersById.values()) {
            if (peer.handshakeComplete()) {
                names.put(peer.id(), peer.displayName());
            }
        }
        return names;
    }

    private void broadcastRoster() {
        if (!role.isServer()) {
            return;
        }
        Map<Integer, String> names = serverRoster();
        for (NetworkPeer peer : List.copyOf(peersById.values())) {
            if (peer.handshakeComplete()) {
                send(peer.connection(), NetChannel.RELIABLE, writer -> writeRoster(writer, names));
            }
        }
    }

    private static void writeRoster(NetWriter writer, Map<Integer, String> names) {
        writer.writeMessageType(MessageType.PEER_ROSTER);
        writer.writeVarInt(names.size());
        for (Map.Entry<Integer, String> entry : names.entrySet()) {
            writer.writeVarInt(entry.getKey());
            writer.writeString(entry.getValue());
        }
    }

    private void readRoster(NetReader reader) {
        int count = reader.requireCount(reader.readVarInt(), 1);
        receivedRoster.clear();
        for (int index = 0; index < count; index++) {
            int peerId = reader.readVarInt();
            receivedRoster.put(peerId, reader.readString());
        }
    }

    public List<NetworkPeer> peers() {
        return List.copyOf(peersById.values());
    }

    public Optional<NetworkPeer> peer(int peerId) {
        return Optional.ofNullable(peersById.get(peerId));
    }

    public void startServer(NetworkConfig configuration, SessionIdentity sessionIdentity, boolean listenServer) {
        stop();
        this.config = configuration;
        this.identity = sessionIdentity;
        this.transport = createTransport(configuration);
        resizeScratchWriter();
        transport.listen(configuration.port());
        transportOpen = true;
        this.role = listenServer ? NetworkRole.LISTEN_SERVER : NetworkRole.SERVER;
        this.localPeer = listenServer ? nextPeerId++ : PeerIds.SERVER;
        logger.info("[net] " + role + " listening on port " + configuration.port());
        events.onSessionStarted(role);
    }

    public void connect(NetworkConfig configuration, SessionIdentity sessionIdentity, String host, int port) {
        stop();
        this.config = configuration;
        this.identity = sessionIdentity;
        this.transport = createTransport(configuration);
        resizeScratchWriter();
        this.serverConnection = transport.connect(host, port);
        transportOpen = true;
        this.role = NetworkRole.CLIENT;
        sendConnectRequest();
        logger.info("[net] connecting to " + host + ":" + port);
        events.onSessionStarted(role);
    }

    private void resizeScratchWriter() {
        scratchWriter = NetWriter.allocate(writerCapacity());
        writerBusy = false;
    }

    private Transport createTransport(NetworkConfig configuration) {
        Transport base = baseTransport(configuration);
        if (!configuration.simulationEnabled()) {
            return base;
        }
        return new LatencySimulator(base, configuration.latencySeed()).configure(
                configuration.simulatedLatencySeconds(),
                configuration.simulatedJitterSeconds(),
                configuration.simulatedLossProbability());
    }

    private static Transport baseTransport(NetworkConfig configuration) {
        return switch (configuration.transport()) {
            case LOOPBACK -> new LoopbackTransport();
            case STEAM -> new SteamTransport();
            case UDP -> new UdpTransport(configuration.transmissionUnit());
        };
    }

    public void stop() {
        stop(DisconnectReason.REQUESTED);
    }

    public void stop(DisconnectReason reason) {
        if (!transportOpen) {
            return;
        }
        if (polling) {
            deferredStop = Optional.of(reason);
            return;
        }
        closeNow(reason);
    }

    private void closeNow(DisconnectReason reason) {
        broadcastDisconnect(reason);
        transport.close();
        peersById.clear();
        receivedRoster.clear();
        peerIdByConnection.clear();
        budgetByConnection.clear();
        handshakeAgeByConnection.clear();
        securityByConnection.clear();
        pendingIdentities.clear();
        reconnects.clear();
        localReconnectToken = 0L;
        transportOpen = false;
        role = NetworkRole.OFFLINE;
        localPeer = PeerIds.NONE;
        serverConnection = NO_CONNECTION;
        secondsSinceServerPacket = 0.0f;
        pendingTickAdjustment = 0;
        tick = 0;
        events.onSessionStopped();
    }

    private void broadcastDisconnect(DisconnectReason reason) {
        for (NetworkPeer peer : List.copyOf(peersById.values())) {
            send(peer.connection(), NetChannel.RELIABLE, writer -> {
                writer.writeMessageType(MessageType.DISCONNECT);
                writer.writeByte(reason.ordinal());
            });
        }
        if (role == NetworkRole.CLIENT && serverConnection != NO_CONNECTION) {
            send(serverConnection, NetChannel.RELIABLE, writer -> {
                writer.writeMessageType(MessageType.DISCONNECT);
                writer.writeByte(reason.ordinal());
            });
        }
    }

    public void poll(float deltaTimeSeconds) {
        if (!transportOpen) {
            return;
        }
        polling = true;
        try {
            transport.poll(this, deltaTimeSeconds);
            expireTimedOutPeers(deltaTimeSeconds);
            expireServerIfSilent(deltaTimeSeconds);
            advanceBudgets(deltaTimeSeconds);
            expireReconnectGrace(deltaTimeSeconds);
            expireStalledHandshakes(deltaTimeSeconds);
            stats.advance(deltaTimeSeconds);
        } finally {
            polling = false;
        }
        applyDeferredStop();
    }

    private void advanceBudgets(float deltaTimeSeconds) {
        for (Map.Entry<Integer, ConnectionBudget> entry : Map.copyOf(budgetByConnection).entrySet()) {
            entry.getValue().advance(deltaTimeSeconds);
            if (entry.getValue().isAbusive()) {
                dropConnection(entry.getKey(), DisconnectReason.RATE_LIMIT_EXCEEDED);
            }
        }
    }

    private void parkIfReclaimable(NetworkPeer peer, DisconnectReason reason) {
        if (reason == DisconnectReason.KICKED || reason == DisconnectReason.BANNED) {
            return;
        }
        reconnects.park(peer.reconnectToken(), peer.id(), peer.displayName(),
                events.ownedObjectsOf(peer.id()), config.reconnectGraceSeconds());
    }

    private void expireReconnectGrace(float deltaTimeSeconds) {
        for (ReconnectRegistry.ParkedPeer expired : reconnects.expire(deltaTimeSeconds)) {
            events.onReconnectWindowClosed(expired.peerId(), expired.ownedObjects());
        }
    }

    private void expireStalledHandshakes(float deltaTimeSeconds) {
        if (!role.isServer()) {
            return;
        }
        for (Map.Entry<Integer, Float> entry : Map.copyOf(handshakeAgeByConnection).entrySet()) {
            float age = entry.getValue() + deltaTimeSeconds;
            handshakeAgeByConnection.put(entry.getKey(), age);
            if (age >= config.handshakeTimeoutSeconds()) {
                dropConnection(entry.getKey(), DisconnectReason.HANDSHAKE_TIMEOUT);
            }
        }
    }

    private void dropConnection(int connection, DisconnectReason reason) {
        Integer peerId = peerIdByConnection.get(connection);
        if (peerId != null) {
            peer(peerId).ifPresent(peer -> removePeer(peer, reason));
            return;
        }
        handshakeAgeByConnection.remove(connection);
        budgetByConnection.remove(connection);
        transport.disconnect(connection);
        logger.warn("[net] dropped an unauthenticated connection: " + reason);
    }

    public void kick(int peerId, DisconnectReason reason) {
        peer(peerId).ifPresent(peer -> {
            send(peer.connection(), NetChannel.RELIABLE, writer -> {
                writer.writeMessageType(MessageType.DISCONNECT);
                writer.writeByte(reason.ordinal());
            });
            removePeer(peer, reason);
        });
    }

    public void ban(int peerId) {
        peer(peerId).ifPresent(peer -> {
            bannedAddresses.add(transport.addressOf(peer.connection()));
            kick(peerId, DisconnectReason.BANNED);
        });
    }

    public Set<String> bannedAddresses() {
        return Set.copyOf(bannedAddresses);
    }

    public void unban(String address) {
        bannedAddresses.remove(address);
    }

    private void applyDeferredStop() {
        Optional<DisconnectReason> reason = deferredStop;
        deferredStop = Optional.empty();
        reason.ifPresent(this::closeNow);
    }

    private void expireServerIfSilent(float deltaTimeSeconds) {
        if (role != NetworkRole.CLIENT) {
            return;
        }
        secondsSinceServerPacket += deltaTimeSeconds;
        if (secondsSinceServerPacket >= config.timeoutSeconds()) {
            logger.warn("[net] the server went silent for " + config.timeoutSeconds() + " s");
            stop(DisconnectReason.TIMEOUT);
        }
    }

    private void expireTimedOutPeers(float deltaTimeSeconds) {
        for (NetworkPeer peer : List.copyOf(peersById.values())) {
            if (peer.hasTimedOut(deltaTimeSeconds, config.timeoutSeconds())) {
                removePeer(peer, DisconnectReason.TIMEOUT);
            }
        }
    }

    public long localReconnectToken() {
        return localReconnectToken;
    }

    public ReconnectRegistry reconnects() {
        return reconnects;
    }

    private void removePeer(NetworkPeer peer, DisconnectReason reason) {
        parkIfReclaimable(peer, reason);
        peersById.remove(peer.id());
        peerIdByConnection.remove(peer.connection());
        budgetByConnection.remove(peer.connection());
        handshakeAgeByConnection.remove(peer.connection());
        securityByConnection.remove(peer.connection());
        pendingIdentities.remove(peer.connection());
        transport.disconnect(peer.connection());
        stats.forgetPeer(peer.id());
        logger.info("[net] peer " + peer.id() + " left: " + reason);
        events.onPeerLeft(peer, reason);
        broadcastRoster();
    }

    public void send(int connection, NetChannel channel, Consumer<NetWriter> body) {
        writeThenSend(connection, body, written -> channel);
    }

    public void sendChoosingChannel(int connection, Consumer<NetWriter> body) {
        writeThenSend(connection, body,
                written -> written > config.transmissionUnit() ? NetChannel.RELIABLE : NetChannel.UNRELIABLE);
    }

    private void writeThenSend(int connection, Consumer<NetWriter> body, IntFunction<NetChannel> channelChoice) {
        if (!transportOpen || connection == NO_CONNECTION) {
            return;
        }
        NetWriter writer = acquireWriter();
        try {
            body.accept(writer);
            transmit(connection, channelChoice.apply(writer.position()), writer);
        } catch (BufferOverflowException overflow) {
            stats.recordOversizedMessage();
            logger.warn("[net] dropped a message larger than the " + writerCapacity() + " byte send buffer");
        } finally {
            releaseWriter(writer);
        }
    }

    private void transmit(int connection, NetChannel channel, NetWriter writer) {
        ByteBuffer payload = sealIfEstablished(connection, writer.flipped());
        stats.recordSent(channel, payload.remaining());
        transport.send(connection, channel, payload);
    }

    private ByteBuffer sealIfEstablished(int connection, ByteBuffer payload) {
        ConnectionSecurity security = securityByConnection.get(connection);
        if (security == null || !security.established()) {
            return payload;
        }
        byte[] plaintext = new byte[payload.remaining()];
        payload.duplicate().get(plaintext);
        return ByteBuffer.wrap(security.seal(plaintext));
    }

    private NetWriter acquireWriter() {
        if (writerBusy) {
            return NetWriter.allocate(writerCapacity());
        }
        writerBusy = true;
        return scratchWriter.reset();
    }

    private void releaseWriter(NetWriter writer) {
        if (writer == scratchWriter) {
            writerBusy = false;
        }
    }

    private int writerCapacity() {
        return config.snapshotByteCeiling() + config.transmissionUnit();
    }

    public void sendToPeer(int peerId, NetChannel channel, Consumer<NetWriter> body) {
        peer(peerId).ifPresent(peer -> send(peer.connection(), channel, body));
    }

    public void sendSnapshotToPeer(int peerId, Consumer<NetWriter> body) {
        peer(peerId).ifPresent(peer -> sendChoosingChannel(peer.connection(), body));
    }

    public void sendToServer(NetChannel channel, Consumer<NetWriter> body) {
        send(serverConnection, channel, body);
    }

    public void broadcastToPeers(NetChannel channel, Consumer<NetWriter> body) {
        for (NetworkPeer peer : peersById.values()) {
            if (peer.handshakeComplete()) {
                send(peer.connection(), channel, body);
            }
        }
    }

    @Override
    public void onConnectionOpened(int connection) {
        if (!role.isServer()) {
            return;
        }
        if (bannedAddresses.contains(transport.addressOf(connection))) {
            transport.disconnect(connection);
            return;
        }
        handshakeAgeByConnection.put(connection, 0.0f);
    }

    @Override
    public void onConnectionClosed(int connection) {
        Integer peerId = peerIdByConnection.get(connection);
        if (peerId != null) {
            peer(peerId).ifPresent(peer -> removePeer(peer, DisconnectReason.TRANSPORT_CLOSED));
            return;
        }
        if (connection == serverConnection) {
            stop();
        }
    }

    @Override
    public void onPacketReceived(int connection, NetChannel channel, NetReader reader, long arrivalNanos) {
        packetArrivalNanos = arrivalNanos;
        onPacketReceived(connection, channel, reader);
    }

    @Override
    public void onPacketReceived(int connection, NetChannel channel, NetReader reader) {
        int byteCount = reader.remaining();
        stats.recordReceived(channel, byteCount);
        if (!budgetFor(connection).accept(byteCount)) {
            stats.recordRateLimitedPacket();
            return;
        }
        try {
            openIfEstablished(connection, reader).ifPresent(opened -> dispatch(connection, opened));
        } catch (MalformedPacketException malformed) {
            stats.recordMalformedPacket();
            logger.warn("[net] dropped a malformed packet: " + malformed.getMessage());
        } catch (MessageAuthenticationException rejected) {
            stats.recordRejectedPacket();
            logger.warn("[net] dropped an unauthentic packet: " + rejected.getMessage());
        }
    }

    private Optional<NetReader> openIfEstablished(int connection, NetReader reader) {
        ConnectionSecurity security = securityByConnection.get(connection);
        if (security == null || !security.established()) {
            return Optional.of(reader);
        }
        byte[] framed = new byte[reader.remaining()];
        reader.buffer().get(framed);
        Optional<byte[]> opened = security.open(framed);
        if (opened.isEmpty()) {
            stats.recordRejectedPacket();
            return Optional.empty();
        }
        return Optional.of(NetReader.wrapping(opened.get(), 0, opened.get().length));
    }

    private ConnectionBudget budgetFor(int connection) {
        return budgetByConnection.computeIfAbsent(connection, ignored ->
                new ConnectionBudget(config.maximumPacketsPerSecond(), config.maximumBytesPerSecond()));
    }

    private void dispatch(int connection, NetReader reader) {
        Optional<MessageType> type = reader.readMessageType();
        if (type.isEmpty()) {
            stats.recordUnknownMessage();
            return;
        }
        noteTrafficFrom(connection);
        if (role.isServer()) {
            handleServerMessage(connection, type.get(), reader);
            return;
        }
        handleClientMessage(type.get(), reader);
    }

    private void noteTrafficFrom(int connection) {
        if (connection == serverConnection) {
            secondsSinceServerPacket = 0.0f;
        }
        Integer peerId = peerIdByConnection.get(connection);
        if (peerId != null) {
            peer(peerId).ifPresent(NetworkPeer::noteTraffic);
        }
    }

    private void handleServerMessage(int connection, MessageType type, NetReader reader) {
        switch (type) {
            case CONNECT -> acceptOrRefuse(connection, reader);
            case CONNECT_CONFIRM -> confirmAndAdmit(connection, reader);
            case INPUT_BATCH -> peerOf(connection).ifPresent(peer -> events.onInputBatchReceived(peer, reader));
            case ACK -> peerOf(connection).ifPresent(peer -> peer.acknowledgeSnapshot(reader.readInt()));
            case RPC -> peerOf(connection).ifPresent(peer ->
                    events.onRemoteProcedureCallReceived(peer.id(), reader));
            case VOICE -> peerOf(connection).ifPresent(peer -> events.onVoiceFrameReceived(peer.id(), reader));
            case DISCONNECT -> peerOf(connection).ifPresent(peer -> removePeer(peer, readReason(reader)));
            case HEARTBEAT -> replyToHeartbeat(connection, reader);
            default -> stats.recordUnknownMessage();
        }
    }

    private void handleClientMessage(MessageType type, NetReader reader) {
        switch (type) {
            case CONNECT_CHALLENGE -> answerChallenge(reader);
            case CONNECT_ACCEPTED -> acceptAssignment(reader);
            case CONNECT_REFUSED -> refuseLocally(readReason(reader));
            case SNAPSHOT -> events.onSnapshotReceived(reader);
            case SPAWN -> events.onSpawnReceived(reader);
            case DESPAWN -> events.onDespawnReceived(reader);
            case RPC -> events.onRemoteProcedureCallReceived(PeerIds.SERVER, reader);
            case VOICE -> events.onVoiceFrameReceived(PeerIds.SERVER, reader);
            case DISCONNECT -> refuseLocally(readReason(reader));
            case HEARTBEAT -> replyToHeartbeat(serverConnection, reader);
            case PEER_ROSTER -> readRoster(reader);
            default -> stats.recordUnknownMessage();
        }
    }

    private static DisconnectReason readReason(NetReader reader) {
        return DisconnectReason.fromOrdinal(reader.readByte()).orElse(DisconnectReason.REQUESTED);
    }

    private Optional<NetworkPeer> peerOf(int connection) {
        Integer peerId = peerIdByConnection.get(connection);
        return peerId == null ? Optional.empty() : peer(peerId);
    }

    private void acceptOrRefuse(int connection, NetReader reader) {
        SessionIdentity remote = SessionIdentity.read(reader);
        Optional<DisconnectReason> incompatible = identity.incompatibilityWith(remote);
        if (incompatible.isPresent()) {
            refuse(connection, incompatible.get());
            return;
        }
        if (peersById.size() >= config.maximumPeers()) {
            refuse(connection, DisconnectReason.SERVER_FULL);
            return;
        }
        challenge(connection, remote, reader);
    }

    private void challenge(int connection, SessionIdentity remote, NetReader reader) {
        ConnectionSecurity security = new ConnectionSecurity(config.joinSecret());
        security.acceptRemoteHello(reader.readSizedBytes(), reader.readSizedBytes());
        securityByConnection.put(connection, security);
        pendingIdentities.put(connection, remote);
        byte[] tag = security.tagFor(true, false);
        send(connection, NetChannel.RELIABLE, writer -> {
            writer.writeMessageType(MessageType.CONNECT_CHALLENGE);
            writer.writeSizedBytes(security.localPublicKey(), 0, security.localPublicKey().length);
            writer.writeSizedBytes(security.localNonce(), 0, security.localNonce().length);
            writer.writeSizedBytes(tag, 0, tag.length);
        });
    }

    private void answerChallenge(NetReader reader) {
        ConnectionSecurity security = securityByConnection.get(serverConnection);
        if (security == null) {
            return;
        }
        security.acceptRemoteHello(reader.readSizedBytes(), reader.readSizedBytes());
        if (!security.verifyTag(reader.readSizedBytes(), true, true)) {
            logger.warn("[net] the server failed to prove it knows the join secret");
            stop(DisconnectReason.JOIN_SECRET_MISMATCH);
            return;
        }
        byte[] tag = security.tagFor(false, true);
        send(serverConnection, NetChannel.RELIABLE, writer -> {
            writer.writeMessageType(MessageType.CONNECT_CONFIRM);
            writer.writeSizedBytes(tag, 0, tag.length);
        });
        security.establish(true);
    }

    private void confirmAndAdmit(int connection, NetReader reader) {
        ConnectionSecurity security = securityByConnection.get(connection);
        SessionIdentity remote = pendingIdentities.remove(connection);
        if (security == null || remote == null) {
            return;
        }
        if (!security.verifyTag(reader.readSizedBytes(), false, false)) {
            refuse(connection, DisconnectReason.JOIN_SECRET_MISMATCH);
            return;
        }
        security.establish(false);
        admit(connection, remote);
    }

    private void refuse(int connection, DisconnectReason reason) {
        handshakeAgeByConnection.remove(connection);
        pendingIdentities.remove(connection);
        securityByConnection.remove(connection);
        send(connection, NetChannel.RELIABLE, writer -> {
            writer.writeMessageType(MessageType.CONNECT_REFUSED);
            writer.writeByte(reason.ordinal());
        });
        transport.disconnect(connection);
        logger.warn("[net] refused a connection: " + reason);
    }

    private void admit(int connection, SessionIdentity remote) {
        handshakeAgeByConnection.remove(connection);
        Optional<ReconnectRegistry.ParkedPeer> returning = reconnects.claim(remote.reconnectToken());
        NetworkPeer peer = new NetworkPeer(returning.map(ReconnectRegistry.ParkedPeer::peerId)
                .orElseGet(() -> nextPeerId++), connection);
        peer.setDisplayName(remote.displayName());
        peer.markHandshakeComplete();
        peersById.put(peer.id(), peer);
        peerIdByConnection.put(connection, peer.id());
        long token = reconnects.issueToken();
        peer.setReconnectToken(token);
        send(connection, NetChannel.RELIABLE, writer -> {
            writer.writeMessageType(MessageType.CONNECT_ACCEPTED);
            writer.writeVarInt(peer.id());
            writer.writeInt(tick);
            writer.writeLong(token);
        });
        logger.info("[net] peer " + peer.id() + " ("
                + peer.displayName() + (returning.isPresent() ? ") returned" : ") joined"));
        returning.ifPresentOrElse(parked -> events.onPeerReturned(peer, parked.ownedObjects()),
                () -> events.onPeerJoined(peer));
        broadcastRoster();
    }

    private void sendConnectRequest() {
        ConnectionSecurity security = new ConnectionSecurity(config.joinSecret());
        securityByConnection.put(serverConnection, security);
        send(serverConnection, NetChannel.RELIABLE, writer -> {
            writer.writeMessageType(MessageType.CONNECT);
            identity.write(writer);
            writer.writeSizedBytes(security.localPublicKey(), 0, security.localPublicKey().length);
            writer.writeSizedBytes(security.localNonce(), 0, security.localNonce().length);
        });
    }

    private void acceptAssignment(NetReader reader) {
        localPeer = reader.readVarInt();
        tick = reader.readInt();
        localReconnectToken = reader.readLong();
        logger.info("[net] joined as peer " + localPeer + " at server tick " + tick);
        events.onLocalPeerAssigned(localPeer, tick);
    }

    private void refuseLocally(DisconnectReason reason) {
        logger.warn("[net] the server closed the session: " + reason);
        stop();
    }

    private void replyToHeartbeat(int connection, NetReader reader) {
        boolean isReply = reader.readBoolean();
        long stamp = reader.readLong();
        if (isReply) {
            recordRoundTrip(connection, stamp, reader.readLong());
            return;
        }
        long heldNanos = Math.max(0L, System.nanoTime() - arrivalOfCurrentPacket());
        send(connection, NetChannel.UNRELIABLE, writer -> {
            writer.writeMessageType(MessageType.HEARTBEAT);
            writer.writeBoolean(true);
            writer.writeLong(stamp);
            writer.writeLong(heldNanos);
        });
    }

    private long arrivalOfCurrentPacket() {
        return packetArrivalNanos == 0L ? System.nanoTime() : packetArrivalNanos;
    }

    private void recordRoundTrip(int connection, long sentNanos, long peerHeldNanos) {
        long observed = arrivalOfCurrentPacket() - sentNanos;
        long networkOnly = Math.max(0L, observed - Math.max(0L, peerHeldNanos));
        int peerId = peerIdByConnection.getOrDefault(connection, PeerIds.SERVER);
        stats.latencyOf(peerId).sample(networkOnly / 1_000_000_000.0f);
    }

    public void sendHeartbeats() {
        Consumer<NetWriter> body = writer -> {
            writer.writeMessageType(MessageType.HEARTBEAT);
            writer.writeBoolean(false);
            writer.writeLong(System.nanoTime());
        };
        if (role.isServer()) {
            broadcastToPeers(NetChannel.UNRELIABLE, body);
            return;
        }
        sendToServer(NetChannel.UNRELIABLE, body);
    }

    private static final class NoNetworkEvents implements NetworkEvents {
        @Override
        public void onSessionStarted(NetworkRole role) {
        }

        @Override
        public void onSessionStopped() {
        }

        @Override
        public void onPeerJoined(NetworkPeer peer) {
        }

        @Override
        public void onPeerLeft(NetworkPeer peer, DisconnectReason reason) {
        }

        @Override
        public void onPeerReturned(NetworkPeer peer, List<Integer> ownedObjects) {
        }

        @Override
        public void onReconnectWindowClosed(int peerId, List<Integer> ownedObjects) {
        }

        @Override
        public List<Integer> ownedObjectsOf(int peerId) {
            return List.of();
        }

        @Override
        public void onLocalPeerAssigned(int peerId, int serverTick) {
        }

        @Override
        public void onSnapshotReceived(NetReader reader) {
        }

        @Override
        public void onSpawnReceived(NetReader reader) {
        }

        @Override
        public void onDespawnReceived(NetReader reader) {
        }

        @Override
        public void onInputBatchReceived(NetworkPeer peer, NetReader reader) {
        }

        @Override
        public void onRemoteProcedureCallReceived(int fromPeer, NetReader reader) {
        }

        @Override
        public void onVoiceFrameReceived(int fromPeer, NetReader reader) {
        }
    }
}
