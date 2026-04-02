package com.lunar_prototype.iron_horizon;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.lunar_prototype.iron_horizon.common.MapSettings;
import com.lunar_prototype.iron_horizon.common.Network;
import com.lunar_prototype.iron_horizon.common.model.Building;
import com.lunar_prototype.iron_horizon.common.model.GameState;
import com.lunar_prototype.iron_horizon.common.model.Projectile;
import com.lunar_prototype.iron_horizon.common.model.Unit;
import com.lunar_prototype.iron_horizon.common.util.SpatialGrid;
import com.lunar_prototype.iron_horizon.common.util.Pathfinder;
import com.lunar_prototype.iron_horizon.server.TeamAI;
import org.joml.Vector2f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerLauncher {

    private final Server server;
    private final GameState gameState;
    private final SpatialGrid spatialGrid;
    private final Map<Integer, Network.ViewportUpdate> clientViewports = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> connectionTeams = new ConcurrentHashMap<>();
    private final Map<Integer, String> connectionNames = new ConcurrentHashMap<>();
    private final List<Network.CombatEvent> pendingEvents = new ArrayList<>();
    
    public final Map<Integer, Float> playerIncome = new ConcurrentHashMap<>();
    public final Map<Integer, Float> playerDrain = new ConcurrentHashMap<>();
    
    // 差分更新用の状態管理
    private static class ClientSyncState {
        final Map<Integer, Network.UnitData> lastUnits = new HashMap<>();
        final Map<Integer, Network.BuildingData> lastBuildings = new HashMap<>();
        long lastFullSyncTime = 0;
    }
    private final Map<Integer, ClientSyncState> clientSyncStates = new ConcurrentHashMap<>();
    
    private final TeamAI team2AI = new TeamAI(2);
    private static final int TICK_RATE = 30;
    private int idCounter = 10000;
    public static final int AI_PLAYER_ID = 999;

    public ServerLauncher() throws IOException {
        System.out.println("Initializing Iron Horizon Server...");
        gameState = new GameState();
        spatialGrid = new SpatialGrid(10.0f);
        setupMap();
        float spawnMargin = 40.0f;
        setupTeam(1, spawnMargin, spawnMargin, 0); // Initial Nexus has owner 0 (team shared)
        setupTeam(2, MapSettings.WORLD_SIZE - spawnMargin, MapSettings.WORLD_SIZE - spawnMargin, AI_PLAYER_ID);
        
        server = new Server(256000, 256000);
        Network.register(server);

        server.addListener(new Listener() {
            public void received(Connection connection, Object object) {
                int pid = connection.getID();
                int teamId = connectionTeams.getOrDefault(pid, 0);
                
                if (object instanceof Network.LoginRequest) {
                    int assignedTeamId = (connectionTeams.size() % 2) + 1;
                    connectionTeams.put(pid, assignedTeamId);
                    connectionNames.put(assignedTeamId, ((Network.LoginRequest) object).username != null ? ((Network.LoginRequest) object).username : "Player " + assignedTeamId);
                    
                    // Initialize player resources
                    gameState.playerMetal.put(pid, 1000.0f);
                    
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
                          Building b = new Building(idCounter++, cmd.buildingType, cmd.x, cmd.y, teamId, pid);
                          gameState.addBuilding(b);
                          Unit constructor = gameState.units.get(cmd.constructorUnitId);
                          if (constructor != null) {
                              Network.Task task = new Network.Task(); task.type = Network.Task.Type.BUILD; task.x = b.position.x; task.y = b.position.y; task.targetBuildingId = b.id;
                              synchronized (constructor.tasks) {
                                  if (!cmd.shiftHold) {
                                      constructor.tasks.clear();
                                  }
                                  constructor.tasks.add(task);
                                  if (constructor.targetBuildingId == null && constructor.targetUnitId == null && constructor.attackTargetBuildingId == null) {
                                      startNextQueuedTask(constructor);
                                  }
                              }
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
                                  synchronized (unit.tasks) {
                                      if (!cmd.queue) {
                                          unit.tasks.clear();
                                          unit.targetUnitId = null;
                                          unit.attackTargetBuildingId = null;
                                          unit.targetBuildingId = null;
                                      }
                                      if (cmd.waypoints != null && !cmd.waypoints.isEmpty()) {
                                          for (Vector2f wp : cmd.waypoints) {
                                              Network.Task task = new Network.Task();
                                              task.type = Network.Task.Type.MOVE;
                                              task.x = wp.x; task.y = wp.y;
                                              unit.tasks.add(task);
                                          }
                                      } else {
                                          Network.Task task = new Network.Task();
                                          task.type = Network.Task.Type.MOVE;
                                          task.x = cmd.targetX; task.y = cmd.targetY;
                                          unit.tasks.add(task);
                                      }
                                      if (unit.targetBuildingId == null && unit.targetUnitId == null && unit.attackTargetBuildingId == null) {
                                          startNextQueuedTask(unit);
                                      }
                                  }
                              }
                        }
                    } else if (object instanceof Network.AttackCommand) {
                        Network.AttackCommand cmd = (Network.AttackCommand) object;
                          for (Integer id : cmd.unitIds) {
                              Unit unit = gameState.units.get(id);
                              if (unit != null && unit.teamId == teamId) {
                                  unit.targetUnitId = cmd.targetUnitId;
                                  unit.attackTargetBuildingId = cmd.targetBuildingId;
                                  unit.targetBuildingId = null;
                                  if (cmd.targetBuildingId != null) {
                                      Building tb = gameState.buildings.get(cmd.targetBuildingId);
                                      if (tb != null) {
                                          unit.targetPosition.set(tb.position);
                                          unit.currentPath = Pathfinder.findPath(unit.position, unit.targetPosition, gameState, tb.id);
                                          unit.currentPathIndex = 0;
                                      }
                                  }
                                  synchronized (unit.tasks) {
                                      unit.tasks.clear();
                                  }
                              }
                          }
                      }
                }
                if (object instanceof Network.ViewportUpdate) { clientViewports.put(pid, (Network.ViewportUpdate) object); }
            }
            public void disconnected(Connection connection) {
                clientViewports.remove(connection.getID());
                Integer tid = connectionTeams.remove(connection.getID());
                if (tid != null) connectionNames.remove(tid);
            }
        });

        // Initialize AI resources
        gameState.playerMetal.put(AI_PLAYER_ID, 1000.0f);

        server.start(); server.bind(Network.TCP_PORT, Network.UDP_PORT); startSimulation();
    }

    private void setupMap() {
        java.util.Random r = new java.util.Random();
        int patchCount = 60;
        float grid = MapSettings.RESOURCE_GRID_SIZE;
        int gridCells = (int) (MapSettings.WORLD_SIZE / grid);
        java.util.HashSet<Long> occupied = new java.util.HashSet<>();
        for (int i = 0; i < patchCount; i++) {
            int cellX = 1 + r.nextInt(Math.max(1, gridCells - 2));
            int cellY = 1 + r.nextInt(Math.max(1, gridCells - 2));
            float x = cellX * grid + grid / 2.0f;
            float y = cellY * grid + grid / 2.0f;
            long key = (((long) cellX) << 32) | (cellY & 0xffffffffL);
            if (!occupied.contains(key)) {
                occupied.add(key);
                gameState.addBuilding(new Building(idCounter++, Building.Type.METAL_PATCH, x, y, 0, 0));
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
        team2AI.update(gameState, dt, playerIncome, playerDrain);
        
        playerIncome.clear();
        playerDrain.clear();

        // Base income for all players
        for (Integer pid : gameState.playerMetal.keySet()) {
            playerIncome.put(pid, 10.0f);
        }

        // Extractor income and cost drain
        for (Building b : gameState.buildings.values()) {
            if (b.teamId == 0) continue;
            int owner = b.ownerId;
            if (owner == 0) continue; // Skip team-shared (Nexus) for individual income calculation

            if (b.type == Building.Type.EXTRACTOR && b.isComplete) {
                playerIncome.put(owner, playerIncome.getOrDefault(owner, 0f) + 15.0f);
            }

            if (!b.isComplete) {
                float cost = getBuildingCost(b.type);
                int helpers = (int) gameState.units.values().stream().filter(u -> u.teamId == b.teamId && u.targetBuildingId != null && u.targetBuildingId == b.id && u.position.distance(b.position) < 15.0f).count();
                if (helpers > 0) playerDrain.put(owner, playerDrain.getOrDefault(owner, 0f) + (cost * 0.3f) * helpers);
            } else if (b.type == Building.Type.FACTORY && !b.productionQueue.isEmpty()) {
                float cost = getUnitCost(b.productionQueue.get(0));
                playerDrain.put(owner, playerDrain.getOrDefault(owner, 0f) + cost / 6.0f);
            }
        }

        // Apply metal updates and efficiency
        for (Integer pid : gameState.playerMetal.keySet()) {
            float inc = playerIncome.getOrDefault(pid, 0f);
            float drn = playerDrain.getOrDefault(pid, 0f);
            float currentMetal = gameState.getMetal(pid);
            float efficiency = 1.0f;
            if (drn > 0 && currentMetal <= 0 && inc < drn) efficiency = inc / drn;
            gameState.playerMetal.put(pid, Math.max(0, currentMetal + (inc - drn * efficiency) * dt));

            // Individual build/production logic
            for (Building b : gameState.buildings.values()) {
                if (b.ownerId != pid) continue;
                if (!b.isComplete) {
                    int helpers = (int) gameState.units.values().stream().filter(u -> u.teamId == b.teamId && u.targetBuildingId != null && u.targetBuildingId == b.id && u.position.distance(b.position) < 15.0f).count();
                    if (helpers > 0) {
                        b.buildProgress += dt * 0.3f * helpers * efficiency;
                        b.hp = b.maxHp * b.buildProgress;
                        if (b.buildProgress >= 1.0f) { b.buildProgress = 1.0f; b.isComplete = true; b.hp = b.maxHp; }
                    }
                } else if (b.type == Building.Type.FACTORY && !b.productionQueue.isEmpty()) {
                    float productionDuration = getProductionDuration(b.productionQueue.get(0));
                    b.productionTimer += dt * efficiency;
                    if (b.productionTimer >= productionDuration) {
                        Vector2f sp = new Vector2f(b.position.x + 5, b.position.y + 5);
                        Unit unit = new Unit(idCounter++, sp.x, sp.y);
                        unit.setType(b.productionQueue.remove(0)); unit.teamId = b.teamId; unit.ownerId = b.ownerId;
                        gameState.addUnit(unit); b.productionTimer = 0;
                    }
                }
            }
        }

        // Shared Logic: Nexus Support and Salvage
        for (int tid : new int[]{1, 2}) {
            // Finding a representative PID for efficiency (Nexus is shared, so we just use an arbitrary PID for building speed if needed)
            float efficiency = 1.0f; 
            applyNexusSupport(tid, dt, efficiency);
        }

        // Shared Logic: Laser Towers
        for (Building b : gameState.buildings.values()) {
            if (b.type == Building.Type.LASER_TOWER && b.isComplete) {
                b.attackTimer = Math.max(0, b.attackTimer - dt);
                if (b.attackTimer <= 0) {
                    Unit target = gameState.units.values().stream()
                            .filter(u -> u.teamId != b.teamId && u.position.distance(b.position) <= b.attackRange)
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

        // Shared Logic: Projectiles & Combat logic
        updateProjectiles(dt);
        updateUnits(dt);
        
        checkWinCondition();
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
                int pid = nexus.ownerId; // If nexus has no owner, maybe we share the metal or give to team rep
                if (pid == 0) {
                    // Find any player on this team
                    pid = connectionTeams.entrySet().stream().filter(e -> e.getValue() == nexus.teamId).map(Map.Entry::getKey).findFirst().orElse(AI_PLAYER_ID);
                }
                gameState.playerMetal.put(pid, gameState.getMetal(pid) + salvageAmount);
                return;
            }
        }
    }

    private void applySplashDamage(Unit attacker, float impactX, float impactY) {
        float splashRadius = 5.0f;
        float directRadius = 1.9f;
        float directDamage = attacker.attackDamage;
        float splashDamage = 120.0f;
        for (Unit enemy : gameState.units.values()) {
            if (enemy.teamId == attacker.teamId) continue;
            float dist = enemy.position.distance(new Vector2f(impactX, impactY));
            if (dist <= directRadius) enemy.hp -= directDamage;
            else if (dist <= splashRadius) {
                float falloff = 1.0f - ((dist - directRadius) / Math.max(0.001f, splashRadius - directRadius));
                enemy.hp -= splashDamage * Math.max(0.25f, falloff);
            }
        }
        for (Building building : gameState.buildings.values()) {
            if (building.teamId == attacker.teamId || building.type == Building.Type.METAL_PATCH) continue;
            float dist = building.position.distance(new Vector2f(impactX, impactY));
            if (dist <= splashRadius) building.hp -= splashDamage * 0.6f;
        }
    }

    private void updateProjectiles(float dt) {
        Iterator<Map.Entry<Integer, Projectile>> pIter = gameState.projectiles.entrySet().iterator();
        while (pIter.hasNext()) {
            Projectile p = pIter.next().getValue(); p.update(dt);
            if (p.life <= 0) { pIter.remove(); continue; }
            boolean hit = false;
            for (Unit u : gameState.units.values()) { if (u.teamId != p.teamId && u.position.distance(p.position) < u.radius + 1.0f) { u.hp -= p.damage; hit = true; break; } }
            if (!hit) { for (Building b : gameState.buildings.values()) { if (b.teamId != p.teamId && b.type != Building.Type.METAL_PATCH && b.position.distance(p.position) < b.collisionRadius) { b.hp -= p.damage; hit = true; break; } } }
            if (hit) pIter.remove();
        }
    }

    private void updateUnits(float dt) {
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
            if ((u.type == Unit.Type.TANK || u.type == Unit.Type.OBELISK) && u.targetUnitId == null && u.attackTargetBuildingId == null) {
                for (Unit enemy : gameState.units.values()) { if (enemy.teamId != u.teamId && enemy.position.distance(u.position) < u.attackRange) { u.targetUnitId = enemy.id; break; } }
            }
            if (u.targetUnitId != null) {
                Unit t = gameState.units.get(u.targetUnitId);
                if (t == null || t.hp <= 0) u.targetUnitId = null;
                else {
                    float dist = u.position.distance(t.position);
                    if (dist > u.attackRange) { if (u.manualMoveOrder) u.targetUnitId = null; else u.targetPosition.set(t.position); }
                    else {
                        if (!u.manualMoveOrder) u.targetPosition.set(u.position);
                        if (u.attackCooldown <= 0) {
                            if (u.type == Unit.Type.OBELISK) {
                                applySplashDamage(u, t.position.x, t.position.y);
                                addCombatEvent(Network.CombatEvent.Type.OBELISK_BLAST, u.position.x, u.position.y, t.position.x, t.position.y);
                                u.attackCooldown = 3.6f;
                            } else { spawnProjectile(u.position.x, u.position.y, t.position.x, t.position.y, u.attackDamage, u.teamId); u.attackCooldown = 1.0f; }
                        }
                    }
                }
            } else if (u.attackTargetBuildingId != null) {
                Building t = gameState.buildings.get(u.attackTargetBuildingId);
                if (t == null || t.hp <= 0) u.attackTargetBuildingId = null;
                else {
                    float dist = u.position.distance(t.position);
                    if (dist > u.attackRange + t.size) u.targetPosition.set(t.position);
                    else {
                        u.targetPosition.set(u.position);
                        if (u.attackCooldown <= 0) {
                            if (u.type == Unit.Type.OBELISK) {
                                applySplashDamage(u, t.position.x, t.position.y);
                                addCombatEvent(Network.CombatEvent.Type.OBELISK_BLAST, u.position.x, u.position.y, t.position.x, t.position.y);
                                u.attackCooldown = 3.6f;
                            } else { spawnProjectile(u.position.x, u.position.y, t.position.x, t.position.y, u.attackDamage, u.teamId); u.attackCooldown = 1.0f; }
                        }
                    }
                }
            }
                  if (u.targetBuildingId != null) {
                      Building b = gameState.buildings.get(u.targetBuildingId);
                      if (b == null || b.isComplete) { u.targetBuildingId = null; }
                  }
                  if (u.targetBuildingId == null && u.targetUnitId == null && u.attackTargetBuildingId == null) {
                      synchronized (u.tasks) {
                          if (!u.tasks.isEmpty()) {
                              // 移動命令中であれば、目的地に到着していることを確認してから次のタスクへ
                              if (u.isAtDestination || !u.manualMoveOrder) {
                                  startNextQueuedTask(u);
                              }
                          }
                      }
                  }
              }
        Iterator<Map.Entry<Integer, Building>> bIter = gameState.buildings.entrySet().iterator();
        while (bIter.hasNext()) { Building b = bIter.next().getValue(); if (b.hp <= 0 && b.type != Building.Type.METAL_PATCH) { addCombatEvent(Network.CombatEvent.Type.EXPLOSION, b.position.x, b.position.y, 0, 0); bIter.remove(); } }
    }

      private void startNextQueuedTask(Unit u) {
          u.isAtDestination = false;
          synchronized (u.tasks) {
              if (u.tasks.isEmpty()) return;
              Network.Task next = u.tasks.remove(0);
              if (next.type == Network.Task.Type.MOVE) {
                  u.targetPosition.set(next.x, next.y);
                  u.currentPath = Pathfinder.findPath(u.position, u.targetPosition, gameState);
                  u.currentPathIndex = 0;
                  u.manualMoveOrder = true;
              } else if (next.type == Network.Task.Type.BUILD) {
                  u.targetBuildingId = next.targetBuildingId;
                  u.targetPosition.set(next.x, next.y);
                  u.currentPath = Pathfinder.findPath(u.position, u.targetPosition, gameState, next.targetBuildingId);
                  u.currentPathIndex = 0;
                  u.manualMoveOrder = true;
              }
          }
      }

    private void checkWinCondition() {
        boolean n1 = false, n2 = false;
        for (Building b : gameState.buildings.values()) if (b.type == Building.Type.NEXUS) { if (b.teamId == 1) n1 = true; if (b.teamId == 2) n2 = true; }
        if (!n1) gameState.winnerTeamId = 2; else if (!n2) gameState.winnerTeamId = 1;
    }

    private float getBuildingCost(Building.Type type) {
        return switch (type) {
            case FACTORY -> 500;
            case EXTRACTOR -> 300;
            case LASER_TOWER -> 400;
            case WALL -> 60;
            default -> 100;
        };
    }

    private float getUnitCost(Unit.Type type) {
        return switch (type) {
            case TANK -> 200;
            case HOUND -> 120;
            case OBELISK -> 600;
            default -> 150;
        };
    }

    private float getProductionDuration(Unit.Type type) {
        return switch (type) {
            case TANK -> 5.0f;
            case HOUND -> 3.5f;
            case OBELISK -> 16.0f;
            default -> 4.0f;
        };
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
        long now = System.currentTimeMillis();
        synchronized (pendingEvents) {
            for (Connection conn : server.getConnections()) {
                int pid = conn.getID();
                Network.ViewportUpdate vp = clientViewports.get(pid);
                if (vp == null) continue;

                ClientSyncState sync = clientSyncStates.computeIfAbsent(pid, k -> new ClientSyncState());
                boolean fullSync = (now - sync.lastFullSyncTime > 5000); // 5秒ごとにフル同期
                if (fullSync) sync.lastFullSyncTime = now;

                Network.StateUpdate update = new Network.StateUpdate();
                update.isFullUpdate = fullSync;
                update.playerMetal.putAll(gameState.playerMetal);
                update.playerIncome.putAll(playerIncome);
                update.playerDrain.putAll(playerDrain);
                update.teamNames.putAll(connectionNames);
                update.teamNames.put(2, "AI Faction");
                update.events.addAll(pendingEvents);
                update.isStarted = gameState.isStarted;
                update.winnerTeamId = gameState.winnerTeamId;

                // ユニットの差分抽出
                Map<Integer, Network.UnitData> currentUnits = new HashMap<>();
                  for (Unit unit : gameState.units.values()) {
                      Network.UnitData data = new Network.UnitData();
                      data.id = unit.id; data.type = unit.type; data.teamId = unit.teamId; data.ownerId = unit.ownerId;
                      data.x = unit.position.x; data.y = unit.position.y; data.hp = unit.hp; data.maxHp = unit.maxHp; data.facingDeg = unit.facingDeg;
                      synchronized (unit.tasks) {
                          data.tasks.addAll(unit.tasks);
                      }
                      currentUnits.put(unit.id, data);

                    Network.UnitData last = sync.lastUnits.get(unit.id);
                      boolean changed = fullSync || last == null ||
                          Math.abs(last.x - data.x) > 0.05f || Math.abs(last.y - data.y) > 0.05f ||
                          Math.abs(last.hp - data.hp) > 1.0f || last.facingDeg != data.facingDeg ||
                          last.tasks.size() != data.tasks.size();

                    if (changed) update.units.add(data);
                }
                // 削除されたユニットの検知
                for (Integer id : sync.lastUnits.keySet()) {
                    if (!currentUnits.containsKey(id)) update.removedUnitIds.add(id);
                }
                sync.lastUnits.clear();
                sync.lastUnits.putAll(currentUnits);

                // 建造物の差分抽出
                Map<Integer, Network.BuildingData> currentBuildings = new HashMap<>();
                for (Building b : gameState.buildings.values()) {
                    Network.BuildingData bd = new Network.BuildingData();
                    bd.id = b.id; bd.type = b.type; bd.teamId = b.teamId; bd.ownerId = b.ownerId; bd.x = b.position.x; bd.y = b.position.y;
                    bd.hp = b.hp; bd.maxHp = b.maxHp; bd.buildProgress = b.buildProgress;
                    bd.productionProgress = b.productionTimer; bd.isComplete = b.isComplete;
                    bd.productionQueue.addAll(b.productionQueue);
                    currentBuildings.put(b.id, bd);

                    Network.BuildingData last = sync.lastBuildings.get(b.id);
                    boolean changed = fullSync || last == null ||
                        last.hp != bd.hp || last.buildProgress != bd.buildProgress ||
                        last.isComplete != bd.isComplete || last.productionQueue.size() != bd.productionQueue.size();

                    if (changed) update.buildings.add(bd);
                }
                // 削除された建造物の検知
                for (Integer id : sync.lastBuildings.keySet()) {
                    if (!currentBuildings.containsKey(id)) update.removedBuildingIds.add(id);
                }
                sync.lastBuildings.clear();
                sync.lastBuildings.putAll(currentBuildings);

                conn.sendUDP(update);
            }
            pendingEvents.clear();
        }
    }

    private void setupTeam(int teamId, float x, float y, int ownerId) {
        gameState.addBuilding(new Building(idCounter++, Building.Type.NEXUS, x, y, teamId, ownerId));
        Unit c = new Unit(idCounter++, x + 5, y + 5);
        c.setType(Unit.Type.CONSTRUCTOR); c.teamId = teamId; c.ownerId = ownerId; gameState.addUnit(c);
    }

    public static void main(String[] args) throws IOException { 
        ServerLauncher s = new ServerLauncher(); Runtime.getRuntime().addShutdownHook(new Thread(s.team2AI::close));
    }
}
