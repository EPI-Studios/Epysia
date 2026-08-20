package fr.epistudio.epysia.events;

import fr.epistudio.epysia.components.IComponent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class EventBus {

    private static final EventBus DETACHED = new EventBus();

    public static EventBus detached() {
        return DETACHED;
    }

    private final Map<Class<?>, List<Registration<?>>> byEventType = new HashMap<>();
    private final Deque<Pending> deferred = new ArrayDeque<>();

    public <T> EventSubscription subscribe(Class<T> eventType, Consumer<T> listener) {
        return subscribe(eventType, listener, null, null);
    }

    public <T> EventSubscription subscribe(IComponent owner, Class<T> eventType,
                                           Consumer<T> listener) {
        return subscribe(eventType, listener, owner, null);
    }

    public <T> EventSubscription subscribeFrom(Object sender, Class<T> eventType,
                                               Consumer<T> listener) {
        return subscribe(eventType, listener, null, sender);
    }

    public <T> EventSubscription subscribeFrom(IComponent owner, Object sender, Class<T> eventType,
                                               Consumer<T> listener) {
        return subscribe(eventType, listener, owner, sender);
    }

    private <T> EventSubscription subscribe(Class<T> eventType, Consumer<T> listener,
                                            IComponent owner, Object sender) {
        Registration<T> registration = new Registration<>(listener, owner, sender);
        byEventType.computeIfAbsent(eventType, type -> new ArrayList<>()).add(registration);
        return () -> remove(eventType, registration);
    }

    private void remove(Class<?> eventType, Registration<?> registration) {
        List<Registration<?>> registrations = byEventType.get(eventType);
        if (registrations != null) {
            registrations.remove(registration);
        }
    }

    public void publish(Object event) {
        publishFrom(null, event);
    }

    public void publishFrom(Object sender, Object event) {
        List<Registration<?>> registrations = byEventType.get(event.getClass());
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        for (Registration<?> registration : List.copyOf(registrations)) {
            if (registration.accepts(sender)) {
                deliver(registration, event, registrations);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void deliver(Registration<T> registration, Object event,
                             List<Registration<?>> registrations) {
        if (registration.isDead()) {
            registrations.remove(registration);
            return;
        }
        registration.listener().accept((T) event);
    }

    public void post(Object event) {
        postFrom(null, event);
    }

    public void postFrom(Object sender, Object event) {
        deferred.add(new Pending(sender, event));
    }

    public void deliverDeferred() {
        while (!deferred.isEmpty()) {
            Pending pending = deferred.poll();
            publishFrom(pending.sender(), pending.event());
        }
    }

    public int subscriberCount(Class<?> eventType) {
        List<Registration<?>> registrations = byEventType.get(eventType);
        return registrations == null ? 0 : registrations.size();
    }

    public void clear() {
        byEventType.clear();
        deferred.clear();
    }

    private record Registration<T>(Consumer<T> listener, IComponent owner, Object sender) {
        boolean isDead() {
            return owner != null && !owner.isAlive();
        }

        boolean accepts(Object publisher) {
            return sender == null || sender == publisher;
        }
    }

    private record Pending(Object sender, Object event) {
    }
}
