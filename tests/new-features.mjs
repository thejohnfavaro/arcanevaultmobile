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
