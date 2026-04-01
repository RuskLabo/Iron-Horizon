package com.lunar_prototype.iron_horizon.common.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class GameState {
    public Map<Integer, Unit> units = new ConcurrentHashMap<>();
    public Map<Integer, Building> buildings = new ConcurrentHashMap<>();
    public Map<Integer, Projectile> projectiles = new ConcurrentHashMap<>();
    public Map<Integer, Float> teamMetal = new ConcurrentHashMap<>();
    public Map<Integer, Float> teamIncome = new ConcurrentHashMap<>();
    public Map<Integer, Float> teamDrain = new ConcurrentHashMap<>();
    public boolean isStarted = false;
    public int winnerTeamId = 0;

    public GameState() {
        teamMetal.put(1, 1000.0f); // Starting metal
        teamMetal.put(2, 1000.0f);
    }

    public float getMetal(int teamId) {
        return teamMetal.getOrDefault(teamId, 0.0f);
    }

    public void addUnit(Unit unit) {
        units.put(unit.id, unit);
    }

    public void addBuilding(Building building) {
        buildings.put(building.id, building);
    }

    public void update(float deltaTime) {
        for (Unit unit : units.values()) {
            unit.update(deltaTime);
        }
    }
}
