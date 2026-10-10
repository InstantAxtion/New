package com.instantaxtion.zombiesandbox;

import java.util.Random;

/**
 * The "New Game" settings. The player picks a map preset, the map size and who is in the city; everything
 * about how the preset's city is built (building style, density, layout, stations, base) comes from the preset.
 */
final class CityConfig implements OptionSet {
    static final String[] PRESETS = {"Classic", "Downtown", "Suburbs", "Industrial", "Parkland", "Old Town",
            "Small Town", "Campus", "Metropolis", "Village", "Seaside", "River City", "Lakeside", "Harbour", "Islands",
            // (Since 10.18: real cities, premade, always the biggest size. See RealCities.)
            "Los Angeles", "Portland", "Seattle", "New York", "Sydney", "Tokyo", "Paris"};
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
            "A beach town on the sea: the promenade, sand, piers and boats, with hotels and cafés along the front.",
            "A river runs through the middle of town. Hold the bridges: the dead can't swim.",
            "Homes and parks round a lake in the middle of town, with a jetty, rowing boats and an island.",
            "A working port: quays, docks, cranes, container stacks and ships, with warehouses along the water.",
            "Three island towns in the sea, joined by bridges, with a causeway to the mainland. Always one size: between Large and Massive.",
            RealCities.INFO[0], RealCities.INFO[1], RealCities.INFO[2], RealCities.INFO[3], RealCities.INFO[4],
            RealCities.INFO[5], RealCities.INFO[6],
    };

    static final int STYLE_MIXED = 0, STYLE_OFFICES = 1, STYLE_HOUSES = 2, STYLE_WAREHOUSES = 3;

    static final int OPT_PRESET = 0, OPT_SIZE = 1, OPT_CIVILIANS = 2, OPT_COPS = 3, OPT_MILITARY = 4,
            OPT_ZOMBIES = 5, OPT_RESERVES = 6, OPT_COUNTRY = 7;
    /** The country round the town (10.20): not one of the eight in v (it's kept in outskirts). */
    static final int OPT_OUTSKIRTS = 8;
    private static final String[] OUTSKIRTS = {"Default", "Little nature"};
    private static final String[] LABELS = {"Map", "Map size", "Civilians", "Cops", "Military", "Zombies",
            "Reinforcements", "Country"};
    private static final String[][] VALUES = {
            PRESETS,
            // (Since 10.18 the three sizes offered are called Default, Large and Massive.)
            {"Tiny", "Small", "Medium", "Default", "Large", "Massive"},
            {"None", "Few", "Some", "Most", "All"},
            {"0", "5", "10", "20", "40", "60"},
            {"0", "5", "10", "20", "40"},
            {"0", "1", "5", "20", "50", "100", "200"},
            // (Since 10.20 help from outside is just on or off: the old Low and Medium count as On.)
            {"Off", "On (Low)", "On (Medium)", "On"},
            Country.NAMES,
    };
    /** Tiles across for each size; cities built before 10.10 (no Tiny or Huge then) were a quarter smaller. */
    private static final int[] SIZES = {160, 240, 320, 400, 560, 704};
    private static final int[] OLD_SIZES = {192, 192, 256, 320, 448, 448};
    /** The size settings, by name. */
    static final int TINY = 0, SMALL = 1, MEDIUM = 2, LARGE = 3, MASSIVE = 4, HUGE = 5;
    /** Share of the city's residents out and about when the game starts. */
    private static final float[] RESIDENT_SHARE = {0, 0.2f, 0.45f, 0.7f, 1f};
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
            {STYLE_MIXED, 1, 1, 2, 1, 1, 1, 0},       // Seaside
            {STYLE_MIXED, 1, 2, 2, 2, 0, 2, 1},       // River City
            {STYLE_HOUSES, 1, 0, 3, 1, 2, 1, 0},      // Lakeside
            {STYLE_MIXED, 1, 1, 1, 2, 0, 1, 1},       // Harbour
            {STYLE_MIXED, 1, 1, 2, 1, 1, 2, 1},       // Islands
            {STYLE_MIXED, 2, 3, 1, 2, 0, 3, 1},       // Los Angeles
            {STYLE_MIXED, 1, 2, 2, 2, 0, 3, 1},       // Portland
            {STYLE_MIXED, 2, 3, 1, 2, 0, 3, 1},       // Seattle
            {STYLE_OFFICES, 2, 3, 1, 2, 0, 3, 1},     // New York
            {STYLE_MIXED, 2, 2, 2, 2, 0, 3, 1},       // Sydney
            {STYLE_OFFICES, 2, 3, 1, 2, 0, 3, 1},     // Tokyo
            {STYLE_MIXED, 2, 1, 2, 2, 0, 3, 1},       // Paris
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
            {1, 1, 1, 1, 1, 0, 40},   // Seaside
            {1, 1, 1, 1, 1, 1, 30},   // River City
            {1, 1, 1, 1, 1, 1, 15},   // Lakeside
            {1, 1, 1, 1, 2, 0, 20},   // Harbour
            {2, 2, 2, 2, 2, 1, 30},   // Islands
            {2, 2, 2, 2, 2, 1, 40}, {2, 2, 2, 2, 1, 1, 40}, {2, 2, 2, 2, 1, 1, 40}, {2, 2, 2, 2, 1, 1, 55},
            {2, 2, 2, 2, 1, 1, 40}, {2, 2, 2, 2, 1, 1, 50}, {3, 2, 2, 2, 1, 1, 55},
    };
    // Share of office lots that become apartment blocks, parking garages and pharmacies (percent).
    private static final int[][] BUILDING_MIX = {
            {15, 6, 5}, {15, 15, 5}, {20, 0, 6}, {5, 8, 2}, {15, 3, 6}, {10, 0, 8}, {10, 2, 8}, {20, 5, 6},
            {25, 15, 4}, {0, 0, 10}, {25, 3, 6}, {20, 8, 5}, {15, 2, 6}, {10, 6, 3}, {20, 8, 5},
            {20, 12, 5}, {20, 6, 5}, {25, 10, 5}, {35, 12, 4}, {25, 8, 5}, {35, 12, 5}, {35, 4, 6},
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
            {3, 2, 1, 2, 2, 1},
            {3, 1, 1, 2, 2, 1},
            {4, 2, 1, 1, 3, 0},
            {2, 1, 1, 1, 0, 2},
            {3, 2, 1, 2, 2, 1},
            {3, 2, 1, 2, 1, 2}, {4, 2, 1, 1, 2, 1}, {3, 2, 1, 2, 2, 1}, {3, 2, 1, 3, 1, 1}, {3, 2, 2, 1, 2, 1},
            {2, 1, 1, 1, 3, 0}, {3, 2, 0, 1, 4, 0},
    };

    /** Current index into VALUES for each option. */
    final int[] v = {METROPOLIS, LARGE, 4, 2, 1, 0, 3, 0};
    /** The map a new game starts on (since Classic went in 10.18). */
    static final int METROPOLIS = 8;

    /**
     * Choices no longer offered on the New Game screen (10.17): five of the maps (Industrial, Parkland, Old
     * Town, Small Town and Harbour), the three smallest map sizes and seven of the countries. Cities from older
     * saves and shared codes that use them still build as they were.
     */
    static boolean retired(int option, int value) {
        if (option == OPT_PRESET) return value == 0 || value == 3 || value == 4 || value == 5 || value == 6 || value == 7 || value == 13;
        // (The number of zombies at the start isn't a choice any more: the game starts quiet.)
        if (option == OPT_ZOMBIES) return value != 0;
        if (option == OPT_SIZE) return value < LARGE;
        if (option == OPT_COUNTRY) return Country.retired(value);
        if (option == OPT_RESERVES) return value == 1 || value == 2;
        return false;
    }
    long seed = new Random().nextInt(1000000);
    /** Start the next game on the city from {@link #code()} instead of a new random one. */
    boolean keepCity;
    /**
     * The country round the town (10.20): 0 as it comes (woods, hills, mountains), 1 little nature (open fields
     * and farmland, few trees, no mountains). Part of the city code ("!" at the end).
     */
    int outskirts;

    boolean littleNature() { return outskirts == 1 && civic >= 7; }
    /**
     * Which public buildings the city was laid out with: 0 as before 10.4, 1 the government quarter (10.4),
     * 2 and the National Guard armory (10.5), 3 the bigger maps of 10.10 with hills, mountains, lakes, streams
     * and nature parks out in the country, 4 the wilds of 10.14 (streams you can wade, trails that cross the
     * lanes, a ranger station and fire lookout towers), 5 the lanes of 10.18 (no stretch of tarmac in the
     * middle of a dirt lane) and the real cities, 6 the driveways of 10.19, 7 the helipads and busier outskirts of 10.20, 8 dirt lanes that stay dirt into town (10.23), 9 streets that line up, more kinds of road and hills in town (10.24). Saved games rebuild with what they had.
     */
    int civic = 9;

    /** Hills, mountains, streams, lakes, parks and campsites (cities built since 10.10). */
    boolean nature() { return civic >= 3; }

    /** Fords, trails that cross the lanes and the railway, the ranger station and fire towers (since 10.14). */
    boolean wilds() { return civic >= 4; }

    /** Country lanes that stay dirt all the way (since 10.18). */
    boolean dirtLanes() { return civic >= 5; }

    /** Driveways out to the road from houses and farms that open onto nothing but grass (since 10.19). */
    boolean driveways() { return civic >= 6; }

    /** Helipads for the police and rescue helicopters, livelier outskirts with their own small towns (since 10.20). */
    boolean servicePads() { return civic >= 7; }

    int preset() {
        return v[OPT_PRESET];
    }

    /** Water on the map: none, the sea along one side (a beach or a port), a river, or a lake. */
    static final int W_NONE = 0, W_SEA = 1, W_RIVER = 2, W_LAKE = 3, W_HARBOUR = 4, W_ISLANDS = 5, W_REAL = 6;
    /** The Islands map, and its one size (tiles across: a bit bigger than Massive). */
    static final int ISLANDS = 14, ISLANDS_TILES = 640;

    /** Islands only comes in one size: it counts as Massive for everything that depends on size. */
    void normalize() {
        if (v[OPT_PRESET] == ISLANDS) v[OPT_SIZE] = MASSIVE;
        // A real city is always the same city: the biggest size, in its own country.
        RealCities.Spec real = RealCities.get(v[OPT_PRESET]);
        if (real != null) {
            v[OPT_SIZE] = HUGE;
            v[OPT_COUNTRY] = real.country;
            seed = real.seed;
            edits.clear();
        }
    }

    /** The real city this is (null for a generated one). */
    RealCities.Spec real() {
        return civic >= 5 ? RealCities.get(v[OPT_PRESET]) : null;
    }

    int water() {
        if (RealCities.isReal(v[OPT_PRESET])) return W_REAL;
        switch (v[OPT_PRESET]) {
            case 10: return W_SEA;
            case 11: return W_RIVER;
            case 12: return W_LAKE;
            case 13: return W_HARBOUR;
            case ISLANDS: return W_ISLANDS;
            default: return W_NONE;
        }
    }

    /**
     * A short code that rebuilds this exact city: map preset and size, then the seed, e.g. "12-483920".
     * Share it and anyone can play the same streets.
     */
    String code() {
        return CODE_MAPS.charAt(v[OPT_PRESET]) + "" + v[OPT_SIZE] + "-" + seed
                + (v[OPT_COUNTRY] == 0 ? "" : "@" + v[OPT_COUNTRY]) + (outskirts == 1 ? "!" : "") + (edits.isEmpty() ? "" : "~" + editString());
    }

    /** Changes made with the Build tool: {tile x, tile y, what}. They're part of the city code. */
    final java.util.ArrayList<int[]> edits = new java.util.ArrayList<int[]>();
    private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    /** The edits packed into text: three bytes each, base64 (URL-safe). */
    String editString() {
        StringBuilder sb = new StringBuilder();
        for (int[] e : edits) {
            int bits = (e[0] & 0xFF) << 16 | (e[1] & 0xFF) << 8 | (e[2] & 0xFF);
            for (int k = 3; k >= 0; k--) sb.append(B64.charAt((bits >> (k * 6)) & 63));
        }
        return sb.toString();
    }

    void setEdits(String text) {
        edits.clear();
        if (text == null) return;
        for (int i = 0; i + 4 <= text.length(); i += 4) {
            int bits = 0;
            for (int k = 0; k < 4; k++) {
                int d = B64.indexOf(text.charAt(i + k));
                if (d < 0) return;
                bits = bits << 6 | d;
            }
            edits.add(new int[]{bits >> 16 & 0xFF, bits >> 8 & 0xFF, bits & 0xFF});
        }
    }

    /** The character for each map in a city code. */
    private static final String CODE_MAPS = "123456789ABCDEFGHIJKLM";

    /** Reads a city code. Returns false (changing nothing) if it isn't one. */
    boolean applyCode(String text) {
        if (text == null) return false;
        String t = text.trim().replace(" ", "");
        String ed = null;
        int tilde = t.indexOf('~');
        if (tilde > 0) {
            ed = t.substring(tilde + 1);
            t = t.substring(0, tilde);
        }
        // Little nature out of town: a "!" (since 10.20).
        int out = 0;
        if (t.endsWith("!")) {
            out = 1;
            t = t.substring(0, t.length() - 1);
        }
        // The country, after an "@" (codes without one are American cities).
        int country = 0;
        int at = t.indexOf('@');
        if (at > 0) {
            String num = t.substring(at + 1);
            if (num.isEmpty() || num.length() > 2) return false;
            country = 0;
            for (int k = 0; k < num.length(); k++) {
                char ch = num.charAt(k);
                if (ch < '0' || ch > '9') return false;
                country = country * 10 + (ch - '0');
            }
            if (country < 0 || country >= Country.NAMES.length) return false;
            t = t.substring(0, at);
        }
        int dash = t.indexOf('-');
        if (dash != 2 || t.length() < 4 || t.length() > 22) return false;
        // (The size digit: 0 Tiny, 1 Small, 2 Medium, 3 Large, 4 Massive, 5 Huge. Codes from before Tiny was
        // added used 1 to 4 for Small to Massive, so they still mean the same size.)
        int preset = CODE_MAPS.indexOf(Character.toUpperCase(t.charAt(0))), size = t.charAt(1) - '0';
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
        v[OPT_COUNTRY] = country;
        outskirts = out;
        normalize();
        setEdits(ed);
        keepCity = true;
        return true;
    }

    /** Picks a new random city (keeping the options). */
    void newSeed(Random r) {
        edits.clear();
        seed = r.nextInt(1000000);
        // (A real city stays the same.)
        normalize();
    }

    int tiles() {
        if (v[OPT_PRESET] == ISLANDS) return nature() ? ISLANDS_TILES : 512;
        return (nature() ? SIZES : OLD_SIZES)[v[OPT_SIZE]];
    }

    /** A massive (or huge) map: the city sits in the middle of open countryside. */
    boolean massive() { return v[OPT_SIZE] >= MASSIVE; }

    int size() { return v[OPT_SIZE]; }

    /**
     * How much of the map (across) is town; the rest is countryside with dirt roads, farms and woods. A village
     * is a small cluster in the fields, a small town has fields round the edge, and massive maps are mostly
     * country around a city.
     */
    float coreFraction() {
        int p = v[OPT_PRESET];
        // (Towns on the water fill the whole map.)
        if (water() != W_NONE) return 1f;
        float f = p == 9 ? 0.55f : p == 6 ? 0.72f : 1f;
        // A massive map is nearly all town: a thin band of country round the edge for the highway and farms.
        if (massive()) f = p == 9 ? 0.6f : p == 6 ? 0.88f : 0.92f;
        if (!nature()) return f;
        // Since 10.10 the maps are a quarter bigger, and the extra room is countryside: hills and woods,
        // mountains, lakes and streams, a nature park and campsites round a town a little bigger than before.
        if (p == 9) return v[OPT_SIZE] <= SMALL ? 0.6f : 0.5f;
        float town = new float[]{1f, 0.86f, 0.8f, 0.76f, 0.74f, 0.66f}[v[OPT_SIZE]];
        return p == 6 ? town * 0.8f : town;
    }
    /** How many of a city's residents (everyone its homes can house) are in the game. */
    int civilians(int residents) {
        return Math.round(residents * RESIDENT_SHARE[v[OPT_CIVILIANS]]);
    }
    /**
     * Police on duty when the game starts: about one officer for every eighty residents (more than a real city
     * has on shift, so the war is a fair fight), a couple per station, at least four.
     */
    int cops(int residents) {
        // About one officer for every 45 residents the game simulates, plus a few for each station.
        return Math.max(8, Math.min(220, Math.round(residents / 32f) + policeStations() * 5));
    }

    /** Soldiers at the base (if the map has one): a garrison that grows with the city. */
    int soldiers(int residents) {
        if (!militaryBase()) return 0;
        return Math.max(12, Math.min(72, Math.round(residents / 75f) + 6));
    }

    /** What each Reinforcements setting means, in plain words. */
    static final String[] RESERVE_INFO = {
            "Off: nobody else comes. Only the police, soldiers and helicopters already in the city fight.",
            "On: help comes from outside the city.",
            "On: help comes from outside the city.",
            "On: police come first, within a minute of being asked: some 220 officers in waves from the towns around "
                    + "(their police, highway patrol, SWAT teams, sheriff's deputies and federal agents). The army is the "
                    + "slowest to come, several minutes, but then arrives all at once: some 120 soldiers in one big convoy "
                    + "with armour, the helicopters on the map flying cover."};
    int zombies() { return ZOMBIES[v[OPT_ZOMBIES]]; }
    /** Which country the city is in (see {@link Country}). */
    int country() { return v[OPT_COUNTRY]; }
    /** 0 off, 1 low, 2 medium, 3 high: how many reserve squads and backup waves can be called in. */
    int reinforcements() { return v[OPT_RESERVES]; }

    private int map(int i) { return MAPS[v[OPT_PRESET]][i]; }
    int style() { return map(0); }
    int density() { return map(1); }
    int height() { return map(2); }
    int parks() { return map(3); }
    int traffic() { return map(4); }
    int layout() { return map(5); }
    /** Police stations: the map's own number, and more on Large and Massive maps. */
    int policeStations() {
        int n = map(6);
        int s = v[OPT_SIZE];
        return n == 0 ? 0 : Math.max(1, n - (s == TINY ? 1 : 0)) + (s >= LARGE ? 1 : 0) + (s >= MASSIVE ? 1 : 0) + (s >= HUGE ? 1 : 0);
    }

    /** Medics: the hospital's, and the paramedics at the fire stations, growing with the city. */
    int medics(int residents) {
        return Math.max(4, Math.min(36, Math.round(residents / 110f)));
    }
    boolean militaryBase() { return map(7) == 1; }

    /** How many of a landmark (0 church, 1 school, 2 fire station, 3 supermarket, 4 gas station, 5 cemetery). */
    int landmarks(int kind) {
        int n = LANDMARKS[v[OPT_PRESET]][kind];
        // Fire stations: one more on a Large map, two more on a Massive one.
        // (Every town has at least one.)
        int s = v[OPT_SIZE];
        if (kind == 2) {
            n = Math.max(1, n);
            if (s >= LARGE) return n + s - MEDIUM;
        }
        if (s == TINY) return Math.min(n, kind == 2 || kind == 3 ? 1 : 0);
        if (s == SMALL) return n > 1 ? 1 : n;
        if (s == LARGE) return n + (n + 1) / 2;
        if (s == HUGE) return n * 2;
        return n;
    }

    int shopShare() { return LANDMARKS[v[OPT_PRESET]][6]; }

    // Which maps have a railway line (with a station and passing trains).
    // Which maps have a railway line: 0 never, 1 about half the time (it depends on the city), 2 always.
    private static final int[] RAIL = {1, 0, 0, 2, 0, 0, 1, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    boolean hasRail() {
        int r = RAIL[v[OPT_PRESET]];
        return r == 2 || (r == 1 && Math.abs(seed % 2) == 0);
    }

    // Districts:  downtown, midtown, old town, suburb, industrial, campus, parkside (relative weights), then the
    // kind of district at the heart of town.
    private static final int[][] DISTRICT_MIX = {
            {3, 3, 2, 4, 2, 1, 2, 0},   // Classic
            {6, 5, 2, 0, 1, 0, 1, 0},   // Downtown
            {0, 1, 1, 8, 0, 1, 2, 1},   // Suburbs
            {1, 1, 0, 2, 7, 0, 0, 4},   // Industrial
            {1, 2, 1, 3, 0, 1, 6, 6},   // Parkland
            {0, 2, 7, 2, 0, 0, 1, 2},   // Old Town
            {0, 1, 3, 6, 0, 0, 2, 2},   // Small Town
            {0, 2, 1, 3, 0, 6, 2, 5},   // Campus
            {6, 5, 1, 1, 3, 0, 1, 0},   // Metropolis
            {0, 0, 3, 6, 0, 0, 3, 2},   // Village
            {2, 4, 2, 4, 0, 0, 3, 1},   // Seaside
            {4, 4, 2, 3, 2, 1, 1, 0},   // River City
            {1, 2, 1, 6, 0, 1, 4, 6},   // Lakeside
            {2, 3, 1, 2, 5, 0, 0, 1},   // Harbour
            {3, 4, 2, 4, 1, 1, 2, 0},   // Islands
            {3, 3, 2, 3, 1, 1, 1, 0}, {3, 3, 2, 3, 1, 1, 1, 0}, {3, 3, 2, 3, 1, 1, 1, 0}, {3, 3, 2, 3, 1, 1, 1, 0},
            {3, 3, 2, 3, 1, 1, 1, 0}, {3, 3, 2, 3, 1, 1, 1, 0}, {3, 3, 2, 3, 1, 1, 1, 0},
    };

    /** How likely each kind of district is on this map. */
    int districtWeight(int type) { return DISTRICT_MIX[v[OPT_PRESET]][type]; }

    /** The kind of district in the middle of town. */
    int coreDistrict() { return DISTRICT_MIX[v[OPT_PRESET]][7]; }

    /** Percent of office lots that become apartments, garages and pharmacies. */
    int[] buildingMix() { return BUILDING_MIX[v[OPT_PRESET]]; }

    /** Relative weights of the park kinds for this map. */
    int[] parkMix() { return PARK_MIX[v[OPT_PRESET]]; }

    void randomize(Random r) {
        for (int i = 0; i < v.length; i++) {
            // (A random city is a made-up one: not one of the real cities.)
            do v[i] = r.nextInt(VALUES[i].length);
            while (retired(i, v[i]) || (i == OPT_PRESET && RealCities.isReal(v[i])));
        }
        v[OPT_SIZE] = LARGE;
        normalize();
        if (v[OPT_CIVILIANS] == 0) v[OPT_CIVILIANS] = 4;
        keepCity = false;
    }

    // ------------------------------------------------------------------ OptionSet

    @Override public int count() { return LABELS.length; }
    @Override public String label(int i) { return LABELS[i]; }
    @Override public String[] values(int i) { return i == OPT_OUTSKIRTS ? OUTSKIRTS : VALUES[i]; }
    @Override public int get(int i) { return i == OPT_OUTSKIRTS ? outskirts : v[i]; }
    @Override public void set(int i, int value) {
        if (i == OPT_OUTSKIRTS) {
            outskirts = value;
            return;
        }
        v[i] = value;
        normalize();
    }
}
