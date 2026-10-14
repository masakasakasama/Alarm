# CODEX_STATE

Status: blocked
Goal: 最新Galaxy Alarmの信頼性を検証し、状態を実装/PC検証/実機検証に分けて維持する。

## Done
- 現行main a771b0d / v2.2.14 / versionCode69 / SDK36を確認。古いhandoffを歴史資料として明示し最新状態への導線を追加。
- SDK36・JDK17・Gradle8.11.1でローカルunit/lint/debug APK検証を完了。

## Current
- 最新のタイマー終了音fade-in実装を含む現行Androidソースはビルド・lint・単体テスト正常。今回runtime変更なし。

## Next
- READMEのGalaxy手動テストを実機で実施: 鳴動/停止/スヌーズ、Doze、再起動PIN前、権限失効/復帰、更新後の予約保持、同時刻複数件。
- 実機結果が出るまでblockedを維持する。6月handoffの未実装リストをそのまま現在のタスクとみなさない。

## Blockers
- Galaxy実機/Android emulatorが未接続。PCユニットテストだけで鳴動品質を証明できない。

## Verification
- ./gradlew testDebugUnitTest lintDebug assembleDebug (SDK36/JDK17): passed
- JUnit 39/39 passed、lintDebug successful、debug APK生成
- git diff --check: passed

## 2026-10-04 更新
- v2.2.16 / versionCode 71: 残り時間の毎秒更新・画面復帰時の再計算、予約行とrequestCodeのトランザクション化を実装。
- JUnit 43/43成功（Room DBの同時予約・途中失敗時のロールバックを含む）、lintDebug・release lint・署名済みrelease APKビルド成功。
- 鳴動全体の保証・実機検証は未完了。再生共有、タイマーの音への復帰、OS予約確認の限界等は ALARM_RELIABILITY_REVIEW.md に記載。
- 配布先: https://github.com/masakasakasama/Alarm/releases

## 2026-10-13 更新
- v2.2.17 / versionCode 72: アラーム画面の戻る操作を、全停止から画面を閉じるだけの処理に変更。
- ロック解除だけの停止原因は未確定。Activityの一時停止・再表示・再作成・電源キーで鳴動状態を保持し、音量キーで明示的に停止できることをRobolectricで検証。
- 停止ログに操作種別を追加。実機のイベントログで停止ボタン・音量キー・通知・自動停止を切り分けられる。
- 予備発火でServiceの再配信設定を無効化しないよう、START_REDELIVER_INTENTを維持する修正を追加。OSによる終了後の復旧経路を保持する。
- ActivityとServiceの回帰テストを追加。Galaxyの実際のロック・認証解除と音の継続は未検証。
- PC単体テスト49/49成功。ロック解除だけで止まるという報告は実機ログによる追加確認が必要。

Updated at: 2026-10-13

## v2.2.18: 最新版でも画面ロックで停止するとの追加報告への対応
- 前回の修正で実機症状が解消したとは扱わない。ユーザーは最新版での発生を確認している。
- MediaPlayerのPARTIAL_WAKE_LOCK、予期しない一時停止の検知と再開、停止時の確実な解放、フェード更新のメインスレッド化を追加。
- ServiceのCPU保持を61分に更新・追加発火時に延長する。再開実行を画面ON/OFFとともにイベントログに記録する。
- 回帰テストはPC上での状態注入。実機の停止原因の確定・ロック時の音の継続確認は未完了。
- 最新ソースのPC単体テスト54/54・lintDebug成功。公開APKはGitHub Actionsでテスト・releaseビルドを行ったものを使用する。

## 2026-10-14: v2.2.19 グループの並べ替え
- グループ画面に上へ・下へ移動ボタンを追加。既存sortOrderをDBトランザクションで更新し、アプリ再起動後も表示順を維持する。
- 連続移動は最新DB順を使用。同順位・削除後の欠番を整理し、新規グループを末尾に追加する。名前変更が古い順番を上書きしないよう修正。
- グループなし・アラームのON/OFF・既存予約は維持する。順番はアラーム画面とグループ選択にも反映される。
- SDK36/JDK17でPC単体テスト58/58・lintDebug成功。並べ替えの4件の回帰テストを追加。実機での操作確認は未実施。
