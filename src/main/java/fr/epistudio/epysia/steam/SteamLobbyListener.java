package fr.epistudio.epysia.steam;

import java.util.List;

public interface SteamLobbyListener {

    default void onLobbyCreated(long lobbyId, boolean succeeded) {
    }

    default void onLobbyEntered(long lobbyId, boolean succeeded) {
    }

    default void onLobbyListReceived(List<Long> lobbyIds) {
    }

    default void onMemberJoined(long lobbyId, long memberId) {
    }

    default void onMemberLeft(long lobbyId, long memberId) {
    }

    default void onInviteReceived(long lobbyId, long fromMemberId) {
    }

    default void onJoinRequested(long lobbyId, long fromFriendId) {
    }

    default void onConnectStringRequested(String connectString, long fromFriendId) {
    }
}
