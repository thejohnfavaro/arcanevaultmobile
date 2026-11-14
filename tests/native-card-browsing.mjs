import assert from 'node:assert/strict';
import fs from 'node:fs';
const screen=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeCatalogScreen.java','utf8');
const catalog=fs.readFileSync('app/src/main/java/com/arcanevault/mobile/CatalogStore.java','utf8');
for(const p of [
  /Filters & Sort/,
  /buildAdvancedFilters\(\)/,
  /setCode=""/,
  /filterField\("SET CODE"/,
  /filterField\("TYPE CONTAINS"/,
  /filterPicker\("RARITY"/,
  /filterPicker\("UTILIZATION TAG"/,
  /filterPicker\("SORT BY"/,
  /exactColorIdentity/,
  /Reset advanced filters/,
  /options\.put\("set",selectedSet\)/,
  /options\.put\("role",selectedRole\)/,
  /options\.put\("colors",selectedIdentity\)/,
  /renderArtworkTile/,
  /GridLayout gallery/,
  /viewModeButton/,
  /browseScroll\.addView\(browseContent\)/,
  /filtersReady=true/
])assert.match(screen,p);
assert.match(catalog,/EXISTS \(SELECT 1 FROM variants vs WHERE vs\.name=c\.name/);
assert.match(catalog,/v\.setcode=\? COLLATE NOCASE/);
assert.match(catalog,/String exactColors=get\(p,"colors"\)/);
assert.match(catalog,/String sort=get\(p,"sort"\)/);
assert.match(catalog,/confirmed\+"SELECT COUNT\(\*\)"\+from,filter\.toArray/);
console.log('PASS: Android card browsing filters and gallery preserve source, owned printing and SQL filters');
