package com.instantaxtion.zombiesandbox;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Random;

/** Main menu, pause menu, New Game setup, settings and patch notes, all drawn on the game canvas. */
final class Menu {
    interface Host {
        boolean hasGame();

        void continueGame();

        void startGame(CityConfig cfg);

        void toMainMenu();

        void quit();

        void settingsChanged();

        void click();

        /** True if there is a game in memory or a saved one to load. */
        boolean canContinue();

        /** Saves the current game; returns false if it failed. */
        boolean saveGame();

        World world();

        /** Asks the player to type a city code (shows the current one to copy or edit). */
        void askCityCode(String current);
    }

    static final int NONE = 0, MAIN = 1, SETUP = 2, SETTINGS = 3, NOTES = 4, PAUSE = 5, STATS = 6, TUTORIAL = 7;

    private static final int A_CONTINUE = 0, A_NEW = 1, A_SETTINGS = 2, A_NOTES = 3, A_QUIT = 4, A_RESUME = 5,
            A_MAIN_MENU = 6, A_BACK = 7, A_START = 8, A_RANDOM = 9, A_SAVE = 10, A_STATS = 11, A_HOW_TO = 12,
            A_TUT_NEXT = 13, A_TUT_PREV = 14, A_TUT_DONE = 15, A_CODE = 16;

    private static final String[][] TUTORIAL_PAGES = {
            {"Welcome to Zombie City", "A sandbox: set up a city, start an outbreak and watch what happens. There are no missions. Play however you like."},
            {"Spawning", "Pick a unit in the bottom bar and tap the city. Drag to paint a line of them, or use Brush (top) to spawn 5 or 10 at once. Tap the Zombie button again to switch between zombie types, and the Civilian button again for dogs."},
            {"Looking around", "Pinch to zoom and drag with two fingers. With Move selected you can drag with one finger, and tap anyone to follow them with the camera. Zoom right in to see through roofs. The View button switches between 3D and bird's-eye."},
            {"The city fights back", "Civilians call 911, the police respond by car, and the military is called in when it gets bad. The radio feed shows what they say. Tap a message to jump there."},
            {"Safe zones and hiding", "Police and soldiers set up guarded safe zones and civilians run to them. Others barricade themselves in buildings until zombies break the door down. Use the Safe Zone tool to order a zone yourself."},
            {"Giving orders", "Pick Orders, tap a cop or soldier (a soldier brings their whole squad), then tap where to send them. They hold that spot. Tap them again to send them back to normal duty."},
            {"Medics, ammo and reserves", "Medics heal the hurt and can cure fresh bites. Ammo runs out, and units go back to the station or base to resupply. Backup, army squads and helicopter support are limited."},
            {"Pausing and saving", "Menu (top right) pauses the game, saves it and shows the stats. The game also saves itself when you leave the app."},
    };
    private int tutorialPage;

    private static final class Button {
        final RectF r = new RectF();
        String label;
        int action;
        boolean primary;
    }

    private static final class Row {
        final RectF r = new RectF(), prev = new RectF();
        int index;
    }

    int screen = MAIN;
    private int returnTo = MAIN;
    final CityConfig config = new CityConfig();

    private final Host host;
    private final Settings settings;
    private final float dp;
    private final Random rnd = new Random();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plain = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final ArrayList<Button> buttons = new ArrayList<Button>();
    private final ArrayList<Button> pool = new ArrayList<Button>();
    private final ArrayList<Row> rows = new ArrayList<Row>();
    private final RectF listArea = new RectF();
    private final RectF tmp = new RectF();
    private float scroll, contentHeight;
    /** A short line shown above the New Game buttons (city code accepted or not). */
    private String note;
    private float noteTime;
    private float time;

    private boolean dragging, pressed;
    private float downX, downY, lastY;

    Menu(Host host, Settings settings, float dp) {
        this.host = host;
        this.settings = settings;
        this.dp = dp;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        plain.setTypeface(Typeface.DEFAULT);
    }

    boolean isOpen() {
        return screen != NONE;
    }

    /** True while the city behind the menu should keep simulating (not when the game is paused). */
    boolean liveBackground() {
        return screen == MAIN || (screen != NONE && screen != PAUSE && returnTo == MAIN);
    }

    void open(int s) {
        if (s == SETUP || s == SETTINGS || s == NOTES || s == STATS || s == TUTORIAL) {
            if (screen == MAIN || screen == PAUSE || screen == NONE) returnTo = screen;
        } else {
            returnTo = MAIN;
        }
        screen = s;
        scroll = 0;
        if (s == NOTES) settings.markNotesRead();
        if (s == TUTORIAL) tutorialPage = 0;
    }

    /** A city code typed in by the player. */
    void cityCodeEntered(String text) {
        boolean ok = config.applyCode(text);
        note = ok ? "City " + config.code() + " (" + CityConfig.PRESETS[config.v[CityConfig.OPT_PRESET]]
                + "). Press Start to play it." : "That isn't a city code. Codes look like 12-483920.";
        noteTime = 4;
    }

    /** Handles the system back button. Returns false when the app should close. */
    boolean back() {
        switch (screen) {
            case MAIN:
                return false;
            case PAUSE:
                host.continueGame();
                return true;
            case NONE:
                open(PAUSE);
                return true;
            default:
                screen = returnTo;
                scroll = 0;
                return true;
        }
    }

    // ------------------------------------------------------------------ drawing

    void draw(Canvas c, int w, int h, float dt) {
        time += dt;
        for (Button b : buttons) pool.add(b);
        buttons.clear();
        rows.clear();
        switch (screen) {
            case MAIN: drawMain(c, w, h); break;
            case PAUSE: drawPause(c, w, h); break;
            case SETUP: drawOptions(c, w, h, "New Game", config, true); break;
            case SETTINGS: drawOptions(c, w, h, "Settings", settings, false); break;
            case NOTES: drawNotes(c, w, h); break;
            case STATS: drawStats(c, w, h); break;
            case TUTORIAL: drawTutorial(c, w, h); break;
        }
        for (Button b : buttons) drawButton(c, b);
    }

    private Button button(String label, int action, boolean primary, float l, float t, float r, float b) {
        Button btn = pool.isEmpty() ? new Button() : pool.remove(pool.size() - 1);
        btn.label = label;
        btn.action = action;
        btn.primary = primary;
        btn.r.set(l, t, r, b);
        buttons.add(btn);
        return btn;
    }

    private void drawButton(Canvas c, Button b) {
        fill.setColor(b.primary ? 0xFF3F8A3A : 0xE0262A30);
        c.drawRoundRect(b.r, 12 * dp, 12 * dp, fill);
        stroke.setStrokeWidth(1.5f * dp);
        stroke.setColor(b.primary ? 0xFF9BE08A : 0x50FFFFFF);
        c.drawRoundRect(b.r, 12 * dp, 12 * dp, stroke);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Math.min(18 * dp, b.r.height() * 0.38f));
        text.setColor(0xFFF4F4F4);
        c.drawText(b.label, b.r.centerX(), b.r.centerY() + text.getTextSize() * 0.36f, text);
    }

    private void drawTitle(Canvas c, float cx, float cy, float maxW) {
        String title = "ZOMBIE CITY";
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(100);
        float size = Math.min(64 * dp, maxW / text.measureText(title) * 100);
        text.setTextSize(size);
        float wobble = (float) Math.sin(time * 1.3f) * 1.5f * dp;
        text.setColor(0xCC000000);
        c.drawText(title, cx + 3 * dp, cy + 4 * dp + wobble, text);
        text.setColor(0xFF8BD450);
        c.drawText(title, cx, cy + wobble, text);
        text.setTextSize(size * 0.36f);
        text.setColor(0xFFEDEDED);
        String sub = "S A N D B O X";
        c.drawText(sub, cx, cy + size * 0.62f + wobble, text);
        // Blood drips under the title.
        fill.setColor(0xFFA01818);
        float tw = Math.min(maxW, size * 6.5f);
        for (int i = 0; i < 6; i++) {
            float x = cx - tw / 2 + tw * (0.1f + i * 0.16f);
            float len = (6 + (i * 37 % 11)) * dp * (1 + 0.25f * (float) Math.sin(time * 0.8f + i));
            c.drawRect(x - 1.5f * dp, cy + 2 * dp, x + 1.5f * dp, cy + 2 * dp + len, fill);
            c.drawCircle(x, cy + 2 * dp + len, 2.6f * dp, fill);
        }
    }

    private void drawMain(Canvas c, int w, int h) {
        fill.setColor(0x88000000);
        c.drawRect(0, 0, w, h, fill);
        boolean landscape = w > h;
        float bw = Math.min(300 * dp, w - 60 * dp), bh = 52 * dp, gap = 12 * dp;
        boolean game = host.canContinue();
        int n = game ? 6 : 5;
        float bx, by;
        if (landscape) {
            drawTitle(c, w * 0.29f, h * 0.45f, w * 0.44f);
            bw = Math.min(280 * dp, w * 0.38f);
            bh = Math.min(52 * dp, (h - 40 * dp - gap * (n - 1)) / n);
            bx = w * 0.72f - bw / 2;
            by = (h - (bh * n + gap * (n - 1))) / 2;
        } else {
            drawTitle(c, w / 2f, h * 0.22f, w - 40 * dp);
            bx = (w - bw) / 2;
            by = h * 0.4f;
        }
        if (game) {
            button("Continue", A_CONTINUE, true, bx, by, bx + bw, by + bh);
            by += bh + gap;
        }
        button("New Game", A_NEW, !game, bx, by, bx + bw, by + bh);
        by += bh + gap;
        button("How to Play", A_HOW_TO, false, bx, by, bx + bw, by + bh);
        by += bh + gap;
        button("Settings", A_SETTINGS, false, bx, by, bx + bw, by + bh);
        by += bh + gap;
        button(settings.hasUnreadNotes() ? "Patch Notes  (new!)" : "Patch Notes", A_NOTES, false, bx, by, bx + bw, by + bh);
        by += bh + gap;
        button("Quit", A_QUIT, false, bx, by, bx + bw, by + bh);

        plain.setTextSize(12 * dp);
        plain.setTextAlign(Paint.Align.RIGHT);
        plain.setColor(0xAAFFFFFF);
        c.drawText("v" + PatchNotes.VERSION, w - 12 * dp, h - 12 * dp, plain);
    }

    private void drawPause(Canvas c, int w, int h) {
        fill.setColor(0x99000000);
        c.drawRect(0, 0, w, h, fill);
        float gap = 8 * dp, bh = Math.min(48 * dp, (h - 110 * dp - gap * 5) / 6);
        float pw = Math.min(320 * dp, w - 40 * dp), ph = 70 * dp + bh * 6 + gap * 5 + 20 * dp;
        tmp.set((w - pw) / 2, (h - ph) / 2, (w + pw) / 2, (h + ph) / 2);
        fill.setColor(0xF0181A1E);
        c.drawRoundRect(tmp, 16 * dp, 16 * dp, fill);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(26 * dp);
        text.setColor(0xFF8BD450);
        c.drawText("PAUSED", w / 2f, tmp.top + 38 * dp, text);
        plain.setTextAlign(Paint.Align.CENTER);
        plain.setTextSize(12 * dp);
        plain.setColor(0xFF9AA0A8);
        c.drawText("City code " + host.world().city.cfg.code(), w / 2f, tmp.top + 58 * dp, plain);
        float bx = tmp.left + 20 * dp, bw = pw - 40 * dp, by = tmp.top + 70 * dp;
        String[] labels = {"Resume", "Save Game", "Stats", "New Game", "Settings", "Main Menu"};
        int[] actions = {A_RESUME, A_SAVE, A_STATS, A_NEW, A_SETTINGS, A_MAIN_MENU};
        for (int i = 0; i < labels.length; i++) {
            button(labels[i], actions[i], i == 0, bx, by, bx + bw, by + bh);
            by += bh + gap;
        }
    }

    /** Humans vs zombies over time, plus the totals for this game. */
    private void drawStats(Canvas c, int w, int h) {
        float top = header(c, w, h, "Stats");
        World world = host.world();
        boolean landscape = w > h;
        float side = 20 * dp;
        RectF chart = new RectF(side, top + 10 * dp, landscape ? w * 0.58f : w - side,
                landscape ? h - 20 * dp : top + Math.min(260 * dp, h * 0.36f));
        fill.setColor(0xFF1C1F24);
        c.drawRoundRect(chart, 10 * dp, 10 * dp, fill);
        RectF plot = new RectF(chart.left + 36 * dp, chart.top + 30 * dp, chart.right - 14 * dp, chart.bottom - 24 * dp);
        int n = world.histCount;
        int max = 10;
        for (int i = 0; i < n; i++) max = Math.max(max, Math.max(world.histHumans[i], world.histZombies[i]));
        max = (max + 9) / 10 * 10;
        plain.setTextSize(11 * dp);
        plain.setTextAlign(Paint.Align.RIGHT);
        stroke.setStrokeWidth(1);
        for (int k = 0; k <= 4; k++) {
            float y = plot.bottom - plot.height() * k / 4;
            stroke.setColor(0x30FFFFFF);
            c.drawLine(plot.left, y, plot.right, y, stroke);
            plain.setColor(0xFF8A9099);
            c.drawText(String.valueOf(max * k / 4), plot.left - 6 * dp, y + 4 * dp, plain);
        }
        plain.setTextAlign(Paint.Align.LEFT);
        int secs = (int) (n * world.statStep);
        plain.setColor(0xFF8A9099);
        c.drawText("0:00", plot.left, plot.bottom + 16 * dp, plain);
        plain.setTextAlign(Paint.Align.RIGHT);
        c.drawText(String.format("%d:%02d", secs / 60, secs % 60), plot.right, plot.bottom + 16 * dp, plain);
        if (n >= 2) {
            int[][] series = {world.histHumans, world.histZombies};
            int[] colors = {0xFF6FA8FF, 0xFF7CC24E};
            for (int sIdx = 0; sIdx < 2; sIdx++) {
                stroke.setColor(colors[sIdx]);
                stroke.setStrokeWidth(2.2f * dp);
                for (int i = 1; i < n; i++) {
                    float x0 = plot.left + plot.width() * (i - 1) / (n - 1), x1 = plot.left + plot.width() * i / (n - 1);
                    float y0 = plot.bottom - plot.height() * series[sIdx][i - 1] / max;
                    float y1 = plot.bottom - plot.height() * series[sIdx][i] / max;
                    c.drawLine(x0, y0, x1, y1, stroke);
                }
            }
        } else {
            plain.setTextAlign(Paint.Align.CENTER);
            c.drawText("Play a little longer to see the chart.", plot.centerX(), plot.centerY(), plain);
        }
        // Legend.
        text.setTextSize(12 * dp);
        text.setTextAlign(Paint.Align.LEFT);
        fill.setColor(0xFF6FA8FF);
        c.drawCircle(chart.left + 16 * dp, chart.top + 16 * dp, 4.5f * dp, fill);
        text.setColor(0xFFE6E6E6);
        c.drawText("People", chart.left + 26 * dp, chart.top + 20 * dp, text);
        fill.setColor(0xFF7CC24E);
        c.drawCircle(chart.left + 96 * dp, chart.top + 16 * dp, 4.5f * dp, fill);
        c.drawText("Zombies", chart.left + 106 * dp, chart.top + 20 * dp, text);

        int t = (int) world.time;
        String[][] totals = {
                {"Time", String.format("%d:%02d", t / 60, t % 60)},
                {"People now", String.valueOf(world.humanCount() + world.hiding + world.riding)},
                {"Zombies now", String.valueOf(world.zombieCount())},
                {"Most zombies at once", String.valueOf(world.peakZombies)},
                {"Zombies killed", String.valueOf(world.zombiesKilled)},
                {"People turned", String.valueOf(world.turned)},
                {"Civilians lost", String.valueOf(world.civiliansLost)},
                {"Infections cured", String.valueOf(world.cured)},
                {"911 calls", String.valueOf(world.dispatch.calls)},
                {"Shots fired", String.valueOf(world.shotsFired)},
                {"City code", world.city.cfg.code()},
        };
        float tx0 = landscape ? chart.right + 24 * dp : side, tx1 = w - side;
        float ty = landscape ? top + 26 * dp : chart.bottom + 30 * dp, lh = Math.min(26 * dp, (h - ty - 10 * dp) / totals.length);
        plain.setTextSize(14 * dp);
        for (String[] row : totals) {
            plain.setTextAlign(Paint.Align.LEFT);
            plain.setColor(0xFFB8BDC4);
            c.drawText(row[0], tx0, ty, plain);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTextSize(14 * dp);
            text.setColor(0xFFFFFFFF);
            c.drawText(row[1], tx1, ty, text);
            ty += lh;
        }
    }

    /** The How to Play cards. */
    private void drawTutorial(Canvas c, int w, int h) {
        fill.setColor(0xC0000000);
        c.drawRect(0, 0, w, h, fill);
        float cw = Math.min(460 * dp, w - 40 * dp), ch = Math.min(340 * dp, h - 40 * dp);
        tmp.set((w - cw) / 2, (h - ch) / 2, (w + cw) / 2, (h + ch) / 2);
        fill.setColor(0xF5181A1E);
        c.drawRoundRect(tmp, 16 * dp, 16 * dp, fill);
        stroke.setStrokeWidth(1.5f * dp);
        stroke.setColor(0xFF3F8A3A);
        c.drawRoundRect(tmp, 16 * dp, 16 * dp, stroke);
        String[] page = TUTORIAL_PAGES[tutorialPage];
        plain.setTextSize(12 * dp);
        plain.setTextAlign(Paint.Align.LEFT);
        plain.setColor(0xFF8A9099);
        c.drawText((tutorialPage + 1) + " / " + TUTORIAL_PAGES.length, tmp.left + 20 * dp, tmp.top + 28 * dp, plain);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(21 * dp);
        text.setColor(0xFF8BD450);
        c.drawText(page[0], tmp.left + 20 * dp, tmp.top + 58 * dp, text);
        plain.setTextSize(15 * dp);
        plain.setColor(0xFFE0E3E8);
        float y = tmp.top + 90 * dp;
        for (String line : wrap(page[1], cw - 40 * dp, plain)) {
            c.drawText(line, tmp.left + 20 * dp, y, plain);
            y += 22 * dp;
        }
        float bw = (cw - 60 * dp) / 2, bh = 46 * dp, by = tmp.bottom - bh - 18 * dp;
        boolean last = tutorialPage == TUTORIAL_PAGES.length - 1;
        button(tutorialPage == 0 ? "Skip" : "Back", tutorialPage == 0 ? A_TUT_DONE : A_TUT_PREV, false,
                tmp.left + 20 * dp, by, tmp.left + 20 * dp + bw, by + bh);
        button(last ? "Start playing" : "Next", last ? A_TUT_DONE : A_TUT_NEXT, true,
                tmp.right - 20 * dp - bw, by, tmp.right - 20 * dp, by + bh);
    }

    private float header(Canvas c, int w, int h, String title) {
        fill.setColor(0xF2121418);
        c.drawRect(0, 0, w, h, fill);
        float top = 12 * dp, hh = 42 * dp;
        button("< Back", A_BACK, false, 12 * dp, top, 12 * dp + 96 * dp, top + hh);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(22 * dp);
        text.setColor(0xFF8BD450);
        c.drawText(title, w / 2f, top + hh / 2 + 8 * dp, text);
        return top + hh + 12 * dp;
    }

    private void drawOptions(Canvas c, int w, int h, String title, OptionSet opts, boolean setup) {
        float top = header(c, w, h, title);
        float footer = setup ? 72 * dp : 12 * dp;
        listArea.set(0, top, w, h - footer);
        int cols = w >= 700 * dp ? 2 : 1;
        float gap = 10 * dp, side = 16 * dp, rowH = 50 * dp;
        float colW = (w - side * 2 - gap * (cols - 1)) / cols;
        int n = opts.count();
        int perCol = (n + cols - 1) / cols;
        contentHeight = perCol * (rowH + gap);
        clampScroll();

        c.save();
        c.clipRect(listArea);
        for (int i = 0; i < n; i++) {
            int col = i / perCol, row = i % perCol;
            float x = side + col * (colW + gap), y = top + row * (rowH + gap) - scroll;
            Row r = new Row();
            r.index = i;
            r.r.set(x, y, x + colW, y + rowH);
            float vw = Math.min(190 * dp, colW * 0.55f);
            r.prev.set(r.r.right - vw, y, r.r.right - vw + 44 * dp, y + rowH);
            rows.add(r);

            fill.setColor(i == 0 && setup ? 0xE0233322 : 0xE0202328);
            c.drawRoundRect(r.r, 10 * dp, 10 * dp, fill);
            plain.setTextSize(15 * dp);
            plain.setTextAlign(Paint.Align.LEFT);
            plain.setColor(0xFFDADDE2);
            c.drawText(opts.label(i), x + 14 * dp, y + rowH / 2 + 5 * dp, plain);

            tmp.set(r.r.right - vw, y + 7 * dp, r.r.right - 7 * dp, y + rowH - 7 * dp);
            fill.setColor(0xFF15171B);
            c.drawRoundRect(tmp, 8 * dp, 8 * dp, fill);
            text.setTextSize(14 * dp);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(0xFF8BD450);
            c.drawText("<", tmp.left + 16 * dp, tmp.centerY() + 5 * dp, text);
            c.drawText(">", tmp.right - 16 * dp, tmp.centerY() + 5 * dp, text);
            text.setColor(0xFFFFFFFF);
            c.drawText(opts.values(i)[opts.get(i)], tmp.centerX(), tmp.centerY() + 5 * dp, text);
        }
        c.restore();
        drawScrollHint(c, w);

        if (setup) {
            float bgap = 8 * dp, bw = Math.min(180 * dp, (w - 32 * dp - bgap * 2) / 3), bh = 50 * dp, by = h - footer + 11 * dp;
            float x = (w - bw * 3 - bgap * 2) / 2;
            button("Randomize", A_RANDOM, false, x, by, x + bw, by + bh);
            button(config.keepCity ? config.code() : "City code", A_CODE, false, x + bw + bgap, by, x + bw * 2 + bgap, by + bh);
            button("Start", A_START, true, x + bw * 2 + bgap * 2, by, x + bw * 3 + bgap * 2, by + bh);
            if (noteTime > 0 && note != null) {
                noteTime -= 1 / 60f;
                fill.setColor(0xF0121418);
                c.drawRect(0, by - 30 * dp, w, by - 4 * dp, fill);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(13 * dp);
                text.setColor(0xFFFFE27A);
                c.drawText(note, w / 2f, by - 12 * dp, text);
            }
        }
    }

    private void drawNotes(Canvas c, int w, int h) {
        float top = header(c, w, h, "Patch Notes");
        listArea.set(0, top, w, h - 12 * dp);
        float side = 20 * dp, maxW = Math.min(w - side * 2, 640 * dp), x0 = (w - maxW) / 2;
        c.save();
        c.clipRect(listArea);
        float y = top + 10 * dp - scroll;
        for (String[] entry : PatchNotes.NOTES) {
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(17 * dp);
            text.setColor(0xFF8BD450);
            c.drawText(entry[0], x0, y + 18 * dp, text);
            y += 32 * dp;
            plain.setTextSize(14.5f * dp);
            plain.setTextAlign(Paint.Align.LEFT);
            for (int i = 1; i < entry.length; i++) {
                ArrayList<String> lines = wrap(entry[i], maxW - 18 * dp, plain);
                fill.setColor(0xFF8BD450);
                c.drawCircle(x0 + 5 * dp, y + 10 * dp, 2.5f * dp, fill);
                plain.setColor(0xFFDADDE2);
                for (String line : lines) {
                    c.drawText(line, x0 + 18 * dp, y + 15 * dp, plain);
                    y += 21 * dp;
                }
                y += 6 * dp;
            }
            y += 18 * dp;
        }
        c.restore();
        contentHeight = y + scroll - top;
        clampScroll();
        drawScrollHint(c, w);
    }

    /** Shows a "more below" arrow when the list can scroll further down. */
    private void drawScrollHint(Canvas c, int w) {
        float max = contentHeight - listArea.height() + 12 * dp;
        if (scroll >= max - 2 * dp) return;
        float cx = w / 2f, y = listArea.bottom - 8 * dp;
        fill.setColor(0xF2121418);
        c.drawRect(0, y - 26 * dp, w, listArea.bottom, fill);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(13 * dp);
        text.setColor(0xFF8BD450);
        c.drawText("scroll for more", cx, y - 7.5f * dp, text);
    }

    private static ArrayList<String> wrap(String s, float maxW, Paint p) {
        ArrayList<String> lines = new ArrayList<String>();
        String line = "";
        for (String word : s.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && p.measureText(next) > maxW) {
                lines.add(line);
                line = word;
            } else {
                line = next;
            }
        }
        lines.add(line);
        return lines;
    }

    private void clampScroll() {
        float max = Math.max(0, contentHeight - listArea.height() + 12 * dp);
        scroll = Math.max(0, Math.min(max, scroll));
    }

    // ------------------------------------------------------------------ input

    boolean onTouch(MotionEvent ev) {
        float x = ev.getX(), y = ev.getY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                downX = x;
                downY = y;
                lastY = y;
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(y - downY) > 10 * dp && listArea.contains(downX, downY)
                        && (screen == SETUP || screen == SETTINGS || screen == NOTES)) dragging = true;
                if (dragging) {
                    scroll -= y - lastY;
                    clampScroll();
                }
                lastY = y;
                break;
            case MotionEvent.ACTION_UP:
                // Ignore the lift of a finger that went down before the menu opened.
                if (pressed && !dragging) tap(x, y);
                dragging = false;
                pressed = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                pressed = false;
                break;
        }
        return true;
    }

    private void tap(float x, float y) {
        for (Button b : buttons) {
            if (b.r.contains(x, y)) {
                host.click();
                act(b.action);
                return;
            }
        }
        if (!listArea.contains(x, y)) return;
        OptionSet opts = screen == SETUP ? config : screen == SETTINGS ? settings : null;
        if (opts == null) return;
        for (Row r : rows) {
            if (!r.r.contains(x, y)) continue;
            int count = opts.values(r.index).length;
            int dir = r.prev.contains(x, y) ? -1 : 1;
            opts.set(r.index, (opts.get(r.index) + dir + count) % count);
            host.click();
            if (screen == SETTINGS) host.settingsChanged();
            return;
        }
    }

    private void act(int action) {
        switch (action) {
            case A_CONTINUE:
            case A_RESUME:
                host.continueGame();
                break;
            case A_NEW:
                open(SETUP);
                break;
            case A_SETTINGS:
                open(SETTINGS);
                break;
            case A_NOTES:
                open(NOTES);
                break;
            case A_QUIT:
                host.quit();
                break;
            case A_MAIN_MENU:
                host.toMainMenu();
                break;
            case A_BACK:
                back();
                break;
            case A_RANDOM:
                config.randomize(rnd);
                break;
            case A_CODE:
                host.askCityCode(config.code());
                break;
            case A_START:
                if (!config.keepCity) config.newSeed(rnd);
                config.keepCity = false;
                host.startGame(config);
                if (!settings.tutorialDone()) open(TUTORIAL);
                break;
            case A_SAVE:
                host.world().say(host.saveGame() ? "Game saved" : "Could not save the game");
                host.continueGame();
                break;
            case A_STATS:
                open(STATS);
                break;
            case A_HOW_TO:
                open(TUTORIAL);
                break;
            case A_TUT_NEXT:
                tutorialPage = Math.min(TUTORIAL_PAGES.length - 1, tutorialPage + 1);
                break;
            case A_TUT_PREV:
                tutorialPage = Math.max(0, tutorialPage - 1);
                break;
            case A_TUT_DONE:
                settings.markTutorialDone();
                screen = returnTo;
                break;
        }
    }
}
