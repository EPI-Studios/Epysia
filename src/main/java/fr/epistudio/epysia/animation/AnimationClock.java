package fr.epistudio.epysia.animation;

import fr.epistudio.epysia.components.Animator;
import fr.epistudio.epysia.scene.Scene;

public final class AnimationClock {
    private static final float MAXIMUM_STEP_SECONDS = 0.25f;

    private long generation;

    public void advance(Scene scene, float deltaSeconds) {
        float step = Math.max(0.0f, Math.min(MAXIMUM_STEP_SECONDS, deltaSeconds));
        for (Animator animator : scene.componentsOf(Animator.class)) {
            animator.advance(step);
        }
        generation++;
    }

    public long generation() {
        return generation;
    }
}
