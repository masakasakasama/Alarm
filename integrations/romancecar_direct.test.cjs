/* Standalone mocked Apps Script integration tests. No real Gmail credentials. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const SOURCE = fs.readFileSync(path.join(__dirname, 'romancecar_direct.gs'), 'utf8');
const NOW = Date.UTC(2026, 9, 22, 6, 0); // 2026-10-22 15:00 JST
const SECRET = 'X'.repeat(43);

function makeMail({ id, timestamp, subject = 'EMotロマンスカー特急券購入完了のお知らせ・領収書', body }) {
  return {
    getId: () => id,
    getDate: () => new Date(timestamp),
    getSubject: () => subject,
    getPlainBody: () => body,
  };
}

function bookingBody(date, destinationTime, station, code = 'E123') {
  return [
    '乗車日：' + date,
    '列車名：はこね 21号',
    '時間および区間：08:11 新宿 → ' + destinationTime + ' ' + station,
    '確認番号：' + code,
    '特急券ID：fictional-ticket',
  ].join('\n');
}

function appContext(mails, key = SECRET) {
  let gmailSearches = 0;
  class FixedDate extends Date { static now() { return NOW; } }
  const sandbox = {
    Date: FixedDate,
    GmailApp: {
      search(query) {
        gmailSearches++;
        assert.match(query, /no-reply@maasjapan\.net/);
        return [{getMessages: () => mails}];
      },
    },
    PropertiesService: {getScriptProperties: () => ({getProperty: () => key})},
    ContentService: {
      MimeType: {JSON: 'application/json'},
      createTextOutput: text => ({text, setMimeType() { return this; }}),
    },
    console: {error() {}},
  };
  vm.runInNewContext(SOURCE, sandbox, {filename: 'romancecar_direct.gs'});
  return {
    post: (token) => JSON.parse(sandbox.doPost({postData: {contents: JSON.stringify({token})}}).text),
    searches: () => gmailSearches,
  };
}

test('purchase mail: JST arrival, station, and no email body leakage', () => {
  const mail = makeMail({
    id: 'purchase',
    timestamp: NOW - 60_000,
    body: bookingBody('2026/10/23', '09:05', '本厚木'),
  });
  const result = appContext([mail]).post(SECRET);
  const arrivalMs = Date.UTC(2026, 9, 23, 0, 5); // JST 09:05
  assert.deepEqual(result, {ok: true, bookings: [{arrival_ms: arrivalMs, station: '本厚木|E123'}]});
  assert.equal(new Date(arrivalMs - 3 * 60_000).toISOString(), '2026-10-23T00:02:00.000Z');
  assert.equal(JSON.stringify(result).includes('fictional-ticket'), false);
});

test('change mail supersedes earlier purchase by confirmation code', () => {
  const previous = makeMail({
    id: 'old', timestamp: NOW - 180_000,
    body: bookingBody('2026/10/23', '09:05', '本厚木'),
  });
  const changed = makeMail({
    id: 'new', timestamp: NOW - 60_000,
    subject: 'EMot ロマンスカー特急券変更完了のお知らせ',
    body: bookingBody('2026/10/23', '08:32', '海老名'),
  });
  const result = appContext([changed, previous]).post(SECRET);
  assert.equal(result.bookings.length, 1);
  assert.equal(result.bookings[0].station, '海老名|E123');
  assert.equal(result.bookings[0].arrival_ms, Date.UTC(2026, 9, 22, 23, 32));
});

test('reject unauthenticated requests before accessing Gmail', () => {
  const app = appContext([]);
  assert.deepEqual(app.post('wrong'), {ok: false, error: 'unauthorized'});
  assert.equal(app.searches(), 0);
});

test('malformed date, route, and non-booking subjects are not returned', () => {
  const badDate = makeMail({
    id: 'bad-date', timestamp: NOW - 1,
    body: bookingBody('2026/02/29', '09:05', '本厚木'),
  });
  const badRoute = makeMail({
    id: 'bad-route', timestamp: NOW - 2,
    body: '乗車日：2026/10/23\n時間および区間：unknown\n確認番号：E124',
  });
  const unrelated = makeMail({
    id: 'unrelated', timestamp: NOW - 3, subject: 'レシート',
    body: bookingBody('2026/10/23', '09:05', '本厚木'),
  });
  assert.deepEqual(appContext([badDate, badRoute, unrelated]).post(SECRET), {ok: true, bookings: []});
});

test('expired and too-far-future trips do not create alarms', () => {
  const old = makeMail({
    id: 'old', timestamp: NOW - 1,
    body: bookingBody('2026/10/21', '09:05', '本厚木', 'E124'),
  });
  const far = makeMail({
    id: 'far', timestamp: NOW - 2,
    body: bookingBody('2027/01/01', '09:05', '本厚木', 'E125'),
  });
  assert.deepEqual(appContext([old, far]).post(SECRET), {ok: true, bookings: []});
});

test('distinct confirmation codes are kept separate, sorted by arrival', () => {
  const later = makeMail({
    id: 'later', timestamp: NOW - 60_000,
    body: bookingBody('2026/10/24', '09:05', '本厚木', 'E124'),
  });
  const earlier = makeMail({
    id: 'earlier', timestamp: NOW - 30_000,
    body: bookingBody('2026/10/23', '09:05', '新宿', 'E125'),
  });
  const result = appContext([later, earlier]).post(SECRET);
  assert.equal(result.bookings.length, 2);
  assert.deepEqual(result.bookings.map(x => x.station), ['新宿|E125', '本厚木|E124']);
});
