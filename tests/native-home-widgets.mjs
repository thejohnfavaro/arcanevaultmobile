import assert from 'node:assert/strict';
import fs from 'node:fs';
const r=p=>fs.readFileSync(p,'utf8');
const manifest=r('app/src/main/AndroidManifest.xml');
const store=r('app/src/main/java/com/arcanevault/mobile/VaultWidgets.java');
const progress=r('app/src/main/java/com/arcanevault/mobile/VaultSetProgressCard.java');
const art=r('app/src/main/java/com/arcanevault/mobile/VaultSetArtwork.java');
const imageCache=r('app/src/main/java/com/arcanevault/mobile/ArtworkCache.java');
const config=r('app/src/main/java/com/arcanevault/mobile/VaultSetWidgetConfigActivity.java');
const value=r('app/src/main/java/com/arcanevault/mobile/VaultCollectionValueWidget.java');
const set=r('app/src/main/java/com/arcanevault/mobile/VaultSetProgressWidget.java');
const workspace=r('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java');
const activity=r('app/src/main/java/com/arcanevault/mobile/MainActivity.java');
const shell=r('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java');
const html=r('app/src/main/assets/index.html');
const inventory=r('app/src/main/java/com/arcanevault/mobile/NativePhysicalEditor.java');
const collections=r('app/src/main/java/com/arcanevault/mobile/CatalogStore.java');
const valxml=r('app/src/main/res/xml/widget_collection_value_info.xml');
const setxml=r('app/src/main/res/xml/widget_set_progress_info.xml');
const priceLayout=r('app/src/main/res/layout/widget_collection_value.xml');
const setLayout=r('app/src/main/res/layout/widget_set_progress.xml');
for(const [name,source] of [
 ['Value',value],['Set',set]
])assert.match(source,/extends AppWidgetProvider/,'Native '+name+' launcher widget provider');
assert.match(manifest,/\.VaultCollectionValueWidget/);
assert.match(manifest,/\.VaultSetProgressWidget/);
assert.match(manifest,/\.VaultSetWidgetConfigActivity/);
assert.match(valxml,/widget_collection_value/);
assert.match(setxml,/widget_set_progress/);
assert.match(setxml,/VaultSetWidgetConfigActivity/);
assert.match(setxml,/reconfigurable/);
assert.match(setxml,/resizeMode="horizontal\|vertical"/);
assert.match(priceLayout,/widget_value_amount/);
assert.match(priceLayout,/widget_value_coverage/);
assert.match(setLayout,/widget_set_card/);
assert.match(setLayout,/widget_set_configure/);

// Widget shows the SAME set-card layout as native Set Progress, without a
// duplicate percentage or ownership calculation.
assert.match(workspace,/VaultSetProgressCard\.render\(activity,set,source,/);
assert.match(store,/VaultSetProgressCard\.render\(context,selected,configuredSource\(context,id\)/);
assert.match(config,/VaultSetProgressCard\.render\(this,choice,source,/);
assert.match(progress,/Math\.min\(600,widthPx\)/,
 'Bitmaps must be under binder size limits');
assert.match(progress,/boolean measured=set!=null&&set\.has\("total"\)&&total>0/,
 'Absent catalog must not be reported as 0%');
assert.match(progress,/CATALOG NOT AVAILABLE/);
assert.match(progress,/VERIFIED PHYSICAL PRINTINGS/);
assert.match(progress,/ARENA CARD NAMES/);
assert.match(progress,/canvas\.drawBitmap\(backdrop,crop,dest,paint\)/,
  'Cards display representative set art as a cropped background');
assert.match(progress,/canvas\.drawBitmap\(icon,null,/,
  'Cards display official set icon when cached');
assert.match(progress,/0xF3101523/,'Art retains readable Windows-style dark overlay');
assert.match(art,/https:\/\/svgs\.scryfall\.io\/sets\//);
assert.match(art,/\/art_crop\//);
assert.match(art,/getCached\(/,'Launcher art and set symbol must read disk-only cache');
assert.match(imageCache,/byte\[\] getCached\(String raw\)/);
assert.match(store,/representativeSetArt\(code,"arena"\.equals\(source\)\)/);
assert.match(store,/prefetchSelectedSet\(Context context,int widgetId\)/);
assert.match(config,/VaultWidgets\.prefetchSelectedSet\(this,widgetId\)/);
assert.match(workspace,/catalog\.representativeSetArt\(/);


// Multiple independently configured Set Progress widgets.
assert.match(config,/new Spinner\(this\)/);
assert.match(config,/Search set name or code/);
assert.match(config,/loadSets\(\)/);
assert.match(store,/putString\("code_"\+id/);
assert.match(store,/putString\("source_"\+id/);
assert.match(store,/removeSetChoice\(Context context,int id\)/);
assert.match(store,/snapshot_"\+id/);
assert.match(store,/catalog\.indexedSetProgress\(code,source\)/);
assert.match(store,/if\(row==null\)continue/,
 'Missing set catalog must retain last valid widget snapshot');
assert.match(store,/catalog\.indexedSetProgress\(code,source\)/);

// Valuation may use only already calculated Scryfall printing quotes.
assert.match(html,/PHYSICAL_PRICE_KEY='physicalPriceQuotesV1'/);
assert.match(html,/const value=priced\.reduce\(\(n,c\)=>n\+physicalPrice\(c\)\*c\.qty,0\)/);
assert.match(html,/publishCollectionValueWidget/);
assert.match(store,/pricedRows:|priced_rows/);
assert.match(store,/priced>total/);
assert.match(store,/value_cents/);
assert.match(store,/No Scryfall quotes cached/);
assert.match(store,/Scryfall USD · cached estimate/);
assert.doesNotMatch(store,/https?:\/\/|HttpURLConnection|scryfall\.com/,
 'Launcher redraws must never initiate quote network requests');
assert.match(activity,/VaultWidgets\.invalidateValue\(MainActivity\.this\)/);
assert.match(inventory,/VaultWidgets\.invalidateValue\(activity\)/);
assert.match(activity,/VaultWidgets\.refreshSets\(MainActivity\.this\)/);
assert.match(activity,/publishCollectionValueWidget\(String payload\)/);
assert.match(activity,/catalog\.physicalOwnership\(new JSONObject/);
assert.match(collections,/JSONObject sets\(Map<String,String> p\)/);

// Both taps use precise Android destinations; background/launcher actions
// must never import, sync, or overwrite user collection data.
assert.match(store,/intent\.putExtra\(TARGET,target\)/); // Android Intent extra must identify destination
assert.match(store,/PendingIntent\.getActivity/);
assert.match(shell,/openWidgetDestination\(String destination,String set,String source\)/);
assert.match(shell,/if\(workspace!=null\)workspace\.openWidgetSet\(set,requested\)/);
assert.match(workspace,/void openWidgetSet\(String code,String source\)/);
assert.match(workspace,/showSet\(code,display,0\)/);
assert.match(activity,/deliverWidgetNavigation\(\)/);
assert.match(activity,/WIDGET_OPEN/);
assert.match(activity,/onNewIntent\(Intent intent\)/);
assert.match(set,/goAsync\(\)/,
 'Scheduled SQLite refresh must finish outside the broadcast ANR window');
console.log('PASS: independent launcher widgets, shared set cards, cached valuation, read-only ownership, deep linking');
