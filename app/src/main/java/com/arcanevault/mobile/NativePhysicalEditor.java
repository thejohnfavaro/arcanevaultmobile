package com.arcanevault.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.text.InputType;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Guarded direct physical-inventory edits. No parallel collection ledger. */
final class NativePhysicalEditor {
    interface Result { void complete(boolean success,String message); }
    private static final String KEY="vault_physicalCollectionV1";
    private static final String BACKUP="vault_nativePhysicalBackupV1";
    private static final ExecutorService WRITES=Executors.newSingleThreadExecutor();
    private final Activity activity;
    private final SharedPreferences preferences;
    private final CatalogStore catalog;

    NativePhysicalEditor(Activity activity,SharedPreferences preferences,CatalogStore catalog){
        this.activity=activity;this.preferences=preferences;this.catalog=catalog;
    }

    void edit(String name,JSONObject printing,Runnable onSaved){
        String set=printing.optString("set","");
        String collector=printing.optString("collectorNumber","");
        String id=printing.optString("id","");
        if(name.isEmpty() || id.isEmpty()){
            error("Cannot edit a printing without its official Scryfall ID");return;
        }
        EditText copies=new EditText(activity);
        copies.setInputType(InputType.TYPE_CLASS_NUMBER);
        copies.setHint("Total copies of this edition");
        copies.setText(Integer.toString(existingCopies(name,id,set,collector)));
        copies.setSelectAllOnFocus(true);
        LinearLayout layout=VaultNativeStyle.column(activity);
        layout.setPadding(VaultNativeStyle.dp(activity,20),0,VaultNativeStyle.dp(activity,20),0);
        layout.addView(copies);
        new AlertDialog.Builder(activity).setTitle("Edit physical copies")
            .setMessage(name+" · "+set.toUpperCase()+" #"+collector+
                "\nSet the total copies of this exact printing (0 removes it). This does not change Arena or proxies.")
            .setView(layout).setNegativeButton("Cancel",null)
            .setPositiveButton("Review", (d,w)-> {
                int total;
                try {total=Integer.parseInt(copies.getText().toString().trim());}
                catch(Exception ex){error("Enter a whole number between 0 and 99999");return;}
                if(total<0||total>99999){error("Quantity must be from 0 to 99999");return;}
                new AlertDialog.Builder(activity).setTitle("Confirm printing change")
                    .setMessage(name+" ("+set.toUpperCase()+" #"+collector+") → "+total+
                        " physical copies. A recovery snapshot is saved before applying.")
                    .setNegativeButton("Keep unchanged",null)
                    .setPositiveButton("Save", (dialog,which)->write(name,printing,total,onSaved)).show();
            }).show();
    }

    private int existingCopies(String name,String id,String set,String collector){
        try{
            JSONArray rows=new JSONArray(preferences.getString(KEY,"[]"));
            int count=0;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);
                if(row==null||!name.equalsIgnoreCase(row.optString("name","")))continue;
                String savedId=row.optString("scryfallId","");
                boolean editable="nonfoil".equalsIgnoreCase(row.optString("finish","nonfoil")) &&
                    row.optString("language","").isEmpty() &&
                    row.optString("condition","").isEmpty() &&
                    row.optString("location","").isEmpty();
                if(!editable)continue;
                if(!savedId.isEmpty() && savedId.equalsIgnoreCase(id))
                    count+=row.optInt("qty",0);
                else if(savedId.isEmpty()&&set.equalsIgnoreCase(row.optString("set",""))&&
                    collector.equalsIgnoreCase(row.optString("number","")))
                    count+=row.optInt("qty",0);
            }
            return count;
        }catch(Exception ex){return 0;}
    }

    private void write(String name,JSONObject printing,int newQty,Runnable onSaved){
        WRITES.execute(()->{
            String previous=preferences.getString(KEY,"[]");
            try{
                JSONArray old=new JSONArray(previous);
                if(old.length()>150000)throw new IllegalStateException("Inventory safety limit exceeded");
                JSONArray updated=new JSONArray();
                String id=printing.optString("id","");
                String set=printing.optString("set","");
                String number=printing.optString("collectorNumber","");
                String finish="nonfoil";
                // Preserve other finish/language/condition/location records.
                // This editor controls a single exact ID with the default finish only.
                JSONObject template=null;
                int matches=0;
                for(int i=0;i<old.length();i++){
                    JSONObject row=old.optJSONObject(i);
                    if(row==null)continue;
                    String currentId=row.optString("scryfallId","");
                    boolean match=name.equalsIgnoreCase(row.optString("name","")) &&
                        (id.equalsIgnoreCase(currentId) ||
                            (currentId.isEmpty()&&set.equalsIgnoreCase(row.optString("set",""))&&
                                number.equalsIgnoreCase(row.optString("number","")))) &&
                        finish.equalsIgnoreCase(row.optString("finish","nonfoil")) &&
                        row.optString("language","").isEmpty() &&
                        row.optString("condition","").isEmpty() &&
                        row.optString("location","").isEmpty();
                    if(match){
                        matches++;
                        if(template==null)template=new JSONObject(row.toString());
                    } else updated.put(new JSONObject(row.toString()));
                }
                // Distinct purchase-price or other source rows may share a printing.
                // Never collapse those into one record or erase their metadata.
                if(matches>1)throw new IllegalStateException(
                    "This printing has multiple separate ownership records. Edit them in Legacy tools.");
                if(newQty>0){
                    JSONObject edited=template==null?new JSONObject():template;
                    edited.put("name",name).put("qty",newQty)
                        .put("set",set).put("number",number).put("scryfallId",id);
                    if(template==null)edited.put("finish",finish);
                    updated.put(edited);
                }
                if(!preferences.edit().putString(BACKUP,previous).commit())
                    throw new IllegalStateException("Unable to preserve recovery snapshot");
                if(!preferences.edit().putString(KEY,updated.toString()).commit())
                    throw new IllegalStateException("Unable to save physical inventory");
                try{
                    catalog.physicalOwnership(new JSONObject().put("cards",updated));
                }catch(Exception dbFailure){
                    preferences.edit().putString(KEY,previous).commit();
                    catalog.physicalOwnership(new JSONObject().put("cards",new JSONArray(previous)));
                    throw dbFailure;
                }
                // The prior quote projection can no longer be assumed to
                // match inventory. Invalidate value and recalculate set
                // ownership from the updated native SQLite mirror.
                VaultWidgets.invalidateValue(activity);
                VaultWidgets.refreshSets(activity);
                activity.runOnUiThread(()->{
                    new AlertDialog.Builder(activity).setTitle("Physical printing saved")
                        .setMessage("Inventory updated. The previous inventory is stored as a recovery snapshot.")
                        .setPositiveButton("OK",(d,w)->onSaved.run()).show();
                });
            }catch(Exception error){
                activity.runOnUiThread(()->error("Could not save. "+error.getMessage()+
                    ". Check your recovery snapshot in Data & Sync."));
            }
        });
    }
    private void error(String message){
        activity.runOnUiThread(()->new AlertDialog.Builder(activity)
            .setTitle("Physical collection").setMessage(message).setPositiveButton("OK",null).show());
    }
}
