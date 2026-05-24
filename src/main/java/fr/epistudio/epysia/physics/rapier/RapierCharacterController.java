package fr.epistudio.epysia.physics.rapier;

import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class RapierCharacterController implements AutoCloseable {

    private final RapierPhysicsWorld physicsWorld;
    private final MemorySegment world;
    private final MemorySegment controller;
    private final MemorySegment moveResultScratch;
    private final Arena scratchArena = Arena.ofShared();
    private boolean closed;

    public RapierCharacterController(RapierPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
        this.world = physicsWorld.nativeHandle();
        this.controller = RapierNativeBridge.characterControllerNew(world);
        this.moveResultScratch = scratchArena.allocate(16);
    }

    public MoveResult move(BodyHandle body, Vector3fc desiredDisplacement, float stepSeconds) {
        RapierNativeBridge.characterControllerMove(controller, world, body.id(),
                desiredDisplacement.x(), desiredDisplacement.y(), desiredDisplacement.z(),
                stepSeconds, moveResultScratch);
        return decodeMoveResult(moveResultScratch);
    }

    public MoveResult moveShape(ShapeDescriptor shape, Vector3fc position, Quaternionfc rotation,
                                Vector3fc desiredDisplacement, float stepSeconds) {
        long shapeHandle = physicsWorld.internShape(shape);
        RapierNativeBridge.characterControllerMoveShape(controller, world, shapeHandle,
                position.x(), position.y(), position.z(),
                rotation.x(), rotation.y(), rotation.z(), rotation.w(),
                desiredDisplacement.x(), desiredDisplacement.y(), desiredDisplacement.z(),
                stepSeconds, moveResultScratch);
        return decodeMoveResult(moveResultScratch);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RapierNativeBridge.characterControllerDrop(controller);
        scratchArena.close();
    }

    private static MoveResult decodeMoveResult(MemorySegment buffer) {
        float displacementX = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 0);
        float displacementY = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 1);
        float displacementZ = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 2);
        int groundedFlag = buffer.get(ValueLayout.JAVA_INT, 12L);
        return new MoveResult(new Vector3f(displacementX, displacementY, displacementZ), groundedFlag != 0);
    }

    public record MoveResult(Vector3fc correctedDisplacement, boolean grounded) {
    }
}
