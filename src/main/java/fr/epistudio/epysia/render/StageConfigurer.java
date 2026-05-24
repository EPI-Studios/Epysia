package fr.epistudio.epysia.render;

import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;

public interface StageConfigurer {

    void bindStageTarget(Stage stage, RenderTargetHandle target, PassClear clear);
}
