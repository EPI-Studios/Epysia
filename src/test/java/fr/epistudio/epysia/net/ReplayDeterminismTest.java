package fr.epistudio.epysia.net;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.net.prediction.InputSample;
import fr.epistudio.epysia.net.prediction.PredictedMovement;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ReplayDeterminismTest {
    private static final float FIXED_STEP = 1.0f / 60.0f;
    private static final int SAMPLE_COUNT = 90;

    @Test
    void replayingTheSameInputsFromTheSameStateLandsOnTheSameTransform() {
        List<InputSample> inputs = buildInputs();
        Vector3f first = simulate(inputs);
        Vector3f second = simulate(inputs);
        assertEquals(Float.floatToRawIntBits(first.x), Float.floatToRawIntBits(second.x));
        assertEquals(Float.floatToRawIntBits(first.y), Float.floatToRawIntBits(second.y));
        assertEquals(Float.floatToRawIntBits(first.z), Float.floatToRawIntBits(second.z));
    }

    private static Vector3f simulate(List<InputSample> inputs) {
        Transform3D transform = new Transform3D().setPosition(3.0f, 1.5f, -2.0f);
        PredictedMovement mover = new AxisMover(transform);
        for (InputSample sample : inputs) {
            mover.simulatePredictedStep(sample, FIXED_STEP);
        }
        return new Vector3f(transform.position());
    }

    private static List<InputSample> buildInputs() {
        List<InputSample> samples = new ArrayList<>();
        for (int tick = 0; tick < SAMPLE_COUNT; tick++) {
            float forward = (float) Math.sin(tick * 0.13);
            float strafe = (float) Math.cos(tick * 0.07);
            samples.add(new InputSample(tick, tick % 3 == 0 ? 1L : 0L, new float[]{forward, strafe}));
        }
        return samples;
    }

    private record AxisMover(Transform3D transform) implements PredictedMovement {
        private static final float SPEED = 6.0f;
        private static final float JUMP_SPEED = 4.0f;

        @Override
        public void simulatePredictedStep(InputSample input, float deltaTimeSeconds) {
            float vertical = input.isDown(0) ? JUMP_SPEED * deltaTimeSeconds : 0.0f;
            transform.translate(input.axis(0) * SPEED * deltaTimeSeconds, vertical,
                    input.axis(1) * SPEED * deltaTimeSeconds);
        }
    }
}
