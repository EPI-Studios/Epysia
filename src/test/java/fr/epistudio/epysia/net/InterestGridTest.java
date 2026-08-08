package fr.epistudio.epysia.net;

import fr.epistudio.epysia.net.replication.NetworkInterestGrid;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InterestGridTest {
    private static final float CELL_SIZE = 16.0f;
    private static final float RADIUS = 20.0f;

    private final NetworkInterestGrid grid = new NetworkInterestGrid(CELL_SIZE);

    @Test
    void onlyObjectsInsideTheRadiusComeBack() {
        grid.rebuild(positions(Map.of(
                1, new Vector3f(0.0f, 0.0f, 0.0f),
                2, new Vector3f(10.0f, 0.0f, 0.0f),
                3, new Vector3f(200.0f, 0.0f, 0.0f))));
        Set<Integer> relevant = grid.within(new Vector3f(), RADIUS, Set.of());
        assertTrue(relevant.contains(1));
        assertTrue(relevant.contains(2));
        assertFalse(relevant.contains(3), "an object far outside the radius must not be relevant");
    }

    @Test
    void theRadiusIsExactRatherThanTheCellItLandsIn() {
        grid.rebuild(positions(Map.of(1, new Vector3f(RADIUS + 1.0f, 0.0f, 0.0f))));
        assertTrue(grid.within(new Vector3f(), RADIUS, Set.of()).isEmpty(),
                "an object in a covered cell but past the radius must still be excluded");
    }

    @Test
    void alwaysRelevantObjectsSurviveWhateverTheDistance() {
        grid.rebuild(positions(Map.of(1, new Vector3f(500.0f, 0.0f, 0.0f))));
        assertTrue(grid.within(new Vector3f(), RADIUS, Set.of(1)).contains(1));
    }

    @Test
    void heightDoesNotSeparateObjectsButDistanceStillDoes() {
        grid.rebuild(positions(Map.of(
                1, new Vector3f(0.0f, 5.0f, 0.0f),
                2, new Vector3f(0.0f, 500.0f, 0.0f))));
        Set<Integer> relevant = grid.within(new Vector3f(), RADIUS, Set.of());
        assertTrue(relevant.contains(1));
        assertFalse(relevant.contains(2), "the exact check is in three dimensions even though cells are not");
    }

    @Test
    void aQueryReadsOnlyTheCellsItsRadiusCovers() {
        Map<Integer, Vector3f> scattered = new LinkedHashMap<>();
        for (int index = 0; index < 500; index++) {
            scattered.put(index, new Vector3f(index * CELL_SIZE, 0.0f, 0.0f));
        }
        grid.rebuild(scattered);
        assertEquals(500, grid.cellCount(), "each object should have landed in its own cell");
        assertTrue(grid.within(new Vector3f(), RADIUS, Set.of()).size() < 5,
                "a tight query over a wide world must not return most of it");
    }

    private static Map<Integer, Vector3f> positions(Map<Integer, Vector3f> source) {
        return new LinkedHashMap<>(source);
    }
}
