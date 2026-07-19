package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.CapsuleCollider;
import fr.epistudio.epysia.physics.components.Collider;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.physics.components.SphereCollider;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

final class LegacyRigidBodyMigration {

    private static final String RIGID_BODY_TYPE = RigidBodyComponent.class.getName();
    private static final Set<String> LEGACY_FIELDS = Set.of("shapeKind", "halfExtents", "radius", "halfHeight");
    private static final String AREA_KIND = "AREA";

    private LegacyRigidBodyMigration() {
    }

    static boolean matches(String typeName, Map<String, Object> fields) {
        if (!RIGID_BODY_TYPE.equals(typeName)) {
            return false;
        }
        if (AREA_KIND.equals(fields.get("kind"))) {
            return true;
        }
        for (String legacyField : LEGACY_FIELDS) {
            if (fields.containsKey(legacyField)) {
                return true;
            }
        }
        return false;
    }

    static void migrate(GameObject gameObject, Map<String, Object> fields,
                        Function<Class<? extends IComponent>, IComponent> instantiator,
                        BiConsumer<IComponent, Map<String, Object>> applyFields) {
        boolean isArea = AREA_KIND.equals(fields.get("kind"));
        IComponent body = instantiator.apply(RigidBodyComponent.class);
        applyFields.accept(body, bodyFields(fields, isArea));
        gameObject.addComponent(body);
        Class<? extends Collider> colliderClass = colliderClassFor(fields);
        IComponent collider = instantiator.apply(colliderClass);
        applyFields.accept(collider, colliderFields(fields, isArea));
        gameObject.addComponent(collider);
    }

    private static Map<String, Object> bodyFields(Map<String, Object> legacy, boolean isArea) {
        Map<String, Object> result = new HashMap<>();
        result.put("kind", isArea ? "STATIC" : legacy.getOrDefault("kind", "DYNAMIC"));
        copyIfPresent(legacy, result, "mass");
        copyIfPresent(legacy, result, "gravityScale");
        copyIfPresent(legacy, result, "linearDamping");
        copyIfPresent(legacy, result, "angularDamping");
        copyIfPresent(legacy, result, "continuousCollisionDetection");
        return result;
    }

    private static Map<String, Object> colliderFields(Map<String, Object> legacy, boolean isArea) {
        Map<String, Object> result = new HashMap<>();
        copyIfPresent(legacy, result, "halfExtents");
        copyIfPresent(legacy, result, "radius");
        copyIfPresent(legacy, result, "halfHeight");
        result.put("isTrigger", isArea);
        return result;
    }

    private static Class<? extends Collider> colliderClassFor(Map<String, Object> legacy) {
        String shapeKind = legacy.get("shapeKind") instanceof String name ? name : "BOX";
        return switch (shapeKind) {
            case "SPHERE" -> SphereCollider.class;
            case "CAPSULE" -> CapsuleCollider.class;
            default -> BoxCollider.class;
        };
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        renameIfPresent(source, target, key, key);
    }

    private static void renameIfPresent(Map<String, Object> source, Map<String, Object> target,
                                        String sourceKey, String targetKey) {
        Object value = source.get(sourceKey);
        if (value != null) {
            target.put(targetKey, value);
        }
    }
}
