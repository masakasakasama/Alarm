# handoff.md — 次の作業者へ

このファイルを読めば続きから進められるようにしてあります。

## 作業内容(今回)

Galaxy 向け高信頼アラームアプリを新規実装(Phase 1〜3 を一括)。

- Room DB(`AlarmGroup` / `AlarmItem` / `ScheduledOccurrence` / `AlarmEventLog`)
- グループ CRUD / アラーム CRUD(各アラームは必ず1グループ所属)
- 次回鳴動時刻計算(`NextTriggerCalculator`、曜日マスク・一度きり対応)
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

## 主な変更ファイル

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml
app/build.gradle.kts, app/proguard-rules.pro
keystore/galaxyalarm.jks, keystore/keystore.properties
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

## ビルド / 配布

- ローカル: `./gradlew assembleDebug`(SDK 35 必須)。
- 配布: `git tag vX.Y.Z && git push origin vX.Y.Z` → Actions が Releases に APK を添付。
- `UpdateChecker.REPO` は `masakasakasama/alarm`。リポジトリ名が変わったら更新すること。

## 未解決課題 / 次にやること

1. **実機ビルド未検証**: 開発コンテナに Android SDK が無く、外部ダウンロードも遮断されているため、
   ローカルでの APK ビルドは未実施。**CI(build.yml)で初回ビルドを通すこと**。
   コンパイルエラーが出たら CI ログを見て修正(特に Compose material3 の `menuAnchor()` 等の API 差異)。
2. **実機での鳴動確認が必須**(下記「実機テスト結果」は未実施)。
3. 同時刻多数時、`setAlarmClock` のロック画面表示は1件のみ。複数を見せたい場合は通知側の工夫が必要。
4. FGS の `foregroundServiceType` は `mediaPlayback`。完全無音アラームでは音を出さないため、
   Android 14+ のポリシー上は `specialUse` 等への変更を検討してもよい(現状は実用上問題ない想定)。
5. 着信音選択 UI(`ringtoneUri`)は未実装。今はデフォルトのアラーム音を使用。RingtonePicker を追加すると親切。
6. グループの並べ替え(`sortOrder`)は保存のみで UI 未実装。
7. Room スキーマは version=1。スキーマ変更時は migration を追加すること(`fallbackToDestructiveMigration` は未設定=データ保護優先)。

## 既知の不具合 / 注意

- 上記 1 のとおり、**コンパイル未検証**。最初の CI ランで型・import の修正が必要になる可能性がある。
- `keystore/` をコミットしている(個人利用前提)。公開・共有時は必ず差し替え。
- Galaxy の省電力(端末ケア)で除外しないと不発し得る。READMEの権限手順を実施すること。

## 実機テスト結果

- 未実施(SDK/実機なしのため)。README「手動テスト手順」に沿って実機で確認し、結果をここに追記してください。

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
