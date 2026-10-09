package com.instantaxtion.zombiesandbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * The lie of the land (cities built since 10.10). Every tile has a height: gentle rises in town, rolling hills
 * out in the country, a mountain or two on the bigger maps (rocky up top, pine woods on the slopes, snow on the
 * highest), and the wild country that goes with them: streams running down off the hills (with a waterfall
 * where they drop off a crag), small lakes and ponds, a nature park with a visitor centre and hiking trails
 * up to a lookout, the falls and the lake shore, and campsites with tents, cabins and a camp store.
 */
final class Terrain {
    private final City c;
    private final int w, h;
    private final byte[] t;
    private final Random rnd;
    private final float[] e;
    private final int noiseSeed;
    /** How far each tile is from anything that isn't grass or trees (a road, a building, the town). */
    private int[] wild;
    /** How much of each tile's height is mountain. */
    private float[] mount;
    /** Mountains: {x, y, radius, height}. */
    private final List<float[]> peaks = new ArrayList<float[]>();
    /** Lakes made here: {x, y, radius}. */
    private final List<float[]> lakes = new ArrayList<float[]>();
    /** The park: centre and radius (radius 0 if there is none). */
    private float parkX, parkY, parkR;
    private int parkPeak = -1;

    /** The country's land: its hills, mountains, woods, lakes, snow and trees (see Country.landscape). */
    private final Country land;
    /** How much of the usual woodland grows (10.20: little nature out of town). */
    private final float nature;

    Terrain(City c, Random rnd) {
        this.land = c.country;
        this.nature = c.cfg.littleNature() ? 0.2f : 1f;
        this.c = c;
        this.w = c.w;
        this.h = c.h;
        this.t = c.tiles;
        this.rnd = rnd;
        this.e = new float[w * h];
        this.noiseSeed = rnd.nextInt();
    }

    // ------------------------------------------------------------------ noise

    private float lattice(int x, int y, int oct) {
        int n = x * 374761393 + y * 668265263 + noiseSeed * 1442695041 + oct * 1274126177;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0xFFFF) / 65535f;
    }

    private float noise(float x, float y, int oct) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = lattice(x0, y0, oct), b = lattice(x0 + 1, y0, oct), cc = lattice(x0, y0 + 1, oct), d = lattice(x0 + 1, y0 + 1, oct);
        return a + (b - a) * fx + (cc - a) * fy + (a - b - cc + d) * fx * fy;
    }

    /** Smooth noise from 0 to 1, with detail. */
    private float fbm(float x, float y, int base) {
        float s = 0, amp = 0.5f, f = 1, norm = 0;
        for (int o = 0; o < 4; o++) {
            s += amp * noise(x * f, y * f, base + o);
            norm += amp;
            amp *= 0.5f;
            f *= 2.03f;
        }
        return s / norm;
    }

    private static float smooth(float v) {
        v = Math.max(0, Math.min(1, v));
        return v * v * (3 - 2 * v);
    }

    // ------------------------------------------------------------------ building it

    /** Lays out the land: called once the roads, the town and the farms are in. Returns the heights. */
    float[] build() {
        boolean country = c.townX0 > 0;
        computeWild();
        int[] waterDist = distanceToWater();
        mount = new float[w * h];
        // Heights: gentle in town, rolling hills out in the country.
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int out = Math.max(Math.max(c.townX0 - x, x - (c.townX1 - 1)), Math.max(c.townY0 - y, y - (c.townY1 - 1)));
                float amp = (5 + 22 * smooth(out / 18f)) * land.hills;
                float hills = fbm(x / 52f, y / 52f, 0), rough = fbm(x / 17f + 100, y / 17f + 100, 10);
                e[y * w + x] = amp * (hills * 0.8f + rough * 0.2f);
            }
        // (Little nature: no mountains, far fewer trees.)
        if (country && !c.cfg.littleNature()) mountains();
        // Shores slope down to the sea, the river or the lake; islands rise to hills in the middle.
        for (int i = 0; i < w * h; i++) {
            float dw = waterDist[i];
            e[i] *= smooth(dw / 9f);
            if (c.waterKind == CityConfig.W_ISLANDS) e[i] += 16 * smooth(dw / 45f);
            else if (c.waterKind == CityConfig.W_SEA || c.waterKind == CityConfig.W_HARBOUR) e[i] += 10 * smooth(dw / 90f);
        }
        if (country) {
            rocksAndPines();
            lakes();
            if (!land.desert || land.lakes >= 0.3f) streams();
            park();
            campsites();
            if (c.cfg.wilds()) {
                // (Since 10.14: trails that wind and cross the lanes, a ranger station and fire lookouts.)
                if (parkR > 0 && trailhead >= 0) rangerStation();
                fireTowers();
                wildTrails();
            } else if (parkR > 0) trails();
        }
        countryTrees();
        return e;
    }

    /**
     * A real city's lie of the land (since 10.18): gentle ground in town, its real hills where they are, the
     * shores sloping to the water; and in each of its woods and big parks a trail from the nearest street up
     * to the high point (the lookout), so people can be out hiking.
     */
    float[] buildReal(RealCities.Spec s, byte[] area) {
        int[] waterDist = distanceToWater();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float fx = (x + 0.5f) / w, fy = (y + 0.5f) / h;
                float v = 4 * land.hills * (fbm(x / 52f, y / 52f, 0) * 0.8f + fbm(x / 17f + 100, y / 17f + 100, 10) * 0.2f);
                for (float[] hl : s.hills) {
                    float dx = (fx - hl[0]) / hl[2], dy = (fy - hl[1]) / hl[2];
                    float d2 = dx * dx + dy * dy;
                    if (d2 < 4) v += hl[3] * (float) Math.exp(-d2 * 1.6f) * (0.8f + 0.4f * fbm(x / 23f + 7, y / 23f + 7, 20));
                }
                int i = y * w + x;
                if (area[i] > 0 && s.areas.get(area[i] - 1).kind == 1) v += 10 * fbm(x / 14f + 50, y / 14f + 50, 30);
                e[i] = v * smooth(waterDist[i] / 9f);
            }
        // Trails: from the street nearest each wood (or big park) up to its high point.
        int[] cost = new int[w * h];
        for (int a = 0; a < s.areas.size(); a++) {
            RealCities.Area ar = s.areas.get(a);
            if (ar.kind == 2) continue;
            int top = -1, cells = 0;
            long sx = 0, sy = 0;
            for (int i = 0; i < w * h; i++) {
                if (area[i] != a + 1 || (t[i] != City.GRASS && t[i] != City.TREE && t[i] != City.PLAZA)) continue;
                cells++;
                sx += i % w;
                sy += i / w;
                if (top < 0 || e[i] > e[top]) top = i;
            }
            if (top < 0 || cells < (ar.kind == 1 ? 150 : 500)) continue;
            float cx = sx / (float) cells, cy = sy / (float) cells;
            // The trailhead: the street tile nearest the middle of it.
            int head = -1;
            float hd = Float.MAX_VALUE;
            for (int i = 0; i < w * h; i++) {
                if (t[i] != City.ROAD || (c.bridge != null && c.bridge[i])) continue;
                float d = (float) Math.hypot(i % w - cx, i / w - cy);
                if (d < hd) {
                    hd = d;
                    head = i;
                }
            }
            if (head < 0 || hd > 160) continue;
            // (Starting from the verge beside the street.)
            int start = -1;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = head % w + d[0] * 2, y = head / w + d[1] * 2;
                if (x < 1 || y < 1 || x >= w - 1 || y >= h - 1) continue;
                int j = y * w + x;
                if (area[j] == a + 1 && (t[j] == City.GRASS || t[j] == City.TREE || t[j] == City.PLAZA)) start = j;
            }
            if (start < 0) start = head;
            float limit = (float) Math.hypot(top % w - start % w, top / w - start / w) + 40;
            int[] from = trailSearch(new int[]{start}, (start % w + top % w) / 2f, (start / w + top / w) / 2f, limit, cost);
            layTrail(top, from, cost, 0);
            // A second, on round to the far side (if it's a big wood).
            if (ar.kind == 1 && cells > 2500) {
                int far = -1;
                float fd = 0;
                for (int i = 0; i < w * h; i++) {
                    if (area[i] != a + 1 || t[i] != City.GRASS) continue;
                    float d = (float) Math.hypot(i % w - top % w, i / w - top / w);
                    if (d > fd && d < 120) {
                        fd = d;
                        far = i;
                    }
                }
                if (far >= 0) {
                    from = trailSearch(new int[]{top}, (far % w + top % w) / 2f, (far / w + top / w) / 2f, fd + 30, cost);
                    layTrail(far, from, cost, 0);
                }
            }
            c.addNatureDecor(City.D_LOOKOUT, (top % w) * City.T - 6, (top / w) * City.T - 6, (top % w) * City.T + 22, (top / w) * City.T + 22, 0);
            if (c.trailheadX <= 0) {
                c.trailheadX = (start % w + 0.5f) * City.T;
                c.trailheadY = (start / w + 0.5f) * City.T;
            }
        }
        countryTrees();
        // (Evergreens in the woods: thick in the Pacific Northwest.)
        float pines = s.name.equals("Portland") || s.name.equals("Seattle") ? 0.75f : land.pineShare;
        for (int i = 0; i < w * h; i++)
            if (t[i] == City.TREE && area[i] > 0 && s.areas.get(area[i] - 1).kind == 1 && rnd.nextFloat() < pines)
                c.setTreeKind(i, Country.TK_PINE);
        return e;
    }

    /** Distance (in tiles, up to 40) from anything that isn't open grass or trees. */
    private void computeWild() {
        wild = new int[w * h];
        int[] q = new int[w * h];
        int tail = 0;
        Arrays.fill(wild, 40);
        for (int i = 0; i < w * h; i++) {
            byte k = t[i];
            int x = i % w, y = i / w;
            boolean inTown = x >= c.townX0 && x < c.townX1 && y >= c.townY0 && y < c.townY1;
            if ((k != City.GRASS && k != City.TREE) || inTown) {
                wild[i] = 0;
                q[tail++] = i;
            }
        }
        spread(wild, q, tail, 40);
        // (The edge of the map counts too: nothing big gets squeezed into a corner.)
        for (int i = 0; i < w * h; i++) {
            int x = i % w, y = i / w;
            wild[i] = Math.min(wild[i], Math.min(Math.min(x, y), Math.min(w - 1 - x, h - 1 - y)) + 2);
        }
    }

    /** Distance (in tiles, up to 90) from the water that was there before (the sea, a river, a lake in town). */
    private int[] distanceToWater() {
        int[] d = new int[w * h];
        int[] q = new int[w * h];
        int tail = 0;
        Arrays.fill(d, 90);
        for (int i = 0; i < w * h; i++)
            if (t[i] == City.WATER || (c.bridge != null && c.bridge[i])) {
                d[i] = 0;
                q[tail++] = i;
            }
        spread(d, q, tail, 90);
        return d;
    }

    private void spread(int[] d, int[] q, int tail, int cap) {
        int head = 0;
        while (head < tail) {
            int i = q[head++], x = i % w, y = i / w, nd = d[i] + 1;
            if (nd >= cap) continue;
            if (x > 0 && d[i - 1] > nd) { d[i - 1] = nd; q[tail++] = i - 1; }
            if (x < w - 1 && d[i + 1] > nd) { d[i + 1] = nd; q[tail++] = i + 1; }
            if (y > 0 && d[i - w] > nd) { d[i - w] = nd; q[tail++] = i - w; }
            if (y < h - 1 && d[i + w] > nd) { d[i + w] = nd; q[tail++] = i + w; }
        }
    }

    private int size() {
        return c.cfg.size();
    }

    /** A mountain or two in the emptiest country: a ridge with crags along the top. */
    private void mountains() {
        int want = size() >= CityConfig.HUGE ? 3 : size() >= CityConfig.LARGE ? 2 : size() >= CityConfig.MEDIUM ? 1 : 0;
        // (Some countries have more and bigger mountains, flat ones none at all.)
        want = land.mountains <= 0 ? 0 : Math.max(want > 0 ? 1 : 0, Math.round(want * Math.min(1.4f, land.mountains)));
        for (int k = 0; k < want; k++) {
            int best = -1;
            float score = -1;
            for (int s = 0; s < 900; s++) {
                int i = rnd.nextInt(w * h);
                if (wild[i] < 10) continue;
                float x = i % w, y = i / w;
                boolean clear = true;
                for (float[] p : peaks) if (Math.hypot(p[0] - x, p[1] - y) < p[2] * 2.2f) clear = false;
                if (!clear) continue;
                float sc = wild[i] + rnd.nextFloat() * 3;
                if (sc > score) {
                    score = sc;
                    best = i;
                }
            }
            if (best < 0) break;
            float cx = best % w, cy = best / w;
            float r = Math.max(16, Math.min(54, wild[best] * 1.7f));
            float height = (34 + r * 1.4f) * (0.6f + 0.4f * land.mountains);
            float ang = rnd.nextFloat() * (float) Math.PI, stretch = 1.3f + rnd.nextFloat() * 0.6f;
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            int reach = (int) (r * stretch) + 2;
            for (int y = Math.max(0, (int) cy - reach); y < Math.min(h, (int) cy + reach); y++)
                for (int x = Math.max(0, (int) cx - reach); x < Math.min(w, (int) cx + reach); x++) {
                    float dx = x - cx, dy = y - cy;
                    float u = (dx * ca + dy * sa) / stretch, v = -dx * sa + dy * ca;
                    float d = (float) Math.sqrt(u * u + v * v) / r;
                    if (d >= 1) continue;
                    float f = (float) Math.pow(1 - d, 1.7f);
                    float ridge = 1 - Math.abs(2 * noise(x / 9f, y / 9f, 20) - 1);
                    float add = height * f * (0.7f + 0.45f * ridge);
                    // (Gentler where it meets a road or a farm.)
                    add *= 0.35f + 0.65f * smooth(wild[y * w + x] / 6f);
                    e[y * w + x] += add;
                    mount[y * w + x] = Math.max(mount[y * w + x], add / height);
                }
            peaks.add(new float[]{cx, cy, r, height});
        }
    }

    /** Crags and boulders up top, pine woods on the slopes. */
    private void rocksAndPines() {
        for (int i = 0; i < w * h; i++) {
            float m = mount[i];
            if (m <= 0.05f) continue;
            int x = i % w, y = i / w;
            if (t[i] != City.GRASS && t[i] != City.TREE) continue;
            if (wild[i] >= 3 && (m > 0.8f || (m > 0.5f && noise(x / 5f, y / 5f, 30) > 1.28f - m))) {
                t[i] = City.ROCK;
                continue;
            }
            // (Desert mountains are bare; elsewhere woods climb the slopes, mostly pines where the country has them.)
            if (!land.desert && m > 0.1f && m < 0.6f && wild[i] >= 2
                    && rnd.nextFloat() < 0.5f * Math.min(1.3f, land.forest) * smooth((0.62f - m) / 0.2f) * nature) t[i] = City.TREE;
            if (t[i] == City.TREE) c.setTreeKind(i, rnd.nextFloat() < Math.max(land.pineShare, 0.2f) + m ? Country.TK_PINE : land.lowTree);
        }
    }

    /**
     * Every tree gets its country's kind: palms, gums, birches, acacias or broadleaves in the lowlands, pines
     * in the woods where the country has them. In a desert the open country is sand: the trees go, apart from
     * the palms round the water.
     */
    private void countryTrees() {
        int[] wet = null;
        if (land.desert) {
            wet = new int[w * h];
            int[] q = new int[w * h];
            int tail = 0;
            Arrays.fill(wet, 9);
            for (int i = 0; i < w * h; i++)
                if (t[i] == City.WATER) {
                    wet[i] = 0;
                    q[tail++] = i;
                }
            spread(wet, q, tail, 9);
        }
        for (int i = 0; i < w * h; i++) {
            int x = i % w, y = i / w;
            boolean inTown = x >= c.townX0 && x < c.townX1 && y >= c.townY0 && y < c.townY1;
            if (land.desert && !inTown && c.townX0 > 0) {
                if (t[i] == City.GRASS && wet[i] > 3) t[i] = City.SAND;
                // Palms round the oasis.
                else if (t[i] == City.GRASS && wet[i] >= 1 && wild[i] >= 2 && rnd.nextFloat() < 0.35f * nature) t[i] = City.TREE;
                else if (t[i] == City.TREE && wet[i] > 4 && rnd.nextFloat() < 0.85f) {
                    t[i] = City.SAND;
                    c.setTreeKind(i, Country.TK_BROAD);
                    continue;
                }
            }
            if (t[i] != City.TREE || c.treeKindAt(i) == Country.TK_PINE) continue;
            c.setTreeKind(i, !inTown && rnd.nextFloat() < land.pineShare * 0.6f ? Country.TK_PINE : land.lowTree);
        }
    }

    /** A blob of water: a lake or a pond. Returns how many tiles it covers. */
    private int blob(float cx, float cy, float r, int minWild) {
        int n = 0;
        float level = Float.MAX_VALUE;
        int reach = (int) (r * 1.4f) + 1;
        List<Integer> cells = new ArrayList<Integer>();
        for (int y = Math.max(1, (int) cy - reach); y < Math.min(h - 1, (int) cy + reach); y++)
            for (int x = Math.max(1, (int) cx - reach); x < Math.min(w - 1, (int) cx + reach); x++) {
                int i = y * w + x;
                float d = (float) Math.hypot(x - cx, y - cy) / r + (noise(x / 4f, y / 4f, 40) - 0.5f) * 0.55f;
                if (d >= 1) continue;
                // (A stream running into a pond is deep water from there on.)
                if (t[i] == City.WATER && c.isFord(i)) {
                    c.setFord(i, false);
                    continue;
                }
                if ((t[i] != City.GRASS && t[i] != City.TREE) || wild[i] < minWild) continue;
                cells.add(i);
                level = Math.min(level, e[i]);
            }
        for (int i : cells) {
            t[i] = City.WATER;
            c.unpine(i);
            e[i] = level - 1;
            n++;
        }
        return n;
    }

    private void lakes() {
        int want = Math.round((size() >= CityConfig.HUGE ? 3 : size() >= CityConfig.LARGE ? 2 : 1) * land.lakes);
        // (A desert has its oasis.)
        if (land.desert) want = 1;
        for (int k = 0; k < want; k++) {
            int best = -1;
            float low = Float.MAX_VALUE;
            for (int s = 0; s < 120; s++) {
                int i = rnd.nextInt(w * h);
                if (wild[i] < 9 || mount[i] > 0.15f) continue;
                float x = i % w, y = i / w;
                boolean clear = true;
                for (float[] l : lakes) if (Math.hypot(l[0] - x, l[1] - y) < 40) clear = false;
                if (!clear) continue;
                if (e[i] < low) {
                    low = e[i];
                    best = i;
                }
            }
            if (best < 0) continue;
            float r = Math.min(9, wild[best] - 2) * (0.7f + rnd.nextFloat() * 0.3f);
            if (r < 4) continue;
            float cx = best % w, cy = best / w;
            if (blob(cx, cy, r, 2) > 12) {
                lakes.add(new float[]{cx, cy, r});
                if (k == 0) c.natureLabel(c.country.lakeName(word()), (cx + 0.5f) * City.T, (cy + 0.5f) * City.T, City.NL_LAKE);
            }
        }
    }

    private final java.util.HashSet<String> usedWords = new java.util.HashSet<String>();

    /** A word for a name (each used once, while they last). */
    private String word() {
        String[] words = c.country.natureWords();
        // (Once they've all been used, the district names will do.)
        if (usedWords.size() >= words.length) words = c.country.districtWords;
        String wd = words[rnd.nextInt(words.length)];
        for (int k = 0; k < words.length && usedWords.contains(wd); k++) wd = words[rnd.nextInt(words.length)];
        usedWords.add(wd);
        return wd;
    }

    /** Streams: off each mountain, and from the higher hills, downhill until they reach water or run off the map. */
    private void streams() {
        for (int k = 0; k < peaks.size(); k++) {
            float[] p = peaks.get(k);
            int n = 2 + (p[2] > 30 ? 1 : 0);
            for (int s = 0; s < n; s++) {
                float a = rnd.nextFloat() * (float) Math.PI * 2;
                for (int tries = 0; tries < 12; tries++, a += 0.5f) {
                    int x = (int) (p[0] + Math.cos(a) * p[2] * 0.32f), y = (int) (p[1] + Math.sin(a) * p[2] * 0.32f);
                    if (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) continue;
                    int i = y * w + x;
                    if (t[i] != City.GRASS && t[i] != City.TREE) continue;
                    if (stream(x, y, true)) break;
                }
            }
        }
        // And one or two from the hills.
        int hill = size() >= CityConfig.MASSIVE ? 2 : 1;
        for (int k = 0; k < hill; k++) {
            int best = -1;
            float high = -1;
            for (int s = 0; s < 200; s++) {
                int i = rnd.nextInt(w * h);
                if (wild[i] < 6 || mount[i] > 0.05f || (t[i] != City.GRASS && t[i] != City.TREE)) continue;
                if (e[i] > high) {
                    high = e[i];
                    best = i;
                }
            }
            if (best >= 0) stream(best % w, best / w, false);
        }
    }

    private int[] stamp;
    private int stampN;

    /** One stream from (x, y). False if it came to nothing (too short). */
    private boolean stream(int x, int y, boolean mountain) {
        if (stamp == null) stamp = new int[w * h];
        stampN++;
        List<Integer> path = new ArrayList<Integer>();
        float climb = 0;
        boolean pond = false;
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int step = 0; step < w * 2; step++) {
            int i = y * w + x;
            path.add(i);
            stamp[i] = stampN;
            if (step > 0 && t[i] == City.WATER) break;
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) break;
            int bi = -1;
            float bs = Float.MAX_VALUE;
            for (int[] d : nb) {
                int nx = x + d[0], ny = y + d[1], j = ny * w + nx;
                if (stamp[j] == stampN) continue;
                byte k = t[j];
                boolean ok = k == City.GRASS || k == City.TREE || k == City.WATER || k == City.DIRT || k == City.ROCK
                        || (k == City.ROAD && !(nx >= c.townX0 && nx < c.townX1 && ny >= c.townY0 && ny < c.townY1));
                if (!ok) continue;
                // (A little meander, so it doesn't run dead straight down the slope.)
                float s = e[j] + noise(nx / 6f, ny / 6f, 50) * 1.6f + (k == City.ROCK ? 0.8f : 0)
                        // (It keeps away from the edge of town and the farms rather than running along them.)
                        + (k != City.ROAD && k != City.DIRT && wild[j] < 3 ? (3 - wild[j]) * 2.5f : 0);
                // (Water it can join wins.)
                if (k == City.WATER) s -= 1000;
                if (s < bs) {
                    bs = s;
                    bi = j;
                }
            }
            if (bi < 0) break;
            float rise = e[bi] - e[i];
            if (rise > 0) {
                climb += rise;
                // Into a hollow with no way out: it pools into a pond.
                if (climb > 5 || rise > 2.5f) {
                    pond = true;
                    break;
                }
            }
            x = bi % w;
            y = bi / w;
        }
        if (path.size() < 14) return false;
        // The waterfall: where it drops furthest in one step, off the mountain.
        int fall = -1;
        if (mountain) {
            float drop = 1.2f;
            for (int k = 4; k < path.size() * 0.7f; k++) {
                float d = e[path.get(k - 1)] - e[path.get(k)];
                if (d > drop && t[path.get(k)] != City.ROAD && t[path.get(k)] != City.DIRT) {
                    drop = d;
                    fall = k;
                }
            }
        }
        // Carve it: water (a culvert where it passes under a road), wider lower down, the bed always downhill.
        float level = Float.MAX_VALUE;
        for (int k = 0; k < path.size(); k++) {
            int i = path.get(k);
            level = Math.min(level, e[i]) - 0.15f;
            e[i] = level;
            if (t[i] == City.ROAD || t[i] == City.DIRT) {
                c.setBridge(i);
                continue;
            }
            t[i] = City.WATER;
            c.unpine(i);
            // Shallow enough to wade across (since 10.14).
            if (c.cfg.wilds()) c.setFord(i, true);
            if (k > path.size() / 2 && k + 1 < path.size()) {
                int j = path.get(k + 1), dx = j % w - i % w, dy = j / w - i / w;
                int side = (i % w - dy) + (i / w + dx) * w;
                if (side >= 0 && side < w * h && (t[side] == City.GRASS || t[side] == City.TREE) && wild[side] >= 1) {
                    t[side] = City.WATER;
                    c.unpine(side);
                    if (c.cfg.wilds()) c.setFord(side, true);
                    e[side] = level;
                }
            }
        }
        // A little valley either side.
        for (int k = 0; k < path.size(); k++) {
            int i = path.get(k), x0 = i % w, y0 = i / w;
            for (int dy = -2; dy <= 2; dy++)
                for (int dx = -2; dx <= 2; dx++) {
                    int nx = x0 + dx, ny = y0 + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int j = ny * w + nx;
                    if (t[j] == City.WATER) continue;
                    e[j] = Math.min(e[j], e[i] + 1.2f * Math.max(Math.abs(dx), Math.abs(dy)));
                }
        }
        if (fall > 0) {
            int i = path.get(fall), p = path.get(fall - 1);
            int dx = i % w - p % w, dy = i / w - p / w;
            // Crags on each side of the drop, and a plunge pool below.
            for (int s = -1; s <= 1; s += 2)
                for (int a = -1; a <= 0; a++) {
                    int rx = p % w + dx * a - dy * s, ry = p / w + dy * a + dx * s;
                    if (rx < 0 || ry < 0 || rx >= w || ry >= h) continue;
                    int j = ry * w + rx;
                    if (t[j] == City.GRASS || t[j] == City.TREE) {
                        t[j] = City.ROCK;
                        c.unpine(j);
                    }
                }
            if (fall + 2 < path.size()) {
                int q = path.get(fall + 2);
                blob(q % w, q / w, 1.8f, 0);
            }
            // As wide as the stream is at the lip (it may have widened there), centred on the water.
            int lo = 0, hi = 0;
            for (int s = 1; s <= 2; s++) {
                int ax = p % w - dy * s, ay = p / w + dx * s;
                if (ax < 0 || ay < 0 || ax >= w || ay >= h || t[ay * w + ax] != City.WATER) break;
                hi = s;
            }
            for (int s = 1; s <= 2; s++) {
                int ax = p % w + dy * s, ay = p / w - dx * s;
                if (ax < 0 || ay < 0 || ax >= w || ay >= h || t[ay * w + ax] != City.WATER) break;
                lo = s;
            }
            float mid = (hi - lo) * 0.5f, half = (1 + lo + hi) * City.T / 2f;
            float fx = (p % w + 0.5f + dx * 0.5f - dy * mid) * City.T, fy = (p / w + 0.5f + dy * 0.5f + dx * mid) * City.T;
            c.falls.add(new float[]{fx, fy, dx, dy, half});
            float ax = dx != 0 ? City.T : half, ay = dx != 0 ? half : City.T;
            c.addNatureDecor(City.D_FALLS, fx - ax, fy - ay, fx + ax, fy + ay, (dx != 0 ? 1 : 0));
            if (c.falls.size() == 1) c.natureLabel(c.country.fallsName(word()), fx, fy + 22, City.NL_FALLS);
        }
        if (pond) {
            int end = path.get(path.size() - 1);
            blob(end % w, end / w, 2.5f + rnd.nextFloat() * 2, 1);
        }
        return true;
    }

    /** The nature park: round the first mountain (or the wildest country), thick with woods and meadows. */
    private void park() {
        if (size() < CityConfig.MEDIUM) return;
        if (!peaks.isEmpty()) {
            float[] p = peaks.get(0);
            parkX = p[0];
            parkY = p[1];
            parkR = p[2] * 1.35f + 8;
            parkPeak = 0;
        } else {
            int best = -1;
            for (int s = 0; s < 600; s++) {
                int i = rnd.nextInt(w * h);
                if (wild[i] >= 12 && (best < 0 || wild[i] > wild[best])) best = i;
            }
            if (best < 0) return;
            parkX = best % w;
            parkY = best / w;
            parkR = wild[best] * 1.6f;
        }
        int reach = (int) parkR + 1;
        for (int y = Math.max(1, (int) parkY - reach); y < Math.min(h - 1, (int) parkY + reach); y++)
            for (int x = Math.max(1, (int) parkX - reach); x < Math.min(w - 1, (int) parkX + reach); x++) {
                int i = y * w + x;
                if (t[i] != City.GRASS || wild[i] < 2) continue;
                float d = (float) Math.hypot(x - parkX, y - parkY) / parkR;
                if (d > 1) continue;
                // Woods with meadows in them (as thick as the country's woods are).
                if (fbm(x / 11f, y / 11f, 60) > 0.47f + (1 - Math.min(1.4f, land.forest)) * 0.12f
                        && rnd.nextFloat() < 0.75f * Math.min(1, land.forest + 0.1f)) {
                    t[i] = City.TREE;
                    c.setTreeKind(i, mount[i] > 0.08f || noise(x / 20f, y / 20f, 70) < land.pineShare ? Country.TK_PINE : land.lowTree);
                }
            }
        String name = c.country.parkName(word());
        c.natureLabel(name, (parkX + 0.5f) * City.T, (parkY + 0.5f + (parkPeak >= 0 ? peaks.get(0)[2] * 0.55f : 0)) * City.T, City.NL_PARK);
        c.parkName = name;
        c.parkX = (parkX + 0.5f) * City.T;
        c.parkY = (parkY + 0.5f) * City.T;
        c.parkR = parkR * City.T;
        for (int k = 0; k < peaks.size(); k++) {
            float[] p = peaks.get(k);
            c.natureLabel(c.country.mountainName(word()), (p[0] + 0.5f) * City.T, (p[1] + 0.5f) * City.T - 26, City.NL_PEAK);
        }
    }

    // ------------------------------------------------------------------ places along the lanes

    /**
     * A clear rectangle beside a country lane, near (tx, ty): {x, y, w, h, side tile x, side tile y} with the
     * rectangle's near edge on the lane's verge (the lane at side tile). Null if there's no room.
     */
    private int[] siteBeside(float tx, float ty, int rw, int rh, float within) {
        int[] best = null;
        float bd = Float.MAX_VALUE;
        // (Every tile of every lane, starting somewhere random.)
        int start = rnd.nextInt(w * h);
        for (int s = 0; s < w * h; s++) {
            int i = (start + s) % (w * h);
            if (t[i] != City.DIRT) continue;
            int x = i % w, y = i / w;
            if (x >= c.townX0 - 4 && x < c.townX1 + 4 && y >= c.townY0 - 4 && y < c.townY1 + 4) continue;
            float d = (float) Math.hypot(x - tx, y - ty);
            if (d > within || d > bd) continue;
            boolean vertical = (y > 0 && t[i - w] == City.DIRT) && (y < h - 1 && t[i + w] == City.DIRT);
            boolean horizontal = (x > 0 && t[i - 1] == City.DIRT) && (x < w - 1 && t[i + 1] == City.DIRT);
            if (vertical == horizontal) continue;
            // The far side of a two-tile lane.
            for (int side = -1; side <= 1; side += 2) {
                int rx, ry, ww = vertical ? rw : rh, hh = vertical ? rh : rw;
                if (vertical) {
                    int edge = x;
                    while (edge + side >= 0 && edge + side < w && t[y * w + edge + side] == City.DIRT) edge += side;
                    rx = side > 0 ? edge + 1 : edge - ww;
                    ry = y - hh / 2;
                } else {
                    int edge = y;
                    while (edge + side >= 0 && edge + side < h && t[(edge + side) * w + x] == City.DIRT) edge += side;
                    ry = side > 0 ? edge + 1 : edge - hh;
                    rx = x - ww / 2;
                }
                if (!clear(rx, ry, ww, hh)) continue;
                bd = d;
                best = new int[]{rx, ry, ww, hh, vertical ? (side > 0 ? -1 : 1) : 0, vertical ? 0 : (side > 0 ? -1 : 1)};
            }
        }
        return best;
    }

    /** All grass or trees (no water, rock, roads or buildings). */
    private boolean clear(int x, int y, int fw, int fh) {
        if (x < 1 || y < 1 || x + fw >= w - 1 || y + fh >= h - 1) return false;
        for (int j = y; j < y + fh; j++)
            for (int i = x; i < x + fw; i++) {
                byte k = t[j * w + i];
                if (k != City.GRASS && k != City.TREE) return false;
                if (c.bridge != null && c.bridge[j * w + i]) return false;
            }
        return true;
    }

    /** A cell of a site, in site coordinates (u across from the lane, v along it). */
    private static int[] cell(int[] s, int u, int v) {
        // s[4], s[5]: the way back towards the lane.
        if (s[4] != 0) return new int[]{s[4] < 0 ? s[0] + u : s[0] + s[2] - 1 - u, s[1] + v};
        return new int[]{s[0] + v, s[5] < 0 ? s[1] + u : s[1] + s[3] - 1 - u};
    }

    private void setTile(int[] p, byte k) {
        int i = p[1] * w + p[0];
        t[i] = k;
        c.unpine(i);
    }

    /** The trailhead (tile) the trails start from. */
    private int trailhead = -1;
    private final List<int[]> trailTargets = new ArrayList<int[]>();

    /** Campsites: one in the park, the rest beside lakes and woods along the lanes. */
    private void campsites() {
        int want = size() >= CityConfig.HUGE ? 3 : size() >= CityConfig.MASSIVE ? 2 : size() >= CityConfig.LARGE ? 2 : size() >= CityConfig.SMALL ? 1 : 0;
        // The visitor centre comes first, at the park gate (with a track out to it if no lane comes near).
        if (parkR > 0) {
            visitorCentre();
            if (trailhead < 0 && accessLane()) visitorCentre();
        }
        for (int k = 0; k < want; k++) {
            float tx, ty, within;
            if (k == 0 && parkR > 0) {
                tx = parkX;
                ty = parkY;
                within = parkR * 1.8f;
            } else if (k - (parkR > 0 ? 1 : 0) < lakes.size()) {
                float[] l = lakes.get(k - (parkR > 0 ? 1 : 0));
                tx = l[0];
                ty = l[1];
                within = 60;
            } else {
                tx = rnd.nextInt(w);
                ty = rnd.nextInt(h);
                within = w;
            }
            int[] s = siteBeside(tx, ty, 13, 17, within);
            if (s == null) continue;
            campsite(s, k == 0 && parkR > 0);
        }
    }

    /** A dirt track from the nearest lane or road out to the edge of the park. False if there's no way. */
    private boolean accessLane() {
        int[] d = new int[w * h];
        int[] q = new int[w * h];
        Arrays.fill(d, Integer.MAX_VALUE);
        int tail = 0;
        for (int i = 0; i < w * h; i++)
            if (t[i] == City.DIRT || t[i] == City.ROAD) {
                d[i] = 0;
                q[tail++] = i;
            }
        int head = 0;
        while (head < tail) {
            int i = q[head++], x = i % w, y = i / w;
            int[] nb = {x > 1 ? i - 1 : -1, x < w - 2 ? i + 1 : -1, y > 1 ? i - w : -1, y < h - 2 ? i + w : -1};
            for (int j : nb) {
                if (j < 0 || d[j] != Integer.MAX_VALUE) continue;
                byte k = t[j];
                if (k != City.GRASS && k != City.TREE && k != City.WATER) continue;
                d[j] = d[i] + 1;
                q[tail++] = j;
            }
        }
        // Where the park's edge comes closest to a road.
        int start = -1;
        for (int a = 0; a < 24; a++) {
            double ang = a * Math.PI / 12;
            int x = (int) (parkX + Math.cos(ang) * parkR * 0.75f), y = (int) (parkY + Math.sin(ang) * parkR * 0.75f);
            if (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) continue;
            int i = y * w + x;
            if (d[i] == Integer.MAX_VALUE || t[i] == City.WATER) continue;
            if (start < 0 || d[i] < d[start]) start = i;
        }
        if (start < 0) return false;
        for (int i = start, guard = 0; d[i] > 0 && guard < w * 3; guard++) {
            int x = i % w, y = i / w;
            for (int k = 0; k < 2; k++) {
                int j = y * w + x + k;
                if (t[j] == City.WATER) {
                    t[j] = City.DIRT;
                    c.setBridge(j);
                } else if (t[j] == City.GRASS || t[j] == City.TREE) {
                    t[j] = City.DIRT;
                    c.unpine(j);
                }
            }
            int[] nb = {i - 1, i + 1, i - w, i + w};
            int next = -1;
            for (int j : nb) if (j >= 0 && j < w * h && d[j] < d[i] && (next < 0 || d[j] < d[next])) next = j;
            if (next < 0) break;
            i = next;
        }
        return true;
    }

    private void visitorCentre() {
        int[] s = siteBeside(parkX, parkY, 9, 10, parkR * 2.2f);
        if (s == null) return;
        for (int u = 0; u < 9; u++)
            for (int v = 0; v < 10; v++) setTile(cell(s, u, v), City.GRASS);
        // Parking by the lane, the visitor centre behind.
        for (int u = 0; u < 3; u++)
            for (int v = 1; v < 9; v++) setTile(cell(s, u, v), City.LOT);
        int[] a = cell(s, 4, 2), b = cell(s, 7, 7);
        int x0 = Math.min(a[0], b[0]), y0 = Math.min(a[1], b[1]);
        c.natureLot(x0, y0, Math.abs(a[0] - b[0]) + 1, Math.abs(a[1] - b[1]) + 1, City.OFFICE, 1, 0xFF6A4A32, 0xFFB09068,
                c.country.parkName(null), 0);
        int[] head = cell(s, 3, 0);
        trailhead = head[1] * w + head[0];
        setTile(head, City.TRAIL);
        c.addNatureDecor(City.D_SIGN, head[0] * City.T, head[1] * City.T, (head[0] + 1) * City.T, (head[1] + 1) * City.T, 0);
    }

    private void campsite(int[] s, boolean inPark) {
        for (int u = 0; u < 13; u++)
            for (int v = 0; v < 17; v++) {
                int[] p = cell(s, u, v);
                // A clearing in the trees: a ring of them round the edge (the way in left open).
                boolean edge = u == 12 || ((v == 0 || v == 16) && u > 3);
                setTile(p, edge && rnd.nextFloat() < 0.7f ? City.TREE : City.GRASS);
            }
        String name = c.country.campName(word());
        // The track in, and parking by the camp store.
        for (int u = 0; u < 4; u++) {
            setTile(cell(s, u, 7), City.DIRT);
            setTile(cell(s, u, 8), City.DIRT);
        }
        for (int u = 0; u < 3; u++)
            for (int v = 9; v < 12; v++) setTile(cell(s, u, v), City.LOT);
        int[] a = cell(s, 0, 12), b = cell(s, 2, 15);
        int tents = 6 + rnd.nextInt(5);
        c.natureLot(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.abs(a[0] - b[0]) + 1, Math.abs(a[1] - b[1]) + 1,
                City.SHOP, 1, 0xFF5A6A3A, 0xFFC8B088, "Camp Store, " + name, tents * 2);
        // Cabins along the back.
        int cabins = 2 + rnd.nextInt(2);
        for (int k = 0; k < cabins; k++) {
            int[] p = cell(s, 9, 1 + k * 5), q = cell(s, 11, 3 + k * 5);
            c.natureLot(Math.min(p[0], q[0]), Math.min(p[1], q[1]), Math.abs(p[0] - q[0]) + 1, Math.abs(p[1] - q[1]) + 1,
                    City.HOUSE, 1, 0xFF6E4A30, 0xFF9A7048, "Cabin " + (k + 1) + ", " + name, -1);
        }
        // Tent pitches with fire rings and picnic tables.
        int[][] pitches = {{5, 1}, {5, 4}, {5, 11}, {5, 14}, {7, 2}, {7, 13}, {4, 15}, {6, 8}, {8, 6}, {8, 10}};
        for (int k = 0; k < tents && k < pitches.length; k++) {
            int[] p = cell(s, pitches[k][0], pitches[k][1]);
            float x = (p[0] + 0.5f) * City.T, y = (p[1] + 0.5f) * City.T;
            c.addNatureDecor(City.D_TENT, x - 9, y - 7, x + 9, y + 7, rnd.nextInt(6) * 2 + (s[4] != 0 ? 1 : 0));
            if (k % 2 == 0) c.addNatureDecor(City.D_CAMPFIRE, x + 10, y + 6, x + 18, y + 14, 0);
            else c.addNatureDecor(City.D_PICNIC, x + 8, y - 4, x + 20, y + 6, s[4] != 0 ? 1 : 0);
        }
        int[] mid = cell(s, 6, 8);
        float mx = (mid[0] + 0.5f) * City.T, my = (mid[1] + 0.5f) * City.T;
        c.natureLabel(name, mx, my - 30, City.NL_CAMP);
        c.settlements.add(new float[]{mx, my});
        c.openAreas.add(new float[]{mx, my, 0});
        if (inPark) trailTargets.add(cell(s, 12, 8));
    }

    // ------------------------------------------------------------------ hiking trails

    /** Trails from the trailhead up to a lookout, to the falls, the lake shore and the campsite. */
    private void trails() {
        if (trailhead < 0) return;
        float limit = Math.max(parkR * 1.7f, (float) Math.hypot(trailhead % w - parkX, trailhead / w - parkY) + parkR * 0.5f);
        int[] cost = new int[w * h];
        int[] from = new int[w * h];
        Arrays.fill(cost, Integer.MAX_VALUE);
        PriorityQueue<long[]> pq = new PriorityQueue<long[]>(64, new java.util.Comparator<long[]>() {
            @Override
            public int compare(long[] a, long[] b) {
                return Long.compare(a[0], b[0]);
            }
        });
        cost[trailhead] = 0;
        from[trailhead] = -1;
        pq.add(new long[]{0, trailhead});
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            int i = (int) top[1];
            if (top[0] > cost[i]) continue;
            int x = i % w, y = i / w;
            for (int[] d : nb) {
                int nx = x + d[0], ny = y + d[1];
                if (nx < 1 || ny < 1 || nx >= w - 1 || ny >= h - 1) continue;
                if (Math.hypot(nx - parkX, ny - parkY) > limit) continue;
                int j = ny * w + nx;
                byte k = t[j];
                int step;
                if (k == City.GRASS || k == City.TRAIL) step = 10;
                else if (k == City.TREE) step = 22;
                else if (k == City.WATER && !(c.bridge != null && c.bridge[j]) && narrow(nx, ny)) step = 160;
                else continue;
                step += (int) (Math.abs(e[j] - e[i]) * 30);
                int nc = cost[i] + step;
                if (nc < cost[j]) {
                    cost[j] = nc;
                    from[j] = i;
                    pq.add(new long[]{nc, j});
                }
            }
        }
        // The lookout: the highest spot the trail can reach on the mountain.
        List<int[]> goals = new ArrayList<int[]>(trailTargets);
        if (parkPeak >= 0) {
            float[] p = peaks.get(parkPeak);
            int best = -1;
            for (int y = Math.max(0, (int) (p[1] - p[2])); y < Math.min(h, (int) (p[1] + p[2])); y++)
                for (int x = Math.max(0, (int) (p[0] - p[2])); x < Math.min(w, (int) (p[0] + p[2])); x++) {
                    int i = y * w + x;
                    if (cost[i] == Integer.MAX_VALUE || t[i] == City.WATER) continue;
                    if (best < 0 || e[i] > e[best]) best = i;
                }
            if (best >= 0) {
                goals.add(new int[]{best % w, best / w});
                float lx = (best % w + 0.5f) * City.T, ly = (best / w + 0.5f) * City.T;
                c.addNatureDecor(City.D_LOOKOUT, lx - 10, ly - 10, lx + 10, ly + 10, 0);
            }
        }
        for (float[] f : c.falls) {
            int fx = (int) (f[0] / City.T), fy = (int) (f[1] / City.T);
            int best = nearestReached(cost, fx, fy, 4);
            if (best >= 0) goals.add(new int[]{best % w, best / w});
        }
        for (float[] l : lakes) {
            if (Math.hypot(l[0] - parkX, l[1] - parkY) > limit) continue;
            int best = nearestReached(cost, (int) l[0], (int) l[1], (int) l[2] + 4);
            if (best >= 0) goals.add(new int[]{best % w, best / w});
        }
        for (int[] g : goals) {
            int i = g[1] * w + g[0];
            if (cost[i] == Integer.MAX_VALUE) continue;
            for (int guard = 0; i >= 0 && guard < w * 4; guard++) {
                if (t[i] == City.WATER) c.setBridge(i);
                if (t[i] != City.LOT && t[i] != City.DIRT) {
                    t[i] = City.TRAIL;
                    c.unpine(i);
                }
                i = from[i];
            }
        }
    }

    /** Water a footbridge can cross: a stream, not the middle of a lake. */
    private boolean narrow(int x, int y) {
        int water = 0;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) if (t[(y + dy) * w + x + dx] == City.WATER) water++;
        return water <= 6;
    }

    private int nearestReached(int[] cost, int cx, int cy, int r) {
        int best = -1;
        float bd = Float.MAX_VALUE;
        for (int y = Math.max(0, cy - r); y <= Math.min(h - 1, cy + r); y++)
            for (int x = Math.max(0, cx - r); x <= Math.min(w - 1, cx + r); x++) {
                int i = y * w + x;
                if (cost[i] == Integer.MAX_VALUE || t[i] == City.WATER) continue;
                float d = (float) Math.hypot(x - cx, y - cy);
                if (d < bd) {
                    bd = d;
                    best = i;
                }
            }
        return best;
    }

    // ------------------------------------------------------------------ the wilds (10.14)

    /** The ranger station, beside the visitor centre at the park gate. */
    private void rangerStation() {
        int[] s0 = siteBeside(parkX, parkY, 8, 9, parkR * 2.4f);
        if (s0 == null) return;
        c.clearDecorIn(s0[0], s0[1], s0[2], s0[3]);
        for (int u = 0; u < 8; u++)
            for (int v = 0; v < 9; v++) setTile(cell(s0, u, v), City.GRASS);
        // The trucks park out front.
        for (int u = 0; u < 3; u++)
            for (int v = 1; v < 8; v++) setTile(cell(s0, u, v), City.LOT);
        int[] a = cell(s0, 4, 2), b = cell(s0, 7, 6);
        String name = c.country.rangerStationName(c.parkName);
        c.natureLot(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.abs(a[0] - b[0]) + 1, Math.abs(a[1] - b[1]) + 1,
                City.OFFICE, 1, 0xFF4E5A3A, 0xFFA88C62, name, 0);
        c.rangerStationName = name;
        int[] lot = cell(s0, 1, 4);
        c.rangerLot = new float[]{(lot[0] + 0.5f) * City.T, (lot[1] + 0.5f) * City.T};
    }

    /** Fire lookout towers up on the wooded high ground, each with a path out to it. */
    private void fireTowers() {
        int want = size() >= CityConfig.HUGE ? 3 : size() >= CityConfig.LARGE ? 2 : size() >= CityConfig.MEDIUM ? 1 : 0;
        if (land.desert && land.forest < 0.3f) want = Math.min(want, 1);
        for (int k = 0; k < want; k++) {
            int best = -1;
            float bs = 0;
            for (int s = 0; s < 1500; s++) {
                int i = rnd.nextInt(w * h), x = i % w, y = i / w;
                if (x < 4 || y < 4 || x >= w - 4 || y >= h - 4 || wild[i] < 8) continue;
                if (t[i] != City.GRASS && t[i] != City.TREE) continue;
                boolean far = true;
                for (float[] f : c.fireTowers) if (Math.hypot(f[0] / City.T - x, f[1] / City.T - y) < 70) far = false;
                if (!far) continue;
                // High up, among the trees (that's what it's watching).
                int trees = 0;
                for (int dy = -6; dy <= 6; dy += 2)
                    for (int dx = -6; dx <= 6; dx += 2) {
                        int j = (y + dy) * w + x + dx;
                        if (j >= 0 && j < w * h && t[j] == City.TREE) trees++;
                    }
                if (trees < 10 && mount[i] < 0.2f) continue;
                float score = e[i] + trees * 0.4f;
                if (score > bs) {
                    bs = score;
                    best = i;
                }
            }
            if (best < 0) break;
            int x = best % w, y = best / w;
            // A clearing round its legs.
            for (int dy = -2; dy <= 2; dy++)
                for (int dx = -2; dx <= 2; dx++) {
                    int j = (y + dy) * w + x + dx;
                    if (t[j] == City.TREE || t[j] == City.ROCK) {
                        t[j] = City.GRASS;
                        c.unpine(j);
                    }
                }
            float fx = (x + 0.5f) * City.T, fy = (y + 0.5f) * City.T;
            c.fireTowers.add(new float[]{fx, fy});
            c.fireTowerNames.add(c.country.lookoutName(word()));
            c.addNatureDecor(City.D_FIRETOWER, fx - 14, fy - 14, fx + 14, fy + 14, 0);
        }
    }

    /** What it costs to walk a trail onto this tile, or -1 if a trail can't go there. */
    private int trailStep(int j) {
        byte k = t[j];
        int x = j % w, y = j / w;
        if (k == City.GRASS || k == City.TRAIL || k == City.LOT) return 10;
        if (k == City.TREE) return 22;
        if (k == City.SAND) return 14;
        // A footbridge over a stream, not across a lake.
        if (k == City.WATER) return c.isFord(j) || narrow(x, y) ? 90 : -1;
        // Across a lane, a road out of town or the railway, at a crossing.
        if (k == City.DIRT) return 40;
        if (k == City.ROAD) return (c.bridge != null && c.bridge[j]) || (x >= c.townX0 && x < c.townX1 && y >= c.townY0 && y < c.townY1) ? -1 : 140;
        if (k == City.RAIL) return 170;
        return -1;
    }

    /**
     * Cheapest walking routes out from start (any of the tiles given), over grass and through the woods,
     * winding a little, round the steepest ground: cost[] gets the costs, the result where each step came from.
     * Only within limit tiles of (cx, cy).
     */
    private int[] trailSearch(int[] starts, float cx, float cy, float limit, int[] cost) {
        int[] from = new int[w * h];
        Arrays.fill(cost, Integer.MAX_VALUE);
        PriorityQueue<long[]> pq = new PriorityQueue<long[]>(64, new java.util.Comparator<long[]>() {
            @Override
            public int compare(long[] a, long[] b) {
                return Long.compare(a[0], b[0]);
            }
        });
        for (int st : starts) {
            cost[st] = 0;
            from[st] = -1;
            pq.add(new long[]{0, st});
        }
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            int i = (int) top[1];
            if (top[0] > cost[i]) continue;
            int x = i % w, y = i / w;
            for (int[] d : nb) {
                int nx = x + d[0], ny = y + d[1];
                if (nx < 1 || ny < 1 || nx >= w - 1 || ny >= h - 1) continue;
                if (Math.hypot(nx - cx, ny - cy) > limit) continue;
                int j = ny * w + nx;
                int step = trailStep(j);
                if (step < 0) continue;
                if (d[0] != 0 && d[1] != 0) {
                    // Cutting a corner only where both sides are open going (never between two crossings).
                    int sa = trailStep(y * w + nx), sb = trailStep(ny * w + x);
                    if (sa < 0 || sb < 0 || sa > 30 || sb > 30 || step > 30) continue;
                    step = step * 14 / 10;
                }
                // Round the steep bits, and a gentle meander so it isn't ruler-straight across a meadow.
                step += (int) (Math.abs(e[j] - e[i]) * 30) + (int) (noise(nx / 5f, ny / 5f, 70) * 9);
                int nc = cost[i] + step;
                if (nc < cost[j]) {
                    cost[j] = nc;
                    from[j] = i;
                    pq.add(new long[]{nc, j});
                }
            }
        }
        return from;
    }

    /** Tiles already on a trail (so a new one joins the path instead of running alongside it). */
    private boolean[] onTrail;

    /**
     * Lays a trail back from goal along the search's came-from map: the tiles become trail (footbridges over
     * the water), the line drawn through them goes on the map, and the whole way from the start is a hike.
     */
    private void layTrail(int goal, int[] from, int[] cost, int hikeKind) {
        if (cost[goal] == Integer.MAX_VALUE) return;
        if (onTrail == null) onTrail = new boolean[w * h];
        List<Integer> path = new ArrayList<Integer>();
        for (int i = goal, guard = 0; i >= 0 && guard < w * 6; guard++) {
            path.add(i);
            i = from[i];
        }
        java.util.Collections.reverse(path);
        if (path.size() < 4) return;
        // The hike: the whole way, start to goal.
        float[] hike = new float[path.size() * 2];
        for (int k = 0; k < path.size(); k++) {
            hike[k * 2] = (path.get(k) % w + 0.5f) * City.T;
            hike[k * 2 + 1] = (path.get(k) / w + 0.5f) * City.T;
        }
        c.hikes.add(hike);
        c.hikeKinds.add(hikeKind);
        // The new stretch: from the goal back until it meets a trail already there.
        int stop = 0;
        for (int k = path.size() - 1; k > 0; k--)
            if (onTrail[path.get(k)]) {
                stop = k;
                break;
            }
        List<Float> line = new ArrayList<Float>();
        for (int k = stop; k < path.size(); k++) {
            int i = path.get(k);
            byte kind = t[i];
            boolean crossing = kind == City.DIRT || kind == City.ROAD || kind == City.RAIL || kind == City.LOT;
            if (crossing) {
                // The trail stops at the verge and carries on the far side.
                if (kind == City.ROAD || kind == City.RAIL) c.trailCrossings.add(new float[]{(i % w + 0.5f) * City.T, (i / w + 0.5f) * City.T, kind});
                if (!line.isEmpty()) {
                    line.add((i % w + 0.5f) * City.T);
                    line.add((i / w + 0.5f) * City.T);
                    flushLine(line);
                }
                continue;
            }
            if (line.isEmpty() && k > stop) {
                // (Starting again from the crossing just passed.)
                int p = path.get(k - 1);
                line.add((p % w + 0.5f) * City.T);
                line.add((p / w + 0.5f) * City.T);
            }
            line.add((i % w + 0.5f) * City.T);
            line.add((i / w + 0.5f) * City.T);
            if (kind == City.WATER) {
                c.setBridge(i);
                c.setFord(i, false);
            }
            if (kind != City.TRAIL) {
                t[i] = City.TRAIL;
                c.unpine(i);
            }
            onTrail[i] = true;
            // A diagonal step: the corner it cuts is open ground too, so nobody snags on a tree there.
            if (k > 0) {
                int p = path.get(k - 1), px = p % w, py = p / w, x = i % w, y = i / w;
                if (px != x && py != y) {
                    int ca = py * w + x, cb = y * w + px;
                    int corner = t[ca] == City.GRASS || t[ca] == City.TREE ? ca : t[cb] == City.GRASS || t[cb] == City.TREE ? cb : -1;
                    if (corner >= 0) {
                        t[corner] = City.TRAIL;
                        c.unpine(corner);
                    }
                }
            }
        }
        flushLine(line);
    }

    private void flushLine(List<Float> line) {
        if (line.size() >= 4) {
            float[] a = new float[line.size()];
            for (int k = 0; k < a.length; k++) a[k] = line.get(k);
            c.trailLines.add(a);
        }
        line.clear();
    }

    /** The trails: the park's network from the trailhead, nature walks from the campsites, paths to the towers. */
    private void wildTrails() {
        int[] cost = new int[w * h];
        if (parkR > 0 && trailhead >= 0) {
            float limit = Math.max(parkR * 1.7f, (float) Math.hypot(trailhead % w - parkX, trailhead / w - parkY) + parkR * 0.5f);
            int[] from = trailSearch(new int[]{trailhead}, parkX, parkY, limit, cost);
            c.trailheadX = (trailhead % w + 0.5f) * City.T;
            c.trailheadY = (trailhead / w + 0.5f) * City.T;
            // Up to the lookout on the mountain.
            if (parkPeak >= 0) {
                float[] p = peaks.get(parkPeak);
                int best = -1;
                for (int y = Math.max(0, (int) (p[1] - p[2])); y < Math.min(h, (int) (p[1] + p[2])); y++)
                    for (int x = Math.max(0, (int) (p[0] - p[2])); x < Math.min(w, (int) (p[0] + p[2])); x++) {
                        int i = y * w + x;
                        if (cost[i] == Integer.MAX_VALUE || t[i] == City.WATER || t[i] == City.ROAD || t[i] == City.RAIL || t[i] == City.DIRT) continue;
                        if (best < 0 || e[i] > e[best]) best = i;
                    }
                if (best >= 0) {
                    float lx = (best % w + 0.5f) * City.T, ly = (best / w + 0.5f) * City.T;
                    c.addNatureDecor(City.D_LOOKOUT, lx - 10, ly - 10, lx + 10, ly + 10, 0);
                    layTrail(best, from, cost, City.HIKE_LOOKOUT);
                }
            }
            for (float[] f : c.falls) {
                int best = nearestReached(cost, (int) (f[0] / City.T), (int) (f[1] / City.T), 4);
                if (best >= 0) layTrail(best, from, cost, City.HIKE_FALLS);
            }
            for (float[] l : lakes) {
                if (Math.hypot(l[0] - parkX, l[1] - parkY) > limit) continue;
                int best = nearestReached(cost, (int) l[0], (int) l[1], (int) l[2] + 4);
                if (best >= 0) layTrail(best, from, cost, City.HIKE_LAKE);
            }
            for (int[] g : trailTargets) layTrail(g[1] * w + g[0], from, cost, City.HIKE_CAMP);
            for (float[] tw : c.fireTowers) {
                if (Math.hypot(tw[0] / City.T - parkX, tw[1] / City.T - parkY) > limit) continue;
                int best = nearestReached(cost, (int) (tw[0] / City.T), (int) (tw[1] / City.T), 3);
                if (best >= 0) layTrail(best, from, cost, City.HIKE_TOWER);
            }
            // A loop out through the far woods and back.
            for (int k = 0; k < 2; k++) {
                double a = Math.atan2(parkY - trailhead / w, parkX - trailhead % w) + (k == 0 ? 0.8 : -0.8);
                int best = nearestReached(cost, (int) (parkX + Math.cos(a) * parkR * 0.8f), (int) (parkY + Math.sin(a) * parkR * 0.8f), 6);
                if (best >= 0) layTrail(best, from, cost, City.HIKE_WOODS);
            }
        }
        // Each campsite outside the park: a walk down to the nearest water or into the woods.
        for (float[] camp : c.settlements) {
            int cx = (int) (camp[0] / City.T), cy = (int) (camp[1] / City.T);
            if (parkR > 0 && Math.hypot(cx - parkX, cy - parkY) < parkR * 1.2f) continue;
            if (!c.isCampAt(camp[0], camp[1])) continue;
            int st = nearestOpen(cx, cy, 4);
            if (st < 0) continue;
            int[] from = trailSearch(new int[]{st}, cx, cy, 55, cost);
            int goal = -1;
            for (float[] l : lakes)
                if (Math.hypot(l[0] - cx, l[1] - cy) < 50) goal = nearestReached(cost, (int) l[0], (int) l[1], (int) l[2] + 4);
            if (goal < 0) {
                // Out into the thickest woods near by.
                int bestTrees = 0;
                for (int s = 0; s < 300; s++) {
                    int x = cx + rnd.nextInt(80) - 40, y = cy + rnd.nextInt(80) - 40;
                    if (x < 3 || y < 3 || x >= w - 3 || y >= h - 3 || Math.hypot(x - cx, y - cy) < 20) continue;
                    int i = y * w + x;
                    if (cost[i] == Integer.MAX_VALUE || trailStep(i) > 30) continue;
                    int trees = 0;
                    for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) if (t[(y + dy) * w + x + dx] == City.TREE) trees++;
                    if (trees > bestTrees) {
                        bestTrees = trees;
                        goal = i;
                    }
                }
            }
            if (goal >= 0) layTrail(goal, from, cost, City.HIKE_WOODS);
        }
        // The towers outside the park: a path out from the nearest lane or trail.
        for (float[] tw : c.fireTowers) {
            int tx = (int) (tw[0] / City.T), ty = (int) (tw[1] / City.T);
            if (parkR > 0 && Math.hypot(tx - parkX, ty - parkY) < parkR * 1.7f && onTrail != null && nearOnTrail(tx, ty, 4)) continue;
            List<Integer> ends = new ArrayList<Integer>();
            for (int y = Math.max(1, ty - 60); y < Math.min(h - 1, ty + 60); y++)
                for (int x = Math.max(1, tx - 60); x < Math.min(w - 1, tx + 60); x++) {
                    int i = y * w + x;
                    if (t[i] == City.DIRT || (onTrail != null && onTrail[i])) ends.add(i);
                }
            if (ends.isEmpty()) continue;
            int[] st = new int[ends.size()];
            for (int k = 0; k < st.length; k++) st[k] = ends.get(k);
            int[] from = trailSearch(st, tx, ty, 90, cost);
            int best = nearestReached(cost, tx, ty, 3);
            if (best >= 0) layTrail(best, from, cost, City.HIKE_TOWER);
        }
    }

    private boolean nearOnTrail(int x, int y, int r) {
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++) {
                int j = (y + dy) * w + x + dx;
                if (j >= 0 && j < w * h && onTrail[j]) return true;
            }
        return false;
    }

    /** The nearest open grass (or trail) tile to (x, y), within r, or -1. */
    private int nearestOpen(int x, int y, int r) {
        int best = -1;
        float bd = Float.MAX_VALUE;
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx < 1 || ny < 1 || nx >= w - 1 || ny >= h - 1) continue;
                int i = ny * w + nx;
                if (t[i] != City.GRASS && t[i] != City.TRAIL) continue;
                float d = dx * dx + dy * dy;
                if (d < bd) {
                    bd = d;
                    best = i;
                }
            }
        return best;
    }
}
