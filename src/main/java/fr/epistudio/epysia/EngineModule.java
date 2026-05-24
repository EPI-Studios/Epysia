package fr.epistudio.epysia;

public interface EngineModule {

    int order();

    void registerSystems(SystemRegistry registry);

    default void registerEditorExtensions(EditorContext editor) {
    }
}
