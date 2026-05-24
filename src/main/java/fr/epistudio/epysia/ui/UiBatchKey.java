package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.TextureHandle;

record UiBatchKey(PipelineHandle pipeline, TextureHandle texture, UiShaderKind kind) {
}
