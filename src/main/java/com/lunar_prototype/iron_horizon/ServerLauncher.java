package com.lunar_prototype.iron_horizon;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.lunar_prototype.dark_singularity_api.Singularity;
import com.lunar_prototype.iron_horizon.common.MapSettings;
import com.lunar_prototype.iron_horizon.common.Network;
import com.lunar_prototype.iron_horizon.common.model.Building;
import com.lunar_prototype.iron_horizon.common.model.GameState;
import com.lunar_prototype.iron_horizon.common.model.Projectile;
import com.lunar_prototype.iron_horizon.common.model.Unit;
import com.lunar_prototype.iron_horizon.common.util.SpatialGrid;
import com.lunar_prototype.iron_horizon.server.TeamAI;
import org.joml.Vector2f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerLauncher {

    private final Server server;
    private final GameState gameState;
    private final SpatialGrid spatialGrid;
    private final Map<Integer, Network.ViewportUpdate> clientViewports = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> connectionTeams = new ConcurrentHashMap<>();
    private final List<Network.CombatEvent> pendingEvents = new ArrayList<>();
    public final Map<Integer, Float> teamIncome = new ConcurrentHashMap<>();
    public final Map<Integer, Float> teamDrain = new ConcurrentHashMap<>();
    private final TeamAI team2AI = new TeamAI(2);
    private static final int TICK_RATE = 30;
    private int idCounter = 10000;

    public ServerLauncher() throws IOException {
        System.out.println("Initializing Iron Horizon Server...");
        gameState = new GameState();
        spatialGrid = new SpatialGrid(10.0f);
        setupMap();
        float spawnMargin = 40.0f;
        setupTeam(1, spawnMargin, spawnMargin);
        setupTeam(2, MapSettings.WORLD_SIZE - spawnMargin, MapSettings.WORLD_SIZE - spawnMargin);
        server = new Server(256000, 256000);
        Network.register(server);

        server.addListener(new Listener() {
            public void received(Connection connection, Object object) {
                int teamId = connectionTeams.getOrDefault(connection.getID(), 0);
                if (object instanceof Network.LoginRequest) {
                    int assignedTeamId = (connectionTeams.size() % 2) + 1;
                    connectionTeams.put(connection.getID(), assignedTeamId);
                    Network.LoginResponse response = new Network.LoginResponse();
                    response.accepted = true; response.teamId = assignedTeamId;
                    response.message = "Joined team " + assignedTeamId;
                    connection.sendTCP(response);
                } else if (object instanceof Network.StartGameCommand) {
                    gameState.isStarted = true;
                } else if (gameState.isStarted && gameState.winnerTeamId == 0) {
                    if (object instanceof Network.BuildCommand) {
                        Network.BuildCommand cmd = (Network.BuildCommand) object;
                        if (cmd.buildingType == Building.Type.EXTRACTOR) {
                            boolean onPatch = false;
                            for (Building b : gameState.buildings.values()) {
                                if (b.type == Building.Type.METAL_PATCH && b.position.distance(new Vector2f(cmd.x, cmd.y)) < b.size) { onPatch = true; break; }
                            }
                            if (!onPatch) return;
                        }
                        Building b = new Building(idCounter++, cmd.buildingType, cmd.x, cmd.y, teamId);
                        gameState.addBuilding(b);
                        Unit constructor = gameState.units.get(cmd.constructorUnitId);
                        if (constructor != null) {
                            Network.Task task = new Network.Task(); task.type = Network.Task.Type.BUILD; task.x = b.position.x; task.y = b.position.y; task.targetBuildingId = b.id;
                            if (!cmd.shiftHold) { constructor.tasks.clear(); constructor.targetBuildingId = b.id; constructor.targetPosition.set(b.position); }
                            else constructor.tasks.add(task);
                        }
                    } else if (object instanceof Network.ProduceCommand) {
                        Network.ProduceCommand cmd = (Network.ProduceCommand) object;
                        Building factory = gameState.buildings.get(cmd.factoryId);
                        if (factory != null && factory.isComplete && factory.teamId == teamId) {
                            factory.productionQueue.add(cmd.unitType);
                        }
                    } else if (object instanceof Network.MoveCommand) {
                        Network.MoveCommand cmd = (Network.MoveCommand) object;
                        for (Integer id : cmd.unitIds) {
                            Unit unit = gameState.units.get(id);
                            if (unit != null && unit.teamId == teamId) {
                            unit.targetPosition.set(cmd.targetX, cmd.targetY);
                                unit.targetBuildingId = null; unit.tasks.clear(); unit.targetUnitId = null; unit.attackTargetBuildingId = null;
                                unit.manualMoveOrder = true;
                                if (cmd.targetBuildingId != null) {
                                    Building tb = gameState.buildings.get(cmd.targetBuildingId);
                                    if (tb != null && !tb.isComplete && unit.type == Unit.Type.CONSTRUCTOR) {
                                        unit.targetBuildingId = tb.id; unit.targetPosition.set(tb.position);
                                    }
                                }
                            }
                        }
                    } else if (object instanceof Network.AttackCommand) {
                        Network.AttackCommand cmd = (Network.AttackCommand) object;
                        for (Integer id : cmd.unitIds) {
                            Unit unit = gameState.units.get(id);
                            if (unit != null && unit.teamId == teamId) {
                                unit.targetUnitId = cmd.targetUnitId; unit.attackTargetBuildingId = cmd.targetBuildingId;
                                unit.targetBuildingId = null; unit.tasks.clear();
                            }
                        }
                    }
                }
                if (object instanceof Network.ViewportUpdate) { clientViewports.put(connection.getID(), (Network.ViewportUpdate) object); }
            }
            public void disconnected(Connection connection) { clientViewports.remove(connection.getID()); connectionTeams.remove(connection.getID()); }
        });

        server.start(); server.bind(Network.TCP_PORT, Network.UDP_PORT); startSimulation();
    }

    private void setupMap() {
        java.util.Random r = new java.util.Random();
        int patchCount = 60;
        float margin = MapSettings.RESOURCE_GRID_MARGIN;
        float grid = MapSettings.RESOURCE_GRID_SIZE;
        int gridCells = (int) (MapSettings.WORLD_SIZE / grid);
        java.util.HashSet<Long> occupied = new java.util.HashSet<>();
        for (int i = 0; i < patchCount; i++) {
            float x = 0;
            float y = 0;
            boolean valid = false;
            int attempts = 0;
            while (!valid && attempts < 200) {
                int cellX = 1 + r.nextInt(Math.max(1, gridCells - 2));
                int cellY = 1 + r.nextInt(Math.max(1, gridCells - 2));
                x = cellX * grid + grid / 2.0f;
                y = cellY * grid + grid / 2.0f;
                long key = (((long) cellX) << 32) | (cellY & 0xffffffffL);
                valid = x >= margin && y >= margin && x <= MapSettings.WORLD_SIZE - margin && y <= MapSettings.WORLD_SIZE - margin && !occupied.contains(key);
                if (valid) {
                    for (Building b : gameState.buildings.values()) {
                        if (b.position.distance(new Vector2f(x, y)) < 10.0f) {
                            valid = false;
                            break;
                        }
                    }
                }
                if (valid) {
                    occupied.add(key);
                    gameState.addBuilding(new Building(idCounter++, Building.Type.METAL_PATCH, x, y, 0));
                }
                attempts++;
            }
        }
    }

    private void startSimulation() {
        Thread simulationThread = new Thread(() -> {
            long lastTime = System.nanoTime();
            float nsPerTick = 1000000000.0f / TICK_RATE;
            while (true) {
                long now = System.nanoTime();
                float deltaTime = (now - lastTime) / 1000000000.0f; lastTime = now;
                if (gameState.isStarted && gameState.winnerTeamId == 0) { processGameLogic(deltaTime); gameState.update(deltaTime); }
                spatialGrid.clear();
                for (Unit unit : gameState.units.values()) spatialGrid.add(unit);

                Vector2f temp = new Vector2f();
                for (Unit unit : gameState.units.values()) {
                    if (unit.velocity.lengthSquared() > 0.001f) {
                        for (Building b : gameState.buildings.values()) {
                            if (b.type == Building.Type.METAL_PATCH) continue;
                            float dist = unit.position.distance(b.position);
                            float cr = unit.radius + b.collisionRadius + 2.0f;
                            if (dist < cr) {
                                temp.set(b.position).sub(unit.position);
                                if (unit.velocity.dot(temp) > 0) {
                                    Vector2f ss = new Vector2f(-unit.velocity.y, unit.velocity.x).normalize().mul(2.0f);
                                    unit.velocity.add(ss);
                                }
                            }
                        }
                    }
                    for (Building b : gameState.buildings.values()) {
                        if (b.type == Building.Type.METAL_PATCH) continue;
                        float d = unit.position.distance(b.position);
                        float md = unit.radius + b.collisionRadius;
                        if (d < md) { temp.set(unit.position).sub(b.position).normalize().mul(md - d); unit.position.add(temp); }
                    }
                    for (Unit neighbor : spatialGrid.query(unit.position.x-2, unit.position.y-2, unit.position.x+2, unit.position.y+2)) {
                        if (unit == neighbor) continue;
                        float d = unit.position.distance(neighbor.position);
                        float md = unit.radius + neighbor.radius;
                        if (d < md && d > 0.001f) {
                            temp.set(unit.position).sub(neighbor.position).normalize().mul((md - d) * 0.5f);
                            unit.position.add(temp); neighbor.position.sub(temp);
                        }
                    }
                }
                if (gameState.isStarted) for (Unit u : gameState.units.values()) u.position.add(u.velocity.x * deltaTime, u.velocity.y * deltaTime);
                broadcastState();
                synchronized (pendingEvents) { pendingEvents.clear(); }
                try {
                    long sleepTime = (long) ((nsPerTick - (System.nanoTime() - now)) / 1000000);
                    if (sleepTime > 0) Thread.sleep(sleepTime);
                } catch (InterruptedException e) {}
            }
        }, "SimulationThread");
        simulationThread.start();
    }

    private void processGameLogic(float dt) {
        team2AI.update(gameState, dt, teamIncome, teamDrain);
        
        for (int tid : new int[]{1, 2}) {
            float income = 10.0f; // Base income balanced
            for (Building b : gameState.buildings.values()) if (b.teamId == tid && b.isComplete && b.type == Building.Type.EXTRACTOR) income += 15.0f;
            teamIncome.put(tid, income);
            
            float drain = 0;
            for (Building b : gameState.buildings.values()) {
                if (b.teamId != tid) continue;
                if (!b.isComplete) {
                    float cost = switch (b.type) {
                        case FACTORY -> 500;
                        case EXTRACTOR -> 300;
                        case LASER_TOWER -> 400;
                        default -> 100;
                    };
                    int helpers = (int) gameState.units.values().stream().filter(u -> u.teamId == tid && u.targetBuildingId != null && u.targetBuildingId == b.id && u.position.distance(b.position) < 15.0f).count();
                    if (helpers > 0) drain += (cost * 0.3f) * helpers; // Build speed 0.3
                } else if (b.type == Building.Type.FACTORY && !b.productionQueue.isEmpty()) {
                    float cost = (b.productionQueue.get(0) == Unit.Type.TANK) ? 200 : 150;
                    drain += cost / 6.0f; // Production balanced
                }
            }
            teamDrain.put(tid, drain);
            
            float currentMetal = gameState.getMetal(tid);
            float efficiency = 1.0f;
            if (drain > 0 && currentMetal <= 0 && income < drain) efficiency = income / drain;
            gameState.teamMetal.put(tid, Math.max(0, currentMetal + (income - drain * efficiency) * dt));

            applyNexusSupport(tid, dt, efficiency);

            for (Building b : gameState.buildings.values()) {
                if (b.teamId != tid) continue;
                if (!b.isComplete) {
                    int helpers = (int) gameState.units.values().stream().filter(u -> u.teamId == tid && u.targetBuildingId != null && u.targetBuildingId == b.id && u.position.distance(b.position) < 15.0f).count();
                    if (helpers > 0) {
                        b.buildProgress += dt * 0.3f * helpers * efficiency;
                        b.hp = b.maxHp * b.buildProgress;
                        if (b.buildProgress >= 1.0f) { b.buildProgress = 1.0f; b.isComplete = true; b.hp = b.maxHp; }
                    }
                } else if (b.type == Building.Type.FACTORY && !b.productionQueue.isEmpty()) {
                    b.productionTimer += dt * efficiency;
                    if (b.productionTimer >= 5.0f) {
                        Vector2f sp = new Vector2f(b.position.x + 5, b.position.y + 5);
                        for (Unit u : gameState.units.values()) if (u.position.distance(sp) < 2.0f) u.targetPosition.set(sp.x + 5, sp.y + 2);
                        Unit unit = new Unit(idCounter++, sp.x, sp.y);
                        unit.setType(b.productionQueue.remove(0)); unit.teamId = tid;
                        gameState.addUnit(unit); b.productionTimer = 0;
                    }
                } else if (b.type == Building.Type.LASER_TOWER) {
                    b.attackTimer = Math.max(0, b.attackTimer - dt);
                    if (b.attackTimer <= 0) {
                        Unit target = gameState.units.values().stream()
                                .filter(u -> u.teamId != tid && u.position.distance(b.position) <= b.attackRange)
                                .min((a, c) -> Float.compare(a.position.distance(b.position), c.position.distance(b.position)))
                                .orElse(null);
                        if (target != null) {
                            target.hp -= b.attackDamage;
                            addCombatEvent(Network.CombatEvent.Type.LASER, b.position.x, b.position.y, target.position.x, target.position.y);
                            b.attackTimer = b.attackCooldown;
                        }
                    }
                }
            }
        }

        Iterator<Map.Entry<Integer, Projectile>> pIter = gameState.projectiles.entrySet().iterator();
        while (pIter.hasNext()) {
            Projectile p = pIter.next().getValue(); p.update(dt);
            if (p.life <= 0) { pIter.remove(); continue; }
            boolean hit = false;
            for (Unit u : gameState.units.values()) { if (u.teamId != p.teamId && u.position.distance(p.position) < u.radius + 1.0f) { u.hp -= p.damage; hit = true; break; } }
            if (!hit) { for (Building b : gameState.buildings.values()) { if (b.teamId != p.teamId && b.type != Building.Type.METAL_PATCH && b.position.distance(p.position) < b.collisionRadius) { b.hp -= p.damage; hit = true; break; } } }
            if (hit) pIter.remove();
        }

        Iterator<Map.Entry<Integer, Unit>> unitIter = gameState.units.entrySet().iterator();
        while (unitIter.hasNext()) {
            Unit u = unitIter.next().getValue();
            if (u.hp <= 0) {
                addCombatEvent(Network.CombatEvent.Type.EXPLOSION, u.position.x, u.position.y, 0, 0);
                awardNexusSalvage(u);
                unitIter.remove();
                continue;
            }
            if (u.attackCooldown > 0) u.attackCooldown -= dt;
            if (u.type == Unit.Type.TANK && u.targetUnitId == null && u.attackTargetBuildingId == null && !u.manualMoveOrder) {
                for (Unit enemy : gameState.units.values()) { if (enemy.teamId != u.teamId && enemy.position.distance(u.position) < u.attackRange) { u.targetUnitId = enemy.id; break; } }
            }
            if (u.targetUnitId != null) {
                Unit t = gameState.units.get(u.targetUnitId);
                if (t == null || t.hp <= 0) u.targetUnitId = null;
                else {
                    float dist = u.position.distance(t.position);
                    if (dist > u.attackRange) u.targetPosition.set(t.position);
                    else { u.targetPosition.set(u.position); if (u.attackCooldown <= 0) { spawnProjectile(u.position.x, u.position.y, t.position.x, t.position.y, u.attackDamage, u.teamId); u.attackCooldown = 1.0f; } }
                }
            } else if (u.attackTargetBuildingId != null) {
                Building t = gameState.buildings.get(u.attackTargetBuildingId);
                if (t == null || t.hp <= 0) u.attackTargetBuildingId = null;
                else {
                    float dist = u.position.distance(t.position);
                    if (dist > u.attackRange + t.size) u.targetPosition.set(t.position);
                    else { u.targetPosition.set(u.position); if (u.attackCooldown <= 0) { spawnProjectile(u.position.x, u.position.y, t.position.x, t.position.y, u.attackDamage, u.teamId); u.attackCooldown = 1.0f; } }
                }
            }
            if (u.targetBuildingId != null) { Building b = gameState.buildings.get(u.targetBuildingId); if (b == null || b.isComplete) u.targetBuildingId = null; }
            if (u.targetBuildingId == null && u.targetUnitId == null && u.attackTargetBuildingId == null && !u.tasks.isEmpty()) {
                Network.Task next = u.tasks.remove(0); if (next.type == Network.Task.Type.BUILD) { u.targetBuildingId = next.targetBuildingId; u.targetPosition.set(next.x, next.y); }
            }
        }
        Iterator<Map.Entry<Integer, Building>> bIter = gameState.buildings.entrySet().iterator();
        while (bIter.hasNext()) { Building b = bIter.next().getValue(); if (b.hp <= 0 && b.type != Building.Type.METAL_PATCH) { addCombatEvent(Network.CombatEvent.Type.EXPLOSION, b.position.x, b.position.y, 0, 0); bIter.remove(); } }
        boolean n1 = false, n2 = false;
        for (Building b : gameState.buildings.values()) if (b.type == Building.Type.NEXUS) { if (b.teamId == 1) n1 = true; if (b.teamId == 2) n2 = true; }
        if (!n1) gameState.winnerTeamId = 2; else if (!n2) gameState.winnerTeamId = 1;
    }

    private void applyNexusSupport(int teamId, float dt, float efficiency) {
        for (Building nexus : gameState.buildings.values()) {
            if (nexus.teamId != teamId || nexus.type != Building.Type.NEXUS || !nexus.isComplete) continue;
            for (Unit unit : gameState.units.values()) {
                if (unit.teamId != teamId) continue;
                if (unit.position.distance(nexus.position) <= 20.0f) {
                    unit.hp = Math.min(unit.maxHp, unit.hp + 35.0f * dt * efficiency);
                }
            }
        }
    }

    private void awardNexusSalvage(Unit deadUnit) {
        float salvageRadius = 20.0f;
        float salvageAmount = deadUnit.type == Unit.Type.TANK ? 35.0f : 20.0f;
        for (Building nexus : gameState.buildings.values()) {
            if (nexus.type != Building.Type.NEXUS || !nexus.isComplete || nexus.teamId == deadUnit.teamId) continue;
            if (nexus.position.distance(deadUnit.position) <= salvageRadius) {
                gameState.teamMetal.put(nexus.teamId, gameState.getMetal(nexus.teamId) + salvageAmount);
                return;
            }
        }
    }

    private void spawnProjectile(float x, float y, float tx, float ty, float dmg, int teamId) {
        Vector2f dir = new Vector2f(tx - x, ty - y).normalize().mul(40.0f);
        Projectile p = new Projectile(idCounter++, x, y, dir.x, dir.y, dmg, teamId);
        gameState.projectiles.put(p.id, p);
    }

    private void addCombatEvent(Network.CombatEvent.Type t, float x, float y, float tx, float ty) {
        Network.CombatEvent e = new Network.CombatEvent(); e.type = t; e.x = x; e.y = y; e.tx = tx; e.ty = ty;
        synchronized (pendingEvents) { pendingEvents.add(e); }
    }

    private void broadcastState() {
        synchronized (pendingEvents) {
            for (Connection conn : server.getConnections()) {
                Network.ViewportUpdate vp = clientViewports.get(conn.getID());
                if (vp != null) {
                    Network.StateUpdate update = new Network.StateUpdate();
                    update.teamMetal.putAll(gameState.teamMetal); update.teamIncome.putAll(teamIncome); update.teamDrain.putAll(teamDrain);
                    update.events.addAll(pendingEvents); update.isStarted = gameState.isStarted; update.winnerTeamId = gameState.winnerTeamId;
                    for (Unit unit : gameState.units.values()) {
                        Network.UnitData data = new Network.UnitData();
                        data.id = unit.id; data.type = unit.type; data.teamId = unit.teamId;
                        data.x = unit.position.x; data.y = unit.position.y; data.hp = unit.hp; data.maxHp = unit.maxHp;
                        update.units.add(data);
                    }
                    for (Building b : gameState.buildings.values()) {
                        Network.BuildingData bd = new Network.BuildingData();
                        bd.id = b.id; bd.type = b.type; bd.teamId = b.teamId; bd.x = b.position.x; bd.y = b.position.y;
                        bd.hp = b.hp; bd.maxHp = b.maxHp; bd.buildProgress = b.buildProgress; 
                        bd.productionProgress = b.productionTimer / 5.0f; bd.isComplete = b.isComplete;
                        bd.productionQueue.addAll(b.productionQueue); update.buildings.add(bd);
                    }
                    for (Projectile p : gameState.projectiles.values()) {
                        Network.ProjectileData pd = new Network.ProjectileData();
                        pd.id = p.id; pd.x = p.position.x; pd.y = p.position.y; pd.teamId = p.teamId;
                        pd.vx = p.velocity.x; pd.vy = p.velocity.y; update.projectiles.add(pd);
                    }
                    conn.sendUDP(update);
                }
            }
        }
    }

    private void setupTeam(int teamId, float x, float y) {
        gameState.addBuilding(new Building(idCounter++, Building.Type.NEXUS, x, y, teamId));
        Unit c = new Unit(idCounter++, x + 5, y + 5);
        c.setType(Unit.Type.CONSTRUCTOR); c.teamId = teamId; gameState.addUnit(c);
    }

    public static void main(String[] args) throws IOException { 
        ServerLauncher s = new ServerLauncher(); Runtime.getRuntime().addShutdownHook(new Thread(s.team2AI::close));
    }
}
