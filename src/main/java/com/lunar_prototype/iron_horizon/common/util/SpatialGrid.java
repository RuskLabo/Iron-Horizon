package com.lunar_prototype.iron_horizon.common.util;

import com.lunar_prototype.iron_horizon.common.model.Unit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpatialGrid {
    private final float cellSize;
    private final Map<Long, List<Unit>> grid = new HashMap<>();

    public SpatialGrid(float cellSize) {
        this.cellSize = cellSize;
    }

    private long getHash(float x, float y) {
        long ix = (long) Math.floor(x / cellSize);
        long iy = (long) Math.floor(y / cellSize);
        return (ix << 32) | (iy & 0xFFFFFFFFL);
    }

    public void clear() {
        grid.clear();
    }

    public void add(Unit unit) {
        long hash = getHash(unit.position.x, unit.position.y);
        grid.computeIfAbsent(hash, k -> new ArrayList<>()).add(unit);
    }

    public List<Unit> query(float minX, float minY, float maxX, float maxY) {
        List<Unit> result = new ArrayList<>();
        int startX = (int) Math.floor(minX / cellSize);
        int startY = (int) Math.floor(minY / cellSize);
        int endX = (int) Math.floor(maxX / cellSize);
        int endY = (int) Math.floor(maxY / cellSize);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                long hash = ((long) x << 32) | (y & 0xFFFFFFFFL);
                List<Unit> units = grid.get(hash);
                if (units != null) {
                    result.addAll(units);
                }
            }
        }
        return result;
    }
}
