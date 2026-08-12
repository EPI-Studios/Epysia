package fr.epistudio.epysia.steam;

import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamNativeHandle;
import com.codedisaster.steamworks.SteamUser;

import java.util.Optional;

public final class SteamIds {

    public static final long INVALID = 0L;

    private SteamIds() {
    }

    public static long rawOf(SteamUser user) {
        return rawOf(user.getSteamID());
    }

    public static long rawOf(SteamID steamId) {
        return steamId == null ? INVALID : SteamNativeHandle.getNativeHandle(steamId);
    }

    public static SteamID of(long raw) {
        return SteamID.createFromNativeHandle(raw);
    }

    public static Optional<SteamID> parse(String text) {
        try {
            long raw = Long.parseUnsignedLong(text.trim());
            return raw == INVALID ? Optional.empty() : Optional.of(of(raw));
        } catch (NumberFormatException notAnIdentifier) {
            return Optional.empty();
        }
    }

    public static String format(SteamID steamId) {
        return Long.toUnsignedString(rawOf(steamId));
    }
}
