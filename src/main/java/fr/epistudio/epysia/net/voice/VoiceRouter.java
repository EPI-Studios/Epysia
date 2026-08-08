package fr.epistudio.epysia.net.voice;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VoiceRouter {
    public List<Integer> listenersFor(VoiceFrame frame, VoiceRoutingContext context) {
        List<Integer> reached = new ArrayList<>();
        for (int listener : context.listeners()) {
            if (listener != frame.speakerPeer() && reaches(frame, context, listener)) {
                reached.add(listener);
            }
        }
        return reached;
    }

    private boolean reaches(VoiceFrame frame, VoiceRoutingContext context, int listener) {
        return switch (context.scope()) {
            case GLOBAL -> true;
            case TEAM -> context.channelOf(listener) == frame.channelId();
            case PROXIMITY -> isWithinHearingRadius(frame, context, listener);
        };
    }

    private boolean isWithinHearingRadius(VoiceFrame frame, VoiceRoutingContext context, int listener) {
        Optional<Vector3f> speakerPosition = context.positionOf(frame.speakerPeer());
        Optional<Vector3f> listenerPosition = context.positionOf(listener);
        if (speakerPosition.isEmpty() || listenerPosition.isEmpty()) {
            return false;
        }
        float radius = context.hearingRadiusMeters();
        return speakerPosition.get().distanceSquared(listenerPosition.get()) <= radius * radius;
    }
}
