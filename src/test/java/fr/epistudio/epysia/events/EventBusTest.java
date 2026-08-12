package fr.epistudio.epysia.events;

import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest {

    private record Damaged(int amount) {
    }

    @Test
    void aPublishedEventReachesEverySubscriber() {
        EventBus bus = new EventBus();
        List<Integer> seen = new ArrayList<>();
        bus.subscribe(Damaged.class, event -> seen.add(event.amount()));
        bus.subscribe(Damaged.class, event -> seen.add(event.amount() * 2));

        bus.publish(new Damaged(5));

        assertEquals(List.of(5, 10), seen, "every subscriber must see the event, in subscribe order");
    }

    @Test
    void aPostedEventWaitsForTheDrain() {
        EventBus bus = new EventBus();
        List<Integer> seen = new ArrayList<>();
        bus.subscribe(Damaged.class, event -> seen.add(event.amount()));

        bus.post(new Damaged(7));
        assertTrue(seen.isEmpty(), "a posted event must not fire before the drain");

        bus.deliverDeferred();
        assertEquals(List.of(7), seen, "the drain must deliver what was posted");
    }

    @Test
    void aClosedSubscriptionStopsReceiving() {
        EventBus bus = new EventBus();
        List<Integer> seen = new ArrayList<>();
        EventSubscription subscription = bus.subscribe(Damaged.class, event -> seen.add(event.amount()));

        subscription.close();
        bus.publish(new Damaged(3));

        assertTrue(seen.isEmpty(), "closing a subscription must stop delivery");
    }

    @Test
    void aSubscriptionDiesWithItsComponent() {
        Scene scene = new Scene("scene");
        GameObject owner = new GameObject("listener");
        owner.addComponent(new Transform3D());
        PointLight component = owner.addComponent(new PointLight());
        scene.addGameObject(owner);
        scene.advanceTick();
        EventBus bus = new EventBus();
        List<Integer> seen = new ArrayList<>();
        bus.subscribe(component, Damaged.class, event -> seen.add(event.amount()));

        scene.removeGameObject(owner);
        scene.advanceTick();
        bus.publish(new Damaged(9));

        assertTrue(seen.isEmpty(), "a destroyed component must stop receiving events");
        assertEquals(0, bus.subscriberCount(Damaged.class),
                "the dead subscription must be dropped rather than kept and skipped forever");
    }
}
