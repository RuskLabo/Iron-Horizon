package com.lunar_prototype.iron_horizon.client.render;

import com.lunar_prototype.iron_horizon.common.MapSettings;

import java.util.Random;

public final class TerrainGenerator {
    private static final int WORLD_SEED = 1337;

    private TerrainGenerator() {}

    public static float heightAt(float x, float z) {
        float h = 0.0f;
        h += noise(x * 0.010f, z * 0.010f) * 5.0f;
        h += noise(x * 0.035f + 100.0f, z * 0.035f - 50.0f) * 1.8f;
        h += noise(x * 0.075f - 200.0f, z * 0.075f + 150.0f) * 0.7f;
        float edgeX = edgeFalloff(x, MapSettings.WORLD_SIZE);
        float edgeZ = edgeFalloff(z, MapSettings.WORLD_SIZE);
        return h * Math.min(edgeX, edgeZ);
    }

    private static float edgeFalloff(float value, float max) {
        float t = Math.min(value, max - value) / 40.0f;
        return clamp01(t);
    }

    private static float noise(float x, float z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        float sx = smooth(x - x0);
        float sz = smooth(z - z0);
        float n0 = lerp(hash(x0, z0), hash(x1, z0), sx);
        float n1 = lerp(hash(x0, z1), hash(x1, z1), sx);
        return lerp(n0, n1, sz);
    }

    private static float hash(int x, int z) {
        int n = x * 374761393 + z * 668265263 + WORLD_SEED * 1442695041;
        n = (n ^ (n >> 13)) * 1274126177;
        n ^= (n >> 16);
        return ((n & 0x7fffffff) / (float) Integer.MAX_VALUE) * 2.0f - 1.0f;
    }

    private static float smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
