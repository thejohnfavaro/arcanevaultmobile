import assert from 'node:assert/strict';
import fs from 'node:fs';
const source=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java','utf8');
const catalog=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/CatalogStore.java','utf8');
const art=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/VaultSetProgressCard.java','utf8');
const fallback=source.split('private void renderCachedSetNames(')[1]?.split('private void addSetProgressCard(')[0];
const normal=source.split('private void renderSetProgress(')[1]?.split('private void showSet(')[0];
assert.ok(fallback,'Missing offline fallback');
assert.ok(normal,'Missing set overview');
assert.match(fallback,/addSetProgressCard\(card,source,setRequestId\)/,
 'Offline set directory must use the original visual progress cards');
assert.doesNotMatch(fallback,/row\(name\s*\+\s*" · "/,
 'Plain text fallback is a visual regression');
assert.match(normal,/addSetProgressCard\(set,source,request\)/,
 'Saved/indexed overviews must use the same set-card renderer');
assert.match(source,/VaultSetProgressCard\.render\(activity,set,source,width,dp\(161\)\)/,
 'Both modes render the original card treatment');
assert.match(source,/catalog\.cachedRepresentativeSetArt\(code,source\)/);
assert.match(source,/VaultSetArtwork\.prefetchArt\(activity,art\)/,
 'Background card art should warm outside UI thread');
assert.match(source,/VaultSetArtwork\.prefetchSymbol\(activity,code\)/,
 'Set symbols should warm independently from the background');
assert.match(catalog,/String resolveAndCacheSetArt\(/,
 'Art identity persists across progress cache invalidation');
assert.match(art,/VaultSetArtwork\.cachedSymbol\(context,code\)/,
 'Official set icon must be rendered');
assert.match(art,/boolean measured=set!=null&&set\.has\("total"\)&&total>0/,
 'Unindexed fallbacks must never show misleading 0%');

// Progress snapshot filtering must never reinsert unowned tiles.
assert.doesNotMatch(normal,/if\(missingOwned\)filtered=NativeSetProgressFilters\.select\(sets,false,order\)/,
 'Hide-empty sets must not silently switch to unfiltered mode');
assert.match(source,/if\(hide\)\{\s*row\("Preparing verified /,
 'Unknown ownership directory must respect hide-empty preference');
assert.match(source,/setArtWorkers\.execute\(/,'Art uses its own nonblocking workers');
assert.match(source,/VaultSetArtwork\.prefetchSymbol\(activity,code\)/,
 'Set icon must load independently before art');
assert.match(source,/VaultSetArtwork\.prefetchArt\(activity,art\)/,
 'Set artwork is prefetched separately');
assert.match(source,/redrawSetProgressArt\(tile,set,source,width,request\)/,
 'Visible cards rerender when cached assets arrive');
assert.match(art,/VaultSetArtwork\.cachedSymbol\(context,code\)/);
assert.match(art,/float scale=Math\.min\(1f,Math\.min\(600f\/targetW,240f\/targetH\)\)/,
 'Bitmap width and height must share a single uniform scale');
assert.match(source,/tile\.setScaleType\(ImageView\.ScaleType\.FIT_CENTER\)/,
 'Native ImageView must not stretch rendered text or symbols');
assert.doesNotMatch(source,/tile\.setScaleType\(ImageView\.ScaleType\.FIT_XY\)/,
 'Stretched image cards distort typography');
// Set drill-down must have its own usable viewport, without the overview
// controls occupying half the screen or a text-only card inspector.
assert.match(source,/setOverviewFilters\.setVisibility\(View\.GONE\)/,
 'Hide overview sorting and source controls in set detail');
assert.match(source,/setOverviewSearch\.setVisibility\(View\.GONE\)/,
 'Hide overview set search in set detail');
assert.match(source,/setOverviewFilters\.setVisibility\(View\.VISIBLE\)/,
 'Restore overview controls on return to sets');
assert.match(source,/setOverviewSearch\.setVisibility\(View\.VISIBLE\)/,
 'Restore overview search on return to sets');
const catalogInspector=fs.readFileSync(
 'app/src/main/java/com/arcanevault/mobile/NativeCatalogScreen.java','utf8');
const companion=fs.readFileSync(
 'app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java','utf8');
assert.match(source,/\.inspectSetPrinting\(/,
 'Set card rows must enter the full Catalog inspector');
assert.match(companion,/nativeCatalog\.inspectSetPrinting\(/,
 'Handoff must preserve the actual Set Progress printing');
assert.match(catalogInspector,/void inspectSetPrinting\(JSONObject printing,/,
 'Catalog should support a printing-aware external inspector');
assert.match(catalogInspector,/addInspectorArtwork\(name,front,backArt\)/,
 'Full inspector must use exact selected printing art and its alternate face');
assert.match(catalogInspector,/Printing ID: /,
 'Owned physical printing identity must remain visible');
assert.match(catalogInspector,/FORMAT LEGALITIES \(cached\)/,
 'Set and Catalog inspectors should expose cached format legality metadata');
assert.match(source,/Released /,
 'Set Progress overview must show release data without opening a set');
assert.match(source,/verified set entries/,
 'Overview must show verified ownership and missing coverage');
assert.match(catalogInspector,/catalog\.cardDetails\(name\)/,
 'Set Progress must use an exact, unambiguous on-device card metadata lookup');
assert.match(catalogInspector,/catalog\.variants\(variantArgs\)/,
 'Exact printing information must come from the local Scryfall printings catalog');
assert.match(catalogInspector,/Mana cost: /,
 'Full card inspector should show exact casting mana cost when cached');
assert.match(catalogInspector,/Color identity: /,
 'Inspector should show Scryfall color identity separately from mana cost');
assert.match(catalogInspector,/FORMAT LEGALITIES \(cached\)/,
 'Inspector must show available format legalities');
assert.match(catalogInspector,/ORACLE TEXT/,
 'Shared inspector must retain readable oracle text');
assert.match(companion,/workspace\.openSetFromCatalog\(/,
 'Back navigation must restore original Set Progress detail view');
const catalogStore=fs.readFileSync(
 'app/src/main/java/com/arcanevault/mobile/CatalogStore.java','utf8');
assert.match(catalogStore,/JSONObject cardDetails\(String name\)/,
 'Rich card details must be fetched without bounded fuzzy search false negatives');
assert.match(catalogStore,/ALTER TABLE cards ADD COLUMN mana/,
 'Existing Android catalogs need a non-destructive mana-cost migration');
assert.match(catalogStore,/meta\.put\("manaCost",r\.getString\(16\)\)/,
 'Search results must carry the indexed exact mana cost');
assert.match(source,/COLLECTION-WIDE SET COVERAGE/,
 'Set overview must surface aggregate owned and missing coverage');
assert.match(source,/totalCoverage-verifiedCoverage/,
 'Aggregate count must use cached verified set coverage, not guessed quantity');
assert.match(catalogStore,/\.put\("totalCards",snapshot\.optInt\("totalCards",-1\)\)/,
 'Filtering cached sets must retain collection-wide overview totals');
console.log('PASS: visual set-card parity, cached cover art, icons and completion safety');
