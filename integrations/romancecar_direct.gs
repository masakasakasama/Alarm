/**
 * MacroDroid-free feed for personal Galaxy Alarm.
 * Deploy this Google Apps Script as a web app:
 *  Execute as: Me; Who has access: Anyone
 * Then set Script Property ROMANCECAR_SYNC_TOKEN to the 43-character random
 * token copied from Galaxy Alarm. The token is sent only in POST body.
 * Never place credentials in this public GitHub file.
 */
function doPost(e) {
  try {
    const supplied = JSON.parse(e && e.postData && e.postData.contents || '{}');
    const expected = PropertiesService.getScriptProperties().getProperty('ROMANCECAR_SYNC_TOKEN');
    if (!expected || expected.length < 32 || supplied.token !== expected) {
      return response_({ok: false, error: 'unauthorized'});
    }
    return response_({ok: true, bookings: findRomancecarBookings_()});
  } catch (error) {
    // Do not return email data, access tokens, or stack traces.
    console.error('Romancecar feed failed:', String(error).slice(0, 100));
    return response_({ok: false, error: 'internal_error'});
  }
}

function response_(object) {
  return ContentService.createTextOutput(JSON.stringify(object))
    .setMimeType(ContentService.MimeType.JSON);
}

function findRomancecarBookings_() {
  const now = Date.now();
  const byCode = {};
  const threads = GmailApp.search(
    'from:no-reply@maasjapan.net subject:ロマンスカー newer_than:45d -in:trash',
    0, 80
  );
  const messages = threads.flatMap(t => t.getMessages())
    .filter(m => /購入完了|変更完了/.test(m.getSubject()))
    .sort((a, b) => a.getDate().getTime() - b.getDate().getTime());

  for (const message of messages) {
    const body = message.getPlainBody();
    const date = body.match(/乗車日\s*[：:]\s*(\d{4})\/(\d{1,2})\/(\d{1,2})/);
    const route = body.match(/時間および区間\s*[：:]\s*(\d{1,2}):(\d{2})\s+([^\r\n]*?)[→➡]\s*(\d{1,2}):(\d{2})\s+([^\r\n]+)/);
    if (!date || !route) continue;

    const year = Number(date[1]), month = Number(date[2]), day = Number(date[3]);
    const hour = Number(route[4]), minute = Number(route[5]);
    if (month < 1 || month > 12 || day < 1 || day > 31 ||
        hour < 0 || hour > 23 || minute < 0 || minute > 59) continue;
    const arrivalMs = Date.UTC(year, month - 1, day, hour - 9, minute);
    const jsDate = new Date(arrivalMs + 9 * 60 * 60 * 1000);
    if (jsDate.getUTCFullYear() !== year || jsDate.getUTCMonth() + 1 !== month ||
        jsDate.getUTCDate() !== day) continue;

    // Codes in EMot purchase and change emails are stable within the booking.
    const confirmation = body.match(/確認番号\s*[：:]\s*(E[0-9A-Za-z]{3,10})/);
    const code = confirmation ? confirmation[1] : null;
    const station = route[6].trim().slice(0, 24);
    const departureStation = route[3].trim().slice(0, 24);
    const departureHour = Number(route[1]), departureMinute = Number(route[2]);
    if (!departureStation || departureHour > 23 || departureMinute > 59) continue;
    const departureMs = Date.UTC(year, month - 1, day, departureHour - 9, departureMinute);
    // A train arriving after midnight belongs to the next calendar day.
    const finalArrivalMs = arrivalMs < departureMs ? arrivalMs + 24 * 60 * 60 * 1000 : arrivalMs;
    if (!station || finalArrivalMs < now - 24 * 60 * 60 * 1000 ||
        finalArrivalMs > now + 45 * 24 * 60 * 60 * 1000) continue;
    const key = code || message.getId();
    byCode[key] = {
      arrival_ms: finalArrivalMs,
      departure_ms: departureMs,
      departure_station: departureStation + (code ? '|' + code : ''),
      station: station + (code ? '|' + code : ''),
    };
  }

  // Only minimal arrival metadata is returned; no raw mail or ticket IDs.
  return Object.values(byCode)
    .filter(trip => trip.arrival_ms > now)
    .sort((a, b) => a.arrival_ms - b.arrival_ms)
    .slice(0, 60);
}
