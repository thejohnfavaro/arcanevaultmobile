import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(p,'utf8');
const activity=read('app/src/main/java/com/arcanevault/mobile/MainActivity.java');
const shell=read('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java');
const catalog=read('app/src/main/java/com/arcanevault/mobile/NativeCatalogScreen.java');
const commander=read('app/src/main/java/com/arcanevault/mobile/NativeCommanderBrowserScreen.java');
const workspace=read('app/src/main/java/com/arcanevault/mobile/NativeWorkspaceScreen.java');
const gradle=read('app/build.gradle');

// Old hardware buttons and modern gesture dispatch must reach same handler.
assert.match(activity,/@Override public void onBackPressed\(\)\{\s*handleAppBack\(\);/);
assert.match(activity,/getOnBackInvokedDispatcher\(\)\.registerOnBackInvokedCallback/);
assert.match(activity,/systemBackCallback=this::handleAppBack/);
assert.match(activity,/getOnBackInvokedDispatcher\(\)\.unregisterOnBackInvokedCallback/);
assert.match(activity,/nativeController\.onBack\(\)/);
assert.match(activity,/if\(nativeController!=null\)nativeController\.close\(\)/);
assert.match(activity,/NativeCompanionScreen\.createController\(this,/);
assert.match(activity,/if \("\\"none\\""\.equals\(result\)\) showNativeHome\(\)/,
  'Legacy WebView dialog dismissal must remain intact');
assert.match(activity,/finish\(\)/,'Home may exit only after stack is empty');

// One native shell handles tabs and nested in-workspace screens.
assert.match(shell,/private final ArrayDeque<String> navigationHistory=new ArrayDeque<>\(\)/);
assert.match(shell,/navigationHistory\.size\(\)>=32/);
assert.match(shell,/boolean onBack\(\)/);
for(const method of ['nativeCatalog.onBack()','commanderBrowser.onBack()',
  'workspace.onBack()','navigationHistory.removeLast()',
  'show("home")','restoringHistory','workspace.confirmLeave(()->show(next))']){
  assert.ok(shell.includes(method),'Missing native back routing '+method);
}
assert.match(shell,/if\(!"decks"\.equals\(scope\)\)remember\(scope\)/,
  'EDH Lab commander to saved-deck handoff must push previous workspace');
assert.match(shell,/if\(!next\.equals\(scope\)&&!restoringHistory\)remember\(scope\)/);

// Card inspectors follow details -> printings -> printing -> previous stage.
assert.match(catalog,/boolean onBack\(\)/);
assert.match(catalog,/inspectorBack\.run\(\)/);
assert.match(catalog,/inspectorBack=previous/);
assert.match(catalog,/inspectorBack=null/);
assert.match(catalog,/back\.setOnClickListener\(v->onBack\(\)\)/);

// Commander preview on a phone is a nested page, not an exit.
assert.match(commander,/boolean onBack\(\)/);
assert.match(commander,/wide\|\|previewScroll\.getVisibility\(\)!=View\.VISIBLE/);
assert.match(commander,/previewScroll\.setVisibility\(View\.GONE\)/);

// The unsaved-deck draft is guarded on Back AND cross-tab changes.
assert.match(workspace,/case "deck-edit":/);
assert.match(workspace,/case "deck-details":/);
assert.match(workspace,/case "shortlist-preview":/);
assert.match(workspace,/case "set-details":/);
assert.match(workspace,/boolean confirmLeave\(Runnable afterConfirmation\)/);
assert.match(workspace,/private boolean editorChanged\(\)/);
assert.match(workspace,/Discard unsaved deck changes\?/);
assert.match(workspace,/Keep editing/);
assert.match(workspace,/setPositiveButton\("Discard draft"/);
assert.match(workspace,/screenState="leaving-editor"/,
  'Confirmed leaving must not recursively trigger the dirty editor dialog');
assert.match(workspace,/editorOriginalMainboard=lines.getText\(\).toString\(\)/);
assert.match(workspace,/deckSaver\.save\(deck,revised/,
  'Persistence still requires normal recovery-backed compare-and-swap save');

// Back from Shortlist preview restores the original gallery page/search and scroll.
assert.match(workspace,/shortlistRestoreScrollY=workspaceScroll\.getScrollY\(\)/);
assert.match(workspace,/shortlistNeedsScrollRestore=true/);
assert.match(workspace,/workspaceScroll\.scrollTo\(0,shortlistRestoreScrollY\)/);
assert.match(workspace,/SHORTLIST_PAGE_SIZE=48/);

// Sync subtabs navigate one state back without modifying any synced data.
assert.match(workspace,/private final ArrayDeque<String> syncHistory/);
assert.match(workspace,/syncHistory\.addLast\(syncSection\)/);
assert.match(workspace,/syncSection=syncHistory\.removeLast\(\)/);
assert.ok(Number(gradle.match(/versionCode (\d+)/)?.[1])>=84);
console.log('PASS: Android hardware/gesture Back navigates one page with safe deck drafts, nested cards and bounded history');
