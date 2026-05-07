package com.ceres.checkers;

import com.playerapi.InventoryInfo;
import com.playerapi.types.ItemSnapshot;

import java.util.List;

public class InventoryChecker {

    private List<ItemSnapshot> lastSnapshot = null;
    private long lastChangeTick = 0;

    public void reset(long currentTick) {
        lastSnapshot = InventoryInfo.getHotbarSnapshot();
        lastChangeTick = currentTick;
    }

    public boolean check(long currentTick) {
        List<ItemSnapshot> current = InventoryInfo.getHotbarSnapshot();

        if (lastSnapshot == null) {
            lastSnapshot = current;
            lastChangeTick = currentTick;
            return true;
        }

        if (!current.equals(lastSnapshot)) {
            lastSnapshot = current;
            lastChangeTick = currentTick;
            return true;
        }

        return (currentTick - lastChangeTick) <= 120;
    }
}
