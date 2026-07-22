package fr.epistudio.epysia.vfx;

import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BufferHandle;

record VfxBindingLayouts(BindingSetLayout computeLayout, BindingSetLayout drawLayout,
                         BufferHandle frameUniformBuffer) {
}
