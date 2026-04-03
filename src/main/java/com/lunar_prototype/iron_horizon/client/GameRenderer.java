package com.lunar_prototype.iron_horizon.client;

import com.lunar_prototype.iron_horizon.common.Network;
import com.lunar_prototype.iron_horizon.common.MapSettings;
import com.lunar_prototype.iron_horizon.common.util.TerrainGenerator;
import com.lunar_prototype.iron_horizon.common.model.Building;
import com.lunar_prototype.iron_horizon.common.model.GameState;
import com.lunar_prototype.iron_horizon.common.model.Unit;
import com.lunar_prototype.iron_horizon.client.render.Mesh;
import com.lunar_prototype.iron_horizon.client.render.MeshFactory;
import com.lunar_prototype.iron_horizon.client.render.FsrPreset;
import com.lunar_prototype.iron_horizon.client.render.FsrUpscaler;
import com.lunar_prototype.iron_horizon.client.render.ObjLoader;
import com.lunar_prototype.iron_horizon.client.render.TerrainMeshFactory;
import com.lunar_prototype.iron_horizon.client.render.UiIconFactory;
import com.lunar_prototype.iron_horizon.client.render.Texture;
import com.lunar_prototype.iron_horizon.client.render.FontRenderer;
import com.lunar_prototype.iron_horizon.client.util.DisplayMode;
import com.lunar_prototype.iron_horizon.client.ui.*;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.glGetString;
import static org.lwjgl.opengl.GL30.*;

public class GameRenderer {
    public static class MoveMarker {
        public float x, z, life = 1.0f;

        public MoveMarker(float x, float z) {
            this.x = x;
            this.z = z;
        }
    }

    public static class CombatMarker {
        public float x, z, life = 1.0f;

        public CombatMarker(float x, float z) {
            this.x = x;
            this.z = z;
        }
    }

    public static class Effect {
        public enum Type {
            LASER, EXPLOSION, BUILD_COMPLETE, OBELISK_BLAST, SHIELD_HIT
        }

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
    private final List<CombatMarker> combatMarkers;
    private final List<Effect> effects;
    private final List<Vector3f> pathPreviewPoints;
    private final List<Network.ProjectileData> projectileData;
    private final Map<Integer, Vector2f> localUnitTargets;
    private Mesh cubeMesh;
    private Mesh houndMesh;
    private Mesh obeliskMesh;
    private Mesh tankMesh;
    private Mesh terrainMesh;
    private Texture grassTexture;
    private Texture factoryIcon;
    private Texture wallIcon;
    private Texture extractorIcon;
    private Texture laserTowerIcon;
    private Texture tankIcon;
    private Texture houndIcon;
    private Texture constructorIcon;
    private Texture obeliskIcon;
    private Texture solarIcon;
    private Texture shieldIcon;
    private Mesh solarMesh;
    private Mesh shieldMesh;
    private final Map<Integer, Texture> houndTextures = new HashMap<>();
    private final Map<Integer, Texture> obeliskTextures = new HashMap<>();
    private final Map<Integer, Texture> tankTextures = new HashMap<>();
    private final Map<Integer, Float> unitFacingAngles = new HashMap<>();
    private static final float HOUND_MODEL_YAW_OFFSET = -90.0f;
    private static final float OBELISK_MODEL_YAW_OFFSET = -90.0f;
    private static final float TANK_MODEL_YAW_OFFSET = -90.0f;
    private final FsrUpscaler fsrUpscaler = new FsrUpscaler();
    private FontRenderer fontRenderer;
    private boolean corePrepared = false;
    private boolean assetsLoaded = false;

    private Vector3f cameraPos = new Vector3f(50, 60, 100);
    private float pitch = 60;
    private float yaw = -90;
    private double lastMouseX;
    private double lastMouseY;
    private float currentUiScale = 1.0f;
    private boolean rightMouseDown = false;
    private int windowWidth = 1280;
    private int windowHeight = 720;
    private int framebufferWidth = 1280;
    private int framebufferHeight = 720;
    private int currentRenderTeamId = 0;
    private int currentRenderPlayerId = 0;
    private boolean debugOverlayEnabled = false;
    private boolean fsrEnabled = true;
    private FsrPreset fsrPreset = FsrPreset.QUALITY;
    private float fsrSharpness = 0.2f;
    private String glVersion = "";
    private String glRenderer = "";
    private String glVendor = "";

    public GameRenderer(
            long window,
            GameState gameState,
            SoundManager soundManager,
            Set<Integer> selectedUnitIds,
            Set<Integer> selectedBuildingIds,
            List<MoveMarker> moveMarkers,
            List<CombatMarker> combatMarkers,
            List<Effect> effects,
            List<Vector3f> pathPreviewPoints,
            List<Network.ProjectileData> projectileData,
            Map<Integer, Vector2f> localUnitTargets) {
        this.window = window;
        this.gameState = gameState;
        this.soundManager = soundManager;
        this.selectedUnitIds = selectedUnitIds;
        this.selectedBuildingIds = selectedBuildingIds;
        this.moveMarkers = moveMarkers;
        this.combatMarkers = combatMarkers;
        this.effects = effects;
        this.pathPreviewPoints = pathPreviewPoints;
        this.projectileData = projectileData;
        this.localUnitTargets = localUnitTargets;
    }

    public void setPlayerContext(int teamId, int playerId) {
        this.currentRenderTeamId = teamId;
        this.currentRenderPlayerId = playerId;
    }

    public void init() {
        prepareCore();
        loadGameAssets();
    }

    public void setFsrSettings(boolean enabled, FsrPreset preset, float sharpness) {
        this.fsrEnabled = enabled;
        this.fsrPreset = preset != null ? preset : FsrPreset.QUALITY;
        this.fsrSharpness = Math.max(0.0f, Math.min(2.0f, sharpness));
        fsrUpscaler.setEnabled(enabled);
        fsrUpscaler.setPreset(this.fsrPreset);
        fsrUpscaler.setSharpness(this.fsrSharpness);
    }

    public void prepareCore() {
        if (corePrepared) {
            return;
        }
        GL.createCapabilities();
        glVersion = safeGlString(GL_VERSION);
        glRenderer = safeGlString(GL_RENDERER);
        glVendor = safeGlString(GL_VENDOR);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        fsrUpscaler.init();
        syncWindowMetrics();
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
        try {
            fontRenderer = new FontRenderer("/fonts/MPLUS1-Medium.ttf", 13.0f);
        } catch (Exception e) {
            System.err.println("Failed to load font: " + e.getMessage());
        }
        corePrepared = true;
    }

    public void loadGameAssets() {
        if (assetsLoaded) {
            return;
        }
        cubeMesh = MeshFactory.createUnitCube();
        try {
            houndMesh = ObjLoader.loadFromResource(GameRenderer.class, "/model/scout.obj");
        } catch (Exception e) {
            System.err.println("Failed to load Hound model: " + e.getMessage());
            houndMesh = null;
        }
        try {
            houndTextures.put(1, Texture.createTeamTintedTexture(GameRenderer.class, "/model/texture.png", 0.20f, 0.80f,
                    1.00f, true));
            houndTextures.put(2, Texture.createTeamTintedTexture(GameRenderer.class, "/model/texture.png", 1.00f, 0.22f,
                    0.22f, true));
        } catch (Exception e) {
            System.err.println("Failed to load Hound textures: " + e.getMessage());
        }
        try {
            obeliskMesh = ObjLoader.loadFromResource(GameRenderer.class, "/model/obelisk.obj");
        } catch (Exception e) {
            System.err.println("Failed to load Obelisk model: " + e.getMessage());
            obeliskMesh = null;
        }
        try {
            obeliskTextures.put(1, Texture.createTeamTintedTexture(GameRenderer.class, "/model/texture2.png", 0.20f,
                    0.80f, 1.00f, true));
            obeliskTextures.put(2, Texture.createTeamTintedTexture(GameRenderer.class, "/model/texture2.png", 1.00f,
                    0.22f, 0.22f, true));
        } catch (Exception e) {
            System.err.println("Failed to load Obelisk textures: " + e.getMessage());
        }
        try {
            tankMesh = ObjLoader.loadFromResource(GameRenderer.class, "/model/tank.obj");
        } catch (Exception e) {
            System.err.println("Failed to load Tank model: " + e.getMessage());
            tankMesh = null;
        }
        try {
            tankTextures.put(1, Texture.createTeamTintedTexture(GameRenderer.class, "/model/texture3.png", 0.20f, 0.80f,
                    1.00f, true));
            tankTextures.put(2, Texture.createTeamTintedTexture(GameRenderer.class, "/model/texture3.png", 1.00f, 0.22f,
                    0.22f, true));
        } catch (Exception e) {
            System.err.println("Failed to load Tank textures: " + e.getMessage());
        }
        terrainMesh = TerrainMeshFactory.createTerrain();
        grassTexture = Texture.createGrassTexture(MapSettings.TERRAIN_TEXTURE_SIZE, MapSettings.TERRAIN_TEXTURE_SIZE);
        factoryIcon = UiIconFactory.createBuildingIcon(Building.Type.FACTORY);
        wallIcon = UiIconFactory.createBuildingIcon(Building.Type.WALL);
        extractorIcon = UiIconFactory.createBuildingIcon(Building.Type.EXTRACTOR);
        laserTowerIcon = UiIconFactory.createBuildingIcon(Building.Type.LASER_TOWER);
        tankIcon = UiIconFactory.createUnitIcon(Unit.Type.TANK);
        houndIcon = UiIconFactory.createUnitIcon(Unit.Type.HOUND);
        constructorIcon = UiIconFactory.createUnitIcon(Unit.Type.CONSTRUCTOR);
        obeliskIcon = UiIconFactory.createUnitIcon(Unit.Type.OBELISK);
        solarIcon = UiIconFactory.createBuildingIcon(Building.Type.SOLAR_COLLECTOR);
        shieldIcon = UiIconFactory.createBuildingIcon(Building.Type.SHIELD_GENERATOR);
        try { solarMesh = ObjLoader.loadFromResource(GameRenderer.class, "/model/solar_collector.obj"); } catch (Exception e) {}
        try { shieldMesh = ObjLoader.loadFromResource(GameRenderer.class, "/model/shield.obj"); } catch (Exception e) {}
        assetsLoaded = true;
    }

    public void cleanup() {
        fsrUpscaler.cleanup();
        if (terrainMesh != null) {
            terrainMesh.close();
            terrainMesh = null;
        }
        if (cubeMesh != null) {
            cubeMesh.close();
            cubeMesh = null;
        }
        if (houndMesh != null) {
            houndMesh.close();
            houndMesh = null;
        }
        if (obeliskMesh != null) {
            obeliskMesh.close();
            obeliskMesh = null;
        }
        if (tankMesh != null) {
            tankMesh.close();
            tankMesh = null;
        }
        for (Texture texture : houndTextures.values()) {
            texture.close();
        }
        houndTextures.clear();
        for (Texture texture : obeliskTextures.values()) {
            texture.close();
        }
        obeliskTextures.clear();
        for (Texture texture : tankTextures.values()) {
            texture.close();
        }
        tankTextures.clear();
        if (grassTexture != null) {
            grassTexture.close();
            grassTexture = null;
        }
        if (factoryIcon != null) {
            factoryIcon.close();
            factoryIcon = null;
        }
        if (wallIcon != null) {
            wallIcon.close();
            wallIcon = null;
        }
        if (extractorIcon != null) {
            extractorIcon.close();
            extractorIcon = null;
        }
        if (laserTowerIcon != null) {
            laserTowerIcon.close();
            laserTowerIcon = null;
        }
        if (tankIcon != null) {
            tankIcon.close();
            tankIcon = null;
        }
        if (houndIcon != null) {
            houndIcon.close();
            houndIcon = null;
        }
        if (constructorIcon != null) {
            constructorIcon.close();
            constructorIcon = null;
        }
        if (fontRenderer != null) {
            fontRenderer.close();
            fontRenderer = null;
        }
        if (obeliskIcon != null) {
            obeliskIcon.close();
            obeliskIcon = null;
        }
        if (solarIcon != null) { solarIcon.close(); solarIcon = null; }
        if (shieldIcon != null) { shieldIcon.close(); shieldIcon = null; }
        if (solarMesh != null) { solarMesh.close(); solarMesh = null; }
        if (shieldMesh != null) { shieldMesh.close(); shieldMesh = null; }
        unitFacingAngles.clear();
    }

    public void handleKeyboardInput(float dt) {
        float speed = 50.0f * dt;
        float yawRad = (float) Math.toRadians(yaw + 90.0f);
        float forwardX = (float) -Math.sin(yawRad);
        float forwardZ = (float) -Math.cos(yawRad);
        float rightX = (float) Math.cos(yawRad);
        float rightZ = (float) -Math.sin(yawRad);
        float moveX = 0.0f;
        float moveZ = 0.0f;

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            moveX += forwardX;
            moveZ += forwardZ;
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            moveX -= forwardX;
            moveZ -= forwardZ;
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            moveX -= rightX;
            moveZ -= rightZ;
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            moveX += rightX;
            moveZ += rightZ;
        }
        float length = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (length > 0.0f) {
            moveX /= length;
            moveZ /= length;
            cameraPos.x += moveX * speed;
            cameraPos.z += moveZ * speed;
        }
        cameraPos.x = Math.max(0.0f, Math.min(MapSettings.WORLD_SIZE, cameraPos.x));
        cameraPos.z = Math.max(0.0f, Math.min(MapSettings.WORLD_SIZE, cameraPos.z));
    }

    public void onRightMouseButton(boolean down) {
        rightMouseDown = down;
    }

    public void onMouseMoved(double xpos, double ypos, boolean isMenuOpen, Building.Type selectedBuildType,
            boolean pathDrawing) {
        if (rightMouseDown && selectedBuildType == null && !isMenuOpen && !pathDrawing) {
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
        float fx = toFramebufferX(screenX);
        float fy = toFramebufferY(screenY);
        Matrix4f vp = buildViewProjection();
        Vector3f near = new Vector3f();
        Vector3f far = new Vector3f();
        int[] viewport = { 0, 0, framebufferWidth, framebufferHeight };
        vp.unproject(fx, (float) framebufferHeight - fy, 0, viewport, near);
        vp.unproject(fx, (float) framebufferHeight - fy, 1, viewport, far);
        float t = -near.y / (far.y - near.y);
        return new Vector3f(near).lerp(far, t);
    }

    public Vector3f projectWorldToScreen(float worldX, float worldY, float worldZ) {
        Matrix4f vp = buildViewProjection();
        Vector3f screen = new Vector3f(worldX, worldY, worldZ);
        int[] viewport = { 0, 0, framebufferWidth, framebufferHeight };
        vp.project(screen, viewport, screen);
        screen.x = toWindowX(screen.x);
        screen.y = windowHeight - toWindowY(screen.y);
        return screen;
    }

    public float getTerrainHeightAt(float x, float z) {
        return terrainHeightAt(x, z);
    }

    public Vector2f snapToBuildGrid(float worldX, float worldZ) {
        float half = GRID_SIZE * 0.5f;
        float snappedX = Math.round((worldX - half) / GRID_SIZE) * GRID_SIZE + half;
        float snappedZ = Math.round((worldZ - half) / GRID_SIZE) * GRID_SIZE + half;
        snappedX = clamp(snappedX, half, MapSettings.WORLD_SIZE - half);
        snappedZ = clamp(snappedZ, half, MapSettings.WORLD_SIZE - half);
        return new Vector2f(snappedX, snappedZ);
    }

    public Vector3f getCameraPosition() {
        return new Vector3f(cameraPos);
    }

    public void setDebugOverlayEnabled(boolean enabled) {
        this.debugOverlayEnabled = enabled;
    }

    public void renderFrame(
            int myTeamId,
            boolean isSelecting,
            double selectionStartX,
            double selectionStartY,
            Building.Type selectedBuildType,
            boolean isMenuOpen,
            float fps,
            float frameTimeMs) {
        float dt = (fps > 0) ? 1.0f / fps : 0.016f;
        currentRenderTeamId = myTeamId;
        if (fsrUpscaler.isEnabled()) {
            fsrUpscaler.beginSceneRender(framebufferWidth, framebufferHeight);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderWorldPass(myTeamId, selectedBuildType, dt);
            renderOverlayPass();
            fsrUpscaler.presentSceneToBackBuffer(framebufferWidth, framebufferHeight);
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, framebufferWidth, framebufferHeight);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderWorldPass(myTeamId, selectedBuildType, dt);
            renderOverlayPass();
        }
        renderUiPass(myTeamId, isSelecting, selectionStartX, selectionStartY, selectedBuildType, isMenuOpen);
        if (debugOverlayEnabled) {
            setup2D();
            renderDebugOverlay(fps, frameTimeMs);
        }
    }

    public void renderInitialSetupScreen(String ip, String username, int activeField, boolean connecting) {
        glDisable(GL_DEPTH_TEST);
        setup2D();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // 背景
        glColor4f(0.03f, 0.05f, 0.04f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(windowWidth, 0);
        glVertex2f(windowWidth, windowHeight);
        glVertex2f(0, windowHeight);
        glEnd();

        float pW = 500.0f;
        float pH = 450.0f;
        float px = (windowWidth - pW) * 0.5f;
        float py = (windowHeight - pH) * 0.5f;

        // パネル
        glColor4f(0.02f, 0.04f, 0.02f, 0.95f);
        glBegin(GL_QUADS);
        glVertex2f(px, py);
        glVertex2f(px + pW, py);
        glVertex2f(px + pW, py + pH);
        glVertex2f(px, py + pH);
        glEnd();

        glColor4f(0.2f, 0.8f, 0.35f, 0.9f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(px, py);
        glVertex2f(px + pW, py);
        glVertex2f(px + pW, py + pH);
        glVertex2f(px, py + pH);
        glEnd();

        drawText("DEPLOYMENT SETTINGS", px + 30, py + 50, 2.0f);

        // IP 入力欄
        drawText("SERVER ADDRESS", px + 30, py + 100, 1.2f);
        renderInputField(px + 30, py + 120, pW - 60, 40, ip, activeField == 0);

        // Username 入力欄
        drawText("COMMANDER NAME", px + 30, py + 190, 1.2f);
        renderInputField(px + 30, py + 210, pW - 60, 40, username, activeField == 1);

        drawText("FSR: " + (fsrEnabled ? fsrPreset.label() : "Off"), px + 30, py + 315, 1.05f);
        drawText(String.format("Sharpness: %.2f", fsrSharpness), px + 260, py + 315, 1.05f);

        if (connecting) {
            drawText("CONNECTING...", px + 30, py + 300, 1.5f);
        } else {
            // ボタンのヒント的な描画（判定は Launcher 側）
            renderButton(px + 30, py + 280, 210, 50, "CONNECT", 0.2f, 0.6f, 0.3f);
            renderButton(px + 260, py + 280, 210, 50, "LOCAL", 0.3f, 0.4f, 0.6f);
            renderButton(px + 30, py + 340, 210, 50, "GRAPHICS", 0.35f, 0.45f, 0.25f);
            drawText("Press ENTER to Connect / ESC to Quit", px + 30, py + 400, 1.0f);
        }
    }

    public void renderSettingsScreen(String title, DisplayMode displayMode, boolean fsrEnabled, FsrPreset preset,
            float sharpness, boolean inInitialSetup) {
        glDisable(GL_DEPTH_TEST);
        setup2D();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glColor4f(0.03f, 0.05f, 0.04f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(windowWidth, 0);
        glVertex2f(windowWidth, windowHeight);
        glVertex2f(0, windowHeight);
        glEnd();

        float pW = 620.0f;
        float pH = 500.0f;
        float px = (windowWidth - pW) * 0.5f;
        float py = (windowHeight - pH) * 0.5f;

        glColor4f(0.02f, 0.04f, 0.02f, 0.95f);
        glBegin(GL_QUADS);
        glVertex2f(px, py);
        glVertex2f(px + pW, py);
        glVertex2f(px + pW, py + pH);
        glVertex2f(px, py + pH);
        glEnd();

        glColor4f(0.2f, 0.8f, 0.35f, 0.9f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(px, py);
        glVertex2f(px + pW, py);
        glVertex2f(px + pW, py + pH);
        glVertex2f(px, py + pH);
        glEnd();

        drawText(title, px + 30, py + 50, 2.0f);
        drawText("DISPLAY / FSR OPTIONS", px + 30, py + 90, 1.2f);

        renderButton(px + 30, py + 120, 370, 48, "DISPLAY MODE: " + displayMode.label(), false);
        renderButton(px + 30, py + 180, 180, 48, fsrEnabled ? "FSR: ON" : "FSR: OFF", fsrEnabled);
        renderButton(px + 220, py + 180, 180, 48, "PRESET: " + preset.label(), false);

        drawText("SHARPNESS", px + 30, py + 262, 1.15f);
        renderButton(px + 30, py + 285, 60, 42, "-", false);
        glColor3f(0.15f, 0.2f, 0.15f);
        glBegin(GL_QUADS);
        glVertex2f(px + 100, py + 285);
        glVertex2f(px + 250, py + 285);
        glVertex2f(px + 250, py + 327);
        glVertex2f(px + 100, py + 327);
        glEnd();
        glColor3f(0.9f, 0.95f, 0.9f);
        drawText(String.format("%.2f", sharpness), px + 150, py + 309, 1.15f);
        renderButton(px + 260, py + 285, 60, 42, "+", false);

        float scale = fsrEnabled && preset != null ? preset.renderScale() : 1.0f;
        drawText(String.format("Internal Scale: %.0f%%", scale * 100.0f), px + 30, py + 362, 1.1f);
        drawText("UI stays native, scene is rendered low-res and upscaled.", px + 30, py + 390, 1.0f);
        drawText(inInitialSetup ? "ESC to return to deployment setup" : "ESC to close settings", px + 30, py + 418,
                1.0f);

        renderButton(px + pW - 180, py + pH - 68, 150, 48, "BACK", false);
    }

    private void renderInputField(float x, float y, float w, float h, String text, boolean active) {
        if (active)
            glColor4f(0.1f, 0.25f, 0.15f, 1.0f);
        else
            glColor4f(0.05f, 0.1f, 0.05f, 1.0f);

        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();

        if (active)
            glColor4f(0.4f, 1.0f, 0.6f, 1.0f);
        else
            glColor4f(0.2f, 0.5f, 0.3f, 1.0f);

        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();

        drawText(text + (active && (System.currentTimeMillis() / 500 % 2 == 0) ? "_" : ""), x + 10, y + 8, 1.5f);
    }

    private void renderButton(float x, float y, float w, float h, String label, float r, float g, float b) {
        glColor4f(r, g, b, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();

        glColor4f(1, 1, 1, 0.9f);
        float tw = label.length() * 12.0f; // 簡易的な幅計算
        drawText(label, x + (w - tw) / 2, y + 12, 1.5f);
    }

    public void renderLoadingScreen(String title, String detail, float progress) {
        glDisable(GL_DEPTH_TEST);
        setup2D();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glColor4f(0.03f, 0.05f, 0.04f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(windowWidth, 0);
        glVertex2f(windowWidth, windowHeight);
        glVertex2f(0, windowHeight);
        glEnd();
        float panelW = 520.0f;
        float panelH = 200.0f;
        float px = (windowWidth - panelW) * 0.5f;
        float py = (windowHeight - panelH) * 0.5f;
        glColor4f(0.02f, 0.04f, 0.02f, 0.95f);
        glBegin(GL_QUADS);
        glVertex2f(px, py);
        glVertex2f(px + panelW, py);
        glVertex2f(px + panelW, py + panelH);
        glVertex2f(px, py + panelH);
        glEnd();
        glColor4f(0.2f, 0.8f, 0.35f, 0.9f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(px, py);
        glVertex2f(px + panelW, py);
        glVertex2f(px + panelW, py + panelH);
        glVertex2f(px, py + panelH);
        glEnd();
        drawText(title, px + 24.0f, py + 42.0f, 2.0f);
        drawText(detail, px + 24.0f, py + 80.0f, 1.35f);
        float barX = px + 24.0f;
        float barY = py + 126.0f;
        float barW = panelW - 48.0f;
        float barH = 18.0f;
        glColor4f(0.08f, 0.12f, 0.08f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(barX, barY);
        glVertex2f(barX + barW, barY);
        glVertex2f(barX + barW, barY + barH);
        glVertex2f(barX, barY + barH);
        glEnd();
        glColor4f(0.2f, 0.8f, 0.35f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(barX, barY);
        glVertex2f(barX + barW * clamp(progress, 0.0f, 1.0f), barY);
        glVertex2f(barX + barW * clamp(progress, 0.0f, 1.0f), barY + barH);
        glVertex2f(barX, barY + barH);
        glEnd();
        glColor4f(1.0f, 1.0f, 1.0f, 0.75f);
        drawText(String.format("LOADING %.0f%%", clamp(progress, 0.0f, 1.0f) * 100.0f), barX, barY + 32.0f, 1.1f);
    }

    private void renderWorldPass(int myTeamId, Building.Type selectedBuildType, float dt) {
        setup3D();
        renderGround();
        synchronized (gameState) {
            renderBuildings(myTeamId);
            renderUnits(myTeamId, dt);
            renderProjectiles(myTeamId);
        }
        synchronized (moveMarkers) {
            renderMoveMarkers();
        }
        renderBuildPreview(selectedBuildType);
    }

    private void renderOverlayPass() {
        renderSelectionRanges(currentRenderTeamId);
        renderPathGuides();
        renderPlannedPathPreview();
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

    private void renderDebugOverlay(float fps, float frameTimeMs) {
        float x = 14.0f;
        float y = 14.0f;
        float width = 350.0f;
        float height = 180.0f;
        glColor4f(0.02f, 0.04f, 0.02f, 0.92f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        glColor4f(0.2f, 0.8f, 0.35f, 0.8f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        drawText("IRON HORIZON DEBUG", x + 12, y + 22, 1.45f);
        drawText(String.format("FPS: %.1f", fps), x + 12, y + 44, 1.35f);
        drawText(String.format("Frame: %.2f ms", frameTimeMs), x + 12, y + 62, 1.35f);
        drawText(String.format("Window: %d x %d", windowWidth, windowHeight), x + 12, y + 80, 1.2f);
        drawText(String.format("Framebuffer: %d x %d", framebufferWidth, framebufferHeight), x + 12, y + 98, 1.2f);
        drawText(String.format("Camera: %.1f, %.1f, %.1f", cameraPos.x, cameraPos.y, cameraPos.z), x + 12, y + 116,
                1.2f);
        drawText(String.format("Yaw/Pitch: %.1f / %.1f", yaw, pitch), x + 12, y + 134, 1.2f);
        drawText(shortenLine("GL: " + glVersion), x + 12, y + 152, 1.1f);
        drawText(shortenLine(glRenderer), x + 12, y + 168, 1.1f);
        drawText(shortenLine(glVendor), x + 12, y + 184, 1.1f);
    }

    private String shortenLine(String text) {
        if (text == null)
            return "";
        if (text.length() <= 42)
            return text;
        return text.substring(0, 39) + "...";
    }

    private String safeGlString(int which) {
        String value = glGetString(which);
        return value != null ? value : "unknown";
    }

    private void setup3D() {
        glEnable(GL_DEPTH_TEST);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(45.0f),
                (float) framebufferWidth / framebufferHeight, 0.1f, 1000.0f);
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

    private Matrix4f buildViewProjection() {
        return new Matrix4f()
                .perspective((float) Math.toRadians(45.0f), (float) framebufferWidth / framebufferHeight, 0.1f, 1000.0f)
                .rotate((float) Math.toRadians(pitch), 1, 0, 0)
                .rotate((float) Math.toRadians(yaw + 90), 0, 1, 0)
                .translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
    }

    private float toFramebufferX(double windowX) {
        if (windowWidth <= 0)
            return (float) windowX;
        return (float) (windowX * framebufferWidth / (double) windowWidth);
    }

    private float toFramebufferY(double windowY) {
        if (windowHeight <= 0)
            return (float) windowY;
        return (float) (windowY * framebufferHeight / (double) windowHeight);
    }

    private float toWindowX(float framebufferX) {
        if (framebufferWidth <= 0)
            return framebufferX;
        return framebufferX * windowWidth / (float) framebufferWidth;
    }

    private float toWindowY(float framebufferY) {
        if (framebufferHeight <= 0)
            return framebufferY;
        return framebufferY * windowHeight / (float) framebufferHeight;
    }

    private void setup2D() {
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, framebufferWidth, framebufferHeight, 0, -1, 1);
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
            float groundY = terrainHeightAt(b.position.x, b.position.y);
            glPushMatrix();
            glTranslatef(b.position.x, groundY + b.size / 2, b.position.y);
            if (b.type == Building.Type.METAL_PATCH) {
                glColor3f(1.0f, 0.5f, 0.0f);
            } else if (b.isComplete) {
                if (b.teamId == myTeamId)
                    glColor3f(0.2f, 0.5f, 1.0f);
                else
                    glColor3f(1.0f, 0.2f, 0.2f);
                if (b.type == Building.Type.NEXUS)
                    glColor3f(0.8f, 0.2f, 0.8f);
                else if (b.type == Building.Type.LASER_TOWER)
                    glColor3f(0.9f, 0.9f, 0.2f);
            } else {
                glColor4f(1, 1, 1, 0.3f + b.buildProgress * 0.7f);
            }
            float s = b.size / 2;
            if (b.type == Building.Type.SOLAR_COLLECTOR && solarMesh != null) {
                glPushMatrix();
                glTranslatef(0.0f, -s, 0.0f);
                glScalef(s*1.5f, s*1.5f, s*1.5f);
                solarMesh.draw();
                glPopMatrix();
            } else if (b.type == Building.Type.SHIELD_GENERATOR && shieldMesh != null) {
                glPushMatrix();
                glTranslatef(0.0f, -s, 0.0f);
                glScalef(s*1.5f, s*1.5f, s*1.5f);
                shieldMesh.draw();
                glPopMatrix();
            } else {
                renderCube(s);
            }
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
            if (b.type == Building.Type.SHIELD_GENERATOR && b.isComplete && b.shieldHp > 0) {
                float radius = b.shieldRadius;
                float baseAlpha = 0.15f + 0.25f * (b.shieldHp / b.maxShieldHp);
                glColor4f(0.2f, 0.6f, 1.0f, baseAlpha);
                glDisable(GL_DEPTH_TEST);
                glLineWidth(1.5f);
                for (int ri = 0; ri < 6; ri++) {
                    float yHeight = radius * (float)Math.sin(ri * Math.PI / 12.0);
                    float rDist = radius * (float)Math.cos(ri * Math.PI / 12.0);
                    glBegin(GL_LINE_LOOP);
                    for (int i = 0; i < 32; i++) {
                        float angle = (float) (Math.PI * 2.0 * i / 32.0);
                        glVertex3f((float) Math.cos(angle) * rDist, yHeight - s, (float) Math.sin(angle) * rDist);
                    }
                    glEnd();
                }
                glEnable(GL_DEPTH_TEST);
            }
            
            // Energy Grid Range Visualization
            if (b.teamId == myTeamId && b.isComplete && 
               (b.energyIncome > 0 || b.type == Building.Type.EXTRACTOR)) {
                float radius = 35.0f; // ServerLauncher.ENERGY_GRID_RADIUS
                glColor4f(0.0f, 0.8f, 0.9f, 0.15f);
                glDisable(GL_DEPTH_TEST);
                glLineWidth(2.0f);
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < 64; i++) {
                    float angle = (float) (Math.PI * 2.0 * i / 64.0);
                    glVertex3f((float) Math.cos(angle) * radius, -s + 0.1f, (float) Math.sin(angle) * radius);
                }
                glEnd();
                glEnable(GL_DEPTH_TEST);
            }
            glPopMatrix();

            if (b.type != Building.Type.METAL_PATCH) {
                if (!b.isComplete) {
                    renderProgressBar(b.position.x, groundY + b.size + 1.0f, b.position.y, 3.0f, b.buildProgress, 0.2f, 0.5f,
                            1.0f);
                } else {
                    renderProgressBar(b.position.x, groundY + b.size + 1.0f, b.position.y, 3.0f, b.hp / b.maxHp, 0.2f, 1.0f,
                            0.2f);
                    if (b.type == Building.Type.FACTORY && !b.productionQueue.isEmpty()) {
                        renderProgressBar(b.position.x, groundY + b.size + 1.5f, b.position.y, 3.0f, b.productionTimer, 1.0f,
                                0.8f, 0.0f);
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

    private void renderUnits(int myTeamId, float dt) {
        for (Unit u : gameState.units.values()) {
            float terrainY = terrainHeightAt(u.position.x, u.position.y);
            glPushMatrix();
            glTranslatef(u.position.x, terrainY + 0.5f, u.position.y);
            if (selectedUnitIds.contains(u.id))
                glColor3f(1.0f, 1.0f, 0.0f);
            else if (u.teamId == myTeamId) {
                if (u.type == Unit.Type.CONSTRUCTOR)
                    glColor3f(0.2f, 0.8f, 0.2f);
                else if (u.type == Unit.Type.HOUND)
                    glColor3f(0.95f, 0.75f, 0.2f);
                else if (u.type == Unit.Type.OBELISK)
                    glColor3f(0.95f, 0.4f, 1.0f);
                else
                    glColor3f(0.0f, 0.8f, 1.0f);
            } else
                glColor3f(1.0f, 0.2f, 0.2f);

            if (u.type == Unit.Type.HOUND) {
                renderHoundModel(u, dt);
            } else if (u.type == Unit.Type.OBELISK) {
                renderObeliskModel(u, dt);
            } else if (u.type == Unit.Type.TANK) {
                renderTankModel(u, dt);
            } else {
                float sz = (u.type == Unit.Type.CONSTRUCTOR) ? 0.3f : 0.5f;
                renderCube(sz);
            }
            glPopMatrix();
            renderProgressBar(u.position.x, terrainY + 1.5f, u.position.y, 1.0f, u.hp / u.maxHp, 0.2f, 1.0f, 0.2f);
        }
    }

    private void renderHoundModel(Unit unit, float dt) {
        if (houndMesh == null) {
            renderCube(0.45f);
            return;
        }
        Texture texture = houndTextures.get(unit.teamId);
        if (texture != null) {
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, texture.id());
        }
        float heading = getUnitFacingHeading(unit, HOUND_MODEL_YAW_OFFSET, dt);
        glPushMatrix();
        glTranslatef(0.0f, -0.35f, 0.0f);
        glRotatef(heading, 0.0f, 1.0f, 0.0f);
        glScalef(2.4f, 2.4f, 2.4f);
        glColor3f(1.0f, 1.0f, 1.0f);
        houndMesh.draw();
        glPopMatrix();
        if (texture != null) {
            glBindTexture(GL_TEXTURE_2D, 0);
            glDisable(GL_TEXTURE_2D);
        }
    }

    private void renderObeliskModel(Unit unit, float dt) {
        if (obeliskMesh == null) {
            renderCube(0.9f);
            return;
        }
        Texture texture = obeliskTextures.get(unit.teamId);
        if (texture != null) {
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, texture.id());
        }
        float heading = getUnitFacingHeading(unit, OBELISK_MODEL_YAW_OFFSET, dt);
        glPushMatrix();
        glTranslatef(0.0f, -0.10f, 0.0f);
        glRotatef(heading, 0.0f, 1.0f, 0.0f);
        glScalef(3.0f, 3.0f, 3.0f);
        glColor3f(1.0f, 1.0f, 1.0f);
        obeliskMesh.draw();
        glPopMatrix();
        if (texture != null) {
            glBindTexture(GL_TEXTURE_2D, 0);
            glDisable(GL_TEXTURE_2D);
        }
    }

    private void renderTankModel(Unit unit, float dt) {
        if (tankMesh == null) {
            renderCube(0.6f);
            renderTankBarrel(unit);
            return;
        }
        Texture texture = tankTextures.get(unit.teamId);
        if (texture != null) {
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, texture.id());
        }
        float heading = getUnitFacingHeading(unit, TANK_MODEL_YAW_OFFSET, dt);
        glPushMatrix();
        glTranslatef(0.0f, -0.35f, 0.0f);
        glRotatef(heading, 0.0f, 1.0f, 0.0f);
        glScalef(3.2f, 3.2f, 3.2f);
        glColor3f(1.0f, 1.0f, 1.0f);
        tankMesh.draw();
        glPopMatrix();
        if (texture != null) {
            glBindTexture(GL_TEXTURE_2D, 0);
            glDisable(GL_TEXTURE_2D);
        }
        renderTankBarrel(unit);
    }

    private void renderTankBarrel(Unit unit) {
        float bodyFacing = -unit.facingDeg;
        float turretFacing = -unit.turretFacingDeg;
        float offsetX = (float) Math.cos(Math.toRadians(bodyFacing)) * 0.8f;
        float offsetZ = (float) Math.sin(Math.toRadians(bodyFacing)) * 0.8f;
        float barrelLen = 1.5f;
        float barrelEndX = offsetX + (float) Math.cos(Math.toRadians(turretFacing)) * barrelLen;
        float barrelEndZ = offsetZ + (float) Math.sin(Math.toRadians(turretFacing)) * barrelLen;
        if (!unit.turretReady) {
            glColor3f(1.0f, 0.5f, 0.0f);
        } else {
            glColor3f(0.3f, 0.3f, 0.3f);
        }
        glLineWidth(3.0f);
        glBegin(GL_LINES);
        glVertex3f(offsetX, 0.5f, offsetZ);
        glVertex3f(barrelEndX, 0.5f, barrelEndZ);
        glEnd();
    }

    private void renderProjectiles(int myTeamId) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        glLineWidth(5.0f);
        glBegin(GL_LINES);
        synchronized (projectileData) {
            for (Network.ProjectileData p : projectileData) {
                if (p.teamId == myTeamId)
                    glColor4f(0.2f, 0.8f, 1.0f, 1.0f);
                else
                    glColor4f(1.0f, 0.2f, 0.2f, 1.0f);

                float groundY = terrainHeightAt(p.x, p.y) + 0.5f;
                glVertex3f(p.x, groundY, p.y);

                float trailLen = 3.0f;
                float vLen = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
                float tailX = p.x;
                float tailZ = p.y;
                if (vLen > 0) {
                    tailX = p.x - (p.vx / vLen) * trailLen;
                    tailZ = p.y - (p.vy / vLen) * trailLen;
                }
                glColor4f(1.0f, 0.45f, 0.2f, 1.0f);
                glVertex3f(tailX, groundY, tailZ);
            }
        }
        glEnd();
        glPointSize(8.0f);
        glBegin(GL_POINTS);
        synchronized (projectileData) {
            for (Network.ProjectileData p : projectileData) {
                float groundY = terrainHeightAt(p.x, p.y) + 0.5f;
                glColor4f(1.0f, 1.0f, 0.65f, 1.0f);
                glVertex3f(p.x, groundY + 0.18f, p.y);
            }
        }
        glEnd();
        glPointSize(1.0f);
        glLineWidth(1.0f);
        glEnable(GL_DEPTH_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void renderEffects() {
        glDisable(GL_DEPTH_TEST);
        for (Effect e : effects) {
            if (e.type == Effect.Type.LASER) {
                float startY = terrainHeightAt(e.x, e.y) + 0.5f; // 地形に合わせる
                float endY = terrainHeightAt(e.tx, e.ty) + 0.5f;
                glLineWidth(2.0f);
                glColor4f(1, 1, 0, e.life);
                glBegin(GL_LINES);
                glVertex3f(e.x, startY, e.y);
                glVertex3f(e.tx, endY, e.ty);
                glEnd();
            } else if (e.type == Effect.Type.BUILD_COMPLETE) {
                float radius = 1.5f + (1.0f - e.life) * 3.0f;
                float baseY = terrainHeightAt(e.x, e.y) + 0.15f;
                glPushMatrix();
                glTranslatef(e.x, baseY, e.y);
                glColor4f(0.4f, 1.0f, 0.55f, e.life);
                glLineWidth(2.0f);
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < 32; i++) {
                    float angle = (float) (Math.PI * 2.0 * i / 32.0);
                    glVertex3f((float) Math.cos(angle) * radius, 0, (float) Math.sin(angle) * radius);
                }
                glEnd();
                glBegin(GL_LINES);
                glVertex3f(-radius * 0.4f, 0, 0);
                glVertex3f(-radius, 0, 0);
                glVertex3f(radius * 0.4f, 0, 0);
                glVertex3f(radius, 0, 0);
                glVertex3f(0, 0, -radius * 0.4f);
                glVertex3f(0, 0, -radius);
                glVertex3f(0, 0, radius * 0.4f);
                glVertex3f(0, 0, radius);
                glEnd();
                glPopMatrix();
            } else if (e.type == Effect.Type.OBELISK_BLAST) {
                float radius = 1.0f + (1.0f - e.life) * 7.0f;
                float baseY = terrainHeightAt(e.tx, e.ty) + 0.2f;
                glPushMatrix();
                glTranslatef(e.tx, baseY, e.ty);
                glColor4f(1.0f, 0.35f, 0.95f, e.life);
                glLineWidth(4.0f);
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < 48; i++) {
                    float angle = (float) (Math.PI * 2.0 * i / 48.0);
                    glVertex3f((float) Math.cos(angle) * radius, 0, (float) Math.sin(angle) * radius);
                }
                glEnd();
                glBegin(GL_LINES);
                glVertex3f(0.0f, 0.0f, 0.0f);
                glVertex3f(0.0f, 2.5f + e.life * 2.0f, 0.0f);
                glVertex3f(-radius * 0.6f, 0.0f, 0.0f);
                glVertex3f(-radius * 1.7f, 0.4f, 0.0f);
                glVertex3f(radius * 0.6f, 0.0f, 0.0f);
                glVertex3f(radius * 1.7f, 0.4f, 0.0f);
                glVertex3f(0.0f, 0.0f, -radius * 0.6f);
                glVertex3f(0.0f, 0.4f, -radius * 1.7f);
                glVertex3f(0.0f, 0.0f, radius * 0.6f);
                glVertex3f(0.0f, 0.4f, radius * 1.7f);
                glEnd();
                glBegin(GL_LINES);
                for (int i = 0; i < 16; i++) {
                    float angle = (float) (Math.PI * 2.0 * i / 16.0);
                    float inner = radius * 0.25f;
                    float outer = radius * (1.2f + (i % 2) * 0.2f);
                    glVertex3f((float) Math.cos(angle) * inner, 0, (float) Math.sin(angle) * inner);
                    glVertex3f((float) Math.cos(angle) * outer, 0, (float) Math.sin(angle) * outer);
                }
                glEnd();
                glBegin(GL_QUADS);
                float spark = radius * 0.28f;
                glVertex3f(-spark, 0.0f, -spark);
                glVertex3f(spark, 0.0f, -spark);
                glVertex3f(spark, 0.0f, spark);
                glVertex3f(-spark, 0.0f, spark);
                glEnd();
                glPopMatrix();
                glPushMatrix();
                glTranslatef(e.x, terrainHeightAt(e.x, e.y) + 0.6f, e.y);
                glColor4f(1.0f, 0.8f, 1.0f, e.life * 0.9f);
                glBegin(GL_LINES);
                glVertex3f(0.0f, 0.0f, 0.0f);
                glVertex3f(e.tx - e.x, 0.0f, e.ty - e.y);
                glEnd();
                glPopMatrix();
            } else if (e.type == Effect.Type.SHIELD_HIT) {
                float radius = 1.0f + (1.0f - e.life) * 4.0f; // Expand rapidly
                float baseY = terrainHeightAt(e.x, e.y) + 0.5f;
                glPushMatrix();
                glTranslatef(e.x, baseY, e.y);
                glColor4f(0.2f, 0.8f, 1.0f, e.life * 0.85f);
                glLineWidth(3.5f);
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < 24; i++) {
                    float angle = (float) (Math.PI * 2.0 * i / 24.0);
                    glVertex3f((float) Math.cos(angle) * radius, 0.0f, (float) Math.sin(angle) * radius);
                }
                glEnd();
                glColor4f(0.8f, 0.9f, 1.0f, Math.max(0, e.life - 0.5f));
                glBegin(GL_POINTS);
                glVertex3f(0, 0, 0);
                glEnd();
                glPopMatrix();
            } else if (e.type == Effect.Type.EXPLOSION) {
                float s = (1.0f - e.life) * 4.0f; // Rapid expansion
                float groundY = terrainHeightAt(e.x, e.y) + 0.5f;
                glPushMatrix();
                glTranslatef(e.x, groundY, e.y);
                
                // Outer ring
                glColor4f(1.0f, 0.6f, 0.2f, e.life * 0.7f);
                glLineWidth(2.0f);
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < 32; i++) {
                    float angle = (float) (Math.PI * 2.0 * i / 32.0);
                    glVertex3f((float) Math.cos(angle) * s, 0, (float) Math.sin(angle) * s);
                }
                glEnd();

                // Bright core (Billboard style approximation)
                glColor4f(1.0f, 0.9f, 0.4f, e.life);
                float coreSize = s * 0.4f;
                glBegin(GL_QUADS);
                glVertex3f(-coreSize, coreSize, 0);
                glVertex3f(coreSize, coreSize, 0);
                glVertex3f(coreSize, -coreSize, 0);
                glVertex3f(-coreSize, -coreSize, 0);
                glEnd();
                glBegin(GL_QUADS);
                glVertex3f(0, coreSize, -coreSize);
                glVertex3f(0, coreSize, coreSize);
                glVertex3f(0, -coreSize, coreSize);
                glVertex3f(0, -coreSize, -coreSize);
                glEnd();

                // Spark lines
                glColor4f(1.0f, 0.45f, 0.1f, e.life * 0.9f);
                glLineWidth(1.5f);
                glBegin(GL_LINES);
                for (int i = 0; i < 8; i++) {
                    float angle = (float) (Math.PI * 2.0 * i / 8.0);
                    float inner = s * 0.2f;
                    float outer = s * 1.2f;
                    glVertex3f((float) Math.cos(angle) * inner, s * 0.3f, (float) Math.sin(angle) * inner);
                    glVertex3f((float) Math.cos(angle) * outer, s * 0.5f, (float) Math.sin(angle) * outer);
                }
                glEnd();
                glPopMatrix();
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
        glEnable(GL_DEPTH_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void renderBuildPreview(Building.Type selectedBuildType) {
        if (selectedBuildType == null)
            return;
        double[] x = new double[1];
        double[] y = new double[1];
        glfwGetCursorPos(window, x, y);
        Vector3f pos = getMouseWorldPos(x[0], y[0]);
        Vector2f snapped = snapToBuildGrid(pos.x, pos.z);
        float gx = snapped.x;
        float gz = snapped.y;
        glPushMatrix();
        glTranslatef(gx, terrainHeightAt(gx, gz) + 1.0f, gz);
        glColor4f(1, 1, 1, 0.5f);
        float s = (selectedBuildType == Building.Type.FACTORY) ? 1.5f
                : (selectedBuildType == Building.Type.LASER_TOWER ? 0.9f : 0.5f);
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

    private void renderPathGuides() {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);
        glLineWidth(2.0f);
        for (Unit u : gameState.units.values()) {
            if (!selectedUnitIds.contains(u.id))
                continue;

            // 建造タスク（キュー）がある場合のマルチポイント描画
            if (u.type == Unit.Type.CONSTRUCTOR && u.tasks != null && !u.tasks.isEmpty()) {
                synchronized (u.tasks) {
                    if (!u.tasks.isEmpty()) {
                        glColor4f(0.3f, 0.8f, 1.0f, 0.75f);
                        glBegin(GL_LINE_STRIP);
                        float curY = terrainHeightAt(u.position.x, u.position.y) + 0.2f;
                        glVertex3f(u.position.x, curY, u.position.y);
                        for (Network.Task task : u.tasks) {
                            float ty = terrainHeightAt(task.x, task.y) + 0.2f;
                            glVertex3f(task.x, ty, task.y);
                        }
                        glEnd();
                        for (Network.Task task : u.tasks) {
                            float ty = terrainHeightAt(task.x, task.y) + 0.2f;
                            renderPathArrow(task.x, ty, task.y);
                        }
                        continue;
                    }
                }
            }

            Vector2f target = localUnitTargets.get(u.id);
            if (target == null)
                continue;
            float targetDist = u.position.distance(target);
            if (targetDist < 0.75f)
                continue;
            float startY = terrainHeightAt(u.position.x, u.position.y) + 0.18f;
            float endY = terrainHeightAt(target.x, target.y) + 0.18f;
            glColor4f(0.25f, 0.95f, 0.45f, 0.65f);
            glBegin(GL_LINES);
            glVertex3f(u.position.x, startY, u.position.y);
            glVertex3f(target.x, endY, target.y);
            glEnd();
            renderPathArrow(target.x, endY, target.y);
        }
        glEnable(GL_DEPTH_TEST);
    }

    private void renderPlannedPathPreview() {
        synchronized (pathPreviewPoints) {
            if (pathPreviewPoints.size() < 2) {
                return;
            }
            glDisable(GL_TEXTURE_2D);
            glDisable(GL_DEPTH_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glLineWidth(3.0f);
            glColor4f(0.2f, 1.0f, 0.45f, 0.85f);
            glBegin(GL_LINE_STRIP);
            for (Vector3f point : pathPreviewPoints) {
                float y = terrainHeightAt(point.x, point.z) + 0.22f;
                glVertex3f(point.x, y, point.z);
            }
            glEnd();
            for (int i = 0; i < pathPreviewPoints.size(); i += Math.max(1, pathPreviewPoints.size() / 12)) {
                Vector3f point = pathPreviewPoints.get(i);
                float y = terrainHeightAt(point.x, point.z) + 0.24f;
                glPushMatrix();
                glTranslatef(point.x, y, point.z);
                glColor4f(0.7f, 1.0f, 0.9f, 0.95f);
                glBegin(GL_QUADS);
                float s = 0.18f;
                glVertex3f(-s, 0, -s);
                glVertex3f(s, 0, -s);
                glVertex3f(s, 0, s);
                glVertex3f(-s, 0, s);
                glEnd();
                glPopMatrix();
            }
            glLineWidth(1.0f);
            glEnable(GL_DEPTH_TEST);
        }
    }

    private Vector3f getUnitGuideTarget(Unit u) {
        if (u.targetUnitId != null) {
            Unit target = gameState.units.get(u.targetUnitId);
            if (target != null)
                return new Vector3f(target.position.x, 0, target.position.y);
        }
        if (u.attackTargetBuildingId != null) {
            Building target = gameState.buildings.get(u.attackTargetBuildingId);
            if (target != null)
                return new Vector3f(target.position.x, 0, target.position.y);
        }
        if (u.targetBuildingId != null) {
            Building target = gameState.buildings.get(u.targetBuildingId);
            if (target != null)
                return new Vector3f(target.position.x, 0, target.position.y);
        }
        if (u.targetPosition.distanceSquared(u.position) > 0.5f) {
            return new Vector3f(u.targetPosition.x, 0, u.targetPosition.y);
        }
        return null;
    }

    private void renderPathArrow(float x, float y, float z) {
        glPushMatrix();
        glTranslatef(x, y, z);
        glColor4f(0.25f, 0.95f, 0.45f, 0.9f);
        glBegin(GL_TRIANGLES);
        glVertex3f(0.0f, 0.0f, 0.0f);
        glVertex3f(-0.3f, 0.0f, -0.3f);
        glVertex3f(0.3f, 0.0f, -0.3f);
        glEnd();
        glPopMatrix();
    }

    private void renderSelectionRanges(int myTeamId) {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);
        glLineWidth(1.8f);
        for (Unit u : gameState.units.values()) {
            if (u.teamId != myTeamId)
                continue;
            if (!selectedUnitIds.contains(u.id) || u.attackRange <= 0.0f)
                continue;
            float y = terrainHeightAt(u.position.x, u.position.y) + 0.08f;
            renderRangeCircle(u.position.x, y, u.position.y, u.attackRange, 0.2f, 0.9f, 1.0f, 0.35f);
        }
        for (Building b : gameState.buildings.values()) {
            if (b.teamId != myTeamId)
                continue;
            if (!selectedBuildingIds.contains(b.id) || b.attackDamage <= 0.0f || b.attackRange <= 0.0f)
                continue;
            float y = terrainHeightAt(b.position.x, b.position.y) + 0.08f;
            renderRangeCircle(b.position.x, y, b.position.y, b.attackRange, 1.0f, 0.9f, 0.2f, 0.32f);
        }
        glEnable(GL_DEPTH_TEST);
    }

    private void renderRangeCircle(float x, float y, float z, float radius, float r, float g, float b, float a) {
        glPushMatrix();
        glTranslatef(x, y, z);
        glColor4f(r, g, b, a);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < 64; i++) {
            float angle = (float) (Math.PI * 2.0 * i / 64.0);
            glVertex3f((float) Math.cos(angle) * radius, 0.0f, (float) Math.sin(angle) * radius);
        }
        glEnd();
        glPopMatrix();
    }

    private void renderSelectionBox(boolean isSelecting, double selectionStartX, double selectionStartY) {
        if (!isSelecting)
            return;
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
        // ... (existing code for background and border)
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
                if (b.type == Building.Type.METAL_PATCH)
                    glColor3f(1, 0.5f, 0);
                else if (b.teamId == myTeamId)
                    glColor3f(0, 0.5f, 1);
                else
                    glColor3f(1, 0, 0);
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
                if (u.teamId == myTeamId)
                    glColor3f(0, 1, 0);
                else
                    glColor3f(1, 0, 0);
                float px = x + (u.position.x / MapSettings.WORLD_SIZE) * size;
                float py = y + (u.position.y / MapSettings.WORLD_SIZE) * size;
                glPointSize(2);
                glBegin(GL_POINTS);
                glVertex2f(px, py);
                glEnd();
            }
        }
        synchronized (combatMarkers) {
            for (CombatMarker marker : combatMarkers) {
                float px = x + (marker.x / MapSettings.WORLD_SIZE) * size;
                float py = y + (marker.z / MapSettings.WORLD_SIZE) * size;
                float alpha = Math.max(0.15f, marker.life);
                glColor4f(1.0f, 0.25f, 0.1f, alpha);
                glBegin(GL_TRIANGLES);
                glVertex2f(px, py - 4);
                glVertex2f(px - 3, py + 3);
                glVertex2f(px + 3, py + 3);
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

        renderPlayerList(x, y + size + 10);
    }

    private void renderPlayerList(float x, float y) {
        synchronized (gameState) {
            if (gameState.teamNames.isEmpty())
                return;
            float currentY = y;
            for (Map.Entry<Integer, String> entry : gameState.teamNames.entrySet()) {
                int teamId = entry.getKey();
                String name = entry.getValue();

                // チームカラーの設定
                if (teamId == currentRenderTeamId)
                    glColor3f(0.2f, 0.8f, 1.0f); // Blue
                else if (teamId == 2)
                    glColor3f(1.0f, 0.2f, 0.2f); // Red
                else
                    glColor3f(0.8f, 0.8f, 0.8f);

                drawText(name + " (Team " + teamId + ")", x, currentY + 15, 1.1f);
                currentY += 20;
            }
        }
    }

    private void renderHUD(int myTeamId, Building.Type selectedBuildType) {
        float hudY = windowHeight - 80;
        double[] cursorX = new double[1];
        double[] cursorY = new double[1];
        glfwGetCursorPos(window, cursorX, cursorY);
        float mouseX = (float) cursorX[0];
        float mouseY = (float) cursorY[0];
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
            drawText(gameState.winnerTeamId == myTeamId ? "VICTORY!" : "DEFEAT", windowWidth / 2 - 100,
                    windowHeight / 2, 5.0f);
            return;
        }
        if (!gameState.isStarted) {
            renderButton(20, hudY + 10, 200, 60, "START GAME", true);
            drawText("WAITING FOR START...", 250, hudY + 35, 1.5f);
            return;
        }
        float met = gameState.playerMetal.getOrDefault(currentRenderPlayerId, 0f);
        float inc = gameState.playerIncome.getOrDefault(currentRenderPlayerId, 0f);
        float drn = gameState.playerDrain.getOrDefault(currentRenderPlayerId, 0f);
        float ene = gameState.playerEnergy.getOrDefault(currentRenderPlayerId, 0f);
        float ecap = gameState.playerEnergyCapacity.getOrDefault(currentRenderPlayerId, 0f);
        float eInc = gameState.playerEnergyIncome.getOrDefault(currentRenderPlayerId, 0f);
        float eDrn = gameState.playerEnergyDrain.getOrDefault(currentRenderPlayerId, 0f);
        if (met <= 0 && drn > inc)
            glColor3f(1, 0.2f, 0.2f);
        else
            glColor3f(1, 1, 1);
        drawText(String.format("METAL: %d (+%.1f / -%.1f)", (int) met, inc, drn), 20, 20, 1.5f);
        if (ene <= 0 && eDrn > eInc)
            glColor3f(1, 0.5f, 0.1f);
        else
            glColor3f(0.5f, 0.8f, 1.0f);
        drawText(String.format("ENERGY: %d / %d (+%.1f / -%.1f)", (int) ene, (int) ecap, eInc, eDrn), 20, 45, 1.5f);
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
          List<String> tooltip = null;
          if (cS) {
              boolean hoverFactory = isInsideRect(mouseX, mouseY, 20, hudY + 10, 120, 60);
              boolean hoverWall = isInsideRect(mouseX, mouseY, 150, hudY + 10, 120, 60);
              boolean hoverExtractor = isInsideRect(mouseX, mouseY, 280, hudY + 10, 120, 60);
              boolean hoverLaser = isInsideRect(mouseX, mouseY, 410, hudY + 10, 120, 60);
              boolean hoverSolar = isInsideRect(mouseX, mouseY, 540, hudY + 10, 120, 60);
              boolean hoverShield = isInsideRect(mouseX, mouseY, 670, hudY + 10, 120, 60);
              
              renderActionCardElement(20, hudY + 10, 120, 60, "FACTORY", getBuildingCost(Building.Type.FACTORY), factoryIcon,
                      selectedBuildType == Building.Type.FACTORY, hoverFactory, null);
              renderActionCardElement(150, hudY + 10, 120, 60, "WALL", getBuildingCost(Building.Type.WALL), wallIcon,
                      selectedBuildType == Building.Type.WALL, hoverWall, null);
              renderActionCardElement(280, hudY + 10, 120, 60, "EXTRACT", getBuildingCost(Building.Type.EXTRACTOR),
                      extractorIcon, selectedBuildType == Building.Type.EXTRACTOR, hoverExtractor, null);
              renderActionCardElement(410, hudY + 10, 120, 60, "LASER", getBuildingCost(Building.Type.LASER_TOWER),
                      laserTowerIcon, selectedBuildType == Building.Type.LASER_TOWER, hoverLaser, null);
              renderActionCardElement(540, hudY + 10, 120, 60, "SOLAR", getBuildingCost(Building.Type.SOLAR_COLLECTOR),
                      solarIcon, selectedBuildType == Building.Type.SOLAR_COLLECTOR, hoverSolar, null);
              renderActionCardElement(670, hudY + 10, 120, 60, "SHIELD", getBuildingCost(Building.Type.SHIELD_GENERATOR),
                      shieldIcon, selectedBuildType == Building.Type.SHIELD_GENERATOR, hoverShield, null);
              
              if (hoverFactory)
                  tooltip = describeBuildingType(Building.Type.FACTORY);
              else if (hoverWall)
                  tooltip = describeBuildingType(Building.Type.WALL);
              else if (hoverExtractor)
                  tooltip = describeBuildingType(Building.Type.EXTRACTOR);
              else if (hoverLaser)
                  tooltip = describeBuildingType(Building.Type.LASER_TOWER);
              else if (hoverSolar)
                  tooltip = describeBuildingType(Building.Type.SOLAR_COLLECTOR);
              else if (hoverShield)
                  tooltip = describeBuildingType(Building.Type.SHIELD_GENERATOR);
          } else if (factory != null) {
              boolean hoverTank = isInsideRect(mouseX, mouseY, 20, hudY + 10, 150, 60);
              boolean hoverHound = isInsideRect(mouseX, mouseY, 180, hudY + 10, 150, 60);
              boolean hoverBot = isInsideRect(mouseX, mouseY, 340, hudY + 10, 150, 60);
              boolean hoverObelisk = isInsideRect(mouseX, mouseY, 500, hudY + 10, 150, 60);
              renderActionCardElement(20, hudY + 10, 150, 60, "TANK", getUnitCost(Unit.Type.TANK), tankIcon, true, hoverTank,
                      factory.productionQueue.isEmpty() ? null : "Q:" + factory.productionQueue.size());
              renderActionCardElement(180, hudY + 10, 150, 60, "HOUND", getUnitCost(Unit.Type.HOUND), houndIcon, true,
                      hoverHound, null);
              renderActionCardElement(340, hudY + 10, 150, 60, "CONSTRUCTOR", getUnitCost(Unit.Type.CONSTRUCTOR),
                      constructorIcon, true, hoverBot, null);
              renderActionCardElement(500, hudY + 10, 150, 60, "OBELISK", getUnitCost(Unit.Type.OBELISK), obeliskIcon, true,
                      hoverObelisk, null);
              if (hoverTank)
                  tooltip = describeUnitType(Unit.Type.TANK);
            else if (hoverHound)
                tooltip = describeUnitType(Unit.Type.HOUND);
            else if (hoverBot)
                tooltip = describeUnitType(Unit.Type.CONSTRUCTOR);
            else if (hoverObelisk)
                tooltip = describeUnitType(Unit.Type.OBELISK);
        } else {
            drawText("SELECT ALLIES", 20, hudY + 35, 1.5f);
        }
        drawText("WASD:Move Rotate:R-Drag Select:L-Drag Action:R-Click ESC:Menu", 450, hudY + 35, 1.2f);

        if (tooltip == null && mouseY < hudY) {
            tooltip = getHoveredWorldTooltip(mouseX, mouseY, myTeamId);
        }
        if (tooltip != null && !tooltip.isEmpty()) {
            renderTooltipPanel(mouseX + 18.0f, Math.max(20.0f, mouseY - 10.0f), tooltip);
        }
    }

    private void renderActionCard(float x, float y, float width, float height, String label, int cost, Texture icon,
            boolean active, boolean hovered, String badgeText) {
        if (System.nanoTime() >= 0) {
        glColor4f(0, 0, 0, 0.40f);
        glBegin(GL_QUADS);
        glVertex2f(x + 4, y + 5);
        glVertex2f(x + width + 4, y + 5);
        glVertex2f(x + width + 4, y + height + 5);
        glVertex2f(x + 4, y + height + 5);
        glEnd();

        if (active) {
            glColor3f(0.16f, 0.58f, 0.26f);
        } else if (hovered) {
            glColor3f(0.22f, 0.28f, 0.22f);
        } else {
            glColor3f(0.14f, 0.18f, 0.14f);
        }
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        glColor4f(0.34f, 0.85f, 0.45f, 0.90f);
        glLineWidth(2);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        if (icon != null) {
            float iconSize = height - 16.0f;
            drawTexture(icon, x + 6.0f, y + 8.0f, iconSize, iconSize);
        }

        glColor4f(0.06f, 0.08f, 0.06f, 0.85f);
        glBegin(GL_QUADS);
        glVertex2f(x + 6.0f, y + height - 20.0f);
        glVertex2f(x + width - 6.0f, y + height - 20.0f);
        glVertex2f(x + width - 6.0f, y + height - 4.0f);
        glVertex2f(x + 6.0f, y + height - 4.0f);
        glEnd();
        glColor3f(0.93f, 0.98f, 0.93f);
        drawText(label, x + 56.0f, y + 32.0f, label.length() > 8 ? 0.92f : 1.05f);

        glColor4f(0.06f, 0.08f, 0.06f, 0.85f);
        glBegin(GL_QUADS);
        glVertex2f(x + width - 50.0f, y + 8.0f);
        glVertex2f(x + width - 8.0f, y + 8.0f);
        glVertex2f(x + width - 8.0f, y + 24.0f);
        glVertex2f(x + width - 50.0f, y + 24.0f);
        glEnd();
        glColor3f(0.90f, 0.95f, 0.90f);
        drawText(String.valueOf(cost), x + width - 40.0f, y + 20.0f, 0.95f);

        if (badgeText != null && !badgeText.isEmpty()) {
            glColor4f(0.08f, 0.12f, 0.08f, 0.90f);
            glBegin(GL_QUADS);
            glVertex2f(x + width - 50.0f, y + height - 18.0f);
            glVertex2f(x + width - 8.0f, y + height - 18.0f);
            glVertex2f(x + width - 8.0f, y + height - 3.0f);
            glVertex2f(x + width - 50.0f, y + height - 3.0f);
            glEnd();
            glColor3f(0.8f, 0.95f, 0.82f);
            drawText(badgeText, x + width - 43.0f, y + height - 5.0f, 0.80f);
        }
        return;
        }

        UiBox root = new UiBox();
        root.setUiScale(currentUiScale);
        root.x = x;
        root.y = y;
        root.width = width;
        root.height = height;
        root.widthMode = UiElement.LayoutMode.FIXED;
        root.heightMode = UiElement.LayoutMode.FIXED;
        root.setPadding(6);
        root.drawBorder = true;

        if (active) {
            root.bgR = 0.16f; root.bgG = 0.58f; root.bgB = 0.26f;
        } else if (hovered) {
            root.bgR = 0.22f; root.bgG = 0.28f; root.bgB = 0.22f;
        } else {
            root.bgR = 0.14f; root.bgG = 0.18f; root.bgB = 0.14f;
        }
        root.bgA = 0.92f;
        root.borderR = 0.34f; root.borderG = 0.85f; root.borderB = 0.45f;
        root.borderA = 0.90f;

        // メインの内容（アイコンとラベル）を横に並べるスタック
        UiStack mainStack = new UiStack(UiStack.Orientation.HORIZONTAL);
        mainStack.spacing = 10;
        mainStack.horizontalAlign = UiElement.HorizontalAlign.CENTER; // 追加
        mainStack.verticalAlign = UiElement.VerticalAlign.MIDDLE;
        root.addChild(mainStack);

        // アイコン
        if (icon != null) {
            float iconSize = height - 16.0f;
            mainStack.addChild(new UiElement() {
                @Override public void render() { drawTexture(icon, this.x, this.y, this.width, this.height); }
                @Override public void updateLayout() {}
                @Override public float getPreferredWidth() { return iconSize * uiScale; }
                @Override public float getPreferredHeight() { return iconSize * uiScale; }
            });
        }

        // ラベル
        UiLabel uiLabel = new UiLabel(label, fontRenderer);
        uiLabel.scale = (label.length() > 8 ? 0.9f : 1.1f);
        uiLabel.setColor(0.93f, 0.98f, 0.93f, 1.0f);
        uiLabel.verticalAlign = UiElement.VerticalAlign.MIDDLE; // 追加
        mainStack.addChild(uiLabel);

        // コストバッジを右上に配置
        UiBox costPanel = new UiBox();
        costPanel.bgR = 0.06f; costPanel.bgG = 0.08f; costPanel.bgB = 0.06f; costPanel.bgA = 0.8f;
        costPanel.drawBorder = false;
        costPanel.setPadding(3);
        costPanel.horizontalAlign = UiElement.HorizontalAlign.RIGHT;
        costPanel.verticalAlign = UiElement.VerticalAlign.TOP;
        costPanel.marginRight = 4;
        costPanel.marginTop = 4;
        
        UiLabel costLabel = new UiLabel(String.valueOf(cost), fontRenderer);
        costLabel.scale = 0.85f;
        costLabel.setColor(0.9f, 0.95f, 0.9f, 1.0f);
        costPanel.addChild(costLabel);
        root.addChild(costPanel);

        // キューバッジを右下に配置
        if (badgeText != null && !badgeText.isEmpty()) {
            UiBox badgePanel = new UiBox();
            badgePanel.bgR = 0.08f; badgePanel.bgG = 0.12f; badgePanel.bgB = 0.08f; badgePanel.bgA = 0.9f;
            badgePanel.drawBorder = false;
            badgePanel.setPadding(3);
            badgePanel.horizontalAlign = UiElement.HorizontalAlign.RIGHT;
            badgePanel.verticalAlign = UiElement.VerticalAlign.BOTTOM;
            badgePanel.marginRight = 4;
            badgePanel.marginBottom = 4;

            UiLabel badgeLabel = new UiLabel(badgeText, fontRenderer);
            badgeLabel.scale = 0.75f;
            badgeLabel.setColor(0.8f, 0.95f, 0.82f, 1.0f);
            badgePanel.addChild(badgeLabel);
            root.addChild(badgePanel);
        }

        root.updateLayout();
        root.render();
        if (icon != null) {
            float iconSize = height - 16.0f;
            drawTexture(icon, x + 6.0f, y + 8.0f, iconSize, iconSize);
        }
        drawText(String.valueOf(cost), x + width - 44, y + 21, 1.05f);
        if (badgeText != null && !badgeText.isEmpty()) {
            drawText(badgeText, x + width - 44, y + height - 5, 0.85f);
        }
    }

    private void renderActionCardElement(float x, float y, float width, float height, String label, int cost, Texture icon,
            boolean active, boolean hovered, String badgeText) {
        UiActionCard card = new UiActionCard(fontRenderer, icon, label, cost, badgeText);
        card.x = x;
        card.y = y;
        card.width = width;
        card.height = height;
        card.active = active;
        card.hovered = hovered;
        card.setUiScale(currentUiScale);
        card.render();
    }

    private void drawTexture(Texture texture, float x, float y, float width, float height) {
        if (texture == null) return;
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, texture.id());
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(x, y);
        glTexCoord2f(1, 0); glVertex2f(x + width, y);
        glTexCoord2f(1, 1); glVertex2f(x + width, y + height);
        glTexCoord2f(0, 1); glVertex2f(x, y + height);
        glEnd();
        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_TEXTURE_2D);
    }

    private UiBox pauseMenuBox;
    private UiLabel pauseMenuTitle;
    private UiButton resumeButton;
    private UiButton settingsButton;
    private UiButton quitButton;

    public UiButton getPauseMenuSettingsButton() { return settingsButton; }
    public UiButton getPauseMenuResumeButton() { return resumeButton; }
    public UiButton getPauseMenuQuitButton() { return quitButton; }
    public UiBox getPauseMenuBox() { return pauseMenuBox; }

    private void renderMenu() {
        setup2D();

        if (pauseMenuBox == null) {
            initPauseMenu();
        }

        double[] cursorX = new double[1];
        double[] cursorY = new double[1];
        glfwGetCursorPos(window, cursorX, cursorY);
        float mouseX = (float) cursorX[0];
        float mouseY = (float) cursorY[0];

        float scale = currentUiScale;

        int[] w = new int[1], h = new int[1];
        glfwGetWindowSize(window, w, h);
        float cx = w[0] / 2.0f;
        float cy = h[0] / 2.0f;

        pauseMenuBox.setUiScale(scale);
        pauseMenuBox.x = cx - pauseMenuBox.getPreferredWidth() / 2.0f;
        pauseMenuBox.y = cy - pauseMenuBox.getPreferredHeight() / 2.0f;
        pauseMenuBox.updateLayout();

        settingsButton.hovered = settingsButton.isMouseOver(mouseX, mouseY);
        resumeButton.hovered = resumeButton.isMouseOver(mouseX, mouseY);
        quitButton.hovered = quitButton.isMouseOver(mouseX, mouseY);

        pauseMenuBox.render();
    }

    private void initPauseMenu() {
        pauseMenuBox = new UiBox();
        pauseMenuBox.bgR = 0.02f;
        pauseMenuBox.bgG = 0.04f;
        pauseMenuBox.bgB = 0.02f;
        pauseMenuBox.bgA = 0.95f;
        pauseMenuBox.borderR = 0.2f;
        pauseMenuBox.borderG = 0.8f;
        pauseMenuBox.borderB = 0.35f;
        pauseMenuBox.borderA = 0.9f;
        pauseMenuBox.paddingTop = 20.0f;
        pauseMenuBox.paddingBottom = 20.0f;
        pauseMenuBox.paddingLeft = 40.0f;
        pauseMenuBox.paddingRight = 40.0f;

        pauseMenuTitle = new UiLabel("PAUSE MENU", fontRenderer);
        pauseMenuTitle.scale = 1.3f;
        pauseMenuTitle.r = 0.34f;
        pauseMenuTitle.g = 0.85f;
        pauseMenuTitle.b = 0.45f;
        pauseMenuTitle.marginTop = 10.0f;
        pauseMenuTitle.marginBottom = 30.0f;
        pauseMenuTitle.horizontalAlign = UiElement.HorizontalAlign.CENTER;
        pauseMenuBox.addChild(pauseMenuTitle);

        settingsButton = new UiButton(fontRenderer, "Settings");
        settingsButton.marginTop = 15.0f;
        settingsButton.width = 240.0f;
        settingsButton.height = 50.0f;
        settingsButton.widthMode = UiElement.LayoutMode.FIXED;
        settingsButton.heightMode = UiElement.LayoutMode.FIXED;

        resumeButton = new UiButton(fontRenderer, "Resume");
        resumeButton.marginTop = 15.0f;
        resumeButton.width = 240.0f;
        resumeButton.height = 50.0f;
        resumeButton.widthMode = UiElement.LayoutMode.FIXED;
        resumeButton.heightMode = UiElement.LayoutMode.FIXED;

        quitButton = new UiButton(fontRenderer, "Quit");
        quitButton.marginTop = 15.0f;
        quitButton.width = 240.0f;
        quitButton.height = 50.0f;
        quitButton.widthMode = UiElement.LayoutMode.FIXED;
        quitButton.heightMode = UiElement.LayoutMode.FIXED;

        UiStack buttonStack = new UiStack(UiStack.Orientation.VERTICAL);
        buttonStack.spacing = 15.0f * currentUiScale;
        buttonStack.horizontalAlign = UiElement.HorizontalAlign.CENTER;
        buttonStack.addChild(settingsButton);
        buttonStack.addChild(resumeButton);
        buttonStack.addChild(quitButton);
        pauseMenuBox.addChild(buttonStack);
    }

    private float terrainHeightAt(float x, float z) {
        return TerrainGenerator.heightAt(clamp(x, 0.0f, MapSettings.WORLD_SIZE),
                clamp(z, 0.0f, MapSettings.WORLD_SIZE));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public void syncWindowMetrics() {
        int[] windowWidthOut = new int[1];
        int[] windowHeightOut = new int[1];
        int[] framebufferWidthOut = new int[1];
        int[] framebufferHeightOut = new int[1];
        glfwGetWindowSize(window, windowWidthOut, windowHeightOut);
        glfwGetFramebufferSize(window, framebufferWidthOut, framebufferHeightOut);
        windowWidth = Math.max(1, windowWidthOut[0]);
        windowHeight = Math.max(1, windowHeightOut[0]);
        framebufferHeight = Math.max(1, framebufferHeightOut[0]);

        // 1280x720 を基準解像度として UI スケールを算出
        float scaleW = windowWidth / 1280.0f;
        float scaleH = windowHeight / 720.0f;
        currentUiScale = Math.min(scaleW, scaleH);
    }

    public float getCurrentUiScale() {
        return currentUiScale;
    }

    private void renderButton(float x, float y, float width, float height, String label, boolean active) {
        renderButton(x, y, width, height, label, active, false);
    }

    private void renderButton(float x, float y, float width, float height, String label, boolean active,
            boolean hovered) {
        glColor4f(0, 0, 0, 0.35f);
        glBegin(GL_QUADS);
        glVertex2f(x + 3, y + 4);
        glVertex2f(x + width + 3, y + 4);
        glVertex2f(x + width + 3, y + height + 4);
        glVertex2f(x + 3, y + height + 4);
        glEnd();
        if (active)
            glColor3f(0.16f, 0.58f, 0.26f);
        else if (hovered)
            glColor3f(0.22f, 0.28f, 0.22f);
        else
            glColor3f(0.18f, 0.2f, 0.18f);
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
        float textScale = 1.45f;
        float textWidth = label.length() * 12.0f * textScale;
        float textX = x + Math.max(0.0f, (width - textWidth) / 2.0f);
        float textY = y + Math.max(0.0f, (height - 16.0f * textScale) / 2.0f) + 1.0f;
        drawText(label, textX, textY, textScale);
    }

    private boolean isInsideRect(float x, float y, float rectX, float rectY, float rectW, float rectH) {
        return x >= rectX && x <= rectX + rectW && y >= rectY && y <= rectY + rectH;
    }

    private List<String> describeUnitType(Unit.Type type) {
        List<String> lines = new ArrayList<>();
        if (type == Unit.Type.TANK) {
            lines.add("TANK");
            lines.add("HP: 240");
            lines.add("DMG: 18");
            lines.add("RANGE: 27");
            lines.add("SPD: 9");
            lines.add("ROLE: Balanced front-line unit");
        } else if (type == Unit.Type.HOUND) {
            lines.add("HOUND");
            lines.add("HP: 90");
            lines.add("DMG: 10");
            lines.add("RANGE: 24");
            lines.add("SPD: 18");
            lines.add("ROLE: Fast scout vehicle");
        } else if (type == Unit.Type.OBELISK) {
            lines.add("OBELISK");
            lines.add("HP: 1150");
            lines.add("DMG: 320");
            lines.add("RANGE: 34");
            lines.add("SPD: 4.5");
            lines.add("ROLE: Heavy siege artillery");
        } else {
            lines.add("CONSTRUCTOR");
            lines.add("HP: 150");
            lines.add("DMG: 0");
            lines.add("RANGE: 0");
            lines.add("SPD: 12");
            lines.add("ROLE: Builds structures");
        }
        return lines;
    }

    private int getUnitCost(Unit.Type type) {
        return switch (type) {
            case TANK -> 200;
            case HOUND -> 120;
            case OBELISK -> 400;
            case CONSTRUCTOR -> 150;
        };
    }

    private float getUnitFacingHeading(Unit unit, float yawOffsetDegrees, float dt) {
        float target = -unit.facingDeg + yawOffsetDegrees;
        float current = unitFacingAngles.getOrDefault(unit.id, target);
        float smoothed = lerpAngle(current, target, dt * 8.0f); // 旋回速度を 8.0f に設定
        unitFacingAngles.put(unit.id, smoothed);
        return smoothed;
    }

    private float lerpAngle(float start, float end, float t) {
        float diff = ((end - start + 180) % 360 + 360) % 360 - 180;
        return start + diff * Math.min(1.0f, t);
    }

    private List<String> describeBuildingType(Building.Type type) {
        List<String> lines = new ArrayList<>();
        switch (type) {
            case FACTORY -> {
                lines.add("FACTORY");
                lines.add("HP: 1000");
                lines.add("PROD: Tank / Bot");
                lines.add("ROLE: Unit production");
            }
            case WALL -> {
                lines.add("WALL");
                lines.add("HP: 500");
                lines.add("ROLE: Basic barrier");
            }
            case EXTRACTOR -> {
                lines.add("EXTRACTOR");
                lines.add("HP: 800");
                lines.add("ROLE: Resource income");
            }
            case LASER_TOWER -> {
                lines.add("LASER TOWER");
                lines.add("HP: 650");
                lines.add("DMG: 18 (Uses Energy)");
                lines.add("RANGE: 28");
                lines.add("CD: 0.8s");
                lines.add("ROLE: Defensive weapon");
            }
            case SOLAR_COLLECTOR -> {
                lines.add("SOLAR COLLECTOR");
                lines.add("HP: 400");
                lines.add("ROLE: Energy Generation");
            }
            case SHIELD_GENERATOR -> {
                lines.add("SHIELD GENERATOR");
                lines.add("HP: 700");
                lines.add("SHIELD: 1500");
                lines.add("ROLE: Regional Defense");
            }
            default -> {
                lines.add(type.name());
                lines.add("No extra data");
            }
        }
        return lines;
    }

    private int getBuildingCost(Building.Type type) {
        return switch (type) {
            case FACTORY -> 500;
            case WALL -> 60;
            case EXTRACTOR -> 300;
            case LASER_TOWER -> 400;
            case SOLAR_COLLECTOR -> 150;
            case SHIELD_GENERATOR -> 600;
            default -> 0;
        };
    }

    private List<String> getHoveredWorldTooltip(float mouseX, float mouseY, int myTeamId) {
        List<String> best = null;
        float bestDist = Float.MAX_VALUE;
        synchronized (gameState) {
            for (Unit u : gameState.units.values()) {
                float groundY = terrainHeightAt(u.position.x, u.position.y) + 0.5f;
                Vector3f screen = projectWorldToScreen(u.position.x, groundY, u.position.y);
                if (screen.z < 0.0f || screen.z > 1.0f)
                    continue;
                float dist = screenDistance(mouseX, mouseY, screen.x, screen.y);
                float threshold = 18.0f + u.radius * 10.0f;
                if (dist <= threshold && dist < bestDist) {
                    bestDist = dist;
                    best = describeUnitInfo(u);
                }
            }
            for (Building b : gameState.buildings.values()) {
                float groundY = terrainHeightAt(b.position.x, b.position.y) + b.size / 2.0f;
                Vector3f screen = projectWorldToScreen(b.position.x, groundY, b.position.y);
                if (screen.z < 0.0f || screen.z > 1.0f)
                    continue;
                float dist = screenDistance(mouseX, mouseY, screen.x, screen.y);
                float threshold = 22.0f + b.size * 6.0f;
                if (dist <= threshold && dist < bestDist) {
                    bestDist = dist;
                    best = describeBuildingInfo(b);
                }
            }
        }
        return best;
    }

    private float screenDistance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private List<String> describeUnitInfo(Unit u) {
        List<String> lines = new ArrayList<>();
        lines.add(u.type.name());
        lines.add("TEAM: " + u.teamId);
        lines.add("HP: " + (int) u.hp + " / " + (int) u.maxHp);
        lines.add("DMG: " + (int) u.attackDamage);
        lines.add("RANGE: " + (int) u.attackRange);
        lines.add("SPD: " + String.format("%.1f", u.speed));
        return lines;
    }

    private List<String> describeBuildingInfo(Building b) {
        List<String> lines = new ArrayList<>();
        lines.add(b.type.name());
        lines.add("TEAM: " + b.teamId);
        lines.add("HP: " + (int) b.hp + " / " + (int) b.maxHp);
        if (!b.isComplete) {
            lines.add(String.format("BUILD: %.0f%%", b.buildProgress * 100.0f));
        }
        if (b.attackRange > 0.0f && b.attackDamage > 0.0f) {
            lines.add("DMG: " + (int) b.attackDamage);
            lines.add("RANGE: " + (int) b.attackRange);
        }
        if (b.type == Building.Type.FACTORY) {
            lines.add("QUEUE: " + b.productionQueue.size());
        }
        if (b.type == Building.Type.EXTRACTOR) {
            lines.add("ROLE: Resource income");
        }
        return lines;
    }

    private void renderTooltipPanel(float x, float y, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        float maxChars = 0.0f;
        for (String line : lines) {
            maxChars = Math.max(maxChars, line.length());
        }
        float width = Math.max(180.0f, Math.min(360.0f, maxChars * 8.0f + 26.0f));
        float height = 18.0f + lines.size() * 18.0f;
        float px = clamp(x, 10.0f, windowWidth - width - 10.0f);
        float py = clamp(y - height, 10.0f, windowHeight - height - 10.0f);
        glColor4f(0.03f, 0.05f, 0.03f, 0.95f);
        glBegin(GL_QUADS);
        glVertex2f(px, py);
        glVertex2f(px + width, py);
        glVertex2f(px + width, py + height);
        glVertex2f(px, py + height);
        glEnd();
        glColor4f(0.34f, 0.85f, 0.45f, 0.8f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(px, py);
        glVertex2f(px + width, py);
        glVertex2f(px + width, py + height);
        glVertex2f(px, py + height);
        glEnd();
        for (int i = 0; i < lines.size(); i++) {
            drawText(lines.get(i), px + 12.0f, py + 20.0f + i * 18.0f, 1.25f);
        }
    }

    private void drawText(String text, float x, float y, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (fontRenderer == null) {
            ByteBuffer buffer = BufferUtils.createByteBuffer(text.length() * 270);
            int vertices = STBEasyFont.stb_easy_font_print(0, 0, text, null, buffer);
            glPushMatrix();
            glTranslatef(x, y, 0);
            glScalef(scale, scale, 1);
            glDisable(GL_TEXTURE_2D);
            glEnableClientState(GL_VERTEX_ARRAY);
            glEnableClientState(GL_COLOR_ARRAY);
            glVertexPointer(2, GL_FLOAT, 16, buffer);
            glColorPointer(4, GL_UNSIGNED_BYTE, 16, buffer);
            glDrawArrays(GL_QUADS, 0, vertices * 4);
            glDisableClientState(GL_COLOR_ARRAY);
            glDisableClientState(GL_VERTEX_ARRAY);
            glPopMatrix();
            return;
        }
        float[] color = new float[4];
        glGetFloatv(GL_CURRENT_COLOR, color);
        fontRenderer.drawText(text, x, y, color[0], color[1], color[2], color[3], scale);
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
