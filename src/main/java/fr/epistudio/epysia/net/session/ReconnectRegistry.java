package fr.epistudio.epysia.net.session;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ReconnectRegistry {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<Long, ParkedPeer> parkedByToken = new LinkedHashMap<>();

    public long issueToken() {
        long token = RANDOM.nextLong();
        return token == 0L ? 1L : token;
    }

    public void park(long token, int peerId, String displayName, List<Integer> ownedObjects,
                     float graceSeconds) {
        if (token == 0L || graceSeconds <= 0.0f) {
            return;
        }
        parkedByToken.put(token, new ParkedPeer(peerId, displayName, List.copyOf(ownedObjects), graceSeconds));
    }

    public Optional<ParkedPeer> claim(long token) {
        if (token == 0L) {
            return Optional.empty();
        }
        return Optional.ofNullable(parkedByToken.remove(token));
    }

    public List<ParkedPeer> expire(float deltaTimeSeconds) {
        List<ParkedPeer> expired = new ArrayList<>();
        parkedByToken.values().removeIf(parked -> {
            if (parked.advance(deltaTimeSeconds)) {
                expired.add(parked);
                return true;
            }
            return false;
        });
        return expired;
    }

    public int parkedCount() {
        return parkedByToken.size();
    }

    public void clear() {
        parkedByToken.clear();
    }

    public static final class ParkedPeer {
        private final int peerId;
        private final String displayName;
        private final List<Integer> ownedObjects;
        private float remainingSeconds;

        private ParkedPeer(int peerId, String displayName, List<Integer> ownedObjects, float graceSeconds) {
            this.peerId = peerId;
            this.displayName = displayName;
            this.ownedObjects = ownedObjects;
            this.remainingSeconds = graceSeconds;
        }

        private boolean advance(float deltaTimeSeconds) {
            remainingSeconds -= deltaTimeSeconds;
            return remainingSeconds <= 0.0f;
        }

        public int peerId() {
            return peerId;
        }

        public String displayName() {
            return displayName;
        }

        public List<Integer> ownedObjects() {
            return ownedObjects;
        }
    }
}
