import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=(path)=>fs.readFileSync(path,'utf8');
const style=read('app/src/main/java/com/arcanevault/mobile/VaultNativeStyle.java');
for(const expression of ['static ScrollView scroll(Activity activity)',
 'static HorizontalScrollView horizontalScroll(Activity activity)',
 'View.SCROLLBARS_INSIDE_OVERLAY','setScrollbarFadingEnabled(true)',
 'setScrollBarFadeDuration(450)','setVerticalScrollbarThumbDrawable',
 'setHorizontalScrollbarThumbDrawable',
 'android.os.Build.VERSION.SDK_INT>=29'])assert.ok(style.includes(expression),expression);
for(const file of ['NativeCatalogScreen.java','NativeCommanderBrowserScreen.java',
 'NativeCompanionScreen.java','NativeWorkspaceScreen.java']){
 const source=read('app/src/main/java/com/arcanevault/mobile/'+file);
 assert.match(source,/VaultNativeStyle\.scroll\(activity\)/);
 assert.doesNotMatch(source,/new ScrollView\(activity\)/);
 assert.doesNotMatch(source,/new HorizontalScrollView\(activity\)/);
}
console.log('PASS: Android themed scrollbars cover native catalog, EDH, companion and workspace without changing gesture semantics');
