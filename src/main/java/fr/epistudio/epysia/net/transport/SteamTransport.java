package fr.epistudio.epysia.net.transport;

import com.codedisaster.steamworks.SteamException;
import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamNetworking;
import com.codedisaster.steamworks.SteamNetworkingCallback;
import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.steam.SteamIds;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SteamTransport implements Transport {

    private static final int PACKET_BUFFER_BYTES = 1 << 16;
    private static final int FIRST_CONNECTION = 1;

    private final SteamNetworking networking;
    private final Map<Long, Integer> connectionBySteamId = new HashMap<>();
    private final Map<Integer, Long> steamIdByConnection = new HashMap<>();
    private final List<Long> closedRemotely = new ArrayList<>();
    private final ByteBuffer sendBuffer = BufferUtils.createByteBuffer(PACKET_BUFFER_BYTES);
    private final ByteBuffer receiveBuffer = BufferUtils.createByteBuffer(PACKET_BUFFER_BYTES);
    private final SteamID scratchSender = new SteamID();
    private final int[] packetSize = new int[1];
    private boolean accepting;
    private int nextConnection = FIRST_CONNECTION;

    public SteamTransport() {
        this(true);
    }

    public SteamTransport(boolean relayAllowed) {
        networking = new SteamNetworking(new Callbacks());
        networking.allowP2PPacketRelay(relayAllowed);
    }

    @Override
    public void listen(int port) {
        accepting = true;
    }

    @Override
    public int connect(String host, int port) {
        SteamID remote = SteamIds.parse(host)
                .orElseThrow(() -> new TransportException("Not a Steam identifier: " + host));
        accepting = true;
        return connectionFor(SteamIds.rawOf(remote));
    }

    private int connectionFor(long steamId) {
        Integer existing = connectionBySteamId.get(steamId);
        if (existing != null) {
            return existing;
        }
        int connection = nextConnection++;
        connectionBySteamId.put(steamId, connection);
        steamIdByConnection.put(connection, steamId);
        return connection;
    }

    @Override
    public void send(int connection, NetChannel channel, ByteBuffer payload) {
        Long steamId = steamIdByConnection.get(connection);
        if (steamId == null || payload.remaining() > sendBuffer.capacity()) {
            return;
        }
        sendBuffer.clear();
        sendBuffer.put(payload.duplicate()).flip();
        try {
            networking.sendP2PPacket(SteamIds.of(steamId), sendBuffer, sendTypeOf(channel), channel.ordinal());
        } catch (SteamException refused) {
            throw new TransportException("Steam refused a packet: " + refused.getMessage());
        }
    }

    private static SteamNetworking.P2PSend sendTypeOf(NetChannel channel) {
        return channel == NetChannel.RELIABLE
                ? SteamNetworking.P2PSend.Reliable
                : SteamNetworking.P2PSend.Unreliable;
    }

    @Override
    public void poll(TransportListener listener, float deltaTimeSeconds) {
        for (NetChannel channel : NetChannel.values()) {
            drain(listener, channel);
        }
        dispatchRemoteClosures(listener);
    }

    private void drain(TransportListener listener, NetChannel channel) {
        while (networking.isP2PPacketAvailable(channel.ordinal(), packetSize)) {
            if (!readOne(listener, channel)) {
                return;
            }
        }
    }

    private boolean readOne(TransportListener listener, NetChannel channel) {
        receiveBuffer.clear();
        int received = readPacket(channel);
        if (received <= 0) {
            return false;
        }
        byte[] payload = new byte[received];
        receiveBuffer.position(0);
        receiveBuffer.get(payload, 0, received);
        deliver(listener, SteamIds.rawOf(scratchSender), channel, payload);
        return true;
    }

    private int readPacket(NetChannel channel) {
        try {
            return networking.readP2PPacket(scratchSender, receiveBuffer, channel.ordinal());
        } catch (SteamException unreadable) {
            return 0;
        }
    }

    private void deliver(TransportListener listener, long steamId, NetChannel channel, byte[] payload) {
        boolean known = connectionBySteamId.containsKey(steamId);
        if (!known && !accepting) {
            return;
        }
        int connection = connectionFor(steamId);
        if (!known) {
            listener.onConnectionOpened(connection);
        }
        listener.onPacketReceived(connection, channel, NetReader.wrapping(payload, 0, payload.length));
    }

    private void dispatchRemoteClosures(TransportListener listener) {
        if (closedRemotely.isEmpty()) {
            return;
        }
        List<Long> failed = List.copyOf(closedRemotely);
        closedRemotely.clear();
        for (long steamId : failed) {
            Integer connection = connectionBySteamId.get(steamId);
            if (connection != null) {
                forget(connection, steamId);
                listener.onConnectionClosed(connection);
            }
        }
    }

    @Override
    public void disconnect(int connection) {
        Long steamId = steamIdByConnection.get(connection);
        if (steamId == null) {
            return;
        }
        networking.closeP2PSessionWithUser(SteamIds.of(steamId));
        forget(connection, steamId);
    }

    private void forget(int connection, long steamId) {
        steamIdByConnection.remove(connection);
        connectionBySteamId.remove(steamId);
    }

    @Override
    public boolean isConnectionAlive(int connection) {
        return steamIdByConnection.containsKey(connection);
    }

    @Override
    public String addressOf(int connection) {
        Long steamId = steamIdByConnection.get(connection);
        return steamId == null ? "" : Long.toUnsignedString(steamId);
    }

    @Override
    public void close() {
        for (long steamId : List.copyOf(connectionBySteamId.keySet())) {
            networking.closeP2PSessionWithUser(SteamIds.of(steamId));
        }
        connectionBySteamId.clear();
        steamIdByConnection.clear();
        accepting = false;
        networking.dispose();
    }

    private final class Callbacks implements SteamNetworkingCallback {

        @Override
        public void onP2PSessionRequest(SteamID steamIDRemote) {
            if (accepting || connectionBySteamId.containsKey(SteamIds.rawOf(steamIDRemote))) {
                networking.acceptP2PSessionWithUser(steamIDRemote);
            }
        }

        @Override
        public void onP2PSessionConnectFail(SteamID steamIDRemote,
                                            SteamNetworking.P2PSessionError sessionError) {
            closedRemotely.add(SteamIds.rawOf(steamIDRemote));
        }
    }
}
