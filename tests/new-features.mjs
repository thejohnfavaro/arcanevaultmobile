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


// Reproduce the Android Card Details regression using a minimal DOM with real
// insertBefore membership checks. A moved section is an invalid insertion anchor.
{
 const start=html.indexOf('function avCardDetailsLayout(){');
 const end=html.indexOf("document.addEventListener('click',e=>{",start);
 assert.ok(start>0&&end>start,'Card Details layout must exist');
 const source=html.slice(start,end);
 class NodeStub {
  constructor(tag='div',name='',text=''){
   this.tagName=tag.toUpperCase();this.className=name;this.id='';this.textContent=text;
   this.children=[];this.parentNode=null;this.dataset={};this.attributes={};this.hidden=false;
   this.classList={contains:value=>this.className.split(' ').includes(value)};
  }
  removeChild(child){
   const index=this.children.indexOf(child);
   if(index<0)throw Error('NotFoundError: not a child');
   this.children.splice(index,1);child.parentNode=null;
  }
  appendChild(child){child.parentNode?.removeChild(child);this.children.push(child);child.parentNode=this;return child;}
  insertBefore(child,ref){
   if(ref&&!this.children.includes(ref))throw Error('NotFoundError: insertBefore target not a child');
   child.parentNode?.removeChild(child);
   const index=ref?this.children.indexOf(ref):this.children.length;
   this.children.splice(index,0,child);child.parentNode=this;return child;
  }
  after(child){
   if(!this.parentNode)throw Error('Detached anchor');
   const nodes=this.parentNode.children;
   this.parentNode.insertBefore(child,nodes[nodes.indexOf(this)+1]||null);
  }
  querySelector(selector){
   if(selector[0]==='.')return this.children.find(n=>n.className.split(' ').includes(selector.slice(1)))||null;
   return null;
  }
  querySelectorAll(selector){
   if(selector==='[data-ux-detail-tab]')return this.children.filter(n=>n.dataset.uxDetailTab);
   return [];
  }
  setAttribute(name,value){this.attributes[name]=String(value);}
  addEventListener(){}
 }
 const detail=new NodeStub(),art=new NodeStub(),info=new NodeStub();
 detail.appendChild(art);detail.appendChild(info);
 info.appendChild(new NodeStub('h2'));
 const overview=info.appendChild(new NodeStub('section','', 'Overview summary'));
 const prints=info.appendChild(new NodeStub('section','av-prints-panel','Owned physical printings'));
 const legal=info.appendChild(new NodeStub('section','','Format legalities'));
 const combo=info.appendChild(new NodeStub('section','','Combos'));combo.id='cardComboPanel';
 const actions=info.appendChild(new NodeStub('div','actions'));
 const run=new Function('document','$','localStorage',source+';return avCardDetailsLayout')(
  {createElement:tag=>new NodeStub(tag)},id=>id==='detailInner'?detail:null,{getItem:()=>null,setItem:()=>{}}
 );
 assert.doesNotThrow(()=>run(),'Card tap must construct its modal tabs without NotFoundError');
 assert.deepEqual(info.children.filter(n=>n.dataset.uxDetailPanel).map(n=>n.dataset.uxDetailPanel),
  ['overview','printings','legality','combos'],'Tabs must remain ordered');
 assert.equal(prints.parentNode.dataset.uxDetailPanel,'printings');
 assert.equal(legal.parentNode.dataset.uxDetailPanel,'legality');
 assert.equal(combo.parentNode.dataset.uxDetailPanel,'combos');
 assert.equal(actions.parentNode,info);
 assert.doesNotThrow(()=>run(),'Detail layout must remain idempotent');
}
console.log('PASS: Android card detail tab DOM insertions and repeated opens');


// Exercise the shipped human-readable indexing status formatter.
{
 const source=html.match(/function avCatalogProgressText\(status\)\{[\s\S]*?\n\}/)?.[0];
 assert.ok(source,'Missing catalog progress display helper');
 const progress=new Function(source+';return avCatalogProgressText')();
 const active=progress({phase:'Downloading and indexing card printings',imported:12000,
  downloadedBytes:10485760,expectedBytes:52428800,idleSeconds:2,stalled:false});
 assert.match(active,/12,000 printings indexed/);
 assert.match(active,/10\.0 \/ 50\.0 MiB downloaded/);
 const stalled=progress({phase:'Indexing card printings',imported:0,idleSeconds:188,stalled:true});
 assert.match(stalled,/may be stalled/);
 assert.match(stalled,/Existing collections are safe/);
}
console.log('PASS: indexing status reports activity and actionable stalled states');


// Quantity-safe physical printing recovery from the user's own CSV/Windows snapshot.
{
 const between=(a,b)=>{
  const start=html.indexOf(a),end=html.indexOf(b,start);
  assert.ok(start>=0&&end>start,'Missing printing recovery helper: '+a);
  return html.slice(start,end);
 };
 const helpers=[
  between('function physicalLegacyKey(c)','function physicalNormalize(rows)'),
  between('function physicalNormalize(rows)','function physicalOwnership()'),
  between('function avPlanPhysicalIdentityRecovery(current,source)','function avRestorePhysicalIdentities(source,description)')
 ].join('\n');
 const api=new Function(helpers+';return {normalize:physicalNormalize,plan:avPlanPhysicalIdentityRecovery,hasId:avPhysicalHasIdentity}')();
 const unknown=(name,qty,more={})=>({name,qty,set:'',number:'',finish:'nonfoil',scryfallId:'',...more});
 const sameQuantities=(first,second)=>{
  const totals=rows=>Object.fromEntries([...rows.reduce((m,c)=>{
   const n=c.name.toLowerCase();m.set(n,(m.get(n)||0)+c.qty);return m
  },new Map())].sort((a,b)=>a[0].localeCompare(b[0])));
  assert.deepEqual(totals(first),totals(second),'Physical quantities must never change during recovery');
 };
 const current=[unknown('Sol Ring',3),unknown('Arcane Signet',2),unknown('Counterspell',1),
  unknown('Lightning Bolt',4,{location:'Binder A'})];
 const source=[
  {name:'Sol Ring',qty:1,set:'cmm',number:'001',scryfallId:''},
  {name:'Sol Ring',qty:2,set:'2xm',number:'002',scryfallId:''},
  {name:'Arcane Signet',qty:3,set:'cmm',number:'003',scryfallId:''}, // Quantity mismatch: skip.
  unknown('Counterspell',1), // No print ID: skip.
  {name:'Lightning Bolt',qty:4,set:'m11',number:'148',scryfallId:''}
 ];
 const recovered=api.plan(current,source);
 assert.equal(recovered.recoveredNames,2);
 assert.equal(recovered.recoveredCopies,7);
 assert.equal(recovered.recoveredPrintings,3);
 assert.equal(recovered.quantityMismatches,1);
 assert.equal(recovered.missingIdentifiers,1);
 sameQuantities(current,recovered.entries);
 assert.deepEqual(recovered.entries.filter(c=>c.name==='Sol Ring').map(c=>c.qty).sort(),[1,2]);
 assert.deepEqual(recovered.entries.filter(c=>c.name==='Arcane Signet').map(c=>c.set),['']);
 assert.equal(recovered.entries.find(c=>c.name==='Lightning Bolt').location,'Binder A');

 const preidentified=[{name:'Island',qty:1,set:'m11',number:'235',finish:'nonfoil'}];
 const ignored=api.plan(preidentified,[{name:'Island',qty:1,set:'cmm',number:'111'}]);
 assert.equal(ignored.recoveredNames,0,'Previously verified printings must not be overwritten');
 assert.deepEqual(ignored.entries,api.normalize(preidentified));
 const ambiguous=api.plan([unknown('Opt',1,{location:'Binder 1'}),unknown('Opt',1,{location:'Binder 2'})],
  [{name:'Opt',qty:2,set:'m21',number:'59'}]);
 assert.equal(ambiguous.ambiguousNames,1);
 assert.equal(ambiguous.recoveredNames,0);
 sameQuantities([unknown('Opt',1,{location:'Binder 1'}),unknown('Opt',1,{location:'Binder 2'})],ambiguous.entries);
 const uuidA='c72b6a17-9a8e-4ef6-9fce-726d1d72a927';
 const uuidB='0a7b6a17-9a8e-4ef6-9fce-726d1d72a928';
 const distinct=api.normalize([
  {name:'Test card',qty:1,set:'test',number:'1',finish:'nonfoil',scryfallId:uuidA},
  {name:'Test card',qty:1,set:'test',number:'1',finish:'nonfoil',scryfallId:uuidB}
 ]);
 assert.equal(distinct.length,2,'Distinct Scryfall printing IDs must not collapse');
 assert.equal(api.hasId({scryfallId:'not-a-real-uuid'}),false,'Invalid Scryfall IDs must not be trusted');
}
console.log('PASS: physical printing recovery preserves quantities, explicit IDs and ambiguous names');


// Set Progress illustrated tiles: official symbols survive and art has explicit fallback.
assert.match(html,/id="setShowArt"/,'Artwork opt-out control must remain available');
assert.match(html,/function setSymbol\(code\)/,'Official set symbols must remain independent of art');
assert.match(html,/const setPromotionalArtwork=Object\.freeze\(/,'Curated promotional art source must be explicit');
assert.match(html,/blb:'https:\/\/media\.wizards\.com\//,'Bloomburrow should use an official asset URL');
assert.match(html,/ecl:'https:\/\//,'Lorwyn Eclipsed should have promotional art');
assert.match(html,/neo:'https:\/\//,'Kamigawa: Neon Dynasty should have promotional art');
assert.match(html,/iko:'https:\/\//,'Ikoria should have promotional art');
assert.match(html,/woe:'https:\/\/media\.wizards\.com\//,'Wilds of Eldraine should use Wizards-hosted promotional artwork');
assert.match(html,/function setArtMarkup\(code\)/,'Set artwork must use shared renderer');
assert.match(html,/data-fallback/,'Promotional artwork must fall back on error');
assert.match(html,/setArtMarkup\(r\.code\)/,'Set tiles must render background artwork');
assert.match(html,/setSymbol\(r\.code\)/,'Set tiles must still render official expansion symbols');
assert.match(html,/av-set-complete/,'Completed sets should have special styling');
console.log('PASS: Set Progress promo-art mapping, icons, fallback, preferences and completion styling');

 
// Native update-release discovery must not depend exclusively on embedded WebView GitHub fetch.
assert.match(html,/async function fetchGithubReleases\(repo\)/,'Updater must have native release discovery helper');
assert.match(html,/\/api\/app\/releases\?repo=/,'Updater must call the safe local release endpoint');
assert.match(html,/const items=await fetchGithubReleases\(repo\)/,'Update checker must use the native/browser fallback');
assert.match(html,/Open published GitHub releases/,'Update failure must retain a manual recovery link');
console.log('PASS: native update discovery and manual fallback');
