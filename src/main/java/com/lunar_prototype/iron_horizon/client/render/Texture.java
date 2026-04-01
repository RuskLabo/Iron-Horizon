package com.lunar_prototype.iron_horizon.client.render;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

public class Texture implements AutoCloseable {
    private final int textureId;

    public Texture(int width, int height, ByteBuffer data) {
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public int id() {
        return textureId;
    }

    @Override
    public void close() {
        glDeleteTextures(textureId);
    }

    public static ByteBuffer createGrassTextureData(int width, int height) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float nx = x / (float) width;
                float ny = y / (float) height;
                float v = layeredNoise(nx * 12.0f, ny * 12.0f);
                int r = clamp(42 + (int) (v * 28) + (int) (ny * 12));
                int g = clamp(96 + (int) (v * 60) + (int) (nx * 10));
                int b = clamp(35 + (int) (v * 18));
                if (((x + y) & 7) == 0) {
                    r += 4;
                    g += 8;
                }
                buffer.put((byte) r);
                buffer.put((byte) g);
                buffer.put((byte) b);
                buffer.put((byte) 255);
            }
        }
        buffer.flip();
        return buffer;
    }

    public static Texture createGrassTexture(int width, int height) {
        return new Texture(width, height, createGrassTextureData(width, height));
    }

    private static float layeredNoise(float x, float y) {
        float total = 0.0f;
        float amplitude = 1.0f;
        float frequency = 1.0f;
        float sum = 0.0f;
        for (int i = 0; i < 4; i++) {
            total += noise(x * frequency, y * frequency) * amplitude;
            sum += amplitude;
            amplitude *= 0.5f;
            frequency *= 2.0f;
        }
        return (total / sum + 1.0f) * 0.5f;
    }

    private static float noise(float x, float y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        float sx = smooth(x - x0);
        float sy = smooth(y - y0);
        float n0 = lerp(hash(x0, y0), hash(x1, y0), sx);
        float n1 = lerp(hash(x0, y1), hash(x1, y1), sx);
        return lerp(n0, n1, sy);
    }

    private static float hash(int x, int y) {
        int n = x * 374761393 + y * 668265263 + 1442695041;
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

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
