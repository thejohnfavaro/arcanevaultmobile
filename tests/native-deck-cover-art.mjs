import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=(p)=>fs.readFileSync(p,'utf8');
const workspace=read('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java');
const catalog=read('app/src/main/java/com/arcanevault/mobile/CatalogStore.java');
const shell=read('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java');

assert.match(workspace,/deckCoverRow\(d,qty,pageGeneration\)/,
  'Saved decks must display commander-art deck-box cards instead of text-only rows');
for(const part of [
  'String commander=deck.optString("commander","").trim()',
  'new ImageView(activity)',
  'cover.setClipToOutline(true)',
  'art.setScaleType(ImageView.ScaleType.CENTER_CROP)',
  'entry.setOnClickListener(v->showDeck(deck))',
  'commander.isEmpty()?"Commander not specified"',
  'count+" cards · "+folder',
  'ribbon',
])assert.ok(workspace.includes(part),'Missing deck cover presentation: '+part);
assert.match(workspace,/catalog\.commanderCoverUrl\(commander\)/,'Lookup must use local catalog');
assert.match(catalog,/String commanderCoverUrl\(String commanderName\)/);
assert.match(catalog,/SELECT image FROM cards WHERE name=\? COLLATE NOCASE LIMIT 1/,
  'Only exact commander names are allowed to drive art selection');
assert.match(catalog,/SELECT image FROM variants WHERE name=\? COLLATE NOCASE/,
  'Catalog variants may provide fallback commander art');
assert.match(catalog,/url\.startsWith\("https:\/\/cards\.scryfall\.io\/"/,
  'Only trusted Scryfall art endpoints may be returned');
assert.match(workspace,/workspaceScroll\.setOnScrollChangeListener/,
  'Lazy cover loading must react to scroll');
assert.match(workspace,/target\.image\.getGlobalVisibleRect\(visible\)/,
  'Only visible covers may be requested');
assert.match(workspace,/coverWorkers=Executors\.newFixedThreadPool\(2\)/,
  'Cover fetch concurrency must be bounded');
assert.match(workspace,/return size\(\)>32;/,
  'Decoded cover memory cache must be bounded');
assert.match(workspace,/options\.inSampleSize=4/,
  'Decode small thumbnails rather than full-resolution card images');
assert.match(workspace,/artwork\.get\(url\)/,
  'Use the existing shared Scryfall download/cache implementation');
assert.match(workspace,/pageGeneration==deckPageGeneration/,
  'Stale cover updates must not replace content after navigation');
assert.match(workspace,/coverWorkers\.shutdownNow\(\)/,
  'Cover workers must shut down on workspace close');
assert.match(shell,/NativeWorkspaceScreen\.create\(activity,prefs,catalog,artwork,openLegacy/,
  'Native workspace must receive existing artwork cache rather than duplicating assets');

assert.doesNotMatch(workspace,/localStorage\.setItem\([^\n]*cover/i,
  'Cover preview must not persist separate deck or artwork records');
console.log('PASS: Android Deck Library displays lazy, safe commander artwork deck-box previews');
