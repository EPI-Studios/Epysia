package fr.epistudio.epysia.physics.api;

public record QueryFilter(int mask, long excludedBodyId) {

    public static final QueryFilter ALL = new QueryFilter(0xFFFF, 0L);
}
