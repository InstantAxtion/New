package com.instantaxtion.zombiesandbox;

import java.util.ArrayList;
import java.util.List;

/**
 * Real cities (since 10.18): Los Angeles, Portland, Seattle, New York, Sydney, Tokyo and Paris, always built
 * the same way at the biggest map size. Each is laid out from its own geography - the coast, rivers, bays,
 * lakes, hills, woods and parks where they really are - with its real neighbourhoods, its famous streets,
 * bridges and landmarks. Positions are fractions of the map (0 to 1; north is up).
 */
final class RealCities {
    private RealCities() {
    }

    /** The first real city's map number: they come after the generated maps. */
    static final int FIRST = 15;

    // Landmark building styles (drawn by Roofs.drawLandmark).
    static final int L_STEPPED = 0, L_CROWN = 1, L_OCTAGON = 2, L_HELIPAD = 3, L_SAIL = 4, L_OBSERVATORY = 5,
            L_SAILS = 6, L_PYRAMID = 7, L_MARKET = 8, L_CATHEDRAL = 9, L_TEMPLE = 10, L_SHRINE = 11, L_HALL = 12,
            L_DOME = 13, L_WHITE_DOMES = 14, L_PINK = 15, L_DARK = 16, L_LIBRARY = 17, L_ARENA = 18, L_CLASSIC = 19,
            L_STATION = 20, L_THEATRE = 21, L_CAMPUS = 22, L_CLOCK = 23, L_ROUND = 24, L_PIPES = 25, L_WEDGE = 26,
            L_SLAB = 27, L_SPIRAL = 28, L_BLOB = 29, L_TWIN = 30, L_PALACE = 31, L_CUBE_ARCH = 32, L_CURVES = 33,
            L_BRICK = 34, L_MANSION = 35, L_SIGN = 36, L_GOLD_DOME = 37, L_DOME_STADIUM = 38;
    /** Not a building: a stadium (built by City.stadium), or a structure drawn standing up (see City.Structure). */
    static final int L_STADIUM = 100;
    static final int ST_NEEDLE = 200, ST_EIFFEL = 201, ST_TOKYO_TOWER = 202, ST_SKYTREE = 203, ST_SYDNEY_TOWER = 204,
            ST_LIBERTY = 205, ST_HOLLYWOOD = 206, ST_ARC = 207, ST_FERRIS = 208, ST_SCREENS = 209, ST_OBELISK = 210,
            ST_BRIDGE_ARCH = 211, ST_BRIDGE_TOWERS = 212, ST_PIER = 213;

    /** Districts, by City.DT_ kind. */
    private static final int DOWN = City.DT_DOWNTOWN, MID = City.DT_MIDTOWN, OLD = City.DT_OLDTOWN, SUB = City.DT_SUBURB,
            IND = City.DT_INDUSTRIAL, UNI = City.DT_CAMPUS, PARK = City.DT_PARKSIDE;

    static final class Area {
        final float[] poly;
        final String name;
        /** 0 a park (lawns, paths and trees), 1 wild woods and hills, 2 a cemetery. */
        final int kind;

        Area(String name, int kind, float[] poly) {
            this.name = name;
            this.kind = kind;
            this.poly = poly;
        }
    }

    static final class Place {
        final String name;
        final float x, y;
        final int type;

        Place(String name, float x, float y, int type) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.type = type;
        }
    }

    static final class Landmark {
        final String name;
        final float x, y;
        /** Size in tiles, floors (or a structure's height in world units). */
        final int w, h, floors, style;

        Landmark(String name, float x, float y, int w, int h, int floors, int style) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.floors = floors;
            this.style = style;
        }
    }

    /** A named bridge: across the water at (x, y), running north-south (vertical) or east-west. */
    static final class Crossing {
        final String name;
        final float x, y;
        final boolean vertical;
        /** A structure over it: ST_BRIDGE_ARCH, ST_BRIDGE_TOWERS, or 0. */
        final int look;

        Crossing(String name, float x, float y, boolean vertical, int look) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.vertical = vertical;
            this.look = look;
        }
    }

    /** A street name for the street lines nearest pos (y for east-west streets, x for north-south ones), between from and to across. */
    static final class StreetName {
        final String name;
        final float pos, from, to;

        StreetName(String name, float pos, float from, float to) {
            this.name = name;
            this.pos = pos;
            this.from = from;
            this.to = to;
        }
    }

    static final class Spec {
        String name, info;
        int country;
        long seed;
        /** The open sea and bays (water). */
        final List<float[]> sea = new ArrayList<float[]>();
        /** If any: the land (everything else is water). */
        final List<float[]> land = new ArrayList<float[]>();
        /** Land inside the water anyway: islands. */
        final List<float[]> islands = new ArrayList<float[]>();
        /** Rivers: {width in tiles, x0, y0, x1, y1, ...}. */
        final List<float[]> rivers = new ArrayList<float[]>();
        /** Lakes and ponds: {x, y, rx, ry}. */
        final List<float[]> lakes = new ArrayList<float[]>();
        /** Beaches: {x0, y0, x1, y1} along the shore. */
        final List<float[]> beaches = new ArrayList<float[]>();
        /** Hills: {x, y, radius, height}. */
        final List<float[]> hills = new ArrayList<float[]>();
        /** Moats round a park: {x0, y0, x1, y1}. */
        final List<float[]> moats = new ArrayList<float[]>();
        final List<Area> areas = new ArrayList<Area>();
        final List<Place> places = new ArrayList<Place>();
        final List<Landmark> landmarks = new ArrayList<Landmark>();
        final List<Crossing> crossings = new ArrayList<Crossing>();
        final List<StreetName> eastWest = new ArrayList<StreetName>(), northSouth = new ArrayList<StreetName>();
        /** The heart of downtown, where the towers are tallest. */
        float downX = 0.5f, downY = 0.5f;
        /** How tall the city builds (CityConfig height: 0 to 3), and a height limit outside downtown (0: none). */
        int height = 3, maxFloors;

        void sea(float... p) { sea.add(p); }
        void land(float... p) { land.add(p); }
        void island(float... p) { islands.add(p); }
        void river(float... p) { rivers.add(p); }
        void lake(float x, float y, float rx, float ry) { lakes.add(new float[]{x, y, rx, ry}); }
        void beach(float x0, float y0, float x1, float y1) { beaches.add(new float[]{x0, y0, x1, y1}); }
        void hill(float x, float y, float r, float height) { hills.add(new float[]{x, y, r, height}); }
        void park(String name, float... p) { areas.add(new Area(name, 0, p)); }
        void wild(String name, float... p) { areas.add(new Area(name, 1, p)); }
        void cemetery(String name, float... p) { areas.add(new Area(name, 2, p)); }
        void place(String name, float x, float y, int type) { places.add(new Place(name, x, y, type)); }
        void mark(String name, float x, float y, int w, int h, int floors, int style) { landmarks.add(new Landmark(name, x, y, w, h, floors, style)); }
        void bridge(String name, float x, float y, boolean vertical, int look) { crossings.add(new Crossing(name, x, y, vertical, look)); }
        void ew(String name, float y) { eastWest.add(new StreetName(name, y, 0, 1)); }
        void ew(String name, float y, float from, float to) { eastWest.add(new StreetName(name, y, from, to)); }
        void ns(String name, float x) { northSouth.add(new StreetName(name, x, 0, 1)); }
        void ns(String name, float x, float from, float to) { northSouth.add(new StreetName(name, x, from, to)); }

        /** A rectangle as a polygon. */
        static float[] rect(float x0, float y0, float x1, float y1) {
            return new float[]{x0, y0, x1, y0, x1, y1, x0, y1};
        }
    }

    static final String[] NAMES = {"Los Angeles", "Portland", "Seattle", "New York", "Sydney", "Tokyo", "Paris"};
    static final int[] COUNTRIES = {Country.USA, Country.USA, Country.USA, Country.USA, Country.AUSTRALIA, Country.JAPAN, Country.FRANCE};
    static final String[] INFO = {
            "Los Angeles: the Pacific beaches at Santa Monica and Venice, the Santa Monica Mountains and Griffith Park, "
                    + "Hollywood and its sign, the LA River and the towers of Downtown.",
            "Portland, Oregon: the Willamette through the middle with its bridges, Forest Park and the West Hills, the "
                    + "Columbia to the north, the Pearl District and the east side.",
            "Seattle, Washington: Elliott Bay and Puget Sound, Lake Union and Lake Washington, the Space Needle, Pike "
                    + "Place Market, Queen Anne and Capitol Hill.",
            "New York: Manhattan between the Hudson and the East River, Central Park, Midtown and the Financial District, "
                    + "Brooklyn, Queens, the Bronx and New Jersey across the water.",
            "Sydney: the harbour, the Opera House and the Harbour Bridge, the CBD and the North Shore, Bondi and Manly "
                    + "on the Pacific.",
            "Tokyo: the Imperial Palace and its moat, Marunouchi, Ginza, Shinjuku and Shibuya, the Sumida, Tokyo Tower, "
                    + "the Skytree and Odaiba out in the bay.",
            "Paris: the Seine and its islands, the Eiffel Tower, the Arc de Triomphe, the Louvre, Notre-Dame, "
                    + "Montmartre, the Bois de Boulogne and La Défense.",
    };

    static boolean isReal(int preset) {
        return preset >= FIRST && preset < FIRST + NAMES.length;
    }

    private static final Spec[] CACHE = new Spec[NAMES.length];

    static synchronized Spec get(int preset) {
        if (!isReal(preset)) return null;
        int k = preset - FIRST;
        if (CACHE[k] == null) {
            Spec s = new Spec();
            s.name = NAMES[k];
            s.info = INFO[k];
            s.country = COUNTRIES[k];
            s.seed = 1000 + k * 7919;
            switch (k) {
                case 0: losAngeles(s); break;
                case 1: portland(s); break;
                case 2: seattle(s); break;
                case 3: newYork(s); break;
                case 4: sydney(s); break;
                case 5: tokyo(s); break;
                default: paris(s); break;
            }
            CACHE[k] = s;
        }
        return CACHE[k];
    }

    // ------------------------------------------------------------------ Los Angeles

    private static void losAngeles(Spec s) {
        s.downX = 0.85f;
        s.downY = 0.56f;
        // The Pacific, curving in to Santa Monica Bay; the marina.
        s.sea(0, 0, 0.1f, 0, 0.09f, 0.15f, 0.075f, 0.3f, 0.085f, 0.45f, 0.1f, 0.6f, 0.12f, 0.75f, 0.13f, 0.9f, 0.12f, 1, 0, 1);
        s.sea(0.12f, 0.71f, 0.165f, 0.71f, 0.17f, 0.765f, 0.125f, 0.775f);
        s.beach(0.075f, 0.22f, 0.085f, 0.45f);
        s.beach(0.085f, 0.47f, 0.12f, 0.7f);
        s.beach(0.12f, 0.78f, 0.13f, 0.98f);
        s.wild("Santa Monica Mountains", 0.08f, 0, 0.62f, 0, 0.62f, 0.12f, 0.55f, 0.17f, 0.45f, 0.18f, 0.35f, 0.2f, 0.25f, 0.21f,
                0.16f, 0.23f, 0.1f, 0.19f, 0.09f, 0.1f);
        s.wild("Griffith Park", 0.62f, 0, 0.8f, 0, 0.82f, 0.1f, 0.78f, 0.2f, 0.7f, 0.215f, 0.64f, 0.17f, 0.62f, 0.12f);
        s.park("Elysian Park", 0.82f, 0.36f, 0.87f, 0.35f, 0.875f, 0.41f, 0.83f, 0.415f);
        s.park("Kenneth Hahn Park", Spec.rect(0.36f, 0.69f, 0.41f, 0.74f));
        s.park("Exposition Park", Spec.rect(0.74f, 0.67f, 0.79f, 0.72f));
        s.park("MacArthur Park", Spec.rect(0.75f, 0.5f, 0.775f, 0.52f));
        s.river(4, 0.84f, 0, 0.83f, 0.12f, 0.86f, 0.25f, 0.9f, 0.38f, 0.92f, 0.5f, 0.93f, 0.65f, 0.92f, 0.8f, 0.9f, 1);
        s.lake(0.78f, 0.3f, 0.012f, 0.02f);
        s.lake(0.8f, 0.425f, 0.008f, 0.007f);
        s.lake(0.6f, 0.095f, 0.015f, 0.01f);
        s.lake(0.7625f, 0.51f, 0.008f, 0.006f);
        s.hill(0.35f, 0.08f, 0.18f, 40);
        s.hill(0.55f, 0.08f, 0.1f, 35);
        s.hill(0.72f, 0.1f, 0.08f, 38);
        s.hill(0.25f, 0.2f, 0.05f, 22);
        s.hill(0.38f, 0.72f, 0.06f, 16);
        s.hill(0.84f, 0.38f, 0.04f, 12);
        s.place("Downtown LA", 0.85f, 0.56f, DOWN);
        s.place("Koreatown", 0.7f, 0.5f, MID);
        s.place("Hollywood", 0.6f, 0.28f, MID);
        s.place("West Hollywood", 0.5f, 0.32f, MID);
        s.place("Beverly Hills", 0.4f, 0.35f, SUB);
        s.place("Century City", 0.36f, 0.44f, DOWN);
        s.place("Westwood", 0.28f, 0.33f, UNI);
        s.place("Brentwood", 0.2f, 0.28f, SUB);
        s.place("Santa Monica", 0.14f, 0.42f, MID);
        s.place("Venice", 0.15f, 0.62f, OLD);
        s.place("Marina del Rey", 0.19f, 0.74f, MID);
        s.place("Culver City", 0.32f, 0.62f, SUB);
        s.place("Palms", 0.25f, 0.53f, SUB);
        s.place("Mid-City", 0.55f, 0.52f, MID);
        s.place("Los Feliz", 0.72f, 0.27f, SUB);
        s.place("Silver Lake", 0.77f, 0.33f, OLD);
        s.place("Echo Park", 0.81f, 0.43f, OLD);
        s.place("Arts District", 0.89f, 0.6f, IND);
        s.place("Boyle Heights", 0.96f, 0.6f, SUB);
        s.place("Vernon", 0.88f, 0.82f, IND);
        s.place("South Central", 0.72f, 0.82f, SUB);
        s.place("Leimert Park", 0.55f, 0.75f, SUB);
        s.place("Inglewood", 0.45f, 0.9f, SUB);
        s.place("Westchester", 0.25f, 0.88f, SUB);
        s.mark("Hollywood Sign", 0.6f, 0.13f, 0, 0, 14, ST_HOLLYWOOD);
        s.mark("Griffith Observatory", 0.72f, 0.17f, 7, 4, 2, L_OBSERVATORY);
        s.mark("Santa Monica Pier", 0.08f, 0.44f, 0, 0, 0, ST_PIER);
        s.mark("Los Angeles City Hall", 0.86f, 0.535f, 6, 6, 24, L_HALL);
        s.mark("U.S. Bank Tower", 0.835f, 0.565f, 5, 5, 30, L_HELIPAD);
        s.mark("Wilshire Grand Center", 0.82f, 0.585f, 5, 4, 32, L_SAIL);
        s.mark("Walt Disney Concert Hall", 0.865f, 0.555f, 6, 5, 4, L_CURVES);
        s.mark("Dodger Stadium", 0.86f, 0.42f, 16, 14, 0, L_STADIUM);
        s.mark("Los Angeles Memorial Coliseum", 0.765f, 0.695f, 16, 14, 0, L_STADIUM);
        s.mark("SoFi Stadium", 0.45f, 0.84f, 16, 14, 0, L_STADIUM);
        s.mark("Getty Center", 0.25f, 0.215f, 9, 5, 3, L_CAMPUS);
        s.mark("Capitol Records Building", 0.6f, 0.295f, 4, 4, 13, L_ROUND);
        s.mark("TCL Chinese Theatre", 0.585f, 0.285f, 5, 4, 2, L_THEATRE);
        s.ew("Franklin Ave", 0.23f);
        s.ew("Hollywood Blvd", 0.26f);
        s.ew("Sunset Blvd", 0.3f);
        s.ew("Santa Monica Blvd", 0.34f);
        s.ew("Melrose Ave", 0.37f);
        s.ew("Beverly Blvd", 0.4f);
        s.ew("3rd St", 0.43f);
        s.ew("Wilshire Blvd", 0.47f);
        s.ew("6th St", 0.5f);
        s.ew("Olympic Blvd", 0.53f);
        s.ew("Pico Blvd", 0.56f);
        s.ew("Venice Blvd", 0.6f);
        s.ew("Washington Blvd", 0.63f);
        s.ew("Jefferson Blvd", 0.66f);
        s.ew("Exposition Blvd", 0.69f);
        s.ew("Martin Luther King Jr Blvd", 0.72f);
        s.ew("Slauson Ave", 0.76f);
        s.ew("Florence Ave", 0.8f);
        s.ew("Manchester Ave", 0.84f);
        s.ew("Century Blvd", 0.9f);
        s.ew("Imperial Hwy", 0.96f);
        s.ns("Ocean Ave", 0.1f);
        s.ns("Lincoln Blvd", 0.15f);
        s.ns("Bundy Dr", 0.2f);
        s.ns("Sepulveda Blvd", 0.25f);
        s.ns("Westwood Blvd", 0.29f);
        s.ns("Overland Ave", 0.33f);
        s.ns("Robertson Blvd", 0.4f);
        s.ns("La Cienega Blvd", 0.45f);
        s.ns("Fairfax Ave", 0.5f);
        s.ns("La Brea Ave", 0.54f);
        s.ns("Highland Ave", 0.57f);
        s.ns("Vine St", 0.6f);
        s.ns("Western Ave", 0.65f);
        s.ns("Normandie Ave", 0.68f);
        s.ns("Vermont Ave", 0.71f);
        s.ns("Hoover St", 0.74f);
        s.ns("Alvarado St", 0.77f);
        s.ns("Figueroa St", 0.82f);
        s.ns("Grand Ave", 0.845f);
        s.ns("Broadway", 0.86f);
        s.ns("Main St", 0.875f);
        s.ns("Alameda St", 0.89f);
        s.ns("Crenshaw Blvd", 0.62f, 0.55f, 1);
        s.ns("Soto St", 0.97f);
    }

    // ------------------------------------------------------------------ Portland

    private static void portland(Spec s) {
        s.downX = 0.465f;
        s.downY = 0.49f;
        s.height = 2;
        // The Columbia along the north; the Willamette winding up through the middle to meet it.
        s.sea(0, 0, 1, 0, 1, 0.07f, 0.7f, 0.09f, 0.4f, 0.075f, 0, 0.06f);
        s.river(16, 0.18f, 0.03f, 0.28f, 0.15f, 0.4f, 0.28f, 0.49f, 0.37f, 0.515f, 0.47f, 0.52f, 0.6f, 0.55f, 0.75f, 0.53f, 0.88f, 0.5f, 1);
        s.wild("Forest Park", 0, 0.07f, 0.08f, 0.08f, 0.16f, 0.14f, 0.26f, 0.25f, 0.33f, 0.33f, 0.36f, 0.41f, 0.33f, 0.52f,
                0.3f, 0.62f, 0.26f, 0.8f, 0.22f, 1, 0, 1);
        s.park("Washington Park", 0.33f, 0.42f, 0.385f, 0.43f, 0.39f, 0.5f, 0.34f, 0.52f);
        s.park("Mt. Tabor Park", Spec.rect(0.76f, 0.53f, 0.8f, 0.575f));
        s.park("Laurelhurst Park", Spec.rect(0.665f, 0.49f, 0.69f, 0.51f));
        s.park("Peninsula Park", Spec.rect(0.56f, 0.24f, 0.58f, 0.26f));
        s.park("Kelley Point Park", Spec.rect(0.2f, 0.07f, 0.24f, 0.1f));
        s.park("Oaks Bottom", Spec.rect(0.57f, 0.74f, 0.6f, 0.8f));
        s.lake(0.677f, 0.5f, 0.006f, 0.005f);
        s.lake(0.785f, 0.55f, 0.006f, 0.004f);
        s.hill(0.2f, 0.4f, 0.2f, 48);
        s.hill(0.78f, 0.55f, 0.03f, 16);
        s.hill(0.92f, 0.3f, 0.03f, 18);
        s.place("Downtown", 0.465f, 0.5f, DOWN);
        s.place("Pearl District", 0.45f, 0.42f, MID);
        s.place("Old Town Chinatown", 0.485f, 0.45f, OLD);
        s.place("Northwest District", 0.39f, 0.38f, OLD);
        s.place("NW Industrial", 0.32f, 0.24f, IND);
        s.place("St. Johns", 0.4f, 0.13f, SUB);
        s.place("Kenton", 0.52f, 0.14f, SUB);
        s.place("Mississippi", 0.53f, 0.3f, OLD);
        s.place("Alberta Arts District", 0.62f, 0.29f, OLD);
        s.place("Lloyd District", 0.6f, 0.42f, MID);
        s.place("Irvington", 0.65f, 0.37f, SUB);
        s.place("Hollywood", 0.73f, 0.4f, MID);
        s.place("Laurelhurst", 0.68f, 0.47f, SUB);
        s.place("Central Eastside", 0.57f, 0.52f, IND);
        s.place("Hawthorne", 0.64f, 0.6f, OLD);
        s.place("South Waterfront", 0.51f, 0.64f, DOWN);
        s.place("Goose Hollow", 0.41f, 0.5f, MID);
        s.place("Sellwood", 0.6f, 0.87f, SUB);
        s.place("Montavilla", 0.86f, 0.55f, SUB);
        s.place("Parkrose", 0.9f, 0.2f, SUB);
        s.place("Lents", 0.85f, 0.8f, SUB);
        s.place("Multnomah Village", 0.38f, 0.8f, SUB);
        s.bridge("St. Johns Bridge", 0.326f, 0.2f, false, ST_BRIDGE_TOWERS);
        s.bridge("Fremont Bridge", 0.493f, 0.38f, false, ST_BRIDGE_ARCH);
        s.bridge("Broadway Bridge", 0.503f, 0.42f, false, 0);
        s.bridge("Steel Bridge", 0.509f, 0.445f, false, 0);
        s.bridge("Burnside Bridge", 0.515f, 0.47f, false, 0);
        s.bridge("Morrison Bridge", 0.516f, 0.5f, false, 0);
        s.bridge("Hawthorne Bridge", 0.517f, 0.53f, false, 0);
        s.bridge("Marquam Bridge", 0.518f, 0.56f, false, 0);
        s.bridge("Tilikum Crossing", 0.52f, 0.6f, false, ST_BRIDGE_TOWERS);
        s.bridge("Ross Island Bridge", 0.53f, 0.65f, false, 0);
        s.bridge("Sellwood Bridge", 0.535f, 0.85f, false, ST_BRIDGE_ARCH);
        s.mark("Union Station", 0.475f, 0.425f, 7, 4, 3, L_CLOCK);
        s.mark("Big Pink", 0.468f, 0.475f, 5, 4, 28, L_PINK);
        s.mark("Moda Center", 0.56f, 0.425f, 10, 10, 4, L_ARENA);
        s.mark("Providence Park", 0.415f, 0.485f, 14, 12, 0, L_STADIUM);
        s.mark("Powell's City of Books", 0.448f, 0.465f, 6, 5, 3, L_SIGN);
        s.mark("Pittock Mansion", 0.31f, 0.37f, 5, 4, 3, L_MANSION);
        s.mark("OMSI", 0.545f, 0.56f, 8, 5, 3, L_STATION);
        s.mark("Portland Oregon Sign", 0.505f, 0.46f, 0, 0, 30, ST_SCREENS);
        s.ew("Lombard St", 0.15f);
        s.ew("Killingsworth St", 0.22f);
        s.ew("Alberta St", 0.27f);
        s.ew("Fremont St", 0.32f);
        s.ew("Broadway", 0.4f, 0.5f, 1);
        s.ew("Lovejoy St", 0.4f, 0, 0.5f);
        s.ew("Glisan St", 0.44f);
        s.ew("Burnside St", 0.47f);
        s.ew("Stark St", 0.49f);
        s.ew("Morrison St", 0.5f);
        s.ew("Belmont St", 0.52f);
        s.ew("Hawthorne Blvd", 0.55f);
        s.ew("Division St", 0.6f);
        s.ew("Powell Blvd", 0.65f);
        s.ew("Holgate Blvd", 0.7f);
        s.ew("Woodstock Blvd", 0.78f);
        s.ew("Tacoma St", 0.86f);
        s.ns("Skyline Blvd", 0.2f);
        s.ns("23rd Ave", 0.38f);
        s.ns("18th Ave", 0.41f);
        s.ns("Broadway", 0.455f, 0, 0.5f);
        s.ns("Naito Pkwy", 0.49f);
        s.ns("MLK Jr Blvd", 0.55f);
        s.ns("Grand Ave", 0.565f);
        s.ns("Williams Ave", 0.58f, 0, 0.45f);
        s.ns("12th Ave", 0.6f);
        s.ns("28th Ave", 0.66f);
        s.ns("César Chávez Blvd", 0.7f);
        s.ns("60th Ave", 0.76f);
        s.ns("82nd Ave", 0.84f);
        s.ns("122nd Ave", 0.95f);
    }

    // ------------------------------------------------------------------ Seattle

    private static void seattle(Spec s) {
        s.downX = 0.42f;
        s.downY = 0.5f;
        // Puget Sound and Elliott Bay to the west, West Seattle beyond the Duwamish.
        s.sea(0, 0, 0.22f, 0, 0.2f, 0.15f, 0.235f, 0.24f, 0.24f, 0.36f, 0.3f, 0.37f, 0.36f, 0.42f, 0.385f, 0.5f, 0.37f, 0.56f,
                0.33f, 0.6f, 0.26f, 0.62f, 0.2f, 0.6f, 0.15f, 0.62f, 0.12f, 0.7f, 0.1f, 0.82f, 0.12f, 1, 0, 1);
        s.river(7, 0.335f, 0.59f, 0.35f, 0.72f, 0.38f, 0.85f, 0.42f, 1);
        // Lake Washington to the east, Lake Union in the middle, the Ship Canal joining them to the Sound.
        s.sea(0.73f, 0, 1, 0, 1, 1, 0.79f, 1, 0.77f, 0.8f, 0.74f, 0.6f, 0.725f, 0.45f, 0.71f, 0.3f, 0.725f, 0.15f);
        s.lake(0.5f, 0.32f, 0.035f, 0.05f);
        s.river(6, 0.22f, 0.245f, 0.3f, 0.25f, 0.4f, 0.26f, 0.475f, 0.285f);
        s.river(6, 0.525f, 0.29f, 0.6f, 0.3f, 0.66f, 0.31f, 0.72f, 0.3f);
        s.lake(0.48f, 0.1f, 0.025f, 0.035f);
        s.beach(0.2f, 0.62f, 0.13f, 0.66f);
        s.beach(0.235f, 0.25f, 0.24f, 0.34f);
        s.park("Green Lake Park", Spec.rect(0.445f, 0.05f, 0.515f, 0.155f));
        s.wild("Discovery Park", 0.245f, 0.27f, 0.3f, 0.27f, 0.3f, 0.34f, 0.25f, 0.34f);
        s.park("Seattle Center", Spec.rect(0.395f, 0.38f, 0.425f, 0.415f));
        s.park("Volunteer Park", Spec.rect(0.545f, 0.39f, 0.565f, 0.41f));
        s.wild("Washington Park Arboretum", 0.62f, 0.33f, 0.68f, 0.33f, 0.68f, 0.45f, 0.63f, 0.45f);
        s.park("Gas Works Park", Spec.rect(0.49f, 0.265f, 0.51f, 0.28f));
        s.wild("Seward Park", 0.74f, 0.7f, 0.77f, 0.7f, 0.77f, 0.74f, 0.75f, 0.74f);
        s.park("Jefferson Park", Spec.rect(0.5f, 0.74f, 0.54f, 0.78f));
        s.wild("Lincoln Park", 0.1f, 0.8f, 0.14f, 0.8f, 0.14f, 0.86f, 0.11f, 0.86f);
        s.hill(0.38f, 0.33f, 0.05f, 30);
        s.hill(0.54f, 0.44f, 0.05f, 26);
        s.hill(0.5f, 0.66f, 0.05f, 22);
        s.hill(0.28f, 0.3f, 0.05f, 20);
        s.hill(0.2f, 0.76f, 0.08f, 28);
        s.hill(0.38f, 0.12f, 0.06f, 18);
        s.place("Downtown", 0.42f, 0.5f, DOWN);
        s.place("Belltown", 0.39f, 0.44f, MID);
        s.place("South Lake Union", 0.47f, 0.4f, DOWN);
        s.place("Capitol Hill", 0.54f, 0.45f, MID);
        s.place("First Hill", 0.47f, 0.53f, MID);
        s.place("Pioneer Square", 0.42f, 0.56f, OLD);
        s.place("International District", 0.46f, 0.58f, OLD);
        s.place("SoDo", 0.43f, 0.67f, IND);
        s.place("Queen Anne", 0.38f, 0.34f, SUB);
        s.place("Magnolia", 0.28f, 0.31f, SUB);
        s.place("Ballard", 0.3f, 0.18f, OLD);
        s.place("Fremont", 0.42f, 0.22f, OLD);
        s.place("Wallingford", 0.5f, 0.21f, SUB);
        s.place("University District", 0.62f, 0.22f, UNI);
        s.place("Green Lake", 0.48f, 0.07f, PARK);
        s.place("Ravenna", 0.62f, 0.1f, SUB);
        s.place("Montlake", 0.6f, 0.36f, SUB);
        s.place("Central District", 0.58f, 0.52f, SUB);
        s.place("Madison Park", 0.69f, 0.42f, SUB);
        s.place("Beacon Hill", 0.5f, 0.68f, SUB);
        s.place("Georgetown", 0.4f, 0.8f, IND);
        s.place("West Seattle", 0.2f, 0.76f, SUB);
        s.place("Columbia City", 0.62f, 0.75f, SUB);
        s.place("Rainier Valley", 0.6f, 0.86f, SUB);
        s.place("Northgate", 0.56f, 0.03f, MID);
        s.bridge("Aurora Bridge", 0.44f, 0.265f, true, ST_BRIDGE_ARCH);
        s.bridge("Fremont Bridge", 0.415f, 0.26f, true, 0);
        s.bridge("Ballard Bridge", 0.3f, 0.25f, true, 0);
        s.bridge("University Bridge", 0.54f, 0.293f, true, 0);
        s.bridge("Montlake Bridge", 0.6f, 0.3f, true, 0);
        s.bridge("West Seattle Bridge", 0.345f, 0.64f, false, 0);
        s.bridge("1st Ave S Bridge", 0.37f, 0.8f, false, 0);
        s.mark("Space Needle", 0.405f, 0.395f, 0, 0, 300, ST_NEEDLE);
        s.mark("Pike Place Market", 0.395f, 0.488f, 10, 3, 3, L_MARKET);
        s.mark("Columbia Center", 0.43f, 0.52f, 5, 5, 32, L_DARK);
        s.mark("Smith Tower", 0.425f, 0.553f, 3, 3, 16, L_CROWN);
        s.mark("Seattle Central Library", 0.425f, 0.505f, 5, 4, 5, L_LIBRARY);
        s.mark("Lumen Field", 0.425f, 0.615f, 16, 14, 0, L_STADIUM);
        s.mark("T-Mobile Park", 0.425f, 0.66f, 14, 12, 0, L_STADIUM);
        s.mark("Climate Pledge Arena", 0.405f, 0.37f, 8, 8, 3, L_ARENA);
        s.mark("Museum of Pop Culture", 0.415f, 0.405f, 5, 4, 3, L_BLOB);
        s.ew("N 85th St", 0.04f);
        s.ew("N 65th St", 0.13f);
        s.ew("N 45th St", 0.19f);
        s.ew("Market St", 0.215f, 0, 0.4f);
        s.ew("Leary Way", 0.235f, 0, 0.45f);
        s.ew("Nickerson St", 0.27f, 0.3f, 0.48f);
        s.ew("Mercer St", 0.37f);
        s.ew("Denny Way", 0.42f);
        s.ew("Bell St", 0.45f);
        s.ew("Pine St", 0.48f);
        s.ew("Pike St", 0.49f);
        s.ew("Madison St", 0.52f);
        s.ew("Yesler Way", 0.555f);
        s.ew("Jackson St", 0.575f);
        s.ew("Royal Brougham Way", 0.63f);
        s.ew("Holgate St", 0.67f);
        s.ew("Spokane St", 0.64f, 0, 0.35f);
        s.ew("Columbian Way", 0.72f);
        s.ew("Orcas St", 0.79f);
        s.ew("Othello St", 0.87f);
        s.ew("Henderson St", 0.94f);
        s.ns("24th Ave NW", 0.28f, 0, 0.3f);
        s.ns("15th Ave NW", 0.32f, 0, 0.3f);
        s.ns("California Ave SW", 0.2f, 0.6f, 1);
        s.ns("Elliott Ave", 0.37f, 0.35f, 0.5f);
        s.ns("1st Ave", 0.395f);
        s.ns("3rd Ave", 0.41f);
        s.ns("5th Ave", 0.425f);
        s.ns("Aurora Ave N", 0.44f, 0, 0.42f);
        s.ns("Westlake Ave", 0.46f, 0.3f, 0.48f);
        s.ns("Eastlake Ave", 0.53f, 0.3f, 0.45f);
        s.ns("Broadway", 0.53f, 0.42f, 0.6f);
        s.ns("12th Ave", 0.55f);
        s.ns("15th Ave", 0.575f);
        s.ns("23rd Ave", 0.6f);
        s.ns("Rainier Ave S", 0.6f, 0.6f, 1);
        s.ns("Beacon Ave S", 0.51f, 0.6f, 1);
        s.ns("MLK Jr Way", 0.64f, 0.45f, 1);
        s.ns("Roosevelt Way", 0.56f, 0, 0.3f);
        s.ns("University Way", 0.62f, 0, 0.3f);
        s.ns("35th Ave NE", 0.68f, 0, 0.3f);
    }

    // ------------------------------------------------------------------ New York

    private static void newYork(Spec s) {
        s.downX = 0.53f;
        s.downY = 0.56f;
        // The land: New Jersey, Manhattan, the Bronx, and Brooklyn and Queens; the rivers and the bay between.
        s.land(0, 0, 0.3f, 0, 0.32f, 0.3f, 0.34f, 0.55f, 0.36f, 0.72f, 0.35f, 0.85f, 0.32f, 0.93f, 0.3f, 1, 0, 1);
        s.land(0.47f, 0, 0.55f, 0, 0.58f, 0.08f, 0.6f, 0.2f, 0.62f, 0.32f, 0.63f, 0.45f, 0.62f, 0.55f, 0.6f, 0.62f, 0.615f, 0.72f,
                0.6f, 0.8f, 0.565f, 0.88f, 0.525f, 0.95f, 0.485f, 0.94f, 0.465f, 0.88f, 0.45f, 0.75f, 0.44f, 0.6f, 0.43f, 0.45f, 0.43f, 0.3f, 0.44f, 0.15f);
        s.land(0.62f, 0, 1, 0, 1, 0.16f, 0.85f, 0.19f, 0.72f, 0.19f, 0.66f, 0.17f, 0.63f, 0.08f);
        s.land(0.68f, 0.25f, 1, 0.22f, 1, 1, 0.62f, 1, 0.605f, 0.94f, 0.625f, 0.88f, 0.66f, 0.8f, 0.67f, 0.7f, 0.67f, 0.6f, 0.68f, 0.5f,
                0.68f, 0.4f, 0.67f, 0.32f);
        s.island(0.374f, 0.963f, 0.396f, 0.958f, 0.401f, 0.978f, 0.38f, 0.99f);
        s.park("Central Park", Spec.rect(0.5f, 0.22f, 0.556f, 0.42f));
        s.park("Prospect Park", Spec.rect(0.78f, 0.86f, 0.84f, 0.94f));
        s.park("Battery Park", 0.485f, 0.925f, 0.51f, 0.92f, 0.52f, 0.945f, 0.49f, 0.945f);
        s.park("Washington Square Park", Spec.rect(0.5f, 0.705f, 0.512f, 0.715f));
        s.park("Bryant Park", Spec.rect(0.528f, 0.55f, 0.54f, 0.557f));
        s.park("Riverside Park", Spec.rect(0.432f, 0.2f, 0.445f, 0.4f));
        s.park("Liberty State Park", Spec.rect(0.25f, 0.86f, 0.32f, 0.92f));
        s.park("McCarren Park", Spec.rect(0.74f, 0.66f, 0.76f, 0.68f));
        s.park("Flushing Meadows", Spec.rect(0.92f, 0.38f, 1, 0.5f));
        s.lake(0.527f, 0.29f, 0.017f, 0.018f);
        s.lake(0.52f, 0.34f, 0.012f, 0.006f);
        s.lake(0.54f, 0.228f, 0.008f, 0.004f);
        s.lake(0.81f, 0.92f, 0.01f, 0.008f);
        s.hill(0.5f, 0.05f, 0.05f, 16);
        s.hill(0.3f, 0.15f, 0.04f, 18);
        s.hill(0.8f, 0.08f, 0.1f, 10);
        s.place("Midtown", 0.53f, 0.54f, DOWN);
        s.place("Financial District", 0.5f, 0.9f, DOWN);
        s.place("Upper West Side", 0.47f, 0.31f, MID);
        s.place("Upper East Side", 0.59f, 0.32f, MID);
        s.place("Harlem", 0.54f, 0.15f, OLD);
        s.place("Washington Heights", 0.5f, 0.05f, SUB);
        s.place("Hell's Kitchen", 0.46f, 0.53f, MID);
        s.place("Chelsea", 0.47f, 0.63f, MID);
        s.place("Murray Hill", 0.58f, 0.58f, MID);
        s.place("Greenwich Village", 0.49f, 0.71f, OLD);
        s.place("East Village", 0.57f, 0.71f, OLD);
        s.place("SoHo", 0.505f, 0.77f, OLD);
        s.place("Tribeca", 0.475f, 0.82f, MID);
        s.place("Chinatown", 0.54f, 0.8f, OLD);
        s.place("Lower East Side", 0.58f, 0.77f, OLD);
        s.place("Hoboken", 0.31f, 0.5f, MID);
        s.place("Jersey City", 0.28f, 0.74f, DOWN);
        s.place("Weehawken", 0.27f, 0.36f, SUB);
        s.place("Union City", 0.2f, 0.4f, SUB);
        s.place("Long Island City", 0.72f, 0.45f, IND);
        s.place("Astoria", 0.75f, 0.31f, SUB);
        s.place("Greenpoint", 0.74f, 0.6f, SUB);
        s.place("Williamsburg", 0.72f, 0.72f, OLD);
        s.place("DUMBO", 0.66f, 0.85f, IND);
        s.place("Brooklyn Heights", 0.65f, 0.9f, OLD);
        s.place("Downtown Brooklyn", 0.71f, 0.88f, DOWN);
        s.place("Park Slope", 0.76f, 0.93f, SUB);
        s.place("Bushwick", 0.83f, 0.74f, IND);
        s.place("Bedford-Stuyvesant", 0.85f, 0.86f, SUB);
        s.place("Jackson Heights", 0.88f, 0.4f, SUB);
        s.place("Mott Haven", 0.68f, 0.13f, SUB);
        s.place("The Bronx", 0.82f, 0.08f, SUB);
        s.bridge("George Washington Bridge", 0.38f, 0.07f, false, ST_BRIDGE_TOWERS);
        s.bridge("Willis Avenue Bridge", 0.605f, 0.12f, false, 0);
        s.bridge("RFK Bridge", 0.7f, 0.22f, true, 0);
        s.bridge("Queensboro Bridge", 0.65f, 0.45f, false, ST_BRIDGE_TOWERS);
        s.bridge("Williamsburg Bridge", 0.64f, 0.74f, false, ST_BRIDGE_TOWERS);
        s.bridge("Manhattan Bridge", 0.615f, 0.825f, false, ST_BRIDGE_TOWERS);
        s.bridge("Brooklyn Bridge", 0.6f, 0.86f, false, ST_BRIDGE_TOWERS);
        s.mark("Empire State Building", 0.545f, 0.585f, 5, 5, 34, L_STEPPED);
        s.mark("Chrysler Building", 0.565f, 0.56f, 4, 4, 28, L_CROWN);
        s.mark("One World Trade Center", 0.49f, 0.865f, 6, 6, 38, L_OCTAGON);
        s.mark("30 Rockefeller Plaza", 0.535f, 0.535f, 6, 4, 26, L_SLAB);
        s.mark("Grand Central Terminal", 0.56f, 0.57f, 8, 5, 4, L_CLASSIC);
        s.mark("Times Square", 0.515f, 0.55f, 0, 0, 60, ST_SCREENS);
        s.mark("Madison Square Garden", 0.5f, 0.6f, 9, 9, 5, L_ARENA);
        s.mark("Flatiron Building", 0.53f, 0.645f, 3, 4, 10, L_WEDGE);
        s.mark("Statue of Liberty", 0.388f, 0.975f, 0, 0, 110, ST_LIBERTY);
        s.mark("Yankee Stadium", 0.66f, 0.1f, 16, 14, 0, L_STADIUM);
        s.mark("Barclays Center", 0.73f, 0.9f, 9, 9, 4, L_ARENA);
        s.mark("Metropolitan Museum of Art", 0.565f, 0.3f, 8, 5, 4, L_CLASSIC);
        s.mark("Guggenheim Museum", 0.565f, 0.26f, 4, 4, 5, L_SPIRAL);
        s.mark("St. Patrick's Cathedral", 0.548f, 0.528f, 5, 3, 6, L_CATHEDRAL);
        // Manhattan's streets are numbered north from Houston Street; the avenues run west to east.
        float[][] anchors = {{1, 0.765f}, {14, 0.67f}, {34, 0.585f}, {42, 0.55f}, {59, 0.43f}, {72, 0.36f}, {86, 0.3f},
                {96, 0.27f}, {110, 0.215f}, {125, 0.17f}, {155, 0.085f}, {190, 0.01f}};
        for (int a = 0; a + 1 < anchors.length; a++)
            for (int n = (int) anchors[a][0]; n < anchors[a + 1][0]; n += (n < 14 ? 2 : n < 60 ? 3 : 4)) {
                float f = (n - anchors[a][0]) / (anchors[a + 1][0] - anchors[a][0]);
                float y = anchors[a][1] + (anchors[a + 1][1] - anchors[a][1]) * f;
                String num = n + (n % 100 >= 11 && n % 100 <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th");
                s.ew("W " + num + " St", y, 0.43f, 0.525f);
                s.ew("E " + num + " St", y, 0.525f, 0.64f);
            }
        s.ew("Houston St", 0.765f, 0.43f, 0.64f);
        s.ew("Canal St", 0.8f, 0.43f, 0.64f);
        s.ew("Chambers St", 0.84f, 0.43f, 0.64f);
        s.ew("Fulton St", 0.87f, 0.43f, 0.64f);
        s.ew("Wall St", 0.9f, 0.43f, 0.64f);
        s.ew("Delancey St", 0.78f, 0.55f, 0.64f);
        s.ew("Atlantic Ave", 0.9f, 0.64f, 1);
        s.ew("Myrtle Ave", 0.84f, 0.64f, 1);
        s.ew("Metropolitan Ave", 0.72f, 0.64f, 1);
        s.ew("Northern Blvd", 0.38f, 0.64f, 1);
        s.ew("Queens Blvd", 0.47f, 0.64f, 1);
        s.ew("Fordham Rd", 0.06f, 0.6f, 1);
        s.ew("Bruckner Blvd", 0.15f, 0.6f, 1);
        s.ew("Newark Ave", 0.72f, 0, 0.37f);
        s.ew("Washington St", 0.5f, 0, 0.37f);
        s.ew("Bergenline Ave", 0.35f, 0, 0.37f);
        s.ns("12th Ave", 0.435f, 0, 0.8f);
        s.ns("10th Ave", 0.455f, 0, 0.8f);
        s.ns("Amsterdam Ave", 0.47f, 0.1f, 0.5f);
        s.ns("8th Ave", 0.485f, 0.4f, 0.8f);
        s.ns("Broadway", 0.5f);
        s.ns("6th Ave", 0.515f, 0.42f, 0.8f);
        s.ns("5th Ave", 0.525f);
        s.ns("Madison Ave", 0.535f);
        s.ns("Park Ave", 0.545f);
        s.ns("Lexington Ave", 0.555f);
        s.ns("3rd Ave", 0.57f);
        s.ns("2nd Ave", 0.585f);
        s.ns("1st Ave", 0.6f);
        s.ns("York Ave", 0.612f, 0.2f, 0.6f);
        s.ns("Kennedy Blvd", 0.2f);
        s.ns("Palisade Ave", 0.28f);
        s.ns("Hudson St", 0.33f, 0.4f, 1);
        s.ns("Flatbush Ave", 0.74f, 0.75f, 1);
        s.ns("Bedford Ave", 0.76f);
        s.ns("Nostrand Ave", 0.82f, 0.5f, 1);
        s.ns("Grand Concourse", 0.7f, 0, 0.2f);
        s.ns("Steinway St", 0.78f, 0.2f, 0.5f);
    }

    // ------------------------------------------------------------------ Sydney

    private static void sydney(Spec s) {
        s.downX = 0.48f;
        s.downY = 0.47f;
        s.height = 2;
        // The harbour from the Heads in through the middle; the city on the south shore, the North Shore across it.
        s.land(0, 0.48f, 0.18f, 0.47f, 0.3f, 0.44f, 0.36f, 0.4f, 0.39f, 0.43f, 0.42f, 0.42f, 0.43f, 0.5f, 0.445f, 0.5f, 0.45f, 0.41f,
                0.48f, 0.395f, 0.495f, 0.42f, 0.5f, 0.385f, 0.51f, 0.39f, 0.52f, 0.42f, 0.54f, 0.4f, 0.56f, 0.44f, 0.6f, 0.42f, 0.66f, 0.45f,
                0.72f, 0.42f, 0.78f, 0.44f, 0.84f, 0.4f, 0.88f, 0.34f, 0.9f, 0.36f, 0.9f, 0.5f, 0.92f, 0.6f, 0.9f, 0.66f, 0.92f, 0.72f,
                0.93f, 0.85f, 0.94f, 1, 0, 1);
        s.land(0, 0, 0.95f, 0, 0.95f, 0.15f, 0.92f, 0.2f, 0.88f, 0.24f, 0.86f, 0.3f, 0.84f, 0.31f, 0.8f, 0.28f, 0.74f, 0.3f,
                0.725f, 0.2f, 0.71f, 0.1f, 0.695f, 0.2f, 0.68f, 0.33f, 0.62f, 0.32f, 0.56f, 0.35f, 0.5f, 0.33f, 0.47f, 0.34f, 0.44f, 0.33f,
                0.38f, 0.34f, 0.3f, 0.36f, 0.2f, 0.38f, 0.1f, 0.4f, 0, 0.4f);
        s.beach(0.9f, 0.61f, 0.905f, 0.66f);
        s.beach(0.92f, 0.78f, 0.925f, 0.83f);
        s.beach(0.93f, 0.12f, 0.95f, 0.19f);
        s.park("Royal Botanic Garden", 0.505f, 0.42f, 0.52f, 0.425f, 0.535f, 0.41f, 0.545f, 0.44f, 0.54f, 0.455f, 0.51f, 0.455f);
        s.park("The Domain", Spec.rect(0.52f, 0.455f, 0.54f, 0.48f));
        s.park("Hyde Park", Spec.rect(0.49f, 0.47f, 0.5f, 0.52f));
        s.park("Centennial Park", Spec.rect(0.62f, 0.62f, 0.7f, 0.7f));
        s.park("Moore Park", Spec.rect(0.57f, 0.6f, 0.61f, 0.66f));
        s.park("Victoria Park", Spec.rect(0.42f, 0.56f, 0.44f, 0.58f));
        s.wild("Sydney Harbour National Park", 0.86f, 0.35f, 0.895f, 0.37f, 0.895f, 0.45f, 0.87f, 0.43f);
        s.wild("North Head", 0.85f, 0.26f, 0.9f, 0.25f, 0.9f, 0.3f, 0.86f, 0.3f);
        s.wild("Lane Cove National Park", 0.08f, 0.04f, 0.3f, 0.04f, 0.3f, 0.24f, 0.08f, 0.26f);
        s.wild("Bradleys Head", 0.6f, 0.3f, 0.64f, 0.3f, 0.64f, 0.33f, 0.6f, 0.33f);
        s.lake(0.66f, 0.66f, 0.008f, 0.012f);
        s.hill(0.4f, 0.15f, 0.2f, 22);
        s.hill(0.78f, 0.56f, 0.1f, 16);
        s.hill(0.88f, 0.28f, 0.03f, 18);
        s.place("Sydney CBD", 0.48f, 0.47f, DOWN);
        s.place("The Rocks", 0.47f, 0.415f, OLD);
        s.place("Circular Quay", 0.5f, 0.43f, MID);
        s.place("Barangaroo", 0.455f, 0.43f, DOWN);
        s.place("Darling Harbour", 0.44f, 0.47f, MID);
        s.place("Pyrmont", 0.41f, 0.45f, MID);
        s.place("Ultimo", 0.44f, 0.53f, UNI);
        s.place("Haymarket", 0.47f, 0.54f, OLD);
        s.place("Surry Hills", 0.52f, 0.56f, OLD);
        s.place("Darlinghurst", 0.54f, 0.5f, OLD);
        s.place("Kings Cross", 0.56f, 0.47f, MID);
        s.place("Paddington", 0.6f, 0.54f, OLD);
        s.place("Woollahra", 0.68f, 0.52f, SUB);
        s.place("Double Bay", 0.7f, 0.465f, SUB);
        s.place("Bondi", 0.86f, 0.63f, SUB);
        s.place("Coogee", 0.88f, 0.8f, SUB);
        s.place("Randwick", 0.76f, 0.75f, SUB);
        s.place("Redfern", 0.5f, 0.63f, SUB);
        s.place("Newtown", 0.42f, 0.66f, OLD);
        s.place("Glebe", 0.4f, 0.55f, OLD);
        s.place("Balmain", 0.33f, 0.43f, OLD);
        s.place("Leichhardt", 0.3f, 0.56f, SUB);
        s.place("Alexandria", 0.5f, 0.76f, IND);
        s.place("Mascot", 0.55f, 0.9f, IND);
        s.place("North Sydney", 0.46f, 0.27f, DOWN);
        s.place("Kirribilli", 0.485f, 0.315f, SUB);
        s.place("Neutral Bay", 0.53f, 0.28f, SUB);
        s.place("Mosman", 0.62f, 0.25f, SUB);
        s.place("Manly", 0.9f, 0.16f, SUB);
        s.place("Chatswood", 0.4f, 0.08f, MID);
        s.place("Lane Cove", 0.25f, 0.3f, SUB);
        s.bridge("Sydney Harbour Bridge", 0.462f, 0.37f, true, ST_BRIDGE_ARCH);
        s.bridge("Gladesville Bridge", 0.12f, 0.44f, true, ST_BRIDGE_ARCH);
        s.bridge("Spit Bridge", 0.71f, 0.22f, false, 0);
        s.mark("Sydney Opera House", 0.505f, 0.395f, 8, 6, 5, L_SAILS);
        s.mark("Sydney Tower Eye", 0.485f, 0.48f, 0, 0, 380, ST_SYDNEY_TOWER);
        s.mark("Queen Victoria Building", 0.474f, 0.49f, 3, 9, 4, L_GOLD_DOME);
        s.mark("Sydney Cricket Ground", 0.6f, 0.63f, 16, 14, 0, L_STADIUM);
        s.mark("Luna Park", 0.452f, 0.335f, 0, 0, 60, ST_FERRIS);
        s.mark("Crown Sydney", 0.452f, 0.435f, 5, 4, 30, L_SAIL);
        s.mark("Central Station", 0.474f, 0.55f, 9, 5, 4, L_CLOCK);
        s.mark("St Mary's Cathedral", 0.51f, 0.48f, 5, 3, 6, L_CATHEDRAL);
        s.ew("Military Rd", 0.27f, 0.45f, 0.8f);
        s.ew("Falcon St", 0.3f, 0.4f, 0.6f);
        s.ew("Victoria Rd", 0.43f, 0, 0.36f);
        s.ew("Bridge St", 0.43f, 0.45f, 0.52f);
        s.ew("Market St", 0.48f, 0.44f, 0.5f);
        s.ew("William St", 0.48f, 0.5f, 0.6f);
        s.ew("Oxford St", 0.52f, 0.5f, 0.72f);
        s.ew("Parramatta Rd", 0.55f, 0, 0.45f);
        s.ew("Cleveland St", 0.6f, 0.4f, 0.6f);
        s.ew("Bondi Rd", 0.6f, 0.72f, 0.9f);
        s.ew("King St", 0.65f, 0.35f, 0.5f);
        s.ew("New South Head Rd", 0.46f, 0.6f, 0.9f);
        s.ew("Alison Rd", 0.7f, 0.6f, 0.9f);
        s.ew("Gardeners Rd", 0.85f, 0.4f, 0.8f);
        s.ns("Pacific Hwy", 0.43f, 0, 0.36f);
        s.ns("Miller St", 0.47f, 0, 0.33f);
        s.ns("Harris St", 0.42f, 0.42f, 0.6f);
        s.ns("Sussex St", 0.455f, 0.42f, 0.56f);
        s.ns("George St", 0.47f, 0.4f, 0.6f);
        s.ns("Pitt St", 0.48f, 0.4f, 0.56f);
        s.ns("Elizabeth St", 0.495f, 0.43f, 0.7f);
        s.ns("Macquarie St", 0.505f, 0.4f, 0.47f);
        s.ns("Crown St", 0.53f, 0.45f, 0.65f);
        s.ns("Bourke St", 0.545f, 0.45f, 0.7f);
        s.ns("Anzac Pde", 0.6f, 0.6f, 1);
        s.ns("Avoca St", 0.75f, 0.6f, 1);
        s.ns("Campbell Pde", 0.88f, 0.5f, 0.7f);
        s.ns("Botany Rd", 0.52f, 0.7f, 1);
        s.ns("Pittwater Rd", 0.9f, 0, 0.25f);
    }

    // ------------------------------------------------------------------ Tokyo

    private static void tokyo(Spec s) {
        s.downX = 0.52f;
        s.downY = 0.48f;
        // Tokyo Bay to the south-east, Odaiba out in it; the Sumida and the Arakawa running down to it.
        s.sea(0.4f, 1, 0.47f, 0.9f, 0.55f, 0.84f, 0.65f, 0.8f, 0.75f, 0.78f, 0.85f, 0.72f, 1, 0.68f, 1, 1);
        s.island(0.565f, 0.885f, 0.66f, 0.875f, 0.665f, 0.935f, 0.575f, 0.945f);
        s.beach(0.58f, 0.935f, 0.63f, 0.945f);
        s.river(10, 0.62f, 0, 0.66f, 0.15f, 0.67f, 0.3f, 0.66f, 0.45f, 0.645f, 0.6f, 0.625f, 0.72f, 0.6f, 0.83f);
        s.river(14, 0.8f, 0, 0.83f, 0.2f, 0.86f, 0.4f, 0.88f, 0.55f, 0.9f, 0.72f);
        s.river(4, 0.25f, 0.38f, 0.4f, 0.39f, 0.5f, 0.405f, 0.58f, 0.42f, 0.645f, 0.44f);
        s.moats.add(new float[]{0.455f, 0.395f, 0.535f, 0.505f});
        s.park("Imperial Palace", Spec.rect(0.462f, 0.402f, 0.528f, 0.498f));
        s.wild("Meiji Jingu", Spec.rect(0.18f, 0.49f, 0.24f, 0.56f));
        s.park("Yoyogi Park", Spec.rect(0.2f, 0.56f, 0.25f, 0.6f));
        s.park("Shinjuku Gyoen", Spec.rect(0.27f, 0.48f, 0.32f, 0.53f));
        s.park("Ueno Park", Spec.rect(0.575f, 0.24f, 0.62f, 0.3f));
        s.park("Hibiya Park", Spec.rect(0.5f, 0.51f, 0.515f, 0.535f));
        s.park("Hama-rikyu Gardens", Spec.rect(0.54f, 0.66f, 0.57f, 0.7f));
        s.park("Shiba Park", Spec.rect(0.44f, 0.63f, 0.465f, 0.67f));
        s.park("Rikugien", Spec.rect(0.45f, 0.17f, 0.48f, 0.2f));
        s.park("Kiyosumi Gardens", Spec.rect(0.68f, 0.55f, 0.7f, 0.57f));
        s.lake(0.59f, 0.285f, 0.012f, 0.01f);
        s.lake(0.295f, 0.505f, 0.008f, 0.004f);
        s.hill(0.3f, 0.5f, 0.22f, 14);
        s.hill(0.42f, 0.6f, 0.05f, 10);
        s.place("Marunouchi", 0.535f, 0.47f, DOWN);
        s.place("Ginza", 0.555f, 0.56f, MID);
        s.place("Nihonbashi", 0.57f, 0.47f, MID);
        s.place("Shinjuku", 0.24f, 0.44f, DOWN);
        s.place("Shibuya", 0.27f, 0.63f, MID);
        s.place("Harajuku", 0.25f, 0.57f, MID);
        s.place("Roppongi", 0.38f, 0.62f, MID);
        s.place("Akasaka", 0.42f, 0.54f, MID);
        s.place("Akihabara", 0.585f, 0.38f, MID);
        s.place("Ueno", 0.6f, 0.31f, OLD);
        s.place("Asakusa", 0.665f, 0.26f, OLD);
        s.place("Ikebukuro", 0.32f, 0.2f, DOWN);
        s.place("Shinagawa", 0.42f, 0.83f, MID);
        s.place("Odaiba", 0.615f, 0.905f, MID);
        s.place("Tsukiji", 0.58f, 0.64f, OLD);
        s.place("Ryogoku", 0.69f, 0.44f, OLD);
        s.place("Kinshicho", 0.75f, 0.5f, MID);
        s.place("Oshiage", 0.72f, 0.33f, MID);
        s.place("Toyosu", 0.68f, 0.76f, IND);
        s.place("Koto", 0.78f, 0.64f, IND);
        s.place("Meguro", 0.3f, 0.76f, SUB);
        s.place("Ebisu", 0.32f, 0.68f, SUB);
        s.place("Nakano", 0.1f, 0.42f, SUB);
        s.place("Setagaya", 0.12f, 0.7f, SUB);
        s.place("Hongo", 0.5f, 0.3f, UNI);
        s.place("Kita", 0.48f, 0.07f, SUB);
        s.place("Adachi", 0.75f, 0.1f, SUB);
        s.place("Edogawa", 0.95f, 0.5f, SUB);
        s.bridge("Rainbow Bridge", 0.6f, 0.86f, true, ST_BRIDGE_TOWERS);
        s.bridge("Kachidoki Bridge", 0.632f, 0.68f, false, 0);
        s.bridge("Eitai Bridge", 0.653f, 0.52f, false, 0);
        s.bridge("Ryogoku Bridge", 0.662f, 0.42f, false, 0);
        s.bridge("Azuma Bridge", 0.668f, 0.27f, false, 0);
        s.mark("Tokyo Tower", 0.455f, 0.655f, 0, 0, 380, ST_TOKYO_TOWER);
        s.mark("Tokyo Skytree", 0.72f, 0.32f, 0, 0, 520, ST_SKYTREE);
        s.mark("Senso-ji", 0.665f, 0.28f, 8, 6, 3, L_TEMPLE);
        s.mark("Imperial Palace", 0.495f, 0.45f, 10, 7, 2, L_PALACE);
        s.mark("Tokyo Station", 0.538f, 0.48f, 14, 4, 3, L_BRICK);
        s.mark("Shibuya Crossing", 0.27f, 0.62f, 0, 0, 60, ST_SCREENS);
        s.mark("Meiji Shrine", 0.21f, 0.52f, 6, 5, 2, L_SHRINE);
        s.mark("Tokyo Metropolitan Government Building", 0.22f, 0.43f, 8, 5, 32, L_TWIN);
        s.mark("Roppongi Hills Mori Tower", 0.385f, 0.615f, 5, 5, 30, L_ROUND);
        s.mark("Tokyo Dome", 0.5f, 0.33f, 10, 10, 4, L_DOME_STADIUM);
        s.mark("Kabuki-za", 0.57f, 0.58f, 5, 4, 3, L_THEATRE);
        s.mark("Ferris wheel", 0.635f, 0.9f, 0, 0, 70, ST_FERRIS);
        s.mark("Fuji TV Building", 0.6f, 0.9f, 4, 4, 18, L_CUBE_ARCH);
        s.ew("Kan-nana-dori", 0.08f);
        s.ew("Kasuga-dori", 0.3f);
        s.ew("Kuramae-bashi-dori", 0.35f, 0.55f, 0.8f);
        s.ew("Yasukuni-dori", 0.41f);
        s.ew("Shinjuku-dori", 0.45f, 0, 0.46f);
        s.ew("Eitai-dori", 0.49f, 0.53f, 0.8f);
        s.ew("Harumi-dori", 0.55f, 0.5f, 0.75f);
        s.ew("Aoyama-dori", 0.58f, 0.25f, 0.45f);
        s.ew("Roppongi-dori", 0.62f, 0.25f, 0.45f);
        s.ew("Sakurada-dori", 0.7f);
        s.ew("Meguro-dori", 0.77f, 0, 0.45f);
        s.ew("Keihin Highway", 0.82f, 0.3f, 0.5f);
        s.ns("Kan-pachi-dori", 0.08f);
        s.ns("Yamate-dori", 0.19f);
        s.ns("Meiji-dori", 0.26f);
        s.ns("Gaien-nishi-dori", 0.34f);
        s.ns("Gaien-higashi-dori", 0.4f);
        s.ns("Hakusan-dori", 0.48f, 0, 0.4f);
        s.ns("Hibiya-dori", 0.5f, 0.5f, 1);
        s.ns("Chuo-dori", 0.56f);
        s.ns("Showa-dori", 0.585f);
        s.ns("Shin-ohashi-dori", 0.62f, 0.4f, 0.7f);
        s.ns("Kiyosumi-dori", 0.7f);
        s.ns("Mitsume-dori", 0.75f);
        s.ns("Kuramae-dori", 0.94f);
    }

    // ------------------------------------------------------------------ Paris

    private static void paris(Spec s) {
        s.downX = 0.47f;
        s.downY = 0.44f;
        s.height = 1;
        s.maxFloors = 8;
        // The Seine, in from the east and out in a great loop round the Bois de Boulogne; the two islands.
        s.river(9, 1, 0.66f, 0.9f, 0.64f, 0.8f, 0.6f, 0.7f, 0.56f, 0.62f, 0.54f, 0.56f, 0.52f, 0.5f, 0.5f, 0.44f, 0.485f, 0.38f, 0.49f,
                0.32f, 0.52f, 0.28f, 0.56f, 0.24f, 0.62f, 0.2f, 0.68f, 0.16f, 0.66f, 0.12f, 0.58f, 0.1f, 0.48f, 0.08f, 0.4f, 0.05f, 0.35f, 0, 0.33f);
        s.lake(0.575f, 0.525f, 0.048f, 0.022f);
        s.island(0.532f, 0.517f, 0.57f, 0.509f, 0.578f, 0.525f, 0.54f, 0.534f);
        s.island(0.586f, 0.524f, 0.613f, 0.527f, 0.611f, 0.541f, 0.586f, 0.536f);
        s.river(3, 0.665f, 0.24f, 0.655f, 0.34f, 0.645f, 0.44f, 0.635f, 0.535f);
        s.wild("Bois de Boulogne", 0.02f, 0.4f, 0.09f, 0.39f, 0.12f, 0.5f, 0.15f, 0.62f, 0.1f, 0.66f, 0.03f, 0.6f);
        s.wild("Bois de Vincennes", 0.86f, 0.66f, 0.97f, 0.63f, 0.98f, 0.82f, 0.88f, 0.84f);
        s.park("Jardin des Tuileries", Spec.rect(0.41f, 0.465f, 0.455f, 0.48f));
        s.park("Champ de Mars", 0.29f, 0.555f, 0.31f, 0.545f, 0.355f, 0.6f, 0.335f, 0.612f);
        s.park("Trocadéro", Spec.rect(0.265f, 0.51f, 0.29f, 0.53f));
        s.park("Jardin du Luxembourg", Spec.rect(0.5f, 0.6f, 0.53f, 0.635f));
        s.park("Parc des Buttes-Chaumont", Spec.rect(0.73f, 0.29f, 0.77f, 0.33f));
        s.park("Parc Monceau", Spec.rect(0.375f, 0.32f, 0.395f, 0.34f));
        s.park("Jardin des Plantes", Spec.rect(0.6f, 0.565f, 0.63f, 0.59f));
        s.park("Parc de la Villette", Spec.rect(0.73f, 0.14f, 0.78f, 0.19f));
        s.park("Champs-Élysées Gardens", Spec.rect(0.36f, 0.45f, 0.405f, 0.465f));
        s.cemetery("Père Lachaise", Spec.rect(0.76f, 0.42f, 0.8f, 0.46f));
        s.cemetery("Montparnasse Cemetery", Spec.rect(0.46f, 0.68f, 0.48f, 0.7f));
        s.lake(0.515f, 0.62f, 0.006f, 0.004f);
        s.lake(0.75f, 0.31f, 0.008f, 0.006f);
        s.lake(0.07f, 0.48f, 0.01f, 0.02f);
        s.lake(0.92f, 0.74f, 0.015f, 0.01f);
        s.hill(0.5f, 0.2f, 0.05f, 30);
        s.hill(0.74f, 0.34f, 0.07f, 20);
        s.hill(0.27f, 0.5f, 0.03f, 10);
        s.hill(0.55f, 0.6f, 0.04f, 8);
        s.place("Opéra", 0.47f, 0.4f, DOWN);
        s.place("Les Halles", 0.53f, 0.46f, MID);
        s.place("Le Marais", 0.6f, 0.47f, OLD);
        s.place("Île de la Cité", 0.555f, 0.52f, OLD);
        s.place("Latin Quarter", 0.555f, 0.58f, UNI);
        s.place("Saint-Germain-des-Prés", 0.48f, 0.55f, OLD);
        s.place("Montparnasse", 0.45f, 0.66f, MID);
        s.place("Champs-Élysées", 0.32f, 0.42f, MID);
        s.place("Invalides", 0.38f, 0.55f, MID);
        s.place("Montmartre", 0.5f, 0.2f, OLD);
        s.place("Pigalle", 0.5f, 0.27f, OLD);
        s.place("Belleville", 0.73f, 0.36f, SUB);
        s.place("Bastille", 0.65f, 0.51f, OLD);
        s.place("Canal Saint-Martin", 0.64f, 0.37f, OLD);
        s.place("Gare du Nord", 0.56f, 0.31f, MID);
        s.place("Bercy", 0.72f, 0.62f, MID);
        s.place("Place d'Italie", 0.6f, 0.7f, MID);
        s.place("Passy", 0.21f, 0.52f, SUB);
        s.place("Grenelle", 0.3f, 0.64f, SUB);
        s.place("Batignolles", 0.38f, 0.24f, SUB);
        s.place("La Défense", 0.04f, 0.26f, DOWN);
        s.place("Neuilly-sur-Seine", 0.15f, 0.33f, SUB);
        s.place("Boulogne-Billancourt", 0.12f, 0.8f, SUB);
        s.place("Clichy", 0.4f, 0.12f, SUB);
        s.place("Saint-Denis", 0.56f, 0.05f, IND);
        s.place("Ivry-sur-Seine", 0.7f, 0.86f, IND);
        s.place("Vincennes", 0.9f, 0.55f, SUB);
        s.bridge("Pont Neuf", 0.545f, 0.505f, true, 0);
        s.bridge("Pont Neuf", 0.545f, 0.545f, true, 0);
        s.bridge("Pont Notre-Dame", 0.562f, 0.505f, true, 0);
        s.bridge("Petit Pont", 0.562f, 0.545f, true, 0);
        s.bridge("Pont Marie", 0.598f, 0.512f, true, 0);
        s.bridge("Pont de la Tournelle", 0.598f, 0.55f, true, 0);
        s.bridge("Pont Alexandre III", 0.39f, 0.49f, true, 0);
        s.bridge("Pont d'Iéna", 0.285f, 0.535f, true, 0);
        s.bridge("Pont de la Concorde", 0.405f, 0.49f, true, 0);
        s.bridge("Pont des Arts", 0.5f, 0.5f, true, 0);
        s.bridge("Pont de Bercy", 0.72f, 0.57f, true, 0);
        s.mark("Eiffel Tower", 0.293f, 0.548f, 0, 0, 520, ST_EIFFEL);
        s.mark("Arc de Triomphe", 0.27f, 0.385f, 0, 0, 60, ST_ARC);
        s.mark("Louvre", 0.475f, 0.48f, 14, 6, 4, L_PYRAMID);
        s.mark("Notre-Dame de Paris", 0.565f, 0.522f, 6, 3, 7, L_CATHEDRAL);
        s.mark("Sacré-Cœur", 0.5f, 0.19f, 6, 5, 6, L_WHITE_DOMES);
        s.mark("Panthéon", 0.55f, 0.6f, 5, 5, 5, L_DOME);
        s.mark("Tour Montparnasse", 0.45f, 0.655f, 5, 4, 28, L_DARK);
        s.mark("Palais Garnier", 0.47f, 0.4f, 6, 6, 5, L_CLASSIC);
        s.mark("Musée d'Orsay", 0.44f, 0.505f, 9, 4, 4, L_STATION);
        s.mark("Les Invalides", 0.37f, 0.56f, 7, 7, 4, L_GOLD_DOME);
        s.mark("Grand Palais", 0.37f, 0.47f, 8, 5, 3, L_LIBRARY);
        s.mark("Centre Pompidou", 0.565f, 0.465f, 5, 4, 6, L_PIPES);
        s.mark("Parc des Princes", 0.13f, 0.72f, 14, 12, 0, L_STADIUM);
        s.mark("Stade de France", 0.6f, 0.06f, 16, 14, 0, L_STADIUM);
        s.mark("Obélisque", 0.405f, 0.473f, 0, 0, 30, ST_OBELISK);
        s.mark("Grande Arche", 0.03f, 0.24f, 7, 7, 20, L_CUBE_ARCH);
        s.mark("Gare du Nord", 0.56f, 0.3f, 9, 5, 3, L_STATION);
        s.ew("Boulevard de Clichy", 0.24f);
        s.ew("Rue La Fayette", 0.33f);
        s.ew("Boulevard Haussmann", 0.385f, 0.3f, 0.6f);
        s.ew("Avenue des Champs-Élysées", 0.41f, 0.25f, 0.41f);
        s.ew("Rue de Rivoli", 0.47f, 0.42f, 0.63f);
        s.ew("Rue Saint-Antoine", 0.5f, 0.6f, 0.68f);
        s.ew("Boulevard Saint-Germain", 0.56f, 0.38f, 0.62f);
        s.ew("Rue de Vaugirard", 0.62f, 0.3f, 0.52f);
        s.ew("Boulevard du Montparnasse", 0.66f, 0.38f, 0.56f);
        s.ew("Boulevard de Port-Royal", 0.68f, 0.5f, 0.62f);
        s.ew("Rue de Tolbiac", 0.76f);
        s.ew("Avenue Daumesnil", 0.6f, 0.65f, 1);
        s.ew("Boulevard de Belleville", 0.35f, 0.65f, 0.8f);
        s.ns("Avenue Kléber", 0.26f, 0.3f, 0.5f);
        s.ns("Avenue Marceau", 0.3f, 0.38f, 0.5f);
        s.ns("Avenue Montaigne", 0.335f, 0.4f, 0.5f);
        s.ns("Rue Royale", 0.4f, 0.38f, 0.47f);
        s.ns("Avenue de l'Opéra", 0.465f, 0.4f, 0.48f);
        s.ns("Boulevard de Sébastopol", 0.545f, 0.3f, 0.5f);
        s.ns("Boulevard Saint-Michel", 0.54f, 0.55f, 0.7f);
        s.ns("Rue de Rennes", 0.47f, 0.55f, 0.68f);
        s.ns("Boulevard Raspail", 0.49f, 0.55f, 0.75f);
        s.ns("Boulevard Voltaire", 0.68f, 0.4f, 0.55f);
        s.ns("Avenue de la République", 0.7f, 0.35f, 0.5f);
        s.ns("Rue de Charonne", 0.73f, 0.45f, 0.6f);
        s.ns("Avenue d'Italie", 0.6f, 0.68f, 1);
        s.ns("Rue Lepic", 0.49f, 0.15f, 0.3f);
        s.ns("Avenue de Clichy", 0.42f, 0.1f, 0.3f);
    }
}
