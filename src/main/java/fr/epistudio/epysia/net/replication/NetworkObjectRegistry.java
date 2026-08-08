package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class NetworkObjectRegistry {
    private final Map<Integer, GameObject> gameObjectByNetworkId = new LinkedHashMap<>();
    private final Map<UUID, Integer> networkIdBySceneId = new LinkedHashMap<>();
    private final Map<Integer, Integer> ownerScratch = new LinkedHashMap<>();
    private int nextNetworkId = 1;

    public int assignSceneIds(Scene scene) {
        List<NetworkObject> placed = scenePlacedSortedByUuid(scene);
        int assigned = 0;
        for (NetworkObject networkObject : placed) {
            register(networkObject.ownerOrNull(), networkObject, nextNetworkId++);
            assigned++;
        }
        return assigned;
    }

    private static List<NetworkObject> scenePlacedSortedByUuid(Scene scene) {
        List<NetworkObject> placed = new ArrayList<>();
        for (NetworkObject networkObject : scene.componentsOf(NetworkObject.class)) {
            GameObject owner = networkObject.ownerOrNull();
            if (owner != null && !networkObject.spawnedAtRuntime()
                    && networkObject.networkId() == NetworkObject.UNASSIGNED_ID) {
                placed.add(networkObject);
            }
        }
        placed.sort(Comparator.comparing(networkObject -> networkObject.ownerOrNull().id()));
        return placed;
    }

    public int assignServerIdsForScene(Scene scene) {
        int assigned = 0;
        for (NetworkObject networkObject : scene.componentsOf(NetworkObject.class)) {
            GameObject owner = networkObject.ownerOrNull();
            if (owner == null || networkObject.networkId() != NetworkObject.UNASSIGNED_ID) {
                continue;
            }
            register(owner, networkObject, nextNetworkId++);
            assigned++;
        }
        return assigned;
    }

    public void register(GameObject owner, NetworkObject networkObject, int networkId) {
        networkObject.assignNetworkId(networkId);
        gameObjectByNetworkId.put(networkId, owner);
        networkIdBySceneId.put(owner.id(), networkId);
    }

    public int allocateNetworkId() {
        return nextNetworkId++;
    }

    public Optional<GameObject> find(int networkId) {
        return Optional.ofNullable(gameObjectByNetworkId.get(networkId));
    }

    public Optional<Integer> networkIdOfScenePlaced(UUID sceneId) {
        return Optional.ofNullable(networkIdBySceneId.get(sceneId));
    }

    public void unregister(int networkId) {
        GameObject removed = gameObjectByNetworkId.remove(networkId);
        if (removed != null) {
            networkIdBySceneId.remove(removed.id());
        }
    }

    public List<Integer> networkIds() {
        return new ArrayList<>(gameObjectByNetworkId.keySet());
    }

    public Set<Integer> networkIdView() {
        return Collections.unmodifiableSet(gameObjectByNetworkId.keySet());
    }

    public Map<Integer, Integer> ownersByNetworkId() {
        ownerScratch.clear();
        Map<Integer, Integer> owners = ownerScratch;
        for (Map.Entry<Integer, GameObject> entry : gameObjectByNetworkId.entrySet()) {
            NetworkObject networkObject = entry.getValue().getComponentOrNull(NetworkObject.class);
            owners.put(entry.getKey(),
                    networkObject == null ? NetworkObject.SERVER_PEER : networkObject.ownerPeer());
        }
        return owners;
    }

    public void clear() {
        gameObjectByNetworkId.clear();
        networkIdBySceneId.clear();
        nextNetworkId = 1;
    }
}
