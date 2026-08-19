package fr.epistudio.epysia.render;

import org.lwjgl.system.Configuration;

public final class NativeStack {

    private static final int KILOBYTES = 512;

    private NativeStack() {
    }

    public static void widen() {
        if (Configuration.STACK_SIZE.get() == null) {
            Configuration.STACK_SIZE.set(KILOBYTES);
        }
    }
}
