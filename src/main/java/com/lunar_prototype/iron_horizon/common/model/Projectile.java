package com.lunar_prototype.iron_horizon.common.model;

import org.joml.Vector2f;

public class Projectile {
    public int id;
    public Vector2f position = new Vector2f();
    public Vector2f velocity = new Vector2f();
    public float damage;
    public int teamId;
    public float life = 5.0f; // Seconds until despawn

    public Projectile() {}

    public Projectile(int id, float x, float y, float vx, float vy, float damage, int teamId) {
        this.id = id;
        this.position.set(x, y);
        this.velocity.set(vx, vy);
        this.damage = damage;
        this.teamId = teamId;
    }

    public void update(float dt) {
        position.add(velocity.x * dt, velocity.y * dt);
        life -= dt;
    }
}
