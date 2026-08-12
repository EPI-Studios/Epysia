package fr.epistudio.epysia.events;

@FunctionalInterface
public interface EventSubscription extends AutoCloseable {

    @Override
    void close();
}
