package fr.epistudio.epysia;

import java.util.Optional;
public interface SystemRegistry {
    void add(GameSystem system);

    <T extends GameSystem> T get(Class<T> type);

    <T extends GameSystem> Optional<T> find(Class<T> type);
}
