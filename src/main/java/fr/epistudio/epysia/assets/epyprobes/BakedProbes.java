package fr.epistudio.epysia.assets.epyprobes;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Vector3f;

public final class BakedProbes {

    private final long bakeHash;
    private final Vector3f gridOrigin;
    private final Vector3f gridSpacing;
    private final int resolutionX;
    private final int resolutionY;
    private final int resolutionZ;
    private final float[] positions;
    private final float[] coefficients;

    public BakedProbes(long bakeHash, Vector3f gridOrigin, Vector3f gridSpacing,
                       int resolutionX, int resolutionY, int resolutionZ,
                       float[] positions, float[] coefficients) {
        this.bakeHash = bakeHash;
        this.gridOrigin = new Vector3f(gridOrigin);
        this.gridSpacing = new Vector3f(gridSpacing);
        this.resolutionX = resolutionX;
        this.resolutionY = resolutionY;
        this.resolutionZ = resolutionZ;
        this.positions = positions;
        this.coefficients = coefficients;
        validate();
    }

    private void validate() {
        int count = probeCount();
        if (count <= 0) {
            throw new EpysiaException("Baked probe grid needs a positive resolution on every axis.");
        }
        if (positions.length != count * 3) {
            throw new EpysiaException("Baked probes expected " + count * 3
                    + " position floats, got " + positions.length);
        }
        if (coefficients.length != count * EpyProbesFormat.FLOATS_PER_PROBE) {
            throw new EpysiaException("Baked probes expected " + count * EpyProbesFormat.FLOATS_PER_PROBE
                    + " coefficient floats, got " + coefficients.length);
        }
    }

    public long bakeHash() {
        return bakeHash;
    }

    public Vector3f gridOrigin(Vector3f destination) {
        return destination.set(gridOrigin);
    }

    public Vector3f gridSpacing(Vector3f destination) {
        return destination.set(gridSpacing);
    }

    public int resolutionX() {
        return resolutionX;
    }

    public int resolutionY() {
        return resolutionY;
    }

    public int resolutionZ() {
        return resolutionZ;
    }

    public int probeCount() {
        return resolutionX * resolutionY * resolutionZ;
    }

    public float[] positions() {
        return positions;
    }

    public float[] coefficients() {
        return coefficients;
    }
}
