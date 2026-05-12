package com.ceres.path;

public class PathUtils {

    private PathUtils() {}

    public static double calculateDistance(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static boolean hasReachedTarget(double px, double pz, double tx, double tz, double tolerance) {
        return calculateDistance(px, pz, tx, tz) <= tolerance;
    }
}
