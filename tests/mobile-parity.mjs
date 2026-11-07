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
for(const id of ['browse','sets','physical','shortlist','builder','library','deck','planner','workbench','archetypes','synergy','arena','performance','settings']){
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
assert.match(bridge,/scanWindowsQr\(\)/);
assert.match(bridge,/fetchWindowsSync\(String link\)/);
assert.match(bridge,/arcane-vault-sync/);
assert.match(gradle,/play-services-code-scanner:16\.1\.0/);

assert.equal(html.trimEnd().endsWith('</body></html>'),true,'No stray content may follow closing HTML');
assert.match(gradle,/versionName '2\.0\.3'/);
assert.match(gradle,/versionCode 23/);
for(const marker of ['physical_ownership','synchronized void physicalOwnership','ownershipTable(p)','if(oldVersion<2)'])assert.ok(backend.includes(marker),'Native DB missing: '+marker);
assert.ok(bridge.includes('/api/catalog/physical-ownership'));
// Exercise the shipping CSV parser + printing normalization, not a duplicate implementation.
const pStart=html.indexOf('const PHYSICAL_KEY='),pEnd=html.indexOf('const index=new Map()',pStart);
const csvStart=html.indexOf('function parseCsv('),csvEnd=html.indexOf('async function importCsv(',csvStart);
assert.ok(pStart>0 && pEnd>pStart && csvStart>0 && csvEnd>csvStart);
const context=vm.createContext({load:()=>[],save:()=>{},fetch:async()=>({ok:true}),document:{},console});
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
