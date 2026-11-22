import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(p,'utf8');
const shell=read('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java');
const workspace=read('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java');
const catalog=read('app/src/main/java/com/arcanevault/mobile/CatalogStore.java');
const cardScreen=read('app/src/main/java/com/arcanevault/mobile/NativeCatalogScreen.java');
const activity=read('app/src/main/java/com/arcanevault/mobile/MainActivity.java');
const textExchange=read('app/src/main/java/com/arcanevault/mobile/NativeDeckTextExchange.java');
const gradle=read('app/build.gradle');

for(const text of ['Search deck, commander, format, or tags',
  'Recently updated','Deck name A-Z','Commander A-Z',
  'All folders','Favorite decks only','renderDeckList()']){
  assert.ok(workspace.includes(text),'Missing deck discovery feature: '+text);
}
assert.match(workspace,/deckCoverRow\(deck,total,deckPageGeneration\)/);
assert.match(workspace,/deck\.optBoolean\("favorite",false\)/);
assert.match(workspace,/matches\.sort\(order\.thenComparing/);
assert.match(workspace,/b\.optString\("updated",""\)\.compareTo\(a\.optString\("updated",""\)\)/);
assert.match(workspace,/workspaceScroll\.post\(this::loadVisibleDeckCovers\)/);
assert.match(catalog,/JSONObject deckInsights\(String commander,JSONArray entries\)/);
assert.match(catalog,/SELECT type,mv,colors,mana FROM cards WHERE name=\? COLLATE NOCASE LIMIT 1/);
assert.match(catalog,/unknown\+=qty/,'Unresolved metadata must remain unknown');
assert.match(catalog,/if\(normalized\.contains\("land"\)\)\{/);
assert.match(catalog,/lands\+=qty/);
assert.match(catalog,/if\(i>=0\)mainboardLands\+=qty/);
assert.match(workspace,/new android\.widget\.ProgressBar/);
assert.match(workspace,/if\(report!=null\)renderDeckInsights\(report\)/);

assert.match(workspace,/Copy for Arena/);
assert.match(workspace,/Copy plain list/);
assert.match(workspace,/Paste \/ Import Arena decklist/);
assert.match(workspace,/NativeDeckTextExchange\.parse\(/);
assert.match(workspace,/NativeDeckTextExchange\.export\(/);
assert.match(workspace,/Replace draft mainboard\?/);
assert.match(workspace,/The original deck must still match the version you opened/);
assert.match(textExchange,/Sideboard\/companion sections cannot be imported without losing data/);
assert.match(textExchange,/Imported commander must match this deck's commander/);

assert.match(shell,/addSecondaryTab\("My Shortlist","shortlist"\)/);
assert.match(shell,/if\(scope\.equals\("shortlist"\)\)return "catalog"/);
assert.match(workspace,/private void shortlist\(\)/);
assert.match(workspace,/private void renderShortlist\(\)/);
assert.match(workspace,/loadNativeShortlist\(/);
assert.match(workspace,/setNativeShortlist\(name,false/);
assert.match(catalog,/JSONObject cardBrief\(String cardName\)/);
assert.match(activity,/localStorage\.getItem\('vault_favorites'\)/);
assert.match(activity,/localStorage\.setItem\(key,JSON\.stringify\(names\)\)/);
assert.match(activity,/favorite=new Set\(names\)/);
assert.match(cardScreen,/☆ Add to Shortlist/);
assert.match(cardScreen,/★ Remove from Shortlist/);

assert.match(cardScreen,/＋ Add to Physical Collection/);
assert.match(cardScreen,/quickAdd\.setOnClickListener\(v->showInlinePrintings/);
assert.match(cardScreen,/new NativePhysicalEditor\(activity,inventory,catalog\)/);
assert.match(cardScreen,/View printings & edit physical ownership/);

assert.ok(Number(gradle.match(/versionCode (\d+)/)?.[1])>=82,
  'Quality-of-life features must not regress to an older release');
assert.match(gradle,/versionName '2\.1\.\d+'/);
console.log('PASS: five native QOL features, read-only analytics, same shortlist authority and safe import/physical edit flow');
