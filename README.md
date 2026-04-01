# Iron Horizon

`Iron Horizon` は、LWJGL ベースで作っているリアルタイム戦略ゲームです。  
ユニットを選択して移動・建設・戦闘を行い、ネクサスを中心に資源と防衛を管理していきます。

## 主な特徴

- クライアント / サーバー分離の構成
- 3D 描画と UI を分けたレンダリング構成
- 立方体メッシュを VBO で描画
- プロシージャル生成の地形と草原テクスチャ
- マップ上の資源ポイントをグリッド配置
- ネクサス周辺の味方回復
- ネクサス周辺で倒れた敵ユニットからの資源回収
- レーザータワーによる自動防衛
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

このプロジェクトはクライアントとサーバーを別プロセスで起動します。

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
- `マウス右クリック`: 移動命令
- `マウスホイール`: ズーム
- `Esc`: メニュー / 戻る

## ゲームの流れ

1. サーバーを起動する
2. クライアントを起動して接続する
3. ユニットを選択して移動命令を出す
4. 資源を集めて建物を建てる
5. ネクサスと防衛設備で前線を維持する

## 現在の実装

### 描画

- `ClientLauncher` から描画責務を切り離し、`GameRenderer` に集約しています
- 描画は world / overlay / UI のパスに分けています
- ユニットと建物の箱は VBO ベースのメッシュ描画です
- `OBJ` ローダの土台も用意しています

### 地形

- マップサイズは `MapSettings` で共通管理しています
- 地形はプロシージャル生成です
- 地面は草原っぽいテクスチャで描画しています
- 資源ポイントはグリッドにスナップして配置しています

### ゲームルール

- ネクサスは周辺の味方ユニットを回復します
- 周辺で敵ユニットが倒れると資源回収が発生します
- `LASER_TOWER` を建築でき、近距離の敵へ自動攻撃します
- 手動移動命令は AI の自動上書きより優先されます

## プロジェクト構成

- `src/main/java/com/lunar_prototype/iron_horizon/ClientLauncher.java`
  - クライアント起動、入力、ネットワーク受信、UI の配線
- `src/main/java/com/lunar_prototype/iron_horizon/ServerLauncher.java`
  - サーバー起動、ゲーム進行、AI、資源、戦闘処理
- `src/main/java/com/lunar_prototype/iron_horizon/client/GameRenderer.java`
  - 3D 描画、UI 描画、ミニマップ、エフェクト
- `src/main/java/com/lunar_prototype/iron_horizon/client/render/`
  - `Mesh`、`Texture`、`TerrainGenerator`、`OBJ` ローダなどの描画基盤
- `src/main/java/com/lunar_prototype/iron_horizon/common/`
  - 共有ネットワーク定義、マップ設定、共通モデル

## メモ

- `OBJ` ローダは入っていますが、現時点では本格的なモデル差し替えはまだ途中です
- 一部 UI や描画は今後さらに整理していく予定です

