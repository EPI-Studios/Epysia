package fr.epistudio.epysia.tween;

import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TweenTest {

    private static final float STEP = 1.0f / 60.0f;
    private static final float TOLERANCE = 1.0e-3f;

    @Test
    void everyEasingStartsAtZeroAndEndsAtOne() {
        for (Easing easing : Easing.values()) {
            assertEquals(0.0f, easing.at(0.0f), TOLERANCE,
                    easing + " must start at zero, or the value jumps on the first frame");
            assertEquals(1.0f, easing.at(1.0f), TOLERANCE,
                    easing + " must land exactly on the target, or the value never arrives");
        }
    }

    @Test
    void easingClampsOutsideTheUnitRange() {
        for (Easing easing : Easing.values()) {
            assertEquals(0.0f, easing.at(-5.0f), TOLERANCE, easing + " must clamp below zero");
            assertEquals(1.0f, easing.at(5.0f), TOLERANCE, easing + " must clamp above one");
        }
    }

    @Test
    void aValueTweenReachesItsTargetExactly() {
        Tweens tweens = new Tweens();
        AtomicReference<Float> seen = new AtomicReference<>(0.0f);
        tweens.over(0.2f).easing(Easing.LINEAR).value(0.0f, 10.0f, seen::set);

        advance(tweens, 0.5f);

        assertEquals(10.0f, seen.get(), TOLERANCE,
                "a finished tween must land on the target, not near it");
        assertEquals(0, tweens.activeCount(), "a finished tween must stop being advanced");
    }

    @Test
    void aDelayHoldsTheValueBeforeStarting() {
        Tweens tweens = new Tweens();
        AtomicReference<Float> seen = new AtomicReference<>(-1.0f);
        tweens.over(0.2f).after(0.3f).value(0.0f, 10.0f, seen::set);

        advance(tweens, 0.2f);
        assertEquals(-1.0f, seen.get(), TOLERANCE, "nothing may be written during the delay");

        advance(tweens, 0.5f);
        assertEquals(10.0f, seen.get(), TOLERANCE, "the tween runs once the delay elapses");
    }

    @Test
    void onCompleteFiresOnceAndOnlyWhenFinished() {
        Tweens tweens = new Tweens();
        AtomicInteger completions = new AtomicInteger();
        tweens.over(0.1f).value(0.0f, 1.0f, value -> {
        }).onComplete(completions::incrementAndGet);

        advance(tweens, 0.05f);
        assertEquals(0, completions.get(), "onComplete must not fire mid flight");

        advance(tweens, 0.3f);
        assertEquals(1, completions.get(), "onComplete fires exactly once");
    }

    @Test
    void cancellingStopsTheTweenAndItsCallback() {
        Tweens tweens = new Tweens();
        AtomicInteger completions = new AtomicInteger();
        AtomicReference<Float> seen = new AtomicReference<>(0.0f);
        Tween tween = tweens.over(0.2f).value(0.0f, 10.0f, seen::set)
                .onComplete(completions::incrementAndGet);

        advance(tweens, 0.05f);
        tween.cancel();
        float atCancel = seen.get();
        advance(tweens, 0.5f);

        assertEquals(atCancel, seen.get(), TOLERANCE, "a cancelled tween writes nothing more");
        assertEquals(0, completions.get(), "a cancelled tween never completes");
        assertFalse(tween.isPending(), "a cancelled tween is not pending");
    }

    @Test
    void aTweenOwnedByADestroyedComponentStops() {
        Scene scene = new Scene("scene");
        GameObject owner = new GameObject("fading");
        owner.addComponent(new Transform3D());
        PointLight light = owner.addComponent(new PointLight());
        scene.addGameObject(owner);
        scene.advanceTick();
        Tweens tweens = new Tweens();
        AtomicReference<Float> seen = new AtomicReference<>(0.0f);
        tweens.over(1.0f).ownedBy(light).value(0.0f, 10.0f, seen::set);

        advance(tweens, 0.1f);
        float beforeRemoval = seen.get();
        scene.removeGameObject(owner);
        scene.advanceTick();
        advance(tweens, 1.0f);

        assertEquals(beforeRemoval, seen.get(), TOLERANCE,
                "a tween must stop writing into a destroyed component");
        assertEquals(0, tweens.activeCount(), "the dead tween must be dropped, not kept and skipped");
    }

    @Test
    void aVectorTweenInterpolatesEveryComponent() {
        Tweens tweens = new Tweens();
        AtomicReference<Vector3f> seen = new AtomicReference<>(new Vector3f());
        tweens.over(0.2f).easing(Easing.LINEAR).vector3(
                new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(2.0f, 4.0f, 6.0f),
                value -> seen.set(new Vector3f(value)));

        advance(tweens, 0.5f);

        assertEquals(2.0f, seen.get().x, TOLERANCE, "x must reach its target");
        assertEquals(4.0f, seen.get().y, TOLERANCE, "y must reach its target");
        assertEquals(6.0f, seen.get().z, TOLERANCE, "z must reach its target");
    }

    @Test
    void pingPongComesBackTowardsTheStart() {
        Tweens tweens = new Tweens();
        AtomicReference<Float> seen = new AtomicReference<>(0.0f);
        tweens.over(0.1f).easing(Easing.LINEAR).loop(TweenLoop.PING_PONG)
                .value(0.0f, 10.0f, seen::set);

        advance(tweens, 0.1f);
        float atEnd = seen.get();
        advance(tweens, 0.1f);

        assertTrue(atEnd > 9.0f, "the first leg must reach the far end");
        assertTrue(seen.get() < 1.0f, "the second leg must come back towards the start");
        assertTrue(tweens.activeCount() > 0, "a ping pong tween keeps running");
    }

    private static void advance(Tweens tweens, float seconds) {
        for (float elapsed = 0.0f; elapsed < seconds; elapsed += STEP) {
            tweens.advance(STEP);
        }
    }
}
