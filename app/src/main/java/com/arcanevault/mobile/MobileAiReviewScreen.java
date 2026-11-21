package com.arcanevault.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.ClickableSpan;
import android.text.method.LinkMovementMethod;
import android.text.TextPaint;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native read-only Android counterpart of Windows EDH Lab AI Review. */
final class MobileAiReviewScreen {
    private final Activity activity;
    private final MobileChatGptClient client;
    private final SharedPreferences prefs;
    private final CatalogStore catalog;
    private final java.util.function.Consumer<String> inspectCard;
    private final ExecutorService catalogReads=Executors.newSingleThreadExecutor();
    private int reviewGeneration;
    private final JSONObject deck;
    private final String board;
    private final LinearLayout root;
    private final Spinner mode;
    private final CheckBox shareOwned;
    private final TextView preview,result,status;
    private final Button run;
    private boolean running;
    private String response="";
    private String request="";
    private static final String[] MODES={"Deck health and consistency",
        "Owned-card substitutions","Combos and synergy"};
    private static final String[] KEYS={"health","owned","synergy"};
    private int dp(float value){return VaultNativeStyle.dp(activity,value);}

    MobileAiReviewScreen(Activity activity,MobileChatGptClient client,SharedPreferences prefs,
            JSONObject deck,String unsavedBoard,Runnable back,
            CatalogStore catalog,java.util.function.Consumer<String> inspectCard){
        this.activity=activity;this.client=client;this.prefs=prefs;this.deck=deck;board=unsavedBoard;
        this.catalog=catalog;this.inspectCard=inspectCard;
        root=VaultNativeStyle.column(activity);
        root.setPadding(dp(14),dp(10),dp(14),dp(24));
        root.setBackgroundColor(VaultNativeStyle.BACKGROUND);

        Button backButton=VaultNativeStyle.button(activity,"← Return to deck",false);
        root.addView(backButton);
        backButton.setOnClickListener(v->back.run());
        label("AI DECK REVIEW",20,true,VaultNativeStyle.GOLD);
        label(deck.optString("name","Saved deck")+" · read-only recommendations",
            12,false,VaultNativeStyle.SUBTEXT);
        label(client.status(),12,false,VaultNativeStyle.SUBTEXT);
        if(!client.connected())label("Connect your ChatGPT plan under Home → Options first. "+
            "You can still copy the review prompt.",12,false,VaultNativeStyle.GOLD);

        mode=new Spinner(activity);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,MODES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mode.setAdapter(adapter);
        root.addView(mode,new LinearLayout.LayoutParams(-1,dp(52)));
        shareOwned=new CheckBox(activity);
        shareOwned.setText("Include only this deck's ownership shortages (optional)");
        shareOwned.setTextColor(VaultNativeStyle.TEXT);
        shareOwned.setChecked(false);
        root.addView(shareOwned);
        Button viewPrompt=VaultNativeStyle.button(activity,"Preview exact data to share ▾",false);
        root.addView(viewPrompt);
        preview=VaultNativeStyle.label(activity,"",11,VaultNativeStyle.TEXT,false);
        preview.setTextIsSelectable(true);
        preview.setPadding(dp(8),dp(10),dp(8),dp(10));
        preview.setBackground(VaultNativeStyle.rounded(activity,
            VaultNativeStyle.ELEVATED,VaultNativeStyle.BORDER,8));
        preview.setVisibility(View.GONE);
        root.addView(preview);
        viewPrompt.setOnClickListener(v->{
            boolean open=preview.getVisibility()!=View.VISIBLE;
            preview.setVisibility(open?View.VISIBLE:View.GONE);
            viewPrompt.setText(open?"Hide data preview ▴":"Preview exact data to share ▾");
        });

        LinearLayout actions=VaultNativeStyle.row(activity);
        run=VaultNativeStyle.button(activity,"Analyze deck",true);
        Button copy=VaultNativeStyle.button(activity,"Copy prompt",false);
        actions.addView(run,new LinearLayout.LayoutParams(0,dp(48),1));
        LinearLayout.LayoutParams second=new LinearLayout.LayoutParams(0,dp(48),1);
        second.leftMargin=dp(7);
        actions.addView(copy,second);
        root.addView(actions);
        status=VaultNativeStyle.label(activity,"No automatic requests. Analyze sends the shown prompt.",
            12,VaultNativeStyle.SUBTEXT,false);
        root.addView(status);
        Button copyResult=VaultNativeStyle.button(activity,"Copy review",false);
        root.addView(copyResult);
        result=VaultNativeStyle.label(activity,"Select Analyze to review your deck.",
            14,VaultNativeStyle.TEXT,false);
        result.setLinksClickable(true);
        result.setMovementMethod(LinkMovementMethod.getInstance());
        result.setPadding(dp(9),dp(16),dp(9),dp(16));
        result.setBackground(VaultNativeStyle.rounded(activity,
            VaultNativeStyle.SURFACE,VaultNativeStyle.BORDER,10));
        root.addView(result);
        Runnable refresh=()->{
            try{
                String text=AiReviewContract.deckText(deck,board);
                String coverage="";
                if(shareOwned.isChecked()){
                    boolean arena=NativeDeckFit.arenaFormat(deck.optString("format",""));
                    String key=arena?"vault_cards":"vault_physicalCollectionV1";
                    coverage=AiReviewContract.coverage(deck,
                        board==null?NativeDeckDraft.formatEntries(deck.optJSONArray("entries")):board,
                        new JSONArray(prefs.getString(key,"[]")),arena?"Arena":"Physical");
                }
                int index=Math.max(0,Math.min(mode.getSelectedItemPosition(),KEYS.length-1));
                request=AiReviewContract.prompt(text,KEYS[index],coverage);
                preview.setText(request);
                run.setEnabled(client.connected()&&!running);
            }catch(Exception ex){
                request="";run.setEnabled(false);
                preview.setText("Invalid deck: "+ex.getMessage());
            }
        };
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                refresh.run();
            }
        });
        shareOwned.setOnCheckedChangeListener((v,checked)->refresh.run());
        copy.setOnClickListener(v->{
            refresh.run();
            if(!request.isEmpty())copy("Deck review prompt",request);
        });
        copyResult.setOnClickListener(v->{
            if(!response.isEmpty())copy("AI deck review",response);
        });
        run.setOnClickListener(v->{
            refresh.run();
            if(request.isEmpty()||running||!client.connected())return;
            String exactPrompt=request;running=true;run.setEnabled(false);
            status.setText("Analyzing the previewed deck…");
            client.review(exactPrompt,(answer,model,error)->{
                running=false;refresh.run();
                if(error!=null){
                    status.setText("AI review unavailable: "+error);
                    return;
                }
                response=answer;
                result.setText(formatReview(AiReviewCardMentions.EXPLICIT.matcher(answer).replaceAll("$1")));
                linkVerifiedCards(answer);
                status.setText("Review complete ("+model+"). No saved deck or collection changes.");
            });
        });
        refresh.run();
    }
    /** Exact local catalog verification is mandatory; the AI cannot invent a
     * clickable card by merely formatting arbitrary text as a name. */
    private void linkVerifiedCards(String answer){
        int generation=++reviewGeneration;
        JSONArray entries;
        try{
            entries=board==null?deck.optJSONArray("entries"):NativeDeckDraft.parseEntries(board);
        }catch(Exception ex){entries=deck.optJSONArray("entries");}
        final JSONArray snapshot=entries;
        final java.util.List<String> candidates=AiReviewCardMentions.candidates(
            answer,snapshot,deck.optString("commander",""));
        catalogReads.execute(()->{
            // The visible review phrase may be only the front face of an
            // MDFC. Keep display text separate from its verified full identity.
            java.util.LinkedHashMap<String,String> confirmed=new java.util.LinkedHashMap<>();
            for(String candidate:candidates){
                try{
                    JSONObject details=catalog.cardDetailsForReviewMention(candidate);
                    String exact=details.optString("name","");
                    if(!exact.isEmpty()){
                        confirmed.put(exact,exact);
                        if(!candidate.equalsIgnoreCase(exact) &&
                           exact.regionMatches(true,0,candidate+" // ",0,
                               (candidate+" // ").length()))
                            confirmed.put(candidate,exact);
                    }
                }catch(Exception ignored){ /* No verified catalog entry, no link. */ }
            }
            java.util.List<String> sorted=AiReviewCardMentions.longestFirst(confirmed.keySet());
            activity.runOnUiThread(()->{
                if(generation!=reviewGeneration)return;
                // Work right-to-left so replacing visible markers never shifts
                // positions of earlier references. Only marked phrases link.
                SpannableStringBuilder text=new SpannableStringBuilder(formatReview(answer));
                java.util.ArrayList<int[]> positions=new java.util.ArrayList<>();
                java.util.ArrayList<String> labels=new java.util.ArrayList<>();
                java.util.regex.Matcher m=AiReviewCardMentions.EXPLICIT.matcher(text);
                while(m.find()){
                    positions.add(new int[]{m.start(),m.end()});
                    labels.add(m.group(1).trim());
                }
                for(int i=positions.size()-1;i>=0;i--){
                    int[] pos=positions.get(i);
                    String shown=labels.get(i);
                    String selected=confirmed.get(shown);
                    text.replace(pos[0],pos[1],shown);
                    if(selected==null)continue; // Display unverified names, never link.
                    text.setSpan(new ClickableSpan(){
                        @Override public void onClick(View widget){inspectCard.accept(selected);}
                        @Override public void updateDrawState(TextPaint paint){
                            paint.setColor(VaultNativeStyle.GOLD);
                            paint.setUnderlineText(true);
                        }
                    },pos[0],pos[0]+shown.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                result.setText(text);
            });
        });
    }

    View view(){return root;}
    private void label(String text,int size,boolean bold,int color){
        TextView label=VaultNativeStyle.label(activity,text,size,color,bold);
        label.setPadding(0,dp(5),0,dp(8));
        root.addView(label);
    }
    private void copy(String label,String text){
        ClipboardManager manager=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if(manager==null)return;
        manager.setPrimaryClip(ClipData.newPlainText(label,text));
        status.setText(label+" copied. No data was sent automatically.");
    }

    /** Never render remote HTML or executable markup. Native text spans only. */
    static CharSequence formatReview(String markdown){
        SpannableStringBuilder builder=new SpannableStringBuilder();
        String[] lines=(markdown==null?"":markdown).replace("\r\n","\n").split("\n");
        boolean table=false;
        for(int i=0;i<lines.length;i++){
            String line=lines[i].trim();
            if(line.isEmpty()){builder.append('\n');table=false;continue;}
            if(line.startsWith("|")){
                // Mobile-first vertical representation instead of clipping a wide table.
                if(line.matches("^\\|?[\\s|:\\-]+\\|?$"))continue;
                String[] cells=line.replaceAll("^\\||\\|$","").split("\\|");
                if(!table){table=true;continue;} // column headings
                if(cells.length>=3)line="• "+cells[0].trim()+": "+
                    cells[1].trim()+"\n   "+cells[2].trim();
                else line="• "+line.replace('|',' ');
            }else table=false;
            int level=0;while(level<line.length()&&line.charAt(level)=='#')level++;
            boolean heading=level>0&&level<=4&&line.length()>level&&line.charAt(level)==' ';
            if(heading)line=line.substring(level+1);
            line=line.replaceFirst("^[-*+]\\s+","• ");
            int offset=builder.length();
            Matcher matcher=Pattern.compile("\\*\\*([^*]+)\\*\\*|\\*([^*]+)\\*").matcher(line);
            int start=0;
            while(matcher.find()){
                builder.append(line.substring(start,matcher.start()));
                int from=builder.length();
                String value=matcher.group(1)!=null?matcher.group(1):matcher.group(2);
                builder.append(value);
                builder.setSpan(new StyleSpan(matcher.group(1)!=null?Typeface.BOLD:Typeface.ITALIC),
                    from,builder.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                start=matcher.end();
            }
            builder.append(line.substring(start)).append('\n');
            if(heading){
                builder.setSpan(new StyleSpan(Typeface.BOLD),offset,builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new RelativeSizeSpan(level<=2?1.24f:1.08f),
                    offset,builder.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(VaultNativeStyle.GOLD),
                    offset,builder.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }
}
