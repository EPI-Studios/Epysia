package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.assets.epytilemap.CellBounds;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TilemapLayer;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.prefab.PrefabInstantiator;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import org.joml.Vector2f;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@EpysiaComponent(name = "Tilemap Scene Spawner", category = "2D",
        description = "Instantiates a scene for every marked tile of a tilemap.")
@RequiresComponent(Transform2D.class)
public final class TilemapSceneSpawner extends Component {
    @Export(label = "Tilemap")
    private final AssetRef<SpriteTilemap> tilemap = new AssetRef<>(SpriteTilemap.class);

    @Export(label = "Clear Painted Cells")
    private boolean clearPaintedCells = true;

    public AssetRef<SpriteTilemap> tilemapRef() {
        return tilemap;
    }

    public TilemapSceneSpawner setTilemapPath(String path) {
        tilemap.setPath(path);
        return this;
    }

    private transient SpriteTilemap spawnedFrom;

    public Optional<SpriteTilemap> tilemapValue() {
        return tilemap.direct();
    }

    @Override
    public void onLoad(EngineServices services) {
        tilemap.resolve(services.assets());
        tilemap.direct().ifPresent(map -> spawnOnce(services, map));
    }

    private void spawnOnce(EngineServices services, SpriteTilemap map) {
        if (map == spawnedFrom) {
            return;
        }
        spawnedFrom = map;
        spawnAll(services, map);
    }

    private void spawnAll(EngineServices services, SpriteTilemap map) {
        if (map.scenesByTile().isEmpty()) {
            return;
        }
        PrefabInstantiator instantiator = createInstantiator();
        for (int layerIndex = 0; layerIndex < map.layerCount(); layerIndex++) {
            spawnLayer(services, map, instantiator, layerIndex);
        }
    }

    private PrefabInstantiator createInstantiator() {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        return new PrefabInstantiator(registry);
    }

    private void spawnLayer(EngineServices services, SpriteTilemap map,
                            PrefabInstantiator instantiator, int layerIndex) {
        TilemapLayer layer = map.layer(layerIndex);
        CellBounds bounds = layer.usedBounds();
        for (int cellY = bounds.minY(); cellY <= bounds.maxY(); cellY++) {
            for (int cellX = bounds.minX(); cellX <= bounds.maxX(); cellX++) {
                Optional<String> scenePath = map.sceneForTile(layer.tileIndex(cellX, cellY));
                if (scenePath.isPresent()) {
                    spawnCell(services, map, instantiator, scenePath.get(), layerIndex, cellX, cellY);
                }
            }
        }
    }

    private void spawnCell(EngineServices services, SpriteTilemap map, PrefabInstantiator instantiator,
                           String path, int layerIndex, int cellX, int cellY) {
        Optional<Path> file = services.assets().locator().file(path);
        if (file.isEmpty()) {
            services.logger().warn("[TilemapSceneSpawner] cannot spawn '" + path + "': file not found");
            return;
        }
        try {
            GameObject spawned = instantiator.instantiate(file.get(), services.scene(), services);
            placeAtCell(spawned, map, cellX, cellY);
            if (clearPaintedCells) {
                map.setTile(layerIndex, cellX, cellY, SpriteTilemap.EMPTY_TILE_INDEX);
            }
        } catch (IOException unreadable) {
            services.logger().warn("[TilemapSceneSpawner] cannot spawn '" + path + "': " + unreadable.getMessage());
        }
    }

    private void placeAtCell(GameObject spawned, SpriteTilemap map, int cellX, int cellY) {
        Transform2D placed = spawned.getComponentOrNull(Transform2D.class);
        Transform2D origin = owner().map(owning -> owning.getComponentOrNull(Transform2D.class)).orElse(null);
        if (placed == null || origin == null) {
            return;
        }
        Vector2f position = origin.position();
        placed.setPosition(position.x + (cellX + 0.5f) * map.cellWidth(),
                position.y + (cellY + 0.5f) * map.cellHeight());
    }
}
