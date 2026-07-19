package fr.epistudio.epysia.render.environment;

import org.joml.Vector3f;

record CubeFaceBasis(Vector3f forward, Vector3f right, Vector3f up) {

    static final CubeFaceBasis[] FACES = {
            new CubeFaceBasis(new Vector3f(1, 0, 0), new Vector3f(0, 0, -1), new Vector3f(0, -1, 0)),
            new CubeFaceBasis(new Vector3f(-1, 0, 0), new Vector3f(0, 0, 1), new Vector3f(0, -1, 0)),
            new CubeFaceBasis(new Vector3f(0, 1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, 1)),
            new CubeFaceBasis(new Vector3f(0, -1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, -1)),
            new CubeFaceBasis(new Vector3f(0, 0, 1), new Vector3f(1, 0, 0), new Vector3f(0, -1, 0)),
            new CubeFaceBasis(new Vector3f(0, 0, -1), new Vector3f(-1, 0, 0), new Vector3f(0, -1, 0))
    };
}
