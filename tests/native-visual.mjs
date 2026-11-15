import assert from 'node:assert/strict';
import fs from 'node:fs';
const shell=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java','utf8');
const catalog=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeCatalogScreen.java','utf8');
const style=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/VaultNativeStyle.java','utf8');
const manifest=fs.readFileSync('app/src/main/AndroidManifest.xml','utf8');
assert.match(manifest,/@android:style\/Theme.Material.NoActionBar/,'App must not inherit Android light default controls');
assert.doesNotMatch(manifest,/Theme.Material.Light/);
assert.match(style,/BACKGROUND=Color\.rgb\(13,16,23\)/,'Native shell uses a dark vault palette');
assert.match(style,/static GradientDrawable rounded/);
assert.match(style,/setTextColor\(active\?BACKGROUND:TEXT\)/,'Active navigation contrast must work');
assert.match(shell,/root.addView\(tabs,new LinearLayout.LayoutParams\(-1,dp\(66\)\)\)/,'Five primary workspaces must remain visible at the bottom');
assert.match(shell,/drawSecondaryTabs\(\)/,'Related destinations must use contextual navigation');
assert.match(shell,/secondaryScroll.setVisibility/,'Contextual navigation must not waste catalog space');
assert.match(shell,/FrameLayout stage/,'Native Catalog must fill available space');
assert.doesNotMatch(shell,/dp\(540\)/,'Do not clip Catalog to a hardcoded partial-height window');
assert.match(shell,/VaultNativeStyle\.surface/,'Dashboard and collection rows use themed surfaces');
assert.match(catalog,/FrameLayout thumb=flippableArt/,'Cards retain official artwork with optional flip control');
assert.match(catalog,/loadThumbnail/);
assert.match(catalog,/artworkWorker\.execute/,'Artwork decoding must not block SQLite search or the UI');
assert.match(catalog,/NativeArtworkQuality\.load\(artwork,imageUrl,/,
  'Thumbnails use adaptive display-pixel decoding, not a fixed 1/4 bitmap');
assert.match(catalog,/VaultNativeStyle\.search/,'Native search fields must use the dark theme');
assert.match(catalog,/ORACLE TEXT/,'Card inspector must have readable rules text');
assert.match(catalog,/View printings & edit physical ownership/,'Printing inspection is preserved');
assert.match(catalog,/NativePrintingOwnership\.inspect/,'Verified physical identity safety stays in place');
console.log('PASS: dark native shell, full-height catalog, real cached thumbnails, legible inspector, printing safety');

// Android gallery must prioritize real browsing space over permanently expanded filters.
assert.match(catalog,/quickFilters=VaultNativeStyle\.column\(activity\)/);
assert.match(catalog,/advancedFilters\.addView\(quickFilters,0\)/);
assert.match(catalog,/catalogTileWidth\(\)/);
assert.match(catalog,/usableDp >= 680f \? 4 : usableDp >= 490f \? 3 : 2/);
assert.match(catalog,/if\(artGrid\)\{/);
assert.match(catalog,/renderOwnedPrintingTile\(gallery,row,i\)/);
assert.match(catalog,/PREF_COMPACT_GRID/);
console.log('PASS: responsive compact card grid and collapsible catalog controls');

assert.match(style,/PRIMARY=Color\.rgb\(181,155,252\)/,'Default mobile theme uses Arcane Noir accent');
assert.match(shell,/destination.equals\("collection"\)\?"physical"/);
console.log('PASS: five bottom destinations and themed compact secondary tabs');
