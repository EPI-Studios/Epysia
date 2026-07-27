package fr.epistudio.epysia.render.mesh;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SurfacePopulator {

    public enum UpAxis { X, Y, Z }

    public record Settings(int amount, UpAxis upAxis, float rotateRandom, float tiltRandom,
                           float scale, float scaleRandom, int seed) {

        public static Settings defaults() {
            return new Settings(1000, UpAxis.Y, 1.0f, 0.0f, 1.0f, 0.0f, 1);
        }
    }

    private SurfacePopulator() {
    }

    public static List<Matrix4f> populate(MeshData surface, Matrix4f surfaceToWorld, Settings settings) {
        List<Matrix4f> instances = new ArrayList<>(Math.max(0, settings.amount()));
        float[] cumulativeAreas = triangleAreas(surface, surfaceToWorld);
        if (cumulativeAreas.length == 0 || settings.amount() <= 0) {
            return instances;
        }
        Random random = new Random(settings.seed());
        float totalArea = cumulativeAreas[cumulativeAreas.length - 1];
        for (int index = 0; index < settings.amount(); index++) {
            int triangle = pickTriangle(cumulativeAreas, random.nextFloat() * totalArea);
            instances.add(instanceOn(surface, surfaceToWorld, triangle, random, settings));
        }
        return instances;
    }

    private static float[] triangleAreas(MeshData surface, Matrix4f surfaceToWorld) {
        int triangleCount = surface.indices().length / 3;
        float[] cumulative = new float[triangleCount];
        Vector3f first = new Vector3f();
        Vector3f second = new Vector3f();
        Vector3f third = new Vector3f();
        float running = 0.0f;
        for (int triangle = 0; triangle < triangleCount; triangle++) {
            readCorners(surface, surfaceToWorld, triangle, first, second, third);
            running += second.sub(first, new Vector3f()).cross(third.sub(first, new Vector3f())).length() * 0.5f;
            cumulative[triangle] = running;
        }
        return cumulative;
    }

    private static void readCorners(MeshData surface, Matrix4f surfaceToWorld, int triangle,
                                    Vector3f first, Vector3f second, Vector3f third) {
        readVertex(surface, surfaceToWorld, surface.indices()[triangle * 3], first);
        readVertex(surface, surfaceToWorld, surface.indices()[triangle * 3 + 1], second);
        readVertex(surface, surfaceToWorld, surface.indices()[triangle * 3 + 2], third);
    }

    private static void readVertex(MeshData surface, Matrix4f surfaceToWorld, int index, Vector3f destination) {
        destination.set(surface.positions()[index * 3],
                surface.positions()[index * 3 + 1],
                surface.positions()[index * 3 + 2]);
        surfaceToWorld.transformPosition(destination);
    }

    private static int pickTriangle(float[] cumulativeAreas, float target) {
        int low = 0;
        int high = cumulativeAreas.length - 1;
        while (low < high) {
            int middle = (low + high) / 2;
            if (cumulativeAreas[middle] < target) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static Matrix4f instanceOn(MeshData surface, Matrix4f surfaceToWorld, int triangle,
                                       Random random, Settings settings) {
        Vector3f first = new Vector3f();
        Vector3f second = new Vector3f();
        Vector3f third = new Vector3f();
        readCorners(surface, surfaceToWorld, triangle, first, second, third);
        Vector3f point = barycentricPoint(first, second, third, random);
        Vector3f normal = second.sub(first, new Vector3f())
                .cross(third.sub(first, new Vector3f()))
                .normalize();
        return orientedInstance(point, normal, random, settings);
    }

    private static Vector3f barycentricPoint(Vector3f first, Vector3f second, Vector3f third, Random random) {
        float weightA = random.nextFloat();
        float weightB = random.nextFloat();
        if (weightA + weightB > 1.0f) {
            weightA = 1.0f - weightA;
            weightB = 1.0f - weightB;
        }
        return new Vector3f(first)
                .fma(weightA, second.sub(first, new Vector3f()))
                .fma(weightB, third.sub(first, new Vector3f()));
    }

    private static Matrix4f orientedInstance(Vector3f point, Vector3f surfaceNormal,
                                             Random random, Settings settings) {
        Vector3f up = tiltedNormal(surfaceNormal, random, settings.tiltRandom());
        Matrix4f model = new Matrix4f().translation(point);
        model.mul(alignmentOf(settings.upAxis(), up));
        model.rotateY(random.nextFloat() * (float) (Math.PI * 2.0) * settings.rotateRandom());
        float scale = settings.scale() * (1.0f - random.nextFloat() * settings.scaleRandom());
        return model.scale(Math.max(scale, 1.0e-4f));
    }

    private static Vector3f tiltedNormal(Vector3f surfaceNormal, Random random, float tiltRandom) {
        if (tiltRandom <= 0.0f) {
            return new Vector3f(surfaceNormal);
        }
        Vector3f jitter = new Vector3f(
                random.nextFloat() * 2.0f - 1.0f,
                random.nextFloat() * 2.0f - 1.0f,
                random.nextFloat() * 2.0f - 1.0f).mul(tiltRandom);
        return new Vector3f(surfaceNormal).add(jitter).normalize();
    }

    private static Matrix4f alignmentOf(UpAxis upAxis, Vector3f target) {
        Vector3f localUp = switch (upAxis) {
            case X -> new Vector3f(1.0f, 0.0f, 0.0f);
            case Y -> new Vector3f(0.0f, 1.0f, 0.0f);
            case Z -> new Vector3f(0.0f, 0.0f, 1.0f);
        };
        return new Matrix4f().rotation(new org.joml.Quaternionf().rotationTo(localUp, target));
    }
}
