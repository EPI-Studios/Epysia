package com.meekdev.box3d;

// query side filtering, category says what the query is, mask what it wants to hit
public record B3Filter(long categoryBits, long maskBits) {

    public static final B3Filter everything = new B3Filter(1L, ~0L);

    public B3Filter hitting(long mask) {
        return new B3Filter(categoryBits, mask);
    }
}
