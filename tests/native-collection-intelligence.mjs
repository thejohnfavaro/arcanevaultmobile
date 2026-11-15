import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(p,'utf8');
const insights=read('app/src/main/java/com/arcanevault/mobile/VaultCollectionInsights.java');
const audit=read('app/src/main/java/com/arcanevault/mobile/VaultCollectionAudit.java');
const deck=read('app/src/main/java/com/arcanevault/mobile/NativeDeckFit.java');
const home=read('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java');
const ws=read('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java');
const app=read('app/src/main/java/com/arcanevault/mobile/MainActivity.java');
const legacy=read('app/src/main/assets/index.html');
const peer=read('app/src/main/java/com/arcanevault/mobile/next/NativePeerSync.java');
assert.match(app,/publishCollectionAnalytics\?\.\(JSON\.stringify\(/,
 'Top-card values must be derived from exact-printing quote bootstrap');
assert.match(app,/ranked\.push\(\{name:c\.name,set:c\.set/,
 'Ranking must retain set identity');
assert.match(legacy,/top\.push\(\{name:c\.name,set:c\.set/,
 'Legacy quote batches publish the same ranking to native');
assert.match(insights,/if\(json==null\|\|json\.length\(\)>160000\)return false/,
 'Native projection bounds incoming analytics payload');
assert.match(home,/renderCollectionInsights\(content\)/,
 'Analytics shown in Home and Physical Collection');
assert.match(home,/auditWorker\.execute\(/,
 'Integrity audit must run outside the UI thread');
assert.match(audit,/missingIdentities/,
 'Physical printing identity gaps audited');
assert.match(audit,/possibleDuplicates/,
 'Possible duplicate entries surfaced without deletion');
assert.match(deck,/Map<String,Integer> ownedPaper=countPool\(physical\)/,
 'Deck discovery should build each owned pool only once');
assert.match(deck,/owned=arena\?arenaCards:physical/,
 'Deck buildability never combines Arena and physical inventory');
assert.match(deck,/if\(commanderFormat&&!commander\.isEmpty\(\)&&!need\.containsKey\(commander\)\)/,
 'Commander is counted only once in commander formats');
assert.match(ws,/Find decks closest to buildable/,
 'Library offers near-buildable deck discovery');
assert.match(peer,/\.put\("groups",groups\)/,
 'Sync previews summarize affected record domains');
assert.match(peer,/safeToAutoApply",false/,
 'Conflict-sensitive sync remains nonautomatic');
assert.doesNotMatch(insights,/HttpURLConnection|new URL\(/,
 'Analytics must not fetch prices or scrape live data');
assert.doesNotMatch(audit,/getWritableDatabase|\.edit\(\)/,
 'Read-only integrity check must not modify card ownership');
console.log('PASS: bounded analytics, scoped deck fit, safe peer previews and off-UI audits');
