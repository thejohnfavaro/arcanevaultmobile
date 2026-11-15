import assert from 'node:assert/strict';
import fs from 'node:fs';
const history=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/VaultValueHistory.java','utf8');
const values=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/VaultSetValues.java','utf8');
const widgets=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/VaultWidgets.java','utf8');
const home=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java','utf8');
const sets=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java','utf8');
assert.match(history,/private static final int MAX_DAYS=730/,
 'History should bound stored days');
assert.match(history,/day\.compareTo\(today\)<0/,
 'Previous days are retained and current day is replaced');
assert.match(history,/bounded\.put\(new JSONObject\(\)\.put\("day",today\)\.put\("cents",cents\)\)/,
 'Store a single updated sample for the current day');
assert.match(history,/if\(priced<=0\|\|cents<=0/,
 'Unpriced inventory cannot produce fabricated zero-value history');
assert.match(history,/if\(data\.length\(\)<2\)/,
 'A single real observation must not be displayed as a fake trend');
assert.match(history,/History begins with the first priced day/,
 'Graph explains why history is unavailable');
assert.match(widgets,/VaultValueHistory\.record\(context,null,currentCents,priced\)/,
 'Full physical collection snapshot records history');
assert.match(values,/VaultValueHistory\.record\(context,row\.optString\("code",""\)/,
 'Physical set values record separate history');
assert.match(home,/VaultValueHistory\.chart\(activity,null\)/,
 'Android Home and physical collection expose overall chart');
assert.match(sets,/VaultValueHistory\.chart\(activity,set\)/,
 'Physical set details expose scoped chart');
assert.doesNotMatch(history,/scryfall\.com|HttpURLConnection|physical_printings/,
 'History is offline and cannot modify collection or fetch quotes');
console.log('PASS: offline, per-set and overall daily value history charts');
