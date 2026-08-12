package fr.epistudio.epysia.steam;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamLibraryLoader;
import com.codedisaster.steamworks.SteamLibraryLoaderLwjgl3;

public final class SteamPresence {

    private static final long REFRESH_NANOS = 2_000_000_000L;

    private static Boolean librariesLoaded;
    private static boolean clientRunning;
    private static long checkedAtNanos;

    private SteamPresence() {
    }

    public static synchronized boolean librariesAvailable() {
        if (librariesLoaded == null) {
            librariesLoaded = loadLibraries();
        }
        return librariesLoaded;
    }

    public static synchronized boolean clientRunning() {
        if (!librariesAvailable()) {
            return false;
        }
        long now = System.nanoTime();
        if (now - checkedAtNanos < REFRESH_NANOS && checkedAtNanos != 0L) {
            return clientRunning;
        }
        checkedAtNanos = now;
        clientRunning = probe();
        return clientRunning;
    }

    private static boolean loadLibraries() {
        try {
            SteamLibraryLoader loader = new SteamLibraryLoaderLwjgl3();
            return SteamAPI.loadLibraries(loader);
        } catch (RuntimeException | UnsatisfiedLinkError | NoClassDefFoundError unavailable) {
            return false;
        }
    }

    private static boolean probe() {
        try {
            return SteamAPI.isSteamRunning();
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            return false;
        }
    }
}
