// Run: node integrations/romancecar_direct.test.cjs
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = readFileSync(__dirname + '/romancecar_direct.gs', 'utf8');
const now = Date.parse('2026-10-22T00:00:00Z');
let messages = [];
const context = vm.createContext({
  Date: class extends Date { static now() { return now; } },
  GmailApp: {search: () => [{getMessages: () => messages}]},
  PropertiesService: {getScriptProperties: () => ({getProperty: () => 'x'.repeat(43)})},
  ContentService: {MimeType: {JSON:'json'},createTextOutput: text => ({text,setMimeType(){return this;}})},
  console,
});
vm.runInContext(source, context);
function mail(route, code='E123', subject='ロマンスカー 購入完了', date='2026/10/22', received=now) {
  return {getPlainBody: () => `乗車日：${date}\n時間および区間：${route}\n確認番号：${code}`,
    getSubject: () => subject,getDate: () => new Date(received),getId: () => code};
}
function bookings() { return JSON.parse(JSON.stringify(context.findRomancecarBookings_())); }
messages = [mail('12:00 新宿 → 13:05 本厚木')];
let [trip] = bookings();
assert.equal(trip.departure_ms, Date.parse('2026-10-22T03:00:00Z'));
assert.equal(trip.arrival_ms, Date.parse('2026-10-22T04:05:00Z'));
assert.equal(trip.departure_station,'新宿|E123');
assert.equal(trip.station,'本厚木|E123');
messages.push(mail('14:00 町田 ➡ 14:30 本厚木', 'E123','ロマンスカー 変更完了','2026/10/22',now+1000));
assert.equal(bookings().length,1);
assert.equal(bookings()[0].departure_station,'町田|E123');
assert.equal(bookings()[0].departure_ms,Date.parse('2026-10-22T05:00:00Z'));
messages=[mail('23:50 新宿 → 00:30 本厚木')];
assert.equal(bookings()[0].arrival_ms,Date.parse('2026-10-22T15:30:00Z'));
messages=[mail('25:00 新宿 → 26:00 本厚木'),mail('12:00 新宿 → 13:00 本厚木','E124','ロマンスカー 購入完了','2026/02/30')];
assert.equal(bookings().length,0);
messages=[mail('07:00 新宿 → 08:00 本厚木')];
assert.equal(bookings().length,0);
const unauthorized=context.doPost({postData:{contents:JSON.stringify({token:'wrong'})}});
assert.deepEqual(JSON.parse(unauthorized.text),{ok:false,error:'unauthorized'});
console.log('PASS: departure/arrival, booking changes, midnight, invalid dates, past trips, authentication');
