package fr.epistudio.epysia.steam;

import com.codedisaster.steamworks.SteamException;
import com.codedisaster.steamworks.SteamFriends;
import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SteamFriendsInfo {

    private static final int BYTES_PER_PIXEL = 4;

    private final SteamFriends friends;
    private final SteamUtils utils;
    private final Map<String, SteamAvatar> avatars = new HashMap<>();

    public SteamFriendsInfo(SteamFriends friends, SteamUtils utils) {
        this.friends = friends;
        this.utils = utils;
    }

    public String personaName() {
        return friends.getPersonaName();
    }

    public String personaState() {
        return friends.getPersonaState().name();
    }

    public String nameOf(long steamId) {
        return friends.getFriendPersonaName(SteamIds.of(steamId));
    }

    public String stateOf(long steamId) {
        return friends.getFriendPersonaState(SteamIds.of(steamId)).name();
    }

    public int friendCount() {
        return friends.getFriendCount(SteamFriends.FriendFlags.Immediate);
    }

    public List<Long> friendIds() {
        int count = friendCount();
        List<Long> identifiers = new ArrayList<>(Math.max(count, 0));
        for (int index = 0; index < count; index++) {
            SteamID friend = friends.getFriendByIndex(index, SteamFriends.FriendFlags.Immediate);
            identifiers.add(SteamIds.rawOf(friend));
        }
        return identifiers;
    }

    public Optional<SteamAvatar> avatar(long steamId, SteamAvatarSize size) {
        String key = steamId + "/" + size;
        SteamAvatar cached = avatars.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        return readAvatar(steamId, size).map(avatar -> {
            avatars.put(key, avatar);
            return avatar;
        });
    }

    public void forget(long steamId) {
        avatars.keySet().removeIf(key -> key.startsWith(steamId + "/"));
    }

    private Optional<SteamAvatar> readAvatar(long steamId, SteamAvatarSize size) {
        int handle = handleOf(steamId, size);
        if (handle == 0) {
            return Optional.empty();
        }
        int[] dimensions = new int[2];
        if (!utils.getImageSize(handle, dimensions) || dimensions[0] <= 0 || dimensions[1] <= 0) {
            return Optional.empty();
        }
        ByteBuffer pixels = ByteBuffer
                .allocateDirect(dimensions[0] * dimensions[1] * BYTES_PER_PIXEL)
                .order(ByteOrder.nativeOrder());
        try {
            if (!utils.getImageRGBA(handle, pixels)) {
                return Optional.empty();
            }
        } catch (SteamException unreadable) {
            return Optional.empty();
        }
        byte[] copy = new byte[pixels.capacity()];
        pixels.rewind();
        pixels.get(copy);
        return Optional.of(new SteamAvatar(dimensions[0], dimensions[1], copy));
    }

    private int handleOf(long steamId, SteamAvatarSize size) {
        SteamID identifier = SteamIds.of(steamId);
        return switch (size) {
            case SMALL -> friends.getSmallFriendAvatar(identifier);
            case MEDIUM -> friends.getMediumFriendAvatar(identifier);
            case LARGE -> friends.getLargeFriendAvatar(identifier);
        };
    }
}
