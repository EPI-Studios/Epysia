package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultSchedulerTest {

    private static final float STEP = 1.0f / 60.0f;

    @Test
    void aCancelledDelayNeverRuns() {
        DefaultScheduler scheduler = new DefaultScheduler();
        AtomicInteger runs = new AtomicInteger();
        ScheduledAction action = scheduler.after(0.05f, runs::incrementAndGet);

        action.cancel();
        advance(scheduler, 0.2f);

        assertEquals(0, runs.get(), "a cancelled delay must never run");
        assertFalse(action.isPending(), "a cancelled action must stop reporting itself as pending");
    }

    @Test
    void aCancelledRepeatStopsAfterTheCancel() {
        DefaultScheduler scheduler = new DefaultScheduler();
        AtomicInteger runs = new AtomicInteger();
        ScheduledAction action = scheduler.every(0.05f, runs::incrementAndGet);

        advance(scheduler, 0.12f);
        int before = runs.get();
        action.cancel();
        advance(scheduler, 0.5f);

        assertEquals(before, runs.get(), "a cancelled repeat must stop firing");
    }

    @Test
    void aRepeatOwnedByADestroyedComponentStopsOnItsOwn() {
        Scene scene = new Scene("scene");
        GameObject owner = new GameObject("ticker");
        owner.addComponent(new Transform3D());
        PointLight component = owner.addComponent(new PointLight());
        scene.addGameObject(owner);
        scene.advanceTick();
        DefaultScheduler scheduler = new DefaultScheduler();
        AtomicInteger runs = new AtomicInteger();
        scheduler.every(component, 0.05f, runs::incrementAndGet);

        advance(scheduler, 0.12f);
        int before = runs.get();
        scene.removeGameObject(owner);
        scene.advanceTick();
        advance(scheduler, 0.5f);

        assertEquals(before, runs.get(),
                "a repeat tied to a destroyed component must stop instead of firing into a corpse");
    }

    private static void advance(DefaultScheduler scheduler, float seconds) {
        for (float elapsed = 0.0f; elapsed < seconds; elapsed += STEP) {
            scheduler.tick(STEP);
        }
    }
}
