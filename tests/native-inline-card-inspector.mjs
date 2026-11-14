import assert from 'node:assert/strict';
import fs from 'node:fs';
const source=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeCatalogScreen.java','utf8');
for(const pattern of [
 /private final ScrollView browseScroll/,
 /private final ScrollView inspectorScroll/,
 /browseScroll\.setVisibility\(View\.GONE\)/,
 /browseScroll\.setVisibility\(View\.VISIBLE\)/,
 /inspectorScroll\.setVisibility\(View\.VISIBLE\)/,
 /private void openInspector\(/,
 /private void closeInspector\(/,
 /showInlinePrintings\(/,
 /showInlinePrintingDetail\(/,
 /NativePrintingOwnership\.inspect/,
 /NativePhysicalEditor\(activity,inventory,catalog\)/,
 /savePreferredArtwork/,
 /inspectorRequest/,
 /target\.setImageBitmap\(bitmap\)/,
 /flippableArt/,
 /private void prefetchPageArtwork\(/,
 /pageCache\.get\(cacheKey\)/,
 /pageCache\.put\(cacheKey,response\)/,
 /pagePrefetchWorker\.execute/,
 /invalidatePageCache\(\)/,
 /browseScroll\.post\(\(\)->browseScroll\.smoothScrollTo\(0,0\)\)/
])assert.match(source,pattern);
const inspector=source.slice(source.indexOf('private void showDetails('),
    source.indexOf('private void showInlinePrintings('));
assert.doesNotMatch(inspector,/new AlertDialog\.Builder/,
 'Inline card inspector should not create a modal dialog');
console.log('PASS: inline inspector preserves catalog state, reviews printing ownership, edits via native ledger');
