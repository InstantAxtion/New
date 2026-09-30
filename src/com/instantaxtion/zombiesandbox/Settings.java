package com.instantaxtion.zombiesandbox;

import android.content.Context;
import android.content.SharedPreferences;

/** Player settings, saved between sessions. */
final class Settings implements OptionSet {
    private static final String[] VOLUME = {"Off", "25%", "50%", "75%", "100%"};
    private static final String[] ON_OFF = {"Off", "On"};
    private static final String[] LABELS = {"Music", "Sound effects", "View", "Blood", "Health bars",
            "Screen shake", "Show FPS", "Max population", "Radio messages", "Minimap",
            "Text & buttons", "Battery saver", "Name tags", "Graphics"};
    private static final String[][] VALUES = {VOLUME, VOLUME, {"Bird's-eye", "3D"}, ON_OFF, ON_OFF, ON_OFF, ON_OFF,
            {"Auto", "2500", "5000", "8000", "12000"}, ON_OFF, ON_OFF, {"Small", "Normal", "Large"}, ON_OFF, ON_OFF,
            {"Classic", "Realistic"}};
    private static final String[] KEYS = {"music", "sfx", "buildings3d", "gore", "healthBars", "shake", "fps",
            "maxPop2", "radio", "minimap", "uiSize", "battery", "nameTags", "graphics"};
    private static final int[] DEFAULTS = {2, 3, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1};
    /** 0 is Auto: room for everyone who lives in the city, and plenty of zombies. */
    private static final int[] MAX_POP = {0, 2500, 5000, 8000, 12000};

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
    /** 3D (GTA 2 style, buildings lean) or bird's-eye (flat, straight down). */
    boolean buildings3d() { return v[2] == 1; }
    void setBuildings3d(boolean on) { set(2, on ? 1 : 0); }
    boolean gore() { return v[3] == 1; }
    boolean healthBars() { return v[4] == 1; }
    boolean shake() { return v[5] == 1; }
    boolean showFps() { return v[6] == 1; }
    int maxPopulation() { return MAX_POP[v[7]]; }
    boolean radio() { return v[8] == 1; }
    boolean minimap() { return v[9] == 1; }
    /** How big the buttons and text are compared to normal. */
    float uiScale() { return v[10] == 0 ? 0.85f : v[10] == 2 ? 1.2f : 1f; }
    /** Battery saver: 30 frames a second, and fewer still while paused. */
    boolean batterySaver() { return v[11] == 1; }
    /** Names above people when zoomed right in. */
    boolean nameTags() { return v[12] == 1; }
    /** Realistic graphics (textured ground, soft shadows, shading) or the classic flat look. */
    boolean realistic() { return v[13] == 1; }

    /** True if the player has not opened the patch notes since this version was installed. */
    boolean hasUnreadNotes() {
        return prefs.getInt("notesSeen", 0) < PatchNotes.VERSION_CODE;
    }

    boolean tutorialDone() {
        return prefs.getInt("tutorialDone", 0) == 1;
    }

    void markTutorialDone() {
        prefs.edit().putInt("tutorialDone", 1).apply();
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
