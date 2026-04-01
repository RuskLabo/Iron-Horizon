package com.lunar_prototype.iron_horizon.server;

import com.lunar_prototype.dark_singularity_api.Singularity;
import com.lunar_prototype.iron_horizon.common.MapSettings;
import com.lunar_prototype.iron_horizon.common.model.Building;
import com.lunar_prototype.iron_horizon.common.model.GameState;
import com.lunar_prototype.iron_horizon.common.model.Unit;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TeamAI {
    private final int teamId;
    private float decisionTimer = 0;
    private float nextDecisionTime = 1.0f;
    private Singularity singularity;
    private Vector2f enemyBaseLoc;
    private boolean isAssaultMode = false;

    private static final float UNIT_VISION = 40.0f;
    private static final float BUILDING_VISION = 60.0f;

    private static final int C_NEED_EXTRACTOR = 0;
    private static final int C_NEED_FACTORY = 1;
    private static final int C_NEED_TANKS = 2;
    private static final int C_READY_TO_ATTACK = 3;
    private static final int C_NEED_DEFENSE = 4;
    private static final int C_ECONOMY_STALLING = 5;
    private static final int C_NEED_SCOUT = 6;
    private static final int C_NEED_OBELISK = 7;

    public TeamAI(int teamId) {
        this.teamId = teamId;
        float spawnMargin = 40.0f;
        this.enemyBaseLoc = (teamId == 1)
                ? new Vector2f(MapSettings.WORLD_SIZE - spawnMargin, MapSettings.WORLD_SIZE - spawnMargin)
                : new Vector2f(spawnMargin, spawnMargin);
        try {
            // Singularity 内部のハミルトニアン学習エンジンを初期化
            this.singularity = new Singularity(16, 10);

            // イミテーション学習 (IRL): エキスパートの行動を観察させて「定石」を学習
            singularity.observeExpert(C_NEED_EXTRACTOR, new int[]{1}, 5.0f); // 資源不足 -> Extractor
            singularity.observeExpert(C_NEED_FACTORY, new int[]{2}, 5.0f);   // 工場不足 -> Factory
            singularity.observeExpert(C_NEED_TANKS, new int[]{3}, 4.0f);     // 戦力不足 -> Tank
            singularity.observeExpert(C_NEED_DEFENSE, new int[]{7}, 8.0f);   // 防衛必要 -> Laser Tower
            singularity.observeExpert(C_NEED_SCOUT, new int[]{8}, 6.0f);     // 偵察必要 -> Hound
            singularity.observeExpert(C_NEED_OBELISK, new int[]{9}, 7.0f);   // 遠距離火欲 -> Obelisk
            singularity.observeExpert(C_READY_TO_ATTACK, new int[]{5}, 6.0f); // 準備完了 -> 攻撃開始
            singularity.observeExpert(C_ECONOMY_STALLING, new int[]{0}, 4.0f); // リソース枯渇 -> 一時停止

            int[] conditions = {C_NEED_EXTRACTOR, C_NEED_FACTORY, C_NEED_TANKS, C_READY_TO_ATTACK, C_NEED_DEFENSE, C_ECONOMY_STALLING, C_NEED_SCOUT, C_NEED_OBELISK};
            int[] actions = {1, 2, 3, 5, 7, 0, 8, 9}; 
            float[] strengths = {4.0f, 5.0f, 4.0f, 6.0f, 10.0f, 8.0f, 5.0f, 7.0f};
            singularity.registerHamiltonianRules(conditions, actions, strengths);
        } catch (UnsatisfiedLinkError e) { e.printStackTrace(); }
    }

    public void update(GameState gameState, float dt, Map<Integer, Float> teamIncome, Map<Integer, Float> teamDrain) {
        if (!gameState.isStarted || gameState.winnerTeamId != 0 || singularity == null) return;

        decisionTimer += dt;
        if (decisionTimer < nextDecisionTime) return; 
        decisionTimer = 0;

        float temp = singularity.getSystemTemperature();
        nextDecisionTime = 0.35f + (float)Math.random() * 0.3f;

        List<Unit> allUnits = new ArrayList<>(gameState.units.values());
        List<Building> allBuildings = new ArrayList<>(gameState.buildings.values());
        List<Unit> myUnits = allUnits.stream().filter(u -> u.teamId == teamId).collect(Collectors.toList());
        List<Building> myBuildings = allBuildings.stream().filter(b -> b.teamId == teamId).collect(Collectors.toList());
        
        float income = teamIncome.getOrDefault(teamId, 0f);
        float drain = teamDrain.getOrDefault(teamId, 0f);
        float metal = gameState.teamMetal.getOrDefault(teamId, 0f);
        boolean stalling = (metal <= 0 && drain > income);

        long extractorCount = myBuildings.stream().filter(b -> b.type == Building.Type.EXTRACTOR && b.isComplete).count();
        long factoryCount = myBuildings.stream().filter(b -> b.type == Building.Type.FACTORY && b.isComplete).count();
        long towerCount = myBuildings.stream().filter(b -> b.type == Building.Type.LASER_TOWER).count();
        
        List<Unit> myTanks = myUnits.stream().filter(u -> u.type == Unit.Type.TANK).collect(Collectors.toList());
        List<Unit> myHounds = myUnits.stream().filter(u -> u.type == Unit.Type.HOUND).collect(Collectors.toList());
        List<Unit> myObelisks = myUnits.stream().filter(u -> u.type == Unit.Type.OBELISK).collect(Collectors.toList());
        List<Building> incomplete = myBuildings.stream().filter(b -> !b.isComplete).collect(Collectors.toList());

        List<Unit> visibleEnemies = allUnits.stream().filter(u -> u.teamId != teamId)
            .filter(e -> myUnits.stream().anyMatch(u -> u.position.distance(e.position) < (u.type == Unit.Type.HOUND ? 80.0f : UNIT_VISION)) ||
                         myBuildings.stream().anyMatch(b -> b.position.distance(e.position) < BUILDING_VISION))
            .collect(Collectors.toList());

        Building myNexus = myBuildings.stream().filter(b -> b.type == Building.Type.NEXUS).findFirst().orElse(null);
        boolean baseUnderAttack = visibleEnemies.stream().anyMatch(e -> myNexus != null && e.position.distance(myNexus.position) < 60.0f);

        if (myTanks.size() + myObelisks.size() * 2 >= 8) isAssaultMode = true;
        if (myTanks.size() + myObelisks.size() == 0) isAssaultMode = false;

        // 1. Conditions
        List<Integer> activeCons = new ArrayList<>();
        if (stalling) activeCons.add(C_ECONOMY_STALLING);
        if (baseUnderAttack) activeCons.add(C_NEED_DEFENSE);
        if (extractorCount < 8) activeCons.add(C_NEED_EXTRACTOR);
        if (factoryCount < 2) activeCons.add(C_NEED_FACTORY);
        if (myTanks.size() < 15) activeCons.add(C_NEED_TANKS);
        if (myHounds.size() < 2) activeCons.add(C_NEED_SCOUT);
        if (myObelisks.size() < 3 && factoryCount > 0) activeCons.add(C_NEED_OBELISK);
        if (isAssaultMode && !baseUnderAttack) activeCons.add(C_READY_TO_ATTACK);
        singularity.setActiveConditions(activeCons.stream().mapToInt(i -> i).toArray());

        // 2. State Vector (16-D)
        float[] state = new float[16];
        state[0] = metal / 3000f;
        state[1] = myTanks.size() / 30f;
        state[2] = myObelisks.size() / 10f;
        state[3] = myHounds.size() / 5f;
        state[4] = factoryCount / 4f;
        state[5] = extractorCount / 10f;
        state[6] = towerCount / 6f;
        state[7] = baseUnderAttack ? 1f : 0f;
        state[8] = visibleEnemies.size() / 20f;
        state[9] = income / Math.max(1, drain);
        state[10] = stalling ? 1f : 0f;
        state[11] = incomplete.size() / 5f;
        state[12] = isAssaultMode ? 1f : 0f;
        state[13] = temp;
        state[14] = (myNexus != null ? myNexus.hp / myNexus.maxHp : 0f);
        state[15] = (float)Math.random(); // Entropy

        int action = singularity.selectAction(state);
        
        // 3. Execution
        switch (action) {
            case 1 -> { if (incomplete.isEmpty() || income > 60) build(Building.Type.EXTRACTOR, gameState, myUnits, allBuildings, teamId); }
            case 2 -> { if (!stalling && factoryCount < 3) build(Building.Type.FACTORY, gameState, myUnits, allBuildings, teamId); }
            case 3 -> produce(Unit.Type.TANK, myBuildings);
            case 4 -> produce(Unit.Type.CONSTRUCTOR, myBuildings);
            case 5 -> performCombat(myUnits, visibleEnemies, allBuildings, teamId, myNexus);
            case 6 -> assistExistingConstruction(myUnits, incomplete);
            case 7 -> { if (towerCount < 5) build(Building.Type.LASER_TOWER, gameState, myUnits, allBuildings, teamId); }
            case 8 -> produce(Unit.Type.HOUND, myBuildings);
            case 9 -> produce(Unit.Type.OBELISK, myBuildings);
            case 0 -> { /* Idle / Conserve */ }
        }
        if (!incomplete.isEmpty()) assistExistingConstruction(myUnits, incomplete);

        singularity.learn(stalling ? -0.2f : (baseUnderAttack ? -0.1f : 0.15f));
    }

    private void assistExistingConstruction(List<Unit> myUnits, List<Building> incomplete) {
        if (incomplete.isEmpty()) return;
        myUnits.stream().filter(u -> u.type == Unit.Type.CONSTRUCTOR && u.targetBuildingId == null && !u.manualMoveOrder)
            .forEach(u -> {
                Building target = incomplete.stream().min((a,b)->Float.compare(a.position.distance(u.position), b.position.distance(u.position))).orElse(incomplete.get(0));
                u.targetBuildingId = target.id; u.targetPosition.set(target.position);
            });
    }

    private void performCombat(List<Unit> myUnits, List<Unit> visibleEnemies, List<Building> allBuildings, int teamId, Building myNexus) {
        Building enemyNexus = allBuildings.stream().filter(b -> b.type == Building.Type.NEXUS && b.teamId != teamId).findFirst().orElse(null);
        List<Building> enemyBuildings = allBuildings.stream().filter(b -> b.teamId != teamId && b.teamId != 0).collect(Collectors.toList());
        
        for (Unit u : myUnits) {
            if (u.type == Unit.Type.CONSTRUCTOR) continue;

            // Unit specific logic
            if (u.type == Unit.Type.HOUND) {
                // Scout logic: deep penetration or finding targets for Obelisks
                Unit target = visibleEnemies.stream().min((a,b)->Float.compare(a.position.distance(u.position), b.position.distance(u.position))).orElse(null);
                if (target != null) {
                    u.targetUnitId = target.id;
                } else if (isAssaultMode) {
                    u.targetPosition.set(enemyBaseLoc);
                } else if (myNexus != null) {
                    // Patrol around base
                    float angle = (float)(System.currentTimeMillis() / 2000.0 % (Math.PI * 2));
                    u.targetPosition.set(myNexus.position.x + (float)Math.cos(angle)*60, myNexus.position.y + (float)Math.sin(angle)*60);
                }
            } else if (u.type == Unit.Type.OBELISK) {
                // Long range support: stay back and fire at visible targets
                Unit target = visibleEnemies.stream().min((a,b)->Float.compare(a.position.distance(u.position), b.position.distance(u.position))).orElse(null);
                if (target != null) {
                    float dist = u.position.distance(target.position);
                    if (dist < u.attackRange * 0.8f) {
                        // Back away if too close
                        Vector2f away = new Vector2f(u.position).sub(target.position).normalize().mul(15.0f).add(u.position);
                        u.targetPosition.set(away);
                    } else {
                        u.targetUnitId = target.id;
                    }
                } else if (isAssaultMode) {
                    // Move to a position where enemy base is just in range
                    Vector2f movePos = new Vector2f(enemyBaseLoc).sub(u.position).normalize().mul(-u.attackRange * 0.9f).add(enemyBaseLoc);
                    u.targetPosition.set(movePos);
                }
            } else {
                // Tanks and others
                Unit target = visibleEnemies.stream().filter(e -> e.position.distance(u.position) < 50.0f).findFirst().orElse(null);
                if (target != null) { u.targetUnitId = target.id; u.attackTargetBuildingId = null; }
                else if (isAssaultMode) {
                    Building tb = enemyBuildings.stream().filter(b -> b.position.distance(u.position) < 60.0f).findFirst().orElse(enemyNexus);
                    if (tb != null) { u.attackTargetBuildingId = tb.id; u.targetUnitId = null; }
                    else { u.targetPosition.set(enemyBaseLoc); u.targetUnitId = null; u.attackTargetBuildingId = null; }
                } else if (myNexus != null) {
                    u.targetPosition.set(myNexus.position.x + 15, myNexus.position.y + 15);
                }
            }
        }
    }

    private void build(Building.Type type, GameState state, List<Unit> myUnits, List<Building> allBuildings, int teamId) {
        Unit constructor = myUnits.stream().filter(u -> u.type == Unit.Type.CONSTRUCTOR && u.targetBuildingId == null && !u.manualMoveOrder).findFirst().orElse(null);
        if (constructor == null) return;
        Vector2f pos;
        if (type == Building.Type.EXTRACTOR) {
            Building patch = findNearestUnoccupiedPatch(constructor.position, allBuildings);
            if (patch == null) return;
            pos = patch.position;
        } else if (type == Building.Type.LASER_TOWER) {
            Building nexus = allBuildings.stream().filter(b -> b.type == Building.Type.NEXUS && b.teamId == teamId).findFirst().orElse(null);
            if (nexus == null) return;
            float angle = (float)(Math.random() * Math.PI * 2);
            float dist = 25.0f + (float)Math.random() * 15.0f;
            pos = new Vector2f(nexus.position.x + (float)Math.cos(angle)*dist, nexus.position.y + (float)Math.sin(angle)*dist);
        } else {
            Building nexus = allBuildings.stream().filter(b -> b.type == Building.Type.NEXUS && b.teamId == teamId).findFirst().orElse(null);
            pos = new Vector2f(nexus != null ? nexus.position : constructor.position).add((float)Math.random()*60-30, (float)Math.random()*60-30);
        }
        Building b = new Building(state.hashCode() + (int)System.nanoTime(), type, pos.x, pos.y, teamId);
        state.addBuilding(b);
        constructor.targetBuildingId = b.id; constructor.targetPosition.set(b.position);
    }

    private void produce(Unit.Type type, List<Building> myBuildings) {
        for (Building b : myBuildings) { if (b.type == Building.Type.FACTORY && b.isComplete && b.productionQueue.size() < 3) { b.productionQueue.add(type); break; } }
    }

    private Building findNearestUnoccupiedPatch(Vector2f pos, List<Building> allBuildings) {
        Building nearest = null; float minDist = Float.MAX_VALUE;
        for (Building b : allBuildings) {
            if (b.type == Building.Type.METAL_PATCH) {
                boolean occupied = allBuildings.stream().anyMatch(o -> (o.type == Building.Type.EXTRACTOR) && o.position.distance(b.position) < 1.0f);
                if (!occupied) { float d = b.position.distance(pos); if (d < minDist) { minDist = d; nearest = b; } }
            }
        }
        return nearest;
    }

    public void close() { if (singularity != null) singularity.close(); }
}
