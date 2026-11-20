import assert from 'node:assert/strict';
import fs from 'node:fs';
const native = fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeCatalogScreen.java','utf8');
const shell = fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java','utf8');
const activity = fs.readFileSync('app/src/main/java/com/arcanevault/mobile/MainActivity.java','utf8');
const backend = fs.readFileSync('app/src/main/java/com/arcanevault/mobile/CatalogStore.java','utf8');
assert.match(native,/catalog\.search\(options\)/,'Use existing SQLite card search, never a duplicate WebView index');
assert.match(native,/Executors\.newSingleThreadExecutor\(\)/,'DB reads must be off the UI thread');
assert.match(native,/request\.incrementAndGet\(\)/,'Obsolete search results must be discarded');
assert.match(native,/NativeCatalogScreen\(Activity activity, CatalogStore catalog, ArtworkCache artwork, SharedPreferences inventory\)/);
assert.match(native,/NativeArtworkQuality\.load\(artwork,imageUrl,/,'Official art uses the shared high quality Scryfall loader');
assert.match(fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeArtworkQuality.java','utf8'),
  /BitmapFactory\.decodeByteArray/,'Shared loader decodes bounded official image bytes');
assert.match(native,/new AlertDialog\.Builder\(activity\)/,'Native inspector replaces HTML card details');
assert.match(native,/options\.put\("size","40"\)/,'Bounded native catalog page size');
assert.match(shell,/new NativeCatalogScreen\(activity, catalog, artwork, prefs\)/);
assert.match(shell,/destinations=\{"home","catalog","collection","decks","sync"\}/);
assert.match(shell,/addSecondaryTab\("EDH Lab","edh lab"\)/);
assert.match(shell,/addSecondaryTab\("Data & Sync","data & sync"\)/);
assert.match(activity,/NativeCompanionScreen\.createController\(this, durable, catalog, artwork,/,
  'Native catalog lives in the retained page-navigation controller');
assert.match(backend,/JSONObject search\(Map<String,String> p\)/);
assert.match(native,/KEYWORD \/ RULES TEXT/,'Mechanic filter should be visible in advanced Card Catalog filters');
assert.match(native,/options\.put\("oracle",selectedOracle\)/,'Pass typed mechanics to SQL query and cache key');
assert.match(backend,/private static String\[\] oracleTerms/,'Split bounded Oracle phrases for all/any selection');
assert.match(backend,/addOracleTerms\(where,args,p\)/,'General catalog must support multi-mechanic terms');
assert.match(backend,/addOracleTerms\(from,filter,p\)/,'Exact owned printings must support multi-mechanic terms');
assert.match(backend,/terms\.length>8/,'Limit user-provided Oracle search phrase count');
assert.match(backend,/mode\.equals\("any"\)\?" OR ":" AND "/,'Support AND and OR without SQL string injection');
assert.match(native,/MECHANIC MATCH/,'Show all/any mechanic matching in advanced filters');
assert.match(native,/options\.put\("oracleMatch",selectedOracleMatch\)/,'Send match mode to catalog query and cache');
assert.match(native,/oracleFilter=""/,'Resetting filters must reset the keyword filter');
console.log('PASS: native Android Card Catalog uses SQLite, bounded async search, cached art and native inspection');

// Every new Android catalog session opens on the recorded physical collection,
// not the entire catalog, without altering persisted ownership values.
assert.match(native,/private String source = "physical", owned = "owned", query = "";/);
assert.match(native,/options.put\("source",src\)/);
assert.match(native,/options.put\("scope",scope\)/);
assert.match(native,/buildQuickManaFilters\(\)/);
assert.match(native,/NativeManaFilterButton\(activity,artwork,color\)/);
assert.match(native,/advancedFilters.addView\(quickFilters,0\)/);
assert.match(native,/updateIdentityButtons\(\);(?:inventory\.edit\(\)\.putString\(PREF_COLOR,exactColorIdentity\)\.apply\(\);)?page=0;schedule\(\)/);
console.log('PASS: Android catalog defaults to Physical + Owned and provides quick official mana toggles');
