package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImGui;
import org.joml.Vector3fc;

import java.util.Optional;

public final class RigidBodyLiveSection {

    private static final String NOT_SIMULATED = "not simulated";

    public void render(RigidBodyComponent rigidBody, boolean playModeActive) {
        if (!playModeActive) {
            return;
        }
        ImGui.separator();
        ImGui.textDisabled("Live");
        row("Mass (computed)", rigidBody.computedMass().map(mass -> format(mass)));
        row("Center Of Mass", rigidBody.worldCenterOfMass().map(RigidBodyLiveSection::format));
        row("Velocity", rigidBody.velocity().map(RigidBodyLiveSection::format));
        row("Angular Velocity", rigidBody.angularVelocity().map(RigidBodyLiveSection::format));
        row("Sleeping", Optional.of(rigidBody.isAwake() ? "no" : "yes"));
    }

    private static void row(String label, Optional<String> value) {
        Texts.muted(label);
        ImGui.sameLine(LiveLayout.VALUE_COLUMN);
        ImGui.textUnformatted(value.orElse(NOT_SIMULATED));
    }

    private static String format(Vector3fc vector) {
        return String.format("%.2f  %.2f  %.2f", vector.x(), vector.y(), vector.z());
    }

    private static String format(float value) {
        return String.format("%.2f", value);
    }

    private static final class LiveLayout {
        private static final float VALUE_COLUMN = 170.0f;
    }
}
