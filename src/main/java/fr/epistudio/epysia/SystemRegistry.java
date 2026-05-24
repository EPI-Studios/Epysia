package fr.epistudio.epysia;

public interface SystemRegistry {

    void add(GameSystem system);

    <T extends GameSystem> T get(Class<T> type);
}
