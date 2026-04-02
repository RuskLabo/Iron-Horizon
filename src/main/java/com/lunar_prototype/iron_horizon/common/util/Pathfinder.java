package com.lunar_prototype.iron_horizon.common.util;

import com.lunar_prototype.iron_horizon.common.MapSettings;
import com.lunar_prototype.iron_horizon.common.model.Building;
import com.lunar_prototype.iron_horizon.common.model.GameState;
import org.joml.Vector2f;

import java.util.*;

public class Pathfinder {
    private static final float STEP_SIZE = 1.0f; // グリッド解像度
    private static final float MAX_SLOPE = 1.2f;  // 通行可能な最大高度差

    public static class Node implements Comparable<Node> {
        public int x, z;
        public float gCost, hCost;
        public Node parent;

        public Node(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public float fCost() {
            return gCost + hCost;
        }

        @Override
        public int compareTo(Node o) {
            return Float.compare(this.fCost(), o.fCost());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Node node)) return false;
            return x == node.x && z == node.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }

    public static List<Vector2f> findPath(Vector2f start, Vector2f end, GameState state) {
        return findPath(start, end, state, null);
    }

    public static List<Vector2f> findPath(Vector2f start, Vector2f end, GameState state, Integer ignoreBuildingId) {
        int startX = (int) (start.x / STEP_SIZE);
        int startZ = (int) (start.y / STEP_SIZE);
        int endX = (int) (end.x / STEP_SIZE);
        int endZ = (int) (end.y / STEP_SIZE);

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Set<Node> closedSet = new HashSet<>();
        Map<Node, Node> allNodes = new HashMap<>();

        Node startNode = new Node(startX, startZ);
        Node endNode = new Node(endX, endZ);
        openSet.add(startNode);
        allNodes.put(startNode, startNode);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (current.equals(endNode)) {
                return retracePath(current);
            }

            closedSet.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;

                    int nx = current.x + dx;
                    int nz = current.z + dz;

                    if (nx < 0 || nz < 0 || nx >= MapSettings.WORLD_SIZE / STEP_SIZE || nz >= MapSettings.WORLD_SIZE / STEP_SIZE) {
                        continue;
                    }

                    Node neighbor = new Node(nx, nz);
                    if (closedSet.contains(neighbor)) continue;

                    if (!isWalkable(current.x, current.z, nx, nz, state, ignoreBuildingId)) continue;

                    float moveCost = (dx == 0 || dz == 0) ? 1.0f : 1.414f;
                    float newGCost = current.gCost + moveCost;

                    Node existingNeighbor = allNodes.get(neighbor);
                    if (existingNeighbor == null || newGCost < existingNeighbor.gCost) {
                        if (existingNeighbor == null) {
                            existingNeighbor = neighbor;
                            allNodes.put(neighbor, existingNeighbor);
                        }
                        existingNeighbor.gCost = newGCost;
                        existingNeighbor.hCost = diagonalDistance(existingNeighbor, endNode);
                        existingNeighbor.parent = current;

                        if (!openSet.contains(existingNeighbor)) {
                            openSet.add(existingNeighbor);
                        }
                    }
                }
            }
        }

        return new ArrayList<>(); // パスが見つからない場合は空リスト
    }

    private static boolean isWalkable(int x1, int z1, int x2, int z2, GameState state, Integer ignoreBuildingId) {
        float h1 = TerrainGenerator.heightAt(x1 * STEP_SIZE, z1 * STEP_SIZE);
        float h2 = TerrainGenerator.heightAt(x2 * STEP_SIZE, z2 * STEP_SIZE);

        // 高度差（勾配）チェック
        if (Math.abs(h1 - h2) > MAX_SLOPE) return false;

        // 建物チェック
        float worldX = x2 * STEP_SIZE;
        float worldZ = z2 * STEP_SIZE;
        for (Building b : state.buildings.values()) {
            if (b.type == Building.Type.METAL_PATCH) continue;
            if (ignoreBuildingId != null && b.id == ignoreBuildingId.intValue()) continue;
            float dx = b.position.x - worldX;
            float dz = b.position.y - worldZ;
            float minDist = b.size / 2.0f + 0.5f;
            if (dx * dx + dz * dz < minDist * minDist) {
                return false;
            }
        }

        return true;
    }

    private static float diagonalDistance(Node a, Node b) {
        int dx = Math.abs(a.x - b.x);
        int dz = Math.abs(a.z - b.z);
        return (dx + dz) + (1.414f - 2.0f) * Math.min(dx, dz);
    }

    private static List<Vector2f> retracePath(Node endNode) {
        List<Vector2f> path = new ArrayList<>();
        Node current = endNode;
        while (current != null) {
            path.add(0, new Vector2f(current.x * STEP_SIZE, current.z * STEP_SIZE));
            current = current.parent;
        }
        return path;
    }
}
