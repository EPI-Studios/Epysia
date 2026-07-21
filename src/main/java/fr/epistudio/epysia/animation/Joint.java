package fr.epistudio.epysia.animation;

import fr.epistudio.epysia.exceptions.EpysiaException;

public record Joint(String name, int parentIndex, float[] localBindTransform, float[] inverseBindMatrix) {

    public Joint {
        if (localBindTransform.length != 16 || inverseBindMatrix.length != 16) {
            throw new EpysiaException("Joint matrices must have 16 floats: " + name);
        }
    }
}
