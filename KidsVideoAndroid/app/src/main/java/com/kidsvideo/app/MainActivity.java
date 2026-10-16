package com.kidsvideo.app;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final long REFRESH_INTERVAL_MS = 4L * 60L * 60L * 1000L;

    private Prefs prefs;
    private YouTubeRepository repo;
    private SpeechHelper speech;
    private LinearLayout list;
    private LinearLayout cats;
    private EditText search;
    private TextView status;
    private List<VideoItem> videos = new ArrayList<>();
    private String category = "Home";
    private boolean loading = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        repo = new YouTubeRepository(this);
        speech = new SpeechHelper(this);
        build();
        if (prefs.apiKey().isEmpty() || prefs.parentPin().isEmpty()) {
            startActivity(new Intent(this, ApiSetupActivity.class));
        } else {
            load();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null) removeRejectedFromMemory();
        if (prefs != null && !prefs.apiKey().isEmpty() && !loading
                && (videos.isEmpty() || prefs.cacheTime() == 0)) {
            load();
        } else if (list != null && !videos.isEmpty()) {
            render();
        }
    }

    private void removeRejectedFromMemory() {
        Set<String> rejected = prefs.playbackRejectedVideos();
        if (rejected.isEmpty() || videos.isEmpty()) return;
        List<VideoItem> keep = new ArrayList<>();
        for (VideoItem v : videos) if (!rejected.contains(v.id)) keep.add(v);
        videos = keep;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(15, 15, 15));

        boolean wideHeader = Ui.isTv(this) || (Ui.isTablet(this) && Ui.isLandscape(this));
        if (wideHeader) {
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 8));
            TextView logo = makeLogo();
            top.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
            search = makeSearch();
            int searchWidth = Ui.isTv(this) ? Ui.dp(this, 440) : Ui.dp(this, 360);
            top.addView(search, new LinearLayout.LayoutParams(searchWidth, Ui.dp(this, 48)));
            top.addView(makeMicButton(), new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 48)));
            top.addView(makeGearButton(), new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 48)));
            root.addView(top);
        } else {
            LinearLayout topRow = new LinearLayout(this);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            topRow.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 4));
            topRow.addView(makeLogo(), new LinearLayout.LayoutParams(0, -2, 1));
            topRow.addView(makeMicButton(), new LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 46)));
            topRow.addView(makeGearButton(), new LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 46)));
            root.addView(topRow);
            search = makeSearch();
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 48));
            sp.setMargins(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 6));
            root.addView(search, sp);
        }

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        cats = new LinearLayout(this);
        cats.setOrientation(LinearLayout.HORIZONTAL);
        cats.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 6));
        hsv.addView(cats);
        root.addView(hsv);
        buildCategories();

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(Ui.isTv(this) ? 16 : 14);
        status.setPadding(Ui.dp(this, 18), Ui.dp(this, 6), Ui.dp(this, 18), Ui.dp(this, 6));
        root.addView(status);

        ScrollView sc = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.isTv(this) ? 28 : 12;
        list.setPadding(Ui.dp(this, side), 0, Ui.dp(this, side), Ui.dp(this, 30));
        sc.addView(list);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        search.setOnEditorActionListener((v, a, e) -> {
            render();
            return true;
        });
        setContentView(root);
    }

    private TextView makeLogo() {
        TextView logo = new TextView(this);
        logo.setText("▶  " + BuildConfig.APP_DISPLAY_NAME);
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(Ui.isTv(this) ? 25 : 21);
        logo.setTypeface(null, 1);
        return logo;
    }

    private EditText makeSearch() {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint("Search approved videos");
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setBackground(Ui.rounded(Color.rgb(35, 35, 35), 22, this));
        e.setPadding(Ui.dp(this, 15), 0, Ui.dp(this, 15), 0);
        return e;
    }

    private Button makeMicButton() {
        Button mic = new Button(this);
        mic.setText("🎤");
        mic.setContentDescription("Voice search");
        mic.setOnClickListener(v -> voiceSearch());
        return mic;
    }

    private Button makeGearButton() {
        Button gear = new Button(this);
        gear.setText("⚙");
        gear.setContentDescription("Parent settings");
        gear.setOnClickListener(v -> askPin());
        return gear;
    }

    private void buildCategories() {
        cats.removeAllViews();
        for (String c : AppConfig.categories()) {
            TextView x = new TextView(this);
            x.setText(c);
            x.setTextSize(Ui.isTv(this) ? 17 : 15);
            x.setFocusable(true);
            x.setClickable(true);
            Ui.styleCategory(x, c.equals(category), false);
            x.setOnClickListener(v -> {
                category = c;
                rebuildCategories();
                render();
            });
            x.setOnFocusChangeListener((v, focused) -> {
                TextView chip = (TextView) v;
                boolean selected = chip.getText().toString().equals(category);
                Ui.styleCategory(chip, selected, focused);
                if (focused && status != null) {
                    status.setText("Move: " + chip.getText() + "   •   Press OK to select");
                } else if (!loading && status != null) {
                    renderStatusForCurrentCategory();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            cats.addView(x, lp);
        }
    }

    private void rebuildCategories() {
        for (int i = 0; i < cats.getChildCount(); i++) {
            TextView v = (TextView) cats.getChildAt(i);
            boolean selected = v.getText().toString().equals(category);
            Ui.styleCategory(v, selected, v.hasFocus());
        }
    }

    private void load() {
        if (loading || prefs.apiKey().isEmpty()) return;
        List<VideoItem> cache = prefs.loadVideos();
        if (!cache.isEmpty()) {
            videos = cache;
            removeRejectedFromMemory();
            render();
            long age = System.currentTimeMillis() - prefs.cacheTime();
            if (age >= 0 && age < REFRESH_INTERVAL_MS) return;
        }

        loading = true;
        status.setText(cache.isEmpty() ? "Loading approved channels…" : "Refreshing approved channels…");
        repo.refresh(new YouTubeRepository.Callback() {
            public void onSuccess(List<VideoItem> v) {
                runOnUiThread(() -> {
                    loading = false;
                    videos = v;
                    removeRejectedFromMemory();
                    render();
                });
            }

            public void onError(String m) {
                runOnUiThread(() -> {
                    loading = false;
                    status.setText("Could not refresh: " + m);
                });
            }
        });
    }

    private void renderStatusForCurrentCategory() {
        if (status == null) return;
        int count = 0;
        Set<String> fav = prefs.favorites();
        List<String> hist = prefs.history();
        for (VideoItem v : videos) {
            if (prefs.isPlaybackRejected(v.id)) continue;
            boolean ok = category.equals("Home")
                    || category.equals(v.category)
                    || (category.equals("Favourites") && fav.contains(v.id))
                    || (category.equals("Watched") && hist.contains(v.id));
            if (ok) count++;
        }
        status.setText("Selected: " + category + "   •   " + count + " approved long videos");
    }

    private void render() {
        if (list == null || search == null) return;
        list.removeAllViews();
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        Set<String> fav = prefs.favorites();
        List<String> hist = prefs.history();
        int shown = 0;
        for (VideoItem v : videos) {
            if (prefs.isPlaybackRejected(v.id)) continue;
            boolean ok = category.equals("Home")
                    || category.equals(v.category)
                    || (category.equals("Favourites") && fav.contains(v.id))
                    || (category.equals("Watched") && hist.contains(v.id));
            if (!ok) continue;
            if (!q.isEmpty() && !(v.title.toLowerCase(Locale.ROOT).contains(q)
                    || v.channelTitle.toLowerCase(Locale.ROOT).contains(q)
                    || v.category.toLowerCase(Locale.ROOT).contains(q))) continue;
            list.addView(card(v));
            shown++;
        }
        if (shown == 0) {
            TextView e = new TextView(this);
            e.setText(q.isEmpty()
                    ? "No playable approved videos in this section yet."
                    : "No approved videos match ‘" + q + "’. " + AppConfig.searchSuggestion());
            e.setTextColor(Color.LTGRAY);
            e.setTextSize(Ui.isTv(this) ? 21 : 18);
            e.setPadding(Ui.dp(this, 22), Ui.dp(this, 30), Ui.dp(this, 22), Ui.dp(this, 30));
            list.addView(e);
        }
        if (!loading) {
            status.setText("Selected: " + category + "   •   " + shown + " shown   •   " + videos.size() + " playable candidates");
        }
    }

    private View card(VideoItem v) {
        boolean horizontal = Ui.useHorizontalCards(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        row.setGravity(horizontal ? Gravity.CENTER_VERTICAL : Gravity.NO_GRAVITY);
        row.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 12));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(Ui.rounded(Color.rgb(25, 25, 25), 12, this));

        ImageView im = new ImageView(this);
        im.setScaleType(ImageView.ScaleType.CENTER_CROP);
        im.setBackgroundColor(Color.DKGRAY);
        im.setAdjustViewBounds(false);
        int thumbWdp = Ui.thumbnailWidthDp(this);
        int thumbHdp = Math.round(thumbWdp * 9f / 16f);
        if (horizontal) {
            row.addView(im, new LinearLayout.LayoutParams(Ui.dp(this, thumbWdp), Ui.dp(this, thumbHdp)));
        } else {
            row.addView(im, new LinearLayout.LayoutParams(-1, Ui.dp(this, thumbHdp)));
        }
        ThumbnailLoader.load(im, v.thumbnailUrl);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(Ui.dp(this, 14), Ui.dp(this, horizontal ? 4 : 9), Ui.dp(this, 8), Ui.dp(this, 8));

        TextView title = new TextView(this);
        title.setText(v.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(Ui.isTv(this) ? 21 : (Ui.isTablet(this) ? 19 : 17));
        title.setTypeface(null, 1);
        title.setMaxLines(Ui.isTv(this) ? 3 : 2);
        meta.addView(title);

        TextView sub = new TextView(this);
        sub.setText(v.channelTitle + "  •  " + v.category + "  •  " + v.durationLabel());
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(Ui.isTv(this) ? 16 : 14);
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        meta.addView(sub);

        Button fav = new Button(this);
        fav.setText(prefs.isFavorite(v.id) ? "★ Favourite" : "☆ Favourite");
        fav.setAllCaps(false);
        fav.setOnClickListener(x -> {
            prefs.toggleFavorite(v.id);
            render();
        });
        meta.addView(fav, new LinearLayout.LayoutParams(-2, -2));

        if (horizontal) row.addView(meta, new LinearLayout.LayoutParams(0, -2, 1));
        else row.addView(meta, new LinearLayout.LayoutParams(-1, -2));

        row.setOnClickListener(x -> open(v));
        row.setOnFocusChangeListener((x, f) -> Ui.focusScale(x, f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        row.setLayoutParams(lp);
        return row;
    }

    private void open(VideoItem v) {
        if (prefs.isPlaybackRejected(v.id)) {
            removeRejectedFromMemory();
            render();
            return;
        }
        TimeLimit t = new TimeLimit(this);
        if (!t.canWatch()) {
            new AlertDialog.Builder(this)
                    .setTitle("Viewing time finished")
                    .setMessage("The parent-set viewing limit has been reached for today.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        prefs.addHistory(v.id);
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("id", v.id);
        i.putExtra("title", v.title);
        startActivity(i);
    }

    private void voiceSearch() {
        status.setText("Listening…");
        speech.listen(prefs.voiceLocale(), new SpeechHelper.Callback() {
            public void onResult(String t) {
                runOnUiThread(() -> {
                    search.setText(t);
                    status.setText("You said: " + t);
                    render();
                });
            }

            public void onError(String m) {
                runOnUiThread(() -> status.setText(m));
            }
        });
    }

    private void askPin() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Parent PIN")
                .setView(e)
                .setPositiveButton("Open", (d, w) -> {
                    if (e.getText().toString().equals(prefs.parentPin())) {
                        startActivity(new Intent(this, ParentSettingsActivity.class));
                    } else {
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (repo != null) repo.shutdown();
        if (speech != null) speech.destroy();
    }
}
