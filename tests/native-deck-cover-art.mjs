import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=(p)=>fs.readFileSync(p,'utf8');
const workspace=read('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java');
const catalog=read('app/src/main/java/com/arcanevault/mobile/CatalogStore.java');
const shell=read('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java');

assert.match(workspace,/deckCoverRow\(deck,total,deckPageGeneration\)/,
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
assert.match(workspace,/NativeArtworkQuality\.load\(artwork,url,/,
  'Deck covers use adaptive sampling instead of forced quarter-resolution');
assert.match(read('app/src/main/java/com/arcanevault/mobile/NativeArtworkQuality.java'),/cache\.get\(url\)/,
  'Adaptive decoding uses the existing Scryfall download/cache implementation');
assert.match(workspace,/pageGeneration==deckPageGeneration/,
  'Stale cover updates must not replace content after navigation');
assert.match(workspace,/coverWorkers\.shutdownNow\(\)/,
  'Cover workers must shut down on workspace close');
assert.match(shell,/NativeWorkspaceScreen\.create\(activity,prefs,catalog,artwork,openLegacy/,
  'Native workspace must receive existing artwork cache rather than duplicating assets');

assert.match(workspace,/clearDeckSearch\.setOnClickListener\(v->search\.setText\(""\)\)/,
  'Deck search must provide a one-tap clear action');
assert.match(workspace,/ui\.postDelayed\(deckSearchPending,180\)/,
  'Deck search should coalesce rapid keystrokes rather than rebuilding every row');
assert.match(workspace,/deckLibraryRestoreScroll=workspaceScroll\.getScrollY\(\)/,
  'Opening a deck must retain the scroll position of the library');
assert.match(workspace,/workspaceScroll\.scrollTo\(0,restored\)/,
  'Returning to the library must restore its last scroll position');
assert.match(workspace,/ui\.removeCallbacks\(deckSearchPending\)/,
  'Pending search redraw must be canceled on navigation or close');

// Reuse the full, locally verified inspector for saved deck cards, without
// discarding the deck-detail views, ownership analysis or original scroll.
const inspector=read('app/src/main/java/com/arcanevault/mobile/NativeCatalogScreen.java');
assert.match(workspace,/row\("COMMANDER",commanderName,/,
  'Commander row should support direct card preview');
assert.match(workspace,/row\(card\[0\],card\[1\],\(\)->openDeckCard\(card\[2\]\)\)/,
  'Mainboard rows should open their exact card identities');
assert.match(workspace,/deckCardInspector\.inspectDeckCard\(name,this::returnFromDeckCard\)/,
  'Deck Details must open the shared complete card inspector');
assert.match(workspace,/deckDetailsRetainedViews\.add\(content\.getChildAt\(i\)\)/,
  'The live deck-detail view should survive nested inspection');
assert.match(workspace,/workspaceScroll\.scrollTo\(0,scroll\)/,
  'Return from a card should preserve Deck Details scroll');
assert.match(workspace,/if\(deckCardInspector!=null\)\{/,
  'System Back should close nested card before leaving the deck');
assert.match(inspector,/void inspectDeckCard\(String requestedName,Runnable returnToDeck\)/,
  'Inspector needs an explicit deck return path');
assert.match(inspector,/inspectLinkedCard\(requestedName,returnToDeck,"deck"\)/,
  'Deck cards should use the same full inspector as AI Review');

// Visual editing must remain an opt-in view of the existing draft text.
assert.match(workspace,/"Visual mainboard  ▾"/,
  'Editor should expose an optional visual mainboard');
assert.match(workspace,/NativeDeckDraft\.parseEntries\(lines\.getText\(\)\.toString\(\)\)/,
  'Visual mainboard should use the validated draft parser');
assert.match(workspace,/preview\.setOnClickListener\(v->openDeckCard\(cardName\)\)/,
  'Visual draft entries should link to full card previews');
assert.match(workspace,/lines\.setText\(NativeDeckDraft\.formatEntries\(updated\)\)/,
  'Quantity controls should edit the same unsaved mainboard text');
assert.match(workspace,/"Refresh visual mainboard"/,
  'Manual text changes should make the visual list refreshable');
assert.match(workspace,/catalog\.deckTypeLines\(parsed\)/,
  'Card types must be retrieved from the local catalog, not inferred from names');
assert.match(workspace,/for\(String section:NativeDeckTypeGroups\.ORDER\)/,
  'Visual mainboard must render sections in established Windows order');
assert.match(workspace,/NativeDeckTypeGroups\.classify\(visualTypeCache\.get\(key\)\)/,
  'The visible section must come from cached verified card type lines');
assert.match(workspace,/sectionCopies\+" cards"/,
  'Section headings must summarize owned-independent draft quantities');
const groups=read('app/src/main/java/com/arcanevault/mobile/NativeDeckTypeGroups.java');
for(const type of ['Creatures','Instants','Sorceries','Artifacts','Enchantments',
  'Planeswalkers','Battles','Other / Unknown','Lands'])
  assert.ok(groups.includes('"'+type+'"'),'Missing card type section: '+type);
assert.match(groups,/if\(type\.contains\("land"\)\)return "Lands"/,
  'Artifact lands and creature lands must be categorized as lands');
const store=read('app/src/main/java/com/arcanevault/mobile/CatalogStore.java');
assert.match(store,/Map<String,String> deckTypeLines\(JSONArray entries\)/,
  'The catalog must offer a bounded local type-line lookup');
assert.match(store,/SELECT type FROM cards WHERE name=\? COLLATE NOCASE LIMIT 1/,
  'Visual grouping must use exact case-insensitive local Scryfall card names');


assert.match(workspace,/if\(deckCardInspector!=null\)\{/,
  'Editor Back must first dismiss the card inspector, not discard a draft');
assert.match(workspace,/deckCardReturnState=screenState/,
  'Shared deck inspector should remember whether the origin was editing or details');

// Optional artwork rows reuse bounded artwork workers; the list is still
// just a different presentation of the same unsaved mainboard.
assert.match(workspace,/native_draft_art_rows_v1/,
  'Art/List presentation preference should persist independently of deck data');
assert.match(workspace,/"Art \+ Details"/,
  'The visual mainboard must offer an illustrated layout');
assert.match(workspace,/"List",!visualArt\[0\]/,
  'The compact list must remain directly selectable');
assert.match(workspace,/deckCoverTargets\.add\(new DeckCoverTarget\(artImage,cardName\)\)/,
  'Artwork rows should reuse lazy card art loading rather than new bulk requests');
assert.match(workspace,/workspaceScroll\.post\(this::loadVisibleDeckCovers\)/,
  'Visible art should render as the workspace scrolls');
assert.match(workspace,/artFrame\.setOnClickListener\(v->openDeckCard\(cardName\)\)/,
  'Card thumbnail should open the full linked inspector');
assert.match(workspace,/new LinearLayout\.LayoutParams\(dp\(70\),dp\(95\)\)/,
  'Art rows should use compact card thumbnail dimensions');

// Art-forward grid uses the same validated draft, group ordering, image cache,
// exact-card previews and plus/minus listeners as the other two presentations.
assert.match(workspace,/"Grid",visualGrid\[0\]/,
  'Visual editor must offer a third Grid mode');
assert.match(workspace,/native_draft_grid_v1/,
  'Selected grid presentation must persist independently of deck data');
assert.match(workspace,/screenDp>=600\?3:2/,
  'Grid should use two phone columns and three wide-screen columns');
assert.match(workspace,/cardGrid=new GridLayout\(activity\)/,
  'Card art must render as a real multi-column grid');
assert.match(workspace,/badge.setBackgroundColor\(Color.argb/,
  'Artwork tiles should show a readable copy-count badge');
assert.match(workspace,/artFrame.setOnClickListener\(v->openDeckCard\(cardName\)\)/,
  'Grid artwork should preserve full card inspection');
assert.match(workspace,/quantityRow=visualGrid\[0\]\?/,
  'Grid quantity controls should remain reachable under the art');
assert.match(workspace,/lines.setText\(NativeDeckDraft.formatEntries\(updated\)\)/,
  'Grid plus/minus controls must update the authoritative unsaved text');
assert.match(workspace,/if\(visualArt\[0\]\|\|visualGrid\[0\]\)/,
  'Grid thumbnails should use the lazy visible-art loader');

// A draft thumbnail should recover from an early unavailable catalog or art
// request instead of being permanently stuck on the fallback star.
assert.match(workspace,/new DeckCoverTarget\(artImage,cardName,true\)/,
  'Draft thumbnail targets should opt into recoverable artwork behavior');
assert.match(workspace,/draftArtFailures\.getOrDefault\(target\.commander,0\)>=2/,
  'Recovery attempts must be bounded to avoid repeated failed downloads');
assert.match(workspace,/artImage\.addOnAttachStateChangeListener/,
  'Artwork must begin loading when a rebuilt editor thumbnail attaches');
assert.match(workspace,/catalog\.cardDetailsForReviewMention\(commander\)/,
  'The art loader should resolve valid front-face-only double-faced names');
assert.match(workspace,/draftArtFailures\.remove\(commander\)/,
  'Successful art loads should clear the draft failure state');

assert.doesNotMatch(workspace,/localStorage\.setItem\([^\n]*cover/i,
  'Cover preview must not persist separate deck or artwork records');
console.log('PASS: Android Deck Library displays lazy, safe commander artwork deck-box previews');
