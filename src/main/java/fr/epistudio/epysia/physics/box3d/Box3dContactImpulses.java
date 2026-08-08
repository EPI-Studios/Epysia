package fr.epistudio.epysia.physics.box3d;

import com.meekdev.box3d.B3ContactImpulses;
import fr.epistudio.epysia.physics.api.ContactImpulseSnapshot;

final class Box3dContactImpulses implements ContactImpulseSnapshot {
    private final B3ContactImpulses saved;

    Box3dContactImpulses(B3ContactImpulses saved) {
        this.saved = saved;
    }

    @Override
    public int restore() {
        return saved.restore();
    }

    @Override
    public int contactCount() {
        return saved.contactCount();
    }

    @Override
    public void close() {
        saved.close();
    }
}
