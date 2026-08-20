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
            Map.entry("Animator", EditorIcon.ANIMATION_PLAYER),
            Map.entry("AudioSourceComponent", EditorIcon.AUDIO_STREAM_PLAYER_3D),
            Map.entry("AudioListenerComponent", EditorIcon.AUDIO_LISTENER_3D),
            Map.entry("AudioEnvironment", EditorIcon.WORLD_ENVIRONMENT),
            Map.entry("VoiceChatComponent", EditorIcon.AUDIO_MICROPHONE),
            Map.entry("CharacterController2D", EditorIcon.CHARACTER_BODY_2D),
            Map.entry("Collider", EditorIcon.COLLISION_SHAPE_3D),
            Map.entry("Collider2D", EditorIcon.COLLISION_SHAPE_2D),
            Map.entry("GraphComponent", EditorIcon.GRAPH_EDIT),
            Map.entry("Light", EditorIcon.OMNI_LIGHT_3D),
            Map.entry("Light2D", EditorIcon.POINT_LIGHT_2D),
            Map.entry("ParticleEffect", EditorIcon.PARTICLES_3D),
            Map.entry("RigidBody2D", EditorIcon.RIGID_BODY_2D),
            Map.entry("SpriteRenderer", EditorIcon.SPRITE_2D),
            Map.entry("SpriteFlipbook", EditorIcon.ANIMATED_SPRITE_2D),
            Map.entry("TilemapRenderer", EditorIcon.TILE_MAP),
            Map.entry("TilemapSceneSpawner", EditorIcon.TILE_MAP),
            Map.entry("Transform2D", EditorIcon.NODE_2D),
            Map.entry("FollowTransform2D", EditorIcon.NODE_2D),
            Map.entry("UiCanvas", EditorIcon.CANVAS_LAYER),
            Map.entry("UiElement", EditorIcon.CONTROL),
            Map.entry("NavMeshAgent", EditorIcon.NAVIGATION_AGENT_3D),
            Map.entry("NavMeshSurface", EditorIcon.NAVIGATION_REGION_3D),
            Map.entry("NetworkObject", EditorIcon.REMOTE_TRANSFORM_3D),
            Map.entry("NetworkSynchronizer", EditorIcon.REMOTE_TRANSFORM_3D),
            Map.entry("NetworkRigidBody", EditorIcon.REMOTE_TRANSFORM_3D),
            Map.entry("NetworkCharacterController", EditorIcon.REMOTE_TRANSFORM_3D),
            Map.entry("Skybox", EditorIcon.WORLD_ENVIRONMENT),
            Map.entry("WorldText", EditorIcon.LABEL_3D),
            Map.entry("VolumetricRenderer", EditorIcon.FOG_VOLUME),
            Map.entry("LightProbeVolume", EditorIcon.LIGHTMAP_PROBE),
            Map.entry("JointComponent", EditorIcon.HINGE_JOINT_3D),
            Map.entry("JointSocket", EditorIcon.HINGE_JOINT_3D),
            Map.entry("DensityVolume", EditorIcon.DECAL),
            Map.entry("DensityDeformer", EditorIcon.DECAL),
            Map.entry("DensityPropagation", EditorIcon.DECAL),
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
            EditorIcon.NAVIGATION_REGION_3D,
            EditorIcon.PARTICLES_3D,
            EditorIcon.TILE_MAP,
            EditorIcon.SPRITE_2D,
            EditorIcon.CONTROL,
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
        return forComponentClass(component.getClass());
    }

    public static EditorIcon forComponentClass(Class<? extends IComponent> componentClass) {
        EditorIcon mapped = BY_SIMPLE_NAME.get(componentClass.getSimpleName());
        if (mapped != null) {
            return mapped;
        }
        return Behaviour.class.isAssignableFrom(componentClass) ? EditorIcon.SCRIPT : EditorIcon.NODE_3D;
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
