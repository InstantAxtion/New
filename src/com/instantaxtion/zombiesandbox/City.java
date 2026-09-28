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
            STATUE = 7, LOT = 8, BASE = 9, FENCE = 10, PUMP = 11, RUBBLE = 12, RAIL = 13;

    static final int OFFICE = 0, HOUSE = 1, WAREHOUSE = 2, STATION = 3, BARRACKS = 4, TOWER = 5, HOSPITAL = 6,
            SHOP = 7, CHURCH = 8, SCHOOL = 9, FIRE_STATION = 10, MARKET = 11, KIOSK = 12, SPIRE = 13, CRYPT = 14,
            APARTMENT = 15, GARAGE = 16, PHARMACY = 17, TRAIN_STATION = 18;
    static final int KIND_COUNT = 19;
    private static final int[] APARTMENT_WALLS = {0xFFC9B8A0, 0xFFB5A08A, 0xFFD4C8B8, 0xFFA89484, 0xFFBFB0C0};
    private static final String[] PHARMACIES = {"CityCare Pharmacy", "Main Street Drugs", "HealthPlus", "Corner Pharmacy"};
    /** Ground decorations drawn into the map: {kind, x0, y0, x1, y1, variant} in world units. */
    static final int D_COURT = 0, D_FIELD = 1, D_PLAYGROUND = 2, D_GARDEN = 3, D_GRAVE = 4, D_SKATE = 5,
            D_CANOPY = 6;
    private static final int[] SHOP_ROOFS = {0xFF8C5A4A, 0xFF5A6E8C, 0xFF7E7A5C, 0xFF6E5A7E, 0xFF8A6A3E, 0xFF4F6F66};
    private static final int[] SHOP_WALLS = {0xFFE0C9A6, 0xFFB9C6D2, 0xFFD8B8A8, 0xFFC9D6B8, 0xFFE8DCC8, 0xFFB8A8C8};
    static final int FACILITY_POLICE = 0, FACILITY_BASE = 1, FACILITY_HOSPITAL = 2, FACILITY_FIRE = 3;
    private static final String[] SAINTS = {"St. Mary's", "St. Luke's", "St. Peter's", "Grace", "St. Anne's",
            "Trinity", "St. Mark's", "Holy Cross"};
    private static final String[] SCHOOLS = {"Lincoln High", "Westside Elementary", "Roosevelt Middle School",
            "Jefferson High", "Oakwood Academy", "Hamilton Elementary"};
    private static final String[] MARKETS = {"FreshMart", "ValueFoods", "Corner Grocer", "SuperSaver", "GreenBasket",
            "MegaMart"};
    private static final String[] BASE_NAMES = {"Fort Mercer", "Camp Redstone", "Fort Kessler", "Camp Hollow",
            "Fort Whitmore", "Camp Ironwood"};

    /** A police station or military base: where reinforcements come from and a preferred safe zone. */
    static final class Facility {
        final int kind;
        final float x, y, r, gateX, gateY;
        String name;
        int[] field;
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
    private static final int[] HOUSE_ROOFS = {0xFF8E3B2E, 0xFF6E4A3A, 0xFF4E5560, 0xFF7A5A48, 0xFF9A5B3C, 0xFF5A3D35};
    private static final int[] HOUSE_WALLS = {0xFFD8CBB0, 0xFFC9B79C, 0xFFE0D6C4, 0xFFB8A48A, 0xFFA7B4BE, 0xFFC7A9A0};
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
        final List<Entity> occupants = new ArrayList<Entity>();
        /** Blast damage: a building knocked down to 0 collapses into rubble. */
        float hp, maxHp;
        boolean collapsed;
        /** Churches, schools and supermarkets have names (they can become safe zones). */
        String name;
        /** Supermarkets: rounds of ammo left on the hunting shelf. */
        int stock;
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
    /** The town's name, the same every time for a given city code. */
    final String name;
    private static final String[] NAME_START = {"Ash", "Maple", "Oak", "Stone", "Clay", "Wolf", "Fair", "Green", "North",
            "Silver", "Iron", "Elder", "Hollow", "Red", "Kings", "Bright", "Glen", "Hazel", "Thorn", "Mill"};
    private static final String[] NAME_END = {"ford", "field", "ville", "wood", "ton", "bury", "ridge", "haven", "dale",
            "stead", "wick", "gate", "hurst", "moor"};
    final int w, h;
    final byte[] tiles;
    final boolean[] solid;
    final boolean[] opaque;
    final int[] humanDist, zombieDist;
    private final int[] queue;
    final Random rnd;
    final Bitmap bitmap;
    float maxHeight;

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

    private static final String[] STREETS = {"Main", "Oak", "Pine", "Elm", "Maple", "Cedar", "Lake", "Hill", "Park",
            "Church", "Market", "Mill", "King", "Queen", "Walnut", "Spruce", "Birch", "Chestnut", "Harbor", "Union",
            "Rose", "Ash", "Willow", "Station", "River", "Bridge", "High", "Garden", "Orchard", "Victoria", "Albert",
            "Greene", "Water", "Grove", "Meadow", "Forest", "Bay", "Summit", "Liberty", "Franklin", "Madison",
            "Jackson", "Lincoln", "Hawthorn", "Laurel", "Poplar", "Sycamore", "Holly", "Juniper", "Linden"};
    private static final String[] MAIN_SUFFIX = {"Ave", "Blvd", "Rd", "Pkwy"};
    private static final String[] LOCAL_SUFFIX = {"St", "St", "St", "Ln", "Way", "Pl", "Ct", "Dr"};

    City(CityConfig cfg) {
        this(cfg, 1f);
    }

    /** A city; with a detail below 1 the map bitmap is drawn smaller (for the New Game preview). */
    City(CityConfig cfg, float detail) {
        this.cfg = cfg;
        w = h = cfg.tiles();
        rnd = new Random(cfg.seed);
        Random nameRnd = new Random(cfg.seed * 31 + 7);
        name = NAME_START[nameRnd.nextInt(NAME_START.length)] + NAME_END[nameRnd.nextInt(NAME_END.length)];
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
        for (int i = 0; i < tiles.length; i++) {
            byte t = tiles[i];
            solid[i] = t == BUILDING || t == TREE || t == CAR || t == STATUE || t == FENCE || t == PUMP;
            opaque[i] = t == BUILDING;
        }
        for (int[] l : buildingLots) {
            float height = l[7] * FLOOR + 3;
            maxHeight = Math.max(maxHeight, height);
            Building b = new Building(l[0] * T, l[1] * T, (l[0] + l[2]) * T, (l[1] + l[3]) * T, height,
                    l[4], l[8], l[5], l[6]);
            int k = l[6];
            if (k == OFFICE || k == HOUSE || k == WAREHOUSE || k == SHOP || k == CHURCH || k == SCHOOL || k == MARKET
                    || k == KIOSK || k == APARTMENT || k == GARAGE || k == PHARMACY || k == TRAIN_STATION) placeDoor(b, l);
            if (k == APARTMENT) b.capacity = Math.max(8, Math.min(30, l[2] * l[3]));
            // Bigger and taller buildings take more to bring down.
            b.maxHp = b.hp = 150 + l[2] * l[3] * 22 + l[7] * 60;
            Random nr = new Random(l[5]);
            if (k == CHURCH) b.name = SAINTS[nr.nextInt(SAINTS.length)] + " Church";
            else if (k == SCHOOL) b.name = SCHOOLS[nr.nextInt(SCHOOLS.length)];
            else if (k == TRAIN_STATION) b.name = "Central Station";
            else if (k == MARKET) {
                b.name = MARKETS[nr.nextInt(MARKETS.length)];
                b.stock = 240;
            } else if (k == PHARMACY) {
                b.name = PHARMACIES[nr.nextInt(PHARMACIES.length)];
                b.stock = 60;
            }
            for (int j = l[1]; j < l[1] + l[3]; j++)
                for (int i = l[0]; i < l[0] + l[2]; i++) buildingAt[j * w + i] = buildings.size();
            buildings.add(b);
        }
        Arrays.fill(humanDist, FAR);
        Arrays.fill(zombieDist, FAR);
        for (Facility f : facilities) {
            f.field = new int[w * h];
            fieldFromPoints(f.field, new float[]{f.x}, new float[]{f.y}, 1);
        }
        // The big map uses 16-bit colour to keep memory down.
        bitmap = Bitmap.createBitmap((int) (w * T * detail), (int) (h * T * detail),
                w * detail > 140 ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (detail != 1f) canvas.scale(detail, detail);
        render(canvas);
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

    /**
     * Splits a rectangle of city with a street, then splits each side again, until the pieces are block
     * sized. Big pieces get main roads; the two sides are split independently, so streets usually don't
     * line up across a main road: offset crossings and T-junctions instead of a grid. A hint lets the
     * second side sometimes continue a street from the first. Leaves become blocks.
     */
    private int split(int x0, int y0, int x1, int y1, int depth, int hintX, int hintY) {
        int bw = x1 - x0, bh = y1 - y0;
        float cx = (x0 + x1) / 2f - w / 2f, cy = (y0 + y1) / 2f - h / 2f;
        float d = (float) Math.sqrt(cx * cx + cy * cy) / (w * 0.5f);
        boolean rural = cfg.density() == 0 && cfg.style() == CityConfig.STYLE_HOUSES;
        int maxLeaf = d < 0.3f ? 11 + rnd.nextInt(4) : d < 0.65f ? 13 + rnd.nextInt(5) : 15 + rnd.nextInt(7);
        if (rural) maxLeaf += 6;
        if (cfg.density() == 2) maxLeaf -= 2;
        if (bw <= maxLeaf && bh <= maxLeaf) {
            blocks.add(new int[]{x0, y0, x1, y1});
            return -1;
        }
        boolean big = Math.max(bw, bh) > (w > 110 ? 42 : 34) && depth < 3 && !rural;
        int roadW = big ? 5 : 3, minSide = 6;
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
        float align = cfg.layout() == 0 ? 0.75f : cfg.layout() == 1 ? 0.45f : 0.2f;
        int hint = vertical ? hintX : hintY;
        if (hint >= lo && hint <= hi && rnd.nextFloat() < align) at = hint;
        Street st = vertical ? new Street(at, y0, at + roadW, y1, true, big) : new Street(x0, at, x1, at + roadW, false, big);
        carve(st);
        int nextHint;
        if (vertical) {
            nextHint = split(x0, y0, at, y1, depth + 1, -1, hintY);
            split(at + roadW, y0, x1, y1, depth + 1, -1, nextHint >= 0 ? nextHint : hintY);
        } else {
            nextHint = split(x0, y0, x1, at, depth + 1, hintX, -1);
            split(x0, at + roadW, x1, y1, depth + 1, nextHint >= 0 ? nextHint : hintX, -1);
        }
        return at;
    }

    private void generate() {
        origin = 0;
        Arrays.fill(tiles, SIDEWALK);
        industryAngle = rnd.nextFloat() * (float) Math.PI * 2;
        // A ring road around the edge of town.
        carve(new Street(0, 0, w, 3, false, false));
        carve(new Street(0, h - 3, w, h, false, false));
        carve(new Street(0, 0, 3, h, true, false));
        carve(new Street(w - 3, 0, w, h, true, false));
        // The railway cuts straight across the middle third of the map; the streets are laid out on each side.
        if (cfg.hasRail()) {
            railY0 = h / 3 + rnd.nextInt(Math.max(1, h / 3));
            railRows = 3;
            split(3, 3, w - 3, railY0, 0, -1, -1);
            split(3, railY0 + railRows, w - 3, h - 3, 0, -1, -1);
            for (int y = railY0; y < railY0 + railRows; y++)
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    // Streets that reach the line from both sides get a level crossing.
                    boolean above = roadDir[(railY0 - 1) * w + x] == 1, below = roadDir[(railY0 + railRows) * w + x] == 1;
                    if ((above && below) || x < 3 || x >= w - 3) {
                        tiles[i] = ROAD;
                        roadDir[i] = 1;
                    } else {
                        tiles[i] = RAIL;
                        roadDir[i] = 0;
                    }
                }
        } else {
            split(3, 3, w - 3, h - 3, 0, -1, -1);
        }
        // Tree-lined medians down the main roads, open at the junctions.
        for (Street st : streets) {
            if (st.width != 5) continue;
            if (rnd.nextFloat() < 0.35f) continue;
            int n = st.vertical ? st.y1 - st.y0 : st.x1 - st.x0;
            for (int k = 0; k < n; k++) {
                boolean clear = true;
                for (int a = 0; a < 5 && clear; a++) {
                    for (int e = -1; e <= 1 && clear; e++) {
                        int x = st.vertical ? st.x0 + a : st.x0 + k + e, y = st.vertical ? st.y0 + k + e : st.y0 + a;
                        if (x < 0 || y < 0 || x >= w || y >= h || roadDir[y * w + x] != (st.vertical ? 1 : 2)) clear = false;
                    }
                }
                if (!clear) continue;
                int x = st.vertical ? st.x0 + 2 : st.x0 + k, y = st.vertical ? st.y0 + k : st.y0 + 2;
                tiles[y * w + x] = k % 2 == 0 ? TREE : GRASS;
            }
        }

        // Pick blocks for the military base and police stations.
        boolean[] used = new boolean[blocks.size()];
        if (cfg.militaryBase()) {
            int best = -1, bestArea = 0;
            for (int k = 0; k < blocks.size(); k++) {
                int[] b = blocks.get(k);
                int bw = b[2] - b[0] - 2, bh = b[3] - b[1] - 2;
                if (bw < 9 || bh < 9) continue;
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
                    if (f.kind == FACILITY_POLICE && Math.hypot(f.x - cx, f.y - cy) < w * T / 3f) near = true;
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

        for (int k = 0; k < blocks.size(); k++) {
            if (used[k]) continue;
            int[] b = blocks.get(k);
            int x0 = b[0], y0 = b[1], x1 = b[2], y1 = b[3];
            if (x1 - x0 < 1 || y1 - y0 < 1) continue;
            fill(x0, y0, x1 - x0, y1 - y0, SIDEWALK);
            int ix = x0 + 1, iy = y0 + 1, iw = x1 - x0 - 2, ih = y1 - y0 - 2;
            if (iw < 2 || ih < 2) continue;
            block(ix, iy, iw, ih);
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
                tiles[y * w + x] = CAR;
            }
        }

        // Street names: main roads get avenues and boulevards, the rest streets, lanes and courts.
        List<String> names = new ArrayList<String>(Arrays.asList(STREETS));
        java.util.Collections.shuffle(names, rnd);
        int next = 0;
        for (Street st : streets) {
            String base = names.get(next++ % names.size());
            int len = st.vertical ? st.y1 - st.y0 : st.x1 - st.x0;
            if (st.x0 == 0 && st.y0 == 0 || st.x1 == w || st.y1 == h) st.name = "Ring Rd";
            else if (st.main || len > 40) st.name = base + " " + MAIN_SUFFIX[rnd.nextInt(MAIN_SUFFIX.length)];
            else st.name = base + " " + LOCAL_SUFFIX[rnd.nextInt(LOCAL_SUFFIX.length)];
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
            for (int row = 0; row < 2 && ay + 1 + row * 3 + 2 <= ay + ah / 2; row++) {
                int by = ay + 1 + row * 3;
                int mid = gx - 1;
                if (mid - left >= 3) addFacilityLot(left, by, mid - left, 2, BARRACKS, 1);
                if (right - (gx + 4) >= 3) addFacilityLot(gx + 4, by, right - (gx + 4), 2, BARRACKS, 1);
            }
        } else {
            // Narrow base: long barracks down both sides of the parade ground.
            int len = Math.max(3, ah / 2 - 1);
            addFacilityLot(ax + 1, ay + 2, 2, len, BARRACKS, 1);
            addFacilityLot(ax + aw - 3, ay + 2, 2, len, BARRACKS, 1);
        }
        addFacilityLot(ax, ay + ah - 1, 1, 1, TOWER, 3);
        addFacilityLot(ax + aw - 1, ay, 1, 1, TOWER, 3);
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
                BASE_NAMES[rnd.nextInt(BASE_NAMES.length)]);
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
    private void serviceBuilding(int x, int y, int bw, int bh, int kind, int number) {
        fill(x, y, bw, bh, LOT);
        boolean wide = bw >= bh;
        int sw = wide ? Math.max(3, bw / 2 - 1) : bw - 2, sh = wide ? bh - 2 : Math.max(3, bh / 2 - 1);
        addFacilityLot(x + 1, y + 1, sw, sh, kind, kind == HOSPITAL ? 4 : kind == FIRE_STATION ? 2 : 3);
        int lx = wide ? x + sw + 2 : x, ly = wide ? y : y + sh + 2;
        int lw = wide ? bw - sw - 2 : bw, lh = wide ? bh : bh - sh - 2;
        for (int j = ly + 1; j < ly + lh - 1; j += 3)
            for (int i = lx + 1; i < lx + lw - 1; i++)
                if (rnd.nextFloat() < (kind == HOSPITAL ? 0.35f : kind == FIRE_STATION ? 0.45f : 0.6f)) {
                    tiles[j * w + i] = CAR;
                    carKind[j * w + i] = (byte) (kind == HOSPITAL ? 3 : kind == FIRE_STATION ? 4 : 1);
                }
        float cx = (lx + lw / 2f) * T, cy = (ly + lh / 2f) * T;
        float[] c = findWalkable(cx, cy);
        if (c == null) c = new float[]{cx, cy};
        if (kind == HOSPITAL) {
            facilities.add(new Facility(FACILITY_HOSPITAL, c[0], c[1], 64, c[0], c[1], "City Hospital"));
            return;
        }
        if (kind == FIRE_STATION) {
            int n = 1;
            for (Facility f : facilities) if (f.kind == FACILITY_FIRE) n++;
            facilities.add(new Facility(FACILITY_FIRE, c[0], c[1], 48, c[0], c[1], "Fire Station " + n));
            return;
        }
        Facility station = new Facility(FACILITY_POLICE, c[0], c[1], 64, c[0], c[1], "Precinct " + number);
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
            roof = SHOP_ROOFS[rnd.nextInt(SHOP_ROOFS.length)];
            wall = SHOP_WALLS[rnd.nextInt(SHOP_WALLS.length)];
        } else if (kind == TOWER) {
            roof = 0xFF4B5536;
            wall = 0xFF6B7350;
        } else {
            roof = 0xFF5B6B3A;
            wall = 0xFF7C8456;
        }
        buildingLots.add(new int[]{x, y, lw, lh, roof, rnd.nextInt(100000), kind, floors, wall});
    }

    /** Fills the inside of one city block according to the map settings. */
    private void block(int x, int y, int bw, int bh) {
        float park = PARK_CHANCE[cfg.parks()];
        int style = cfg.style();
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
            boolean industrialZone = d > 0.55f && Math.abs(ang) < 0.7f;
            boolean mainRoad = onMainRoad(x - 1, y - 1, x + bw + 1, y + bh + 1);
            float r = rnd.nextFloat();
            if (style == CityConfig.STYLE_MIXED) {
                if (industrialZone && r < 0.8f) style = CityConfig.STYLE_WAREHOUSES;
                else if (d < 0.32f) style = CityConfig.STYLE_OFFICES;
                else if (d < 0.55f) style = r < 0.55f ? CityConfig.STYLE_OFFICES : CityConfig.STYLE_HOUSES;
                else style = r < 0.12f ? CityConfig.STYLE_OFFICES : CityConfig.STYLE_HOUSES;
            } else if (style == CityConfig.STYLE_HOUSES && d < 0.2f && cfg.density() > 0) {
                // Even a town of houses has a little centre.
                style = CityConfig.STYLE_OFFICES;
            } else if (style == CityConfig.STYLE_OFFICES && industrialZone && r < 0.35f) {
                style = CityConfig.STYLE_WAREHOUSES;
            }
            // Shops line the main roads; side streets are mostly homes and offices.
            int shopChance = mainRoad ? cfg.shopShare() + 20 : cfg.shopShare() / 3;
            if (style == CityConfig.STYLE_HOUSES && !mainRoad) shopChance = cfg.shopShare() / 6;
            if (rnd.nextInt(100) < shopChance && bw >= 5 && bh >= 5 && style != CityConfig.STYLE_WAREHOUSES) shops(x, y, bw, bh);
            else if (style == CityConfig.STYLE_HOUSES && rnd.nextInt(100) < cfg.buildingMix()[0] / 2 && bw >= 5 && bh >= 5) {
                // An apartment block on the edge of the suburbs.
                fill(x, y, bw, bh, GRASS);
                addLot(x + 1, y + 1, bw - 2, bh - 2, APARTMENT);
            } else if (style == CityConfig.STYLE_HOUSES) houses(x, y, bw, bh);
            else if (style == CityConfig.STYLE_WAREHOUSES) warehouses(x, y, bw, bh);
            else if (cfg.density() == 0 && bw >= 5 && bh >= 5) {
                fill(x, y, bw, bh, GRASS);
                lots(x + 1, y + 1, bw - 2, bh - 2, 0);
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
        if (depth < maxDepth && lw >= 7 && lw >= lh && rnd.nextFloat() < 0.75f) {
            int cut = 3 + rnd.nextInt(lw - 5);
            lots(x, y, cut, lh, depth + 1);
            lots(x + cut + 1, y, lw - cut - 1, lh, depth + 1);
            return;
        }
        if (depth < maxDepth && lh >= 7 && rnd.nextFloat() < 0.75f) {
            int cut = 3 + rnd.nextInt(lh - 5);
            lots(x, y, lw, cut, depth + 1);
            lots(x, y + cut + 1, lw, lh - cut - 1, depth + 1);
            return;
        }
        if (lw < 2 || lh < 2) return;
        addLot(x, y, lw, lh, officeKind(lw, lh));
    }

    /** Most lots are offices; some maps mix in apartment blocks, parking garages and pharmacies. */
    private int officeKind(int lw, int lh) {
        int[] mix = cfg.buildingMix();
        int r = rnd.nextInt(100);
        if (r < mix[0] && lw >= 3 && lh >= 3) return APARTMENT;
        if ((r -= mix[0]) < mix[1] && lw >= 4 && lh >= 4) return GARAGE;
        if ((r -= mix[1]) < mix[2] && lw <= 4 && lh <= 4) return PHARMACY;
        return OFFICE;
    }

    private void houses(int x, int y, int pw, int ph) {
        if ((pw >= 11 || ph >= 11) && Math.min(pw, ph) >= 7 && rnd.nextFloat() < 0.7f) {
            culDeSac(x, y, pw, ph);
            return;
        }
        fill(x, y, pw, ph, GRASS);
        for (int cy = y; cy + 2 <= y + ph; cy += 4)
            for (int cx = x; cx + 2 <= x + pw; cx += 4) {
                int hw = Math.min(2 + rnd.nextInt(2), x + pw - cx);
                int hh = Math.min(2 + rnd.nextInt(2), y + ph - cy);
                if (hw >= 2 && hh >= 2 && rnd.nextFloat() > 0.1f) addLot(cx, cy, hw, hh, HOUSE);
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
        for (int k = 1; k + 2 <= (vertical ? ph : pw) - 1; k += 3)
            for (int side = 0; side < 2; side++) {
                int hw = 2, hd = 2 + rnd.nextInt(2);
                int along = (vertical ? y : x) + k;
                int across = side == 0 ? mid - 1 - hd : mid + 3;
                int lx = vertical ? across : along, ly = vertical ? along : across;
                placeHouse(lx, ly, vertical ? hd : hw, vertical ? hw : hd, x, y, pw, ph);
            }
        for (int cy = y; cy + 2 <= y + ph; cy += 3)
            for (int cx = x; cx + 2 <= x + pw; cx += 3) placeHouse(cx, cy, 2, 2, x, y, pw, ph);
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
        int sw = Math.min(bw - 2, Math.max(6, bw * 2 / 3)), sh = Math.max(2, Math.min(4, bh - 3));
        int sx = x + (bw - sw) / 2, sy = railBelow ? y + 1 : y + bh - 1 - sh;
        addFacilityLot(sx, sy, sw, sh, TRAIN_STATION, 2);
        // Platform canopy along the tracks.
        int py = railBelow ? y + bh - 1 : y;
        addDecor(D_CANOPY, x * T + 4, py * T + 2, (x + bw) * T - 4, py * T + T - 2, 1);
        openAreas.add(new float[]{(x + bw / 2f) * T, (y + bh / 2f) * T, 1});
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

    private void addLot(int x, int y, int lw, int lh, int kind) {
        fill(x, y, lw, lh, BUILDING);
        int floors, roof, wall;
        int height = cfg.height();
        if (kind == HOUSE) {
            floors = 1 + rnd.nextInt(2);
            roof = HOUSE_ROOFS[rnd.nextInt(HOUSE_ROOFS.length)];
            wall = HOUSE_WALLS[rnd.nextInt(HOUSE_WALLS.length)];
        } else if (kind == WAREHOUSE) {
            floors = 1 + rnd.nextInt(2) + (height >= 2 ? 1 : 0);
            roof = WAREHOUSE_ROOFS[rnd.nextInt(WAREHOUSE_ROOFS.length)];
            wall = WAREHOUSE_WALLS[rnd.nextInt(WAREHOUSE_WALLS.length)];
        } else if (kind == APARTMENT) {
            floors = 3 + rnd.nextInt(4) + height * 2;
            roof = darken(ROOFS[rnd.nextInt(ROOFS.length)], 0.9f);
            wall = APARTMENT_WALLS[rnd.nextInt(APARTMENT_WALLS.length)];
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
            roof = ROOFS[rnd.nextInt(ROOFS.length)];
            wall = WALLS[rnd.nextInt(WALLS.length)];
        }
        buildingLots.add(new int[]{x, y, lw, lh, roof, rnd.nextInt(100000), kind, floors, wall});
    }

    private void addDecor(int kind, float x0, float y0, float x1, float y1, int variant) {
        decor.add(new float[]{kind, x0, y0, x1, y1, variant});
    }

    /** A row of small shops around the edge of the block with a service lot behind. */
    private void shops(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, LOT);
        int depth = bh >= 8 ? 3 : 2;
        for (int side = 0; side < 2; side++) {
            int sy = side == 0 ? y : y + bh - depth;
            int i = x;
            while (i < x + bw) {
                int uw = Math.min(2 + rnd.nextInt(2), x + bw - i);
                if (uw >= 2) addFacilityLot(i, sy, uw, depth, SHOP, 1 + (rnd.nextFloat() < 0.3f ? 1 : 0));
                i += uw;
            }
        }
        for (int j = y + depth + 1; j < y + bh - depth - 1; j++)
            for (int i = x + 1; i < x + bw - 1; i++)
                if (rnd.nextFloat() < 0.12f && !hasNeighbor(i, j, CAR)) tiles[j * w + i] = CAR;
    }

    /** A church with a steeple, and a churchyard with graves. */
    private void church(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, GRASS);
        boolean wide = bw >= bh;
        int cw = wide ? Math.min(bw - 3, Math.max(4, bw / 2)) : Math.min(bw - 2, 4);
        int ch = wide ? Math.min(bh - 2, 4) : Math.min(bh - 3, Math.max(4, bh / 2));
        addFacilityLot(x + 1, y + 1, cw, ch, CHURCH, 2);
        if (wide) addFacilityLot(x + 1 + cw, y + 1 + ch / 2, 1, 1, SPIRE, 6);
        else addFacilityLot(x + 1 + cw / 2, y + 1 + ch, 1, 1, SPIRE, 6);
        graves(x, y, bw, bh, 0.5f);
    }

    private void cemetery(int x, int y, int bw, int bh) {
        fill(x, y, bw, bh, GRASS);
        int cx = x + bw / 2, cy = y + bh / 2;
        for (int i = x; i < x + bw; i++) tiles[cy * w + i] = PLAZA;
        for (int j = y; j < y + bh; j++) tiles[j * w + cx] = PLAZA;
        if (bw >= 7 && bh >= 7) addFacilityLot(cx + 1, cy + 1, 2, 2, CRYPT, 1);
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
        addFacilityLot(x + bw - 3, y + 1, 2, 2, KIOSK, 1);
        int cx0 = x + 1, cy0 = y + 1, cx1 = x + bw - 4, cy1 = y + bh - 1;
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
                int courtsX = bw >= 9 ? 2 : 1, courtsY = bh >= 9 ? 2 : 1;
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
                break;
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
        Canvas c = new Canvas(bitmap);
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
        for (Facility f : facilities) fieldFromPoints(f.field, new float[]{f.x}, new float[]{f.y}, 1);
    }

    /** Scorches a burnt-out car into the map. */
    void charTile(int tx, int ty) {
        Canvas c = new Canvas(bitmap);
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

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                byte t = tiles[y * w + x];
                int col;
                switch (t) {
                    case SIDEWALK: col = 0xFF8F8D87; break;
                    case GRASS: case TREE: col = 0xFF4C7837; break;
                    case PLAZA: case STATUE: col = 0xFFB3A487; break;
                    case LOT: col = 0xFF48494D; break;
                    case BUILDING: col = 0xFF8F8D87; break;
                    case BASE: case FENCE: col = 0xFF6A6E5E; break;
                    default: col = 0xFF3A3D43; break;
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
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                byte t = tiles[y * w + x];
                float fx = x * T, fy = y * T;
                if (t == SIDEWALK) {
                    // Paving slabs, a few of them newer or stained.
                    float roll = rnd.nextFloat();
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
                        p.setColor(rnd.nextBoolean() ? 0xFF578A3F : 0xFF426B30);
                        c.drawCircle(fx + rnd.nextFloat() * T, fy + rnd.nextFloat() * T, 0.9f, p);
                    }
                    // Tufts and the odd wildflower.
                    if (rnd.nextFloat() < 0.35f) {
                        p.setColor(0xFF3A6128);
                        float gx = fx + rnd.nextFloat() * T, gy = fy + rnd.nextFloat() * T;
                        c.drawLine(gx, gy, gx - 1, gy - 2, p);
                        c.drawLine(gx, gy, gx + 1, gy - 2, p);
                    }
                    if (rnd.nextFloat() < 0.05f) {
                        int[] flowers = {0xFFF2E86B, 0xFFF2F2F2, 0xFFE87BB0, 0xFFB08AE8};
                        p.setColor(flowers[rnd.nextInt(flowers.length)]);
                        for (int k = 0; k < 3; k++)
                            c.drawCircle(fx + rnd.nextFloat() * T, fy + rnd.nextFloat() * T, 0.8f, p);
                    }
                } else if (t == PLAZA) {
                    p.setColor(0xFFA29376);
                    c.drawLine(fx, fy, fx + T, fy, p);
                    c.drawLine(fx, fy, fx, fy + T, p);
                } else if (t == ROAD || t == CAR) {
                    if (rnd.nextFloat() < 0.25f) {
                        p.setColor(0x22000000);
                        c.drawCircle(fx + rnd.nextFloat() * T, fy + rnd.nextFloat() * T, 1 + rnd.nextFloat() * 3, p);
                    }
                    float roll = rnd.nextFloat();
                    if (roll < 0.03f) {
                        // Patched asphalt.
                        p.setColor(0xFF33363B);
                        float pw = 5 + rnd.nextFloat() * 8, ph = 4 + rnd.nextFloat() * 6;
                        float px = fx + rnd.nextFloat() * (T - pw), py = fy + rnd.nextFloat() * (T - ph);
                        c.drawRect(px, py, px + pw, py + ph, p);
                    } else if (roll < 0.07f) {
                        // Cracks.
                        p.setColor(0xFF2A2C30);
                        float cx0 = fx + rnd.nextFloat() * T, cy0 = fy + rnd.nextFloat() * T;
                        float cx1 = cx0 + rnd.nextFloat() * 8 - 4, cy1 = cy0 + rnd.nextFloat() * 8 - 4;
                        c.drawLine(cx0, cy0, cx1, cy1, p);
                        c.drawLine(cx1, cy1, cx1 + rnd.nextFloat() * 6 - 3, cy1 + rnd.nextFloat() * 6 - 3, p);
                    } else if (roll < 0.085f && t == ROAD) {
                        // Manhole cover.
                        float mx = fx + 4 + rnd.nextFloat() * (T - 8), my = fy + 4 + rnd.nextFloat() * (T - 8);
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

        drawRoadMarkings(c, p);
        drawRail(c, p);
        drawStreetFurniture(c, p);
        drawParkingLines(c, p);
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

        for (float[] d : decor) drawDecor(c, p, d);

        // Building shadows (longer for taller buildings), then roofs. The roof art is drawn at the
        // footprint and GameView lifts it to the building's height.
        p.setColor(0x50000000);
        Path shadow = new Path();
        for (Building b : buildings) {
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
        for (int[] b : buildingLots) drawBuilding(c, p, b);

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

        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
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

        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (tiles[y * w + x] == TREE) {
                    float cx = x * T + T / 2f, cy = y * T + T / 2f;
                    float r = 7.5f + rnd.nextFloat() * 2f;
                    p.setColor(0x50000000);
                    c.drawCircle(cx + TREE_HEIGHT * 0.28f, cy + TREE_HEIGHT * 0.38f, r, p);
                    p.setColor(0xFF4A3524);
                    c.drawCircle(cx, cy, 2f, p);
                    trees.add(new float[]{cx, cy, r});
                }
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
    private void drawDecor(Canvas c, Paint p, float[] d) {
        int kind = (int) d[0], variant = (int) d[5];
        float x0 = d[1], y0 = d[2], x1 = d[3], y1 = d[4], cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        float dw = x1 - x0, dh = y1 - y0;
        boolean wide = dw >= dh;
        p.setStyle(Paint.Style.FILL);
        switch (kind) {
            case D_COURT: {
                p.setColor(variant == 0 ? 0xFFB0663A : 0xFF3F7F5A);
                c.drawRect(x0, y0, x1, y1, p);
                p.setColor(variant == 0 ? 0xFFC47A4A : 0xFF4E946A);
                c.drawRect(x0 + 4, y0 + 4, x1 - 4, y1 - 4, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.2f);
                p.setColor(0xDDFFFFFF);
                c.drawRect(x0 + 4, y0 + 4, x1 - 4, y1 - 4, p);
                if (wide) c.drawLine(cx, y0 + 4, cx, y1 - 4, p);
                else c.drawLine(x0 + 4, cy, x1 - 4, cy, p);
                if (variant == 0) {
                    c.drawCircle(cx, cy, Math.min(dw, dh) * 0.15f, p);
                    float r = Math.min(dw, dh) * 0.25f;
                    if (wide) {
                        c.drawCircle(x0 + 4, cy, r, p);
                        c.drawCircle(x1 - 4, cy, r, p);
                    } else {
                        c.drawCircle(cx, y0 + 4, r, p);
                        c.drawCircle(cx, y1 - 4, r, p);
                    }
                } else {
                    p.setStrokeWidth(2f);
                    p.setColor(0xFF2A2A2A);
                    if (wide) c.drawLine(cx, y0 + 1, cx, y1 - 1, p);
                    else c.drawLine(x0 + 1, cy, x1 - 1, cy, p);
                }
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case D_FIELD: {
                int stripes = 8;
                for (int i = 0; i < stripes; i++) {
                    p.setColor(i % 2 == 0 ? 0xFF4F8A3A : 0xFF5A9644);
                    if (wide) c.drawRect(x0 + dw * i / stripes, y0, x0 + dw * (i + 1) / stripes, y1, p);
                    else c.drawRect(x0, y0 + dh * i / stripes, x1, y0 + dh * (i + 1) / stripes, p);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.4f);
                p.setColor(0xEEFFFFFF);
                c.drawRect(x0 + 3, y0 + 3, x1 - 3, y1 - 3, p);
                c.drawCircle(cx, cy, Math.min(dw, dh) * 0.14f, p);
                float gb = Math.min(dw, dh) * 0.3f, gd = Math.max(dw, dh) * 0.12f;
                if (wide) {
                    c.drawLine(cx, y0 + 3, cx, y1 - 3, p);
                    c.drawRect(x0 + 3, cy - gb, x0 + 3 + gd, cy + gb, p);
                    c.drawRect(x1 - 3 - gd, cy - gb, x1 - 3, cy + gb, p);
                } else {
                    c.drawLine(x0 + 3, cy, x1 - 3, cy, p);
                    c.drawRect(cx - gb, y0 + 3, cx + gb, y0 + 3 + gd, p);
                    c.drawRect(cx - gb, y1 - 3 - gd, cx + gb, y1 - 3, p);
                }
                p.setStyle(Paint.Style.FILL);
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
        for (int x = 0; x < w; x++) {
            float fx = x * T;
            boolean crossing = tiles[railY0 * w + x] == ROAD;
            if (!crossing) {
                p.setColor(0xFF6E665C);
                c.drawRect(fx, y0 + 6, fx + T, y1 - 6, p);
                for (int k = 0; k < 10; k++) {
                    p.setColor(rnd.nextBoolean() ? 0xFF7E766A : 0xFF5E574E);
                    c.drawCircle(fx + rnd.nextFloat() * T, y0 + 6 + rnd.nextFloat() * (y1 - y0 - 12), 0.8f, p);
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
        // Crossing barriers (red and white poles) at each road.
        for (int x = 0; x < w; x++) {
            if (tiles[railY0 * w + x] != ROAD) continue;
            float x0 = x * T, x1 = x0 + T;
            for (float bx = x0; bx < x1; bx += 4) {
                p.setColor(((int) (bx / 4)) % 2 == 0 ? 0xFFD83A3A : 0xFFF2F2F2);
                c.drawRect(bx, y0 + 1, bx + 4, y0 + 2.5f, p);
                c.drawRect(bx, y1 - 2.5f, bx + 4, y1 - 1, p);
            }
        }
    }

    /** Benches, bins, fire hydrants, bus stops and traffic lights along the pavements. */
    private void drawStreetFurniture(Canvas c, Paint p) {
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++) {
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
                float roll = rnd.nextFloat();
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
                if (st.width == 5) {
                    p.setColor(0xCCE8E8E8);
                    if (st.vertical) {
                        c.drawLine((st.x0 + 1) * T, y * T + 4, (st.x0 + 1) * T, y * T + 10, p);
                        c.drawLine((st.x0 + 4) * T, y * T + 4, (st.x0 + 4) * T, y * T + 10, p);
                    } else {
                        c.drawLine(x * T + 4, (st.y0 + 1) * T, x * T + 10, (st.y0 + 1) * T, p);
                        c.drawLine(x * T + 4, (st.y0 + 4) * T, x * T + 10, (st.y0 + 4) * T, p);
                    }
                } else if (st.width == 3 && !"Ring Rd".equals(st.name)) {
                    p.setColor(0xFFD9B43A);
                    if (st.vertical) c.drawLine((st.x0 + 1.5f) * T, y * T + 3, (st.x0 + 1.5f) * T, y * T + 11, p);
                    else c.drawLine(x * T + 3, (st.y0 + 1.5f) * T, x * T + 11, (st.y0 + 1.5f) * T, p);
                }
            }
            // Zebra crossings where the street meets a junction.
            p.setColor(0xCCE8E8E8);
            for (int k = 1; k < n; k++) {
                int ax = st.vertical ? st.x0 : st.x0 + k - 1, ay = st.vertical ? st.y0 + k - 1 : st.y0;
                int bx = st.vertical ? st.x0 : st.x0 + k, by = st.vertical ? st.y0 + k : st.y0;
                boolean ja = roadDir[ay * w + ax] == 3, jb = roadDir[by * w + bx] == 3;
                if (ja == jb) continue;
                // The crossing sits on the non-junction tile, against the junction.
                int cx = ja ? bx : ax, cy = ja ? by : ay;
                if (!paved(cx, cy)) continue;
                for (int o = 0; o < st.width; o++) {
                    int tx = st.vertical ? st.x0 + o : cx, ty = st.vertical ? cy : st.y0 + o;
                    if (!paved(tx, ty)) continue;
                    for (float q = 2; q + 2 <= T - 1; q += 5) {
                        if (st.vertical) {
                            float yy = ja ? ty * T + 2 : ty * T + T - 9;
                            c.drawRect(tx * T + q, yy, tx * T + q + 2.5f, yy + 7, p);
                        } else {
                            float xx = ja ? tx * T + 2 : tx * T + T - 9;
                            c.drawRect(xx, ty * T + q, xx + 7, ty * T + q + 2.5f, p);
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
            p.setFakeBoldText(true);
            c.drawText("POLICE", (x0 + x1) / 2, (y0 + y1) / 2 + 3.5f, p);
            p.setFakeBoldText(false);
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
            String label = kind == FIRE_STATION ? "FIRE" : kind == MARKET ? "MARKET" : kind == SCHOOL ? "SCHOOL" : "GAS";
            p.setColor(kind == FIRE_STATION ? 0xFFFFFFFF : kind == KIOSK ? 0xFFD83A3A : 0xFF3A3A3A);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.min(10f, bw / (label.length() * 0.75f)));
            p.setFakeBoldText(true);
            c.drawText(label, (x0 + x1) / 2, (y0 + y1) / 2 + 3.5f, p);
            p.setFakeBoldText(false);
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
        float dx0 = x0 + 7, dy0 = (y0 + y1) / 2 + 2, dx1 = x1 - 7, dy1 = y1 - 7;
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
        if (bw >= 64 && bh >= 64 && r.nextFloat() < 0.35f) {
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
                : CAR_COLORS[rnd.nextInt(CAR_COLORS.length)];
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

    /** True if a circle at (x, y) overlaps any solid tile or leaves the map. */
    boolean circleBlocked(float x, float y, float r) {
        int tx0 = (int) Math.floor((x - r) / T), tx1 = (int) Math.floor((x + r) / T);
        int ty0 = (int) Math.floor((y - r) / T), ty1 = (int) Math.floor((y + r) / T);
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                if (!solidTile(tx, ty)) continue;
                float nx = Math.max(tx * T, Math.min(x, tx * T + T));
                float ny = Math.max(ty * T, Math.min(y, ty * T + T));
                float dx = x - nx, dy = y - ny;
                if (dx * dx + dy * dy < r * r) return true;
            }
        }
        return false;
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
        bfs(humanDist, entities, false);
        bfs(zombieDist, entities, true);
    }

    /** Can a vehicle drive over this tile, and at what cost: roads are cheap, sidewalks and lots dearer. */
    private int driveCost(int i) {
        byte t = tiles[i];
        if (t == ROAD) return 2;
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
        Arrays.fill(dist, FAR);
        float[] p = nearestDrivable(x, y);
        if (p == null) return false;
        java.util.PriorityQueue<Long> pq = new java.util.PriorityQueue<Long>();
        int s0 = tileIndex(p[0], p[1]);
        dist[s0] = 0;
        pq.add((long) s0);
        while (!pq.isEmpty()) {
            long top = pq.poll();
            int t = (int) (top & 0xFFFFFF), d = (int) (top >>> 24);
            if (d > dist[t]) continue;
            int tx = t % w, ty = t / w;
            for (int k = 0; k < 4; k++) {
                int nx = tx + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = ty + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int n = ny * w + nx, c = driveCost(n);
                if (c < 0 || d + c >= dist[n]) continue;
                dist[n] = d + c;
                pq.add(((long) dist[n] << 24) | n);
            }
        }
        return true;
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
        spread(dist, tail);
    }

    private void bfs(int[] dist, List<Entity> entities, boolean zombies) {
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
        spread(dist, tail);
    }

    private void spread(int[] dist, int tail) {
        int head = 0;
        while (head < tail) {
            int t = queue[head++];
            int tx = t % w, ty = t / w, nd = dist[t] + 1;
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
