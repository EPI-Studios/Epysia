package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.MultiMeshRenderer;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.material.MaterialFields;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.ComponentException;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.graph.GraphComponent;
import fr.epistudio.epysia.graph.GraphValueJson;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ExportedProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class GameObjectJsonCodec {

    public enum IdentityPolicy {
        PRESERVE_IDS,
        FRESH_IDS
    }

    private final ComponentRegistry componentRegistry;
    private final ComponentFieldsCodec fieldsCodec = new ComponentFieldsCodec();
    private final MaterialJsonCodec materialCodec = new MaterialJsonCodec();
    private final PostEffectStackJsonCodec postEffectCodec = new PostEffectStackJsonCodec();
    private List<String> lastIncompatibleComponents = List.of();
    private List<String> lastUnloadableComponents = List.of();

    public GameObjectJsonCodec(ComponentRegistry componentRegistry) {
        this.componentRegistry = componentRegistry;
    }

    public void writeGameObjectArray(JsonWriter writer, List<GameObject> exported) {
        Map<GameObject, Integer> indexByGameObject = new IdentityHashMap<>();
        for (int i = 0; i < exported.size(); i++) {
            indexByGameObject.put(exported.get(i), i);
        }
        writer.beginArray();
        for (GameObject gameObject : exported) {
            writeGameObject(writer, gameObject, indexByGameObject);
        }
        writer.endArray();
    }

    private void writeGameObject(JsonWriter writer, GameObject gameObject,
                                 Map<GameObject, Integer> indexByGameObject) {
        writer.beginObject();
        writer.key("id").valueString(gameObject.id().toString());
        writer.key("name").valueString(gameObject.name());
        writer.key("tag").valueString(gameObject.tag());
        writer.key("active").valueBoolean(gameObject.active());
        writer.key("parentIndex").valueNumber(resolveParentIndex(gameObject, indexByGameObject));
        writer.key("components").beginArray();
        for (ComponentRegistry.Entry entry : componentRegistry.entries()) {
            gameObject.getComponent(entry.componentClass()).ifPresent(component ->
                    writeComponent(writer, entry, component, indexByGameObject));
        }
        for (Object payload : gameObject.unloadableComponentPayloads()) {
            JsonValueWriter.write(writer, payload);
        }
        writer.endArray();
        writer.endObject();
    }

    private static int resolveParentIndex(GameObject gameObject, Map<GameObject, Integer> indexByGameObject) {
        return gameObject.getComponent(Transform3D.class)
                .flatMap(Transform3D::parent)
                .flatMap(Transform3D::owner)
                .map(parent -> indexByGameObject.getOrDefault(parent, -1))
                .orElse(-1);
    }

    private void writeComponent(JsonWriter writer, ComponentRegistry.Entry entry, IComponent component,
                                Map<GameObject, Integer> indexByGameObject) {
        writer.beginObject();
        writer.key("type").valueString(entry.componentClass().getName());
        writer.key("displayName").valueString(entry.displayName());
        writer.key("fields").beginObject();
        fieldsCodec.writeFields(writer, component,
                target -> encodeReference(target, indexByGameObject));
        writer.endObject();
        writeMaterialsIfPresent(writer, component);
        writePostEffectsIfPresent(writer, component);
        writeGraphOverridesIfPresent(writer, component);
        writer.endObject();
    }

    private void writeGraphOverridesIfPresent(JsonWriter writer, IComponent component) {
        if (!(component instanceof GraphComponent graph) || graph.variableOverrides().isEmpty()) {
            return;
        }
        writer.key("graphVariables").beginObject();
        for (Map.Entry<String, Object> entry : graph.variableOverrides().entrySet()) {
            writer.key(entry.getKey());
            GraphValueJson.write(writer, entry.getValue());
        }
        writer.endObject();
    }

    private void writeMaterialsIfPresent(JsonWriter writer, IComponent component) {
        if (!(component instanceof MeshRenderer renderer) || renderer.materials().isEmpty()) {
            return;
        }
        writer.key("materials");
        materialCodec.writeMaterialArray(writer, renderer.materials());
    }

    private void writePostEffectsIfPresent(JsonWriter writer, IComponent component) {
        if (!(component instanceof Camera3D camera) || camera.postEffectStack().isEmpty()) {
            return;
        }
        writer.key("postEffects");
        postEffectCodec.writeStack(writer, camera.postEffectStack().get());
    }

    private static String encodeReference(GameObject target, Map<GameObject, Integer> indexByGameObject) {
        return indexByGameObject.containsKey(target) ? target.id().toString() : "";
    }

    public List<GameObject> readGameObjectArray(List<Object> gameObjectsJson, IdentityPolicy identityPolicy) {
        GraphReader reader = new GraphReader(identityPolicy);
        List<GameObject> result = reader.read(gameObjectsJson);
        lastIncompatibleComponents = reader.incompatibleComponents;
        lastUnloadableComponents = reader.unloadableComponents;
        return result;
    }

    public void applyFieldsWithoutReferences(IComponent component, Map<String, Object> fields) {
        fieldsCodec.applyFields(component, fields, ComponentFieldsCodec.ReferenceSink.IGNORING);
    }

    public void invokeOnLoad(List<GameObject> loaded, EngineServices services) {
        for (String skipped : lastIncompatibleComponents) {
            services.logger().warn("[GameObjectJsonCodec] Skipped incompatible component: " + skipped);
        }
        for (String unloadable : lastUnloadableComponents) {
            services.logger().warn("[GameObjectJsonCodec] Component class unavailable, "
                    + "data preserved for the next save: " + unloadable);
        }
        for (GameObject gameObject : loaded) {
            for (IComponent component : new ArrayList<>(gameObject.components())) {
                try {
                    component.onLoad(services);
                } catch (RuntimeException error) {
                    services.logger().error("[GameObjectJsonCodec] onLoad failed for "
                            + component.getClass().getName(), error);
                }
            }
        }
        resolveMaterialAssets(loaded, services);
    }

    private void resolveMaterialAssets(List<GameObject> loaded, EngineServices services) {
        for (GameObject gameObject : loaded) {
            gameObject.getComponent(MeshRenderer.class)
                    .ifPresent(renderer -> replaceMaterialPlaceholders(renderer, services));
            gameObject.getComponent(MultiMeshRenderer.class)
                    .ifPresent(renderer -> renderer.material()
                            .ifPresent(material -> renderer.setMaterial(resolveMaterial(material, services))));
        }
    }

    private void replaceMaterialPlaceholders(MeshRenderer renderer, EngineServices services) {
        List<Material> replaced = new ArrayList<>();
        for (Material material : renderer.materials()) {
            replaced.add(resolveMaterial(material, services));
        }
        renderer.setMaterials(replaced);
    }

    private Material resolveMaterial(Material material, EngineServices services) {
        if (material.assetPath().isEmpty()) {
            return material;
        }
        try {
            Material resolved = services.assets().resolve(Material.class, material.assetPath()).orElse(material);
            MaterialFields.resolveTextures(resolved, services.assets());
            return resolved;
        } catch (RuntimeException error) {
            services.logger().warn("[GameObjectJsonCodec] Material asset unavailable, keeping placeholder: "
                    + material.assetPath() + " (" + error + ")");
            return material;
        }
    }

    private final class GraphReader implements ComponentFieldsCodec.ReferenceSink {

        private record PendingIndexReference(ExportedProperty property, int index) {
        }

        private record PendingIdReference(ExportedProperty property, String id) {
        }

        private final IdentityPolicy identityPolicy;
        private final List<GameObject> loaded = new ArrayList<>();
        private final List<Integer> parentIndices = new ArrayList<>();
        private final Map<String, GameObject> gameObjectsByFileId = new HashMap<>();
        private final List<PendingIndexReference> pendingIndexReferences = new ArrayList<>();
        private final List<PendingIdReference> pendingIdReferences = new ArrayList<>();
        private final List<String> incompatibleComponents = new ArrayList<>();
        private final List<String> unloadableComponents = new ArrayList<>();

        private GraphReader(IdentityPolicy identityPolicy) {
            this.identityPolicy = identityPolicy;
        }

        @Override
        public void referenceByIndex(ExportedProperty property, int index) {
            pendingIndexReferences.add(new PendingIndexReference(property, index));
        }

        @Override
        public void referenceById(ExportedProperty property, String id) {
            pendingIdReferences.add(new PendingIdReference(property, id));
        }

        @SuppressWarnings("unchecked")
        private List<GameObject> read(List<Object> gameObjectsJson) {
            for (Object element : gameObjectsJson) {
                buildGameObject((Map<String, Object>) element);
            }
            applyParentLinks();
            resolvePendingReferences();
            return loaded;
        }

        @SuppressWarnings("unchecked")
        private void buildGameObject(Map<String, Object> gameObjectJson) {
            GameObject gameObject = instantiateGameObject(gameObjectJson);
            applyFlags(gameObject, gameObjectJson);
            List<Object> componentsJson = (List<Object>) gameObjectJson.getOrDefault("components", List.of());
            for (Object componentObject : componentsJson) {
                attachComponent(gameObject, (Map<String, Object>) componentObject);
            }
            loaded.add(gameObject);
            parentIndices.add(parentIndexOf(gameObjectJson));
        }

        private GameObject instantiateGameObject(Map<String, Object> gameObjectJson) {
            String name = gameObjectJson.getOrDefault("name", "Unnamed").toString();
            Optional<String> fileId = gameObjectJson.get("id") instanceof String id && !id.isEmpty()
                    ? Optional.of(id) : Optional.empty();
            GameObject gameObject = fileId
                    .flatMap(this::parseRestoredId)
                    .map(id -> new GameObject(name, id))
                    .orElseGet(() -> new GameObject(name));
            fileId.ifPresent(id -> gameObjectsByFileId.put(id, gameObject));
            return gameObject;
        }

        private Optional<UUID> parseRestoredId(String fileId) {
            if (identityPolicy == IdentityPolicy.FRESH_IDS) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(fileId));
            } catch (IllegalArgumentException malformed) {
                return Optional.empty();
            }
        }

        private void applyFlags(GameObject gameObject, Map<String, Object> gameObjectJson) {
            if (gameObjectJson.get("tag") instanceof String tag) {
                gameObject.setTag(tag);
            }
            if (gameObjectJson.get("active") instanceof Boolean active) {
                gameObject.setActive(active);
            }
        }

        @SuppressWarnings("unchecked")
        private void attachComponent(GameObject gameObject, Map<String, Object> componentJson) {
            String typeName = componentJson.get("type") instanceof String name ? name : "";
            Map<String, Object> fields = (Map<String, Object>) componentJson.getOrDefault("fields", Map.of());
            if (LegacyRigidBodyMigration.matches(typeName, fields)) {
                LegacyRigidBodyMigration.migrate(gameObject, fields, this::instantiateComponent,
                        (component, migratedFields) -> fieldsCodec.applyFields(component, migratedFields, this));
                return;
            }
            Optional<ComponentRegistry.Entry> entryLookup = findEntry(typeName);
            if (entryLookup.isEmpty()) {
                gameObject.unloadableComponentPayloads().add(componentJson);
                unloadableComponents.add(typeName + " on '" + gameObject.name() + "'");
                return;
            }
            entryLookup.ifPresent(entry -> {
                IComponent component = entry.factory().get();
                IComponent existing = gameObject.getComponentOrNull(component.getClass());
                IComponent target = existing != null ? existing : component;
                fieldsCodec.applyFields(target, fields, this);
                applyMaterialsIfPresent(target, componentJson);
                applyPostEffectsIfPresent(target, componentJson);
                applyGraphOverridesIfPresent(target, componentJson);
                if (existing == null) {
                    attachTolerant(gameObject, component);
                }
            });
        }

        private void attachTolerant(GameObject gameObject, IComponent component) {
            try {
                gameObject.addComponent(component);
            } catch (ComponentException incompatible) {
                incompatibleComponents.add(component.getClass().getSimpleName()
                        + " on '" + gameObject.name() + "': " + incompatible.getMessage());
            }
        }

        private void applyMaterialsIfPresent(IComponent component, Map<String, Object> componentJson) {
            if (!(component instanceof MeshRenderer renderer)) {
                return;
            }
            if (!(componentJson.get("materials") instanceof List<?> materialsJson) || materialsJson.isEmpty()) {
                return;
            }
            List<Material> materials = new ArrayList<>();
            for (Object element : materialsJson) {
                if (element instanceof Map<?, ?> materialJson) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) materialJson;
                    materialCodec.readMaterial(typed).ifPresent(materials::add);
                }
            }
            if (!materials.isEmpty()) {
                renderer.setMaterials(materials);
            }
        }

        private void applyGraphOverridesIfPresent(IComponent component, Map<String, Object> componentJson) {
            if (!(component instanceof GraphComponent graph)) {
                return;
            }
            if (!(componentJson.get("graphVariables") instanceof Map<?, ?> overridesJson)) {
                return;
            }
            for (Map.Entry<?, ?> entry : overridesJson.entrySet()) {
                graph.variableOverrides().put(String.valueOf(entry.getKey()),
                        GraphValueJson.normalize(entry.getValue()));
            }
        }

        private void applyPostEffectsIfPresent(IComponent component, Map<String, Object> componentJson) {
            if (!(component instanceof Camera3D camera)) {
                return;
            }
            if (componentJson.get("postEffects") instanceof List<?> stackJson) {
                postEffectCodec.readStack(stackJson, camera.enablePostEffectStack());
            }
        }

        private IComponent instantiateComponent(Class<? extends IComponent> componentClass) {
            return componentRegistry.factoryFor(componentClass)
                    .map(Supplier::get)
                    .orElseThrow(() -> new EpysiaException(
                            "Component not registered for migration: " + componentClass.getName()));
        }

        private Optional<ComponentRegistry.Entry> findEntry(String typeName) {
            for (ComponentRegistry.Entry entry : componentRegistry.entries()) {
                if (entry.componentClass().getName().equals(typeName)) {
                    return Optional.of(entry);
                }
            }
            return Optional.empty();
        }

        private int parentIndexOf(Map<String, Object> gameObjectJson) {
            Object value = gameObjectJson.get("parentIndex");
            return value instanceof Number numericValue ? numericValue.intValue() : -1;
        }

        private void applyParentLinks() {
            for (int childIndex = 0; childIndex < loaded.size(); childIndex++) {
                int parentIndex = parentIndices.get(childIndex);
                if (parentIndex < 0 || parentIndex >= loaded.size()) {
                    continue;
                }
                linkTransforms(loaded.get(childIndex), loaded.get(parentIndex));
            }
        }

        private void linkTransforms(GameObject childObject, GameObject parentObject) {
            Optional<Transform3D> childTransform = childObject.getComponent(Transform3D.class);
            Optional<Transform3D> parentTransform = parentObject.getComponent(Transform3D.class);
            if (childTransform.isPresent() && parentTransform.isPresent()) {
                childTransform.get().setParent(parentTransform.get());
            }
        }

        private void resolvePendingReferences() {
            for (PendingIndexReference pending : pendingIndexReferences) {
                if (pending.index() < loaded.size()) {
                    pending.property().writeObject(loaded.get(pending.index()));
                }
            }
            for (PendingIdReference pending : pendingIdReferences) {
                GameObject target = gameObjectsByFileId.get(pending.id());
                if (target != null) {
                    pending.property().writeObject(target);
                }
            }
        }
    }
}
