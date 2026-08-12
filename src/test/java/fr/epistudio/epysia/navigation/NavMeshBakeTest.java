package fr.epistudio.epysia.navigation;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NavMeshBakeTest {

    private static Scene sceneWithFloor() {
        Scene scene = new Scene("navigation");
        GameObject floor = new GameObject("floor");
        floor.addComponent(new Transform3D().setPosition(0.0f, 0.0f, 0.0f));
        floor.addComponent(new BoxCollider().setHalfExtents(20.0f, 0.5f, 20.0f));
        scene.addGameObject(floor);
        scene.advanceTick();
        return scene;
    }

    @Test
    void aFlatFloorBakesIntoAWalkableSurface() {
        NavigationService service = new NavigationService();
        assertTrue(service.bake(sceneWithFloor(), NavMeshSettings.walkingCharacter()),
                "the bake produced no polygon from " + service.bakedTriangleCount() + " triangles");
        assertTrue(service.nearestPoint(new Vector3f(0.0f, 2.0f, 0.0f)).isPresent(),
                "no walkable point was found above the floor");
    }

    @Test
    void aPathCrossesTheFloorFromCornerToCorner() {
        NavigationService service = new NavigationService();
        service.bake(sceneWithFloor(), NavMeshSettings.walkingCharacter());
        List<Vector3f> path = service.findPath(new Vector3f(-15.0f, 1.0f, -15.0f),
                new Vector3f(15.0f, 1.0f, 15.0f));
        assertFalse(path.isEmpty(), "no path was returned across an open floor");
        Vector3f last = path.getLast();
        assertTrue(last.distance(new Vector3f(15.0f, last.y, 15.0f)) < 2.0f,
                "the path ended at " + last + " instead of the requested corner");
    }

    private static Scene sceneWithFloorAt(float centreX, float centreZ) {
        Scene scene = new Scene("navigation");
        GameObject floor = new GameObject("floor");
        floor.addComponent(new Transform3D().setPosition(centreX, 0.0f, centreZ));
        floor.addComponent(new BoxCollider().setHalfExtents(20.0f, 0.5f, 20.0f));
        scene.addGameObject(floor);
        scene.advanceTick();
        return scene;
    }

    @Test
    void streamedTilesAppearAroundTheFocusAndAreDroppedBehindIt() {
        NavigationService service = new NavigationService();
        service.reset(NavMeshSettings.walkingCharacter(), 0.0f, 0.0f);
        Scene scene = sceneWithFloorAt(0.0f, 0.0f);
        service.refreshAround(scene, new Vector3f(0.0f, 1.0f, 0.0f), 20.0f, 64);
        assertTrue(service.loadedTileCount() > 0, "no tile was baked around the focus");
        assertTrue(service.nearestPoint(new Vector3f(0.0f, 2.0f, 0.0f)).isPresent(),
                "the streamed tiles carry no walkable point");
        int nearby = service.loadedTileCount();
        service.refreshAround(scene, new Vector3f(600.0f, 1.0f, 600.0f), 20.0f, 64);
        assertTrue(service.loadedTileCount() < nearby,
                "tiles left behind were kept: " + service.loadedTileCount() + " of " + nearby);
    }

    @Test
    void aPathCrossesTwoAdjacentStreamedTiles() {
        NavigationService service = new NavigationService();
        NavMeshSettings settings = NavMeshSettings.walkingCharacter().withTileSizeCells(32);
        service.reset(settings, 0.0f, 0.0f);
        Scene scene = sceneWithFloorAt(0.0f, 0.0f);
        service.refreshAround(scene, new Vector3f(0.0f, 1.0f, 0.0f), 25.0f, 128);
        List<Vector3f> path = service.findPath(new Vector3f(-15.0f, 1.0f, 0.0f),
                new Vector3f(15.0f, 1.0f, 0.0f));
        assertFalse(path.isEmpty(), "no path crossed the tile boundary");
        Vector3f last = path.getLast();
        assertTrue(Math.abs(last.x - 15.0f) < 2.0f,
                "the path stopped at " + last + " instead of reaching the far tile");
    }
}
