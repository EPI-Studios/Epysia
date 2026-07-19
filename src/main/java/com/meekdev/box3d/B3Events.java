package com.meekdev.box3d;

import java.util.List;

// transient per step event data, valid until the next step
public final class B3Events {

    public record ContactBegin(B3Body bodyA, B3Body bodyB) {}

    public record ContactEnd(B3Body bodyA, B3Body bodyB) {}

    public record ContactHit(B3Body bodyA, B3Body bodyB, Vec3 point, Vec3 normal, float approachSpeed) {}

    public record BodyMove(B3Body body, Vec3 position, Quat rotation, boolean fellAsleep) {}

    public record Contacts(List<ContactBegin> begins, List<ContactEnd> ends, List<ContactHit> hits) {}

    private B3Events() {
    }
}
