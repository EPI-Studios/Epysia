package fr.epistudio.epysia.animation;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.List;

public record Skeleton(List<Joint> joints) {

    public static final int MAX_JOINTS = 256;

    public Skeleton {
        if (joints.isEmpty()) {
            throw new EpysiaException("Skeleton must have at least one joint.");
        }
        if (joints.size() > MAX_JOINTS) {
            throw new EpysiaException("Skeleton exceeds " + MAX_JOINTS + " joints: " + joints.size());
        }
        validateOrder(joints);
        joints = List.copyOf(joints);
    }

    private static void validateOrder(List<Joint> joints) {
        for (int index = 0; index < joints.size(); index++) {
            int parent = joints.get(index).parentIndex();
            if (parent >= index || parent < -1) {
                throw new EpysiaException("Joint " + joints.get(index).name()
                        + " at " + index + " has invalid parent " + parent + ".");
            }
        }
    }

    public int indexOfJoint(String name) {
        for (int index = 0; index < joints.size(); index++) {
            if (joints.get(index).name().equals(name)) {
                return index;
            }
        }
        return -1;
    }

    public int jointCount() {
        return joints.size();
    }

    public long nameChecksum() {
        long checksum = 1125899906842597L;
        for (Joint joint : joints) {
            checksum = 31L * checksum + joint.name().hashCode();
        }
        return checksum;
    }
}
