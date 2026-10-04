package com.instantaxtion.zombiesandbox;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import java.util.Random;

/**
 * Roofs the way each country builds them, drawn into the city map (so they show from above and on top of the
 * 3D buildings): Australian hip roofs in Colorbond or tile with a verandah and a rainwater tank, Japanese
 * tiled roofs, temples and shrines, French mansards with dormers and chimney pots, Mexican flat roofs with
 * black water tanks and tiled church domes. American cities keep the usual look.
 */
final class Roofs {
    private Roofs() {
    }

    private static final Path path = new Path();
    private static final RectF oval = new RectF();

    /** Draws the roof of a building lot {x, y, w, h, roof, seed, kind, floors, wall}; false if it isn't styled here. */
    static boolean draw(City city, Canvas c, Paint p, int[] b, String name) {
        int id = city.country.id;
        if (id == Country.USA) return false;
        float T = City.T;
        float x0 = b[0] * T, y0 = b[1] * T, x1 = (b[0] + b[2]) * T, y1 = (b[1] + b[3]) * T;
        int roof = b[4], kind = b[6], wall = b.length > 8 ? b[8] : 0xFFCCCCCC;
        Random r = new Random(b[5] * 31L + 11);
        switch (kind) {
            case City.HOUSE:
                if (id == Country.AUSTRALIA) aussieHouse(c, p, x0, y0, x1, y1, roof, r);
                else if (id == Country.JAPAN) japaneseHouse(c, p, x0, y0, x1, y1, roof, r);
                else if (id == Country.FRANCE) frenchHouse(c, p, x0, y0, x1, y1, roof, r);
                else mexicanHouse(c, p, x0, y0, x1, y1, roof, wall, r);
                return true;
            case City.CHURCH:
                if (id == Country.JAPAN) {
                    if (name != null && name.contains("Shrine")) shrine(c, p, x0, y0, x1, y1);
                    else temple(c, p, x0, y0, x1, y1);
                } else if (id == Country.FRANCE) cathedral(c, p, x0, y0, x1, y1);
                else if (id == Country.MEXICO) domedChurch(c, p, x0, y0, x1, y1, r);
                else return false;
                return true;
            case City.APARTMENT:
            case City.OFFICE:
                // Glass towers look the same the world over.
                if (b[7] > 10) return false;
                if (id == Country.FRANCE) mansard(c, p, x0, y0, x1, y1, roof, r);
                else if (id == Country.JAPAN) japaneseBlock(c, p, x0, y0, x1, y1, roof, kind == City.APARTMENT, r);
                else if (id == Country.MEXICO) mexicanBlock(c, p, x0, y0, x1, y1, roof, wall, r);
                else return false;
                return true;
            case City.SHOP:
                shop(c, p, x0, y0, x1, y1, roof, wall, id, r);
                return true;
            case City.SPIRE:
                if (id != Country.JAPAN) return false;
                pagoda(c, p, x0, y0, x1, y1);
                return true;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ shapes

    private static void quad(Canvas c, Paint p, int col, float ax, float ay, float bx, float by, float cx, float cy,
                             float dx, float dy) {
        path.reset();
        path.moveTo(ax, ay);
        path.lineTo(bx, by);
        path.lineTo(cx, cy);
        path.lineTo(dx, dy);
        path.close();
        p.setColor(col);
        c.drawPath(path, p);
    }

    /** A hip roof: four slopes up to a short ridge, lit from the top left. Returns {rx0, ry0, rx1, ry1}. */
    private static float[] hip(Canvas c, Paint p, float x0, float y0, float x1, float y1, int roof) {
        float bw = x1 - x0, bh = y1 - y0, in = Math.min(bw, bh) / 2;
        boolean along = bw >= bh;
        float rx0 = along ? x0 + in : (x0 + x1) / 2, rx1 = along ? x1 - in : (x0 + x1) / 2;
        float ry0 = along ? (y0 + y1) / 2 : y0 + in, ry1 = along ? (y0 + y1) / 2 : y1 - in;
        quad(c, p, City.lighten(roof, 0.16f), x0, y0, x1, y0, rx1, ry0, rx0, ry0);
        quad(c, p, City.darken(roof, 0.86f), x0, y1, x1, y1, rx1, ry1, rx0, ry1);
        quad(c, p, City.lighten(roof, 0.06f), x0, y0, rx0, ry0, rx0, ry1, x0, y1);
        quad(c, p, City.darken(roof, 0.74f), x1, y0, rx1, ry0, rx1, ry1, x1, y1);
        p.setColor(City.darken(roof, 0.55f));
        p.setStrokeWidth(0.7f);
        c.drawLine(x0, y0, rx0, ry0, p);
        c.drawLine(x1, y0, rx1, ry0, p);
        c.drawLine(x0, y1, rx0, ry1, p);
        c.drawLine(x1, y1, rx1, ry1, p);
        p.setStrokeWidth(1.1f);
        c.drawLine(rx0, ry0, rx1, ry1, p);
        return new float[]{rx0, ry0, rx1, ry1};
    }

    /** Fine lines across a roof: tile courses or sheet ribs. */
    private static void courses(Canvas c, Paint p, float x0, float y0, float x1, float y1, int col, float gap, boolean vertical) {
        p.setColor(col);
        p.setStrokeWidth(0.35f);
        if (vertical) for (float x = x0 + gap; x < x1; x += gap) c.drawLine(x, y0, x, y1, p);
        else for (float y = y0 + gap; y < y1; y += gap) c.drawLine(x0, y, x1, y, p);
    }

    private static void tank(Canvas c, Paint p, float x, float y, float rad, int col) {
        p.setColor(0x50000000);
        c.drawCircle(x + 1, y + 1.2f, rad, p);
        p.setColor(col);
        c.drawCircle(x, y, rad, p);
        p.setColor(City.lighten(col, 0.25f));
        c.drawCircle(x - rad * 0.2f, y - rad * 0.2f, rad * 0.45f, p);
    }

    private static void acUnit(Canvas c, Paint p, float x, float y) {
        p.setColor(0x44000000);
        c.drawRect(x + 1, y + 1, x + 6, y + 5, p);
        p.setColor(0xFFC8CCD0);
        c.drawRect(x, y, x + 5, y + 4, p);
        p.setColor(0xFF7D8185);
        c.drawCircle(x + 2.5f, y + 2, 1.3f, p);
    }

    private static void washing(Canvas c, Paint p, float x0, float y, float x1, Random r) {
        p.setColor(0xFFDADADA);
        p.setStrokeWidth(0.35f);
        c.drawLine(x0, y, x1, y, p);
        int[] cloth = {0xFFE05050, 0xFF5080E0, 0xFFF0F0F0, 0xFFE0C050, 0xFF50B080};
        for (float x = x0 + 1; x < x1 - 2; x += 3 + r.nextFloat() * 2) {
            p.setColor(cloth[r.nextInt(cloth.length)]);
            c.drawRect(x, y, x + 1.8f, y + 2.2f, p);
        }
    }

    // ------------------------------------------------------------------ houses

    private static void aussieHouse(Canvas c, Paint p, float x0, float y0, float x1, float y1, int roof, Random r) {
        p.setColor(City.darken(roof, 0.6f));
        c.drawRect(x0, y0, x1, y1, p);
        // A verandah along the front under its own skillion roof.
        float vy = y1 - 4.5f;
        p.setColor(0xFFDCD8CC);
        c.drawRect(x0 + 1, vy, x1 - 1, y1 - 0.5f, p);
        courses(c, p, x0 + 1, vy, x1 - 1, y1 - 0.5f, 0xFFB8B4A8, 1.4f, true);
        float[] ridge = hip(c, p, x0 + 1, y0 + 1, x1 - 1, vy, roof);
        boolean tiles = roof == 0xFFB0583A || roof == 0xFF7A2E28;
        int line = City.darken(roof, tiles ? 0.8f : 0.88f);
        if (tiles) courses(c, p, x0 + 2, y0 + 2, x1 - 2, vy - 1, line, 1.6f, false);
        else courses(c, p, x0 + 2, y0 + 2, x1 - 2, vy - 1, line, 1.2f, (x1 - x0) < (vy - y0));
        // Solar on the sunny (north) slope, a rainwater tank at the side.
        if (r.nextInt(5) < 3) {
            float sx0 = Math.max(x0 + 3, ridge[0] - 2), sx1 = Math.min(x1 - 3, ridge[2] + 2);
            if (sx1 - sx0 > 6) solar(c, p, sx0, y0 + 3, sx1, Math.min(ridge[1] - 1.5f, y0 + 10));
        }
        if (r.nextBoolean()) tank(c, p, x1 - 3.5f, y0 + 3.5f, 2.6f, 0xFF7C8C6A);
    }

    private static void japaneseHouse(Canvas c, Paint p, float x0, float y0, float x1, float y1, int roof, Random r) {
        if (r.nextInt(10) < 3) {
            // A modern box with a flat roof and an air conditioner.
            p.setColor(0xFF9A9C9E);
            c.drawRect(x0, y0, x1, y1, p);
            p.setColor(0xFFC6C8C8);
            c.drawRect(x0 + 1.5f, y0 + 1.5f, x1 - 1.5f, y1 - 1.5f, p);
            acUnit(c, p, x0 + 4, y0 + 4);
            if (r.nextBoolean()) washing(c, p, x0 + 3, y1 - 6, x1 - 4, r);
            return;
        }
        p.setColor(City.darken(roof, 0.55f));
        c.drawRect(x0, y0, x1, y1, p);
        float[] ridge = hip(c, p, x0 + 0.5f, y0 + 0.5f, x1 - 0.5f, y1 - 0.5f, roof);
        // Kawara tiles: close courses parallel to the eaves, and heavy ridge ends.
        courses(c, p, x0 + 1, y0 + 1, x1 - 1, y1 - 1, City.darken(roof, 0.72f), 1.3f, (x1 - x0) < (y1 - y0));
        p.setColor(City.darken(roof, 0.4f));
        p.setStrokeWidth(1.6f);
        c.drawLine(ridge[0], ridge[1], ridge[2], ridge[3], p);
        c.drawCircle(ridge[0], ridge[1], 1.3f, p);
        c.drawCircle(ridge[2], ridge[3], 1.3f, p);
    }

    private static void frenchHouse(Canvas c, Paint p, float x0, float y0, float x1, float y1, int roof, Random r) {
        boolean along = (x1 - x0) >= (y1 - y0);
        boolean tiles = roof == 0xFFB0623E || roof == 0xFF9E5A3C;
        p.setColor(City.darken(roof, 0.7f));
        c.drawRect(x0, y0, x1, y1, p);
        float mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
        p.setColor(City.lighten(roof, 0.14f));
        if (along) c.drawRect(x0 + 1, y0 + 1, x1 - 1, my, p);
        else c.drawRect(x0 + 1, y0 + 1, mx, y1 - 1, p);
        p.setColor(roof);
        if (along) c.drawRect(x0 + 1, my, x1 - 1, y1 - 1, p);
        else c.drawRect(mx, y0 + 1, x1 - 1, y1 - 1, p);
        courses(c, p, x0 + 1, y0 + 1, x1 - 1, y1 - 1, City.darken(roof, tiles ? 0.8f : 0.85f), tiles ? 1.5f : 1.1f, !along);
        p.setColor(City.darken(roof, 0.55f));
        if (along) c.drawRect(x0 + 1, my - 0.6f, x1 - 1, my + 0.6f, p);
        else c.drawRect(mx - 0.6f, y0 + 1, mx + 0.6f, y1 - 1, p);
        // Chimneys on both gable ends, with their terracotta pots.
        for (int k = 0; k < 2; k++) {
            float cx = along ? (k == 0 ? x0 + 3 : x1 - 6) : mx - 1.5f, cy = along ? my - 1.5f : (k == 0 ? y0 + 3 : y1 - 6);
            p.setColor(0xFFD8CCB0);
            c.drawRect(cx, cy, cx + 3, cy + 3, p);
            p.setColor(0xFFB0623E);
            c.drawCircle(cx + 1.5f, cy + 1.5f, 0.9f, p);
        }
        if (r.nextInt(3) == 0) {
            // A skylight.
            p.setColor(0xFF6E8CA0);
            float sx = along ? x0 + (x1 - x0) * 0.6f : mx + 2, sy = along ? my + 2 : y0 + (y1 - y0) * 0.6f;
            c.drawRect(sx, sy, sx + 3, sy + 3.5f, p);
        }
    }

    private static void mexicanHouse(Canvas c, Paint p, float x0, float y0, float x1, float y1, int roof, int wall, Random r) {
        if (roof == 0xFFB4583A || roof == 0xFFA04A30 || roof == 0xFFBE6A44) {
            // Clay tiles on a low hip roof.
            p.setColor(City.darken(roof, 0.6f));
            c.drawRect(x0, y0, x1, y1, p);
            hip(c, p, x0 + 0.5f, y0 + 0.5f, x1 - 0.5f, y1 - 0.5f, roof);
            courses(c, p, x0 + 1, y0 + 1, x1 - 1, y1 - 1, City.darken(roof, 0.78f), 1.8f, (x1 - x0) < (y1 - y0));
            return;
        }
        // A flat concrete roof behind a painted parapet, with the black water tank and the rebar left
        // sticking up for the next floor.
        p.setColor(City.darken(wall, 0.85f));
        c.drawRect(x0, y0, x1, y1, p);
        p.setColor(roof);
        c.drawRect(x0 + 1.4f, y0 + 1.4f, x1 - 1.4f, y1 - 1.4f, p);
        p.setColor(City.darken(roof, 0.9f));
        c.drawRect(x0 + 1.4f, (y0 + y1) / 2, x1 - 1.4f, (y0 + y1) / 2 + 0.4f, p);
        tank(c, p, x0 + 4.5f + r.nextFloat() * Math.max(1, x1 - x0 - 10), y0 + 4.5f, 2.6f, 0xFF1E1E20);
        p.setColor(0xFF5A4030);
        for (int k = 0; k < 4; k++) {
            float cx = k % 2 == 0 ? x0 + 2.3f : x1 - 2.3f, cy = k < 2 ? y0 + 2.3f : y1 - 2.3f;
            c.drawCircle(cx - 0.5f, cy, 0.35f, p);
            c.drawCircle(cx + 0.5f, cy, 0.35f, p);
            c.drawCircle(cx, cy + 0.6f, 0.35f, p);
        }
        if (r.nextBoolean()) washing(c, p, x0 + 3, y1 - 5, x1 - 4, r);
        if (r.nextInt(3) == 0) {
            // The stair hut up to the roof.
            p.setColor(City.lighten(wall, 0.1f));
            c.drawRect(x1 - 7, y1 - 8, x1 - 2.5f, y1 - 2.5f, p);
        }
    }

    // ------------------------------------------------------------------ blocks and offices

    /** Paris: zinc mansard roofs with a ring of dormer windows, chimney pots along the party walls, a courtyard. */
    private static void mansard(Canvas c, Paint p, float x0, float y0, float x1, float y1, int roof, Random r) {
        float bw = x1 - x0, bh = y1 - y0, m = Math.min(6, Math.min(bw, bh) / 4);
        p.setColor(City.darken(roof, 0.62f));
        c.drawRect(x0, y0, x1, y1, p);
        // The steep lower slope, lit on the north and west.
        quad(c, p, City.lighten(roof, 0.1f), x0, y0, x1, y0, x1 - m, y0 + m, x0 + m, y0 + m);
        quad(c, p, City.darken(roof, 0.82f), x0, y1, x1, y1, x1 - m, y1 - m, x0 + m, y1 - m);
        quad(c, p, City.lighten(roof, 0.04f), x0, y0, x0 + m, y0 + m, x0 + m, y1 - m, x0, y1);
        quad(c, p, City.darken(roof, 0.74f), x1, y0, x1 - m, y0 + m, x1 - m, y1 - m, x1, y1);
        p.setColor(City.lighten(roof, 0.2f));
        c.drawRect(x0 + m, y0 + m, x1 - m, y1 - m, p);
        // Dormers.
        for (float x = x0 + m + 2; x < x1 - m - 3; x += 7) {
            dormer(c, p, x, y0 + m * 0.25f, 3, m * 0.6f);
            dormer(c, p, x, y1 - m * 0.85f, 3, m * 0.6f);
        }
        for (float y = y0 + m + 2; y < y1 - m - 3; y += 7) {
            dormer(c, p, x0 + m * 0.25f, y, m * 0.6f, 3);
            dormer(c, p, x1 - m * 0.85f, y, m * 0.6f, 3);
        }
        // Chimney stacks with rows of pots.
        boolean along = bw >= bh;
        for (int k = 0; k < 2; k++) {
            float sx = along ? x0 + bw * (0.3f + 0.4f * k) : x0 + m + 1, sy = along ? y0 + m + 1 : y0 + bh * (0.3f + 0.4f * k);
            float sw = along ? 3 : 8, sh = along ? 8 : 3;
            p.setColor(0x44000000);
            c.drawRect(sx + 1, sy + 1, sx + sw + 1, sy + sh + 1, p);
            p.setColor(0xFFD8CCB0);
            c.drawRect(sx, sy, sx + sw, sy + sh, p);
            p.setColor(0xFFB0623E);
            for (int q = 0; q < 4; q++) {
                float px = along ? sx + 1.5f : sx + 1 + q * 2, py = along ? sy + 1 + q * 2 : sy + 1.5f;
                c.drawCircle(px, py, 0.7f, p);
            }
        }
        if (bw >= 44 && bh >= 44) {
            // The light well in the middle.
            float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, s = Math.min(bw, bh) * 0.16f;
            p.setColor(0xFF5A5C5E);
            c.drawRect(cx - s, cy - s, cx + s, cy + s, p);
            p.setColor(0xFF7A7C7E);
            c.drawRect(cx - s + 1, cy - s + 1, cx + s - 1, cy + s - 1, p);
        }
    }

    private static void dormer(Canvas c, Paint p, float x, float y, float w, float h) {
        p.setColor(0xFFE8E2D4);
        c.drawRect(x, y, x + w, y + h, p);
        p.setColor(0xFF34414E);
        c.drawRect(x + w * 0.25f, y + h * 0.25f, x + w * 0.75f, y + h * 0.75f, p);
    }

    /** Japan: pale concrete roofs crowded with air conditioners, a water tank on legs and a stair house. */
    private static void japaneseBlock(Canvas c, Paint p, float x0, float y0, float x1, float y1, int roof, boolean flats, Random r) {
        float bw = x1 - x0, bh = y1 - y0;
        p.setColor(City.darken(roof, 0.72f));
        c.drawRect(x0, y0, x1, y1, p);
        p.setColor(roof);
        c.drawRect(x0 + 1.6f, y0 + 1.6f, x1 - 1.6f, y1 - 1.6f, p);
        courses(c, p, x0 + 1.6f, y0 + 1.6f, x1 - 1.6f, y1 - 1.6f, City.darken(roof, 0.93f), 4f, false);
        // Stair and lift house.
        p.setColor(0x44000000);
        c.drawRect(x0 + 5, y0 + 5, x0 + 15, y0 + 13, p);
        p.setColor(City.lighten(roof, 0.12f));
        c.drawRect(x0 + 4, y0 + 4, x0 + 14, y0 + 12, p);
        // Square water tank on its stand.
        float tx = x1 - 12, ty = y0 + 4;
        p.setColor(0xFF6A6E72);
        c.drawRect(tx, ty, tx + 7, ty + 7, p);
        p.setColor(0xFFB8C4CC);
        c.drawRect(tx + 1, ty + 1, tx + 6, ty + 6, p);
        int n = Math.max(2, (int) (bw * bh / 300));
        for (int i = 0; i < Math.min(n, 14); i++) acUnit(c, p, x0 + 4 + r.nextFloat() * Math.max(1, bw - 12), y0 + 15 + r.nextFloat() * Math.max(1, bh - 21));
        if (!flats && bw >= 30 && r.nextInt(3) == 0) {
            // A rooftop billboard frame facing the street.
            p.setColor(0xFF4A4E52);
            c.drawRect(x0 + 3, y1 - 6, x1 - 3, y1 - 3, p);
            int[] ads = {0xFFE03A3A, 0xFF3A8AE0, 0xFFF0C040, 0xFF40B070};
            p.setColor(ads[r.nextInt(ads.length)]);
            c.drawRect(x0 + 4, y1 - 5.5f, x1 - 4, y1 - 3.5f, p);
        }
        if (flats && r.nextBoolean()) washing(c, p, x0 + 4, y1 - 6, x0 + Math.min(bw - 4, 30), r);
    }

    /** Mexico: flat roofs behind parapets, rows of black water tanks, a satellite dish and a roof terrace. */
    private static void mexicanBlock(Canvas c, Paint p, float x0, float y0, float x1, float y1, int roof, int wall, Random r) {
        float bw = x1 - x0, bh = y1 - y0;
        p.setColor(City.darken(wall, 0.8f));
        c.drawRect(x0, y0, x1, y1, p);
        p.setColor(roof);
        c.drawRect(x0 + 1.6f, y0 + 1.6f, x1 - 1.6f, y1 - 1.6f, p);
        int tanks = Math.max(1, Math.min(8, (int) (bw * bh / 500)));
        int cols = Math.max(1, (int) ((bw - 8) / 7));
        for (int i = 0; i < tanks; i++) tank(c, p, x0 + 5 + (i % cols) * 7, y0 + 5 + (i / cols) * 7, 2.6f, 0xFF1E1E20);
        p.setColor(0xFFD8D8D8);
        float dx = x1 - 7, dy = y1 - 7;
        c.drawCircle(dx, dy, 2.4f, p);
        p.setColor(0xFF8A8A8A);
        c.drawCircle(dx + 0.5f, dy + 0.5f, 0.8f, p);
        if (bw >= 30 && r.nextBoolean()) {
            // A terrace with potted plants.
            float tx0 = x0 + 4, ty0 = y1 - Math.min(16, bh / 2), tx1 = x0 + Math.min(bw - 12, 26), ty1 = y1 - 4;
            p.setColor(0xFFC87A52);
            c.drawRect(tx0, ty0, tx1, ty1, p);
            courses(c, p, tx0, ty0, tx1, ty1, 0xFFB06A46, 2.5f, true);
            courses(c, p, tx0, ty0, tx1, ty1, 0xFFB06A46, 2.5f, false);
            p.setColor(0xFF3B742D);
            for (float x = tx0 + 2; x < tx1 - 1; x += 4) c.drawCircle(x, ty0 + 1.5f, 1.3f, p);
        }
        washing(c, p, x0 + 4, (y0 + y1) / 2, x0 + Math.min(bw - 4, 24), r);
    }

    // ------------------------------------------------------------------ shops

    private static void shop(Canvas c, Paint p, float x0, float y0, float x1, float y1, int roof, int wall, int id, Random r) {
        p.setColor(City.darken(id == Country.MEXICO ? wall : roof, 0.7f));
        c.drawRect(x0, y0, x1, y1, p);
        p.setColor(roof);
        c.drawRect(x0 + 1.5f, y0 + 1.5f, x1 - 1.5f, y1 - 1.5f, p);
        boolean along = (x1 - x0) >= (y1 - y0);
        if (id == Country.FRANCE || id == Country.AUSTRALIA) {
            // A striped awning over the pavement side.
            int[] awn = id == Country.FRANCE ? new int[]{0xFFB03A2E, 0xFF2E6A4A, 0xFF2E4F8A} : new int[]{0xFF2E6A8A, 0xFFE0A030, 0xFF8A3A2E};
            int col = awn[r.nextInt(awn.length)];
            for (int k = 0; k < 40; k++) {
                float a0 = along ? x0 + 1 + k * 2.5f : y0 + 1 + k * 2.5f;
                if (a0 > (along ? x1 - 2 : y1 - 2)) break;
                p.setColor(k % 2 == 0 ? col : 0xFFF2F0EA);
                if (along) c.drawRect(a0, y1 - 4, Math.min(a0 + 2.5f, x1 - 1), y1 - 0.5f, p);
                else c.drawRect(x1 - 4, a0, x1 - 0.5f, Math.min(a0 + 2.5f, y1 - 1), p);
            }
            acUnit(c, p, x0 + 3 + r.nextFloat() * Math.max(1, x1 - x0 - 12), y0 + 3);
        } else if (id == Country.JAPAN) {
            // Signboards stacked up the front.
            int[] signs = {0xFFE03A3A, 0xFFF0C040, 0xFF3A8AE0, 0xFF40B070, 0xFFE070B0};
            for (int k = 0; k < 3; k++) {
                p.setColor(signs[r.nextInt(signs.length)]);
                float sx = x0 + 3 + k * Math.max(4, (x1 - x0 - 8) / 3);
                c.drawRect(sx, y1 - 5, sx + 2.5f, y1 - 1, p);
            }
            acUnit(c, p, x0 + 3, y0 + 3);
        } else {
            // A painted parapet in a loud colour and a water tank.
            p.setColor(wall);
            c.drawRect(x0, y1 - 2.2f, x1, y1, p);
            tank(c, p, x1 - 5, y0 + 5, 2.4f, 0xFF1E1E20);
        }
    }

    // ------------------------------------------------------------------ places of worship

    private static void temple(Canvas c, Paint p, float x0, float y0, float x1, float y1) {
        int roof = 0xFF3A4048;
        // Deep eaves, a heavy tiled hip roof and gold finials on the ridge.
        p.setColor(0xFF2A2E34);
        c.drawRect(x0, y0, x1, y1, p);
        float[] ridge = hip(c, p, x0 + 0.5f, y0 + 0.5f, x1 - 0.5f, y1 - 0.5f, roof);
        courses(c, p, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF2A3036, 1.2f, (x1 - x0) < (y1 - y0));
        p.setColor(0xFF5A6068);
        p.setStrokeWidth(1.2f);
        c.drawLine(x0 + 1, y0 + 1, x1 - 1, y0 + 1, p);
        c.drawLine(x0 + 1, y1 - 1, x1 - 1, y1 - 1, p);
        p.setColor(0xFF1E2228);
        p.setStrokeWidth(2.2f);
        c.drawLine(ridge[0], ridge[1], ridge[2], ridge[3], p);
        p.setColor(0xFFD8B040);
        c.drawCircle(ridge[0], ridge[1], 1.8f, p);
        c.drawCircle(ridge[2], ridge[3], 1.8f, p);
    }

    private static void shrine(Canvas c, Paint p, float x0, float y0, float x1, float y1) {
        // A green copper gable roof with crossed chigi at the ends, and a vermilion frame.
        boolean along = (x1 - x0) >= (y1 - y0);
        p.setColor(0xFFC8402A);
        c.drawRect(x0, y0, x1, y1, p);
        float mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
        p.setColor(0xFF5E9A86);
        if (along) c.drawRect(x0 + 1.5f, y0 + 1.5f, x1 - 1.5f, my, p);
        else c.drawRect(x0 + 1.5f, y0 + 1.5f, mx, y1 - 1.5f, p);
        p.setColor(0xFF4A7E6C);
        if (along) c.drawRect(x0 + 1.5f, my, x1 - 1.5f, y1 - 1.5f, p);
        else c.drawRect(mx, y0 + 1.5f, x1 - 1.5f, y1 - 1.5f, p);
        courses(c, p, x0 + 1.5f, y0 + 1.5f, x1 - 1.5f, y1 - 1.5f, 0xFF3E6A5A, 1.6f, along);
        p.setColor(0xFF2A2A2A);
        p.setStrokeWidth(1.8f);
        if (along) c.drawLine(x0 + 2, my, x1 - 2, my, p);
        else c.drawLine(mx, y0 + 2, mx, y1 - 2, p);
        p.setColor(0xFFD8B040);
        p.setStrokeWidth(0.8f);
        for (int k = 0; k < 2; k++) {
            float ex = along ? (k == 0 ? x0 + 3 : x1 - 3) : mx, ey = along ? my : (k == 0 ? y0 + 3 : y1 - 3);
            c.drawLine(ex - 2, ey - 2, ex + 2, ey + 2, p);
            c.drawLine(ex - 2, ey + 2, ex + 2, ey - 2, p);
        }
        // Gold logs along the ridge.
        for (int k = 1; k < 5; k++) {
            float t = k / 5f;
            float lx = along ? x0 + (x1 - x0) * t : mx, ly = along ? my : y0 + (y1 - y0) * t;
            c.drawCircle(lx, ly, 0.9f, p);
        }
    }

    /** A Gothic church: a slate cross-shaped roof with a spire over the west front. */
    private static void cathedral(Canvas c, Paint p, float x0, float y0, float x1, float y1) {
        boolean along = (x1 - x0) >= (y1 - y0);
        float bw = x1 - x0, bh = y1 - y0;
        p.setColor(0xFFC8BCA0);
        c.drawRect(x0, y0, x1, y1, p);
        int slate = 0xFF4E5560;
        float mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
        // Nave and transept.
        float nw = along ? bh * 0.55f : bw * 0.55f;
        float nx0 = along ? x0 + 1 : mx - nw / 2, nx1 = along ? x1 - 1 : mx + nw / 2;
        float ny0 = along ? my - nw / 2 : y0 + 1, ny1 = along ? my + nw / 2 : y1 - 1;
        p.setColor(City.lighten(slate, 0.12f));
        if (along) c.drawRect(nx0, ny0, nx1, my, p);
        else c.drawRect(nx0, ny0, mx, ny1, p);
        p.setColor(slate);
        if (along) c.drawRect(nx0, my, nx1, ny1, p);
        else c.drawRect(mx, ny0, nx1, ny1, p);
        float tc = along ? x0 + bw * 0.62f : y0 + bh * 0.62f, tw = along ? bw * 0.12f : bh * 0.12f;
        p.setColor(City.lighten(slate, 0.06f));
        if (along) c.drawRect(tc - tw, y0 + 1, tc + tw, y1 - 1, p);
        else c.drawRect(x0 + 1, tc - tw, x1 - 1, tc + tw, p);
        p.setColor(0xFF2E333A);
        p.setStrokeWidth(0.9f);
        if (along) {
            c.drawLine(nx0, my, nx1, my, p);
            c.drawLine(tc, y0 + 1, tc, y1 - 1, p);
        } else {
            c.drawLine(mx, ny0, mx, ny1, p);
            c.drawLine(x0 + 1, tc, x1 - 1, tc, p);
        }
        // The spire: a pyramid seen from above.
        float sx = along ? x0 + Math.min(bw * 0.12f, 9) : mx, sy = along ? my : y0 + Math.min(bh * 0.12f, 9);
        float s = Math.min(Math.min(bw, bh) * 0.22f, 8);
        quad(c, p, 0xFF5E6672, sx - s, sy - s, sx + s, sy - s, sx, sy, sx, sy);
        quad(c, p, 0xFF3A4048, sx - s, sy + s, sx + s, sy + s, sx, sy, sx, sy);
        quad(c, p, 0xFF4E5560, sx - s, sy - s, sx - s, sy + s, sx, sy, sx, sy);
        quad(c, p, 0xFF2E333A, sx + s, sy - s, sx + s, sy + s, sx, sy, sx, sy);
        p.setColor(0xFFD8B040);
        c.drawCircle(sx, sy, 0.9f, p);
    }

    /** A Mexican church: ochre vaults, a dome in blue and yellow tiles, two bell towers on the front. */
    private static void domedChurch(Canvas c, Paint p, float x0, float y0, float x1, float y1, Random r) {
        boolean along = (x1 - x0) >= (y1 - y0);
        float bw = x1 - x0, bh = y1 - y0;
        p.setColor(0xFFC8A060);
        c.drawRect(x0, y0, x1, y1, p);
        p.setColor(0xFFD8B478);
        c.drawRect(x0 + 1.5f, y0 + 1.5f, x1 - 1.5f, y1 - 1.5f, p);
        courses(c, p, x0 + 1.5f, y0 + 1.5f, x1 - 1.5f, y1 - 1.5f, 0xFFC09858, 5f, along);
        // Tiled dome over the crossing.
        float dx = along ? x0 + bw * 0.62f : (x0 + x1) / 2, dy = along ? (y0 + y1) / 2 : y0 + bh * 0.62f;
        float rad = Math.min(bw, bh) * 0.32f;
        p.setColor(0x50000000);
        c.drawCircle(dx + 1.5f, dy + 2, rad, p);
        int[] tiles = {0xFF2E5FB0, 0xFFF0C040, 0xFFF2F2F2};
        int seg = 12;
        for (int k = 0; k < seg; k++) {
            p.setColor(tiles[k % 3]);
            path.reset();
            path.moveTo(dx, dy);
            for (int q = 0; q <= 3; q++) {
                double a = (k + q / 3.0) * Math.PI * 2 / seg;
                path.lineTo(dx + (float) Math.cos(a) * rad, dy + (float) Math.sin(a) * rad);
            }
            path.close();
            c.drawPath(path, p);
        }
        p.setColor(0x40FFFFFF);
        c.drawCircle(dx - rad * 0.3f, dy - rad * 0.3f, rad * 0.4f, p);
        p.setColor(0xFFE8E0D0);
        c.drawCircle(dx, dy, rad * 0.25f, p);
        p.setColor(0xFFD8B040);
        c.drawCircle(dx, dy, rad * 0.1f, p);
        // Twin bell towers at the front, each with a little dome.
        for (int k = 0; k < 2; k++) {
            float tx = along ? x0 + 2 : (k == 0 ? x0 + 2 : x1 - 9), ty = along ? (k == 0 ? y0 + 2 : y1 - 9) : y0 + 2;
            p.setColor(0x50000000);
            c.drawRect(tx + 1.5f, ty + 1.5f, tx + 8.5f, ty + 8.5f, p);
            p.setColor(0xFFE8D0A0);
            c.drawRect(tx, ty, tx + 7, ty + 7, p);
            p.setColor(0xFF2E5FB0);
            c.drawCircle(tx + 3.5f, ty + 3.5f, 2.4f, p);
            p.setColor(0xFFF2F2F2);
            c.drawCircle(tx + 3.5f, ty + 3.5f, 0.8f, p);
        }
    }

    // ------------------------------------------------------------------ building variants

    /** The roof of one of the many kinds of building (see {@link Variants}). */
    static boolean drawVariant(City city, Canvas c, Paint p, int[] b, Variants.V v) {
        if (v == null) return false;
        float T = City.T;
        float x0 = b[0] * T, y0 = b[1] * T, x1 = (b[0] + b[2]) * T, y1 = (b[1] + b[3]) * T, bw = x1 - x0, bh = y1 - y0;
        int roof = b[4], acc = v.accent;
        Random r = new Random(b[5] * 17L + 3);
        boolean along = bw >= bh;
        switch (v.roof) {
            case Variants.R_GABLE: {
                p.setColor(City.darken(roof, 0.65f));
                c.drawRect(x0, y0, x1, y1, p);
                float mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
                p.setColor(City.lighten(roof, 0.14f));
                if (along) c.drawRect(x0 + 1, y0 + 1, x1 - 1, my, p);
                else c.drawRect(x0 + 1, y0 + 1, mx, y1 - 1, p);
                p.setColor(roof);
                if (along) c.drawRect(x0 + 1, my, x1 - 1, y1 - 1, p);
                else c.drawRect(mx, y0 + 1, x1 - 1, y1 - 1, p);
                courses(c, p, x0 + 1, y0 + 1, x1 - 1, y1 - 1, City.darken(roof, 0.85f), 1.6f, !along);
                p.setColor(City.darken(roof, 0.5f));
                if (along) c.drawRect(x0 + 1, my - 0.7f, x1 - 1, my + 0.7f, p);
                else c.drawRect(mx - 0.7f, y0 + 1, mx + 0.7f, y1 - 1, p);
                if (r.nextBoolean()) {
                    p.setColor(0xFF6A5A50);
                    c.drawRect(x0 + bw * 0.72f, y0 + 3, x0 + bw * 0.72f + 4, y0 + 7, p);
                }
                break;
            }
            case Variants.R_HIP: {
                p.setColor(City.darken(roof, 0.6f));
                c.drawRect(x0, y0, x1, y1, p);
                hip(c, p, x0 + 0.5f, y0 + 0.5f, x1 - 0.5f, y1 - 0.5f, roof);
                courses(c, p, x0 + 1, y0 + 1, x1 - 1, y1 - 1, City.darken(roof, 0.82f), 1.5f, !along);
                break;
            }
            case Variants.R_TILES: {
                p.setColor(City.darken(roof, 0.6f));
                c.drawRect(x0, y0, x1, y1, p);
                hip(c, p, x0 + 0.5f, y0 + 0.5f, x1 - 0.5f, y1 - 0.5f, roof);
                courses(c, p, x0 + 1, y0 + 1, x1 - 1, y1 - 1, City.darken(roof, 0.72f), 1.2f, !along);
                courses(c, p, x0 + 1, y0 + 1, x1 - 1, y1 - 1, City.darken(roof, 0.88f), 2.4f, along);
                break;
            }
            case Variants.R_MANSARD:
                mansard(c, p, x0, y0, x1, y1, roof, r);
                break;
            case Variants.R_SAWTOOTH: {
                p.setColor(City.darken(roof, 0.65f));
                c.drawRect(x0, y0, x1, y1, p);
                // Rows of north-light teeth: a lit slope and a strip of glass.
                for (float t = (along ? x0 : y0) + 2; t < (along ? x1 : y1) - 6; t += 8) {
                    p.setColor(roof);
                    if (along) c.drawRect(t, y0 + 2, t + 5.5f, y1 - 2, p);
                    else c.drawRect(x0 + 2, t, x1 - 2, t + 5.5f, p);
                    p.setColor(0xCC9CC3D9);
                    if (along) c.drawRect(t + 5.5f, y0 + 2, t + 7.5f, y1 - 2, p);
                    else c.drawRect(x0 + 2, t + 5.5f, x1 - 2, t + 7.5f, p);
                }
                break;
            }
            case Variants.R_DOME: {
                flatBase(c, p, x0, y0, x1, y1, roof);
                float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, rad = Math.min(bw, bh) * 0.3f;
                p.setColor(0x50000000);
                c.drawCircle(cx + 2, cy + 2.5f, rad, p);
                p.setColor(City.darken(acc, 0.85f));
                c.drawCircle(cx, cy, rad, p);
                p.setColor(acc);
                c.drawCircle(cx - rad * 0.15f, cy - rad * 0.15f, rad * 0.8f, p);
                p.setColor(City.lighten(acc, 0.3f));
                c.drawCircle(cx - rad * 0.35f, cy - rad * 0.35f, rad * 0.3f, p);
                p.setColor(City.darken(acc, 0.6f));
                p.setStrokeWidth(0.5f);
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    c.drawLine(cx, cy, cx + (float) Math.cos(a) * rad, cy + (float) Math.sin(a) * rad, p);
                }
                // A row of columns along the front.
                p.setColor(0xFFE8E2D4);
                for (float u = x0 + 4; u < x1 - 4; u += 5) c.drawRect(u, y1 - 5, u + 2, y1 - 2, p);
                break;
            }
            case Variants.R_GLASS: {
                p.setColor(City.darken(roof, 0.6f));
                c.drawRect(x0, y0, x1, y1, p);
                p.setColor(roof);
                c.drawRect(x0 + 1.5f, y0 + 1.5f, x1 - 1.5f, y1 - 1.5f, p);
                p.setColor(City.darken(roof, 0.75f));
                p.setStrokeWidth(0.6f);
                for (float u = x0 + 6; u < x1 - 2; u += 6) c.drawLine(u, y0 + 1.5f, u, y1 - 1.5f, p);
                for (float u = y0 + 6; u < y1 - 2; u += 6) c.drawLine(x0 + 1.5f, u, x1 - 1.5f, u, p);
                p.setColor(0x40FFFFFF);
                quad(c, p, 0x40FFFFFF, x0 + 2, y0 + 2, x0 + bw * 0.45f, y0 + 2, x0 + 2, y0 + bh * 0.45f, x0 + 2, y0 + bh * 0.45f);
                if (b[7] >= 12) {
                    // A crown of plant and a maintenance cradle on the tallest towers.
                    p.setColor(0xFF4A4E52);
                    c.drawRect(x0 + bw * 0.3f, y0 + bh * 0.3f, x0 + bw * 0.7f, y0 + bh * 0.7f, p);
                    p.setColor(0xFF7A7E82);
                    c.drawRect(x0 + bw * 0.34f, y0 + bh * 0.34f, x0 + bw * 0.66f, y0 + bh * 0.66f, p);
                }
                break;
            }
            case Variants.R_GREEN: {
                flatBase(c, p, x0, y0, x1, y1, roof);
                p.setColor(0xFF4F8A3A);
                c.drawRect(x0 + 3, y0 + 3, x1 - 3, y1 - 3, p);
                for (int k = 0; k < (int) (bw * bh / 50); k++) {
                    float gx = x0 + 4 + r.nextFloat() * (bw - 8), gy = y0 + 4 + r.nextFloat() * (bh - 8);
                    p.setColor(r.nextInt(5) == 0 ? acc : r.nextBoolean() ? 0xFF3B742D : 0xFF6AA84F);
                    c.drawCircle(gx, gy, 1.2f + r.nextFloat() * 1.6f, p);
                }
                p.setColor(0xFFB8A888);
                c.drawRect(x0 + 3, (y0 + y1) / 2 - 0.8f, x1 - 3, (y0 + y1) / 2 + 0.8f, p);
                break;
            }
            case Variants.R_POOL: case Variants.R_HOUSEPOOL: {
                if (v.roof == Variants.R_HOUSEPOOL) {
                    // A house roof with the pool in the back garden drawn on the lot's far end.
                    p.setColor(City.darken(roof, 0.65f));
                    c.drawRect(x0, y0, x1, y1, p);
                    hip(c, p, x0 + 0.5f, y0 + 0.5f, x1 - 0.5f, y1 - bh * 0.3f, roof);
                    p.setColor(0xFFD8D4C8);
                    c.drawRect(x0, y1 - bh * 0.3f, x1, y1, p);
                    p.setColor(0xFF4FB8D8);
                    c.drawRect(x0 + 2, y1 - bh * 0.3f + 2, x1 - 2, y1 - 2, p);
                    p.setColor(0x60FFFFFF);
                    c.drawRect(x0 + 3, y1 - bh * 0.3f + 3, x0 + bw * 0.4f, y1 - bh * 0.3f + 4, p);
                    break;
                }
                flatBase(c, p, x0, y0, x1, y1, roof);
                float pw = Math.min(bw * 0.5f, 40), ph = Math.min(bh * 0.35f, 20);
                float px = x0 + 5, py = y1 - ph - 5;
                p.setColor(0xFFE8E2D4);
                c.drawRect(px - 2, py - 2, px + pw + 2, py + ph + 2, p);
                p.setColor(0xFF4FB8D8);
                c.drawRect(px, py, px + pw, py + ph, p);
                p.setColor(0x60FFFFFF);
                c.drawRect(px + 1, py + 1, px + pw * 0.5f, py + 2, p);
                // Sun loungers and parasols.
                for (float u = px + pw + 5; u < x1 - 6; u += 7) {
                    p.setColor(0xFFF2F2F2);
                    c.drawRect(u, py, u + 3, py + 7, p);
                    p.setColor(acc);
                    c.drawCircle(u + 1.5f, py - 4, 3, p);
                }
                units(c, p, x0, y0, x1, y0 + bh * 0.5f, r, 2);
                break;
            }
            case Variants.R_HELIPAD: {
                flatBase(c, p, x0, y0, x1, y1, roof);
                float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, rad = Math.min(Math.min(bw, bh) * 0.3f, 16);
                p.setColor(0xFF3F4347);
                c.drawCircle(cx, cy, rad, p);
                p.setColor(acc);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.4f);
                c.drawCircle(cx, cy, rad * 0.82f, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFEEEEEE);
                c.drawRect(cx - rad * 0.38f, cy - rad * 0.45f, cx - rad * 0.22f, cy + rad * 0.45f, p);
                c.drawRect(cx + rad * 0.22f, cy - rad * 0.45f, cx + rad * 0.38f, cy + rad * 0.45f, p);
                c.drawRect(cx - rad * 0.3f, cy - rad * 0.08f, cx + rad * 0.3f, cy + rad * 0.08f, p);
                break;
            }
            case Variants.R_BARREL: {
                p.setColor(City.darken(roof, 0.6f));
                c.drawRect(x0, y0, x1, y1, p);
                // A curved roof: bands from dark at the edges to light along the crown.
                int bands = 8;
                for (int k = 0; k < bands; k++) {
                    float t0 = k / (float) bands, t1 = (k + 1) / (float) bands;
                    float light = 1 - Math.abs((t0 + t1) / 2 - 0.4f) * 1.4f;
                    p.setColor(light > 0.5f ? City.lighten(roof, (light - 0.5f) * 0.4f) : City.darken(roof, 0.75f + light * 0.5f));
                    if (along) c.drawRect(x0 + 1, y0 + 1 + (bh - 2) * t0, x1 - 1, y0 + 1 + (bh - 2) * t1, p);
                    else c.drawRect(x0 + 1 + (bw - 2) * t0, y0 + 1, x0 + 1 + (bw - 2) * t1, y1 - 1, p);
                }
                courses(c, p, x0 + 1, y0 + 1, x1 - 1, y1 - 1, City.darken(roof, 0.82f), 3f, along);
                break;
            }
            case Variants.R_ANTENNA: {
                flatBase(c, p, x0, y0, x1, y1, roof);
                float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
                p.setColor(City.darken(roof, 0.8f));
                c.drawRect(cx - bw * 0.25f, cy - bh * 0.25f, cx + bw * 0.25f, cy + bh * 0.25f, p);
                p.setColor(0xFFB8BCC0);
                c.drawCircle(cx, cy, 3, p);
                p.setColor(0xFF8A8E92);
                p.setStrokeWidth(0.6f);
                for (int k = 0; k < 4; k++) c.drawLine(cx, cy, cx + (k < 2 ? -1 : 1) * bw * 0.22f, cy + (k % 2 == 0 ? -1 : 1) * bh * 0.22f, p);
                p.setColor(0xFFE03A30);
                c.drawCircle(cx, cy, 1.2f, p);
                // Satellite dishes.
                for (int k = 0; k < 3; k++) {
                    p.setColor(0xFFE8E8E8);
                    c.drawCircle(x0 + 6 + k * 7, y1 - 6, 2.4f, p);
                }
                break;
            }
            case Variants.R_STACKS: {
                flatBase(c, p, x0, y0, x1, y1, roof);
                courses(c, p, x0 + 2, y0 + 2, x1 - 2, y1 - 2, City.darken(roof, 0.88f), 4f, along);
                int n = Math.max(1, Math.min(4, (int) (Math.max(bw, bh) / 40)));
                for (int k = 0; k < n; k++) {
                    float cx = along ? x0 + bw * (k + 0.5f) / n : x0 + bw * 0.3f, cy = along ? y0 + bh * 0.3f : y0 + bh * (k + 0.5f) / n;
                    p.setColor(0x60000000);
                    c.drawCircle(cx + 3, cy + 4, 4.5f, p);
                    p.setColor(0xFFB8B4AC);
                    c.drawCircle(cx, cy, 4.5f, p);
                    p.setColor(acc);
                    c.drawCircle(cx, cy, 3.3f, p);
                    p.setColor(0xFF1E1E20);
                    c.drawCircle(cx, cy, 2, p);
                }
                // Pipes along the roof.
                p.setColor(0xFF8A8E92);
                if (along) c.drawRect(x0 + 3, y1 - 7, x1 - 3, y1 - 5.5f, p);
                else c.drawRect(x1 - 7, y0 + 3, x1 - 5.5f, y1 - 3, p);
                break;
            }
            case Variants.R_TANKS: {
                flatBase(c, p, x0, y0, x1, y1, roof);
                int n = Math.max(2, Math.min(8, (int) (bw * bh / 300)));
                float rad = Math.min(7, Math.min(bw, bh) / 6);
                for (int k = 0; k < n; k++) {
                    float cx = x0 + rad + 3 + (k % 4) * (rad * 2 + 3), cy = y0 + rad + 3 + (k / 4) * (rad * 2 + 3);
                    if (cx > x1 - rad || cy > y1 - rad) continue;
                    tank(c, p, cx, cy, rad, k % 2 == 0 ? 0xFFC8CCD0 : City.lighten(acc, 0.4f));
                }
                break;
            }
            case Variants.R_SOLAR: {
                if (v.base == City.HOUSE) {
                    p.setColor(City.darken(roof, 0.6f));
                    c.drawRect(x0, y0, x1, y1, p);
                    float[] ridge = hip(c, p, x0 + 0.5f, y0 + 0.5f, x1 - 0.5f, y1 - 0.5f, roof);
                    solar(c, p, x0 + 3, y0 + 3, x1 - 3, Math.max(y0 + 6, ridge[1] - 1.5f));
                    break;
                }
                flatBase(c, p, x0, y0, x1, y1, roof);
                for (float t = y0 + 4; t < y1 - 8; t += 9) solar(c, p, x0 + 4, t, x1 - 4, t + 6);
                break;
            }
            case Variants.R_COURTYARD: {
                // A block round a courtyard: roofs on all four wings, a garden or patio in the middle.
                float m = Math.min(bw, bh) * 0.3f;
                p.setColor(City.darken(roof, 0.65f));
                c.drawRect(x0, y0, x1, y1, p);
                hip(c, p, x0, y0, x1, y0 + m, roof);
                hip(c, p, x0, y1 - m, x1, y1, roof);
                hip(c, p, x0, y0 + m, x0 + m, y1 - m, roof);
                hip(c, p, x1 - m, y0 + m, x1, y1 - m, roof);
                p.setColor(v.base == City.HOUSE || r.nextBoolean() ? 0xFF6AA84F : 0xFFD8C8A8);
                c.drawRect(x0 + m, y0 + m, x1 - m, y1 - m, p);
                p.setColor(0xFF3B742D);
                c.drawCircle((x0 + x1) / 2, (y0 + y1) / 2, Math.min(bw, bh) * 0.1f, p);
                break;
            }
            case Variants.R_STRIPES: {
                // A shop with its striped awning out front and a sign over the door.
                flatBase(c, p, x0, y0, x1, y1, roof);
                units(c, p, x0, y0, x1, y0 + bh * 0.6f, r, 1);
                for (int k = 0; k < 60; k++) {
                    float a0 = (along ? x0 : y0) + 1 + k * 2.5f;
                    if (a0 > (along ? x1 - 2 : y1 - 2)) break;
                    p.setColor(k % 2 == 0 ? acc : 0xFFF2F0EA);
                    if (along) c.drawRect(a0, y1 - 4.5f, Math.min(a0 + 2.5f, x1 - 1), y1 - 0.5f, p);
                    else c.drawRect(x1 - 4.5f, a0, x1 - 0.5f, Math.min(a0 + 2.5f, y1 - 1), p);
                }
                break;
            }
            case Variants.R_CLOCK: {
                flatBase(c, p, x0, y0, x1, y1, roof);
                hip(c, p, x0 + 3, y0 + 3, x1 - 3, y1 - 3, roof);
                float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, rad = Math.min(bw, bh) * 0.15f;
                p.setColor(0xFF4A4E56);
                c.drawRect(cx - rad - 1, cy - rad - 1, cx + rad + 1, cy + rad + 1, p);
                p.setColor(0xFFF2EEE0);
                c.drawCircle(cx, cy, rad, p);
                p.setColor(0xFF2A2A2A);
                p.setStrokeWidth(0.8f);
                c.drawLine(cx, cy, cx, cy - rad * 0.8f, p);
                c.drawLine(cx, cy, cx + rad * 0.5f, cy, p);
                break;
            }
            case Variants.R_PARAPET: {
                p.setColor(City.darken(roof, 0.55f));
                c.drawRect(x0, y0, x1, y1, p);
                p.setColor(City.lighten(roof, 0.1f));
                c.drawRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, p);
                p.setColor(roof);
                c.drawRect(x0 + 3, y0 + 3, x1 - 3, y1 - 3, p);
                units(c, p, x0, y0, x1, y1, r, 3);
                break;
            }
            default: {
                flatBase(c, p, x0, y0, x1, y1, roof);
                units(c, p, x0, y0, x1, y1, r, 2 + (int) (bw * bh / 900));
                break;
            }
        }
        if (v.sign != null && bw >= 18 && bh >= 12) sign(c, p, v.sign, x0, y0, x1, y1, acc);
        return true;
    }

    private static void flatBase(Canvas c, Paint p, float x0, float y0, float x1, float y1, int roof) {
        p.setColor(City.darken(roof, 0.65f));
        c.drawRect(x0, y0, x1, y1, p);
        p.setColor(roof);
        c.drawRect(x0 + 1.5f, y0 + 1.5f, x1 - 1.5f, y1 - 1.5f, p);
        p.setColor(City.lighten(roof, 0.06f));
        c.drawRect(x0 + 4, y0 + 4, x1 - 4, y1 - 4, p);
    }

    private static void units(Canvas c, Paint p, float x0, float y0, float x1, float y1, Random r, int n) {
        for (int k = 0; k < n; k++) {
            float ux = x0 + 4 + r.nextFloat() * Math.max(1, x1 - x0 - 12), uy = y0 + 4 + r.nextFloat() * Math.max(1, y1 - y0 - 10);
            acUnit(c, p, ux, uy);
        }
    }

    /** The name of the place on a board on the roof (so it can be read from above). */
    private static void sign(Canvas c, Paint p, String text, float x0, float y0, float x1, float y1, int acc) {
        float bw = x1 - x0;
        float size = Math.min(7f, Math.max(3.5f, (bw - 8) / (text.length() * 0.62f)));
        p.setTextSize(size);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.CENTER);
        float tw = Math.min(bw - 4, p.measureText(text) + 5);
        float cx = (x0 + x1) / 2, cy = y0 + Math.max(6, (y1 - y0) * 0.22f);
        p.setColor(0x60000000);
        c.drawRect(cx - tw / 2 + 1, cy - size * 0.8f + 1, cx + tw / 2 + 1, cy + size * 0.45f + 1, p);
        p.setColor(acc);
        c.drawRect(cx - tw / 2, cy - size * 0.8f, cx + tw / 2, cy + size * 0.45f, p);
        int lum = ((acc >> 16) & 0xFF) * 3 + ((acc >> 8) & 0xFF) * 6 + (acc & 0xFF);
        p.setColor(lum > 1500 ? 0xFF1E1E20 : 0xFFF8F8F4);
        c.drawText(text, cx, cy + size * 0.12f, p);
        p.setFakeBoldText(false);
    }

    /** A pagoda seen from above: tiers of square tiled roofs shrinking to a gold spire. */
    private static void pagoda(Canvas c, Paint p, float x0, float y0, float x1, float y1) {
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, s = Math.min(x1 - x0, y1 - y0) / 2;
        for (int k = 0; k < 4; k++) {
            float h = s * (1 - k * 0.22f);
            hip(c, p, cx - h, cy - h, cx + h, cy + h, k % 2 == 0 ? 0xFF3A4048 : 0xFF464C56);
        }
        p.setColor(0xFFD8B040);
        c.drawCircle(cx, cy, 1.6f, p);
    }

    private static void solar(Canvas c, Paint p, float x0, float y0, float x1, float y1) {
        if (x1 - x0 < 4 || y1 - y0 < 4) return;
        p.setColor(0xFF8A8E94);
        c.drawRect(x0, y0, x1, y1, p);
        for (float y = y0 + 0.6f; y + 3 <= y1; y += 3.6f)
            for (float x = x0 + 0.6f; x + 4 <= x1; x += 4.6f) {
                p.setColor(0xFF1F3A66);
                c.drawRect(x, y, x + 4, y + 3, p);
            }
    }
}
