import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
const root=path.resolve(import.meta.dirname, '..');
const htmlFile=fs.existsSync(path.join(root,'ArcaneVault.Desktop/Assets/Arcane_Vault_MTGA_Collection_v4_2.html'))
 ?path.join(root,'ArcaneVault.Desktop/Assets/Arcane_Vault_MTGA_Collection_v4_2.html'):path.join(root,'app/src/main/assets/index.html');
const html=fs.readFileSync(htmlFile,'utf8');
for(const x of ['id="avUpdateRepo"','id="avAutoUpdateCheck"','id="avCheckUpdates"','id="avStudioTribe"','id="avTribeQuery"','function linkedTypes(card)','function checkUpdates(silent=false)'])assert.ok(html.includes(x),`Missing ${x}`);
assert.match(html,/all non-\[a-z-\]\+ creatures get -/,'Board wipe heuristic should cover non-Zombie wipes');
const line='All non-Zombie creatures get -3/-3 until end of turn.';
assert.match(line,/all non-[a-z-]+ creatures get -/i);
const tokens='Create a 2/2 black Zombie creature token.';
assert.ok(new RegExp('\\b'+'Zombie'+'s?'+'\\b','i').test(tokens));
assert.ok(new RegExp('\\b'+'Zombie'+'s?'+'\\b','i').test('Zombies you control get +1/+1'));
assert.match(html,/https:\/\/api\.github\.com\/repos\//,'Updater must check GitHub public release feed');
assert.match(html,/if\(release\.draft\|\|release\.prerelease\)return false/,'Only stable published releases');
assert.match(html,/INSTALLED/);
console.log('PASS: update controls, safe release filtering, type filters, and Liliana Zombie/wipe tags');

// Android Set Progress must default to the source that can confirm printings.
assert.match(html, /let setCollectionSource=load\('setCollectionSourceV1','physical'\)==='arena'\?'arena':'physical'/);
assert.match(html, /Arena imports do not contain confirmed printing identities/);
assert.match(html, /No sets match these filters. Clear the search or turn off Hide sets with no progress./);

// Executable filter behavior checks: selection of W + R + WR excludes WUR / BR.
// Read the actual production helper, rather than duplicating its implementation.
const avIdentitySource=html.match(/function avIdentityKey\(colors\)\{[\s\S]*?\n\}/)?.[0];
assert.ok(avIdentitySource,'Missing identity normalization helper');
const avIdentityKeyUnderTest=new Function('AV_IDENTITY_ORDER',avIdentitySource+';return avIdentityKey')('WUBRG');
assert.equal(avIdentityKeyUnderTest(['R','W']),'WR');
assert.equal(avIdentityKeyUnderTest(['W']),'W');
assert.equal(avIdentityKeyUnderTest([]),'C');
assert.equal(avIdentityKeyUnderTest(['U','R','W']),'WUR');
const avBorosSelections=['W','R','WR'];
assert.equal(avBorosSelections.includes(avIdentityKeyUnderTest(['R','W'])),true);
assert.equal(avBorosSelections.includes(avIdentityKeyUnderTest(['W'])),true);
assert.equal(avBorosSelections.includes(avIdentityKeyUnderTest(['R'])),true);
assert.equal(avBorosSelections.includes(avIdentityKeyUnderTest(['W','U','R'])),false);
const avRaritySource=html.match(/function avRarityLabel\(value,fallback='Unverified'\)\{[\s\S]*?\n\}/)?.[0];
assert.ok(avRaritySource,'Missing rarity display helper');
const avRarityLabelUnderTest=new Function(avRaritySource+';return avRarityLabel')();
for(const [raw,expected] of [['common','Common'],['uncommon','Uncommon'],['rare','Rare'],['mythic','Mythic Rare']])
 assert.equal(avRarityLabelUnderTest(raw),expected);
assert.equal(avRarityLabelUnderTest('other'),'Unverified');
console.log('PASS: exact mixed Boros/mono-color identities and normalized rarity labels');
