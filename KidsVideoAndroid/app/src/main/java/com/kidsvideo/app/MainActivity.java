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
    private static final int BATCH_SIZE = 24;

    private Prefs prefs;
    private YouTubeRepository repo;
    private SpeechHelper speech;
    private LinearLayout list;
    private LinearLayout cats;
    private EditText search;
    private TextView status;
    private ScrollView sc;
    private List<VideoItem> videos = new ArrayList<>();
    private List<VideoItem> filtered = new ArrayList<>();
    private String category = "Home";
    private boolean loading = false;
    private boolean appending = false;
    private int renderedCount = 0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        // Keep the existing cache visible immediately, but force one background
        // refresh so v1.9 can expand it into the smarter 70-per-channel library.
        prefs.migrateCatalogueRevision("v19-smart-mix");
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
            // Re-render immediately after a video is watched so watched items sink
            // below unseen recommendations without waiting for the next API refresh.
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
            top.addView(makeLogo(), new LinearLayout.LayoutParams(0, -2, 1));
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

        sc = new ScrollView(this);
        sc.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.isTv(this) ? 28 : 12;
        list.setPadding(Ui.dp(this, side), 0, Ui.dp(this, side), Ui.dp(this, 30));
        sc.addView(list);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        sc.setOnScrollChangeListener((v, sx, sy, ox, oy) -> {
            View child = sc.getChildAt(0);
            if (child == null || appending) return;
            int remaining = child.getMeasuredHeight() - (sc.getHeight() + sy);
            if (remaining < Ui.dp(this, 900)) appendNextBatch();
        });

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
        status.setText(cache.isEmpty()
                ? "Loading approved channels…"
                : "Refreshing new recommendations in background…");
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

    private boolean matchesSection(VideoItem v, Set<String> fav, List<String> hist) {
        return category.equals("Home")
                || category.equals(v.category)
                || (category.equals("Favourites") && fav.contains(v.id))
                || (category.equals("Watched") && hist.contains(v.id));
    }

    private void renderStatusForCurrentCategory() {
        if (status == null) return;
        status.setText("Selected: " + category + "   •   " + filtered.size() + " approved long videos");
    }

    private void render() {
        if (list == null || search == null) return;
        list.removeAllViews();
        filtered.clear();
        renderedCount = 0;
        if (sc != null) sc.scrollTo(0, 0);

        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        Set<String> fav = prefs.favorites();
        List<String> hist = prefs.history();

        for (VideoItem v : videos) {
            if (prefs.isPlaybackRejected(v.id)) continue;
            if (!matchesSection(v, fav, hist)) continue;
            if (!q.isEmpty() && !(v.title.toLowerCase(Locale.ROOT).contains(q)
                    || v.channelTitle.toLowerCase(Locale.ROOT).contains(q)
                    || v.category.toLowerCase(Locale.ROOT).contains(q))) continue;
            filtered.add(v);
        }

        // For Home and every normal category, unseen videos always come before
        // already-watched ones. This updates immediately when returning from playback.
        if (!category.equals("Watched") && !filtered.isEmpty() && !hist.isEmpty()) {
            List<VideoItem> unseen = new ArrayList<>();
            List<VideoItem> seen = new ArrayList<>();
            for (VideoItem v : filtered) {
                if (hist.contains(v.id)) seen.add(v); else unseen.add(v);
            }
            filtered.clear();
            filtered.addAll(unseen);
            filtered.addAll(seen);
        }

        if (filtered.isEmpty()) {
            TextView e = new TextView(this);
            e.setText(q.isEmpty()
                    ? "No playable approved videos in this section yet."
                    : "No approved videos match ‘" + q + "’. " + AppConfig.searchSuggestion());
            e.setTextColor(Color.LTGRAY);
            e.setTextSize(Ui.isTv(this) ? 21 : 18);
            e.setPadding(Ui.dp(this, 22), Ui.dp(this, 30), Ui.dp(this, 22), Ui.dp(this, 30));
            list.addView(e);
        } else {
            appendNextBatch();
        }
        updateStatus();
    }

    private void appendNextBatch() {
        if (appending || renderedCount >= filtered.size()) return;
        appending = true;
        int end = Math.min(filtered.size(), renderedCount + BATCH_SIZE);
        for (int i = renderedCount; i < end; i++) list.addView(card(filtered.get(i)));
        renderedCount = end;
        appending = false;
        updateStatus();
    }

    private void updateStatus() {
        if (status == null || loading) return;
        status.setText("Selected: " + category + "   •   " + renderedCount + "/" + filtered.size()
                + " shown   •   unseen first");
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

        Button favButton = new Button(this);
        favButton.setText(prefs.isFavorite(v.id) ? "★ Favourite" : "☆ Favourite");
        favButton.setAllCaps(false);
        favButton.setOnClickListener(x -> {
            prefs.toggleFavorite(v.id);
            favButton.setText(prefs.isFavorite(v.id) ? "★ Favourite" : "☆ Favourite");
            if (category.equals("Favourites")) render();
        });
        meta.addView(favButton, new LinearLayout.LayoutParams(-2, -2));

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
        i.putExtra("channelTitle", v.channelTitle);
        startActivity(i);
    }

    private void voiceSearch() {
        status.setText("Listening…");
        speech.listen(prefs.voiceLocale(), new SpeechHelper.Callback() {
            public void onResult(String t) {
                runOnUiThread(() -> {
                    search.setText(t);
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
