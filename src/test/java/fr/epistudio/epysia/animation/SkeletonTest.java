package fr.epistudio.epysia.animation;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkeletonTest {

    private static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private static Joint joint(String name, int parent) {
        return new Joint(name, parent, identity(), identity());
    }

    @Test
    void acceptsTopologicalOrder() {
        Skeleton skeleton = new Skeleton(List.of(joint("root", -1), joint("spine", 0), joint("head", 1)));
        assertEquals(3, skeleton.jointCount());
    }

    @Test
    void rejectsChildBeforeParent() {
        assertThrows(EpysiaException.class,
                () -> new Skeleton(List.of(joint("spine", 1), joint("root", -1))));
    }

    @Test
    void rejectsSelfParent() {
        assertThrows(EpysiaException.class,
                () -> new Skeleton(List.of(joint("root", 0))));
    }

    @Test
    void checksumChangesWithJointNames() {
        Skeleton first = new Skeleton(List.of(joint("root", -1), joint("arm", 0)));
        Skeleton second = new Skeleton(List.of(joint("root", -1), joint("leg", 0)));
        assertNotEquals(first.nameChecksum(), second.nameChecksum());
        assertEquals(first.nameChecksum(),
                new Skeleton(List.of(joint("root", -1), joint("arm", 0))).nameChecksum());
    }
}
