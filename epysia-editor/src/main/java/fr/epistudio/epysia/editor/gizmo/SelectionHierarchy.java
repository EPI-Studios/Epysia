package fr.epistudio.epysia.editor.gizmo;

import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class SelectionHierarchy {

    private SelectionHierarchy() {
    }

    public static List<GameObject> meshObjectsUnder(List<GameObject> roots) {
        Set<GameObject> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<GameObject> meshObjects = new ArrayList<>();
        for (GameObject root : roots) {
            collectMeshObjects(root, visited, meshObjects);
        }
        return meshObjects;
    }

    public static boolean hasMeshInHierarchy(GameObject gameObject) {
        Set<GameObject> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<GameObject> meshObjects = new ArrayList<>();
        collectMeshObjects(gameObject, visited, meshObjects);
        return !meshObjects.isEmpty();
    }

    private static void collectMeshObjects(GameObject gameObject, Set<GameObject> visited,
                                           List<GameObject> destination) {
        if (!visited.add(gameObject)) {
            return;
        }
        if (gameObject.getComponent(MeshRenderer.class).isPresent()) {
            destination.add(gameObject);
        }
        gameObject.getComponent(Transform3D.class)
                .ifPresent(transform -> collectChildren(transform, visited, destination));
    }

    private static void collectChildren(Transform3D transform, Set<GameObject> visited,
                                        List<GameObject> destination) {
        for (Transform3D child : transform.children()) {
            child.owner().ifPresent(owner -> collectMeshObjects(owner, visited, destination));
        }
    }
}
