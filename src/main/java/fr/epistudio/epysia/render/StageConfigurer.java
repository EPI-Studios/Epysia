package fr.epistudio.epysia.render;

import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;

public interface StageConfigurer {

    void bindStageTarget(RenderPass pass, RenderTargetHandle target, PassClear clear);

    void bindStageTargetFollowing(RenderPass pass, RenderPass followed, PassClear clear);

    default void bindStagePreparation(RenderPass pass, Runnable preparation) {
    }
}
