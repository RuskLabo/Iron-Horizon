package com.lunar_prototype.iron_horizon.client.render;

import com.lunar_prototype.iron_horizon.common.MapSettings;

public final class TerrainGenerator {
    private TerrainGenerator() {}

    public static float heightAt(float x, float z) {
        float cx = x - MapSettings.HALF_WORLD_SIZE;
        float cz = z - MapSettings.HALF_WORLD_SIZE;
        float continental = fbm(x * 0.0035f, z * 0.0035f, 5, 1.9f, 0.55f);
        float ridges = ridgedFbm(x * 0.010f + 120.0f, z * 0.010f - 80.0f, 4, 2.2f, 0.6f);
        float detail = fbm(x * 0.045f - 220.0f, z * 0.045f + 180.0f, 3, 2.0f, 0.5f);
        float plains = fbm(x * 0.0075f + 500.0f, z * 0.0075f - 300.0f, 4, 2.0f, 0.5f);
        float flatMask = smoothstep(0.42f, 0.76f, fbm(x * 0.014f - 50.0f, z * 0.014f + 90.0f, 3, 2.0f, 0.5f));
        float basin = 1.0f - clamp01((float) Math.sqrt(cx * cx + cz * cz) / (MapSettings.HALF_WORLD_SIZE * 0.95f));

        float mountainHeight = continental * 13.0f + ridges * 7.0f + detail * 2.5f;
        float flatHeight = plains * 2.0f + detail * 0.5f;
        float height = lerp(mountainHeight, flatHeight, flatMask);
        height += basin * 2.2f;

        float edgeX = edgeFalloff(x, MapSettings.WORLD_SIZE);
        float edgeZ = edgeFalloff(z, MapSettings.WORLD_SIZE);
        float edge = Math.min(edgeX, edgeZ);
        return height * edge;
    }

    private static float edgeFalloff(float value, float max) {
        float t = Math.min(value, max - value) / 40.0f;
        return clamp01(t);
    }

    private static float fbm(float x, float z, int octaves, float lacunarity, float gain) {
        float total = 0.0f;
        float amplitude = 1.0f;
        float frequency = 1.0f;
        float sum = 0.0f;
        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency, z * frequency) * amplitude;
            sum += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return total / sum;
    }

    private static float ridgedFbm(float x, float z, int octaves, float lacunarity, float gain) {
        float total = 0.0f;
        float amplitude = 1.0f;
        float frequency = 1.0f;
        float sum = 0.0f;
        for (int i = 0; i < octaves; i++) {
            float n = noise(x * frequency, z * frequency);
            n = 1.0f - Math.abs(n);
            n *= n;
            total += n * amplitude;
            sum += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return total / sum * 2.0f - 1.0f;
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
        int n = x * 374761393 + z * 668265263 + MapSettings.TERRAIN_SEED * 1442695041;
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

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
