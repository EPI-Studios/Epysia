package fr.epistudio.epysia.ui;

import org.joml.Vector4f;

public final class UiColors {
    private UiColors() {
    }

    public static UiColor of(Vector4f source) {
        return new UiColor(source.x(), source.y(), source.z(), source.w());
    }

    public static void copyInto(UiColor color, Vector4f destination) {
        destination.set(color.red(), color.green(), color.blue(), color.alpha());
    }
}
