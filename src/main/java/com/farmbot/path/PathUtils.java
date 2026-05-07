package com.ceres.path;

import java.util.ArrayList;
import java.util.List;

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

    public static List<String> getMovementDirections(double deltaX, double deltaZ, float rawYaw) {
        int snapped = (int) Math.round(normaliseYaw(rawYaw) / 90.0) * 90;
        snapped = ((snapped % 360) + 360) % 360;

        List<String> keys = new ArrayList<>(2);

        switch (snapped) {
            case 0 -> {
                if (deltaZ > 0) keys.add("forward");
                else if (deltaZ < 0) keys.add("back");
                if (deltaX > 0) keys.add("right");
                else if (deltaX < 0) keys.add("left");
            }
            case 90 -> {
                if (deltaX < 0) keys.add("forward");
                else if (deltaX > 0) keys.add("back");
                if (deltaZ > 0) keys.add("left");
                else if (deltaZ < 0) keys.add("right");
            }
            case 180 -> {
                if (deltaZ < 0) keys.add("forward");
                else if (deltaZ > 0) keys.add("back");
                if (deltaX < 0) keys.add("right");
                else if (deltaX > 0) keys.add("left");
            }
            case 270 -> {
                if (deltaX > 0) keys.add("forward");
                else if (deltaX < 0) keys.add("back");
                if (deltaZ < 0) keys.add("left");
                else if (deltaZ > 0) keys.add("right");
            }
        }
        return keys;
    }

    public static float normaliseYaw(float yaw) {
        return ((yaw % 360) + 360) % 360;
    }
}
