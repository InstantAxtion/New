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
            "Los Angeles", "Portland", "Seattle", "New York", "Sydney", "Tokyo", "Paris",
            // (10.56)
            "Garrison State",
            // (10.60)
            "Archipelago", "Mountain Valley",
            // (11.0)
            "Bay City", "Delta Marsh", "Fjord Town", "Canal City"};
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
            "",
            "",
            "Homes and parks round a lake in the middle of town, with a jetty, rowing boats and an island.",
            "A working port: quays, docks, cranes, container stacks and ships, with warehouses along the water.",
            "Three island towns in the sea, joined by bridges, with a causeway to the mainland. Always one size: between Large and Massive.",
            RealCities.INFO[0], RealCities.INFO[1], RealCities.INFO[2], RealCities.INFO[3], "", "", "",
            "",
            "One big island city in the middle of the sea, with two to five smaller island towns round it, every one lived in and joined to it by a bridge, and a causeway to the mainland. Always one size.",
            "",
            "",
            "", "",
            "",
    };

    static final int STYLE_MIXED = 0, STYLE_OFFICES = 1, STYLE_HOUSES = 2, STYLE_WAREHOUSES = 3;

    static final int OPT_PRESET = 0, OPT_SIZE = 1, OPT_CIVILIANS = 2, OPT_COPS = 3, OPT_MILITARY = 4,
            OPT_ZOMBIES = 5, OPT_RESERVES = 6, OPT_COUNTRY = 7;
    /** The country round the town (10.20): not one of the eight in v (it's kept in outskirts). */
    static final int OPT_OUTSKIRTS = 8;
    private static final String[] OUTSKIRTS = {"Default", "Little nature"};
    /** (10.47) Whether the people flee the city once the news breaks (not one of the eight in v either). */
    static final int OPT_EXODUS = 9;
    private static final String[] EXODUS = {"Off", "On foot", "Bus & train", "All ways"};
    private static final String[] LABELS = {"Map", "Map size", "Civilians", "Cops", "Military", "Zombies",
            "Reinforcements", "Country"};
    private static final String[][] VALUES = {
            PRESETS,
            // (Since 10.18 the three sizes offered are called Default, Large and Massive.)
            {"Tiny", "Small", "Medium", "Default", "Large", "Massive", "Gigantic"},
            {"None", "Few", "Some", "Most", "All"},
            {"0", "5", "10", "20", "40", "60"},
            {"0", "5", "10", "20", "40"},
            {"0", "1", "5", "20", "50", "100", "200"},
            // (Since 10.20 help from outside is just on or off: the old Low and Medium count as On.)
            {"Off", "On (Low)", "On (Medium)", "On"},
            Country.NAMES,
    };
    /** Tiles across for each size; cities built before 10.10 (no Tiny or Huge then) were a quarter smaller. */
    private static final int[] SIZES = {160, 240, 320, 400, 560, 704, 1024};
    private static final int[] OLD_SIZES = {192, 192, 256, 320, 448, 448, 448};
    /** The size settings, by name. */
    static final int TINY = 0, SMALL = 1, MEDIUM = 2, LARGE = 3, MASSIVE = 4, HUGE = 5, GIGANTIC = 6;
    /** Share of the city's residents out and about when the game starts. */
    private static final float[] RESIDENT_SHARE = {0, 0.2f, 0.45f, 0.7f, 1f};
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
            {STYLE_MIXED, 1, 2, 1, 2, 1, 2, 1},       // Garrison State
            {STYLE_MIXED, 1, 2, 2, 1, 1, 2, 1},       // Archipelago
            {STYLE_HOUSES, 1, 1, 2, 1, 1, 2, 1},      // Mountain Valley
            {STYLE_MIXED, 1, 2, 2, 2, 0, 2, 1},       // Bay City
            {STYLE_HOUSES, 1, 1, 2, 1, 1, 2, 1},      // Delta Marsh
            {STYLE_HOUSES, 1, 1, 2, 1, 1, 2, 1},      // Fjord Town
            {STYLE_MIXED, 2, 1, 1, 2, 0, 2, 1},       // Canal City
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
            {1, 1, 1, 1, 2, 1, 25},   // Garrison State
            {2, 2, 2, 2, 2, 1, 35},   // Archipelago
            {2, 1, 1, 1, 2, 1, 30},   // Mountain Valley
            {2, 2, 2, 2, 2, 1, 40},   // Bay City
            {2, 2, 2, 2, 2, 1, 25},   // Delta Marsh
            {2, 2, 1, 1, 2, 1, 30},   // Fjord Town
            {3, 2, 2, 2, 1, 1, 50},   // Canal City
    };
    // Share of office lots that become apartment blocks, parking garages and pharmacies (percent).
    private static final int[][] BUILDING_MIX = {
            {15, 6, 5}, {15, 15, 5}, {20, 0, 6}, {5, 8, 2}, {15, 3, 6}, {10, 0, 8}, {10, 2, 8}, {20, 5, 6},
            {25, 15, 4}, {0, 0, 10}, {25, 3, 6}, {20, 8, 5}, {15, 2, 6}, {10, 6, 3}, {20, 8, 5},
            {20, 12, 5}, {20, 6, 5}, {25, 10, 5}, {35, 12, 4}, {25, 8, 5}, {35, 12, 5}, {35, 4, 6},
            {30, 8, 4},
            {20, 8, 5}, {12, 3, 6},
            {25, 10, 5}, {12, 3, 6}, {12, 3, 6}, {30, 6, 6},
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
            {2, 1, 3, 2, 0, 0},
            {3, 2, 1, 2, 2, 1}, {4, 2, 1, 1, 3, 0},
            {3, 2, 1, 2, 2, 1}, {4, 2, 1, 1, 3, 0}, {4, 2, 1, 1, 3, 0}, {3, 1, 0, 1, 3, 0},
    };

    /** Current index into VALUES for each option. */
    final int[] v = {METROPOLIS, LARGE, PEOPLE_ABOUT, 2, 1, 0, 3, 0};
    /** The map a new game starts on (since Classic went in 10.18). */
    static final int METROPOLIS = 8;

    /**
     * Choices no longer offered on the New Game screen (10.17): five of the maps (Industrial, Parkland, Old
     * Town, Small Town and Harbour), the three smallest map sizes and seven of the countries. Cities from older
     * saves and shared codes that use them still build as they were.
     */
    static boolean retired(int option, int value) {
        // (The real cities went in 10.28.)
        // (Downtown, Village and Lakeside went in 10.50.)
        if (option == OPT_PRESET) return value == 0 || value == 1 || value == 3 || value == 4 || value == 5 || value == 6 || value == 7
                || value == 9 || value == 12 || value == 13 || RealCities.isReal(value) || removed(value);
        // (The number of zombies at the start isn't a choice any more: the game starts quiet.)
        if (option == OPT_ZOMBIES) return value != 0;
        if (option == OPT_SIZE) return value < LARGE;
        if (option == OPT_COUNTRY) return value != Country.USA;
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
    /**
     * (10.47) Fleeing citizens: 0 off (everyone sits it out in town, as before); otherwise, once the news breaks,
     * most of the people leave the city, and come home once the outbreak is over - 1 on foot, 2 (10.49) by
     * evacuation bus and train only, 3 by bus or train if a stop is near, on foot if not. (In 10.48, 2 and 3 were
     * by car.) Not part of the city code.
     */
    int exodus;
    static final int EXODUS_OFF = 0, EXODUS_FOOT = 1, EXODUS_TRANSIT = 2, EXODUS_ALL = 3;

    boolean exodus() { return exodus != EXODUS_OFF; }

    boolean littleNature() { return outskirts == 1 && civic >= 7; }
    /**
     * Which public buildings the city was laid out with: 0 as before 10.4, 1 the government quarter (10.4),
     * 2 and the National Guard armory (10.5), 3 the bigger maps of 10.10 with hills, mountains, lakes, streams
     * and nature parks out in the country, 4 the wilds of 10.14 (streams you can wade, trails that cross the
     * lanes, a ranger station and fire lookout towers), 5 the lanes of 10.18 (no stretch of tarmac in the
     * middle of a dirt lane) and the real cities, 6 the driveways of 10.19, 7 the helipads and busier outskirts of 10.20, 8 dirt lanes that stay dirt into town (10.23), 9 streets that line up, more kinds of road and hills in town (10.24), 10 curved streets in the suburbs (10.25), 11 farm fields and park paths kept clear (10.26), 12 the ring road, wider avenues and stations on the road network (10.28), 13 highway interchanges with slip roads instead of crossroads (10.29), 14 fuller country on the Massive maps with a big army base and its checkpoints (10.30), 15 one-way slip roads that run in alongside the highway, and the Gigantic size (10.31), 16 bridges over the highway at the interchanges, the army base checkpoints (10.32), 17 nothing joining the highway but its slip roads, and fuller maps (10.33), 18 every road joined up, and livelier maps (10.34), 21 another police station on every map from the Default size up, 22 the beltway right round the map, 23 an army base out in the country from the Default size up (bigger on the Massive maps) and a police headquarters, 24 buildings of more shapes than a box (10.52), 25 about the same number of people on every map (10.63). Saved games rebuild with what they had.
     */
    int civic = 27;

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
    static final int W_NONE = 0, W_LAKE = 3, W_HARBOUR = 4, W_ISLANDS = 5, W_REAL = 6;
    /** The Islands map, and its one size (tiles across: a bit bigger than Massive). */
    static final int ISLANDS = 14, ISLANDS_TILES = 640;
    /** (10.60) The Archipelago (always one size, like Islands). */
    static final int ARCHIPELAGO = 23, ARCHIPELAGO_TILES = 720;

    /**
     * (11.3) Maps taken out of the game altogether, code and all: Sydney, Tokyo and Paris (their countries are
     * gone), Seaside, River City, the Garrison State, the Mountain Valley, Bay City, Delta Marsh, Fjord Town and
     * Canal City. Their numbers stay taken so the other maps keep
     * theirs; saves and city codes on them no longer load.
     */
    static boolean removed(int preset) {
        return preset == 10 || preset == 11 || preset == 25 || preset == 19 || preset == 20 || preset == 21 || preset == 22 || preset == 24 || preset == 26 || preset == 27 || preset == 28;
    }

    boolean archipelago() { return v[OPT_PRESET] == ARCHIPELAGO; }

    /** Islands only comes in one size: it counts as Massive for everything that depends on size. */
    void normalize() {
        if (v[OPT_PRESET] == ISLANDS || v[OPT_PRESET] == ARCHIPELAGO) v[OPT_SIZE] = MASSIVE;
        if (removed(v[OPT_PRESET])) v[OPT_PRESET] = METROPOLIS;
        v[OPT_COUNTRY] = Country.USA;
        // A real city is always the same city: the biggest size, in its own country.
        RealCities.Spec real = RealCities.get(v[OPT_PRESET]);
        if (real != null) {
            v[OPT_SIZE] = HUGE;
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
            case 12: return W_LAKE;
            case 13: return W_HARBOUR;
            case ISLANDS: case ARCHIPELAGO: return W_ISLANDS;
            default: return W_NONE;
        }
    }

    /**
     * A short code that rebuilds this exact city: map preset and size, then the seed, e.g. "12-483920".
     * Share it and anyone can play the same streets.
     */
    String code() {
        return CODE_MAPS.charAt(v[OPT_PRESET]) + "" + v[OPT_SIZE] + "-" + seed
                + (outskirts == 1 ? "!" : "") + (edits.isEmpty() ? "" : "~" + editString());
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
    private static final String CODE_MAPS = "123456789ABCDEFGHIJKLMNOPQRST";

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
        // (Codes for a city in another country, after an "@", no longer load: since 11.3 there's only the USA.)
        int at = t.indexOf('@');
        if (at > 0) {
            if (!t.substring(at + 1).equals("0")) return false;
            t = t.substring(0, at);
        }
        int dash = t.indexOf('-');
        if (dash != 2 || t.length() < 4 || t.length() > 22) return false;
        // (The size digit: 0 Tiny, 1 Small, 2 Medium, 3 Large, 4 Massive, 5 Huge. Codes from before Tiny was
        // added used 1 to 4 for Small to Massive, so they still mean the same size.)
        int preset = CODE_MAPS.indexOf(Character.toUpperCase(t.charAt(0))), size = t.charAt(1) - '0';
        if (preset < 0 || preset >= PRESETS.length || removed(preset) || size < 0 || size >= SIZES.length) return false;
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
        v[OPT_COUNTRY] = Country.USA;
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
        if (v[OPT_PRESET] == ARCHIPELAGO) return ARCHIPELAGO_TILES;
        return (nature() ? SIZES : OLD_SIZES)[v[OPT_SIZE]];
    }

    /** A massive (or huge) map: the city sits in the middle of open countryside. */
    boolean massive() { return v[OPT_SIZE] >= MASSIVE; }
    /** (10.30) A big army base out in the country: every Massive and Huge map with country round the town. */
    boolean outskirtsBase() { return civic >= 14 && civic < 26 && (massive() || (civic >= 23 && v[OPT_SIZE] >= LARGE)) && water() == W_NONE && coreFraction() < 0.99f; }

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
        // ((11.0) Less nature: a thin band of country round a town that fills nine tenths of the map, whatever the map.)
        // (Just wide enough for the highway and its interchanges: 28 tiles each side, or a little more on the big maps.
        // The Garrison State keeps its open country for its bases and the prison.)
        if (civic >= 26) {
            int t = tiles();
            float band = Math.max(28, t * 0.04f) * 2f / t;
            return Math.max(p == 9 ? 0.72f : 0.78f, 1 - band);
        }
        if (p == 9) return v[OPT_SIZE] <= SMALL ? 0.6f : 0.5f;
        if (p == 9) return v[OPT_SIZE] <= SMALL ? 0.6f : 0.5f;
        float town = new float[]{1f, 0.86f, 0.8f, 0.76f, 0.74f, 0.66f, 0.66f}[v[OPT_SIZE]];
        // (Since 10.30 the Massive and Huge maps have more country round the town, for its towns and the base.)
        if (civic >= 14 && massive()) town = v[OPT_SIZE] >= GIGANTIC ? 0.7f : v[OPT_SIZE] >= HUGE ? 0.6f : 0.66f;
        // (10.33) Less empty country: the town reaches further out (the country beyond fills with towns too).
        if (civic >= 17) town = Math.min(1f, town + (v[OPT_SIZE] >= GIGANTIC ? 0.04f : massive() ? 0.08f : 0.02f));
        // ((10.56) The Garrison State keeps more open country round the town, for its bases and the prison.)
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
        // About one officer for every 24 residents the game simulates, plus a few for each station (10.33: more).
        return Math.max(10, Math.min(360, Math.round(residents / 24f) + policeStations() * 6));
    }

    /** Soldiers at the base (if the map has one): a garrison that grows with the city. */
    int soldiers(int residents) {
        if (!militaryBase() && !outskirtsBase()) return 0;
        // (10.33: bigger garrisons.)
        return Math.max(16, Math.min(120, Math.round(residents / 50f) + 10));
    }


    /**
     * (10.63) How many should live in the city (cities built since map version 25): enough that with most of them
     * about (the usual setting) there are some 15,000 people on the Default size, rising to 20,000 on the Gigantic.
     */
    int residentsTarget() {
        int s = v[OPT_SIZE];
        float people = s <= LARGE ? 15000 : s == MASSIVE ? 16500 : s == HUGE ? 18000 : 20000;
        return Math.round(people / RESIDENT_SHARE[PEOPLE_ABOUT]);
    }

    /** What each Reinforcements setting means, in plain words. */
    static final String[] RESERVE_INFO = {
            "Off: nobody else comes. Only the police, soldiers and helicopters already in the city fight.",
            "On: help comes from outside the city.",
            "On: help comes from outside the city.",
            "On: police come first, half a minute or so after being asked: some 320 officers in waves from the towns around "
                    + "(their police, highway patrol, SWAT teams, sheriff's deputies and federal agents), sooner and bigger "
                    + "the worse the city is losing. Then some 240 soldiers in three waves: a quick reaction force in Humvees, "
                    + "the main convoy with armour, and air assaults by helicopter wherever it's worst."};
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
        return n == 0 ? 0 : Math.max(1, n - (s == TINY ? 1 : 0)) + (s >= LARGE ? 1 : 0) + (s >= MASSIVE ? 1 : 0) + (s >= HUGE ? 1 : 0) + (s >= GIGANTIC ? 2 : 0)
                // ((10.52) One more on every map from the Default size up.)
                + (civic >= 21 && s >= LARGE ? 1 : 0);
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
        if (s >= GIGANTIC) return n * 3;
        return n;
    }

    int shopShare() { return LANDMARKS[v[OPT_PRESET]][6]; }

    // Which maps have a railway line (with a station and passing trains).
    // Which maps have a railway line: 0 never, 1 about half the time (it depends on the city), 2 always.
    private static final int[] RAIL = {1, 0, 0, 2, 0, 0, 1, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0};

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
            {3, 4, 1, 3, 3, 0, 1, 1},   // Garrison State
            {3, 4, 2, 4, 1, 1, 2, 0},   // Archipelago
            {1, 2, 3, 5, 1, 0, 3, 2},   // Mountain Valley
            {3, 4, 2, 3, 1, 1, 2, 0},   // Bay City
            {1, 2, 2, 6, 1, 0, 3, 3},   // Delta Marsh
            {1, 2, 3, 5, 1, 0, 3, 2},   // Fjord Town
            {2, 3, 6, 2, 1, 0, 1, 2},   // Canal City
    };

    /** How likely each kind of district is on this map. */
    int districtWeight(int type) { return DISTRICT_MIX[v[OPT_PRESET]][type]; }

    /** The kind of district in the middle of town. */
    int coreDistrict() { return DISTRICT_MIX[v[OPT_PRESET]][7]; }

    /** Percent of office lots that become apartments, garages and pharmacies. */
    int[] buildingMix() { return BUILDING_MIX[v[OPT_PRESET]]; }

    /** Relative weights of the park kinds for this map. */
    int[] parkMix() { return PARK_MIX[v[OPT_PRESET]]; }

    /**
     * (10.50) A fresh New Game screen leaves behind the choices no longer offered (a map or country taken off, the
     * people about and outskirts settings that aren't choices now).
     */
    void dropRetired() {
        if (retired(OPT_PRESET, v[OPT_PRESET])) v[OPT_PRESET] = METROPOLIS;
        if (retired(OPT_COUNTRY, v[OPT_COUNTRY])) v[OPT_COUNTRY] = Country.USA;
        v[OPT_CIVILIANS] = PEOPLE_ABOUT;
        outskirts = 0;
        normalize();
    }

    /** (10.50) The share of residents out and about when a game starts: no longer a setting. */
    static final int PEOPLE_ABOUT = 3;

    void randomize(Random r) {
        for (int i = 0; i < v.length; i++) {
            // (A random city is a made-up one: not one of the real cities.)
            do v[i] = r.nextInt(VALUES[i].length);
            while (retired(i, v[i]) || (i == OPT_PRESET && RealCities.isReal(v[i])));
        }
        v[OPT_SIZE] = LARGE;
        normalize();
        v[OPT_CIVILIANS] = PEOPLE_ABOUT;
        outskirts = 0;
        keepCity = false;
    }

    // ------------------------------------------------------------------ OptionSet

    @Override public int count() { return LABELS.length; }
    @Override public String label(int i) { return LABELS[i]; }
    @Override public String[] values(int i) { return i == OPT_OUTSKIRTS ? OUTSKIRTS : i == OPT_EXODUS ? EXODUS : VALUES[i]; }
    @Override public int get(int i) { return i == OPT_OUTSKIRTS ? outskirts : i == OPT_EXODUS ? exodus : v[i]; }
    @Override public void set(int i, int value) {
        if (i == OPT_OUTSKIRTS) {
            outskirts = value;
            return;
        }
        if (i == OPT_EXODUS) {
            exodus = value;
            return;
        }
        v[i] = value;
        normalize();
    }
}
