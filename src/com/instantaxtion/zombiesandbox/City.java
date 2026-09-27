package com.instantaxtion.zombiesandbox;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
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
            FOUNTAIN = 7, LOT = 8;

    private static final int[] ROOFS = {
            0xFF6B6F78, 0xFF7A6A5C, 0xFF5C6670, 0xFF8A7F70, 0xFF6E5D5A, 0xFF59646B, 0xFF7C7C74,
            0xFF4F5A66, 0xFF866E5E, 0xFF6A7468
    };
    private static final int[] CAR_COLORS = {
            0xFFB03A2E, 0xFF2E5FB0, 0xFFE0E0E0, 0xFF222428, 0xFFD4A21C, 0xFF3C8A4E, 0xFF8A8F96, 0xFF6B2E8A
    };

    final int w, h;
    final byte[] tiles;
    final boolean[] solid;
    final boolean[] opaque;
    final int[] humanDist, zombieDist;
    private final int[] queue;
    final Random rnd;
    final Bitmap bitmap;

    private final boolean[] roadCol, roadRow;
    private final List<Integer> colStarts = new ArrayList<Integer>();
    private final List<Integer> rowStarts = new ArrayList<Integer>();
    private final List<int[]> buildings = new ArrayList<int[]>();
    private final List<int[]> fountains = new ArrayList<int[]>();

    City(int w, int h, long seed) {
        this.w = w;
        this.h = h;
        rnd = new Random(seed);
        tiles = new byte[w * h];
        solid = new boolean[w * h];
        opaque = new boolean[w * h];
        humanDist = new int[w * h];
        zombieDist = new int[w * h];
        queue = new int[w * h];
        roadCol = new boolean[w];
        roadRow = new boolean[h];
        generate();
        for (int i = 0; i < tiles.length; i++) {
            byte t = tiles[i];
            solid[i] = t == BUILDING || t == TREE || t == CAR || t == FOUNTAIN;
            opaque[i] = t == BUILDING;
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

    private void roadStarts(int size, List<Integer> out, boolean[] mark) {
        int p = 0;
        out.add(p);
        while (true) {
            int next = p + 3 + 9 + rnd.nextInt(7);
            if (next + 3 > size - 4) break;
            out.add(next);
            p = next;
        }
        for (int s : out) for (int i = 0; i < 3 && s + i < size; i++) mark[s + i] = true;
    }

    private void generate() {
        roadStarts(w, colStarts, roadCol);
        roadStarts(h, rowStarts, roadRow);
        Arrays.fill(tiles, ROAD);

        for (int bi = 0; bi < colStarts.size(); bi++) {
            int x0 = colStarts.get(bi) + 3;
            int x1 = bi + 1 < colStarts.size() ? colStarts.get(bi + 1) : w;
            for (int bj = 0; bj < rowStarts.size(); bj++) {
                int y0 = rowStarts.get(bj) + 3;
                int y1 = bj + 1 < rowStarts.size() ? rowStarts.get(bj + 1) : h;
                if (x1 - x0 < 1 || y1 - y0 < 1) continue;
                fill(x0, y0, x1 - x0, y1 - y0, SIDEWALK);
                int ix = x0 + 1, iy = y0 + 1, iw = x1 - x0 - 2, ih = y1 - y0 - 2;
                if (iw < 2 || ih < 2) continue;
                float roll = rnd.nextFloat();
                if (roll < 0.66f) lots(ix, iy, iw, ih, 0);
                else if (roll < 0.80f) park(ix, iy, iw, ih);
                else if (roll < 0.90f) plaza(ix, iy, iw, ih);
                else parking(ix, iy, iw, ih);
            }
        }

        // Abandoned cars on the roads (never at intersections, never next to each other).
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (tiles[y * w + x] != ROAD) continue;
                if (roadCol[x] == roadRow[y]) continue;
                if (rnd.nextFloat() > 0.018f) continue;
                if (hasNeighbor(x, y, CAR)) continue;
                tiles[y * w + x] = CAR;
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
        if (depth < 3 && lw >= 7 && lw >= lh && rnd.nextFloat() < 0.75f) {
            int cut = 3 + rnd.nextInt(lw - 5);
            lots(x, y, cut, lh, depth + 1);
            lots(x + cut + 1, y, lw - cut - 1, lh, depth + 1);
            return;
        }
        if (depth < 3 && lh >= 7 && rnd.nextFloat() < 0.75f) {
            int cut = 3 + rnd.nextInt(lh - 5);
            lots(x, y, lw, cut, depth + 1);
            lots(x, y + cut + 1, lw, lh - cut - 1, depth + 1);
            return;
        }
        if (lw < 2 || lh < 2) return;
        fill(x, y, lw, lh, BUILDING);
        buildings.add(new int[]{x, y, lw, lh, ROOFS[rnd.nextInt(ROOFS.length)], rnd.nextInt(1000)});
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
                }
            }
        }

        drawRoadMarkings(c, p);
        drawParkingLines(c, p);

        // Building shadows then roofs.
        p.setColor(0x55000000);
        for (int[] b : buildings)
            c.drawRect(b[0] * T + 4, b[1] * T + 4, (b[0] + b[2]) * T + 4, (b[1] + b[3]) * T + 4, p);
        for (int[] b : buildings) drawBuilding(c, p, b);

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

        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (tiles[y * w + x] == TREE) {
                    float cx = x * T + T / 2f, cy = y * T + T / 2f;
                    float r = 7.5f + rnd.nextFloat() * 2f;
                    p.setColor(0x55000000);
                    c.drawCircle(cx + 3, cy + 3, r, p);
                    p.setColor(0xFF2C5A22);
                    c.drawCircle(cx, cy, r, p);
                    p.setColor(0xFF3B742D);
                    c.drawCircle(cx - 1.5f, cy - 1.5f, r * 0.65f, p);
                    p.setColor(0xFF4C8A3A);
                    c.drawCircle(cx - 2.5f, cy - 2.5f, r * 0.3f, p);
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
                if (roadRow[y]) continue;
                c.drawLine(lx, y * T + 3, lx, y * T + 11, p);
            }
        }
        for (int s : rowStarts) {
            float ly = (s + 1.5f) * T;
            for (int x = 0; x < w; x++) {
                if (roadCol[x]) continue;
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
                    if (rs > 0) c.drawRect(x0 + o, y0 - 9, x0 + o + 3, y0 - 2, p);
                    if (rs + 3 < h) c.drawRect(x0 + o, y0 + size + 2, x0 + o + 3, y0 + size + 9, p);
                    if (cs > 0) c.drawRect(x0 - 9, y0 + o, x0 - 2, y0 + o + 3, p);
                    if (cs + 3 < w) c.drawRect(x0 + size + 2, y0 + o, x0 + size + 9, y0 + o + 3, p);
                }
            }
        }
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
        int roof = b[4];
        Random r = new Random(b[5]);
        p.setColor(darken(roof, 0.7f));
        c.drawRect(x0, y0, x1, y1, p);
        p.setColor(roof);
        c.drawRect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, p);
        p.setColor(lighten(roof, 0.08f));
        c.drawRect(x0 + 5, y0 + 5, x1 - 5, y1 - 5, p);

        float bw = x1 - x0, bh = y1 - y0;
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
            if (t == SIDEWALK || t == PLAZA || t == GRASS || (t == ROAD && r.nextFloat() < 0.2f))
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
