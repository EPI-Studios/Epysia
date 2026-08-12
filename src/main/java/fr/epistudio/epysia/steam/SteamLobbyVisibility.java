package fr.epistudio.epysia.steam;

import com.codedisaster.steamworks.SteamMatchmaking;

public enum SteamLobbyVisibility {
    PRIVATE(SteamMatchmaking.LobbyType.Private),
    FRIENDS_ONLY(SteamMatchmaking.LobbyType.FriendsOnly),
    PUBLIC(SteamMatchmaking.LobbyType.Public),
    INVISIBLE(SteamMatchmaking.LobbyType.Invisible);

    private final SteamMatchmaking.LobbyType lobbyType;

    SteamLobbyVisibility(SteamMatchmaking.LobbyType lobbyType) {
        this.lobbyType = lobbyType;
    }

    SteamMatchmaking.LobbyType lobbyType() {
        return lobbyType;
    }
}
