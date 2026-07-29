package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.MultiMeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MergeIntoMultiMeshCommand implements EditorCommand {

    private final List<GameObject> sources;
    private final List<GameObject> created = new ArrayList<>();

    public MergeIntoMultiMeshCommand(List<GameObject> sources) {
        this.sources = List.copyOf(sources);
    }

    public static boolean mergeable(GameObject gameObject) {
        MeshRenderer renderer = gameObject.getComponentOrNull(MeshRenderer.class);
        Transform3D transform = gameObject.transform3DOrNull();
        return renderer != null && transform != null && transform.children().isEmpty()
                && renderer.meshOrNull() != null && renderer.materials().size() == 1;
    }

    @Override
    public void apply(CommandContext context) {
        created.clear();
        List<GameObject> merged = new ArrayList<>();
        for (Map.Entry<GroupKey, List<GameObject>> group : groupSources().entrySet()) {
            if (group.getValue().size() < 2) {
                continue;
            }
            created.add(buildGroupObject(group.getKey(), group.getValue()));
            merged.addAll(group.getValue());
        }
        created.forEach(context.scene()::addGameObject);
        merged.forEach(context.scene()::removeGameObject);
        merged.forEach(context.selection()::deselect);
        context.scene().advanceTick();
    }

    private Map<GroupKey, List<GameObject>> groupSources() {
        Map<GroupKey, List<GameObject>> groups = new LinkedHashMap<>();
        for (GameObject source : sources) {
            if (!mergeable(source)) {
                continue;
            }
            MeshRenderer renderer = source.getComponentOrNull(MeshRenderer.class);
            groups.computeIfAbsent(new GroupKey(renderer.meshOrNull(), renderer.materials().get(0),
                    renderer.castsShadows(), renderer.layerMask()), ignored -> new ArrayList<>()).add(source);
        }
        return groups;
    }

    private GameObject buildGroupObject(GroupKey key, List<GameObject> members) {
        List<Matrix4f> instances = new ArrayList<>(members.size());
        for (GameObject member : members) {
            instances.add(new Matrix4f(member.transform3DOrNull().worldMatrix()));
        }
        MultiMeshRenderer renderer = new MultiMeshRenderer();
        renderer.setMesh(key.mesh());
        copyMeshPath(members.get(0), renderer);
        renderer.setMaterial(key.material());
        renderer.setCastShadows(key.castsShadows());
        renderer.setLayerMask(key.layerMask());
        renderer.setInstances(instances);
        GameObject group = new GameObject(members.get(0).name() + " Instances");
        group.addComponent(new Transform3D());
        group.addComponent(renderer);
        return group;
    }

    private static void copyMeshPath(GameObject source, MultiMeshRenderer renderer) {
        MeshRenderer meshRenderer = source.getComponentOrNull(MeshRenderer.class);
        String path = meshRenderer.meshRef().path();
        if (!path.isEmpty()) {
            renderer.meshRef().setPath(path);
        }
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new SplitMultiMeshCommand(created, sources);
    }

    @Override
    public String label() {
        return "Merge " + sources.size() + " objects into MultiMesh";
    }

    private record GroupKey(UploadedMesh mesh, Material material, boolean castsShadows, int layerMask) {

        @Override
        public boolean equals(Object other) {
            return other instanceof GroupKey key && key.mesh == mesh && key.material == material
                    && key.castsShadows == castsShadows && key.layerMask == layerMask;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(mesh) * 31 + System.identityHashCode(material)
                    + (castsShadows ? 1 : 0) + layerMask * 7;
        }
    }

    private static final class SplitMultiMeshCommand implements EditorCommand {

        private final List<GameObject> created;
        private final List<GameObject> restored;

        private SplitMultiMeshCommand(List<GameObject> created, List<GameObject> restored) {
            this.created = created;
            this.restored = restored;
        }

        @Override
        public void apply(CommandContext context) {
            created.forEach(context.scene()::removeGameObject);
            restored.forEach(context.scene()::addGameObject);
            context.scene().advanceTick();
        }

        @Override
        public EditorCommand invert(CommandContext context) {
            return new MergeIntoMultiMeshCommand(restored);
        }

        @Override
        public String label() {
            return "Split MultiMesh back into " + restored.size() + " objects";
        }
    }
}
