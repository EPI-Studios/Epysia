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
    private final Deque<Object> deferred = new ArrayDeque<>();

    public <T> EventSubscription subscribe(Class<T> eventType, Consumer<T> listener) {
        return subscribe(eventType, listener, null);
    }

    public <T> EventSubscription subscribe(IComponent owner, Class<T> eventType,
                                           Consumer<T> listener) {
        return subscribe(eventType, listener, owner);
    }

    private <T> EventSubscription subscribe(Class<T> eventType, Consumer<T> listener,
                                            IComponent owner) {
        Registration<T> registration = new Registration<>(listener, owner);
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
        List<Registration<?>> registrations = byEventType.get(event.getClass());
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        for (Registration<?> registration : List.copyOf(registrations)) {
            deliver(registration, event, registrations);
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
        deferred.add(event);
    }

    public void deliverDeferred() {
        while (!deferred.isEmpty()) {
            publish(deferred.poll());
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

    private record Registration<T>(Consumer<T> listener, IComponent owner) {
        boolean isDead() {
            return owner != null && !owner.isAlive();
        }
    }
}
