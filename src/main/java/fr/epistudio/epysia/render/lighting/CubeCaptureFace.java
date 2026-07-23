package fr.epistudio.epysia.render.lighting;

import org.joml.Vector3f;

public final class CubeCaptureFace {

    public static final int COUNT = 6;

    private static final CubeCaptureFace[] FACES = createFaces();

    private final Vector3f forward;
    private final Vector3f up;
    private final Vector3f right;

    private CubeCaptureFace(Vector3f forward, Vector3f up) {
        this.forward = forward;
        this.up = up;
        this.right = new Vector3f(forward).cross(up).normalize();
    }

    public static CubeCaptureFace at(int index) {
        return FACES[index];
    }

    public Vector3f forward(Vector3f destination) {
        return destination.set(forward);
    }

    public Vector3f up(Vector3f destination) {
        return destination.set(up);
    }

    public Vector3f direction(int x, int y, int faceSize, Vector3f destination) {
        float u = planeCoordinate(x, faceSize);
        float v = planeCoordinate(y, faceSize);
        destination.set(forward);
        destination.fma(u, right);
        destination.fma(v, up);
        return destination.normalize();
    }

    public static float solidAngle(int x, int y, int faceSize) {
        float u = planeCoordinate(x, faceSize);
        float v = planeCoordinate(y, faceSize);
        float lengthSquared = 1.0f + u * u + v * v;
        float area = 4.0f / (faceSize * (float) faceSize);
        return area / (lengthSquared * (float) Math.sqrt(lengthSquared));
    }

    private static float planeCoordinate(int texel, int faceSize) {
        return 2.0f * (texel + 0.5f) / faceSize - 1.0f;
    }

    private static CubeCaptureFace[] createFaces() {
        return new CubeCaptureFace[]{
                new CubeCaptureFace(new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f)),
                new CubeCaptureFace(new Vector3f(-1.0f, 0.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f)),
                new CubeCaptureFace(new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f(0.0f, 0.0f, -1.0f)),
                new CubeCaptureFace(new Vector3f(0.0f, -1.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f)),
                new CubeCaptureFace(new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f(0.0f, 1.0f, 0.0f)),
                new CubeCaptureFace(new Vector3f(0.0f, 0.0f, -1.0f), new Vector3f(0.0f, 1.0f, 0.0f))};
    }
}
