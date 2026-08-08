package fr.epistudio.epysia.net.security;

public final class ReplayWindow {
    private static final int WIDTH = 64;

    private long highestSeen = -1L;
    private long seenMask;

    public boolean accept(long counter) {
        if (counter < 0L) {
            return false;
        }
        if (counter > highestSeen) {
            shiftTo(counter);
            return true;
        }
        long distance = highestSeen - counter;
        if (distance >= WIDTH) {
            return false;
        }
        long bit = 1L << distance;
        if ((seenMask & bit) != 0L) {
            return false;
        }
        seenMask |= bit;
        return true;
    }

    private void shiftTo(long counter) {
        long advance = counter - highestSeen;
        seenMask = advance >= WIDTH ? 0L : (seenMask << advance);
        seenMask |= 1L;
        highestSeen = counter;
    }

    public long highestSeen() {
        return highestSeen;
    }
}
