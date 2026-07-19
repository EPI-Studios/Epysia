package fr.epistudio.epysia.physics.api;

import fr.epistudio.epysia.components.HiddenInEditor;

public enum RigidBodyKind {
    STATIC,
    DYNAMIC,
    KINEMATIC,
    @HiddenInEditor
    AREA
}
