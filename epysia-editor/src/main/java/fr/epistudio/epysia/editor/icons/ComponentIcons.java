package fr.epistudio.epysia.editor.icons;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scripting.Behaviour;

import java.util.List;
import java.util.Map;

public final class ComponentIcons {

    private static final Map<String, EditorIcon> BY_SIMPLE_NAME = Map.ofEntries(
            Map.entry("Transform3D", EditorIcon.NODE_3D),
            Map.entry("MeshRenderer", EditorIcon.MESH_INSTANCE_3D),
            Map.entry("MultiMeshRenderer", EditorIcon.MESH_INSTANCE_3D),
            Map.entry("Camera3D", EditorIcon.CAMERA_3D),
            Map.entry("DirectionalLight", EditorIcon.DIRECTIONAL_LIGHT_3D),
            Map.entry("PointLight", EditorIcon.OMNI_LIGHT_3D),
            Map.entry("SpotLight", EditorIcon.SPOT_LIGHT_3D),
            Map.entry("BoxCollider", EditorIcon.COLLISION_SHAPE_3D),
            Map.entry("SphereCollider", EditorIcon.COLLISION_SHAPE_3D),
            Map.entry("CapsuleCollider", EditorIcon.COLLISION_SHAPE_3D),
            Map.entry("MeshCollider", EditorIcon.COLLISION_SHAPE_3D),
            Map.entry("RigidBodyComponent", EditorIcon.RIGID_BODY_3D),
            Map.entry("StaticBodyComponent", EditorIcon.STATIC_BODY_3D),
            Map.entry("CharacterControllerComponent", EditorIcon.CHARACTER_BODY_3D),
            Map.entry("FlyCameraComponent", EditorIcon.CAMERA_3D),
            Map.entry("AnimationPlayer", EditorIcon.ANIMATION_PLAYER));

    private static final List<EditorIcon> DOMINANCE_ORDER = List.of(
            EditorIcon.CAMERA_3D,
            EditorIcon.DIRECTIONAL_LIGHT_3D,
            EditorIcon.OMNI_LIGHT_3D,
            EditorIcon.SPOT_LIGHT_3D,
            EditorIcon.CHARACTER_BODY_3D,
            EditorIcon.RIGID_BODY_3D,
            EditorIcon.STATIC_BODY_3D,
            EditorIcon.MESH_INSTANCE_3D,
            EditorIcon.COLLISION_SHAPE_3D,
            EditorIcon.SCRIPT);

    private ComponentIcons() {
    }

    public static EditorIcon forComponent(IComponent component) {
        EditorIcon mapped = BY_SIMPLE_NAME.get(component.getClass().getSimpleName());
        if (mapped != null) {
            return mapped;
        }
        return component instanceof Behaviour ? EditorIcon.SCRIPT : EditorIcon.NODE_3D;
    }

    public static EditorIcon forGameObject(GameObject gameObject) {
        EditorIcon best = EditorIcon.NODE_3D;
        int bestRank = Integer.MAX_VALUE;
        for (IComponent component : gameObject.components()) {
            if (component instanceof Transform3D) {
                continue;
            }
            bestRank = betterRank(forComponent(component), bestRank);
            best = bestRank < Integer.MAX_VALUE ? DOMINANCE_ORDER.get(bestRank) : best;
        }
        return best;
    }

    private static int betterRank(EditorIcon candidate, int currentBest) {
        int rank = DOMINANCE_ORDER.indexOf(candidate);
        return rank >= 0 && rank < currentBest ? rank : currentBest;
    }
}
