import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(p,'utf8');
const store=read('app/src/main/java/com/arcanevault/mobile/CatalogStore.java');
const workspace=read('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java');
const shell=read('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java');
assert.match(store,/physicalSetSignatures\(db\)/,'Compare old/new printing identities by set');
assert.match(store,/changed\.removeIf\(code->java\.util\.Objects\.equals/,'Keep unchanged sets valid');
assert.match(store,/invalidateChangedSets\("physical",changed\)/,'Target physical changes');
assert.match(store,/invalidateChangedSets\("arena",affected\)/,'Target Arena changes');
assert.match(store,/dirty_"\+source/,'Persist dirty set identity across visits');
assert.match(store,/refreshDirtySetProgress\(String source\)/,'Recompute only dirty sets');
assert.match(store,/indexedSetProgress\(code,source\)/,'Single-set recomputation');
assert.match(store,/revision_"\+source/,'Discard results invalidated during computation');
assert.match(store,/invalidateSetProgress\("all"\)/,'Catalog sync may invalidate all sets');
assert.match(workspace,/catalog\.cachedSetOverview\(source,filter\)/,'Cache-first navigation');
assert.match(workspace,/catalog\.refreshDirtySetProgress\(source\)/,'Background refresh on dirty codes');
assert.match(workspace,/refreshing\.contains\(code\.toLowerCase/,'Show pending state on affected tile only');
assert.match(workspace,/previous value shown/,'Do not fake zero during revaluation');
assert.match(shell,/pendingSetFromWidget=set/,'Selected set bypasses overview queue');
assert.match(store,/COUNT\(DISTINCT collector COLLATE NOCASE\)/,
 'Physical completion must count paper collector-number slots like Windows');
assert.match(store,/p\.print_id!='' AND /,
 'Only explicit recognized IDs can authorize verified printing ownership');
assert.match(store,/unique_slot\.digital=0/,
 'Fallback identities must not credit digital-only editions');
assert.match(store,/slotMetricVersion",2/,
 'Prior name-based progress caches must not survive the metric change');
assert.match(store,/snapshot\.put\("ownedCards",aggregateOwned\)/,
 'Incremental refresh must update the collection-wide totals');
console.log('PASS: granular Set Progress cache invalidation, previous-value UI and index safety');
