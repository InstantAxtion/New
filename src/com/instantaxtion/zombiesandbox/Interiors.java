package com.instantaxtion.zombiesandbox;

import java.util.ArrayList;
import java.util.Random;

/**
 * Floor plans for the insides of buildings: rooms split off by walls with doorways, furnished for what they
 * are (a living room, a kitchen, a café, an open-plan office, a hotel room...), and the places people stand
 * or sit in them. Made once per building from its seed.
 */
final class Interiors {
    private Interiors() {
    }

    // Kinds of room.
    static final int LIVING = 0, KITCHEN = 1, BEDROOM = 2, BATH = 3, SHOPFLOOR = 4, STORE = 5, DINING = 6, COOKING = 7,
            BARROOM = 8, OPENPLAN = 9, MEETING = 10, CLASS = 11, WARD = 12, PEWS = 13, RACKS = 14, MACHINES = 15,
            HOTELROOM = 16, GYMFLOOR = 17, BOOKS = 18, POOLS = 19, SEATS = 20, SALON = 21, LAUNDRY = 22, SHOWROOM = 23,
            ARCADE = 24, BAKERY = 25, LAB = 26, DORMROOM = 27, LOBBY = 28, APPARATUS = 29;

    static final class Plan {
        /** Rooms: {x0, y0, x1, y1, kind, doorX, doorY}. */
        final ArrayList<float[]> rooms = new ArrayList<float[]>();
        /** Interior walls: {x0, y0, x1, y1} (doorways are left as gaps). */
        final ArrayList<float[]> walls = new ArrayList<float[]>();
        /** Furniture: {x0, y0, x1, y1, colour, round (1) or not (0)}. */
        final ArrayList<float[]> items = new ArrayList<float[]>();
        /** Where people sit or stand: {x, y, room index, facing angle}. */
        final ArrayList<float[]> spots = new ArrayList<float[]>();
    }

    /** What kind of inside a building has. */
    static int insideOf(City.Building b) {
        Variants.V v = Variants.get(b.variant);
        if (v != null) return v.inside;
        switch (b.kind) {
            case City.HOUSE: case City.APARTMENT: return Variants.I_HOME;
            case City.SHOP: return b.shopType == 2 ? Variants.I_RESTAURANT : Variants.I_SHELVES;
            case City.PHARMACY: case City.KIOSK: return Variants.I_SHELVES;
            case City.SCHOOL: return Variants.I_CLASSROOM;
            case City.HOSPITAL: return Variants.I_WARD;
            case City.WAREHOUSE: return Variants.I_RACKS;
            case City.OFFICE: case City.TOWER: return Variants.I_OFFICE;
            case City.TRAIN_STATION: return Variants.I_SEATS;
            case City.FIRE_STATION: return Variants.I_FIRE;
            default: return -1;
        }
    }

    static Plan make(City.Building b, int inside) {
        Plan p = new Plan();
        Random r = new Random(b.seed * 7919L + 13);
        float x0 = b.x0 + 2, y0 = b.y0 + 2, x1 = b.x1 - 2, y1 = b.y1 - 2;
        float dx = Math.max(x0 + 4, Math.min(x1 - 4, b.doorX)), dy = Math.max(y0 + 4, Math.min(y1 - 4, b.doorY));
        float min;
        switch (inside) {
            case Variants.I_HOME: min = 26; break;
            case Variants.I_HOTEL: case Variants.I_DORM: case Variants.I_WARD: case Variants.I_CLASSROOM: min = 26; break;
            case Variants.I_OFFICE: case Variants.I_LAB: min = 44; break;
            case Variants.I_RACKS: case Variants.I_FACTORY: case Variants.I_SEATS: case Variants.I_SHOWROOM:
            case Variants.I_GYM: case Variants.I_BATH: case Variants.I_FIRE: min = 70; break;
            default: min = 34; break;
        }
        if (isHall(inside)) hall(p, inside, x0, y0, x1, y1, dx, dy, r);
        else {
            split(p, x0, y0, x1, y1, min, r, 0, dx, dy);
            assign(p, inside, b, r);
        }
        for (int i = 0; i < p.rooms.size(); i++) furnish(p, i, r);
        return p;
    }

    /** Places that are one big room with a few back rooms: shops, cafés, warehouses, halls. */
    private static boolean isHall(int inside) {
        switch (inside) {
            case Variants.I_HOME: case Variants.I_HOTEL: case Variants.I_DORM: case Variants.I_WARD:
            case Variants.I_CLASSROOM: case Variants.I_OFFICE: case Variants.I_LAB:
                return false;
            default:
                return true;
        }
    }

    /**
     * The main floor taking most of the building (on the side with the door), and a strip of back rooms along
     * the far wall: a storeroom, a kitchen or an office, the toilets.
     */
    private static void hall(Plan p, int inside, float x0, float y0, float x1, float y1, float dx, float dy, Random r) {
        float w = x1 - x0, h = y1 - y0;
        boolean wide = w >= h;
        float depth = Math.min(26, (wide ? h : w) * 0.28f);
        int main;
        switch (inside) {
            case Variants.I_CAFE: case Variants.I_RESTAURANT: main = DINING; break;
            case Variants.I_BAR: main = BARROOM; break;
            case Variants.I_PEWS: main = PEWS; break;
            case Variants.I_RACKS: main = RACKS; break;
            case Variants.I_FACTORY: main = MACHINES; break;
            case Variants.I_GYM: main = GYMFLOOR; break;
            case Variants.I_LIBRARY: main = BOOKS; break;
            case Variants.I_BATH: main = POOLS; break;
            case Variants.I_SEATS: main = SEATS; break;
            case Variants.I_SALON: main = SALON; break;
            case Variants.I_LAUNDRY: main = LAUNDRY; break;
            case Variants.I_SHOWROOM: main = SHOWROOM; break;
            case Variants.I_FIRE: main = APPARATUS; break;
            case Variants.I_ARCADE: main = ARCADE; break;
            case Variants.I_KITCHEN: main = BAKERY; break;
            default: main = SHOPFLOOR; break;
        }
        if (depth < 12) {
            p.rooms.add(new float[]{x0, y0, x1, y1, main, dx, dy});
            return;
        }
        // The back strip goes on the side away from the door.
        boolean backFar = wide ? dy < (y0 + y1) / 2 : dx < (x0 + x1) / 2;
        float m0x = x0, m0y = y0, m1x = x1, m1y = y1;
        float s0x, s0y, s1x, s1y;
        if (wide) {
            if (backFar) { m1y = y1 - depth; s0x = x0; s0y = m1y; s1x = x1; s1y = y1; }
            else { m0y = y0 + depth; s0x = x0; s0y = y0; s1x = x1; s1y = m0y; }
        } else {
            if (backFar) { m1x = x1 - depth; s0x = m1x; s0y = y0; s1x = x1; s1y = y1; }
            else { m0x = x0 + depth; s0x = x0; s0y = y0; s1x = m0x; s1y = y1; }
        }
        p.rooms.add(new float[]{m0x, m0y, m1x, m1y, main, dx, dy});
        // The wall between, and the back rooms along it.
        float len = wide ? w : h;
        int n = Math.max(2, Math.min(5, (int) (len / 34)));
        boolean food = main == DINING || main == BARROOM || main == BAKERY;
        for (int k = 0; k < n; k++) {
            float a0 = (wide ? x0 : y0) + len * k / n, a1 = (wide ? x0 : y0) + len * (k + 1) / n;
            float doorA = (a0 + a1) / 2;
            float wallPos = wide ? (backFar ? m1y : m0y) : (backFar ? m1x : m0x);
            float rx0 = wide ? a0 : s0x, ry0 = wide ? s0y : a0, rx1 = wide ? a1 : s1x, ry1 = wide ? s1y : a1;
            int kind = k == n - 1 ? BATH : k == 0 && food ? COOKING : k == 1 && n > 2 ? MEETING : STORE;
            // A fire station: the crew's bunks and kitchen behind the engine bay.
            if (main == APPARATUS && k < n - 1) kind = k == 0 ? DORMROOM : k == 1 ? COOKING : STORE;
            p.rooms.add(new float[]{rx0, ry0, rx1, ry1, kind, wide ? doorA : wallPos, wide ? wallPos : doorA});
            // Wall with a doorway into each back room, and walls between them.
            if (wide) {
                p.walls.add(new float[]{a0, wallPos, doorA - 4, wallPos});
                p.walls.add(new float[]{doorA + 4, wallPos, a1, wallPos});
                if (k > 0) p.walls.add(new float[]{a0, s0y, a0, s1y});
            } else {
                p.walls.add(new float[]{wallPos, a0, wallPos, doorA - 4});
                p.walls.add(new float[]{wallPos, doorA + 4, wallPos, a1});
                if (k > 0) p.walls.add(new float[]{s0x, a0, s1x, a0});
            }
        }
    }

    private static void split(Plan p, float x0, float y0, float x1, float y1, float min, Random r, int depth,
                              float doorX, float doorY) {
        float w = x1 - x0, h = y1 - y0;
        if (depth >= 8 || (w < min * 2 && h < min * 2)) {
            p.rooms.add(new float[]{x0, y0, x1, y1, -1, doorX, doorY});
            return;
        }
        boolean vert = w > h * 1.15f || (w > h * 0.87f && r.nextBoolean());
        if (vert && w < min * 2) vert = false;
        if (!vert && h < min * 2) vert = true;
        float len = vert ? w : h;
        float cut = (vert ? x0 : y0) + min + r.nextFloat() * (len - 2 * min);
        // A doorway in the new wall, somewhere along it.
        float a0 = vert ? y0 : x0, a1 = vert ? y1 : x1;
        float gap = a0 + 4 + r.nextFloat() * Math.max(1, a1 - a0 - 16);
        if (vert) {
            p.walls.add(new float[]{cut, y0, cut, gap});
            p.walls.add(new float[]{cut, gap + 8, cut, y1});
        } else {
            p.walls.add(new float[]{x0, cut, gap, cut});
            p.walls.add(new float[]{gap + 8, cut, x1, cut});
        }
        float gx = vert ? cut : gap + 4, gy = vert ? gap + 4 : cut;
        // The side with the building's door keeps that door; the other side's door is the new doorway.
        boolean doorFirst = vert ? doorX < cut : doorY < cut;
        if (vert) {
            split(p, x0, y0, cut, y1, min, r, depth + 1, doorFirst ? doorX : gx, doorFirst ? doorY : gy);
            split(p, cut, y0, x1, y1, min, r, depth + 1, doorFirst ? gx : doorX, doorFirst ? gy : doorY);
        } else {
            split(p, x0, y0, x1, cut, min, r, depth + 1, doorFirst ? doorX : gx, doorFirst ? doorY : gy);
            split(p, x0, cut, x1, y1, min, r, depth + 1, doorFirst ? gx : doorX, doorFirst ? gy : doorY);
        }
    }

    /** Decides what each room is for: the biggest the main room, the smallest the washroom, and so on. */
    private static void assign(Plan p, int inside, City.Building b, Random r) {
        int n = p.rooms.size();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        final ArrayList<float[]> rooms = p.rooms;
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer c) {
                float[] ra = rooms.get(a), rc = rooms.get(c);
                return Float.compare((rc[2] - rc[0]) * (rc[3] - rc[1]), (ra[2] - ra[0]) * (ra[3] - ra[1]));
            }
        });
        for (int k = 0; k < n; k++) {
            float[] room = p.rooms.get(order[k]);
            boolean last = k == n - 1 && n >= 3;
            int kind;
            switch (inside) {
                case Variants.I_HOME:
                    // A house: living room, kitchen, bedrooms, bathroom. A block of flats: the same, flat
                    // after flat.
                    if (n <= 6) kind = k == 0 ? LIVING : k == 1 ? KITCHEN : last ? BATH : BEDROOM;
                    else kind = new int[]{LIVING, KITCHEN, BEDROOM, BEDROOM, BATH}[k % 5];
                    break;
                case Variants.I_SHELVES: kind = k == 0 ? SHOPFLOOR : last ? BATH : STORE; break;
                case Variants.I_CAFE: case Variants.I_RESTAURANT: kind = k == 0 ? DINING : k == 1 ? COOKING : last ? BATH : DINING; break;
                case Variants.I_BAR: kind = k == 0 ? BARROOM : k == 1 ? DINING : last ? BATH : STORE; break;
                case Variants.I_OFFICE: kind = k == 0 ? OPENPLAN : k == 1 ? MEETING : last ? BATH : OPENPLAN; break;
                case Variants.I_CLASSROOM: kind = k == n - 1 && n > 2 ? LOBBY : CLASS; break;
                case Variants.I_WARD: kind = k == n - 1 && n > 2 ? LOBBY : WARD; break;
                case Variants.I_PEWS: kind = PEWS; break;
                case Variants.I_RACKS: kind = k == 0 ? RACKS : MEETING; break;
                case Variants.I_FACTORY: kind = k == 0 ? MACHINES : k == 1 ? RACKS : MEETING; break;
                case Variants.I_HOTEL: kind = k == 0 ? LOBBY : HOTELROOM; break;
                case Variants.I_GYM: kind = k == 0 ? GYMFLOOR : BATH; break;
                case Variants.I_LIBRARY: kind = k == 0 ? BOOKS : k == 1 ? DINING : BOOKS; break;
                case Variants.I_BATH: kind = k == 0 ? POOLS : LOBBY; break;
                case Variants.I_SEATS: kind = k == 0 ? SEATS : LOBBY; break;
                case Variants.I_SALON: kind = k == 0 ? SALON : STORE; break;
                case Variants.I_LAUNDRY: kind = k == 0 ? LAUNDRY : STORE; break;
                case Variants.I_SHOWROOM: kind = k == 0 ? SHOWROOM : MEETING; break;
                case Variants.I_ARCADE: kind = k == 0 ? ARCADE : k == 1 ? DINING : ARCADE; break;
                case Variants.I_KITCHEN: kind = k == 0 ? BAKERY : k == 1 ? SHOPFLOOR : STORE; break;
                case Variants.I_LAB: kind = k == 0 ? LAB : k == 1 ? MEETING : LAB; break;
                case Variants.I_DORM: kind = last ? BATH : k == 0 ? LOBBY : DORMROOM; break;
                default: kind = OPENPLAN; break;
            }
            room[4] = kind;
        }
    }

    /** A rectangle given along a room's length (a) and across it (b), for rooms laid out either way round. */
    private static void edge(Plan p, boolean along, float a0, float b0, float a1, float b1, int col) {
        if (along) item(p, a0, b0, a1, b1, col);
        else item(p, b0, a0, b1, a1, col);
    }

    private static void item(Plan p, float x0, float y0, float x1, float y1, int col) {
        p.items.add(new float[]{x0, y0, x1, y1, col, 0});
    }

    private static void round(Plan p, float cx, float cy, float rad, int col) {
        p.items.add(new float[]{cx - rad, cy - rad, cx + rad, cy + rad, col, 1});
    }

    private static void spot(Plan p, float x, float y, int room, float facing) {
        // Keep everyone a sensible distance apart.
        for (int i = 0; i < p.spots.size(); i++) {
            float[] s = p.spots.get(i);
            if (Math.abs(s[0] - x) < 8 && Math.abs(s[1] - y) < 8) return;
        }
        p.spots.add(new float[]{x, y, room, facing});
    }

    private static final int WOOD = 0xFF8A5A3A, DARKWOOD = 0xFF5A3A28, WHITE = 0xFFF0F0EC, METAL = 0xFF8A8E92,
            FABRIC = 0xFF4E5A7A, SCREEN = 0xFF7FB0D8, GREEN = 0xFF3E8A34;

    /** Furniture for one room, and the spots in it. */
    private static void furnish(Plan p, int idx, Random r) {
        float[] room = p.rooms.get(idx);
        float x0 = room[0] + 3, y0 = room[1] + 3, x1 = room[2] - 3, y1 = room[3] - 3, w = x1 - x0, h = y1 - y0;
        if (w < 8 || h < 8) return;
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        boolean wide = w >= h;
        int kind = (int) room[4];
        switch (kind) {
            case LIVING: {
                int sofa = new int[]{FABRIC, 0xFF7A4A3A, 0xFF4A6A4A, 0xFF8A7A6A}[r.nextInt(4)];
                item(p, x0 + w * 0.2f, y0, x0 + w * 0.8f, y0 + 5, sofa);
                item(p, x0 + w * 0.2f, y0 + 8, x0 + w * 0.8f, Math.min(y1 - 6, y0 + 18), 0xFF8A3A3A);
                item(p, x0 + w * 0.35f, y0 + 10, x0 + w * 0.65f, Math.min(y1 - 8, y0 + 15), DARKWOOD);
                item(p, x0 + w * 0.3f, y1 - 2, x0 + w * 0.7f, y1, 0xFF1E1E22);
                round(p, x1 - 2, y0 + 2, 2, GREEN);
                spot(p, x0 + w * 0.35f, y0 + 3, idx, 1.57f);
                spot(p, x0 + w * 0.65f, y0 + 3, idx, 1.57f);
                spot(p, cx, y1 - 7, idx, -1.57f);
                break;
            }
            case KITCHEN: {
                item(p, x0, y0, x1, y0 + 4, WHITE);
                item(p, x0, y0, x0 + 4, y1, WHITE);
                round(p, x0 + 7, y0 + 2, 1.2f, 0xFF3A3A3A);
                round(p, x0 + 11, y0 + 2, 1.2f, 0xFF3A3A3A);
                float tw = Math.min(14, w * 0.4f), th = Math.min(9, h * 0.4f);
                item(p, cx - tw / 2 + 2, cy - th / 2 + 2, cx + tw / 2 + 2, cy + th / 2 + 2, WOOD);
                spot(p, cx - tw / 2 - 1, cy + 2, idx, 0);
                spot(p, cx + tw / 2 + 5, cy + 2, idx, 3.14f);
                spot(p, x0 + 8, y0 + 7, idx, -1.57f);
                break;
            }
            case BEDROOM: {
                float bw2 = Math.min(11, w * 0.5f), bh2 = Math.min(15, h * 0.6f);
                item(p, x0 + 1, y0 + 1, x0 + 1 + bw2, y0 + 1 + bh2, WHITE);
                item(p, x0 + 1, y0 + 1, x0 + 1 + bw2, y0 + 4, 0xFFB0C0E0);
                item(p, x0 + 1, y0 + 6, x0 + 1 + bw2, y0 + 1 + bh2, new int[]{0xFF7A8AB0, 0xFFB07A8A, 0xFF8AB07A}[r.nextInt(3)]);
                item(p, x1 - 4, y0, x1, y0 + Math.min(12, h * 0.5f), DARKWOOD);
                item(p, x1 - 9, y1 - 4, x1, y1, WOOD);
                spot(p, x0 + bw2 + 4, y0 + bh2 * 0.6f, idx, 3.14f);
                spot(p, x1 - 5, y1 - 7, idx, 1.57f);
                break;
            }
            case BATH: {
                item(p, x0, y0, x0 + Math.min(14, w * 0.6f), y0 + 6, WHITE);
                item(p, x0 + 1, y0 + 1, x0 + Math.min(13, w * 0.6f) - 1, y0 + 5, 0xFFB8DCE8);
                round(p, x1 - 3, y1 - 3, 2.2f, WHITE);
                item(p, x1 - 6, y0, x1, y0 + 3, WHITE);
                spot(p, x1 - 4, y0 + 6, idx, -1.57f);
                break;
            }
            case SHOPFLOOR: case BOOKS: case STORE: case RACKS: {
                int shelf = kind == BOOKS ? DARKWOOD : kind == RACKS ? 0xFF4A6A8A : kind == STORE ? 0xFF8A7A5A : 0xFF6A6A70;
                int[] goods = {0xFFE05050, 0xFF5080E0, 0xFFE0C050, 0xFF50B060, 0xFFE08840};
                float gapS = kind == RACKS ? 16 : 11;
                for (float t = (wide ? y0 : x0) + 4; t < (wide ? y1 : x1) - 8; t += gapS) {
                    float a0 = (wide ? x0 : y0) + 4, a1 = (wide ? x1 : y1) - 10;
                    if (wide) item(p, a0, t, a1, t + 3, shelf);
                    else item(p, t, a0, t + 3, a1, shelf);
                    // Goods along the shelf (more sparsely drawn on very long shelves).
                    float step = 3 * Math.max(1, (a1 - a0) / 90);
                    for (float u = a0 + 1; u < a1 - 2; u += step) {
                        int col = kind == RACKS ? 0xFFA0784A : goods[r.nextInt(goods.length)];
                        if (kind == STORE && r.nextBoolean()) continue;
                        if (wide) item(p, u, t + 0.5f, u + 2, t + 2.5f, col);
                        else item(p, t + 0.5f, u, t + 2.5f, u + 2, col);
                    }
                    if (wide) spot(p, a0 + 6 + r.nextFloat() * (a1 - a0 - 10), t + gapS / 2 + 1.5f, idx, -1.57f);
                    else spot(p, t + gapS / 2 + 1.5f, a0 + 6 + r.nextFloat() * (a1 - a0 - 10), idx, 3.14f);
                }
                if (kind == SHOPFLOOR) {
                    item(p, wide ? x1 - 8 : x0 + 3, wide ? y0 + 3 : y1 - 8, wide ? x1 - 4 : x1 - 3, wide ? y1 - 3 : y1 - 4, WOOD);
                    spot(p, wide ? x1 - 2 : cx, wide ? cy : y1 - 1, idx, wide ? 3.14f : -1.57f);
                }
                if (kind == RACKS) {
                    item(p, x1 - 9, y1 - 7, x1 - 4, y1 - 2, 0xFFE8B830);
                    spot(p, x1 - 12, y1 - 4, idx, 0);
                }
                break;
            }
            case DINING: case BARROOM: {
                if (kind == BARROOM) {
                    item(p, x0, y0 + 2, x1 - 6, y0 + 6, DARKWOOD);
                    item(p, x0, y0, x1 - 6, y0 + 2, 0xFF6A4A6A);
                    for (float u = x0 + 3; u < x1 - 8; u += 7) {
                        round(p, u, y0 + 9, 1.4f, 0xFF3A3A3A);
                        spot(p, u, y0 + 9, idx, -1.57f);
                    }
                    y0 += 14;
                }
                boolean roundT = r.nextBoolean();
                int cloth = new int[]{WOOD, WHITE, 0xFFB03A2E, 0xFF2E4A36}[r.nextInt(4)];
                for (float ty = y0 + 6; ty < y1 - 5; ty += 15)
                    for (float tx = x0 + 6; tx < x1 - 5; tx += 15) {
                        if (roundT) round(p, tx, ty, 3.2f, cloth);
                        else item(p, tx - 3.5f, ty - 3.5f, tx + 3.5f, ty + 3.5f, cloth);
                        spot(p, tx - 6, ty, idx, 0);
                        spot(p, tx + 6, ty, idx, 3.14f);
                    }
                break;
            }
            case COOKING: case BAKERY: {
                item(p, x0, y0, x1, y0 + 5, METAL);
                for (float u = x0 + 2; u < x1 - 3; u += 5) {
                    round(p, u + 1.5f, y0 + 2.5f, 1.3f, 0xFF2A2A2A);
                }
                item(p, x0 + 4, cy - 3, x1 - 4, cy + 3, METAL);
                if (kind == BAKERY) {
                    item(p, x0, y1 - 7, x0 + 10, y1, 0xFF6A6A70);
                    for (float u = x0 + 5; u < x1 - 6; u += 4) round(p, u, cy, 1.4f, 0xFFD8A860);
                }
                spot(p, x0 + 6, y0 + 8, idx, -1.57f);
                spot(p, cx, cy + 6, idx, -1.57f);
                spot(p, x1 - 6, y0 + 8, idx, -1.57f);
                break;
            }
            case OPENPLAN: case LAB: case CLASS: {
                int desk = kind == LAB ? WHITE : kind == CLASS ? 0xFFB08A5A : DARKWOOD;
                float stepX = kind == CLASS ? 9 : 14, stepY = kind == CLASS ? 9 : 13;
                if (kind == CLASS) item(p, x0 + w * 0.2f, y0, x0 + w * 0.8f, y0 + 2, 0xFF2E4A36);
                for (float ty = y0 + (kind == CLASS ? 7 : 4); ty < y1 - 6; ty += stepY)
                    for (float tx = x0 + 4; tx < x1 - stepX + 4; tx += stepX) {
                        if (kind == CLASS) {
                            item(p, tx, ty, tx + 5, ty + 3, desk);
                            spot(p, tx + 2.5f, ty + 5, idx, -1.57f);
                        } else {
                            item(p, tx, ty, tx + 10, ty + 5, desk);
                            item(p, tx + 1, ty + 0.5f, tx + 4, ty + 1.8f, kind == LAB ? 0xFF3FB8B0 : SCREEN);
                            item(p, tx + 6, ty + 0.5f, tx + 9, ty + 1.8f, kind == LAB ? 0xFFE0C050 : SCREEN);
                            spot(p, tx + 2.5f, ty + 7.5f, idx, -1.57f);
                            spot(p, tx + 7.5f, ty + 7.5f, idx, -1.57f);
                        }
                    }
                round(p, x1 - 2, y1 - 2, 2, GREEN);
                break;
            }
            case MEETING: case LOBBY: {
                if (kind == LOBBY) {
                    item(p, cx - Math.min(10, w * 0.3f), y1 - 7, cx + Math.min(10, w * 0.3f), y1 - 3, WOOD);
                    spot(p, cx, y1 - 9, idx, 1.57f);
                    for (float u = x0 + 3; u < x1 - 6; u += 8) {
                        item(p, u, y0, u + 6, y0 + 4, FABRIC);
                        spot(p, u + 3, y0 + 2, idx, 1.57f);
                    }
                    round(p, x0 + 2, y1 - 2, 2.2f, GREEN);
                    break;
                }
                float tw = Math.min(w - 10, 26), th = Math.min(h - 10, 9);
                item(p, cx - tw / 2, cy - th / 2, cx + tw / 2, cy + th / 2, DARKWOOD);
                for (float u = cx - tw / 2 + 3; u < cx + tw / 2 - 1; u += 7) {
                    spot(p, u, cy - th / 2 - 3, idx, 1.57f);
                    spot(p, u, cy + th / 2 + 3, idx, -1.57f);
                }
                item(p, x0, cy - 5, x0 + 1.5f, cy + 5, WHITE);
                break;
            }
            case WARD: case HOTELROOM: case DORMROOM: {
                float bw2 = 7, bh2 = 12, step = kind == WARD ? 11 : 30;
                for (float u = x0 + 1; u < x1 - bw2; u += step) {
                    item(p, u, y0 + 1, u + bw2, y0 + 1 + bh2, WHITE);
                    item(p, u, y0 + 1, u + bw2, y0 + 4, 0xFFE8E8F0);
                    item(p, u, y0 + 5, u + bw2, y0 + 1 + bh2, kind == WARD ? 0xFF7AA8D8 : kind == DORMROOM ? 0xFF8A7AB0 : 0xFFB89A6A);
                    spot(p, u + bw2 + 3, y0 + 8, idx, 3.14f);
                    if (kind == HOTELROOM) break;
                }
                if (kind != WARD) {
                    item(p, x1 - 9, y1 - 5, x1, y1, WOOD);
                    spot(p, x1 - 5, y1 - 8, idx, 1.57f);
                } else {
                    item(p, cx - 5, y1 - 5, cx + 5, y1, 0xFFD83A3A);
                    spot(p, cx, y1 - 8, idx, 1.57f);
                }
                break;
            }
            case PEWS: case SEATS: {
                int seat = kind == PEWS ? DARKWOOD : 0xFF8A2A3A;
                item(p, wide ? x1 - 5 : x0 + 4, wide ? y0 + 4 : y1 - 5, wide ? x1 : x1 - 4, wide ? y1 - 4 : y1, kind == PEWS ? 0xFFE8D24A : 0xFFE8E8E8);
                for (float t = (wide ? x0 : y0) + 3; t < (wide ? x1 : y1) - 12; t += 6) {
                    if (wide) {
                        item(p, t, y0 + 3, t + 2.5f, cy - 3, seat);
                        item(p, t, cy + 3, t + 2.5f, y1 - 3, seat);
                        spot(p, t + 4, y0 + 3 + r.nextFloat() * (cy - y0 - 8), idx, 0);
                    } else {
                        item(p, x0 + 3, t, cx - 3, t + 2.5f, seat);
                        item(p, cx + 3, t, x1 - 3, t + 2.5f, seat);
                        spot(p, x0 + 3 + r.nextFloat() * (cx - x0 - 8), t + 4, idx, 1.57f);
                    }
                }
                break;
            }
            case MACHINES: {
                for (float ty = y0 + 4; ty < y1 - 12; ty += 20)
                    for (float tx = x0 + 4; tx < x1 - 16; tx += 22) {
                        item(p, tx, ty, tx + 14, ty + 9, 0xFF6A7078);
                        item(p, tx + 2, ty + 2, tx + 6, ty + 7, 0xFFE8B830);
                        round(p, tx + 10, ty + 4.5f, 2.2f, 0xFF3A3E44);
                        spot(p, tx + 7, ty + 13, idx, -1.57f);
                    }
                item(p, x0 + 2, y1 - 6, x1 - 2, y1 - 3, 0xFF3A3A3A);
                break;
            }
            case GYMFLOOR: {
                for (float tx = x0 + 4; tx < x1 - 10; tx += 12) {
                    item(p, tx, y0 + 2, tx + 5, y0 + 12, 0xFF2A2A2A);
                    spot(p, tx + 2.5f, y0 + 7, idx, 1.57f);
                }
                item(p, x0 + 4, cy, x0 + Math.min(w - 4, 30), cy + 12, 0xFF3A6AA8);
                for (float tx = x0 + 6; tx < x1 - 6; tx += 10) round(p, tx, y1 - 4, 1.8f, METAL);
                spot(p, x0 + 12, cy + 6, idx, 0);
                break;
            }
            case POOLS: {
                item(p, x0 + 4, y0 + 4, x1 - 4, cy, 0xFF4FB8D8);
                item(p, x0 + 4, cy + 5, cx - 2, y1 - 4, 0xFF7AC8D8);
                for (float u = cx + 4; u < x1 - 4; u += 5) item(p, u, y1 - 8, u + 4, y1 - 3, WOOD);
                spot(p, x0 + 10, y0 + 10, idx, 0);
                spot(p, x0 + 20, y0 + 14, idx, 0);
                spot(p, x0 + 10, cy + 10, idx, 0);
                break;
            }
            case SALON: {
                for (float u = x0 + 3; u < x1 - 6; u += 10) {
                    item(p, u, y0, u + 6, y0 + 1.5f, 0xFFB8DCE8);
                    round(p, u + 3, y0 + 6, 2.4f, 0xFF2A2A2A);
                    spot(p, u + 3, y0 + 6, idx, -1.57f);
                    spot(p, u + 3, y0 + 11, idx, -1.57f);
                }
                item(p, x0, y1 - 5, x0 + Math.min(20, w * 0.5f), y1, FABRIC);
                break;
            }
            case LAUNDRY: {
                for (float u = x0 + 2; u < x1 - 6; u += 7) {
                    item(p, u, y0, u + 6, y0 + 6, WHITE);
                    round(p, u + 3, y0 + 3, 2, 0xFF8AB0D8);
                    item(p, u, y1 - 6, u + 6, y1, WHITE);
                    round(p, u + 3, y1 - 3, 2, 0xFF8AB0D8);
                }
                spot(p, cx, cy, idx, 0);
                spot(p, x0 + 8, cy, idx, 0);
                break;
            }
            case SHOWROOM: {
                int[] cars = {0xFFB03A2E, 0xFF2E5FB0, 0xFFE0E0E0, 0xFF222428, 0xFFD4A21C};
                for (float ty = y0 + 4; ty < y1 - 10; ty += 18)
                    for (float tx = x0 + 4; tx < x1 - 18; tx += 22) {
                        item(p, tx, ty, tx + 16, ty + 9, cars[r.nextInt(cars.length)]);
                        item(p, tx + 9, ty + 1, tx + 12, ty + 8, 0xFF1E2A33);
                        spot(p, tx + 8, ty + 13, idx, -1.57f);
                    }
                break;
            }
            case APPARATUS: {
                // The engine bay: big painted bays for the engines (out on the apron, ready, when they're home)
                // with keep-clear hatching at the doors, the crew's turnout gear on hooks along the back wall,
                // breathing sets on a rack, a workbench, a hose rack and the brass pole.
                boolean along = x1 - x0 >= y1 - y0;
                float len = along ? x1 - x0 : y1 - y0, depth = along ? y1 - y0 : x1 - x0;
                float a00 = along ? x0 : y0, b00 = along ? y0 : x0;
                int bays = Math.max(1, Math.min(4, (int) (len / 44)));
                float slot = len / bays, bw = Math.min(30, slot - 10), bd = depth - 22;
                for (int k = 0; k < bays; k++) {
                    float a0 = a00 + slot * (k + 0.5f) - bw / 2, a1 = a0 + bw, b0 = b00 + 3, b1 = b0 + bd;
                    // Outline, and the hatching by the doors.
                    edge(p, along, a0, b0, a1, b0 + 1, 0xFFE0C040);
                    edge(p, along, a0, b1 - 1, a1, b1, 0xFFE0C040);
                    edge(p, along, a0, b0, a0 + 1, b1, 0xFFE0C040);
                    edge(p, along, a1 - 1, b0, a1, b1, 0xFFE0C040);
                    for (float q = a0 + 3; q < a1 - 3; q += 5) edge(p, along, q, b0 + 2, q + 2, b0 + 7, 0xFFC8B040);
                    // Wheel chocks where the engine stops.
                    edge(p, along, a0 + 4, b1 - 8, a0 + 7, b1 - 6, 0xFFE0C040);
                    edge(p, along, a1 - 7, b1 - 8, a1 - 4, b1 - 6, 0xFFE0C040);
                }
                // Turnout gear: coat and helmet on each hook, along the back wall.
                float gw = b00 + depth - 6;
                for (float u = a00 + 3; u < a00 + len - 30; u += 6) {
                    edge(p, along, u, gw, u + 4, gw + 4, 0xFFC8A040);
                    float hx = along ? u + 2 : gw - 1.5f, hy = along ? gw - 1.5f : u + 2;
                    round(p, hx, hy, 1.4f, 0xFFD03A2A);
                }
                // Breathing sets on a rack, the workbench and the hose rack in the far corner, and the pole.
                float c0 = a00 + len - 26;
                for (int k = 0; k < 4; k++) {
                    float hx = along ? c0 + 2 + k * 3 : gw + 1, hy = along ? gw + 1 : c0 + 2 + k * 3;
                    round(p, hx, hy, 1.2f, 0xFFB8BCC0);
                }
                edge(p, along, c0 + 14, gw - 1, c0 + 24, gw + 4, WOOD);
                for (int k = 0; k < 3; k++) {
                    float hx = along ? c0 + 4 + k * 4 : gw - 8, hy = along ? gw - 8 : c0 + 4 + k * 4;
                    round(p, hx, hy, 1.8f, 0xFF8A8A84);
                }
                float px = along ? a00 + len - 6 : b00 + depth - 14, py = along ? b00 + depth - 14 : a00 + len - 6;
                round(p, px, py, 1.6f, 0xFFE0B040);
                for (float u = a00 + 10; u < a00 + len - 30; u += 24) {
                    float sx = along ? u : gw - 6, sy = along ? gw - 6 : u;
                    spot(p, sx, sy, idx, along ? 1.57f : 0);
                }
                break;
            }
            case ARCADE: {
                int[] cab = {0xFFE040C0, 0xFF3A8AE0, 0xFFE0C040, 0xFF40B070, 0xFFE03A3A};
                for (float ty = y0 + 2; ty < y1 - 8; ty += 13)
                    for (float tx = x0 + 2; tx < x1 - 6; tx += 7) {
                        item(p, tx, ty, tx + 5, ty + 4, cab[r.nextInt(cab.length)]);
                        spot(p, tx + 2.5f, ty + 7, idx, -1.57f);
                    }
                break;
            }
            default: break;
        }
        // Somewhere to stand in any room.
        spot(p, cx + (r.nextFloat() - 0.5f) * w * 0.4f, cy + (r.nextFloat() - 0.5f) * h * 0.4f, idx, r.nextFloat() * 6.28f);
    }

    /** Floor colour for a room. */
    static int floorOf(int kind) {
        switch (kind) {
            case LIVING: case BEDROOM: case DINING: case BARROOM: case BOOKS: case HOTELROOM: case DORMROOM: return 0xFFB8946A;
            case KITCHEN: case BATH: case COOKING: case BAKERY: case LAUNDRY: case POOLS: return 0xFFDCDCD4;
            case WARD: case LAB: return 0xFFD8E2E6;
            case RACKS: case MACHINES: case SHOWROOM: case STORE: return 0xFF8A8A84;
            case APPARATUS: return 0xFF9A9A94;
            case PEWS: return 0xFF9C8C76;
            case SEATS: case ARCADE: return 0xFF4A3A4A;
            case GYMFLOOR: return 0xFF6A6E74;
            case CLASS: return 0xFFC8B89A;
            case MEETING: case OPENPLAN: case LOBBY: case SALON: return 0xFFA8A8A0;
            default: return 0xFFC8C0B0;
        }
    }
}
