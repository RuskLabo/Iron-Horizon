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
    private float lastMyTotalHp = 0;
    private float lastEnemyTotalHp = 0;
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

    public TeamAI(int teamId) {
        this.teamId = teamId;
        float spawnMargin = 40.0f;
        this.enemyBaseLoc = (teamId == 1)
                ? new Vector2f(MapSettings.WORLD_SIZE - spawnMargin, MapSettings.WORLD_SIZE - spawnMargin)
                : new Vector2f(spawnMargin, spawnMargin);
        try {
            this.singularity = new Singularity(10, 7);
            int[] conditions = {C_NEED_EXTRACTOR, C_NEED_FACTORY, C_NEED_TANKS, C_READY_TO_ATTACK, C_NEED_DEFENSE, C_ECONOMY_STALLING};
            int[] actions = {1, 2, 3, 5, 5, 0}; 
            float[] strengths = {4.0f, 5.0f, 4.0f, 5.0f, 9.0f, 7.0f};
            singularity.registerHamiltonianRules(conditions, actions, strengths);
        } catch (UnsatisfiedLinkError e) { e.printStackTrace(); }
    }

    public void update(GameState gameState, float dt, Map<Integer, Float> teamIncome, Map<Integer, Float> teamDrain) {
        if (!gameState.isStarted || gameState.winnerTeamId != 0 || singularity == null) return;

        decisionTimer += dt;
        if (decisionTimer < nextDecisionTime) return; 
        decisionTimer = 0;

        float temp = singularity.getSystemTemperature();
        nextDecisionTime = 0.4f + (float)Math.random() * 0.4f;

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
        List<Unit> myTanks = myUnits.stream().filter(u -> u.type == Unit.Type.TANK && !u.manualMoveOrder).collect(Collectors.toList());
        List<Building> incomplete = myBuildings.stream().filter(b -> !b.isComplete).collect(Collectors.toList());

        List<Unit> visibleEnemies = allUnits.stream().filter(u -> u.teamId != teamId)
            .filter(e -> myUnits.stream().anyMatch(u -> u.position.distance(e.position) < UNIT_VISION) ||
                         myBuildings.stream().anyMatch(b -> b.position.distance(e.position) < BUILDING_VISION))
            .collect(Collectors.toList());

        Building myNexus = myBuildings.stream().filter(b -> b.type == Building.Type.NEXUS).findFirst().orElse(null);
        boolean baseUnderAttack = visibleEnemies.stream().anyMatch(e -> myNexus != null && e.position.distance(myNexus.position) < 50.0f);

        // Grouping Logic: Assault mode ON if we have 8+ tanks, OFF if less than 3
        if (myTanks.size() >= 8) isAssaultMode = true;
        if (myTanks.size() < 3) isAssaultMode = false;

        // 1. Conditions
        List<Integer> activeCons = new ArrayList<>();
        if (stalling) activeCons.add(C_ECONOMY_STALLING);
        if (baseUnderAttack) activeCons.add(C_NEED_DEFENSE);
        if (extractorCount < 6) activeCons.add(C_NEED_EXTRACTOR);
        if (extractorCount >= 3 && factoryCount < (1 + (int)(metal / 1000))) activeCons.add(C_NEED_FACTORY);
        if (myTanks.size() < 25) activeCons.add(C_NEED_TANKS);
        if (isAssaultMode && !baseUnderAttack) activeCons.add(C_READY_TO_ATTACK);
        singularity.setActiveConditions(activeCons.stream().mapToInt(i -> i).toArray());

        // 2. State
        float[] state = { metal/2000f, myTanks.size()/40f, factoryCount/5f, baseUnderAttack?1:0, 0, visibleEnemies.size()/20f, income/Math.max(1, drain), stalling?1:0, incomplete.size()/5f, temp };
        int action = singularity.selectAction(state);
        
        System.out.printf("AI [%d] - Metal: %.0f (+%.1f) | Army: %d Mode: %s | Action: %d\n", teamId, metal, income, myTanks.size(), isAssaultMode?"ASSAULT":"PREP", action);

        // 3. Execution
        switch (action) {
            case 1 -> { if (incomplete.isEmpty() || (income > 50 && incomplete.size() < 2)) build(Building.Type.EXTRACTOR, gameState, myUnits, allBuildings, teamId); }
            case 2 -> { if (!stalling && incomplete.isEmpty() && income > 40) build(Building.Type.FACTORY, gameState, myUnits, allBuildings, teamId); }
            case 3 -> { if (!stalling && income > drain + 30) produce(Unit.Type.TANK, myBuildings); }
            case 4 -> { if (!stalling && income > drain + 20) produce(Unit.Type.CONSTRUCTOR, myBuildings); }
            case 5 -> performCombat(myTanks, visibleEnemies, allBuildings, teamId, myNexus);
            case 6 -> assistExistingConstruction(myUnits, incomplete);
        }
        if (!incomplete.isEmpty()) assistExistingConstruction(myUnits, incomplete);

        singularity.learn(stalling ? -0.1f : 0.1f);
    }

    private void assistExistingConstruction(List<Unit> myUnits, List<Building> incomplete) {
        if (incomplete.isEmpty()) return;
        myUnits.stream().filter(u -> u.type == Unit.Type.CONSTRUCTOR && u.targetBuildingId == null && !u.manualMoveOrder)
            .forEach(u -> {
                Building target = incomplete.stream().min((a,b)->Float.compare(a.position.distance(u.position), b.position.distance(u.position))).orElse(incomplete.get(0));
                u.targetBuildingId = target.id; u.targetPosition.set(target.position);
            });
    }

    private void performCombat(List<Unit> myTanks, List<Unit> visibleEnemies, List<Building> allBuildings, int teamId, Building myNexus) {
        Building enemyNexus = allBuildings.stream().filter(b -> b.type == Building.Type.NEXUS && b.teamId != teamId).findFirst().orElse(null);
        List<Building> enemyBuildings = allBuildings.stream().filter(b -> b.teamId != teamId && b.teamId != 0).collect(Collectors.toList());
        
        for (Unit u : myTanks) {
            // Immediate threats
            Unit target = visibleEnemies.stream().filter(e -> e.position.distance(u.position) < 50.0f).findFirst().orElse(null);
            if (target != null) { u.targetUnitId = target.id; u.attackTargetBuildingId = null; }
            else if (isAssaultMode) {
                // Seek and destroy buildings
                Building tb = enemyBuildings.stream().filter(b -> b.position.distance(u.position) < 60.0f).findFirst().orElse(enemyNexus);
                if (tb != null) { u.attackTargetBuildingId = tb.id; u.targetUnitId = null; }
                else { u.targetPosition.set(enemyBaseLoc); u.targetUnitId = null; u.attackTargetBuildingId = null; }
            } else {
                // Group up near Nexus
                if (myNexus != null) u.targetPosition.set(myNexus.position.x + 10, myNexus.position.y + 10);
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
