package com.lunar_prototype.iron_horizon.common;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.EndPoint;
import com.lunar_prototype.iron_horizon.common.model.Building;
import com.lunar_prototype.iron_horizon.common.model.GameState;
import com.lunar_prototype.iron_horizon.common.model.Projectile;
import com.lunar_prototype.iron_horizon.common.model.Unit;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Network {
    public static final int TCP_PORT = 54555;
    public static final int UDP_PORT = 54777;

    public static void register(EndPoint endPoint) {
        Kryo kryo = endPoint.getKryo();
        kryo.register(LoginRequest.class);
        kryo.register(LoginResponse.class);
        
        // Game State related
        kryo.register(StateUpdate.class);
        kryo.register(GameState.class);
        kryo.register(Unit.class);
        kryo.register(Unit.Type.class);
        kryo.register(UnitData.class);
        kryo.register(Building.class);
        kryo.register(Building.Type.class);
        kryo.register(BuildingData.class);
        kryo.register(Projectile.class);
        kryo.register(ProjectileData.class);
        kryo.register(Vector2f.class);
        kryo.register(ConcurrentHashMap.class);
        kryo.register(HashMap.class);
        kryo.register(ArrayList.class);

        // Commands and Viewport
        kryo.register(ViewportUpdate.class);
        kryo.register(MoveCommand.class);
        kryo.register(BuildCommand.class);
        kryo.register(ProduceCommand.class);
        kryo.register(AttackCommand.class);
        kryo.register(StartGameCommand.class);
        kryo.register(CombatEvent.class);
        kryo.register(CombatEvent.Type.class);
        kryo.register(Task.class);
        kryo.register(Task.Type.class);
    }

    public static class LoginRequest {
        public String username;
    }

    public static class LoginResponse {
        public boolean accepted;
        public String message;
        public int teamId;
    }

    public static class StateUpdate {
        public List<UnitData> units = new ArrayList<>();
        public List<BuildingData> buildings = new ArrayList<>();
        public List<ProjectileData> projectiles = new ArrayList<>();
        public Map<Integer, Float> playerMetal = new HashMap<>();
        public Map<Integer, Float> playerIncome = new HashMap<>();
        public Map<Integer, Float> playerDrain = new HashMap<>();
        public Map<Integer, Float> playerEnergy = new HashMap<>();
        public Map<Integer, Float> playerEnergyCapacity = new HashMap<>();
        public Map<Integer, Float> playerEnergyIncome = new HashMap<>();
        public Map<Integer, Float> playerEnergyDrain = new HashMap<>();
        public Map<Integer, String> teamNames = new HashMap<>();
        public List<CombatEvent> events = new ArrayList<>();
        public List<Integer> removedUnitIds = new ArrayList<>();
        public List<Integer> removedBuildingIds = new ArrayList<>();
        public boolean isFullUpdate = false;
        public boolean isStarted;
        public int winnerTeamId;
    }

    public static class ProjectileData {
        public int id;
        public float x, y, vx, vy;
        public int teamId;
    }
    
    public static class StartGameCommand {}

    public static class CombatEvent {
        public enum Type { ATTACK, EXPLOSION, LASER, OBELISK_BLAST, SHIELD_HIT }
        public Type type;
        public float x, y, tx, ty; // Source and Target pos
    }

    public static class UnitData {
        public int id;
        public Unit.Type type;
        public int teamId;
        public int ownerId;
        public float x, y;
        public float hp, maxHp;
        public float facingDeg;
        public float turretFacingDeg;
        public boolean turretReady;
        public boolean selected;
        public boolean canAttackGround;
        public boolean canAttackAir;
        public float turnSpeed;
        public List<Task> tasks = new ArrayList<>();
    }
    
    public static class AttackCommand {
        public List<Integer> unitIds = new ArrayList<>();
        public Integer targetUnitId = null;
        public Integer targetBuildingId = null;
    }

    public static class BuildingData {
        public int id;
        public Building.Type type;
        public int teamId;
        public int ownerId;
        public float x, y;
        public float hp, maxHp;
        public float buildProgress;
        public float productionProgress;
        public boolean isComplete;
        public float shieldHp;
        public float maxShieldHp;
        public float shieldRadius;
        public List<Unit.Type> productionQueue = new ArrayList<>();
    }

    public static class ViewportUpdate {
        public float centerX, centerY;
        public float width, height;
    }

    public static class MoveCommand {
        public float targetX, targetY;
        public List<Integer> unitIds = new ArrayList<>();
        public Integer targetBuildingId = null;
        public List<Vector2f> waypoints = new ArrayList<>();
        public boolean queue = false;
    }

    public static class BuildCommand {
        public Building.Type buildingType;
        public float x, y;
        public int constructorUnitId;
        public boolean shiftHold;
    }

    public static class ProduceCommand {
        public int factoryId;
        public Unit.Type unitType;
    }

    public static class Task {
        public enum Type { MOVE, BUILD }
        public Type type;
        public float x, y;
        public Building.Type buildType;
        public Integer targetBuildingId;
    }
}
