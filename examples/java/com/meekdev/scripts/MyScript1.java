package com.meekdev.scripts;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scripting.Behaviour;

@EpysiaComponent(name = "MyScript1", category = "Scripts")
public final class MyScript1 extends Behaviour {

    @Export(label = "Speed", min = 0.0f, max = 100.0f, step = 0.1f)
    private float speed = 1.0f;

    private Transform3D transform;

    @Override
    public void onStart(EngineServices services) {
        transform = owner().orElseThrow().getComponent(Transform3D.class).orElse(null);
    }

    @Override
    public void onUpdate(InputState input, float deltaTimeSeconds) {
        if (transform == null) {
            return;
        }
        // example: spin around the Y axis at `speed` radians/second
        transform.rotateAxisAngle(0.0f, 1.0f, 0.0f, speed * deltaTimeSeconds);
    }
}
