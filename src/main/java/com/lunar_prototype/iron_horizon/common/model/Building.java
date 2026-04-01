package com.lunar_prototype.iron_horizon.common.model;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class Building {
    public enum Type { NEXUS, FACTORY, WALL, EXTRACTOR, METAL_PATCH }

    public int id;
    public Type type;
    public Vector2f position = new Vector2f();
    public int teamId;
    public float hp;
    public float maxHp;
    public float size = 2.0f;
    public float collisionRadius;
    public float metalAmount = 0; // For METAL_PATCH
    
    // Construction & Production
    public float buildProgress = 0.0f; // 0.0 to 1.0
    public boolean isComplete = false;
    public List<Unit.Type> productionQueue = new ArrayList<>();
    public float productionTimer = 0.0f;

    public Building() {}

    public Building(int id, Type type, float x, float y, int teamId) {
        this.id = id;
        this.type = type;
        this.position.set(x, y);
        this.teamId = teamId;
        this.isComplete = (type == Type.NEXUS); // Nexus starts complete
        this.buildProgress = isComplete ? 1.0f : 0.0f;
        
        switch (type) {
            case NEXUS -> { maxHp = 5000; size = 4.0f; }
            case FACTORY -> { maxHp = 1000; size = 3.0f; }
            case WALL -> { maxHp = 500; size = 1.0f; }
            case EXTRACTOR -> { maxHp = 800; size = 2.0f; }
            case METAL_PATCH -> { maxHp = 10000; size = 2.0f; metalAmount = 10000; }
        }
        this.collisionRadius = size * 0.7f; // Slightly larger than half-size for safety
        this.hp = isComplete ? maxHp : 1.0f;
    }
}
