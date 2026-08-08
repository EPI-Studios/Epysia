package fr.epistudio.epysia.net;

import fr.epistudio.epysia.net.voice.VoiceFrame;
import fr.epistudio.epysia.net.voice.VoiceRouter;
import fr.epistudio.epysia.net.voice.VoiceRoutingContext;
import fr.epistudio.epysia.net.voice.VoiceScope;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class VoiceRouterTest {
    private static final int SPEAKER = 1;
    private static final int NEARBY_LISTENER = 2;
    private static final int DISTANT_LISTENER = 3;
    private static final float HEARING_RADIUS = 10.0f;

    private final VoiceRouter router = new VoiceRouter();
    private final VoiceFrame frame = new VoiceFrame(SPEAKER, 0, 4, new byte[]{1, 2, 3});

    @Test
    void proximityReachesTheListenerInsideTheRadiusOnly() {
        VoiceRoutingContext context = new VoiceRoutingContext(VoiceScope.PROXIMITY, HEARING_RADIUS,
                Map.of(SPEAKER, new Vector3f(),
                        NEARBY_LISTENER, new Vector3f(3.0f, 0.0f, 0.0f),
                        DISTANT_LISTENER, new Vector3f(40.0f, 0.0f, 0.0f)),
                Map.of(), List.of(SPEAKER, NEARBY_LISTENER, DISTANT_LISTENER));
        assertEquals(List.of(NEARBY_LISTENER), router.listenersFor(frame, context));
    }

    @Test
    void teamReachesTheSpeakersChannelOnly() {
        VoiceRoutingContext context = new VoiceRoutingContext(VoiceScope.TEAM, HEARING_RADIUS,
                Map.of(), Map.of(SPEAKER, 4, NEARBY_LISTENER, 4, DISTANT_LISTENER, 9),
                List.of(SPEAKER, NEARBY_LISTENER, DISTANT_LISTENER));
        assertEquals(List.of(NEARBY_LISTENER), router.listenersFor(frame, context));
    }

    @Test
    void globalReachesEveryListenerButNeverTheSpeaker() {
        VoiceRoutingContext context = new VoiceRoutingContext(VoiceScope.GLOBAL, HEARING_RADIUS,
                Map.of(), Map.of(), List.of(SPEAKER, NEARBY_LISTENER, DISTANT_LISTENER));
        assertEquals(List.of(NEARBY_LISTENER, DISTANT_LISTENER), router.listenersFor(frame, context));
    }
}
