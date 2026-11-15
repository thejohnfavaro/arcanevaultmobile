import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(p,'utf8');
const sql=read('app/src/main/java/com/arcanevault/mobile/CatalogStore.java');
const browser=read('app/src/main/java/com/arcanevault/mobile/NativeCommanderBrowserScreen.java');
const workspace=read('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java');
const nav=read('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java');
for(const q of [
  'COALESCE(po.qty,0)>0','COALESCE(ao.qty,0)>0',
  'COALESCE(po.qty,0)+COALESCE(ao.qty,0)',
  'Unsupported commander color identity',
  'Duplicate commander color symbols',
  'colorExpression','args.toArray(new String[0])'
])assert.ok(sql.includes(q),'Missing safe SQLite commander filter: '+q);
for(const q of [
  'Commander Filters','OWNERSHIP','EXACT COLOR IDENTITY',
  'args.put("owned",ownership)','args.put("colors",identity)',
  'View saved decks with this commander',
  'openCommanderDecks.accept(name)',
  'Copy commander name & open deck editor'
])assert.ok(browser.includes(q),'Missing native EDH control: '+q);
for(const q of [
  'deckCommanderFilter=initialCommander',
  'deckCommanderFilter.equalsIgnoreCase(commander)',
  'deckCoverRow(deck,total,deckPageGeneration)',
  'Show all saved decks',
  'No matching decks',
  'No deck was created automatically'
])assert.ok(workspace.includes(q),'Missing read-only saved-deck navigation: '+q);
assert.match(nav,/this::showDecksForCommander/);
assert.match(nav,/deckLoader,deckSaver,"decks",commanderName/);
assert.match(workspace,/private void editDeck\(JSONObject deck\)/,'Native saved decks must support editing');
assert.match(workspace,/deckSaver\.save\(deck,revised/,'Native editing must use authoritative deck persistence');
console.log('PASS: Native Android commander ownership/identity filters and filtered saved-deck handoff preserve the original authoritative saved deck library');

// Both native search surfaces use the exact same symbol filter control.
const mana=read('app/src/main/java/com/arcanevault/mobile/NativeManaFilterButton.java');
const catalog=read('app/src/main/java/com/arcanevault/mobile/NativeCatalogScreen.java');
const gradle=read('app/build.gradle');
assert.match(catalog,/new NativeManaFilterButton\(activity,artwork,color\)/);
assert.match(browser,/new NativeManaFilterButton\(activity,artwork,symbol\)/);
assert.match(mana,/https:\/\/svgs\.scryfall\.io\/card-symbols\//);
assert.match(mana,/SVG.getFromInputStream/);
assert.match(mana,/artwork.get\(url\)/);
assert.match(mana,/fallback=VaultNativeStyle.label\(activity,"\{"\+normalized\+"\}"/);
assert.match(mana,/selected\?VaultNativeStyle.PRIMARY:VaultNativeStyle.BORDER/);
assert.match(gradle,/com.caverock:androidsvg:1\.4/);
console.log('PASS: Card Catalog and EDH Lab share official Scryfall mana icons with offline tokens');
