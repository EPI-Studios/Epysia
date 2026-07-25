package fr.epistudio.epysia.editor.gizmo;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.editor.command.builtin.TransformDragCommand;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class GizmoFollowers {

    private record Follower(Transform3D transform, Matrix4f leaderOffset, Vector3f beforePosition,
                            Quaternionf beforeRotation, Vector3f beforeScale) {
    }

    private final List<Follower> followers = new ArrayList<>();

    public void capture(Transform3D leader, List<Transform3D> transforms) {
        followers.clear();
        Matrix4f leaderInverse = new Matrix4f(leader.worldMatrix()).invert();
        for (Transform3D transform : transforms) {
            followers.add(capture(leaderInverse, transform));
        }
    }

    private static Follower capture(Matrix4f leaderInverse, Transform3D transform) {
        Matrix4f offset = new Matrix4f(leaderInverse).mul(transform.worldMatrix());
        return new Follower(transform, offset, new Vector3f(transform.position()),
                new Quaternionf(transform.rotation()), new Vector3f(transform.scale()));
    }

    public void follow(Transform3D leader, BiConsumer<Transform3D, Matrix4f> writer) {
        for (Follower follower : followers) {
            writer.accept(follower.transform(),
                    new Matrix4f(leader.worldMatrix()).mul(follower.leaderOffset()));
        }
    }

    public List<EditorCommand> rewindAll() {
        List<EditorCommand> commands = new ArrayList<>(followers.size());
        for (Follower follower : followers) {
            commands.add(rewind(follower));
        }
        followers.clear();
        return commands;
    }

    private static EditorCommand rewind(Follower follower) {
        Transform3D transform = follower.transform();
        TransformDragCommand command = new TransformDragCommand(transform,
                follower.beforePosition(), follower.beforeRotation(), follower.beforeScale(),
                new Vector3f(transform.position()), new Quaternionf(transform.rotation()),
                new Vector3f(transform.scale()));
        transform.setPosition(follower.beforePosition().x, follower.beforePosition().y,
                follower.beforePosition().z);
        transform.setRotation(follower.beforeRotation());
        transform.setScale(follower.beforeScale().x, follower.beforeScale().y, follower.beforeScale().z);
        transform.markDirty();
        return command;
    }

    public void clear() {
        followers.clear();
    }
}
