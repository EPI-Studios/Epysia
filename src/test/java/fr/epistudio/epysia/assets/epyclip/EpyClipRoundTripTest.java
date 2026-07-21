package fr.epistudio.epysia.assets.epyclip;

import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.animation.ClipChannel;
import fr.epistudio.epysia.animation.ClipInterpolation;
import fr.epistudio.epysia.animation.ClipProperty;
import fr.epistudio.epysia.exceptions.EpysiaException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EpyClipRoundTripTest {

    private static Clip walkClip() {
        ClipChannel rotation = new ClipChannel(1, ClipProperty.ROTATION, ClipInterpolation.LINEAR,
                new float[]{0.0f, 0.5f, 1.0f},
                new float[]{0, 0, 0, 1, 0, 0.707f, 0, 0.707f, 0, 1, 0, 0});
        ClipChannel position = new ClipChannel(0, ClipProperty.TRANSLATION, ClipInterpolation.STEP,
                new float[]{0.0f, 1.0f}, new float[]{0, 0, 0, 0, 1, 0});
        return new Clip("walk", 1.0f, 42L, List.of(rotation, position));
    }

    @Test
    void clipRoundTripsThroughBytes() {
        Clip original = walkClip();
        Clip decoded = EpyClipReader.read(EpyClipWriter.write(original));
        assertEquals("walk", decoded.name());
        assertEquals(1.0f, decoded.durationSeconds());
        assertEquals(42L, decoded.skeletonChecksum());
        assertEquals(2, decoded.channels().size());
        ClipChannel channel = decoded.channels().get(0);
        assertEquals(ClipProperty.ROTATION, channel.property());
        assertEquals(ClipInterpolation.LINEAR, channel.interpolation());
        assertArrayEquals(new float[]{0.0f, 0.5f, 1.0f}, channel.times(), 0.0f);
    }

    @Test
    void channelRejectsMismatchedValueCount() {
        assertThrows(EpysiaException.class, () -> new ClipChannel(0, ClipProperty.TRANSLATION,
                ClipInterpolation.LINEAR, new float[]{0.0f, 1.0f}, new float[]{0, 0, 0}));
    }

    @Test
    void channelRejectsNonIncreasingTimes() {
        assertThrows(EpysiaException.class, () -> new ClipChannel(0, ClipProperty.TRANSLATION,
                ClipInterpolation.LINEAR, new float[]{0.5f, 0.5f}, new float[]{0, 0, 0, 0, 0, 0}));
    }
}
