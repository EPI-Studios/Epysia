package fr.epistudio.epysia.worldgen;

public record WorldRect(float minX, float minZ, float maxX, float maxZ) {

    public static WorldRect around(float centreX, float centreZ, float radius) {
        return new WorldRect(centreX - radius, centreZ - radius, centreX + radius, centreZ + radius);
    }

    public WorldRect expanded(float margin) {
        return new WorldRect(minX - margin, minZ - margin, maxX + margin, maxZ + margin);
    }

    public boolean contains(float worldX, float worldZ) {
        return worldX >= minX && worldX <= maxX && worldZ >= minZ && worldZ <= maxZ;
    }

    public float centreX() {
        return (minX + maxX) * 0.5f;
    }

    public float centreZ() {
        return (minZ + maxZ) * 0.5f;
    }
}
