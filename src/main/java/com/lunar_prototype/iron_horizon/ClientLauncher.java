package com.lunar_prototype.iron_horizon;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.lunar_prototype.iron_horizon.client.SoundManager;
import com.lunar_prototype.iron_horizon.common.Network;
import com.lunar_prototype.iron_horizon.common.model.Building;
import com.lunar_prototype.iron_horizon.common.model.GameState;
import com.lunar_prototype.iron_horizon.common.model.Unit;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBEasyFont;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class ClientLauncher {

    private long window;
    private Client client;
    private final GameState gameState = new GameState();
    private final SoundManager soundManager = new SoundManager();
    private final Set<Integer> selectedUnitIds = new HashSet<>();
    private final Set<Integer> selectedBuildingIds = new HashSet<>();
    private final List<MoveMarker> moveMarkers = new ArrayList<>();
    private final List<Effect> effects = new ArrayList<>();
    private List<Network.ProjectileData> projectileData = new ArrayList<>();
    private int myTeamId = 0;
    private boolean gameStartedPreviously = false;
    private int winnerPreviously = 0;
    private boolean isMenuOpen = false;

    private Building.Type selectedBuildType = null;
    private static final float GRID_SIZE = 2.0f;

    private static class MoveMarker {
        float x, z, life = 1.0f;
        MoveMarker(float x, float z) { this.x = x; this.z = z; }
    }

    private static class Effect {
        enum Type { LASER, EXPLOSION }
        Type type;
        float x, y, tx, ty, life = 1.0f;
        Effect(Type t, float x, float y, float tx, float ty) { this.type=t; this.x=x; this.y=y; this.tx=tx; this.ty=ty; }
    }
    
    private Vector3f cameraPos = new Vector3f(50, 60, 100);
    private float pitch = 60, yaw = -90;
    private double lastMouseX, lastMouseY;
    private boolean rightMouseDown = false, isSelecting = false;
    private double selectionStartX, selectionStartY;

    public void run() {
        init();
        loop();
        soundManager.cleanup();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
        if (client != null) client.stop();
    }

    private void init() {
        soundManager.init();
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        window = glfwCreateWindow(1280, 720, "Iron Horizon - RTS", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create the GLFW window");

        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            double[] x = new double[1], y = new double[1];
            glfwGetCursorPos(w, x, y);
            if (isMenuOpen) { if (action == GLFW_PRESS) checkMenuClick(x[0], y[0]); return; }
            boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                if (checkUIClick(x[0], y[0])) return;
                if (!gameState.isStarted || gameState.winnerTeamId != 0) return;
                if (selectedBuildType != null) { placeBuilding(x[0], y[0], shift); if (!shift) selectedBuildType = null; return; }
                selectionStartX = x[0]; selectionStartY = y[0]; isSelecting = true;
            } else if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_RELEASE) {
                if (isSelecting) finishSelection(x[0], y[0]);
                isSelecting = false;
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
                if (selectedBuildType != null) selectedBuildType = null;
                else handleRightClick(x[0], y[0]);
                rightMouseDown = true;
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_RELEASE) {
                rightMouseDown = false;
            }
        });

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                if (selectedBuildType != null || !selectedUnitIds.isEmpty() || !selectedBuildingIds.isEmpty()) { selectedUnitIds.clear(); selectedBuildingIds.clear(); selectedBuildType = null; }
                else isMenuOpen = !isMenuOpen;
            }
        });

        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (rightMouseDown && selectedBuildType == null && !isMenuOpen) {
                yaw += (float) (xpos - lastMouseX) * 0.2f; pitch += (float) (ypos - lastMouseY) * 0.2f;
                pitch = Math.max(-89, Math.min(89, pitch));
            }
            lastMouseX = xpos; lastMouseY = ypos;
        });

        glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
            if (!isMenuOpen) { cameraPos.y -= yoffset * 5.0f; cameraPos.y = Math.max(10, cameraPos.y); }
        });

        glfwMakeContextCurrent(window); glfwSwapInterval(1); glfwShowWindow(window);

        client = new Client(256000, 256000);
        Network.register(client);
        client.addListener(new Listener() {
            public void received (Connection connection, Object object) {
                if (object instanceof Network.LoginResponse) {
                    myTeamId = ((Network.LoginResponse) object).teamId;
                } else if (object instanceof Network.StateUpdate) {
                    Network.StateUpdate update = (Network.StateUpdate) object;
                    synchronized (gameState) {
                        gameState.teamMetal.putAll(update.teamMetal);
                        gameState.teamIncome.putAll(update.teamIncome);
                        gameState.teamDrain.putAll(update.teamDrain);
                        if (update.isStarted && !gameStartedPreviously) { soundManager.playSound("start"); gameStartedPreviously = true; }
                        if (update.winnerTeamId != 0 && winnerPreviously == 0) { soundManager.playSound(update.winnerTeamId == myTeamId ? "victory" : "defeat"); winnerPreviously = update.winnerTeamId; }
                        gameState.isStarted = update.isStarted; gameState.winnerTeamId = update.winnerTeamId;
                        int oldT = (int) gameState.units.values().stream().filter(u -> u.teamId == myTeamId && u.type == Unit.Type.TANK).count();
                        int newT = (int) update.units.stream().filter(u -> u.teamId == myTeamId && u.type == Unit.Type.TANK).count();
                        if (newT > oldT) soundManager.playSound("ready");
                        gameState.units.clear();
                        for (Network.UnitData data : update.units) {
                            Unit u = new Unit(data.id, data.x, data.y);
                            u.type = data.type; u.teamId = data.teamId; u.hp = data.hp; u.maxHp = data.maxHp;
                            gameState.addUnit(u);
                        }
                        gameState.buildings.clear();
                        for (Network.BuildingData bData : update.buildings) {
                            Building b = new Building(bData.id, bData.type, bData.x, bData.y, bData.teamId);
                            b.hp = bData.hp; b.maxHp = bData.maxHp; b.buildProgress = bData.buildProgress; b.isComplete = bData.isComplete;
                            b.productionTimer = bData.productionProgress; b.productionQueue.addAll(bData.productionQueue);
                            gameState.addBuilding(b);
                        }
                        projectileData = update.projectiles;
                    }
                    synchronized (effects) {
                        for (Network.CombatEvent e : update.events) {
                            if (e.type == Network.CombatEvent.Type.ATTACK) {
                                for (Network.BuildingData bd : update.buildings) { if (bd.teamId == myTeamId && Math.abs(bd.x - e.tx) < 2.0f && Math.abs(bd.y - e.ty) < 2.0f) { soundManager.playSound("under_attack"); break; } }
                            }
                            else if (e.type == Network.CombatEvent.Type.EXPLOSION) effects.add(new Effect(Effect.Type.EXPLOSION, e.x, e.y, 0, 0));
                        }
                    }
                }
            }
        });
        client.start();
        try { client.connect(5000, "localhost", Network.TCP_PORT, Network.UDP_PORT); Network.LoginRequest req = new Network.LoginRequest(); req.username = "Player1"; client.sendTCP(req); } catch (IOException e) { e.printStackTrace(); }
    }

    private void checkMenuClick(double x, double y) {
        int[] w = new int[1], h = new int[1]; glfwGetWindowSize(window, w, h);
        float cx = w[0]/2.0f, cy = h[0]/2.0f;
        if (x > cx-100 && x < cx+100 && y > cy && y < cy+20) {
            float vol = (float)((x - (cx-100)) / 200.0); soundManager.setMasterVolume(vol);
        }
        if (x > cx-100 && x < cx+100 && y > cy+40 && y < cy+100) isMenuOpen = false;
        if (x > cx-100 && x < cx+100 && y > cy+110 && y < cy+170) glfwSetWindowShouldClose(window, true);
    }

    private boolean checkUIClick(double x, double y) {
        int[] wh = new int[1], ht = new int[1]; glfwGetWindowSize(window, wh, ht);
        if (y < ht[0] - 80) return false;
        if (!gameState.isStarted) { if (x > 20 && x < 200) client.sendTCP(new Network.StartGameCommand()); return true; }
        boolean cS = false; synchronized(gameState) { for (Integer id : selectedUnitIds) { Unit u = gameState.units.get(id); if (u!=null && u.type==Unit.Type.CONSTRUCTOR) { cS=true; break; } } }
        boolean fS = false; int fid = -1;
        synchronized(gameState) { for (Integer id : selectedBuildingIds) { Building b = gameState.buildings.get(id); if (b!=null && b.type==Building.Type.FACTORY && b.isComplete) { fS=true; fid=id; break; } } }
        if (cS) {
            if (x > 20 && x < 140) selectedBuildType = Building.Type.FACTORY;
            else if (x > 150 && x < 270) selectedBuildType = Building.Type.WALL;
            else if (x > 280 && x < 400) selectedBuildType = Building.Type.EXTRACTOR;
        } else if (fS) {
            if (x > 20 && x < 170) sendProduceCommand(fid, Unit.Type.TANK);
            else if (x > 180 && x < 330) sendProduceCommand(fid, Unit.Type.CONSTRUCTOR);
        }
        return true;
    }

    private void handleRightClick(double x, double y) {
        if (!gameState.isStarted || selectedUnitIds.isEmpty()) return;
        Vector3f target = getMouseWorldPos(x, y); Vector2f target2d = new Vector2f(target.x, target.z);
        synchronized(gameState) {
            for (Unit u : gameState.units.values()) if (u.teamId != myTeamId && u.position.distance(target2d) < 2.0f) { soundManager.playSound("attack"); sendAttackCommand(u.id, null); return; }
            for (Building b : gameState.buildings.values()) if (b.position.distance(target2d) < b.size) {
                if (b.teamId != myTeamId && b.type != Building.Type.METAL_PATCH) { soundManager.playSound("attack"); sendAttackCommand(null, b.id); return; }
                else if (b.teamId == myTeamId && !b.isComplete) { sendMoveCommand(x, y); return; }
            }
        }
        soundManager.playSound("move"); sendMoveCommand(x, y);
    }

    private void sendAttackCommand(Integer tU, Integer tB) {
        Network.AttackCommand cmd = new Network.AttackCommand(); cmd.unitIds.addAll(selectedUnitIds); cmd.targetUnitId = tU; cmd.targetBuildingId = tB;
        client.sendTCP(cmd);
    }

    private void placeBuilding(double x, double y, boolean shift) {
        Integer cId = null; synchronized(gameState) { for (Integer id : selectedUnitIds) { Unit u = gameState.units.get(id); if (u!=null && u.type==Unit.Type.CONSTRUCTOR) { cId=id; break; } } }
        if (cId == null) return;
        Vector3f pos = getMouseWorldPos(x, y);
        float gx = (float) Math.floor(pos.x/GRID_SIZE)*GRID_SIZE + GRID_SIZE/2, gz = (float) Math.floor(pos.z/GRID_SIZE)*GRID_SIZE + GRID_SIZE/2;
        soundManager.playSound("build");
        Network.BuildCommand cmd = new Network.BuildCommand();
        cmd.buildingType = selectedBuildType; cmd.x = gx; cmd.y = gz; cmd.constructorUnitId = cId; cmd.shiftHold = shift;
        client.sendTCP(cmd);
    }

    private void finishSelection(double endX, double endY) {
        Vector3f sW = getMouseWorldPos(selectionStartX, selectionStartY), eW = getMouseWorldPos(endX, endY);
        float minX = Math.min(sW.x, eW.x), maxX = Math.max(sW.x, eW.x), minZ = Math.min(sW.z, eW.z), maxZ = Math.max(sW.z, eW.z);
        boolean anySelected = false; selectedUnitIds.clear(); selectedBuildingIds.clear();
        synchronized(gameState) {
            for (Unit u : gameState.units.values()) if (u.teamId == myTeamId && u.position.x>=minX && u.position.x<=maxX && u.position.y>=minZ && u.position.y<=maxZ) { selectedUnitIds.add(u.id); anySelected=true; }
            for (Building b : gameState.buildings.values()) if (b.teamId == myTeamId && b.position.x>=minX && b.position.x<=maxX && b.position.y>=minZ && b.position.y<=maxZ) { selectedBuildingIds.add(b.id); anySelected=true; }
        }
        if (anySelected) soundManager.playSound("selected");
    }

    private Vector3f getMouseWorldPos(double sX, double sY) {
        int[] w = new int[1], h = new int[1]; glfwGetWindowSize(window, w, h);
        Matrix4f vp = new Matrix4f().perspective((float)Math.toRadians(45.0f), (float)w[0]/h[0], 0.1f, 1000.0f)
            .rotate((float)Math.toRadians(pitch), 1, 0, 0).rotate((float)Math.toRadians(yaw + 90), 0, 1, 0).translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Vector3f n = new Vector3f(), f = new Vector3f(); int[] v = {0, 0, w[0], h[0]};
        vp.unproject((float)sX, (float)h[0] - (float)sY, 0, v, n); vp.unproject((float)sX, (float)h[0] - (float)sY, 1, v, f);
        float t = -n.y / (f.y - n.y); return new Vector3f(n).lerp(f, t);
    }

    private void sendMoveCommand(double sX, double sY) {
        if (selectedUnitIds.isEmpty()) return;
        Vector3f target = getMouseWorldPos(sX, sY); moveMarkers.add(new MoveMarker(target.x, target.z));
        Network.MoveCommand cmd = new Network.MoveCommand(); cmd.targetX = target.x; cmd.targetY = target.z; cmd.unitIds.addAll(selectedUnitIds);
        synchronized(gameState) { for (Building b : gameState.buildings.values()) if (b.position.distance(new Vector2f(target.x, target.z)) < b.size) { cmd.targetBuildingId = b.id; break; } }
        client.sendTCP(cmd);
    }

    private void sendProduceCommand(int fId, Unit.Type type) {
        Network.ProduceCommand cmd = new Network.ProduceCommand(); cmd.factoryId = fId; cmd.unitType = type; client.sendTCP(cmd);
    }

    private void loop() {
        GL.createCapabilities(); glEnable(GL_DEPTH_TEST); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        long lastTime = System.currentTimeMillis();
        while (!glfwWindowShouldClose(window)) {
            long now = System.currentTimeMillis(); float dt = (now - lastTime) / 1000.0f; lastTime = now;
            if (!isMenuOpen) { 
                handleInput(dt); syncViewport(); 
                synchronized(moveMarkers) { moveMarkers.removeIf(m -> (m.life -= dt * 2.0f) <= 0); }
                synchronized(effects) { effects.removeIf(e -> (e.life -= dt * 3.0f) <= 0); }
            }
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            setup3D(); renderGround(); 
            synchronized(gameState) { renderBuildings(); renderUnits(); renderProjectiles(); }
            synchronized(moveMarkers) { renderMoveMarkers(); }
            renderBuildPreview(); synchronized(effects) { renderEffects(); }
            setup2D(); renderSelectionBox(); renderMinimap(); renderHUD(); if (isMenuOpen) renderMenu();
            glfwSwapBuffers(window); glfwPollEvents();
        }
    }

    private void handleInput(float dt) {
        float s = 50.0f * dt; float r = (float)Math.toRadians(yaw + 90);
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) { cameraPos.x += Math.sin(r) * s; cameraPos.z += Math.cos(r) * s; }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) { cameraPos.x -= Math.sin(r) * s; cameraPos.z -= Math.cos(r) * s; }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) { cameraPos.x += Math.sin(r-Math.PI/2) * s; cameraPos.z += Math.cos(r-Math.PI/2) * s; }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) { cameraPos.x -= Math.sin(r-Math.PI/2) * s; cameraPos.z -= Math.cos(r-Math.PI/2) * s; }
    }

    private void setup3D() {
        glEnable(GL_DEPTH_TEST); glMatrixMode(GL_PROJECTION); glLoadIdentity();
        int[] w = new int[1], h = new int[1]; glfwGetWindowSize(window, w, h);
        Matrix4f proj = new Matrix4f().perspective((float)Math.toRadians(45.0f), (float)w[0]/h[0], 0.1f, 1000.0f);
        float[] fb = new float[16]; proj.get(fb); glLoadMatrixf(fb);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity();
        Matrix4f view = new Matrix4f().rotate((float)Math.toRadians(pitch), 1, 0, 0).rotate((float)Math.toRadians(yaw + 90), 0, 1, 0).translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        view.get(fb); glLoadMatrixf(fb);
    }

    private void setup2D() {
        int[] w = new int[1], h = new int[1]; glfwGetWindowSize(window, w, h);
        glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(0, w[0], h[0], 0, -1, 1);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity(); glDisable(GL_DEPTH_TEST);
    }

    private void renderGround() {
        glBegin(GL_LINES); glColor3f(0.2f, 0.2f, 0.2f);
        for (int i = 0; i <= 100; i += 2) { glVertex3f(i, 0, 0); glVertex3f(i, 0, 100); glVertex3f(0, 0, i); glVertex3f(100, 0, i); }
        glEnd();
    }

    private void renderBuildings() {
        for (Building b : gameState.buildings.values()) {
            glPushMatrix(); glTranslatef(b.position.x, b.size / 2, b.position.y);
            if (b.type == Building.Type.METAL_PATCH) glColor3f(1.0f, 0.5f, 0.0f);
            else if (b.isComplete) { if (b.teamId == myTeamId) glColor3f(0.2f, 0.5f, 1.0f); else glColor3f(1.0f, 0.2f, 0.2f); if (b.type == Building.Type.NEXUS) glColor3f(0.8f, 0.2f, 0.8f); }
            else glColor4f(1, 1, 1, 0.3f + b.buildProgress * 0.7f);
            float s = b.size / 2;
            glBegin(GL_QUADS);
            glVertex3f(-s, s, s); glVertex3f(s, s, s); glVertex3f(s, -s, s); glVertex3f(-s, -s, s);
            glVertex3f(-s, s, -s); glVertex3f(s, s, -s); glVertex3f(s, -s, -s); glVertex3f(-s, -s, -s);
            glVertex3f(-s, s, -s); glVertex3f(s, s, -s); glVertex3f(s, s, s); glVertex3f(-s, s, s);
            glVertex3f(-s, -s, -s); glVertex3f(s, -s, -s); glVertex3f(s, -s, s); glVertex3f(-s, -s, s);
            glVertex3f(s, -s, -s); glVertex3f(s, s, -s); glVertex3f(s, s, s); glVertex3f(s, -s, s);
            glVertex3f(-s, -s, -s); glVertex3f(-s, s, -s); glVertex3f(-s, s, s); glVertex3f(-s, -s, s);
            glEnd(); 
            if (selectedBuildingIds.contains(b.id)) { glDisable(GL_DEPTH_TEST); glColor3f(1, 1, 0); glBegin(GL_LINE_LOOP); glVertex3f(-s, s+0.1f, -s); glVertex3f(s, s+0.1f, -s); glVertex3f(s, s+0.1f, s); glVertex3f(-s, s+0.1f, s); glEnd(); glEnable(GL_DEPTH_TEST); }
            glPopMatrix();
            if (b.type != Building.Type.METAL_PATCH) {
                if (!b.isComplete) renderProgressBar(b.position.x, b.size + 1.0f, b.position.y, 3.0f, b.buildProgress, 0.2f, 0.5f, 1.0f);
                else { renderProgressBar(b.position.x, b.size + 1.0f, b.position.y, 3.0f, b.hp / b.maxHp, 0.2f, 1.0f, 0.2f);
                    if (b.type == Building.Type.FACTORY && !b.productionQueue.isEmpty()) renderProgressBar(b.position.x, b.size + 1.5f, b.position.y, 3.0f, b.productionTimer, 1.0f, 0.8f, 0.0f);
                }
            }
        }
    }

    private void renderUnits() {
        for (Unit u : gameState.units.values()) {
            glPushMatrix(); glTranslatef(u.position.x, 0.5f, u.position.y);
            if (selectedUnitIds.contains(u.id)) glColor3f(1.0f, 1.0f, 0.0f);
            else if (u.teamId == myTeamId) { if (u.type == Unit.Type.CONSTRUCTOR) glColor3f(0.2f, 0.8f, 0.2f); else glColor3f(0.0f, 0.8f, 1.0f); }
            else glColor3f(1.0f, 0.2f, 0.2f);
            float sz = (u.type == Unit.Type.CONSTRUCTOR) ? 0.3f : 0.5f;
            glBegin(GL_QUADS);
            glVertex3f(-sz, sz, sz); glVertex3f(sz, sz, sz); glVertex3f(sz, -sz, sz); glVertex3f(-sz, -sz, sz); 
            glVertex3f(-sz, sz, -sz); glVertex3f(sz, sz, -sz); glVertex3f(sz, -sz, -sz); glVertex3f(-sz, -sz, -sz); 
            glEnd(); glPopMatrix();
            renderProgressBar(u.position.x, 1.5f, u.position.y, 1.0f, u.hp / u.maxHp, 0.2f, 1.0f, 0.2f);
        }
    }

    private void renderProjectiles() {
        glLineWidth(5.0f);
        glBegin(GL_LINES);
        for (Network.ProjectileData p : projectileData) {
            if (p.teamId == myTeamId) glColor4f(0.2f, 0.8f, 1.0f, 1.0f); 
            else glColor4f(1.0f, 0.2f, 0.2f, 1.0f);
            
            glVertex3f(p.x, 0.5f, p.y);
            
            float trailLen = 3.0f; // Long trail
            float vLen = (float)Math.sqrt(p.vx*p.vx + p.vy*p.vy);
            if (vLen > 0) {
                glVertex3f(p.x - (p.vx/vLen)*trailLen, 0.5f, p.y - (p.vy/vLen)*trailLen);
            } else {
                glVertex3f(p.x, 0.5f, p.y);
            }
        }
        glEnd();
    }

    private void renderEffects() {
        for (Effect e : effects) {
            if (e.type == Effect.Type.LASER) { glLineWidth(2.0f); glColor4f(1, 1, 0, e.life); glBegin(GL_LINES); glVertex3f(e.x, 0.5f, e.y); glVertex3f(e.tx, 0.5f, e.ty); glEnd(); }
            else { float s = (1.0f - e.life) * 3.0f; glColor4f(1, 0.5f, 0, e.life); glPushMatrix(); glTranslatef(e.x, 0.5f, e.y); glBegin(GL_QUADS); glVertex3f(-s, s, 0); glVertex3f(s, s, 0); glVertex3f(s, -s, 0); glVertex3f(-s, -s, 0); glEnd(); glPopMatrix(); }
        }
    }

    private void renderBuildPreview() {
        if (selectedBuildType == null) return;
        double[] x = new double[1], y = new double[1]; glfwGetCursorPos(window, x, y);
        Vector3f pos = getMouseWorldPos(x[0], y[0]);
        float gx = (float) Math.floor(pos.x/GRID_SIZE)*GRID_SIZE + GRID_SIZE/2, gz = (float) Math.floor(pos.z/GRID_SIZE)*GRID_SIZE + GRID_SIZE/2;
        glPushMatrix(); glTranslatef(gx, 1.0f, gz); glColor4f(1, 1, 1, 0.5f);
        float s = (selectedBuildType == Building.Type.FACTORY) ? 1.5f : 0.5f;
        glBegin(GL_LINE_LOOP); glVertex3f(-s, 0, -s); glVertex3f(s, 0, -s); glVertex3f(s, 0, s); glVertex3f(-s, 0, s); glEnd(); glPopMatrix();
    }

    private void renderMoveMarkers() {
        for (MoveMarker m : moveMarkers) { glColor4f(1.0f, 1.0f, 0.0f, m.life); glPushMatrix(); glTranslatef(m.x, 0.1f, m.z); glBegin(GL_LINE_LOOP); float s = 1.0f * (2.0f - m.life); glVertex3f(-s, 0, -s); glVertex3f(s, 0, -s); glVertex3f(s, 0, s); glVertex3f(-s, 0, s); glEnd(); glPopMatrix(); }
    }

    private void renderSelectionBox() {
        if (!isSelecting) return;
        double[] x = new double[1], y = new double[1]; glfwGetCursorPos(window, x, y);
        glColor3f(0, 1, 0); glBegin(GL_LINE_LOOP); glVertex2d(selectionStartX, selectionStartY); glVertex2d(x[0], selectionStartY); glVertex2d(x[0], y[0]); glVertex2d(selectionStartX, y[0]); glEnd();
    }

    private void renderMinimap() {
        int[] w = new int[1], h = new int[1]; glfwGetWindowSize(window, w, h);
        float size = 150; float x = w[0] - size - 20; float y = 20;
        glColor4f(0, 0, 0, 0.7f); glBegin(GL_QUADS); glVertex2f(x, y); glVertex2f(x+size, y); glVertex2f(x+size, y+size); glVertex2f(x, y+size); glEnd();
        glLineWidth(1); glColor3f(0.5f, 0.5f, 0.5f); glBegin(GL_LINE_LOOP); glVertex2f(x, y); glVertex2f(x+size, y); glVertex2f(x+size, y+size); glVertex2f(x, y+size); glEnd();
        synchronized(gameState) {
            for (Building b : gameState.buildings.values()) {
                if (b.type == Building.Type.METAL_PATCH) glColor3f(1, 0.5f, 0); else if (b.teamId == myTeamId) glColor3f(0, 0.5f, 1); else glColor3f(1, 0, 0);
                float px = x + (b.position.x / 100.0f) * size; float py = y + (b.position.y / 100.0f) * size;
                glBegin(GL_QUADS); glVertex2f(px-2, py-2); glVertex2f(px+2, py-2); glVertex2f(px+2, py+2); glVertex2f(px-2, py+2); glEnd();
            }
            for (Unit u : gameState.units.values()) {
                if (u.teamId == myTeamId) glColor3f(0, 1, 0); else glColor3f(1, 0, 0);
                float px = x + (u.position.x / 100.0f) * size; float py = y + (u.position.y / 100.0f) * size;
                glPointSize(2); glBegin(GL_POINTS); glVertex2f(px, py); glEnd();
            }
        }
        glColor3f(1, 1, 1); glBegin(GL_LINE_LOOP);
        float vx = x + ((cameraPos.x - 20) / 100.0f) * size, vy = y + ((cameraPos.z - 20) / 100.0f) * size, vw = (40 / 100.0f) * size;
        glVertex2f(vx, vy); glVertex2f(vx+vw, vy); glVertex2f(vx+vw, vy+vw); glVertex2f(vx, vy+vw); glEnd();
    }

    private void renderHUD() {
        int[] w = new int[1], h = new int[1]; glfwGetWindowSize(window, w, h); float hY = h[0] - 80;
        glColor4f(0.1f, 0.1f, 0.1f, 0.9f); glBegin(GL_QUADS); glVertex2f(0, hY); glVertex2f(w[0], hY); glVertex2f(w[0], h[0]); glVertex2f(0, h[0]); glEnd();
        if (gameState.winnerTeamId != 0) { drawText(gameState.winnerTeamId == myTeamId ? "VICTORY!" : "DEFEAT", w[0]/2 - 100, h[0]/2, 5.0f); return; }
        if (!gameState.isStarted) { renderButton(20, hY + 10, 200, 60, "START GAME", true); drawText("WAITING FOR START...", 250, hY + 35, 1.5f); return; }
        float met = gameState.teamMetal.getOrDefault(myTeamId, 0f), inc = gameState.teamIncome.getOrDefault(myTeamId, 0f), drn = gameState.teamDrain.getOrDefault(myTeamId, 0f);
        if (met <= 0 && drn > inc) glColor3f(1, 0.2f, 0.2f); else glColor3f(1, 1, 1);
        drawText(String.format("METAL: %d (+%.1f / -%.1f)", (int)met, inc, drn), 20, 20, 1.5f);
        boolean cS = false; synchronized(gameState) { for (Integer id : selectedUnitIds) { Unit u = gameState.units.get(id); if (u!=null && u.type==Unit.Type.CONSTRUCTOR) { cS=true; break; } } }
        Building f = null; synchronized(gameState) { for (Integer id : selectedBuildingIds) { Building b = gameState.buildings.get(id); if (b!=null && b.type==Building.Type.FACTORY) { f=b; break; } } }
        if (cS) {
            renderButton(20, hY + 10, 120, 60, "FACTORY", selectedBuildType == Building.Type.FACTORY);
            renderButton(150, hY + 10, 120, 60, "WALL", selectedBuildType == Building.Type.WALL);
            renderButton(280, hY + 10, 120, 60, "EXTRACT", selectedBuildType == Building.Type.EXTRACTOR);
        } else if (f != null) {
            renderButton(20, hY + 10, 150, 60, "TANK (" + f.productionQueue.size() + ")", true);
            renderButton(180, hY + 10, 150, 60, "BOT", true);
        } else drawText("SELECT ALLIES", 20, hY + 35, 1.5f);
        drawText("WASD:Move Rotate:R-Drag Select:L-Drag Action:R-Click ESC:Menu", 450, hY + 35, 1.2f);
    }

    private void renderMenu() {
        int[] w = new int[1], h = new int[1]; glfwGetWindowSize(window, w, h);
        float cx = w[0]/2.0f, cy = h[0]/2.0f;
        glColor4f(0, 0, 0, 0.8f); glBegin(GL_QUADS); glVertex2f(cx-150, cy-150); glVertex2f(cx+150, cy-150); glVertex2f(cx+150, cy+200); glVertex2f(cx-150, cy+200); glEnd();
        drawText("PAUSE MENU", cx-80, cy-120, 2.0f); drawText("VOLUME:", cx-130, cy-40, 1.5f);
        glColor3f(0.3f, 0.3f, 0.3f); glBegin(GL_QUADS); glVertex2f(cx-100, cy); glVertex2f(cx+100, cy); glVertex2f(cx+100, cy+20); glVertex2f(cx-100, cy+20); glEnd();
        float vol = soundManager.getMasterVolume(); glColor3f(0.4f, 0.8f, 0.4f); glBegin(GL_QUADS); glVertex2f(cx-100, cy); glVertex2f(cx-100 + vol*200, cy); glVertex2f(cx-100 + vol*200, cy+20); glVertex2f(cx-100, cy+20); glEnd();
        renderButton(cx-100, cy+40, 200, 60, "RESUME", false); renderButton(cx-100, cy+110, 200, 60, "QUIT", false);
    }

    private void renderButton(float x, float y, float w, float h, String l, boolean a) {
        if (a) glColor3f(0.2f, 0.6f, 0.2f); else glColor3f(0.3f, 0.3f, 0.3f);
        glBegin(GL_QUADS); glVertex2f(x, y); glVertex2f(x+w, y); glVertex2f(x+w, y+h); glVertex2f(x, y+h); glEnd();
        glColor3f(0.8f, 0.8f, 0.8f); glLineWidth(2); glBegin(GL_LINE_LOOP); glVertex2f(x, y); glVertex2f(x+w, y); glVertex2f(x+w, y+h); glVertex2f(x, y+h); glEnd();
        drawText(l, x + 10, y + 35, 1.5f);
    }

    private void drawText(String t, float x, float y, float s) {
        ByteBuffer b = BufferUtils.createByteBuffer(t.length() * 270); int n = STBEasyFont.stb_easy_font_print(0, 0, t, null, b);
        glPushMatrix(); glTranslatef(x, y, 0); glScalef(s, s, 1); glDisable(GL_TEXTURE_2D); glEnableClientState(GL_VERTEX_ARRAY); glEnableClientState(GL_COLOR_ARRAY);
        glVertexPointer(2, GL_FLOAT, 16, b); b.position(12); glColorPointer(4, GL_UNSIGNED_BYTE, 16, b); glDrawArrays(GL_QUADS, 0, n * 4);
        glDisableClientState(GL_COLOR_ARRAY); glDisableClientState(GL_VERTEX_ARRAY); glPopMatrix();
    }

    private void renderProgressBar(float x, float y, float z, float w, float pr, float r, float g, float b) {
        glPushMatrix(); glTranslatef(x, y, z); float[] mv = new float[16]; glGetFloatv(GL_MODELVIEW_MATRIX, mv);
        mv[0]=1; mv[1]=0; mv[2]=0; mv[4]=0; mv[5]=1; mv[6]=0; mv[8]=0; mv[9]=0; mv[10]=1; glLoadMatrixf(mv);
        float h = 0.2f, hw = w / 2; glDisable(GL_DEPTH_TEST); glColor3f(0, 0, 0); glBegin(GL_QUADS); glVertex3f(-hw, 0, 0); glVertex3f(hw, 0, 0); glVertex3f(hw, h, 0); glVertex3f(-hw, h, 0); glEnd();
        glColor3f(r, g, b); float pw = -hw + (w * Math.max(0, Math.min(1, pr))); glBegin(GL_QUADS); glVertex3f(-hw, 0, 0.01f); glVertex3f(pw, 0, 0.01f); glVertex3f(pw, h, 0.01f); glVertex3f(-hw, h, 0.01f); glEnd(); glEnable(GL_DEPTH_TEST); glPopMatrix();
    }

    private void syncViewport() { Network.ViewportUpdate vp = new Network.ViewportUpdate(); vp.centerX = cameraPos.x; vp.centerY = cameraPos.z; vp.width = 150; vp.height = 150; client.sendUDP(vp); }
    public static void main(String[] args) { new ClientLauncher().run(); }
}
