import assert from 'node:assert/strict';
import fs from 'node:fs';
const main=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/MainActivity.java','utf8');
const data=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java','utf8');
const peer=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/next/NativePeerSync.java','utf8');
assert.match(main,/exportPeerSync\(\)/);
assert.match(main,/previewPeerSync\(\)/);
assert.match(main,/PEER_PICK/);
assert.match(main,/PEER_3WAY_PICK/);
assert.match(main,/PEER_BASE_PICK/);
assert.match(main,/previewThreeWayPeerUris/);
assert.match(data,/Three-way compare \(known shared baseline\)/);
assert.match(peer,/compareWithBaseline\(JSONObject local,JSONObject incoming,/);
assert.match(peer,/safeToAutoApply",false/);
assert.match(main,/browser\.evaluateJavascript/,'Decks/artwork remain in original WebView storage');
assert.match(main,/peer_sync_device_id_v1/,'Peer ID survives app restarts');
assert.match(data,/Export peer sync file/);
assert.match(data,/Preview Windows \/ Android peer file/);
assert.match(peer,/arcane-vault-peer-sync/);
assert.match(peer,/physicalCards/);
assert.match(peer,/deckLibrary/);
assert.match(peer,/preferredArtwork/);
assert.match(peer,/Duplicate /,'Duplicate identities should stop sync');
assert.match(peer,/safeToAutoApply",false/,'No silent auto-overwrite');
assert.doesNotMatch(peer,/\.commit\(\)|\.apply\(\)|INSERT INTO|DELETE FROM/,'Preview contract must not write inventory');
const checkpoint=fs.readFileSync(
  'app/src/main/java/com/arcanevault/mobile/NativePeerCheckpoint.java','utf8');
const delta=fs.readFileSync(
  'app/src/main/java/com/arcanevault/mobile/next/NativePeerDeltaManifest.java','utf8');
const nativeMirror=fs.readFileSync(
  'app/src/main/java/com/arcanevault/mobile/NativeDeckArtworkMirror.java','utf8');
assert.match(checkpoint,/static String overview\(Context context\)/,
  'Device sync should display only validated checkpoint metadata');
assert.match(data,/NativePeerCheckpoint\.overview\(activity\)/);
assert.match(delta,/public static JSONObject compare\(JSONObject base,JSONObject current\)/,
  'Differential protocol must remain a read-only manifest');
assert.match(delta,/baseRevision/);
assert.match(delta,/targetRevision/);
assert.match(nativeMirror,/static void saveObserved\(/,
  'Native mirror must be a verified observation, not an unchecked authority switch');
assert.match(main,/NativeDeckArtworkMirror\.saveObserved\(/);
assert.match(main,/Use Windows for all Arena \+ saved decks/);
assert.match(main,/Use Android for all Arena \+ saved decks/);
console.log('PASS: Android offline peer snapshot and no-write reconciliation wire-up');
