package com.instantaxtion.zombiesandbox;

import java.util.Random;

/**
 * The "New Game" settings. The player picks a map preset, the map size and who is in the city; everything
 * about how the preset's city is built (building style, density, layout, stations, base) comes from the preset.
 */
final class CityConfig implements OptionSet {
    static final String[] PRESETS = {"Classic", "Downtown", "Suburbs", "Industrial", "Parkland", "Old Town",
            "Small Town", "Campus", "Metropolis", "Village", "Seaside", "River City", "Lakeside", "Harbour", "Islands"};
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
            "Three island towns in the sea, joined by bridges, with a causeway to the mainland. Always one size: bigger than Massive.",
    };

    static final int STYLE_MIXED = 0, STYLE_OFFICES = 1, STYLE_HOUSES = 2, STYLE_WAREHOUSES = 3;

    static final int OPT_PRESET = 0, OPT_SIZE = 1, OPT_CIVILIANS = 2, OPT_COPS = 3, OPT_MILITARY = 4,
            OPT_ZOMBIES = 5, OPT_RESERVES = 6, OPT_COUNTRY = 7;
    private static final String[] LABELS = {"Map", "Map size", "Civilians", "Cops", "Military", "Zombies",
            "Reinforcements", "Country"};
    private static final String[][] VALUES = {
            PRESETS,
            {"Tiny", "Small", "Medium", "Large", "Massive", "Huge"},
            {"None", "Few", "Some", "Most", "All"},
            {"0", "5", "10", "20", "40", "60"},
            {"0", "5", "10", "20", "40"},
            {"0", "1", "5", "20", "50", "100", "200"},
            {"Off", "Low", "Medium", "High"},
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
    };
    // Share of office lots that become apartment blocks, parking garages and pharmacies (percent).
    private static final int[][] BUILDING_MIX = {
            {15, 6, 5}, {15, 15, 5}, {20, 0, 6}, {5, 8, 2}, {15, 3, 6}, {10, 0, 8}, {10, 2, 8}, {20, 5, 6},
            {25, 15, 4}, {0, 0, 10}, {25, 3, 6}, {20, 8, 5}, {15, 2, 6}, {10, 6, 3}, {20, 8, 5},
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
    };

    /** Current index into VALUES for each option. */
    final int[] v = {0, MEDIUM, 4, 2, 1, 0, 2, 0};
    long seed = new Random().nextInt(1000000);
    /** Start the next game on the city from {@link #code()} instead of a new random one. */
    boolean keepCity;
    /**
     * Which public buildings the city was laid out with: 0 as before 10.4, 1 the government quarter (10.4),
     * 2 and the National Guard armory (10.5), 3 the bigger maps of 10.10 with hills, mountains, lakes, streams
     * and nature parks out in the country, 4 the wilds of 10.14 (streams you can wade, trails that cross the
     * lanes, a ranger station and fire lookout towers). Saved games rebuild with what they had.
     */
    int civic = 4;

    /** Hills, mountains, streams, lakes, parks and campsites (cities built since 10.10). */
    boolean nature() { return civic >= 3; }

    /** Fords, trails that cross the lanes and the railway, the ranger station and fire towers (since 10.14). */
    boolean wilds() { return civic >= 4; }

    int preset() {
        return v[OPT_PRESET];
    }

    /** Water on the map: none, the sea along one side (a beach or a port), a river, or a lake. */
    static final int W_NONE = 0, W_SEA = 1, W_RIVER = 2, W_LAKE = 3, W_HARBOUR = 4, W_ISLANDS = 5;
    /** The Islands map, and its one size (tiles across: a bit bigger than Massive). */
    static final int ISLANDS = 14, ISLANDS_TILES = 640;

    /** Islands only comes in one size: it counts as Massive for everything that depends on size. */
    void normalize() {
        if (v[OPT_PRESET] == ISLANDS) v[OPT_SIZE] = MASSIVE;
    }

    int water() {
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
                + (v[OPT_COUNTRY] == 0 ? "" : "@" + v[OPT_COUNTRY]) + (edits.isEmpty() ? "" : "~" + editString());
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
    private static final String CODE_MAPS = "123456789ABCDEF";

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
        normalize();
        setEdits(ed);
        keepCity = true;
        return true;
    }

    /** Picks a new random city (keeping the options). */
    void newSeed(Random r) {
        edits.clear();
        seed = r.nextInt(1000000);
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
            "Off: nobody else comes. Only the police and soldiers already in the city fight.",
            "Low: 1 wave of police backup (4 officers), 1 army reserve squad (4 soldiers), 1 tank and 1 helicopter sortie. "
                    + "The National Guard comes if the army runs out and the city is losing.",
            "Medium: 2 waves of police backup, 2 army reserve squads, 1 tank and 1 helicopter sortie, then the National Guard "
                    + "if it's needed.",
            "High: 3 waves of police backup, 3 army reserve squads, 2 tanks and 2 helicopter sorties, then the National Guard "
                    + "if it's needed."};
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
    private static final int[] RAIL = {1, 0, 0, 2, 0, 0, 1, 0, 2, 0, 0, 0, 0, 0, 0};

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
        for (int i = 0; i < v.length; i++) v[i] = r.nextInt(VALUES[i].length);
        v[OPT_SIZE] = MEDIUM;
        normalize();
        if (v[OPT_CIVILIANS] == 0) v[OPT_CIVILIANS] = 4;
        keepCity = false;
    }

    // ------------------------------------------------------------------ OptionSet

    @Override public int count() { return LABELS.length; }
    @Override public String label(int i) { return LABELS[i]; }
    @Override public String[] values(int i) { return VALUES[i]; }
    @Override public int get(int i) { return v[i]; }
    @Override public void set(int i, int value) {
        v[i] = value;
        normalize();
    }
}
