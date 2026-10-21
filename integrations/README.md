# EMot ロマンスカー到着3分前アラーム（Galaxy Alarm 自作アプリ連携）

購入・変更完了メール → Google Apps Script (5分間隔で照会) → MacroDroid Webhook → MacroDroid Send Intent → **Galaxy Alarm (com.galaxyalarm)** でバイブのみの単発アラーム。

> 注意：Google Apps Script と MacroDroid を一度設定し、Galaxy Alarm のこの変更を含む更新APKを端末に入れる必要があります。GitHubのコード変更だけでは自動化は有効になりません。

## 1. Galaxy Alarm

アプリの「設定」→「ロマンスカー到着アラーム」→「トークンをコピー」から連携用トークンを取得。これは端末ごとの秘密キーです。再生成すると既存のMacroDroidマクロは動かなくなります。

## 2. MacroDroid

1. トリガーは「Webhook (URL)」。識別子 `romancecar`。Webhook URLを取得。誰にも共有しないこと
2. Webhookクエリパラメータ `arrival_ms` と `station` をそれぞれ文字列変数に保存する（変数ホワイトリストを設定）
3. アクション「Send Intent」を選ぶ <https://macrodroidforum.com/wiki/index.php?title=Action:_Send_Intent>
4. 下記設定で、Galaxy Alarmの受信コンポーネントにブロードキャストを送る

| 設定 | 値 |
| --- | --- |
| Target | Broadcast |
| Action | `com.galaxyalarm.action.SET_ROMANCECAR_ARRIVAL` |
| Package | `com.galaxyalarm` |
| Class | `com.galaxyalarm.automation.RomancecarImportReceiver` |
| String extra `arrival_ms` | Webhookから受け取った `arrival_ms` 変数 |
| String extra `station` | Webhookから受け取った `station` 変数 |
| String extra `auth_token` | Galaxy Alarmの設定からコピーしたトークン |

受信はアプリ側でトークンを照合します。トークン未設定、時刻が過去、12時間より先、または到着3分前まで60秒未満の場合は登録しません。アラームは `ロマンスカー（自動）` グループ内で、確認番号（例：E123）が同じ予約なら、到着日・駅が変わっても同じアラームを更新します。無音・振動ありの `VIBRATE_ONLY` 固定です。

## 3. Google Apps Script

1. <https://script.google.com/> にアクセスして新規プロジェクトを作成
2. `romancecar_alarm.gs` を貼り付け
3. 「プロジェクトの設定」→「スクリプトプロパティ」に `MACRODROID_WEBHOOK_URL` = MacroDroidのWebhook URL（クエリパラメータなし）を追加
4. `installRomancecarWatcher` を一度実行し、Gmail と外部HTTP通信の権限を許可
5. Gmail検索条件は送信者 `no-reply@maasjapan.net`、件名 `ロマンスカー`、メール種別は「購入完了」「変更完了」

## 検証

- 端末時刻の約10分後に相当する `arrival_ms` をMacroDroid Webhookへ手動送信し、Galaxy Alarmで7分後の単発アラームがONになったか確認
- 音は出ず、振動が生じることを実機確認
- 二度送っても重複せず、同じ乗車日・到着駅で到着時刻を変えると更新されること
- 到着までの時間が12時間超、期限切れ、認証トークンが誤りの場合は登録されないこと
- 購入後の変更メールは確認番号で旧アラームを書き換えます。ただし、取消・払い戻しメールは未対応のため、キャンセル時はアラームを手動OFFにしてください

この方法ではGoogle Apps Scriptの定期実行とMacroDroid Webhookのクラウド通知を使うため、メール到着の即時検出は保証されません。Galaxy Alarm側の時刻指定は既存の `setAlarmClock()` を使用。Androidの強制停止、通信不可、Gmail遅延などで自動登録に失敗する場合があります。
