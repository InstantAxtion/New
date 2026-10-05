package com.instantaxtion.zombiesandbox;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Procedurally generated city: tile map, pre-rendered ground bitmap, line of sight and flow fields. */
final class City {
    static final int T = 16;
    static final int FAR = 1 << 20;

    static final byte ROAD = 0, SIDEWALK = 1, BUILDING = 2, GRASS = 3, TREE = 4, PLAZA = 5, CAR = 6,
            STATUE = 7, LOT = 8, BASE = 9, FENCE = 10, PUMP = 11, RUBBLE = 12, RAIL = 13, DIRT = 14;

    static final int OFFICE = 0, HOUSE = 1, WAREHOUSE = 2, STATION = 3, BARRACKS = 4, TOWER = 5, HOSPITAL = 6,
            SHOP = 7, CHURCH = 8, SCHOOL = 9, FIRE_STATION = 10, MARKET = 11, KIOSK = 12, SPIRE = 13, CRYPT = 14,
            APARTMENT = 15, GARAGE = 16, PHARMACY = 17, TRAIN_STATION = 18, MALL = 19, STADIUM = 20, POWER = 21,
            BARN = 22, SILO = 23;
    static final int KIND_COUNT = 24;
    /** Ground decorations drawn into the map: {kind, x0, y0, x1, y1, variant} in world units. */
    static final int D_COURT = 0, D_FIELD = 1, D_PLAYGROUND = 2, D_GARDEN = 3, D_GRAVE = 4, D_SKATE = 5,
            D_CANOPY = 6, D_ALLEY = 7, D_SITE = 8, D_POWER = 9, D_HELIPAD = 10, D_BAY = 11, D_BANDSTAND = 12,
            D_FLOWERS = 13, D_CROPS = 14, D_ROUNDABOUT = 15, D_FLOODLIGHT = 16;
    static final int FACILITY_POLICE = 0, FACILITY_BASE = 1, FACILITY_HOSPITAL = 2, FACILITY_FIRE = 3;

    /** A police station or military base: where reinforcements come from and a preferred safe zone. */
    static final class Facility {
        final int kind;
        final float x, y, r, gateX, gateY;
        String name;
        int[] field;
        /** Rounds of ammunition in the armoury. */
        int ammo;
        boolean dryAnnounced;
        /** Guard posts (station entrance, base gates) as {x, y}. */
        final List<float[]> posts = new ArrayList<float[]>();

        Facility(int kind, float x, float y, float r, float gateX, float gateY, String name) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.r = r;
            this.gateX = gateX;
            this.gateY = gateY;
            this.name = name;
        }
    }

    private static final int[] ROOFS = {
            0xFF6B6F78, 0xFF7A6A5C, 0xFF5C6670, 0xFF8A7F70, 0xFF6E5D5A, 0xFF59646B, 0xFF7C7C74,
            0xFF4F5A66, 0xFF866E5E, 0xFF6A7468
    };
    private static final int[] WALLS = {
            0xFF9A8F80, 0xFFA8746A, 0xFF8C959E, 0xFFB5A58A, 0xFF7E8A7A, 0xFF9B7E6B, 0xFFA0A4A8, 0xFF6F7B88
    };
    private static final int[] WAREHOUSE_ROOFS = {0xFF8A9096, 0xFF7C848A, 0xFF9AA0A4, 0xFF6F777D, 0xFF7D8A80};
    private static final int[] WAREHOUSE_WALLS = {0xFF8A8F94, 0xFF9C8F7F, 0xFF7F8A8F, 0xFF8F8575};
    private static final int[] CAR_COLORS = {
            0xFFB03A2E, 0xFF2E5FB0, 0xFFE0E0E0, 0xFF222428, 0xFFD4A21C, 0xFF3C8A4E, 0xFF8A8F96, 0xFF6B2E8A
    };
    private static final float[] PARK_CHANCE = {0f, 0.07f, 0.15f, 0.35f};
    private static final float[] CAR_CHANCE = {0f, 0.018f, 0.05f};

    /** A building footprint (world units) with a height, drawn standing up by GameView. */
    static final class Building {
        final float x0, y0, x1, y1;
        float height;
        final int roof, wall, seed, kind;
        /** Civilians can hide inside homes, offices and warehouses: the door, room and barricade. */
        float doorX, doorY, barricade = 100, calmTimer, releaseTimer;
        int capacity;
        /** How many people live here: families in houses, flats in apartment blocks, a flat over some shops. */
        int residents;
        final List<Entity> occupants = new ArrayList<Entity>();
        /** Blast damage: a building knocked down to 0 collapses into rubble. */
        float hp, maxHp;
        boolean collapsed;
        /** Churches, schools and supermarkets have names (they can become safe zones). */
        String name;
        /** Supermarkets and gun stores: rounds of ammo on the shelves; pharmacies: medicine left. */
        int stock;
        /** Food for anyone sheltering here (and for foragers). */
        int food;
        /** Shops: 0 a general store, 1 a gun store, 2 a diner. */
        int shopType;
        /** What kind of building it is beyond its basic kind (see {@link Variants}), or -1. */
        int variant = -1;

        /** "Café", "Factory", "Bank"... or null for a plain building. */
        String typeName() {
            Variants.V v = Variants.get(variant);
            return v != null ? v.name : null;
        }
        /** Zombies shut inside, waiting. Nobody knows until they burst out (then {@link #infestKnown}). */
        int lurkers;
        /**
         * A fight inside: the dead got in while people were still there (lurkers and occupants together).
         * The one the defenders are fighting now has intruderHp left; flash is a muzzle flash to draw.
         */
        float intruderHp, fightTime, flash, fightSaid;
        boolean fighting;
        /** A survivor group has made this building its home. */
        World.Holdout holdout;
        /** On fire: the flames firefighters hose (null if not burning). */
        World.Fire fire;
        /** People inside on an errand (shopping, at church, at school), and how many are on their way. */
        final List<Entity> visitors = new ArrayList<Entity>();
        int heading;
        boolean infestKnown;
        /** Shop windows smashed in, and shelves stripped bare. */
        boolean smashed, looted;
        /** How long survivors inside have gone without food, and whether that's been reported. */
        float hunger;
        boolean outOfFood;
        /** Path to the door (made when someone first needs it). */
        int[] field;
        /** Bullet holes and scorch marks on the walls: {side, along, up, size} each, in a ring buffer. */
        final float[] marks = new float[MAX_MARKS * 4];
        int markCount, markNext;

        void mark(int side, float along, float up, float size) {
            int i = markNext * 4;
            marks[i] = side;
            marks[i + 1] = along;
            marks[i + 2] = up;
            marks[i + 3] = size;
            markNext = (markNext + 1) % MAX_MARKS;
            if (markCount < MAX_MARKS) markCount++;
        }

        Building(float x0, float y0, float x1, float y1, float height, int roof, int wall, int seed, int kind) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.height = height;
            this.roof = roof;
            this.wall = wall;
            this.seed = seed;
            this.kind = kind;
        }
    }

    static final int MAX_MARKS = 48;
    static final float FLOOR = 12f;
    static final float TREE_HEIGHT = 14f;

    final CityConfig cfg;
    /** Where the city is: its look and its names. */
    final Country country;
    /** The town's name, the same every time for a given city code. */
    final String name;
    final int w, h;
    final byte[] tiles;
    final boolean[] solid;
    final boolean[] opaque;
    final int[] humanDist, zombieDist;
    private final int[] queue;
    final Random rnd;
    final Bitmap bitmap;
    float maxHeight;
    /** The Graphics setting: realistic ground, shadows and roofs, or the classic flat look. */
    static volatile boolean realistic = true;
    /** Which look the map bitmap was last drawn with. */
    boolean drawnRealistic;
    final float detail;

    // ------------------------------------------------------------------ districts

    static final int DT_DOWNTOWN = 0, DT_MIDTOWN = 1, DT_OLDTOWN = 2, DT_SUBURB = 3, DT_INDUSTRIAL = 4, DT_CAMPUS = 5,
            DT_PARKSIDE = 6, DT_COUNT = 7;
    static final String[] DISTRICT_KINDS = {"Downtown", "Midtown", "Old Town", "Suburb", "Industrial", "University",
            "Parkside"};

    /** A neighbourhood of the city with its own character: tall towers, shops, old streets, homes or industry. */
    static final class District {
        int type;
        float x, y;
        String name;
        /** The middle of the district's area (world units) and how many tiles it covers. */
        float cx, cy;
        int tiles;
    }

    final List<District> districts = new ArrayList<District>();
    /** Which district each tile belongs to. */
    private byte[] districtAt;
    /** The countryside around a town, or -1. */
    private int countryDistrict = -1;
    /** The district of the block currently being filled in. */
    private int curDistrict = DT_MIDTOWN;

    /** Scatters district centres over the map, gives each a kind and a name, and assigns every tile to one. */
    private void makeDistricts() {
        // Only the town is split into districts; any countryside around it is one area of its own.
        float core = cfg.coreFraction();
        int m = core < 0.99f ? (int) (w * (1 - core) / 2) : 0;
        int n = 3 + (w - 2 * m) / 32;
        Random r = rnd;
        List<String> words = new ArrayList<String>(Arrays.asList(country.districtWords));
        java.util.Collections.shuffle(words, r);
        int word = 0;
        boolean[] usedUnique = new boolean[DT_COUNT];
        for (int k = 0; k < n; k++) {
            District d = new District();
            // The first district sits in the middle of town; the rest spread out with some space between them.
            float bestX = w / 2f, bestY = h / 2f;
            if (k > 0) {
                float bestScore = -1;
                for (int tries = 0; tries < 30; tries++) {
                    float x = m + 6 + r.nextFloat() * (w - 2 * m - 12), y = m + 6 + r.nextFloat() * (h - 2 * m - 12);
                    float near = Float.MAX_VALUE;
                    for (District o : districts) near = Math.min(near, (float) Math.hypot(o.x - x, o.y - y));
                    if (near > bestScore) {
                        bestScore = near;
                        bestX = x;
                        bestY = y;
                    }
                }
            }
            d.x = bestX;
            d.y = bestY;
            float dist = (float) Math.hypot(d.x - w / 2f, d.y - h / 2f) / (w * 0.5f);
            if (k == 0) d.type = cfg.coreDistrict();
            else {
                // Towers towards the middle, homes and parks towards the edge of town.
                float[] wts = new float[DT_COUNT];
                float total = 0;
                for (int t = 0; t < DT_COUNT; t++) {
                    float wt = cfg.districtWeight(t);
                    if (t == DT_DOWNTOWN) wt *= Math.max(0.1f, 1.4f - dist * 1.5f);
                    if (t == DT_SUBURB || t == DT_PARKSIDE) wt *= 0.4f + dist;
                    if (t == DT_INDUSTRIAL) {
                        float ang = (float) Math.atan2(d.y - h / 2f, d.x - w / 2f) - industryAngle;
                        while (ang > Math.PI) ang -= Math.PI * 2;
                        while (ang < -Math.PI) ang += Math.PI * 2;
                        wt *= Math.abs(ang) < 1f ? 2.5f : 0.4f;
                    }
                    if ((t == DT_OLDTOWN || t == DT_CAMPUS) && usedUnique[t]) wt *= 0.3f;
                    wts[t] = wt;
                    total += wt;
                }
                float pick = r.nextFloat() * total;
                int t = 0;
                while (t < DT_COUNT - 1 && pick >= wts[t]) pick -= wts[t++];
                d.type = t;
            }
            usedUnique[d.type] = true;
            String wd = words.get(word++ % words.size());
            switch (d.type) {
                case DT_DOWNTOWN: d.name = usedUnique[DT_COUNT - 1] ? wd + " Center" : "Downtown"; break;
                case DT_MIDTOWN: d.name = r.nextBoolean() ? wd + " Village" : wd + " Square"; break;
                case DT_OLDTOWN: d.name = r.nextBoolean() ? "Old Town" : "The Old Quarter"; break;
                case DT_SUBURB: d.name = wd + (new String[]{" Heights", " Hills", " Gardens", " Park", " Grove"})[r.nextInt(5)]; break;
                case DT_INDUSTRIAL: d.name = r.nextBoolean() ? wd + " Industrial Estate" : wd + " Works"; break;
                case DT_CAMPUS: d.name = "University District"; break;
                default: d.name = wd + " Common"; break;
            }
            if (d.type == DT_DOWNTOWN) usedUnique[DT_COUNT - 1] = true;
            d.name = this.country.districtName(d.name, wd, d.type);
            for (District o : districts) if (o.name.equals(d.name)) d.name = this.country.districtName(wd + " " + DISTRICT_KINDS[d.type], wd + " " + (k + 1), -1);
            districts.add(d);
        }
        int country = -1;
        if (m > 0) {
            District d = new District();
            d.type = DT_PARKSIDE;
            d.name = this.country.districtName(words.get(word) + (r.nextBoolean() ? " Valley" : " Vale"), words.get(word), -2);
            word++;
            country = districts.size();
            countryDistrict = country;
            districts.add(d);
        }
        // Every tile belongs to its nearest centre, with wobbly borders.
        districtAt = new byte[w * h];
        float[] sx = new float[districts.size()], sy = new float[districts.size()];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (country >= 0 && (x < m || y < m || x >= w - m || y >= h - m)) {
                    districtAt[y * w + x] = (byte) country;
                    districts.get(country).tiles++;
                    continue;
                }
                float wx = x + (float) Math.sin(y * 0.21f + cfg.seed) * 3, wy = y + (float) Math.sin(x * 0.17f + cfg.seed * 0.7f) * 3;
                int best = 0;
                float bd = Float.MAX_VALUE;
                for (int k = 0; k < n; k++) {
                    District d = districts.get(k);
                    // Downtown is compact; suburbs sprawl.
                    float weight = d.type == DT_DOWNTOWN ? 1.25f : d.type == DT_SUBURB ? 0.85f : 1f;
                    float dd = ((d.x - wx) * (d.x - wx) + (d.y - wy) * (d.y - wy)) * weight;
                    if (dd < bd) {
                        bd = dd;
                        best = k;
                    }
                }
                districtAt[y * w + x] = (byte) best;
                sx[best] += x;
                sy[best] += y;
                districts.get(best).tiles++;
            }
        for (int k = 0; k < n; k++) {
            District d = districts.get(k);
            if (d.tiles == 0) continue;
            d.cx = (sx[k] / d.tiles + 0.5f) * T;
            d.cy = (sy[k] / d.tiles + 0.5f) * T;
        }
        if (country >= 0) {
            // Its name goes out in the fields, to one side of town.
            District d = districts.get(country);
            d.cx = m / 2f * T;
            d.cy = h / 2f * T;
        }
    }

    /** The district at a world position. */
    /** Which district a world position is in (an index into districts), or -1. */
    int districtIndex(float x, float y) {
        if (districtAt == null) return -1;
        return districtAt[tileIndex(x, y)];
    }

    District districtOf(float x, float y) {
        if (districtAt == null || districts.isEmpty()) return null;
        return districts.get(districtAt[tileIndex(x, y)]);
    }

    /** The kind of district at a tile. */
    private int districtType(int tx, int ty) {
        if (districtAt == null) return DT_MIDTOWN;
        tx = Math.max(0, Math.min(w - 1, tx));
        ty = Math.max(0, Math.min(h - 1, ty));
        return districts.get(districtAt[ty * w + tx]).type;
    }

    /** A street: a band of road tiles {x0, y0, x1, y1} (x1/y1 exclusive) running one way, with its name. */
    static final class Street {
        final int x0, y0, x1, y1, width;
        final boolean vertical, main;
        String name;

        Street(int x0, int y0, int x1, int y1, boolean vertical, boolean main) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.vertical = vertical;
            this.main = main;
            width = vertical ? x1 - x0 : y1 - y0;
        }
    }

    final List<Street> streets = new ArrayList<Street>();
    /** Road direction per tile: 0 none, 1 north-south, 2 east-west, 3 junction. */
    private final byte[] roadDir;
    /** Tiles that are part of a main road. */
    private final boolean[] mainRoad;
    /** Block rectangles (including their sidewalk ring) as {x0, y0, x1, y1}, x1/y1 exclusive. */
    private final List<int[]> blocks = new ArrayList<int[]>();
    /** 0 normal car, 1 police car, 2 army truck. */
    private byte[] carKind;
    private final List<float[]> helipads = new ArrayList<float[]>();
    private final List<float[]> tents = new ArrayList<float[]>();
    private final List<float[]> paths = new ArrayList<float[]>();
    final List<Facility> facilities = new ArrayList<Facility>();
    final List<float[]> decor = new ArrayList<float[]>();
    /** Gas pumps as {x, y, alive}: an explosion nearby sets them off. */
    final List<float[]> pumps = new ArrayList<float[]>();
    /** Building lots: {x, y, w, h, roof, seed, kind, floors, wall} in tiles. */
    private final List<int[]> buildingLots = new ArrayList<int[]>();
    final List<Building> buildings = new ArrayList<Building>();
    /** Everyone the city's homes can house. */
    int totalResidents;
    /** Which building stands on each tile (index into buildings), or -1. */
    final int[] buildingAt;
    /** Tree canopies as {x, y, radius}; drawn above the ground by GameView. */
    final List<float[]> trees = new ArrayList<float[]>();
    /** Street lamps as {x, y}. */
    final List<float[]> lamps = new ArrayList<float[]>();
    private final List<int[]> statues = new ArrayList<int[]>();
    /** Open spaces where a safe zone can be set up, as {x, y, kind}: 0 park, 1 plaza, 2 parking lot. */
    final List<float[]> openAreas = new ArrayList<float[]>();
    private int origin;


    City(CityConfig cfg) {
        // The map picture is kept to about 4096 pixels across, so a massive map still fits in a phone's memory.
        this(cfg, Math.min(1f, 4096f / (cfg.tiles() * T)));
    }

    /** A city; with a detail below 1 the map bitmap is drawn smaller (for the New Game preview). */
    City(CityConfig cfg, float detail) {
        this.cfg = cfg;
        this.detail = detail;
        w = h = cfg.tiles();
        rnd = new Random(cfg.seed);
        Variants.extra = new Random(cfg.seed * 7919L + 101);
        Random nameRnd = new Random(cfg.seed * 31 + 7);
        country = Country.get(cfg.country());
        name = country.townName(nameRnd);
        tiles = new byte[w * h];
        solid = new boolean[w * h];
        opaque = new boolean[w * h];
        humanDist = new int[w * h];
        zombieDist = new int[w * h];
        queue = new int[w * h];
        roadDir = new byte[w * h];
        mainRoad = new boolean[w * h];
        carKind = new byte[w * h];
        buildingAt = new int[w * h];
        Arrays.fill(buildingAt, -1);
        generate();
        for (int[] e : cfg.edits) applyEdit(e[0], e[1], e[2], false);
        for (int i = 0; i < tiles.length; i++) {
            byte t = tiles[i];
            solid[i] = t == BUILDING || t == TREE || t == CAR || t == STATUE || t == FENCE || t == PUMP;
            opaque[i] = t == BUILDING;
        }
        for (int[] l : buildingLots) createBuilding(l);
        Arrays.fill(humanDist, FAR);
        Arrays.fill(zombieDist, FAR);
        for (Facility f : facilities) {
            f.field = new int[w * h];
            walkFieldFromPoints(f.field, new float[]{f.x}, new float[]{f.y}, 1);
        }
        // The big map uses 16-bit colour to keep memory down.
        bitmap = Bitmap.createBitmap((int) (w * T * detail), (int) (h * T * detail),
                w * detail > 140 ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888);
        redraw();
    }

    /** Builds a Building from a lot: its door, room for people, toughness, name, food and stock. */
    private Building createBuilding(int[] l) {
        {
            float height = l[7] * FLOOR + 3;
            maxHeight = Math.max(maxHeight, height);
            Building b = new Building(l[0] * T, l[1] * T, (l[0] + l[2]) * T, (l[1] + l[3]) * T, height,
                    l[4], l[8], l[5], l[6]);
            int k = l[6];
            b.variant = l.length > 9 ? l[9] : -1;
            int k2 = k;
            if (k2 == OFFICE || k2 == HOUSE || k == WAREHOUSE || k == SHOP || k == CHURCH || k == SCHOOL || k == MARKET
                    || k == KIOSK || k == APARTMENT || k == GARAGE || k == PHARMACY || k == TRAIN_STATION || k == MALL
                    || k == BARN || (k == STADIUM && !stadiumNamed)) placeDoor(b, l);
            if (k == MALL) b.capacity = 40;
            if (k == APARTMENT) b.capacity = Math.max(8, Math.min(40, l[2] * l[3] / 3));
            Random rr = new Random(l[5] * 31L + 7);
            if (b.capacity > 0) {
                // Two people to a flat, a flat for every two tiles of floor, on every floor.
                if (k == HOUSE) b.residents = 1 + rr.nextInt(4) + (l[2] * l[3] >= 24 ? 1 : 0);
                else if (k == APARTMENT) b.residents = Math.min(160, l[2] * l[3] * Math.max(1, l[7]) / 9);
                else if (k == SHOP && l[7] >= 2) b.residents = 1 + rr.nextInt(3);
                // Some office blocks are flats (and some old buildings have flats upstairs).
                else if (k == OFFICE && l[7] >= 2 && rr.nextFloat() < 0.2f) b.residents = Math.min(60, l[2] * l[3] * l[7] / 18);
                // Home is somewhere the whole household can shelter.
                b.capacity = Math.max(b.capacity, Math.min(b.residents, 40));
                totalResidents += b.residents;
            }
            // Bigger and taller buildings take more to bring down.
            b.maxHp = b.hp = 150 + l[2] * l[3] * 22 + l[7] * 60;
            Random nr = new Random(l[5]);
            if (k == CHURCH) b.name = country.churches[nr.nextInt(country.churches.length)];
            else if (k == SCHOOL) b.name = country.schools[nr.nextInt(country.schools.length)];
            else if (k == TRAIN_STATION) {
                b.name = "Central Station";
                stationBuilding = b;
            }
            else if (k == MALL) b.name = name + " Mall";
            else if (k == STADIUM && !stadiumNamed) {
                b.name = name + " Stadium";
                stadiumNamed = true;
            }
            else if (k == MARKET) {
                b.name = country.markets[nr.nextInt(country.markets.length)];
                b.stock = 240;
            } else if (k == PHARMACY) {
                b.name = country.pharmacies[nr.nextInt(country.pharmacies.length)];
                b.stock = 60;
            } else if (k == SHOP) {
                // Some shops are gun stores or diners (and the rest are whatever kind of shop they are).
                int roll = nr.nextInt(12);
                b.shopType = roll == 0 ? 1 : roll < 3 && b.variant < 0 ? 2 : 0;
                if (b.shopType == 1) b.variant = -1;
                if (b.shopType == 1) {
                    b.name = country.gunStores[nr.nextInt(country.gunStores.length)];
                    b.stock = 240;
                }
            }
            // Food in the cupboards and on the shelves.
            b.food = k == MARKET ? 400 : k == MALL ? 300 : k == WAREHOUSE ? 150 : k == SHOP ? (b.shopType == 2 ? 120 : 40)
                    : k == APARTMENT ? 60 : k == HOUSE ? 25 : k == SCHOOL ? 80 : k == BARN ? 120 : 12;
            for (int j = l[1]; j < l[1] + l[3]; j++)
                for (int i = l[0]; i < l[0] + l[2]; i++) buildingAt[j * w + i] = buildings.size();
            buildings.add(b);
            return b;
        }
    }

    // ------------------------------------------------------------------ the Build tool

    static final int ED_ROAD = 0, ED_PAVE = 1, ED_GRASS = 2, ED_TREES = 3, ED_WALL = 4, ED_HOUSE = 5, ED_SHOP = 6, ED_CLEAR = 7;

    /**
     * Changes the map: paints a tile, or puts up a house or a shop. While generating, before the buildings are
     * made (live = false); or during a game (live = true), when the building is made at once. Returns the new
     * building, or null.
     */
    Building applyEdit(int tx, int ty, int kind, boolean live) {
        if (live) renderVersion++;
        if (tx < 1 || ty < 1 || tx >= w - 1 || ty >= h - 1) return null;
        int i = ty * w + tx;
        if (kind == ED_HOUSE || kind == ED_SHOP) {
            int lw = kind == ED_HOUSE ? 2 : 3, lh = 2;
            if (tx + lw >= w || ty + lh >= h) return null;
            for (int y = ty; y < ty + lh; y++)
                for (int x = tx; x < tx + lw; x++) {
                    byte t = tiles[y * w + x];
                    if (t == BUILDING || t == ROAD || t == RAIL || t == BASE || buildingAt[y * w + x] >= 0) return null;
                }
            addLot(tx, ty, lw, lh, kind == ED_HOUSE ? HOUSE : SHOP);
            if (!live) return null;
            for (int y = ty; y < ty + lh; y++)
                for (int x = tx; x < tx + lw; x++) {
                    solid[y * w + x] = true;
                    opaque[y * w + x] = true;
                    roadDir[y * w + x] = 0;
                }
            return createBuilding(buildingLots.get(buildingLots.size() - 1));
        }
        if (buildingAt[i] >= 0 && !buildings.isEmpty()) {
            // Only Clear touches a building: it knocks it down.
            if (kind != ED_CLEAR || !live) return null;
            Building b = buildings.get(buildingAt[i]);
            if (!b.collapsed) collapse(b);
            return null;
        }
        byte t = tiles[i];
        if (t == BUILDING || t == RAIL || t == BASE) return null;
        byte nt = kind == ED_ROAD ? ROAD : kind == ED_PAVE ? SIDEWALK : kind == ED_TREES ? TREE : kind == ED_WALL ? FENCE : GRASS;
        tiles[i] = nt;
        roadDir[i] = (byte) (nt == ROAD ? 3 : 0);
        if (live) {
            solid[i] = nt == TREE || nt == FENCE;
            opaque[i] = false;
        }
        return null;
    }

    /** After live edits: routes to the police stations, base and hospital are worked out again. */
    void editsDone() {
        for (Facility f : facilities) fieldFromPoints(f.field, new float[]{f.x}, new float[]{f.y}, 1);
    }

    // ------------------------------------------------------------------ close-ups

    /** Randomness for drawing, seeded from each tile's position (see tileSeed). */
    private final Random prnd = new Random();
    /** While drawing a close-up: only this region (in tiles, with a margin). */
    private boolean regionOnly;
    private int rx0, ry0, rx1, ry1;
    private float rwx0, rwy0, rwx1, rwy1;
    /** Goes up whenever the map picture changes after it's drawn (rubble, scorch marks, edits). */
    volatile int renderVersion;

    private long tileSeed(int x, int y, int pass) {
        return (x * 73856093L) ^ (y * 19349663L) ^ (pass * 83492791L) ^ (cfg.seed * 2654435761L);
    }

    /** True when drawing a close-up and this box (with room for shadows) is nowhere near it. */
    private boolean offRegion(float x0, float y0, float x1, float y1) {
        return regionOnly && (x1 < rwx0 - 90 || x0 > rwx1 + 30 || y1 < rwy0 - 90 || y0 > rwy1 + 30);
    }

    /**
     * A sharp close-up of part of the map: the square from (wx0, wy0), size world units across, drawn at res
     * pixels per world unit, exactly as the whole map is drawn (just more finely).
     */
    synchronized Bitmap renderRegion(float wx0, float wy0, float size, float res) {
        int px = Math.max(1, Math.round(size * res));
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(out);
        c.scale(res, res);
        c.translate(-wx0, -wy0);
        c.clipRect(wx0, wy0, wx0 + size, wy0 + size);
        regionOnly = true;
        rwx0 = wx0;
        rwy0 = wy0;
        rwx1 = wx0 + size;
        rwy1 = wy0 + size;
        rx0 = Math.max(0, (int) (wx0 / T) - 3);
        ry0 = Math.max(0, (int) (wy0 / T) - 3);
        rx1 = Math.min(w, (int) ((wx0 + size) / T) + 3);
        ry1 = Math.min(h, (int) ((wy0 + size) / T) + 3);
        try {
            render(c);
            for (Building b : buildings) if (b.collapsed && !offRegion(b.x0, b.y0, b.x1, b.y1)) drawRubble(c, b);
            for (int[] t : charred) charTile(c, t[0], t[1]);
        } finally {
            regionOnly = false;
        }
        return out;
    }

    /**
     * Lettering on the roofs (shop names, POLICE, FIRE...): kept as text and drawn over the map by the view at
     * screen resolution, instead of into the map picture where it blurs as soon as you zoom in.
     */
    static final class Label {
        final String text;
        final float x, y, size;
        final int color, building;

        Label(String text, float x, float y, float size, int color, int building) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.size = size;
            this.color = color;
            this.building = building;
        }
    }

    final List<Label> labels = new ArrayList<Label>();
    private boolean collectLabels;

    void label(String text, float x, float y, float size, int color) {
        if (!collectLabels) return;
        int tx = Math.max(0, Math.min(w - 1, (int) (x / T))), ty = Math.max(0, Math.min(h - 1, (int) (y / T)));
        labels.add(new Label(text, x, y, size, color, buildingAt[ty * w + tx]));
    }

    /** Draws (or redraws, after the Graphics setting changes) the ground bitmap. */
    synchronized void redraw() {
        renderVersion++;
        labels.clear();
        trees.clear();
        computeJunctions();
        Canvas canvas = new Canvas(bitmap);
        if (detail != 1f) canvas.scale(detail, detail);
        drawnRealistic = realistic;
        collectLabels = true;
        render(canvas);
        collectLabels = false;
        for (Building b : buildings) if (b.collapsed) drawRubble(b);
    }

    private boolean stadiumNamed;

    /**
     * Emergency services spread across town: a block well away from every police station, base, hospital and
     * fire station already placed (or the furthest there is).
     */
    private int spreadOut(List<Integer> options, int fallback) {
        float want = Math.max(28, w / 4f) * T;
        List<Integer> far = new ArrayList<Integer>();
        int best = fallback;
        float bestD = -1;
        for (int k : options) {
            int[] b = blocks.get(k);
            float cx = (b[0] + b[2]) / 2f * T, cy = (b[1] + b[3]) / 2f * T, d = Float.MAX_VALUE;
            for (Facility f : facilities) d = Math.min(d, (float) Math.hypot(f.x - cx, f.y - cy));
            if (d >= want) far.add(k);
            if (d > bestD) {
                bestD = d;
                best = k;
            }
        }
        return far.isEmpty() ? best : far.get(rnd.nextInt(far.size()));
    }

    /** Puts the door in the middle of the first side that opens onto walkable ground. */
    private void placeDoor(Building b, int[] l) {
        int x = l[0], y = l[1], lw = l[2], lh = l[3];
        int[][] sides = {{x + lw / 2, y + lh}, {x + lw / 2, y - 1}, {x - 1, y + lh / 2}, {x + lw, y + lh / 2}};
        for (int[] s : sides) {
            if (solidTile(s[0], s[1])) continue;
            b.doorX = s[0] * T + T / 2f;
            b.doorY = s[1] * T + T / 2f;
            b.capacity = Math.max(3, Math.min(15, lw * lh / 2));
            return;
        }
    }

    float worldW() {
        return w * T;
    }

    float worldH() {
        return h * T;
    }

    // ------------------------------------------------------------------ generation

    /** The railway: tile rows it runs along, or -1 if the map has none. */
    int railY0 = -1, railRows;
    /** Level crossings as {x0, x1} (tile columns), and the middle of the station platform (or -1). */
    final List<int[]> crossings = new ArrayList<int[]>();
    float stationX = -1, stationY;
    Building stationBuilding;
    String stationName;

    /** Where the industrial district is: an angle around the centre (maps with warehouses on the edge). */
    private float industryAngle;

    /** True if any tile around the block's edge is part of a main road (shops and businesses line up there). */
    private boolean onMainRoad(int x0, int y0, int x1, int y1) {
        for (int x = x0 - 1; x <= x1; x++)
            for (int k = 0; k < 2; k++) {
                int y = k == 0 ? y0 - 1 : y1;
                if (x >= 0 && y >= 0 && x < w && y < h && mainRoad[y * w + x]) return true;
            }
        for (int y = y0 - 1; y <= y1; y++)
            for (int k = 0; k < 2; k++) {
                int x = k == 0 ? x0 - 1 : x1;
                if (x >= 0 && y >= 0 && x < w && y < h && mainRoad[y * w + x]) return true;
            }
        return false;
    }

    /** Lays road tiles for a street, marking junctions where it crosses another. */
    private void carve(Street st) {
        for (int y = st.y0; y < st.y1; y++)
            for (int x = st.x0; x < st.x1; x++) {
                if (x < 0 || y < 0 || x >= w || y >= h) continue;
                int i = y * w + x;
                roadDir[i] = roadDir[i] != 0 ? 3 : (byte) (st.vertical ? 1 : 2);
                tiles[i] = ROAD;
                if (st.main) mainRoad[i] = true;
            }
        streets.add(st);
    }

    /** Where streets have been laid out so far: centres (doubled) of north-south and east-west ones. */
    private final List<Integer> vLines = new ArrayList<Integer>(), hLines = new ArrayList<Integer>();

    /** Marks a split's returned street centre as a north-south street. */
    private static final int VERT = 1 << 20;

    /**
     * Splits a rectangle of city with a street, then splits each side again, until the pieces are block
     * sized. Big pieces get main roads. Streets carry on straight across junctions: the second side continues
     * the first side's street, or a street lines up with one already laid elsewhere in town; only now and
     * then (more in the old town) does a street jog. Leaves become blocks. Returns the street's centre.
     */
    private int split(int x0, int y0, int x1, int y1, int depth, int hintX, int hintY) {
        int bw = x1 - x0, bh = y1 - y0;
        float cx = (x0 + x1) / 2f - w / 2f, cy = (y0 + y1) / 2f - h / 2f;
        float d = (float) Math.sqrt(cx * cx + cy * cy) / (w * 0.5f);
        boolean rural = cfg.density() == 0 && cfg.style() == CityConfig.STYLE_HOUSES;
        // Each district has its own grain: small tight blocks downtown and in the old town, big ones in the
        // suburbs, on campus and in the industrial estates.
        int dt = districtType((x0 + x1) / 2, (y0 + y1) / 2);
        int maxLeaf;
        switch (dt) {
            case DT_DOWNTOWN: maxLeaf = 9 + rnd.nextInt(3); break;
            case DT_MIDTOWN: maxLeaf = 11 + rnd.nextInt(4); break;
            case DT_OLDTOWN: maxLeaf = 8 + rnd.nextInt(5); break;
            case DT_SUBURB: maxLeaf = 15 + rnd.nextInt(7); break;
            case DT_INDUSTRIAL: maxLeaf = 15 + rnd.nextInt(6); break;
            case DT_CAMPUS: maxLeaf = 15 + rnd.nextInt(5); break;
            default: maxLeaf = 13 + rnd.nextInt(6); break;
        }
        // On the biggest maps a stretch of suburb is laid out as a neighbourhood of its own, not more grid.
        if (w >= 400 && depth >= 1 && bw >= 44 && bh >= 44 && bw <= 76 && bh <= 76
                && rnd.nextFloat() < (dt == DT_SUBURB || dt == DT_PARKSIDE ? 0.8f : dt == DT_MIDTOWN ? (d > 0.45f ? 0.55f : 0.2f)
                        : dt == DT_INDUSTRIAL ? 0.45f : 0f)
                && neighbourhood(x0, y0, x1, y1, d)) return -1;
        if (d > 0.8f) maxLeaf += 2;
        // Bigger blocks: fewer, longer streets, as in a real town.
        maxLeaf += 3;
        if (rural) maxLeaf += 6;
        if (cfg.density() == 2) maxLeaf -= 2;
        // Blocks are twice the size they used to be (in tiles): real buildings need room.
        maxLeaf *= 2;
        if (bw <= maxLeaf && bh <= maxLeaf) {
            blocks.add(new int[]{x0, y0, x1, y1});
            return -1;
        }
        boolean big = Math.max(bw, bh) > (w > 220 ? 84 : 68) && depth < 3 && !rural;
        int roadW = big ? 5 : 3, minSide = 16;
        boolean vertical;
        if (bw > bh * 1.3f) vertical = true;
        else if (bh > bw * 1.3f) vertical = false;
        else vertical = rnd.nextBoolean();
        int len = vertical ? bw : bh;
        if (len < minSide * 2 + roadW) {
            vertical = !vertical;
            len = vertical ? bw : bh;
            if (len < minSide * 2 + roadW) {
                blocks.add(new int[]{x0, y0, x1, y1});
                return -1;
            }
        }
        int lo = (vertical ? x0 : y0) + Math.max(minSide, (int) (len * 0.3f)), hi = (vertical ? x1 : y1) - roadW - Math.max(minSide, (int) (len * 0.3f));
        if (hi < lo) hi = lo;
        int at = lo + rnd.nextInt(hi - lo + 1);
        // Carry on the neighbouring street now and then, so some roads run on straight.
        // Streets carry on across junctions far more often than not (a jog at every crossing makes no sense).
        float align = cfg.layout() == 0 ? 0.97f : cfg.layout() == 1 ? 0.9f : 0.75f;
        // Old streets wander a little; downtown is laid out on a grid.
        if (dt == DT_OLDTOWN) align = 0.6f;
        else if (dt == DT_DOWNTOWN) align = Math.max(align, 0.95f);
        // Big-map suburbs grew a bit at a time: more T-junctions, fewer streets running dead straight.
        if (w >= 400 && (dt == DT_SUBURB || dt == DT_PARKSIDE)) align = Math.min(align, 0.55f);
        // Lines are kept as street centres (doubled, so odd widths stay exact): a narrow street lines up
        // with the middle of a wide one, not with its edge.
        int hint = vertical ? hintX : hintY;
        int wideLo = (vertical ? x0 : y0) + minSide, wideHi = (vertical ? x1 : y1) - roadW - minSide;
        if (hint >= 0 && rnd.nextFloat() < align) {
            int h0 = (hint - roadW) / 2;
            if (h0 >= wideLo && h0 <= wideHi) at = h0;
        } else if (rnd.nextFloat() < align) {
            // Line up with a street already laid out elsewhere in town, so streets run straight across
            // the map instead of jogging at every junction.
            List<Integer> lines = vertical ? vLines : hLines;
            int best = -1, bd = Integer.MAX_VALUE, mid = (lo + hi) / 2;
            for (int k = 0; k < lines.size(); k++) {
                int l = (lines.get(k) - roadW) / 2;
                if (l < wideLo || l > wideHi) continue;
                if (Math.abs(l - mid) < bd) {
                    bd = Math.abs(l - mid);
                    best = l;
                }
            }
            if (best >= 0) at = best;
        }
        (vertical ? vLines : hLines).add(at * 2 + roadW);
        Street st = vertical ? new Street(at, y0, at + roadW, y1, true, big) : new Street(x0, at, x1, at + roadW, false, big);
        carve(st);
        // The first side's own street, when it runs across this one, is carried on into the second side.
        int nextHint;
        if (vertical) {
            nextHint = split(x0, y0, at, y1, depth + 1, -1, hintY);
            boolean across = nextHint >= 0 && (nextHint & VERT) == 0;
            split(at + roadW, y0, x1, y1, depth + 1, -1, across ? nextHint : hintY);
        } else {
            nextHint = split(x0, y0, x1, at, depth + 1, hintX, -1);
            boolean across = nextHint >= 0 && (nextHint & VERT) != 0;
            split(x0, at + roadW, x1, y1, depth + 1, across ? nextHint & ~VERT : hintX, -1);
        }
        return (at * 2 + roadW) | (vertical ? VERT : 0);
    }

    private void generate() {
        origin = 0;
        Arrays.fill(tiles, SIDEWALK);
        industryAngle = rnd.nextFloat() * (float) Math.PI * 2;
        makeDistricts();
        // Where the town is: the whole map, or a core surrounded by countryside.
        float core = cfg.coreFraction();
        int m = core < 0.99f ? (int) (w * (1 - core) / 2) : 0;
        int bx0 = m, by0 = m, bx1 = w - m, by1 = h - m;
        townX0 = bx0;
        townY0 = by0;
        townX1 = bx1;
        townY1 = by1;
        if (m > 0) fill(0, 0, w, h, GRASS);
        // A ring road around the edge of town (a village just has its lanes).
        boolean village = cfg.density() == 0 && cfg.style() == CityConfig.STYLE_HOUSES;
        // A town out in the country grows its own shape instead of filling a square.
        boolean organic = m > 0 && !village;
        int in = organic ? 0 : 3;
        if (m == 0) {
            carve(new Street(bx0, by0, bx1, by0 + 3, false, false));
            carve(new Street(bx0, by1 - 3, bx1, by1, false, false));
            carve(new Street(bx0, by0, bx0 + 3, by1, true, false));
            carve(new Street(bx1 - 3, by0, bx1, by1, true, false));
        }
        if (m > 0) fill(bx0 + in, by0 + in, bx1 - bx0 - in * 2, by1 - by0 - in * 2, village ? GRASS : SIDEWALK);
        // The railway cuts straight across the middle third of the town; the streets are laid out on each side.
        if (cfg.hasRail()) {
            int th = by1 - by0;
            railY0 = by0 + th / 3 + rnd.nextInt(Math.max(1, th / 3));
            railRows = 3;
            split(bx0 + in, by0 + in, bx1 - in, railY0, 0, -1, -1);
            split(bx0 + in, railY0 + railRows, bx1 - in, by1 - in, 0, -1, -1);
            for (int y = railY0; y < railY0 + railRows; y++)
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    // Streets that reach the line from both sides get a level crossing.
                    boolean above = roadDir[(railY0 - 1) * w + x] == 1, below = roadDir[(railY0 + railRows) * w + x] == 1;
                    if ((above && below) || (m == 0 && ((x >= bx0 && x < bx0 + 3) || (x >= bx1 - 3 && x < bx1)))) {
                        tiles[i] = ROAD;
                        roadDir[i] = 1;
                    } else {
                        tiles[i] = RAIL;
                        roadDir[i] = 0;
                    }
                }
            ensureCrossings(bx0, bx1);
        } else if (village && m > 0) {
            villageCore(bx0, by0, bx1, by1);
        } else {
            split(bx0 + in, by0 + in, bx1 - in, by1 - in, 0, -1, -1);
        }
        if (organic) erodeTown(bx0, by0, bx1, by1);
        // Out in the country, a highway runs past the town.
        if (m >= 16) highway(bx0, by0, bx1, by1);
        if (m > 0) {
            int n = w >= 400 ? 3 + rnd.nextInt(3) : w >= 300 ? 1 + rnd.nextInt(2) : rnd.nextInt(2);
            hamlets(n);
        }
        if (m > 0) countryside(townX0, townY0, townX1, townY1);
        if (m > 0) tidyDeadEnds();
        // Each neighbourhood is a place of its own, as far as anyone roaming between places is concerned.
        for (int[] b : blocks)
            if (b.length > 5 && b[4] == 3) settlements.add(new float[]{(b[0] + b[2]) / 2f * T, (b[1] + b[3]) / 2f * T});
        // Tree-lined medians down the main roads, open at the junctions.
        for (Street st : streets) {
            if (st.width != 5) continue;
            // Only the long avenues get a median.
            if ((st.vertical ? st.y1 - st.y0 : st.x1 - st.x0) < 30 || rnd.nextFloat() < 0.35f) continue;
            int n = st.vertical ? st.y1 - st.y0 : st.x1 - st.x0;
            for (int k = 0; k < n; k++) {
                boolean clear = true;
                for (int a = 0; a < 5 && clear; a++) {
                    for (int e = -1; e <= 1 && clear; e++) {
                        int x = st.vertical ? st.x0 + a : st.x0 + k + e, y = st.vertical ? st.y0 + k + e : st.y0 + a;
                        if (x < 0 || y < 0 || x >= w || y >= h || roadDir[y * w + x] != (st.vertical ? 1 : 2)) clear = false;
                    }
                }
                // A side street joining from either side leaves a gap so traffic can turn across.
                for (int a = -1; a <= 5 && clear; a += 6)
                    for (int e = -2; e <= 2 && clear; e++) {
                        int x = st.vertical ? st.x0 + a : st.x0 + k + e, y = st.vertical ? st.y0 + k + e : st.y0 + a;
                        if (isRoad(x, y)) clear = false;
                    }
                if (!clear) continue;
                int x = st.vertical ? st.x0 + 2 : st.x0 + k, y = st.vertical ? st.y0 + k : st.y0 + 2;
                tiles[y * w + x] = k % 2 == 0 ? TREE : GRASS;
                // Each side of the median is one way, like a highway's carriageways: nobody drives up the
                // wrong side of a boulevard (and then has to cross the median to get back).
                if (divided == null) divided = new byte[w * h];
                for (int a = 0; a < 5; a++) {
                    if (a == 2) continue;
                    int lx = st.vertical ? st.x0 + a : st.x0 + k, ly = st.vertical ? st.y0 + k : st.y0 + a;
                    boolean firstSide = a < 2, neg = firstSide != country.leftHand;
                    divided[ly * w + lx] = (byte) (st.vertical ? (neg ? 3 : 4) : (neg ? 2 : 1));
                }
            }
        }

        roundabouts();

        // Pick blocks for the military base and police stations.
        boolean[] used = new boolean[blocks.size()];
        // Hamlets out in the country are just homes, farms and the odd shop.
        for (int k = 0; k < blocks.size(); k++) if (blocks.get(k).length > 4 && blocks.get(k)[4] >= 2) used[k] = true;
        if (cfg.militaryBase()) {
            int best = -1, bestArea = 0;
            for (int k = 0; k < blocks.size(); k++) {
                int[] b = blocks.get(k);
                int bw = b[2] - b[0] - 2, bh = b[3] - b[1] - 2;
                if (used[k] || bw < 9 || bh < 9) continue;
                if (bw * bh > bestArea) {
                    bestArea = bw * bh;
                    best = k;
                }
            }
            if (best >= 0) used[best] = true;
            if (best >= 0) {
                int[] b = blocks.get(best);
                fill(b[0], b[1], b[2] - b[0], b[3] - b[1], SIDEWALK);
                militaryBase(b[0] + 1, b[1] + 1, b[2] - b[0] - 2, b[3] - b[1] - 2);
            }
        }
        for (int n = 0; n < cfg.policeStations(); n++) {
            List<Integer> options = new ArrayList<Integer>();
            for (int k = 0; k < blocks.size(); k++) {
                int[] b = blocks.get(k);
                if (used[k] || b[2] - b[0] - 2 < 6 || b[3] - b[1] - 2 < 6) continue;
                float cx = (b[0] + b[2]) / 2f * T, cy = (b[1] + b[3]) / 2f * T;
                boolean near = false;
                for (Facility f : facilities)
                    if ((f.kind == FACILITY_POLICE && Math.hypot(f.x - cx, f.y - cy) < w * T / (cfg.policeStations() > 3 ? 4.5f : 3f))
                            || Math.hypot(f.x - cx, f.y - cy) < Math.max(20, w / 6f) * T) near = true;
                if (!near) options.add(k);
            }
            if (options.isEmpty()) break;
            int k = options.get(rnd.nextInt(options.size()));
            used[k] = true;
            int[] b = blocks.get(k);
            fill(b[0], b[1], b[2] - b[0], b[3] - b[1], SIDEWALK);
            serviceBuilding(b[0] + 1, b[1] + 1, b[2] - b[0] - 2, b[3] - b[1] - 2, STATION, n + 1);
        }

        // One hospital, as far as it can be from the police stations.
        int hospital = -1;
        float far = -1;
        for (int k = 0; k < blocks.size(); k++) {
            int[] b = blocks.get(k);
            if (used[k] || b[2] - b[0] - 2 < 6 || b[3] - b[1] - 2 < 6) continue;
            float cx = (b[0] + b[2]) / 2f * T, cy = (b[1] + b[3]) / 2f * T, d = w * T;
            for (Facility f : facilities) d = Math.min(d, (float) Math.hypot(f.x - cx, f.y - cy));
            d += rnd.nextFloat() * T * 6;
            if (d > far) {
                far = d;
                hospital = k;
            }
        }
        if (hospital >= 0) {
            used[hospital] = true;
            int[] b = blocks.get(hospital);
            fill(b[0], b[1], b[2] - b[0], b[3] - b[1], SIDEWALK);
            serviceBuilding(b[0] + 1, b[1] + 1, b[2] - b[0] - 2, b[3] - b[1] - 2, HOSPITAL, 0);
        }

        // Landmarks: churches, schools, fire stations, supermarkets, gas stations and cemeteries.
        int[][] minSize = {{5, 5}, {8, 8}, {6, 6}, {8, 7}, {5, 5}, {7, 7}};
        for (int kind = 0; kind < 6; kind++)
            for (int n = 0; n < cfg.landmarks(kind); n++) {
                List<Integer> options = new ArrayList<Integer>();
                for (int k = 0; k < blocks.size(); k++) {
                    int[] b = blocks.get(k);
                    int iw = b[2] - b[0] - 2, ih = b[3] - b[1] - 2;
                    if (!used[k] && ((iw >= minSize[kind][0] && ih >= minSize[kind][1])
                            || (ih >= minSize[kind][0] && iw >= minSize[kind][1]))) options.add(k);
                }
                if (options.isEmpty()) break;
                int k = options.get(rnd.nextInt(options.size()));
                if (kind == 2) k = spreadOut(options, k);
                used[k] = true;
                int[] b = blocks.get(k);
                fill(b[0], b[1], b[2] - b[0], b[3] - b[1], SIDEWALK);
                int ix = b[0] + 1, iy = b[1] + 1, iw = b[2] - b[0] - 2, ih = b[3] - b[1] - 2;
                float ax = (ix + iw / 2f) * T, ay = (iy + ih / 2f) * T;
                switch (kind) {
                    case 0: church(ix, iy, iw, ih); break;
                    case 1: school(ix, iy, iw, ih); openAreas.add(new float[]{ax, ay, 2}); break;
                    case 2: serviceBuilding(ix, iy, iw, ih, FIRE_STATION, 0); break;
                    case 3: market(ix, iy, iw, ih); openAreas.add(new float[]{ax, ay, 2}); break;
                    case 4: gasStation(ix, iy, iw, ih); break;
                    default: cemetery(ix, iy, iw, ih); break;
                }
            }

        // The train station sits beside the railway.
        if (railY0 >= 0) {
            int best = -1;
            float bestD = Float.MAX_VALUE;
            for (int k = 0; k < blocks.size(); k++) {
                int[] b = blocks.get(k);
                if (used[k] || (b[3] != railY0 && b[1] != railY0 + railRows)) continue;
                if (b[2] - b[0] - 2 < 6 || b[3] - b[1] - 2 < 4) continue;
                float d = Math.abs((b[0] + b[2]) / 2f - w / 2f);
                if (d < bestD) {
                    bestD = d;
                    best = k;
                }
            }
            if (best >= 0) {
                used[best] = true;
                int[] b = blocks.get(best);
                fill(b[0], b[1], b[2] - b[0], b[3] - b[1], SIDEWALK);
                trainStation(b[0] + 1, b[1] + 1, b[2] - b[0] - 2, b[3] - b[1] - 2, b[3] == railY0);
            }
        }

        // Big sites: a shopping mall, a stadium and a power station, on blocks large enough to take them.
        boolean rural = cfg.density() == 0 && cfg.style() == CityConfig.STYLE_HOUSES;
        if (!rural) {
            int k = bigBlock(used, 11, 9, 0.45f);
            if (k >= 0) {
                used[k] = true;
                int[] b = blocks.get(k);
                fill(b[0], b[1], b[2] - b[0], b[3] - b[1], SIDEWALK);
                mall(b[0] + 1, b[1] + 1, b[2] - b[0] - 2, b[3] - b[1] - 2);
            }
        }
        if ((w >= 256 || cfg.density() == 2) && !rural) {
            int k = bigBlock(used, 12, 11, 0.6f);
            if (k >= 0) {
                used[k] = true;
                int[] b = blocks.get(k);
                fill(b[0], b[1], b[2] - b[0], b[3] - b[1], SIDEWALK);
                stadium(b[0] + 1, b[1] + 1, b[2] - b[0] - 2, b[3] - b[1] - 2);
            }
        }
        if (!rural) {
            int best = -1;
            float bestD = Float.MAX_VALUE;
            float ix0 = w / 2f + (float) Math.cos(industryAngle) * w * 0.4f, iy0 = h / 2f + (float) Math.sin(industryAngle) * h * 0.4f;
            for (int k = 0; k < blocks.size(); k++) {
                int[] b = blocks.get(k);
                if (used[k] || b[2] - b[0] - 2 < 8 || b[3] - b[1] - 2 < 7) continue;
                float d = (float) Math.hypot((b[0] + b[2]) / 2f - ix0, (b[1] + b[3]) / 2f - iy0);
                if (d < bestD) {
                    bestD = d;
                    best = k;
                }
            }
            if (best >= 0) {
                used[best] = true;
                int[] b = blocks.get(best);
                fill(b[0], b[1], b[2] - b[0], b[3] - b[1], SIDEWALK);
                powerStation(b[0] + 1, b[1] + 1, b[2] - b[0] - 2, b[3] - b[1] - 2);
            }
        }

        for (int k = 0; k < blocks.size(); k++) {
            int[] b = blocks.get(k);
            boolean plot = b.length > 4;
            if (plot && b[4] == 3) {
                fillNeighbourhood(b);
                continue;
            }
            if (used[k] && !(plot && b[4] == 2)) continue;
            int x0 = b[0], y0 = b[1], x1 = b[2], y1 = b[3];
            if (x1 - x0 < 1 || y1 - y0 < 1) continue;
            fill(x0, y0, x1 - x0, y1 - y0, SIDEWALK);
            int ix = x0 + 1, iy = y0 + 1, iw = x1 - x0 - 2, ih = y1 - y0 - 2;
            if (iw < 2 || ih < 2) continue;
            // Farms on the edge of town.
            float fcx = (x0 + x1) / 2f - w / 2f, fcy = (y0 + y1) / 2f - h / 2f;
            float fd = (float) Math.sqrt(fcx * fcx + fcy * fcy) / (w * 0.5f);
            float farmChance = rural ? 0.4f : cfg.style() == CityConfig.STYLE_HOUSES || cfg.density() == 0 ? 0.25f : cfg.density() == 2 ? 0 : 0.1f;
            curDistrict = districtType(ix + iw / 2, iy + ih / 2);
            if (curDistrict != DT_SUBURB && curDistrict != DT_PARKSIDE && curDistrict != DT_OLDTOWN) farmChance = 0;
            // (A massive map's town runs right out to the country band: only the odd farm inside it.)
            if (w >= 400) farmChance *= 0.25f;
            if (fd > 0.8f && iw >= 7 && ih >= 7 && rnd.nextFloat() < farmChance) {
                farm(ix, iy, iw, ih);
                continue;
            }
            if (plot) {
                // A village plot: a cottage or two in a garden, now and then a paddock or a shop.
                float r = rnd.nextFloat();
                if (r < 0.12f && iw >= 7 && ih >= 7) farm(ix, iy, iw, ih);
                else if (r < 0.3f) block(ix, iy, iw, ih);
                else {
                    fill(x0, y0, x1 - x0, y1 - y0, GRASS);
                    houses(ix, iy, iw, ih);
                }
                continue;
            }
            block(ix, iy, iw, ih);
        }

        if (railY0 >= 0)
            for (int x = 0; x < w; x++) {
                if (tiles[railY0 * w + x] != ROAD || underRailBridge(x)) continue;
                int x0 = x;
                while (x < w && tiles[railY0 * w + x] == ROAD) x++;
                crossings.add(new int[]{x0, x});
            }
        // Abandoned cars on the roads (never at intersections, never next to each other).
        float carChance = CAR_CHANCE[cfg.traffic()];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (tiles[y * w + x] != ROAD) continue;
                int dir = roadDir[y * w + x];
                if (dir == 0 || dir == 3 || hasNeighborDir(x, y)) continue;
                if (railY0 >= 0 && y >= railY0 - 1 && y <= railY0 + railRows) continue;
                if (rnd.nextFloat() > carChance) continue;
                if (hasNeighbor(x, y, CAR)) continue;
                // Parked against the kerb, never in the middle lanes.
                boolean vertical = dir == 1;
                boolean kerb = vertical ? !isRoad(x - 1, y) || !isRoad(x + 1, y) : !isRoad(x, y - 1) || !isRoad(x, y + 1);
                if (!kerb) continue;
                // Only where the road is wide enough to drive past, and well back from any junction.
                int ox = vertical ? (!isRoad(x - 1, y) ? -1 : 1) : 0, oy = vertical ? 0 : (!isRoad(x, y - 1) ? -1 : 1);
                int width = 0;
                while (width < 8 && isRoad(x - ox * width, y - oy * width)) width++;
                if (width < 4) continue;
                boolean nearJunction = false;
                for (int k = -6; k <= 6 && !nearJunction; k++)
                    if (vertical ? isRoad(x + ox, y + k) : isRoad(x + k, y + oy)) nearJunction = true;
                if (nearJunction) continue;
                tiles[y * w + x] = CAR;
            }
        }

        // Street names: main roads get avenues and boulevards, the rest streets, lanes and courts.
        List<String> names = new ArrayList<String>(Arrays.asList(country.streets));
        java.util.Collections.shuffle(names, rnd);
        int next = 0;
        for (Street st : streets) {
            String base = names.get(next++ % names.size());
            int len = st.vertical ? st.y1 - st.y0 : st.x1 - st.x0;
            if (st.x0 == 0 && st.y0 == 0 || st.x1 == w || st.y1 == h) st.name = country.ringRoad;
            else st.name = country.streetName(base, st.main || len > 40, rnd);
        }
        for (Facility f : facilities)
            if (f.kind == FACILITY_POLICE) {
                String street = placeName(f.x, f.y);
                int amp = street.indexOf(" & ");
                f.name = f.name + " (" + (amp > 0 ? street.substring(0, amp) : street) + ")";
            }

        // Street lamps on the pavement corners at junctions.
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++) {
                if (tiles[y * w + x] != SIDEWALK) continue;
                boolean horiz = paved(x - 1, y) || paved(x + 1, y), vert = paved(x, y - 1) || paved(x, y + 1);
                if (horiz && vert) lamps.add(new float[]{x * T + T / 2f, y * T + T / 2f});
            }
    }

    /** True if a neighbouring road tile is a junction (cars don't park there). */
    private boolean hasNeighborDir(int x, int y) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && roadDir[ny * w + nx] == 3) return true;
            }
        return false;
    }

    /** A fenced compound with gates, barracks, a helipad, tents, a watchtower and army trucks. */
    private void militaryBase(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, BASE);
        for (int i = x; i < x + bw; i++) {
            tiles[y * w + i] = FENCE;
            tiles[(y + bh - 1) * w + i] = FENCE;
        }
        for (int j = y; j < y + bh; j++) {
            tiles[j * w + x] = FENCE;
            tiles[j * w + x + bw - 1] = FENCE;
        }
        int gx = x + bw / 2 - 1;
        for (int i = gx; i < gx + 3; i++) {
            tiles[y * w + i] = BASE;
            tiles[(y + bh - 1) * w + i] = BASE;
        }
        int gy = y + bh / 2 - 1;
        for (int j = gy; j < gy + 3; j++) tiles[j * w + x + bw - 1] = BASE;

        int ax = x + 1, ay = y + 1, aw = bw - 2, ah = bh - 2;
        // Barracks along the top, leaving a lane by the fence and around the gate.
        int left = ax + 2, right = ax + aw - 2;
        if (aw >= 16) {
            for (int row = 0; row < 2 && ay + 1 + row * 6 + 4 <= ay + ah / 2; row++) {
                int by = ay + 2 + row * 6;
                int mid = gx - 2;
                if (mid - left >= 5) addFacilityLot(left, by, mid - left, 4, BARRACKS, 1);
                if (right - (gx + 5) >= 5) addFacilityLot(gx + 5, by, right - (gx + 5), 4, BARRACKS, 1);
            }
        } else {
            // Narrow base: long barracks down both sides of the parade ground.
            int len = Math.max(3, ah / 2 - 1);
            addFacilityLot(ax + 1, ay + 2, 4, len, BARRACKS, 1);
            addFacilityLot(ax + aw - 5, ay + 2, 4, len, BARRACKS, 1);
        }
        addFacilityLot(ax, ay + ah - 2, 2, 2, TOWER, 3);
        addFacilityLot(ax + aw - 2, ay, 2, 2, TOWER, 3);
        // Helipad and tents in the open lower half, trucks parked along the bottom right.
        helipads.add(new float[]{(ax + aw * 0.3f) * T, (ay + ah * 0.72f) * T});
        for (int i = 0; i < 4; i++)
            tents.add(new float[]{(ax + aw * 0.55f + (i % 2) * 2.2f) * T, (ay + ah * 0.55f + (i / 2) * 2f) * T});
        for (int i = ax + aw / 2 + 2; i < ax + aw - 1; i++) {
            int ty = ay + ah - 2;
            if (rnd.nextFloat() < 0.75f && tiles[ty * w + i] == BASE && !hasNeighbor(i, ty, CAR)) {
                tiles[ty * w + i] = CAR;
                carKind[ty * w + i] = 2;
            }
        }
        float cx = (ax + aw / 2f) * T, cy = (ay + ah * 0.62f) * T;
        float[] c = findWalkable(cx, cy);
        if (c == null) c = new float[]{cx, cy};
        float r = Math.min(110, Math.min(aw, ah) * T * 0.42f);
        Facility base = new Facility(FACILITY_BASE, c[0], c[1], r, (gx + 1.5f) * T, (y + bh - 2.5f) * T,
                country.bases[rnd.nextInt(country.bases.length)]);
        // Two guards inside each gate.
        float[][] gates = {{(gx + 1.5f) * T, (y + bh - 2.5f) * T}, {(gx + 1.5f) * T, (y + 1.5f) * T},
                {(x + bw - 2.5f) * T, (gy + 1.5f) * T}};
        for (int i = 0; i < gates.length; i++) {
            boolean across = i < 2;
            for (int k = -1; k <= 1; k += 2) {
                float[] p = findWalkable(gates[i][0] + (across ? k * 14 : 0), gates[i][1] + (across ? 0 : k * 14));
                if (p != null) base.posts.add(p);
            }
        }
        facilities.add(base);
    }

    /** A precinct with police cruisers, or a hospital with ambulances, parked next to it. */
    private boolean spareEngine;

    private void serviceBuilding(int x, int y, int bw, int bh, int kind, int number) {
        fill(x, y, bw, bh, LOT);
        spareEngine = false;
        boolean wide = bw >= bh;
        int sw = wide ? Math.max(3, bw / 2 - 1) : bw - 2, sh = wide ? bh - 2 : Math.max(3, bh / 2 - 1);
        addFacilityLot(x + 1, y + 1, sw, sh, kind, kind == HOSPITAL ? 4 : kind == FIRE_STATION ? 2 : 3);
        int lx = wide ? x + sw + 2 : x, ly = wide ? y : y + sh + 2;
        int lw = wide ? bw - sw - 2 : bw, lh = wide ? bh : bh - sh - 2;
        for (int j = ly + 1; j < ly + lh - 1; j += 3)
            for (int i = lx + 1; i < lx + lw - 1; i++)
                if (rnd.nextFloat() < (kind == HOSPITAL ? 0.35f : kind == FIRE_STATION ? 0.2f : 0.6f)) {
                    tiles[j * w + i] = CAR;
                    // (At a fire station: the crew's own cars, and a spare engine at most. Its engine is the real
                    // one, out on the apron.)
                    boolean spare = kind == FIRE_STATION && !spareEngine && rnd.nextFloat() < 0.3f;
                    if (spare) spareEngine = true;
                    carKind[j * w + i] = (byte) (kind == HOSPITAL ? 3 : kind == FIRE_STATION ? (spare ? 4 : 0) : 1);
                }
        float cx = (lx + lw / 2f) * T, cy = (ly + lh / 2f) * T;
        float[] c = findWalkable(cx, cy);
        if (c == null) c = new float[]{cx, cy};
        if (kind == HOSPITAL) {
            // A helipad on the far end of the car park, and an ambulance bay by the doors.
            int px = wide ? lx + lw - 4 : lx + lw - 4, py = wide ? ly + lh - 4 : ly + lh - 4;
            if (lw >= 5 && lh >= 5) {
                fill(px, py, 3, 3, LOT);
                addDecor(D_HELIPAD, px * T + 2, py * T + 2, (px + 3) * T - 2, (py + 3) * T - 2, 0);
            }
            float bx0 = wide ? (x + sw + 1) * T + 1 : (x + 1) * T, by0 = wide ? (y + 1) * T : (y + sh + 1) * T + 1;
            if (wide) addDecor(D_BAY, bx0, by0, bx0 + T - 2, by0 + Math.min(sh, 4) * T, 1);
            else addDecor(D_BAY, bx0, by0, bx0 + Math.min(sw, 4) * T, by0 + T - 2, 0);
            facilities.add(new Facility(FACILITY_HOSPITAL, c[0], c[1], 64, c[0], c[1], country.hospital));
            return;
        }
        if (kind == FIRE_STATION) {
            int n = 1;
            for (Facility f : facilities) if (f.kind == FACILITY_FIRE) n++;
            facilities.add(new Facility(FACILITY_FIRE, c[0], c[1], 48, c[0], c[1], country.fireStation + n));
            return;
        }
        Facility station = new Facility(FACILITY_POLICE, c[0], c[1], 64, c[0], c[1], country.policeStation + number);
        for (int k = -1; k <= 1; k += 2) {
            float[] p = findWalkable(c[0] + k * 18, c[1] + 10);
            if (p != null) station.posts.add(p);
        }
        facilities.add(station);
    }

    private void addFacilityLot(int x, int y, int lw, int lh, int kind, int floors) {
        fill(x, y, lw, lh, BUILDING);
        int roof, wall;
        if (kind == STATION) {
            roof = 0xFF2F4F86;
            wall = 0xFFD5D9DF;
        } else if (kind == HOSPITAL) {
            roof = 0xFFE9ECEF;
            wall = 0xFFE2E6EA;
        } else if (kind == FIRE_STATION) {
            roof = 0xFFA8322C;
            wall = 0xFFB8574A;
        } else if (kind == CHURCH || kind == SPIRE) {
            roof = 0xFF4A4550;
            wall = 0xFFB5AA98;
        } else if (kind == CRYPT) {
            roof = 0xFF8A8680;
            wall = 0xFF9E9A92;
        } else if (kind == SCHOOL) {
            roof = 0xFF6E6A62;
            wall = 0xFFA85A44;
        } else if (kind == MARKET) {
            roof = 0xFFB8BCC0;
            wall = 0xFFDADDE0;
        } else if (kind == KIOSK) {
            roof = 0xFFE8E8E8;
            wall = 0xFFD6DBE0;
        } else if (kind == TRAIN_STATION) {
            roof = 0xFF5E4A3A;
            wall = 0xFFC4A882;
        } else if (kind == SHOP) {
            roof = country.shopRoofs[rnd.nextInt(country.shopRoofs.length)];
            wall = country.shopWalls[rnd.nextInt(country.shopWalls.length)];
        } else if (kind == TOWER) {
            roof = 0xFF4B5536;
            wall = 0xFF6B7350;
        } else if (kind == MALL) {
            roof = 0xFFC8C4BC;
            wall = 0xFFE2DCD0;
        } else if (kind == STADIUM) {
            roof = 0xFF9EA2A8;
            wall = 0xFFB8BCC0;
        } else if (kind == POWER) {
            roof = 0xFF7C7F82;
            wall = 0xFF9A8A78;
        } else if (kind == BARN) {
            roof = 0xFF8E2E24;
            wall = 0xFFA83A2C;
        } else if (kind == SILO) {
            roof = 0xFFB8BCC0;
            wall = 0xFFC8CCD0;
        } else {
            roof = 0xFF5B6B3A;
            wall = 0xFF7C8456;
        }
        addVariantLot(x, y, lw, lh, roof, kind, floors, wall);
    }

    /** Fills the inside of one city block according to the map settings. */
    private void block(int x, int y, int bw, int bh) {
        curDistrict = districtType(x + bw / 2, y + bh / 2);
        float park = PARK_CHANCE[cfg.parks()];
        int style = cfg.style();
        switch (curDistrict) {
            case DT_DOWNTOWN: style = CityConfig.STYLE_OFFICES; park *= 0.5f; break;
            case DT_MIDTOWN: style = rnd.nextFloat() < 0.75f ? CityConfig.STYLE_OFFICES : CityConfig.STYLE_HOUSES; break;
            case DT_OLDTOWN: style = CityConfig.STYLE_OFFICES; break;
            case DT_SUBURB: style = CityConfig.STYLE_HOUSES; break;
            case DT_INDUSTRIAL: style = CityConfig.STYLE_WAREHOUSES; park *= 0.3f; break;
            case DT_CAMPUS: style = CityConfig.STYLE_OFFICES; park = Math.max(park, 0.2f) * 1.6f; break;
            default: style = CityConfig.STYLE_HOUSES; park = Math.max(park, 0.15f) * 2.5f; break;
        }
        float plaza = style == CityConfig.STYLE_HOUSES ? 0.03f : 0.07f;
        if (cfg.parks() == 0) plaza *= 0.5f;
        float parking = style == CityConfig.STYLE_WAREHOUSES ? 0.15f : 0.05f;
        float open = cfg.density() == 0 ? 0.15f : 0f;
        if (cfg.density() == 2) {
            park *= 0.6f;
            plaza *= 0.6f;
        }
        float roll = rnd.nextFloat();
        float ax = (x + bw / 2f) * T, ay = (y + bh / 2f) * T;
        if (roll < park) {
            parkVariant(x, y, bw, bh);
            openAreas.add(new float[]{ax, ay, 0});
        } else if ((roll -= park) < plaza) {
            plaza(x, y, bw, bh);
            openAreas.add(new float[]{ax, ay, 1});
        } else if ((roll -= plaza) < parking) {
            parking(x, y, bw, bh);
            openAreas.add(new float[]{ax, ay, 2});
        } else if ((roll -= parking) < open) {
            boolean isPark = rnd.nextBoolean();
            if (isPark) park(x, y, bw, bh);
            else plaza(x, y, bw, bh);
            openAreas.add(new float[]{ax, ay, isPark ? 0 : 1});
        } else {
            // Zoning: an office core, apartments around it, shops along the main roads, houses further
            // out and an industrial district on one side of town.
            float cx = x + bw / 2f - w / 2f, cy = y + bh / 2f - h / 2f;
            float d = (float) Math.sqrt(cx * cx + cy * cy) / (w * 0.5f);
            float ang = (float) Math.atan2(cy, cx) - industryAngle;
            while (ang > Math.PI) ang -= Math.PI * 2;
            while (ang < -Math.PI) ang += Math.PI * 2;
            boolean industrialZone = curDistrict == DT_INDUSTRIAL;
            boolean mainRoad = onMainRoad(x - 1, y - 1, x + bw + 1, y + bh + 1);
            float r = rnd.nextFloat();
            // Shops line the main roads; side streets are mostly homes and offices.
            int shopChance = mainRoad ? cfg.shopShare() + 20 : cfg.shopShare() / 3;
            if (curDistrict == DT_OLDTOWN) shopChance = Math.max(shopChance, 45);
            if (curDistrict == DT_MIDTOWN && mainRoad) shopChance += 15;
            if (curDistrict == DT_OLDTOWN && bw >= 10 && bh >= 10 && rnd.nextFloat() < 0.55f) {
                terraces(x, y, bw, bh);
                return;
            }
            if (curDistrict == DT_CAMPUS && bw >= 12 && bh >= 12) {
                // Campus: halls on lawns.
                fill(x, y, bw, bh, GRASS);
                lots(x + 2, y + 2, bw - 4, bh - 4, 1);
                return;
            }
            if (style == CityConfig.STYLE_HOUSES && !mainRoad) shopChance = cfg.shopShare() / 6;
            if (style != CityConfig.STYLE_HOUSES && bw >= 12 && bh >= 12 && rnd.nextFloat() < 0.05f) constructionSite(x, y, bw, bh);
            else if (rnd.nextInt(100) < shopChance && bw >= 10 && bh >= 10 && style != CityConfig.STYLE_WAREHOUSES) shops(x, y, bw, bh);
            else if (style == CityConfig.STYLE_HOUSES && rnd.nextInt(100) < cfg.buildingMix()[0] / 2 && bw >= 10 && bh >= 10) {
                // An apartment block on the edge of the suburbs.
                fill(x, y, bw, bh, GRASS);
                addLot(x + 2, y + 2, bw - 4, bh - 4, APARTMENT);
            } else if (style == CityConfig.STYLE_HOUSES) houses(x, y, bw, bh);
            else if (style == CityConfig.STYLE_WAREHOUSES) warehouses(x, y, bw, bh);
            else if (cfg.density() == 0 && bw >= 10 && bh >= 10) {
                fill(x, y, bw, bh, GRASS);
                lots(x + 2, y + 2, bw - 4, bh - 4, 0);
            } else {
                lots(x, y, bw, bh, 0);
            }
        }
    }

    /** Which way the road on this tile runs: 1 north-south, 2 east-west, 3 a junction, 0 not a road. */
    int roadDirAt(int x, int y) {
        return x < 0 || y < 0 || x >= w || y >= h ? 0 : roadDir[y * w + x];
    }

    private boolean isRoad(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && tiles[y * w + x] == ROAD;
    }

    private boolean hasNeighbor(int x, int y, byte type) {
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && tiles[ny * w + nx] == type) return true;
            }
        return false;
    }

    private void fill(int x, int y, int fw, int fh, byte t) {
        for (int j = y; j < y + fh; j++)
            for (int i = x; i < x + fw; i++)
                if (i >= 0 && j >= 0 && i < w && j < h) tiles[j * w + i] = t;
    }

    private void lots(int x, int y, int lw, int lh, int depth) {
        int maxDepth = cfg.density() == 2 ? 2 : 3;
        if (depth < maxDepth && lw >= 13 && lw >= lh && rnd.nextFloat() < 0.75f) {
            int cut = 6 + rnd.nextInt(lw - 12);
            lots(x, y, cut, lh, depth + 1);
            lots(x + cut + 1, y, lw - cut - 1, lh, depth + 1);
            if (lh >= 4) addDecor(D_ALLEY, (x + cut) * T + 1, y * T, (x + cut + 1) * T - 1, (y + lh) * T, 0);
            return;
        }
        if (depth < maxDepth && lh >= 13 && rnd.nextFloat() < 0.75f) {
            int cut = 6 + rnd.nextInt(lh - 12);
            lots(x, y, lw, cut, depth + 1);
            lots(x, y + cut + 1, lw, lh - cut - 1, depth + 1);
            if (lw >= 4) addDecor(D_ALLEY, x * T, (y + cut) * T + 1, (x + lw) * T, (y + cut + 1) * T - 1, 0);
            return;
        }
        if (lw < 3 || lh < 3) return;
        addLot(x, y, lw, lh, officeKind(lw, lh));
    }

    /** Most lots are offices; some maps mix in apartment blocks, parking garages and pharmacies. */
    private int officeKind(int lw, int lh) {
        int[] mix = cfg.buildingMix();
        int r = rnd.nextInt(100);
        if (r < mix[0] && lw >= 6 && lh >= 6) return APARTMENT;
        if ((r -= mix[0]) < mix[1] && lw >= 8 && lh >= 8) return GARAGE;
        if ((r -= mix[1]) < mix[2] && lw <= 8 && lh <= 8) return PHARMACY;
        return OFFICE;
    }

    private void houses(int x, int y, int pw, int ph) {
        if ((pw >= 22 || ph >= 22) && Math.min(pw, ph) >= 14 && rnd.nextFloat() < 0.7f) {
            culDeSac(x, y, pw, ph);
            return;
        }
        fill(x, y, pw, ph, GRASS);
        // Houses set back from the pavement, each in its own garden.
        for (int cy = y + 1; cy + 4 <= y + ph; cy += 7)
            for (int cx = x + 1; cx + 4 <= x + pw; cx += 7) {
                int hw = Math.min(4 + rnd.nextInt(2), x + pw - cx);
                int hh = Math.min(4 + rnd.nextInt(2), y + ph - cy);
                if (hw >= 4 && hh >= 4 && rnd.nextFloat() > 0.1f) addLot(cx, cy, hw, hh, HOUSE);
            }
        for (int j = y; j < y + ph; j++)
            for (int i = x; i < x + pw; i++)
                if (tiles[j * w + i] == GRASS && rnd.nextFloat() < 0.1f && !hasNeighbor(i, j, TREE)
                        && !hasNeighbor(i, j, BUILDING))
                    tiles[j * w + i] = TREE;
    }

    /**
     * A big residential block with a dead-end lane running into it, a turning circle at the end and
     * houses along both sides.
     */
    private void culDeSac(int x, int y, int pw, int ph) {
        fill(x, y, pw, ph, GRASS);
        boolean vertical = ph >= pw;
        int len = (vertical ? ph : pw) * 2 / 3;
        int mid = vertical ? x + pw / 2 - 1 : y + ph / 2 - 1;
        boolean fromStart = rnd.nextBoolean();
        // The lane (2 tiles wide), entering from one end of the block.
        for (int k = 0; k < len; k++)
            for (int t = 0; t < 2; t++) {
                int along = fromStart ? k : (vertical ? ph : pw) - 1 - k;
                int i = vertical ? mid + t : (x + along), j = vertical ? (y + along) : mid + t;
                tiles[j * w + i] = ROAD;
            }
        // Where the lane meets the street, the pavement is dropped.
        for (int t = 0; t < 2; t++) {
            int i = vertical ? mid + t : (fromStart ? x - 1 : x + pw), j = vertical ? (fromStart ? y - 1 : y + ph) : mid + t;
            if (i >= 0 && j >= 0 && i < w && j < h && tiles[j * w + i] == SIDEWALK) tiles[j * w + i] = ROAD;
        }
        // Turning circle.
        int endAlong = fromStart ? len - 1 : (vertical ? ph : pw) - len;
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 2; b++) {
                int i = vertical ? mid + b : x + endAlong + a, j = vertical ? y + endAlong + a : mid + b;
                if (i >= x && j >= y && i < x + pw && j < y + ph) tiles[j * w + i] = ROAD;
            }
        // Houses facing the lane on both sides, then more filling the rest of the block.
        for (int k = 1; k + 4 <= (vertical ? ph : pw) - 1; k += 6)
            for (int side = 0; side < 2; side++) {
                int hw = 4, hd = 4 + rnd.nextInt(2);
                int along = (vertical ? y : x) + k;
                int across = side == 0 ? mid - 2 - hd : mid + 4;
                int lx = vertical ? across : along, ly = vertical ? along : across;
                placeHouse(lx, ly, vertical ? hd : hw, vertical ? hw : hd, x, y, pw, ph);
            }
        for (int cy = y + 1; cy + 4 <= y + ph; cy += 6)
            for (int cx = x + 1; cx + 4 <= x + pw; cx += 6) placeHouse(cx, cy, 4, 4, x, y, pw, ph);
        for (int j = y; j < y + ph; j++)
            for (int i = x; i < x + pw; i++)
                if (tiles[j * w + i] == GRASS && rnd.nextFloat() < 0.1f && !hasNeighbor(i, j, TREE)
                        && !hasNeighbor(i, j, BUILDING) && !hasNeighbor(i, j, ROAD))
                    tiles[j * w + i] = TREE;
    }

    /** A house if the spot is inside the block, on grass, and has a garden's width from everything else. */
    private void placeHouse(int lx, int ly, int lw, int lh, int x, int y, int pw, int ph) {
        if (lx < x || ly < y || lx + lw > x + pw || ly + lh > y + ph) return;
        for (int j = ly - 1; j <= ly + lh; j++)
            for (int i = lx - 1; i <= lx + lw; i++) {
                if (i < x || j < y || i >= x + pw || j >= y + ph) continue;
                byte t = tiles[j * w + i];
                boolean inside = i >= lx && j >= ly && i < lx + lw && j < ly + lh;
                if (t == BUILDING || (inside && t != GRASS)) return;
            }
        if (rnd.nextFloat() > 0.06f) addLot(lx, ly, lw, lh, HOUSE);
    }

    /** A station building facing the street, a platform along the tracks and a small car park. */
    private void trainStation(int x, int y, int bw, int bh, boolean railBelow) {
        fill(x, y, bw, bh, PLAZA);
        stationX = (x + bw / 2f) * T;
        stationY = railBelow ? (y + bh - 0.5f) * T : (y + 0.5f) * T;
        stationName = "Central Station";
        int sw = Math.min(bw - 2, Math.max(12, bw * 2 / 3)), sh = Math.max(4, Math.min(8, bh - 3));
        int sx = x + (bw - sw) / 2, sy = railBelow ? y + 1 : y + bh - 1 - sh;
        addFacilityLot(sx, sy, sw, sh, TRAIN_STATION, 2);
        // Platform canopy along the tracks.
        int py = railBelow ? y + bh - 1 : y;
        addDecor(D_CANOPY, x * T + 4, py * T + 2, (x + bw) * T - 4, py * T + T - 2, 1);
        openAreas.add(new float[]{(x + bw / 2f) * T, (y + bh / 2f) * T, 1});
    }

    private static final int[] OLD_WALLS = {0xFFA8543A, 0xFFB86A48, 0xFFD8C8A8, 0xFF9A4A38, 0xFFC8B090, 0xFFE0D2B4};
    private static final int[] OLD_ROOFS = {0xFF8A4A32, 0xFF6E4A3E, 0xFF5A5E66, 0xFF9A5838, 0xFF7A3E2E};
    private static final int[] GLASS_WALLS = {0xFF6F8898, 0xFF7C94A4, 0xFF5E7686, 0xFF8898A4, 0xFF6A7E8C};

    /**
     * An old-town block: narrow terraced buildings shoulder to shoulder around the edge, shops on the ground
     * floor, and a courtyard in the middle.
     */
    private void terraces(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, PLAZA);
        int depth = bh >= 16 && bw >= 16 ? 6 : 4;
        for (int side = 0; side < 4; side++) {
            boolean horizontal = side < 2;
            int len = horizontal ? bw : bh - 2 * depth;
            int i = 0;
            boolean arch = !horizontal;
            while (i < len) {
                // An archway through the front and back rows into the courtyard.
                if (!arch && i >= len / 2 - 1) {
                    arch = true;
                    i += 2;
                    continue;
                }
                int uw = Math.min(3 + rnd.nextInt(3), len - i);
                if (uw < 3) break;
                int lx, ly, lw, lh;
                if (side == 0) { lx = x + i; ly = y; lw = uw; lh = depth; }
                else if (side == 1) { lx = x + i; ly = y + bh - depth; lw = uw; lh = depth; }
                else if (side == 2) { lx = x; ly = y + depth + i; lw = depth; lh = uw; }
                else { lx = x + bw - depth; ly = y + depth + i; lw = depth; lh = uw; }
                if (rnd.nextFloat() < 0.45f) addFacilityLot(lx, ly, lw, lh, SHOP, 2 + rnd.nextInt(2));
                else addLot(lx, ly, lw, lh, rnd.nextFloat() < 0.4f ? APARTMENT : OFFICE);
                i += uw;
            }
        }
        // A tree or two in the courtyard.
        int cx = x + bw / 2, cy = y + bh / 2;
        if (bw >= 14 && bh >= 14 && tiles[cy * w + cx] == PLAZA) tiles[cy * w + cx] = TREE;
    }

    private void warehouses(int x, int y, int pw, int ph) {
        fill(x, y, pw, ph, LOT);
        int bx = x + 1, by = y + 1, bw = pw - 2, bh = ph - 2;
        if (bw < 3 || bh < 3) {
            parking(x, y, pw, ph);
            return;
        }
        if (bw >= 10 && rnd.nextBoolean()) {
            int cut = bw / 2 - 1;
            addLot(bx, by, cut, bh, WAREHOUSE);
            addLot(bx + cut + 2, by, bw - cut - 2, bh, WAREHOUSE);
        } else {
            addLot(bx, by, bw, bh, WAREHOUSE);
        }
        for (int j = y; j < y + ph; j++)
            for (int i = x; i < x + pw; i++)
                if (tiles[j * w + i] == LOT && rnd.nextFloat() < 0.12f && !hasNeighbor(i, j, CAR))
                    tiles[j * w + i] = CAR;
    }

    private int oldWall() {
        int[] a = country.oldWalls != null ? country.oldWalls : OLD_WALLS;
        return a[rnd.nextInt(a.length)];
    }

    private int oldRoof() {
        int[] a = country.oldRoofs != null ? country.oldRoofs : OLD_ROOFS;
        return a[rnd.nextInt(a.length)];
    }

    private void addLot(int x, int y, int lw, int lh, int kind) {
        fill(x, y, lw, lh, BUILDING);
        int floors, roof, wall;
        int height = cfg.height();
        if (kind == HOUSE) {
            floors = 1 + rnd.nextInt(2);
            roof = country.houseRoofs[rnd.nextInt(country.houseRoofs.length)];
            wall = country.houseWalls[rnd.nextInt(country.houseWalls.length)];
        } else if (kind == WAREHOUSE) {
            floors = 1 + rnd.nextInt(2) + (height >= 2 ? 1 : 0);
            roof = WAREHOUSE_ROOFS[rnd.nextInt(WAREHOUSE_ROOFS.length)];
            wall = WAREHOUSE_WALLS[rnd.nextInt(WAREHOUSE_WALLS.length)];
        } else if (kind == APARTMENT) {
            floors = 3 + rnd.nextInt(4) + height * 2;
            roof = darken(ROOFS[rnd.nextInt(ROOFS.length)], 0.9f);
            wall = country.apartmentWalls[rnd.nextInt(country.apartmentWalls.length)];
        } else if (kind == GARAGE) {
            floors = 3 + rnd.nextInt(2);
            roof = 0xFF6C6E70;
            wall = 0xFFA8A8A2;
        } else if (kind == PHARMACY) {
            floors = 1 + rnd.nextInt(2);
            roof = 0xFFE4E8E4;
            wall = 0xFFF0F2EE;
        } else {
            // Downtown (the middle of the map) gets the tallest towers; small lots stay low.
            float cx = x + lw / 2f - w / 2f, cy = y + lh / 2f - h / 2f;
            float dt = 1 - Math.min(1, (float) Math.sqrt(cx * cx + cy * cy) / (w * 0.55f));
            float r = rnd.nextFloat();
            switch (height) {
                case 0: floors = 1 + rnd.nextInt(3); break;
                case 1: floors = 2 + rnd.nextInt(3) + (int) (dt * dt * r * 9); break;
                case 2: floors = 3 + rnd.nextInt(4) + (int) (dt * dt * r * 14); break;
                default: floors = 4 + rnd.nextInt(5) + (int) (dt * r * 18); break;
            }
            int min = Math.min(lw, lh);
            if (min <= 2) floors = Math.min(floors, 4);
            else if (min <= 3) floors = Math.min(floors, 8);
            else if (min <= 4) floors = Math.min(floors, 14);
            floors = Math.min(floors, 22);
            int[] roofs = country.officeRoofs != null ? country.officeRoofs : ROOFS;
            int[] walls = country.officeWalls != null ? country.officeWalls : WALLS;
            roof = roofs[rnd.nextInt(roofs.length)];
            wall = walls[rnd.nextInt(walls.length)];
            if (curDistrict == DT_DOWNTOWN) {
                // Downtown towers climb higher and many are clad in glass.
                floors = Math.min(24, floors + 2 + rnd.nextInt(3));
                if (rnd.nextFloat() < 0.55f) wall = GLASS_WALLS[rnd.nextInt(GLASS_WALLS.length)];
            } else if (curDistrict == DT_OLDTOWN || curDistrict == DT_CAMPUS) {
                // Old brick and stone, a few storeys high.
                floors = 2 + rnd.nextInt(3);
                wall = oldWall();
                roof = oldRoof();
            } else if (curDistrict == DT_SUBURB || curDistrict == DT_PARKSIDE) {
                floors = Math.min(floors, 5);
            }
        }
        if (kind == APARTMENT && curDistrict == DT_OLDTOWN) {
            floors = Math.min(floors, 5);
            wall = oldWall();
            roof = oldRoof();
        }
        addVariantLot(x, y, lw, lh, roof, kind, floors, wall);
    }

    /**
     * Records a lot, maybe as one of the country's and district's own kinds of building (a café, a factory,
     * a konbini...), which sets its roof, walls and height.
     */
    private void addVariantLot(int x, int y, int lw, int lh, int roof, int kind, int floors, int wall) {
        int dist = districtType(x + lw / 2, y + lh / 2);
        int var = Variants.pick(kind, country.id, dist, lw, lh, rnd);
        Variants.V v = Variants.get(var);
        if (v != null && v.floorsMin > 0) floors = v.floorsMin + rnd.nextInt(Math.max(1, v.floorsMax - v.floorsMin + 1));
        // (One of the newer kinds instead, now and then: chosen with numbers of its own, so the rest of the
        // city is laid out exactly as before.)
        int swapped = Variants.swap(var, kind, country.id, dist, lw, lh);
        if (swapped != var) {
            var = swapped;
            v = Variants.get(var);
            if (v != null && v.floorsMin > 0) floors = v.floorsMin + Variants.extra.nextInt(Math.max(1, v.floorsMax - v.floorsMin + 1));
        }
        if (v != null) {
            // Small lots stay low whatever they are.
            int min = Math.min(lw, lh);
            if (min <= 5) floors = Math.min(floors, 6);
            else if (min <= 8) floors = Math.min(floors, 14);
            roof = v.roofCol;
            if (v.wall != 0) wall = v.wall;
        }
        buildingLots.add(new int[]{x, y, lw, lh, roof, rnd.nextInt(100000), kind, floors, wall, var});
    }

    private void addDecor(int kind, float x0, float y0, float x1, float y1, int variant) {
        decor.add(new float[]{kind, x0, y0, x1, y1, variant});
    }

    /** A row of small shops around the edge of the block with a service lot behind. */
    private void shops(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, LOT);
        int depth = bh >= 16 ? 6 : 4;
        for (int side = 0; side < 2; side++) {
            int sy = side == 0 ? y : y + bh - depth;
            int i = x;
            while (i < x + bw) {
                int uw = Math.min(3 + rnd.nextInt(3), x + bw - i);
                if (uw >= 3) addFacilityLot(i, sy, uw, depth, SHOP, 1 + (rnd.nextFloat() < 0.3f ? 1 : 0));
                i += uw;
            }
        }
        // Behind the shopfronts: offices and flats (a service yard with a few parked cars in the suburbs).
        int iy0 = y + depth + 1, iy1 = y + bh - depth - 1;
        if (iy1 - iy0 >= 8 && curDistrict != DT_SUBURB && curDistrict != DT_PARKSIDE) {
            fill(x, iy0, bw, iy1 - iy0, SIDEWALK);
            lots(x + 1, iy0 + 1, bw - 2, iy1 - iy0 - 2, 1);
            return;
        }
        for (int j = iy0; j < iy1; j++)
            for (int i = x + 1; i < x + bw - 1; i++)
                if (rnd.nextFloat() < 0.12f && !hasNeighbor(i, j, CAR)) tiles[j * w + i] = CAR;
    }

    /** A church with a steeple, and a churchyard with graves. */
    private void church(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, GRASS);
        boolean wide = bw >= bh;
        int cw = wide ? Math.min(bw - 6, Math.max(8, bw / 2)) : Math.min(bw - 4, 8);
        int ch = wide ? Math.min(bh - 4, 8) : Math.min(bh - 6, Math.max(8, bh / 2));
        addFacilityLot(x + 2, y + 2, cw, ch, CHURCH, 3);
        if (wide) addFacilityLot(x + 2 + cw, y + 2 + ch / 2 - 1, 2, 2, SPIRE, 8);
        else addFacilityLot(x + 2 + cw / 2 - 1, y + 2 + ch, 2, 2, SPIRE, 8);
        graves(x, y, bw, bh, 0.5f);
    }

    private void cemetery(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, GRASS);
        int cx = x + bw / 2, cy = y + bh / 2;
        for (int i = x; i < x + bw; i++) tiles[cy * w + i] = PLAZA;
        for (int j = y; j < y + bh; j++) tiles[j * w + cx] = PLAZA;
        if (bw >= 14 && bh >= 14) addFacilityLot(cx + 2, cy + 2, 3, 3, CRYPT, 1);
        graves(x, y, bw, bh, 0.8f);
        for (int j = y; j < y + bh; j++)
            for (int i = x; i < x + bw; i++)
                if ((i == x || i == x + bw - 1 || j == y || j == y + bh - 1) && tiles[j * w + i] == GRASS
                        && rnd.nextFloat() < 0.25f && !hasNeighbor(i, j, TREE)) tiles[j * w + i] = TREE;
    }

    /** Rows of headstones on the grass (they are decoration; people walk between them). */
    private void graves(int x, int y, int bw, int bh, float density) {
        for (int j = y + 1; j < y + bh - 1; j++)
            for (int i = x + 1; i < x + bw - 1; i++) {
                if (tiles[j * w + i] != GRASS || rnd.nextFloat() > density) continue;
                for (int k = 0; k < 2; k++) {
                    float gx = i * T + 4 + k * 8, gy = j * T + 5;
                    addDecor(D_GRAVE, gx, gy, gx + 4, gy + 6, rnd.nextInt(3));
                }
            }
    }

    /** A school: the building, a basketball court on the tarmac and a sports field. */
    private void school(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, LOT);
        boolean wide = bw >= bh;
        int sw = wide ? Math.max(4, bw / 2 - 1) : bw - 2, sh = wide ? bh - 2 : Math.max(4, bh / 2 - 1);
        addFacilityLot(x + 1, y + 1, sw, sh, SCHOOL, 2 + rnd.nextInt(2));
        int rx = wide ? x + sw + 2 : x + 1, ry = wide ? y + 1 : y + sh + 2;
        int rw = wide ? bw - sw - 3 : bw - 2, rh = wide ? bh - 2 : bh - sh - 3;
        if (rw < 3 || rh < 3) return;
        if (rw * rh >= 30) {
            fill(rx, ry, rw, rh, GRASS);
            addDecor(D_FIELD, rx * T + 3, ry * T + 3, (rx + rw) * T - 3, (ry + rh) * T - 3, 0);
        } else {
            addDecor(D_COURT, rx * T + 2, ry * T + 2, (rx + rw) * T - 2, (ry + rh) * T - 2, 0);
        }
    }

    /** A supermarket and its big car park. */
    private void market(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, LOT);
        boolean wide = bw >= bh;
        int mw = wide ? bw - 2 : Math.max(4, bw / 2), mh = wide ? Math.max(4, bh / 2) : bh - 2;
        addFacilityLot(x + 1, y + 1, mw, mh, MARKET, 1);
        int px = wide ? x : x + mw + 2, py = wide ? y + mh + 2 : y;
        int pw = wide ? bw : bw - mw - 2, ph = wide ? bh - mh - 2 : bh;
        for (int j = py; j < py + ph; j++) {
            if ((j - py) % 3 != 1) continue;
            for (int i = px + 1; i < px + pw - 1; i++) if (rnd.nextFloat() < 0.5f) tiles[j * w + i] = CAR;
        }
    }

    /** A gas station: canopy over the pumps and a little shop. */
    private void gasStation(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, LOT);
        addFacilityLot(x + bw - 5, y + 1, 4, 3, KIOSK, 1);
        int cx0 = x + 1, cy0 = y + 1, cx1 = x + bw - 6, cy1 = y + bh - 1;
        if (cx1 - cx0 < 2) {
            int j = y + bh / 2;
            tiles[j * w + x + 1] = PUMP;
            pumps.add(new float[]{(x + 1) * T + T / 2f, j * T + T / 2f, 1});
            return;
        }
        addDecor(D_CANOPY, cx0 * T + 2, cy0 * T + 2, cx1 * T - 2, cy1 * T - 2, 0);
        // Two islands of pumps under the canopy, well spaced (at least one).
        int placed = 0;
        for (int j = cy0 + 1; j < cy1 - 1 && placed < 4; j += 3)
            for (int i = cx0 + 1; i < cx1 - 1 && placed < 4; i += 3) {
                tiles[j * w + i] = PUMP;
                pumps.add(new float[]{i * T + T / 2f, j * T + T / 2f, 1});
                placed++;
            }
        if (placed == 0) {
            int i = (cx0 + cx1) / 2, j = (cy0 + cy1) / 2;
            tiles[j * w + i] = PUMP;
            pumps.add(new float[]{i * T + T / 2f, j * T + T / 2f, 1});
        }
    }

    /** Picks a kind of park by the map's taste. */
    private void parkVariant(int x, int y, int bw, int bh) {
        int[] mix = cfg.parkMix();
        int total = 0;
        for (int m : mix) total += m;
        int r = rnd.nextInt(Math.max(1, total)), kind = 0;
        while (kind < mix.length - 1 && r >= mix[kind]) r -= mix[kind++];
        if (kind == 2 && (bw < 7 || bh < 6)) kind = 0;
        switch (kind) {
            case 1:
                park(x, y, bw, bh);
                // A playground in one corner, clear of the paths.
                int qw = Math.max(2, bw / 2 - 1), qh = Math.max(2, bh / 2 - 1);
                for (int j = y; j < y + qh; j++) for (int i = x; i < x + qw; i++) if (tiles[j * w + i] == TREE) tiles[j * w + i] = GRASS;
                addDecor(D_PLAYGROUND, x * T + 3, y * T + 3, (x + qw) * T - 2, (y + qh) * T - 2, 0);
                break;
            case 2:
                fill(x, y, bw, bh, GRASS);
                addDecor(D_FIELD, x * T + 4, y * T + 4, (x + bw) * T - 4, (y + bh) * T - 4, 0);
                break;
            case 3:
                fill(x, y, bw, bh, LOT);
                // Courts at their real size, as many as fit (each about 11 x 7 tiles with its run-off).
                boolean longX = bw >= bh;
                int courtsX = Math.max(1, Math.min(4, bw / (longX ? 12 : 8))), courtsY = Math.max(1, Math.min(4, bh / (longX ? 8 : 12)));
                float cw = bw * T / (float) courtsX, chh = bh * T / (float) courtsY;
                for (int a = 0; a < courtsX; a++)
                    for (int b = 0; b < courtsY; b++)
                        addDecor(D_COURT, x * T + a * cw + 4, y * T + b * chh + 4, x * T + (a + 1) * cw - 4,
                                y * T + (b + 1) * chh - 4, rnd.nextInt(2));
                break;
            case 4:
                fill(x, y, bw, bh, GRASS);
                addDecor(D_GARDEN, x * T + 4, y * T + 4, (x + bw) * T - 4, (y + bh) * T - 4, rnd.nextInt(3));
                break;
            case 5:
                fill(x, y, bw, bh, PLAZA);
                addDecor(D_SKATE, x * T + 6, y * T + 6, (x + bw) * T - 6, (y + bh) * T - 6, 0);
                break;
            default:
                park(x, y, bw, bh);
                if (bw >= 6 && bh >= 6 && rnd.nextFloat() < 0.45f) bandstand(x, y, bw, bh);
                break;
        }
    }

    /** A bandstand in the middle of the park, with flower beds around it. */
    private void bandstand(int x, int y, int bw, int bh) {
        int cx = x + bw / 2, cy = y + bh / 2;
        for (int j = cy - 1; j <= cy + 1; j++)
            for (int i = cx - 1; i <= cx + 1; i++) tiles[j * w + i] = PLAZA;
        addDecor(D_BANDSTAND, (cx - 1) * T, (cy - 1) * T, (cx + 2) * T, (cy + 2) * T, 0);
        int[][] beds = {{x + 1, y + 1}, {x + bw - 3, y + 1}, {x + 1, y + bh - 3}, {x + bw - 3, y + bh - 3}};
        for (int[] b : beds) {
            boolean clear = true;
            for (int j = b[1]; j < b[1] + 2; j++)
                for (int i = b[0]; i < b[0] + 2; i++) if (tiles[j * w + i] != GRASS && tiles[j * w + i] != TREE) clear = false;
            if (!clear) continue;
            fill(b[0], b[1], 2, 2, GRASS);
            addDecor(D_FLOWERS, b[0] * T + 3, b[1] * T + 3, (b[0] + 2) * T - 3, (b[1] + 2) * T - 3, rnd.nextInt(3));
        }
    }

    /**
     * Open country around the town: dirt roads winding out from the ring road to the edge of the map, lanes off
     * them to farms and cabins, and woods.
     */
    private void countryside(int bx0, int by0, int bx1, int by1) {
        int[][] outs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        List<int[]> lane = new ArrayList<int[]>();
        // A village's lanes carry on from the ends of its streets.
        for (int[] e : villageEnds) dirtRoad(e[0], e[1], e[2], e[3], lane);
        for (int[] o : villageEnds.isEmpty() ? outs : new int[0][]) {
            int roads = 1 + (w > 360 ? 1 : 0) + (rnd.nextFloat() < 0.4f ? 1 : 0);
            for (int k = 0; k < roads; k++) {
                // Start on the ring road, somewhere along this side (clear of the railway).
                int x, y;
                if (o[0] == 0) {
                    x = bx0 + 6 + rnd.nextInt(Math.max(1, bx1 - bx0 - 12));
                    y = o[1] < 0 ? by0 : by1 - 1;
                } else {
                    x = o[0] < 0 ? bx0 : bx1 - 1;
                    y = by0 + 6 + rnd.nextInt(Math.max(1, by1 - by0 - 12));
                    if (railY0 >= 0 && Math.abs(y - railY0) < 6) y = railY0 - 6;
                }
                dirtRoad(x, y, o[0], o[1], lane);
            }
        }
        // On big maps, side tracks branch off the lanes out to the remote corners.
        int branches = w > 360 ? 4 + rnd.nextInt(3) : w > 240 ? 1 + rnd.nextInt(2) : 0;
        int mains = lane.size();
        for (int k = 0; k < branches && mains > 0; k++) {
            int[] c = lane.get(rnd.nextInt(mains));
            if (c[0] >= bx0 - 8 && c[0] < bx1 + 8 && c[1] >= by0 - 8 && c[1] < by1 + 8) continue;
            int s = rnd.nextBoolean() ? 1 : -1;
            if (c[2] == 1) dirtRoad(c[0], c[1], s, 0, lane);
            else dirtRoad(c[0], c[1], 0, s, lane);
        }
        // Farms and cabins along the lanes, set back a little with a track to the road.
        for (int k = 0; k < lane.size(); k += 3) {
            int[] c = lane.get(k);
            if (rnd.nextFloat() > 0.35f) continue;
            boolean farmhouse = rnd.nextFloat() < 0.55f;
            int fw = farmhouse ? 18 : 6, fh = farmhouse ? 16 : 6;
            int side = rnd.nextBoolean() ? 1 : -1;
            boolean vert = c[2] == 1;
            int fx = vert ? c[0] + side * 4 + (side < 0 ? -fw : 0) : c[0] - fw / 2;
            int fy = vert ? c[1] - fh / 2 : c[1] + side * 4 + (side < 0 ? -fh : 0);
            if (!allGrass(fx - 1, fy - 1, fw + 2, fh + 2)) continue;
            if (farmhouse) farm(fx, fy, fw, fh);
            else addLot(fx + 1, fy + 1, 4, 4, HOUSE);
            // The track from the road.
            int tx = c[0], ty = c[1];
            for (int s = 1; s < 5; s++) {
                int ax = vert ? tx + side * s : tx, ay = vert ? ty : ty + side * s;
                if (ax >= 0 && ay >= 0 && ax < w && ay < h && tiles[ay * w + ax] == GRASS) tiles[ay * w + ax] = DIRT;
            }
        }
        // Woods: clumps of trees out in the fields.
        int woods = (w * h - (bx1 - bx0) * (by1 - by0)) / 500;
        for (int k = 0; k < woods; k++) {
            int cx = rnd.nextInt(w), cy = rnd.nextInt(h);
            if (cx >= bx0 - 2 && cx < bx1 + 2 && cy >= by0 - 2 && cy < by1 + 2) continue;
            int r = 3 + rnd.nextInt(6);
            for (int y = cy - r; y <= cy + r; y++)
                for (int x = cx - r; x <= cx + r; x++) {
                    if (x < 1 || y < 1 || x >= w - 1 || y >= h - 1 || tiles[y * w + x] != GRASS) continue;
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > r * r * (0.6f + rnd.nextFloat() * 0.4f)) continue;
                    if (hasNeighbor(x, y, DIRT) || hasNeighbor(x, y, ROAD) || hasNeighbor(x, y, BUILDING) || rnd.nextFloat() < 0.35f) continue;
                    tiles[y * w + x] = TREE;
                }
        }
        // A few lone trees along the lanes.
        for (int[] c : lane)
            if (rnd.nextFloat() < 0.06f) {
                int x = c[0] + (c[2] == 0 ? 2 : 0) * (rnd.nextBoolean() ? 1 : -1), y = c[1] + (c[2] == 0 ? 0 : 2) * (rnd.nextBoolean() ? 1 : -1);
                if (x > 0 && y > 0 && x < w - 1 && y < h - 1 && tiles[y * w + x] == GRASS && !hasNeighbor(x, y, DIRT)) tiles[y * w + x] = TREE;
            }
    }

    /**
     * A two-tile dirt road from (x, y) out to the edge of the map, drifting from side to side. Every tile of the
     * centre line is added to lane as {x, y, vertical ? 1 : 0}.
     */
    private void dirtRoad(int x, int y, int dx, int dy, List<int[]> lane) {
        boolean vertical = dx == 0, joined = false;
        int drift = 0;
        for (int step = 0; step < w * 2; step++) {
            x += dx;
            y += dy;
            if (x < 0 || y < 0 || x >= w || y >= h) break;
            // Now and then the road bends a little one way or the other.
            if (step > 4 && rnd.nextFloat() < 0.18f) drift = rnd.nextInt(3) - 1;
            if (step > 4 && drift != 0 && rnd.nextFloat() < 0.5f) {
                if (vertical) x = Math.max(2, Math.min(w - 3, x + drift));
                else y = Math.max(2, Math.min(h - 3, y + drift));
            }
            if (railY0 >= 0 && !vertical && y >= railY0 - 2 && y <= railY0 + railRows + 1) y = railY0 - 3;
            for (int t = 0; t < 2; t++) {
                int ax = vertical ? x + t : x, ay = vertical ? y : y + t;
                if (ax < 0 || ay < 0 || ax >= w || ay >= h) continue;
                byte old = tiles[ay * w + ax];
                if (old == GRASS || old == TREE) tiles[ay * w + ax] = DIRT;
                else if (old == RAIL) {
                    // A level crossing.
                    tiles[ay * w + ax] = ROAD;
                    roadDir[ay * w + ax] = 1;
                } else if (step > 2 && old != DIRT) joined = true;
                // A bend: fill the corner so the road stays connected.
                if (vertical && ay > 0 && tiles[(ay - 1) * w + ax] != DIRT && tiles[(ay - 1) * w + ax] == GRASS) tiles[(ay - 1) * w + ax] = DIRT;
                if (!vertical && ax > 0 && tiles[ay * w + ax - 1] != DIRT && tiles[ay * w + ax - 1] == GRASS) tiles[ay * w + ax - 1] = DIRT;
            }
            lane.add(new int[]{x, y, vertical ? 1 : 0});
            if (joined) break;
        }
    }

    // ------------------------------------------------------------------ the highway

    /** The highway: which way it runs (0 east-west, 1 north-south, -1 there isn't one), its first row or column. */
    int hwyAxis = -1, hwyAt;
    String hwyName, hwyShield;
    /** Which way traffic must go on each tile (0 any; 1 east, 2 west, 3 south, 4 north). */
    byte[] oneWay;
    /** Where roads join the highway: {x, y} tile at the side of the highway, and which side (+1/-1). */
    final List<int[]> hwyExits = new ArrayList<int[]>();

    /**
     * A divided highway across the countryside from one edge of the map to the other, three lanes each way
     * with a concrete barrier between, and a road or two from town joining it at lights.
     */
    private void highway(int bx0, int by0, int bx1, int by1) {
        boolean ew = railY0 < 0 && rnd.nextBoolean();
        hwyAxis = ew ? 0 : 1;
        int lo = ew ? by0 : bx0, hi = ew ? by1 : bx1, len = ew ? h : w, len2 = ew ? w : h;
        boolean before = rnd.nextBoolean();
        int at = before ? lo / 2 - 3 : hi + (len - hi) / 2 - 3;
        at = Math.max(3, Math.min(len - 10, at));
        hwyAt = at;
        String[] hn = country.highways[rnd.nextInt(country.highways.length)];
        hwyName = hn[0];
        hwyShield = hn[1];
        oneWay = new byte[w * h];
        for (int k = 0; k < len2; k++)
            for (int a = 0; a < 7; a++) {
                int x = ew ? k : at + a, y = ew ? at + a : k;
                int i = y * w + x;
                // (The railway crosses on a bridge: the highway carries on underneath, a pier in the middle.)
                tiles[i] = a == 3 ? FENCE : ROAD;
                roadDir[i] = (byte) (ew ? 2 : 1);
                mainRoad[i] = true;
                if (a == 3) continue;
                // Keeping right, the first carriageway (north, or west) carries traffic west (or south).
                boolean firstSide = a < 3, neg = firstSide != country.leftHand;
                oneWay[i] = (byte) (ew ? (neg ? 2 : 1) : (neg ? 3 : 4));
            }
        // At each end, a way across the barrier (where it leaves the map), so nobody is stuck going one way.
        for (int k : new int[]{0, 1, len2 - 2, len2 - 1}) {
            int x = ew ? k : at + 3, y = ew ? at + 3 : k;
            tiles[y * w + x] = ROAD;
        }
        // Roads from town: carry a street on out to the highway where one ends facing it.
        // (side: the way from the town out to the highway; edge: the first row or column beside it, on the town side.)
        int side = before ? -1 : 1, edge = before ? at + 7 : at - 1;
        List<int[]> cands = new ArrayList<int[]>();
        int mid2 = ew ? (bx0 + bx1) / 2 : (by0 + by1) / 2;
        int runStart = -1, runD = 0;
        for (int k = 4; k < len2 - 4; k++) {
            // Walk from the highway towards town across open country; a street that carries straight on into
            // town from there is one to join up.
            int d = 0, x = ew ? k : edge, y = ew ? edge : k;
            while (d < len && x >= 0 && y >= 0 && x < w && y < h
                    && (tiles[y * w + x] == GRASS || tiles[y * w + x] == DIRT || tiles[y * w + x] == TREE)) {
                d++;
                x -= ew ? 0 : side;
                y -= ew ? side : 0;
            }
            boolean ok = d >= 2;
            for (int j = 0; j < 10 && ok; j++) {
                int ax = x - (ew ? 0 : side * j), ay = y - (ew ? side * j : 0);
                if (ax < 0 || ay < 0 || ax >= w || ay >= h || tiles[ay * w + ax] != ROAD) ok = false;
            }
            if (ok && runStart < 0) {
                runStart = k;
                runD = d;
            }
            if ((!ok || k == len2 - 5) && runStart >= 0) {
                int width = (ok ? k + 1 : k) - runStart;
                if (width >= 3 && width <= 6) cands.add(new int[]{runStart, width, runD, Math.abs(runStart + width / 2 - mid2)});
                runStart = -1;
            }
            if (ok) runD = Math.max(runD, d);
        }
        java.util.Collections.sort(cands, new java.util.Comparator<int[]>() {
            public int compare(int[] p, int[] q) {
                return p[3] - q[3];
            }
        });
        int made = 0;
        List<Integer> used = new ArrayList<Integer>();
        for (int[] cnd : cands) {
            if (made >= (len2 > 300 ? 3 : 2)) break;
            boolean near = false;
            for (int u : used) if (Math.abs(u - cnd[0]) < 40) near = true;
            if (near || cnd[1] > 6) continue;
            used.add(cnd[0]);
            made++;
            // The connecting road across the fields (a street of its own, with a name), and a gap in the
            // barrier so traffic can turn across.
            int r0 = Math.min(edge, edge - side * (cnd[2] - 1)), r1 = Math.max(edge, edge - side * (cnd[2] - 1)) + 1;
            carve(ew ? new Street(cnd[0], r0, cnd[0] + cnd[1], r1, true, cnd[1] >= 5) : new Street(r0, cnd[0], r1, cnd[0] + cnd[1], false, cnd[1] >= 5));
            for (int q = 0; q < cnd[1]; q++) {
                int k = cnd[0] + q;
                int x = ew ? k : at + 3, y = ew ? at + 3 : k;
                tiles[y * w + x] = ROAD;
            }
            hwyExits.add(new int[]{cnd[0], cnd[1], side});
        }
        // Where town comes right up to the highway, a frontage road runs along beside it, and the streets that
        // stop just short of it carry on to meet it (instead of ending in the verge).
        int f0 = before ? at + 8 : at - 4;
        boolean room = f0 >= 0 && f0 + 3 <= len && (before ? lo - (f0 + 3) : f0 - hi) <= 6;
        for (int k = 0; k < len2 && room; k++)
            for (int a = 0; a < 3; a++) {
                int x = ew ? k : f0 + a, y = ew ? f0 + a : k;
                byte t = tiles[y * w + x];
                if (t != GRASS && t != TREE && t != DIRT && t != ROAD && t != RAIL) room = false;
            }
        if (room) {
            carve(ew ? new Street(0, f0, w, f0 + 3, false, false) : new Street(f0, 0, f0 + 3, h, true, false));
            int step = before ? 1 : -1, from = before ? f0 + 3 : f0 - 1;
            for (int k = 0; k < len2; k++) {
                int x = ew ? k : from, y = ew ? from : k, cx = x, cy = y, d = 0;
                while (d < 7 && cx >= 0 && cy >= 0 && cx < w && cy < h
                        && (tiles[cy * w + cx] == GRASS || tiles[cy * w + cx] == TREE || tiles[cy * w + cx] == DIRT)) {
                    d++;
                    if (ew) cy += step;
                    else cx += step;
                }
                if (d == 0 || d >= 7 || !isRoad(cx, cy) || roadDir[cy * w + cx] != (ew ? 1 : 2)) continue;
                for (int q = 0; q < d; q++) {
                    int gx = ew ? x : x + step * q, gy = ew ? y + step * q : y;
                    tiles[gy * w + gx] = ROAD;
                    roadDir[gy * w + gx] = (byte) (ew ? 1 : 2);
                }
            }
        }
    }

    /** Whether this column of the railway is the bridge over the highway (no level crossing there). */
    boolean underRailBridge(int tx) {
        return railY0 >= 0 && hwyAxis == 1 && tx >= hwyAt - 1 && tx <= hwyAt + 7;
    }

    boolean underRailBridge(float x, float y) {
        if (!underRailBridge((int) (x / T))) return false;
        float ry = (railY0 + railRows / 2f) * T;
        return Math.abs(y - ry) < (railRows / 2f + 3) * T;
    }

    /** Whether a car may go (dx, dy) on tile i. */
    /** One-way lanes down each side of a tree-lined median (null if there are none). */
    byte[] divided;

    private static boolean wayOk(byte[] ways, int i, int dx, int dy) {
        switch (ways[i]) {
            case 1: return dx != -1;
            case 2: return dx != 1;
            case 3: return dy != -1;
            case 4: return dy != 1;
            default: return true;
        }
    }

    private boolean oneWayOk(int i, int dx, int dy) {
        switch (oneWay[i]) {
            case 1: return dx != -1;
            case 2: return dx != 1;
            case 3: return dy != -1;
            case 4: return dy != 1;
            default: return true;
        }
    }

    /** The middle of the carriageway a highway tile is on (across the highway, in world units). */
    private float hwyCentre(int tx, int ty) {
        int a = (hwyAxis == 0 ? ty : tx) - hwyAt;
        return (hwyAt + (a < 3 ? 1.5f : 5.5f)) * T;
    }

    /** Inside the town (not out in the country). */
    boolean inTown(float x, float y) {
        return x >= townX0 * T && x < townX1 * T && y >= townY0 * T && y < townY1 * T;
    }

    boolean nearHighway(float x, float y, float r) {
        if (hwyAxis < 0) return false;
        float c = (hwyAt + 3.5f) * T;
        return Math.abs((hwyAxis == 0 ? y : x) - c) < r;
    }

    boolean onHighway(float x, float y) {
        if (oneWay == null) return false;
        int tx = (int) (x / T), ty = (int) (y / T);
        return tx >= 0 && ty >= 0 && tx < w && ty < h && oneWay[ty * w + tx] != 0;
    }

    /** A random spot on the highway (a tile centre), or null if there's no highway. */
    float[] randomHighway(Random r) {
        if (hwyAxis < 0) return null;
        for (int k = 0; k < 40; k++) {
            int along = 2 + r.nextInt((hwyAxis == 0 ? w : h) - 4), a = r.nextBoolean() ? 1 : 5;
            int x = hwyAxis == 0 ? along : hwyAt + a, y = hwyAxis == 0 ? hwyAt + a : along;
            if (oneWay[y * w + x] != 0) return new float[]{(x + 0.5f) * T, (y + 0.5f) * T};
        }
        return null;
    }

    /**
     * Through traffic on the highway: where a car comes onto the map on one carriageway and where it leaves
     * at the far end, as {x0, y0, x1, y1}; null if there's no highway.
     */
    float[] highwayRun(Random r) {
        if (hwyAxis < 0) return null;
        int a = r.nextBoolean() ? 1 : 5, len = hwyAxis == 0 ? w : h;
        int x = hwyAxis == 0 ? 0 : hwyAt + a, y = hwyAxis == 0 ? hwyAt + a : 0;
        int ow = oneWay[y * w + x];
        // Comes in where the traffic flows from.
        boolean fromLow = ow == 1 || ow == 3;
        int k0 = fromLow ? 1 : len - 2, k1 = fromLow ? len - 2 : 1;
        float c = (hwyAt + a + 0.5f) * T;
        return hwyAxis == 0 ? new float[]{(k0 + 0.5f) * T, c, (k1 + 0.5f) * T, c} : new float[]{c, (k0 + 0.5f) * T, c, (k1 + 0.5f) * T};
    }

    /** A random spot on a country lane, out of town, or null. */
    float[] randomCountryRoad(Random r) {
        for (int k = 0; k < 300; k++) {
            int x = r.nextInt(w), y = r.nextInt(h);
            if (x >= townX0 && x < townX1 && y >= townY0 && y < townY1) continue;
            if (tiles[y * w + x] == DIRT) return new float[]{(x + 0.5f) * T, (y + 0.5f) * T};
        }
        return null;
    }

    /** Where a village's streets end, as {x, y, dx, dy}: the lanes out into the country start there. */
    private final List<int[]> villageEnds = new ArrayList<int[]>();
    /** The town's centre and each hamlet's, in world units: where hordes wander between on big maps. */
    final List<float[]> settlements = new ArrayList<float[]>();

    /**
     * A village: one high street across the middle, a crossroad and a back lane, with plots strung along
     * them and open grass between. No grid.
     */
    private void villageCore(int bx0, int by0, int bx1, int by1) {
        int cy = (by0 + by1) / 2 - 1 + rnd.nextInt(7) - 3, cx = (bx0 + bx1) / 2 - 1 + rnd.nextInt(9) - 4;
        List<Street> own = new ArrayList<Street>();
        Street high = new Street(bx0, cy, bx1, cy + 3, false, false);
        Street cross = new Street(cx, by0, cx + 3, by1, true, false);
        carve(high);
        carve(cross);
        own.add(high);
        own.add(cross);
        villageEnds.add(new int[]{bx0, cy, -1, 0});
        villageEnds.add(new int[]{bx1 - 1, cy, 1, 0});
        villageEnds.add(new int[]{cx, by0, 0, -1});
        villageEnds.add(new int[]{cx, by1 - 1, 0, 1});
        // A back lane off the crossroad, running part way to one side.
        int off = (24 + rnd.nextInt(8)) * (rnd.nextBoolean() ? 1 : -1), ly = cy + off;
        if (ly > by0 + 20 && ly < by1 - 24) {
            boolean east = rnd.nextBoolean();
            int len = (bx1 - bx0) / 2 - 12 - rnd.nextInt(16);
            Street back = east ? new Street(cx + 3, ly, cx + 3 + len, ly + 3, false, false) : new Street(cx - len, ly, cx, ly + 3, false, false);
            carve(back);
            own.add(back);
        }
        // Plots along both sides of each street, with gaps of open ground between them.
        for (Street st : own) plotsAlong(st, bx0, by0, bx1, by1, 1);
        // Hedgerows and garden trees on the open ground in between.
        for (int y = by0; y < by1; y++)
            for (int x = bx0; x < bx1; x++)
                if (tiles[y * w + x] == GRASS && rnd.nextFloat() < 0.03f && !hasNeighbor(x, y, ROAD) && !hasNeighbor(x, y, SIDEWALK))
                    tiles[y * w + x] = TREE;
    }

    /** Plots along both sides of a street, with gaps of open ground between them. flag: 1 village, 2 hamlet. */
    private int plotsAlong(Street st, int bx0, int by0, int bx1, int by1, int flag) {
        int made = 0;
        int from = st.vertical ? st.y0 : st.x0, to = st.vertical ? st.y1 : st.x1;
        for (int side = -1; side <= 1; side += 2) {
            int p = from + 1 + rnd.nextInt(3);
            while (p < to - 12) {
                int pw = 15 + rnd.nextInt(5), pd = 15 + rnd.nextInt(5);
                int x0, y0, x1, y1;
                if (st.vertical) {
                    y0 = p;
                    y1 = p + pw;
                    x0 = side < 0 ? st.x0 - pd : st.x1;
                    x1 = side < 0 ? st.x0 : st.x1 + pd;
                } else {
                    x0 = p;
                    x1 = p + pw;
                    y0 = side < 0 ? st.y0 - pd : st.y1;
                    y1 = side < 0 ? st.y0 : st.y1 + pd;
                }
                if (x0 >= bx0 && y0 >= by0 && x1 <= bx1 && y1 <= by1 && allGrass(x0, y0, x1 - x0, y1 - y0)) {
                    fill(x0, y0, x1 - x0, y1 - y0, SIDEWALK);
                    blocks.add(new int[]{x0, y0, x1, y1, flag});
                    made++;
                    p += pw + 1 + (rnd.nextFloat() < 0.3f ? 6 + rnd.nextInt(10) : 0);
                } else p += 3;
            }
        }
        return made;
    }

    // ------------------------------------------------------------------ neighbourhoods

    static final int NB_CLOSES = 0, NB_CRESCENT = 1, NB_GREEN = 2, NB_ESTATE = 3, NB_ACRES = 4, NB_GATED = 5, NB_WORKS = 6;

    /**
     * A stretch of a big map's suburbs laid out as a neighbourhood of its own instead of more grid: a spine road
     * with closes (cul-de-sacs) off both sides, a crescent round a green, houses round a village green, flats
     * set in parkland, big plots out on the edge of town, or a gated estate. Its roads are laid now and its
     * homes with the rest of the blocks (fillNeighbourhood). False if it can't be done here.
     */
    private boolean neighbourhood(int x0, int y0, int x1, int y1, float d) {
        int mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
        boolean rT = isRoad(mx, y0 - 1), rB = isRoad(mx, y1), rL = isRoad(x0 - 1, my), rR = isRoad(x1, my);
        if (!rT && !rB && !rL && !rR) return false;
        int dt = districtType(mx, my);
        int type;
        float r = rnd.nextFloat();
        if (dt == DT_INDUSTRIAL) type = NB_WORKS;
        else if (d > 0.8f && dt != DT_MIDTOWN && r < 0.15f) type = NB_ACRES;
        else {
            r = rnd.nextFloat();
            if (dt == DT_MIDTOWN) type = r < 0.5f ? NB_ESTATE : NB_GREEN;
            else type = r < 0.3f ? NB_CLOSES : r < 0.52f ? NB_CRESCENT : r < 0.72f ? NB_GREEN : r < 0.84f ? NB_ESTATE : NB_GATED;
        }
        // Next door to one just like it? Try something else: every neighbourhood its own.
        for (int tries = 0; tries < 3 && type != NB_WORKS && sameNearby(mx, my, type); tries++) {
            r = rnd.nextFloat();
            type = dt == DT_MIDTOWN ? (r < 0.5f ? NB_ESTATE : NB_GREEN)
                    : r < 0.25f ? NB_CLOSES : r < 0.45f ? NB_CRESCENT : r < 0.65f ? NB_GREEN : r < 0.8f ? NB_ESTATE : NB_GATED;
        }
        int[] green = null;
        switch (type) {
            case NB_CLOSES:
            case NB_GATED:
                if (!closes(x0, y0, x1, y1, rT, rB, rL, rR)) return false;
                break;
            case NB_CRESCENT:
            case NB_ESTATE:
            case NB_WORKS:
                green = crescent(x0, y0, x1, y1, rT, rB, rL, rR);
                if (green == null) return false;
                break;
            case NB_GREEN:
                green = villageGreen(x0, y0, x1, y1, rT, rB, rL, rR);
                if (green == null) return false;
                break;
            default:
                break;
        }
        blocks.add(green == null ? new int[]{x0, y0, x1, y1, 3, type}
                : new int[]{x0, y0, x1, y1, 3, type, green[0], green[1], green[2], green[3]});
        nameNeighbourhood(x0, y0, x1, y1, type, dt);
        return true;
    }

    /** How many neighbourhoods of each kind the town has (NB_CLOSES ... NB_WORKS). */
    int[] neighbourhoodKinds() {
        int[] n = new int[7];
        for (int[] b : blocks) if (b.length > 5 && b[4] == 3) n[b[5]]++;
        return n;
    }

    private boolean sameNearby(int x, int y, int type) {
        for (int[] b : blocks)
            if (b.length > 5 && b[4] == 3 && (b[5] == type || (type == NB_GATED && b[5] == NB_CLOSES) || (type == NB_CLOSES && b[5] == NB_GATED))
                    && Math.hypot((b[0] + b[2]) / 2f - x, (b[1] + b[3]) / 2f - y) < 130) return true;
        return false;
    }

    /** A two-lane street three tiles wide, running north-south (vertical) from a0 to a1 at column c, or east-west at row c. */
    private void lane3(boolean vertical, int a0, int a1, int c) {
        carve(vertical ? new Street(c, a0, c + 3, a1, true, false) : new Street(a0, c, a1, c + 3, false, false));
    }

    /** A turning circle at the end of a street (vertical or not) whose last row is at a, the street at c..c+2. */
    private void bulb(boolean vertical, int a, int c, boolean atStart) {
        int lo = atStart ? a - 1 : a - 3, hi = atStart ? a + 3 : a + 1;
        for (int along = lo; along <= hi; along++)
            for (int across = c - 1; across <= c + 3; across++) {
                boolean corner = (along == lo || along == hi) && (across == c - 1 || across == c + 3);
                if (corner) continue;
                int x = vertical ? across : along, y = vertical ? along : across;
                if (x < 0 || y < 0 || x >= w || y >= h) continue;
                int i = y * w + x;
                if (tiles[i] != ROAD) {
                    tiles[i] = ROAD;
                    roadDir[i] = (byte) (vertical ? 1 : 2);
                }
            }
    }

    /** A spine road through (or into) the block with closes off it on both sides, staggered. */
    private boolean closes(int x0, int y0, int x1, int y1, boolean rT, boolean rB, boolean rL, boolean rR) {
        int bw = x1 - x0, bh = y1 - y0;
        boolean vert = (rT || rB) && (bh >= bw || !(rL || rR));
        int a0 = vert ? y0 : x0, a1 = vert ? y1 : x1, c0 = vert ? x0 : y0, c1 = vert ? x1 : y1;
        boolean s0 = vert ? rT : rL, s1 = vert ? rB : rR;
        if (a1 - a0 < 36 || c1 - c0 < 34) return false;
        int c = c0 + (c1 - c0 - 3) / 2 + rnd.nextInt(5) - 2;
        int sa0 = s0 ? a0 : a0 + 9, sa1 = s1 ? a1 : a1 - 9;
        lane3(vert, sa0, sa1, c);
        if (!s0) bulb(vert, sa0, c, true);
        if (!s1) bulb(vert, sa1 - 1, c, false);
        for (int side = -1; side <= 1; side += 2) {
            int depth = side < 0 ? c - c0 : c1 - (c + 3);
            int len = depth - 10;
            if (len < 6) continue;
            int step = 17 + rnd.nextInt(3);
            for (int a = sa0 + 8 + (side > 0 ? step / 2 : 0); a + 3 <= sa1 - 8; a += step + rnd.nextInt(3)) {
                // Now and then a gap: a bigger garden, or a footpath through.
                if (rnd.nextFloat() < 0.15f) continue;
                int e0 = side < 0 ? c - len : c + 3, e1 = side < 0 ? c : c + 3 + len;
                lane3(!vert, e0, e1, a);
                bulb(!vert, side < 0 ? e0 : e1 - 1, a, side < 0);
            }
        }
        return true;
    }

    /**
     * A crescent: a loop off one of the streets round the block, its two arms joined at the back, with a green
     * in the middle. Returns the green {x0, y0, x1, y1}.
     */
    private int[] crescent(int x0, int y0, int x1, int y1, boolean rT, boolean rB, boolean rL, boolean rR) {
        List<Integer> sides = new ArrayList<Integer>();
        if (rT) sides.add(0);
        if (rB) sides.add(1);
        if (rL) sides.add(2);
        if (rR) sides.add(3);
        int side = sides.get(rnd.nextInt(sides.size()));
        boolean legsVert = side < 2;
        int a0 = legsVert ? x0 : y0, a1 = legsVert ? x1 : y1, c0 = legsVert ? y0 : x0, c1 = legsVert ? y1 : x1;
        if (a1 - a0 < 36 || c1 - c0 < 30) return null;
        boolean fromLow = side == 0 || side == 2;
        int p1 = a0 + 9 + rnd.nextInt(3), p2 = a1 - 12 - rnd.nextInt(3);
        int depth = c1 - c0 - 10 - rnd.nextInt(4);
        int l0 = fromLow ? c0 : c1 - depth, l1 = fromLow ? c0 + depth : c1;
        lane3(legsVert, l0, l1, p1);
        lane3(legsVert, l0, l1, p2);
        int cc = fromLow ? c0 + depth - 3 : c1 - depth;
        lane3(!legsVert, p1, p2 + 3, cc);
        int g0 = fromLow ? c0 : cc + 3, g1 = fromLow ? cc : c1;
        return legsVert ? new int[]{p1 + 3, g0, p2, g1} : new int[]{g0, p1 + 3, g1, p2};
    }

    /** A village green with a road all round it and a road or two out to the streets. Returns the green. */
    private int[] villageGreen(int x0, int y0, int x1, int y1, boolean rT, boolean rB, boolean rL, boolean rR) {
        int bw = x1 - x0, bh = y1 - y0;
        if (bw < 44 || bh < 44) return null;
        int gw = Math.min(18, bw - 32), gh = Math.min(18, bh - 32);
        int gx0 = x0 + (bw - gw) / 2 + rnd.nextInt(3) - 1, gy0 = y0 + (bh - gh) / 2 + rnd.nextInt(3) - 1;
        int rx0 = gx0 - 3, ry0 = gy0 - 3, rx1 = gx0 + gw + 3, ry1 = gy0 + gh + 3;
        lane3(false, rx0, rx1, ry0);
        lane3(false, rx0, rx1, ry1 - 3);
        lane3(true, ry0, ry1, rx0);
        lane3(true, ry0, ry1, rx1 - 3);
        // Roads out: one or two (more is just a grid again), off-centre.
        List<Integer> sides = new ArrayList<Integer>();
        if (rT) sides.add(0);
        if (rB) sides.add(1);
        if (rL) sides.add(2);
        if (rR) sides.add(3);
        java.util.Collections.shuffle(sides, rnd);
        int outs = Math.min(sides.size(), 1 + rnd.nextInt(2));
        for (int k = 0; k < outs; k++) {
            int sd = sides.get(k);
            if (sd < 2) {
                int c = gx0 + gw / 3 + rnd.nextInt(Math.max(1, gw / 3));
                if (sd == 0) lane3(true, y0, ry0, c);
                else lane3(true, ry1, y1, c);
            } else {
                int c = gy0 + gh / 3 + rnd.nextInt(Math.max(1, gh / 3));
                if (sd == 2) lane3(false, x0, rx0, c);
                else lane3(false, rx1, x1, c);
            }
        }
        return new int[]{gx0, gy0, gx0 + gw, gy0 + gh};
    }

    private void nameNeighbourhood(int x0, int y0, int x1, int y1, int type, int dt) {
        if (districtAt == null || districts.size() > 110) return;
        String[][] ends = {{" Meadows", " Fields", " Chase", " Rise"}, {" Crescent", " Gardens", " Park"},
                {" Green", " Common"}, {" Estate", " Court", " Towers"}, {" Acres", " Ranch", " Farms"},
                {" Manor", " Reserve", " Hills"}, {" Business Park", " Trading Estate", " Commerce Park"}};
        String word = country.districtWords[rnd.nextInt(country.districtWords.length)];
        String[] e = ends[type];
        District d = new District();
        d.type = dt;
        d.name = country.districtName(word + e[rnd.nextInt(e.length)], word, dt);
        for (District o : districts) if (o.name.equals(d.name)) return;
        d.x = (x0 + x1) / 2f;
        d.y = (y0 + y1) / 2f;
        d.cx = d.x * T;
        d.cy = d.y * T;
        int id = districts.size();
        districts.add(d);
        for (int y = Math.max(0, y0); y < Math.min(h, y1); y++)
            for (int x = Math.max(0, x0); x < Math.min(w, x1); x++) {
                districts.get(districtAt[y * w + x]).tiles--;
                districtAt[y * w + x] = (byte) id;
                d.tiles++;
            }
    }

    /** The homes, gardens and green of a neighbourhood laid out by neighbourhood(). */
    private void fillNeighbourhood(int[] b) {
        int x0 = b[0], y0 = b[1], x1 = b[2], y1 = b[3], type = b[5];
        if (x1 - x0 < 4 || y1 - y0 < 4) return;
        curDistrict = districtType((x0 + x1) / 2, (y0 + y1) / 2);
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
                if (tiles[y * w + x] != ROAD) tiles[y * w + x] = GRASS;
        // A pavement round the edge and along every road.
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                int i = y * w + x;
                if (tiles[i] == ROAD) continue;
                boolean edge = x == x0 || y == y0 || x == x1 - 1 || y == y1 - 1, by = false;
                if (type != NB_ACRES)
                    for (int dy = -1; dy <= 1 && !by; dy++)
                        for (int dx = -1; dx <= 1 && !by; dx++)
                            if (inside(x + dx, y + dy, x0, y0, x1, y1) && isRoad(x + dx, y + dy)) by = true;
                if (edge || by) tiles[i] = SIDEWALK;
            }
        // The green, with a ring of homes round it if it's a big one.
        int[] keep = null;
        if (b.length >= 10) {
            int gx0 = b[6] + 1, gy0 = b[7] + 1, gx1 = b[8] - 1, gy1 = b[9] - 1;
            if (type != NB_GREEN && gx1 - gx0 >= 26 && gy1 - gy0 >= 26) {
                gx0 += 7;
                gy0 += 7;
                gx1 -= 7;
                gy1 -= 7;
            }
            if (gx1 - gx0 >= 4 && gy1 - gy0 >= 4) {
                keep = new int[]{gx0, gy0, gx1, gy1};
                int gw = gx1 - gx0, gh = gy1 - gy0;
                boolean small = gw <= 16 && gh <= 16;
                if (type == NB_WORKS) {
                    // A yard with a big distribution depot in the middle.
                    fill(gx0, gy0, gw, gh, LOT);
                    if (gw >= 12 && gh >= 12) addLot(gx0 + 3, gy0 + 3, gw - 6, gh - 6, WAREHOUSE);
                    openAreas.add(new float[]{(gx0 + 1.5f) * T, (gy0 + 1.5f) * T, 2});
                } else if (type == NB_ESTATE && small && rnd.nextFloat() < 0.5f) {
                    parking(gx0, gy0, gw, gh);
                    openAreas.add(new float[]{(gx0 + gx1) / 2f * T, (gy0 + gy1) / 2f * T, 2});
                } else {
                    if (gw > 20 && gh > 20) {
                        // A big green is a park, with a playground in a corner, not a sports complex.
                        park(gx0, gy0, gw, gh);
                        if (rnd.nextFloat() < 0.5f) bandstand(gx0, gy0, gw, gh);
                        int qw = Math.min(8, gw / 3), qh = Math.min(8, gh / 3);
                        for (int j = gy0 + 1; j < gy0 + 1 + qh; j++)
                            for (int i = gx0 + 1; i < gx0 + 1 + qw; i++) if (tiles[j * w + i] == TREE) tiles[j * w + i] = GRASS;
                        addDecor(D_PLAYGROUND, (gx0 + 1) * T + 3, (gy0 + 1) * T + 3, (gx0 + 1 + qw) * T - 2, (gy0 + 1 + qh) * T - 2, 0);
                    } else parkVariant(gx0, gy0, gw, gh);
                    openAreas.add(new float[]{(gx0 + gx1) / 2f * T, (gy0 + gy1) / 2f * T, 0});
                }
            }
        }
        // A gated estate: a fence all round inside the pavement, open only where the road goes in, and a gatehouse.
        if (type == NB_GATED) {
            for (int y = y0 + 1; y < y1 - 1; y++)
                for (int x = x0 + 1; x < x1 - 1; x++) {
                    if (x != x0 + 1 && x != x1 - 2 && y != y0 + 1 && y != y1 - 2) continue;
                    if (tiles[y * w + x] == GRASS) tiles[y * w + x] = FENCE;
                }
            for (int y = y0 + 1; y < y1 - 1; y++)
                for (int x = x0 + 1; x < x1 - 1; x++) {
                    if (tiles[y * w + x] != SIDEWALK || (x != x0 + 1 && x != x1 - 2 && y != y0 + 1 && y != y1 - 2)) continue;
                    // Just inside the gate, beside the pavement.
                    int gx = x == x0 + 1 ? x + 1 : x == x1 - 2 ? x - 2 : x + 1, gy = y == y0 + 1 ? y + 1 : y == y1 - 2 ? y - 2 : y + 1;
                    if (allType(gx, gy, 2, 2, GRASS)) {
                        addLot(gx, gy, 2, 2, KIOSK);
                        break;
                    }
                }
        }
        switch (type) {
            case NB_ESTATE:
                frontage(x0, y0, x1, y1, keep, APARTMENT, 8, 11, 2, 3, 0f);
                frontage(x0, y0, x1, y1, keep, HOUSE, 4, 5, 1, 2, 0.06f);
                break;
            case NB_ACRES: {
                frontage(x0, y0, x1, y1, keep, HOUSE, 5, 6, 5, 8, 0.05f);
                // Paddocks and crops out the back.
                int fx0 = x0 + 16, fy0 = y0 + 16, fx1 = x1 - 16, fy1 = y1 - 16;
                if (fx1 - fx0 >= 10 && fy1 - fy0 >= 10) {
                    boolean wide = fx1 - fx0 >= fy1 - fy0;
                    int n = 1 + (Math.max(fx1 - fx0, fy1 - fy0) >= 24 ? 1 : 0), span = (wide ? fx1 - fx0 : fy1 - fy0) / n;
                    for (int k = 0; k < n; k++) {
                        int a = (wide ? fx0 : fy0) + k * span, b2 = a + span - (k < n - 1 ? 2 : 0);
                        int cx0 = wide ? a : fx0, cy0 = wide ? fy0 : a, cx1 = wide ? b2 : fx1, cy1 = wide ? fy1 : b2;
                        boolean clear = true;
                        for (int j = cy0; j < cy1 && clear; j++)
                            for (int i = cx0; i < cx1 && clear; i++) if (tiles[j * w + i] != GRASS && tiles[j * w + i] != TREE) clear = false;
                        if (!clear) continue;
                        fill(cx0, cy0, cx1 - cx0, cy1 - cy0, GRASS);
                        addDecor(D_CROPS, cx0 * T + 3, cy0 * T + 3, cx1 * T - 3, cy1 * T - 3, rnd.nextInt(3));
                        for (int j = cy0 - 1; j <= cy1; j++)
                            for (int i = cx0 - 1; i <= cx1; i++) if (tiles[j * w + i] == TREE) tiles[j * w + i] = GRASS;
                    }
                    keep = new int[]{fx0 - 1, fy0 - 1, fx1 + 1, fy1 + 1};
                }
                break;
            }
            case NB_WORKS:
                frontage(x0, y0, x1, y1, keep, WAREHOUSE, 9, 13, 3, 3, 0f);
                frontage(x0, y0, x1, y1, keep, OFFICE, 6, 8, 2, 2, 0.1f);
                break;
            case NB_GATED:
                // Inside the fence, every house faces the estate's own roads.
                frontage(x0 + 1, y0 + 1, x1 - 1, y1 - 1, keep, HOUSE, 4, 5, 1, 2, 0.06f);
                break;
            default:
                frontage(x0, y0, x1, y1, keep, HOUSE, 4, 5, 1, 2, 0.06f);
                break;
        }
        // Trees in the back gardens (orchards and windbreaks out on the big plots).
        float trees = type == NB_ACRES ? 0.22f : 0.1f;
        for (int y = y0 + 1; y < y1 - 1; y++)
            for (int x = x0 + 1; x < x1 - 1; x++) {
                if (keep != null && inside(x, y, keep[0], keep[1], keep[2], keep[3])) continue;
                if (tiles[y * w + x] == GRASS && rnd.nextFloat() < trees && !hasNeighbor(x, y, TREE)
                        && !hasNeighbor(x, y, BUILDING) && !hasNeighbor(x, y, ROAD) && !hasNeighbor(x, y, SIDEWALK))
                    tiles[y * w + x] = TREE;
            }
    }

    private static boolean inside(int x, int y, int x0, int y0, int x1, int y1) {
        return x >= x0 && y >= y0 && x < x1 && y < y1;
    }

    private boolean allType(int x, int y, int fw, int fh, byte t) {
        for (int j = y; j < y + fh; j++)
            for (int i = x; i < x + fw; i++)
                if (i < 0 || j < 0 || i >= w || j >= h || tiles[j * w + i] != t) return false;
        return true;
    }

    /**
     * Homes facing the roads: behind every bit of pavement with a road beyond it, one set back a garden's depth
     * (setback tiles) from the pavement, sizes min..max, at least gap tiles from the next. A few plots stay empty.
     */
    private void frontage(int x0, int y0, int x1, int y1, int[] keep, int kind, int min, int max, int setback, int gap, float empty) {
        int[] ddx = {1, -1, 0, 0}, ddy = {0, 0, 1, -1};
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                if (tiles[y * w + x] != SIDEWALK) continue;
                for (int k = 0; k < 4; k++) {
                    int dx = ddx[k], dy = ddy[k];
                    if (!paved(x + dx, y + dy)) continue;
                    int hw = min + rnd.nextInt(max - min + 1), hd = min + rnd.nextInt(max - min + 1);
                    int fx = x - dx * (1 + setback), fy = y - dy * (1 + setback);
                    int lx, ly, lw, lh;
                    if (dx != 0) {
                        lw = hd;
                        lh = hw;
                        lx = dx > 0 ? fx - hd + 1 : fx;
                        ly = y - hw / 2;
                    } else {
                        lw = hw;
                        lh = hd;
                        lx = x - hw / 2;
                        ly = dy > 0 ? fy - hd + 1 : fy;
                    }
                    if (lx < x0 + 1 || ly < y0 + 1 || lx + lw > x1 - 1 || ly + lh > y1 - 1) continue;
                    if (keep != null && lx < keep[2] && lx + lw > keep[0] && ly < keep[3] && ly + lh > keep[1]) continue;
                    if (!allType(lx, ly, lw, lh, GRASS)) continue;
                    boolean clear = true;
                    for (int j = ly - gap; j < ly + lh + gap && clear; j++)
                        for (int i = lx - gap; i < lx + lw + gap && clear; i++)
                            if (i >= 0 && j >= 0 && i < w && j < h && tiles[j * w + i] == BUILDING) clear = false;
                    if (!clear) continue;
                    if (rnd.nextFloat() < empty) {
                        // An empty plot: mark it taken with a tree so the next house doesn't crowd in.
                        tiles[(ly + lh / 2) * w + lx + lw / 2] = TREE;
                        break;
                    }
                    addLot(lx, ly, lw, lh, kind);
                    // A drive from the road to the house.
                    for (int s = 1; s <= setback; s++) {
                        int ax = x - dx * s, ay = y - dy * s;
                        if (tiles[ay * w + ax] == GRASS) tiles[ay * w + ax] = SIDEWALK;
                    }
                    if (kind == HOUSE && setback >= 4 && rnd.nextFloat() < 0.4f) {
                        // Out on the big plots, a barn behind the house.
                        int bx = dx > 0 ? lx - 6 : dx < 0 ? lx + lw + 2 : lx, by = dy > 0 ? ly - 6 : dy < 0 ? ly + lh + 2 : ly;
                        if (bx > x0 + 1 && by > y0 + 1 && bx + 4 < x1 - 1 && by + 4 < y1 - 1 && allType(bx - 1, by - 1, 6, 6, GRASS))
                            addLot(bx, by, 4, 4, BARN);
                    }
                    break;
                }
            }
    }

    /**
     * Gives a town in the country a ragged, organic edge: blocks outside a wobbly outline become fields again,
     * and the roads that only served them go too. What's left is one connected town with streets running out
     * of it, and the town bounds shrink to fit.
     */
    private void erodeTown(int bx0, int by0, int bx1, int by1) {
        float cx = (bx0 + bx1) / 2f, cy = (by0 + by1) / 2f, rad = (bx1 - bx0) / 2f;
        float[] amp = new float[4], ph = new float[4];
        for (int k = 0; k < 4; k++) {
            amp[k] = w >= 400 ? 0.01f + rnd.nextFloat() * 0.025f : 0.04f + rnd.nextFloat() * (k == 0 ? 0.14f : 0.08f);
            ph[k] = rnd.nextFloat() * (float) Math.PI * 2;
        }
        float stretch = w >= 400 ? 1f : 0.8f + rnd.nextFloat() * 0.4f;
        List<int[]> kept = new ArrayList<int[]>();
        for (int[] b : blocks) {
            float x = (b[0] + b[2]) / 2f - cx, y = ((b[1] + b[3]) / 2f - cy) * stretch;
            float a = (float) Math.atan2(y, x), d = (float) Math.sqrt(x * x + y * y) / rad;
            // A massive town fills its square out to a ragged edge (rounded corners, not a circle in a field);
            // a small town just loses its corners.
            if (w >= 400) {
                float ax = Math.abs(x) / rad, ay = Math.abs(y) / rad;
                d = (float) Math.pow(Math.pow(ax, 6) + Math.pow(ay, 6), 1 / 6.0);
            }
            float r = w >= 400 ? 1.06f : 1.02f;
            for (int k = 0; k < 4; k++) r += amp[k] * (float) Math.sin((k + 2) * a + ph[k]);
            boolean keep = d < r && !(d > r - 0.14f && rnd.nextFloat() < (w >= 400 ? 0.12f : 0.3f));
            if (keep) kept.add(b);
            else fill(b[0], b[1], b[2] - b[0], b[3] - b[1], GRASS);
        }
        // Roads more than a few tiles from the town are dug up.
        boolean[] near = new boolean[w * h];
        for (int[] b : kept)
            for (int y = Math.max(0, b[1] - 3); y < Math.min(h, b[3] + 3); y++)
                for (int x = Math.max(0, b[0] - 3); x < Math.min(w, b[2] + 3); x++) near[y * w + x] = true;
        for (int y = by0; y < by1; y++)
            for (int x = bx0; x < bx1; x++) {
                int i = y * w + x;
                if ((tiles[i] == ROAD || tiles[i] == SIDEWALK) && !near[i]) unroad(i);
            }
        // Keep the big connected pieces of road network; stray bits (and the blocks only they reach) go.
        int[] comp = new int[w * h];
        Arrays.fill(comp, -1);
        List<Integer> sizes = new ArrayList<Integer>();
        int[] q = new int[w * h];
        for (int s = 0; s < w * h; s++) {
            if (tiles[s] != ROAD || comp[s] >= 0) continue;
            int id = sizes.size(), head = 0, tail = 0;
            q[tail++] = s;
            comp[s] = id;
            while (head < tail) {
                int i = q[head++], x = i % w, y = i / w;
                int[] nb = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
                for (int j : nb)
                    if (j >= 0 && comp[j] < 0 && tiles[j] == ROAD) {
                        comp[j] = id;
                        q[tail++] = j;
                    }
            }
            sizes.add(tail);
        }
        int biggest = 0;
        for (int sz : sizes) biggest = Math.max(biggest, sz);
        for (int i = 0; i < w * h; i++)
            if (comp[i] >= 0 && sizes.get(comp[i]) < biggest / 5) unroad(i);
        List<int[]> connected = new ArrayList<int[]>();
        for (int[] b : kept) {
            boolean touches = false;
            for (int y = Math.max(0, b[1] - 1); y <= Math.min(h - 1, b[3]) && !touches; y++)
                for (int x = Math.max(0, b[0] - 1); x <= Math.min(w - 1, b[2]) && !touches; x++)
                    if (tiles[y * w + x] == ROAD) touches = true;
            if (touches) connected.add(b);
            else fill(b[0], b[1], b[2] - b[0], b[3] - b[1], GRASS);
        }
        blocks.clear();
        blocks.addAll(connected);
        for (int i = streets.size() - 1; i >= 0; i--) {
            Street st = streets.get(i);
            boolean any = false;
            for (int y = st.y0; y < st.y1 && !any; y++)
                for (int x = st.x0; x < st.x1 && !any; x++)
                    if (x >= 0 && y >= 0 && x < w && y < h && tiles[y * w + x] == ROAD) any = true;
            if (!any) streets.remove(i);
        }
        // Fields where town used to be belong to the countryside, and the districts' names move to what's left.
        if (countryDistrict >= 0) {
            float[] sx = new float[districts.size()], sy = new float[districts.size()];
            for (District d : districts) d.tiles = 0;
            for (int i = 0; i < w * h; i++) {
                if (!near[i]) districtAt[i] = (byte) countryDistrict;
                int id = districtAt[i];
                districts.get(id).tiles++;
                sx[id] += i % w;
                sy[id] += i / w;
            }
            for (int k = 0; k < districts.size(); k++) {
                District d = districts.get(k);
                if (k == countryDistrict || d.tiles == 0) continue;
                d.cx = (sx[k] / d.tiles + 0.5f) * T;
                d.cy = (sy[k] / d.tiles + 0.5f) * T;
            }
        }
        // The town is now as big as what's left.
        int x0 = w, y0 = h, x1 = 0, y1 = 0;
        for (int[] b : blocks) {
            x0 = Math.min(x0, b[0]);
            y0 = Math.min(y0, b[1]);
            x1 = Math.max(x1, b[2]);
            y1 = Math.max(y1, b[3]);
        }
        if (x1 > x0) {
            townX0 = Math.max(0, x0 - 3);
            townY0 = Math.max(0, y0 - 3);
            townX1 = Math.min(w, x1 + 3);
            townY1 = Math.min(h, y1 + 3);
        }
        // Lanes out into the country start from the ends of the roads on each side of town.
        int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] d : dirs) {
            int best = -1;
            float bestV = -Float.MAX_VALUE;
            for (int i = 0; i < w * h; i++) {
                if (tiles[i] != ROAD) continue;
                int x = i % w, y = i / w;
                if (railY0 >= 0 && y >= railY0 - 3 && y <= railY0 + railRows + 2) continue;
                // Only a road end with open country ahead of it.
                boolean clear = true;
                for (int k = 1; k <= 8 && clear; k++)
                    for (int t = 0; t < 2 && clear; t++) {
                        int ax = x + d[0] * k + (d[0] == 0 ? t : 0), ay = y + d[1] * k + (d[1] == 0 ? t : 0);
                        if (ax < 0 || ay < 0 || ax >= w || ay >= h) clear = false;
                        else if (tiles[ay * w + ax] != GRASS && tiles[ay * w + ax] != TREE) clear = false;
                    }
                if (!clear) continue;
                float v = x * d[0] + y * d[1] + rnd.nextFloat() * 4;
                if (v > bestV) {
                    bestV = v;
                    best = i;
                }
            }
            if (best >= 0) villageEnds.add(new int[]{best % w, best / w, d[0], d[1]});
        }
    }

    /**
     * Streets that stop dead in a field look like a mistake. A stub running on past the last houses is dug up
     * (back to the junction, if that was all it was), and a street that still ends with nothing beyond it
     * carries on as a dirt lane to the nearest other road or lane, or out to the edge of the map.
     */
    private void tidyDeadEnds() {
        boolean[] dug = new boolean[w * h];
        for (int[] e : deadEnds()) trimStub(e, dug);
        fixJunctions(dug);
        for (int[] e : deadEnds()) laneOn(e);
    }

    /** Digs up the end of a street where there's open ground on both sides (nothing it serves). */
    private void trimStub(int[] e, boolean[] dug) {
        int x = e[0], y = e[1], n = e[2], dx = e[3], dy = e[4];
        int px = dx == 0 ? 1 : 0, py = 1 - px;
        byte along = (byte) (dx == 0 ? 1 : 2);
        int bare = 0;
        while (bare < 40) {
            int rx = x - dx * bare, ry = y - dy * bare;
            // (A level crossing with nothing past it goes too: the street can stop at the tracks.)
            boolean ok = (open(rx - px, ry - py) && open(rx + px * n, ry + py * n))
                    || (railAt(rx - px, ry - py) && railAt(rx + px * n, ry + py * n));
            for (int k = 0; k < n && ok; k++) {
                int ax = rx + px * k, ay = ry + py * k;
                ok = ax >= 0 && ay >= 0 && ax < w && ay < h && tiles[ay * w + ax] == ROAD && roadDir[ay * w + ax] == along;
            }
            if (!ok) break;
            bare++;
        }
        // A street that's nothing but stub (a lane's worth of road out in a field) is left for the lane.
        if (bare >= 40) return;
        for (int b = 0; b < bare; b++)
            for (int k = 0; k < n; k++) {
                int rx = x - dx * b, ry = y - dy * b, i = (ry + py * k) * w + rx + px * k;
                boolean rail = railAt(rx - px, ry - py);
                unroad(i);
                if (rail) tiles[i] = RAIL;
                dug[i] = true;
            }
    }

    private boolean railAt(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && tiles[y * w + x] == RAIL;
    }

    private boolean dirtAt(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && tiles[y * w + x] == DIRT;
    }

    private boolean open(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        byte t = tiles[y * w + x];
        return t == GRASS || t == TREE;
    }

    /** A junction that lost a street to trimStub is a plain bend or a straight run if that's all it now is. */
    private void fixJunctions(boolean[] dug) {
        boolean[] seen = new boolean[w * h];
        int[] q = new int[w * h];
        for (int s = 0; s < w * h; s++) {
            if (seen[s] || tiles[s] != ROAD || roadDir[s] != 3) continue;
            int head = 0, tail = 0;
            q[tail++] = s;
            seen[s] = true;
            boolean touched = false, ns = false, ew = false;
            while (head < tail) {
                int i = q[head++], x = i % w, y = i / w;
                for (int k = 0; k < 4; k++) {
                    int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int j = ny * w + nx;
                    if (dug[j]) touched = true;
                    if (tiles[j] != ROAD) continue;
                    if (roadDir[j] == 3) {
                        if (!seen[j]) {
                            seen[j] = true;
                            q[tail++] = j;
                        }
                    } else if (roadDir[j] == 1 && k >= 2) ns = true;
                    else if (roadDir[j] == 2 && k < 2) ew = true;
                }
            }
            if (!touched || (ns && ew) || (!ns && !ew)) continue;
            for (int k = 0; k < tail; k++) roadDir[q[k]] = (byte) (ns ? 1 : 2);
        }
    }

    /**
     * Carries a dead end on as a two-tile dirt lane, by the shortest (and straightest) way across open ground
     * to a road or lane well away from it along the roads, or to the edge of the map.
     */
    private void laneOn(int[] e) {
        int x = e[0], y = e[1], n = e[2], dx = e[3], dy = e[4];
        int px = dx == 0 ? 1 : 0, py = 1 - px;
        // How far every road and lane is from this end, going by road.
        int[] far = new int[w * h];
        Arrays.fill(far, Integer.MAX_VALUE);
        int[] q = new int[w * h];
        int head = 0, tail = 0;
        for (int k = 0; k < n; k++) {
            int i = (y + py * k) * w + x + px * k;
            far[i] = 0;
            q[tail++] = i;
        }
        while (head < tail) {
            int i = q[head++], cx = i % w, cy = i / w;
            if (far[i] > 600) continue;
            for (int k = 0; k < 4; k++) {
                int nx = cx + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = cy + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int j = ny * w + nx;
                if (far[j] != Integer.MAX_VALUE || (tiles[j] != ROAD && tiles[j] != DIRT)) continue;
                far[j] = far[i] + 1;
                q[tail++] = j;
            }
        }
        // The lane's 2x2 footprint, by its top-left tile; it starts just beyond the middle of the end.
        int a = Math.max(0, n / 2 - 1);
        int sx = x + px * a + (dx > 0 ? 1 : dx < 0 ? -2 : 0), sy = y + py * a + (dy > 0 ? 1 : dy < 0 ? -2 : 0);
        if (!laneFits(sx, sy)) return;
        // Dijkstra over (tile, heading): a step costs 2, a turn 5 more, so lanes run fairly straight.
        int[] cost = new int[w * h * 4], from = new int[w * h * 4];
        Arrays.fill(cost, Integer.MAX_VALUE);
        int startDir = dx > 0 ? 0 : dx < 0 ? 1 : dy > 0 ? 2 : 3;
        java.util.PriorityQueue<long[]> pq = new java.util.PriorityQueue<long[]>(64, new java.util.Comparator<long[]>() {
            public int compare(long[] p, long[] r) {
                return Long.compare(p[0], r[0]);
            }
        });
        int s0 = (sy * w + sx) * 4 + startDir;
        cost[s0] = 0;
        from[s0] = -1;
        pq.add(new long[]{0, s0});
        int goal = -1;
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            int st = (int) top[1], c = (int) top[0];
            if (c != cost[st]) continue;
            int cell = st / 4, dir = st % 4, cx = cell % w, cy = cell / w;
            if (laneArrives(cx, cy, far, c / 2)) {
                goal = st;
                break;
            }
            if (c > 300) break;
            for (int k = 0; k < 4; k++) {
                if ((k ^ 1) == dir) continue;
                int nx = cx + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = cy + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (!laneFits(nx, ny)) continue;
                int ns = (ny * w + nx) * 4 + k, nc = c + 2 + (k != dir ? 5 : 0);
                if (nc >= cost[ns]) continue;
                cost[ns] = nc;
                from[ns] = st;
                pq.add(new long[]{nc, ns});
            }
        }
        if (goal < 0) return;
        for (int st = goal; st >= 0; st = from[st]) {
            int cell = st / 4, cx = cell % w, cy = cell / w;
            for (int j = cy; j < cy + 2; j++)
                for (int i = cx; i < cx + 2; i++)
                    if (open(i, j)) tiles[j * w + i] = DIRT;
        }
    }

    /** Open ground for a lane's 2x2 footprint, not up against the highway or the railway. */
    private boolean laneFits(int x, int y) {
        if (x < 0 || y < 0 || x + 1 >= w || y + 1 >= h) return false;
        for (int j = y - 1; j <= y + 2; j++)
            for (int i = x - 1; i <= x + 2; i++) {
                boolean inside = i >= x && i <= x + 1 && j >= y && j <= y + 1;
                if (inside && !open(i, j)) return false;
                if (i < 0 || j < 0 || i >= w || j >= h) continue;
                if (tiles[j * w + i] == RAIL || (oneWay != null && oneWay[j * w + i] != 0)) return false;
            }
        return true;
    }

    /** Whether a lane's footprint at (x, y), len tiles out, has reached the map edge or a road worth joining. */
    private boolean laneArrives(int x, int y, int[] far, int len) {
        if ((x == 0 || y == 0 || x + 2 >= w || y + 2 >= h) && len < 90) return true;
        for (int j = y - 1; j <= y + 2; j++)
            for (int i = x - 1; i <= x + 2; i++) {
                if ((i == x - 1 || i == x + 2) && (j == y - 1 || j == y + 2)) continue;
                if (i < 0 || j < 0 || i >= w || j >= h) continue;
                int f = far[j * w + i];
                if ((tiles[j * w + i] == ROAD || tiles[j * w + i] == DIRT) && (f == Integer.MAX_VALUE || f > len * 2 + 30))
                    return true;
            }
        return false;
    }

    /**
     * Road ends with nowhere to go: {x, y, width, dx, dy} for the last row of a street (its first tile, how
     * many tiles across) that stops facing (dx, dy) into open fields.
     */
    List<int[]> deadEnds() {
        List<int[]> out = new ArrayList<int[]>();
        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] d : dirs) {
            int px = d[0] == 0 ? 1 : 0, py = 1 - px;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    if (!endTile(x, y, d) || endTile(x - px, y - py, d)) continue;
                    int n = 0;
                    while (endTile(x + px * n, y + py * n, d)) n++;
                    // (A one-tile gap is a median or a planter, not the end of the road.)
                    boolean dead = n >= 2;
                    // A street turning the corner, or ending at a side street, isn't a dead end.
                    for (int b = 0; b < n + 1 && dead; b++) {
                        int rx = x - d[0] * b, ry = y - d[1] * b;
                        if (isRoad(rx - px, ry - py) || isRoad(rx + px * n, ry + py * n)
                                || dirtAt(rx - px, ry - py) || dirtAt(rx + px * n, ry + py * n)) dead = false;
                    }
                    for (int k = 0; k < n && dead; k++)
                        for (int s = 1; s <= 3 && dead; s++) {
                            int bx = x + px * k + d[0] * s, by = y + py * k + d[1] * s;
                            if (bx < 0 || by < 0 || bx >= w || by >= h) dead = s > 1;
                            else if (tiles[by * w + bx] != GRASS && tiles[by * w + bx] != TREE) dead = false;
                        }
                    if (dead) out.add(new int[]{x, y, n, d[0], d[1]});
                }
        }
        return out;
    }

    /** A tile on the last row of a street running towards (d[0], d[1]), with no road beyond it. */
    private boolean endTile(int x, int y, int[] d) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        int i = y * w + x;
        if (tiles[i] != ROAD || roadDir[i] != (d[0] == 0 ? 1 : 2)) return false;
        int bx = x + d[0], by = y + d[1];
        return bx < 0 || by < 0 || bx >= w || by >= h || tiles[by * w + bx] != ROAD;
    }

    private void unroad(int i) {
        tiles[i] = GRASS;
        roadDir[i] = 0;
        mainRoad[i] = false;
    }


    /**
     * Hamlets out in the country: a short street of cottages and a farm or two, with a lane back to town.
     * Each is a named place of its own.
     */
    private void hamlets(int count) {
        float tcx = (townX0 + townX1) / 2f, tcy = (townY0 + townY1) / 2f;
        settlements.add(new float[]{tcx * T, tcy * T});
        List<float[]> made = new ArrayList<float[]>();
        for (int k = 0; k < count; k++) {
            for (int tries = 0; tries < 250; tries++) {
                int cx = 30 + rnd.nextInt(Math.max(1, w - 60)), cy = 30 + rnd.nextInt(Math.max(1, h - 60));
                // Clear of the town's own (eroded, irregular) edge rather than its bounding box.
                if (!allOpen(cx - 28, cy - 28, 56, 56)) continue;
                if (railY0 >= 0 && Math.abs(cy - railY0) < 30) continue;
                boolean far = true;
                for (float[] o : made) if (Math.hypot(o[0] - cx, o[1] - cy) < 90) far = false;
                if (!far) continue;
                boolean vertical = rnd.nextBoolean();
                int len = 30 + rnd.nextInt(20);
                Street st = vertical ? new Street(cx - 1, cy - len / 2, cx + 2, cy + len / 2, true, false)
                        : new Street(cx - len / 2, cy - 1, cx + len / 2, cy + 2, false, false);
                carve(st);
                plotsAlong(st, cx - 25, cy - 25, cx + 25, cy + 25, 2);
                made.add(new float[]{cx, cy});
                settlements.add(new float[]{(cx + 0.5f) * T, (cy + 0.5f) * T});
                // A lane from the end nearer town, back to the nearest road.
                int ex, ey;
                if (vertical) {
                    ex = cx;
                    ey = tcy < cy ? st.y0 - 1 : st.y1;
                } else {
                    ex = tcx < cx ? st.x0 - 1 : st.x1;
                    ey = cy;
                }
                laneTo(ex, ey, (int) tcx, (int) tcy);
                nameHamlet(cx, cy);
                break;
            }
        }
    }

    private boolean allOpen(int x, int y, int fw, int fh) {
        for (int j = y; j < y + fh; j++)
            for (int i = x; i < x + fw; i++) {
                if (i < 1 || j < 1 || i >= w - 1 || j >= h - 1) return false;
                byte t = tiles[j * w + i];
                if (t != GRASS && t != TREE) return false;
            }
        return true;
    }

    /** A winding two-tile dirt lane from (x, y) towards (tx, ty), until it meets a road. */
    private void laneTo(int x, int y, int tx, int ty) {
        for (int step = 0; step < w * 3; step++) {
            int dx = tx - x, dy = ty - y;
            if (dx == 0 && dy == 0) break;
            boolean alongX = Math.abs(dx) * (0.7f + rnd.nextFloat() * 0.6f) > Math.abs(dy);
            if (alongX) x += Integer.signum(dx);
            else y += Integer.signum(dy);
            boolean hit = false;
            for (int j = y; j < y + 2; j++)
                for (int i = x; i < x + 2; i++) {
                    if (i < 0 || j < 0 || i >= w || j >= h) continue;
                    int k = j * w + i;
                    if (tiles[k] == ROAD) hit = step > 2;
                    else if (tiles[k] == RAIL) {
                        // A level crossing.
                        tiles[k] = ROAD;
                        roadDir[k] = 1;
                    } else if (tiles[k] == GRASS || tiles[k] == TREE) tiles[k] = DIRT;
                    else if (tiles[k] != DIRT && step > 2) hit = true;
                }
            if (hit) break;
        }
    }

    private void nameHamlet(int cx, int cy) {
        if (districtAt == null) return;
        District d = new District();
        d.type = DT_PARKSIDE;
        d.name = country.districtWords[rnd.nextInt(country.districtWords.length)] + country.hamletEnds[rnd.nextInt(country.hamletEnds.length)];
        for (District o : districts) if (o.name.equals(d.name)) d.name = d.name + " Village";
        d.x = cx;
        d.y = cy;
        d.cx = (cx + 0.5f) * T;
        d.cy = (cy - 8) * T;
        int id = districts.size();
        if (id > 120) return;
        districts.add(d);
        for (int y = Math.max(0, cy - 16); y < Math.min(h, cy + 16); y++)
            for (int x = Math.max(0, cx - 16); x < Math.min(w, cx + 16); x++) {
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > 16 * 16) continue;
                District old = districts.get(districtAt[y * w + x]);
                old.tiles--;
                districtAt[y * w + x] = (byte) id;
                d.tiles++;
            }
    }

    private boolean allGrass(int x, int y, int fw, int fh) {
        for (int j = y; j < y + fh; j++)
            for (int i = x; i < x + fw; i++)
                if (i < 1 || j < 1 || i >= w - 1 || j >= h - 1 || tiles[j * w + i] != GRASS) return false;
        return true;
    }

    /** The unused block with room for (mw x mh) inside, closest to a distance from the centre (0-1). */
    private int bigBlock(boolean[] used, int mw, int mh, float want) {
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int k = 0; k < blocks.size(); k++) {
            int[] b = blocks.get(k);
            int iw = b[2] - b[0] - 2, ih = b[3] - b[1] - 2;
            if (used[k] || !((iw >= mw && ih >= mh) || (iw >= mh && ih >= mw))) continue;
            float cx = (b[0] + b[2]) / 2f - w / 2f, cy = (b[1] + b[3]) / 2f - h / 2f;
            float d = (float) Math.sqrt(cx * cx + cy * cy) / (w * 0.5f);
            float score = Math.abs(d - want) + rnd.nextFloat() * 0.15f;
            if (score < bestScore) {
                bestScore = score;
                best = k;
            }
        }
        return best;
    }

    /**
     * Four-way junctions sometimes become roundabouts: a round island in the middle that traffic drives around.
     * Only junctions with a road coming in on every side.
     */
    private void roundabouts() {
        for (int y = 5; y < h - 10; y++)
            for (int x = 5; x < w - 10; x++)
                for (int sz = 3; sz <= 5; sz += 2) {
                    if (railY0 >= 0 && y + sz >= railY0 - 3 && y <= railY0 + railRows + 3) continue;
                    boolean ok = arm(x - 1, y, sz, false) && arm(x + sz, y, sz, false) && arm(x, y - 1, sz, true)
                            && arm(x, y + sz, sz, true);
                    for (int j = 0; j < sz && ok; j++)
                        for (int k = 0; k < sz && ok; k++) if (tiles[(y + j) * w + x + k] != ROAD) ok = false;
                    if (!ok || rnd.nextFloat() > 0.4f) continue;
                    int island = sz == 3 ? 1 : 3, o = (sz - island) / 2;
                    for (int j = 0; j < island; j++)
                        for (int k = 0; k < island; k++) {
                            tiles[(y + o + j) * w + x + o + k] = GRASS;
                            roadDir[(y + o + j) * w + x + o + k] = 0;
                        }
                    for (int j = 0; j < sz; j++)
                        for (int k = 0; k < sz; k++) if (roadDir[(y + j) * w + x + k] != 0) roadDir[(y + j) * w + x + k] = 3;
                    addDecor(D_ROUNDABOUT, x * T, y * T, (x + sz) * T, (y + sz) * T, island);
                }
    }

    /** A road exactly sz tiles wide leading away at (x, y): vertical (running north-south) or horizontal. */
    private boolean arm(int x, int y, int sz, boolean vertical) {
        for (int k = 0; k < sz; k++) {
            int i = vertical ? x + k : x, j = vertical ? y : y + k;
            if (roadDir[j * w + i] != (vertical ? 1 : 2)) return false;
        }
        int bi = vertical ? x - 1 : x, bj = vertical ? y : y - 1, ai = vertical ? x + sz : x, aj = vertical ? y : y + sz;
        return roadDir[bj * w + bi] == 0 && roadDir[aj * w + ai] == 0;
    }

    /** A shopping mall in the middle of its car park. */
    private void mall(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, LOT);
        int mw = Math.max(6, bw - 4), mh = Math.max(5, bh - 4);
        int mx = x + (bw - mw) / 2, my = y + (bh - mh) / 2;
        addFacilityLot(mx, my, mw, mh, MALL, 2 + rnd.nextInt(2));
        for (int j = y; j < y + bh; j++)
            for (int i = x; i < x + bw; i++)
                if (tiles[j * w + i] == LOT && (j - y) % 3 != 1 && rnd.nextFloat() < 0.3f && !hasNeighbor(i, j, CAR))
                    tiles[j * w + i] = CAR;
        openAreas.add(new float[]{(x + bw / 2f) * T, (y + bh / 2f) * T, 2});
    }

    /** A football stadium: stands on all four sides with gaps to walk in, the pitch in the middle. */
    private void stadium(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, PLAZA);
        int t = 2, gap = 2;
        int mx = x + bw / 2 - 1, my = y + bh / 2 - 1;
        // Top and bottom stands (split by an entrance), then the ends.
        addFacilityLot(x, y, mx - x, t, STADIUM, 2);
        addFacilityLot(mx + gap, y, x + bw - mx - gap, t, STADIUM, 2);
        addFacilityLot(x, y + bh - t, mx - x, t, STADIUM, 2);
        addFacilityLot(mx + gap, y + bh - t, x + bw - mx - gap, t, STADIUM, 2);
        addFacilityLot(x, y + t, t, my - y - t, STADIUM, 2);
        addFacilityLot(x, my + gap, t, y + bh - t - my - gap, STADIUM, 2);
        addFacilityLot(x + bw - t, y + t, t, my - y - t, STADIUM, 2);
        addFacilityLot(x + bw - t, my + gap, t, y + bh - t - my - gap, STADIUM, 2);
        fill(x + t + 1, y + t + 1, bw - 2 * t - 2, bh - 2 * t - 2, GRASS);
        addDecor(D_FIELD, (x + t + 1) * T + 3, (y + t + 1) * T + 3, (x + bw - t - 1) * T - 3, (y + bh - t - 1) * T - 3, 0);
        addDecor(D_FLOODLIGHT, (x + t) * T, (y + t) * T, (x + bw - t) * T, (y + bh - t) * T, 0);
        openAreas.add(new float[]{(x + bw / 2f) * T, (y + bh / 2f) * T, 0});
    }

    /** A power station: turbine hall, chimneys, and a fenced yard of transformers. */
    private void powerStation(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, LOT);
        boolean wide = bw >= bh;
        int hw = wide ? bw / 2 : bw - 2, hh = wide ? bh - 2 : bh / 2;
        addFacilityLot(x + 1, y + 1, hw, hh, POWER, 3);
        int yx = wide ? x + hw + 2 : x + 1, yy = wide ? y + 1 : y + hh + 2;
        int yw = wide ? bw - hw - 3 : bw - 2, yh = wide ? bh - 2 : bh - hh - 3;
        if (yw < 3 || yh < 3) return;
        for (int i = yx; i < yx + yw; i++) {
            tiles[yy * w + i] = FENCE;
            tiles[(yy + yh - 1) * w + i] = FENCE;
        }
        for (int j = yy; j < yy + yh; j++) {
            tiles[j * w + yx] = FENCE;
            tiles[j * w + yx + yw - 1] = FENCE;
        }
        addDecor(D_POWER, (yx + 1) * T, (yy + 1) * T, (yx + yw - 1) * T, (yy + yh - 1) * T, 0);
    }

    /** A building site: hoardings round the edge, a crane, foundations and piles of materials. */
    private void constructionSite(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, LOT);
        for (int i = x; i < x + bw; i++) {
            if (Math.abs(i - (x + bw / 2)) > 1) tiles[y * w + i] = FENCE;
            tiles[(y + bh - 1) * w + i] = FENCE;
        }
        for (int j = y; j < y + bh; j++) {
            tiles[j * w + x] = FENCE;
            tiles[j * w + x + bw - 1] = FENCE;
        }
        addDecor(D_SITE, (x + 1) * T, (y + 1) * T, (x + bw - 1) * T, (y + bh - 1) * T, rnd.nextInt(4));
        // A half-built block in one corner.
        if (bw >= 16 && bh >= 16) addLot(x + bw - 8, y + bh - 8, 5, 5, OFFICE);
    }

    /** A farm: the farmhouse, a barn and a silo in the yard, and fields of crops. */
    private void farm(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, GRASS);
        boolean wide = bw >= bh;
        int yard = 8;
        int yx = x, yy = y;
        addLot(yx + 1, yy + 1, 5, 4, HOUSE);
        addFacilityLot(wide ? yx + 1 : yx + 7, wide ? yy + 7 : yy + 1, 6, 4, BARN, 1);
        if (wide ? bh >= 15 : bw >= 15) addFacilityLot(wide ? yx + 4 : yx + 14, wide ? yy + 12 : yy + 4, 2, 2, SILO, 3);
        int fx = wide ? x + yard + 1 : x, fy = wide ? y : y + yard + 1;
        int fw = wide ? bw - yard - 1 : bw, fh = wide ? bh : bh - yard - 1;
        // Split the land into two or three fields with different crops.
        int fields = Math.max(1, Math.min(3, (wide ? fw : fh) / 10));
        for (int k = 0; k < fields; k++) {
            float a0 = (wide ? fx : fy) + (wide ? fw : fh) * k / (float) fields, a1 = (wide ? fx : fy) + (wide ? fw : fh) * (k + 1) / (float) fields;
            if (wide) addDecor(D_CROPS, a0 * T + 3, fy * T + 3, a1 * T - 3, (fy + fh) * T - 3, rnd.nextInt(3));
            else addDecor(D_CROPS, fx * T + 3, a0 * T + 3, (fx + fw) * T - 3, a1 * T - 3, rnd.nextInt(3));
        }
    }

    /** The helipad nearest a point, or null if the city has none. */
    float[] nearestHelipad(float x, float y) {
        float[] best = null;
        float bd = Float.MAX_VALUE;
        for (float[] p : helipads) {
            float d = (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** The building standing at a world position, or null. */
    Building buildingAt(float x, float y) {
        int tx = (int) Math.floor(x / T), ty = (int) Math.floor(y / T);
        if (tx < 0 || ty < 0 || tx >= w || ty >= h) return null;
        int i = buildingAt[ty * w + tx];
        return i < 0 || buildings.get(i).collapsed ? null : buildings.get(i);
    }

    /** Knocks a building down: its tiles become a walkable heap of rubble. */
    void collapse(Building b) {
        b.collapsed = true;
        b.height = 0;
        b.occupants.clear();
        b.capacity = 0;
        int tx0 = (int) (b.x0 / T), ty0 = (int) (b.y0 / T), tx1 = (int) (b.x1 / T), ty1 = (int) (b.y1 / T);
        for (int j = ty0; j < ty1; j++)
            for (int i = tx0; i < tx1; i++) {
                int k = j * w + i;
                if (tiles[k] != BUILDING) continue;
                tiles[k] = RUBBLE;
                solid[k] = false;
                opaque[k] = false;
            }
        drawRubble(b);
        for (Facility f : facilities) fieldFromPoints(f.field, new float[]{f.x}, new float[]{f.y}, 1);
    }

    private void drawRubble(Building b) {
        Canvas c = new Canvas(bitmap);
        if (detail != 1f) c.scale(detail, detail);
        drawRubble(c, b);
        renderVersion++;
    }

    private void drawRubble(Canvas c, Building b) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        Random r = new Random(b.seed * 31L + 7);
        p.setColor(0xFF6E6860);
        c.drawRect(b.x0, b.y0, b.x1, b.y1, p);
        // Broken slabs and bricks, with the old wall colour mixed in.
        int n = (int) ((b.x1 - b.x0) * (b.y1 - b.y0) / 30);
        for (int k = 0; k < n; k++) {
            float x = b.x0 + r.nextFloat() * (b.x1 - b.x0), y = b.y0 + r.nextFloat() * (b.y1 - b.y0);
            float s = 1 + r.nextFloat() * 3.5f;
            int roll = r.nextInt(4);
            p.setColor(roll == 0 ? darken(b.wall, 0.8f) : roll == 1 ? darken(b.roof, 0.7f) : roll == 2 ? 0xFF8C867C : 0xFF4E4A45);
            c.drawRect(x - s, y - s * 0.6f, x + s, y + s * 0.6f, p);
        }
        p.setColor(0x60101010);
        for (int k = 0; k < n / 6; k++)
            c.drawCircle(b.x0 + r.nextFloat() * (b.x1 - b.x0), b.y0 + r.nextFloat() * (b.y1 - b.y0), 2 + r.nextFloat() * 4, p);
        // Stumps of the outer walls.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(darken(b.wall, 0.6f));
        c.drawLine(b.x0 + 1, b.y0 + 1, b.x0 + (b.x1 - b.x0) * (0.2f + r.nextFloat() * 0.3f), b.y0 + 1, p);
        c.drawLine(b.x1 - 1, b.y1 - 1, b.x1 - (b.x1 - b.x0) * (0.2f + r.nextFloat() * 0.3f), b.y1 - 1, p);
        c.drawLine(b.x0 + 1, b.y1 - 1, b.x0 + 1, b.y1 - (b.y1 - b.y0) * (0.2f + r.nextFloat() * 0.4f), p);
        p.setStyle(Paint.Style.FILL);
    }

    /** Scorches a burnt-out car into the map. */
    void charTile(int tx, int ty) {
        Canvas c = new Canvas(bitmap);
        if (detail != 1f) c.scale(detail, detail);
        charred.add(new int[]{tx, ty});
        charTile(c, tx, ty);
        renderVersion++;
    }

    /** Burnt-out cars scorched into the map, so a close-up picture of the area shows them too. */
    private final List<int[]> charred = new ArrayList<int[]>();

    private void charTile(Canvas c, int tx, int ty) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        float cx = tx * T + T / 2f, cy = ty * T + T / 2f;
        p.setColor(0xB0101010);
        c.drawCircle(cx, cy, 11, p);
        p.setColor(0xFF2A2522);
        c.drawRect(cx - 7, cy - 4, cx + 7, cy + 4, p);
        p.setColor(0xFF4A2F22);
        c.drawRect(cx - 5, cy - 2.5f, cx + 5, cy + 2.5f, p);
    }

    private void park(int x, int y, int pw, int ph) {
        fill(x, y, pw, ph, GRASS);
        int cx = x + pw / 2, cy = y + ph / 2;
        boolean organic = cfg.layout() == 2 || (cfg.layout() == 1 && rnd.nextBoolean());
        if (organic) {
            // Curved footpaths (drawn smoothly; the grass is walkable anyway).
            paths.add(new float[]{(x + pw / 2f) * T, (y + ph / 2f) * T, pw * T * 0.32f, ph * T * 0.3f});
        } else {
            for (int i = x; i < x + pw; i++) tiles[cy * w + i] = PLAZA;
            for (int j = y; j < y + ph; j++) tiles[j * w + cx] = PLAZA;
        }
        for (int j = y; j < y + ph; j++)
            for (int i = x; i < x + pw; i++)
                if (tiles[j * w + i] == GRASS && rnd.nextFloat() < 0.13f && !hasNeighbor(i, j, TREE))
                    tiles[j * w + i] = TREE;
    }

    private void plaza(int x, int y, int pw, int ph) {
        fill(x, y, pw, ph, PLAZA);
        if (pw >= 6 && ph >= 6) {
            int fx = x + pw / 2 - 1, fy = y + ph / 2 - 1;
            fill(fx, fy, 2, 2, STATUE);
            statues.add(new int[]{fx, fy});
        }
        int[][] corners = {{x + 1, y + 1}, {x + pw - 2, y + 1}, {x + 1, y + ph - 2}, {x + pw - 2, y + ph - 2}};
        for (int[] c : corners) if (tiles[c[1] * w + c[0]] == PLAZA) tiles[c[1] * w + c[0]] = TREE;
    }

    private void parking(int x, int y, int pw, int ph) {
        fill(x, y, pw, ph, LOT);
        for (int j = y; j < y + ph; j++) {
            if ((j - y) % 3 != 1) continue;
            for (int i = x + 1; i < x + pw - 1; i++) if (rnd.nextFloat() < 0.55f) tiles[j * w + i] = CAR;
        }
    }

    // ------------------------------------------------------------------ rendering

    private void render(Canvas c) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        c.drawColor(0xFF1B1C1F);
        // (Drawing just one region of the map, at a higher resolution: only the tiles in and around it.)
        int X0 = regionOnly ? rx0 : 0, Y0 = regionOnly ? ry0 : 0, X1 = regionOnly ? rx1 : w, Y1 = regionOnly ? ry1 : h;

        for (int y = Y0; y < Y1; y++) {
            for (int x = X0; x < X1; x++) {
                byte t = tiles[y * w + x];
                int col;
                boolean real = drawnRealistic;
                switch (t) {
                    case SIDEWALK: col = country.pavement != 0 ? country.pavement : real ? 0xFF96938C : 0xFF8F8D87; break;
                    case GRASS: case TREE: col = country.grass != 0 ? country.grass : real ? 0xFF4F6F36 : 0xFF4C7837; break;
                    case PLAZA: case STATUE: col = country.plaza != 0 ? country.plaza : real ? 0xFFB2A58A : 0xFFB3A487; break;
                    case LOT: col = real ? 0xFF46474A : 0xFF48494D; break;
                    case BUILDING: col = country.pavement != 0 ? country.pavement : real ? 0xFF96938C : 0xFF8F8D87; break;
                    case BASE: case FENCE: col = 0xFF6A6E5E; break;
                    case DIRT: col = real ? 0xFF8A7252 : 0xFF8C7456; break;
                    default: col = real ? 0xFF38393C : 0xFF3A3D43; break;
                }
                if (t == TREE && isPlazaTree(x, y)) col = 0xFFB3A487;
                if (t == CAR && isLotCar(x, y)) col = 0xFF48494D;
                if (t == CAR && carKind[y * w + x] == 2) col = 0xFF6A6E5E;
                p.setColor(col);
                c.drawRect(x * T, y * T, x * T + T, y * T + T, p);
            }
        }

        // Ground texture.
        p.setStrokeWidth(1f);
        for (int y = Y0; y < Y1; y++) {
            for (int x = X0; x < X1; x++) {
                byte t = tiles[y * w + x];
                float fx = x * T, fy = y * T;
                // Each tile's own randomness, so it looks the same however much of the map is drawn.
                prnd.setSeed(tileSeed(x, y, 1));
                if (t == SIDEWALK) {
                    // Paving slabs, a few of them newer or stained.
                    float roll = prnd.nextFloat();
                    if (roll < 0.08f) {
                        p.setColor(roll < 0.04f ? 0xFF999791 : 0xFF84827C);
                        c.drawRect(fx + (roll < 0.06f ? 0 : T / 2f), fy, fx + (roll < 0.06f ? T / 2f : T), fy + T, p);
                    }
                    p.setColor(0xFF7E7C77);
                    c.drawLine(fx, fy, fx + T, fy, p);
                    c.drawLine(fx, fy, fx, fy + T, p);
                    c.drawLine(fx + T / 2f, fy, fx + T / 2f, fy + T, p);
                } else if (t == GRASS || t == TREE) {
                    for (int k = 0; k < 6; k++) {
                        p.setColor(prnd.nextBoolean() ? 0xFF578A3F : 0xFF426B30);
                        c.drawCircle(fx + prnd.nextFloat() * T, fy + prnd.nextFloat() * T, 0.9f, p);
                    }
                    // Tufts and the odd wildflower.
                    if (prnd.nextFloat() < 0.35f) {
                        p.setColor(0xFF3A6128);
                        float gx = fx + prnd.nextFloat() * T, gy = fy + prnd.nextFloat() * T;
                        c.drawLine(gx, gy, gx - 1, gy - 2, p);
                        c.drawLine(gx, gy, gx + 1, gy - 2, p);
                    }
                    if (prnd.nextFloat() < 0.05f) {
                        int[] flowers = {0xFFF2E86B, 0xFFF2F2F2, 0xFFE87BB0, 0xFFB08AE8};
                        p.setColor(flowers[prnd.nextInt(flowers.length)]);
                        for (int k = 0; k < 3; k++)
                            c.drawCircle(fx + prnd.nextFloat() * T, fy + prnd.nextFloat() * T, 0.8f, p);
                    }
                } else if (t == DIRT) {
                    // Packed earth: wheel ruts and pebbles.
                    p.setColor(0xFF7A6446);
                    boolean vert = (y > 0 && tiles[(y - 1) * w + x] == DIRT) && (y < h - 1 && tiles[(y + 1) * w + x] == DIRT);
                    if (vert) {
                        c.drawRect(fx + 4, fy, fx + 6, fy + T, p);
                        c.drawRect(fx + 10, fy, fx + 12, fy + T, p);
                    } else {
                        c.drawRect(fx, fy + 4, fx + T, fy + 6, p);
                        c.drawRect(fx, fy + 10, fx + T, fy + 12, p);
                    }
                    for (int k = 0; k < 4; k++) {
                        p.setColor(prnd.nextBoolean() ? 0xFFA08A68 : 0xFF6E5A40);
                        c.drawCircle(fx + prnd.nextFloat() * T, fy + prnd.nextFloat() * T, 0.7f, p);
                    }
                } else if (t == PLAZA) {
                    p.setColor(0xFFA29376);
                    c.drawLine(fx, fy, fx + T, fy, p);
                    c.drawLine(fx, fy, fx, fy + T, p);
                } else if (t == ROAD || t == CAR) {
                    if (prnd.nextFloat() < 0.25f) {
                        p.setColor(0x22000000);
                        c.drawCircle(fx + prnd.nextFloat() * T, fy + prnd.nextFloat() * T, 1 + prnd.nextFloat() * 3, p);
                    }
                    float roll = prnd.nextFloat();
                    if (roll < 0.03f) {
                        // Patched asphalt.
                        p.setColor(0xFF33363B);
                        float pw = 5 + prnd.nextFloat() * 8, ph = 4 + prnd.nextFloat() * 6;
                        float px = fx + prnd.nextFloat() * (T - pw), py = fy + prnd.nextFloat() * (T - ph);
                        c.drawRect(px, py, px + pw, py + ph, p);
                    } else if (roll < 0.07f) {
                        // Cracks.
                        p.setColor(0xFF2A2C30);
                        float cx0 = fx + prnd.nextFloat() * T, cy0 = fy + prnd.nextFloat() * T;
                        float cx1 = cx0 + prnd.nextFloat() * 8 - 4, cy1 = cy0 + prnd.nextFloat() * 8 - 4;
                        c.drawLine(cx0, cy0, cx1, cy1, p);
                        c.drawLine(cx1, cy1, cx1 + prnd.nextFloat() * 6 - 3, cy1 + prnd.nextFloat() * 6 - 3, p);
                    } else if (roll < 0.085f && t == ROAD) {
                        // Manhole cover.
                        float mx = fx + 4 + prnd.nextFloat() * (T - 8), my = fy + 4 + prnd.nextFloat() * (T - 8);
                        p.setColor(0xFF26282B);
                        c.drawCircle(mx, my, 2.6f, p);
                        p.setColor(0xFF4A4C50);
                        c.drawCircle(mx, my, 2f, p);
                        p.setColor(0xFF34363A);
                        c.drawLine(mx - 1.5f, my, mx + 1.5f, my, p);
                        c.drawLine(mx, my - 1.5f, mx, my + 1.5f, p);
                    }
                }
            }
        }

        if (drawnRealistic) drawRealGround(c, p);
        drawRoadMarkings(c, p);
        drawHighway(c, p);
        drawRail(c, p);
        drawStreetFurniture(c, p);
        drawParkingLines(c, p);
        if (drawnRealistic) drawKerbs(c, p);
        roundCorners(c, p);
        drawBaseDetails(c, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (float[] pa : paths) {
            p.setStrokeWidth(8f);
            p.setColor(0xFFB3A487);
            c.drawOval(new RectF(pa[0] - pa[2], pa[1] - pa[3], pa[0] + pa[2], pa[1] + pa[3]), p);
            c.drawLine(pa[0], pa[1] - pa[3] * 1.7f, pa[0], pa[1] - pa[3], p);
            c.drawLine(pa[0], pa[1] + pa[3], pa[0], pa[1] + pa[3] * 1.7f, p);
            c.drawLine(pa[0] - pa[2] * 1.6f, pa[1], pa[0] - pa[2], pa[1], p);
            c.drawLine(pa[0] + pa[2], pa[1], pa[0] + pa[2] * 1.6f, pa[1], p);
        }
        p.setStyle(Paint.Style.FILL);

        for (float[] d : decor) if (!offRegion(d[1], d[2], d[3], d[4])) drawDecor(c, p, d);

        // Building shadows (longer for taller buildings), then roofs. The roof art is drawn at the
        // footprint and GameView lifts it to the building's height.
        Path shadow = new Path();
        if (drawnRealistic) {
            // Soft light: ambient darkening hugging each building, and a pale penumbra past the shadow's edge.
            for (Building b : buildings) {
                if (offRegion(b.x0, b.y0, b.x1, b.y1)) continue;
                for (int k = 3; k >= 1; k--) {
                    p.setColor(0x16000000);
                    c.drawRect(b.x0 - k * 1.6f, b.y0 - k * 1.6f, b.x1 + k * 1.6f, b.y1 + k * 1.6f, p);
                }
                float sx = b.height * 0.28f * 1.12f + 2, sy = b.height * 0.38f * 1.12f + 2;
                shadow.reset();
                shadow.moveTo(b.x0 - 1, b.y0 - 1);
                shadow.lineTo(b.x1 + 1, b.y0 - 1);
                shadow.lineTo(b.x1 + sx, b.y0 + sy);
                shadow.lineTo(b.x1 + sx, b.y1 + sy);
                shadow.lineTo(b.x0 + sx, b.y1 + sy);
                shadow.lineTo(b.x0 - 1, b.y1 + 1);
                shadow.close();
                p.setColor(0x22000000);
                c.drawPath(shadow, p);
            }
        }
        p.setColor(drawnRealistic ? 0x46000000 : 0x50000000);
        for (Building b : buildings) {
            if (offRegion(b.x0, b.y0, b.x1, b.y1)) continue;
            float sx = b.height * 0.28f, sy = b.height * 0.38f;
            shadow.reset();
            shadow.moveTo(b.x0, b.y0);
            shadow.lineTo(b.x1, b.y0);
            shadow.lineTo(b.x1 + sx, b.y0 + sy);
            shadow.lineTo(b.x1 + sx, b.y1 + sy);
            shadow.lineTo(b.x0 + sx, b.y1 + sy);
            shadow.lineTo(b.x0, b.y1);
            shadow.close();
            c.drawPath(shadow, p);
        }
        for (int[] b : buildingLots) if (!offRegion(b[0] * T, b[1] * T, (b[0] + b[2]) * T, (b[1] + b[3]) * T)) drawBuilding(c, p, b);
        if (drawnRealistic)
            for (int[] b : buildingLots) if (!offRegion(b[0] * T, b[1] * T, (b[0] + b[2]) * T, (b[1] + b[3]) * T)) weatherRoof(c, p, b);

        for (int[] f : statues) {
            // A statue on a stepped stone plinth.
            float cx = (f[0] + 1) * T, cy = (f[1] + 1) * T;
            p.setColor(0x50000000);
            c.drawRect(cx - 12, cy - 10, cx + 16, cy + 16, p);
            p.setColor(0xFF9C968A);
            c.drawRect(cx - 14, cy - 14, cx + 14, cy + 14, p);
            p.setColor(0xFFB2AC9F);
            c.drawRect(cx - 10, cy - 10, cx + 10, cy + 10, p);
            p.setColor(0xFF5E6A5C);
            c.drawCircle(cx, cy, 5, p);
            p.setColor(0xFF7D8B7A);
            c.drawCircle(cx - 1.2f, cy - 1.2f, 2.6f, p);
        }

        for (int y = Y0; y < Y1; y++)
            for (int x = X0; x < X1; x++)
                if (tiles[y * w + x] == CAR) drawCar(c, p, x, y);

        for (float[] pu : pumps) {
            p.setColor(0x50000000);
            c.drawRect(pu[0] - 2.5f, pu[1] - 3, pu[0] + 4.5f, pu[1] + 5, p);
            p.setColor(0xFFD83A3A);
            c.drawRect(pu[0] - 3.5f, pu[1] - 4.5f, pu[0] + 3.5f, pu[1] + 4.5f, p);
            p.setColor(0xFFEEEEEE);
            c.drawRect(pu[0] - 2.5f, pu[1] - 3.5f, pu[0] + 2.5f, pu[1] - 0.5f, p);
            p.setColor(0xFF222222);
            c.drawRect(pu[0] + 3.5f, pu[1] - 1, pu[0] + 5, pu[1] + 3, p);
        }
        // Gas station canopies are drawn over the pumps, see-through so the pumps show.
        for (float[] d : decor) {
            if (d[0] != D_CANOPY) continue;
            p.setColor(0x50000000);
            c.drawRect(d[1] + 4, d[2] + 5, d[3] + 4, d[4] + 5, p);
            p.setColor(0x70F2F2F2);
            c.drawRect(d[1], d[2], d[3], d[4], p);
            p.setColor(0xFFD83A3A);
            c.drawRect(d[1], d[2], d[3], d[2] + 3, p);
            c.drawRect(d[1], d[4] - 3, d[3], d[4], p);
            c.drawRect(d[1], d[2], d[1] + 3, d[4], p);
            c.drawRect(d[3] - 3, d[2], d[3], d[4], p);
        }

        for (float[] l : lamps) {
            p.setColor(0x50000000);
            c.drawCircle(l[0] + 1.5f, l[1] + 2f, 2f, p);
            p.setColor(0xFF2E3034);
            c.drawCircle(l[0], l[1], 2f, p);
            p.setColor(0xFFE8E4D0);
            c.drawCircle(l[0], l[1], 1.1f, p);
        }

        for (int y = Y0; y < Y1; y++)
            for (int x = X0; x < X1; x++)
                if (tiles[y * w + x] == TREE) {
                    float cx = x * T + T / 2f, cy = y * T + T / 2f;
                    prnd.setSeed(tileSeed(x, y, 2));
                    float r = 7.5f + prnd.nextFloat() * 2f;
                    p.setColor(0x50000000);
                    c.drawCircle(cx + TREE_HEIGHT * 0.28f, cy + TREE_HEIGHT * 0.38f, r, p);
                    p.setColor(0xFF4A3524);
                    c.drawCircle(cx, cy, 2f, p);
                    if (!regionOnly) trees.add(new float[]{cx, cy, r});
                }

        // Street trees along the pavement, their canopies over people's heads.
        if (cfg.parks() > 0)
            for (int y = Math.max(1, Y0); y < Math.min(h - 1, Y1); y++)
                for (int x = Math.max(1, X0); x < Math.min(w - 1, X1); x++) {
                    if (tiles[y * w + x] != SIDEWALK || ((x * 7 + y * 13) % (cfg.parks() >= 2 ? 5 : 8)) != 0) continue;
                    boolean nextToRoad = tiles[y * w + x - 1] == ROAD || tiles[y * w + x + 1] == ROAD
                            || tiles[(y - 1) * w + x] == ROAD || tiles[(y + 1) * w + x] == ROAD;
                    if (!nextToRoad || hasNeighborDir(x, y) || hasNeighbor(x, y, CAR)) continue;
                    boolean lamp = false;
                    for (float[] l : lamps) if (Math.abs(l[0] - (x * T + T / 2f)) < T * 2 && Math.abs(l[1] - (y * T + T / 2f)) < T * 2) lamp = true;
                    if (lamp) continue;
                    float cx = x * T + T / 2f, cy = y * T + T / 2f, r = 6f + ((x * 3 + y) % 3);
                    p.setColor(0x40000000);
                    c.drawCircle(cx + TREE_HEIGHT * 0.28f, cy + TREE_HEIGHT * 0.38f, r, p);
                    p.setColor(0xFF6B5A48);
                    c.drawRect(cx - 3.5f, cy - 3.5f, cx + 3.5f, cy + 3.5f, p);
                    p.setColor(0xFF4A3524);
                    c.drawCircle(cx, cy, 1.6f, p);
                    if (!regionOnly) trees.add(new float[]{cx, cy, r});
                }
    }

    // ------------------------------------------------------------------ realistic graphics

    /**
     * Realistic ground: grainy asphalt with darker tyre tracks and the odd oil stain, paving slabs that
     * vary in tone, and grass with lighter and darker patches.
     */
    private void drawRealGround(Canvas c, Paint p) {
        Random r = new Random(cfg.seed * 7L + 1);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                byte t = tiles[y * w + x];
                float fx = x * T, fy = y * T;
                if (t == ROAD || t == CAR) {
                    for (int k = 0; k < 5; k++) {
                        p.setColor(r.nextBoolean() ? 0x14FFFFFF : 0x1A000000);
                        float gx = fx + r.nextFloat() * T, gy = fy + r.nextFloat() * T;
                        c.drawRect(gx, gy, gx + 0.8f, gy + 0.8f, p);
                    }
                    // Patchy tarmac: lighter worn areas and darker fresh ones.
                    if (r.nextFloat() < 0.2f) {
                        p.setColor(0x08000000);
                        float bx = fx + r.nextFloat() * T, by = fy + r.nextFloat() * T, br = 5 + r.nextFloat() * 7;
                        c.drawCircle(bx, by, br, p);
                        c.drawCircle(bx, by, br * 0.6f, p);
                    }
                    if (r.nextFloat() < 0.025f) {
                        p.setColor(0x22101010);
                        c.drawCircle(fx + 4 + r.nextFloat() * 8, fy + 4 + r.nextFloat() * 8, 2 + r.nextFloat() * 2.5f, p);
                    }
                } else if (t == SIDEWALK) {
                    // Each half-slab a slightly different tone, with the odd crack.
                    for (int k = 0; k < 2; k++) {
                        int tone = r.nextInt(3);
                        if (tone == 0) continue;
                        p.setColor(tone == 1 ? 0x10FFFFFF : 0x12000000);
                        c.drawRect(fx + k * T / 2f + 0.5f, fy + 0.5f, fx + (k + 1) * T / 2f - 0.5f, fy + T - 0.5f, p);
                    }
                    if (r.nextFloat() < 0.06f) {
                        p.setColor(0x33000000);
                        p.setStrokeWidth(0.5f);
                        float cx = fx + r.nextFloat() * T, cy = fy + r.nextFloat() * T;
                        c.drawLine(cx, cy, cx + r.nextFloat() * 6 - 3, cy + r.nextFloat() * 6 - 3, p);
                    }
                } else if (t == GRASS || t == TREE) {
                    // Mown lawn stripes.
                    p.setColor((x / 2) % 2 == 0 ? 0x0CFFFFFF : 0x08000000);
                    c.drawRect(fx, fy, fx + T, fy + T, p);
                    if (r.nextFloat() < 0.3f) {
                        p.setColor(r.nextBoolean() ? 0x1A8AC060 : 0x18203A10);
                        c.drawCircle(fx + r.nextFloat() * T, fy + r.nextFloat() * T, 4 + r.nextFloat() * 7, p);
                    }
                    for (int k = 0; k < 4; k++) {
                        p.setColor(r.nextBoolean() ? 0x30A8D080 : 0x30284A18);
                        float gx = fx + r.nextFloat() * T, gy = fy + r.nextFloat() * T;
                        c.drawRect(gx, gy, gx + 0.6f, gy + 1.6f, p);
                    }
                } else if (t == LOT || t == BASE) {
                    for (int k = 0; k < 3; k++) {
                        p.setColor(r.nextBoolean() ? 0x10FFFFFF : 0x14000000);
                        float gx = fx + r.nextFloat() * T, gy = fy + r.nextFloat() * T;
                        c.drawRect(gx, gy, gx + 0.8f, gy + 0.8f, p);
                    }
                }
            }
    }

    /** A raised kerb where pavement meets road: a shadow on the road side and a lit edge on top. */
    private void drawKerbs(Canvas c, Paint p) {
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++) {
                byte t = tiles[y * w + x];
                if (t != SIDEWALK && t != PLAZA) continue;
                float fx = x * T, fy = y * T;
                if (tiles[y * w + x + 1] == ROAD) {
                    p.setColor(0x40000000);
                    c.drawRect(fx + T, fy, fx + T + 1.2f, fy + T, p);
                    p.setColor(0x30FFFFFF);
                    c.drawRect(fx + T - 1, fy, fx + T, fy + T, p);
                }
                if (tiles[y * w + x - 1] == ROAD) {
                    p.setColor(0x28000000);
                    c.drawRect(fx - 1, fy, fx, fy + T, p);
                    p.setColor(0x30FFFFFF);
                    c.drawRect(fx, fy, fx + 1, fy + T, p);
                }
                if (tiles[(y + 1) * w + x] == ROAD) {
                    p.setColor(0x40000000);
                    c.drawRect(fx, fy + T, fx + T, fy + T + 1.2f, p);
                    p.setColor(0x30FFFFFF);
                    c.drawRect(fx, fy + T - 1, fx + T, fy + T, p);
                }
                if (tiles[(y - 1) * w + x] == ROAD) {
                    p.setColor(0x28000000);
                    c.drawRect(fx, fy - 1, fx + T, fy, p);
                    p.setColor(0x30FFFFFF);
                    c.drawRect(fx, fy, fx + T, fy + 1, p);
                }
            }
    }

    /** Weathering on roofs: grime speckles, streaks, a lit parapet on the sunny edges and a dark one opposite. */
    private void weatherRoof(Canvas c, Paint p, int[] b) {
        float x0 = b[0] * T, y0 = b[1] * T, x1 = (b[0] + b[2]) * T, y1 = (b[1] + b[3]) * T;
        int kind = b[6];
        if (kind == SILO || kind == SPIRE) return;
        Random r = new Random(b[5] * 13L + 5);
        int n = (int) ((x1 - x0) * (y1 - y0) / 40);
        for (int k = 0; k < n; k++) {
            p.setColor(r.nextInt(3) == 0 ? 0x14FFFFFF : 0x16000000);
            float gx = x0 + 2 + r.nextFloat() * (x1 - x0 - 4), gy = y0 + 2 + r.nextFloat() * (y1 - y0 - 4);
            c.drawRect(gx, gy, gx + 0.9f, gy + 0.9f, p);
        }
        // A few rain streaks and stains.
        for (int k = 0; k < 1 + n / 60; k++) {
            p.setColor(0x12000000);
            float gx = x0 + 3 + r.nextFloat() * (x1 - x0 - 6), gy = y0 + 3 + r.nextFloat() * (y1 - y0 - 6);
            c.drawCircle(gx, gy, 2 + r.nextFloat() * 4, p);
        }
        p.setColor(0x38FFFFFF);
        c.drawRect(x0, y0, x1, y0 + 0.9f, p);
        c.drawRect(x0, y0, x0 + 0.9f, y1, p);
        p.setColor(0x40000000);
        c.drawRect(x0, y1 - 0.9f, x1, y1, p);
        c.drawRect(x1 - 0.9f, y0, x1, y1, p);
    }

    /** Rounds the outer corners of every block's sidewalk where two roads meet. */
    /** Rounds off the block corners where two streets meet: wide kerbs on big blocks, tight on small ones. */
    private void roundCorners(Canvas c, Paint p) {
        Path path = new Path();
        for (int[] b : blocks) {
            int bw = b[2] - b[0], bh = b[3] - b[1];
            float r = Math.min(bw, bh) >= 8 ? T * 2.2f : T * 1.1f;
            int[][] corners = {{b[0], b[1], 1, 1}, {b[2], b[1], -1, 1}, {b[0], b[3], 1, -1}, {b[2], b[3], -1, -1}};
            for (int[] k : corners) {
                // Only where roads meet on both sides of the corner.
                int ox = k[2] > 0 ? k[0] - 1 : k[0], oy = k[3] > 0 ? k[1] - 1 : k[1];
                int ix = k[2] > 0 ? k[0] : k[0] - 1, iy = k[3] > 0 ? k[1] : k[1] - 1;
                if (!paved(ox, iy) || !paved(ix, oy)) continue;
                if (ix < 0 || iy < 0 || ix >= w || iy >= h || tiles[iy * w + ix] != SIDEWALK) continue;
                float cx = k[0] * T, cy = k[1] * T;
                path.reset();
                path.moveTo(cx, cy);
                path.lineTo(cx + k[2] * r, cy);
                path.quadTo(cx, cy, cx, cy + k[3] * r);
                path.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF3A3D43);
                c.drawPath(path, p);
                // The kerb.
                path.reset();
                path.moveTo(cx + k[2] * r, cy);
                path.quadTo(cx, cy, cx, cy + k[3] * r);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.2f);
                p.setColor(0xFF7E7C77);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }

    private void drawBaseDetails(Canvas c, Paint p) {
        // Concrete slabs, then chain-link fences with posts.
        p.setStrokeWidth(1f);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                byte t = tiles[y * w + x];
                if (t != BASE && t != FENCE && !(t == CAR && carKind[y * w + x] == 2)) continue;
                p.setColor(0xFF62665A);
                c.drawLine(x * T, y * T, x * T + T, y * T, p);
                c.drawLine(x * T, y * T, x * T, y * T + T, p);
            }
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (tiles[y * w + x] != FENCE) continue;
                float cx = x * T + T / 2f, cy = y * T + T / 2f;
                boolean hor = (x > 0 && tiles[y * w + x - 1] == FENCE) || (x < w - 1 && tiles[y * w + x + 1] == FENCE);
                boolean ver = (y > 0 && tiles[(y - 1) * w + x] == FENCE) || (y < h - 1 && tiles[(y + 1) * w + x] == FENCE);
                p.setColor(0x60000000);
                p.setStrokeWidth(2.4f);
                if (hor) c.drawLine(x * T, cy + 1.5f, x * T + T, cy + 1.5f, p);
                if (ver) c.drawLine(cx + 1.5f, y * T, cx + 1.5f, y * T + T, p);
                p.setColor(0xFFB9BEC2);
                p.setStrokeWidth(1.4f);
                if (hor) c.drawLine(x * T, cy, x * T + T, cy, p);
                if (ver) c.drawLine(cx, y * T, cx, y * T + T, p);
                p.setColor(0xFF4A4E52);
                c.drawCircle(cx, cy, 1.6f, p);
            }
        for (float[] hp : helipads) {
            p.setColor(0xFF4A4E46);
            c.drawCircle(hp[0], hp[1], 22, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f);
            p.setColor(0xFFE8D24A);
            c.drawCircle(hp[0], hp[1], 18, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFEEEEEE);
            c.drawRect(hp[0] - 7, hp[1] - 9, hp[0] - 4, hp[1] + 9, p);
            c.drawRect(hp[0] + 4, hp[1] - 9, hp[0] + 7, hp[1] + 9, p);
            c.drawRect(hp[0] - 5, hp[1] - 1.5f, hp[0] + 5, hp[1] + 1.5f, p);
        }
        for (float[] t : tents) {
            p.setColor(0x50000000);
            c.drawRect(t[0] - 9, t[1] - 6, t[0] + 11, t[1] + 8, p);
            p.setColor(0xFF5E6B3E);
            c.drawRect(t[0] - 10, t[1] - 7, t[0] + 10, t[1] + 7, p);
            p.setColor(0xFF6F7D4A);
            c.drawRect(t[0] - 10, t[1] - 7, t[0] + 10, t[1] - 0.5f, p);
            p.setColor(0xFF465030);
            c.drawRect(t[0] - 10, t[1] - 0.6f, t[0] + 10, t[1] + 0.6f, p);
        }
    }

    /** Courts, sports fields, playgrounds, gardens, graves and skateparks. */
    /**
     * The biggest rectangle of the given shape (long side / short side) that fits in the box, no longer than
     * maxLen, centred, along the box's long side. Returns {x0, y0, x1, y1, 1 if it runs across}.
     */
    private static float[] fit(float x0, float y0, float x1, float y1, float ratio, float maxLen) {
        float bw = x1 - x0, bh = y1 - y0;
        boolean wide = bw >= bh;
        float lng = wide ? bw : bh, shrt = wide ? bh : bw;
        float L = Math.min(maxLen, Math.min(lng, shrt * ratio)), S = L / ratio;
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        return wide ? new float[]{cx - L / 2, cy - S / 2, cx + L / 2, cy + S / 2, 1}
                : new float[]{cx - S / 2, cy - L / 2, cx + S / 2, cy + L / 2, 0};
    }

    /** A point on a court: a along its length (0..1), b across it (0..1). */
    private static float cxAt(float[] ct, float a, float b) {
        return ct[4] > 0 ? ct[0] + (ct[2] - ct[0]) * a : ct[0] + (ct[2] - ct[0]) * b;
    }

    private static float cyAt(float[] ct, float a, float b) {
        return ct[4] > 0 ? ct[1] + (ct[3] - ct[1]) * b : ct[1] + (ct[3] - ct[1]) * a;
    }

    private static void line(Canvas c, Paint p, float[] ct, float a0, float b0, float a1, float b1) {
        c.drawLine(cxAt(ct, a0, b0), cyAt(ct, a0, b0), cxAt(ct, a1, b1), cyAt(ct, a1, b1), p);
    }

    private static void box(Canvas c, Paint p, float[] ct, float a0, float b0, float a1, float b1) {
        float xa = cxAt(ct, a0, b0), ya = cyAt(ct, a0, b0), xb = cxAt(ct, a1, b1), yb = cyAt(ct, a1, b1);
        c.drawRect(Math.min(xa, xb), Math.min(ya, yb), Math.max(xa, xb), Math.max(ya, yb), p);
    }

    private static float lenOf(float[] ct) {
        return ct[4] > 0 ? ct[2] - ct[0] : ct[3] - ct[1];
    }

    /** A basketball court: keys, free-throw circles, three-point arcs (inside the court), centre circle. */
    private static void basketball(Canvas c, Paint p, float[] ct) {
        float L = lenOf(ct);
        p.setColor(0xFFB0663A);
        c.drawRect(ct[0], ct[1], ct[2], ct[3], p);
        p.setColor(0xFF8A4A2A);
        // The painted keys.
        box(c, p, ct, 0, 0.5f - 4.9f / 30, 5.8f / 28, 0.5f + 4.9f / 30);
        box(c, p, ct, 1 - 5.8f / 28, 0.5f - 4.9f / 30, 1, 0.5f + 4.9f / 30);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.1f);
        p.setColor(0xEEFFFFFF);
        c.drawRect(ct[0], ct[1], ct[2], ct[3], p);
        line(c, p, ct, 0.5f, 0, 0.5f, 1);
        c.drawCircle(cxAt(ct, 0.5f, 0.5f), cyAt(ct, 0.5f, 0.5f), L * 1.8f / 28, p);
        box(c, p, ct, 0, 0.5f - 4.9f / 30, 5.8f / 28, 0.5f + 4.9f / 30);
        box(c, p, ct, 1 - 5.8f / 28, 0.5f - 4.9f / 30, 1, 0.5f + 4.9f / 30);
        c.drawCircle(cxAt(ct, 5.8f / 28, 0.5f), cyAt(ct, 5.8f / 28, 0.5f), L * 1.8f / 28, p);
        c.drawCircle(cxAt(ct, 1 - 5.8f / 28, 0.5f), cyAt(ct, 1 - 5.8f / 28, 0.5f), L * 1.8f / 28, p);
        // Three-point lines: arcs round each basket, kept inside the court.
        c.save();
        c.clipRect(ct[0], ct[1], ct[2], ct[3]);
        c.drawCircle(cxAt(ct, 1.575f / 28, 0.5f), cyAt(ct, 1.575f / 28, 0.5f), L * 6.75f / 28, p);
        c.drawCircle(cxAt(ct, 1 - 1.575f / 28, 0.5f), cyAt(ct, 1 - 1.575f / 28, 0.5f), L * 6.75f / 28, p);
        c.restore();
        p.setStyle(Paint.Style.FILL);
        // Hoops.
        p.setColor(0xFFE0702E);
        c.drawCircle(cxAt(ct, 1.575f / 28, 0.5f), cyAt(ct, 1.575f / 28, 0.5f), 1.4f, p);
        c.drawCircle(cxAt(ct, 1 - 1.575f / 28, 0.5f), cyAt(ct, 1 - 1.575f / 28, 0.5f), 1.4f, p);
    }

    /** A tennis court: doubles and singles sidelines, service boxes, the net. */
    private static void tennis(Canvas c, Paint p, float[] ct) {
        p.setColor(0xFF4E946A);
        c.drawRect(ct[0], ct[1], ct[2], ct[3], p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0xEEFFFFFF);
        c.drawRect(ct[0], ct[1], ct[2], ct[3], p);
        float alley = (1 - 8.23f / 10.97f) / 2, svc = 6.4f / 23.77f;
        line(c, p, ct, 0, alley, 1, alley);
        line(c, p, ct, 0, 1 - alley, 1, 1 - alley);
        line(c, p, ct, 0.5f - svc, alley, 0.5f - svc, 1 - alley);
        line(c, p, ct, 0.5f + svc, alley, 0.5f + svc, 1 - alley);
        line(c, p, ct, 0.5f - svc, 0.5f, 0.5f + svc, 0.5f);
        p.setStrokeWidth(1.8f);
        p.setColor(0xFF2A2A2A);
        line(c, p, ct, 0.5f, -0.04f, 0.5f, 1.04f);
        p.setStyle(Paint.Style.FILL);
    }

    /** A football pitch: halfway line, centre circle, penalty and goal areas, penalty arcs, goals. */
    private static void football(Canvas c, Paint p, float[] ct) {
        float L = lenOf(ct);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.3f);
        p.setColor(0xEEFFFFFF);
        c.drawRect(ct[0], ct[1], ct[2], ct[3], p);
        line(c, p, ct, 0.5f, 0, 0.5f, 1);
        c.drawCircle(cxAt(ct, 0.5f, 0.5f), cyAt(ct, 0.5f, 0.5f), L * 9.15f / 105, p);
        float pb = 16.5f / 105, pw = 40.3f / 68 / 2, gb = 5.5f / 105, gw = 18.3f / 68 / 2;
        box(c, p, ct, 0, 0.5f - pw, pb, 0.5f + pw);
        box(c, p, ct, 1 - pb, 0.5f - pw, 1, 0.5f + pw);
        box(c, p, ct, 0, 0.5f - gw, gb, 0.5f + gw);
        box(c, p, ct, 1 - gb, 0.5f - gw, 1, 0.5f + gw);
        // The "D" on each penalty area: the part of the circle round the spot outside the box.
        for (int end = 0; end < 2; end++) {
            float spot = end == 0 ? 11f / 105 : 1 - 11f / 105;
            c.save();
            float ea = end == 0 ? pb : 1 - pb;
            float xa = cxAt(ct, ea, 0), ya = cyAt(ct, ea, 0), xb = cxAt(ct, end == 0 ? 0.5f : 0.5f, 1), yb = cyAt(ct, 0.5f, 1);
            c.clipRect(Math.min(xa, xb), Math.min(ya, yb), Math.max(xa, xb), Math.max(ya, yb));
            c.drawCircle(cxAt(ct, spot, 0.5f), cyAt(ct, spot, 0.5f), L * 9.15f / 105, p);
            c.restore();
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xEEFFFFFF);
        c.drawCircle(cxAt(ct, 11f / 105, 0.5f), cyAt(ct, 11f / 105, 0.5f), 1.1f, p);
        c.drawCircle(cxAt(ct, 1 - 11f / 105, 0.5f), cyAt(ct, 1 - 11f / 105, 0.5f), 1.1f, p);
        c.drawCircle(cxAt(ct, 0.5f, 0.5f), cyAt(ct, 0.5f, 0.5f), 1.1f, p);
        // Goals.
        p.setColor(0xFFF2F2F2);
        box(c, p, ct, -0.012f, 0.5f - 3.66f / 68, 0, 0.5f + 3.66f / 68);
        box(c, p, ct, 1, 0.5f - 3.66f / 68, 1.012f, 0.5f + 3.66f / 68);
    }

    private void drawDecor(Canvas c, Paint p, float[] d) {
        int kind = (int) d[0], variant = (int) d[5];
        float x0 = d[1], y0 = d[2], x1 = d[3], y1 = d[4], cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        float dw = x1 - x0, dh = y1 - y0;
        boolean wide = dw >= dh;
        p.setStyle(Paint.Style.FILL);
        switch (kind) {
            case D_COURT: {
                // The surface round the court, then a court of the right shape in the middle of it.
                p.setColor(variant == 0 ? 0xFF6E6E70 : 0xFF3F6A50);
                c.drawRect(x0, y0, x1, y1, p);
                float[] ct = fit(x0 + 3, y0 + 3, x1 - 3, y1 - 3, variant == 0 ? 28f / 15f : 23.77f / 10.97f, variant == 0 ? 160 : 140);
                if (variant == 0) basketball(c, p, ct);
                else tennis(c, p, ct);
                break;
            }
            case D_FIELD: {
                int stripes = 8;
                for (int i = 0; i < stripes; i++) {
                    p.setColor(i % 2 == 0 ? 0xFF4F8A3A : 0xFF5A9644);
                    if (wide) c.drawRect(x0 + dw * i / stripes, y0, x0 + dw * (i + 1) / stripes, y1, p);
                    else c.drawRect(x0, y0 + dh * i / stripes, x1, y0 + dh * (i + 1) / stripes, p);
                }
                float[] ct = fit(x0 + 4, y0 + 4, x1 - 4, y1 - 4, 105f / 68f, 560);
                football(c, p, ct);
                break;
            }
            case D_PLAYGROUND: {
                p.setColor(0xFFD9C38E);
                c.drawRoundRect(new RectF(x0, y0, x1, y1), 6, 6, p);
                p.setColor(0x40000000);
                c.drawRect(x0 + dw * 0.15f + 1.5f, y0 + dh * 0.2f + 1.5f, x0 + dw * 0.35f + 1.5f, y0 + dh * 0.7f + 1.5f, p);
                p.setColor(0xFFE0483A);
                c.drawRect(x0 + dw * 0.15f, y0 + dh * 0.2f, x0 + dw * 0.35f, y0 + dh * 0.7f, p);
                p.setColor(0xFFE8C53A);
                c.drawRect(x0 + dw * 0.18f, y0 + dh * 0.55f, x0 + dw * 0.32f, y0 + dh * 0.85f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.5f);
                p.setColor(0xFF3A6FB0);
                c.drawLine(x0 + dw * 0.5f, y0 + dh * 0.25f, x0 + dw * 0.85f, y0 + dh * 0.25f, p);
                p.setColor(0xFF555555);
                c.drawLine(x0 + dw * 0.6f, y0 + dh * 0.25f, x0 + dw * 0.6f, y0 + dh * 0.45f, p);
                c.drawLine(x0 + dw * 0.75f, y0 + dh * 0.25f, x0 + dw * 0.75f, y0 + dh * 0.45f, p);
                p.setStyle(Paint.Style.FILL);
                int[] cols = {0xFF4FB06A, 0xFFE0483A, 0xFF3A6FB0, 0xFFE8C53A};
                for (int i = 0; i < 4; i++) {
                    p.setColor(cols[i]);
                    c.drawCircle(x0 + dw * (0.55f + 0.1f * i), y0 + dh * 0.72f, 2.2f, p);
                }
                break;
            }
            case D_GARDEN: {
                int rows = (int) ((wide ? dh : dw) / 6);
                for (int i = 0; i < rows; i++) {
                    float a = (wide ? y0 : x0) + i * 6;
                    p.setColor(0xFF6B4A30);
                    if (wide) c.drawRect(x0, a, x1, a + 4, p);
                    else c.drawRect(a, y0, a + 4, y1, p);
                    int crop = (i + variant) % 3;
                    p.setColor(crop == 0 ? 0xFF6FB04A : crop == 1 ? 0xFF3E8A34 : 0xFFB0A03A);
                    float len = wide ? dw : dh;
                    for (float t = 2; t < len - 2; t += 4) {
                        if (wide) c.drawCircle(x0 + t, a + 2, 1.5f, p);
                        else c.drawCircle(a + 2, y0 + t, 1.5f, p);
                    }
                }
                break;
            }
            case D_GRAVE: {
                p.setColor(0x50000000);
                c.drawRect(x0 + 1, y0 + 1.5f, x1 + 1, y1 + 1.5f, p);
                p.setColor(variant == 0 ? 0xFFA8A49C : variant == 1 ? 0xFF8E8A84 : 0xFFBDB8AE);
                c.drawRect(x0, y0, x1, y1, p);
                if (variant == 2) {
                    p.setColor(0xFF7A766E);
                    c.drawRect(cx - 0.5f, y0 + 0.5f, cx + 0.5f, y1 - 1, p);
                    c.drawRect(x0 + 0.5f, y0 + 2, x1 - 0.5f, y0 + 3, p);
                }
                break;
            }
            case D_SKATE: {
                p.setColor(0xFFBDB6A4);
                c.drawRect(x0, y0, x1, y1, p);
                RectF bowl = new RectF(x0 + dw * 0.1f, y0 + dh * 0.15f, x0 + dw * 0.5f, y0 + dh * 0.75f);
                p.setColor(0xFF8E887A);
                c.drawRoundRect(bowl, 12, 12, p);
                bowl.inset(4, 4);
                p.setColor(0xFF77715F);
                c.drawRoundRect(bowl, 10, 10, p);
                p.setColor(0xFF9A9384);
                c.drawRect(x0 + dw * 0.6f, y0 + dh * 0.2f, x0 + dw * 0.9f, y0 + dh * 0.4f, p);
                p.setColor(0xFFA9A293);
                c.drawRect(x0 + dw * 0.6f, y0 + dh * 0.2f, x0 + dw * 0.9f, y0 + dh * 0.26f, p);
                p.setStrokeWidth(1.4f);
                p.setColor(0xFF555555);
                c.drawLine(x0 + dw * 0.6f, y0 + dh * 0.65f, x0 + dw * 0.9f, y0 + dh * 0.65f, p);
                break;
            }
            case D_ALLEY: {
                // A narrow back alley: worn tarmac, bins and a dumpster or two.
                Random r = new Random((long) (x0 * 31 + y0 * 17));
                p.setColor(0xFF5A5854);
                c.drawRect(x0, y0, x1, y1, p);
                float len = wide ? dw : dh;
                for (float t = 6 + r.nextFloat() * 20; t < len - 8; t += 22 + r.nextFloat() * 30) {
                    float px = wide ? x0 + t : x0 + 1, py = wide ? y0 + 1 : y0 + t;
                    float sx = wide ? 9 : dw - 2, sy = wide ? dh - 2 : 9;
                    if (r.nextBoolean()) {
                        p.setColor(0x50000000);
                        c.drawRect(px + 1, py + 1.5f, px + sx + 1, py + sy + 1.5f, p);
                        p.setColor(r.nextBoolean() ? 0xFF2F6B45 : 0xFF3A5A8A);
                        c.drawRect(px, py, px + sx, py + sy, p);
                        p.setColor(0x33FFFFFF);
                        c.drawRect(px, py, px + sx, py + 1.2f, p);
                    } else {
                        p.setColor(0xFF22262A);
                        c.drawCircle(px + (wide ? 3 : sx / 2), py + (wide ? sy / 2 : 3), 2.3f, p);
                        p.setColor(0xFF1A1A1A);
                        c.drawCircle(px + (wide ? 7 : sx / 2 + 2), py + (wide ? sy / 2 + 1 : 7), 1.8f, p);
                    }
                }
                break;
            }
            case D_SITE: {
                // Dug-out foundations, rebar, stacked materials and a tower crane.
                Random r = new Random((long) (x0 * 13 + y0 * 7));
                p.setColor(0xFF8A6E4E);
                c.drawRect(x0, y0, x1, y1, p);
                for (int k = 0; k < 40; k++) {
                    p.setColor(r.nextBoolean() ? 0xFF7A5E40 : 0xFF9A7E5A);
                    c.drawCircle(x0 + r.nextFloat() * dw, y0 + r.nextFloat() * dh, 1 + r.nextFloat() * 2, p);
                }
                float fx0 = x0 + dw * 0.1f, fy0 = y0 + dh * 0.12f, fx1 = x0 + dw * 0.6f, fy1 = y0 + dh * 0.55f;
                p.setColor(0xFF9E9A92);
                c.drawRect(fx0, fy0, fx1, fy1, p);
                p.setColor(0xFF6E6A62);
                c.drawRect(fx0 + 2, fy0 + 2, fx1 - 2, fy1 - 2, p);
                p.setColor(0xFFB06A3A);
                p.setStrokeWidth(0.5f);
                for (float gx = fx0 + 4; gx < fx1 - 2; gx += 5) c.drawLine(gx, fy0 + 2, gx, fy1 - 2, p);
                for (float gy = fy0 + 4; gy < fy1 - 2; gy += 5) c.drawLine(fx0 + 2, gy, fx1 - 2, gy, p);
                // Stacks of planks, pipes and bricks.
                for (int k = 0; k < 3; k++) {
                    float mx = x0 + dw * (0.12f + k * 0.18f), my = y0 + dh * 0.75f;
                    p.setColor(k == 0 ? 0xFFC8A060 : k == 1 ? 0xFF8A8E94 : 0xFFA8543A);
                    c.drawRect(mx, my, mx + 10, my + 6, p);
                    p.setColor(0x33000000);
                    for (float l = my + 1.5f; l < my + 6; l += 1.5f) c.drawLine(mx, l, mx + 10, l, p);
                }
                // Portable cabin.
                p.setColor(0xFFE8C547);
                c.drawRect(x1 - 18, y0 + 3, x1 - 3, y0 + 10, p);
                p.setColor(0xFF4A6E8A);
                c.drawRect(x1 - 15, y0 + 5, x1 - 11, y0 + 8, p);
                // The crane: a mast with a long jib, and its shadow on the ground.
                // The jib swings over the site (never out over the street or the next block).
                float mx = fx1 - 4, my = fy1 - 4, ang = (float) Math.PI + 0.25f + (variant % 4) * 0.35f;
                float jx = (float) Math.cos(ang), jy = (float) Math.sin(ang);
                float jl = 0.92f * Math.min((mx - x0) / Math.max(0.05f, -jx), (my - y0) / Math.max(0.05f, -jy));
                float cj = Math.min(jl * 0.25f, Math.min((x1 - mx) / Math.max(0.05f, -jx), (y1 - my) / Math.max(0.05f, -jy)));
                p.setStrokeWidth(3f);
                p.setColor(0x40000000);
                c.drawLine(mx + 10 - jx * cj, my + 14 - jy * cj, mx + 10 + jx * jl, my + 14 + jy * jl, p);
                p.setColor(0xFFE8B830);
                c.drawRect(mx - 3, my - 3, mx + 3, my + 3, p);
                p.setStrokeWidth(2.2f);
                c.drawLine(mx - jx * cj, my - jy * cj, mx + jx * jl, my + jy * jl, p);
                p.setColor(0xFF6A6A6A);
                c.drawRect(mx - jx * cj - 3, my - jy * cj - 3, mx - jx * cj + 3, my - jy * cj + 3, p);
                p.setStrokeWidth(1f);
                break;
            }
            case D_POWER: {
                // Rows of transformers and a pylon.
                p.setColor(0xFF9A968C);
                c.drawRect(x0, y0, x1, y1, p);
                p.setColor(0xFF8A867C);
                for (float gy = y0 + 2; gy < y1; gy += 4) c.drawRect(x0, gy, x1, gy + 0.6f, p);
                for (float ty = y0 + 5; ty < y1 - 8; ty += 14)
                    for (float tx = x0 + 5; tx < x1 - 8; tx += 14) {
                        p.setColor(0x50000000);
                        c.drawRect(tx + 1.5f, ty + 2, tx + 9.5f, ty + 10, p);
                        p.setColor(0xFF5E6A5A);
                        c.drawRect(tx, ty, tx + 8, ty + 8, p);
                        p.setColor(0xFF7E8A78);
                        for (float fx = tx + 1; fx < tx + 8; fx += 2) c.drawRect(fx, ty + 1, fx + 0.8f, ty + 7, p);
                        p.setColor(0xFFD8D8D0);
                        c.drawCircle(tx + 2, ty + 1, 0.9f, p);
                        c.drawCircle(tx + 6, ty + 1, 0.9f, p);
                    }
                p.setColor(0xFF3A3A3A);
                p.setStrokeWidth(0.5f);
                c.drawLine(x0, y0 + 3, x1, y0 + 3, p);
                c.drawLine(x0, y1 - 3, x1, y1 - 3, p);
                p.setStrokeWidth(1f);
                break;
            }
            case D_HELIPAD: {
                p.setColor(0xFF3A3C40);
                c.drawCircle(cx, cy, Math.min(dw, dh) / 2, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.5f);
                p.setColor(0xFFE8E8E8);
                c.drawCircle(cx, cy, Math.min(dw, dh) / 2 - 2.5f, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFD83A3A);
                float hs = Math.min(dw, dh) * 0.22f;
                c.drawRect(cx - hs, cy - hs, cx - hs * 0.55f, cy + hs, p);
                c.drawRect(cx + hs * 0.55f, cy - hs, cx + hs, cy + hs, p);
                c.drawRect(cx - hs, cy - hs * 0.2f, cx + hs, cy + hs * 0.2f, p);
                break;
            }
            case D_BAY: {
                // Yellow hatched ambulance bay.
                p.setColor(0xFFE8C547);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1f);
                c.drawRect(x0 + 0.5f, y0 + 0.5f, x1 - 0.5f, y1 - 0.5f, p);
                p.setStyle(Paint.Style.FILL);
                float len = wide ? dw : dh;
                for (float t = 3; t < len - 2; t += 5) {
                    if (wide) c.drawLine(x0 + t, y0 + 1, x0 + t + 3, y1 - 1, p);
                    else c.drawLine(x0 + 1, y0 + t, x1 - 1, y0 + t + 3, p);
                }
                break;
            }
            case D_BANDSTAND: {
                // An eight-sided bandstand with a green roof.
                float rr = Math.min(dw, dh) * 0.42f;
                Path oct = new Path();
                for (int k = 0; k < 8; k++) {
                    double a = Math.PI / 8 + k * Math.PI / 4;
                    float px = cx + (float) Math.cos(a) * rr, py = cy + (float) Math.sin(a) * rr;
                    if (k == 0) oct.moveTo(px, py);
                    else oct.lineTo(px, py);
                }
                oct.close();
                c.save();
                c.translate(3, 4);
                p.setColor(0x50000000);
                c.drawPath(oct, p);
                c.restore();
                p.setColor(0xFFE8E4DA);
                c.drawPath(oct, p);
                c.save();
                c.translate(cx, cy);
                c.scale(0.82f, 0.82f);
                c.translate(-cx, -cy);
                p.setColor(0xFF3F7A5A);
                c.drawPath(oct, p);
                c.restore();
                p.setColor(0xFF5A9A74);
                c.drawCircle(cx, cy, rr * 0.3f, p);
                p.setColor(0xFFE8C547);
                c.drawCircle(cx, cy, 1.5f, p);
                break;
            }
            case D_FLOWERS: {
                Random r = new Random((long) (x0 * 7 + y0 * 3));
                p.setColor(0xFF6B4A30);
                c.drawRoundRect(new RectF(x0, y0, x1, y1), 4, 4, p);
                int[][] sets = {{0xFFE84A5A, 0xFFF2E86B}, {0xFFB08AE8, 0xFFF2F2F2}, {0xFFE87BB0, 0xFFE8A040}};
                for (int k = 0; k < (int) (dw * dh / 5); k++) {
                    p.setColor(r.nextInt(3) == 0 ? 0xFF3E8A34 : sets[variant % 3][r.nextInt(2)]);
                    c.drawCircle(x0 + 2 + r.nextFloat() * (dw - 4), y0 + 2 + r.nextFloat() * (dh - 4), 1.1f, p);
                }
                break;
            }
            case D_CROPS: {
                // Ploughed rows: wheat, cabbages or bare earth.
                int base = variant == 0 ? 0xFFC8A850 : variant == 1 ? 0xFF4E8A38 : 0xFF7A5A3A;
                int row = variant == 0 ? 0xFFB09040 : variant == 1 ? 0xFF3A6E2A : 0xFF6A4A2E;
                p.setColor(base);
                c.drawRect(x0, y0, x1, y1, p);
                p.setColor(row);
                boolean rowsAcross = variant == 1 ? !wide : wide;
                if (rowsAcross) for (float gy = y0 + 1; gy < y1; gy += 3.5f) c.drawRect(x0, gy, x1, gy + 1.2f, p);
                else for (float gx = x0 + 1; gx < x1; gx += 3.5f) c.drawRect(gx, y0, gx + 1.2f, y1, p);
                if (variant == 1) {
                    p.setColor(0xFF6FB04A);
                    for (float gy = y0 + 2; gy < y1 - 1; gy += 3.5f)
                        for (float gx = x0 + 2; gx < x1 - 1; gx += 3.5f) c.drawCircle(gx, gy, 1.1f, p);
                }
                break;
            }
            case D_ROUNDABOUT: {
                // Paint the corners of the island back to road, then the round island with a kerb and flowers.
                float ir = variant * T / 2f + (variant == 1 ? 2 : 4);
                float ix0 = cx - variant * T / 2f, iy0 = cy - variant * T / 2f;
                p.setColor(0xFF3A3D43);
                c.drawRect(ix0, iy0, ix0 + variant * T, iy0 + variant * T, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1f);
                p.setColor(0xB0E8E8E8);
                c.drawCircle(cx, cy, ir + 5, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFB8B4AC);
                c.drawCircle(cx, cy, ir, p);
                p.setColor(0xFF4C7837);
                c.drawCircle(cx, cy, ir - 1.5f, p);
                p.setColor(0xFF578A3F);
                c.drawCircle(cx, cy, ir * 0.6f, p);
                int[] fl = {0xFFE84A5A, 0xFFF2E86B, 0xFFF2F2F2};
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    p.setColor(fl[k % 3]);
                    c.drawCircle(cx + (float) Math.cos(a) * ir * 0.72f, cy + (float) Math.sin(a) * ir * 0.72f, 1.2f, p);
                }
                break;
            }
            case D_FLOODLIGHT: {
                float[][] corners = {{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}};
                for (float[] k : corners) {
                    p.setColor(0x50000000);
                    c.drawRect(k[0] - 2, k[1] - 2, k[0] + 5, k[1] + 5, p);
                    p.setColor(0xFF4A4C50);
                    c.drawRect(k[0] - 3.5f, k[1] - 3.5f, k[0] + 3.5f, k[1] + 3.5f, p);
                    p.setColor(0xFFF2F0DA);
                    c.drawRect(k[0] - 2.5f, k[1] - 2.5f, k[0] + 2.5f, k[1] - 0.5f, p);
                }
                break;
            }
            default:
                break;
        }
    }

    private boolean isPlazaTree(int x, int y) {
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && tiles[ny * w + nx] == PLAZA) return true;
            }
        return false;
    }

    private boolean isLotCar(int x, int y) {
        return (x > 0 && tiles[y * w + x - 1] == LOT) || (x < w - 1 && tiles[y * w + x + 1] == LOT)
                || (y > 0 && tiles[(y - 1) * w + x] == LOT) || (y < h - 1 && tiles[(y + 1) * w + x] == LOT);
    }

    /** Gravel, sleepers and rails, carried across the level crossings with barrier markings. */
    private void drawRail(Canvas c, Paint p) {
        if (railY0 < 0) return;
        float y0 = railY0 * T, y1 = (railY0 + railRows) * T, cy = (y0 + y1) / 2;
        for (int x = regionOnly ? rx0 : 0; x < (regionOnly ? rx1 : w); x++) {
            prnd.setSeed(tileSeed(x, 0, 4));
            float fx = x * T;
            boolean crossing = tiles[railY0 * w + x] == ROAD;
            if (!crossing) {
                p.setColor(0xFF6E665C);
                c.drawRect(fx, y0 + 6, fx + T, y1 - 6, p);
                for (int k = 0; k < 10; k++) {
                    p.setColor(prnd.nextBoolean() ? 0xFF7E766A : 0xFF5E574E);
                    c.drawCircle(fx + prnd.nextFloat() * T, y0 + 6 + prnd.nextFloat() * (y1 - y0 - 12), 0.8f, p);
                }
                p.setColor(0xFF4A3A2C);
                for (float sx = fx + 1; sx < fx + T; sx += 4) c.drawRect(sx, cy - 8, sx + 2, cy + 8, p);
            } else {
                // Level crossing: yellow boxes and stop lines either side.
                p.setColor(0x55E8C547);
                c.drawRect(fx, y0 + 4, fx + T, y0 + 6, p);
                c.drawRect(fx, y1 - 6, fx + T, y1 - 4, p);
            }
        }
        p.setColor(0xFFB8BCC0);
        c.drawRect(0, cy - 6, w * T, cy - 4.8f, p);
        c.drawRect(0, cy + 4.8f, w * T, cy + 6, p);
    }

    /**
     * Makes sure the town isn't cut in two by the railway: streets that run up to the line from one side are
     * carried across it (cutting through the block opposite) until there's a level crossing every so often.
     */
    private void ensureCrossings(int bx0, int bx1) {
        List<Integer> xs = new ArrayList<Integer>();
        for (int x = 0; x < w; x++)
            if (tiles[railY0 * w + x] == ROAD && (x == 0 || tiles[railY0 * w + x - 1] != ROAD)) xs.add(x);
        int want = Math.max(2, (bx1 - bx0) / 26);
        List<Street> cands = new ArrayList<Street>();
        for (Street st : streets) if (st.vertical && (st.y1 == railY0 || st.y0 == railY0 + railRows)) cands.add(st);
        java.util.Collections.shuffle(cands, rnd);
        for (Street st : cands) {
            if (xs.size() >= want) break;
            boolean close = false;
            for (int x : xs) if (Math.abs(x - st.x0) < 14) close = true;
            if (close) continue;
            boolean fromAbove = st.y1 == railY0;
            int dy = fromAbove ? 1 : -1, y = fromAbove ? railY0 + railRows : railY0 - 1;
            // Carry on across the block beyond until meeting a road.
            while (y > 0 && y < h - 1) {
                boolean road = false;
                for (int x = st.x0; x < st.x1; x++) if (tiles[y * w + x] == ROAD) road = true;
                if (road) break;
                y += dy;
            }
            if (y <= 0 || y >= h - 1) continue;
            int lo = fromAbove ? railY0 : y, hi = fromAbove ? y + 1 : railY0 + railRows;
            boolean estate = false;
            for (int[] b : blocks)
                if (b.length > 4 && b[4] == 3 && b[2] > st.x0 && b[0] < st.x1 && b[3] > lo && b[1] < hi) estate = true;
            if (estate) continue;
            carve(new Street(st.x0, lo, st.x1, hi, true, false));
            for (int ry = railY0; ry < railY0 + railRows; ry++)
                for (int x = st.x0; x < st.x1; x++) roadDir[ry * w + x] = 1;
            // The block it cut through becomes two.
            for (int k = blocks.size() - 1; k >= 0; k--) {
                int[] b = blocks.get(k);
                if (b[2] <= st.x0 || b[0] >= st.x1 || b[3] <= lo || b[1] >= hi) continue;
                blocks.remove(k);
                if (st.x0 - b[0] >= 3) blocks.add(new int[]{b[0], b[1], st.x0, b[3]});
                if (b[2] - st.x1 >= 3) blocks.add(new int[]{st.x1, b[1], b[2], b[3]});
            }
            xs.add(st.x0);
        }
    }

    /** Benches, bins, fire hydrants, bus stops and traffic lights along the pavements. */
    private void drawStreetFurniture(Canvas c, Paint p) {
        for (int y = Math.max(1, regionOnly ? ry0 : 0); y < Math.min(h - 1, regionOnly ? ry1 : h); y++)
            for (int x = Math.max(1, regionOnly ? rx0 : 0); x < Math.min(w - 1, regionOnly ? rx1 : w); x++) {
                prnd.setSeed(tileSeed(x, y, 5));
                if (tiles[y * w + x] != SIDEWALK) continue;
                // Which side faces the road?
                int dx = 0, dy = 0;
                if (paved(x - 1, y)) dx = -1;
                else if (paved(x + 1, y)) dx = 1;
                else if (paved(x, y - 1)) dy = -1;
                else if (paved(x, y + 1)) dy = 1;
                if (dx == 0 && dy == 0) continue;
                boolean corner = (paved(x - 1, y) || paved(x + 1, y)) && (paved(x, y - 1) || paved(x, y + 1));
                float cx = x * T + T / 2f + dx * 5, cy = y * T + T / 2f + dy * 5;
                float roll = prnd.nextFloat();
                if (corner) {
                    if (roll < 0.35f && onMainRoad(x - 2, y - 2, x + 2, y + 2)) {
                        // Traffic light.
                        p.setColor(0xFF2A2C30);
                        c.drawRect(cx - 1.5f, cy - 3, cx + 1.5f, cy + 3, p);
                        p.setColor(0xFFE0302A);
                        c.drawCircle(cx, cy - 1.8f, 0.8f, p);
                        p.setColor(0xFF3AD06A);
                        c.drawCircle(cx, cy + 1.8f, 0.8f, p);
                    }
                    continue;
                }
                if (roll < 0.035f) {
                    // Bench, parallel to the kerb.
                    p.setColor(0xFF7A5634);
                    if (dx != 0) c.drawRect(cx - 1.2f, cy - 4, cx + 1.2f, cy + 4, p);
                    else c.drawRect(cx - 4, cy - 1.2f, cx + 4, cy + 1.2f, p);
                } else if (roll < 0.06f) {
                    // Litter bin.
                    p.setColor(0xFF3C4A3C);
                    c.drawCircle(cx, cy, 1.6f, p);
                    p.setColor(0xFF232A23);
                    c.drawCircle(cx, cy, 0.9f, p);
                } else if (roll < 0.075f) {
                    // Fire hydrant.
                    p.setColor(0xFFD8302A);
                    c.drawCircle(cx, cy, 1.4f, p);
                    p.setColor(0xFFF0C040);
                    c.drawCircle(cx, cy, 0.6f, p);
                } else if (roll < 0.085f && onMainRoad(x - 2, y - 2, x + 2, y + 2)) {
                    // Bus stop shelter.
                    p.setColor(0xAA9CC3D9);
                    if (dx != 0) c.drawRect(cx - 2, cy - 6, cx + 2, cy + 6, p);
                    else c.drawRect(cx - 6, cy - 2, cx + 6, cy + 2, p);
                    p.setColor(0xFF3A5A8A);
                    c.drawCircle(cx + dx * 3 + (dy != 0 ? 7 : 0), cy + dy * 3 + (dx != 0 ? 7 : 0), 1.2f, p);
                }
            }
    }

    /**
     * Turn lanes on the avenues: just before each junction, the lane nearest the middle is for turning across
     * the traffic (left, or right where they drive on the left) and the kerb lane is for going straight on or
     * turning off. The arrows are painted on whichever side of the road the country drives on.
     */
    private void turnLanes(Canvas c, Paint p, Street st, int n) {
        float drive = country.leftHand ? -1 : 1;
        float mid = st.vertical ? st.x0 + st.width / 2f : st.y0 + st.width / 2f;
        p.setColor(0xE0ECECEC);
        p.setStrokeWidth(1.4f);
        for (int k = 2; k < n - 2; k++) {
            int cx = st.vertical ? st.x0 + st.width / 2 : st.x0 + k, cy = st.vertical ? st.y0 + k : st.y0 + st.width / 2;
            if (!paved(cx, cy) || junctionAt(cx, cy) || crosswalk(cx, cy)) continue;
            for (int s = -1; s <= 1; s += 2) {
                // Heading towards a crossing and junction just ahead?
                int ax = st.vertical ? cx : cx + s, ay = st.vertical ? cy + s : cy;
                int bx = st.vertical ? cx : cx + 2 * s, by = st.vertical ? cy + 2 * s : cy;
                if (!crosswalk(ax, ay) || !junctionAt(bx, by)) continue;
                float hx = st.vertical ? 0 : s, hy = st.vertical ? s : 0;
                // The right-hand side of someone driving that way.
                float rx = -hy, ry = hx;
                float along = (st.vertical ? cy : cx) + 0.5f;
                for (int lane = 0; lane < 2; lane++) {
                    float off = (lane == 0 ? 2.0f : 1.0f) * drive;
                    float lx = st.vertical ? mid + rx * off : along, ly = st.vertical ? along : mid + ry * off;
                    float px = lx * T, py = ly * T;
                    if (lane == 0) {
                        arrow(c, p, px, py, hx, hy, 0);
                        arrow(c, p, px, py, hx, hy, drive);
                    } else {
                        arrow(c, p, px, py, hx, hy, -drive);
                    }
                }
            }
        }
    }

    /**
     * Where each street meets a junction: a stop line across the lanes coming in, with STOP (ALTO in Mexico)
     * painted before stop junctions and a dashed give-way line at roundabouts.
     */
    private void stopLines(Canvas c, Paint p, Street st, int n) {
        float drive = country.leftHand ? -1 : 1;
        for (int k = 1; k < n - 1; k++) {
            int cx = st.vertical ? st.x0 + st.width / 2 : st.x0 + k, cy = st.vertical ? st.y0 + k : st.y0 + st.width / 2;
            if (!paved(cx, cy) || junctionAt(cx, cy) || crosswalk(cx, cy)) continue;
            for (int s = -1; s <= 1; s += 2) {
                int ax = st.vertical ? cx : cx + s, ay = st.vertical ? cy + s : cy;
                int bx = st.vertical ? cx : cx + 2 * s, by = st.vertical ? cy + 2 * s : cy;
                if (!crosswalk(ax, ay) || !junctionAt(bx, by)) continue;
                int id = junctionId[by * w + bx];
                int type = junctions.get(id)[4];
                if (type == J_BEND) continue;
                float hx = st.vertical ? 0 : s, hy = st.vertical ? s : 0, rx = -hy, ry = hx;
                // The edge of this tile nearest the junction, and the half of the road coming in.
                float ex = (cx + 0.5f) * T + hx * T / 2, ey = (cy + 0.5f) * T + hy * T / 2;
                float mid = st.width * T / 2f;
                float sx = st.vertical ? st.x0 * T + mid : ex, sy = st.vertical ? ey : st.y0 * T + mid;
                float ox = rx * drive, oy = ry * drive;
                p.setColor(0xE8EEEEEE);
                if (type == J_ROUNDABOUT) {
                    p.setStrokeWidth(1.2f);
                    for (float q = 1; q < mid - 1; q += 3)
                        c.drawLine(sx + ox * q, sy + oy * q, sx + ox * (q + 1.6f), sy + oy * (q + 1.6f), p);
                    continue;
                }
                p.setStrokeWidth(1.8f);
                c.drawLine(sx + ox * 0.8f, sy + oy * 0.8f, sx + ox * (mid - 1), sy + oy * (mid - 1), p);
                if (type == J_STOP && st.width == 3) {
                    String word = country.id == Country.MEXICO ? "ALTO" : "STOP";
                    float lx = sx + ox * mid * 0.5f - hx * 9, ly = sy + oy * mid * 0.5f - hy * 9;
                    c.save();
                    c.translate(lx, ly);
                    // Read by the driver coming in.
                    c.rotate((float) Math.toDegrees(Math.atan2(hy, hx)) + 90);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(5.5f);
                    p.setFakeBoldText(true);
                    c.drawText(word, 0, 2, p);
                    p.setFakeBoldText(false);
                    c.restore();
                }
            }
        }
    }

    /** A painted arrow at (x, y) pointing along (hx, hy); turn 0 is straight on, +1 bends right, -1 left. */
    private static void arrow(Canvas c, Paint p, float x, float y, float hx, float hy, float turn) {
        float rx = -hy, ry = hx;
        if (turn == 0) {
            c.drawLine(x - hx * 5, y - hy * 5, x + hx * 3, y + hy * 3, p);
            c.drawLine(x + hx * 5, y + hy * 5, x + hx * 2.5f - rx * 1.8f, y + hy * 2.5f - ry * 1.8f, p);
            c.drawLine(x + hx * 5, y + hy * 5, x + hx * 2.5f + rx * 1.8f, y + hy * 2.5f + ry * 1.8f, p);
            c.drawLine(x + hx * 5, y + hy * 5, x + hx * 3, y + hy * 3, p);
            return;
        }
        float ex = x + hx * 1 + rx * turn * 3.5f, ey = y + hy * 1 + ry * turn * 3.5f;
        c.drawLine(x - hx * 5, y - hy * 5, x + hx * 1, y + hy * 1, p);
        c.drawLine(x + hx * 1, y + hy * 1, ex, ey, p);
        float tx = rx * turn, ty = ry * turn;
        c.drawLine(ex + tx * 1.5f, ey + ty * 1.5f, ex - hx * 1.8f, ey - hy * 1.8f, p);
        c.drawLine(ex + tx * 1.5f, ey + ty * 1.5f, ex + hx * 1.8f, ey + hy * 1.8f, p);
    }

    /** A rectangle on the highway, in tiles along it (k) and across it from its first row (a). */
    private void hwyRect(Canvas c, Paint p, float k0, float k1, float a0, float a1) {
        if (hwyAxis == 0) c.drawRect(k0 * T, (hwyAt + a0) * T, k1 * T, (hwyAt + a1) * T, p);
        else c.drawRect((hwyAt + a0) * T, k0 * T, (hwyAt + a1) * T, k1 * T, p);
    }

    /**
     * The highway: smoother, darker asphalt, white edge lines, dashed lane lines, a concrete barrier down the
     * middle, stop lines at the lights, green signs before each junction and the route shield at each end.
     */
    private void drawHighway(Canvas c, Paint p) {
        if (hwyAxis < 0) return;
        int len = hwyAxis == 0 ? w : h;
        for (int k = 0; k < len; k++) {
            int x = hwyAxis == 0 ? k : hwyAt, y = hwyAxis == 0 ? hwyAt : k;
            int cx = hwyAxis == 0 ? k : hwyAt + 1, cy = hwyAxis == 0 ? hwyAt + 1 : k;
            boolean junc = junctionAt(cx, cy) || junctionAt(hwyAxis == 0 ? k : hwyAt + 5, hwyAxis == 0 ? hwyAt + 5 : k);
            boolean rail = tiles[(hwyAxis == 0 ? hwyAt : y) * w + (hwyAxis == 0 ? x : hwyAt)] == RAIL || (railY0 >= 0 && hwyAxis == 1 && k >= railY0 && k < railY0 + railRows);
            p.setColor(0xFF303236);
            hwyRect(c, p, k, k + 1, 0, 3);
            hwyRect(c, p, k, k + 1, 4, 7);
            // Gravel shoulders.
            p.setColor(0xFF6A665E);
            hwyRect(c, p, k, k + 1, -0.35f, 0);
            hwyRect(c, p, k, k + 1, 7, 7.35f);
            if (rail) continue;
            if (!junc) {
                p.setColor(0xF0ECECEC);
                hwyRect(c, p, k, k + 1, 0.12f, 0.24f);
                hwyRect(c, p, k, k + 1, 6.76f, 6.88f);
                // The inside edge: yellow next to the barrier in the Americas, white elsewhere.
                p.setColor(country.id == Country.USA || country.id == Country.MEXICO ? 0xF0E8C440 : 0xF0ECECEC);
                hwyRect(c, p, k, k + 1, 2.76f, 2.88f);
                hwyRect(c, p, k, k + 1, 4.12f, 4.24f);
                if (k % 3 == 0) {
                    p.setColor(0xE0ECECEC);
                    hwyRect(c, p, k + 0.2f, k + 1.4f, 1.44f, 1.56f);
                    hwyRect(c, p, k + 0.2f, k + 1.4f, 5.44f, 5.56f);
                }
                // The concrete barrier.
                if (tiles[(hwyAxis == 0 ? hwyAt + 3 : k) * w + (hwyAxis == 0 ? k : hwyAt + 3)] == FENCE) {
                    p.setColor(0xFF7A776F);
                    hwyRect(c, p, k, k + 1, 3.2f, 3.8f);
                    p.setColor(0xFFC4C0B6);
                    hwyRect(c, p, k, k + 1, 3.32f, 3.68f);
                    p.setColor(0xFF9A968C);
                    hwyRect(c, p, k, k + 0.06f, 3.32f, 3.68f);
                } else {
                    p.setColor(0xFF303236);
                    hwyRect(c, p, k, k + 1, 3, 4);
                }
            } else {
                p.setColor(0xFF303236);
                hwyRect(c, p, k, k + 1, 3, 4);
            }
        }
        // Stop lines at the lights, and the signs: a green board before each junction, both ways.
        for (int[] ex : hwyExits) {
            int k0 = ex[0], k1 = ex[0] + ex[1];
            int jk0 = k0, jk1 = k1;
            while (jk0 > 0 && junctionAt(hwyAxis == 0 ? jk0 - 1 : hwyAt + 1, hwyAxis == 0 ? hwyAt + 1 : jk0 - 1)) jk0--;
            while (jk1 < len - 1 && junctionAt(hwyAxis == 0 ? jk1 : hwyAt + 1, hwyAxis == 0 ? hwyAt + 1 : jk1)) jk1++;
            p.setColor(0xF0F2F2F2);
            for (int car = 0; car < 2; car++) {
                // Traffic on carriageway 0 comes from the high end (it heads to lower k); keeping left flips it.
                boolean fromHigh = (car == 0) != country.leftHand;
                if (hwyAxis == 1) fromHigh = !fromHigh;
                float a0 = car == 0 ? 0.2f : 4.2f, a1 = car == 0 ? 2.8f : 6.8f;
                float lineK = fromHigh ? jk1 + 0.15f : jk0 - 0.35f;
                hwyRect(c, p, lineK, lineK + 0.2f, a0, a1);
                // The sign, on the verge, a good way back.
                float sk = fromHigh ? jk1 + 9 : jk0 - 11, sa = car == 0 ? -1.6f : 7.6f;
                p.setColor(0x60000000);
                hwyRect(c, p, sk + 0.15f, sk + 2.15f, sa + 0.15f, sa + 1.25f);
                p.setColor(0xFF1E6A3A);
                hwyRect(c, p, sk, sk + 2, sa, sa + 1.1f);
                p.setColor(0xFFF2F2F2);
                hwyRect(c, p, sk + 0.15f, sk + 1.85f, sa + 0.1f, sa + 0.16f);
                hwyRect(c, p, sk + 0.15f, sk + 1.85f, sa + 0.94f, sa + 1f);
                p.setColor(0xF0F2F2F2);
            }
        }
        // The route shield at each end of the highway.
        for (int end = 0; end < 2; end++) {
            float k = end == 0 ? 3 : len - 5, a = end == 0 ? 7.6f : -1.8f;
            float sx = hwyAxis == 0 ? k * T : (hwyAt + a) * T, sy = hwyAxis == 0 ? (hwyAt + a) * T : k * T;
            p.setColor(country.id == Country.USA ? 0xFF1E3A8A : country.id == Country.FRANCE ? 0xFFB02020 : 0xFF1E6A3A);
            c.drawRect(sx, sy, sx + 26, sy + 14, p);
            if (country.id == Country.USA) {
                p.setColor(0xFFB02020);
                c.drawRect(sx, sy, sx + 26, sy + 4, p);
            }
            p.setColor(0xFFF2F2F2);
            p.setTextSize(7);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            c.drawText(hwyShield, sx + 13, sy + 11.5f, p);
            p.setFakeBoldText(false);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawRoadMarkings(Canvas c, Paint p) {
        p.setStrokeWidth(1.2f);
        for (Street st : streets) {
            int n = st.vertical ? st.y1 - st.y0 : st.x1 - st.x0;
            int dir = st.vertical ? 1 : 2;
            for (int k = 0; k < n; k++) {
                int x = st.vertical ? st.x0 : st.x0 + k, y = st.vertical ? st.y0 + k : st.y0;
                // Only between junctions.
                int mx = st.vertical ? st.x0 + st.width / 2 : x, my = st.vertical ? y : st.y0 + st.width / 2;
                if (mx >= w || my >= h || roadDir[my * w + mx] != dir || !paved(mx, my)) continue;
                float base = (st.vertical ? st.x0 : st.y0) * T, span = st.width * T;
                // Wear: darker tracks where the wheels run, two to each lane.
                int lanes = st.width >= 5 ? 4 : 2;
                p.setColor(0x14000000);
                for (int ln = 0; ln < lanes; ln++) {
                    float lc = base + span * (ln + 0.5f) / lanes;
                    for (int side = -1; side <= 1; side += 2) {
                        float wc = lc + side * 3.2f;
                        if (st.vertical) c.drawRect(wc - 1.2f, y * T, wc + 1.2f, y * T + T, p);
                        else c.drawRect(x * T, wc - 1.2f, x * T + T, wc + 1.2f, p);
                    }
                }
                // Solid white edge lines along the kerbs (not across a side road or a driveway).
                p.setColor(0xB8E4E4E0);
                for (int side = 0; side < 2; side++) {
                    int ox = st.vertical ? (side == 0 ? st.x0 - 1 : st.x1) : x, oy = st.vertical ? y : (side == 0 ? st.y0 - 1 : st.y1);
                    if (paved(ox, oy) || tiles[Math.max(0, Math.min(h - 1, oy)) * w + Math.max(0, Math.min(w - 1, ox))] == DIRT) continue;
                    float e = side == 0 ? base + 1.6f : base + span - 2.4f;
                    if (st.vertical) c.drawRect(e, y * T, e + 0.8f, y * T + T, p);
                    else c.drawRect(x * T, e, x * T + T, e + 0.8f, p);
                }
                p.setStrokeWidth(1.5f);
                if (st.width == 5) {
                    p.setColor(0xCCE8E8E8);
                    if (st.vertical) {
                        c.drawLine((st.x0 + 1) * T, y * T + 4, (st.x0 + 1) * T, y * T + 10, p);
                        c.drawLine((st.x0 + 4) * T, y * T + 4, (st.x0 + 4) * T, y * T + 10, p);
                    } else {
                        c.drawLine(x * T + 4, (st.y0 + 1) * T, x * T + 10, (st.y0 + 1) * T, p);
                        c.drawLine(x * T + 4, (st.y0 + 4) * T, x * T + 10, (st.y0 + 4) * T, p);
                    }
                } else if (st.width == 3 && !country.ringRoad.equals(st.name)) {
                    p.setColor(country.centreLine);
                    if (st.vertical) c.drawLine((st.x0 + 1.5f) * T, y * T + 2, (st.x0 + 1.5f) * T, y * T + 12, p);
                    else c.drawLine(x * T + 2, (st.y0 + 1.5f) * T, x * T + 12, (st.y0 + 1.5f) * T, p);
                }
                p.setStrokeWidth(1.2f);
            }
            if (st.width == 5) turnLanes(c, p, st, n);
            stopLines(c, p, st, n);
            // Zebra crossings where the street meets a junction.
            p.setColor(0xCCE8E8E8);
            for (int k = 1; k < n; k++) {
                int ax = st.vertical ? st.x0 : st.x0 + k - 1, ay = st.vertical ? st.y0 + k - 1 : st.y0;
                int bx = st.vertical ? st.x0 : st.x0 + k, by = st.vertical ? st.y0 + k : st.y0;
                boolean ja = junctionAt(ax, ay), jb = junctionAt(bx, by);
                if (ja == jb || bendAt(ja ? ax : bx, ja ? ay : by)) continue;
                // The crossing sits on the non-junction tile, against the junction.
                int cx = ja ? bx : ax, cy = ja ? by : ay;
                if (!paved(cx, cy)) continue;
                for (int o = 0; o < st.width; o++) {
                    int tx = st.vertical ? st.x0 + o : cx, ty = st.vertical ? cy : st.y0 + o;
                    if (!paved(tx, ty)) continue;
                    // Bold zebra bars, as wide as the gaps between them.
                    for (float q = 1.5f; q + 3 <= T; q += 6) {
                        if (st.vertical) {
                            float yy = ja ? ty * T + 1.5f : ty * T + T - 11.5f;
                            c.drawRect(tx * T + q, yy, tx * T + q + 3.2f, yy + 10, p);
                        } else {
                            float xx = ja ? tx * T + 1.5f : tx * T + T - 11.5f;
                            c.drawRect(xx, ty * T + q, xx + 10, ty * T + q + 3.2f, p);
                        }
                    }
                }
            }
        }
    }

    private boolean paved(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        byte t = tiles[y * w + x];
        return t == ROAD || t == CAR;
    }

    private void drawParkingLines(Canvas c, Paint p) {
        p.setColor(0xAAE0E0E0);
        p.setStrokeWidth(1f);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                byte t = tiles[y * w + x];
                if ((t == LOT || (t == CAR && isLotCar(x, y))) && y > 0 && y < h - 1) {
                    boolean row = tiles[(y - 1) * w + x] == LOT || tiles[(y + 1) * w + x] == LOT;
                    if (row && (t == CAR || rowHasCars(x, y))) c.drawLine(x * T, y * T + 2, x * T, y * T + T - 2, p);
                }
            }
    }

    private boolean rowHasCars(int x, int y) {
        return (x > 0 && tiles[y * w + x - 1] == CAR) || (x < w - 1 && tiles[y * w + x + 1] == CAR);
    }

    private void drawBuilding(Canvas c, Paint p, int[] b) {
        float x0 = b[0] * T, y0 = b[1] * T, x1 = (b[0] + b[2]) * T, y1 = (b[1] + b[3]) * T;
        int roof = b[4], kind = b[6];
        Random r = new Random(b[5]);
        float bw = x1 - x0, bh = y1 - y0;
        int bi = buildingAt[b[1] * w + b[0]];
        if (b.length > 9 && b[9] >= 0 && Roofs.drawVariant(this, c, p, b, Variants.get(b[9]))) return;
        if (Roofs.draw(this, c, p, b, bi >= 0 && bi < buildings.size() ? buildings.get(bi).name : null)) return;
        if (kind == HOUSE) {
            // Pitched roof: two slopes meeting at a ridge along the long side.
            p.setColor(darken(roof, 0.75f));
            c.drawRect(x0, y0, x1, y1, p);
            if (bw >= bh) {
                p.setColor(lighten(roof, 0.12f));
                c.drawRect(x0 + 1, y0 + 1, x1 - 1, (y0 + y1) / 2, p);
                p.setColor(roof);
                c.drawRect(x0 + 1, (y0 + y1) / 2, x1 - 1, y1 - 1, p);
                p.setColor(darken(roof, 0.6f));
                c.drawRect(x0 + 1, (y0 + y1) / 2 - 0.6f, x1 - 1, (y0 + y1) / 2 + 0.6f, p);
            } else {
                p.setColor(lighten(roof, 0.12f));
                c.drawRect(x0 + 1, y0 + 1, (x0 + x1) / 2, y1 - 1, p);
                p.setColor(roof);
                c.drawRect((x0 + x1) / 2, y0 + 1, x1 - 1, y1 - 1, p);
                p.setColor(darken(roof, 0.6f));
                c.drawRect((x0 + x1) / 2 - 0.6f, y0 + 1, (x0 + x1) / 2 + 0.6f, y1 - 1, p);
            }
            if (r.nextBoolean()) {
                p.setColor(0xFF6A5A50);
                c.drawRect(x0 + bw * 0.7f, y0 + 3, x0 + bw * 0.7f + 4, y0 + 7, p);
            }
            // Some houses have solar panels on the sunny slope.
            if (new Random(b[5] * 7L + 3).nextInt(4) == 0) {
                float px0 = x0 + 4, py0 = y0 + 3, px1, py1;
                if (bw >= bh) {
                    px1 = x0 + bw * 0.55f;
                    py1 = (y0 + y1) / 2 - 1.5f;
                } else {
                    px1 = (x0 + x1) / 2 - 1.5f;
                    py1 = y0 + bh * 0.55f;
                }
                drawSolar(c, p, px0, py0, px1, py1);
            }
            return;
        }
        if (kind == STATION) {
            p.setColor(0xFFE8EAEE);
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(roof);
            c.drawRect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, p);
            p.setColor(lighten(roof, 0.1f));
            c.drawRect(x0 + 5, y0 + 5, x1 - 5, y1 - 5, p);
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.min(10f, bw / 5.2f));
            label(country.sign("POLICE"), (x0 + x1) / 2, (y0 + y1) / 2 + 3.5f, Math.min(10f, bw / 5.2f), 0xFFFFFFFF);
            p.setColor(0xFFE8C547);
            c.drawCircle((x0 + x1) / 2, y0 + Math.min(bh * 0.25f, 12), 3.2f, p);
            return;
        }
        if (kind == FIRE_STATION || kind == MARKET || kind == SCHOOL || kind == KIOSK) {
            p.setColor(darken(roof, 0.7f));
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(roof);
            c.drawRect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, p);
            if (kind == MARKET || kind == SCHOOL) {
                p.setColor(0xCC9CC3D9);
                for (float sx = x0 + 10; sx < x1 - 10; sx += 18)
                    for (float sy = y0 + 10; sy < y1 - 10; sy += 18) c.drawRect(sx - 3, sy - 2, sx + 3, sy + 2, p);
            }
            String label = country.sign(kind == FIRE_STATION ? "FIRE" : kind == MARKET ? "MARKET" : kind == SCHOOL ? "SCHOOL" : "GAS");
            p.setColor(kind == FIRE_STATION ? 0xFFFFFFFF : kind == KIOSK ? 0xFFD83A3A : 0xFF3A3A3A);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.min(10f, bw / (label.length() * 0.75f)));
            label(label, (x0 + x1) / 2, (y0 + y1) / 2 + 3.5f, Math.min(10f, bw / (label.length() * 0.75f)),
                    kind == FIRE_STATION ? 0xFFFFFFFF : kind == KIOSK ? 0xFFD83A3A : 0xFF3A3A3A);
            return;
        }
        if (kind == CHURCH || kind == CRYPT) {
            // Pitched roof with a cross.
            p.setColor(darken(roof, 0.7f));
            c.drawRect(x0, y0, x1, y1, p);
            boolean alongX = bw >= bh;
            p.setColor(lighten(roof, 0.1f));
            if (alongX) c.drawRect(x0 + 1, y0 + 1, x1 - 1, (y0 + y1) / 2, p);
            else c.drawRect(x0 + 1, y0 + 1, (x0 + x1) / 2, y1 - 1, p);
            p.setColor(roof);
            if (alongX) c.drawRect(x0 + 1, (y0 + y1) / 2, x1 - 1, y1 - 1, p);
            else c.drawRect((x0 + x1) / 2, y0 + 1, x1 - 1, y1 - 1, p);
            p.setColor(0xFFE8D24A);
            float cxx = (x0 + x1) / 2, cyy = (y0 + y1) / 2;
            c.drawRect(cxx - 1, cyy - 5, cxx + 1, cyy + 5, p);
            c.drawRect(cxx - 3.5f, cyy - 2.5f, cxx + 3.5f, cyy - 0.8f, p);
            return;
        }
        if (kind == TRAIN_STATION) {
            // Long pitched roof with a clock.
            p.setColor(darken(roof, 0.75f));
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(lighten(roof, 0.1f));
            c.drawRect(x0 + 1, y0 + 1, x1 - 1, (y0 + y1) / 2, p);
            p.setColor(roof);
            c.drawRect(x0 + 1, (y0 + y1) / 2, x1 - 1, y1 - 1, p);
            p.setColor(0xFFF2EEE0);
            c.drawCircle((x0 + x1) / 2, (y0 + y1) / 2, 4, p);
            p.setColor(0xFF2A2A2A);
            p.setStrokeWidth(0.8f);
            c.drawLine((x0 + x1) / 2, (y0 + y1) / 2, (x0 + x1) / 2, (y0 + y1) / 2 - 3, p);
            c.drawLine((x0 + x1) / 2, (y0 + y1) / 2, (x0 + x1) / 2 + 2, (y0 + y1) / 2, p);
            return;
        }
        if (kind == MALL) {
            // Big flat roof with a glass atrium down the middle and plant rooms.
            p.setColor(darken(roof, 0.7f));
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(roof);
            c.drawRect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, p);
            boolean along = bw >= bh;
            float ax0 = along ? x0 + 8 : (x0 + x1) / 2 - 7, ay0 = along ? (y0 + y1) / 2 - 7 : y0 + 8;
            float ax1 = along ? x1 - 8 : (x0 + x1) / 2 + 7, ay1 = along ? (y0 + y1) / 2 + 7 : y1 - 8;
            p.setColor(0xFF6E8CA0);
            c.drawRect(ax0, ay0, ax1, ay1, p);
            p.setColor(0xFF9CC3D9);
            c.drawRect(ax0 + 1, ay0 + 1, ax1 - 1, ay1 - 1, p);
            p.setColor(0xFF6E8CA0);
            p.setStrokeWidth(0.8f);
            if (along) for (float gx = ax0 + 6; gx < ax1; gx += 6) c.drawLine(gx, ay0, gx, ay1, p);
            else for (float gy = ay0 + 6; gy < ay1; gy += 6) c.drawLine(ax0, gy, ax1, gy, p);
            c.drawLine(along ? ax0 : (ax0 + ax1) / 2, along ? (ay0 + ay1) / 2 : ay0, along ? ax1 : (ax0 + ax1) / 2,
                    along ? (ay0 + ay1) / 2 : ay1, p);
            p.setColor(0x66FFFFFF);
            c.drawRect(ax0 + 1, ay0 + 1, along ? ax1 - 1 : ax0 + 3, along ? ay0 + 3 : ay1 - 1, p);
            p.setColor(0xFFA7ABAF);
            c.drawRect(x0 + 6, y0 + 6, x0 + 16, y0 + 13, p);
            c.drawRect(x1 - 16, y1 - 13, x1 - 6, y1 - 6, p);
            p.setColor(0xFF7D8185);
            c.drawCircle(x0 + 11, y0 + 9.5f, 2.5f, p);
            c.drawCircle(x1 - 11, y1 - 9.5f, 2.5f, p);
            return;
        }
        if (kind == STADIUM) {
            // Tiered seating in club colours.
            p.setColor(0xFF6E7278);
            c.drawRect(x0, y0, x1, y1, p);
            boolean along = bw >= bh;
            int[] seats = {0xFFB83A30, 0xFFE8E8E8};
            int rows = (int) ((along ? bh : bw) / 3);
            for (int k = 0; k < rows; k++) {
                p.setColor(darken(seats[k % 2], 0.8f + 0.2f * k / Math.max(1, rows)));
                if (along) c.drawRect(x0 + 1, y0 + 1 + k * 3, x1 - 1, y0 + 3 + k * 3, p);
                else c.drawRect(x0 + 1 + k * 3, y0 + 1, x0 + 3 + k * 3, y1 - 1, p);
            }
            return;
        }
        if (kind == POWER) {
            p.setColor(darken(roof, 0.7f));
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(roof);
            c.drawRect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, p);
            p.setColor(darken(roof, 0.85f));
            p.setStrokeWidth(1f);
            for (float x = x0 + 5; x < x1 - 3; x += 4) c.drawLine(x, y0 + 2, x, y1 - 2, p);
            // Two tall chimneys, red and white.
            for (int k = 0; k < 2; k++) {
                float cx = x0 + bw * (0.3f + k * 0.4f), cy = y0 + bh * 0.35f;
                p.setColor(0x60000000);
                c.drawCircle(cx + 3, cy + 4, 5, p);
                p.setColor(0xFFE8E8E8);
                c.drawCircle(cx, cy, 5, p);
                p.setColor(0xFFC8302A);
                c.drawCircle(cx, cy, 3.6f, p);
                p.setColor(0xFF2A2A2A);
                c.drawCircle(cx, cy, 2.2f, p);
            }
            return;
        }
        if (kind == BARN) {
            p.setColor(darken(roof, 0.7f));
            c.drawRect(x0, y0, x1, y1, p);
            boolean along = bw >= bh;
            p.setColor(lighten(roof, 0.1f));
            if (along) c.drawRect(x0 + 1, y0 + 1, x1 - 1, (y0 + y1) / 2, p);
            else c.drawRect(x0 + 1, y0 + 1, (x0 + x1) / 2, y1 - 1, p);
            p.setColor(roof);
            if (along) c.drawRect(x0 + 1, (y0 + y1) / 2, x1 - 1, y1 - 1, p);
            else c.drawRect((x0 + x1) / 2, y0 + 1, x1 - 1, y1 - 1, p);
            p.setColor(0xFFE8E0D0);
            if (along) c.drawRect(x0 + 1, (y0 + y1) / 2 - 0.7f, x1 - 1, (y0 + y1) / 2 + 0.7f, p);
            else c.drawRect((x0 + x1) / 2 - 0.7f, y0 + 1, (x0 + x1) / 2 + 0.7f, y1 - 1, p);
            return;
        }
        if (kind == SILO) {
            p.setColor(0xFF4C7837);
            c.drawRect(x0, y0, x1, y1, p);
            float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
            p.setColor(darken(roof, 0.7f));
            c.drawCircle(cx, cy, bw / 2 - 0.5f, p);
            p.setColor(roof);
            c.drawCircle(cx, cy, bw / 2 - 1.5f, p);
            p.setColor(lighten(roof, 0.2f));
            c.drawCircle(cx - 1.5f, cy - 1.5f, bw / 4, p);
            return;
        }
        if (kind == APARTMENT) {
            // Flat roof with a rooftop water tank, stairwell hut and washing lines.
            p.setColor(darken(roof, 0.75f));
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(roof);
            c.drawRect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, p);
            p.setColor(darken(roof, 0.85f));
            for (float gy = y0 + 4; gy < y1 - 3; gy += 3) c.drawRect(x0 + 2, gy, x1 - 2, gy + 0.4f, p);
            p.setColor(0xFF7A5A3C);
            float tx = x0 + 5 + r.nextFloat() * Math.max(1, bw - 16);
            c.drawCircle(tx + 3, y0 + 7, 3.2f, p);
            p.setColor(0xFF9A7A58);
            c.drawCircle(tx + 3, y0 + 7, 2.2f, p);
            p.setColor(0xFFB8B4AC);
            c.drawRect(x1 - 11, y1 - 11, x1 - 4, y1 - 5, p);
            p.setColor(0xFF6A6A70);
            c.drawRect(x1 - 9, y1 - 7, x1 - 6, y1 - 5, p);
            p.setStrokeWidth(0.4f);
            p.setColor(0xFFDADADA);
            c.drawLine(x0 + 4, (y0 + y1) / 2, x0 + bw * 0.55f, (y0 + y1) / 2, p);
            int[] cloth = {0xFFE05050, 0xFF5080E0, 0xFFF0F0F0, 0xFFE0C050};
            for (int k = 0; k < 4; k++) {
                p.setColor(cloth[k]);
                float lx = x0 + 6 + k * (bw * 0.5f - 6) / 4;
                c.drawRect(lx, (y0 + y1) / 2, lx + 2, (y0 + y1) / 2 + 2.5f, p);
            }
            return;
        }
        if (kind == GARAGE) {
            // Parking garage: the top deck is a car park with a ramp.
            p.setColor(0xFF8E8E88);
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(roof);
            c.drawRect(x0 + 1.5f, y0 + 1.5f, x1 - 1.5f, y1 - 1.5f, p);
            p.setColor(0xFFE8E8E0);
            p.setStrokeWidth(0.6f);
            for (float gx = x0 + 4; gx < x1 - 6; gx += 8) {
                c.drawLine(gx, y0 + 2, gx, y0 + 12, p);
                c.drawLine(gx, y1 - 12, gx, y1 - 2, p);
            }
            p.setColor(0xFF55575A);
            c.drawRect(x1 - 10, y0 + 14, x1 - 2, y1 - 14, p);
            p.setColor(0xFFE8C547);
            for (float yy = y0 + 16; yy < y1 - 16; yy += 5) c.drawRect(x1 - 7, yy, x1 - 5, yy + 2, p);
            for (float gx = x0 + 4; gx < x1 - 8; gx += 8) {
                if (r.nextFloat() < 0.45f) continue;
                p.setColor(CAR_COLORS[r.nextInt(CAR_COLORS.length)]);
                c.drawRect(gx + 1.2f, y0 + 3, gx + 6.8f, y0 + 11, p);
                if (r.nextFloat() < 0.5f) {
                    p.setColor(CAR_COLORS[r.nextInt(CAR_COLORS.length)]);
                    c.drawRect(gx + 1.2f, y1 - 11, gx + 6.8f, y1 - 3, p);
                }
            }
            return;
        }
        if (kind == PHARMACY) {
            p.setColor(0xFFB8BEB8);
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(roof);
            c.drawRect(x0 + 1.5f, y0 + 1.5f, x1 - 1.5f, y1 - 1.5f, p);
            float cxx = (x0 + x1) / 2, cyy = (y0 + y1) / 2, a = Math.min(bw, bh) * 0.22f;
            p.setColor(0xFF2FA84F);
            c.drawRect(cxx - a, cyy - a / 3, cxx + a, cyy + a / 3, p);
            c.drawRect(cxx - a / 3, cyy - a, cxx + a / 3, cyy + a, p);
            return;
        }
        if (kind == SPIRE) {
            p.setColor(0xFF3A3640);
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(0xFF524C58);
            Path spire = new Path();
            spire.moveTo((x0 + x1) / 2, y0 + 1);
            spire.lineTo(x1 - 1, (y0 + y1) / 2);
            spire.lineTo((x0 + x1) / 2, y1 - 1);
            spire.lineTo(x0 + 1, (y0 + y1) / 2);
            spire.close();
            c.drawPath(spire, p);
            return;
        }
        if (kind == SHOP) {
            p.setColor(darken(roof, 0.7f));
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(roof);
            c.drawRect(x0 + 1.5f, y0 + 1.5f, x1 - 1.5f, y1 - 1.5f, p);
            Random rr = new Random(b[5]);
            p.setColor(0xFFA7ABAF);
            float ux = x0 + 4 + rr.nextFloat() * Math.max(1, bw - 12);
            c.drawRect(ux, y0 + 4, ux + 5, y0 + 8, p);
            return;
        }
        if (kind == HOSPITAL) {
            p.setColor(0xFFBFC5CB);
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(roof);
            c.drawRect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, p);
            float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, s = Math.min(Math.min(bw, bh) * 0.3f, 14);
            p.setColor(0xFFD83A3A);
            c.drawRect(cx - s, cy - s * 0.33f, cx + s, cy + s * 0.33f, p);
            c.drawRect(cx - s * 0.33f, cy - s, cx + s * 0.33f, cy + s, p);
            return;
        }
        if (kind == TOWER) {
            p.setColor(darken(roof, 0.7f));
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(roof);
            c.drawRect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, p);
            p.setColor(0xFF2C3220);
            c.drawRect(x0 + 6, y0 + 6, x1 - 6, y1 - 6, p);
            return;
        }
        if (kind == WAREHOUSE || kind == BARRACKS) {
            p.setColor(darken(roof, 0.7f));
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(roof);
            c.drawRect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, p);
            p.setColor(darken(roof, 0.88f));
            p.setStrokeWidth(1f);
            if (bw >= bh) for (float x = x0 + 5; x < x1 - 3; x += 4) c.drawLine(x, y0 + 2, x, y1 - 2, p);
            else for (float y = y0 + 5; y < y1 - 3; y += 4) c.drawLine(x0 + 2, y, x1 - 2, y, p);
            p.setColor(0xCC9CC3D9);
            int lights = 1 + (int) (Math.max(bw, bh) / 40);
            for (int i = 0; i < lights; i++) {
                float t = (i + 0.5f) / lights;
                float cx = bw >= bh ? x0 + bw * t : (x0 + x1) / 2, cy = bw >= bh ? (y0 + y1) / 2 : y0 + bh * t;
                c.drawRect(cx - 4, cy - 3, cx + 4, cy + 3, p);
            }
            return;
        }
        p.setColor(darken(roof, 0.7f));
        c.drawRect(x0, y0, x1, y1, p);
        p.setColor(roof);
        c.drawRect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, p);
        p.setColor(lighten(roof, 0.08f));
        c.drawRect(x0 + 5, y0 + 5, x1 - 5, y1 - 5, p);

        // Roof details on the lower half: solar panels, a rooftop garden or a row of skylights.
        int deco = bw >= 40 && bh >= 40 ? new Random(b[5] * 7L + 3).nextInt(5) : 4;
        float dx0 = x0 + 7, dy0 = (y0 + y1) / 2 + 2, dx1 = Math.min(x1 - 7, x0 + 7 + 70), dy1 = Math.min(y1 - 7, dy0 + 44);
        if (deco == 0) {
            drawSolar(c, p, dx0, dy0, dx1, dy1);
        } else if (deco == 1) {
            p.setColor(0xFF6B5238);
            c.drawRect(dx0, dy0, dx1, dy1, p);
            p.setColor(0xFF4F8A3A);
            c.drawRect(dx0 + 1.5f, dy0 + 1.5f, dx1 - 1.5f, dy1 - 1.5f, p);
            Random g = new Random(b[5]);
            for (int k = 0; k < (int) ((dx1 - dx0) * (dy1 - dy0) / 60); k++) {
                float gx = dx0 + 3 + g.nextFloat() * Math.max(1, dx1 - dx0 - 6), gy = dy0 + 3 + g.nextFloat() * Math.max(1, dy1 - dy0 - 6);
                p.setColor(g.nextInt(4) == 0 ? 0xFFE07AA0 : g.nextBoolean() ? 0xFF3B742D : 0xFF6AA84F);
                c.drawCircle(gx, gy, 1.5f + g.nextFloat() * 1.5f, p);
            }
            p.setColor(0xFFB8A888);
            c.drawRect(dx0 + 2, (dy0 + dy1) / 2 - 1, dx1 - 2, (dy0 + dy1) / 2 + 1, p);
        } else if (deco == 2) {
            for (float sx = dx0 + 2; sx + 8 < dx1; sx += 12) {
                p.setColor(darken(roof, 0.6f));
                c.drawRect(sx, dy0 + 2, sx + 8, dy1 - 2, p);
                p.setColor(0xCC9CC3D9);
                c.drawRect(sx + 1, dy0 + 3, sx + 7, dy1 - 3, p);
                p.setColor(0x66FFFFFF);
                c.drawRect(sx + 1, dy0 + 3, sx + 2.5f, dy1 - 3, p);
            }
        }
        float unitsH = deco < 3 ? bh / 2 : bh;
        int units = 1 + r.nextInt(Math.max(1, (int) (bw * bh / 1500f)) + 1);
        for (int i = 0; i < units; i++) {
            float uw = 6 + r.nextInt(6), uh = 5 + r.nextInt(5);
            float ux = x0 + 7 + r.nextFloat() * Math.max(1, bw - 14 - uw);
            float uy = y0 + 7 + r.nextFloat() * Math.max(1, unitsH - 14 - uh);
            p.setColor(0x44000000);
            c.drawRect(ux + 1.5f, uy + 1.5f, ux + uw + 1.5f, uy + uh + 1.5f, p);
            p.setColor(0xFFA7ABAF);
            c.drawRect(ux, uy, ux + uw, uy + uh, p);
            p.setColor(0xFF7D8185);
            c.drawCircle(ux + uw / 2, uy + uh / 2, Math.min(uw, uh) * 0.3f, p);
        }
        if (bw >= 64 && bh >= 64 && b[7] >= 8 && r.nextFloat() < 0.35f) {
            float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
            p.setColor(0xFF3F4347);
            c.drawCircle(cx, cy, 16, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            p.setColor(0xFFE8D24A);
            c.drawCircle(cx, cy, 13, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFEEEEEE);
            c.drawRect(cx - 6, cy - 7, cx - 3.5f, cy + 7, p);
            c.drawRect(cx + 3.5f, cy - 7, cx + 6, cy + 7, p);
            c.drawRect(cx - 4, cy - 1.2f, cx + 4, cy + 1.2f, p);
        }
    }

    /** A grid of dark blue solar panels. */
    private static void drawSolar(Canvas c, Paint p, float x0, float y0, float x1, float y1) {
        if (x1 - x0 < 4 || y1 - y0 < 4) return;
        p.setColor(0xFF8A8E94);
        c.drawRect(x0, y0, x1, y1, p);
        float cw = 5, ch = 4;
        for (float y = y0 + 0.6f; y + ch <= y1; y += ch + 0.6f)
            for (float x = x0 + 0.6f; x + cw <= x1; x += cw + 0.6f) {
                p.setColor(0xFF1F3A66);
                c.drawRect(x, y, x + cw, y + ch, p);
                p.setColor(0xFF3A5C8E);
                c.drawRect(x, y, x + cw, y + 0.8f, p);
            }
    }

    private void drawCar(Canvas c, Paint p, int x, int y) {
        prnd.setSeed(tileSeed(x, y, 3));
        boolean vertical;
        if (isLotCar(x, y)) vertical = true;
        else vertical = roadDir[y * w + x] == 1;
        float cx = x * T + T / 2f, cy = y * T + T / 2f;
        byte kind = carKind[y * w + x];
        if (kind == 2) vertical = true;
        float hl = kind == 2 ? 7.8f : 7.5f, hw = kind == 2 ? 4.8f : 4.2f;
        RectF rect = vertical ? new RectF(cx - hw, cy - hl, cx + hw, cy + hl) : new RectF(cx - hl, cy - hw, cx + hl, cy + hw);
        if (kind == 4) vertical = true;
        int color = kind == 4 ? 0xFFC8302A : kind == 1 ? 0xFF1C1D22 : kind == 2 ? 0xFF4F5A33 : kind == 3 ? 0xFFF2F2F2
                : CAR_COLORS[prnd.nextInt(CAR_COLORS.length)];
        p.setColor(0x55000000);
        rect.offset(1.5f, 1.5f);
        c.drawRoundRect(rect, 2.5f, 2.5f, p);
        rect.offset(-1.5f, -1.5f);
        p.setColor(color);
        c.drawRoundRect(rect, 2.5f, 2.5f, p);
        p.setColor(0xFF1E2A33);
        if (vertical) {
            c.drawRect(cx - hw + 1, cy - 4.5f, cx + hw - 1, cy - 1.5f, p);
            c.drawRect(cx - hw + 1, cy + 3f, cx + hw - 1, cy + 5f, p);
        } else {
            c.drawRect(cx + 1.5f, cy - hw + 1, cx + 4.5f, cy + hw - 1, p);
            c.drawRect(cx - 5f, cy - hw + 1, cx - 3f, cy + hw - 1, p);
        }
        p.setColor(lighten(color, 0.15f));
        if (vertical) c.drawRect(cx - hw + 1.5f, cy - 1f, cx + hw - 1.5f, cy + 2.5f, p);
        else c.drawRect(cx - 2.5f, cy - hw + 1.5f, cx + 1f, cy + hw - 1.5f, p);
        if (kind == 1) {
            // Police cruiser: white doors and a red/blue light bar.
            p.setColor(0xFFEDEDED);
            if (vertical) {
                c.drawRect(cx - hw, cy - 1.5f, cx - hw + 1.3f, cy + 3f, p);
                c.drawRect(cx + hw - 1.3f, cy - 1.5f, cx + hw, cy + 3f, p);
            } else {
                c.drawRect(cx - 3f, cy - hw, cx + 1.5f, cy - hw + 1.3f, p);
                c.drawRect(cx - 3f, cy + hw - 1.3f, cx + 1.5f, cy + hw, p);
            }
            p.setColor(0xFFE0302A);
            if (vertical) c.drawRect(cx - hw + 1, cy - 0.8f, cx, cy + 0.6f, p);
            else c.drawRect(cx - 0.8f, cy - hw + 1, cx + 0.6f, cy, p);
            p.setColor(0xFF2F6BFF);
            if (vertical) c.drawRect(cx, cy - 0.8f, cx + hw - 1, cy + 0.6f, p);
            else c.drawRect(cx - 0.8f, cy, cx + 0.6f, cy + hw - 1, p);
        } else if (kind == 4) {
            // Fire truck: ladder along the top.
            p.setColor(0xFFD8D8D8);
            c.drawRect(cx - 1.5f, cy - hl + 2, cx - 0.7f, cy + hl - 1, p);
            c.drawRect(cx + 0.7f, cy - hl + 2, cx + 1.5f, cy + hl - 1, p);
            for (float yy = cy - hl + 3; yy < cy + hl - 1; yy += 2.5f) c.drawRect(cx - 1.5f, yy, cx + 1.5f, yy + 0.5f, p);
        } else if (kind == 3) {
            // Ambulance: red stripe and cross.
            p.setColor(0xFFD83A3A);
            if (vertical) c.drawRect(cx - hw, cy + 3.5f, cx + hw, cy + 4.6f, p);
            else c.drawRect(cx - 4.6f, cy - hw, cx - 3.5f, cy + hw, p);
            c.drawRect(cx - 0.6f, cy - 2, cx + 0.6f, cy + 2, p);
            c.drawRect(cx - 2, cy - 0.6f, cx + 2, cy + 0.6f, p);
        } else if (kind == 2) {
            // Army truck: canvas-covered cargo bed.
            p.setColor(0xFF5F6B40);
            c.drawRect(cx - hw + 0.8f, cy - 1.5f, cx + hw - 0.8f, cy + hl - 0.8f, p);
            p.setColor(0xFF4A5530);
            for (float yy = cy; yy < cy + hl - 1; yy += 2.2f) c.drawRect(cx - hw + 0.8f, yy, cx + hw - 0.8f, yy + 0.5f, p);
        }
    }

    static int darken(int c, float f) {
        int r = (int) (((c >> 16) & 0xFF) * f), g = (int) (((c >> 8) & 0xFF) * f), b = (int) ((c & 0xFF) * f);
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static int lighten(int c, float f) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r = (int) (r + (255 - r) * f);
        g = (int) (g + (255 - g) * f);
        b = (int) (b + (255 - b) * f);
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ queries

    boolean solidTile(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= w || ty >= h) return true;
        return solid[ty * w + tx];
    }

    boolean solidAt(float x, float y) {
        return solidTile((int) Math.floor(x / T), (int) Math.floor(y / T));
    }

    /** Radius of a tree trunk, for walking into. */
    static final float TRUNK = 2.5f;

    /** True if a circle at (x, y) overlaps any solid tile or leaves the map. */
    boolean circleBlocked(float x, float y, float r) {
        int tx0 = (int) Math.floor((x - r) / T), tx1 = (int) Math.floor((x + r) / T);
        int ty0 = (int) Math.floor((y - r) / T), ty1 = (int) Math.floor((y + r) / T);
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                if (!solidTile(tx, ty)) continue;
                if (tx >= 0 && ty >= 0 && tx < w && ty < h && tiles[ty * w + tx] == TREE) {
                    // A tree is its trunk, not the whole tile: people slip between trees and slide round them.
                    float dx = x - (tx * T + T / 2f), dy = y - (ty * T + T / 2f), rr = r + TRUNK;
                    if (dx * dx + dy * dy < rr * rr) return true;
                    continue;
                }
                float nx = Math.max(tx * T, Math.min(x, tx * T + T));
                float ny = Math.max(ty * T, Math.min(y, ty * T + T));
                float dx = x - nx, dy = y - ny;
                if (dx * dx + dy * dy < r * r) return true;
            }
        }
        return false;
    }

    /** Can someone walk straight from one point to the other (nothing solid in the way but the odd tree)? */
    boolean passLine(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = (int) (len / 6f) + 1;
        for (int i = 1; i < steps; i++) {
            float f = i / (float) steps;
            int tx = (int) Math.floor((x0 + dx * f) / T), ty = (int) Math.floor((y0 + dy * f) / T);
            if (tx < 0 || ty < 0 || tx >= w || ty >= h) return false;
            int k = ty * w + tx;
            if (solid[k] && tiles[k] != TREE) return false;
        }
        return true;
    }

    boolean los(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = (int) (len / 5f) + 1;
        for (int i = 1; i < steps; i++) {
            float f = i / (float) steps;
            int tx = (int) Math.floor((x0 + dx * f) / T), ty = (int) Math.floor((y0 + dy * f) / T);
            if (tx < 0 || ty < 0 || tx >= w || ty >= h || opaque[ty * w + tx]) return false;
        }
        return true;
    }

    int tileIndex(float x, float y) {
        int tx = Math.max(0, Math.min(w - 1, (int) (x / T)));
        int ty = Math.max(0, Math.min(h - 1, (int) (y / T)));
        return ty * w + tx;
    }

    int fieldAt(int[] field, float x, float y) {
        return field[tileIndex(x, y)];
    }

    /** Finds the centre of a walkable tile near (x, y), or null if there is none close by. */
    float[] findWalkable(float x, float y) {
        int tx = (int) Math.floor(x / T), ty = (int) Math.floor(y / T);
        if (!solidTile(tx, ty) && !circleBlocked(x, y, 4f)) return new float[]{x, y};
        for (int r = 1; r <= 5; r++) {
            float best = Float.MAX_VALUE;
            float[] res = null;
            for (int j = ty - r; j <= ty + r; j++)
                for (int i = tx - r; i <= tx + r; i++) {
                    if (Math.max(Math.abs(i - tx), Math.abs(j - ty)) != r || solidTile(i, j)) continue;
                    float cx = i * T + T / 2f, cy = j * T + T / 2f;
                    float d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
                    if (d < best) {
                        best = d;
                        res = new float[]{cx, cy};
                    }
                }
            if (res != null) return res;
        }
        return null;
    }

    /** The town part of the map (tiles), inside any countryside. */
    int townX0, townY0, townX1, townY1;

    /** A random walkable spot in town (the whole map if there's no countryside). */
    float[] randomWalkableInTown(Random r) {
        for (int k = 0; k < 500; k++) {
            int tx = townX0 + r.nextInt(Math.max(1, townX1 - townX0)), ty = townY0 + r.nextInt(Math.max(1, townY1 - townY0));
            byte t = tiles[ty * w + tx];
            if (t == SIDEWALK || t == PLAZA || t == GRASS || ((t == ROAD || t == LOT) && r.nextFloat() < 0.2f))
                return new float[]{tx * T + 3 + r.nextFloat() * (T - 6), ty * T + 3 + r.nextFloat() * (T - 6)};
        }
        return randomWalkable(r);
    }

    /** A drivable spot on the edge of the map (where convoys come in), or null. */
    float[] edgeRoad(Random r) {
        for (int k = 0; k < 400; k++) {
            int side = r.nextInt(4), pos = r.nextInt(w);
            int tx = side == 0 ? pos : side == 1 ? pos : side == 2 ? 1 : w - 2, ty = side == 0 ? 1 : side == 1 ? h - 2 : pos;
            int i = ty * w + tx;
            if (driveCost(i) > 0 && driveCost(i) <= 4) return new float[]{tx * T + T / 2f, ty * T + T / 2f};
        }
        return null;
    }

    float[] randomWalkable(Random r) {
        for (int k = 0; k < 500; k++) {
            int tx = r.nextInt(w), ty = r.nextInt(h);
            byte t = tiles[ty * w + tx];
            if (t == SIDEWALK || t == PLAZA || t == GRASS || ((t == ROAD || t == LOT) && r.nextFloat() < 0.2f))
                return new float[]{tx * T + 3 + r.nextFloat() * (T - 6), ty * T + 3 + r.nextFloat() * (T - 6)};
        }
        return new float[]{T * 1.5f, T * 1.5f};
    }

    // ------------------------------------------------------------------ places

    private static String ordinal(int n) {
        String suffix = n % 100 >= 11 && n % 100 <= 13 ? "th"
                : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + suffix;
    }

    /** A street corner name for a world position, like "Oak St & 3rd Ave". */
    String placeName(float x, float y) {
        if (hwyAxis >= 0) {
            int t = (int) ((hwyAxis == 0 ? y : x) / T);
            if (t >= hwyAt - 2 && t <= hwyAt + 8) return hwyName;
        }
        // The nearest street each way, if the point is beside it.
        Street across = null, along = null;
        float bestV = 6 * T, bestH = 6 * T;
        for (Street st : streets) {
            if (st.vertical) {
                if (y < st.y0 * T - T || y > st.y1 * T + T) continue;
                float d = Math.abs(x - (st.x0 + st.x1) / 2f * T);
                if (d < bestV) {
                    bestV = d;
                    across = st;
                }
            } else {
                if (x < st.x0 * T - T || x > st.x1 * T + T) continue;
                float d = Math.abs(y - (st.y0 + st.y1) / 2f * T);
                if (d < bestH) {
                    bestH = d;
                    along = st;
                }
            }
        }
        if (across != null && along != null && !across.name.equals(along.name)) return along.name + " & " + across.name;
        if (across != null || along != null) return (across != null && (along == null || bestV < bestH) ? across : along).name;
        // Inside a big block: the nearest street at all.
        Street best = null;
        float bd = Float.MAX_VALUE;
        for (Street st : streets) {
            float nx = Math.max(st.x0 * T, Math.min(x, st.x1 * T)), ny = Math.max(st.y0 * T, Math.min(y, st.y1 * T));
            float d = (nx - x) * (nx - x) + (ny - y) * (ny - y);
            if (d < bd) {
                bd = d;
                best = st;
            }
        }
        return best != null ? best.name : "downtown";
    }

    /** A name for an open area, like "Pine St Park". */
    String areaName(float[] area) {
        String street = placeName(area[0], area[1]);
        int amp = street.indexOf(" & ");
        if (amp > 0) street = street.substring(0, amp);
        String[] kinds = {"Park", "Plaza", "Parking Lot"};
        return street + " " + kinds[(int) area[2]];
    }

    /** The nearest facility of a kind to (x, y), or null if the map has none. */
    Facility nearestFacility(int kind, float x, float y) {
        Facility best = null;
        float bd = Float.MAX_VALUE;
        for (Facility f : facilities) {
            if (f.kind != kind) continue;
            float d = (f.x - x) * (f.x - x) + (f.y - y) * (f.y - y);
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        return best;
    }

    /** A walkable spot near the edge of the map, as close as possible to (tx, ty). */
    float[] edgeSpawn(float tx, float ty) {
        float best = Float.MAX_VALUE;
        float[] res = null;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int d = Math.min(Math.min(x, y), Math.min(w - 1 - x, h - 1 - y));
                if (d > origin + 2 || solid[y * w + x]) continue;
                float cx = x * T + T / 2f, cy = y * T + T / 2f;
                float dd = (cx - tx) * (cx - tx) + (cy - ty) * (cy - ty);
                if (dd < best) {
                    best = dd;
                    res = new float[]{cx, cy};
                }
            }
        return res;
    }

    // ------------------------------------------------------------------ flow fields

    void computeFields(List<Entity> entities) {
        // Only near distances matter (how far the nearest zombie or person is, within smelling range), so
        // the searches stop spreading after a while instead of covering a whole massive map.
        bfs(humanDist, entities, false, (int) (World.SCENT * 1.7f) + 4);
        bfs(zombieDist, entities, true, 70);
    }

    /** Can a vehicle drive over this tile, and at what cost: roads are cheap, sidewalks and lots dearer. */
    private int driveCost(int i) {
        byte t = tiles[i];
        if (t == ROAD) return 2;
        if (t == DIRT) return 4;
        if (t == LOT || t == BASE) return 3;
        if (t == SIDEWALK || t == PLAZA) return 8;
        return -1;
    }

    boolean drivable(float x, float y) {
        int tx = (int) Math.floor(x / T), ty = (int) Math.floor(y / T);
        return tx >= 0 && ty >= 0 && tx < w && ty < h && driveCost(ty * w + tx) > 0;
    }

    /** The nearest drivable tile centre to (x, y), or null. */
    float[] nearestDrivable(float x, float y) {
        int tx = (int) (x / T), ty = (int) (y / T);
        for (int r = 0; r <= 6; r++)
            for (int j = ty - r; j <= ty + r; j++)
                for (int i = tx - r; i <= tx + r; i++) {
                    if (Math.max(Math.abs(i - tx), Math.abs(j - ty)) != r) continue;
                    if (i < 0 || j < 0 || i >= w || j >= h || driveCost(j * w + i) < 0) continue;
                    return new float[]{i * T + T / 2f, j * T + T / 2f};
                }
        return null;
    }

    /** Driving cost field to (x, y) for vehicles (Dijkstra; prefers roads). Returns false if unreachable. */
    boolean driveField(int[] dist, float x, float y) {
        return driveField(dist, x, y, false);
    }

    /** With strict on, only roads and dirt tracks: everyday traffic doesn't cut across pavements and car parks. */
    boolean driveField(int[] dist, float x, float y, boolean strict) {
        return driveField(dist, x, y, strict, -1);
    }

    /**
     * As above, but done once the search has got back to the vehicle at tile from (and a little beyond, for the
     * lanes either side of its way): a trip across town needn't cost a search of the whole map. Tiles further
     * out are left FAR.
     */
    boolean driveField(int[] dist, float x, float y, boolean strict, int from) {
        Arrays.fill(dist, FAR);
        int fx = from < 0 ? -9 : from % w, fy = from < 0 ? -9 : from / w, stopAt = Integer.MAX_VALUE;
        // (Its own tile, if it's on the road: a tile near it may be the far carriageway, a long way round.)
        int fc = from < 0 ? -1 : driveCost(from);
        boolean fromRoad = fc >= 0 && (!strict || fc <= 4);
        float[] p = nearestDrivable(x, y);
        if (p == null) return false;
        // Dial's algorithm: step costs are small whole numbers, so a ring of buckets (one per distance) does
        // the priority queue's job without the boxing.
        for (int b = 0; b < 32; b++) {
            if (dialB[b] == null) dialB[b] = new int[256];
            dialN[b] = 0;
        }
        int s0 = tileIndex(p[0], p[1]);
        dist[s0] = 0;
        dialPush(s0, 0);
        int left = 1;
        for (int d = 0; left > 0 && d <= stopAt; d++) {
          int bk = d & 31;
          while (dialN[bk] > 0) {
            int t = dialB[bk][--dialN[bk]];
            left--;
            if (dist[t] != d) continue;
            int tx = t % w, ty = t / w;
            // Back at the vehicle: finish off the lanes round it, then stop.
            if (stopAt == Integer.MAX_VALUE && (fromRoad ? t == from : Math.abs(tx - fx) <= 1 && Math.abs(ty - fy) <= 1)) stopAt = d + 160;
            for (int k = 0; k < 4; k++) {
                int nx = tx + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = ty + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int n = ny * w + nx, c = driveCost(n);
                if (strict && c > 4) continue;
                // One way only on the highway (a car here would drive from n to t).
                if (oneWay != null && c >= 0 && (!oneWayOk(n, tx - nx, ty - ny) || !oneWayOk(t, tx - nx, ty - ny))) continue;
                // (And down each side of a boulevard.)
                if (divided != null && c >= 0 && (!wayOk(divided, n, tx - nx, ty - ny) || !wayOk(divided, t, tx - nx, ty - ny))) continue;
                // A step across the road (from lane to lane) outside a junction is a last resort: routes keep
                // to their row of the road and only change rows at the junctions, so cars don't swerve over.
                // (Emergencies too: lights and sirens still keep to their own side.)
                if (c >= 0 && c <= 4 && junctionId[n] < 0 && junctionId[t] < 0) {
                    byte rd = roadDir[n];
                    if ((rd == 1 && k < 2) || (rd == 2 && k >= 2)) c += 14;
                }
                if (c < 0 || d + c >= dist[n]) continue;
                dist[n] = d + c;
                dialPush(n, d + c);
                left++;
            }
          }
        }
        for (int b = 0; b < 32; b++) dialN[b] = 0;
        return true;
    }

    private final int[][] dialB = new int[32][];
    private final int[] dialN = new int[32];

    private void dialPush(int t, int d) {
        int b = d & 31;
        if (dialN[b] == dialB[b].length) dialB[b] = Arrays.copyOf(dialB[b], dialB[b].length * 2);
        dialB[b][dialN[b]++] = t;
    }

    /**
     * What a step onto each tile costs someone on foot who keeps to the rules: pavement, grass and zebra
     * crossings 1, a junction 3 (cut across only if there's no crossing), the middle of a road 8.
     */
    byte[] walkCost;
    private int[][] walkBuckets;

    /** Where roads meet: paved both ways for further than any one road is wide. */
    private boolean[] junction;

    private boolean sealed;

    /**
     * The highway is only joined at its proper exits: a dirt track or a bit of lot running right up to the
     * edge of a carriageway (where cars would cut on and off the highway, and end up going round in
     * circles trying to) is grassed over.
     */
    private void sealHighway() {
        sealed = true;
        if (hwyAxis < 0 || oneWay == null) return;
        boolean ew = hwyAxis == 0;
        int len = ew ? w : h;
        for (int k = 0; k < len; k++)
            for (int a : new int[]{-1, 7}) {
                int x = ew ? k : hwyAt + a, y = ew ? hwyAt + a : k;
                if (x < 0 || y < 0 || x >= w || y >= h) continue;
                int i = y * w + x;
                if (tiles[i] != DIRT && tiles[i] != LOT) continue;
                int hx = ew ? x : hwyAt + (a < 0 ? 0 : 6), hy = ew ? hwyAt + (a < 0 ? 0 : 6) : y;
                if (oneWay[hy * w + hx] == 0) continue;
                tiles[i] = GRASS;
                solid[i] = false;
            }
    }

    private void computeJunctions() {
        if (!sealed) sealHighway();
        junction = new boolean[w * h];
        int[] run = new int[w * h];
        for (int y = 0; y < h; y++) {
            int x = 0;
            while (x < w) {
                if (!paved(x, y)) { x++; continue; }
                int s = x;
                while (x < w && paved(x, y)) x++;
                for (int k = s; k < x; k++) run[y * w + k] = x - s;
            }
        }
        for (int x = 0; x < w; x++) {
            int y = 0;
            while (y < h) {
                if (!paved(x, y)) { y++; continue; }
                int s = y;
                while (y < h && paved(x, y)) y++;
                for (int k = s; k < y; k++) if (y - s >= 7 && run[k * w + x] >= 7) junction[k * w + x] = true;
            }
        }
        // Group the junction tiles into junctions, and decide how each is controlled.
        junctionId = new int[w * h];
        Arrays.fill(junctionId, -1);
        junctions.clear();
        int[] stack = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            if (!junction[i] || junctionId[i] >= 0) continue;
            int id = junctions.size(), top = 0, x0 = w, y0 = h, x1 = -1, y1 = -1, size = 0;
            stack[top++] = i;
            junctionId[i] = id;
            while (top > 0) {
                int t = stack[--top], tx = t % w, ty = t / w;
                size++;
                x0 = Math.min(x0, tx);
                y0 = Math.min(y0, ty);
                x1 = Math.max(x1, tx);
                y1 = Math.max(y1, ty);
                for (int q = 0; q < 4; q++) {
                    int nx = tx + (q == 0 ? 1 : q == 1 ? -1 : 0), ny = ty + (q == 2 ? 1 : q == 3 ? -1 : 0);
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int n = ny * w + nx;
                    if (junction[n] && junctionId[n] < 0) {
                        junctionId[n] = id;
                        stack[top++] = n;
                    }
                }
            }
            // A ring with an island is a roundabout; where an avenue is involved there are traffic lights;
            // the rest are stop junctions.
            boolean island = false;
            for (int y = Math.max(0, y0 - 1); y <= Math.min(h - 1, y1 + 1) && !island; y++)
                for (int x = Math.max(0, x0 - 1); x <= Math.min(w - 1, x1 + 1); x++)
                    if (x > x0 && x < x1 && y > y0 && y < y1 && (tiles[y * w + x] == GRASS || tiles[y * w + x] == TREE)) island = true;
            int type = island ? J_ROUNDABOUT : (x1 - x0 >= 4 || y1 - y0 >= 4) ? J_LIGHTS : J_STOP;
            // Just two roads meeting at a corner: a bend, not a junction anyone stops at.
            if (type == J_STOP) {
                boolean n = false, s = false, e = false, wst = false;
                for (int x = x0; x <= x1; x++) {
                    if (paved(x, y0 - 1)) n = true;
                    if (paved(x, y1 + 1)) s = true;
                }
                for (int y = y0; y <= y1; y++) {
                    if (paved(x0 - 1, y)) wst = true;
                    if (paved(x1 + 1, y)) e = true;
                }
                if ((n ? 1 : 0) + (s ? 1 : 0) + (e ? 1 : 0) + (wst ? 1 : 0) == 2 && (n || s) && (e || wst)) type = J_BEND;
            }
            junctions.add(new int[]{x0, y0, x1, y1, type, (id * 7919) % 23});
        }
        // Junctions only a tile or two apart are one junction as far as anyone driving is concerned.
        int n = junctions.size();
        int[] root = new int[n];
        for (int i = 0; i < n; i++) root[i] = i;
        for (int i = 0; i < n; i++)
            for (int k = i + 1; k < n; k++) {
                int[] a = junctions.get(i), b = junctions.get(k);
                if (a[0] - 3 <= b[2] && b[0] - 3 <= a[2] && a[1] - 3 <= b[3] && b[1] - 3 <= a[3]) {
                    int ra = i, rb = k;
                    while (root[ra] != ra) ra = root[ra];
                    while (root[rb] != rb) rb = root[rb];
                    if (ra != rb) root[Math.max(ra, rb)] = Math.min(ra, rb);
                }
            }
        boolean merged = false;
        for (int i = 0; i < n; i++) {
            int r = i;
            while (root[r] != r) r = root[r];
            root[i] = r;
            if (r != i) merged = true;
        }
        if (merged) {
            int[] newId = new int[n];
            List<int[]> out = new ArrayList<int[]>();
            for (int i = 0; i < n; i++) {
                if (root[i] != i) continue;
                newId[i] = out.size();
                out.add(junctions.get(i).clone());
            }
            for (int i = 0; i < n; i++) {
                if (root[i] == i) continue;
                int[] into = out.get(newId[root[i]]), from = junctions.get(i);
                newId[i] = newId[root[i]];
                into[0] = Math.min(into[0], from[0]);
                into[1] = Math.min(into[1], from[1]);
                into[2] = Math.max(into[2], from[2]);
                into[3] = Math.max(into[3], from[3]);
                // Lights win over a stop sign; a roundabout stays a roundabout; a bend next to anything is a stop.
                if (into[4] == J_BEND || from[4] == J_BEND) {
                    int o = into[4] == J_BEND ? from[4] : into[4];
                    into[4] = o == J_BEND ? J_STOP : o;
                } else if (into[4] != J_ROUNDABOUT && from[4] != J_STOP) into[4] = from[4];
                // Big enough together (both sides of a divided highway): lights.
                if (into[4] == J_STOP && (into[2] - into[0] >= 4 || into[3] - into[1] >= 4)) into[4] = J_LIGHTS;
            }
            for (int i = 0; i < w * h; i++) if (junctionId[i] >= 0) junctionId[i] = newId[junctionId[i]];
            junctions.clear();
            junctions.addAll(out);
        }
        // The road inside a junction's box (the short links between merged parts) belongs to it too.
        for (int id = 0; id < junctions.size(); id++) {
            int[] jb = junctions.get(id);
            for (int y = jb[1]; y <= jb[3]; y++)
                for (int x = jb[0]; x <= jb[2]; x++)
                    if (junctionId[y * w + x] < 0 && paved(x, y)) junctionId[y * w + x] = id;
        }
    }

    static final int J_STOP = 0, J_LIGHTS = 1, J_ROUNDABOUT = 2, J_BEND = 3;
    /** Junctions: {x0, y0, x1, y1 (tiles, inclusive), type, light timing offset}. */
    final List<int[]> junctions = new ArrayList<int[]>();
    private int[] junctionId;

    /** Which junction a point is in, or -1. */
    int junctionIdAt(float x, float y) {
        if (junction == null) computeJunctions();
        int tx = (int) Math.floor(x / T), ty = (int) Math.floor(y / T);
        return tx < 0 || ty < 0 || tx >= w || ty >= h ? -1 : junctionId[ty * w + tx];
    }

    /** Traffic lights: 0 green, 1 amber, 2 red for traffic going north-south (vertical) or east-west. */
    /**
     * Traffic lights that answer to the traffic: each set stays green while its own traffic flows and nobody
     * waits the other way; with someone waiting, it changes after a short minimum green (or a longer maximum, if
     * its own traffic keeps coming). Phases: 0 north-south green, 1 its amber, 2 all red, 3 east-west green,
     * 4 its amber, 5 all red.
     */
    private byte[] lightPhase;
    private float[] lightT;
    private int[] demandNS, demandEW;

    private void initLights() {
        int n = junctions.size();
        lightPhase = new byte[n];
        lightT = new float[n];
        demandNS = new int[n];
        demandEW = new int[n];
        for (int i = 0; i < n; i++) {
            lightPhase[i] = (byte) ((i * 7919) % 2 == 0 ? 0 : 3);
            lightT[i] = (i * 31) % 6;
        }
    }

    /** A vehicle coming up to lights, heading north-south (vertical) or east-west. */
    void lightDemand(int id, boolean vertical) {
        if (demandNS == null || id < 0 || id >= demandNS.length) return;
        if (vertical) demandNS[id]++;
        else demandEW[id]++;
    }

    void updateLights(float dt) {
        if (lightPhase == null || lightPhase.length != junctions.size()) initLights();
        for (int i = 0; i < lightPhase.length; i++) {
            lightT[i] += dt;
            int p = lightPhase[i];
            float t = lightT[i];
            boolean nsGreen = p == 0;
            if (p == 0 || p == 3) {
                int mine = nsGreen ? demandNS[i] : demandEW[i], other = nsGreen ? demandEW[i] : demandNS[i];
                if (other > 0 && t >= 6 && (mine == 0 || t >= 16)) next(i);
            } else if ((p == 1 || p == 4) && t >= 2.5f) next(i);
            else if ((p == 2 || p == 5) && t >= 1.2f) next(i);
            demandNS[i] = demandEW[i] = 0;
        }
    }

    private void next(int i) {
        lightPhase[i] = (byte) ((lightPhase[i] + 1) % 6);
        lightT[i] = 0;
    }

    int lightState(int id, boolean vertical, float time) {
        if (lightPhase != null && id < lightPhase.length) {
            int p = lightPhase[id];
            if (vertical) return p == 0 ? 0 : p == 1 ? 1 : 2;
            return p == 3 ? 0 : p == 4 ? 1 : 2;
        }
        float t = (time + junctions.get(id)[5]) % 26f;
        // North-south: green 0-9, amber 9-12, then red while east-west has green 13-22 and amber 22-25
        // (a second of all-red in between each way).
        if (vertical) return t < 9 ? 0 : t < 12 ? 1 : 2;
        return t >= 13 && t < 22 ? 0 : t >= 22 && t < 25 ? 1 : 2;
    }

    /**
     * Where the lane is, across the road: how far (world units, to the driver's right) the middle of the lane
     * on the proper side of the road is from the middle of tile (tx, ty), for someone heading (dx, dy). The
     * other side of a median counts as the same road.
     */
    float laneOffset(int tx, int ty, int dx, int dy) {
        // On the highway: the inside lane of your own carriageway.
        if (oneWay != null && oneWay[ty * w + tx] != 0) {
            float c = hwyCentre(tx, ty), here = hwyAxis == 0 ? (ty + 0.5f) * T : (tx + 0.5f) * T;
            float side = hwyAxis == 0 ? dx : -dy;
            // (Across the highway, at the crossovers at the ends: straight over. And never more than a lane
            // and a bit off the tile, whatever tile it's on.)
            if ((hwyAxis == 0 ? dx : dy) == 0) return 0;
            float off = (c - here) * side + T * 0.7f * (country.leftHand ? -1 : 1);
            return Math.max(-T * 1.3f, Math.min(T * 1.3f, off));
        }
        if (junctionAt(tx, ty)) return 4.5f * (country.leftHand ? -1 : 1);
        int rx = -dy, ry = dx;
        int right = 0, left = 0;
        // (The highway is a road of its own: a frontage road beside it doesn't count its lanes as its own.)
        for (int s = 1; s <= 6; s++) {
            int x = tx + rx * s, y = ty + ry * s;
            if (hwyTile(x, y)) break;
            if (paved(x, y)) right = s;
            else if (isMedian(x, y) && paved(x + rx, y + ry) && !hwyTile(x + rx, y + ry)) continue;
            else break;
        }
        for (int s = 1; s <= 6; s++) {
            int x = tx - rx * s, y = ty - ry * s;
            if (hwyTile(x, y)) break;
            if (paved(x, y)) left = s;
            else if (isMedian(x, y) && paved(x - rx, y - ry) && !hwyTile(x - rx, y - ry)) continue;
            else break;
        }
        float centre = (right - left) / 2f, half = (right + left + 1) / 2f;
        float lane = half * 0.42f * (country.leftHand ? -1 : 1);
        return (centre + lane) * T;
    }

    private boolean hwyTile(int x, int y) {
        return oneWay != null && x >= 0 && y >= 0 && x < w && y < h && oneWay[y * w + x] != 0;
    }

    private boolean isMedian(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        byte t = tiles[y * w + x];
        return t == GRASS || t == TREE;
    }

    boolean junctionAt(int x, int y) {
        if (junction == null) computeJunctions();
        return x >= 0 && y >= 0 && x < w && y < h && junction[y * w + x];
    }

    /** Part of a bend: two roads meeting at a corner, where nobody stops. */
    boolean bendAt(int x, int y) {
        if (!junctionAt(x, y)) return false;
        int id = junctionId[y * w + x];
        return id >= 0 && junctions.get(id)[4] == J_BEND;
    }

    /** A road tile with zebra stripes: next to a junction but not in it. */
    boolean crosswalk(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h || tiles[y * w + x] != ROAD || junctionAt(x, y)) return false;
        return junctionAt(x - 1, y) || junctionAt(x + 1, y) || junctionAt(x, y - 1) || junctionAt(x, y + 1);
    }

    void computeWalkCost() {
        computeJunctions();
        walkCost = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                byte t = tiles[i];
                walkCost[i] = (byte) (t != ROAD ? 1 : junction[i] ? 3 : crosswalk(x, y) ? 1 : 8);
            }
    }

    /**
     * Like fieldFromPoints, but for walking the way people really do: along the pavement, across at the
     * zebra crossings. Roads without a pavement (country lanes) still get used when there's no other way.
     */
    void walkFieldFromPoints(int[] dist, float[] xs, float[] ys, int n) {
        walkFieldFromPoints(dist, xs, ys, n, FAR);
    }

    /** As above, but only out to a walking distance of limit (in steps of cost). */
    void walkFieldFromPoints(int[] dist, float[] xs, float[] ys, int n, int limit) {
        if (walkCost == null) computeWalkCost();
        Arrays.fill(dist, FAR);
        if (walkBuckets == null) walkBuckets = new int[9][w * h];
        int[] sizes = new int[9];
        int pending = 0;
        for (int i = 0; i < n; i++) {
            int t = tileIndex(xs[i], ys[i]);
            if (dist[t] != 0) {
                dist[t] = 0;
                walkBuckets[0][sizes[0]++] = t;
                pending++;
            }
        }
        for (int d = 0; pending > 0; d++) {
            int bi = d % 9;
            if (d > limit) {
                // Past the limit: what's left in the queue stays unreached.
                for (int k = 0; k < 9; k++) sizes[k] = 0;
                break;
            }
            int[] bk = walkBuckets[bi];
            int size = sizes[bi];
            for (int k = 0; k < size; k++) {
                int t = bk[k];
                if (dist[t] != d) continue;
                int tx = t % w, ty = t / w;
                for (int q = 0; q < 4; q++) {
                    int nb;
                    if (q == 0) { if (tx == 0) continue; nb = t - 1; }
                    else if (q == 1) { if (tx == w - 1) continue; nb = t + 1; }
                    else if (q == 2) { if (ty == 0) continue; nb = t - w; }
                    else { if (ty == h - 1) continue; nb = t + w; }
                    if (solid[nb]) continue;
                    int nd = d + walkCost[nb];
                    if (nd >= dist[nb]) continue;
                    dist[nb] = nd;
                    int b2 = nd % 9;
                    walkBuckets[b2][sizes[b2]++] = nb;
                    pending++;
                }
            }
            pending -= size;
            sizes[bi] = 0;
        }
    }

    // A* over the walking costs, for one person's trip: no map-sized route to build and keep.
    private int[] astarG, astarFrom, astarStamp;
    private long[] astarHeap = new long[4096];
    private int astarRun;

    /**
     * A walking route from (sx, sy) to (tx, ty) as tile indices (start excluded), keeping to pavements and
     * crossings like walkFieldFromPoints, or null if there's none within maxExpand tiles searched.
     */
    int[] findPath(float sx, float sy, float tx, float ty, int maxExpand) {
        if (walkCost == null) computeWalkCost();
        if (astarG == null) {
            astarG = new int[w * h];
            astarFrom = new int[w * h];
            astarStamp = new int[w * h];
        }
        int run = ++astarRun;
        int s = tileIndex(sx, sy), g = tileIndex(tx, ty);
        if (s == g) return new int[]{g};
        int gx = g % w, gy = g / w;
        int size = 0;
        astarStamp[s] = run;
        astarG[s] = 0;
        astarFrom[s] = -1;
        astarHeap[size++] = ((long) heur(s % w, s / w, gx, gy) << 32) | s;
        int expanded = 0;
        while (size > 0) {
            long top = astarHeap[0];
            astarHeap[0] = astarHeap[--size];
            siftDown(size);
            int t = (int) (top & 0xFFFFFFFFL), f = (int) (top >>> 32);
            int tx0 = t % w, ty0 = t / w;
            if (f - heur(tx0, ty0, gx, gy) > astarG[t]) continue;
            if (t == g) {
                int n = 0;
                for (int c = t; c != s; c = astarFrom[c]) n++;
                int[] path = new int[n];
                for (int c = t, k = n - 1; c != s; c = astarFrom[c]) path[k--] = c;
                return path;
            }
            if (++expanded > maxExpand) return null;
            for (int q = 0; q < 8; q++) {
                int dx = q < 4 ? (q == 0 ? 1 : q == 1 ? -1 : 0) : (q == 4 || q == 6 ? 1 : -1);
                int dy = q < 4 ? (q == 2 ? 1 : q == 3 ? -1 : 0) : (q < 6 ? 1 : -1);
                int nx = tx0 + dx, ny = ty0 + dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int nb = ny * w + nx;
                if (solid[nb] && nb != g) continue;
                // No cutting corners past a wall.
                if (dx != 0 && dy != 0 && (solid[ty0 * w + nx] || solid[ny * w + tx0])) continue;
                int ng = astarG[t] + walkCost[nb] * (dx != 0 && dy != 0 ? 14 : 10);
                if (astarStamp[nb] == run && ng >= astarG[nb]) continue;
                astarStamp[nb] = run;
                astarG[nb] = ng;
                astarFrom[nb] = t;
                if (size == astarHeap.length) astarHeap = Arrays.copyOf(astarHeap, size * 2);
                astarHeap[size] = ((long) (ng + heur(nx, ny, gx, gy)) << 32) | nb;
                siftUp(size++);
            }
        }
        return null;
    }

    private static int heur(int x, int y, int gx, int gy) {
        int dx = Math.abs(x - gx), dy = Math.abs(y - gy);
        return 10 * Math.max(dx, dy) + 4 * Math.min(dx, dy);
    }

    private void siftUp(int i) {
        long v = astarHeap[i];
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (astarHeap[p] <= v) break;
            astarHeap[i] = astarHeap[p];
            i = p;
        }
        astarHeap[i] = v;
    }

    private void siftDown(int size) {
        if (size == 0) return;
        long v = astarHeap[0];
        int i = 0;
        while (true) {
            int c = 2 * i + 1;
            if (c >= size) break;
            if (c + 1 < size && astarHeap[c + 1] < astarHeap[c]) c++;
            if (astarHeap[c] >= v) break;
            astarHeap[i] = astarHeap[c];
            i = c;
        }
        astarHeap[i] = v;
    }

    /** Distance field (in tiles) to the nearest of the given points. */
    void fieldFromPoints(int[] dist, float[] xs, float[] ys, int n) {
        Arrays.fill(dist, FAR);
        int tail = 0;
        for (int i = 0; i < n; i++) {
            int t = tileIndex(xs[i], ys[i]);
            if (dist[t] != 0) {
                dist[t] = 0;
                queue[tail++] = t;
            }
        }
        spread(dist, tail, FAR);
    }

    private void bfs(int[] dist, List<Entity> entities, boolean zombies, int limit) {
        Arrays.fill(dist, FAR);
        int tail = 0;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.dead || e.isZombie() != zombies) continue;
            int t = tileIndex(e.x, e.y);
            if (dist[t] != 0) {
                dist[t] = 0;
                queue[tail++] = t;
            }
        }
        // People hiding indoors can still be smelled: their building's door counts as a source.
        if (!zombies)
            for (int i = 0, n = buildings.size(); i < n; i++) {
                Building b = buildings.get(i);
                if (b.occupants.isEmpty()) continue;
                int t = tileIndex(b.doorX, b.doorY);
                if (dist[t] != 0) {
                    dist[t] = 0;
                    queue[tail++] = t;
                }
            }
        spread(dist, tail, limit);
    }

    private void spread(int[] dist, int tail, int limit) {
        int head = 0;
        while (head < tail) {
            int t = queue[head++];
            int tx = t % w, ty = t / w, nd = dist[t] + 1;
            if (nd > limit) continue;
            if (tx > 0) tail = visit(dist, t - 1, nd, tail);
            if (tx < w - 1) tail = visit(dist, t + 1, nd, tail);
            if (ty > 0) tail = visit(dist, t - w, nd, tail);
            if (ty < h - 1) tail = visit(dist, t + w, nd, tail);
        }
    }

    private int visit(int[] dist, int n, int nd, int tail) {
        if (solid[n] || dist[n] <= nd) return tail;
        dist[n] = nd;
        queue[tail++] = n;
        return tail;
    }
}
