package fr.epistudio.epysia.physics.api;

public interface PhysicsDebugLines {
    void segment(float startX, float startY, float startZ,
                 float endX, float endY, float endZ, int color);
}
