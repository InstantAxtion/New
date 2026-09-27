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
            FOUNTAIN = 7, LOT = 8, WATER = 9, SAND = 10, BRIDGE = 11;

    static final int OFFICE = 0, HOUSE = 1, WAREHOUSE = 2;

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
        final float x0, y0, x1, y1, height;
        final int roof, wall, seed, kind;

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

    static final float FLOOR = 12f;
    static final float TREE_HEIGHT = 14f;

    final CityConfig cfg;
    final int w, h;
    final byte[] tiles;
    final boolean[] solid;
    final boolean[] opaque;
    final int[] humanDist, zombieDist;
    private final int[] queue;
    final Random rnd;
    final Bitmap bitmap;
    float maxHeight;

    private final boolean[] roadCol, roadRow, riverCol, riverRow;
    private final List<Integer> colStarts = new ArrayList<Integer>();
    private final List<Integer> rowStarts = new ArrayList<Integer>();
    /** Building lots: {x, y, w, h, roof, seed, kind, floors, wall} in tiles. */
    private final List<int[]> buildingLots = new ArrayList<int[]>();
    final List<Building> buildings = new ArrayList<Building>();
    /** Tree canopies as {x, y, radius}; drawn above the ground by GameView. */
    final List<float[]> trees = new ArrayList<float[]>();
    /** Street lamps as {x, y}; they light up at night. */
    final List<float[]> lamps = new ArrayList<float[]>();
    private final List<int[]> fountains = new ArrayList<int[]>();
    private int origin;

    City(CityConfig cfg) {
        this.cfg = cfg;
        w = h = cfg.tiles();
        rnd = new Random(cfg.seed);
        tiles = new byte[w * h];
        solid = new boolean[w * h];
        opaque = new boolean[w * h];
        humanDist = new int[w * h];
        zombieDist = new int[w * h];
        queue = new int[w * h];
        roadCol = new boolean[w];
        roadRow = new boolean[h];
        riverCol = new boolean[w];
        riverRow = new boolean[h];
        generate();
        for (int i = 0; i < tiles.length; i++) {
            byte t = tiles[i];
            solid[i] = t == BUILDING || t == TREE || t == CAR || t == FOUNTAIN || t == WATER;
            opaque[i] = t == BUILDING;
        }
        for (int[] l : buildingLots) {
            float height = l[7] * FLOOR + 3;
            maxHeight = Math.max(maxHeight, height);
            buildings.add(new Building(l[0] * T, l[1] * T, (l[0] + l[2]) * T, (l[1] + l[3]) * T, height,
                    l[4], l[8], l[5], l[6]));
        }
        Arrays.fill(humanDist, FAR);
        Arrays.fill(zombieDist, FAR);
        bitmap = Bitmap.createBitmap(w * T, h * T, Bitmap.Config.ARGB_8888);
        render(new Canvas(bitmap));
    }

    float worldW() {
        return w * T;
    }

    float worldH() {
        return h * T;
    }

    // ------------------------------------------------------------------ generation

    /** Road (width 3) and river (width 7) bands along one axis, as {start, width, isRiver}. */
    private List<int[]> separators(int size, boolean river) {
        List<int[]> out = new ArrayList<int[]>();
        int p = origin;
        out.add(new int[]{p, 3, 0});
        while (true) {
            int next = p + 3 + 9 + rnd.nextInt(7);
            if (next + 3 > origin + size - 4) break;
            out.add(new int[]{next, 3, 0});
            p = next;
        }
        if (river && out.size() >= 3) {
            int[] s = out.get(out.size() / 2);
            s[0] -= 2;
            s[1] = 7;
            s[2] = 1;
        }
        return out;
    }

    private void generate() {
        origin = cfg.island() ? 5 : 0;
        int inner = w - origin * 2;
        boolean riverVertical = rnd.nextBoolean();
        List<int[]> cols = separators(inner, cfg.river() && riverVertical);
        List<int[]> rows = separators(inner, cfg.river() && !riverVertical);
        for (int[] s : cols) {
            if (s[2] == 0) colStarts.add(s[0]);
            for (int i = 0; i < s[1]; i++) (s[2] == 1 ? riverCol : roadCol)[s[0] + i] = true;
        }
        for (int[] s : rows) {
            if (s[2] == 0) rowStarts.add(s[0]);
            for (int i = 0; i < s[1]; i++) (s[2] == 1 ? riverRow : roadRow)[s[0] + i] = true;
        }
        Arrays.fill(tiles, ROAD);

        for (int bi = 0; bi < cols.size(); bi++) {
            int x0 = cols.get(bi)[0] + cols.get(bi)[1];
            int x1 = bi + 1 < cols.size() ? cols.get(bi + 1)[0] : origin + inner;
            for (int bj = 0; bj < rows.size(); bj++) {
                int y0 = rows.get(bj)[0] + rows.get(bj)[1];
                int y1 = bj + 1 < rows.size() ? rows.get(bj + 1)[0] : origin + inner;
                if (x1 - x0 < 1 || y1 - y0 < 1) continue;
                fill(x0, y0, x1 - x0, y1 - y0, SIDEWALK);
                int ix = x0 + 1, iy = y0 + 1, iw = x1 - x0 - 2, ih = y1 - y0 - 2;
                if (iw < 2 || ih < 2) continue;
                block(ix, iy, iw, ih);
            }
        }

        // Rivers: water everywhere except where a road crosses (a bridge).
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (riverCol[x]) tiles[y * w + x] = roadRow[y] && inside(x, y) ? BRIDGE : WATER;
                else if (riverRow[y]) tiles[y * w + x] = roadCol[x] && inside(x, y) ? BRIDGE : WATER;
            }

        // Island: ocean all around with a sandy beach.
        if (cfg.island()) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int d = Math.min(Math.min(x, y), Math.min(w - 1 - x, h - 1 - y));
                    if (d < origin - 2) tiles[y * w + x] = WATER;
                    else if (d < origin && tiles[y * w + x] != WATER) tiles[y * w + x] = SAND;
                }
        }

        // Abandoned cars on the roads (never at intersections, never next to each other).
        float carChance = CAR_CHANCE[cfg.traffic()];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (tiles[y * w + x] != ROAD) continue;
                if (roadCol[x] == roadRow[y]) continue;
                if (rnd.nextFloat() > carChance) continue;
                if (hasNeighbor(x, y, CAR)) continue;
                tiles[y * w + x] = CAR;
            }
        }

        // Street lamps on the sidewalk corners of every intersection.
        for (int cs : colStarts)
            for (int rs : rowStarts) {
                int[][] corners = {{cs - 1, rs - 1}, {cs + 3, rs - 1}, {cs - 1, rs + 3}, {cs + 3, rs + 3}};
                for (int[] k : corners) {
                    if (k[0] < 0 || k[1] < 0 || k[0] >= w || k[1] >= h) continue;
                    if (tiles[k[1] * w + k[0]] == SIDEWALK) lamps.add(new float[]{k[0] * T + T / 2f, k[1] * T + T / 2f});
                }
            }
    }

    private boolean inside(int x, int y) {
        return x >= origin && y >= origin && x < w - origin && y < h - origin;
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
        if (roll < park) park(x, y, bw, bh);
        else if ((roll -= park) < plaza) plaza(x, y, bw, bh);
        else if ((roll -= plaza) < parking) parking(x, y, bw, bh);
        else if ((roll -= parking) < open) {
            if (rnd.nextBoolean()) park(x, y, bw, bh);
            else plaza(x, y, bw, bh);
        } else {
            if (style == CityConfig.STYLE_MIXED) {
                float cx = x + bw / 2f - w / 2f, cy = y + bh / 2f - h / 2f;
                float d = (float) Math.sqrt(cx * cx + cy * cy) / (w * 0.5f);
                float r = rnd.nextFloat();
                if (d < 0.4f || r < 0.25f) style = CityConfig.STYLE_OFFICES;
                else if (r < 0.8f) style = CityConfig.STYLE_HOUSES;
                else style = CityConfig.STYLE_WAREHOUSES;
            }
            if (style == CityConfig.STYLE_HOUSES) houses(x, y, bw, bh);
            else if (style == CityConfig.STYLE_WAREHOUSES) warehouses(x, y, bw, bh);
            else if (cfg.density() == 0 && bw >= 5 && bh >= 5) {
                fill(x, y, bw, bh, GRASS);
                lots(x + 1, y + 1, bw - 2, bh - 2, 0);
            } else {
                lots(x, y, bw, bh, 0);
            }
        }
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
        addLot(x, y, lw, lh, OFFICE);
    }

    private void houses(int x, int y, int pw, int ph) {
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

    private void park(int x, int y, int pw, int ph) {
        fill(x, y, pw, ph, GRASS);
        int cx = x + pw / 2, cy = y + ph / 2;
        for (int i = x; i < x + pw; i++) tiles[cy * w + i] = PLAZA;
        for (int j = y; j < y + ph; j++) tiles[j * w + cx] = PLAZA;
        for (int j = y; j < y + ph; j++)
            for (int i = x; i < x + pw; i++)
                if (tiles[j * w + i] == GRASS && rnd.nextFloat() < 0.13f && !hasNeighbor(i, j, TREE))
                    tiles[j * w + i] = TREE;
    }

    private void plaza(int x, int y, int pw, int ph) {
        fill(x, y, pw, ph, PLAZA);
        if (pw >= 6 && ph >= 6) {
            int fx = x + pw / 2 - 1, fy = y + ph / 2 - 1;
            fill(fx, fy, 2, 2, FOUNTAIN);
            fountains.add(new int[]{fx, fy});
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

    private boolean nextTo(int x, int y, byte type) {
        return (x > 0 && tiles[y * w + x - 1] == type) || (x < w - 1 && tiles[y * w + x + 1] == type)
                || (y > 0 && tiles[(y - 1) * w + x] == type) || (y < h - 1 && tiles[(y + 1) * w + x] == type);
    }

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
                    case PLAZA: case FOUNTAIN: col = 0xFFB3A487; break;
                    case LOT: col = 0xFF48494D; break;
                    case BUILDING: col = 0xFF8F8D87; break;
                    case WATER: col = nextTo(x, y, SAND) || nextTo(x, y, SIDEWALK) || nextTo(x, y, BRIDGE)
                            ? 0xFF3A83AE : 0xFF2C6A96; break;
                    case SAND: col = 0xFFD9C791; break;
                    case BRIDGE: col = 0xFF4B4E54; break;
                    default: col = 0xFF3A3D43; break;
                }
                if (t == TREE && isPlazaTree(x, y)) col = 0xFFB3A487;
                if (t == CAR && isLotCar(x, y)) col = 0xFF48494D;
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
                    p.setColor(0xFF7E7C77);
                    c.drawLine(fx, fy, fx + T, fy, p);
                    c.drawLine(fx, fy, fx, fy + T, p);
                    c.drawLine(fx + T / 2f, fy, fx + T / 2f, fy + T, p);
                } else if (t == GRASS || t == TREE) {
                    for (int k = 0; k < 6; k++) {
                        p.setColor(rnd.nextBoolean() ? 0xFF578A3F : 0xFF426B30);
                        c.drawCircle(fx + rnd.nextFloat() * T, fy + rnd.nextFloat() * T, 0.9f, p);
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
                } else if (t == SAND) {
                    for (int k = 0; k < 5; k++) {
                        p.setColor(rnd.nextBoolean() ? 0xFFE6D6A4 : 0xFFC8B57E);
                        c.drawCircle(fx + rnd.nextFloat() * T, fy + rnd.nextFloat() * T, 0.8f, p);
                    }
                } else if (t == WATER) {
                    if (rnd.nextFloat() < 0.35f) {
                        p.setColor(0x40FFFFFF);
                        float wx = fx + rnd.nextFloat() * 10, wy = fy + 3 + rnd.nextFloat() * 10;
                        c.drawLine(wx, wy, wx + 3, wy - 1.2f, p);
                        c.drawLine(wx + 3, wy - 1.2f, wx + 6, wy, p);
                    }
                }
            }
        }

        // Bridge railings wherever a bridge meets the water.
        p.setColor(0xFFB9B9B4);
        p.setStrokeWidth(2f);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (tiles[y * w + x] != BRIDGE) continue;
                float fx = x * T, fy = y * T;
                if (y > 0 && tiles[(y - 1) * w + x] == WATER) c.drawLine(fx, fy + 1, fx + T, fy + 1, p);
                if (y < h - 1 && tiles[(y + 1) * w + x] == WATER) c.drawLine(fx, fy + T - 1, fx + T, fy + T - 1, p);
                if (x > 0 && tiles[y * w + x - 1] == WATER) c.drawLine(fx + 1, fy, fx + 1, fy + T, p);
                if (x < w - 1 && tiles[y * w + x + 1] == WATER) c.drawLine(fx + T - 1, fy, fx + T - 1, fy + T, p);
            }

        drawRoadMarkings(c, p);
        drawParkingLines(c, p);

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

        for (int[] f : fountains) {
            float cx = (f[0] + 1) * T, cy = (f[1] + 1) * T;
            p.setColor(0xFF8A8478);
            c.drawCircle(cx, cy, 15, p);
            p.setColor(0xFF3D86B8);
            c.drawCircle(cx, cy, 12, p);
            p.setColor(0xFF7FC3EA);
            c.drawCircle(cx, cy, 4, p);
            p.setColor(0x66FFFFFF);
            c.drawCircle(cx - 4, cy - 4, 2, p);
        }

        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (tiles[y * w + x] == CAR) drawCar(c, p, x, y);

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

    private void drawRoadMarkings(Canvas c, Paint p) {
        p.setColor(0xFFD9B43A);
        p.setStrokeWidth(1.2f);
        for (int s : colStarts) {
            float lx = (s + 1.5f) * T;
            for (int y = 0; y < h; y++) {
                if (roadRow[y] || !paved(s + 1, y)) continue;
                c.drawLine(lx, y * T + 3, lx, y * T + 11, p);
            }
        }
        for (int s : rowStarts) {
            float ly = (s + 1.5f) * T;
            for (int x = 0; x < w; x++) {
                if (roadCol[x] || !paved(x, s + 1)) continue;
                c.drawLine(x * T + 3, ly, x * T + 11, ly, p);
            }
        }
        // Crosswalks on each side of every intersection.
        p.setColor(0xCCE8E8E8);
        for (int cs : colStarts) {
            for (int rs : rowStarts) {
                float x0 = cs * T, y0 = rs * T, size = 3 * T;
                for (int k = 0; k < 8; k++) {
                    float o = 3 + k * 6;
                    if (paved(cs + 1, rs - 1)) c.drawRect(x0 + o, y0 - 9, x0 + o + 3, y0 - 2, p);
                    if (paved(cs + 1, rs + 3)) c.drawRect(x0 + o, y0 + size + 2, x0 + o + 3, y0 + size + 9, p);
                    if (paved(cs - 1, rs + 1)) c.drawRect(x0 - 9, y0 + o, x0 - 2, y0 + o + 3, p);
                    if (paved(cs + 3, rs + 1)) c.drawRect(x0 + size + 2, y0 + o, x0 + size + 9, y0 + o + 3, p);
                }
            }
        }
    }

    private boolean paved(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        byte t = tiles[y * w + x];
        return t == ROAD || t == CAR || t == BRIDGE;
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
            return;
        }
        if (kind == WAREHOUSE) {
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

        int units = 1 + r.nextInt(Math.max(1, (int) (bw * bh / 1500f)) + 1);
        for (int i = 0; i < units; i++) {
            float uw = 6 + r.nextInt(6), uh = 5 + r.nextInt(5);
            float ux = x0 + 7 + r.nextFloat() * Math.max(1, bw - 14 - uw);
            float uy = y0 + 7 + r.nextFloat() * Math.max(1, bh - 14 - uh);
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

    private void drawCar(Canvas c, Paint p, int x, int y) {
        boolean vertical;
        if (isLotCar(x, y)) vertical = true;
        else vertical = roadCol[x] && !roadRow[y];
        float cx = x * T + T / 2f, cy = y * T + T / 2f;
        float hl = 7.5f, hw = 4.2f;
        RectF rect = vertical ? new RectF(cx - hw, cy - hl, cx + hw, cy + hl) : new RectF(cx - hl, cy - hw, cx + hl, cy + hw);
        int color = CAR_COLORS[rnd.nextInt(CAR_COLORS.length)];
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
            if (t == SIDEWALK || t == PLAZA || t == GRASS || t == SAND || ((t == ROAD || t == LOT) && r.nextFloat() < 0.2f))
                return new float[]{tx * T + 3 + r.nextFloat() * (T - 6), ty * T + 3 + r.nextFloat() * (T - 6)};
        }
        return new float[]{T * 1.5f, T * 1.5f};
    }

    // ------------------------------------------------------------------ flow fields

    void computeFields(List<Entity> entities) {
        bfs(humanDist, entities, false);
        bfs(zombieDist, entities, true);
    }

    private void bfs(int[] dist, List<Entity> entities, boolean zombies) {
        Arrays.fill(dist, FAR);
        int head = 0, tail = 0;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.dead || e.isZombie() != zombies) continue;
            int t = tileIndex(e.x, e.y);
            if (dist[t] != 0) {
                dist[t] = 0;
                queue[tail++] = t;
            }
        }
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
