package com.lunar_prototype.iron_horizon.client.render;

public final class MeshFactory {
    private MeshFactory() {}

    public static Mesh createUnitCube() {
        float[] vertices = {
                // Front
                -1f,  1f,  1f,  0f,  0f,  1f, 0f, 1f,
                 1f,  1f,  1f,  0f,  0f,  1f, 1f, 1f,
                 1f, -1f,  1f,  0f,  0f,  1f, 1f, 0f,
                -1f, -1f,  1f,  0f,  0f,  1f, 0f, 0f,

                // Back
                -1f,  1f, -1f,  0f,  0f, -1f, 1f, 1f,
                -1f, -1f, -1f,  0f,  0f, -1f, 1f, 0f,
                 1f, -1f, -1f,  0f,  0f, -1f, 0f, 0f,
                 1f,  1f, -1f,  0f,  0f, -1f, 0f, 1f,

                // Top
                -1f,  1f, -1f,  0f,  1f,  0f, 0f, 1f,
                 1f,  1f, -1f,  0f,  1f,  0f, 1f, 1f,
                 1f,  1f,  1f,  0f,  1f,  0f, 1f, 0f,
                -1f,  1f,  1f,  0f,  1f,  0f, 0f, 0f,

                // Bottom
                -1f, -1f, -1f,  0f, -1f,  0f, 1f, 1f,
                -1f, -1f,  1f,  0f, -1f,  0f, 1f, 0f,
                 1f, -1f,  1f,  0f, -1f,  0f, 0f, 0f,
                 1f, -1f, -1f,  0f, -1f,  0f, 0f, 1f,

                // Right
                 1f,  1f, -1f,  1f,  0f,  0f, 0f, 1f,
                 1f, -1f, -1f,  1f,  0f,  0f, 0f, 0f,
                 1f, -1f,  1f,  1f,  0f,  0f, 1f, 0f,
                 1f,  1f,  1f,  1f,  0f,  0f, 1f, 1f,

                // Left
                -1f,  1f, -1f, -1f,  0f,  0f, 1f, 1f,
                -1f,  1f,  1f, -1f,  0f,  0f, 0f, 1f,
                -1f, -1f,  1f, -1f,  0f,  0f, 0f, 0f,
                -1f, -1f, -1f, -1f,  0f,  0f, 1f, 0f
        };

        int[] indices = {
                0, 1, 2, 2, 3, 0,
                4, 5, 6, 6, 7, 4,
                8, 9, 10, 10, 11, 8,
                12, 13, 14, 14, 15, 12,
                16, 17, 18, 18, 19, 16,
                20, 21, 22, 22, 23, 20
        };

        return new Mesh(vertices, indices);
    }
}
