package fr.epistudio.epysia.steam;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamException;
import com.codedisaster.steamworks.SteamApps;
import com.codedisaster.steamworks.SteamFriends;
import com.codedisaster.steamworks.SteamFriendsCallback;
import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamLibraryLoader;
import com.codedisaster.steamworks.SteamLibraryLoaderLwjgl3;
import com.codedisaster.steamworks.SteamUser;
import com.codedisaster.steamworks.SteamUserCallback;
import com.codedisaster.steamworks.SteamUtils;
import com.codedisaster.steamworks.SteamUtilsCallback;
import fr.epistudio.epysia.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;

public final class SteamRuntime {

    private static final String LOG_PREFIX = "[steam] ";
    private static final String APP_ID_FILE = "steam_appid.txt";

    private static boolean librariesLoaded;

    private final Logger logger;
    private SteamConfig config = new SteamConfig();
    private boolean running;
    private Optional<SteamUser> user = Optional.empty();
    private Optional<SteamFriends> friends = Optional.empty();
    private Optional<SteamUtils> utils = Optional.empty();
    private Optional<SteamApps> apps = Optional.empty();
    private final List<LongConsumer> personaChanges = new ArrayList<>();
    private Optional<SteamJoinRequests> joinRequests = Optional.empty();

    public SteamRuntime(Logger logger) {
        this.logger = logger;
    }

    public boolean start(SteamConfig requested) {
        if (running) {
            return true;
        }
        config = requested;
        if (!loadLibraries() || !initialize()) {
            return failOrWarn();
        }
        openInterfaces();
        running = true;
        logger.info(LOG_PREFIX + "ready as " + personaName() + " (" + steamId() + ") on app " + appId());
        return true;
    }

    private boolean loadLibraries() {
        if (librariesLoaded) {
            return true;
        }
        try {
            SteamLibraryLoader loader = new SteamLibraryLoaderLwjgl3();
            librariesLoaded = SteamAPI.loadLibraries(loader);
            if (!librariesLoaded) {
                logger.warn(LOG_PREFIX + "native libraries could not be loaded");
            }
            return librariesLoaded;
        } catch (RuntimeException | UnsatisfiedLinkError failure) {
            logger.warn(LOG_PREFIX + "native libraries unavailable: " + failure.getMessage());
            return false;
        }
    }

    private boolean initialize() {
        writeAppIdFile();
        try {
            if (SteamAPI.init()) {
                return true;
            }
            logger.warn(LOG_PREFIX + "init failed, is the Steam client running?");
            return false;
        } catch (SteamException failure) {
            logger.warn(LOG_PREFIX + "init refused: " + failure.getMessage());
            return false;
        }
    }

    private void writeAppIdFile() {
        if (!config.writeAppIdFile()) {
            return;
        }
        Path file = Path.of(APP_ID_FILE);
        try {
            Files.writeString(file, Integer.toString(config.appId()), StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            logger.warn(LOG_PREFIX + "could not write " + file.toAbsolutePath()
                    + ": " + unwritable.getMessage());
        }
    }

    private void openInterfaces() {
        user = Optional.of(new SteamUser(new SteamUserCallback() {
        }));
        friends = Optional.of(new SteamFriends(new SteamFriendsCallback() {
            @Override
            public void onGameLobbyJoinRequested(SteamID steamIDLobby, SteamID steamIDFriend) {
                joinRequests.ifPresent(handler -> handler.onLobbyJoinRequested(
                        SteamIds.rawOf(steamIDLobby), SteamIds.rawOf(steamIDFriend)));
            }

            @Override
            public void onPersonaStateChange(SteamID steamIDUser, SteamFriends.PersonaChange change) {
                personaChanges.forEach(listener -> listener.accept(SteamIds.rawOf(steamIDUser)));
            }

            @Override
            public void onGameRichPresenceJoinRequested(SteamID steamIDFriend, String connect) {
                joinRequests.ifPresent(handler -> handler.onConnectStringRequested(
                        connect, SteamIds.rawOf(steamIDFriend)));
            }
        }));
        utils = Optional.of(new SteamUtils(new SteamUtilsCallback() {
        }));
        apps = Optional.of(new SteamApps());
    }

    private boolean failOrWarn() {
        if (config.required()) {
            throw new SteamUnavailableException("Steam is required but could not be initialised.");
        }
        logger.info(LOG_PREFIX + "continuing without Steam");
        return false;
    }

    public void runCallbacks() {
        if (running && SteamAPI.isSteamRunning()) {
            SteamAPI.runCallbacks();
        }
    }

    public void stop() {
        if (!running) {
            return;
        }
        user.ifPresent(SteamUser::dispose);
        friends.ifPresent(SteamFriends::dispose);
        utils.ifPresent(SteamUtils::dispose);
        apps.ifPresent(SteamApps::dispose);
        user = Optional.empty();
        friends = Optional.empty();
        utils = Optional.empty();
        running = false;
        SteamAPI.shutdown();
        logger.info(LOG_PREFIX + "shut down");
    }

    public boolean available() {
        return running;
    }

    public long steamId() {
        return user.map(SteamIds::rawOf).orElse(SteamIds.INVALID);
    }

    public String personaName() {
        return friends.map(SteamFriends::getPersonaName).orElse("");
    }

    public int appId() {
        return utils.map(SteamUtils::getAppID).orElse(config.appId());
    }

    public boolean overlayEnabled() {
        return utils.map(SteamUtils::isOverlayEnabled).orElse(false);
    }

    public void setJoinRequests(SteamJoinRequests handler) {
        joinRequests = Optional.ofNullable(handler);
    }

    public boolean setRichPresence(String key, String value) {
        return friends.map(steamFriends -> steamFriends.setRichPresence(key, value)).orElse(false);
    }

    public void clearRichPresence() {
        friends.ifPresent(SteamFriends::clearRichPresence);
    }

    public void openInviteDialog(long lobbyId) {
        friends.ifPresent(steamFriends ->
                steamFriends.activateGameOverlayInviteDialog(SteamIds.of(lobbyId)));
    }

    public void onPersonaChanged(LongConsumer listener) {
        personaChanges.add(listener);
    }

    public Optional<SteamApps> apps() {
        return apps;
    }

    public Optional<SteamUtils> utils() {
        return utils;
    }

    public Optional<SteamFriends> friends() {
        return friends;
    }

    public SteamConfig config() {
        return config;
    }

    public Logger logger() {
        return logger;
    }
}
