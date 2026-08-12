package fr.epistudio.epysia.steam;

import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamMatchmaking;
import com.codedisaster.steamworks.SteamMatchmakingCallback;
import com.codedisaster.steamworks.SteamResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SteamLobbies {

    private final SteamMatchmaking matchmaking;
    private final List<Long> lastResults = new ArrayList<>();
    private SteamLobbyListener listener = new SteamLobbyListener() {
    };
    private long currentLobby = SteamIds.INVALID;

    SteamLobbies() {
        matchmaking = new SteamMatchmaking(new Callbacks());
    }

    SteamLobbyListener listener() {
        return listener;
    }

    public SteamLobbies setListener(SteamLobbyListener value) {
        listener = value == null ? new SteamLobbyListener() {
        } : value;
        return this;
    }

    public void create(SteamLobbyVisibility visibility, int maximumMembers) {
        matchmaking.createLobby(visibility.lobbyType(), Math.max(1, maximumMembers));
    }

    public void join(long lobbyId) {
        matchmaking.joinLobby(SteamIds.of(lobbyId));
    }

    public void leave() {
        if (currentLobby == SteamIds.INVALID) {
            return;
        }
        matchmaking.leaveLobby(SteamIds.of(currentLobby));
        currentLobby = SteamIds.INVALID;
    }

    public void requestList() {
        matchmaking.requestLobbyList();
    }

    public void filterByString(String key, String value) {
        matchmaking.addRequestLobbyListStringFilter(key, value,
                SteamMatchmaking.LobbyComparison.Equal);
    }

    public void limitResults(int maximumResults) {
        matchmaking.addRequestLobbyListResultCountFilter(Math.max(1, maximumResults));
    }

    public boolean invite(long inviteeId) {
        return currentLobby != SteamIds.INVALID
                && matchmaking.inviteUserToLobby(SteamIds.of(currentLobby), SteamIds.of(inviteeId));
    }

    public Optional<Long> current() {
        return currentLobby == SteamIds.INVALID ? Optional.empty() : Optional.of(currentLobby);
    }

    public long owner(long lobbyId) {
        return SteamIds.rawOf(matchmaking.getLobbyOwner(SteamIds.of(lobbyId)));
    }

    public List<Long> members() {
        if (currentLobby == SteamIds.INVALID) {
            return List.of();
        }
        SteamID lobby = SteamIds.of(currentLobby);
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < matchmaking.getNumLobbyMembers(lobby); index++) {
            ids.add(SteamIds.rawOf(matchmaking.getLobbyMemberByIndex(lobby, index)));
        }
        return List.copyOf(ids);
    }

    public String dataOf(long lobbyId, String key) {
        return matchmaking.getLobbyData(SteamIds.of(lobbyId), key);
    }

    public boolean setData(String key, String value) {
        return currentLobby != SteamIds.INVALID
                && matchmaking.setLobbyData(SteamIds.of(currentLobby), key, value);
    }

    void dispose() {
        matchmaking.dispose();
    }

    private final class Callbacks implements SteamMatchmakingCallback {

        @Override
        public void onLobbyCreated(SteamResult result, SteamID steamIDLobby) {
            boolean succeeded = result == SteamResult.OK;
            long lobbyId = SteamIds.rawOf(steamIDLobby);
            if (succeeded) {
                currentLobby = lobbyId;
            }
            listener.onLobbyCreated(lobbyId, succeeded);
            if (succeeded) {
                listener.onLobbyEntered(lobbyId, true);
            }
        }

        @Override
        public void onLobbyEnter(SteamID steamIDLobby, int chatPermissions, boolean blocked,
                                 SteamMatchmaking.ChatRoomEnterResponse response) {
            boolean succeeded = response == SteamMatchmaking.ChatRoomEnterResponse.Success;
            if (succeeded) {
                currentLobby = SteamIds.rawOf(steamIDLobby);
            }
            listener.onLobbyEntered(SteamIds.rawOf(steamIDLobby), succeeded);
        }

        @Override
        public void onLobbyMatchList(int lobbiesMatching) {
            lastResults.clear();
            for (int index = 0; index < lobbiesMatching; index++) {
                lastResults.add(SteamIds.rawOf(matchmaking.getLobbyByIndex(index)));
            }
            listener.onLobbyListReceived(List.copyOf(lastResults));
        }

        @Override
        public void onLobbyChatUpdate(SteamID steamIDLobby, SteamID steamIDUserChanged,
                                      SteamID steamIDMakingChange,
                                      SteamMatchmaking.ChatMemberStateChange stateChange) {
            long lobby = SteamIds.rawOf(steamIDLobby);
            long member = SteamIds.rawOf(steamIDUserChanged);
            if (stateChange == SteamMatchmaking.ChatMemberStateChange.Entered) {
                listener.onMemberJoined(lobby, member);
                return;
            }
            listener.onMemberLeft(lobby, member);
        }

        @Override
        public void onLobbyInvite(SteamID steamIDUser, SteamID steamIDLobby, long gameId) {
            listener.onInviteReceived(SteamIds.rawOf(steamIDLobby), SteamIds.rawOf(steamIDUser));
        }
    }
}
