package com.instantaxtion.zombiesandbox;

import java.util.Random;

/**
 * The "New Game" settings. The player picks a map preset, the map size and who is in the city; everything
 * about how the preset's city is built (building style, density, layout, stations, base) comes from the preset.
 */
final class CityConfig implements OptionSet {
    static final String[] PRESETS = {"Classic", "Downtown", "Suburbs", "Industrial", "Parkland", "Old Town",
            "Small Town", "Campus", "Metropolis", "Village"};
    /** One line about each map, shown on the New Game screen. */
    static final String[] PRESET_INFO = {
            "A bit of everything: downtown towers, suburbs, parks, a precinct and an army base.",
            "Tall office towers, shopping streets, parking garages and two precincts. No base.",
            "Winding streets of houses with yards, schools, playgrounds and a few apartment blocks.",
            "Warehouses, truck yards, gas stations and a military base.",
            "Green city: big parks, gardens, sports fields and low buildings.",
            "Narrow streets packed with shops, churches, a cemetery and old houses.",
            "A quiet little town with a main street, a church, a school and plenty of gardens.",
            "Schools, sports fields, courts and student apartments.",
            "A huge, dense skyline: towers, apartments, garages, three precincts and a base.",
            "A few lanes of cottages among fields and gardens. Help is far away.",
    };

    static final int STYLE_MIXED = 0, STYLE_OFFICES = 1, STYLE_HOUSES = 2, STYLE_WAREHOUSES = 3;

    static final int OPT_PRESET = 0, OPT_SIZE = 1, OPT_CIVILIANS = 2, OPT_COPS = 3, OPT_MILITARY = 4,
            OPT_ZOMBIES = 5, OPT_RESERVES = 6;
    private static final String[] LABELS = {"Map", "Map size", "Civilians", "Cops", "Military", "Zombies",
            "Reinforcements"};
    private static final String[][] VALUES = {
            PRESETS,
            {"Small", "Medium", "Large"},
            {"0", "50", "100", "150", "250", "400", "600", "800"},
            {"0", "5", "10", "20", "40", "60"},
            {"0", "5", "10", "20", "40"},
            {"0", "1", "5", "20", "50", "100", "200"},
            {"Off", "Low", "Medium", "High"},
    };
    private static final int[] SIZES = {96, 128, 160};
    private static final int[] CIVILIANS = {0, 50, 100, 150, 250, 400, 600, 800};
    private static final int[] COPS = {0, 5, 10, 20, 40, 60};
    private static final int[] SOLDIERS = {0, 5, 10, 20, 40};
    private static final int[] ZOMBIES = {0, 1, 5, 20, 50, 100, 200};

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
            {STYLE_OFFICES, 2, 3, 1, 2, 1, 3, 1},     // Metropolis
            {STYLE_HOUSES, 0, 0, 3, 1, 2, 1, 0},      // Village
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
            {2, 2, 2, 2, 1, 0, 50},   // Metropolis
            {1, 1, 1, 1, 1, 1, 20},   // Village
    };
    // Share of office lots that become apartment blocks, parking garages and pharmacies (percent).
    private static final int[][] BUILDING_MIX = {
            {15, 6, 5}, {15, 15, 5}, {20, 0, 6}, {5, 8, 2}, {15, 3, 6}, {10, 0, 8}, {10, 2, 8}, {20, 5, 6},
            {25, 15, 4}, {0, 0, 10},
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
            {2, 1, 1, 3, 0, 2},
            {4, 2, 2, 0, 3, 0},
    };

    /** Current index into VALUES for each option. */
    final int[] v = {0, 1, 3, 2, 1, 0, 2};
    long seed = new Random().nextInt(1000000);
    /** Start the next game on the city from {@link #code()} instead of a new random one. */
    boolean keepCity;

    /**
     * A short code that rebuilds this exact city: map preset and size, then the seed, e.g. "12-483920".
     * Share it and anyone can play the same streets.
     */
    String code() {
        return CODE_MAPS.charAt(v[OPT_PRESET]) + "" + (v[OPT_SIZE] + 1) + "-" + seed;
    }

    /** The character for each map in a city code. */
    private static final String CODE_MAPS = "123456789A";

    /** Reads a city code. Returns false (changing nothing) if it isn't one. */
    boolean applyCode(String text) {
        if (text == null) return false;
        String t = text.trim().replace(" ", "");
        int dash = t.indexOf('-');
        if (dash != 2 || t.length() < 4 || t.length() > 22) return false;
        int preset = CODE_MAPS.indexOf(Character.toUpperCase(t.charAt(0))), size = t.charAt(1) - '1';
        if (preset < 0 || preset >= PRESETS.length || size < 0 || size >= SIZES.length) return false;
        long s;
        try {
            s = Long.parseLong(t.substring(3));
        } catch (NumberFormatException e) {
            return false;
        }
        if (s < 0) return false;
        v[OPT_PRESET] = preset;
        v[OPT_SIZE] = size;
        seed = s;
        keepCity = true;
        return true;
    }

    /** Picks a new random city (keeping the options). */
    void newSeed(Random r) {
        seed = r.nextInt(1000000);
    }

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

    // Which maps have a railway line (with a station and passing trains).
    private static final boolean[] RAIL = {true, true, false, true, false, true, true, false, true, false};

    boolean hasRail() { return RAIL[v[OPT_PRESET]]; }

    /** Percent of office lots that become apartments, garages and pharmacies. */
    int[] buildingMix() { return BUILDING_MIX[v[OPT_PRESET]]; }

    /** Relative weights of the park kinds for this map. */
    int[] parkMix() { return PARK_MIX[v[OPT_PRESET]]; }

    void randomize(Random r) {
        for (int i = 0; i < v.length; i++) v[i] = r.nextInt(VALUES[i].length);
        v[OPT_SIZE] = 1;
        if (v[OPT_CIVILIANS] == 0) v[OPT_CIVILIANS] = 3;
        keepCity = false;
    }

    // ------------------------------------------------------------------ OptionSet

    @Override public int count() { return LABELS.length; }
    @Override public String label(int i) { return LABELS[i]; }
    @Override public String[] values(int i) { return VALUES[i]; }
    @Override public int get(int i) { return v[i]; }
    @Override public void set(int i, int value) { v[i] = value; }
}
