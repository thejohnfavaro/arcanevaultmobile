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
 /getWindowToken\(\)==null/,
 /loadThumbnail\(art,imageUrl\)/
])assert.match(source,pattern);
assert.doesNotMatch(source,/private void showDetails\([\s\S]*?\n    private void showInlinePrintings[\s\S]*?\.setTitle\(name\)/,
 'Inline card inspector should not use an alert dialog as its main container');
console.log('PASS: inline inspector preserves catalog state, reviews printing ownership, edits via native ledger');
