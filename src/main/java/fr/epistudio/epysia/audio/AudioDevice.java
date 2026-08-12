package fr.epistudio.epysia.audio;

public final class AudioDevice {

    private static volatile boolean ready;

    private AudioDevice() {
    }

    public static boolean ready() {
        return ready;
    }

    static void markReady(boolean value) {
        ready = value;
    }
}
