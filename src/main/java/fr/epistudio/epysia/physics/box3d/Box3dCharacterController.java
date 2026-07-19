package fr.epistudio.epysia.physics.box3d;

import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3Mover;
import com.meekdev.box3d.Vec3;
import fr.epistudio.epysia.physics.api.BodyHandle;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class Box3dCharacterController implements AutoCloseable {

    private static final float STEP_HEIGHT = 0.3f;

    private final Box3dPhysicsWorld physicsWorld;
    private final B3Mover mover;
    private final float centerToFeet;
    private boolean closed;

    public Box3dCharacterController(Box3dPhysicsWorld physicsWorld, float capsuleRadius, float capsuleHalfHeight) {
        this.physicsWorld = physicsWorld;
        this.mover = new B3Mover(physicsWorld.nativeWorld(), capsuleRadius, capsuleHalfHeight + capsuleRadius);
        this.centerToFeet = capsuleHalfHeight + capsuleRadius;
    }

    public MoveResult move(BodyHandle body, Vector3fc desiredDisplacement, float stepSeconds) {
        B3Body nativeBody = physicsWorld.body(body);
        mover.setExcludedBody(nativeBody);
        Vec3 center = nativeBody.position();
        Vec3 feet = new Vec3(center.x(), center.y() - centerToFeet, center.z());
        boolean descending = desiredDisplacement.y() <= 0.0f;
        B3Mover.MoveResult result = mover.move(feet,
                new Vec3(desiredDisplacement.x(), desiredDisplacement.y(), desiredDisplacement.z()),
                STEP_HEIGHT, descending);
        Vector3f correctedDisplacement = new Vector3f(
                (float) (result.position().x() - feet.x()),
                (float) (result.position().y() - feet.y()),
                (float) (result.position().z() - feet.z()));
        return new MoveResult(correctedDisplacement, result.grounded());
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        mover.close();
    }

    public record MoveResult(Vector3fc correctedDisplacement, boolean grounded) {
    }
}
