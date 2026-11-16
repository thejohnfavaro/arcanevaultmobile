import assert from 'node:assert/strict';
import fs from 'node:fs';
const activity=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/MainActivity.java','utf8');
assert.match(activity,/savedSyncBaselines\(\)/);
assert.match(activity,/new java\.io\.File\(getFilesDir\(\),"sync-snapshots"\)/);
assert.match(activity,/listFiles\(\(dir,name\)->/);
assert.match(activity,/ArcaneVault-Android-/);
assert.match(activity,/Saved baseline found automatically/);
assert.match(activity,/applyWithSavedSyncBaseline\(suggested\)/,
    'User-confirmed suggested baseline should avoid a redundant second confirmation');
assert.match(activity,/showSavedSyncBaselineOptions\(\)/);
assert.match(activity,/selectWirelessBaselineFileManually\(\)/);
assert.match(activity,/readSavedSyncBaseline\(file\)/);
assert.match(activity,/applyPeerInventorySnapshot\(\(\)->incoming,\(\)->baseline\)/);
assert.match(activity,/Select last shared baseline \(/,
    'Selection list must identify that these are candidate common baselines');
assert.match(activity,/\.setItems\(choices,/,
    'Saved baseline picker must remain a compact list of choices');
assert.match(activity,/Use this only if it is the last/);
assert.match(activity,/NativePeerCheckpoint\.exists\(this,incoming\.optString/);
assert.match(activity,/PEER_WIRELESS_BASE_PICK/);
console.log('PASS: USB peer baseline discovery uses in-app snapshots with explicit common-state confirmation and manual fallback');
