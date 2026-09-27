package com.instantaxtion.zombiesandbox;

import android.content.Context;
import android.content.SharedPreferences;

/** Player settings, saved between sessions. */
final class Settings implements OptionSet {
    private static final String[] VOLUME = {"Off", "25%", "50%", "75%", "100%"};
    private static final String[] ON_OFF = {"Off", "On"};
    private static final String[] LABELS = {"Music", "Sound effects", "3D buildings", "Blood", "Health bars",
            "Screen shake", "Show FPS", "Max population", "Radio messages"};
    private static final String[][] VALUES = {VOLUME, VOLUME, ON_OFF, ON_OFF, ON_OFF, ON_OFF, ON_OFF,
            {"800", "1600", "2500"}, ON_OFF};
    private static final String[] KEYS = {"music", "sfx", "buildings3d", "gore", "healthBars", "shake", "fps",
            "maxPop", "radio"};
    private static final int[] DEFAULTS = {2, 3, 1, 1, 1, 1, 0, 1, 1};
    private static final int[] MAX_POP = {800, 1600, 2500};

    private final SharedPreferences prefs;
    private final int[] v = DEFAULTS.clone();

    Settings(Context context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        for (int i = 0; i < v.length; i++) {
            int saved = prefs.getInt(KEYS[i], DEFAULTS[i]);
            v[i] = saved >= 0 && saved < VALUES[i].length ? saved : DEFAULTS[i];
        }
    }

    float music() { return v[0] / 4f; }
    float sfx() { return v[1] / 4f; }
    boolean buildings3d() { return v[2] == 1; }
    boolean gore() { return v[3] == 1; }
    boolean healthBars() { return v[4] == 1; }
    boolean shake() { return v[5] == 1; }
    boolean showFps() { return v[6] == 1; }
    int maxPopulation() { return MAX_POP[v[7]]; }
    boolean radio() { return v[8] == 1; }

    /** True if the player has not opened the patch notes since this version was installed. */
    boolean hasUnreadNotes() {
        return prefs.getInt("notesSeen", 0) < PatchNotes.VERSION_CODE;
    }

    void markNotesRead() {
        prefs.edit().putInt("notesSeen", PatchNotes.VERSION_CODE).apply();
    }

    @Override public int count() { return LABELS.length; }
    @Override public String label(int i) { return LABELS[i]; }
    @Override public String[] values(int i) { return VALUES[i]; }
    @Override public int get(int i) { return v[i]; }

    @Override
    public void set(int i, int value) {
        v[i] = value;
        prefs.edit().putInt(KEYS[i], value).apply();
    }
}
