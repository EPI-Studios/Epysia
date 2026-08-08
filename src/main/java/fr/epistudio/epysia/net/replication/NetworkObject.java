package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.IComponent;

@EpysiaComponent(name = "Network Object", category = "Networking")
public final class NetworkObject extends Component {
    public static final int SERVER_PEER = 0;
    public static final int UNASSIGNED_ID = 0;

    @Export(label = "Prefab Guid")
    private String prefabGuid = "";
    @Export(label = "On Owner Disconnect")
    private OwnershipPolicy onOwnerDisconnect = OwnershipPolicy.RETURN_TO_SERVER;
    @Export(label = "Predict Owned Movement")
    private boolean predictOwnedMovement = true;
    private int networkId = UNASSIGNED_ID;
    private int ownerPeer = SERVER_PEER;
    private boolean spawnedAtRuntime;

    public int networkId() {
        return networkId;
    }

    public NetworkObject assignNetworkId(int value) {
        this.networkId = value;
        return this;
    }

    public int ownerPeer() {
        return ownerPeer;
    }

    public NetworkObject assignOwner(int peer) {
        this.ownerPeer = peer;
        return this;
    }

    public String prefabGuid() {
        return prefabGuid;
    }

    public NetworkObject setPrefabGuid(String guid) {
        this.prefabGuid = guid == null ? "" : guid;
        return this;
    }

    public OwnershipPolicy onOwnerDisconnect() {
        return onOwnerDisconnect;
    }

    public NetworkObject setOnOwnerDisconnect(OwnershipPolicy policy) {
        this.onOwnerDisconnect = policy == null ? OwnershipPolicy.RETURN_TO_SERVER : policy;
        return this;
    }

    public boolean predictOwnedMovement() {
        return predictOwnedMovement;
    }

    public NetworkObject setPredictOwnedMovement(boolean value) {
        this.predictOwnedMovement = value;
        return this;
    }

    public boolean spawnedAtRuntime() {
        return spawnedAtRuntime;
    }

    public NetworkObject markSpawnedAtRuntime() {
        this.spawnedAtRuntime = true;
        return this;
    }

    public boolean isOwnedBy(int peer) {
        return ownerPeer == peer;
    }

    @Override
    public void copyStateFrom(IComponent source) {
        if (!(source instanceof NetworkObject other)) {
            return;
        }
        prefabGuid = other.prefabGuid;
        onOwnerDisconnect = other.onOwnerDisconnect;
        predictOwnedMovement = other.predictOwnedMovement;
    }
}
