package fr.epistudio.epysia.steam;

import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.logging.Logger;

import java.util.Optional;

public final class SteamService {

    private SteamFriendsInfo friendsInfo;

    private final SteamRuntime runtime;
    private Optional<SteamLobbies> lobbies = Optional.empty();
    private Optional<SteamAchievements> achievements = Optional.empty();
    private Optional<SteamCloud> cloud = Optional.empty();

    public SteamService(SteamRuntime runtime) {
        this.runtime = runtime;
    }

    public static SteamService detached() {
        return new SteamService(new SteamRuntime(new ConsoleLogger(System.err)));
    }

    public boolean start(SteamConfig config) {
        if (!runtime.start(config)) {
            return false;
        }
        if (lobbies.isEmpty()) {
            openServices();
        }
        return true;
    }

    private void openServices() {
        SteamLobbies openedLobbies = new SteamLobbies();
        lobbies = Optional.of(openedLobbies);
        achievements = Optional.of(new SteamAchievements());
        cloud = Optional.of(new SteamCloud());
        runtime.setJoinRequests(new JoinRequestRelay(openedLobbies));
    }

    private record JoinRequestRelay(SteamLobbies lobbies) implements SteamJoinRequests {

        @Override
        public void onLobbyJoinRequested(long lobbyId, long fromFriendId) {
            lobbies.listener().onJoinRequested(lobbyId, fromFriendId);
        }

        @Override
        public void onConnectStringRequested(String connectString, long fromFriendId) {
            lobbies.listener().onConnectStringRequested(connectString, fromFriendId);
        }
    }

    public void runCallbacks() {
        runtime.runCallbacks();
    }

    public void stop() {
        lobbies.ifPresent(SteamLobbies::dispose);
        achievements.ifPresent(SteamAchievements::dispose);
        cloud.ifPresent(SteamCloud::dispose);
        lobbies = Optional.empty();
        achievements = Optional.empty();
        cloud = Optional.empty();
        runtime.stop();
    }

    public boolean available() {
        return runtime.available();
    }

    public long steamId() {
        return runtime.steamId();
    }

    public String personaName() {
        return runtime.personaName();
    }

    public int appId() {
        return runtime.appId();
    }

    public boolean overlayEnabled() {
        return runtime.overlayEnabled();
    }

    public Optional<SteamFriendsInfo> friends() {
        if (friendsInfo == null) {
            friendsInfo = runtime.friends()
                    .flatMap(friends -> runtime.utils().map(utils -> new SteamFriendsInfo(friends, utils)))
                    .orElse(null);
            if (friendsInfo != null) {
                runtime.onPersonaChanged(friendsInfo::forget);
            }
        }
        return Optional.ofNullable(friendsInfo);
    }

    public Optional<SteamApplication> application() {
        return runtime.apps().map(SteamApplication::new);
    }

    public Optional<SteamPlatform> platform() {
        return runtime.utils().map(SteamPlatform::new);
    }

    public Optional<SteamLobbies> lobbies() {
        return lobbies;
    }

    public Optional<SteamAchievements> achievements() {
        return achievements;
    }

    public Optional<SteamCloud> cloud() {
        return cloud;
    }

    public boolean setRichPresence(String key, String value) {
        return runtime.setRichPresence(key, value);
    }

    public void clearRichPresence() {
        runtime.clearRichPresence();
    }

    public void openInviteDialog(long lobbyId) {
        runtime.openInviteDialog(lobbyId);
    }

    public Logger logger() {
        return runtime.logger();
    }
}
