package fr.epistudio.epysia.navigation;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EditorAction;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;

@EpysiaComponent(name = "Nav Mesh Surface", category = "Navigation")
public final class NavMeshSurface extends Component {

    @Export(label = "Bake On Load")
    private boolean bakeOnLoad = true;

    @Export(label = "Agent Radius", min = 0.05f, max = 5.0f, step = 0.05f)
    private float agentRadius = 0.4f;

    @Export(label = "Agent Height", min = 0.2f, max = 10.0f, step = 0.1f)
    private float agentHeight = 1.8f;

    @Export(label = "Agent Maximum Climb", min = 0.0f, max = 5.0f, step = 0.05f)
    private float agentMaximumClimb = 0.4f;

    @Export(label = "Agent Maximum Slope", min = 0.0f, max = 89.0f, step = 1.0f)
    private float agentMaximumSlopeDegrees = 45.0f;

    @Export(label = "Cell Size", min = 0.05f, max = 2.0f, step = 0.05f)
    private float cellSize = 0.3f;

    @Export(label = "Cell Height", min = 0.05f, max = 2.0f, step = 0.05f)
    private float cellHeight = 0.2f;

    @Export(label = "Tile Size Cells", min = 16.0f, max = 256.0f, step = 8.0f)
    private int tileSizeCells = 64;

    @Export(label = "Follow Streaming")
    private boolean followStreaming = true;

    @Export(label = "Streaming Radius", min = 8.0f, max = 500.0f, step = 1.0f)
    private float streamingRadius = 70.0f;

    @Export(label = "Tiles Per Frame", min = 1.0f, max = 8.0f, step = 1.0f)
    private int tilesPerFrame = 1;

    public NavMeshSettings settings() {
        return NavMeshSettings.walkingCharacter()
                .withAgent(agentRadius, agentHeight, agentMaximumClimb, agentMaximumSlopeDegrees)
                .withVoxelSize(cellSize, cellHeight)
                .withTileSizeCells(tileSizeCells);
    }

    public boolean followStreaming() {
        return followStreaming;
    }

    public float streamingRadius() {
        return streamingRadius;
    }

    public int tilesPerFrame() {
        return tilesPerFrame;
    }

    @Override
    public void onLoad(EngineServices services) {
        if (followStreaming) {
            services.navigation().reset(settings(), 0.0f, 0.0f);
            return;
        }
        if (bakeOnLoad) {
            bake(services);
        }
    }

    @EditorAction(label = "Bake Nav Mesh")
    public void bake(EngineServices services) {
        boolean built = services.navigation().bake(services.scene(), settings());
        services.logger().info("[NavMeshSurface] bake " + (built ? "reussi" : "sans polygone")
                + " sur " + services.navigation().bakedTriangleCount() + " triangles");
    }
}
