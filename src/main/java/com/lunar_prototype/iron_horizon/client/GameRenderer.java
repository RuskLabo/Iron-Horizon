package com.lunar_prototype.iron_horizon.client;

import com.lunar_prototype.iron_horizon.common.Network;
import com.lunar_prototype.iron_horizon.common.MapSettings;
import com.lunar_prototype.iron_horizon.common.model.Building;
import com.lunar_prototype.iron_horizon.common.model.GameState;
import com.lunar_prototype.iron_horizon.common.model.Unit;
import com.lunar_prototype.iron_horizon.client.render.Mesh;
import com.lunar_prototype.iron_horizon.client.render.MeshFactory;
import com.lunar_prototype.iron_horizon.client.render.TerrainGenerator;
import com.lunar_prototype.iron_horizon.client.render.TerrainMeshFactory;
import com.lunar_prototype.iron_horizon.client.render.Texture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class GameRenderer {
    public static class MoveMarker {
        public float x, z, life = 1.0f;

        public MoveMarker(float x, float z) {
            this.x = x;
            this.z = z;
        }
    }

    public static class Effect {
        public enum Type { LASER, EXPLOSION }

        public Type type;
        public float x, y, tx, ty, life = 1.0f;

        public Effect(Type type, float x, float y, float tx, float ty) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.tx = tx;
            this.ty = ty;
        }
    }

    private static final float GRID_SIZE = MapSettings.TERRAIN_TILE_SIZE;

    private final long window;
    private final GameState gameState;
    private final SoundManager soundManager;
    private final Set<Integer> selectedUnitIds;
    private final Set<Integer> selectedBuildingIds;
    private final List<MoveMarker> moveMarkers;
    private final List<Effect> effects;
    private final List<Network.ProjectileData> projectileData;
    private Mesh cubeMesh;
    private Mesh terrainMesh;
    private Texture grassTexture;

    private Vector3f cameraPos = new Vector3f(50, 60, 100);
    private float pitch = 60;
    private float yaw = -90;
    private double lastMouseX;
    private double lastMouseY;
    private boolean rightMouseDown = false;
    private int windowWidth = 1280;
    private int windowHeight = 720;
    private int framebufferWidth = 1280;
    private int framebufferHeight = 720;

    public GameRenderer(
            long window,
            GameState gameState,
            SoundManager soundManager,
            Set<Integer> selectedUnitIds,
            Set<Integer> selectedBuildingIds,
            List<MoveMarker> moveMarkers,
            List<Effect> effects,
            List<Network.ProjectileData> projectileData) {
        this.window = window;
        this.gameState = gameState;
        this.soundManager = soundManager;
        this.selectedUnitIds = selectedUnitIds;
        this.selectedBuildingIds = selectedBuildingIds;
        this.moveMarkers = moveMarkers;
        this.effects = effects;
        this.projectileData = projectileData;
    }

    public void init() {
        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        cubeMesh = MeshFactory.createUnitCube();
        terrainMesh = TerrainMeshFactory.createTerrain();
        grassTexture = Texture.createGrassTexture(MapSettings.TERRAIN_TEXTURE_SIZE, MapSettings.TERRAIN_TEXTURE_SIZE);
        refreshWindowSize();
        glfwSetWindowSizeCallback(window, (win, width, height) -> {
            windowWidth = Math.max(1, width);
            windowHeight = Math.max(1, height);
        });
        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            framebufferWidth = Math.max(1, width);
            framebufferHeight = Math.max(1, height);
            glViewport(0, 0, framebufferWidth, framebufferHeight);
        });
        glViewport(0, 0, framebufferWidth, framebufferHeight);
    }

    public void cleanup() {
        if (terrainMesh != null) {
            terrainMesh.close();
            terrainMesh = null;
        }
        if (cubeMesh != null) {
            cubeMesh.close();
            cubeMesh = null;
        }
        if (grassTexture != null) {
            grassTexture.close();
            grassTexture = null;
        }
    }

    public void handleKeyboardInput(float dt) {
        float speed = 50.0f * dt;
        float radians = (float) Math.toRadians(yaw + 90);
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            cameraPos.x -= Math.sin(radians) * speed;
            cameraPos.z -= Math.cos(radians) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            cameraPos.x += Math.sin(radians) * speed;
            cameraPos.z += Math.cos(radians) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            cameraPos.x += Math.sin(radians - Math.PI / 2) * speed;
            cameraPos.z += Math.cos(radians - Math.PI / 2) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            cameraPos.x -= Math.sin(radians - Math.PI / 2) * speed;
            cameraPos.z -= Math.cos(radians - Math.PI / 2) * speed;
        }
        cameraPos.x = Math.max(0.0f, Math.min(MapSettings.WORLD_SIZE, cameraPos.x));
        cameraPos.z = Math.max(0.0f, Math.min(MapSettings.WORLD_SIZE, cameraPos.z));
    }

    public void onRightMouseButton(boolean down) {
        rightMouseDown = down;
    }

    public void onMouseMoved(double xpos, double ypos, boolean isMenuOpen, Building.Type selectedBuildType) {
        if (rightMouseDown && selectedBuildType == null && !isMenuOpen) {
            yaw += (float) (xpos - lastMouseX) * 0.2f;
            pitch += (float) (ypos - lastMouseY) * 0.2f;
            pitch = Math.max(-89, Math.min(89, pitch));
        }
        lastMouseX = xpos;
        lastMouseY = ypos;
    }

    public void onScroll(double yoffset, boolean isMenuOpen) {
        if (!isMenuOpen) {
            cameraPos.y -= (float) yoffset * 5.0f;
            cameraPos.y = Math.max(10, cameraPos.y);
        }
    }

    public Vector3f getMouseWorldPos(double screenX, double screenY) {
        Matrix4f vp = new Matrix4f()
                .perspective((float) Math.toRadians(45.0f), (float) framebufferWidth / framebufferHeight, 0.1f, 1000.0f)
                .rotate((float) Math.toRadians(pitch), 1, 0, 0)
                .rotate((float) Math.toRadians(yaw + 90), 0, 1, 0)
                .translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Vector3f near = new Vector3f();
        Vector3f far = new Vector3f();
        int[] viewport = {0, 0, framebufferWidth, framebufferHeight};
        vp.unproject((float) screenX, (float) framebufferHeight - (float) screenY, 0, viewport, near);
        vp.unproject((float) screenX, (float) framebufferHeight - (float) screenY, 1, viewport, far);
        float t = -near.y / (far.y - near.y);
        return new Vector3f(near).lerp(far, t);
    }

    public Vector3f getCameraPosition() {
        return new Vector3f(cameraPos);
    }

    public void renderFrame(
            int myTeamId,
            boolean isSelecting,
            double selectionStartX,
            double selectionStartY,
            Building.Type selectedBuildType,
            boolean isMenuOpen) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        renderWorldPass(myTeamId, selectedBuildType);
        renderOverlayPass();
        renderUiPass(myTeamId, isSelecting, selectionStartX, selectionStartY, selectedBuildType, isMenuOpen);
    }

    private void renderWorldPass(int myTeamId, Building.Type selectedBuildType) {
        setup3D();
        renderGround();
        synchronized (gameState) {
            renderBuildings(myTeamId);
            renderUnits(myTeamId);
            renderProjectiles(myTeamId);
        }
        synchronized (moveMarkers) {
            renderMoveMarkers();
        }
        renderBuildPreview(selectedBuildType);
    }

    private void renderOverlayPass() {
        synchronized (effects) {
            renderEffects();
        }
    }

    private void renderUiPass(
            int myTeamId,
            boolean isSelecting,
            double selectionStartX,
            double selectionStartY,
            Building.Type selectedBuildType,
            boolean isMenuOpen) {
        setup2D();
        renderSelectionBox(isSelecting, selectionStartX, selectionStartY);
        renderMinimap(myTeamId);
        renderHUD(myTeamId, selectedBuildType);
        if (isMenuOpen) {
            renderMenu();
        }
    }

    private void setup3D() {
        glEnable(GL_DEPTH_TEST);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(45.0f), (float) framebufferWidth / framebufferHeight, 0.1f, 1000.0f);
        float[] buffer = new float[16];
        proj.get(buffer);
        glLoadMatrixf(buffer);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        Matrix4f view = new Matrix4f()
                .rotate((float) Math.toRadians(pitch), 1, 0, 0)
                .rotate((float) Math.toRadians(yaw + 90), 0, 1, 0)
                .translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        view.get(buffer);
        glLoadMatrixf(buffer);
    }

    private void setup2D() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);
    }

    private void renderGround() {
        if (terrainMesh == null || grassTexture == null) {
            return;
        }
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, grassTexture.id());
        glColor3f(1.0f, 1.0f, 1.0f);
        terrainMesh.draw();
        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_TEXTURE_2D);
    }

    private void renderBuildings(int myTeamId) {
        for (Building b : gameState.buildings.values()) {
            glPushMatrix();
            glTranslatef(b.position.x, terrainHeightAt(b.position.x, b.position.y) + b.size / 2, b.position.y);
            if (b.type == Building.Type.METAL_PATCH) {
                glColor3f(1.0f, 0.5f, 0.0f);
            } else if (b.isComplete) {
                if (b.teamId == myTeamId) glColor3f(0.2f, 0.5f, 1.0f);
                else glColor3f(1.0f, 0.2f, 0.2f);
                if (b.type == Building.Type.NEXUS) glColor3f(0.8f, 0.2f, 0.8f);
                else if (b.type == Building.Type.LASER_TOWER) glColor3f(0.9f, 0.9f, 0.2f);
            } else {
                glColor4f(1, 1, 1, 0.3f + b.buildProgress * 0.7f);
            }
            float s = b.size / 2;
            renderCube(s);
            if (selectedBuildingIds.contains(b.id)) {
                glDisable(GL_DEPTH_TEST);
                glColor3f(1, 1, 0);
                glBegin(GL_LINE_LOOP);
                glVertex3f(-s, s + 0.1f, -s);
                glVertex3f(s, s + 0.1f, -s);
                glVertex3f(s, s + 0.1f, s);
                glVertex3f(-s, s + 0.1f, s);
                glEnd();
                glEnable(GL_DEPTH_TEST);
            }
            glPopMatrix();

            if (b.type != Building.Type.METAL_PATCH) {
                if (!b.isComplete) {
                    renderProgressBar(b.position.x, b.size + 1.0f, b.position.y, 3.0f, b.buildProgress, 0.2f, 0.5f, 1.0f);
                } else {
                    renderProgressBar(b.position.x, b.size + 1.0f, b.position.y, 3.0f, b.hp / b.maxHp, 0.2f, 1.0f, 0.2f);
                    if (b.type == Building.Type.FACTORY && !b.productionQueue.isEmpty()) {
                        renderProgressBar(b.position.x, b.size + 1.5f, b.position.y, 3.0f, b.productionTimer, 1.0f, 0.8f, 0.0f);
                    }
                }
            }
        }
    }

    private void renderCube(float s) {
        if (cubeMesh == null) {
            return;
        }
        glPushMatrix();
        glScalef(s, s, s);
        cubeMesh.draw();
        glPopMatrix();
    }

    private void renderUnits(int myTeamId) {
        for (Unit u : gameState.units.values()) {
            glPushMatrix();
            glTranslatef(u.position.x, terrainHeightAt(u.position.x, u.position.y) + 0.5f, u.position.y);
            if (selectedUnitIds.contains(u.id)) glColor3f(1.0f, 1.0f, 0.0f);
            else if (u.teamId == myTeamId) {
                if (u.type == Unit.Type.CONSTRUCTOR) glColor3f(0.2f, 0.8f, 0.2f);
                else glColor3f(0.0f, 0.8f, 1.0f);
            } else glColor3f(1.0f, 0.2f, 0.2f);
            float sz = (u.type == Unit.Type.CONSTRUCTOR) ? 0.3f : 0.5f;
            renderCube(sz);
            glPopMatrix();
            renderProgressBar(u.position.x, 1.5f, u.position.y, 1.0f, u.hp / u.maxHp, 0.2f, 1.0f, 0.2f);
        }
    }

    private void renderProjectiles(int myTeamId) {
        glLineWidth(5.0f);
        glBegin(GL_LINES);
        synchronized (projectileData) {
            for (Network.ProjectileData p : projectileData) {
                if (p.teamId == myTeamId) glColor4f(0.2f, 0.8f, 1.0f, 1.0f);
                else glColor4f(1.0f, 0.2f, 0.2f, 1.0f);

                float groundY = terrainHeightAt(p.x, p.y) + 0.5f;
                glVertex3f(p.x, groundY, p.y);

                float trailLen = 3.0f;
                float vLen = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
                if (vLen > 0) {
                    glVertex3f(p.x - (p.vx / vLen) * trailLen, groundY, p.y - (p.vy / vLen) * trailLen);
                } else {
                    glVertex3f(p.x, groundY, p.y);
                }
            }
        }
        glEnd();
    }

    private void renderEffects() {
        for (Effect e : effects) {
            if (e.type == Effect.Type.LASER) {
                glLineWidth(2.0f);
                glColor4f(1, 1, 0, e.life);
                glBegin(GL_LINES);
                glVertex3f(e.x, 0.5f, e.y);
                glVertex3f(e.tx, 0.5f, e.ty);
                glEnd();
            } else {
                float s = (1.0f - e.life) * 3.0f;
                glColor4f(1, 0.5f, 0, e.life);
                glPushMatrix();
                glTranslatef(e.x, 0.5f, e.y);
                glBegin(GL_QUADS);
                glVertex3f(-s, s, 0);
                glVertex3f(s, s, 0);
                glVertex3f(s, -s, 0);
                glVertex3f(-s, -s, 0);
                glEnd();
                glPopMatrix();
            }
        }
    }

    private void renderBuildPreview(Building.Type selectedBuildType) {
        if (selectedBuildType == null) return;
        double[] x = new double[1];
        double[] y = new double[1];
        glfwGetCursorPos(window, x, y);
        Vector3f pos = getMouseWorldPos(x[0], y[0]);
        float gx = (float) Math.floor(pos.x / GRID_SIZE) * GRID_SIZE + GRID_SIZE / 2;
        float gz = (float) Math.floor(pos.z / GRID_SIZE) * GRID_SIZE + GRID_SIZE / 2;
        glPushMatrix();
        glTranslatef(gx, terrainHeightAt(gx, gz) + 1.0f, gz);
        glColor4f(1, 1, 1, 0.5f);
        float s = (selectedBuildType == Building.Type.FACTORY) ? 1.5f : (selectedBuildType == Building.Type.LASER_TOWER ? 0.9f : 0.5f);
        glBegin(GL_LINE_LOOP);
        glVertex3f(-s, 0, -s);
        glVertex3f(s, 0, -s);
        glVertex3f(s, 0, s);
        glVertex3f(-s, 0, s);
        glEnd();
        glPopMatrix();
    }

    private void renderMoveMarkers() {
        for (MoveMarker m : moveMarkers) {
            glColor4f(1.0f, 1.0f, 0.0f, m.life);
            glPushMatrix();
            glTranslatef(m.x, terrainHeightAt(m.x, m.z) + 0.1f, m.z);
            glBegin(GL_LINE_LOOP);
            float s = 1.0f * (2.0f - m.life);
            glVertex3f(-s, 0, -s);
            glVertex3f(s, 0, -s);
            glVertex3f(s, 0, s);
            glVertex3f(-s, 0, s);
            glEnd();
            glPopMatrix();
        }
    }

    private void renderSelectionBox(boolean isSelecting, double selectionStartX, double selectionStartY) {
        if (!isSelecting) return;
        double[] x = new double[1];
        double[] y = new double[1];
        glfwGetCursorPos(window, x, y);
        glColor3f(0, 1, 0);
        glBegin(GL_LINE_LOOP);
        glVertex2d(selectionStartX, selectionStartY);
        glVertex2d(x[0], selectionStartY);
        glVertex2d(x[0], y[0]);
        glVertex2d(selectionStartX, y[0]);
        glEnd();
    }

    private void renderMinimap(int myTeamId) {
        float size = 150;
        float x = windowWidth - size - 20;
        float y = 20;
        glColor4f(0, 0, 0, 0.7f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + size, y);
        glVertex2f(x + size, y + size);
        glVertex2f(x, y + size);
        glEnd();
        glLineWidth(1);
        glColor3f(0.5f, 0.5f, 0.5f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + size, y);
        glVertex2f(x + size, y + size);
        glVertex2f(x, y + size);
        glEnd();
        synchronized (gameState) {
            for (Building b : gameState.buildings.values()) {
                if (b.type == Building.Type.METAL_PATCH) glColor3f(1, 0.5f, 0);
                else if (b.teamId == myTeamId) glColor3f(0, 0.5f, 1);
                else glColor3f(1, 0, 0);
                float px = x + (b.position.x / MapSettings.WORLD_SIZE) * size;
                float py = y + (b.position.y / MapSettings.WORLD_SIZE) * size;
                glBegin(GL_QUADS);
                glVertex2f(px - 2, py - 2);
                glVertex2f(px + 2, py - 2);
                glVertex2f(px + 2, py + 2);
                glVertex2f(px - 2, py + 2);
                glEnd();
            }
            for (Unit u : gameState.units.values()) {
                if (u.teamId == myTeamId) glColor3f(0, 1, 0);
                else glColor3f(1, 0, 0);
                float px = x + (u.position.x / MapSettings.WORLD_SIZE) * size;
                float py = y + (u.position.y / MapSettings.WORLD_SIZE) * size;
                glPointSize(2);
                glBegin(GL_POINTS);
                glVertex2f(px, py);
                glEnd();
            }
        }
        glColor3f(1, 1, 1);
        glBegin(GL_LINE_LOOP);
        float viewportSize = 150.0f;
        float vx = x + ((cameraPos.x - viewportSize / 2.0f) / MapSettings.WORLD_SIZE) * size;
        float vy = y + ((cameraPos.z - viewportSize / 2.0f) / MapSettings.WORLD_SIZE) * size;
        float vw = (viewportSize / MapSettings.WORLD_SIZE) * size;
        glVertex2f(vx, vy);
        glVertex2f(vx + vw, vy);
        glVertex2f(vx + vw, vy + vw);
        glVertex2f(vx, vy + vw);
        glEnd();
    }

    private void renderHUD(int myTeamId, Building.Type selectedBuildType) {
        float hudY = windowHeight - 80;
        glColor4f(0.06f, 0.08f, 0.06f, 0.92f);
        glBegin(GL_QUADS);
        glVertex2f(0, hudY);
        glVertex2f(windowWidth, hudY);
        glVertex2f(windowWidth, windowHeight);
        glVertex2f(0, windowHeight);
        glEnd();
        glColor4f(0.2f, 0.8f, 0.35f, 0.35f);
        glBegin(GL_QUADS);
        glVertex2f(0, hudY);
        glVertex2f(windowWidth, hudY);
        glVertex2f(windowWidth, hudY + 3);
        glVertex2f(0, hudY + 3);
        glEnd();
        if (gameState.winnerTeamId != 0) {
            drawText(gameState.winnerTeamId == myTeamId ? "VICTORY!" : "DEFEAT", windowWidth / 2 - 100, windowHeight / 2, 5.0f);
            return;
        }
        if (!gameState.isStarted) {
            renderButton(20, hudY + 10, 200, 60, "START GAME", true);
            drawText("WAITING FOR START...", 250, hudY + 35, 1.5f);
            return;
        }
        float met = gameState.teamMetal.getOrDefault(myTeamId, 0f);
        float inc = gameState.teamIncome.getOrDefault(myTeamId, 0f);
        float drn = gameState.teamDrain.getOrDefault(myTeamId, 0f);
        if (met <= 0 && drn > inc) glColor3f(1, 0.2f, 0.2f);
        else glColor3f(1, 1, 1);
        drawText(String.format("METAL: %d (+%.1f / -%.1f)", (int) met, inc, drn), 20, 20, 1.5f);
        boolean cS = false;
        synchronized (gameState) {
            for (Integer id : selectedUnitIds) {
                Unit u = gameState.units.get(id);
                if (u != null && u.type == Unit.Type.CONSTRUCTOR) {
                    cS = true;
                    break;
                }
            }
        }
        Building factory = null;
        synchronized (gameState) {
            for (Integer id : selectedBuildingIds) {
                Building b = gameState.buildings.get(id);
                if (b != null && b.type == Building.Type.FACTORY) {
                    factory = b;
                    break;
                }
            }
        }
        if (cS) {
            renderButton(20, hudY + 10, 120, 60, "FACTORY", selectedBuildType == Building.Type.FACTORY);
            renderButton(150, hudY + 10, 120, 60, "WALL", selectedBuildType == Building.Type.WALL);
            renderButton(280, hudY + 10, 120, 60, "EXTRACT", selectedBuildType == Building.Type.EXTRACTOR);
            renderButton(410, hudY + 10, 120, 60, "LASER", selectedBuildType == Building.Type.LASER_TOWER);
        } else if (factory != null) {
            renderButton(20, hudY + 10, 150, 60, "TANK (" + factory.productionQueue.size() + ")", true);
            renderButton(180, hudY + 10, 150, 60, "BOT", true);
        } else {
            drawText("SELECT ALLIES", 20, hudY + 35, 1.5f);
        }
        drawText("WASD:Move Rotate:R-Drag Select:L-Drag Action:R-Click ESC:Menu", 450, hudY + 35, 1.2f);
    }

    private void renderMenu() {
        float cx = windowWidth / 2.0f;
        float cy = windowHeight / 2.0f;
        glColor4f(0.03f, 0.06f, 0.04f, 0.92f);
        glBegin(GL_QUADS);
        glVertex2f(cx - 150, cy - 150);
        glVertex2f(cx + 150, cy - 150);
        glVertex2f(cx + 150, cy + 200);
        glVertex2f(cx - 150, cy + 200);
        glEnd();
        glColor4f(0.2f, 0.8f, 0.35f, 0.35f);
        glBegin(GL_QUADS);
        glVertex2f(cx - 150, cy - 150);
        glVertex2f(cx + 150, cy - 150);
        glVertex2f(cx + 150, cy - 146);
        glVertex2f(cx - 150, cy - 146);
        glEnd();
        drawText("PAUSE MENU", cx - 80, cy - 120, 2.0f);
        drawText("VOLUME:", cx - 130, cy - 40, 1.5f);
        glColor3f(0.3f, 0.3f, 0.3f);
        glBegin(GL_QUADS);
        glVertex2f(cx - 100, cy);
        glVertex2f(cx + 100, cy);
        glVertex2f(cx + 100, cy + 20);
        glVertex2f(cx - 100, cy + 20);
        glEnd();
        float vol = soundManager.getMasterVolume();
        glColor3f(0.4f, 0.8f, 0.4f);
        glBegin(GL_QUADS);
        glVertex2f(cx - 100, cy);
        glVertex2f(cx - 100 + vol * 200, cy);
        glVertex2f(cx - 100 + vol * 200, cy + 20);
        glVertex2f(cx - 100, cy + 20);
        glEnd();
        renderButton(cx - 100, cy + 40, 200, 60, "RESUME", false);
        renderButton(cx - 100, cy + 110, 200, 60, "QUIT", false);
    }

    private float terrainHeightAt(float x, float z) {
        return TerrainGenerator.heightAt(clamp(x, 0.0f, MapSettings.WORLD_SIZE), clamp(z, 0.0f, MapSettings.WORLD_SIZE));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void refreshWindowSize() {
        int[] windowWidthOut = new int[1];
        int[] windowHeightOut = new int[1];
        int[] framebufferWidthOut = new int[1];
        int[] framebufferHeightOut = new int[1];
        glfwGetWindowSize(window, windowWidthOut, windowHeightOut);
        glfwGetFramebufferSize(window, framebufferWidthOut, framebufferHeightOut);
        windowWidth = Math.max(1, windowWidthOut[0]);
        windowHeight = Math.max(1, windowHeightOut[0]);
        framebufferWidth = Math.max(1, framebufferWidthOut[0]);
        framebufferHeight = Math.max(1, framebufferHeightOut[0]);
    }

    private void renderButton(float x, float y, float width, float height, String label, boolean active) {
        glColor4f(0, 0, 0, 0.35f);
        glBegin(GL_QUADS);
        glVertex2f(x + 3, y + 4);
        glVertex2f(x + width + 3, y + 4);
        glVertex2f(x + width + 3, y + height + 4);
        glVertex2f(x + 3, y + height + 4);
        glEnd();
        if (active) glColor3f(0.16f, 0.58f, 0.26f);
        else glColor3f(0.18f, 0.2f, 0.18f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        glColor3f(0.34f, 0.85f, 0.45f);
        glLineWidth(2);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        drawText(label, x + 12, y + 35, 1.45f);
    }

    private void drawText(String text, float x, float y, float scale) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(text.length() * 270);
        int vertices = STBEasyFont.stb_easy_font_print(0, 0, text, null, buffer);
        glPushMatrix();
        glTranslatef(x, y, 0);
        glScalef(scale, scale, 1);
        glDisable(GL_TEXTURE_2D);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        glVertexPointer(2, GL_FLOAT, 16, buffer);
        buffer.position(12);
        glColorPointer(4, GL_UNSIGNED_BYTE, 16, buffer);
        glDrawArrays(GL_QUADS, 0, vertices * 4);
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        glPopMatrix();
    }

    private void renderProgressBar(float x, float y, float z, float width, float progress, float r, float g, float b) {
        glPushMatrix();
        glTranslatef(x, y, z);
        float[] modelView = new float[16];
        glGetFloatv(GL_MODELVIEW_MATRIX, modelView);
        modelView[0] = 1;
        modelView[1] = 0;
        modelView[2] = 0;
        modelView[4] = 0;
        modelView[5] = 1;
        modelView[6] = 0;
        modelView[8] = 0;
        modelView[9] = 0;
        modelView[10] = 1;
        glLoadMatrixf(modelView);
        float h = 0.2f;
        float halfWidth = width / 2;
        glDisable(GL_DEPTH_TEST);
        glColor3f(0, 0, 0);
        glBegin(GL_QUADS);
        glVertex3f(-halfWidth, 0, 0);
        glVertex3f(halfWidth, 0, 0);
        glVertex3f(halfWidth, h, 0);
        glVertex3f(-halfWidth, h, 0);
        glEnd();
        glColor3f(r, g, b);
        float progressWidth = -halfWidth + (width * Math.max(0, Math.min(1, progress)));
        glBegin(GL_QUADS);
        glVertex3f(-halfWidth, 0, 0.01f);
        glVertex3f(progressWidth, 0, 0.01f);
        glVertex3f(progressWidth, h, 0.01f);
        glVertex3f(-halfWidth, h, 0.01f);
        glEnd();
        glEnable(GL_DEPTH_TEST);
        glPopMatrix();
    }
}
