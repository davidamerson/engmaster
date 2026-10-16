package com.kidsvideo.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ParentSettingsActivity extends Activity {
    private Prefs prefs;
    private final Map<String, CheckBox> defaultChecks = new LinkedHashMap<>();
    private LinearLayout customList;
    private EditText customName;
    private EditText customRef;
    private Spinner customCategory;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);

        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.isTv(this) ? 48 : (Ui.isTablet(this) ? 36 : 24);
        root.setPadding(Ui.dp(this, side), Ui.dp(this, 24), Ui.dp(this, side), Ui.dp(this, 50));
        root.setBackgroundColor(Color.rgb(15, 15, 15));
        sc.addView(root);

        TextView t = label("Parent settings", Ui.isTv(this) ? 32 : 28);
        t.setTypeface(null, 1);
        root.addView(t);

        TextView profile = label(AppConfig.isDaughter() ? "Daughter profile" : "Son profile", 16);
        profile.setTextColor(Color.LTGRAY);
        root.addView(profile);

        EditText key = input("YouTube API key", prefs.apiKey());
        root.addView(key);
        EditText pin = input("Parent PIN", prefs.parentPin());
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        root.addView(pin);
        EditText min = input("Minimum video length (minutes)", String.valueOf(prefs.minVideoMinutes()));
        min.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(min);
        EditText limit = input("Daily viewing limit in minutes (0 = off)", String.valueOf(prefs.dailyLimitMinutes()));
        limit.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(limit);

        root.addView(sectionTitle("Playback"));
        CheckBox official = new CheckBox(this);
        official.setText("Use the official YouTube app for playback on this device");
        official.setTextColor(Color.WHITE);
        official.setChecked(prefs.useOfficialYouTubePlayer());
        root.addView(official);
        TextView playbackNote = label(
                "Leave this OFF for the locked-down in-app player. If YouTube shows ‘Sign in to confirm you’re not a bot’ in the in-app player on this TV/tablet, turn this ON after signing in to the official YouTube app. This is the most reliable playback fallback, but once the official YouTube app opens it can show its own interface and recommendations.",
                14);
        playbackNote.setTextColor(Color.LTGRAY);
        root.addView(playbackNote);

        root.addView(sectionTitle("Speaking & conversation"));
        Spinner voice = new Spinner(this);
        ArrayAdapter<String> voiceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"British English (en-GB)", "American English (en-US)"});
        voice.setAdapter(voiceAdapter);
        voice.setSelection(prefs.voiceLocale().equals("en-US") ? 1 : 0);
        root.addView(voice);

        CheckBox convo = new CheckBox(this);
        convo.setText("Offer spoken-English conversation after videos");
        convo.setTextColor(Color.WHITE);
        convo.setChecked(prefs.autoConversation());
        root.addView(convo);

        root.addView(sectionTitle("Blocked topics"));
        EditText blocked = input("Blocked words (comma separated)", prefs.blockedKeywords());
        blocked.setMinLines(2);
        root.addView(blocked);
        TextView blockedNote = label(
                AppConfig.isDaughter()
                        ? "These words are removed from approved video titles before they appear in the child view."
                        : "The son profile starts with Minecraft, Roblox, Fortnite, gaming/gameplay, LEGO, Dude Perfect, MrBeast and Shorts terms blocked. You can edit or add more here.",
                14);
        blockedNote.setTextColor(Color.LTGRAY);
        root.addView(blockedNote);

        root.addView(sectionTitle("Approved built-in channels"));
        TextView channelNote = label(
                AppConfig.isDaughter()
                        ? "Untick any built-in channel you do not want in this app."
                        : "The son's app includes every built-in channel from his sister's app as well as his older-child channels. Untick any channel you want to remove.",
                14);
        channelNote.setTextColor(Color.LTGRAY);
        root.addView(channelNote);

        Set<String> disabled = prefs.disabledHandles();
        for (ChannelSpec c : AppConfig.defaultChannels()) {
            CheckBox cb = new CheckBox(this);
            cb.setText(c.name + "  •  " + c.displayReference() + "  •  " + c.category);
            cb.setTextColor(Color.WHITE);
            cb.setTextSize(Ui.isTv(this) ? 18 : 15);
            cb.setChecked(!disabled.contains(c.key()));
            cb.setFocusable(true);
            defaultChecks.put(c.key(), cb);
            root.addView(cb);
        }

        Button restore = new Button(this);
        restore.setText("Enable all built-in channels");
        restore.setAllCaps(false);
        restore.setOnClickListener(v -> {
            for (CheckBox cb : defaultChecks.values()) cb.setChecked(true);
        });
        root.addView(restore);

        root.addView(sectionTitle("Add your own approved channel"));
        TextView addNote = label(
                "Paste an @handle, a youtube.com/@handle URL, or a YouTube channel ID. Only videos from channels you add here are included in our catalogue; normal YouTube search is still not exposed inside the child app.",
                14);
        addNote.setTextColor(Color.LTGRAY);
        root.addView(addNote);

        customName = input("Channel name (for example: NASA)", "");
        root.addView(customName);
        customRef = input("@handle, channel URL, or channel ID", "");
        root.addView(customRef);

        customCategory = new Spinner(this);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                AppConfig.channelCategories());
        customCategory.setAdapter(categoryAdapter);
        root.addView(label("Category", 14));
        root.addView(customCategory);

        Button add = new Button(this);
        add.setText("+ Add approved channel");
        add.setAllCaps(false);
        add.setOnClickListener(v -> addCustomChannel());
        root.addView(add);

        customList = new LinearLayout(this);
        customList.setOrientation(LinearLayout.VERTICAL);
        root.addView(customList);
        rebuildCustomList();

        Button save = new Button(this);
        save.setText("Save settings");
        save.setAllCaps(false);
        save.setOnClickListener(v -> {
            prefs.setApiKey(key.getText().toString());
            prefs.setParentPin(pin.getText().toString());
            try { prefs.setMinVideoMinutes(Integer.parseInt(min.getText().toString())); } catch (Exception ignored) {}
            try { prefs.setDailyLimitMinutes(Integer.parseInt(limit.getText().toString())); } catch (Exception ignored) {}
            prefs.setUseOfficialYouTubePlayer(official.isChecked());
            prefs.setVoiceLocale(voice.getSelectedItemPosition() == 1 ? "en-US" : "en-GB");
            prefs.setAutoConversation(convo.isChecked());
            prefs.setBlockedKeywords(blocked.getText().toString());

            Set<String> disabledNow = new HashSet<>();
            for (Map.Entry<String, CheckBox> e : defaultChecks.entrySet()) {
                if (!e.getValue().isChecked()) disabledNow.add(e.getKey());
            }
            prefs.setDisabledHandles(disabledNow);
            prefs.clearCache();
            Toast.makeText(this, "Saved. Video list will refresh.", Toast.LENGTH_SHORT).show();
            finish();
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, -2);
        saveLp.topMargin = Ui.dp(this, 26);
        root.addView(save, saveLp);

        Button clear = new Button(this);
        clear.setText("Clear watch history and favourites");
        clear.setAllCaps(false);
        clear.setOnClickListener(v -> {
            prefs.clearHistory();
            prefs.clearFavorites();
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show();
        });
        root.addView(clear);

        setContentView(sc);
    }

    private void addCustomChannel() {
        String name = customName.getText().toString().trim();
        String ref = ChannelSpec.normalize(customRef.getText().toString());
        String category = String.valueOf(customCategory.getSelectedItem());
        if (name.isEmpty() || ref.isEmpty()) {
            Toast.makeText(this, "Enter a channel name and @handle / URL.", Toast.LENGTH_LONG).show();
            return;
        }
        ChannelSpec c = new ChannelSpec(name, ref, category);
        if (!prefs.addCustomChannel(c)) {
            Toast.makeText(this, "That channel is already in the approved list or the details are invalid.", Toast.LENGTH_LONG).show();
            return;
        }
        customName.setText("");
        customRef.setText("");
        rebuildCustomList();
        Toast.makeText(this, "Channel added", Toast.LENGTH_SHORT).show();
    }

    private void rebuildCustomList() {
        customList.removeAllViews();
        List<ChannelSpec> custom = prefs.customChannels();
        if (custom.isEmpty()) {
            TextView none = label("No extra channels added yet.", 14);
            none.setTextColor(Color.GRAY);
            customList.addView(none);
            return;
        }
        for (ChannelSpec c : custom) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));

            TextView text = label(c.name + "  •  " + c.displayReference() + "  •  " + c.category, 15);
            text.setPadding(0, 0, Ui.dp(this, 8), 0);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));

            Button remove = new Button(this);
            remove.setText("Remove");
            remove.setAllCaps(false);
            remove.setOnClickListener(v -> {
                prefs.removeCustomChannel(c.key());
                rebuildCustomList();
                Toast.makeText(this, "Channel removed", Toast.LENGTH_SHORT).show();
            });
            row.addView(remove);
            customList.addView(row);
        }
    }

    private TextView sectionTitle(String s) {
        TextView v = label(s, Ui.isTv(this) ? 23 : 20);
        v.setTypeface(null, 1);
        v.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 8));
        return v;
    }

    private TextView label(String s, int z) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(Color.WHITE);
        v.setTextSize(z);
        v.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        return v;
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.GRAY);
        e.setTextColor(Color.WHITE);
        e.setText(value);
        e.setSingleLine(false);
        e.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        return e;
    }
}
