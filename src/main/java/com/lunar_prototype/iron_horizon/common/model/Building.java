package com.lunar_prototype.iron_horizon.common.model;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class Building {
    public enum Type { NEXUS, FACTORY, WALL, EXTRACTOR, LASER_TOWER, METAL_PATCH, SOLAR_COLLECTOR, SHIELD_GENERATOR }

    public int id;
    public Type type;
    public Vector2f position = new Vector2f();
    public int teamId;
    public int ownerId;
    public float hp;
    public float maxHp;
    public float size = 2.0f;
    public float collisionRadius;
    public float metalAmount = 0; // For METAL_PATCH
    public float attackRange = 0.0f;
    public float attackDamage = 0.0f;
    public float attackCooldown = 0.0f;
    public float attackTimer = 0.0f;
    
    // Energy & Shield System
    public float shieldHp = 0.0f;
    public float maxShieldHp = 0.0f;
    public float shieldRadius = 0.0f;
    public float energyIncome = 0.0f; // Positive energy generated per second
    
    // Construction & Production
    public float buildProgress = 0.0f; // 0.0 to 1.0
    public boolean isComplete = false;
    public List<Unit.Type> productionQueue = new ArrayList<>();
    public float productionTimer = 0.0f;

    public Building() {}

    public Building(int id, Type type, float x, float y, int teamId, int ownerId) {
        this.id = id;
        this.type = type;
        this.position.set(x, y);
        this.teamId = teamId;
        this.ownerId = ownerId;
        this.isComplete = (type == Type.NEXUS); // Nexus starts complete
        this.buildProgress = isComplete ? 1.0f : 0.0f;
        
        switch (type) {
            case NEXUS -> { maxHp = 5000; size = 4.0f; attackRange = 18.0f; attackDamage = 0.0f; energyIncome = 50.0f; }
            case FACTORY -> { maxHp = 1000; size = 3.0f; }
            case WALL -> { maxHp = 500; size = 1.0f; }
            case EXTRACTOR -> { maxHp = 800; size = 2.0f; }
            case LASER_TOWER -> { maxHp = 650; size = 1.5f; attackRange = 28.0f; attackDamage = 18.0f; attackCooldown = 0.8f; }
            case METAL_PATCH -> { maxHp = 10000; size = 2.0f; metalAmount = 10000; }
            case SOLAR_COLLECTOR -> { maxHp = 400; size = 2.0f; energyIncome = 100.0f; }
            case SHIELD_GENERATOR -> { maxHp = 600; size = 2.5f; maxShieldHp = 3000; shieldHp = 0; shieldRadius = 20.0f; }
        }
        this.collisionRadius = size * 0.7f; // Slightly larger than half-size for safety
        this.hp = isComplete ? maxHp : 1.0f;
    }
}
