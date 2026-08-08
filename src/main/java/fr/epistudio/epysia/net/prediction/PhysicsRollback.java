package fr.epistudio.epysia.net.prediction;

import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.ContactImpulseSnapshot;
import fr.epistudio.epysia.physics.api.RigidBodyKind;
import fr.epistudio.epysia.physics.components.RigidBody2D;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.scene.Scene;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PhysicsRollback implements PredictedPhysics {
    private final PhysicsSystem physics;
    private final Scene scene;
    private final Set<Long> predictedBodies = new LinkedHashSet<>();
    private final List<RigidBodyState> frozenStates = new ArrayList<>();
    private final List<ContactImpulseSnapshot> savedImpulses = new ArrayList<>();
    private int replayedSteps;
    private int restoredImpulsePoints;

    public PhysicsRollback(PhysicsSystem physics, Scene scene) {
        this.physics = physics;
        this.scene = scene;
    }

    public PhysicsRollback predicting(GameObject gameObject) {
        RigidBodyComponent rigidBody = gameObject.getComponentOrNull(RigidBodyComponent.class);
        if (rigidBody != null && rigidBody.handle().isValid()) {
            predictedBodies.add(rigidBody.handle().id());
        }
        RigidBody2D rigidBody2D = gameObject.getComponentOrNull(RigidBody2D.class);
        if (rigidBody2D != null && rigidBody2D.handle().isValid()) {
            predictedBodies.add(rigidBody2D.handle().id());
        }
        return this;
    }

    public boolean hasPredictedBody() {
        return !predictedBodies.isEmpty();
    }

    public int replayedSteps() {
        return replayedSteps;
    }

    @Override
    public void beginReplay() {
        replayedSteps = 0;
        restoredImpulsePoints = 0;
        frozenStates.clear();
        for (BodyHandle body : otherDynamicBodies()) {
            RigidBodyState state = new RigidBodyState(body);
            state.captureFrom(physics.world());
            frozenStates.add(state);
        }
        saveContactImpulses();
    }

    private void saveContactImpulses() {
        releaseSavedImpulses();
        for (long bodyId : predictedBodies) {
            savedImpulses.add(physics.world().saveContactImpulses(new BodyHandle(bodyId)));
        }
    }

    private void releaseSavedImpulses() {
        for (ContactImpulseSnapshot snapshot : savedImpulses) {
            snapshot.close();
        }
        savedImpulses.clear();
    }

    public int restoredImpulsePoints() {
        return restoredImpulsePoints;
    }

    private List<BodyHandle> otherDynamicBodies() {
        List<BodyHandle> bodies = new ArrayList<>();
        for (RigidBodyComponent rigidBody : scene.componentsOf(RigidBodyComponent.class)) {
            addIfReplayableNeighbour(bodies, rigidBody.kind(), rigidBody.handle());
        }
        for (RigidBody2D rigidBody : scene.componentsOf(RigidBody2D.class)) {
            addIfReplayableNeighbour(bodies, rigidBody.kind(), rigidBody.handle());
        }
        return bodies;
    }

    private void addIfReplayableNeighbour(List<BodyHandle> bodies, RigidBodyKind kind, BodyHandle handle) {
        if (kind == RigidBodyKind.DYNAMIC && handle.isValid() && !predictedBodies.contains(handle.id())) {
            bodies.add(handle);
        }
    }

    @Override
    public void stepReplay(float deltaTimeSeconds) {
        restoreFrozenBodies();
        if (replayedSteps == 0) {
            restoreContactImpulses();
        }
        physics.stepOnce(scene, deltaTimeSeconds);
        replayedSteps++;
    }

    private void restoreContactImpulses() {
        for (ContactImpulseSnapshot snapshot : savedImpulses) {
            restoredImpulsePoints += snapshot.restore();
        }
    }

    @Override
    public void endReplay() {
        restoreFrozenBodies();
        releaseSavedImpulses();
        frozenStates.clear();
    }

    private void restoreFrozenBodies() {
        for (RigidBodyState state : frozenStates) {
            state.restoreInto(physics.world());
        }
    }
}
