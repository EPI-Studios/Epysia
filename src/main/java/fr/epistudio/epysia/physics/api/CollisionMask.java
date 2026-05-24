package fr.epistudio.epysia.physics.api;

public record CollisionMask(int layer, int mask) {

    public static final CollisionMask DEFAULT = new CollisionMask(0x0001, 0xFFFF);
}
