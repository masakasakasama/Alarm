# Galaxy アラーム (Galaxy Alarm)

Galaxy 端末向けの**個人用アラームアプリ**。最重要目的は「アラームが鳴らなかった」を極力ゼロにすること。
UI の綺麗さより**信頼性を最優先**しています。Kotlin / Jetpack Compose / Room / AlarmManager。

> Play Store には公開しません。APK を GitHub Releases から直接インストールします。

---

## 特徴 / 信頼性の仕組み

- 予約は `setAlarmClock()` を最優先(無理なら `setExactAndAllowWhileIdle()`)。inexact alarm は不使用。
- Room を唯一の真実とし、以下のタイミングで**全アラームを再構築**:
  アプリ起動 / 端末再起動 / アプリ更新 / 時刻変更 / タイムゾーン変更 / exact alarm 権限変更 / グループ・アラームの ON/OFF・編集。
- exact alarm 権限が無いときは**アラームを有効に見せず**、ホームに赤い警告と設定誘導を表示。
- `WorkManager` がバックグラウンドで定期的にスケジュール健全性を確認し、欠落を自動修復。
- 予約失敗・権限不足・自動停止などは `AlarmEventLog` に記録。
- 100 件以上のアラーム、グループ単位 ON/OFF、同時刻アラームの同時鳴動(スタック表示)に対応。

---

## セットアップ(開発)

必要環境:
- JDK 17
- Android SDK (Platform 35 / Build-Tools 35.0.0)
- Android Studio (任意。CLI だけでもビルド可)

```bash
git clone https://github.com/masakasakasama/alarm.git
cd alarm
# SDK の場所を環境変数 ANDROID_HOME で指定するか local.properties を作成
./gradlew assembleDebug
```

`local.properties`(必要な場合):
```
sdk.dir=/path/to/Android/sdk
```

---

## APK の作り方

### A. CI で作る(推奨・スマホだけで完結)

1. バージョンを上げる(`app/build.gradle.kts` の `versionCode` / `versionName`)。
2. タグを打って push:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
3. `.github/workflows/release.yml` が **署名済み release APK** をビルドし、**GitHub Releases** に
   `GalaxyAlarm-v1.0.0.apk` として添付します。
4. スマホのブラウザで Releases ページを開き、APK をタップしてインストール(手動転送不要)。

### B. ローカルで作る

```bash
./gradlew assembleRelease
# 出力: app/build/outputs/apk/release/app-release.apk
```

署名は同梱の `keystore/galaxyalarm.jks`(パスワード等は `keystore/keystore.properties`)を使用します。
**個人利用前提で意図的にリポジトリに同梱**しています。公開する場合は必ず差し替えてください。

---

## APK の更新方法(上書きインストール)

- **署名キーが固定**(同梱 keystore)なので、同じ `applicationId`(`com.galaxyalarm`)で**上書きインストール**できます。
  アンインストール不要。
- 新しい APK を入れるだけ。データ(Room DB)は保持されます。
- アプリ内「設定 → アプリ更新 → 更新を確認」で、GitHub Releases の最新版を確認できます(APIキー不要)。
- **更新できない典型原因**(`handoff.md` にも記載):
  - 署名キーが異なる → 旧版をアンインストールしてから入れ直す。
  - `applicationId` が違う(debug ビルドは `com.galaxyalarm.debug`)→ release 同士で更新する。
  - 提供元不明アプリのインストール許可が必要。

---

## Galaxy で必要な権限・設定

インストール後、アプリ内「設定」または「信頼性チェック」から以下を必ず設定してください:

1. **アラームとリマインダー(正確なアラーム)** を許可
   - 設定 → アプリ → Galaxy アラーム → アラームとリマインダー → 許可
   - ※ 本アプリは `USE_EXACT_ALARM` を宣言しているため Android 13+ では自動許可される場合があります。
2. **通知** を許可(Android 13+)。
3. **バッテリー最適化の対象外**にする(重要)
   - 設定 → アプリ → Galaxy アラーム → バッテリー → **制限なし / 無制限**
   - Galaxy 独自の「スリープ中のアプリ」「ディープスリープ」一覧から除外。
4. **自動起動 / バックグラウンド実行**を許可(Galaxy の端末ケアで制限されることがある)。
5. ロック画面通知を許可(全画面表示のため)。

---

## 制限事項

- Galaxy(One UI)の積極的な省電力により、バッテリー最適化を除外しないと遅延・不発の可能性があります。
- `setAlarmClock` は OS が次アラームをロック画面に表示しますが、表示は1件のみ(同時刻多数でも代表1件)。
- 完全無音アラームは仕様通り音・バイブを出しません(画面と通知のみ)。
- クラウド同期・ログイン・広告・課金・睡眠計測はありません(非対象)。
- 同梱 keystore のため、この APK は「個人用」です。第三者配布や公開は想定していません。

---

## 手動テスト手順

実機(Galaxy 推奨)で確認:

1. **大量登録**: グループを作り、150 件のアラームを追加 → 一覧に全件表示されること。
2. **同時刻**: 同じ時刻(例 07:00)のアラームを 10 件作成 → 全件保持され、上書きされないこと。
3. **グループ OFF/ON**: グループを OFF → 配下アラームが「グループOFF」表示で予約解除。ON で復元。
4. **アラーム OFF/ON**: 個別 OFF → 予約解除、ON → 復元。
5. **音モード**: 音あり=鳴る / バイブのみ=無音で振動 / 完全無音=画面と通知のみ。
6. **再起動**: 端末再起動後、信頼性チェックで「未来予約」が緑のままであること。
7. **アプリ更新**: 新 APK を上書き → 予約が維持/再構築されること。
8. **時刻/TZ 変更**: 端末の時刻・タイムゾーンを変更 → 次回鳴動時刻が再計算されること。
9. **exact alarm 権限**: 権限を取り消す → ホームに赤い警告。戻す → 自動で再スケジュール。
10. **同時鳴動**: 同時刻アラームが複数鳴ったとき、全画面にスタック表示。1件停止しても他は継続。「すべて停止」で全停止。
11. **スヌーズ / 自動停止**: スヌーズで再予約、自動停止時間超過でログに記録。
12. **イベントログ**: 上記操作がログに残ること。

ユニットテスト:
```bash
./gradlew testDebugUnitTest        # 次回鳴動時刻ロジック
./gradlew connectedDebugAndroidTest # Room の大量登録・同時刻・requestCode 一意・CASCADE(要実機/エミュ)
```

<!-- release retrigger: v1.2.0 (full-screen intent fix) -->
