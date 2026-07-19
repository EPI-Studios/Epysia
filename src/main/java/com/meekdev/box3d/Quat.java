package com.meekdev.box3d;

public record Quat(float x, float y, float z, float s) {

    public static final Quat identity = new Quat(0, 0, 0, 1);

    // shortest arc rotation taking the z axis onto the given direction
    public static Quat zTo(Vec3 dir) {
        double len = Math.sqrt(dir.x() * dir.x() + dir.y() * dir.y() + dir.z() * dir.z());
        if (len < 1e-9) {
            return identity;
        }
        double dx = dir.x() / len;
        double dy = dir.y() / len;
        double dz = dir.z() / len;
        double d = 1.0 + dz;
        if (d < 1e-6) {
            // opposite direction, half turn around x
            return new Quat(1, 0, 0, 0);
        }
        double cx = -dy;
        double cy = dx;
        double norm = Math.sqrt(cx * cx + cy * cy + d * d);
        return new Quat((float) (cx / norm), (float) (cy / norm), 0, (float) (d / norm));
    }

    // shortest arc rotation taking the x axis onto the given direction
    public static Quat xTo(Vec3 dir) {
        double len = dir.length();
        if (len < 1e-9) {
            return identity;
        }
        double dx = dir.x() / len;
        double dy = dir.y() / len;
        double dz = dir.z() / len;
        double d = 1.0 + dx;
        if (d < 1e-6) {
            return new Quat(0, 1, 0, 0);
        }
        double cy = dz;
        double cz = -dy;
        double norm = Math.sqrt(cy * cy + cz * cz + d * d);
        return new Quat(0, (float) (cy / norm), (float) (cz / norm), (float) (d / norm));
    }

    public Quat mul(Quat o) {
        return new Quat(
                s * o.x + x * o.s + y * o.z - z * o.y,
                s * o.y - x * o.z + y * o.s + z * o.x,
                s * o.z + x * o.y - y * o.x + z * o.s,
                s * o.s - x * o.x - y * o.y - z * o.z);
    }

    public Quat conjugate() {
        return new Quat(-x, -y, -z, s);
    }

    public Vec3 rotate(Vec3 v) {
        double tx = 2 * (y * v.z() - z * v.y());
        double ty = 2 * (z * v.x() - x * v.z());
        double tz = 2 * (x * v.y() - y * v.x());
        return new Vec3(
                v.x() + s * tx + y * tz - z * ty,
                v.y() + s * ty + z * tx - x * tz,
                v.z() + s * tz + x * ty - y * tx);
    }
}
