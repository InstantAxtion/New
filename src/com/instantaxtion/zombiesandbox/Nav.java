package com.instantaxtion.zombiesandbox;

import java.util.Arrays;

/**
 * (10.36) Walking routes for everyone on foot, the living and the dead: hierarchical pathfinding.
 *
 * The map is cut into 16x16-tile clusters. Where two clusters meet, each stretch of open ground across the border
 * is a gateway (a pair of tiles, one either side). Within a cluster, how far it is between each pair of its
 * gateways is worked out the first time it's needed. A long trip is planned across the clusters' gateways first
 * (a few hundred steps however far it is, instead of tens of thousands of tiles), then filled in tile by tile one
 * cluster at a time, and finally straightened: the corners cut wherever there's a clear, no worse way, so people
 * walk in lines, not staircases, and keep a step back from walls.
 *
 * Costs are the city's walking costs (pavements, crossings, the road only where they must), and a tile right up
 * against a wall or a building costs a little more, so routes don't scrape round corners. When the map changes
 * (a barricade, a wall going up, a building coming down) the clusters it touches are worked out again.
 */
final class Nav {
    static final int CS = 16;
    private static final int FAR = Integer.MAX_VALUE / 4;

    private final City c;
    private final int w, h, cw, ch;

    // Gateways: a tile each, the cluster it's in, its partner across the border and the cost of the step over.
    private int nodes;
    private int[] nodeTile = new int[1024], nodeCluster = new int[1024], nodePartner = new int[1024], nodeCross = new int[1024];
    private int[][] clusterNodes;
    private int[] clusterCount;
    /** Within each cluster, the cost from each of its gateways to each other one (FAR if there's no way). */
    private int[][] intra;
    private boolean[] intraReady;
    private boolean entrancesDirty = true;

    Nav(City c) {
        this.c = c;
        this.w = c.w;
        this.h = c.h;
        cw = (w + CS - 1) / CS;
        ch = (h + CS - 1) / CS;
    }

    /** A tile's walkability changed: the cluster it's in (and, on a border, the gateways) need doing again. */
    void touch(int i) {
        if (intraReady == null) return;
        int x = i % w, y = i / w, k = (y / CS) * cw + x / CS;
        intraReady[k] = false;
        int lx = x % CS, ly = y % CS;
        if (lx == 0 || ly == 0 || lx == CS - 1 || ly == CS - 1) entrancesDirty = true;
    }

    private boolean open(int i) {
        return !c.solid[i] || c.gateTile(i);
    }

    /** What stepping onto tile i costs (x10, before the diagonal's extra), a little more right beside a wall. */
    private int cost(int i) {
        int base = c.walkCost[i] * 10;
        int x = i % w, y = i / w;
        if ((x > 0 && c.solid[i - 1]) || (x < w - 1 && c.solid[i + 1]) || (y > 0 && c.solid[i - w]) || (y < h - 1 && c.solid[i + w]))
            base += 6;
        return base;
    }

    // ------------------------------------------------------------------ the gateways

    private void buildEntrances() {
        nodes = 0;
        clusterCount = new int[cw * ch];
        clusterNodes = new int[cw * ch][];
        intra = new int[cw * ch][];
        intraReady = new boolean[cw * ch];
        for (int cy = 0; cy < ch; cy++)
            for (int cx = 0; cx < cw; cx++) {
                // The border on the right, then the one below.
                if (cx + 1 < cw) {
                    int x = (cx + 1) * CS - 1;
                    scanBorder(x, cy * CS, Math.min(h, (cy + 1) * CS), true);
                }
                if (cy + 1 < ch) {
                    int y = (cy + 1) * CS - 1;
                    scanBorder(y, cx * CS, Math.min(w, (cx + 1) * CS), false);
                }
            }
        // Each cluster's own list of its gateways.
        for (int k = 0; k < cw * ch; k++) clusterNodes[k] = new int[clusterCount[k]];
        int[] fill = new int[cw * ch];
        for (int n = 0; n < nodes; n++) {
            int k = nodeCluster[n];
            clusterNodes[k][fill[k]++] = n;
        }
        entrancesDirty = false;
    }

    /** Along one border (at column or row `at`, from a0 to a1), a gateway in each stretch open on both sides. */
    private void scanBorder(int at, int a0, int a1, boolean vertical) {
        int run = -1;
        for (int a = a0; a <= a1; a++) {
            boolean ok = false;
            if (a < a1) {
                int p = vertical ? a * w + at : at * w + a, q = vertical ? p + 1 : p + w;
                ok = open(p) && open(q);
            }
            if (ok && run < 0) run = a;
            if (!ok && run >= 0) {
                int len = a - run;
                if (len <= 6) gateway(at, run + len / 2, vertical);
                else {
                    gateway(at, run + 1, vertical);
                    gateway(at, a - 2, vertical);
                }
                run = -1;
            }
        }
    }

    private void gateway(int at, int a, boolean vertical) {
        int p = vertical ? a * w + at : at * w + a, q = vertical ? p + 1 : p + w;
        int np = addNode(p), nq = addNode(q);
        nodePartner[np] = nq;
        nodePartner[nq] = np;
        nodeCross[np] = cost(q);
        nodeCross[nq] = cost(p);
    }

    private int addNode(int tile) {
        if (nodes == nodeTile.length) {
            int n = nodes * 2;
            nodeTile = Arrays.copyOf(nodeTile, n);
            nodeCluster = Arrays.copyOf(nodeCluster, n);
            nodePartner = Arrays.copyOf(nodePartner, n);
            nodeCross = Arrays.copyOf(nodeCross, n);
        }
        int x = tile % w, y = tile / w, k = (y / CS) * cw + x / CS;
        nodeTile[nodes] = tile;
        nodeCluster[nodes] = k;
        clusterCount[k]++;
        return nodes++;
    }

    /** The costs between each pair of a cluster's gateways, worked out within the cluster. */
    private void ensureIntra(int k) {
        if (intraReady[k]) return;
        int[] list = clusterNodes[k];
        int n = list.length;
        int[] m = new int[n * n];
        Arrays.fill(m, FAR);
        int x0 = (k % cw) * CS, y0 = (k / cw) * CS, x1 = Math.min(w, x0 + CS), y1 = Math.min(h, y0 + CS);
        for (int a = 0; a < n; a++) {
            int[] d = local(nodeTile[list[a]], x0, y0, x1, y1);
            for (int b = 0; b < n; b++) {
                int t = nodeTile[list[b]];
                m[a * n + b] = d[(t / w - y0) * CS + (t % w - x0)];
            }
        }
        intra[k] = m;
        intraReady[k] = true;
    }

    // Small searches inside one cluster (or a box of them).
    private int[] lDist = new int[CS * CS];
    private long[] lHeap = new long[256];

    /** Costs from tile s to every tile of the box (x0..x1, y0..y1: one cluster), FAR where it can't get. */
    private int[] local(int s, int x0, int y0, int x1, int y1) {
        int bw = CS;
        Arrays.fill(lDist, FAR);
        int size = 0;
        int si = (s / w - y0) * bw + (s % w - x0);
        lDist[si] = 0;
        lHeap[size++] = si;
        while (size > 0) {
            long top = lHeap[0];
            lHeap[0] = lHeap[--size];
            down(lHeap, size);
            int li = (int) (top & 0xFFFFFFFFL), g = (int) (top >>> 32);
            if (g > lDist[li]) continue;
            int lx = li % bw, ly = li / bw, tx = x0 + lx, ty = y0 + ly;
            for (int q = 0; q < 8; q++) {
                int dx = DX[q], dy = DY[q], nx = tx + dx, ny = ty + dy;
                if (nx < x0 || ny < y0 || nx >= x1 || ny >= y1) continue;
                int nb = ny * w + nx;
                if (!open(nb)) continue;
                if (dx != 0 && dy != 0 && (!open(ty * w + nx) || !open(ny * w + tx))) continue;
                int ng = g + cost(nb) * (dx != 0 && dy != 0 ? 14 : 10) / 10;
                int nli = (ny - y0) * bw + (nx - x0);
                if (ng >= lDist[nli]) continue;
                lDist[nli] = ng;
                if (size == lHeap.length) lHeap = Arrays.copyOf(lHeap, size * 2);
                lHeap[size] = ((long) ng << 32) | nli;
                up(lHeap, size++);
            }
        }
        return lDist;
    }

    private static final int[] DX = {1, -1, 0, 0, 1, -1, 1, -1}, DY = {0, 0, 1, -1, 1, 1, -1, -1};

    // ------------------------------------------------------------------ tile A*, in a box

    private int[] aG, aFrom, aStamp;
    private long[] aHeap = new long[4096];
    private int aRun;

    /** A tile route from s to g (start excluded) inside the box, or null within maxExpand tiles searched. */
    private int[] astar(int s, int g, int x0, int y0, int x1, int y1, int maxExpand) {
        if (aG == null) {
            aG = new int[w * h];
            aFrom = new int[w * h];
            aStamp = new int[w * h];
        }
        int run = ++aRun;
        if (s == g) return new int[0];
        int gx = g % w, gy = g / w, size = 0;
        aStamp[s] = run;
        aG[s] = 0;
        aFrom[s] = -1;
        aHeap[size++] = ((long) heur(s % w, s / w, gx, gy) << 32) | s;
        int expanded = 0;
        while (size > 0) {
            long top = aHeap[0];
            aHeap[0] = aHeap[--size];
            down(aHeap, size);
            int t = (int) (top & 0xFFFFFFFFL), f = (int) (top >>> 32), tx = t % w, ty = t / w;
            if (f - heur(tx, ty, gx, gy) > aG[t]) continue;
            if (t == g) {
                int n = 0;
                for (int k = t; k != s; k = aFrom[k]) n++;
                int[] path = new int[n];
                for (int k = t, i = n - 1; k != s; k = aFrom[k]) path[i--] = k;
                return path;
            }
            if (++expanded > maxExpand) return null;
            for (int q = 0; q < 8; q++) {
                int dx = DX[q], dy = DY[q], nx = tx + dx, ny = ty + dy;
                if (nx < x0 || ny < y0 || nx >= x1 || ny >= y1) continue;
                int nb = ny * w + nx;
                if (!open(nb) && nb != g) continue;
                if (dx != 0 && dy != 0 && (!open(ty * w + nx) || !open(ny * w + tx))) continue;
                int ng = aG[t] + cost(nb) * (dx != 0 && dy != 0 ? 14 : 10) / 10;
                if (aStamp[nb] == run && ng >= aG[nb]) continue;
                aStamp[nb] = run;
                aG[nb] = ng;
                aFrom[nb] = t;
                if (size == aHeap.length) aHeap = Arrays.copyOf(aHeap, size * 2);
                aHeap[size] = ((long) (ng + heur(nx, ny, gx, gy)) << 32) | nb;
                up(aHeap, size++);
            }
        }
        return null;
    }

    private static int heur(int x, int y, int gx, int gy) {
        int dx = Math.abs(x - gx), dy = Math.abs(y - gy);
        return 10 * Math.max(dx, dy) + 4 * Math.min(dx, dy);
    }

    // ------------------------------------------------------------------ the whole route

    /**
     * A walking route from (sx, sy) to (tx, ty): the tiles to head for in turn (the corners of a straightened
     * route; the last one the destination), or null if there's no way there.
     */
    int[] path(float sx, float sy, float tx, float ty) {
        int s = c.tileIndex(sx, sy), g = c.tileIndex(tx, ty);
        if (s < 0 || g < 0) return null;
        if (s == g) return new int[]{g};
        int x0 = s % w, y0 = s / w, gx = g % w, gy = g / w;
        int far = Math.max(Math.abs(x0 - gx), Math.abs(y0 - gy));
        int[] tiles = null;
        // Close by: straight to it, in a box round the two of them.
        if (far <= CS * 2) {
            int m = CS;
            tiles = astar(s, g, Math.max(0, Math.min(x0, gx) - m), Math.max(0, Math.min(y0, gy) - m),
                    Math.min(w, Math.max(x0, gx) + m + 1), Math.min(h, Math.max(y0, gy) + m + 1), 6000);
        }
        if (tiles == null) tiles = hierarchical(s, g);
        // (Gave up on the clusters: a long plain search, as a last resort.)
        if (tiles == null && far <= CS * 6) tiles = astar(s, g, 0, 0, w, h, 30000);
        if (tiles == null) return null;
        return straighten(s, tiles);
    }

    private int[] hG, hFrom, hStamp;
    private int hRun, hSize, goalX, goalY;
    private long[] hHeap = new long[1024];

    private int[] hierarchical(int s, int g) {
        if (entrancesDirty || clusterNodes == null) buildEntrances();
        int ks = clusterOf(s), kg = clusterOf(g);
        ensureIntra(ks);
        ensureIntra(kg);
        // From the start to each gateway of its cluster, and from each of the goal's cluster's to the goal.
        int[] toStart = costsInCluster(s, ks), toGoal = costsInCluster(g, kg);
        if (hG == null || hG.length < nodes + 2) {
            int n = Math.max(nodes + 2, 1024) * 2;
            hG = new int[n];
            hFrom = new int[n];
            hStamp = new int[n];
        }
        int run = ++hRun;
        hSize = 0;
        int START = nodes, GOAL = nodes + 1;
        goalX = g % w;
        goalY = g / w;
        hStamp[START] = run;
        hG[START] = 0;
        hFrom[START] = -1;
        int[] ls = clusterNodes[ks];
        for (int a = 0; a < ls.length; a++) if (toStart[a] < FAR) relax(START, ls[a], toStart[a], run);
        int[] lg = clusterNodes[kg];
        int expanded = 0;
        while (hSize > 0) {
            long top = hHeap[0];
            hHeap[0] = hHeap[--hSize];
            down(hHeap, hSize);
            int u = (int) (top & 0xFFFFFFFFL), f = (int) (top >>> 32);
            if (u == GOAL) break;
            if (f - h(u) > hG[u]) continue;
            if (++expanded > 60000) return null;
            int k = nodeCluster[u];
            // Over the border.
            relax(u, nodePartner[u], hG[u] + nodeCross[u], run);
            // Across its own cluster.
            ensureIntra(k);
            int[] list = clusterNodes[k], m = intra[k];
            int n = list.length, ai = indexIn(list, u);
            for (int b = 0; b < n; b++) {
                int d = m[ai * n + b];
                if (d < FAR && list[b] != u) relax(u, list[b], hG[u] + d, run);
            }
            // And on to the goal itself.
            if (k == kg) {
                int d = toGoal[indexIn(lg, u)];
                if (d < FAR) relax(u, GOAL, hG[u] + d, run);
            }
        }
        if (hStamp[GOAL] != run) return null;
        // The gateways in order, then filled in tile by tile.
        int count = 0;
        for (int n = hFrom[GOAL]; n != START; n = hFrom[n]) count++;
        int[] seq = new int[count];
        for (int n = hFrom[GOAL], i = count - 1; n != START; n = hFrom[n]) seq[i--] = n;
        IntList out = new IntList();
        int at = s;
        for (int i = 0; i <= count; i++) {
            int next = i < count ? nodeTile[seq[i]] : g;
            if (next == at) continue;
            // A step over a border is one tile; anything else is a walk inside one cluster.
            int ax = at % w, ay = at / w, bx = next % w, by = next / w;
            if (Math.abs(ax - bx) + Math.abs(ay - by) == 1 && clusterOf(at) != clusterOf(next)) {
                out.add(next);
            } else {
                int k = clusterOf(next);
                int cx0 = (k % cw) * CS, cy0 = (k / cw) * CS;
                int[] part = astar(at, next, Math.max(0, cx0 - 1), Math.max(0, cy0 - 1), Math.min(w, cx0 + CS + 1), Math.min(h, cy0 + CS + 1), 3000);
                if (part == null) {
                    // The map's changed since: work the cluster out again next time, and fall back for now.
                    intraReady[k] = false;
                    return null;
                }
                for (int t : part) out.add(t);
            }
            at = next;
        }
        return out.toArray();
    }

    /** The estimate on from gateway n (or the goal, nothing) to the goal. */
    private int h(int n) {
        if (n >= nodes) return 0;
        int t = nodeTile[n];
        return heur(t % w, t / w, goalX, goalY);
    }

    private void relax(int from, int to, int g, int run) {
        if (hStamp[to] == run && g >= hG[to]) return;
        hStamp[to] = run;
        hG[to] = g;
        hFrom[to] = from;
        if (hSize == hHeap.length) hHeap = Arrays.copyOf(hHeap, hSize * 2);
        hHeap[hSize] = ((long) (g + h(to)) << 32) | to;
        up(hHeap, hSize++);
    }

    private int clusterOf(int t) {
        return ((t / w) / CS) * cw + (t % w) / CS;
    }

    private static int indexIn(int[] list, int n) {
        for (int i = 0; i < list.length; i++) if (list[i] == n) return i;
        return -1;
    }

    /** From tile t to each gateway of its cluster k (FAR where there's no way within the cluster). */
    private int[] costsInCluster(int t, int k) {
        int x0 = (k % cw) * CS, y0 = (k / cw) * CS, x1 = Math.min(w, x0 + CS), y1 = Math.min(h, y0 + CS);
        int[] d = local(t, x0, y0, x1, y1);
        int[] list = clusterNodes[k];
        int[] out = new int[list.length];
        for (int a = 0; a < list.length; a++) {
            int n = nodeTile[list[a]];
            out[a] = d[(n / w - y0) * CS + (n % w - x0)];
        }
        return out;
    }

    // ------------------------------------------------------------------ straightening

    /**
     * The route's corners only: from each point, on to the furthest one further along that can be walked to in a
     * straight line over ground no dearer than the route took (so a shortcut never cuts across the road where the
     * route kept to the pavement), and not brushing a wall.
     */
    private int[] straighten(int s, int[] tiles) {
        int n = tiles.length;
        if (n <= 2) return tiles;
        IntList out = new IntList();
        int from = s, i = -1;
        while (i < n - 1) {
            int best = i + 1, worst = c.walkCost[tiles[i + 1]];
            for (int j = i + 1; j < n && j <= i + 48; j++) {
                worst = Math.max(worst, c.walkCost[tiles[j]]);
                if (j > i + 1 && !clearLine(from, tiles[j], worst)) break;
                best = j;
            }
            out.add(tiles[best]);
            from = tiles[best];
            i = best;
        }
        return out.toArray();
    }

    /** A straight walk between the middles of two tiles crosses nothing solid nor anything dearer than maxCost. */
    private boolean clearLine(int a, int b, int maxCost) {
        float ax = a % w + 0.5f, ay = a / w + 0.5f, bx = b % w + 0.5f, by = b / w + 0.5f;
        float dx = bx - ax, dy = by - ay;
        int steps = (int) (Math.max(Math.abs(dx), Math.abs(dy)) * 3) + 1;
        for (int k = 1; k < steps; k++) {
            float px = ax + dx * k / steps, py = ay + dy * k / steps;
            // (A person's width: the line and a little either side of it.)
            for (int q = 0; q < 3; q++) {
                float ox = q == 0 ? 0 : (q == 1 ? 0.3f : -0.3f) * -dy / (Math.abs(dx) + Math.abs(dy) + 0.001f);
                float oy = q == 0 ? 0 : (q == 1 ? 0.3f : -0.3f) * dx / (Math.abs(dx) + Math.abs(dy) + 0.001f);
                int x = (int) (px + ox), y = (int) (py + oy);
                if (x < 0 || y < 0 || x >= w || y >= h) return false;
                int i = y * w + x;
                if (!open(i) || c.walkCost[i] > maxCost) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ bits

    private static void up(long[] heap, int i) {
        long v = heap[i];
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (heap[p] <= v) break;
            heap[i] = heap[p];
            i = p;
        }
        heap[i] = v;
    }

    /** Restores the heap after its top was replaced by the last item; returns size unchanged. */
    private static int down(long[] heap, int size) {
        if (size == 0) return 0;
        long v = heap[0];
        int i = 0;
        while (true) {
            int ch = 2 * i + 1;
            if (ch >= size) break;
            if (ch + 1 < size && heap[ch + 1] < heap[ch]) ch++;
            if (heap[ch] >= v) break;
            heap[i] = heap[ch];
            i = ch;
        }
        heap[i] = v;
        return size;
    }

    private static final class IntList {
        int[] a = new int[64];
        int n;

        void add(int v) {
            if (n == a.length) a = Arrays.copyOf(a, n * 2);
            a[n++] = v;
        }

        int[] toArray() {
            return Arrays.copyOf(a, n);
        }
    }
}
