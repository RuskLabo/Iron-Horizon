package com.lunar_prototype.iron_horizon.client.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ObjLoader {
    private ObjLoader() {}

    public static Mesh loadFromResource(Class<?> anchor, String resourcePath) throws IOException {
        try (InputStream input = anchor.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("OBJ resource not found: " + resourcePath);
            }
            return load(input);
        }
    }

    public static Mesh load(InputStream input) throws IOException {
        List<float[]> positions = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<Float> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        Map<VertexKey, Integer> vertexLookup = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v" -> positions.add(new float[] {
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])
                    });
                    case "vt" -> texCoords.add(new float[] {
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2])
                    });
                    case "vn" -> normals.add(new float[] {
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])
                    });
                    case "f" -> {
                        int faceVertexCount = parts.length - 1;
                        int[] faceIndices = new int[faceVertexCount];
                        for (int i = 0; i < faceVertexCount; i++) {
                            VertexKey key = parseVertexKey(parts[i + 1]);
                            Integer index = vertexLookup.get(key);
                            if (index == null) {
                                float[] position = positions.get(key.positionIndex());
                                float[] normal = key.normalIndex() >= 0 ? normals.get(key.normalIndex()) : new float[] {0f, 0f, 1f};
                                float[] texCoord = key.texCoordIndex() >= 0 ? texCoords.get(key.texCoordIndex()) : new float[] {0f, 0f};
                                index = vertices.size() / 8;
                                vertices.add(position[0]);
                                vertices.add(position[1]);
                                vertices.add(position[2]);
                                vertices.add(normal[0]);
                                vertices.add(normal[1]);
                                vertices.add(normal[2]);
                                vertices.add(texCoord[0]);
                                vertices.add(texCoord[1]);
                                vertexLookup.put(key, index);
                            }
                            faceIndices[i] = index;
                        }
                        for (int i = 1; i < faceVertexCount - 1; i++) {
                            indices.add(faceIndices[0]);
                            indices.add(faceIndices[i]);
                            indices.add(faceIndices[i + 1]);
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        float[] vertexArray = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            vertexArray[i] = vertices.get(i);
        }
        int[] indexArray = indices.stream().mapToInt(Integer::intValue).toArray();
        return new Mesh(vertexArray, indexArray);
    }

    private static VertexKey parseVertexKey(String token) {
        String[] parts = token.split("/");
        int positionIndex = parseIndex(parts[0]);
        int texCoordIndex = parts.length > 1 && !parts[1].isEmpty() ? parseIndex(parts[1]) : -1;
        int normalIndex = parts.length > 2 && !parts[2].isEmpty() ? parseIndex(parts[2]) : -1;
        return new VertexKey(positionIndex, texCoordIndex, normalIndex);
    }

    private static int parseIndex(String token) {
        return Integer.parseInt(token) - 1;
    }

    private record VertexKey(int positionIndex, int texCoordIndex, int normalIndex) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof VertexKey other)) return false;
            return positionIndex == other.positionIndex
                    && texCoordIndex == other.texCoordIndex
                    && normalIndex == other.normalIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(positionIndex, texCoordIndex, normalIndex);
        }
    }
}
