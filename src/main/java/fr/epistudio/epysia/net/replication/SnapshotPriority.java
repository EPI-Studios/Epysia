package fr.epistudio.epysia.net.replication;

@FunctionalInterface
public interface SnapshotPriority {
    SnapshotPriority NONE = networkId -> 0.0f;

    float scoreOf(int networkId);
}
