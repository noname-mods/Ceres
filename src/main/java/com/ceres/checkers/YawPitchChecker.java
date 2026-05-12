package com.ceres.checkers;

public class YawPitchChecker {

    private float lastYaw = Float.NaN;
    private float lastPitch = Float.NaN;

    public void reset(float yaw, float pitch) {
        lastYaw = yaw;
        lastPitch = pitch;
    }

    public boolean check(float yaw, float pitch) {
        if (Float.isNaN(lastYaw)) {
            lastYaw = yaw;
            lastPitch = pitch;
            return true;
        }

        float yawDiff = Math.abs(yaw - lastYaw);
        if (yawDiff > 180f) yawDiff = 360f - yawDiff;
        float pitchDiff = Math.abs(pitch - lastPitch);

        boolean ok = yawDiff <= 1.0f && pitchDiff <= 1.0f;
        lastYaw = yaw;
        lastPitch = pitch;
        return ok;
    }
}
