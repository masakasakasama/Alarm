# handoff.md — 次の作業者へ

このファイルを読めば続きから進められるようにしてあります。
**最終更新: 2026-06-07(セッション継続用に現状を全記録)**

---

## 0. いまの状況サマリ(最重要・まずここを読む)

- **✅ 完成・ビルド成功・APK公開済み**。アプリ本体は実装完了し、ブランチ
  `claude/galaxy-alarm-app-F5YCQ` に push 済み。CI(GitHub Actions)で **ビルド成功**を確認。
- **📲 ダウンロード(スマホでそのまま開いてインストール可)**:
  - Release ページ: https://github.com/masakasakasama/Alarm/releases/tag/v1.0.0
  - APK 直リンク: https://github.com/masakasakasama/Alarm/releases/download/v1.0.0/GalaxyAlarm-v1.0.0.apk
  - 署名済み release APK(約 11.9MB、applicationId `com.galaxyalarm`)。
- **自動公開の仕組み**: `build.yml` が push のたびに debug+release をビルドし、
  release APK を **GitHub Releases (タグ v1.0.0) に自動アップロード**する。
  (このセッションのトークンは「タグpush」「workflow手動実行」が403で不可だったため、
   ワークフロー自身の `GITHUB_TOKEN` で公開する方式にした。)

### インストール手順(ユーザー向け)
1. スマホのブラウザで上記 Release ページを開く。
2. `GalaxyAlarm-v1.0.0.apk` をタップしてダウンロード。
3. 「提供元不明のアプリ」のインストールを許可してインストール。
4. アプリ内「信頼性チェック」→「アラームとリマインダー」権限を許可、バッテリー最適化を除外。

### 次の作業者が最初にやること
1. **実機(Galaxy)で鳴動テスト**(§9 の表、README の手動テスト手順)。これが唯一の残作業。
2. バージョンを上げたいとき: `app/build.gradle.kts` の versionName を変更 → push すれば
   `build.yml` が自動で release を更新(タグ名は固定 v1.0.0。versionedにしたいなら build.yml の
   `tag_name` を versionName 参照に変更)。
3. 注意: APK更新時は同一署名・同一 applicationId のため**上書きインストール可**(アンインストール不要)。

---

## 1. 作業内容(今回)

Galaxy 向け高信頼アラームアプリを新規実装(Phase 1〜3 を一括)。

- Room DB(`AlarmGroup` / `AlarmItem` / `ScheduledOccurrence` / `AlarmEventLog`)
- グループ CRUD / アラーム CRUD(各アラームは必ず1グループ所属)
- 次回鳴動時刻計算(`NextTriggerCalculator`、曜日マスク・一度きり対応、ユニットテスト付き)
- exact alarm 予約(`setAlarmClock` 優先 / `setExactAndAllowWhileIdle` フォールバック)
- requestCode は `ScheduledOccurrence` の PK を採用 → 同時刻でも上書きされない
- BroadcastReceiver(発火 / BOOT / MY_PACKAGE_REPLACED / TIME_SET / TIMEZONE_CHANGED / exact 権限変更)
- 鳴動: Foreground Service + 全画面 Activity(ロック画面表示)、音/バイブ/完全無音、スタック表示、すべて停止
- スヌーズ、自動停止、イベントログ
- 信頼性チェック画面(権限・電池最適化・未来予約・requestCode 重複・整合性・最終チェック/修復/発火ログ)
- WorkManager で定期健全性チェック+自動修復
- 手動「スケジュール修復」「再チェック」「全再スケジュール」
- アプリ内 GitHub Releases 更新確認(APIキー不要)
- GitHub Actions:`build.yml`(push/PR で debug ビルド+ユニットテスト)/ `release.yml`(タグで署名APKをReleasesへ)
- 固定署名キーストア同梱(上書きインストール用)
- README / 本 handoff

## 2. コミット履歴(このブランチ)

```
2039d64 CI: Gradleキャッシュと--no-daemonでビルド高速化・安定化
9a2ec06 ビルド修正: Locale.JP→Locale.JAPAN、HomeScreenの不正import削除、launcherベクター修正
8202995 Galaxy向け高信頼アラームアプリを実装
8133267 Initial commit
```

## 3. 主な変更ファイル

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml
gradle/wrapper/*(wrapper 8.11.1)、gradlew, gradlew.bat
app/build.gradle.kts, app/proguard-rules.pro
keystore/galaxyalarm.jks, keystore/keystore.properties  ← 固定署名(個人利用のため意図的に同梱)
app/src/main/AndroidManifest.xml
app/src/main/res/...(themes, strings, icons, xml rules)
app/src/main/java/com/galaxyalarm/
  AlarmApplication.kt, MainActivity.kt
  data/model/Enums.kt, data/entity/Entities.kt, data/db/{AppDatabase,Converters}.kt
  data/dao/Daos.kt, data/repo/AlarmRepository.kt
  scheduler/{NextTriggerCalculator,AlarmScheduler,AlarmIntents}.kt
  receiver/{AlarmReceiver,SystemEventReceiver,ExactAlarmPermissionReceiver}.kt
  service/AlarmService.kt
  ring/{AlarmRingActivity,AlarmPlayer,ActiveAlarms}.kt
  notify/NotificationHelper.kt
  reliability/{PermissionChecker,ReliabilityChecker,ReliabilityStore,ScheduleHealthWorker}.kt
  update/UpdateChecker.kt
  ui/...(theme, components, home, groups, alarms, edit, reliability, log, settings, navigation, viewmodels)
app/src/test/java/com/galaxyalarm/NextTriggerCalculatorTest.kt
app/src/androidTest/java/com/galaxyalarm/AlarmDbTest.kt
.github/workflows/{build.yml,release.yml}
```

## 4. 技術スタック / バージョン

- AGP 8.7.3 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / Gradle 8.11.1
- compileSdk 35 / minSdk 26 / targetSdk 35
- Compose BOM 2024.12.01 / Material3 / Navigation Compose 2.8.5
- Room 2.6.1 / WorkManager 2.9.1 / Coroutines 1.9.0
- applicationId: `com.galaxyalarm`(debugは `com.galaxyalarm.debug`)
- DIは軽量自前(`AppContainer`)。Hilt不使用=ビルドリスク低減。

## 5. ビルド / 配布

- ローカル: `./gradlew assembleDebug`(Android SDK 35 が必要。今回の開発コンテナには無い)。
- CI debug: push すると `build.yml` が `testDebugUnitTest assembleDebug` を実行、`app-debug` を artifact 出力。
- CI release: `git tag vX.Y.Z && git push origin vX.Y.Z` → `release.yml` が署名APKを Releases に添付。
- `UpdateChecker.REPO` は `masakasakasama/alarm`。リポジトリ名が変わったら更新すること。
- 署名: `keystore/galaxyalarm.jks`(storepass/keypass=`galaxyalarm`, alias=`galaxyalarm`)。
  CI からは env(`KEYSTORE_FILE` 等)で上書き可。固定署名なので同一パッケージで上書きインストール可。

## 6. CI 履歴と修正済みエラー(重要)

- **Run #1(commit 8202995)= 失敗**。`compileDebugKotlin` で以下のコンパイルエラー:
  - `AlarmService.kt` / `TimeFormat.kt`: `Locale.JP` は存在しない参照 → **`Locale.JAPAN` に修正**。
  - `HomeScreen.kt`: `import androidx.compose.foundation.lazy.item`(importできないメンバ)→ **import削除**。
  - ※ `kspDebugKotlin`(Room生成)・リソース処理・マニフェスト処理は Run #1 で**成功済み**。
    残りは純粋な Kotlin コンパイルのみで、上記修正で解消済み。
- **Run #2(commit 9a2ec06)/ Run #3(commit 2039d64)**:
  GitHub ホストランナーが非常に遅く、長時間 running のまま完了確認できず(インフラ起因)。
  Run #3 は `build.yml` を「Gradleキャッシュ + `--no-daemon`」に改善したもの。
- **結論**: コンパイルブロッカーは解消済み。緑化はランナー完了待ち。MCPのActions APIは
  キャッシュ/遅延が強く状態が古く見えることがある点に注意(Actions タブの実ページを見るのが確実)。

## 7. 未解決課題 / 次にやること

1. **CI を緑にして APK を入手**(§0 の手順)。落ちたらログのコンパイルエラーを直す方針。
2. **実機(Galaxy)での鳴動テスト**(下記「実機テスト結果」は未実施)。READMEの手動テスト手順に従う。
3. 着信音選択 UI(`ringtoneUri`)未実装。現在はデフォルトのアラーム音。RingtonePicker追加が望ましい。
4. グループ並べ替え(`sortOrder`)は保存のみでUI未実装。
5. 同時刻多数時、`setAlarmClock` のロック画面表示はOS仕様で代表1件のみ。
6. FGS の `foregroundServiceType=mediaPlayback`。完全無音アラームは音を出さないため、
   Android 14+ ポリシー的には `specialUse` 等への変更検討余地あり(実用上は現状で動く想定)。
7. Room スキーマ version=1。変更時は migration を追加(`fallbackToDestructiveMigration` 未設定=データ保護優先)。

## 8. 既知の不具合 / 注意

- **実機での鳴動は未検証**(SDK/実機なし)。CIでのビルド成功までは確認プロセス進行中。
- `keystore/` をコミットしている(個人利用前提)。公開・共有時は必ず差し替え。
- Galaxy の省電力(端末ケア)で除外しないと不発し得る。README の権限手順を必ず実施。
- 開発コンテナはネットワーク制限あり(GitHub等は可、dl.google.com 等は不可)。ローカルAndroidビルド不可。

## 9. 実機テスト結果

- 未実施(SDK/実機なしのため)。README「手動テスト手順」に沿って実機で確認し、結果をここに追記。

| 項目 | 結果 | メモ |
|---|---|---|
| 150件登録 | 未 | |
| 同時刻10件 | 未 | |
| グループON/OFF | 未 | |
| 音/バイブ/無音 | 未 | |
| 再起動復元 | 未 | |
| 更新後復元 | 未 | |
| TZ/時刻変更 | 未 | |
| 権限取消/復帰 | 未 | |
| 同時鳴動スタック | 未 | |
| スヌーズ/自動停止 | 未 | |
