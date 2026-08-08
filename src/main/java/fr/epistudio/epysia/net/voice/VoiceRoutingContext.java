package fr.epistudio.epysia.net.voice;

import org.joml.Vector3f;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record VoiceRoutingContext(
        VoiceScope scope,
        float hearingRadiusMeters,
        Map<Integer, Vector3f> positionByPeer,
        Map<Integer, Integer> channelByPeer,
        Collection<Integer> listeners
) {
    public static VoiceRoutingContext empty() {
        return new VoiceRoutingContext(VoiceScope.GLOBAL, 0.0f, Map.of(), Map.of(), List.of());
    }

    public Optional<Vector3f> positionOf(int peer) {
        return Optional.ofNullable(positionByPeer.get(peer));
    }

    public int channelOf(int peer) {
        return channelByPeer.getOrDefault(peer, 0);
    }
}
