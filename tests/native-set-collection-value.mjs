import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(p,'utf8');
const values=read('app/src/main/java/com/arcanevault/mobile/VaultSetValues.java');
const activity=read('app/src/main/java/com/arcanevault/mobile/MainActivity.java');
const legacy=read('app/src/main/assets/index.html');
const native=read('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java');
const card=read('app/src/main/java/com/arcanevault/mobile/VaultSetProgressCard.java');
const widgets=read('app/src/main/java/com/arcanevault/mobile/VaultWidgets.java');
assert.match(activity,/publishSetValueSnapshots\?\.\(setPayload\)/,
    'Native app bootstraps set values from authoritative physical inventory + quotes');
assert.match(activity,/quotes\[exact\]\|\|quotes\[key\]/,
    'Use exact-printing quotes with legacy key fallback');
assert.match(activity,/setRow\.value\+=price\*qty/,
    'Set value multiplies exact printing quote by owned copies');
assert.match(legacy,/physicalPrice\(c\)/,
    'Set valuations reuse actual Scryfall printing-price helper');
assert.match(legacy,/publishSetValueSnapshots\?\.\(/,
    'Valuation refresh pushes new set snapshots as quote batches complete');
assert.match(activity,/@JavascriptInterface public boolean publishSetValueSnapshots/,
    'Native write endpoint exists for derived set snapshots');
assert.match(values,/private static final String PREF="arcane_set_valuations_v1"/,
    'Value snapshot stored independently of ownership and set-progress caches');
assert.match(values,/priced>0&&cents>0&&!prior\.contains\("baseline_"\+code\)/,
    'Lifetime baseline is set once after first positively priced value');
assert.match(values,/String fresh=complete\.toString\(\)/,
    'Set-value snapshot has deterministic change detection');
assert.match(values,/if\(fresh\.equals\(prior\.getString\("sets",""\)\)/,
    'Avoid redundant Set Progress widget redraws');
assert.match(values,/card\.put\("ownedValuePercent"/,
    'Percentage delta is available to set tiles');
assert.match(values,/card\.remove\("ownedValueLabel"\)/,
    'Stale set price is removed when quote or scope changes');
assert.match(values,/if\(!"physical"\.equals\(source\)\)return/,
    'Arena set completion does not imply owned physical prices');
assert.match(values,/if\(total>0&&priced==0\)return/,
    'Never treat unpriced owned printings as a known zero value');
assert.match(native,/VaultSetValues\.decorate\(activity,set,source\)/,
    'Native Set Progress card includes per-set owned value');
assert.match(native,/VaultSetValues\.detail\(activity,set\)/,
    'Selected-set detail view exposes full price and lifetime variance');
assert.match(card,/ownedValueLabel/,
    'Set-card footer draws owned value');
assert.match(card,/ownedValuePercent/,
    'Set-card footer draws lifetime percentage');
assert.match(card,/Color\.rgb\(99,221,155\)/,
    'Increase displayed green');
assert.match(card,/Color\.rgb\(247,124,124\)/,
    'Decrease displayed red');
assert.match(widgets,/VaultSetValues\.decorate\(context,selected,configuredSource\(context,id\)\)/,
    'Set Progress homescreen widget displays same set valuation');
assert.match(widgets,/static void redrawSetWidgets\(Context context\)/,
    'Price refresh does not rebuild Set Progress catalog');
assert.doesNotMatch(values,/fetch\(|HttpURLConnection|scryfall\.com/,
    'Set valuation must never independently scrape or request prices');
// Same financial model as source: quantities count, no known quotes -> unpriced.
const sum=rows=>rows.reduce((v,r)=>{
  const set=v[r.set]??={usd:0,priced:0,total:0};
  set.total++;
  if(Number.isFinite(r.quote)&&r.quote>0){
    set.usd+=r.quote*r.qty;set.priced++;
  }
  v[r.set]=set;return v;
},{});
const result=sum([
 {set:'trk',qty:2,quote:3.50},{set:'trk',qty:1,quote:null},
 {set:'dsk',qty:1,quote:9.25}
]);
assert.deepEqual(result.trk,{usd:7,priced:1,total:2},
  'Only owned, quoted TRK printings contribute to TRK value');
assert.deepEqual(result.dsk,{usd:9.25,priced:1,total:1},
  'Independent set value never includes other sets');
console.log('PASS: set-specific physical valuations, quote coverage, baseline and colored variance');
