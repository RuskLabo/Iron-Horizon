package com.lunar_prototype.iron_horizon.common.model;

import com.lunar_prototype.iron_horizon.common.Network;
import org.joml.Vector2f;
import java.util.ArrayList;
import java.util.List;

public class Unit {
    public enum Type { CONSTRUCTOR, TANK, HOUND, OBELISK }

    public int id;
    public Type type = Type.TANK;
    public int teamId;
    public Vector2f position = new Vector2f();
    public Vector2f velocity = new Vector2f();
    public Vector2f targetPosition = new Vector2f();
    public float facingDeg = 0.0f;
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
    public boolean manualMoveOrder = false;

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
            maxHp = 240;
            speed = 9.0f;
            attackRange = 27.0f;
            attackDamage = 18.0f;
            radius = 0.75f;
        } else if (type == Type.HOUND) {
            maxHp = 90;
            speed = 18.0f;
            attackRange = 24.0f;
            attackDamage = 10.0f;
            radius = 0.55f;
        } else if (type == Type.OBELISK) {
            maxHp = 1150;
            speed = 4.5f;
            attackRange = 34.0f;
            attackDamage = 320.0f;
            attackCooldown = 0.0f;
            radius = 1.1f;
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
            facingDeg = -(float) Math.toDegrees(Math.atan2(velocity.x, velocity.y));
        } else {
            velocity.set(0, 0);
            manualMoveOrder = false;
        }
    }

    public void applySeparation(Vector2f push) {
        position.add(push);
    }
}
