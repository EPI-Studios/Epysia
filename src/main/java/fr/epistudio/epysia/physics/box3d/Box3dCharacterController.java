package fr.epistudio.epysia.physics.box3d;

import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3Mover;
import com.meekdev.box3d.Vec3;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.CharacterContact;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

public final class Box3dCharacterController implements AutoCloseable {

    private final Box3dPhysicsWorld physicsWorld;
    private final B3Mover mover;
    private final float centerToFeet;
    private boolean closed;

    public Box3dCharacterController(Box3dPhysicsWorld physicsWorld, float capsuleRadius,
                                    float capsuleHalfHeight, float maxSlopeDegrees) {
        this.physicsWorld = physicsWorld;
        this.mover = new B3Mover(physicsWorld.nativeWorld(), capsuleRadius,
                capsuleHalfHeight + capsuleRadius, maxSlopeDegrees);
        this.centerToFeet = capsuleHalfHeight + capsuleRadius;
    }

    public void setBodyFilter(LongPredicate filter) {
        mover.setBodyFilter(filter);
    }

    public MoveResult move(BodyHandle body, Vector3fc desiredDisplacement, float stepHeight,
                           boolean snapToGround) {
        B3Body nativeBody = physicsWorld.body(body);
        mover.setExcludedBody(nativeBody);
        Vec3 center = nativeBody.position();
        Vec3 feet = new Vec3(center.x(), center.y() - centerToFeet, center.z());
        B3Mover.MoveResult result = mover.move(feet,
                new Vec3(desiredDisplacement.x(), desiredDisplacement.y(), desiredDisplacement.z()),
                stepHeight, snapToGround);
        return new MoveResult(
                displacementOf(result, feet),
                toVector(result.clippedDelta()),
                result.grounded(),
                toVector(result.groundNormal()),
                toContacts(result.contacts()));
    }

    public boolean groundBelow(BodyHandle body, float depth) {
        Vec3 center = physicsWorld.body(body).position();
        return mover.groundBelow(new Vec3(center.x(), center.y() - centerToFeet, center.z()), depth);
    }

    private static Vector3f displacementOf(B3Mover.MoveResult result, Vec3 feet) {
        return new Vector3f(
                (float) (result.position().x() - feet.x()),
                (float) (result.position().y() - feet.y()),
                (float) (result.position().z() - feet.z()));
    }

    private static Vector3f toVector(Vec3 source) {
        return source == null ? new Vector3f() : new Vector3f((float) source.x(), (float) source.y(), (float) source.z());
    }

    private static List<CharacterContact> toContacts(List<B3Mover.Contact> nativeContacts) {
        if (nativeContacts == null || nativeContacts.isEmpty()) {
            return List.of();
        }
        List<CharacterContact> contacts = new ArrayList<>(nativeContacts.size());
        for (B3Mover.Contact contact : nativeContacts) {
            contacts.add(new CharacterContact(new BodyHandle(contact.body().key()),
                    toVector(contact.point()), toVector(contact.normal())));
        }
        return List.copyOf(contacts);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        mover.close();
    }

    public record MoveResult(Vector3fc correctedDisplacement, Vector3fc clippedDelta, boolean grounded,
                             Vector3fc groundNormal, List<CharacterContact> contacts) {
    }
}
