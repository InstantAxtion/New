package com.instantaxtion.zombiesandbox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/** Renders the world, runs the game loop and handles all touch input and on-screen buttons. */
final class GameView extends View {
    private static final int TOOL_PAN = 0, TOOL_BOMB = 7, TOOL_ERASE = 8;
    // Tool index -> entity type spawned (or -1).
    private static final int[] TOOL_TYPE = {-1, Entity.CIVILIAN, Entity.COP, Entity.SOLDIER, Entity.ZOMBIE,
            Entity.RUNNER, Entity.BRUTE, -1, -1};
    private static final String[] TOOL_NAMES = {"Move", "Civilian", "Cop", "Military", "Zombie", "Runner",
            "Brute", "Bomb", "Erase"};
    private static final int BTN_PAUSE = 0, BTN_SPEED = 1, BTN_BRUSH = 2, BTN_CLEAR = 3, BTN_NEW = 4;
    private static final int[] SPEEDS = {1, 2, 4};
    private static final int[] BRUSHES = {1, 5, 10};
    private static final int[] TYPE_COLORS = {0xFFF0AD4E, 0xFF4F7BE0, 0xFF8FA05A, 0xFF7CC24E, 0xFFD6E05A, 0xFFB36BD6};

    private World world;
    private final Random rnd = new Random();
    private final float dp;

    private float camX, camY, scale = 2f;
    private boolean running, simPaused;
    private long lastFrame;
    private int speedIdx, brushIdx;
    private int tool = Entity.ZOMBIE + 1;
    private Entity follow;
    private float hintTime = 14f;

    // Layout.
    private final RectF[] toolRects = new RectF[TOOL_NAMES.length];
    private final RectF[] topRects = new RectF[5];
    private final RectF statsRect = new RectF();
    private float barTop;

    // Input.
    private static final int MODE_NONE = 0, MODE_UI = 1, MODE_WORLD = 2, MODE_GESTURE = 3;
    private int mode;
    private float downX, downY, lastX, lastY, lastSpawnX, lastSpawnY;
    private boolean dragged;
    private float pinchDist, pinchMidX, pinchMidY;

    // Paints and scratch objects.
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint();
    private final RectF oval = new RectF();
    private final Entity[] icons = new Entity[Entity.TYPE_COUNT];

    GameView(Context context) {
        super(context);
        dp = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        for (int i = 0; i < 9; i++) toolRects[i] = new RectF();
        for (int i = 0; i < 5; i++) topRects[i] = new RectF();
        buildIcons();
        newCity();
    }

    private void buildIcons() {
        int[] bodies = {0xFFD9534F, 0xFF23408E, 0xFF55623A, 0xFF4E5A3E, 0xFF6B3A36, 0xFF4D3F4F};
        int[] heads = {0xFF4A2E1A, 0xFF141C38, 0xFF3C4628, 0xFF7C9A5E, 0xFF9DAA70, 0xFF6F8D55};
        float[] radii = {3.6f, 3.8f, 4f, 3.8f, 3.5f, 6.5f};
        for (int t = 0; t < Entity.TYPE_COUNT; t++) {
            Entity e = new Entity();
            e.type = t;
            e.radius = radii[t];
            e.body = bodies[t];
            e.head = heads[t];
            e.skin = t >= Entity.ZOMBIE ? heads[t] : 0xFFE0AC69;
            e.angle = (float) (-Math.PI / 2);
            e.hp = e.maxHp = 1;
            e.phase = 1.2f;
            icons[t] = e;
        }
    }

    private void newCity() {
        world = new World(System.nanoTime());
        world.populate(140, 8);
        follow = null;
        if (getWidth() > 0) centerCamera();
    }

    private void centerCamera() {
        scale = Math.max(getWidth(), getHeight()) / (34f * City.T);
        camX = world.city.worldW() / 2 - getWidth() / scale / 2;
        camY = world.city.worldH() / 2 - getHeight() / scale / 2;
    }

    void resume() {
        running = true;
        lastFrame = System.nanoTime();
        postInvalidateOnAnimation();
    }

    void pause() {
        running = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float barH = 70 * dp;
        barTop = h - barH;
        int n = toolRects.length;
        float gap = 5 * dp;
        float bw = Math.min(96 * dp, (w - 16 * dp - gap * (n - 1)) / n);
        float total = bw * n + gap * (n - 1);
        float x = (w - total) / 2;
        for (int i = 0; i < n; i++) {
            toolRects[i].set(x, barTop + 6 * dp, x + bw, h - 6 * dp);
            x += bw + gap;
        }
        float tw = Math.min(88 * dp, (w * 0.55f - 4 * 6 * dp) / 5), th = 38 * dp;
        float tx = w - 10 * dp - tw * 5 - 6 * dp * 4;
        for (int i = 0; i < 5; i++) {
            topRects[i].set(tx, 10 * dp, tx + tw, 10 * dp + th);
            tx += tw + 6 * dp;
        }
        if (oldw == 0) centerCamera();
        else clampCamera();
    }

    // ------------------------------------------------------------------ loop

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (!simPaused) {
            float total = dt * SPEEDS[speedIdx];
            int steps = Math.max(1, (int) Math.ceil(total / 0.034f));
            float step = total / steps;
            for (int i = 0; i < steps; i++) world.update(step);
        }
        if (hintTime > 0) hintTime -= dt;
        if (follow != null) {
            if (follow.dead) follow = null;
            else {
                float tx = follow.x - getWidth() / scale / 2, ty = follow.y - (barTop / 2) / scale;
                float k = Math.min(1, dt * 6);
                camX += (tx - camX) * k;
                camY += (ty - camY) * k;
            }
        }
        drawWorld(c);
        drawUi(c);
        if (running) postInvalidateOnAnimation();
    }

    // ------------------------------------------------------------------ world rendering

    private void drawWorld(Canvas c) {
        c.drawColor(0xFF1B1C1F);
        c.save();
        if (world.shake > 0) {
            float s = world.shake * 5 * dp;
            c.translate((rnd.nextFloat() - 0.5f) * s, (rnd.nextFloat() - 0.5f) * s);
        }
        c.scale(scale, scale);
        c.translate(-camX, -camY);
        bmpPaint.setFilterBitmap(scale < 1.5f);
        c.drawBitmap(world.city.bitmap, 0, 0, bmpPaint);

        float vx0 = camX - 20, vy0 = camY - 20;
        float vx1 = camX + getWidth() / scale + 20, vy1 = camY + getHeight() / scale + 20;
        boolean detailed = scale > 1.1f;

        for (int i = 0; i < world.dcount; i++) {
            float x = world.dx[i], y = world.dy[i], r = world.dr[i];
            if (r <= 0 || x < vx0 || x > vx1 || y < vy0 || y > vy1) continue;
            fill.setColor(world.dcol[i]);
            c.drawCircle(x, y, r, fill);
        }

        for (int i = 0, n = world.corpses.size(); i < n; i++) {
            World.Corpse k = world.corpses.get(i);
            if (k.x < vx0 || k.x > vx1 || k.y < vy0 || k.y > vy1) continue;
            drawCorpse(c, k);
        }

        for (int i = 0, n = world.entities.size(); i < n; i++) {
            Entity e = world.entities.get(i);
            if (e.x < vx0 || e.x > vx1 || e.y < vy0 || e.y > vy1) continue;
            if (detailed) drawEntity(c, e, true);
            else {
                fill.setColor(e.isZombie() ? 0xFF6FBF3F : e.body);
                c.drawCircle(e.x, e.y, e.radius * 1.2f, fill);
            }
        }
        if (follow != null) {
            stroke.setColor(0xCCFFFFFF);
            stroke.setStrokeWidth(1f);
            c.drawCircle(follow.x, follow.y, follow.radius + 4 + (float) Math.sin(world.time * 6), stroke);
        }

        for (int i = 0, n = world.grenades.size(); i < n; i++) {
            World.Grenade g = world.grenades.get(i);
            float t = g.t / g.dur;
            float x = g.sx + (g.tx - g.sx) * t, y = g.sy + (g.ty - g.sy) * t;
            float hgt = (float) Math.sin(t * Math.PI) * 22;
            fill.setColor(0x55000000);
            c.drawCircle(x, y, 1.6f, fill);
            fill.setColor(0xFF2F3A1E);
            c.drawCircle(x, y - hgt, 1.9f, fill);
        }

        for (int i = 0; i < World.MAXP; i++) {
            float life = world.plife[i];
            if (life <= 0) continue;
            float x = world.px[i], y = world.py[i];
            if (x < vx0 || x > vx1 || y < vy0 || y > vy1) continue;
            float f = life / world.pmax[i];
            int col = world.pcol[i];
            float size = world.psize[i];
            switch (world.ptype[i]) {
                case World.P_SMOKE:
                    fill.setColor(alpha(col, 0.4f * f));
                    break;
                case World.P_FIRE:
                    fill.setColor(alpha(col, f));
                    size *= 0.4f + f * 0.6f;
                    break;
                case World.P_FLASH:
                    fill.setColor(alpha(col, 0.9f));
                    break;
                default:
                    fill.setColor(alpha(col, Math.min(1, f * 2)));
                    break;
            }
            c.drawCircle(x, y, size, fill);
        }

        stroke.setStrokeWidth(0.9f);
        for (int i = 0; i < World.MAXT; i++) {
            float life = world.tlife[i];
            if (life <= 0) continue;
            stroke.setColor(alpha(0xFFFFE08A, life / 0.08f));
            c.drawLine(world.tx0[i], world.ty0[i], world.tx1[i], world.ty1[i], stroke);
        }

        for (int i = 0, n = world.explosions.size(); i < n; i++) {
            World.Explosion ex = world.explosions.get(i);
            float t = 1 - ex.life / ex.max;
            float r = ex.r * (0.3f + 0.9f * t);
            fill.setColor(alpha(0xFFFF8A20, (1 - t) * 0.8f));
            c.drawCircle(ex.x, ex.y, r, fill);
            fill.setColor(alpha(0xFFFFF2B0, (1 - t)));
            c.drawCircle(ex.x, ex.y, r * 0.5f, fill);
            stroke.setStrokeWidth(2f);
            stroke.setColor(alpha(0xFFFFFFFF, (1 - t) * 0.6f));
            c.drawCircle(ex.x, ex.y, ex.r * (0.5f + t), stroke);
        }
        c.restore();
    }

    private static int alpha(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        int na = (int) (base * Math.max(0, Math.min(1, a)));
        return (na << 24) | (color & 0xFFFFFF);
    }

    private void drawEntity(Canvas c, Entity e, boolean healthBar) {
        float r = e.radius;
        c.save();
        c.translate(e.x, e.y);
        c.rotate((float) Math.toDegrees(e.angle));

        fill.setColor(0x44000000);
        oval.set(-r * 0.7f + 0.9f, -r + 0.9f, r * 0.7f + 0.9f, r + 0.9f);
        c.drawOval(oval, fill);

        if (e.isZombie()) {
            float sw = (float) Math.sin(e.phase) * r * 0.2f;
            stroke.setColor(e.skin);
            stroke.setStrokeWidth(r * 0.42f);
            c.drawLine(r * 0.1f, -r * 0.72f, r * 1.55f + sw, -r * 0.55f, stroke);
            c.drawLine(r * 0.1f, r * 0.72f, r * 1.55f - sw, r * 0.55f, stroke);
        } else if (e.isArmed()) {
            boolean soldier = e.type == Entity.SOLDIER;
            stroke.setColor(e.skin);
            stroke.setStrokeWidth(r * 0.36f);
            c.drawLine(0, -r * 0.8f, r * 1.05f, r * 0.15f, stroke);
            c.drawLine(0, r * 0.8f, r * 1.3f, r * 0.35f, stroke);
            stroke.setColor(0xFF161616);
            stroke.setStrokeWidth(soldier ? r * 0.42f : r * 0.32f);
            c.drawLine(r * 0.6f, r * 0.3f, soldier ? r * 2.5f : r * 2f, r * 0.3f, stroke);
        } else {
            float sw = (float) Math.sin(e.phase) * r * 0.55f;
            fill.setColor(e.skin);
            c.drawCircle(sw, -r * 0.95f, r * 0.28f, fill);
            c.drawCircle(-sw, r * 0.95f, r * 0.28f, fill);
        }

        fill.setColor(e.body);
        oval.set(-r * 0.62f, -r, r * 0.62f, r);
        c.drawOval(oval, fill);
        if (e.type == Entity.SOLDIER) {
            fill.setColor(0xFF3E4829);
            oval.set(-r * 0.8f, -r * 0.5f, -r * 0.25f, r * 0.5f);
            c.drawRoundRect(oval, r * 0.15f, r * 0.15f, fill);
        } else if (e.type == Entity.COP) {
            fill.setColor(0xFFE8C547);
            c.drawCircle(r * 0.3f, -r * 0.55f, r * 0.14f, fill);
        } else if (e.type == Entity.BRUTE) {
            fill.setColor(0xFF3A2E3C);
            oval.set(-r * 0.4f, -r * 0.85f, r * 0.3f, r * 0.85f);
            c.drawOval(oval, fill);
        }

        fill.setColor(e.head);
        c.drawCircle(r * 0.08f, 0, r * 0.56f, fill);
        if (e.type == Entity.COP) {
            fill.setColor(0xFF0B1022);
            oval.set(r * 0.35f, -r * 0.45f, r * 0.85f, r * 0.45f);
            c.drawOval(oval, fill);
        } else if (e.type == Entity.SOLDIER) {
            fill.setColor(0xFF4C5833);
            c.drawCircle(r * 0.0f, -r * 0.1f, r * 0.2f, fill);
        } else if (e.isZombie()) {
            fill.setColor(0xFF5A1414);
            c.drawCircle(-r * 0.1f, r * 0.18f, r * 0.2f, fill);
            if (e.type == Entity.RUNNER) {
                fill.setColor(0xFFFF3B2F);
                c.drawCircle(r * 0.45f, -r * 0.18f, r * 0.08f, fill);
                c.drawCircle(r * 0.45f, r * 0.18f, r * 0.08f, fill);
            }
        }
        if (e.infected) {
            fill.setColor(alpha(0xFF7CFF3A, 0.35f + 0.25f * (float) Math.sin(world.time * 5)));
            c.drawCircle(r * 0.08f, 0, r * 0.58f, fill);
        }
        if (e.hurt > 0) {
            fill.setColor(alpha(0xFFFFFFFF, e.hurt * 0.7f));
            c.drawCircle(0, 0, r * 1.05f, fill);
        }
        c.restore();

        if (healthBar && e.hp < e.maxHp && e.hp > 0) {
            float bw = Math.max(10, r * 3), bx = e.x - bw / 2, by = e.y - r - 4.5f;
            fill.setColor(0xAA000000);
            c.drawRect(bx - 0.4f, by - 0.4f, bx + bw + 0.4f, by + 1.9f, fill);
            float f = e.hp / e.maxHp;
            fill.setColor(e.isZombie() ? 0xFFB5E04A : (f > 0.5f ? 0xFF4CD964 : f > 0.25f ? 0xFFFFCC00 : 0xFFFF3B30));
            c.drawRect(bx, by, bx + bw * f, by + 1.5f, fill);
        }
    }

    private void drawCorpse(Canvas c, World.Corpse k) {
        float r = k.radius;
        c.save();
        c.translate(k.x, k.y);
        float a = k.angle;
        if (k.rise > 0 && k.rise < 1.8f) a += (float) Math.sin(world.time * 30) * 0.12f;
        c.rotate((float) Math.toDegrees(a));
        fill.setColor(0x886E0A0A);
        c.drawCircle(r * 0.2f, 0, r * 1.5f, fill);
        fill.setColor(City.darken(k.body, 0.75f));
        oval.set(-r * 1.2f, -r * 0.62f, r * 0.95f, r * 0.62f);
        c.drawOval(oval, fill);
        fill.setColor(City.darken(k.head, 0.85f));
        c.drawCircle(r * 1.3f, 0, r * 0.5f, fill);
        if (k.rise > 0) {
            fill.setColor(alpha(0xFF7CFF3A, 0.25f + 0.2f * (float) Math.sin(world.time * 8)));
            c.drawCircle(0, 0, r * 1.3f, fill);
        }
        c.restore();
    }

    // ------------------------------------------------------------------ UI rendering

    private void drawUi(Canvas c) {
        int w = getWidth(), h = getHeight();

        // Stats panel.
        float pad = 10 * dp, lh = 17 * dp;
        statsRect.set(pad, pad, pad + 170 * dp, pad + lh * 8 + 12 * dp);
        fill.setColor(0xB0101114);
        c.drawRoundRect(statsRect, 10 * dp, 10 * dp, fill);
        text.setTextSize(12.5f * dp);
        text.setTextAlign(Paint.Align.LEFT);
        float y = pad + 6 * dp + lh * 0.8f;
        String[] labels = {"Civilians", "Cops", "Military", "Zombies", "Runners", "Brutes"};
        for (int t = 0; t < Entity.TYPE_COUNT; t++) {
            fill.setColor(TYPE_COLORS[t]);
            c.drawCircle(pad + 14 * dp, y - 4.5f * dp, 4.5f * dp, fill);
            text.setColor(0xFFE6E6E6);
            c.drawText(labels[t], pad + 26 * dp, y, text);
            text.setTextAlign(Paint.Align.RIGHT);
            c.drawText(String.valueOf(world.counts[t]), statsRect.right - 12 * dp, y, text);
            text.setTextAlign(Paint.Align.LEFT);
            y += lh;
        }
        text.setColor(0xFFA0A4AA);
        text.setTextSize(11.5f * dp);
        c.drawText("Turned " + world.turned + "   Killed " + world.zombiesKilled, pad + 12 * dp, y, text);
        y += lh;
        int secs = (int) world.time;
        c.drawText(String.format("Time %d:%02d   Total %d", secs / 60, secs % 60, world.entities.size()),
                pad + 12 * dp, y, text);

        // Top buttons.
        String[] top = {simPaused ? "Play" : "Pause", "Speed " + SPEEDS[speedIdx] + "x",
                "Brush " + BRUSHES[brushIdx], "Clear", "New City"};
        text.setTextSize(13 * dp);
        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 5; i++) {
            RectF r = topRects[i];
            boolean hot = i == BTN_PAUSE && simPaused;
            fill.setColor(hot ? 0xE0D9534F : 0xC0202227);
            c.drawRoundRect(r, 9 * dp, 9 * dp, fill);
            stroke.setColor(0x40FFFFFF);
            stroke.setStrokeWidth(1 * dp);
            c.drawRoundRect(r, 9 * dp, 9 * dp, stroke);
            text.setColor(0xFFF2F2F2);
            c.drawText(top[i], r.centerX(), r.centerY() + 4.5f * dp, text);
        }

        // Bottom tool bar.
        fill.setColor(0xD80E0F12);
        c.drawRect(0, barTop, w, h, fill);
        text.setTextSize(11.5f * dp);
        for (int i = 0; i < toolRects.length; i++) {
            RectF r = toolRects[i];
            boolean sel = i == tool;
            fill.setColor(sel ? 0xFF3A6EA5 : 0xFF23262C);
            c.drawRoundRect(r, 10 * dp, 10 * dp, fill);
            if (sel) {
                stroke.setColor(0xFFA8D0FF);
                stroke.setStrokeWidth(2 * dp);
                c.drawRoundRect(r, 10 * dp, 10 * dp, stroke);
            }
            float cx = r.centerX(), cy = r.top + r.height() * 0.4f;
            drawToolIcon(c, i, cx, cy, r.height() * 0.26f);
            text.setColor(sel ? 0xFFFFFFFF : 0xFFC8CCD2);
            c.drawText(TOOL_NAMES[i], cx, r.bottom - 7 * dp, text);
        }

        // Messages.
        text.setTextAlign(Paint.Align.CENTER);
        if (world.messageTime > 0 && world.message != null) {
            float a = Math.min(1, world.messageTime);
            text.setTextSize(28 * dp);
            text.setColor(alpha(0xFF000000, a * 0.7f));
            c.drawText(world.message, w / 2f + 2 * dp, h * 0.3f + 2 * dp, text);
            text.setColor(alpha(0xFFFFE27A, a));
            c.drawText(world.message, w / 2f, h * 0.3f, text);
        }
        float infoY = barTop - 12 * dp;
        text.setTextSize(13 * dp);
        if (follow != null) {
            String s = "Following " + Entity.NAMES[follow.type] + "  -  HP " + Math.max(0, (int) follow.hp) + "/"
                    + (int) follow.maxHp + (follow.infected ? "  -  INFECTED" : "") + "   (drag to stop)";
            drawBanner(c, s, w / 2f, infoY);
        } else if (hintTime > 0) {
            drawBanner(c, "Pick a unit below and tap the city to spawn it.  Pinch to zoom, two fingers to pan.",
                    w / 2f, infoY);
        } else if (simPaused) {
            drawBanner(c, "PAUSED", w / 2f, infoY);
        }
    }

    private void drawBanner(Canvas c, String s, float cx, float by) {
        float tw = text.measureText(s);
        oval.set(cx - tw / 2 - 12 * dp, by - 18 * dp, cx + tw / 2 + 12 * dp, by + 7 * dp);
        fill.setColor(0xB0000000);
        c.drawRoundRect(oval, 8 * dp, 8 * dp, fill);
        text.setColor(0xFFFFFFFF);
        c.drawText(s, cx, by, text);
    }

    private void drawToolIcon(Canvas c, int t, float cx, float cy, float size) {
        int type = TOOL_TYPE[t];
        if (type >= 0) {
            Entity e = icons[type];
            float s = size / 5.2f;
            if (type == Entity.BRUTE) s *= 0.62f;
            float forward = e.isArmed() ? 1.1f : e.isZombie() ? 0.6f : 0f;
            c.save();
            c.translate(cx, cy + e.radius * s * forward);
            c.scale(s, s);
            e.x = 0;
            e.y = 0;
            drawEntity(c, e, false);
            c.restore();
            return;
        }
        if (t == TOOL_PAN) {
            stroke.setColor(0xFFE6E6E6);
            stroke.setStrokeWidth(2.2f * dp);
            float a = size * 0.9f;
            c.drawLine(cx - a, cy, cx + a, cy, stroke);
            c.drawLine(cx, cy - a, cx, cy + a, stroke);
            float hd = size * 0.3f;
            c.drawLine(cx - a, cy, cx - a + hd, cy - hd, stroke);
            c.drawLine(cx - a, cy, cx - a + hd, cy + hd, stroke);
            c.drawLine(cx + a, cy, cx + a - hd, cy - hd, stroke);
            c.drawLine(cx + a, cy, cx + a - hd, cy + hd, stroke);
            c.drawLine(cx, cy - a, cx - hd, cy - a + hd, stroke);
            c.drawLine(cx, cy - a, cx + hd, cy - a + hd, stroke);
            c.drawLine(cx, cy + a, cx - hd, cy + a - hd, stroke);
            c.drawLine(cx, cy + a, cx + hd, cy + a - hd, stroke);
        } else if (t == TOOL_BOMB) {
            fill.setColor(0xFF15171A);
            c.drawCircle(cx - size * 0.1f, cy + size * 0.15f, size * 0.62f, fill);
            fill.setColor(0x55FFFFFF);
            c.drawCircle(cx - size * 0.3f, cy - size * 0.05f, size * 0.15f, fill);
            stroke.setColor(0xFFB08A5A);
            stroke.setStrokeWidth(2 * dp);
            c.drawLine(cx + size * 0.25f, cy - size * 0.3f, cx + size * 0.55f, cy - size * 0.7f, stroke);
            fill.setColor(0xFFFFB030);
            c.drawCircle(cx + size * 0.6f, cy - size * 0.78f, size * 0.18f, fill);
        } else if (t == TOOL_ERASE) {
            stroke.setColor(0xFFFF6B6B);
            stroke.setStrokeWidth(3 * dp);
            float a = size * 0.55f;
            c.drawLine(cx - a, cy - a, cx + a, cy + a, stroke);
            c.drawLine(cx - a, cy + a, cx + a, cy - a, stroke);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                float x = ev.getX(), y = ev.getY();
                if (hitUi(x, y)) {
                    mode = MODE_UI;
                } else {
                    mode = MODE_WORLD;
                    downX = lastX = x;
                    downY = lastY = y;
                    dragged = false;
                    worldDown(x, y);
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mode != MODE_UI && ev.getPointerCount() >= 2) {
                    mode = MODE_GESTURE;
                    follow = null;
                    startPinch(ev);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mode == MODE_GESTURE && ev.getPointerCount() >= 2) {
                    doPinch(ev);
                } else if (mode == MODE_WORLD) {
                    worldMove(ev.getX(), ev.getY());
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (mode == MODE_GESTURE && ev.getPointerCount() > 2) startPinchExcluding(ev, ev.getActionIndex());
                return true;
            case MotionEvent.ACTION_UP:
                if (mode == MODE_WORLD) worldUp(ev.getX(), ev.getY());
                mode = MODE_NONE;
                return true;
            case MotionEvent.ACTION_CANCEL:
                mode = MODE_NONE;
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private boolean hitUi(float x, float y) {
        if (y >= barTop) {
            for (int i = 0; i < toolRects.length; i++) {
                if (toolRects[i].contains(x, y)) {
                    tool = i;
                    hintTime = Math.min(hintTime, 3);
                }
            }
            return true;
        }
        for (int i = 0; i < topRects.length; i++) {
            RectF r = topRects[i];
            if (x >= r.left - 4 * dp && x <= r.right + 4 * dp && y >= r.top - 6 * dp && y <= r.bottom + 6 * dp) {
                pressTop(i);
                return true;
            }
        }
        return statsRect.contains(x, y);
    }

    private void pressTop(int i) {
        switch (i) {
            case BTN_PAUSE:
                simPaused = !simPaused;
                break;
            case BTN_SPEED:
                speedIdx = (speedIdx + 1) % SPEEDS.length;
                break;
            case BTN_BRUSH:
                brushIdx = (brushIdx + 1) % BRUSHES.length;
                break;
            case BTN_CLEAR:
                world.clearAll();
                follow = null;
                break;
            case BTN_NEW:
                newCity();
                break;
        }
    }

    private float worldX(float sx) {
        return sx / scale + camX;
    }

    private float worldY(float sy) {
        return sy / scale + camY;
    }

    private void worldDown(float x, float y) {
        float wx = worldX(x), wy = worldY(y);
        if (tool == TOOL_PAN) return;
        follow = null;
        if (tool == TOOL_BOMB) {
            world.explode(wx, wy, 70, 400);
        } else if (tool == TOOL_ERASE) {
            world.erase(wx, wy, 22);
        } else {
            spawnBrush(wx, wy);
        }
        lastSpawnX = wx;
        lastSpawnY = wy;
    }

    private void worldMove(float x, float y) {
        if (Math.abs(x - downX) > 8 * dp || Math.abs(y - downY) > 8 * dp) dragged = true;
        float wx = worldX(x), wy = worldY(y);
        if (tool == TOOL_PAN) {
            if (dragged) {
                follow = null;
                camX -= (x - lastX) / scale;
                camY -= (y - lastY) / scale;
                clampCamera();
            }
        } else if (tool == TOOL_ERASE) {
            world.erase(wx, wy, 22);
        } else if (tool != TOOL_BOMB) {
            float ddx = wx - lastSpawnX, ddy = wy - lastSpawnY;
            float spacing = BRUSHES[brushIdx] > 1 ? 22 : 12;
            if (ddx * ddx + ddy * ddy > spacing * spacing) {
                spawnBrush(wx, wy);
                lastSpawnX = wx;
                lastSpawnY = wy;
            }
        }
        lastX = x;
        lastY = y;
    }

    private void worldUp(float x, float y) {
        if (tool != TOOL_PAN || dragged) return;
        float wx = worldX(x), wy = worldY(y);
        float best = Math.max(12, 28 * dp / scale);
        best *= best;
        Entity pick = null;
        for (int i = 0, n = world.entities.size(); i < n; i++) {
            Entity e = world.entities.get(i);
            float ddx = e.x - wx, ddy = e.y - wy, d2 = ddx * ddx + ddy * ddy;
            if (d2 < best) {
                best = d2;
                pick = e;
            }
        }
        follow = pick;
    }

    private void spawnBrush(float wx, float wy) {
        int type = TOOL_TYPE[tool];
        int n = BRUSHES[brushIdx];
        for (int i = 0; i < n; i++) {
            float ox = 0, oy = 0;
            if (n > 1) {
                double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * 20;
                ox = (float) (Math.cos(a) * r);
                oy = (float) (Math.sin(a) * r);
            }
            world.spawn(type, wx + ox, wy + oy);
        }
    }

    private void startPinch(MotionEvent ev) {
        startPinchExcluding(ev, -1);
    }

    private void startPinchExcluding(MotionEvent ev, int skip) {
        int a = -1, b = -1;
        for (int i = 0; i < ev.getPointerCount(); i++) {
            if (i == skip) continue;
            if (a < 0) a = i;
            else if (b < 0) b = i;
        }
        if (a < 0 || b < 0) return;
        float x0 = ev.getX(a), y0 = ev.getY(a), x1 = ev.getX(b), y1 = ev.getY(b);
        pinchDist = (float) Math.hypot(x1 - x0, y1 - y0);
        pinchMidX = (x0 + x1) / 2;
        pinchMidY = (y0 + y1) / 2;
    }

    private void doPinch(MotionEvent ev) {
        float x0 = ev.getX(0), y0 = ev.getY(0), x1 = ev.getX(1), y1 = ev.getY(1);
        float d = (float) Math.hypot(x1 - x0, y1 - y0);
        float mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
        float wx = worldX(pinchMidX), wy = worldY(pinchMidY);
        if (pinchDist > 10 && d > 10) {
            float minScale = Math.min(getWidth() / world.city.worldW(), getHeight() / world.city.worldH()) * 0.8f;
            scale = Math.max(minScale, Math.min(10f, scale * d / pinchDist));
        }
        camX = wx - mx / scale;
        camY = wy - my / scale;
        clampCamera();
        pinchDist = d;
        pinchMidX = mx;
        pinchMidY = my;
    }

    private void clampCamera() {
        float vw = getWidth() / scale, vh = getHeight() / scale;
        camX = Math.max(-vw / 2, Math.min(world.city.worldW() - vw / 2, camX));
        camY = Math.max(-vh / 2, Math.min(world.city.worldH() - vh / 2, camY));
    }
}
