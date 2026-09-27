package com.instantaxtion.zombiesandbox;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/** Renders the world, runs the game loop and handles all touch input and on-screen buttons. */
final class GameView extends View implements Menu.Host {
    private static final int TOOL_PAN = 0, TOOL_ZONE = 7, TOOL_BOMB = 8, TOOL_ERASE = 9;
    // Tool index -> entity type spawned (or -1).
    private static final int[] TOOL_TYPE = {-1, Entity.CIVILIAN, Entity.COP, Entity.SOLDIER, Entity.ZOMBIE,
            Entity.RUNNER, Entity.BRUTE, -1, -1, -1};
    private static final String[] TOOL_NAMES = {"Move", "Civilian", "Cop", "Military", "Zombie", "Runner",
            "Brute", "Safe Zone", "Bomb", "Erase"};
    private static final int BTN_PAUSE = 0, BTN_SPEED = 1, BTN_BRUSH = 2, BTN_CLEAR = 3, BTN_MENU = 4;
    private static final int[] SPEEDS = {1, 2, 4};
    private static final int[] BRUSHES = {1, 5, 10};
    private static final int[] TYPE_COLORS = {0xFFF0AD4E, 0xFF4F7BE0, 0xFF8FA05A, 0xFF7CC24E, 0xFFD6E05A, 0xFFB36BD6};

    private World world;
    private final Random rnd = new Random();
    private final float dp;
    private final Settings settings;
    private final Sound sound;
    private final Menu menu;
    private boolean hasGame;
    private float menuTime, savedCamX, savedCamY, savedScale;
    private float fps, fpsTimer;
    private int lastMessageCount;
    private final RectF feedRect = new RectF();
    private final RectF[] feedLines = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final Dispatch.Message[] feedMsgs = new Dispatch.Message[4];
    private int feedCount;
    private int fpsFrames;

    private float camX, camY, scale = 2f;
    private boolean running, simPaused;
    private long lastFrame;
    private int speedIdx, brushIdx;
    private int tool = TOOL_PAN;
    private Entity follow;
    private float hintTime = 14f;

    // Layout.
    private final RectF[] toolRects = new RectF[TOOL_NAMES.length];
    private final RectF[] topRects = new RectF[5];
    private final RectF statsRect = new RectF();
    private float barTop;
    private boolean portrait;

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
    private final Matrix wallMatrix = new Matrix();
    private final float[] wallSrc = new float[8], wallDst = new float[8];
    private final Rect roofSrc = new Rect();
    private final RectF roofDst = new RectF();
    private City.Building[] visible = new City.Building[64];
    private float[] visibleKey = new float[64];

    GameView(Context context) {
        super(context);
        dp = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        for (int i = 0; i < toolRects.length; i++) toolRects[i] = new RectF();
        for (int i = 0; i < 5; i++) topRects[i] = new RectF();
        buildIcons();
        settings = new Settings(context);
        sound = new Sound(context);
        menu = new Menu(this, settings, dp);
        // The main menu shows a live demo city in the background.
        CityConfig demo = new CityConfig();
        demo.applyPreset(rnd.nextInt(CityConfig.CUSTOM));
        demo.v[9] = 3;
        demo.v[10] = 1;
        demo.v[11] = 1;
        demo.v[12] = 2;
        loadWorld(demo);
        applySettings();
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

    private void loadWorld(CityConfig cfg) {
        world = new World(cfg);
        world.maxEntities = settings.maxPopulation();
        world.gore = settings.gore();
        world.populate(cfg);
        follow = null;
        if (getWidth() > 0) centerCamera();
    }

    private void applySettings() {
        world.maxEntities = settings.maxPopulation();
        world.gore = settings.gore();
        sound.setVolumes(settings.music(), settings.sfx());
    }

    // ------------------------------------------------------------------ Menu.Host

    @Override
    public boolean hasGame() {
        return hasGame;
    }

    @Override
    public void continueGame() {
        if (menu.screen == Menu.MAIN) {
            camX = savedCamX;
            camY = savedCamY;
            scale = savedScale;
        }
        menu.screen = Menu.NONE;
    }

    @Override
    public void startGame(CityConfig cfg) {
        loadWorld(cfg);
        applySettings();
        hasGame = true;
        simPaused = false;
        speedIdx = 0;
        tool = TOOL_PAN;
        hintTime = 14f;
        lastMessageCount = 0;
        menu.screen = Menu.NONE;
    }

    @Override
    public void toMainMenu() {
        savedCamX = camX;
        savedCamY = camY;
        savedScale = scale;
        menu.open(Menu.MAIN);
    }

    @Override
    public void quit() {
        if (getContext() instanceof Activity) ((Activity) getContext()).finish();
    }

    @Override
    public void settingsChanged() {
        applySettings();
    }

    @Override
    public void click() {
        sound.play(Sfx.CLICK, 0.8f, 0);
    }

    /** System back button. Returns false when the app should close. */
    boolean onBack() {
        boolean handled = menu.back();
        if (handled) click();
        return handled;
    }

    void release() {
        sound.release();
    }

    private void centerCamera() {
        scale = Math.max(getWidth(), getHeight()) / (34f * City.T);
        camX = world.city.worldW() / 2 - getWidth() / scale / 2;
        camY = world.city.worldH() / 2 - getHeight() / scale / 2;
    }

    void resume() {
        sound.resume();
        running = true;
        lastFrame = System.nanoTime();
        postInvalidateOnAnimation();
    }

    void pause() {
        sound.pause();
        running = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        portrait = h > w;
        int n = toolRects.length;
        int perRow = portrait ? 5 : n;
        int rows = (n + perRow - 1) / perRow;
        float gap = 5 * dp, rowH = 62 * dp;
        barTop = h - rows * rowH - 8 * dp;
        float bw = Math.min(96 * dp, (w - 16 * dp - gap * (perRow - 1)) / perRow);
        for (int r = 0; r < rows; r++) {
            int first = r * perRow, count = Math.min(perRow, n - first);
            float x = (w - (bw * count + gap * (count - 1))) / 2;
            float top = barTop + 6 * dp + r * rowH;
            for (int i = first; i < first + count; i++) {
                toolRects[i].set(x, top, x + bw, top + rowH - 6 * dp);
                x += bw + gap;
            }
        }
        float th = 38 * dp, tgap = 6 * dp, tw, tx;
        if (portrait) {
            tw = (w - 20 * dp - tgap * 4) / 5;
            tx = 10 * dp;
        } else {
            tw = Math.min(88 * dp, (w * 0.55f - tgap * 4) / 5);
            tx = w - 10 * dp - tw * 5 - tgap * 4;
        }
        for (int i = 0; i < 5; i++) {
            topRects[i].set(tx, 10 * dp, tx + tw, 10 * dp + th);
            tx += tw + tgap;
        }
        float lh = 17 * dp;
        if (portrait) {
            float top = 10 * dp + th + 8 * dp;
            statsRect.set(10 * dp, top, 10 * dp + Math.min(260 * dp, w - 20 * dp), top + lh * 7 + 12 * dp);
            feedRect.set(10 * dp, statsRect.bottom + 8 * dp, w - 10 * dp, statsRect.bottom + 8 * dp + 4 * 24 * dp);
        } else {
            statsRect.set(10 * dp, 10 * dp, 190 * dp, 10 * dp + lh * 10 + 12 * dp);
            feedRect.set(topRects[0].left, topRects[0].bottom + 8 * dp, w - 10 * dp, topRects[0].bottom + 8 * dp + 4 * 24 * dp);
        }
        if (oldw == 0) {
            centerCamera();
        } else {
            // Keep the same spot in the middle of the screen when the phone rotates.
            camX += (oldw - w) / 2f / scale;
            camY += (oldh - h) / 2f / scale;
            clampCamera();
        }
    }

    // ------------------------------------------------------------------ loop

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float rawDt = (now - lastFrame) / 1e9f;
        float dt = Math.min(0.05f, rawDt);
        lastFrame = now;
        fpsFrames++;
        fpsTimer += rawDt;
        if (fpsTimer >= 0.5f) {
            fps = fpsFrames / fpsTimer;
            fpsFrames = 0;
            fpsTimer = 0;
        }

        boolean inGame = !menu.isOpen();
        boolean simulate = inGame ? !simPaused : menu.liveBackground();
        if (simulate) {
            float total = dt * (inGame ? SPEEDS[speedIdx] : 1);
            int steps = Math.max(1, (int) Math.ceil(total / 0.034f));
            float step = total / steps;
            for (int i = 0; i < steps; i++) world.update(step);
        }
        if (inGame && hintTime > 0) hintTime -= dt;
        if (menu.liveBackground()) driftCamera(dt);
        else if (follow != null) {
            if (follow.dead) follow = null;
            else {
                float tx = follow.x - getWidth() / scale / 2, ty = follow.y - (barTop / 2) / scale;
                float k = Math.min(1, dt * 6);
                camX += (tx - camX) * k;
                camY += (ty - camY) * k;
            }
        }
        sound.playTrack(inGame || menu.screen == Menu.PAUSE ? Synth.TRACK_GAME : Synth.TRACK_MENU);
        playSounds(inGame ? 1f : 0.35f);
        if (world.dispatch.messageCount != lastMessageCount) {
            if (inGame && settings.radio()) sound.play(Sfx.RADIO, 0.5f, 0);
            lastMessageCount = world.dispatch.messageCount;
        }

        drawWorld(c);
        if (menu.isOpen()) menu.draw(c, getWidth(), getHeight(), dt);
        else drawUi(c);
        if (settings.showFps()) {
            text.setTextSize(12 * dp);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(0xFFFFFF66);
            c.drawText((int) (fps + 0.5f) + " FPS", getWidth() - 8 * dp,
                    menu.isOpen() ? getHeight() - 30 * dp : barTop - 8 * dp, text);
        }
        if (running) postInvalidateOnAnimation();
    }

    /** Slowly glides the camera over the city behind the main menu. */
    private void driftCamera(float dt) {
        menuTime += dt;
        scale = Math.max(getWidth(), getHeight()) / (30f * City.T);
        float ww = world.city.worldW(), wh = world.city.worldH();
        float tx = ww / 2 + ww * 0.3f * (float) Math.sin(menuTime * 0.021f);
        float ty = wh / 2 + wh * 0.3f * (float) Math.sin(menuTime * 0.029f + 1);
        camX = tx - getWidth() / scale / 2;
        camY = ty - getHeight() / scale / 2;
    }

    /** Plays the simulation's sound events that happened on (or near) the screen. */
    private void playSounds(float master) {
        float vw = getWidth() / scale, vh = getHeight() / scale;
        float cx = camX + vw / 2, cy = camY + vh / 2, reach = Math.max(vw, vh) * 0.75f;
        for (int i = 0; i < world.evCount; i++) {
            float ddx = world.evX[i] - cx, ddy = world.evY[i] - cy;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            if (d > reach) continue;
            float vol = master * (1 - 0.7f * d / reach);
            int type = world.evType[i];
            if (type == Sfx.GROAN || type == Sfx.GROAN_DEEP || type == Sfx.SCREAM) vol *= 0.6f;
            sound.play(type, vol, Math.max(-1, Math.min(1, ddx / (vw / 2))) * 0.8f);
        }
        world.evCount = 0;
    }

    // ------------------------------------------------------------------ world rendering

    private void drawWorld(Canvas c) {
        c.drawColor(0xFF1B1C1F);
        c.save();
        int timeOfDay = world.city.cfg.time();
        boolean night = timeOfDay == CityConfig.TIME_NIGHT;
        if (world.shake > 0 && settings.shake()) {
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

        for (int i = 0, n = world.dispatch.zones.size(); i < n; i++) drawZone(c, world.dispatch.zones.get(i));

        for (int i = 0, n = world.corpses.size(); i < n; i++) {
            World.Corpse k = world.corpses.get(i);
            if (k.x < vx0 || k.x > vx1 || k.y < vy0 || k.y > vy1) continue;
            drawCorpse(c, k);
        }

        for (int i = 0, n = world.entities.size(); i < n; i++) {
            Entity e = world.entities.get(i);
            if (e.x < vx0 || e.x > vx1 || e.y < vy0 || e.y > vy1) continue;
            if (detailed) drawEntity(c, e, settings.healthBars());
            else {
                fill.setColor(e.isZombie() ? 0xFF6FBF3F : e.body);
                c.drawCircle(e.x, e.y, e.radius * 1.2f, fill);
            }
        }

        if (detailed) {
            for (int i = 0, n = world.entities.size(); i < n; i++) {
                Entity e = world.entities.get(i);
                if (e.phoneTimer <= 0 && e.talkTimer <= 0) continue;
                if (e.x < vx0 || e.x > vx1 || e.y < vy0 || e.y > vy1) continue;
                drawSpeechIcon(c, e);
            }
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

        if (night) {
            fill.setColor(0xA0060A1C);
            c.drawRect(vx0, vy0, vx1, vy1, fill);
            for (int i = 0, n = world.city.lamps.size(); i < n; i++) {
                float[] l = world.city.lamps.get(i);
                if (l[0] < vx0 - 40 || l[0] > vx1 + 40 || l[1] < vy0 - 40 || l[1] > vy1 + 40) continue;
                fill.setColor(0x26FFD890);
                c.drawCircle(l[0], l[1], 34, fill);
                fill.setColor(0x30FFE2A8);
                c.drawCircle(l[0], l[1], 16, fill);
                fill.setColor(0xFFFFF4C8);
                c.drawCircle(l[0], l[1], 1.6f, fill);
            }
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
                    if (night) {
                        fill.setColor(0x30FFD88A);
                        c.drawCircle(x, y, size * 7, fill);
                    }
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

        drawBuildings(c, vx0, vy0, vx1, vy1, detailed, night);

        for (int i = 0, n = world.dispatch.incidents.size(); i < n; i++) {
            Dispatch.Incident inc = world.dispatch.incidents.get(i);
            float pulse = (world.time * 1.5f + i * 0.3f) % 1f;
            stroke.setStrokeWidth(2f / Math.max(0.5f, scale) + 0.6f);
            stroke.setColor(alpha(0xFFFF4A3A, 1 - pulse));
            c.drawCircle(inc.x, inc.y, 10 + pulse * 40, stroke);
            stroke.setColor(0xCCFF4A3A);
            c.drawCircle(inc.x, inc.y, 7, stroke);
        }

        if (follow != null) {
            stroke.setColor(0xCCFFFFFF);
            stroke.setStrokeWidth(1f);
            c.drawCircle(follow.x, follow.y, follow.radius + 4 + (float) Math.sin(world.time * 6), stroke);
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
        if (timeOfDay == CityConfig.TIME_SUNSET) {
            fill.setColor(0x30FF7A2A);
            c.drawRect(vx0, vy0, vx1, vy1, fill);
        }
        c.restore();
    }

    /**
     * Draws trees and buildings standing up, GTA 2 style: a camera hangs above the middle of the screen,
     * so anything tall leans away from the centre and you see the walls that face the camera.
     */
    private void drawBuildings(Canvas c, float vx0, float vy0, float vx1, float vy1, boolean detailed,
                               boolean night) {
        float cx = camX + getWidth() / scale / 2, cy = camY + getHeight() / scale / 2;
        // Camera height grows with the visible area so the lean looks the same at every zoom level.
        float camH = Math.max(Math.max(getWidth(), getHeight()) / scale * 0.9f,
                Math.max(260f, world.city.maxHeight * 1.7f));
        boolean in3d = settings.buildings3d();
        float dark = night ? 0.42f : 1f;

        float ts = in3d ? camH / (camH - City.TREE_HEIGHT) : 1f;
        for (int i = 0, n = world.city.trees.size(); i < n; i++) {
            float[] t = world.city.trees.get(i);
            float x = cx + (t[0] - cx) * ts, y = cy + (t[1] - cy) * ts, r = t[2] * ts;
            if (x + r < vx0 || x - r > vx1 || y + r < vy0 || y - r > vy1) continue;
            fill.setColor(City.darken(0xFF2C5A22, dark));
            c.drawCircle(x, y, r, fill);
            fill.setColor(City.darken(0xFF3B742D, dark));
            c.drawCircle(x - 1.5f * ts, y - 1.5f * ts, r * 0.65f, fill);
            fill.setColor(City.darken(0xFF4C8A3A, dark));
            c.drawCircle(x - 2.5f * ts, y - 2.5f * ts, r * 0.3f, fill);
        }
        if (!in3d) {
            // Flat roofs are already in the ground bitmap; only night needs darkening, done by the overlay.
            if (night) {
                fill.setColor(0xA0060A1C);
                for (int i = 0, count = world.city.buildings.size(); i < count; i++) {
                    City.Building b = world.city.buildings.get(i);
                    if (b.x1 < vx0 || b.x0 > vx1 || b.y1 < vy0 || b.y0 > vy1) continue;
                    c.drawRect(b.x0, b.y0, b.x1, b.y1, fill);
                }
            }
            return;
        }

        int n = 0;
        for (int i = 0, count = world.city.buildings.size(); i < count; i++) {
            City.Building b = world.city.buildings.get(i);
            float s = camH / (camH - b.height);
            float rx0 = cx + (b.x0 - cx) * s, rx1 = cx + (b.x1 - cx) * s;
            float ry0 = cy + (b.y0 - cy) * s, ry1 = cy + (b.y1 - cy) * s;
            if (Math.max(b.x1, rx1) < vx0 || Math.min(b.x0, rx0) > vx1
                    || Math.max(b.y1, ry1) < vy0 || Math.min(b.y0, ry0) > vy1) continue;
            if (n == visible.length) {
                City.Building[] nb = new City.Building[n * 2];
                float[] nk = new float[n * 2];
                System.arraycopy(visible, 0, nb, 0, n);
                System.arraycopy(visibleKey, 0, nk, 0, n);
                visible = nb;
                visibleKey = nk;
            }
            float ddx = (b.x0 + b.x1) / 2 - cx, ddy = (b.y0 + b.y1) / 2 - cy;
            // Short buildings first, then the far ones: tall and near buildings end up in front.
            float key = b.height * 10000f - (float) Math.sqrt(ddx * ddx + ddy * ddy);
            int j = n++;
            while (j > 0 && visibleKey[j - 1] > key) {
                visible[j] = visible[j - 1];
                visibleKey[j] = visibleKey[j - 1];
                j--;
            }
            visible[j] = b;
            visibleKey[j] = key;
        }

        boolean windows = detailed || scale > 0.8f;
        for (int i = 0; i < n; i++) {
            City.Building b = visible[i];
            float s = camH / (camH - b.height);
            float rx0 = cx + (b.x0 - cx) * s, rx1 = cx + (b.x1 - cx) * s;
            float ry0 = cy + (b.y0 - cy) * s, ry1 = cy + (b.y1 - cy) * s;
            if (cy < b.y0) drawWall(c, b, b.x0, b.y0, b.x1, b.y0, rx0, ry0, rx1, ry0, 0.8f * dark, windows, 0, night);
            if (cy > b.y1) drawWall(c, b, b.x1, b.y1, b.x0, b.y1, rx1, ry1, rx0, ry1, 0.52f * dark, windows, 1, night);
            if (cx < b.x0) drawWall(c, b, b.x0, b.y1, b.x0, b.y0, rx0, ry1, rx0, ry0, 0.9f * dark, windows, 2, night);
            if (cx > b.x1) drawWall(c, b, b.x1, b.y0, b.x1, b.y1, rx1, ry0, rx1, ry1, 0.62f * dark, windows, 3, night);
            roofSrc.set((int) b.x0, (int) b.y0, (int) b.x1, (int) b.y1);
            roofDst.set(rx0, ry0, rx1, ry1);
            c.drawBitmap(world.city.bitmap, roofSrc, roofDst, bmpPaint);
            if (night) {
                fill.setColor(0xA0060A1C);
                c.drawRect(roofDst, fill);
            }
        }
    }

    /** Draws one wall from ground edge a-b up to roof edge ta-tb, with windows and doors on it. */
    private void drawWall(Canvas c, City.Building b, float ax, float ay, float bx, float by, float tax, float tay,
                          float tbx, float tby, float shade, boolean windows, int side, boolean night) {
        float len = Math.abs(bx - ax) + Math.abs(by - ay), hgt = b.height;
        wallSrc[0] = 0;
        wallSrc[1] = 0;
        wallSrc[2] = len;
        wallSrc[3] = 0;
        wallSrc[4] = len;
        wallSrc[5] = hgt;
        wallSrc[6] = 0;
        wallSrc[7] = hgt;
        wallDst[0] = ax;
        wallDst[1] = ay;
        wallDst[2] = bx;
        wallDst[3] = by;
        wallDst[4] = tbx;
        wallDst[5] = tby;
        wallDst[6] = tax;
        wallDst[7] = tay;
        if (!wallMatrix.setPolyToPoly(wallSrc, 0, wallDst, 0, 4)) return;
        c.save();
        c.concat(wallMatrix);
        fill.setColor(City.darken(b.wall, shade));
        c.drawRect(0, 0, len, hgt, fill);
        if (windows) {
            int floors = (int) (hgt / City.FLOOR);
            int cols = Math.max(1, (int) (len / City.T));
            float cw = len / cols;
            float glass = 0.55f + shade * 0.45f;
            int litMask = night ? 1 : 7;
            for (int k = 0; k < floors; k++) {
                float v0 = k * City.FLOOR;
                for (int i = 0; i < cols; i++) {
                    float u0 = i * cw;
                    int hsh = (b.seed * 73856093) ^ (side * 19349663) ^ (k * 83492791) ^ (i * 26544357);
                    boolean lit = ((hsh >>> 9) & litMask) == 0;
                    int litColor = night ? 0xFFFFE08A : 0xFFF0D98C;
                    if (b.kind == City.WAREHOUSE) {
                        if (k == 0 && i % 2 == 0) {
                            fill.setColor(City.darken(0xFFA4A8AC, glass));
                            c.drawRect(u0 + 2, 0, u0 + cw - 2, 9, fill);
                            fill.setColor(City.darken(0xFF7E8286, glass));
                            for (float v = 2; v < 9; v += 2.2f) c.drawRect(u0 + 2, v, u0 + cw - 2, v + 0.6f, fill);
                        } else if (k == floors - 1) {
                            fill.setColor(lit ? litColor : City.darken(0xFF3A4652, glass));
                            c.drawRect(u0 + 1, v0 + 5, u0 + cw - 1, v0 + 8, fill);
                        }
                    } else if (b.kind == City.HOUSE) {
                        boolean door = k == 0 && side == 1 && i == cols / 2;
                        if (door) {
                            fill.setColor(City.darken(0xFF5A3A28, glass));
                            c.drawRect(u0 + cw * 0.35f, 0, u0 + cw * 0.65f, 8, fill);
                        } else {
                            fill.setColor(City.darken(0xFFEEEEEE, glass));
                            c.drawRect(u0 + cw * 0.25f, v0 + 3.5f, u0 + cw * 0.75f, v0 + 9.5f, fill);
                            fill.setColor(lit ? litColor : City.darken(0xFF34414E, glass));
                            c.drawRect(u0 + cw * 0.25f + 1, v0 + 4.5f, u0 + cw * 0.75f - 1, v0 + 8.5f, fill);
                        }
                    } else if (k == 0) {
                        fill.setColor(night ? 0xFFE8D9A0 : City.darken(0xFF5A7890, glass));
                        c.drawRect(u0 + 2, v0 + 1.5f, u0 + cw - 2, v0 + 8.5f, fill);
                    } else {
                        fill.setColor(lit ? litColor : City.darken(0xFF27313B, glass));
                        c.drawRect(u0 + 3, v0 + 3.5f, u0 + cw - 3, v0 + 9.5f, fill);
                    }
                }
            }
            fill.setColor(City.darken(b.wall, shade * 0.8f));
            c.drawRect(0, hgt - 2.5f, len, hgt, fill);
        }
        c.restore();
    }

    /** A safe zone on the ground: tinted area, a sandbag ring with gaps for entrances and a tent. */
    private void drawZone(Canvas c, Dispatch.SafeZone z) {
        int tint = z.military ? 0xFF6FBF3F : 0xFF4F8FE0;
        fill.setColor(alpha(tint, 0.16f));
        c.drawCircle(z.x, z.y, z.r, fill);
        int bags = (int) (z.r * 2 * Math.PI / 5.5f);
        for (int i = 0; i < bags; i++) {
            if (i % 12 == 0 || i % 12 == 1) continue;
            double a = i * Math.PI * 2 / bags;
            float bx = z.x + (float) Math.cos(a) * z.r, by = z.y + (float) Math.sin(a) * z.r;
            fill.setColor(0x60000000);
            c.drawCircle(bx + 0.8f, by + 1f, 3f, fill);
            fill.setColor(i % 2 == 0 ? 0xFFA38D5E : 0xFF917C50);
            c.drawCircle(bx, by, 3f, fill);
        }
        fill.setColor(0x60000000);
        c.drawRect(z.x - 7, z.y - 5, z.x + 9, z.y + 7, fill);
        fill.setColor(z.military ? 0xFF5B6B3A : 0xFF2F4F86);
        c.drawRect(z.x - 8, z.y - 6, z.x + 8, z.y + 6, fill);
        fill.setColor(z.military ? 0xFF6F8048 : 0xFF3F64A6);
        c.drawRect(z.x - 8, z.y - 6, z.x + 8, z.y - 0.5f, fill);
        stroke.setColor(0xFFDDDDDD);
        stroke.setStrokeWidth(1f);
        c.drawLine(z.x + 11, z.y + 6, z.x + 11, z.y - 12, stroke);
        fill.setColor(tint);
        c.drawRect(z.x + 11, z.y - 12, z.x + 19, z.y - 7, fill);
    }

    /** A phone above someone calling 911, or a radio bubble above a unit that just spoke. */
    private void drawSpeechIcon(Canvas c, Entity e) {
        float x = e.x + e.radius + 2, y = e.y - e.radius - 7;
        if (e.phoneTimer > 0) {
            fill.setColor(0xFF1B1B1B);
            oval.set(x - 2.2f, y - 3.6f, x + 2.2f, y + 3.6f);
            c.drawRoundRect(oval, 0.8f, 0.8f, fill);
            fill.setColor((int) (world.time * 4) % 2 == 0 ? 0xFF7FE0FF : 0xFFFFFFFF);
            c.drawRect(x - 1.5f, y - 2.6f, x + 1.5f, y + 1.8f, fill);
        } else {
            int col = e.type == Entity.SOLDIER ? 0xFFA6DC72 : 0xFF7FB0FF;
            fill.setColor(0xEEFFFFFF);
            oval.set(x - 4.5f, y - 3f, x + 4.5f, y + 3f);
            c.drawRoundRect(oval, 2f, 2f, fill);
            c.drawCircle(x - 2.5f, y + 3.5f, 1f, fill);
            fill.setColor(col);
            for (int k = -1; k <= 1; k++) c.drawCircle(x + k * 2.2f, y, 0.8f, fill);
        }
    }

    private float screenX(float wx) {
        return (wx - camX) * scale;
    }

    private float screenY(float wy) {
        return (wy - camY) * scale;
    }

    /** Names over safe zones and 911 markers, drawn at screen size so they stay readable. */
    private void drawMapLabels(Canvas c) {
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(11.5f * dp);
        text.setTextSize(10.5f * dp);
        for (int i = 0, n = world.city.facilities.size(); i < n; i++) {
            City.Facility f = world.city.facilities.get(i);
            float sx = screenX(f.x), sy = screenY(f.y) + 26 * dp;
            if (sx < -100 * dp || sx > getWidth() + 100 * dp || sy < 0 || sy > barTop) continue;
            boolean zoned = false;
            for (int k = 0; k < world.dispatch.zones.size(); k++) {
                Dispatch.SafeZone z = world.dispatch.zones.get(k);
                if (Math.hypot(z.x - f.x, z.y - f.y) < 150) zoned = true;
            }
            if (zoned) continue;
            String label = f.name.toUpperCase();
            float tw = text.measureText(label);
            oval.set(sx - tw / 2 - 7 * dp, sy - 13 * dp, sx + tw / 2 + 7 * dp, sy + 5 * dp);
            fill.setColor(f.kind == City.FACILITY_BASE ? 0xB0303A1E : 0xB01E2E50);
            c.drawRoundRect(oval, 7 * dp, 7 * dp, fill);
            text.setColor(0xFFE6E6E6);
            c.drawText(label, sx, sy, text);
        }
        text.setTextSize(11.5f * dp);
        for (int i = 0, n = world.dispatch.zones.size(); i < n; i++) {
            Dispatch.SafeZone z = world.dispatch.zones.get(i);
            float sx = screenX(z.x), sy = screenY(z.y - z.r) - 10 * dp;
            if (sx < -100 * dp || sx > getWidth() + 100 * dp || sy < 0 || sy > barTop) continue;
            String label = (z.military ? "MILITARY" : "POLICE") + " SAFE ZONE  -  " + z.sheltered + "/" + z.capacity
                    + (z.full ? "  FULL" : "");
            float tw = text.measureText(label);
            oval.set(sx - tw / 2 - 8 * dp, sy - 14 * dp, sx + tw / 2 + 8 * dp, sy + 5 * dp);
            fill.setColor(z.military ? 0xD0304A20 : 0xD0203A66);
            c.drawRoundRect(oval, 8 * dp, 8 * dp, fill);
            text.setColor(0xFFFFFFFF);
            c.drawText(label, sx, sy, text);
        }
        for (int i = 0, n = world.dispatch.incidents.size(); i < n; i++) {
            Dispatch.Incident inc = world.dispatch.incidents.get(i);
            float sx = screenX(inc.x), sy = screenY(inc.y) - 22 * dp;
            if (sx < -60 * dp || sx > getWidth() + 60 * dp || sy < 0 || sy > barTop) continue;
            String label = "911  " + inc.zombiesNear + (inc.cops + inc.soldiers > 0 ? "  -  " + (inc.cops + inc.soldiers) + " responding" : "");
            float tw = text.measureText(label);
            oval.set(sx - tw / 2 - 7 * dp, sy - 13 * dp, sx + tw / 2 + 7 * dp, sy + 5 * dp);
            fill.setColor(0xD8A01E16);
            c.drawRoundRect(oval, 7 * dp, 7 * dp, fill);
            text.setColor(0xFFFFFFFF);
            c.drawText(label, sx, sy, text);
        }
    }

    /** The last few radio messages. Tapping one moves the camera to where it happened. */
    private void drawFeed(Canvas c) {
        feedCount = 0;
        if (!settings.radio()) return;
        java.util.ArrayList<Dispatch.Message> log = world.dispatch.log;
        int shown = 0;
        for (int i = log.size() - 1; i >= 0 && shown < 4; i--) {
            if (log.get(i).age > 16) break;
            shown++;
        }
        float lineH = 22 * dp, gap = 3 * dp, y = feedRect.top;
        text.setTextSize(11.5f * dp);
        for (int k = 0; k < shown; k++) {
            Dispatch.Message m = log.get(log.size() - shown + k);
            float a = Math.min(1, (16 - m.age) / 2f);
            String who = m.who + ": ";
            text.setTextAlign(Paint.Align.LEFT);
            float whoW = text.measureText(who);
            float maxText = feedRect.width() - whoW - 20 * dp;
            String body = m.text;
            if (text.measureText(body) > maxText) {
                while (body.length() > 4 && text.measureText(body + "...") > maxText) body = body.substring(0, body.length() - 1);
                body = body + "...";
            }
            float lw = whoW + text.measureText(body) + 16 * dp;
            RectF r = feedLines[feedCount];
            r.set(feedRect.right - lw, y, feedRect.right, y + lineH);
            if (portrait) r.offsetTo(feedRect.left, y);
            fill.setColor(alpha(0xC8000000, a));
            c.drawRoundRect(r, 7 * dp, 7 * dp, fill);
            text.setColor(alpha(m.color, a));
            c.drawText(who, r.left + 8 * dp, y + lineH * 0.68f, text);
            text.setColor(alpha(0xFFFFFFFF, a));
            c.drawText(body, r.left + 8 * dp + whoW, y + lineH * 0.68f, text);
            feedMsgs[feedCount++] = m;
            y += lineH + gap;
        }
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
        if (settings.gore()) {
            fill.setColor(0x886E0A0A);
            c.drawCircle(r * 0.2f, 0, r * 1.5f, fill);
        }
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

        // Stats panel: one column in landscape, two in portrait.
        float lh = 17 * dp;
        fill.setColor(0xB0101114);
        c.drawRoundRect(statsRect, 10 * dp, 10 * dp, fill);
        text.setTextSize(12.5f * dp);
        String[] labels = {"Civilians", "Cops", "Military", "Zombies", "Runners", "Brutes"};
        int perCol = portrait ? 3 : Entity.TYPE_COUNT;
        float colW = portrait ? statsRect.width() / 2 : statsRect.width();
        for (int t = 0; t < Entity.TYPE_COUNT; t++) {
            float x0 = statsRect.left + (t / perCol) * colW;
            float ty = statsRect.top + 6 * dp + lh * 0.8f + (t % perCol) * lh;
            fill.setColor(TYPE_COLORS[t]);
            c.drawCircle(x0 + 14 * dp, ty - 4.5f * dp, 4.5f * dp, fill);
            text.setColor(0xFFE6E6E6);
            text.setTextAlign(Paint.Align.LEFT);
            c.drawText(labels[t], x0 + 26 * dp, ty, text);
            text.setTextAlign(Paint.Align.RIGHT);
            c.drawText(String.valueOf(world.counts[t]), x0 + colW - 12 * dp, ty, text);
        }
        text.setTextAlign(Paint.Align.LEFT);
        float pad = statsRect.left;
        float y = statsRect.top + 6 * dp + lh * 0.8f + perCol * lh;
        text.setColor(0xFFA0A4AA);
        text.setTextSize(11.5f * dp);
        Dispatch d = world.dispatch;
        c.drawText("Turned " + world.turned + "   Killed " + world.zombiesKilled, pad + 12 * dp, y, text);
        y += lh;
        c.drawText("911 calls " + d.calls + "   Safe zones " + d.zones.size(), pad + 12 * dp, y, text);
        y += lh;
        int secs = (int) world.time;
        c.drawText(String.format("Sheltered %d   Time %d:%02d", d.sheltered, secs / 60, secs % 60),
                pad + 12 * dp, y, text);
        y += lh;
        c.drawText("Reserves: " + d.policeReserve + " police, " + d.squadReserve + " army", pad + 12 * dp, y, text);

        drawMapLabels(c);
        drawFeed(c);

        // Top buttons.
        String[] top = {simPaused ? "Play" : "Pause", "Speed " + SPEEDS[speedIdx] + "x",
                "Brush " + BRUSHES[brushIdx], "Clear", "Menu"};
        text.setTextSize((portrait ? 12 : 13) * dp);
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
            drawBanner(c, "Pick a unit or tool below, then tap the city.  Use Move to drag around, pinch to zoom.",
                    w / 2f, infoY);
        } else if (simPaused) {
            drawBanner(c, "PAUSED", w / 2f, infoY);
        }
    }

    /** Draws text on a dark pill above the tool bar, wrapping onto more lines on narrow screens. */
    private void drawBanner(Canvas c, String s, float cx, float by) {
        float maxW = getWidth() - 48 * dp;
        java.util.ArrayList<String> lines = new java.util.ArrayList<String>();
        String line = "";
        for (String word : s.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && text.measureText(next) > maxW) {
                lines.add(line.trim());
                line = word;
            } else {
                line = next;
            }
        }
        lines.add(line.trim());
        float lineH = text.getTextSize() * 1.35f, widest = 0;
        for (String l : lines) widest = Math.max(widest, text.measureText(l));
        float top = by - lineH * (lines.size() - 1);
        oval.set(cx - widest / 2 - 12 * dp, top - 18 * dp, cx + widest / 2 + 12 * dp, by + 7 * dp);
        fill.setColor(0xB0000000);
        c.drawRoundRect(oval, 8 * dp, 8 * dp, fill);
        text.setColor(0xFFFFFFFF);
        for (int i = 0; i < lines.size(); i++) c.drawText(lines.get(i), cx, top + i * lineH, text);
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
        } else if (t == TOOL_ZONE) {
            stroke.setColor(0xFF9BD46A);
            stroke.setStrokeWidth(2 * dp);
            oval.set(cx - size * 0.85f, cy + size * 0.25f, cx + size * 0.85f, cy + size * 0.85f);
            c.drawOval(oval, stroke);
            stroke.setColor(0xFFDDDDDD);
            c.drawLine(cx - size * 0.2f, cy + size * 0.55f, cx - size * 0.2f, cy - size * 0.8f, stroke);
            fill.setColor(0xFF4F8FE0);
            c.drawRect(cx - size * 0.2f, cy - size * 0.8f, cx + size * 0.55f, cy - size * 0.3f, fill);
            fill.setColor(0xFFFFFFFF);
            c.drawRect(cx + size * 0.05f, cy - size * 0.66f, cx + size * 0.3f, cy - size * 0.44f, fill);
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
        if (menu.isOpen()) {
            mode = MODE_NONE;
            return menu.onTouch(ev);
        }
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
                    if (tool != i) click();
                    tool = i;
                    hintTime = Math.min(hintTime, 3);
                }
            }
            return true;
        }
        for (int i = 0; i < feedCount; i++) {
            if (feedLines[i].contains(x, y)) {
                Dispatch.Message m = feedMsgs[i];
                follow = null;
                camX = m.x - getWidth() / scale / 2;
                camY = m.y - barTop / scale / 2;
                clampCamera();
                click();
                return true;
            }
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
        click();
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
            case BTN_MENU:
                menu.open(Menu.PAUSE);
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
        } else if (tool == TOOL_ZONE) {
            world.dispatch.orderZone(wx, wy);
        } else if (tool == TOOL_ERASE) {
            if (!world.dispatch.removeZoneAt(wx, wy)) world.erase(wx, wy, 22);
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
        } else if (tool != TOOL_BOMB && tool != TOOL_ZONE) {
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
