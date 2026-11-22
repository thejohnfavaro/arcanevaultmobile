package com.arcanevault.mobile;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/** Local-catalog suggestions. No collection mutation or remote requests. */
final class NativeCommanderSuggestions {
    private NativeCommanderSuggestions(){}
    static void attach(Activity activity,CatalogStore catalog,
            AutoCompleteTextView input,String format,ExecutorService worker){
        Handler handler=new Handler(Looper.getMainLooper());
        AtomicInteger generation=new AtomicInteger();
        Runnable[] pending={null};
        input.setThreshold(2);
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void afterTextChanged(Editable s){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                int token=generation.incrementAndGet();
                if(pending[0]!=null)handler.removeCallbacks(pending[0]);
                String term=s.toString().trim();
                if(term.length()<2){input.dismissDropDown();return;}
                pending[0]=()->worker.execute(()->{
                    try{
                        Map<String,String> args=new HashMap<>();
                        args.put("q",term);
                        args.put("source","Arena Brawl".equalsIgnoreCase(format)?"arena":"");
                        args.put("size","80");
                        JSONObject found=catalog.commanders(args);
                        JSONArray rows=found.optJSONArray("cards");
                        ArrayList<String> names=new ArrayList<>();
                        if(rows!=null)for(int i=0;i<rows.length();i++){
                            JSONObject row=rows.optJSONObject(i);
                            if(row==null)continue;
                            String name=row.optString("name","").trim();
                            if(name.isEmpty())continue;
                            // Face-name autocomplete uses the castable front face.
                            int split=name.indexOf(" // ");
                            String front=split<0?name:name.substring(0,split);
                            if(!front.toLowerCase(java.util.Locale.ROOT).contains(
                                    term.toLowerCase(java.util.Locale.ROOT)))continue;
                            if(!names.contains(front))names.add(front);
                            if(names.size()>=8)break;
                        }
                        handler.post(()->{
                            if(activity.isFinishing()||generation.get()!=token||
                                !input.hasFocus())return;
                            ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,
                                android.R.layout.simple_dropdown_item_1line,names);
                            input.setAdapter(adapter);
                            if(!names.isEmpty()&&
                                !(names.size()==1&&names.get(0).equalsIgnoreCase(term)))
                                input.showDropDown();
                            else input.dismissDropDown();
                        });
                    }catch(Exception ignored){
                        handler.post(()->{
                            if(generation.get()==token)input.dismissDropDown();
                        });
                    }
                });
                handler.postDelayed(pending[0],160);
            }
        });
    }
}
