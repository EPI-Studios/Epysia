package fr.epistudio.epysia.steam;

public interface SteamJoinRequests {

    void onLobbyJoinRequested(long lobbyId, long fromFriendId);

    void onConnectStringRequested(String connectString, long fromFriendId);
}
