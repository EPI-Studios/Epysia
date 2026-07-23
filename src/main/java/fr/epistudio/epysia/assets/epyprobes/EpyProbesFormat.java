package fr.epistudio.epysia.assets.epyprobes;

import fr.epistudio.epysia.render.lighting.SphericalHarmonics;

public final class EpyProbesFormat {

    public static final int MAGIC = 0x45505052;
    public static final int VERSION = 1;
    public static final String EXTENSION = ".epyprobes";
    public static final int FLOATS_PER_PROBE = SphericalHarmonics.FLOAT_COUNT;

    private EpyProbesFormat() {
    }
}
