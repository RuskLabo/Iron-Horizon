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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class ClientLauncher {
    private long window;
    private Client client;
    private final GameState gameState = new GameState();
    private final SoundManager soundManager = new SoundManager();
    private final Set<Integer> selectedUnitIds = new HashSet<>();
    private final Set<Integer> selectedBuildingIds = new HashSet<>();
    private final List<GameRenderer.MoveMarker> moveMarkers = new ArrayList<>();
    private final List<GameRenderer.Effect> effects = new ArrayList<>();
    private final List<Network.ProjectileData> projectileData = new ArrayList<>();
    private GameRenderer renderer;

    private int myTeamId = 0;
    private boolean gameStartedPreviously = false;
    private int winnerPreviously = 0;
    private boolean isMenuOpen = false;
    private Building.Type selectedBuildType = null;
    private boolean isSelecting = false;
    private double selectionStartX;
    private double selectionStartY;

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
                effects,
                projectileData);

        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            double[] x = new double[1], y = new double[1];
            glfwGetCursorPos(w, x, y);
            if (isMenuOpen) {
                if (action == GLFW_PRESS) checkMenuClick(x[0], y[0]);
                return;
            }
            boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
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
            } else if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_RELEASE) {
                if (isSelecting) finishSelection(x[0], y[0]);
                isSelecting = false;
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
                renderer.onRightMouseButton(true);
                if (selectedBuildType != null) selectedBuildType = null;
                else handleRightClick(x[0], y[0]);
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_RELEASE) {
                renderer.onRightMouseButton(false);
            }
        });

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
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

        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> renderer.onMouseMoved(xpos, ypos, isMenuOpen, selectedBuildType));
        glfwSetScrollCallback(window, (w, xoffset, yoffset) -> renderer.onScroll(yoffset, isMenuOpen));
        glfwShowWindow(window);
        renderer.init();

        client = new Client(256000, 256000);
        Network.register(client);
        client.addListener(new Listener() {
            public void received(Connection connection, Object object) {
                if (object instanceof Network.LoginResponse) {
                    myTeamId = ((Network.LoginResponse) object).teamId;
                } else if (object instanceof Network.StateUpdate) {
                    Network.StateUpdate update = (Network.StateUpdate) object;
                    synchronized (gameState) {
                        gameState.teamMetal.putAll(update.teamMetal);
                        gameState.teamIncome.putAll(update.teamIncome);
                        gameState.teamDrain.putAll(update.teamDrain);
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
                        gameState.units.clear();
                        for (Network.UnitData data : update.units) {
                            Unit unit = new Unit(data.id, data.x, data.y);
                            unit.type = data.type;
                            unit.teamId = data.teamId;
                            unit.hp = data.hp;
                            unit.maxHp = data.maxHp;
                            gameState.addUnit(unit);
                        }
                        gameState.buildings.clear();
                        for (Network.BuildingData bData : update.buildings) {
                            Building building = new Building(bData.id, bData.type, bData.x, bData.y, bData.teamId);
                            building.hp = bData.hp;
                            building.maxHp = bData.maxHp;
                            building.buildProgress = bData.buildProgress;
                            building.isComplete = bData.isComplete;
                            building.productionTimer = bData.productionProgress;
                            building.productionQueue.addAll(bData.productionQueue);
                            gameState.addBuilding(building);
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
                            }
                        }
                    }
                }
            }
        });
        client.start();
        try {
            client.connect(5000, "localhost", Network.TCP_PORT, Network.UDP_PORT);
            Network.LoginRequest req = new Network.LoginRequest();
            req.username = "Player1";
            client.sendTCP(req);
        } catch (IOException e) {
            e.printStackTrace();
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
            else if (x > 180 && x < 330) sendProduceCommand(factoryId, Unit.Type.CONSTRUCTOR);
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
        Vector3f sW = renderer.getMouseWorldPos(selectionStartX, selectionStartY);
        Vector3f eW = renderer.getMouseWorldPos(endX, endY);
        float minX = Math.min(sW.x, eW.x);
        float maxX = Math.max(sW.x, eW.x);
        float minZ = Math.min(sW.z, eW.z);
        float maxZ = Math.max(sW.z, eW.z);
        boolean anySelected = false;
        selectedUnitIds.clear();
        selectedBuildingIds.clear();
        synchronized (gameState) {
            for (Unit u : gameState.units.values()) {
                if (u.teamId == myTeamId && u.position.x >= minX && u.position.x <= maxX && u.position.y >= minZ && u.position.y <= maxZ) {
                    selectedUnitIds.add(u.id);
                    anySelected = true;
                }
            }
            for (Building b : gameState.buildings.values()) {
                if (b.teamId == myTeamId && b.position.x >= minX && b.position.x <= maxX && b.position.y >= minZ && b.position.y <= maxZ) {
                    selectedBuildingIds.add(b.id);
                    anySelected = true;
                }
            }
        }
        if (anySelected) soundManager.playSound("selected");
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
        client.sendTCP(cmd);
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
            lastTime = now;
            if (!isMenuOpen) {
                handleInput(dt);
                syncViewport();
                synchronized (moveMarkers) {
                    moveMarkers.removeIf(m -> (m.life -= dt * 2.0f) <= 0);
                }
                synchronized (effects) {
                    effects.removeIf(e -> (e.life -= dt * 3.0f) <= 0);
                }
            }
            renderer.renderFrame(myTeamId, isSelecting, selectionStartX, selectionStartY, selectedBuildType, isMenuOpen);
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    public static void main(String[] args) {
        new ClientLauncher().run();
    }
}
