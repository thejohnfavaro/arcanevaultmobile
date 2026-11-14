import assert from 'node:assert/strict';
import fs from 'node:fs';
const catalog=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/CatalogStore.java','utf8');
const cards=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeCatalogScreen.java','utf8');
const browser=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeCommanderBrowserScreen.java','utf8');
const shell=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java','utf8');
for(const pattern of [
 /options\.put\("mvMin",selectedMvMin\)/,
 /options\.put\("mvMax",selectedMvMax\)/,
 /filterField\("MANA VALUE \/ CMC MIN"/,
 /filterField\("MANA VALUE \/ CMC MAX"/,
 /flippableArt\(/,
 /imageBack/,
 /Flip double-faced card/,
 /showInlinePrintingDetail/
])assert.match(cards,pattern);
for(const phrase of [
 'Double.parseDouble(mvMin)','Double.parseDouble(mvMax)',
 'c.mv>=?','c.mv<=?','v.image_back','JSONObject commanders(Map<String,String> p)'
])assert.ok(catalog.includes(phrase),'Missing shared catalog feature '+phrase);
assert.match(browser,/showPreview\(card\)/);
assert.match(browser,/NativeCommanderBrowserScreen\(/);
assert.match(browser,/Flip commander card to alternate face/);
assert.match(browser,/listScroll\.post/);
assert.match(browser,/NativeCommanderBrowserScreen/);
assert.match(shell,/new NativeCommanderBrowserScreen/);
assert.match(shell,/"EDH Lab"/);
console.log('PASS: Android combined mana-value filters, paired face flip, native commander preview and page navigation');
