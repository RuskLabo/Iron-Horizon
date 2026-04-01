package com.lunar_prototype.iron_horizon.common.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class GameState {
    public Map<Integer, Unit> units = new ConcurrentHashMap<>();
    public Map<Integer, Building> buildings = new ConcurrentHashMap<>();
    public Map<Integer, Projectile> projectiles = new ConcurrentHashMap<>();
    
    // Player-specific resources
    public Map<Integer, Float> playerMetal = new ConcurrentHashMap<>();
    public Map<Integer, Float> playerIncome = new ConcurrentHashMap<>();
    public Map<Integer, Float> playerDrain = new ConcurrentHashMap<>();
    
    public boolean isStarted = false;
    public int winnerTeamId = 0;
    public Map<Integer, String> teamNames = new ConcurrentHashMap<>();

    public GameState() {
    }

    public float getMetal(int playerId) {
        return playerMetal.getOrDefault(playerId, 0.0f);
    }

    public void addUnit(Unit unit) {
        units.put(unit.id, unit);
    }

    public void addBuilding(Building building) {
        buildings.put(building.id, building);
    }

    public void update(float deltaTime) {
        for (Unit unit : units.values()) {
            unit.update(deltaTime, this);
        }
    }
}
