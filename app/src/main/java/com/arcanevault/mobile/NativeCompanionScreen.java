package com.arcanevault.mobile;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * First screen of the native Android companion. The authoritative inventory
 * remains in existing Android SharedPreferences; this is a read-only projection.
 * No WebView, HTML, JavaScript, second copy of inventory, or data migration.
 */
final class NativeCompanionScreen {
    private static final int BG = Color.rgb(13, 16, 23);
    private static final int PANEL = Color.rgb(27, 32, 45);
    private static final int TEXT = Color.rgb(240, 244, 251);
    private static final int MUTED = Color.rgb(171, 186, 205);
    private static final int ACCENT = Color.rgb(106, 225, 208);
    private static final int PAGE_SIZE = 60;
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Runnable openLegacy;
    private final LinearLayout root;
    private final LinearLayout content;
    private final TextView heading;
    private final TextView message;
    private String scope = "home";
    private String query = "";
    private int visibleCount = PAGE_SIZE;
    private List<CardRow> physical = Collections.emptyList();
    private List<CardRow> arena = Collections.emptyList();

    private static final class CardRow {
        final String name;
        final int qty;
        CardRow(String name, int qty) { this.name = name; this.qty = qty; }
    }

    private NativeCompanionScreen(Activity activity, SharedPreferences prefs, Runnable openLegacy) {
        this.activity = activity;
        this.prefs = prefs;
        this.openLegacy = openLegacy;
        root = column();
        root.setBackgroundColor(BG);
        heading = text("ARCANE VAULT", 24, TEXT, true);
        heading.setPadding(dp(18), dp(18), dp(18), dp(4));
        root.addView(heading);
        message = text("Loading your local collection…", 13, MUTED, false);
        message.setPadding(dp(18), 0, dp(18), dp(12));
        root.addView(message);
        LinearLayout nav = new LinearLayout(activity);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        for (String tab : new String[]{"Home", "Physical", "Arena", "Legacy tools"}) {
            Button action = button(tab);
            nav.addView(action, new LinearLayout.LayoutParams(0, dp(48), 1));
            action.setOnClickListener(v -> {
                if (tab.equals("Legacy tools")) { openLegacy.run(); return; }
                scope = tab.toLowerCase(java.util.Locale.ROOT);
                query = "";
                visibleCount = PAGE_SIZE;
                render();
            });
        }
        root.addView(nav);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        content = column();
        content.setPadding(dp(14), dp(12), dp(14), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        render();
        refresh();
    }

    static View create(Activity activity, SharedPreferences prefs, Runnable openLegacy) {
        return new NativeCompanionScreen(activity, prefs, openLegacy).root;
    }

    private int dp(float value) { return (int)(value * activity.getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button button(String value) {
        Button b = new Button(activity);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setMinimumHeight(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        return b;
    }
    private View panel(String title, String detail) {
        LinearLayout block = column();
        block.setPadding(dp(16), dp(15), dp(16), dp(15));
        block.setBackgroundColor(PANEL);
        TextView name = text(title, 18, TEXT, true);
        TextView description = text(detail, 13, MUTED, false);
        description.setPadding(0, dp(5), 0, 0);
        block.addView(name);
        block.addView(description);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        content.addView(block, lp);
        return block;
    }

    /** Parse only the native authoritative inventories off the UI thread. */
    private static List<CardRow> load(SharedPreferences prefs, String key) throws Exception {
        String source = prefs.getString(key, "[]");
        JSONArray rows = new JSONArray(source == null ? "[]" : source);
        if (rows.length() > 150000) throw new IllegalArgumentException("Inventory exceeds safety limit");
        ArrayList<CardRow> result = new ArrayList<>(rows.length());
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String name = row.optString("name", "").trim();
            int qty = row.optInt("qty", 0);
            if (!name.isEmpty() && qty > 0) result.add(new CardRow(name, qty));
        }
        result.sort(Comparator.comparing(row -> row.name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private void refresh() {
        message.setText("Reading existing native inventories…");
        // Do not block startup on large user collection JSON parsing.
        new Thread(() -> {
            try {
                List<CardRow> paper = load(prefs, "vault_physicalCollectionV1");
                List<CardRow> digital = load(prefs, "vault_cards");
                activity.runOnUiThread(() -> {
                    physical = paper;
                    arena = digital;
                    message.setText("Local collection • read-only native view");
                    render();
                });
            } catch (Exception ex) {
                activity.runOnUiThread(() -> {
                    message.setText("Inventory unavailable: open Legacy tools to check or restore data.");
                    render();
                });
            }
        }, "arcane-native-collection-read").start();
    }

    private static long copies(List<CardRow> rows) {
        long total = 0;
        for (CardRow row : rows) total += row.qty;
        return total;
    }

    private void render() {
        content.removeAllViews();
        if (scope.equals("home")) {
            panel("Your collection", "Native Android companion • no WebView required for this screen");
            panel("Physical cards", String.format(java.util.Locale.US,
                    "%,d copies across %,d rows", copies(physical), physical.size()));
            panel("Arena cards", String.format(java.util.Locale.US,
                    "%,d copies across %,d rows", copies(arena), arena.size()));
            panel("Safely transitioning", "This screen reads your existing native inventory without changing it. " +
                    "Existing Set Progress, imports, sync, decks and detailed catalog remain available under Legacy tools.");
            Button updater = button("Check for app updates");
            content.addView(updater);
            updater.setOnClickListener(action -> new NativeAppUpdater(activity).check());
            Button refresh = button("Refresh local totals");
            content.addView(refresh);
            refresh.setOnClickListener(v -> refresh());
            return;
        }
        List<CardRow> rows = scope.equals("physical") ? physical : arena;
        panel(scope.equals("physical") ? "Physical inventory" : "Arena inventory",
                String.format(java.util.Locale.US, "%,d copies • %,d rows • read-only", copies(rows), rows.size()));
        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("Find an owned card");
        search.setText(query);
        content.addView(search);
        LinearLayout results = column();
        content.addView(results);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString();
                visibleCount = PAGE_SIZE;
                drawRows(results, rows);
            }
            public void afterTextChanged(Editable s) {}
        });
        drawRows(results, rows);
    }
    private void drawRows(LinearLayout target, List<CardRow> rows) {
        target.removeAllViews();
        int matched = 0, shown = 0;
        String filter = query.trim().toLowerCase(java.util.Locale.ROOT);
        for (CardRow row : rows) {
            if (!row.name.toLowerCase(java.util.Locale.ROOT).contains(filter)) continue;
            matched++;
            if (shown++ >= visibleCount) continue;
            TextView card = text(row.name + "    ×" + row.qty, 15, TEXT, false);
            card.setPadding(dp(12), dp(13), dp(12), dp(13));
            card.setBackgroundColor(PANEL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(3);
            target.addView(card, lp);
        }
        TextView count = text(matched + " matching entries" +
                (matched > visibleCount ? " • showing first " + visibleCount : ""), 12, MUTED, false);
        count.setPadding(0, dp(9), 0, dp(8));
        target.addView(count);
        if (matched > visibleCount) {
            Button more = button("Load next 60");
            target.addView(more);
            more.setOnClickListener(v -> { visibleCount += PAGE_SIZE; drawRows(target, rows); });
        }
    }
}
