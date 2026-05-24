package fr.epistudio.epysia.render;

import fr.epistudio.epysia.render.backend.DrawCommand;

public interface FrameBuilder {

    void submit(Stage stage, DrawCommand command);
}
