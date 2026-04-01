package com.lunar_prototype.iron_horizon.common.model;

import com.lunar_prototype.iron_horizon.common.Network;
import org.joml.Vector2f;
import java.util.ArrayList;
import java.util.List;

public class Unit {
    public enum Type { CONSTRUCTOR, TANK }

    public int id;
    public Type type = Type.TANK;
    public int teamId;
    public Vector2f position = new Vector2f();
    public Vector2f velocity = new Vector2f();
    public Vector2f targetPosition = new Vector2f();
    public float radius = 0.5f;
    public float speed = 5.0f;
    public boolean selected = false;
    public Integer targetBuildingId = null; 
    
    // Combat
    public float hp, maxHp;
    public float attackRange = 15.0f;
    public float attackDamage = 20.0f;
    public float attackCooldown = 0.0f;
    public Integer targetUnitId = null;
    public Integer attackTargetBuildingId = null;

    public List<Network.Task> tasks = new ArrayList<>();

    public Unit() {}

    public Unit(int id, float x, float y) {
        this.id = id;
        this.position.set(x, y);
        this.targetPosition.set(x, y);
    }

    public void setType(Type type) {
        this.type = type;
        if (type == Type.TANK) {
            maxHp = 300; // Increased for longer battles
            speed = 10.0f; // Slightly slower
            attackRange = 30.0f;
            attackDamage = 25.0f;
            radius = 0.8f;
        } else {
            maxHp = 150;
            speed = 12.0f;
            attackRange = 0;
            attackDamage = 0;
            radius = 0.6f;
        }
        this.hp = maxHp;
    }

    public void update(float deltaTime) {
        if (position.distance(targetPosition) > 0.5f) {
            velocity.set(targetPosition).sub(position).normalize().mul(speed);
        } else {
            velocity.set(0, 0);
        }
    }

    public void applySeparation(Vector2f push) {
        position.add(push);
    }
}