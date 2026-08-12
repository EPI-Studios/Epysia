package fr.epistudio.epysia.navigation;

public record NavMeshSettings(float agentRadius, float agentHeight, float agentMaximumClimb,
                              float agentMaximumSlopeDegrees, float cellSize, float cellHeight,
                              float edgeMaximumLength, float edgeMaximumError,
                              int regionMinimumArea, int regionMergeArea,
                              float detailSampleDistance, float detailSampleMaximumError,
                              int tileSizeCells) {

    public static NavMeshSettings walkingCharacter() {
        return new NavMeshSettings(0.4f, 1.8f, 0.4f, 45.0f, 0.3f, 0.2f,
                12.0f, 1.3f, 8, 20, 6.0f, 1.0f, 64);
    }

    public NavMeshSettings withAgent(float radius, float height, float maximumClimb,
                                     float maximumSlopeDegrees) {
        return new NavMeshSettings(radius, height, maximumClimb, maximumSlopeDegrees,
                cellSize, cellHeight, edgeMaximumLength, edgeMaximumError,
                regionMinimumArea, regionMergeArea, detailSampleDistance, detailSampleMaximumError,
                tileSizeCells);
    }

    public NavMeshSettings withVoxelSize(float size, float height) {
        return new NavMeshSettings(agentRadius, agentHeight, agentMaximumClimb, agentMaximumSlopeDegrees,
                size, height, edgeMaximumLength, edgeMaximumError,
                regionMinimumArea, regionMergeArea, detailSampleDistance, detailSampleMaximumError,
                tileSizeCells);
    }

    public NavMeshSettings withTileSizeCells(int cells) {
        return new NavMeshSettings(agentRadius, agentHeight, agentMaximumClimb, agentMaximumSlopeDegrees,
                cellSize, cellHeight, edgeMaximumLength, edgeMaximumError,
                regionMinimumArea, regionMergeArea, detailSampleDistance, detailSampleMaximumError,
                Math.max(16, cells));
    }

    public float tileWorldSize() {
        return tileSizeCells * cellSize;
    }
}
