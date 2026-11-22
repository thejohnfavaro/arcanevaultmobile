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
assert.match(workspace,/catalog\.commanderCoverUrl\(cardIdentity\)/,'Artwork lookup must use the normalized exact local catalog identity');
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
assert.match(workspace,/final int groupCount=sectionCopies;/,
  'Section headings must preserve the computed draft-only quantity');
assert.match(workspace,/groupCount\+" cards"/,
  'Expandable heading and jump labels must reflect live draft quantities');
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
assert.match(workspace,/deckCoverTargets\.add\(new DeckCoverTarget\(artImage,cardName,true\)\)/,
  'Artwork rows should reuse lazy card art loading with recoverable draft requests');
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
assert.match(workspace,/if\(coverInFlight\.size\(\)>=2\)break/,
  'Only two visible image requests should be queued for the two cover workers');
assert.match(workspace,/draftArtRetryAfter\.getOrDefault\(target\.commander,0L\)/,
  'Failed draft images must use a timed backoff rather than permanent exclusion');
assert.match(workspace,/Math\.min\(60000L,6000L\*failures\)/,
  'Retry backoff must be bounded to avoid hammering Scryfall');
assert.match(workspace,/"Retry missing artwork"/,
  'Art and Grid views need an explicit recovery action');
assert.match(workspace,/draftArtRetryAfter\.clear\(\)/,
  'The retry action must reset failed artwork backoff');
assert.match(workspace,/artImage\.addOnAttachStateChangeListener/,
  'Artwork must begin loading when a rebuilt editor thumbnail attaches');
assert.match(workspace,/String cardIdentity=NativeArenaCardName\.identity\(commander\)/,
  'Annotated draft entries must resolve to their canonical card name for artwork');
assert.match(workspace,/catalog\.commanderCoverUrl\(cardIdentity\)/,
  'Card art SQL must use canonical identity rather than Arena printing notes');
assert.match(workspace,/catalog\.cardDetailsForReviewMention\(cardIdentity\)/,
  'Double-faced fallback must also use the canonical identity');
assert.match(workspace,/draftArtFailures\.remove\(commander\)/,
  'Successful art loads should clear the draft failure state');
assert.match(workspace,/candidate\.image\.setImageBitmap\(result\)/,
  'A completed artwork request must paint the live thumbnail immediately');
assert.match(workspace,/candidate\.image\.isAttachedToWindow\(\)/,
  'Completed images must only be assigned to still-attached card rows');

// Type navigation is UI-only; all three visual modes share the same state.
assert.match(workspace,/final Set<String> collapsedDraftGroups=new HashSet<>\(\)/,
  'Section collapsed state must survive in-editor redraws');
assert.match(workspace,/HorizontalScrollView jumpScroll=VaultNativeStyle\.horizontalScroll\(activity\)/,
  'A themed, horizontally scrolling quick-jump strip should fit mobile screens');
assert.match(workspace,/groupHeading\.setOnClickListener\(v->\{/,
  'Every occupied type section must offer a collapse toggle');
assert.match(workspace,/sectionBody\.setVisibility\(collapse\?View\.GONE:View\.VISIBLE\)/,
  'Collapsed sections must truly hide rows, not remove draft entries');
assert.match(workspace,/jump\.setContentDescription\("Jump to "\+group\)/,
  'Quick-jump buttons should identify their target to screen readers');
assert.match(workspace,/if\(collapsedDraftGroups\.contains\(group\)\)\s*groupHeading\.performClick\(\)/,
  'Jumping to a collapsed type must expand it');
assert.match(workspace,/workspaceScroll\.smoothScrollTo\(0,Math\.max\(0,destination\)\)/,
  'Jump controls must scroll to the real section location');
assert.match(workspace,/sectionBody\.addView\(cardGrid\)/,
  'Grid cards must be parented inside the collapsible section');
assert.match(workspace,/VaultNativeStyle\.margin\(sectionBody,cardRow,3,activity\)/,
  'List and Art + Details rows must use the same collapsible section');
assert.match(workspace,/if\(!collapse\)workspaceScroll\.post\(this::loadVisibleDeckCovers\)/,
  'Expanding an art section must request newly visible thumbnails');

// Bulk section controls are presentation-only and reuse the same header
// callbacks, so section state stays correct after a view switch or draft edit.
assert.match(workspace,/"Collapse All"/,
  'Visual editor must provide a bulk collapse control');
assert.match(workspace,/"Expand All"/,
  'Visual editor must provide a bulk expand control');
assert.match(workspace,/if\(heading!=null&&!collapsedDraftGroups\.contains\(type\)\)/,
  'Collapse All must skip sections that are already collapsed');
assert.match(workspace,/if\(heading!=null&&collapsedDraftGroups\.contains\(type\)\)/,
  'Expand All must reopen only collapsed sections');
assert.match(workspace,/heading\.performClick\(\)/,
  'Bulk actions must reuse verified single-section toggle logic');
assert.match(workspace,/workspaceScroll\.post\(this::loadVisibleDeckCovers\)/,
  'Expanded artwork must be eligible for existing visible-only loader');
assert.match(workspace,/"DECK COMPOSITION · "\+populatedGroups\+" card types"/,
  'Composition summary must show populated type count');
assert.match(workspace,/sectionTotal\+=member\.optInt\("qty",0\)/,
  'Composition must use unsaved draft copy counts, not ownership');
assert.match(workspace,/composition\.append\(type\)\.append\(" "\)\.append\(sectionTotal\)/,
  'Composition must expose card counts for each populated type');

// Editor and saved deck details share the same catalog-grounded mana curve
// and quantity-weighted, nonland spell color identity readout.
assert.match(workspace,/"Deck Insights  ▾"/,
  'Deck editor should offer optional insight charts');
assert.match(workspace,/"Refresh Insights"/,
  'Unsaved draft insights should be manually refreshable');
assert.match(workspace,/catalog\.deckInsights\(commander,\s*NativeDeckDraft\.parseEntries\(snapshot\)\)/,
  'Draft insights must analyze validated unsaved text');
assert.match(workspace,/request!=draftInsightRequests\[0\]/,
  'Stale insight requests must not replace later edits or leave-state');
assert.match(workspace,/!snapshot\.equals\(lines\.getText\(\)\.toString\(\)\)/,
  'An edited draft must invalidate its previous insights');
assert.match(workspace,/renderDeckInsights\(report,draftInsightsPanel\)/,
  'Draft insights must reuse the saved deck chart renderer');
assert.match(workspace,/"SPELL COLOR IDENTITY"/,
  'Charts must include spell color identity');
assert.match(workspace,/"Nonland spells only · multicolor cards count once per color/,
  'Color insight labels must distinguish multicolor and lands from mana sources');
const catalogInsights=read('app/src/main/java/com/arcanevault/mobile/CatalogStore.java');
assert.match(catalogInsights,/int\[\] identityCounts=new int\[6\]/,
  'Read-only catalog analysis should count five colors and colorless');
assert.match(catalogInsights,/if\(colors==null\|\|colors\.isEmpty\(\)\)identityCounts\[5\]\+=qty/,
  'Known colorless nonland cards must count in the colorless category');
assert.match(catalogInsights,/identityCounts\[ci\]\+=qty/,
  'Spell color identity must be quantity-weighted');
assert.match(catalogInsights,/\.put\("colorIdentity",colorIdentity\)/,
  'The color histogram should be included in deck insight reports');
assert.match(catalogInsights,/NativeDeckManaPips\.count\(mana\)/,
  'Windows-parity colored pip demand must read cached Scryfall mana costs');
assert.match(catalogInsights,/\.put\("coloredPipDemand",coloredPipDemand\)/,
  'Spell colored symbol demand must be included in deck reports');
assert.match(workspace,/"COLORED CASTING-COST DEMAND"/,
  'Deck insights must distinguish casting cost demand from card identity');
assert.match(workspace,/"Approximate: hybrid\/Phyrexian symbols discounted; not cast odds."/,
  'Colored mana costs must be labeled approximate and not cast odds');
const pipCode=read('app/src/main/java/com/arcanevault/mobile/NativeDeckManaPips.java');
assert.match(pipCode,/String cost=manaCost\.split\(" \/\/ ",2\)\[0\]/,
  'Mana pip analysis must use only the first face of split costs');

assert.doesNotMatch(workspace,/localStorage\.setItem\([^\n]*cover/i,
  'Cover preview must not persist separate deck or artwork records');
console.log('PASS: Android Deck Library displays lazy, safe commander artwork deck-box previews');
