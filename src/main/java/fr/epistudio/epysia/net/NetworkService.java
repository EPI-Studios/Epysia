package fr.epistudio.epysia.net;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.net.diagnostics.NetworkStats;
import fr.epistudio.epysia.net.prediction.InputSample;
import fr.epistudio.epysia.net.session.DisconnectReason;
import fr.epistudio.epysia.net.session.NetworkConfig;
import fr.epistudio.epysia.net.session.NetworkPeer;
import fr.epistudio.epysia.net.session.NetworkRole;
import fr.epistudio.epysia.net.voice.VoiceChannelAssignment;
import fr.epistudio.epysia.net.voice.VoiceService;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import fr.epistudio.epysia.logging.ConsoleLogger;

public final class NetworkService implements VoiceChannelAssignment {
    private final NetworkRuntime runtime;
    private final VoiceService voice;

    public NetworkService(NetworkRuntime runtime) {
        this.runtime = runtime;
        this.voice = new VoiceService(runtime.config().voice(), runtime.voice(), this);
    }

    public static NetworkService detached() {
        return DetachedHolder.INSTANCE;
    }

    public void startServer(NetworkConfig config) {
        runtime.startServer(config, false);
    }

    public void startListenServer(NetworkConfig config) {
        runtime.startServer(config, true);
    }

    public void connect(String host, int port) {
        runtime.connect(runtime.config(), host, port);
    }

    public void connect(NetworkConfig config, String host, int port) {
        runtime.connect(config, host, port);
    }

    public void disconnect() {
        runtime.disconnect();
    }

    public NetworkRole role() {
        return runtime.role();
    }

    public boolean isServer() {
        return runtime.role().isServer();
    }

    public boolean isClient() {
        return runtime.role().isClient();
    }

    public int localPeer() {
        return runtime.localPeer();
    }

    public List<NetworkPeer> peers() {
        return runtime.peers();
    }

    public NetworkStats stats() {
        return runtime.stats();
    }

    public NetworkConfig config() {
        return runtime.config();
    }

    public VoiceService voice() {
        return voice;
    }

    public int tick() {
        return runtime.session().tick();
    }

    public Optional<GameObject> spawn(String prefabGuid, Transform3D transform, int ownerPeer) {
        Vector3f position = transform == null ? new Vector3f() : new Vector3f(transform.position());
        Quaternionf rotation = transform == null ? new Quaternionf() : new Quaternionf(transform.rotation());
        return runtime.spawn(prefabGuid, position, rotation, ownerPeer);
    }

    public Optional<GameObject> spawn(String prefabGuid, Vector3f position, Quaternionf rotation, int ownerPeer) {
        return runtime.spawn(prefabGuid, position, rotation, ownerPeer);
    }

    public void despawn(GameObject gameObject) {
        runtime.despawn(gameObject);
    }

    public boolean startDiagnostics(String host, int port) {
        return runtime.startDiagnostics(host, port);
    }

    public void kick(int peer) {
        runtime.kick(peer, DisconnectReason.KICKED);
    }

    public void ban(int peer) {
        runtime.ban(peer);
    }

    public void muteOnServer(int peer, boolean muted) {
        runtime.muteOnServer(peer, muted);
    }

    public boolean isMutedOnServer(int peer) {
        return runtime.isMutedOnServer(peer);
    }

    public void call(IComponent component, String methodName, Object... arguments) {
        runtime.call(component, methodName, arguments);
    }

    public Optional<InputSample> inputOf(int peer) {
        return runtime.inputOf(peer);
    }

    public Optional<GameObject> findNetworkObject(int networkId) {
        return runtime.replication().objects().find(networkId);
    }

    @Override
    public void assign(int peer, int channelId) {
        if (peer == runtime.localPeer()) {
            runtime.voice().setLocalChannelId(channelId);
            return;
        }
        runtime.session().peer(peer).ifPresent(target -> target.setVoiceChannel(channelId));
    }

    @Override
    public int channelOf(int peer) {
        if (peer == runtime.localPeer()) {
            return runtime.voice().localChannelId();
        }
        return runtime.session().peer(peer).map(NetworkPeer::voiceChannel).orElse(0);
    }

    private static final class DetachedHolder {
        private static final NetworkService INSTANCE =
                new NetworkService(new NetworkRuntime(new ConsoleLogger()));

        private DetachedHolder() {
        }
    }
}
