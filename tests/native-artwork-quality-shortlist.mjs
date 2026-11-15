import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(p,'utf8');
const quality=read('app/src/main/java/com/arcanevault/mobile/NativeArtworkQuality.java');
const shortlist=read('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java');
const cards=read('app/src/main/java/com/arcanevault/mobile/NativeCatalogScreen.java');
const commander=read('app/src/main/java/com/arcanevault/mobile/NativeCommanderBrowserScreen.java');
const cache=read('app/src/main/java/com/arcanevault/mobile/ArtworkCache.java');
const catalog=read('app/src/main/java/com/arcanevault/mobile/CatalogStore.java');

// Never force a single low-resolution sample across card tiles and full previews.
assert.match(quality,/bounds.inJustDecodeBounds=true/);
assert.match(quality,/bounds.outWidth\/\(sample\*2\)>=desiredWidth/);
assert.match(quality,/bounds.outHeight\/\(sample\*2\)>=desiredHeight/);
assert.match(quality,/decode.inSampleSize=sample/);
assert.match(quality,/BitmapFactory.decodeByteArray\(bytes,0,bytes.length,decode\)/);
assert.doesNotMatch(cards,/inSampleSize\s*=\s*4/);
assert.doesNotMatch(commander,/inSampleSize\s*=\s*[24]/);
assert.doesNotMatch(shortlist,/inSampleSize\s*=\s*4/);

// Large previews prefer Scryfall's official large variant, with offline fallback.
assert.match(quality,/original.replace\("\/normal\/","\/large\/"\)/);
assert.match(quality,/if\(fullPreview\)/);
assert.match(quality,/cache.get\(large\)/);
assert.match(quality,/cache.get\(url\)/);
assert.match(cache,/Only official Scryfall image hosts are supported/);
assert.match(cards,/NativeArtworkQuality.load\(artwork,imageUrl,/);
assert.match(commander,/NativeArtworkQuality.load\(artwork,url,/);
assert.match(shortlist,/NativeArtworkQuality.load\(artwork,url,/);
assert.match(cards,/displayWidth,displayHeight,fullPreview/);
assert.match(shortlist,/height,true\)/,'Shortlist full preview requests large artwork');
assert.match(catalog,/JSONObject cardBrief\(String cardName\)/);
assert.match(catalog,/\.put\("image",c.getString\(1\)\)/);

// Image-first native Shortlist with bounded on-screen art work.
assert.match(shortlist,/new GridLayout\(activity\)/);
assert.match(shortlist,/gallery.setColumnCount\(columns\)/);
assert.match(shortlist,/SHORTLIST_PAGE_SIZE=48/);
assert.match(shortlist,/Math.min\(filtered.size\(\),first\+SHORTLIST_PAGE_SIZE\)/);
assert.match(shortlist,/new ShortlistArtTarget\(image,url,/);
assert.match(shortlist,/target.view.getGlobalVisibleRect\(visible\)/);
assert.match(shortlist,/workspaceScroll.post\(\(\)->\{/,
  'Shortlist restores prior gallery scroll position before lazy artwork');
assert.match(shortlist,/loadVisibleShortlistArt\(\)/);
assert.match(shortlist,/shortlistArtInFlight.size\(\)>=6/);
assert.match(shortlist,/return size\(\)>20/);
assert.match(shortlist,/tile.setOnClickListener\(v->openShortlistPreview\(card\)\)/);
assert.match(shortlist,/large.setScaleType\(ImageView.ScaleType.FIT_CENTER\)/);
assert.match(shortlist,/setNativeShortlist\(name,false/);
assert.match(shortlist,/loadNativeShortlist\(names->/);
assert.match(shortlist,/coverWorkers.shutdownNow\(\)/);
console.log('PASS: adaptive full-resolution Android artwork and illustrated, paginated, lazy Shortlist');
