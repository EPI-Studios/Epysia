package fr.epistudio.epysia.components.transforms;

import java.util.List;

public final class TransformResolver {
    private int resolvedThisPass;

    public void resolve(List<Transform3D> transforms, float alpha) {
        resolvedThisPass = 0;
        for (int index = 0; index < transforms.size(); index++) {
            Transform3D transform = transforms.get(index);
            if (transform.parent().isEmpty()) {
                resolveSubtree(transform, alpha);
            }
        }
    }

    public int resolvedThisPass() {
        return resolvedThisPass;
    }

    private void resolveSubtree(Transform3D transform, float alpha) {
        transform.worldMatrix();
        if (!transform.worldMatrixStable(alpha)) {
            transform.worldMatrix(alpha);
        }
        resolvedThisPass++;
        List<Transform3D> children = transform.children();
        for (int index = 0; index < children.size(); index++) {
            resolveSubtree(children.get(index), alpha);
        }
    }
}
