package fr.epistudio.epysia.net.session;

import fr.epistudio.epysia.net.protocol.NetReader;
import java.util.List;

public interface NetworkEvents {
    void onSessionStarted(NetworkRole role);

    void onSessionStopped();

    void onPeerJoined(NetworkPeer peer);

    void onPeerLeft(NetworkPeer peer, DisconnectReason reason);

    void onPeerReturned(NetworkPeer peer, List<Integer> ownedObjects);

    void onReconnectWindowClosed(int peerId, List<Integer> ownedObjects);

    List<Integer> ownedObjectsOf(int peerId);

    void onLocalPeerAssigned(int peerId, int serverTick);

    default void onTickResynchronised() {
    }

    void onSnapshotReceived(NetReader reader);

    void onSpawnReceived(NetReader reader);

    void onDespawnReceived(NetReader reader);

    void onInputBatchReceived(NetworkPeer peer, NetReader reader);

    void onRemoteProcedureCallReceived(int fromPeer, NetReader reader);

    void onVoiceFrameReceived(int fromPeer, NetReader reader);
}
