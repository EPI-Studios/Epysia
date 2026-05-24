package fr.epistudio.epysia.render.mesh;

import org.joml.Vector3f;

import java.util.Optional;

public record MtlDefinition(
        String name,
        Vector3f diffuseColor,
        Optional<String> diffuseTexturePath,
        Optional<String> normalTexturePath
) {
}
