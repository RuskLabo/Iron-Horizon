package com.lunar_prototype.iron_horizon;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.lunar_prototype.iron_horizon.client.GameRenderer;
import com.lunar_prototype.iron_horizon.client.SoundManager;
import com.lunar_prototype.iron_horizon.common.Network;
import com.lunar_prototype.iron_horizon.common.model.Building;
import com.lunar_prototype.iron_horizon.common.model.GameState;
import com.lunar_prototype.iron_horizon.common.model.Unit;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lunar_prototype.iron_horizon.client.util.ConfigManager;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class ClientLauncher {
    private enum LoadingPhase {
        INITIAL_SETUP,
        LOAD_ASSETS,
        CONNECT_SERVER,
        READY
    }

    private long window;
    private Client client;
    private final GameState gameState = new GameState();
    private final SoundManager soundManager = new SoundManager();
    private final Set<Integer> selectedUnitIds = new HashSet<>();
    private final Set<Integer> selectedBuildingIds = new HashSet<>();
    private final List<GameRenderer.MoveMarker> moveMarkers = new ArrayList<>();
    private final List<GameRenderer.CombatMarker> combatMarkers = new ArrayList<>();
    private final List<GameRenderer.Effect> effects = new ArrayList<>();
    private final List<Vector3f> pathPreviewPoints = new ArrayList<>();
    private final List<Network.ProjectileData> projectileData = new ArrayList<>();
    private final Map<Integer, Vector2f> localUnitTargets = new HashMap<>();
    private GameRenderer renderer;

    private int myTeamId = 0;
    private int myPlayerId = 0;
    private boolean gameStartedPreviously = false;
    private int winnerPreviously = 0;
    private boolean isMenuOpen = false;
    private boolean debugOverlayEnabled = false;
    private boolean shiftDown = false;
    private Building.Type selectedBuildType = null;
    private boolean isSelecting = false;
    private boolean isPathDrawing = false;
    private boolean pathQueueMode = false;
    private LoadingPhase loadingPhase = LoadingPhase.INITIAL_SETUP;
    private final ConfigManager configManager = new ConfigManager();
    private String inputServerIp = "";
    private String inputUsername = "";
    private int activeField = 0; // 0: IP, 1: Username
    private boolean isConnecting = false;
    private double selectionStartX;
    private double selectionStartY;
    private double pathStartX;
    private double pathStartY;
    private double pathCurrentX;
    private double pathCurrentY;

    public ClientLauncher() {}

    public void run() {
        init();
        loop();
        if (renderer != null) {
            renderer.cleanup();
        }
        soundManager.cleanup();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
        if (client != null) {
            client.stop();
        }
    }

    private void init() {
        soundManager.init();
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        window = glfwCreateWindow(1280, 720, "Iron Horizon - RTS", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create the GLFW window");
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        renderer = new GameRenderer(
                window,
                gameState,
                soundManager,
                selectedUnitIds,
                selectedBuildingIds,
                moveMarkers,
                combatMarkers,
                effects,
                pathPreviewPoints,
                projectileData,
                localUnitTargets);

        inputServerIp = configManager.getServerIp();
        inputUsername = configManager.getUsername();
        loadingPhase = LoadingPhase.INITIAL_SETUP;

        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            double[] x = new double[1], y = new double[1];
            glfwGetCursorPos(w, x, y);

            if (loadingPhase == LoadingPhase.INITIAL_SETUP) {
                if (action == GLFW_PRESS && button == GLFW_MOUSE_BUTTON_LEFT) {
                    checkInitialSetupClick(x[0], y[0]);
                }
                return;
            }

            if (loadingPhase != LoadingPhase.READY) {
                return;
            }
            if (isMenuOpen) {
                if (action == GLFW_PRESS) checkMenuClick(x[0], y[0]);
                return;
            }
            boolean shift = shiftDown || (mods & GLFW_MOD_SHIFT) != 0;
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                if (checkUIClick(x[0], y[0])) return;
                if (!gameState.isStarted || gameState.winnerTeamId != 0) return;
                if (selectedBuildType != null) {
                    placeBuilding(x[0], y[0], shift);
                    if (!shift) selectedBuildType = null;
                    return;
                }
                selectionStartX = x[0];
                selectionStartY = y[0];
                isSelecting = true;
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
                renderer.onRightMouseButton(true);
                if (selectedBuildType != null) {
                    selectedBuildType = null;
                } else if (shift && !selectedUnitIds.isEmpty()) {
                    startPathDrawing(x[0], y[0]);
                } else {
                    handleRightClick(x[0], y[0]);
                }
            } else if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_RELEASE) {
                if (isSelecting) finishSelection(x[0], y[0]);
                isSelecting = false;
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_RELEASE) {
                renderer.onRightMouseButton(false);
                if (isPathDrawing) {
                    finishPathDrawing(x[0], y[0], shift);
                }
            }
        });

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (loadingPhase == LoadingPhase.INITIAL_SETUP) {
                if (action == GLFW_PRESS) {
                    if (key == GLFW_KEY_ESCAPE) {
                        glfwSetWindowShouldClose(window, true);
                    } else if (key == GLFW_KEY_TAB) {
                        activeField = (activeField + 1) % 2;
                    } else if (key == GLFW_KEY_ENTER) {
                        startConnectionPhase();
                    } else if (key == GLFW_KEY_BACKSPACE) {
                        if (activeField == 0 && !inputServerIp.isEmpty()) {
                            inputServerIp = inputServerIp.substring(0, inputServerIp.length() - 1);
                        } else if (activeField == 1 && !inputUsername.isEmpty()) {
                            inputUsername = inputUsername.substring(0, inputUsername.length() - 1);
                        }
                    }
                }
                return;
            }

            if (loadingPhase != LoadingPhase.READY) {
                return;
            }
            if (key == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT) {
                shiftDown = action != GLFW_RELEASE;
            }
            if (key == GLFW_KEY_F3 && action == GLFW_PRESS) {
                debugOverlayEnabled = !debugOverlayEnabled;
                renderer.setDebugOverlayEnabled(debugOverlayEnabled);
                return;
            }
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                if (selectedBuildType != null || !selectedUnitIds.isEmpty() || !selectedBuildingIds.isEmpty()) {
                    selectedUnitIds.clear();
                    selectedBuildingIds.clear();
                    selectedBuildType = null;
                } else {
                    isMenuOpen = !isMenuOpen;
                }
            }
        });

        glfwSetCharCallback(window, (w, codepoint) -> {
            if (loadingPhase == LoadingPhase.INITIAL_SETUP) {
                char c = (char) codepoint;
                if (activeField == 0) {
                    inputServerIp += c;
                } else if (activeField == 1) {
                    inputUsername += c;
                }
            }
        });

        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (loadingPhase != LoadingPhase.READY) {
                return;
            }
            renderer.onMouseMoved(xpos, ypos, isMenuOpen, selectedBuildType, isPathDrawing);
            if (isPathDrawing) {
                updatePathPreview(xpos, ypos);
            }
        });
        glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
            if (loadingPhase != LoadingPhase.READY) {
                return;
            }
            renderer.onScroll(yoffset, isMenuOpen);
        });
        glfwShowWindow(window);
        renderer.prepareCore();
    }

    private void checkInitialSetupClick(double x, double y) {
        int[] w = new int[1], h = new int[1];
        glfwGetWindowSize(window, w, h);
        float pW = 500.0f;
        float pH = 400.0f;
        float px = (w[0] - pW) * 0.5f;
        float py = (h[0] - pH) * 0.5f;

        // IP Field
        if (x > px + 30 && x < px + pW - 30 && y > py + 120 && y < py + 160) {
            activeField = 0;
        }
        // Username Field
        else if (x > px + 30 && x < px + pW - 30 && y > py + 210 && y < py + 250) {
            activeField = 1;
        }
        // Connect Button
        else if (x > px + 30 && x < px + 240 && y > py + 280 && y < py + 330) {
            startConnectionPhase();
        }
        // Local Button
        else if (x > px + 260 && x < px + 470 && y > py + 280 && y < py + 330) {
            inputServerIp = "localhost";
            startConnectionPhase();
        }
    }

    private void startConnectionPhase() {
        if (inputServerIp.isEmpty() || inputUsername.isEmpty()) return;
        configManager.setServerIp(inputServerIp);
        configManager.setUsername(inputUsername);
        configManager.save();
        loadingPhase = LoadingPhase.LOAD_ASSETS;
    }

    private void advanceLoadingPhase() {
        if (loadingPhase == LoadingPhase.LOAD_ASSETS) {
            renderer.loadGameAssets();
            loadingPhase = LoadingPhase.CONNECT_SERVER;
            return;
        }
        if (loadingPhase == LoadingPhase.CONNECT_SERVER) {
            startClientConnection();
            loadingPhase = LoadingPhase.READY;
        }
    }

    private void renderLoadingFrame() {
        if (loadingPhase == LoadingPhase.INITIAL_SETUP) {
            renderer.renderInitialSetupScreen(inputServerIp, inputUsername, activeField, isConnecting);
        } else if (loadingPhase == LoadingPhase.LOAD_ASSETS) {
            renderer.renderLoadingScreen(
                    "Preparing battlefield",
                    "Generating command cards and loading terrain data.",
                    0.45f);
        } else if (loadingPhase == LoadingPhase.CONNECT_SERVER) {
            renderer.renderLoadingScreen(
                    "Connecting to server",
                    "Syncing factions, units, and buildings.",
                    0.85f);
        } else {
            renderer.renderLoadingScreen(
                    "Starting",
                    "Finalizing client startup.",
                    1.0f);
        }
    }

    private void startClientConnection() {
        client = new Client(256000, 256000);
        Network.register(client);
        client.addListener(new Listener() {
            public void received(Connection connection, Object object) {
                if (object instanceof Network.LoginResponse) {
                    Network.LoginResponse resp = (Network.LoginResponse) object;
                    myTeamId = resp.teamId;
                    // Note: In KryoNet, connection ID on client side for the server is often not directly in the message 
                    // but we can assume the server might send it or we use the local connection ID.
                    // For simplicity, we'll let the server tell us in a future update or use the response's side effect.
                    // Actually, let's just use the connection ID if available.
                    myPlayerId = connection.getID(); 
                    renderer.setPlayerContext(myTeamId, myPlayerId);
                } else if (object instanceof Network.StateUpdate) {
                    Network.StateUpdate update = (Network.StateUpdate) object;
                    synchronized (gameState) {
                        gameState.playerMetal.clear(); gameState.playerMetal.putAll(update.playerMetal);
                        gameState.playerIncome.clear(); gameState.playerIncome.putAll(update.playerIncome);
                        gameState.playerDrain.clear(); gameState.playerDrain.putAll(update.playerDrain);
                        gameState.teamNames.clear();
                        gameState.teamNames.putAll(update.teamNames);
                        if (update.isStarted && !gameStartedPreviously) {
                            soundManager.playSound("start");
                            gameStartedPreviously = true;
                        }
                        if (update.winnerTeamId != 0 && winnerPreviously == 0) {
                            soundManager.playSound(update.winnerTeamId == myTeamId ? "victory" : "defeat");
                            winnerPreviously = update.winnerTeamId;
                        }
                        gameState.isStarted = update.isStarted;
                        gameState.winnerTeamId = update.winnerTeamId;
                        int oldTanks = (int) gameState.units.values().stream().filter(u -> u.teamId == myTeamId && u.type == Unit.Type.TANK).count();
                        int newTanks = (int) update.units.stream().filter(u -> u.teamId == myTeamId && u.type == Unit.Type.TANK).count();
                        if (newTanks > oldTanks) soundManager.playSound("ready");
                        Map<Integer, Boolean> previousCompletion = new HashMap<>();
                        for (Building existing : gameState.buildings.values()) {
                            previousCompletion.put(existing.id, existing.isComplete);
                        }
                        if (update.isFullUpdate) {
                            gameState.units.clear();
                            gameState.buildings.clear();
                        }
                        
                        // ユニットの更新・削除
                        for (Integer id : update.removedUnitIds) gameState.units.remove(id);
                        for (Network.UnitData data : update.units) {
                            Unit unit = gameState.units.get(data.id);
                            if (unit == null) {
                                unit = new Unit(data.id, data.x, data.y);
                                gameState.addUnit(unit);
                            }
                            unit.position.set(data.x, data.y);
                            unit.type = data.type;
                            unit.teamId = data.teamId;
                            unit.ownerId = data.ownerId;
                            unit.hp = data.hp;
                            unit.maxHp = data.maxHp;
                            unit.facingDeg = data.facingDeg;
                            synchronized (unit.tasks) {
                                unit.tasks.clear();
                                unit.tasks.addAll(data.tasks);
                            }
                        }

                        // 建造物の更新・削除
                        for (Integer id : update.removedBuildingIds) gameState.buildings.remove(id);
                        for (Network.BuildingData bData : update.buildings) {
                            Building building = gameState.buildings.get(bData.id);
                            boolean wasJustCompleted = false;
                            if (building == null) {
                                building = new Building(bData.id, bData.type, bData.x, bData.y, bData.teamId, bData.ownerId);
                                gameState.addBuilding(building);
                            }
                            building.hp = bData.hp;
                            building.maxHp = bData.maxHp;
                            building.buildProgress = bData.buildProgress;
                            if (!building.isComplete && bData.isComplete) wasJustCompleted = true;
                            building.isComplete = bData.isComplete;
                            building.productionTimer = bData.productionProgress;
                            building.productionQueue.clear();
                            building.productionQueue.addAll(bData.productionQueue);
                            
                            if (wasJustCompleted) {
                                synchronized (effects) {
                                    effects.add(new GameRenderer.Effect(GameRenderer.Effect.Type.BUILD_COMPLETE, building.position.x, building.position.y, 0, 0));
                                }
                            }
                        }
                        synchronized (projectileData) {
                            projectileData.clear();
                            projectileData.addAll(update.projectiles);
                        }
                    }
                    synchronized (effects) {
                        for (Network.CombatEvent e : update.events) {
                            if (e.type == Network.CombatEvent.Type.ATTACK) {
                                for (Network.BuildingData bd : update.buildings) {
                                    if (bd.teamId == myTeamId && Math.abs(bd.x - e.tx) < 2.0f && Math.abs(bd.y - e.ty) < 2.0f) {
                                        soundManager.playSound("under_attack");
                                        break;
                                    }
                                }
                            } else if (e.type == Network.CombatEvent.Type.LASER) {
                                effects.add(new GameRenderer.Effect(GameRenderer.Effect.Type.LASER, e.x, e.y, e.tx, e.ty));
                            } else if (e.type == Network.CombatEvent.Type.EXPLOSION) {
                                effects.add(new GameRenderer.Effect(GameRenderer.Effect.Type.EXPLOSION, e.x, e.y, 0, 0));
                            } else if (e.type == Network.CombatEvent.Type.OBELISK_BLAST) {
                                effects.add(new GameRenderer.Effect(GameRenderer.Effect.Type.OBELISK_BLAST, e.x, e.y, e.tx, e.ty));
                            }
                            combatMarkers.add(new GameRenderer.CombatMarker(e.x, e.y));
                        }
                    }
                }
            }
        });
        client.start();
        try {
            client.connect(5000, inputServerIp, Network.TCP_PORT, Network.UDP_PORT);
            Network.LoginRequest req = new Network.LoginRequest();
            req.username = inputUsername;
            client.sendTCP(req);
        } catch (IOException e) {
            e.printStackTrace();
            // 接続失敗時はセットアップに戻す
            loadingPhase = LoadingPhase.INITIAL_SETUP;
        }
    }

    private void checkMenuClick(double x, double y) {
        int[] w = new int[1], h = new int[1];
        glfwGetWindowSize(window, w, h);
        float cx = w[0] / 2.0f;
        float cy = h[0] / 2.0f;
        if (x > cx - 100 && x < cx + 100 && y > cy && y < cy + 20) {
            float vol = (float) ((x - (cx - 100)) / 200.0);
            soundManager.setMasterVolume(vol);
        }
        if (x > cx - 100 && x < cx + 100 && y > cy + 40 && y < cy + 100) isMenuOpen = false;
        if (x > cx - 100 && x < cx + 100 && y > cy + 110 && y < cy + 170) glfwSetWindowShouldClose(window, true);
    }

    private boolean checkUIClick(double x, double y) {
        int[] wh = new int[1], ht = new int[1];
        glfwGetWindowSize(window, wh, ht);
        if (y < ht[0] - 80) return false;
        if (!gameState.isStarted) {
            if (x > 20 && x < 200) client.sendTCP(new Network.StartGameCommand());
            return true;
        }
        boolean constructorSelected = false;
        synchronized (gameState) {
            for (Integer id : selectedUnitIds) {
                Unit u = gameState.units.get(id);
                if (u != null && u.type == Unit.Type.CONSTRUCTOR) {
                    constructorSelected = true;
                    break;
                }
            }
        }
        boolean factorySelected = false;
        int factoryId = -1;
        synchronized (gameState) {
            for (Integer id : selectedBuildingIds) {
                Building b = gameState.buildings.get(id);
                if (b != null && b.type == Building.Type.FACTORY && b.isComplete) {
                    factorySelected = true;
                    factoryId = id;
                    break;
                }
            }
        }
        if (constructorSelected) {
            if (x > 20 && x < 140) selectedBuildType = Building.Type.FACTORY;
            else if (x > 150 && x < 270) selectedBuildType = Building.Type.WALL;
            else if (x > 280 && x < 400) selectedBuildType = Building.Type.EXTRACTOR;
            else if (x > 410 && x < 530) selectedBuildType = Building.Type.LASER_TOWER;
        } else if (factorySelected) {
            if (x > 20 && x < 170) sendProduceCommand(factoryId, Unit.Type.TANK);
            else if (x > 180 && x < 330) sendProduceCommand(factoryId, Unit.Type.HOUND);
            else if (x > 340 && x < 490) sendProduceCommand(factoryId, Unit.Type.CONSTRUCTOR);
            else if (x > 500 && x < 650) sendProduceCommand(factoryId, Unit.Type.OBELISK);
        }
        return true;
    }

    private void handleRightClick(double x, double y) {
        if (!gameState.isStarted || selectedUnitIds.isEmpty()) return;
        Vector3f target = renderer.getMouseWorldPos(x, y);
        Vector2f target2d = new Vector2f(target.x, target.z);
        synchronized (gameState) {
            for (Unit u : gameState.units.values()) {
                if (u.teamId != myTeamId && u.position.distance(target2d) < 2.0f) {
                    soundManager.playSound("attack");
                    sendAttackCommand(u.id, null);
                    return;
                }
            }
            for (Building b : gameState.buildings.values()) {
                if (b.position.distance(target2d) < b.size) {
                    if (b.teamId != myTeamId && b.type != Building.Type.METAL_PATCH) {
                        soundManager.playSound("attack");
                        sendAttackCommand(null, b.id);
                        return;
                    } else if (b.teamId == myTeamId && !b.isComplete) {
                        sendMoveCommand(x, y);
                        return;
                    }
                }
            }
        }
        soundManager.playSound("move");
        sendMoveCommand(x, y);
    }

    private void startPathDrawing(double x, double y) {
        isPathDrawing = true;
        pathQueueMode = true;
        pathStartX = x;
        pathStartY = y;
        pathCurrentX = x;
        pathCurrentY = y;
        updatePathPreview(x, y);
    }

    private void updatePathPreview(double x, double y) {
        pathCurrentX = x;
        pathCurrentY = y;
        synchronized (pathPreviewPoints) {
            pathPreviewPoints.clear();
            Vector3f start = renderer.getMouseWorldPos(pathStartX, pathStartY);
            Vector3f end = renderer.getMouseWorldPos(pathCurrentX, pathCurrentY);
            float dx = end.x - start.x;
            float dz = end.z - start.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            int segments = Math.max(2, (int) (dist / 8.0f));
            for (int i = 0; i <= segments; i++) {
                float t = i / (float) segments;
                pathPreviewPoints.add(new Vector3f(start.x + dx * t, 0.0f, start.z + dz * t));
            }
        }
    }

    private void finishPathDrawing(double x, double y, boolean queue) {
        isPathDrawing = false;
        updatePathPreview(x, y);
        Vector3f start = renderer.getMouseWorldPos(pathStartX, pathStartY);
        Vector3f end = renderer.getMouseWorldPos(x, y);
        sendMovePathCommand(start.x, start.z, end.x, end.z, pathQueueMode);
        pathQueueMode = false;
    }

    private void sendAttackCommand(Integer targetUnitId, Integer targetBuildingId) {
        Network.AttackCommand cmd = new Network.AttackCommand();
        cmd.unitIds.addAll(selectedUnitIds);
        cmd.targetUnitId = targetUnitId;
        cmd.targetBuildingId = targetBuildingId;
        client.sendTCP(cmd);
    }

    private void placeBuilding(double x, double y, boolean shift) {
        Integer constructorId = null;
        synchronized (gameState) {
            for (Integer id : selectedUnitIds) {
                Unit u = gameState.units.get(id);
                if (u != null && u.type == Unit.Type.CONSTRUCTOR) {
                    constructorId = id;
                    break;
                }
            }
        }
        if (constructorId == null) return;
        Vector3f pos = renderer.getMouseWorldPos(x, y);
        float gx = (float) Math.floor(pos.x / 2.0f) * 2.0f + 1.0f;
        float gz = (float) Math.floor(pos.z / 2.0f) * 2.0f + 1.0f;
        soundManager.playSound("build");
        Network.BuildCommand cmd = new Network.BuildCommand();
        cmd.buildingType = selectedBuildType;
        cmd.x = gx;
        cmd.y = gz;
        cmd.constructorUnitId = constructorId;
        cmd.shiftHold = shift;
        client.sendTCP(cmd);
    }

    private void finishSelection(double endX, double endY) {
        double minX = Math.min(selectionStartX, endX);
        double maxX = Math.max(selectionStartX, endX);
        double minY = Math.min(selectionStartY, endY);
        double maxY = Math.max(selectionStartY, endY);
        boolean tinyDrag = Math.abs(maxX - minX) < 6.0 && Math.abs(maxY - minY) < 6.0;
        boolean anySelected = false;
        selectedUnitIds.clear();
        selectedBuildingIds.clear();
        synchronized (gameState) {
            for (Unit u : gameState.units.values()) {
                if (u.teamId != myTeamId) continue;
                float groundY = renderer.getTerrainHeightAt(u.position.x, u.position.y) + 0.5f;
                Vector3f screen = renderer.projectWorldToScreen(u.position.x, groundY, u.position.y);
                if (screen.z < 0.0f || screen.z > 1.0f) continue;
                if (isInsideSelection(screen.x, screen.y, minX, maxX, minY, maxY, tinyDrag, 16.0f)) {
                    selectedUnitIds.add(u.id);
                    anySelected = true;
                }
            }
            for (Building b : gameState.buildings.values()) {
                if (b.teamId != myTeamId) continue;
                if (isBuildingInsideSelection(b, minX, maxX, minY, maxY, tinyDrag)) {
                    selectedBuildingIds.add(b.id);
                    anySelected = true;
                }
            }
        }
        if (anySelected) soundManager.playSound("selected");
    }

    private boolean isInsideSelection(float x, float y, double minX, double maxX, double minY, double maxY, boolean tinyDrag, float padding) {
        if (tinyDrag) {
            double centerX = (minX + maxX) * 0.5;
            double centerY = (minY + maxY) * 0.5;
            return Math.abs(x - centerX) <= padding && Math.abs(y - centerY) <= padding;
        }
        return x >= minX - padding && x <= maxX + padding && y >= minY - padding && y <= maxY + padding;
    }

    private boolean isBuildingInsideSelection(Building building, double minX, double maxX, double minY, double maxY, boolean tinyDrag) {
        float baseY = renderer.getTerrainHeightAt(building.position.x, building.position.y);
        float topY = baseY + building.size / 2.0f;
        float half = Math.max(0.6f, building.size / 2.0f);
        float[][] points = new float[][] {
                {building.position.x, topY, building.position.y},
                {building.position.x - half, topY, building.position.y - half},
                {building.position.x + half, topY, building.position.y - half},
                {building.position.x + half, topY, building.position.y + half},
                {building.position.x - half, topY, building.position.y + half}
        };
        float padding = tinyDrag ? 18.0f : 10.0f;
        for (float[] p : points) {
            Vector3f screen = renderer.projectWorldToScreen(p[0], p[1], p[2]);
            if (screen.z < 0.0f || screen.z > 1.0f) continue;
            if (isInsideSelection(screen.x, screen.y, minX, maxX, minY, maxY, tinyDrag, padding)) {
                return true;
            }
        }
        return false;
    }

    private void sendMoveCommand(double x, double y) {
        if (selectedUnitIds.isEmpty()) return;
        Vector3f target = renderer.getMouseWorldPos(x, y);
        moveMarkers.add(new GameRenderer.MoveMarker(target.x, target.z));
        Network.MoveCommand cmd = new Network.MoveCommand();
        cmd.targetX = target.x;
        cmd.targetY = target.z;
        cmd.unitIds.addAll(selectedUnitIds);
        synchronized (gameState) {
            for (Building b : gameState.buildings.values()) {
                if (b.position.distance(new Vector2f(target.x, target.z)) < b.size) {
                    cmd.targetBuildingId = b.id;
                    break;
                }
            }
        }
        // ローカルターゲットを更新
        Vector2f targetVec = new Vector2f(target.x, target.z);
        for (Integer id : selectedUnitIds) {
            localUnitTargets.put(id, new Vector2f(targetVec));
        }
        client.sendTCP(cmd);
    }

    private void sendMovePathCommand(float startX, float startY, float endX, float endY, boolean queue) {
        if (selectedUnitIds.isEmpty()) return;

        float dx = endX - startX;
        float dy = endY - startY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        // ライン方向ベクトルへの射影でユニットをソート（ライン始点に近い順）
        List<Integer> sortedUnitIds = new ArrayList<>(selectedUnitIds);
        if (dist > 0.001f) {
            float invDist = 1.0f / dist;
            float lx = dx * invDist;
            float lz = dy * invDist;
            synchronized (gameState) {
                sortedUnitIds.sort((a, b) -> {
                    Unit ua = gameState.units.get(a);
                    Unit ub = gameState.units.get(b);
                    float pa = (ua != null) ? (ua.position.x * lx + ua.position.y * lz) : 0f;
                    float pb = (ub != null) ? (ub.position.x * lx + ub.position.y * lz) : 0f;
                    return Float.compare(pa, pb);
                });
            }
        }

        int n = sortedUnitIds.size();
        for (int i = 0; i < n; i++) {
            float t = (n == 1) ? 0.5f : i / (float) (n - 1);
            float destX = startX + dx * t;
            float destY = startY + dy * t;

            Network.MoveCommand cmd = new Network.MoveCommand();
            cmd.unitIds.add(sortedUnitIds.get(i));
            cmd.queue = queue;
            cmd.targetX = destX;
            cmd.targetY = destY;
            client.sendTCP(cmd);

            localUnitTargets.put(sortedUnitIds.get(i), new Vector2f(destX, destY));
        }

        // パスプレビューは始点〜終点の全体ラインとして表示
        synchronized (pathPreviewPoints) {
            pathPreviewPoints.clear();
            int segments = Math.max(2, (int) (dist / 8.0f));
            for (int i = 0; i <= segments; i++) {
                float t = i / (float) segments;
                pathPreviewPoints.add(new Vector3f(startX + dx * t, 0.0f, startY + dy * t));
            }
        }
    }

    private void sendProduceCommand(int factoryId, Unit.Type type) {
        Network.ProduceCommand cmd = new Network.ProduceCommand();
        cmd.factoryId = factoryId;
        cmd.unitType = type;
        client.sendTCP(cmd);
    }

    private void handleInput(float dt) {
        renderer.handleKeyboardInput(dt);
    }

    private void syncViewport() {
        Vector3f cameraPos = renderer.getCameraPosition();
        Network.ViewportUpdate vp = new Network.ViewportUpdate();
        vp.centerX = cameraPos.x;
        vp.centerY = cameraPos.z;
        vp.width = 150;
        vp.height = 150;
        client.sendUDP(vp);
    }

    private void loop() {
        long lastTime = System.currentTimeMillis();
        while (!glfwWindowShouldClose(window)) {
            long now = System.currentTimeMillis();
            float dt = (now - lastTime) / 1000.0f;
            float frameTimeMs = (float) (now - lastTime);
            float fps = dt > 0.0f ? 1.0f / dt : 0.0f;
            lastTime = now;
            if (loadingPhase != LoadingPhase.READY) {
                renderLoadingFrame();
                glfwSwapBuffers(window);
                glfwPollEvents();
                advanceLoadingPhase();
                continue;
            }
            if (!isMenuOpen) {
                handleInput(dt);
                syncViewport();
                synchronized (moveMarkers) {
                    moveMarkers.removeIf(m -> (m.life -= dt * 2.0f) <= 0);
                }
                synchronized (effects) {
                    effects.removeIf(e -> (e.life -= dt * 3.0f) <= 0);
                }
                synchronized (combatMarkers) {
                    combatMarkers.removeIf(m -> (m.life -= dt * 1.5f) <= 0);
                }
            }
            renderer.renderFrame(myTeamId, isSelecting, selectionStartX, selectionStartY, selectedBuildType, isMenuOpen, fps, frameTimeMs);
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    public static void main(String[] args) {
        new ClientLauncher().run();
    }
}
