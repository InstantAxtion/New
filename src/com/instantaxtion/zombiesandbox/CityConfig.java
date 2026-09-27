package com.instantaxtion.zombiesandbox;

import java.util.Random;

/**
 * The "New Game" settings. The player picks a map preset, the map size and who is in the city; everything
 * about how the preset's city is built (building style, density, layout, stations, base) comes from the preset.
 */
final class CityConfig implements OptionSet {
    static final String[] PRESETS = {"Classic", "Downtown", "Suburbs", "Industrial", "Parkland", "Old Town",
            "Small Town", "Campus"};

    static final int STYLE_MIXED = 0, STYLE_OFFICES = 1, STYLE_HOUSES = 2, STYLE_WAREHOUSES = 3;

    static final int OPT_PRESET = 0, OPT_SIZE = 1, OPT_CIVILIANS = 2, OPT_COPS = 3, OPT_MILITARY = 4,
            OPT_ZOMBIES = 5, OPT_RESERVES = 6;
    private static final String[] LABELS = {"Map", "Map size", "Civilians", "Cops", "Military", "Zombies",
            "Reinforcements"};
    private static final String[][] VALUES = {
            PRESETS,
            {"Small", "Medium", "Large"},
            {"0", "50", "100", "150", "250", "400"},
            {"0", "5", "10", "20", "40"},
            {"0", "5", "10", "20"},
            {"0", "1", "5", "20", "50"},
            {"Off", "Low", "Medium", "High"},
    };
    private static final int[] SIZES = {64, 96, 128};
    private static final int[] CIVILIANS = {0, 50, 100, 150, 250, 400};
    private static final int[] COPS = {0, 5, 10, 20, 40};
    private static final int[] SOLDIERS = {0, 5, 10, 20};
    private static final int[] ZOMBIES = {0, 1, 5, 20, 50};

    // How each preset's city is built:  style, density, height, parks, traffic, layout, stations, base
    private static final int[][] MAPS = {
            {STYLE_MIXED, 1, 1, 2, 1, 1, 1, 1},       // Classic
            {STYLE_OFFICES, 2, 3, 1, 2, 0, 2, 0},     // Downtown
            {STYLE_HOUSES, 1, 0, 2, 1, 2, 1, 0},      // Suburbs
            {STYLE_WAREHOUSES, 1, 1, 0, 2, 1, 1, 1},  // Industrial
            {STYLE_MIXED, 0, 1, 3, 1, 2, 1, 0},       // Parkland
            {STYLE_HOUSES, 2, 0, 1, 1, 2, 1, 0},      // Old Town
            {STYLE_HOUSES, 1, 0, 2, 1, 1, 1, 0},      // Small Town
            {STYLE_MIXED, 1, 1, 3, 1, 2, 1, 0},       // Campus
    };

    // Landmarks per medium map:  churches, schools, fire stations, supermarkets, gas stations, cemeteries,
    // then the share of building blocks that become rows of shops (percent).
    private static final int[][] LANDMARKS = {
            {1, 1, 1, 1, 1, 1, 25},   // Classic
            {1, 0, 1, 1, 0, 0, 55},   // Downtown
            {1, 1, 1, 1, 1, 1, 12},   // Suburbs
            {0, 0, 1, 0, 2, 0, 8},    // Industrial
            {1, 1, 0, 0, 0, 1, 10},   // Parkland
            {2, 1, 1, 1, 1, 1, 45},   // Old Town
            {1, 1, 1, 1, 2, 1, 35},   // Small Town
            {1, 3, 1, 1, 1, 0, 20},   // Campus
    };
    // Which kinds of park each map likes: park, playground, sports field, courts, garden, skatepark.
    private static final int[][] PARK_MIX = {
            {3, 2, 1, 1, 1, 1},
            {2, 1, 0, 2, 0, 2},
            {3, 3, 1, 1, 2, 0},
            {1, 0, 1, 1, 0, 2},
            {4, 2, 2, 1, 2, 1},
            {3, 1, 0, 0, 3, 0},
            {3, 2, 2, 1, 2, 1},
            {2, 1, 4, 4, 1, 1},
    };

    /** Current index into VALUES for each option. */
    final int[] v = {0, 1, 3, 2, 1, 0, 2};
    long seed = System.nanoTime();

    int tiles() { return SIZES[v[OPT_SIZE]]; }
    int civilians() { return CIVILIANS[v[OPT_CIVILIANS]]; }
    int cops() { return COPS[v[OPT_COPS]]; }
    int soldiers() { return SOLDIERS[v[OPT_MILITARY]]; }
    int zombies() { return ZOMBIES[v[OPT_ZOMBIES]]; }
    /** 0 off, 1 low, 2 medium, 3 high: how many reserve squads and backup waves can be called in. */
    int reinforcements() { return v[OPT_RESERVES]; }

    private int map(int i) { return MAPS[v[OPT_PRESET]][i]; }
    int style() { return map(0); }
    int density() { return map(1); }
    int height() { return map(2); }
    int parks() { return map(3); }
    int traffic() { return map(4); }
    int layout() { return map(5); }
    int policeStations() { return map(6); }
    boolean militaryBase() { return map(7) == 1; }

    /** How many of a landmark (0 church, 1 school, 2 fire station, 3 supermarket, 4 gas station, 5 cemetery). */
    int landmarks(int kind) {
        int n = LANDMARKS[v[OPT_PRESET]][kind];
        if (v[OPT_SIZE] == 0) return n > 1 ? 1 : n;
        if (v[OPT_SIZE] == 2) return n + (n + 1) / 2;
        return n;
    }

    int shopShare() { return LANDMARKS[v[OPT_PRESET]][6]; }

    /** Relative weights of the park kinds for this map. */
    int[] parkMix() { return PARK_MIX[v[OPT_PRESET]]; }

    void randomize(Random r) {
        for (int i = 0; i < v.length; i++) v[i] = r.nextInt(VALUES[i].length);
        v[OPT_SIZE] = 1;
        if (v[OPT_CIVILIANS] == 0) v[OPT_CIVILIANS] = 3;
    }

    // ------------------------------------------------------------------ OptionSet

    @Override public int count() { return LABELS.length; }
    @Override public String label(int i) { return LABELS[i]; }
    @Override public String[] values(int i) { return VALUES[i]; }
    @Override public int get(int i) { return v[i]; }
    @Override public void set(int i, int value) { v[i] = value; }
}
