package fr.epistudio.epysia.physics.box3d;

import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3Shape;

import java.util.ArrayList;
import java.util.List;

final class Box3dBodyState {

    final B3Body body;
    final boolean area;
    final List<B3Shape> shapes = new ArrayList<>();
    float shapeDensity = 1000.0f;
    long categoryBits;
    float desiredMass;

    Box3dBodyState(B3Body body, boolean area) {
        this.body = body;
        this.area = area;
    }
}
