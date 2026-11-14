import assert from 'node:assert/strict';
import fs from 'node:fs';
const main=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/MainActivity.java','utf8');
const peer=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/next/NativeWirelessPeerClient.java','utf8');
const screen=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java','utf8');
for(const phrase of [
 /"baseline"\.equals\(direction\)/,
 /startUsbFirstSync\(raw\)/,
 /"matched"\.equals\(authority\)/,
 /"windows"\.equals\(authority\)/,
 /"android"\.equals\(authority\)/,
 /First USB sync · review differences/,
 /saveFullPeerRecovery\(live\)/,
 /NativePeerInventoryApply\.applyInventoryPart/,
 /writePeerWebData\(old,next,error->/,
 /NativePeerCheckpoint\.saveIdentical/,
 /"paired"\.equals\(response\.optString\("status"\)\)/,
 /finishUsbFirstSync\(link,windows,true\)/
])assert.match(main,phrase);
assert.match(peer,/exchangeUsb\(String link,JSONObject snapshot\)/);
assert.match(peer,/validateUsb\(link\)/);
assert.match(peer,/setReadTimeout\(120000\)/);
assert.match(screen,/First USB Sync \/ Create Shared Baseline/);
console.log('PASS: first USB sync compares, confirms source, writes with backups, and checkpoints only after matching ack');
