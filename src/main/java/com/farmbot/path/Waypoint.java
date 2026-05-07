package com.ceres.path;

import java.util.ArrayList;
import java.util.List;

/**
 * A single point on a bot path.
 * forcedKeys overrides the auto-calculated movement keys at this waypoint.
 * Valid key names: "forward", "back", "left", "right", "sprint", "sneak", "jump"
 */
public class Waypoint {

    public double x;
    public double y;
    public double z;
    public List<String> forcedKeys;

    public Waypoint(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.forcedKeys = new ArrayList<>();
    }

    public Waypoint(double x, double y, double z, List<String> forcedKeys) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.forcedKeys = forcedKeys != null ? new ArrayList<>(forcedKeys) : new ArrayList<>();
    }

    public boolean hasForcedKeys() {
        return forcedKeys != null && !forcedKeys.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("Waypoint(%.1f, %.1f, %.1f, keys=%s)", x, y, z, forcedKeys);
    }
}
