/**
 * EMot booking/changes -> MacroDroid webhook -> Galaxy Alarm.
 * Store the secret MACRODROID_WEBHOOK_URL in Apps Script project properties.
 * MacroDroid keeps the Galaxy Alarm authentication token locally (never here).
 * Japan Standard Time: mail times are JST.
 */
function installRomancecarWatcher() {
  if (!ScriptApp.getProjectTriggers().some(t => t.getHandlerFunction() === 'checkRomancecarMails')) {
    ScriptApp.newTrigger('checkRomancecarMails').timeBased().everyMinutes(5).create();
  }
  checkRomancecarMails();
}

function checkRomancecarMails() {
  const props = PropertiesService.getScriptProperties();
  const endpoint = props.getProperty('MACRODROID_WEBHOOK_URL');
  if (!endpoint || !/^https:\/\/trigger\.macrodroid\.com\/[^/?]+\/[^/?]+$/.test(endpoint)) {
    throw new Error('Set MACRODROID_WEBHOOK_URL in Apps Script properties');
  }
  // Search recent email history so tickets bought in advance can be scheduled later.
  // Oldest first: a later "change completed" message must replace the original booking.
  const messages = GmailApp.search('from:no-reply@maasjapan.net subject:ロマンスカー newer_than:45d -in:trash', 0, 60)
    .flatMap(thread => thread.getMessages())
    .sort((a, b) => a.getDate().getTime() - b.getDate().getTime());
  const now = Date.now();
  for (const msg of messages) {
    const key = 'processed_' + msg.getId();
    if (props.getProperty(key)) continue;
    if (!/ロマンスカー/.test(msg.getSubject()) || !/購入完了|変更完了/.test(msg.getSubject())) {
      props.setProperty(key, String(now));
      continue;
    }
    const body = msg.getPlainBody();
    const date = body.match(/乗車日\s*[：:]\s*(\d{4})\/(\d{1,2})\/(\d{1,2})/);
    const route = body.match(/時間および区間\s*[：:]\s*\d{1,2}:\d{2}\s*[^\r\n]*?[→➡]\s*(\d{1,2}):(\d{2})\s+([^\r\n]+)/);
    if (!date || !route) {
      throw new Error('Unknown EMot mail format, message ID: ' + msg.getId());
    }
    const year = Number(date[1]), month = Number(date[2]), day = Number(date[3]);
    const hour = Number(route[1]), minute = Number(route[2]);
    const station = route[3].trim().slice(0, 24);
    // The EMot confirmation number is stable across ticket changes (unlike ticket ID).
    // Include it in the station variable so MacroDroid still needs just three Intent extras.
    const confirmation = body.match(/確認番号\s*[：:]\s*(E[0-9A-Za-z]{3,10})/);
    const destination = station + (confirmation ? '|' + confirmation[1] : '');
    if (month < 1 || month > 12 || day < 1 || day > 31 || hour < 0 || hour > 23 ||
        minute < 0 || minute > 59) throw new Error('Invalid date/time in: ' + msg.getId());
    // JST = UTC+09:00, year/month/day refer to local Japanese train times.
    const arrivalMs = Date.UTC(year, month - 1, day, hour - 9, minute);
    const back = new Date(arrivalMs + 9 * 3600000);
    if (back.getUTCFullYear() !== year || back.getUTCMonth() + 1 !== month || back.getUTCDate() !== day) {
      throw new Error('Invalid calendar date in: ' + msg.getId());
    }
    const alarmMs = arrivalMs - 3 * 60000;
    if (alarmMs > now + 12 * 3600000) continue; // Check again when in range.
    if (alarmMs < now + 60000) {
      props.setProperty(key, String(now)); // Too late to create an accurate alarm.
      continue;
    }
    const url = endpoint + '?arrival_ms=' + encodeURIComponent(String(arrivalMs)) +
      '&station=' + encodeURIComponent(destination);
    const response = UrlFetchApp.fetch(url, {muteHttpExceptions: true});
    if (response.getResponseCode() < 200 || response.getResponseCode() >= 300) {
      throw new Error('MacroDroid webhook HTTP ' + response.getResponseCode());
    }
    props.setProperty(key, String(Date.now()));
  }
  // Keep script properties bounded; future tickets are not marked as processed.
  const cutoff = now - 46 * 86400000;
  for (const [key, value] of Object.entries(props.getProperties())) {
    if (key.startsWith('processed_') && Number(value) > 0 && Number(value) < cutoff) {
      props.deleteProperty(key);
    }
  }
}
