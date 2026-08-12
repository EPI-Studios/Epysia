package fr.epistudio.epysia.steam;

import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.transport.NetChannel;
import fr.epistudio.epysia.net.transport.SteamTransport;
import fr.epistudio.epysia.net.transport.TransportListener;
import com.codedisaster.steamworks.SteamException;
import com.codedisaster.steamworks.SteamNetworking;
import com.codedisaster.steamworks.SteamNetworkingCallback;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SteamProbe {

    private static final int CALLBACK_ROUNDS = 200;
    private static final long ROUND_PAUSE_MILLISECONDS = 25L;

    private SteamProbe() {
    }

    public static void main(String[] arguments) throws InterruptedException {
        SteamConfig config = new SteamConfig().setAppId(appIdFrom(arguments));
        SteamService steam = new SteamService(new SteamRuntime(new ConsoleLogger()));
        if (!steam.start(config)) {
            System.out.println("[probe] Steam unavailable, engine continues without it");
            return;
        }
        report(steam);
        exerciseLobby(steam);
        exerciseTransport(steam);
        steam.stop();
    }

    private static int appIdFrom(String[] arguments) {
        if (arguments.length == 0) {
            return SteamConfig.SPACEWAR_APP_ID;
        }
        return Integer.parseInt(arguments[0]);
    }

    private static void report(SteamService steam) {
        System.out.println("[probe] available=" + steam.available());
        System.out.println("[probe] appId=" + steam.appId());
        System.out.println("[probe] steamId=" + Long.toUnsignedString(steam.steamId()));
        System.out.println("[probe] persona=" + steam.personaName());
        System.out.println("[probe] overlay=" + steam.overlayEnabled());
        System.out.println("[probe] cloudFiles=" + steam.cloud().map(SteamCloud::fileCount).orElse(-1));
        System.out.println("[probe] achievements="
                + steam.achievements().map(SteamAchievements::names).orElse(List.of()).size());
    }

    private static void exerciseLobby(SteamService steam) throws InterruptedException {
        steam.lobbies().ifPresent(lobbies -> {
            lobbies.setListener(new ProbeListener(lobbies));
            lobbies.create(SteamLobbyVisibility.PUBLIC, 4);
        });
        for (int round = 0; round < CALLBACK_ROUNDS; round++) {
            steam.runCallbacks();
            Thread.sleep(ROUND_PAUSE_MILLISECONDS);
        }
        steam.lobbies().ifPresent(SteamLobbies::leave);
    }

    private static void exerciseTransport(SteamService steam) throws InterruptedException {
        roundTripEveryChannel(steam);
        acceptUnknownSender(steam);
    }

    private static void roundTripEveryChannel(SteamService steam) throws InterruptedException {
        SteamTransport transport = new SteamTransport();
        Recorder recorder = new Recorder();
        transport.listen(0);
        int connection = transport.connect(Long.toUnsignedString(steam.steamId()), 0);
        for (NetChannel channel : NetChannel.values()) {
            transport.send(connection, channel, probePayload(channel));
        }
        pump(steam, transport, recorder);
        System.out.println("[probe] p2p roundTrip opened=" + recorder.opened.size()
                + " packets=" + recorder.packets);
        transport.close();
    }

    private static void acceptUnknownSender(SteamService steam) throws InterruptedException {
        SteamTransport transport = new SteamTransport();
        Recorder recorder = new Recorder();
        transport.listen(0);
        sendRaw(steam.steamId());
        pump(steam, transport, recorder);
        System.out.println("[probe] p2p unknownSender opened=" + recorder.opened.size()
                + " packets=" + recorder.packets);
        transport.close();
    }

    private static void sendRaw(long steamId) {
        SteamNetworking raw = new SteamNetworking(new SteamNetworkingCallback() {
        });
        ByteBuffer direct = BufferUtils.createByteBuffer(8);
        direct.put("stranger".getBytes(StandardCharsets.UTF_8)).flip();
        try {
            raw.sendP2PPacket(SteamIds.of(steamId), direct,
                    SteamNetworking.P2PSend.Reliable, NetChannel.RELIABLE.ordinal());
        } catch (SteamException refused) {
            System.out.println("[probe] raw send refused: " + refused.getMessage());
        }
        raw.dispose();
    }

    private static ByteBuffer probePayload(NetChannel channel) {
        byte[] message = ("epysia-" + channel.name()).getBytes(StandardCharsets.UTF_8);
        ByteBuffer payload = ByteBuffer.allocate(message.length);
        payload.put(message).flip();
        return payload;
    }

    private static void pump(SteamService steam, SteamTransport transport, Recorder recorder)
            throws InterruptedException {
        for (int round = 0; round < CALLBACK_ROUNDS; round++) {
            steam.runCallbacks();
            transport.poll(recorder, ROUND_PAUSE_MILLISECONDS / 1000.0f);
            Thread.sleep(ROUND_PAUSE_MILLISECONDS);
        }
    }

    private static final class Recorder implements TransportListener {

        private final List<Integer> opened = new ArrayList<>();
        private final List<String> packets = new ArrayList<>();

        @Override
        public void onConnectionOpened(int connection) {
            opened.add(connection);
        }

        @Override
        public void onPacketReceived(int connection, NetChannel channel, NetReader reader) {
            packets.add(channel + ":" + reader.remaining() + "b@" + connection);
        }

        @Override
        public void onConnectionClosed(int connection) {
        }
    }

    private record ProbeListener(SteamLobbies lobbies) implements SteamLobbyListener {

        @Override
        public void onLobbyCreated(long lobbyId, boolean succeeded) {
            System.out.println("[probe] lobbyCreated id=" + Long.toUnsignedString(lobbyId)
                    + " ok=" + succeeded);
        }

        @Override
        public void onLobbyEntered(long lobbyId, boolean succeeded) {
            System.out.println("[probe] lobbyEntered id=" + Long.toUnsignedString(lobbyId)
                    + " ok=" + succeeded + " members=" + lobbies.members().size()
                    + " owner=" + Long.toUnsignedString(lobbies.owner(lobbyId)));
        }
    }
}
