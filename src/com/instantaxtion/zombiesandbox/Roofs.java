package com.instantaxtion.zombiesandbox;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import java.util.Random;

/**
 * Roofs drawn into the city map (so they show from above and on top of the 3D buildings): the landmarks of the
 * real cities, the kinds of building in Variants, and the plant on top of flat roofs.
 */
final class Roofs {
    private Roofs() {
    }

    private static final Path path = new Path();
    private static final RectF oval = new RectF();

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


    /** (11.1) One rooftop air-conditioning unit: a shadow, the grey casing with its frame, and the fan grille. */
    static void rtu(Canvas c, Paint p, float x, float y, float uw, float uh) {
        p.setColor(0x48000000);
        c.drawRect(x + 1.1f, y + 1.3f, x + uw + 1.1f, y + uh + 1.3f, p);
        p.setColor(0xFF8E9296);
        c.drawRect(x, y, x + uw, y + uh, p);
        p.setColor(0xFFC2C6CA);
        c.drawRect(x + 0.5f, y + 0.5f, x + uw - 0.5f, y + uh - 0.5f, p);
        float fr = Math.min(uw, uh) * 0.34f, fx = x + uw - fr - 0.9f, fy = y + uh / 2;
        p.setColor(0xFF3E4246);
        c.drawCircle(fx, fy, fr, p);
        p.setColor(0xFF9A9EA2);
        c.drawCircle(fx, fy, fr * 0.32f, p);
        if (uw - fr * 2 > 2.4f) {
            p.setColor(0xFFA4A8AC);
            c.drawRect(x + 0.9f, y + 0.9f, fx - fr - 0.6f, y + uh - 0.9f, p);
        }
    }

    /**
     * (11.1) Rooftop air conditioning laid out the way it's installed: the units in a straight, evenly spaced row
     * along one side of the roof, clear of the edges, with the duct running along behind them to a riser. Fewer
     * than asked for if they won't fit.
     */
    static void hvac(Canvas c, Paint p, float x0, float y0, float x1, float y1, Random r, int n) {
        float aw = x1 - x0 - 9, ah = y1 - y0 - 9;
        if (n <= 0 || aw < 9 || ah < 8) return;
        boolean across = aw >= ah;
        float uw = across ? 7 : 6, uh = across ? 6 : 7, gap = 2.5f;
        float len = across ? aw : ah, step = (across ? uw : uh) + gap;
        n = Math.min(Math.min(n, 2 + (int) (len / 45)), Math.max(1, (int) ((len + gap) / step)));
        float row = n * step - gap;
        float start = (across ? x0 : y0) + 4.5f + (len - row) * (0.1f + r.nextFloat() * 0.8f);
        boolean near = r.nextBoolean();
        float cross = (across ? y0 : x0) + 4.5f + (across ? ah - uh : aw - uw) * (near ? 0.08f : 0.92f);
        // The duct: along behind the row, then in to its riser.
        float dw = 1.8f, dc = cross + (across ? (near ? uh + 1.2f : -1.2f - dw) : (near ? uw + 1.2f : -1.2f - dw));
        p.setColor(0x38000000);
        if (across) c.drawRect(start + 0.6f, dc + 0.6f, start + row + 0.6f, dc + dw + 0.6f, p);
        else c.drawRect(dc + 0.6f, start + 0.6f, dc + dw + 0.6f, start + row + 0.6f, p);
        p.setColor(0xFFB0B4B8);
        if (across) c.drawRect(start, dc, start + row, dc + dw, p);
        else c.drawRect(dc, start, dc + dw, start + row, p);
        float rx = across ? start + row + 1 : dc - 1, ry = across ? dc - 1 : start + row + 1;
        p.setColor(0xFF8A8E92);
        c.drawRect(rx, ry, rx + 3.6f, ry + 3.6f, p);
        for (int k = 0; k < n; k++) {
            float ux = across ? start + k * step : cross, uy = across ? cross : start + k * step;
            rtu(c, p, ux, uy, uw, uh);
        }
    }


    // ------------------------------------------------------------------ houses



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


    // ------------------------------------------------------------------ blocks and offices





    // ------------------------------------------------------------------ shops


    // ------------------------------------------------------------------ places of worship



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


    // ------------------------------------------------------------------ real landmarks (10.18)

    /** A landmark's roof and wall colours (the walls show in 3D). */
    static int[] landmarkColours(int style) {
        switch (style) {
            case RealCities.L_STEPPED: return new int[]{0xFF9A9488, 0xFFB8B0A0};
            case RealCities.L_CROWN: return new int[]{0xFFC8CCD0, 0xFFB8B4AC};
            case RealCities.L_OCTAGON: return new int[]{0xFF8AA6BE, 0xFF7E9AB2};
            case RealCities.L_HELIPAD: return new int[]{0xFF8E949A, 0xFFA8A496};
            case RealCities.L_SAIL: return new int[]{0xFF7C98B0, 0xFF6E8CA6};
            case RealCities.L_OBSERVATORY: return new int[]{0xFFE8E4DA, 0xFFE8E2D4};
            case RealCities.L_MARKET: return new int[]{0xFF5E6268, 0xFFB8A890};
            case RealCities.L_CATHEDRAL: return new int[]{0xFF4E5560, 0xFFC8BCA0};
            case RealCities.L_HALL: return new int[]{0xFFD8D4C8, 0xFFE0DCD0};
            case RealCities.L_PINK: return new int[]{0xFFB08A8A, 0xFFB48C88};
            case RealCities.L_DARK: return new int[]{0xFF2A2E34, 0xFF30343A};
            case RealCities.L_LIBRARY: return new int[]{0xFF7AA0B8, 0xFF8AAEC4};
            case RealCities.L_ARENA: return new int[]{0xFFC8CCD0, 0xFF8A9096};
            case RealCities.L_CLASSIC: return new int[]{0xFF6E8E7E, 0xFFD8D0BC};
            case RealCities.L_STATION: return new int[]{0xFF8A9AA4, 0xFFC8BCA0};
            case RealCities.L_THEATRE: return new int[]{0xFF2E7A4A, 0xFFB03A2A};
            case RealCities.L_CAMPUS: return new int[]{0xFFEAE6DC, 0xFFE4DCC8};
            case RealCities.L_CLOCK: return new int[]{0xFF8A4A32, 0xFFB8946C};
            case RealCities.L_ROUND: return new int[]{0xFF9AA8B4, 0xFFA8B4BE};
            case RealCities.L_WEDGE: return new int[]{0xFF8A8478, 0xFFD0C4A8};
            case RealCities.L_SLAB: return new int[]{0xFF8E8A82, 0xFFB8B2A4};
            case RealCities.L_SPIRAL: return new int[]{0xFFF0EEE8, 0xFFF0EEE8};
            case RealCities.L_BLOB: return new int[]{0xFFB0507A, 0xFF7A60B0};
            case RealCities.L_CURVES: return new int[]{0xFFD0D4D8, 0xFFC4C8CC};
            case RealCities.L_MANSION: return new int[]{0xFF5A4E46, 0xFFC8B89A};
            case RealCities.L_SIGN: return new int[]{0xFF5E6268, 0xFFB86A3A};
            default: return new int[]{0xFF8A8A8A, 0xFFA0A0A0};
        }
    }

    private static void glassGrid(Canvas c, Paint p, float x0, float y0, float x1, float y1, int col, float gap) {
        p.setColor(col);
        c.drawRect(x0, y0, x1, y1, p);
        p.setColor(City.darken(col, 0.82f));
        p.setStrokeWidth(0.5f);
        for (float x = x0 + gap; x < x1; x += gap) c.drawLine(x, y0, x, y1, p);
        for (float y = y0 + gap; y < y1; y += gap) c.drawLine(x0, y, x1, y, p);
        p.setColor(0x30FFFFFF);
        c.drawRect(x0, y0, (x0 + x1) / 2, y1, p);
    }

    private static void dome(Canvas c, Paint p, float x, float y, float r, int col, boolean ribs) {
        p.setColor(0x50000000);
        c.drawCircle(x + r * 0.15f, y + r * 0.2f, r, p);
        p.setColor(col);
        c.drawCircle(x, y, r, p);
        p.setColor(City.lighten(col, 0.25f));
        c.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.55f, p);
        if (ribs) {
            p.setColor(City.darken(col, 0.75f));
            p.setStrokeWidth(0.5f);
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                c.drawLine(x, y, x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r, p);
            }
        }
        p.setColor(City.lighten(col, 0.5f));
        c.drawCircle(x, y, Math.max(0.8f, r * 0.12f), p);
    }

    /** The roof of one of a real city's landmarks. False to draw it as an ordinary building. */
    static boolean drawLandmark(City city, Canvas c, Paint p, int[] b, City.Building own) {
        float T = City.T;
        float x0 = b[0] * T, y0 = b[1] * T, x1 = (b[0] + b[2]) * T, y1 = (b[1] + b[3]) * T, bw = x1 - x0, bh = y1 - y0;
        float mx = (x0 + x1) / 2, my = (y0 + y1) / 2, m = Math.min(bw, bh);
        int roof = b[4];
        boolean along = bw >= bh;
        switch (own.landmark) {
            case RealCities.L_STEPPED: {
                // Setback after setback up to the mast.
                for (int k = 0; k < 4; k++) {
                    float in = k * m * 0.11f;
                    p.setColor(k % 2 == 0 ? City.darken(roof, 0.92f + k * 0.03f) : City.lighten(roof, 0.05f + k * 0.04f));
                    c.drawRect(x0 + in, y0 + in, x1 - in, y1 - in, p);
                }
                p.setColor(0xFFDAD6CC);
                c.drawCircle(mx, my, m * 0.08f, p);
                p.setColor(0xFF6A6660);
                c.drawCircle(mx, my, m * 0.03f, p);
                return true;
            }
            case RealCities.L_CROWN: {
                p.setColor(City.darken(roof, 0.8f));
                c.drawRect(x0, y0, x1, y1, p);
                // The steel crown: terraced arches fanning out, with the triangular windows.
                for (int k = 0; k < 5; k++) {
                    float in = k * m * 0.09f;
                    p.setColor(k % 2 == 0 ? 0xFFDCE2E8 : 0xFF9AA2AA);
                    c.drawRect(x0 + in, y0 + in, x1 - in, y1 - in, p);
                    p.setColor(0xFF2A2E34);
                    for (int q = 1; q < 4; q++) c.drawCircle(x0 + in + (bw - 2 * in) * q / 4f, y0 + in + 1, 0.6f, p);
                }
                p.setColor(0xFFF2F4F6);
                c.drawCircle(mx, my, m * 0.06f, p);
                return true;
            }
            case RealCities.L_OCTAGON: {
                // A square twisted round on itself: glass triangles to a square crown.
                glassGrid(c, p, x0, y0, x1, y1, City.darken(roof, 0.9f), 4);
                path.reset();
                path.moveTo(mx, y0 + 1);
                path.lineTo(x1 - 1, my);
                path.lineTo(mx, y1 - 1);
                path.lineTo(x0 + 1, my);
                path.close();
                p.setColor(City.lighten(roof, 0.2f));
                c.drawPath(path, p);
                p.setColor(City.lighten(roof, 0.35f));
                c.drawRect(mx - m * 0.18f, my - m * 0.18f, mx + m * 0.18f, my + m * 0.18f, p);
                p.setColor(0xFFE8ECF0);
                c.drawCircle(mx, my, m * 0.05f, p);
                return true;
            }
            case RealCities.L_HELIPAD: {
                glassGrid(c, p, x0, y0, x1, y1, roof, 5);
                p.setColor(0xFFB8BCC0);
                c.drawCircle(mx, my, m * 0.45f, p);
                p.setColor(0xFF5A6066);
                c.drawCircle(mx, my, m * 0.36f, p);
                p.setColor(0xFFF2F2F2);
                c.drawRect(mx - m * 0.12f, my - m * 0.16f, mx - m * 0.07f, my + m * 0.16f, p);
                c.drawRect(mx + m * 0.07f, my - m * 0.16f, mx + m * 0.12f, my + m * 0.16f, p);
                c.drawRect(mx - m * 0.1f, my - m * 0.025f, mx + m * 0.1f, my + m * 0.025f, p);
                return true;
            }
            case RealCities.L_SAIL: {
                glassGrid(c, p, x0, y0, x1, y1, roof, 4);
                // The sail-shaped crown curving up to a spire.
                path.reset();
                path.moveTo(x0 + 1, y1 - 1);
                path.quadTo(x0 + bw * 0.2f, y0 + 1, x1 - 1, y0 + 1);
                path.lineTo(x1 - 1, y1 - 1);
                path.close();
                p.setColor(City.lighten(roof, 0.28f));
                c.drawPath(path, p);
                p.setColor(0xFFF0F4F8);
                c.drawCircle(x1 - m * 0.15f, y0 + m * 0.15f, m * 0.05f, p);
                return true;
            }
            case RealCities.L_OBSERVATORY: {
                p.setColor(0xFFD8D2C4);
                c.drawRect(x0, y0, x1, y1, p);
                p.setColor(0xFFEDE8DE);
                c.drawRect(x0 + 1.5f, y0 + 1.5f, x1 - 1.5f, y1 - 1.5f, p);
                // Three copper-green domes: the big one in the middle, a telescope each end.
                dome(c, p, mx, my, m * 0.36f, 0xFF3E7A6A, true);
                dome(c, p, x0 + bw * 0.12f, my, m * 0.22f, 0xFF3E7A6A, false);
                dome(c, p, x1 - bw * 0.12f, my, m * 0.22f, 0xFF3E7A6A, false);
                return true;
            }
            case RealCities.L_MARKET: {
                p.setColor(roof);
                c.drawRect(x0, y0, x1, y1, p);
                courses(c, p, x0, y0, x1, y1, City.darken(roof, 0.8f), 3, !along);
                // The red neon sign and the clock.
                p.setColor(0xFFC8282A);
                c.drawRect(x0 + bw * 0.25f, y1 - 6, x1 - bw * 0.25f, y1 - 1, p);
                city.label("PUBLIC MARKET", mx, y1 - 2, Math.min(5f, bw / 18f), 0xFFFFE0E0);
                p.setColor(0xFFF2F2E8);
                c.drawCircle(x0 + bw * 0.18f, y1 - 3.5f, 2.5f, p);
                return true;
            }
            case RealCities.L_CATHEDRAL:
                cathedral(c, p, x0, y0, x1, y1);
                if (own.name != null && own.name.startsWith("Notre")) {
                    // The twin towers at the west front.
                    p.setColor(0xFFB8AC90);
                    float s = m * 0.32f;
                    c.drawRect(x0 + 1, y0 + 1, x0 + 1 + s, y0 + 1 + s, p);
                    c.drawRect(x0 + 1, y1 - 1 - s, x0 + 1 + s, y1 - 1, p);
                }
                return true;
            case RealCities.L_HALL: {
                // The white tower stepping up to its pyramid top.
                p.setColor(City.darken(roof, 0.88f));
                c.drawRect(x0, y0, x1, y1, p);
                p.setColor(roof);
                c.drawRect(x0 + m * 0.12f, y0 + m * 0.12f, x1 - m * 0.12f, y1 - m * 0.12f, p);
                float r = m * 0.24f;
                quad(c, p, 0xFFE8E4DA, mx - r, my - r, mx + r, my - r, mx, my, mx, my);
                quad(c, p, 0xFFB8B4AA, mx - r, my + r, mx + r, my + r, mx, my, mx, my);
                quad(c, p, 0xFFD0CCC2, mx - r, my - r, mx - r, my + r, mx, my, mx, my);
                quad(c, p, 0xFFA8A49A, mx + r, my - r, mx + r, my + r, mx, my, mx, my);
                return true;
            }
            case RealCities.L_PINK: case RealCities.L_DARK: case RealCities.L_LIBRARY: case RealCities.L_ROUND: {
                boolean lib = own.landmark == RealCities.L_LIBRARY;
                glassGrid(c, p, x0, y0, x1, y1, roof, lib ? 3 : 5);
                if (lib) {
                    // The diamond steel net over the glass.
                    p.setColor(0x80303A44);
                    p.setStrokeWidth(0.6f);
                    for (float k = -bh; k < bw; k += 6) {
                        c.drawLine(x0 + k, y0, x0 + k + bh, y1, p);
                        c.drawLine(x0 + k + bh, y0, x0 + k, y1, p);
                    }
                }
                if (own.landmark == RealCities.L_ROUND) {
                    p.setColor(City.lighten(roof, 0.15f));
                    c.drawCircle(mx, my, m * 0.42f, p);
                    p.setColor(City.darken(roof, 0.8f));
                    c.drawCircle(mx, my, m * 0.3f, p);
                    p.setColor(0xFFE8E8E8);
                    c.drawCircle(mx, my, m * 0.06f, p);
                }
                if (own.landmark == RealCities.L_DARK) {
                    p.setColor(0xFF8A9096);
                    c.drawRect(mx - m * 0.1f, my - m * 0.1f, mx + m * 0.1f, my + m * 0.1f, p);
                }
                return true;
            }
            case RealCities.L_ARENA: {
                p.setColor(0xFF6A7076);
                c.drawRect(x0, y0, x1, y1, p);
                oval.set(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
                p.setColor(roof);
                c.drawOval(oval, p);
                oval.set(x0 + bw * 0.18f, y0 + bh * 0.18f, x1 - bw * 0.18f, y1 - bh * 0.18f);
                p.setColor(City.darken(roof, 0.88f));
                c.drawOval(oval, p);
                p.setColor(City.darken(roof, 0.7f));
                p.setStrokeWidth(0.5f);
                for (int k = 0; k < 12; k++) {
                    double a = k * Math.PI / 6;
                    c.drawLine(mx, my, mx + (float) Math.cos(a) * bw * 0.48f, my + (float) Math.sin(a) * bh * 0.48f, p);
                }
                return true;
            }
            case RealCities.L_CLASSIC: case RealCities.L_STATION: {
                boolean station = own.landmark != RealCities.L_CLASSIC;
                p.setColor(0xFFD0C6AE);
                c.drawRect(x0, y0, x1, y1, p);
                if (station) {
                    // The great arched train shed (or the hall's glass vault), ribbed.
                    float in = m * 0.18f;
                    p.setColor(0xFF8AA0AC);
                    c.drawRect(x0 + (along ? 2 : in), y0 + (along ? in : 2), x1 - (along ? 2 : in), y1 - (along ? in : 2), p);
                    p.setColor(0xFF4A5560);
                    p.setStrokeWidth(0.6f);
                    for (float k = 3; k < (along ? bw : bh) - 2; k += 4) {
                        if (along) c.drawLine(x0 + k, y0 + in, x0 + k, y1 - in, p);
                        else c.drawLine(x0 + in, y0 + k, x1 - in, y0 + k, p);
                    }
                } else {
                    // Green copper roofs round a court.
                    p.setColor(roof);
                    float t = m * 0.3f;
                    c.drawRect(x0 + 1, y0 + 1, x1 - 1, y0 + t, p);
                    c.drawRect(x0 + 1, y1 - t, x1 - 1, y1 - 1, p);
                    c.drawRect(x0 + 1, y0 + 1, x0 + t, y1 - 1, p);
                    c.drawRect(x1 - t, y0 + 1, x1 - 1, y1 - 1, p);
                    p.setColor(0xFFBDB29A);
                    c.drawRect(x0 + t, y0 + t, x1 - t, y1 - t, p);
                    courses(c, p, x0 + 1, y0 + 1, x1 - 1, y1 - 1, City.darken(roof, 0.8f), 2.5f, along);
                }
                return true;
            }
            case RealCities.L_CLOCK: {
                p.setColor(0xFFB8946C);
                c.drawRect(x0, y0, x1, y1, p);
                hip(c, p, x0 + 1, y0 + 1, x1 - 1, y1 - 1, roof);
                // The clock tower at one end.
                float s = m * 0.3f, tx = along ? x0 + bw * 0.15f : mx, ty = along ? my : y0 + bh * 0.15f;
                p.setColor(0xFFC8A47C);
                c.drawRect(tx - s / 2, ty - s / 2, tx + s / 2, ty + s / 2, p);
                quad(c, p, 0xFF6A3A28, tx - s / 2, ty - s / 2, tx + s / 2, ty - s / 2, tx, ty, tx, ty);
                quad(c, p, 0xFF4A2A1C, tx - s / 2, ty + s / 2, tx + s / 2, ty + s / 2, tx, ty, tx, ty);
                if (own.name != null && own.name.startsWith("Union")) city.label("GO BY TRAIN", tx, ty + s, 3.2f, 0xFFF2D070);
                return true;
            }
            case RealCities.L_THEATRE: {
                p.setColor(0xFFB03A2A);
                c.drawRect(x0, y0, x1, y1, p);
                // A green pagoda roof in tiers over the red front.
                for (int k = 0; k < 3; k++) {
                    float in = k * m * 0.14f;
                    p.setColor(k % 2 == 0 ? roof : City.lighten(roof, 0.2f));
                    c.drawRect(x0 + in, y0 + in, x1 - in, y1 - in, p);
                }
                p.setColor(0xFFD8B040);
                c.drawCircle(mx, my, m * 0.06f, p);
                return true;
            }
            case RealCities.L_CAMPUS: {
                // White travertine pavilions round gardens and a round hall.
                p.setColor(0xFFDAD4C6);
                c.drawRect(x0, y0, x1, y1, p);
                p.setColor(0xFFF0ECE2);
                c.drawRect(x0 + 1, y0 + 1, x0 + bw * 0.4f, y1 - 1, p);
                c.drawRect(x0 + bw * 0.6f, y0 + 1, x1 - 1, y0 + bh * 0.45f, p);
                p.setColor(0xFF6A9A5A);
                c.drawRect(x0 + bw * 0.6f, y0 + bh * 0.55f, x1 - 1, y1 - 1, p);
                p.setColor(0xFFF6F2E8);
                c.drawCircle(x0 + bw * 0.5f, my, m * 0.22f, p);
                return true;
            }
            case RealCities.L_WEDGE: {
                // A triangle on its corner of the block.
                path.reset();
                path.moveTo(x0, y0);
                path.lineTo(x1, y0);
                path.lineTo(mx, y1);
                path.close();
                p.setColor(roof);
                c.drawPath(path, p);
                p.setColor(City.lighten(roof, 0.2f));
                c.drawCircle(mx, y0 + bh * 0.3f, m * 0.08f, p);
                return true;
            }
            case RealCities.L_SLAB: {
                glassGrid(c, p, x0, y0, x1, y1, roof, 3);
                // The stepped slab, its long roof terraced at the ends.
                p.setColor(City.lighten(roof, 0.12f));
                c.drawRect(x0 + bw * 0.15f, y0 + bh * 0.2f, x1 - bw * 0.15f, y1 - bh * 0.2f, p);
                p.setColor(City.lighten(roof, 0.22f));
                c.drawRect(x0 + bw * 0.3f, y0 + bh * 0.3f, x1 - bw * 0.3f, y1 - bh * 0.3f, p);
                return true;
            }
            case RealCities.L_SPIRAL: {
                p.setColor(0xFFDAD6CE);
                c.drawRect(x0, y0, x1, y1, p);
                // The white spiral widening as it rises, the glass dome in the middle.
                for (int k = 0; k < 4; k++) {
                    p.setColor(k % 2 == 0 ? 0xFFF6F4EE : 0xFFE2DED6);
                    c.drawCircle(mx, my, m * (0.48f - k * 0.08f), p);
                }
                p.setColor(0xFF9AB6C8);
                c.drawCircle(mx, my, m * 0.14f, p);
                return true;
            }
            case RealCities.L_BLOB: {
                // Sheet metal in waves of colour.
                int[] cols = {0xFFB0507A, 0xFF7A60B0, 0xFFD8A030, 0xFF4E8AC0, 0xFFC0C4C8};
                for (int k = 0; k < 5; k++) {
                    oval.set(x0 + (k % 3) * bw * 0.25f, y0 + (k / 3) * bh * 0.4f, x0 + (k % 3) * bw * 0.25f + bw * 0.5f, y0 + (k / 3) * bh * 0.4f + bh * 0.6f);
                    p.setColor(cols[k]);
                    c.drawOval(oval, p);
                }
                return true;
            }
            case RealCities.L_CURVES: {
                // Sweeping stainless-steel sails.
                p.setColor(0xFFA8AEB4);
                c.drawRect(x0, y0, x1, y1, p);
                for (int k = 0; k < 5; k++) {
                    path.reset();
                    float sx = x0 + bw * (0.1f + k * 0.17f);
                    path.moveTo(sx, y1 - 1);
                    path.quadTo(sx + bw * 0.3f, y0 + bh * (0.1f + (k % 2) * 0.2f), sx + bw * 0.22f, y1 - bh * 0.3f);
                    path.close();
                    p.setColor(k % 2 == 0 ? 0xFFE6EAEE : 0xFFC4CAD0);
                    c.drawPath(path, p);
                }
                return true;
            }
            case RealCities.L_MANSION:
                frenchHouse(c, p, x0, y0, x1, y1, roof, new Random(b[5]));
                return true;
            case RealCities.L_SIGN: {
                p.setColor(roof);
                c.drawRect(x0, y0, x1, y1, p);
                p.setColor(0xFFB86A3A);
                c.drawRect(x0, y1 - 5, x1, y1 - 1, p);
                city.label("POWELL'S", mx, my + 2, Math.min(8f, bw / 7f), 0xFFF2E8D8);
                return true;
            }
            default:
                return false;
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
        if (v.sign != null && bw >= 18 && bh >= 12) sign(city, c, p, v.sign, x0, y0, x1, y1, acc);
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
        hvac(c, p, x0, y0, x1, y1, r, n);
    }

    /** The name of the place on a board on the roof (so it can be read from above). */
    private static void sign(City city, Canvas c, Paint p, String text, float x0, float y0, float x1, float y1, int acc) {
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
        // (The lettering is drawn over the map at screen resolution, so it stays sharp close up.)
        city.label(text, cx, cy + size * 0.12f, size, lum > 1500 ? 0xFF1E1E20 : 0xFFF8F8F4);
        p.setFakeBoldText(false);
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
