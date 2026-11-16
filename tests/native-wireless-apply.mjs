import assert from 'node:assert/strict';
import fs from 'node:fs';
const activity=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/MainActivity.java','utf8');
const screen=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java','utf8');
assert.match(activity,/scanWirelessPeerQrForApply\(\)/);
assert.match(activity,/wirelessPeerReview\(false,link,true\)/);
assert.match(activity,/PEER_WIRELESS_BASE_PICK = 26/);
assert.match(activity,/NativeWirelessPeerClient\s*\n\s*\.receive\(link\)/);
assert.match(activity,/chooseWirelessApplyBaseline\(remote\)/);
assert.match(activity,/pendingWirelessIncoming=null;/);
assert.match(activity,/applyPeerInventorySnapshot\(\(\)->received,data\.getData\(\)\)/);
assert.match(activity,/JSONObject remote=null,original=null;/,
    'Keep incoming snapshot available when a conflict needs review');
assert.match(activity,/remote=incoming\.load\(\)/,
    'Read incoming peer snapshot after declaring its retained reference');
assert.match(activity,/NativePeerSync\s*\n\s*\.prepareMerge\(local,remote,original,decisions\)/,
    'Three-way peer merge must pass explicit, scoped conflict decisions');
assert.match(activity,/\.describeArenaConflict\(local,incomingSnapshot,baseSnapshot,/,
    'Conflicting Arena cards must display verified baseline/Android/Windows quantities');
assert.match(activity,/\.setTitle\("Resolve Arena card conflict"\)/);
assert.match(activity,/\.setItems\(choices,/,
    'Peer conflict resolution must expose all four choice options');
for(const choice of [
    'Keep Android (this card)',
    'Use Windows (this card)',
    'Use Android for all Arena conflicts',
    'Use Windows for all Arena conflicts'
]) assert.ok(activity.includes(choice),'Missing peer-conflict choice: '+choice);
assert.match(activity,/\.chooseAllArenaConflicts\(local,incomingSnapshot,/,
    'Bulk choices must be validated against both snapshots and the baseline');
assert.match(activity,/next\.putAll\(androidAll\)/);
assert.match(activity,/next\.putAll\(windowsAll\)/);
assert.match(activity,/NativePeerInventoryApply\.applyInventoryPart\(/);
assert.match(screen,/Scan QR & review Windows changes/);
assert.match(screen,/baseline and explicit confirmation/);
const download=activity.indexOf('.receive(link)');
const approval=activity.indexOf('chooseWirelessApplyBaseline(remote)');
assert(download>=0&&approval>download,'Receiving snapshot must precede baseline selection and approval');
const checkpoint=fs.readFileSync(
    'app/src/main/java/com/arcanevault/mobile/NativePeerCheckpoint.java','utf8');
assert.match(checkpoint,/saveIdentical\(Context context,JSONObject local,/);
assert.match(checkpoint,/NativePeerSync\.preview\(local,received\)/);
assert.match(checkpoint,/changes"\)\.length\(\)!=0/);
assert.match(checkpoint,/AtomicFile/);
assert.match(checkpoint,/\.history-/);
assert.match(activity,/rememberMatchingWirelessBaseline\(remote\)/);
assert.match(activity,/NativePeerCheckpoint\.exists\(this,incoming\.optString\("deviceId",""\)\)/);
assert.match(activity,/NativePeerCheckpoint\.load\(this,received\.getString\("deviceId"\)\)/);
assert.match(activity,/applyPeerInventorySnapshot\(PeerSnapshotLoader incoming,\s*PeerSnapshotLoader baseline\)/);
console.log('PASS: QR wireless apply routes via baseline selection and existing guarded Android writeback');
