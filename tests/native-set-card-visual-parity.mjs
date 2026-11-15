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
assert.match(source,/VaultSetProgressCard\.render\(activity,set,source,width,dp\(176\)\)/,
 'Both modes render the original card treatment');
assert.match(source,/catalog\.cachedRepresentativeSetArt\(code,source\)/);
assert.match(source,/VaultSetArtwork\.prefetch\(activity,art,code\)/,
 'Art and official set symbols should warm outside UI thread');
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
console.log('PASS: visual set-card parity, cached cover art, icons and completion safety');
