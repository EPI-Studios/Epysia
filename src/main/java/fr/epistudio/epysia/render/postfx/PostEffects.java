package fr.epistudio.epysia.render.postfx;

import fr.epistudio.epysia.components.Camera3D;

public interface PostEffects {

    PostEffectStack globalStack();

    PostEffectStack stackFor(Camera3D camera);

    PostEffectBuiltins builtins();
}
