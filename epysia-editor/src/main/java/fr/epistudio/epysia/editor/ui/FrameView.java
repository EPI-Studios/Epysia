package fr.epistudio.epysia.editor.ui;

public interface FrameView {

    void render(float deltaSeconds);

    default void dispose() {
    }
}
