import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(p,'utf8');
const manifest=read('app/src/main/AndroidManifest.xml');
const valueInfo=read('app/src/main/res/xml/widget_collection_value_info.xml');
const setInfo=read('app/src/main/res/xml/widget_set_progress_info.xml');
const strings=read('app/src/main/res/values/widget_strings.xml');
const layout=read('app/src/main/res/layout/widget_set_progress.xml');
const config=read('app/src/main/java/com/arcanevault/mobile/VaultSetWidgetConfigActivity.java');
const activity=read('app/src/main/java/com/arcanevault/mobile/MainActivity.java');
const widgets=read('app/src/main/java/com/arcanevault/mobile/VaultWidgets.java');
const physicalEditor=read('app/src/main/java/com/arcanevault/mobile/NativePhysicalEditor.java');
const legacy=read('app/src/main/assets/index.html');
const gradle=read('app/build.gradle');

// Android launcher differentiates both widget providers, not just their app.
assert.match(manifest,/VaultCollectionValueWidget" android:label="@string\/widget_collection_value_label"/);
assert.match(manifest,/VaultSetProgressWidget" android:label="@string\/widget_set_progress_label"/);
assert.match(valueInfo,/android:description="@string\/widget_collection_value_description"/);
assert.match(setInfo,/android:description="@string\/widget_set_progress_description"/);
assert.match(strings,/name="widget_collection_value_label">Collection Value/);
assert.match(strings,/name="widget_set_progress_label">Set Progress/);

// Preview must preserve the artwork's aspect ratio on both the setup screen
// and the launcher when the device's cell dimensions differ from the bitmap.
assert.match(layout,/android:scaleType="fitCenter"/);
assert.doesNotMatch(layout,/android:scaleType="fitXY"/);
assert.match(config,/preview.setScaleType\(ImageView.ScaleType.FIT_CENTER\)/);
assert.doesNotMatch(config,/preview.setScaleType\(ImageView.ScaleType.FIT_XY\)/);
assert.match(config,/screenWidth-dp\(40\)/);

// Setup works without prematurely assuming the catalog index is complete.
// Exact set codes are allowed, but no fake owned/total values are written.
assert.match(config,/private JSONObject chosenSet\(\)/);
assert.match(config,/code.matches\("\[a-z0-9\]\{2,5\}"\)/);
assert.match(config,/Track "\+code\+" now/);
assert.match(config,/save.setEnabled\(valid\)/);
assert.match(config,/No set matches. Enter an official/);
assert.match(config,/VaultWidgets.saveSetChoice\(this,widgetId,code,name,source\)/);
assert.match(config,/setResult\(RESULT_OK,result\)/);
assert.match(widgets,/if\(!stored\)throw new IllegalStateException/);

// Price bootstrap runs when native WebView becomes ready, not only when
// the old Physical page renders. Inventory comes from authoritative native
// data; quote keys are exactly those of the legacy Scryfall quote cache.
assert.match(activity,/peerDataPageReady=true;\s*refreshCollectionValueWidget\(\)/);
assert.match(activity,/void refreshCollectionValueWidget\(\)/);
assert.match(activity,/ArcaneAndroid\?\.readCollection\?\.\('physicalCollectionV1'\)/);
assert.match(activity,/localStorage.getItem\('vault_physicalPriceQuotesV1'\)/);
assert.match(activity,/quotes\[exact\]\|\|quotes\[key\]/);
assert.match(activity,/hasQuotes,updatedAt/);
assert.match(activity,/JSON.stringify\(\{value,pricedRows,totalRows,totalCopies,/);
assert.match(activity,/runOnUiThread\(MainActivity.this::refreshCollectionValueWidget\)/);
assert.match(physicalEditor,/refreshCollectionValueWidget\(\)/);
assert.match(legacy,/hasQuotes:Object.keys\(physicalPrices\?\.quotes\|\|\{\}\).length>0/);

// Missing quote cache, no priced match, and empty physical inventory must
// not all be presented as the same misleading "No priced printings" state.
assert.match(widgets,/No Scryfall quotes cached/);
assert.match(widgets,/Quotes do not match owned printings/);
assert.match(widgets,/No physical cards inventoried/);
assert.doesNotMatch(widgets,/No priced printings yet/);
assert.match(widgets,/quotes_present/);
assert.ok(Number(gradle.match(/versionCode (\d+)/)?.[1])>=85);
console.log('PASS: two labeled widgets, un-stretched previews, reliable set-code setup and bootstrapped saved quote valuations');
