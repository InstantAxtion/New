package com.instantaxtion.zombiesandbox;

import java.util.Random;

/** Everything the "New Game" screen can change: map generation settings and the starting population. */
final class CityConfig implements OptionSet {
    static final String[] PRESETS = {"Classic", "Downtown", "Suburbs", "Industrial", "Riverside", "Island",
            "Parkland", "Night City", "Custom"};
    static final int CUSTOM = PRESETS.length - 1;

    static final int STYLE_MIXED = 0, STYLE_OFFICES = 1, STYLE_HOUSES = 2, STYLE_WAREHOUSES = 3;
    static final int WATER_NONE = 0, WATER_RIVER = 1, WATER_ISLAND = 2, WATER_BOTH = 3;
    static final int TIME_DAY = 0, TIME_SUNSET = 1, TIME_NIGHT = 2;

    private static final String[] LABELS = {"Preset", "Map size", "Buildings", "Density", "Height", "Parks",
            "Water", "Traffic", "Time of day", "Civilians", "Cops", "Military", "Zombies", "Reinforcements", "Street layout", "Police stations", "Military base"};
    private static final String[][] VALUES = {
            PRESETS,
            {"Small", "Medium", "Large"},
            {"Mixed", "Offices", "Houses", "Warehouses"},
            {"Low", "Medium", "High"},
            {"Low", "Normal", "Tall", "Skyscrapers"},
            {"None", "Few", "Some", "Lots"},
            {"None", "River", "Island", "River + Island"},
            {"None", "Few", "Many"},
            {"Day", "Sunset", "Night"},
            {"0", "50", "100", "150", "250", "400"},
            {"0", "5", "10", "20", "40"},
            {"0", "5", "10", "20"},
            {"0", "1", "5", "20", "50"},
            {"Off", "On"},
            {"Grid", "Varied", "Organic"},
            {"0", "1", "2", "3"},
            {"None", "Yes"},
    };
    private static final int[] SIZES = {64, 96, 128};
    private static final int[] CIVILIANS = {0, 50, 100, 150, 250, 400};
    private static final int[] COPS = {0, 5, 10, 20, 40};
    private static final int[] SOLDIERS = {0, 5, 10, 20};
    private static final int[] ZOMBIES = {0, 1, 5, 20, 50};

    /** Current index into VALUES for each option; option 0 is the preset. */
    final int[] v = new int[LABELS.length];
    long seed = System.nanoTime();

    CityConfig() {
        applyPreset(0);
    }

    int preset() { return v[0]; }
    int tiles() { return SIZES[v[1]]; }
    int style() { return v[2]; }
    int density() { return v[3]; }
    int height() { return v[4]; }
    int parks() { return v[5]; }
    int water() { return v[6]; }
    int traffic() { return v[7]; }
    int time() { return v[8]; }
    int civilians() { return CIVILIANS[v[9]]; }
    int cops() { return COPS[v[10]]; }
    int soldiers() { return SOLDIERS[v[11]]; }
    int zombies() { return ZOMBIES[v[12]]; }
    boolean reinforcements() { return v[13] == 1; }
    int layout() { return v[14]; }
    int policeStations() { return v[15]; }
    boolean militaryBase() { return v[16] == 1; }

    boolean river() { return water() == WATER_RIVER || water() == WATER_BOTH; }
    boolean island() { return water() == WATER_ISLAND || water() == WATER_BOTH; }

    void applyPreset(int p) {
        //                size style dens height parks water traffic time civ cops mil zombies
        int[][] presets = {
                {1, 0, 1, 1, 2, 0, 1, 0, 3, 2, 0, 0}, // Classic
                {1, 1, 2, 3, 1, 0, 2, 0, 4, 3, 0, 0}, // Downtown
                {1, 2, 1, 0, 2, 0, 1, 0, 3, 1, 0, 0}, // Suburbs
                {1, 3, 1, 1, 0, 0, 2, 1, 2, 1, 0, 0}, // Industrial
                {1, 0, 1, 1, 2, 1, 1, 1, 3, 2, 0, 0}, // Riverside
                {1, 0, 1, 2, 1, 2, 1, 0, 3, 2, 0, 0}, // Island
                {1, 0, 0, 1, 3, 0, 1, 0, 3, 1, 0, 0}, // Parkland
                {1, 1, 2, 2, 1, 1, 1, 2, 4, 3, 0, 1}, // Night City
        };
        //              layout stations base
        int[][] extras = {
                {1, 1, 1}, // Classic
                {0, 2, 0}, // Downtown
                {2, 1, 0}, // Suburbs
                {1, 1, 1}, // Industrial
                {2, 1, 1}, // Riverside
                {2, 1, 1}, // Island
                {2, 1, 0}, // Parkland
                {1, 2, 1}, // Night City
        };
        v[0] = p;
        if (p >= presets.length) return;
        System.arraycopy(presets[p], 0, v, 1, presets[p].length);
        v[13] = 1;
        System.arraycopy(extras[p], 0, v, 14, 3);
    }

    void randomize(Random r) {
        for (int i = 1; i < v.length; i++) v[i] = r.nextInt(VALUES[i].length);
        v[1] = 1;
        if (v[9] == 0) v[9] = 3;
        v[0] = CUSTOM;
    }

    // ------------------------------------------------------------------ OptionSet

    @Override public int count() { return LABELS.length; }
    @Override public String label(int i) { return LABELS[i]; }
    @Override public String[] values(int i) { return VALUES[i]; }
    @Override public int get(int i) { return v[i]; }

    @Override
    public void set(int i, int value) {
        if (i == 0) {
            applyPreset(value);
        } else {
            v[i] = value;
            v[0] = CUSTOM;
        }
    }
}
