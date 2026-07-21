package fr.epistudio.epysia.assets.epymesh;

public final class EpyMeshFormat {

    public static final int MAGIC = 0x4550594D;
    public static final int VERSION = 2;
    public static final int HAS_BAKED_COLLIDER = 0x1;
    public static final int HAS_SKIN = 0x2;
    public static final int HAS_VERTEX_COLORS = 0x4;
    public static final String EXTENSION = ".epymesh";

    private EpyMeshFormat() {
    }
}
