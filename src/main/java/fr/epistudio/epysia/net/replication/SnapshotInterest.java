package fr.epistudio.epysia.net.replication;

import java.util.Collection;

@FunctionalInterface
public interface SnapshotInterest {
    SnapshotInterest EVERYTHING = WorldState::networkIds;

    Collection<Integer> relevantNetworkIds(WorldState current);
}
