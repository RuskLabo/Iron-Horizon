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
    public int ownerId;
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
    public List<Vector2f> currentPath = new ArrayList<>();
    public int currentPathIndex = 0;
    public boolean isAtDestination = false;

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
            radius = 0.3f;
        } else {
            maxHp = 150;
            speed = 12.0f;
            attackRange = 0;
            attackDamage = 0;
            radius = 0.6f;
        }
        this.hp = maxHp;
    }

    public void update(float deltaTime, GameState state) {
        boolean facingOverride = false;
        Vector2f effectiveTargetPos = null;

        if (targetUnitId != null && state != null) {
            Unit target = state.units.get(targetUnitId);
            if (target != null) effectiveTargetPos = target.position;
        } else if (attackTargetBuildingId != null && state != null) {
            Building target = state.buildings.get(attackTargetBuildingId);
            if (target != null) effectiveTargetPos = target.position;
        } else if (targetBuildingId != null && state != null) {
            Building target = state.buildings.get(targetBuildingId);
            if (target != null) effectiveTargetPos = target.position;
        }

        if (effectiveTargetPos != null) {
            facingDeg = (float) Math.toDegrees(Math.atan2(effectiveTargetPos.y - position.y, effectiveTargetPos.x - position.x));
            facingOverride = true;
        }

        // パスによる移動
        Vector2f moveGoal = targetPosition;
        if (!currentPath.isEmpty() && currentPathIndex < currentPath.size()) {
            moveGoal = currentPath.get(currentPathIndex);
            if (position.distance(moveGoal) < 0.8f) {
                currentPathIndex++;
                if (currentPathIndex < currentPath.size()) {
                    moveGoal = currentPath.get(currentPathIndex);
                }
            }
        }

        if (position.distance(moveGoal) > 0.5f) {
            velocity.set(moveGoal).sub(position).normalize().mul(speed);
            if (!facingOverride) {
                facingDeg = (float) Math.toDegrees(Math.atan2(velocity.y, velocity.x));
            }
        } else {
            velocity.set(0, 0);
        }

        // 到着判定とフラグの更新
        float arrivalDist = 0.5f;
        if (targetBuildingId != null && state != null) {
            Building b = state.buildings.get(targetBuildingId);
            if (b != null) {
                arrivalDist = b.size / 2.0f + radius + 1.0f;
            }
        }

        if (position.distance(targetPosition) <= arrivalDist) {
            if (!isAtDestination) {
                isAtDestination = true;
                currentPath.clear();
                currentPathIndex = 0;
                velocity.set(0, 0);
                if (targetBuildingId == null) {
                    manualMoveOrder = false;
                }
            }
        }
    }

    public void applySeparation(Vector2f push) {
        position.add(push);
    }
}
