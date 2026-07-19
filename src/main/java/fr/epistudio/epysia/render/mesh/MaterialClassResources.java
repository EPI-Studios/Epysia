package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.material.MaterialClassMetadata;

record MaterialClassResources(MaterialClassMetadata metadata, PipelineHandle pipeline, BindingSetLayout litBindingLayout) {
}
