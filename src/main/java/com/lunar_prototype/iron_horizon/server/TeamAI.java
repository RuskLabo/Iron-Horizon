package com.lunar_prototype.iron_horizon.server;

import com.lunar_prototype.dark_singularity_api.Singularity;
import com.lunar_prototype.iron_horizon.common.MapSettings;
import com.lunar_prototype.iron_horizon.common.model.Building;
import com.lunar_prototype.iron_horizon.common.model.GameState;
import com.lunar_prototype.iron_horizon.common.model.Unit;
import com.lunar_prototype.iron_horizon.ServerLauncher;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * TeamAI - 二ドメイン並列型 AI
 *
 * <p>経済ドメイン（economySingularity）と戦闘ドメイン（combatSingularity）を
 * 独立した Singularity インスタンスで管理することで、建設・生産・戦闘を
 * 毎ティック並列実行する。</p>
 *
 * <p>Singularity APIは純粋な JNI バインディングであるため、インスタンスを
 * 複数生成しても互いに干渉しない。</p>
 */
public class TeamAI {

    // ─────────────────────────────────────────────
    //  定数
    // ─────────────────────────────────────────────
    private static final float UNIT_VISION     = 40.0f;
    private static final float BUILDING_VISION = 60.0f;
    private static final float BASE_THREAT_RADIUS = 60.0f;

    // ── 経済ドメイン アクション ──
    private static final int EA_IDLE             = 0;
    private static final int EA_BUILD_EXTRACTOR  = 1;
    private static final int EA_BUILD_FACTORY    = 2;
    private static final int EA_BUILD_TOWER      = 3;
    private static final int EA_PRODUCE_TANK     = 4;
    private static final int EA_PRODUCE_HOUND    = 5;
    private static final int EA_PRODUCE_OBELISK  = 6;
    private static final int EA_ASSIST_BUILD     = 7;
    private static final int EA_BUILD_SOLAR      = 8;
    private static final int EA_BUILD_SHIELD     = 9;

    // ── 経済ドメイン 条件 ──
    private static final int EC_STALLING         = 0;
    private static final int EC_NEED_EXTRACTOR   = 1;
    private static final int EC_NEED_FACTORY     = 2;
    private static final int EC_NEED_DEFENSE_TOWER = 3;
    private static final int EC_NEED_TANKS       = 4;
    private static final int EC_NEED_SCOUT       = 5;
    private static final int EC_NEED_OBELISK     = 6;
    private static final int EC_HAS_INCOMPLETE   = 7;
    private static final int EC_NEED_ENERGY      = 8;
    private static final int EC_NEED_SHIELD      = 9;

    // ── 戦闘ドメイン アクション ──
    private static final int CA_HOLD_POSITION    = 0;
    private static final int CA_SCOUT            = 1;
    private static final int CA_RALLY            = 2;
    private static final int CA_ASSAULT          = 3;
    private static final int CA_ATTACK_NEXUS     = 4;
    private static final int CA_ATTACK_ECONOMY   = 5;
    private static final int CA_OBELISK_FORM     = 6;
    private static final int CA_RETREAT          = 7;

    // ── 戦闘ドメイン 条件 ──
    private static final int CC_BASE_UNDER_ATTACK = 0;
    private static final int CC_ASSAULT_READY     = 1;
    private static final int CC_HAS_OBELISKS      = 2;
    private static final int CC_ENEMY_VISIBLE     = 3;
    private static final int CC_NEXUS_CRITICAL    = 4;
    private static final int CC_FORCE_SUPERIOR    = 5;

    // ─────────────────────────────────────────────
    //  フィールド
    // ─────────────────────────────────────────────
    private final int teamId;
    private float decisionTimer  = 0;
    private float nextDecisionTime = 1.0f;

    // 並列 Singularity インスタンス
    private Singularity economySingularity;
    private Singularity combatSingularity;

    private Vector2f enemyBaseLoc;
    private boolean  isAssaultMode = false;

    // 前ティック状態（報酬計算用）
    private long  prevExtractorCount = 0;
    private float prevNexusHp        = Float.MAX_VALUE;
    private int   prevEnemyCount     = 0;

    // ID生成（idCounter と衝突しない大きなオフセットを使用）
    // ServerLauncher.idCounter は 10000 から始まるため、AI は 2_000_000_000 からカウントダウン
    private static final AtomicInteger aiIdCounter = new AtomicInteger(Integer.MAX_VALUE);

    // ─────────────────────────────────────────────
    //  コンストラクタ
    // ─────────────────────────────────────────────
    public TeamAI(int teamId) {
        this.teamId = teamId;
        float spawnMargin = 40.0f;
        this.enemyBaseLoc = (teamId == 1)
                ? new Vector2f(MapSettings.WORLD_SIZE - spawnMargin, MapSettings.WORLD_SIZE - spawnMargin)
                : new Vector2f(spawnMargin, spawnMargin);
        try {
            initEconomyDomain();
            initCombatDomain();
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
    }

    /** 経済ドメイン Singularity の初期化 */
    private void initEconomyDomain() {
        economySingularity = new Singularity(16, 8);

        // エキスパート観測（条件 → 推奨アクション）
        // エクストラクター柘度を最高にして経済基盤を強制
        economySingularity.observeExpert(EC_NEED_EXTRACTOR,     new int[]{EA_BUILD_EXTRACTOR}, 9.0f);
        economySingularity.observeExpert(EC_NEED_FACTORY,       new int[]{EA_BUILD_FACTORY},   6.0f);
        economySingularity.observeExpert(EC_NEED_TANKS,         new int[]{EA_PRODUCE_TANK},    4.0f);
        // タワーは守備異常時のみ権陣を与える（経済強度より低く設定）
        economySingularity.observeExpert(EC_NEED_DEFENSE_TOWER, new int[]{EA_BUILD_TOWER},     4.0f);
        economySingularity.observeExpert(EC_NEED_SCOUT,         new int[]{EA_PRODUCE_HOUND},   5.0f);
        economySingularity.observeExpert(EC_NEED_OBELISK,       new int[]{EA_PRODUCE_OBELISK}, 6.0f);
        economySingularity.observeExpert(EC_STALLING,           new int[]{EA_IDLE},            8.0f);
        economySingularity.observeExpert(EC_HAS_INCOMPLETE,     new int[]{EA_ASSIST_BUILD},    6.0f);
        economySingularity.observeExpert(EC_NEED_ENERGY,        new int[]{EA_BUILD_SOLAR},     9.5f);
        economySingularity.observeExpert(EC_NEED_SHIELD,        new int[]{EA_BUILD_SHIELD},    5.5f);

        int[]   conds  = {EC_STALLING, EC_NEED_EXTRACTOR, EC_NEED_FACTORY, EC_NEED_DEFENSE_TOWER,
                          EC_NEED_TANKS, EC_NEED_SCOUT, EC_NEED_OBELISK, EC_HAS_INCOMPLETE, EC_NEED_ENERGY, EC_NEED_SHIELD};
        int[]   acts   = {EA_IDLE, EA_BUILD_EXTRACTOR, EA_BUILD_FACTORY, EA_BUILD_TOWER,
                          EA_PRODUCE_TANK, EA_PRODUCE_HOUND, EA_PRODUCE_OBELISK, EA_ASSIST_BUILD, EA_BUILD_SOLAR, EA_BUILD_SHIELD};
        float[] str    = {8.0f, 9.0f, 6.0f, 4.0f, 4.0f, 5.0f, 6.0f, 6.0f, 9.5f, 5.5f};
        economySingularity.registerHamiltonianRules(conds, acts, str);
    }

    /** 戦闘ドメイン Singularity の初期化 */
    private void initCombatDomain() {
        combatSingularity = new Singularity(16, 8);

        combatSingularity.observeExpert(CC_BASE_UNDER_ATTACK, new int[]{CA_RETREAT, CA_HOLD_POSITION}, 10.0f);
        combatSingularity.observeExpert(CC_ASSAULT_READY,     new int[]{CA_ASSAULT},                   7.0f);
        combatSingularity.observeExpert(CC_HAS_OBELISKS,      new int[]{CA_OBELISK_FORM},              6.0f);
        combatSingularity.observeExpert(CC_ENEMY_VISIBLE,     new int[]{CA_ATTACK_ECONOMY},            5.0f);
        combatSingularity.observeExpert(CC_NEXUS_CRITICAL,    new int[]{CA_RETREAT},                   10.0f);
        combatSingularity.observeExpert(CC_FORCE_SUPERIOR,    new int[]{CA_ATTACK_NEXUS},              8.0f);

        int[]   conds = {CC_BASE_UNDER_ATTACK, CC_ASSAULT_READY, CC_HAS_OBELISKS,
                         CC_ENEMY_VISIBLE, CC_NEXUS_CRITICAL, CC_FORCE_SUPERIOR};
        int[]   acts  = {CA_RETREAT, CA_ASSAULT, CA_OBELISK_FORM, CA_ATTACK_ECONOMY, CA_RETREAT, CA_ATTACK_NEXUS};
        float[] str   = {10.0f, 7.0f, 6.0f, 5.0f, 10.0f, 8.0f};
        combatSingularity.registerHamiltonianRules(conds, acts, str);
    }

    // ─────────────────────────────────────────────
    //  メインループ
    // ─────────────────────────────────────────────
    public void update(GameState gameState, float dt,
                       Map<Integer, Float> playerIncome,
                       Map<Integer, Float> playerDrain) {

        if (!gameState.isStarted || gameState.winnerTeamId != 0
                || economySingularity == null || combatSingularity == null) return;

        decisionTimer += dt;
        if (decisionTimer < nextDecisionTime) return;
        decisionTimer = 0;
        // 人間らしい反応速度に変更 (0.3-0.55s -> 0.8-1.5s)
        nextDecisionTime = 0.80f + (float) Math.random() * 0.70f;

        // ── 状態収集 ──────────────────────────────────────────────
        List<Unit>     allUnits    = new ArrayList<>(gameState.units.values());
        List<Building> allBuildings= new ArrayList<>(gameState.buildings.values());
        List<Unit>     myUnits     = allUnits.stream().filter(u -> u.teamId == teamId).collect(Collectors.toList());
        List<Building> myBuildings = allBuildings.stream().filter(b -> b.teamId == teamId).collect(Collectors.toList());

        int   pid    = ServerLauncher.AI_PLAYER_ID;
        float income = playerIncome.getOrDefault(pid, 0f);
        float drain  = playerDrain.getOrDefault(pid, 0f);
        float metal  = gameState.getMetal(pid);
        boolean stalling = (metal <= 0 && drain > income);

        long extractorCount  = myBuildings.stream().filter(b -> b.type == Building.Type.EXTRACTOR && b.isComplete).count();
        long factoryCount    = myBuildings.stream().filter(b -> b.type == Building.Type.FACTORY   && b.isComplete).count();
        long towerCount      = myBuildings.stream().filter(b -> b.type == Building.Type.LASER_TOWER).count();
        long solarCount      = myBuildings.stream().filter(b -> b.type == Building.Type.SOLAR_COLLECTOR).count();
        long shieldCount     = myBuildings.stream().filter(b -> b.type == Building.Type.SHIELD_GENERATOR).count();
        // 建設中エクストラクターの数（同パッチへの2重配置防止のため）
        long pendingExtractors = myBuildings.stream().filter(b -> b.type == Building.Type.EXTRACTOR && !b.isComplete).count();

        List<Unit> myTanks   = myUnits.stream().filter(u -> u.type == Unit.Type.TANK).collect(Collectors.toList());
        List<Unit> myHounds  = myUnits.stream().filter(u -> u.type == Unit.Type.HOUND).collect(Collectors.toList());
        List<Unit> myObelisks= myUnits.stream().filter(u -> u.type == Unit.Type.OBELISK).collect(Collectors.toList());
        List<Building> incomplete = myBuildings.stream().filter(b -> !b.isComplete).collect(Collectors.toList());

        // 視野内の敵
        List<Unit> visibleEnemies = allUnits.stream()
                .filter(u -> u.teamId != teamId)
                .filter(e -> myUnits.stream().anyMatch(u -> u.position.distance(e.position)
                                < (u.type == Unit.Type.HOUND ? 80.0f : UNIT_VISION))
                          || myBuildings.stream().anyMatch(b -> b.position.distance(e.position) < BUILDING_VISION))
                .collect(Collectors.toList());

        Building myNexus      = myBuildings.stream().filter(b -> b.type == Building.Type.NEXUS).findFirst().orElse(null);
        boolean baseUnderAttack = visibleEnemies.stream()
                .anyMatch(e -> myNexus != null && e.position.distance(myNexus.position) < BASE_THREAT_RADIUS);

        // temperature（両ドメイン共通参照）
        float ecoTemp    = economySingularity.getSystemTemperature();
        float combatTemp = combatSingularity.getSystemTemperature();

        // ── 戦力評価 ──────────────────────────────────────────────
        int totalCombatUnits = myTanks.size() + myObelisks.size() * 2 + myHounds.size();
        // 適応的アサルト閾値: temperature が高い（学習が安定）ほど少ない兵力で攻撃移行
        float assaultThreshold = 8.0f - Math.min(combatTemp, 1.0f) * 4.0f; // 4〜8
        if (totalCombatUnits >= assaultThreshold) isAssaultMode = true;
        if (myTanks.isEmpty() && myObelisks.isEmpty()) isAssaultMode = false;

        // ── 敵建物の情報 ─────────────────────────────────────────
        List<Building> enemyBuildings = allBuildings.stream()
                .filter(b -> b.teamId != teamId && b.teamId != 0).collect(Collectors.toList());
        long enemyFactoryCount = enemyBuildings.stream().filter(b -> b.type == Building.Type.FACTORY && b.isComplete).count();

        // ─────────────────────────────────────────────────────────
        //  経済ドメイン処理
        // ─────────────────────────────────────────────────────────
        {
            List<Integer> activeCons = new ArrayList<>();
            if (stalling)                                                    activeCons.add(EC_STALLING);
            // 完成済み + 建設中の合計が目標に達していない場合のみ建設要求
            if (extractorCount + pendingExtractors < 8)                      activeCons.add(EC_NEED_EXTRACTOR);
            if (factoryCount < 3 && !stalling)                               activeCons.add(EC_NEED_FACTORY);
            // タワー建設は「エクストラクター3基以上」かつ「守備迫切」の場合のみ発動（序盤からのタワー幽霊防止）
            if (extractorCount >= 3 && towerCount < 5 && (baseUnderAttack || visibleEnemies.size() > 2))
                                                                             activeCons.add(EC_NEED_DEFENSE_TOWER);
            if (myTanks.size() < 15)                                         activeCons.add(EC_NEED_TANKS);
            if (myHounds.size() < 2)                                         activeCons.add(EC_NEED_SCOUT);
            if (myObelisks.size() < 3 && factoryCount > 0)                  activeCons.add(EC_NEED_OBELISK);
            if (!incomplete.isEmpty())                                       activeCons.add(EC_HAS_INCOMPLETE);
            
            float currentEnergy = gameState.getEnergy(pid);
            if (currentEnergy < 300 || solarCount < extractorCount)          activeCons.add(EC_NEED_ENERGY);
            if (shieldCount < 2 && baseUnderAttack)                          activeCons.add(EC_NEED_SHIELD);
            
            economySingularity.setActiveConditions(activeCons.stream().mapToInt(i -> i).toArray());

            float[] eState = buildEconomyState(metal, myTanks, myObelisks, myHounds,
                    factoryCount, extractorCount, towerCount,
                    baseUnderAttack, visibleEnemies, income, drain, stalling,
                    incomplete, isAssaultMode, ecoTemp, myNexus, enemyFactoryCount);
            int eAction = economySingularity.selectAction(eState);
            // allBuildings ではなく gameState を直接渡し、最新の建物状態を参照させる
            executeEconomyAction(eAction, gameState, myUnits, myBuildings,
                    stalling, factoryCount, towerCount, incomplete, shieldCount);

            // 経済ドメイン報酬
            float ecoReward = 0f;
            ecoReward += (income > drain * 1.2f) ? 0.1f : 0f;
            ecoReward -= stalling ? 0.3f : 0f;
            ecoReward += (extractorCount > prevExtractorCount) ? 0.15f : 0f;
            ecoReward -= baseUnderAttack ? 0.05f : 0f;
            economySingularity.learn(ecoReward);
        }

        // ─────────────────────────────────────────────────────────
        //  戦闘ドメイン処理
        // ─────────────────────────────────────────────────────────
        {
            List<Integer> combatCons = new ArrayList<>();
            if (baseUnderAttack)         combatCons.add(CC_BASE_UNDER_ATTACK);
            if (isAssaultMode)           combatCons.add(CC_ASSAULT_READY);
            if (!myObelisks.isEmpty())   combatCons.add(CC_HAS_OBELISKS);
            if (!visibleEnemies.isEmpty()) combatCons.add(CC_ENEMY_VISIBLE);
            if (myNexus != null && myNexus.hp < myNexus.maxHp * 0.3f) combatCons.add(CC_NEXUS_CRITICAL);
            if (totalCombatUnits > visibleEnemies.size() * 1.5f && isAssaultMode) combatCons.add(CC_FORCE_SUPERIOR);
            combatSingularity.setActiveConditions(combatCons.stream().mapToInt(i -> i).toArray());

            float[] cState = buildCombatState(myTanks, myObelisks, myHounds, visibleEnemies,
                    myNexus, isAssaultMode, combatTemp, allBuildings);
            int cAction = combatSingularity.selectAction(cState);
            executeCombatAction(cAction, myUnits, myObelisks, myHounds,
                    visibleEnemies, enemyBuildings, allBuildings, myNexus);

            // 戦闘ドメイン報酬
            int currentEnemyCount = visibleEnemies.size();
            float combatReward = 0f;
            combatReward += (currentEnemyCount < prevEnemyCount) ? (prevEnemyCount - currentEnemyCount) * 0.05f : 0f;
            combatReward -= baseUnderAttack ? 0.2f : 0f;
            combatReward -= (myNexus != null && myNexus.hp < prevNexusHp) ? 0.15f : 0f;
            combatReward += isAssaultMode ? 0.05f : 0f;
            combatSingularity.learn(combatReward);
        }

        // ── 未完成建物への自動建設補助（常時） ───────────────────
        if (!incomplete.isEmpty()) {
            assistExistingConstruction(myUnits, incomplete);
        }

        // ── 前ティック状態の更新 ─────────────────────────────────
        prevExtractorCount = extractorCount;
        prevNexusHp        = (myNexus != null) ? myNexus.hp : Float.MAX_VALUE;
        prevEnemyCount     = visibleEnemies.size();
    }

    // ─────────────────────────────────────────────
    //  状態ベクトル構築
    // ─────────────────────────────────────────────

    /** 経済ドメイン 16 次元状態ベクトル */
    private float[] buildEconomyState(float metal,
            List<Unit> tanks, List<Unit> obelisks, List<Unit> hounds,
            long factories, long extractors, long towers,
            boolean baseUnderAttack, List<Unit> visibleEnemies,
            float income, float drain, boolean stalling,
            List<Building> incomplete, boolean isAssaultMode,
            float temp, Building myNexus, long enemyFactories) {
        float[] s = new float[16];
        s[0]  = metal / 3000f;
        s[1]  = tanks.size() / 30f;
        s[2]  = obelisks.size() / 5f;
        s[3]  = hounds.size() / 5f;
        s[4]  = factories / 4f;
        s[5]  = extractors / 10f;
        s[6]  = towers / 6f;
        s[7]  = baseUnderAttack ? 1f : 0f;
        s[8]  = visibleEnemies.size() / 20f;
        s[9]  = income / Math.max(1f, drain);
        s[10] = stalling ? 1f : 0f;
        s[11] = incomplete.size() / 5f;
        s[12] = isAssaultMode ? 1f : 0f;
        s[13] = temp;
        s[14] = (myNexus != null) ? myNexus.hp / myNexus.maxHp : 0f;
        s[15] = enemyFactories / 4f;
        return s;
    }

    /** 戦闘ドメイン 16 次元状態ベクトル */
    private float[] buildCombatState(List<Unit> tanks, List<Unit> obelisks, List<Unit> hounds,
            List<Unit> visibleEnemies, Building myNexus, boolean isAssaultMode,
            float temp, List<Building> allBuildings) {
        float[] s = new float[16];
        s[0]  = (tanks.size() + obelisks.size() * 2 + hounds.size()) / 30f;
        s[1]  = visibleEnemies.size() / 20f;
        s[2]  = visibleEnemies.stream()
                    .filter(e -> myNexus != null && e.position.distance(myNexus.position) < BASE_THREAT_RADIUS)
                    .count() / 10f;
        s[3]  = (myNexus != null) ? myNexus.hp / myNexus.maxHp : 0f;
        s[4]  = isAssaultMode ? 1f : 0f;
        s[5]  = obelisks.size() / 5f;
        s[6]  = hounds.size() / 5f;
        // 敵 Nexus の HP（視野内で見えていれば実値、そうでなければ推定1.0）
        Building enemyNexus = allBuildings.stream()
                .filter(b -> b.type == Building.Type.NEXUS && b.teamId != teamId && b.teamId != 0)
                .findFirst().orElse(null);
        s[7]  = (enemyNexus != null) ? enemyNexus.hp / enemyNexus.maxHp : 1.0f;
        s[8]  = temp;
        // 敵方向ベクトル（正規化）
        Vector2f toEnemy = new Vector2f(enemyBaseLoc);
        if (myNexus != null) toEnemy.sub(myNexus.position).normalize();
        s[9]  = (toEnemy.x + 1f) * 0.5f;
        s[10] = (toEnemy.y + 1f) * 0.5f;
        s[11] = tanks.size() / 20f;
        // 近距離脅威（Nexus 30 以内の敵）
        s[12] = visibleEnemies.stream()
                    .filter(e -> myNexus != null && e.position.distance(myNexus.position) < 30f)
                    .count() / 5f;
        s[13] = obelisks.isEmpty() ? 0f : 1f;
        s[14] = (float) Math.random() * 0.05f; // 微小ノイズ（探索促進）
        s[15] = 0f;
        return s;
    }

    // ─────────────────────────────────────────────
    //  経済ドメイン アクション実行
    // ─────────────────────────────────────────────
    private void executeEconomyAction(int action, GameState gameState,
            List<Unit> myUnits, List<Building> myBuildings,
            boolean stalling, long factoryCount, long towerCount,
            List<Building> incomplete, long shieldCount) {
        switch (action) {
            // gameState.buildings を直接渡して最新状態を参照（スナップショット問題の解消）
            case EA_BUILD_EXTRACTOR ->
                    build(Building.Type.EXTRACTOR, gameState, myUnits, teamId);
            case EA_BUILD_FACTORY -> {
                if (!stalling && factoryCount < 4)
                    build(Building.Type.FACTORY, gameState, myUnits, teamId);
            }
            case EA_BUILD_TOWER -> {
                if (towerCount < 6)
                    build(Building.Type.LASER_TOWER, gameState, myUnits, teamId);
            }
            case EA_PRODUCE_TANK    -> produceAll(Unit.Type.TANK,   myBuildings);
            case EA_PRODUCE_HOUND   -> produceAll(Unit.Type.HOUND,  myBuildings);
            case EA_PRODUCE_OBELISK -> produceAll(Unit.Type.OBELISK, myBuildings);
            case EA_ASSIST_BUILD    -> assistExistingConstruction(myUnits, incomplete);
            case EA_BUILD_SOLAR     -> build(Building.Type.SOLAR_COLLECTOR, gameState, myUnits, teamId);
            case EA_BUILD_SHIELD    -> {
                if (shieldCount < 4) build(Building.Type.SHIELD_GENERATOR, gameState, myUnits, teamId);
            }
            // EA_IDLE: 何もしない（メタル節約）
        }
    }

    // ─────────────────────────────────────────────
    //  戦闘ドメイン アクション実行
    // ─────────────────────────────────────────────
    private void executeCombatAction(int action,
            List<Unit> myUnits, List<Unit> myObelisks, List<Unit> myHounds,
            List<Unit> visibleEnemies, List<Building> enemyBuildings,
            List<Building> allBuildings, Building myNexus) {

        Building enemyNexus = allBuildings.stream()
                .filter(b -> b.type == Building.Type.NEXUS && b.teamId != teamId && b.teamId != 0)
                .findFirst().orElse(null);

        switch (action) {
            case CA_HOLD_POSITION -> holdPosition(myUnits, myNexus, visibleEnemies);
            case CA_SCOUT         -> performScout(myHounds, myUnits, visibleEnemies);
            case CA_RALLY         -> rallyNearNexus(myUnits, myNexus);
            case CA_ASSAULT       -> performAssault(myUnits, myObelisks, visibleEnemies, enemyBuildings, enemyNexus);
            case CA_ATTACK_NEXUS  -> attackTarget(myUnits, myObelisks, enemyNexus, visibleEnemies);
            case CA_ATTACK_ECONOMY-> attackEconomy(myUnits, myObelisks, enemyBuildings, visibleEnemies);
            case CA_OBELISK_FORM  -> obeliskFormation(myObelisks, myHounds, visibleEnemies, enemyBuildings, enemyNexus);
            case CA_RETREAT       -> retreat(myUnits, myNexus);
        }
    }

    // ─────────────────────────────────────────────
    //  戦闘 サブルーチン
    // ─────────────────────────────────────────────

    /** 防衛ポジションを維持しつつ近敵を攻撃 */
    private void holdPosition(List<Unit> myUnits, Building myNexus, List<Unit> visibleEnemies) {
        for (Unit u : myUnits) {
            if (u.type == Unit.Type.CONSTRUCTOR) continue;
            Unit nearEnemy = closestUnit(u.position, visibleEnemies);
            if (nearEnemy != null && u.position.distance(nearEnemy.position) < u.attackRange * 1.5f) {
                u.targetUnitId = nearEnemy.id;
            } else if (myNexus != null) {
                // Nexus を中心に散開して防衛
                float angle = (float) (teamId * Math.PI + u.id * 0.5f);
                u.targetPosition.set(
                        myNexus.position.x + (float) Math.cos(angle) * 20f,
                        myNexus.position.y + (float) Math.sin(angle) * 20f);
                u.targetUnitId = null;
            }
        }
    }

    /** ハウンドをスカウトに送る */
    private void performScout(List<Unit> myHounds, List<Unit> myUnits, List<Unit> visibleEnemies) {
        for (Unit h : myHounds) {
            Unit target = closestUnit(h.position, visibleEnemies);
            if (target != null) h.targetUnitId = target.id;
            else h.targetPosition.set(enemyBaseLoc);
        }
        // 戦闘ユニットはその場維持
        for (Unit u : myUnits) {
            if (u.type == Unit.Type.HOUND || u.type == Unit.Type.CONSTRUCTOR) continue;
            if (u.targetUnitId == null) u.targetUnitId = null; // 変更なし
        }
    }

    /** Nexus 付近に集結 */
    private void rallyNearNexus(List<Unit> myUnits, Building myNexus) {
        if (myNexus == null) return;
        for (Unit u : myUnits) {
            if (u.type == Unit.Type.CONSTRUCTOR) continue;
            u.targetUnitId = null;
            u.attackTargetBuildingId = null;
            float angle = (float) (u.id * 0.7f);
            u.targetPosition.set(
                    myNexus.position.x + (float) Math.cos(angle) * 30f,
                    myNexus.position.y + (float) Math.sin(angle) * 30f);
        }
    }

    /** 全軍アサルト（優先順位付き目標選択） */
    private void performAssault(List<Unit> myUnits, List<Unit> myObelisks,
            List<Unit> visibleEnemies, List<Building> enemyBuildings, Building enemyNexus) {
        // オベリスクはフォーメーションを優先
        obeliskFormation(myObelisks, List.of(), visibleEnemies, enemyBuildings, enemyNexus);
        // その他の戦闘ユニット
        for (Unit u : myUnits) {
            if (u.type == Unit.Type.CONSTRUCTOR || u.type == Unit.Type.OBELISK) continue;
            Unit nearEnemy = closestUnit(u.position, visibleEnemies);
            if (nearEnemy != null && u.position.distance(nearEnemy.position) < 55f) {
                u.targetUnitId = nearEnemy.id;
                u.attackTargetBuildingId = null;
            } else {
                Building tb = pickPriorityTarget(u.position, enemyBuildings, enemyNexus);
                if (tb != null) { u.attackTargetBuildingId = tb.id; u.targetUnitId = null; }
                else { u.targetPosition.set(enemyBaseLoc); u.targetUnitId = null; }
            }
        }
    }

    /** 敵 Nexus を直接攻撃 */
    private void attackTarget(List<Unit> myUnits, List<Unit> myObelisks,
            Building target, List<Unit> visibleEnemies) {
        obeliskFormation(myObelisks, List.of(), visibleEnemies, target != null ? List.of(target) : List.of(), target);
        for (Unit u : myUnits) {
            if (u.type == Unit.Type.CONSTRUCTOR || u.type == Unit.Type.OBELISK) continue;
            Unit nearEnemy = closestUnit(u.position, visibleEnemies);
            if (nearEnemy != null && u.position.distance(nearEnemy.position) < 40f) {
                u.targetUnitId = nearEnemy.id;
            } else if (target != null) {
                u.attackTargetBuildingId = target.id;
                u.targetUnitId = null;
            } else {
                u.targetPosition.set(enemyBaseLoc);
            }
        }
    }

    /** 敵経済施設（ファクトリー→エクストラクター→タワー→Nexus）を攻撃 */
    private void attackEconomy(List<Unit> myUnits, List<Unit> myObelisks,
            List<Building> enemyBuildings, List<Unit> visibleEnemies) {
        Building ecoTarget = pickEconomyTarget(myUnits.isEmpty() ? null :
                myUnits.get(0).position, enemyBuildings);
        obeliskFormation(myObelisks, List.of(), visibleEnemies,
                ecoTarget != null ? List.of(ecoTarget) : enemyBuildings, null);
        for (Unit u : myUnits) {
            if (u.type == Unit.Type.CONSTRUCTOR || u.type == Unit.Type.OBELISK) continue;
            Unit nearEnemy = closestUnit(u.position, visibleEnemies);
            if (nearEnemy != null && u.position.distance(nearEnemy.position) < 45f) {
                u.targetUnitId = nearEnemy.id;
            } else if (ecoTarget != null) {
                u.attackTargetBuildingId = ecoTarget.id;
                u.targetUnitId = null;
            } else {
                u.targetPosition.set(enemyBaseLoc);
            }
        }
    }

    /**
     * オベリスク フォーメーション
     *
     * <p>ハウンドがスポッターとして敵をビジョン内に捉えている場合、
     * オベリスクは射程外のポジションから間接的に砲撃できる。
     * オベリスクは扇状に展開してクロスファイアを形成する。</p>
     */
    private void obeliskFormation(List<Unit> myObelisks, List<Unit> myHounds,
            List<Unit> visibleEnemies, List<Building> enemyBuildings, Building enemyNexus) {
        if (myObelisks.isEmpty()) return;

        // 砲撃目標の決定（ユニット > 建物 > Nexus > 拠点方向）
        Vector2f aimPoint = null;
        Unit unitTarget = null;
        Building bldTarget = null;

        if (!visibleEnemies.isEmpty()) {
            unitTarget = closestUnit(enemyBaseLoc, visibleEnemies);
            if (unitTarget != null) aimPoint = new Vector2f(unitTarget.position);
        }
        if (aimPoint == null && !enemyBuildings.isEmpty()) {
            bldTarget = pickEconomyTarget(enemyBaseLoc, enemyBuildings);
            if (bldTarget != null) aimPoint = new Vector2f(bldTarget.position);
        }
        if (aimPoint == null && enemyNexus != null) aimPoint = new Vector2f(enemyNexus.position);
        if (aimPoint == null) aimPoint = new Vector2f(enemyBaseLoc);

        final Vector2f finalAimPoint = aimPoint;
        final Unit     finalUnitTarget = unitTarget;
        final Building finalBldTarget  = bldTarget;

        // 各オベリスクを扇状に配置して射程外から砲撃
        int n = myObelisks.size();
        for (int i = 0; i < n; i++) {
            Unit ob = myObelisks.get(i);
            // 完璧すぎる配置を緩和するためのジッター (±2.5°程度の揺らぎ)
            float jitter = (float) ((Math.random() - 0.5) * (Math.PI / 36.0f));
            float spreadAngle = (float) ((i - n / 2.0f) * (Math.PI / 8.0f)) + jitter;
            
            Vector2f toAim = new Vector2f(finalAimPoint).sub(ob.position);
            float dist = toAim.length();
            // 最適距離にもジッターを加える (80-90% の範囲)
            float optimalDist = ob.attackRange * (0.80f + (float) Math.random() * 0.10f); 

            if (dist < optimalDist * 0.6f) {
                // 近すぎる → 後退
                Vector2f retreat = new Vector2f(ob.position).sub(finalAimPoint).normalize().mul(15f).add(ob.position);
                ob.targetPosition.set(retreat);
                ob.targetUnitId = null;
            } else if (dist <= ob.attackRange) {
                // 射程内 → 攻撃（扇の位置に微調整しながら）
                if (finalUnitTarget != null) {
                    ob.targetUnitId = finalUnitTarget.id;
                } else if (finalBldTarget != null) {
                    ob.attackTargetBuildingId = finalBldTarget.id;
                    ob.targetUnitId = null;
                }
                // 扇状の最適ポジションへ少し移動
                if (toAim.length() > 0.01f) {
                    float angle = (float) Math.atan2(toAim.y, toAim.x) + spreadAngle;
                    Vector2f formPos = new Vector2f(
                            finalAimPoint.x - (float) Math.cos(angle) * optimalDist,
                            finalAimPoint.y - (float) Math.sin(angle) * optimalDist);
                    ob.targetPosition.set(formPos);
                }
            } else {
                // 射程外 → 接近（扇の位置を目指す）
                if (toAim.length() > 0.01f) {
                    float angle = (float) Math.atan2(toAim.y, toAim.x) + spreadAngle;
                    Vector2f approachPos = new Vector2f(
                            finalAimPoint.x - (float) Math.cos(angle) * optimalDist,
                            finalAimPoint.y - (float) Math.sin(angle) * optimalDist);
                    ob.targetPosition.set(approachPos);
                    ob.targetUnitId = null;
                }
            }
        }
    }

    /** 全軍 Nexus 前に後退 */
    private void retreat(List<Unit> myUnits, Building myNexus) {
        if (myNexus == null) return;
        for (Unit u : myUnits) {
            if (u.type == Unit.Type.CONSTRUCTOR) continue;
            u.targetUnitId = null;
            u.attackTargetBuildingId = null;
            float angle = (float) (u.id * 0.9f);
            float radius = (u.type == Unit.Type.OBELISK) ? 35f : 18f;
            u.targetPosition.set(
                    myNexus.position.x + (float) Math.cos(angle) * radius,
                    myNexus.position.y + (float) Math.sin(angle) * radius);
        }
    }

    // ─────────────────────────────────────────────
    //  目標選択 ユーティリティ
    // ─────────────────────────────────────────────

    /**
     * 優先順位付き建物ターゲット選択
     * ファクトリー → エクストラクター → レーザータワー → Nexus
     */
    private Building pickPriorityTarget(Vector2f from, List<Building> enemyBuildings, Building enemyNexus) {
        // 1. 近距離のファクトリー
        Building result = enemyBuildings.stream()
                .filter(b -> b.type == Building.Type.FACTORY && b.position.distance(from) < 80f)
                .min(Comparator.comparingDouble(b -> b.position.distance(from))).orElse(null);
        if (result != null) return result;
        // 2. 近距離のエクストラクター
        result = enemyBuildings.stream()
                .filter(b -> b.type == Building.Type.EXTRACTOR && b.position.distance(from) < 80f)
                .min(Comparator.comparingDouble(b -> b.position.distance(from))).orElse(null);
        if (result != null) return result;
        // 3. 近距離のレーザータワー
        result = enemyBuildings.stream()
                .filter(b -> b.type == Building.Type.LASER_TOWER && b.position.distance(from) < 60f)
                .min(Comparator.comparingDouble(b -> b.position.distance(from))).orElse(null);
        if (result != null) return result;
        // 4. Nexus
        return enemyNexus;
    }

    /**
     * 経済施設打撃のターゲット選択
     * ファクトリー → エクストラクター → タワー
     */
    private Building pickEconomyTarget(Vector2f from, List<Building> enemyBuildings) {
        if (from == null || enemyBuildings.isEmpty()) return null;
        // ファクトリー優先
        Building result = enemyBuildings.stream()
                .filter(b -> b.type == Building.Type.FACTORY)
                .min(Comparator.comparingDouble(b -> b.position.distance(from))).orElse(null);
        if (result != null) return result;
        // エクストラクター
        result = enemyBuildings.stream()
                .filter(b -> b.type == Building.Type.EXTRACTOR)
                .min(Comparator.comparingDouble(b -> b.position.distance(from))).orElse(null);
        if (result != null) return result;
        // タワー
        return enemyBuildings.stream()
                .filter(b -> b.type == Building.Type.LASER_TOWER)
                .min(Comparator.comparingDouble(b -> b.position.distance(from))).orElse(null);
    }

    private Unit closestUnit(Vector2f from, List<Unit> units) {
        return units.stream()
                .min(Comparator.comparingDouble(u -> u.position.distance(from)))
                .orElse(null);
    }

    // ─────────────────────────────────────────────
    //  建設 ユーティリティ
    // ─────────────────────────────────────────────

    private void assistExistingConstruction(List<Unit> myUnits, List<Building> incomplete) {
        if (incomplete.isEmpty()) return;
        myUnits.stream()
                .filter(u -> u.type == Unit.Type.CONSTRUCTOR && u.targetBuildingId == null && !u.manualMoveOrder)
                .forEach(u -> {
                    Building target = incomplete.stream()
                            .min(Comparator.comparingDouble(b -> b.position.distance(u.position)))
                            .orElse(incomplete.get(0));
                    u.targetBuildingId = target.id;
                    u.targetPosition.set(target.position);
                });
    }

    /**
     * 建物を配置してコンストラクターを割り当てる。
     * gameState.buildings を直接参照することで、スナップショットのズレを防止する。
     * ID は AtomicInteger（大きなオフセット）で生成し、idCounter との衝突を防ぐ。
     */
    private void build(Building.Type type, GameState state, List<Unit> myUnits, int teamId) {
        Unit constructor = myUnits.stream()
                .filter(u -> u.type == Unit.Type.CONSTRUCTOR && u.targetBuildingId == null && !u.manualMoveOrder)
                .findFirst().orElse(null);
        if (constructor == null) return;

        // gameState.buildings を直接参照（最新状態で占有チェック）
        List<Building> liveBuildings = new ArrayList<>(state.buildings.values());

        Vector2f pos;
        if (type == Building.Type.EXTRACTOR) {
            Building patch = findNearestUnoccupiedPatch(constructor.position, liveBuildings);
            if (patch == null) return;
            pos = patch.position;
        } else if (type == Building.Type.LASER_TOWER) {
            Building nexus = liveBuildings.stream()
                    .filter(b -> b.type == Building.Type.NEXUS && b.teamId == teamId).findFirst().orElse(null);
            if (nexus == null) return;
            float angle = (float) (Math.random() * Math.PI * 2);
            float dist  = 25.0f + (float) Math.random() * 15.0f;
            pos = new Vector2f(nexus.position.x + (float) Math.cos(angle) * dist,
                               nexus.position.y + (float) Math.sin(angle) * dist);
        } else if (type == Building.Type.SOLAR_COLLECTOR) {
            Building ext = liveBuildings.stream()
                    .filter(b -> b.type == Building.Type.EXTRACTOR && b.teamId == teamId).findAny().orElse(null);
            Vector2f ref = ext != null ? ext.position : constructor.position;
            pos = new Vector2f(ref).add((float) Math.random() * 20 - 10, (float) Math.random() * 20 - 10);
        } else if (type == Building.Type.SHIELD_GENERATOR) {
            Building nexus = liveBuildings.stream()
                    .filter(b -> b.type == Building.Type.NEXUS && b.teamId == teamId).findFirst().orElse(null);
            Vector2f ref = nexus != null ? nexus.position : constructor.position;
            pos = new Vector2f(ref).add((float) Math.random() * 40 - 20, (float) Math.random() * 40 - 20);
        } else {
            Building nexus = liveBuildings.stream()
                    .filter(b -> b.type == Building.Type.NEXUS && b.teamId == teamId).findFirst().orElse(null);
            pos = new Vector2f(nexus != null ? nexus.position : constructor.position)
                    .add((float) Math.random() * 60 - 30, (float) Math.random() * 60 - 30);
        }

        // AtomicInteger でデクリメントして一意 ID を生成（Integer.MAX_VALUE から下向き）
        int newId = aiIdCounter.getAndDecrement();
        Building b = new Building(newId, type, pos.x, pos.y, teamId, ServerLauncher.AI_PLAYER_ID);
        state.addBuilding(b);
        constructor.targetBuildingId = b.id;
        constructor.targetPosition.set(b.position);
    }

    /**
     * 全フリーファクトリーに生産指示（並列生産）
     * 旧実装の break を廃止し、空きキューがあるファクトリー全てにエンキュー。
     */
    private void produceAll(Unit.Type type, List<Building> myBuildings) {
        myBuildings.stream()
                .filter(b -> b.type == Building.Type.FACTORY && b.isComplete && b.productionQueue.size() < 3)
                .forEach(b -> b.productionQueue.add(type));
    }

    /**
     * 最近傍の未占有パッチを検索。
     * 「占有」= 完成済み or 建設中のエクストラクターが 1.0 以内に存在する。
     */
    private Building findNearestUnoccupiedPatch(Vector2f pos, List<Building> liveBuildings) {
        return liveBuildings.stream()
                .filter(b -> b.type == Building.Type.METAL_PATCH)
                .filter(b -> liveBuildings.stream().noneMatch(o ->
                        o.type == Building.Type.EXTRACTOR && o.position.distance(b.position) < 1.0f))
                .min(Comparator.comparingDouble(b -> b.position.distance(pos)))
                .orElse(null);
    }

    // ─────────────────────────────────────────────
    //  リソース解放
    // ─────────────────────────────────────────────
    public void close() {
        if (economySingularity != null) economySingularity.close();
        if (combatSingularity  != null) combatSingularity.close();
    }
}
