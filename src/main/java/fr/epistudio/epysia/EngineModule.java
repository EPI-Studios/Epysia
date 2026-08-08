package fr.epistudio.epysia;

public interface EngineModule {
    int order();

    void registerSystems(SystemRegistry registry);

    default boolean runsHeadless() {
        return true;
    }

    default void registerEditorExtensions(EditorContext editor) {
    }
}
