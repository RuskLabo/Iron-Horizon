# Iron Horizon

`Iron Horizon` は、LWJGL ベースで開発されているリアルタイム戦略ゲームです。  
ユニットを選択して移動・建設・戦闘を行い、ネクサスを中心に資源と防衛を管理していきます。

## 主な特徴

- クライアント / サーバー分離のマルチプロセス構成
- 3D 描画と UI を分けた高度なレンダリング構成
- 立方体メッシュを VBO で効率的に描画
- プロシージャル生成の地形と草原テクスチャ
- マップ上の資源ポイントをグリッド配置
- 戦闘・防衛・回復のリアルタイムシミュレーション
- Windows / macOS / Linux 向けの LWJGL native 切り替え

## 動作環境

- Java 21
- Maven 3.x
- Windows / macOS / Linux

## ビルド

```bash
mvn -q -DskipTests package
```

## 起動方法

このプロジェクトは、サーバーとクライアントを別々のプロセスで起動して接続します。

### サーバー

```bash
mvn -Dexec.mainClass=com.lunar_prototype.iron_horizon.ServerLauncher exec:java
```

### クライアント

```bash
mvn -Dexec.mainClass=com.lunar_prototype.iron_horizon.ClientLauncher exec:java
```

`exec:java` を使わず、IDE から `ServerLauncher` と `ClientLauncher` の `main` を直接実行しても構いません。

## 操作

- `W / A / S / D`: カメラ移動
- `Q / E`: カメラ回転
- `マウス左クリック`: 選択 / UI 操作
- `マウス右クリック`: 移動命令 / 攻撃命令
- `マウスホイール`: ズーム
- `Esc`: メニュー / 戻る

---

## ユニット

各ユニットは異なる役割と性能を持っています。

| ユニット名 | 特徴 | ステータス |
| :--- | :--- | :--- |
| **TANK (タンク)** | 万能型の主力戦闘ユニット。 | HP: 240, 射程: 27.0, 速度: 9.0 |
| **HOUND (ハウンド)** | 高速移動が可能な偵察・遊撃ユニット。 | HP: 90, 射程: 24.0, 速度: 18.0 |
| **OBELISK (オベリスク)** | 非常に強力な攻撃力を持つ移動要塞。 | HP: 1150, 射程: 34.0, 速度: 4.5 |
| **CONSTRUCTOR (コンストラクター)** | 建設を担当する非戦闘用ユニット。 | HP: 150, 射程: 0, 速度: 12.0 |

## 建物

拠点の拡張とリソースの管理に不可欠な施設です。

- **NEXUS (ネクサス)**: 中枢施設。周辺の味方ユニットを回復する機能を持ちます。
- **FACTORY (ファクトリー)**: ユニットの生産拠点です。
- **EXTRACTOR (抽出機)**: 資源（メタルパッチ）の上に建設し、資金を生成します。
- **LASER_TOWER (レーザータワー)**: 高性能レーザーによる自動迎撃を行う防衛設備です。
- **WALL (ウォール)**: 敵の進行を妨害する堅牢な壁です。
- **METAL_PATCH (メタルパッチ)**: マップ上に点在する天然資源。抽出機で回収可能です。

---

## ゲームの仕組み

### リソースと経済
- **資源ポイント (Metal Patch)**: グリッド上にスナップして配置されています。
- **資源回収**: ネクサス周辺で敵ユニットを撃破すると、資源として回収されます。
- **建設**: コンストラクターを使って拠点を拡張していきます。

### 戦闘と防衛
- **自動攻撃**: ユニットは射程内に敵が入ると自動的に攻撃を開始します。
- **レーザータワー**: 強力な単体攻撃を行い、敵の初期侵攻を防ぎます。
- **修復**: ネクサスの周辺（Influence Area）にいる味方ユニットは、時間の経過とともに HP が回復します。

### 制御
- **手動移動**: 右クリックによる手動移動命令は、AI の自動行動よりも優先されます。

---

## プロジェクト構成

- `src/main/java/com/lunar_prototype/iron_horizon/ClientLauncher.java`
  - クライアント起動、入力処理、ネットワーク通信、UI 統合。
- `src/main/java/com/lunar_prototype/iron_horizon/ServerLauncher.java`
  - サーバー起動、ゲームロジック、AI、資源管理、戦闘判定。
- `src/main/java/com/lunar_prototype/iron_horizon/client/GameRenderer.java`
  - 3D 描画、UI レンダリング、ミニマップ、エフェクト制御。
- `src/main/java/com/lunar_prototype/iron_horizon/client/render/`
  - `Mesh`、`Texture`、`TerrainGenerator` などのレンダリング基盤。
- `src/main/java/com/lunar_prototype/iron_horizon/common/model/`
  - ユニットや建物のデータ定義 (`Unit.java`, `Building.java`)。

## 開発メモ

- **描画**: LWJGL 3 を使用し、VBO による効率的な描画を実装しています。
- **ネットワーク**: 独自の通信プロトコルにより、サーバー・クライアント間の同期を行っています。
- **拡張性**: `OBJ` ローダを搭載しており、今後のモデル差し替えに対応可能です。

