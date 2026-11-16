import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(p,'utf8');
const setConfig=read('app/src/main/java/com/arcanevault/mobile/VaultSetWidgetConfigActivity.java');
const directory=read('app/src/main/java/com/arcanevault/mobile/VaultSetDirectory.java');
const catalog=read('app/src/main/java/com/arcanevault/mobile/CatalogStore.java');
const widget=read('app/src/main/java/com/arcanevault/mobile/VaultWidgets.java');
const activity=read('app/src/main/java/com/arcanevault/mobile/MainActivity.java');
const legacy=read('app/src/main/assets/index.html');
const version=read('app/build.gradle');

// Search must expose real clickable official results. No guess-based code
// creation is allowed when a query looks like an abbreviation.
assert.match(setConfig,/VaultSetDirectory\.official\(getApplicationContext\(\)\)/);
assert.match(setConfig,/catalog\.indexedSetNames\(\)/);
assert.match(directory,/https:\/\/api\.scryfall\.com\/sets/);
assert.match(directory,/item\.optString\("name",""\)/);
assert.match(directory,/item\.optString\("code",""\)/);
assert.match(directory,/FRESH_MS=7L\*24\*60\*60\*1000/);
assert.match(directory,/new JSONArray\(\{?\}?/); // preserve offline cache representation
assert.match(directory,/for\(JSONArray source:new JSONArray\[\]\{official,indexed\}\)/);
assert.match(setConfig,/private void renderCandidates\(\)/);
assert.match(setConfig,/relevance\(row,query\.toLowerCase\(Locale\.ROOT\)\)/);
assert.match(setConfig,/candidate\.setOnClickListener\(v->selectSet\(row\)\)/);
assert.match(setConfig,/No official set matched/);
assert.match(setConfig,/Star Trek's main set is TRK \(Commander is TRC\), not STK/);
assert.doesNotMatch(setConfig,/new JSONObject\(\).*put\("code",query\)/s,
  'Free-text fallback must never invent a set');
assert.match(setConfig,/save\.setEnabled\(selected!=null\)/);
assert.match(setConfig,/if\(selected==null\)\{status\.setText/);

// The picker is fast and the completion query is EXACTLY scoped to the
// chosen set. Missing index is null/unknown rather than an invented 0%.
assert.match(catalog,/JSONArray indexedSetNames\(\)/);
assert.match(catalog,/JSONObject indexedSetProgress\(String setCode,String source\)/);
assert.match(catalog,/if\(total<=0\)return null/);
assert.match(catalog,/COUNT\(DISTINCT v\.name\).*FROM variants v/s);
assert.match(catalog,/JOIN variants v ON p\.print_id=v\.print_id COLLATE NOCASE/,
 'Exact printing identity uses an index-friendly UUID join');
assert.match(catalog,/COUNT\(DISTINCT collector COLLATE NOCASE\)/,
 'Physical set completion counts distinct paper collector numbers');
assert.match(catalog,/v\.setcode=\? COLLATE NOCASE/);
assert.match(catalog,/JOIN ownership o ON o\.name=v\.name COLLATE NOCASE AND o\.qty>0/);
assert.match(widget,/JSONObject row=catalog\.indexedSetProgress\(code,source\)/);
assert.match(widget,/if\(row==null\)continue/);
assert.match(catalog,/VaultWidgets\.refreshSets\(application\)/,
  'Newly completed Scryfall indexes should immediately update set widgets');

// The setup activity fills the entire screen and handles Android 15 insets,
// avoiding the empty grey theme rectangle and overlapped status bar.
assert.match(setConfig,/scroll\.setFillViewport\(true\)/);
assert.match(setConfig,/scroll\.setBackgroundColor\(VaultNativeStyle\.BACKGROUND\)/);
assert.match(setConfig,/NativeSystemInsets\.apply\(this,scroll\)/);
assert.match(setConfig,/ScrollView\.LayoutParams\(-1,-2\)/);
assert.match(setConfig,/preview\.setScaleType\(ImageView\.ScaleType\.FIT_CENTER\)/);
assert.doesNotMatch(setConfig,/new Spinner\(this\);[\s\S]*setPicker/,
  'Set choices must not be hidden in an unlabeled empty dropdown');

// Price estimates use original official Scryfall batch refresh (only on
// app foreground with a value widget; no launcher callback network).
assert.match(widget,/claimForegroundQuoteRefresh\(Context context\)/);
assert.match(widget,/getAppWidgetIds\(/);
assert.match(widget,/price_attempt_at/);
assert.match(widget,/6L\*60\*60\*1000/);
assert.match(widget,/price_refresh_active/);
assert.match(activity,/if\(result!=null&&result\.contains\("published"\)\)\s*maybeRefreshWidgetPrices\(\)/);
assert.match(activity,/private void maybeRefreshWidgetPrices\(\)/);
assert.match(activity,/VaultWidgets\.claimForegroundQuoteRefresh\(this\)/);
assert.match(activity,/Promise\.resolve\(\)\.then\(\(\)=>physicalRefreshPrices\(\)\)/);
assert.match(activity,/widgetPriceRefreshEnded/);
assert.match(activity,/VaultWidgets\.finishForegroundQuoteRefresh/);
assert.match(legacy,/async function physicalRefreshPrices\(\)/);
assert.match(legacy,/save\(PHYSICAL_PRICE_KEY,physicalPrices\)/);
assert.match(legacy,/physicalRender\(\)/);
assert.doesNotMatch(widget,/HttpURLConnection|https:\/\/api\.scryfall\.com/,
  'Launcher redraw must stay offline');
assert.ok(Number(version.match(/versionCode (\d+)/)?.[1])>=86);
console.log('PASS: real set-name search, exact indexed completion, full-height config and foreground Scryfall price recovery');
