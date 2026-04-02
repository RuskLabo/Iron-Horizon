package com.lunar_prototype.iron_horizon.client.render;

import com.lunar_prototype.iron_horizon.common.MapSettings;
import com.lunar_prototype.iron_horizon.common.util.TerrainGenerator;

public final class TerrainMeshFactory {
    private TerrainMeshFactory() {}

    public static Mesh createTerrain() {
        int segments = (int) (MapSettings.WORLD_SIZE / MapSettings.TERRAIN_TILE_SIZE);
        int vertexCount = (segments + 1) * (segments + 1);
        float[] vertices = new float[vertexCount * 8];
        int cursor = 0;

        for (int z = 0; z <= segments; z++) {
            for (int x = 0; x <= segments; x++) {
                float worldX = x * MapSettings.TERRAIN_TILE_SIZE;
                float worldZ = z * MapSettings.TERRAIN_TILE_SIZE;
                float height = TerrainGenerator.heightAt(worldX, worldZ);
                float heightRight = TerrainGenerator.heightAt(Math.min(worldX + 1, MapSettings.WORLD_SIZE), worldZ);
                float heightLeft = TerrainGenerator.heightAt(Math.max(worldX - 1, 0), worldZ);
                float heightUp = TerrainGenerator.heightAt(worldX, Math.min(worldZ + 1, MapSettings.WORLD_SIZE));
                float heightDown = TerrainGenerator.heightAt(worldX, Math.max(worldZ - 1, 0));
                float nx = heightLeft - heightRight;
                float ny = 2.0f;
                float nz = heightDown - heightUp;
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length == 0) {
                    length = 1.0f;
                }
                nx /= length;
                ny /= length;
                nz /= length;

                vertices[cursor++] = worldX;
                vertices[cursor++] = height;
                vertices[cursor++] = worldZ;
                vertices[cursor++] = nx;
                vertices[cursor++] = ny;
                vertices[cursor++] = nz;
                vertices[cursor++] = worldX / 4.0f;
                vertices[cursor++] = worldZ / 4.0f;
            }
        }

        int quadCount = segments * segments;
        int[] indices = new int[quadCount * 6];
        int indexCursor = 0;
        int row = segments + 1;
        for (int z = 0; z < segments; z++) {
            for (int x = 0; x < segments; x++) {
                int topLeft = z * row + x;
                int topRight = topLeft + 1;
                int bottomLeft = (z + 1) * row + x;
                int bottomRight = bottomLeft + 1;
                indices[indexCursor++] = topLeft;
                indices[indexCursor++] = bottomLeft;
                indices[indexCursor++] = topRight;
                indices[indexCursor++] = topRight;
                indices[indexCursor++] = bottomLeft;
                indices[indexCursor++] = bottomRight;
            }
        }

        return new Mesh(vertices, indices);
    }
}
