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
    private static final int TOOL_PAN = 0, TOOL_ORDER = 1, TOOL_CIV = 2, TOOL_ZOMBIE = 6, TOOL_ZONE = 7, TOOL_BOMB = 8,
            TOOL_ERASE = 9;
    // Tool index -> entity type spawned (or -1). The zombie tool spawns the selected zombie variant.
    private static final int[] TOOL_TYPE = {-1, -1, Entity.CIVILIAN, Entity.COP, Entity.SOLDIER, Entity.MEDIC,
            Entity.ZOMBIE, -1, -1, -1};
    private static final String[] TOOL_NAMES = {"Move", "Orders", "Civilian", "Cop", "Military", "Medic", "Zombie",
            "Safe Zone", "Bomb", "Erase"};
    private static final int[] ZOMBIE_VARIANTS = {Entity.ZOMBIE, Entity.RUNNER, Entity.BRUTE, Entity.CRAWLER,
            Entity.SCREAMER};
    private static final int[] CIV_VARIANTS = {Entity.CIVILIAN, Entity.DOG};
    private static final int BTN_PAUSE = 0, BTN_SPEED = 1, BTN_BRUSH = 2, BTN_VIEW = 3, BTN_CLEAR = 4, BTN_MENU = 5;
    private static final int TOP_BUTTONS = 6;
    private static final int[] SPEEDS = {1, 2, 4};
    private static final int[] BRUSHES = {1, 5, 10};
    private static final int[] SHOP_AWNINGS = {0xFFD83A3A, 0xFF2E7D4F, 0xFF2E5FB0, 0xFFE8A21C, 0xFF8A2E6B};
    private static final int[] ROW_COLORS = {0xFFF0AD4E, 0xFF4F7BE0, 0xFF8FA05A, 0xFFF2F2F2, 0xFF7CC24E};
    private static final String[] ROW_LABELS = {"Civilians", "Cops", "Military", "Medics", "Zombies"};

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
    private int zombieVariant, civVariant;
    private boolean statsCollapsed;
    /** Units picked with the Orders tool. */
    private final java.util.ArrayList<Entity> selection = new java.util.ArrayList<Entity>();
    private float orderX, orderY, orderMarker;
    private Entity follow;
    private float hintTime = 14f;

    // Layout.
    private final RectF[] toolRects = new RectF[TOOL_NAMES.length];
    private final RectF[] topRects = new RectF[TOP_BUTTONS];
    /** The stats panel as drawn this frame (it shrinks when collapsed). */
    private final RectF statsShown = new RectF();
    private final RectF labelRect = new RectF();
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
    /** Walls go partly see-through when zoomed right in. */
    private float wallFade = 1;
    private float[] visibleKey = new float[64];

    GameView(Context context) {
        super(context);
        dp = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        for (int i = 0; i < toolRects.length; i++) toolRects[i] = new RectF();
        for (int i = 0; i < TOP_BUTTONS; i++) topRects[i] = new RectF();
        buildIcons();
        settings = new Settings(context);
        sound = new Sound(context);
        menu = new Menu(this, settings, dp);
        // The main menu shows a live demo city in the background.
        CityConfig demo = new CityConfig();
        demo.v[CityConfig.OPT_PRESET] = rnd.nextInt(CityConfig.PRESETS.length);
        demo.v[CityConfig.OPT_MILITARY] = 1;
        demo.v[CityConfig.OPT_ZOMBIES] = 2;
        loadWorld(demo);
        applySettings();
    }

    private void buildIcons() {
        int[] bodies = {0xFFD9534F, 0xFF23408E, 0xFF55623A, 0xFFF2F2F2, 0xFFB07A3E, 0xFF4E5A3E, 0xFF6B3A36, 0xFF4D3F4F,
                0xFF4E5A3E, 0xFF6A6F60};
        int[] heads = {0xFF4A2E1A, 0xFF141C38, 0xFF3C4628, 0xFF4A2E1A, 0xFF8C6232, 0xFF7C9A5E, 0xFF9DAA70, 0xFF6F8D55,
                0xFF73905A, 0xFFC8D0B4};
        float[] radii = {3.6f, 3.8f, 4f, 3.7f, 2.8f, 3.8f, 3.5f, 6.5f, 3.0f, 3.5f};
        for (int t = 0; t < Entity.TYPE_COUNT; t++) {
            Entity e = new Entity();
            e.type = t;
            e.radius = radii[t];
            e.body = bodies[t];
            e.head = heads[t];
            e.skin = t >= Entity.ZOMBIE ? heads[t] : t == Entity.DOG ? bodies[t] : 0xFFE0AC69;
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

    private java.io.File saveFile() {
        return new java.io.File(getContext().getFilesDir(), "save.dat");
    }

    @Override
    public boolean canContinue() {
        return hasGame || saveFile().exists();
    }

    @Override
    public boolean saveGame() {
        if (!hasGame) return false;
        try {
            SaveGame.save(world, saveFile());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public World world() {
        return world;
    }

    /** Shows a text box for typing a city code; set by the activity (which owns Android's dialogs). */
    interface CodePrompt {
        void ask(String current);
    }

    CodePrompt codePrompt;

    @Override
    public void askCityCode(String current) {
        if (codePrompt != null) codePrompt.ask(current);
    }

    /** Called with whatever the player typed into the city code box. */
    void cityCodeEntered(String code) {
        menu.cityCodeEntered(code);
        invalidate();
    }

    @Override
    public void continueGame() {
        if (!hasGame && menu.screen == Menu.MAIN) {
            // Pick up the saved game.
            try {
                world = SaveGame.load(saveFile());
                applySettings();
                hasGame = true;
                follow = null;
                selection.clear();
                tool = TOOL_PAN;
                lastMessageCount = world.dispatch.messageCount;
                centerCamera();
                menu.screen = Menu.NONE;
                return;
            } catch (Exception e) {
                saveFile().delete();
                return;
            }
        }
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
        selection.clear();
        simPaused = false;
        speedIdx = 0;
        tool = TOOL_PAN;
        hintTime = 14f;
        lastMessageCount = 0;
        menu.screen = Menu.NONE;
    }

    @Override
    public void toMainMenu() {
        saveGame();
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
        saveGame();
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
        float th = 38 * dp, tgap = (portrait ? 4 : 6) * dp, tw, tx;
        int nb = TOP_BUTTONS;
        if (portrait) {
            tw = (w - 16 * dp - tgap * (nb - 1)) / nb;
            tx = 8 * dp;
        } else {
            tw = Math.min(84 * dp, (w * 0.6f - tgap * (nb - 1)) / nb);
            tx = w - 10 * dp - tw * nb - tgap * (nb - 1);
        }
        for (int i = 0; i < nb; i++) {
            topRects[i].set(tx, 10 * dp, tx + tw, 10 * dp + th);
            tx += tw + tgap;
        }
        float lh = 17 * dp;
        if (portrait) {
            float top = 10 * dp + th + 8 * dp;
            statsRect.set(10 * dp, top, 10 * dp + Math.min(260 * dp, w - 20 * dp), top + lh * 8 + 12 * dp);
            feedRect.set(10 * dp, statsRect.bottom + 8 * dp, w - 10 * dp, statsRect.bottom + 8 * dp + 4 * 24 * dp);
        } else {
            statsRect.set(10 * dp, 10 * dp, 200 * dp, 10 * dp + lh * 10 + 12 * dp);
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
        if (orderMarker > 0) orderMarker -= dt;
        for (int i = selection.size() - 1; i >= 0; i--) if (selection.get(i).dead) selection.remove(i);
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

        for (int i = 0, n = world.pickups.size(); i < n; i++) {
            World.Pickup p = world.pickups.get(i);
            if (p.x < vx0 || p.x > vx1 || p.y < vy0 || p.y > vy1) continue;
            // A dropped gun, gently pulsing so it can be spotted.
            fill.setColor(alpha(0xFFFFE27A, 0.25f + 0.15f * (float) Math.sin(world.time * 4)));
            c.drawCircle(p.x, p.y, 5, fill);
            stroke.setColor(0xFF1A1A1A);
            stroke.setStrokeWidth(1.4f);
            c.drawLine(p.x - 3, p.y, p.x + 3.5f, p.y - 1, stroke);
            c.drawLine(p.x - 2.5f, p.y, p.x - 3, p.y + 2.2f, stroke);
        }
        for (int i = 0, n = world.birds.size(); i < n; i++) {
            World.Bird b = world.birds.get(i);
            if (b.flying || b.x < vx0 || b.x > vx1 || b.y < vy0 || b.y > vy1) continue;
            fill.setColor(0xFF7D8088);
            c.drawCircle(b.x, b.y, 1.5f, fill);
            fill.setColor(0xFF4C5058);
            c.drawCircle(b.x + (float) Math.cos(b.flap) * 1.2f, b.y + (float) Math.sin(b.flap) * 0.6f, 0.8f, fill);
        }
        for (int i = 0, n = world.fires.size(); i < n; i++) {
            World.Fire f = world.fires.get(i);
            if (f.x < vx0 || f.x > vx1 || f.y < vy0 || f.y > vy1) continue;
            float flicker = 0.7f + 0.3f * (float) Math.sin(world.time * 17 + i);
            fill.setColor(alpha(0xFFFF8A20, 0.25f * flicker * Math.min(1, f.life / 10)));
            c.drawCircle(f.x, f.y, 16, fill);
        }
        for (int i = 0, n = world.city.buildings.size(); i < n; i++) {
            City.Building b = world.city.buildings.get(i);
            if (b.occupants.isEmpty() || b.doorX < vx0 || b.doorX > vx1 || b.doorY < vy0 || b.doorY > vy1) continue;
            // Boarded-up door.
            fill.setColor(0xFF7A5634);
            c.drawRect(b.doorX - 5, b.doorY - 1.5f, b.doorX + 5, b.doorY + 0.2f, fill);
            c.drawRect(b.doorX - 4, b.doorY + 1.2f, b.doorX + 4, b.doorY + 2.9f, fill);
        }

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
                // Zoomed out: a body in its colour with a head dot, so types stay readable.
                fill.setColor(e.isZombie() ? 0xFF5FAF35 : e.body);
                c.drawCircle(e.x, e.y, e.radius * 1.35f, fill);
                fill.setColor(e.isZombie() ? 0xFF2E5A1A : e.head);
                c.drawCircle(e.x, e.y, e.radius * 0.7f, fill);
            }
        }
        for (int i = 0, n = selection.size(); i < n; i++) {
            Entity e = selection.get(i);
            stroke.setColor(0xFFFFD24A);
            stroke.setStrokeWidth(1.2f);
            c.drawCircle(e.x, e.y, e.radius + 4, stroke);
        }
        if (orderMarker > 0) {
            stroke.setColor(alpha(0xFFFFD24A, Math.min(1, orderMarker)));
            stroke.setStrokeWidth(1.5f);
            c.drawCircle(orderX, orderY, 6 + (1.5f - orderMarker) * 14, stroke);
        }
        for (int i = 0, n = world.screams.size(); i < n; i++) {
            float[] sc = world.screams.get(i);
            stroke.setColor(alpha(0xFFE8F0D0, 1 - sc[2] / 1.2f));
            stroke.setStrokeWidth(2f);
            c.drawCircle(sc[0], sc[1], 10 + sc[2] * 120, stroke);
        }
        for (int i = 0, n = world.fleet.vehicles.size(); i < n; i++) {
            Fleet.Vehicle v = world.fleet.vehicles.get(i);
            if (v.type != Fleet.HELI) drawVehicle(c, v);
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

        drawBuildings(c, vx0, vy0, vx1, vy1, detailed);
        for (int i = 0, n = world.fleet.vehicles.size(); i < n; i++) {
            Fleet.Vehicle v = world.fleet.vehicles.get(i);
            if (v.type == Fleet.HELI) drawHeli(c, v);
        }
        for (int i = 0, n = world.birds.size(); i < n; i++) {
            World.Bird b = world.birds.get(i);
            if (!b.flying || b.x < vx0 || b.x > vx1 || b.y < vy0 || b.y > vy1) continue;
            float wing = (float) Math.sin(b.flap) * 2.2f;
            fill.setColor(0x30000000);
            c.drawCircle(b.x + 10, b.y + 14, 1.4f, fill);
            stroke.setColor(0xFF6E717A);
            stroke.setStrokeWidth(0.9f);
            c.drawLine(b.x - 2.5f, b.y - wing, b.x, b.y, stroke);
            c.drawLine(b.x, b.y, b.x + 2.5f, b.y - wing, stroke);
        }

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
        c.restore();
    }

    /**
     * Draws trees and buildings standing up, GTA 2 style: a camera hangs above the middle of the screen,
     * so anything tall leans away from the centre and you see the walls that face the camera.
     */
    private void drawBuildings(Canvas c, float vx0, float vy0, float vx1, float vy1, boolean detailed) {
        float cx = camX + getWidth() / scale / 2, cy = camY + getHeight() / scale / 2;
        // Camera height grows with the visible area so the lean looks the same at every zoom level.
        float camH = Math.max(Math.max(getWidth(), getHeight()) / scale * 0.9f,
                Math.max(260f, world.city.maxHeight * 1.7f));
        boolean in3d = settings.buildings3d();
        // Zoomed right in, roofs (and walls) turn see-through so you can look inside.
        float see = Math.max(0, Math.min(1, (scale / dp - 2.2f) / 1.3f));

        float ts = in3d ? camH / (camH - City.TREE_HEIGHT) : 1f;
        for (int i = 0, n = world.city.trees.size(); i < n; i++) {
            float[] t = world.city.trees.get(i);
            float x = cx + (t[0] - cx) * ts, y = cy + (t[1] - cy) * ts, r = t[2] * ts;
            if (x + r < vx0 || x - r > vx1 || y + r < vy0 || y - r > vy1) continue;
            fill.setColor(alpha(0xFF2C5A22, 1 - see * 0.5f));
            c.drawCircle(x, y, r, fill);
            fill.setColor(alpha(0xFF3B742D, 1 - see * 0.5f));
            c.drawCircle(x - 1.5f * ts, y - 1.5f * ts, r * 0.65f, fill);
            fill.setColor(alpha(0xFF4C8A3A, 1 - see * 0.5f));
            c.drawCircle(x - 2.5f * ts, y - 2.5f * ts, r * 0.3f, fill);
        }
        if (!in3d) {
            // Bird's-eye: the flat roofs are already in the ground bitmap.
            for (int i = 0, count = world.city.buildings.size(); i < count; i++) {
                City.Building b = world.city.buildings.get(i);
                if (b.collapsed || b.x1 < vx0 || b.x0 > vx1 || b.y1 < vy0 || b.y0 > vy1) continue;
                drawDamage(c, b, b.x0, b.y0, b.x1, b.y1, 1);
                if (see > 0) drawInterior(c, b, see);
            }
            return;
        }

        int n = 0;
        for (int i = 0, count = world.city.buildings.size(); i < count; i++) {
            City.Building b = world.city.buildings.get(i);
            if (b.collapsed) continue;
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
        wallFade = 1 - 0.55f * see;
        for (int i = 0; i < n; i++) {
            City.Building b = visible[i];
            float s = camH / (camH - b.height);
            float rx0 = cx + (b.x0 - cx) * s, rx1 = cx + (b.x1 - cx) * s;
            float ry0 = cy + (b.y0 - cy) * s, ry1 = cy + (b.y1 - cy) * s;
            if (see > 0) drawInterior(c, b, 1);
            if (cy < b.y0) drawWall(c, b, b.x0, b.y0, b.x1, b.y0, rx0, ry0, rx1, ry0, 0.8f, windows, 0);
            if (cy > b.y1) drawWall(c, b, b.x1, b.y1, b.x0, b.y1, rx1, ry1, rx0, ry1, 0.52f, windows, 1);
            if (cx < b.x0) drawWall(c, b, b.x0, b.y1, b.x0, b.y0, rx0, ry1, rx0, ry0, 0.9f, windows, 2);
            if (cx > b.x1) drawWall(c, b, b.x1, b.y0, b.x1, b.y1, rx1, ry0, rx1, ry1, 0.62f, windows, 3);
            roofSrc.set((int) b.x0, (int) b.y0, (int) b.x1, (int) b.y1);
            roofDst.set(rx0, ry0, rx1, ry1);
            float roofA = 1 - 0.82f * see;
            bmpPaint.setAlpha((int) (255 * roofA));
            c.drawBitmap(world.city.bitmap, roofSrc, roofDst, bmpPaint);
            bmpPaint.setAlpha(255);
            drawDamage(c, b, rx0, ry0, rx1, ry1, roofA);
        }
        wallFade = 1;
    }

    /** Cracks and scorch marks on the roof of a building that has taken blast damage. */
    private void drawDamage(Canvas c, City.Building b, float x0, float y0, float x1, float y1, float a) {
        float dmg = 1 - b.hp / b.maxHp;
        if (dmg < 0.12f || a <= 0.05f) return;
        float w = x1 - x0, h = y1 - y0;
        int n = 2 + (int) (dmg * 9);
        int hsh = b.seed * 1103515245 + 12345;
        for (int k = 0; k < n; k++) {
            hsh = hsh * 1103515245 + 12345;
            float u = ((hsh >>> 8) & 1023) / 1023f;
            hsh = hsh * 1103515245 + 12345;
            float v = ((hsh >>> 8) & 1023) / 1023f;
            hsh = hsh * 1103515245 + 12345;
            float r = 2 + ((hsh >>> 8) & 255) / 255f * 5 * dmg;
            fill.setColor(alpha(0x80141210, a * Math.min(1, dmg * 1.6f)));
            c.drawCircle(x0 + u * w, y0 + v * h, r, fill);
            stroke.setColor(alpha(0xC0201C18, a));
            stroke.setStrokeWidth(0.8f);
            float ex = x0 + u * w + (((hsh >>> 4) & 15) - 7.5f) * 1.6f, ey = y0 + v * h + (((hsh >>> 12) & 15) - 7.5f) * 1.6f;
            c.drawLine(x0 + u * w, y0 + v * h, Math.max(x0, Math.min(x1, ex)), Math.max(y0, Math.min(y1, ey)), stroke);
        }
        if (dmg > 0.6f && world.rnd.nextFloat() < 0.15f) {
            // Still smouldering.
            world.particle(x0 + world.rnd.nextFloat() * w, y0 + world.rnd.nextFloat() * h, 3, -8, 1.5f, 2.5f, 0xFF3A3A3A,
                    World.P_SMOKE);
        }
    }

    /** What you see through a see-through roof: the floor, rooms, furniture and anyone hiding inside. */
    private void drawInterior(Canvas c, City.Building b, float a) {
        float x0 = b.x0 + 1, y0 = b.y0 + 1, x1 = b.x1 - 1, y1 = b.y1 - 1, w = x1 - x0, h = y1 - y0;
        int floorCol = b.kind == City.HOUSE ? 0xFFB8946A : b.kind == City.CHURCH || b.kind == City.SPIRE ? 0xFF9C8C76
                : b.kind == City.MARKET || b.kind == City.KIOSK ? 0xFFD8D8D0 : b.kind == City.WAREHOUSE ? 0xFF8A8A84
                : b.kind == City.BARRACKS || b.kind == City.TOWER ? 0xFF7E806E : 0xFFC4BFB4;
        fill.setColor(alpha(floorCol, a));
        c.drawRect(x0, y0, x1, y1, fill);
        int hsh = b.seed * 69069 + 1;
        int furn = City.darken(floorCol, 0.62f);
        fill.setColor(alpha(furn, a));
        if (b.kind == City.MARKET || b.kind == City.KIOSK) {
            // Shelving aisles.
            for (float yy = y0 + 6; yy < y1 - 6; yy += 9) c.drawRect(x0 + 5, yy, x1 - 5, yy + 2.5f, fill);
        } else if (b.kind == City.CHURCH || b.kind == City.SPIRE) {
            // Pews either side of the aisle.
            boolean alongX = w >= h;
            for (float t = 6; t < (alongX ? w : h) - 8; t += 5) {
                if (alongX) {
                    c.drawRect(x0 + t, y0 + 4, x0 + t + 2, y0 + h / 2 - 3, fill);
                    c.drawRect(x0 + t, y0 + h / 2 + 3, x0 + t + 2, y1 - 4, fill);
                } else {
                    c.drawRect(x0 + 4, y0 + t, x0 + w / 2 - 3, y0 + t + 2, fill);
                    c.drawRect(x0 + w / 2 + 3, y0 + t, x1 - 4, y0 + t + 2, fill);
                }
            }
        } else if (b.kind == City.WAREHOUSE) {
            for (int k = 0; k < 6; k++) {
                hsh = hsh * 69069 + 1;
                float u = ((hsh >>> 8) & 255) / 255f, v = ((hsh >>> 16) & 255) / 255f;
                c.drawRect(x0 + 3 + u * (w - 12), y0 + 3 + v * (h - 12), x0 + 9 + u * (w - 12), y0 + 9 + v * (h - 12), fill);
            }
        } else {
            // Desks, tables and beds scattered about.
            for (float yy = y0 + 5; yy < y1 - 6; yy += 11)
                for (float xx = x0 + 5; xx < x1 - 6; xx += 12) {
                    hsh = hsh * 69069 + 1;
                    if (((hsh >>> 10) & 3) == 0) continue;
                    c.drawRect(xx, yy, xx + 5, yy + 3, fill);
                }
            // Inner walls split big floors into rooms.
            stroke.setColor(alpha(City.darken(b.wall, 0.75f), a));
            stroke.setStrokeWidth(1.2f);
            if (w > 40) {
                float sx = x0 + w * (0.4f + ((b.seed >>> 3) & 7) / 40f);
                c.drawLine(sx, y0, sx, y0 + h * 0.4f, stroke);
                c.drawLine(sx, y0 + h * 0.4f + 7, sx, y1, stroke);
            }
            if (h > 40) {
                float sy = y0 + h * (0.4f + ((b.seed >>> 6) & 7) / 40f);
                c.drawLine(x0, sy, x0 + w * 0.3f, sy, stroke);
                c.drawLine(x0 + w * 0.3f + 7, sy, x1, sy, stroke);
            }
        }
        stroke.setColor(alpha(City.darken(b.wall, 0.6f), a));
        stroke.setStrokeWidth(2f);
        oval.set(x0, y0, x1, y1);
        c.drawRect(oval, stroke);
        if (a < 0.5f) return;
        // The people hiding here, huddled away from the door.
        int cols = Math.max(1, (int) ((w - 8) / 9));
        for (int i = 0, n = b.occupants.size(); i < n; i++) {
            Entity o = b.occupants.get(i);
            int row = i / cols;
            o.x = x0 + 6 + (i % cols) * 9 + ((i * 7) % 3);
            o.y = y0 + 6 + (row % Math.max(1, (int) ((h - 8) / 9))) * 9;
            o.angle = (i * 1.7f + b.seed) % 6.28f;
            o.aiming = false;
            drawEntity(c, o, false);
        }
    }

    /** Draws one wall from ground edge a-b up to roof edge ta-tb, with windows and doors on it. */
    private void drawWall(Canvas c, City.Building b, float ax, float ay, float bx, float by, float tax, float tay,
                          float tbx, float tby, float shade, boolean windows, int side) {
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
        fill.setColor(fade(City.darken(b.wall, shade)));
        c.drawRect(0, 0, len, hgt, fill);
        if (windows) {
            int floors = (int) (hgt / City.FLOOR);
            int cols = Math.max(1, (int) (len / City.T));
            float cw = len / cols;
            float glass = 0.55f + shade * 0.45f;
            int litMask = 7;
            for (int k = 0; k < floors; k++) {
                float v0 = k * City.FLOOR;
                for (int i = 0; i < cols; i++) {
                    float u0 = i * cw;
                    int hsh = (b.seed * 73856093) ^ (side * 19349663) ^ (k * 83492791) ^ (i * 26544357);
                    boolean lit = ((hsh >>> 9) & litMask) == 0;
                    int litColor = 0xFFF0D98C;
                    if (b.kind == City.SHOP && k == 0) {
                        // Shopfront: big window under a striped awning.
                        fill.setColor(fade(City.darken(0xFF6F8EA6, glass)));
                        c.drawRect(u0 + 1.5f, 0.5f, u0 + cw - 1.5f, 7, fill);
                        int awn = SHOP_AWNINGS[(b.seed + i) % SHOP_AWNINGS.length];
                        for (int st = 0; st < 4; st++) {
                            fill.setColor(fade(City.darken(st % 2 == 0 ? awn : 0xFFF2F2F2, glass)));
                            c.drawRect(u0 + cw * st / 4f, 7.5f, u0 + cw * (st + 1) / 4f, 10.5f, fill);
                        }
                    } else if (b.kind == City.CHURCH || b.kind == City.SPIRE) {
                        // Tall stained-glass windows spanning the floors.
                        if (k == 0 && i % 2 == 1) {
                            fill.setColor(fade(City.darken(i % 4 == 1 ? 0xFF6A4E9A : 0xFF3F7AA8, glass)));
                            c.drawRect(u0 + cw * 0.35f, 2, u0 + cw * 0.65f, Math.min(hgt - 4, City.FLOOR * floors - 3), fill);
                        }
                    } else if (b.kind == City.FIRE_STATION && k == 0) {
                        fill.setColor(fade(City.darken(0xFFC8302A, glass)));
                        c.drawRect(u0 + 1.5f, 0, u0 + cw - 1.5f, 9.5f, fill);
                        fill.setColor(fade(City.darken(0xFFE8E8E8, glass)));
                        for (float v = 2; v < 9; v += 2.5f) c.drawRect(u0 + 1.5f, v, u0 + cw - 1.5f, v + 0.5f, fill);
                    } else if ((b.kind == City.MARKET || b.kind == City.KIOSK) && k == 0) {
                        fill.setColor(fade(City.darken(0xFF7FA6C0, glass)));
                        c.drawRect(u0, 0.5f, u0 + cw, 8.5f, fill);
                        fill.setColor(fade(City.darken(b.kind == City.KIOSK ? 0xFFD83A3A : 0xFF3E8A4A, glass)));
                        c.drawRect(u0, 8.5f, u0 + cw, 11, fill);
                    } else if (b.kind == City.CRYPT) {
                        if (k == 0 && i == cols / 2) {
                            fill.setColor(fade(City.darken(0xFF3A3632, glass)));
                            c.drawRect(u0 + cw * 0.3f, 0, u0 + cw * 0.7f, 8, fill);
                        }
                    } else if (b.kind == City.WAREHOUSE) {
                        if (k == 0 && i % 2 == 0) {
                            fill.setColor(fade(City.darken(0xFFA4A8AC, glass)));
                            c.drawRect(u0 + 2, 0, u0 + cw - 2, 9, fill);
                            fill.setColor(fade(City.darken(0xFF7E8286, glass)));
                            for (float v = 2; v < 9; v += 2.2f) c.drawRect(u0 + 2, v, u0 + cw - 2, v + 0.6f, fill);
                        } else if (k == floors - 1) {
                            fill.setColor(fade(lit ? litColor : City.darken(0xFF3A4652, glass)));
                            c.drawRect(u0 + 1, v0 + 5, u0 + cw - 1, v0 + 8, fill);
                        }
                    } else if (b.kind == City.HOUSE) {
                        boolean door = k == 0 && side == 1 && i == cols / 2;
                        if (door) {
                            fill.setColor(fade(City.darken(0xFF5A3A28, glass)));
                            c.drawRect(u0 + cw * 0.35f, 0, u0 + cw * 0.65f, 8, fill);
                        } else {
                            fill.setColor(fade(City.darken(0xFFEEEEEE, glass)));
                            c.drawRect(u0 + cw * 0.25f, v0 + 3.5f, u0 + cw * 0.75f, v0 + 9.5f, fill);
                            fill.setColor(fade(lit ? litColor : City.darken(0xFF34414E, glass)));
                            c.drawRect(u0 + cw * 0.25f + 1, v0 + 4.5f, u0 + cw * 0.75f - 1, v0 + 8.5f, fill);
                        }
                    } else if (k == 0) {
                        fill.setColor(fade(City.darken(0xFF5A7890, glass)));
                        c.drawRect(u0 + 2, v0 + 1.5f, u0 + cw - 2, v0 + 8.5f, fill);
                    } else {
                        fill.setColor(fade(lit ? litColor : City.darken(0xFF27313B, glass)));
                        c.drawRect(u0 + 3, v0 + 3.5f, u0 + cw - 3, v0 + 9.5f, fill);
                    }
                }
            }
            fill.setColor(fade(City.darken(b.wall, shade * 0.8f)));
            c.drawRect(0, hgt - 2.5f, len, hgt, fill);
        }
        // Bullet holes and scorch marks from the fighting.
        for (int m = 0; m < b.markCount; m++) {
            int k = m * 4;
            if ((int) b.marks[k] != side) continue;
            float u = b.marks[k + 1], v = b.marks[k + 2], sz = b.marks[k + 3];
            if (u > len || v > hgt) continue;
            if (sz > 2) {
                fill.setColor(fade(0x66141210));
                c.drawRect(u - sz, Math.max(0, v - sz * 0.6f), u + sz, Math.min(hgt, v + sz * 1.6f), fill);
                fill.setColor(fade(0x88141210));
                c.drawRect(u - sz * 0.5f, Math.max(0, v - sz * 0.2f), u + sz * 0.5f, Math.min(hgt, v + sz), fill);
            } else {
                fill.setColor(fade(0xFF2A2622));
                c.drawRect(u - sz, v - sz, u + sz, v + sz, fill);
            }
        }
        c.restore();
    }

    /** A car, police cruiser, army truck or fire engine, with its damage showing. */
    private void drawVehicle(Canvas c, Fleet.Vehicle v) {
        boolean truck = v.type == Fleet.TRUCK, engine = v.type == Fleet.FIRE_ENGINE;
        float hl = truck || engine ? 9.5f : 8f, hw = truck ? 5.2f : engine ? 4.9f : 4.4f;
        if (v.spraying) {
            // The hose: an arc of water from the engine to the fire.
            stroke.setColor(0x99A8D8FF);
            stroke.setStrokeWidth(1.6f);
            float px = v.x, py = v.y;
            for (int k = 1; k <= 8; k++) {
                float t = k / 8f;
                float x = v.x + (v.sprayX - v.x) * t, y = v.y + (v.sprayY - v.y) * t - (float) Math.sin(t * Math.PI) * 8;
                c.drawLine(px, py, x, y, stroke);
                px = x;
                py = y;
            }
        }
        c.save();
        c.translate(v.x, v.y);
        c.rotate((float) Math.toDegrees(v.angle));
        fill.setColor(0x55000000);
        oval.set(-hl + 1.5f, -hw + 1.5f, hl + 1.5f, hw + 1.5f);
        c.drawRoundRect(oval, 2.5f, 2.5f, fill);
        boolean civ = v.type == Fleet.CAR;
        int body = civ ? v.color : truck ? 0xFF4F5A33 : engine ? 0xFFC8302A : 0xFF1C1D22;
        if (v.burnt) body = 0xFF2B2623;
        else if (v.broken) body = City.darken(body, 0.65f);
        fill.setColor(body);
        oval.set(-hl, -hw, hl, hw);
        c.drawRoundRect(oval, 2.5f, 2.5f, fill);
        if (v.burnt) {
            // A burnt-out shell.
            fill.setColor(0xFF4A2F22);
            c.drawRect(-hl + 2, -hw + 1.2f, hl - 2, hw - 1.2f, fill);
            fill.setColor(0xFF151312);
            c.drawRect(-2, -hw + 1.5f, 3, hw - 1.5f, fill);
            c.restore();
            return;
        }
        if (civ) {
            fill.setColor(0xFF1E2A33);
            c.drawRect(1.5f, -hw + 1, 4.5f, hw - 1, fill);
            c.drawRect(-5f, -hw + 1, -3f, hw - 1, fill);
            fill.setColor(City.lighten(body, 0.15f));
            c.drawRect(-2.5f, -hw + 1.5f, 1f, hw - 1.5f, fill);
            if (!v.parked) {
                fill.setColor(0xFFFFF4C0);
                c.drawCircle(hl - 0.8f, -hw + 1.3f, 0.8f, fill);
                c.drawCircle(hl - 0.8f, hw - 1.3f, 0.8f, fill);
            }
            if (!v.riders.isEmpty()) {
                // Heads of the people riding along.
                fill.setColor(0xFF4A2E1A);
                for (int k = 0; k < Math.min(3, v.riders.size()); k++)
                    c.drawCircle(-1.8f + (k == 2 ? -2.5f : 0), k % 2 == 0 ? -1.6f : 1.6f, 1.1f, fill);
            }
        } else if (truck) {
            fill.setColor(v.broken ? 0xFF3C4428 : 0xFF5F6B40);
            c.drawRect(-hl + 0.8f, -hw + 0.8f, hl * 0.3f, hw - 0.8f, fill);
            fill.setColor(0xFF1E2A33);
            c.drawRect(hl * 0.45f, -hw + 1, hl * 0.7f, hw - 1, fill);
        } else if (engine) {
            fill.setColor(0xFF1E2A33);
            c.drawRect(hl * 0.5f, -hw + 1, hl * 0.75f, hw - 1, fill);
            // Ladder along the top.
            fill.setColor(0xFFD8D8D8);
            c.drawRect(-hl + 1.5f, -1.6f, hl * 0.4f, -0.9f, fill);
            c.drawRect(-hl + 1.5f, 0.9f, hl * 0.4f, 1.6f, fill);
            for (float x = -hl + 2.5f; x < hl * 0.4f; x += 2.2f) c.drawRect(x, -1.6f, x + 0.5f, 1.6f, fill);
            if (!v.broken) {
                boolean blink = ((int) (v.anim * 8)) % 2 == 0;
                fill.setColor(blink ? 0xFFFF3A30 : 0xFFFFD27A);
                c.drawRect(hl * 0.42f, -hw + 1, hl * 0.5f, hw - 1, fill);
            }
        } else {
            fill.setColor(v.broken ? 0xFFA0A0A0 : 0xFFEDEDED);
            c.drawRect(-3f, -hw, 2f, -hw + 1.3f, fill);
            c.drawRect(-3f, hw - 1.3f, 2f, hw, fill);
            fill.setColor(0xFF1E2A33);
            c.drawRect(2.5f, -hw + 1, 5f, hw - 1, fill);
            if (!v.broken) {
                boolean blink = ((int) (v.anim * 8)) % 2 == 0;
                fill.setColor(blink ? 0xFFFF3A30 : 0xFF5A1A18);
                c.drawRect(-1f, -hw + 1, 0.5f, 0, fill);
                fill.setColor(blink ? 0xFF3A5AA0 : 0xFF3A7BFF);
                c.drawRect(-1f, 0, 0.5f, hw - 1, fill);
            }
        }
        float dmg = 1 - Math.max(0, v.hp) / v.maxHp;
        if (dmg > 0.3f) {
            // Dents, scrapes and a cracked windscreen.
            stroke.setColor(0xAAD8D8D8);
            stroke.setStrokeWidth(0.5f);
            c.drawLine(hl * 0.3f, -hw * 0.6f, hl * 0.55f, hw * 0.2f, stroke);
            fill.setColor(0x66000000);
            c.drawCircle(hl - 2, -hw + 1.5f, 1.3f, fill);
            if (dmg > 0.6f) {
                c.drawCircle(-hl + 2.5f, hw - 1.5f, 1.6f, fill);
                c.drawLine(-hl * 0.2f, hw * 0.7f, hl * 0.1f, -hw * 0.3f, stroke);
            }
        }
        c.restore();
    }

    /** The helicopter: shadow on the ground, body, tail and a spinning rotor. */
    private void drawHeli(Canvas c, Fleet.Vehicle v) {
        fill.setColor(0x40000000);
        c.drawCircle(v.x + 18, v.y + 24, 11, fill);
        c.save();
        c.translate(v.x, v.y);
        c.rotate((float) Math.toDegrees(v.angle));
        stroke.setColor(0xFF3E4A2C);
        stroke.setStrokeWidth(2.4f);
        c.drawLine(-6, 0, -20, 0, stroke);
        c.drawLine(-19, -3, -19, 3, stroke);
        fill.setColor(0xFF4F5E36);
        oval.set(-8, -5, 9, 5);
        c.drawOval(oval, fill);
        fill.setColor(0xFF7FA3B8);
        oval.set(3, -3.2f, 9, 3.2f);
        c.drawOval(oval, fill);
        fill.setColor(0x30000000);
        c.drawCircle(0, 0, 17, fill);
        stroke.setColor(0xB0202020);
        stroke.setStrokeWidth(1.4f);
        float a = v.anim * 30;
        for (int k = 0; k < 2; k++) {
            float ca = (float) Math.cos(a + k * Math.PI / 2) * 17, sa = (float) Math.sin(a + k * Math.PI / 2) * 17;
            c.drawLine(-ca, -sa, ca, sa, stroke);
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
    /** True if a label would sit under the stats panel, the radio feed or the top buttons. */
    private boolean uiCovers(RectF r) {
        if (overlaps(r, statsShown)) return true;
        for (int i = 0; i < feedCount; i++) if (overlaps(r, feedLines[i])) return true;
        for (RectF t : topRects) if (overlaps(r, t)) return true;
        return r.bottom > barTop;
    }

    private static boolean overlaps(RectF a, RectF b) {
        return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
    }

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
            if (uiCovers(oval)) continue;
            fill.setColor(f.kind == City.FACILITY_BASE ? 0xB0303A1E : f.kind == City.FACILITY_FIRE ? 0xB0802018
                    : f.kind == City.FACILITY_HOSPITAL ? 0xB0703030 : 0xB01E2E50);
            c.drawRoundRect(oval, 7 * dp, 7 * dp, fill);
            text.setColor(0xFFE6E6E6);
            c.drawText(label, sx, sy, text);
        }
        text.setTextSize(10.5f * dp);
        for (int i = 0, n = world.city.buildings.size(); i < n; i++) {
            City.Building b = world.city.buildings.get(i);
            if (b.occupants.isEmpty()) continue;
            float sx = screenX(b.doorX), sy = screenY(b.doorY) - 12 * dp;
            if (sx < -60 * dp || sx > getWidth() + 60 * dp || sy < 0 || sy > barTop) continue;
            String label = b.occupants.size() + " hiding";
            float tw = text.measureText(label);
            oval.set(sx - tw / 2 - 6 * dp, sy - 12 * dp, sx + tw / 2 + 6 * dp, sy + 7 * dp);
            if (uiCovers(oval)) continue;
            fill.setColor(0xC0302418);
            c.drawRoundRect(oval, 6 * dp, 6 * dp, fill);
            text.setColor(0xFFFFFFFF);
            c.drawText(label, sx, sy, text);
            float f = Math.max(0, b.barricade) / 100f;
            fill.setColor(f > 0.5f ? 0xFFB08A5A : 0xFFFF6B4A);
            c.drawRect(oval.left + 4 * dp, oval.bottom - 3 * dp, oval.left + 4 * dp + (oval.width() - 8 * dp) * f,
                    oval.bottom - 1.5f * dp, fill);
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
            if (uiCovers(oval)) continue;
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
            if (uiCovers(oval)) continue;
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
        int maxLines = portrait ? 2 : 3;
        for (int i = log.size() - 1; i >= 0 && shown < maxLines; i--) {
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

    private int fade(int color) {
        return wallFade >= 1 ? color : alpha(color, wallFade);
    }

    private static int alpha(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        int na = (int) (base * Math.max(0, Math.min(1, a)));
        return (na << 24) | (color & 0xFFFFFF);
    }

    private void drawDog(Canvas c, Entity e, boolean healthBar) {
        float r = e.radius;
        c.save();
        c.translate(e.x, e.y);
        c.rotate((float) Math.toDegrees(e.angle));
        fill.setColor(0x44000000);
        oval.set(-r * 1.1f + 0.8f, -r * 0.6f + 0.8f, r * 1.2f + 0.8f, r * 0.6f + 0.8f);
        c.drawOval(oval, fill);
        float sw = (float) Math.sin(e.phase * 1.6f) * r * 0.4f;
        stroke.setColor(City.darken(e.body, 0.7f));
        stroke.setStrokeWidth(r * 0.3f);
        c.drawLine(r * 0.55f, -r * 0.4f, r * 0.55f + sw, -r * 0.72f, stroke);
        c.drawLine(r * 0.55f, r * 0.4f, r * 0.55f - sw, r * 0.72f, stroke);
        c.drawLine(-r * 0.6f, -r * 0.4f, -r * 0.6f - sw, -r * 0.72f, stroke);
        c.drawLine(-r * 0.6f, r * 0.4f, -r * 0.6f + sw, r * 0.72f, stroke);
        // Wagging tail.
        stroke.setColor(e.body);
        stroke.setStrokeWidth(r * 0.25f);
        float wag = (float) Math.sin(world.time * (e.fleeTimer > 0 ? 6 : 14) + e.phase) * r * 0.45f;
        c.drawLine(-r * 0.9f, 0, -r * 1.6f, wag, stroke);
        fill.setColor(e.body);
        oval.set(-r * 1.05f, -r * 0.5f, r * 0.85f, r * 0.5f);
        c.drawOval(oval, fill);
        fill.setColor(e.head);
        c.drawCircle(r * 1.05f, 0, r * 0.48f, fill);
        fill.setColor(City.darken(e.head, 0.7f));
        c.drawCircle(r * 0.9f, -r * 0.38f, r * 0.2f, fill);
        c.drawCircle(r * 0.9f, r * 0.38f, r * 0.2f, fill);
        fill.setColor(0xFF1A1A1A);
        c.drawCircle(r * 1.5f, 0, r * 0.14f, fill);
        if (e.hurt > 0) {
            fill.setColor(alpha(0xFFFFFFFF, e.hurt * 0.7f));
            c.drawCircle(0, 0, r * 1.1f, fill);
        }
        c.restore();
        if (healthBar && e.hp < e.maxHp && e.hp > 0) {
            float bw = 8, bx = e.x - bw / 2, by = e.y - r - 3.5f;
            fill.setColor(0xAA000000);
            c.drawRect(bx - 0.4f, by - 0.4f, bx + bw + 0.4f, by + 1.9f, fill);
            float f = e.hp / e.maxHp;
            fill.setColor(f > 0.5f ? 0xFF4CD964 : f > 0.25f ? 0xFFFFCC00 : 0xFFFF3B30);
            c.drawRect(bx, by, bx + bw * f, by + 1.5f, fill);
        }
    }

    private void drawEntity(Canvas c, Entity e, boolean healthBar) {
        if (e.type == Entity.DOG) {
            drawDog(c, e, healthBar);
            return;
        }
        float r = e.radius;
        c.save();
        c.translate(e.x, e.y);
        c.rotate((float) Math.toDegrees(e.angle));

        fill.setColor(0x44000000);
        oval.set(-r * 0.7f + 0.9f, -r + 0.9f, r * 0.7f + 0.9f, r + 0.9f);
        c.drawOval(oval, fill);

        if (e.type == Entity.CRAWLER) {
            // Dragging itself along on its arms: long low body, no legs.
            float sw = (float) Math.sin(e.phase * 1.5f) * r * 0.4f;
            stroke.setColor(e.skin);
            stroke.setStrokeWidth(r * 0.42f);
            c.drawLine(r * 0.3f, -r * 0.6f, r * 1.7f + sw, -r * 0.9f, stroke);
            c.drawLine(r * 0.3f, r * 0.6f, r * 1.7f - sw, r * 0.9f, stroke);
            fill.setColor(0x906E0A0A);
            c.drawCircle(-r * 1.3f, 0, r * 0.5f, fill);
        } else if (e.isZombie()) {
            float sw = (float) Math.sin(e.phase) * r * 0.2f;
            stroke.setColor(e.skin);
            stroke.setStrokeWidth(r * 0.42f);
            c.drawLine(r * 0.1f, -r * 0.72f, r * 1.55f + sw, -r * 0.55f, stroke);
            c.drawLine(r * 0.1f, r * 0.72f, r * 1.55f - sw, r * 0.55f, stroke);
        } else if (e.isArmed() || (e.hasGun && e.aiming)) {
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
        if (e.type == Entity.CRAWLER) oval.set(-r * 1.2f, -r * 0.7f, r * 0.62f, r * 0.7f);
        else oval.set(-r * 0.62f, -r, r * 0.62f, r);
        c.drawOval(oval, fill);
        if (e.type == Entity.MEDIC) {
            fill.setColor(0xFFD83A3A);
            c.drawRect(-r * 0.45f, -r * 0.13f, r * 0.05f, r * 0.13f, fill);
            c.drawRect(-r * 0.33f, -r * 0.35f, -r * 0.07f, r * 0.35f, fill);
        }
        if (e.hasGun && !e.aiming) {
            // A gun tucked in a belt.
            fill.setColor(0xFF1A1A1A);
            c.drawRect(-r * 0.2f, r * 0.55f, r * 0.45f, r * 0.8f, fill);
        }
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
            if (e.type == Entity.SCREAMER) {
                fill.setColor(0xFF1A0A0A);
                oval.set(r * 0.2f, -r * 0.25f, r * 0.62f, r * 0.25f);
                c.drawOval(oval, fill);
            }
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
        if (e.stun > 0 && e.isZombie()) {
            // Dazed: little stars spinning over its head.
            fill.setColor(0xFFFFE27A);
            for (int k = 0; k < 3; k++) {
                double a = world.time * 6 + k * 2.1;
                c.drawCircle(e.x + (float) Math.cos(a) * r, e.y - r - 2 + (float) Math.sin(a) * 1.5f, 0.8f, fill);
            }
        }
        if (healthBar && e.canShoot() && e.magSize > 0) {
            // Ammo: magazine plus spare rounds, as a thin yellow bar.
            int full = e.type == Entity.SOLDIER ? 180 : e.type == Entity.COP ? 60 : 30;
            float f = Math.min(1, (e.ammo + e.reserve) / (float) full);
            float bw = Math.max(10, r * 3), bx = e.x - bw / 2, by = e.y - r - 2.2f;
            fill.setColor(0x88000000);
            c.drawRect(bx - 0.3f, by - 0.3f, bx + bw + 0.3f, by + 1.1f, fill);
            fill.setColor(f > 0 ? 0xFFE8C547 : 0xFFFF3B30);
            c.drawRect(bx, by, bx + bw * Math.max(f, 0.05f), by + 0.8f, fill);
        }

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

        // Stats panel: one column in landscape, two in portrait. Tap it to collapse to one line.
        float lh = 17 * dp;
        Dispatch d = world.dispatch;
        int[] rows = {world.counts[Entity.CIVILIAN], world.counts[Entity.COP], world.counts[Entity.SOLDIER],
                world.counts[Entity.MEDIC], world.zombieCount()};
        text.setTextSize(12.5f * dp);
        if (statsCollapsed) {
            String line = "People " + (world.humanCount() + world.hiding + world.riding) + "   Zombies " + world.zombieCount()
                    + "   (tap for more)";
            float tw = text.measureText(line);
            oval.set(statsRect.left, statsRect.top, statsRect.left + tw + 24 * dp, statsRect.top + 28 * dp);
            statsShown.set(oval);
            fill.setColor(0xB0101114);
            c.drawRoundRect(oval, 10 * dp, 10 * dp, fill);
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(0xFFE6E6E6);
            c.drawText(line, statsRect.left + 12 * dp, statsRect.top + 19 * dp, text);
        } else {
            statsShown.set(statsRect);
            fill.setColor(0xB0101114);
            c.drawRoundRect(statsRect, 10 * dp, 10 * dp, fill);
            int perCol = portrait ? 3 : rows.length;
            float colW = portrait ? statsRect.width() / 2 : statsRect.width();
            for (int t = 0; t < rows.length; t++) {
                float x0 = statsRect.left + (t / perCol) * colW;
                float ty = statsRect.top + 6 * dp + lh * 0.8f + (t % perCol) * lh;
                fill.setColor(ROW_COLORS[t]);
                c.drawCircle(x0 + 14 * dp, ty - 4.5f * dp, 4.5f * dp, fill);
                text.setColor(0xFFE6E6E6);
                text.setTextAlign(Paint.Align.LEFT);
                c.drawText(ROW_LABELS[t], x0 + 26 * dp, ty, text);
                text.setTextAlign(Paint.Align.RIGHT);
                c.drawText(String.valueOf(rows[t]), x0 + colW - 12 * dp, ty, text);
            }
            text.setTextAlign(Paint.Align.LEFT);
            float pad = statsRect.left;
            float y = statsRect.top + 6 * dp + lh * 0.8f + perCol * lh;
            text.setColor(0xFFA0A4AA);
            text.setTextSize(11.5f * dp);
            c.drawText("Turned " + world.turned + "   Killed " + world.zombiesKilled, pad + 12 * dp, y, text);
            y += lh;
            c.drawText("911 calls " + d.calls + "   Safe zones " + d.zones.size(), pad + 12 * dp, y, text);
            y += lh;
            c.drawText("Sheltered " + d.sheltered + "   Hiding " + world.hiding + "   In cars " + world.riding,
                    pad + 12 * dp, y, text);
            y += lh;
            c.drawText("Reserves: " + d.policeReserve + " police, " + d.squadReserve + " army, " + d.airSorties + " air",
                    pad + 12 * dp, y, text);
            y += lh;
            int secs = (int) world.time;
            c.drawText(String.format("Time %d:%02d", secs / 60, secs % 60), pad + 12 * dp, y, text);
        }

        // The feed goes first so map labels can keep out of its way.
        drawFeed(c);
        drawMapLabels(c);

        // Top buttons.
        String[] top = {simPaused ? "Play" : "Pause", "Speed " + SPEEDS[speedIdx] + "x",
                "Brush " + BRUSHES[brushIdx], settings.buildings3d() ? "View 3D" : "View Top", "Clear", "Menu"};
        text.setTextSize((portrait ? 11 : 13) * dp);
        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < TOP_BUTTONS; i++) {
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
            String name = i == TOOL_ZOMBIE ? Entity.NAMES[ZOMBIE_VARIANTS[zombieVariant]]
                    : i == TOOL_CIV ? Entity.NAMES[CIV_VARIANTS[civVariant]] : TOOL_NAMES[i];
            c.drawText(name, cx, r.bottom - 7 * dp, text);
            if ((i == TOOL_ZOMBIE || i == TOOL_CIV) && sel) {
                text.setTextSize(9 * dp);
                c.drawText("tap to change", cx, r.top + 10 * dp, text);
                text.setTextSize(11.5f * dp);
            }
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
 if (tool == TOOL_ORDER && follow == null) {
            Entity lead = selection.isEmpty() ? null : selection.get(0);
            drawBanner(c, lead == null ? "Orders: tap a cop or soldier to select them."
                    : "Selected " + Dispatch.name(lead) + (selection.size() > 1 ? " and " + (selection.size() - 1) + " more" : "")
                    + ". Tap where to send them, or tap them again to release.", w / 2f, infoY);
        } else if (follow != null) {
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
        int type = t == TOOL_ZOMBIE ? ZOMBIE_VARIANTS[zombieVariant] : t == TOOL_CIV ? CIV_VARIANTS[civVariant] : TOOL_TYPE[t];
        if (type >= 0) {
            Entity e = icons[type];
            float s = size / 5.2f;
            if (type == Entity.BRUTE) s *= 0.62f;
            if (type == Entity.DOG) s *= 1.3f;
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
        if (t == TOOL_ORDER) {
            // A waypoint flag with a chevron.
            stroke.setColor(0xFFFFD24A);
            stroke.setStrokeWidth(2.2f * dp);
            c.drawLine(cx - size * 0.7f, cy + size * 0.2f, cx - size * 0.1f, cy - size * 0.5f, stroke);
            c.drawLine(cx - size * 0.1f, cy - size * 0.5f, cx + size * 0.5f, cy + size * 0.2f, stroke);
            fill.setColor(0xFFFFD24A);
            c.drawCircle(cx - size * 0.1f, cy + size * 0.65f, size * 0.22f, fill);
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
                    click();
                    if (tool == i && i == TOOL_ZOMBIE) zombieVariant = (zombieVariant + 1) % ZOMBIE_VARIANTS.length;
                    if (tool == i && i == TOOL_CIV) civVariant = (civVariant + 1) % CIV_VARIANTS.length;
                    if (i != TOOL_ORDER) selection.clear();
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
        if (statsCollapsed ? (x < statsRect.left + 300 * dp && y < statsRect.top + 30 * dp && x > statsRect.left
                && y > statsRect.top) : statsRect.contains(x, y)) {
            statsCollapsed = !statsCollapsed;
            click();
            return true;
        }
        return false;
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
            case BTN_VIEW:
                settings.setBuildings3d(!settings.buildings3d());
                world.say(settings.buildings3d() ? "3D view" : "Bird's-eye view");
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
        if (tool == TOOL_ORDER) {
            orderTap(wx, wy);
        } else if (tool == TOOL_BOMB) {
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
        } else if (tool != TOOL_BOMB && tool != TOOL_ZONE && tool != TOOL_ORDER) {
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

    /**
     * Orders tool: tap a cop or soldier to select them (a soldier brings their whole squad), then tap where
     * to send them. Tapping the selection again sends them back to normal duty.
     */
    private void orderTap(float wx, float wy) {
        float reach = Math.max(12, 26 * dp / scale);
        Entity pick = null;
        float best = reach * reach;
        for (int i = 0, n = world.entities.size(); i < n; i++) {
            Entity e = world.entities.get(i);
            if (!e.isArmed()) continue;
            float d = (e.x - wx) * (e.x - wx) + (e.y - wy) * (e.y - wy);
            if (d < best) {
                best = d;
                pick = e;
            }
        }
        if (pick != null) {
            if (selection.contains(pick)) {
                world.dispatch.dismiss(selection);
                selection.clear();
                return;
            }
            selection.clear();
            selection.add(pick);
            if (pick.type == Entity.SOLDIER)
                for (int i = 0, n = world.entities.size(); i < n; i++) {
                    Entity e = world.entities.get(i);
                    if (e != pick && e.type == Entity.SOLDIER && e.squad == pick.squad) selection.add(e);
                }
            return;
        }
        if (selection.isEmpty()) return;
        world.dispatch.order(selection, wx, wy);
        orderX = wx;
        orderY = wy;
        orderMarker = 1.5f;
        selection.clear();
    }

    private void spawnBrush(float wx, float wy) {
        int type = tool == TOOL_ZOMBIE ? ZOMBIE_VARIANTS[zombieVariant] : tool == TOOL_CIV ? CIV_VARIANTS[civVariant] : TOOL_TYPE[tool];
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
