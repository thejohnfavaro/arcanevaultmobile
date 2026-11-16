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
assert.match(activity,/NativePeerSync\s*\n\s*\.prepareMerge\(local,remote,original,decisions,deckDecisions\)/,
    'Three-way peer merge must pass independently scoped Arena and deck decisions');
assert.match(activity,/\.describeArenaConflict\(local,incomingSnapshot,baseSnapshot,/,
    'Conflicting Arena cards must display verified baseline/Android/Windows quantities');
assert.match(activity,/\.setTitle\(heading\)/,
    'Compact conflict title should show card and quantities');
assert.match(activity,/\.setItems\(choices,/,
    'Conflict choices must be a visible list-only mobile dialog');
const dialogSource=activity.slice(activity.indexOf('String heading=conflict.getString("name")'),
    activity.indexOf('return;',activity.indexOf('String heading=conflict.getString("name")')));
assert.doesNotMatch(dialogSource,/\.setMessage\(/,
    'Do not add a large message pane that hides the list on mobile');
for(const choice of [
    'Keep Android for this card',
    'Use Windows for this card',
    'Use Android for all ',
    'Use Windows for all '
]) assert.ok(dialogSource.includes(choice),'Missing visible peer-conflict choice: '+choice);
assert.match(activity,/\.chooseAllArenaConflicts\(local,incomingSnapshot,/,
    'Bulk choices must be validated against both snapshots and the baseline');
assert.match(activity,/next\.putAll\(androidAll\)/);
assert.match(activity,/next\.putAll\(windowsAll\)/);
assert.match(activity,/\.describeDeckConflict\(local,incomingSnapshot,baseSnapshot,/,
    'Saved-deck conflicts must have a separate validated review');
assert.match(activity,/\.chooseAllDeckConflicts\(local,incomingSnapshot,/,
    'Bulk saved-deck choices must not be inferred from Arena choices');
for(const choice of [
    'Keep Android for this deck',
    'Use Windows for this deck',
    'Use Android for all ',
    'Use Windows for all '
]) assert.ok(activity.includes(choice),'Missing reviewed deck choice: '+choice);
assert.match(activity,/\.prepareMerge\(local,remote,original,decisions,deckDecisions\)/);
assert.match(activity,/No changes to apply/,
    'A no-op peer proposal must not be reported as a completed sync');
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
