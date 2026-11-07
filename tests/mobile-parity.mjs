import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const html=fs.readFileSync(path.join(root,'app/src/main/assets/index.html'),'utf8');
const backend=fs.readFileSync(path.join(root,'app/src/main/java/com/arcanevault/mobile/CatalogStore.java'),'utf8');
const bridge=fs.readFileSync(path.join(root,'app/src/main/java/com/arcanevault/mobile/MainActivity.java'),'utf8');
const gradle=fs.readFileSync(path.join(root,'app/build.gradle'),'utf8');
// All scripts must parse and every navigation destination must have a visible section.
for(const [,body] of html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)){
 if(body.trim().startsWith('{'))continue;
 new vm.Script(body);
}
for(const id of ['browse','sets','physical','shortlist','builder','library','deck','planner','workbench','archetypes','synergy','performance','settings']){
 assert.ok(html.includes('id="'+id+'"'),'Missing section '+id);
 assert.ok(html.includes('data-tab="'+id+'"'),'Missing navigation '+id);
}
for(const id of ['avNavSearch','avSidebarCompact','avMobileNavToggle','avFavoriteLinks','physicalCsv','physicalRows','setSourceToggle','perfEditDeck','perfManualAdd','perfImport','arenaScope'])assert.ok(html.includes('id="'+id+'"'),id);
assert.match(html,/Arena Only<\/option><option value="physical">Physical Only<\/option>/);
assert.match(html,/setCollectionSourceV1/);
assert.ok(html.includes("'&source='+source"));
assert.match(html,/if\(source==='physical'\)await postPhysicalOwnership/);
assert.match(html,/setCardRequest\+\+/);
assert.match(html,/full-card|av-hand-art/i);
assert.match(html,/physicalCards,manualMatches,favorites:/);
assert.match(html,/id:String\(d.id\|\|makeDeckId\(\)\)/);
assert.match(html,/const hasApk=.*\.apk/);
assert.match(html,/overflow-x:hidden/);
for (const id of ['physicalScanWindowsQr','physicalWindowsQrUrl','physicalQrPreview','physicalQrImport','physicalQrUndo']) assert.ok(html.includes('id="'+id+'"'),'Missing QR collection control '+id);
assert.match(html,/function portableQrValidated\(/);
assert.match(html,/function portableQrApply\(/);
assert.match(html,/physicalInit\(\);physicalQrInit\(\)/);
assert.match(bridge,/SCRYFALL_MIN_GAP_MS = 700L/,'Live Scryfall calls must be paced');
assert.match(bridge,/synchronized\(scryfallRequestGate\)/,'Live Scryfall calls must share a limiter');
assert.match(bridge,/scryfallCooldownUntil/,'Rate limits must activate cooldown');
assert.match(bridge,/status==429 \|\| status==403 \|\| status==400/,'Rate limits must not be retried');
assert.match(bridge,/retryAfterSeconds/,'Clients must receive cooldown length');
assert.match(html,/Previously synced cards are saved; rerun to continue/,'UI must preserve incomplete metadata refresh');
assert.match(bridge,/scanWindowsQr\(\)/);
assert.match(bridge,/fetchWindowsSync\(String link\)/);
assert.match(bridge,/arcane-vault-sync/);
assert.match(bridge,/127\.0\.0\.1/);
assert.ok(html.includes('id="physicalSyncJsonFile"'),'Missing USB file-transfer import control');
assert.match(html,/physicalSyncJsonFile'\)\.onchange/);
assert.match(gradle,/play-services-code-scanner:16\.1\.0/);
for(const id of ['avSyncAllData','avSyncAllStatus']) assert.ok(html.includes('id="'+id+'"'),'Missing unified sync control '+id);
assert.match(html,/function avSyncAllCardData\(/);
assert.match(html,/await syncAll\(true\)/);
assert.match(html,/if\(!ready\.hasPrintings\)/);
assert.match(html,/if\(\(wasBusy&&!catalogStatus\.busy/);
assert.match(backend,/\.put\("printings",printings\)/);
assert.match(backend,/\.put\("hasPrintings",printings>0\)/);
assert.match(bridge,/new HttpServerLite\(APP_PORT\)/,'App must use stable localhost origin');
assert.match(bridge,/APP_PORT = 18765/,'Stable port must be explicit');
assert.match(bridge,/WebStorage\.getInstance\(\)\.getOrigins/,'Previous WebView origins must be inspected for recovery');
assert.match(bridge,/new WebResourceResponse\(/,'Migration must read each prior origin without requiring old random port listener');
assert.match(bridge,/writeCollection\(String key,String json\)/,'Native inventory persistence must be available');
assert.match(bridge,/\.commit\(\)/,'Inventory writes must confirm durable commit');
assert.match(bridge,/deleteOrigin\(origin\)/,'Redundant old origins must be cleaned');
assert.match(bridge,/catalog\.recoverIndexedOwnership\(\)/,'Recover missed inventories from indexed ownership');
assert.match(bridge,/if\(current!=null\)continue/,'Never overwrite a deliberately empty collection');
assert.match(backend,/JSONObject recoverIndexedOwnership\(\)/,'Catalog must expose recovery fallback');
assert.match(html,/localStorage\.getItem\(key\)===String\(values\[key\]\)/,'Legacy cleanup must verify exact preference values');

assert.match(html,/nativeCollectionKey=key/,'Inventories must be stored natively');
assert.match(html,/ArcaneAndroid\.writeCollection/,'Browser must commit collections to native storage');
assert.match(html,/localStorage\.removeItem\('vault_'\+key\)/,'Inventories must not retain duplicate WebView copies');
assert.match(html,/id="avStorageRecoveryStatus"/,'Recoverability status must be visible');

assert.ok(html.includes('id="setHideZero"'),'Missing hide zero-progress sets checkbox');
assert.ok(html.includes('setRows.filter(r=>Number(r.owned)>0)'),'Zero-progress sets must be hidden');
assert.ok(html.includes("save('setHideZeroV1'"),'Set Progress filter must persist');


assert.equal(html.trimEnd().endsWith('</body></html>'),true,'No stray content may follow closing HTML');
assert.match(gradle,/versionName '2\.0\.11'/);
assert.match(gradle,/versionCode 31/);
for(const marker of ['physical_ownership','synchronized void physicalOwnership','printingCte(p)','if(oldVersion<2)'])assert.ok(backend.includes(marker),'Native DB missing: '+marker);
assert.ok(bridge.includes('/api/catalog/physical-ownership'));
// Exercise the shipping CSV parser + printing normalization, not a duplicate implementation.
const pStart=html.indexOf('const PHYSICAL_KEY='),pEnd=html.indexOf('const index=new Map()',pStart);
const csvStart=html.indexOf('function parseCsv('),csvEnd=html.indexOf('async function importCsv(',csvStart);
assert.ok(pStart>0 && pEnd>pStart && csvStart>0 && csvEnd>csvStart);
const context=vm.createContext({load:()=>[],save:()=>{},fetch:async()=>({ok:true}),document:{},window:{},console});
vm.runInContext(html.slice(csvStart,csvEnd)+html.slice(pStart,pEnd)+';globalThis.exportFunctions={physicalCsvRows,physicalNormalize,physicalOwnership,physicalOwnedCount};',context);
const f=context.exportFunctions;
const manabox=f.physicalCsvRows('Binder Name,Binder Type,Name,Set code,Collector number,Foil,Quantity\nMain,Binder,Sol Ring,cmm,1,etched,2\nWishlist,List,Black Lotus,lea,1,normal,1');
assert.equal(manabox.source,'ManaBox');assert.equal(manabox.entries.length,1);assert.equal(manabox.entries[0].qty,2);assert.equal(manabox.entries[0].finish,'etched');assert.equal(manabox.skipped,1);
const mythic=f.physicalCsvRows('Card Name,Set Code,Set Name,Collector Number,Rarity,Language,Quantity,Condition,Finish,Scryfall ID,Container Type,Container Name\nSol Ring,CMM,Commander Masters,1,uncommon,en,2,NM,nonfoil,abc,box,bulk');
assert.equal(mythic.source,'Mythic Tools');assert.equal(mythic.entries[0].location,'bulk');
const second=f.physicalCsvRows('Count,Name,Edition,Collector Number,Foil\n1,Sol Ring,CMM,1,foil\n2,Sol Ring,CMM,1,foil');
assert.equal(second.source,'Moxfield');assert.equal(second.entries.length,1);assert.equal(second.entries[0].qty,3);
assert.throws(()=>f.physicalCsvRows('Name,Quantity\nSol Ring,-1'),/No positive-quantity/);
assert.throws(()=>f.physicalCsvRows('Name\nSol Ring'),/No quantity/);
const matchesStart=html.indexOf('function normalizeManualMatches('),matchesEnd=html.indexOf('function perfDeckOptions(',matchesStart);
assert.ok(matchesStart>0 && matchesEnd>matchesStart);
const matchesCtx=vm.createContext({Math,Date});vm.runInContext(html.slice(matchesStart,matchesEnd)+';globalThis.norm=normalizeManualMatches;',matchesCtx);
const rows=matchesCtx.norm([{matchId:'id1',result:'win'},{matchId:'id1',result:'loss'},{matchId:'id2',result:'draw'}]);assert.equal(rows.length,2);
console.log('PASS: Android 2.0 grouped navigation, physical CSV formats, Set Progress scopes, isolated SQLite ownership, backup and manual match deduplication');

// Set Progress release-date sorting contract: both scopes use the catalog's earliest
// printing date, missing dates sort last, and preferences survive navigation.
assert.match(html,/value="date-desc">Release date \(newest first\)/);
assert.match(html,/value="date-asc">Release date \(oldest first\)/);
assert.match(html,/const da=\/\^\\d\{4\}-\\d\{2\}-\\d\{2\}\$\/\.test\(a\.releaseDate/);
assert.match(html,/save\('setSortV1',\$\('setSort'\)\.value\)/);
assert.match(html,/Released \$\{escapeHtml\(r\.releaseDate\)\}/);
assert.match(backend,/\.put\("releaseDate",r\.isNull\(4\)\?"":r\.getString\(4\)\)/);
console.log('PASS: Set Progress release-date sort and catalog date plumbing');


// Printing-specific progress cannot be credited through card names alone.
assert.match(backend,/physical_printings/);
assert.match(backend,/if\(oldVersion<3\)/);
assert.match(backend,/v\.print_id=p\.print_id/);
assert.match(backend,/v\.setcode=p\.setcode AND v\.collector=p\.collector/);
assert.match(backend,/LEFT JOIN confirmed o ON o\.name=d\.name AND o\.setcode=d\.setcode/);
assert.match(backend,/LEFT JOIN confirmed o ON o\.name=sc\.name AND o\.setcode=\?/);
assert.match(html,/function postPhysicalOwnership\(\)\{const payload=physicalCards;/);
console.log('PASS: printing-identified physical ownership with migration and exact set matching');


// Multi-face card detail lookups must match the exact composite name and
// retry by first face only after a full-name miss.
assert.match(html,/function avCardNameParts\(/);
assert.match(html,/function avCardMatchesName\(/);
assert.match(html,/function avResolveCardResult\(/);
assert.match(html,/const retry=await avSyncBatchRequest\(\[first\]\)/);
assert.match(html,/const raw=avResolveCardResult\(c\.name,data\.data\)/);
const nameStart=html.indexOf('function avCardNameParts(');
const nameEnd=html.indexOf('async function avSyncBatchRequest(',nameStart);
assert.ok(nameStart>0&&nameEnd>nameStart);
const nameCtx={};
vm.createContext(nameCtx);
vm.runInContext(html.slice(nameStart,nameEnd)+';globalThis.avTest={avCardMatchesName,avResolveCardResult};',nameCtx);
const split={name:'Fire // Ice',card_faces:[{name:'Fire'},{name:'Ice'}]};
const adventure={name:'Brazen Borrower',card_faces:[{name:'Brazen Borrower'},{name:'Petty Theft'}]};
assert.equal(nameCtx.avTest.avCardMatchesName('Fire // Ice',split),true);
assert.equal(nameCtx.avTest.avCardMatchesName('Fire // Water',split),false);
assert.equal(nameCtx.avTest.avCardMatchesName('Brazen Borrower // Petty Theft',adventure),true);
assert.equal(nameCtx.avTest.avCardMatchesName('Fire // Ice',{name:'Fire'}),false);
console.log('PASS: verified multi-face name resolution');


// Android must not expose desktop-only Arena log scraping/connection UI.
assert.doesNotMatch(html,/data-tab="arena"/);
assert.doesNotMatch(html,/<section id="arena"/);
assert.doesNotMatch(html,/\/api\/arena\/(?:config|status)/);
assert.doesNotMatch(html,/arenaFetch\(/);
assert.doesNotMatch(html,/Connect Arena and track results/);
assert.match(html,/data-tab="performance"/);
assert.match(html,/id="perfManualAdd"/);
assert.match(html,/id="perfImport"/);
assert.match(html,/id="perfImportButton"/);
assert.match(html,/\$\('perfImportButton'\)\.onclick=/);
assert.match(html,/id="perfExport"/);
assert.match(html,/function normalizeManualMatches\(/);
assert.match(html,/Match History/);
console.log('PASS: mobile-only navigation excludes Windows log connector and retains manual match history');


// UX refresh contract: existing data models, printing IDs and collection routines stay intact.
for(const id of ['uxDataDashboard','uxSyncInventory','uxSyncMetadata','uxSyncCatalog','uxSyncPhysical','uxSyncState','avSyncAllData','avFetchAllDetails','setGrid','detailInner']){
 assert.ok(html.includes('id="'+id+'"'),'Missing UX control '+id);
}
assert.match(html,/function avCardDetailsLayout\(/);
assert.match(html,/function avRefreshSyncDashboard\(/);
assert.match(html,/data-ux-trigger="avFetchAllDetails"/);
assert.match(html,/data-ux-trigger="avSyncAllData"/);
assert.match(html,/class="ux-set-percent"/);
assert.match(html,/setAttribute\('role','tabpanel'\)/);
assert.match(html,/\.ux-detail-panel\[hidden\]/);
assert.match(html,/function physicalSort/);
for(const direction of ['price-desc','price-asc','unit-desc','unit-asc']) assert.ok(html.includes('value="'+direction+'"'),'Missing price sorting '+direction);

for(const target of ['browse','sets','builder','menu','settings'])assert.ok(html.includes('data-ux-bottom="'+target+'"'));
assert.match(html,/function avUxBottomSelect\(/);
assert.match(html,/async function physicalRefreshPrices\(/);
assert.match(html,/physicalPrices=load\(PHYSICAL_PRICE_KEY/);
assert.match(html,/save\(PHYSICAL_PRICE_KEY,physicalPrices\)/);
assert.match(html,/if\(\(aa===null\)!==\(bb===null\)\)return aa===null\?1:-1/);
assert.match(html,/c\.scryfallId\?\{id:c\.scryfallId\}:\{set:c\.set\.toLowerCase\(\),collector_number:String\(c\.number\)\}/);

console.log('PASS: UX refresh navigation, card tabs, sync health and physical price sorting');
