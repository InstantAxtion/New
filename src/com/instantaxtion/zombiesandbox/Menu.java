package com.instantaxtion.zombiesandbox;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
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

        /** Saves a picture of the city and offers to share it. */
        void screenshot();

        /** Save slots 1-3 (0 is the autosave): save, load, and a short description (null if empty). */
        boolean saveSlot(int slot);

        boolean loadSlot(int slot);

        String slotInfo(int slot);

        /** Moves the camera to a spot. */
        void jumpTo(float x, float y);

        /** Hides the buttons and panels until the next tap. */
        void hideHud();

        /** Asks the player to type a city code (shows the current one to copy or edit). */
        void askCityCode(String current);
    }

    static final int NONE = 0, MAIN = 1, SETUP = 2, SETTINGS = 3, NOTES = 4, PAUSE = 5, STATS = 6, TUTORIAL = 7, RECORDS = 8,
            SAVES = 9, LOG = 10, REPLAY = 11;
    /** The save slot screen: saving (from the pause menu) or loading (from the main menu). */
    private boolean savingMode;
    Records records;

    private static final int A_CONTINUE = 0, A_NEW = 1, A_SETTINGS = 2, A_NOTES = 3, A_QUIT = 4, A_RESUME = 5,
            A_MAIN_MENU = 6, A_BACK = 7, A_START = 8, A_RANDOM = 9, A_SAVE = 10, A_STATS = 11, A_HOW_TO = 12,
            A_TUT_NEXT = 13, A_TUT_PREV = 14, A_TUT_DONE = 15, A_CODE = 16, A_REROLL = 17, A_SHOT = 18, A_RECORDS = 19, A_SAVES = 20, A_LOAD = 21, A_LOG = 22, A_HUD = 23,
            A_REPLAY = 24, A_REPLAY_PLAY = 25, A_SLOT = 100, A_LOG_ROW = 200;

    private static final String[][] TUTORIAL_PAGES = {
            {"Welcome to Zombie City", "A sandbox: set up a city, start an outbreak and watch what happens. There are no missions. Play however you like."},
            {"Spawning", "Pick a unit in the bottom bar and tap the city. Drag to paint a line of them, or use Brush (top) to spawn 5 or 10 at once. Buttons with a dot open a picker: People (civilians, medics, firefighters, dogs, raiders), Police, Military (soldiers, snipers, gunners, the National Guard) and Zombies. Tap a choice, then tap the city."},
            {"Looking around", "Pinch to zoom and drag with two fingers. With Move selected you can drag with one finger, and tap anyone to follow them with the camera. Press Take control on their card to play them yourself: left thumb to move (push further to run), the red button to shoot, bite or shove. Zoom right in to see through roofs. The View button switches between 3D and bird's-eye."},
            {"The city fights back", "Civilians call 911, the police respond by car, and the military is called in when it gets bad. The radio feed shows what they say. Tap a message to jump there."},
            {"Safe zones and hiding", "Police and soldiers pick a spot well away from the dead and put up a sandbag line; the zone opens once it's built. Its border grows as people arrive and guards join, follows the streets, and pulls back where the dead push. Others barricade themselves in buildings until zombies break the door down. Where and when zones go up is up to them."},
            {"Medics, ammo and reserves", "Medics heal the hurt (a bite can't be treated). Ammo runs out, and units go back to the station or base to resupply. Backup, army squads and helicopter support are limited."},
            {"Pausing and saving", "Menu (top right) pauses the game, saves it and shows the stats. The game also saves itself when you leave the app."},
    };
    private int tutorialPage;

    private static final class Button {
        final RectF r = new RectF();
        String label;
        int action;
        boolean primary, invisible;
    }

    /** One tappable choice on the New Game screen: an option set to a value. */
    private static final class Seg {
        final RectF r = new RectF();
        int option, value;
    }

    private final ArrayList<Seg> segs = new ArrayList<Seg>();
    private final ArrayList<Seg> segPool = new ArrayList<Seg>();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect bmpSrc = new Rect();
    /** A small picture of the city about to be played, built in the background whenever it changes. */
    private volatile Bitmap preview;
    private volatile String previewKey, buildingKey;
    /** How many people the previewed city's homes house. */
    private volatile int previewResidents;

    private static final class Row {
        final RectF r = new RectF(), prev = new RectF();
        int index;
    }

    int screen = MAIN;
    private int returnTo = MAIN;
    final CityConfig config = new CityConfig();

    private final Host host;
    private final Settings settings;
    private float dp;
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

    void setDp(float dp) {
        this.dp = dp;
    }

    boolean isOpen() {
        return screen != NONE;
    }

    /** True while the city behind the menu should keep simulating (not when the game is paused). */
    boolean liveBackground() {
        return screen == MAIN || (screen != NONE && screen != PAUSE && returnTo == MAIN);
    }

    void open(int s) {
        if (s == SETUP || s == SETTINGS || s == NOTES || s == STATS || s == TUTORIAL || s == RECORDS || s == SAVES || s == LOG) {
            if (screen == MAIN || screen == PAUSE || screen == NONE) returnTo = screen;
        } else {
            returnTo = MAIN;
        }
        screen = s;
        scroll = 0;
        if (s == NOTES) settings.markNotesRead();
        if (s == SETUP && !config.keepCity) config.newSeed(rnd);
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
            case REPLAY:
                screen = STATS;
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
        segPool.addAll(segs);
        segs.clear();
        rows.clear();
        switch (screen) {
            case MAIN: drawMain(c, w, h); break;
            case PAUSE: drawPause(c, w, h); break;
            case SETUP: drawSetup(c, w, h); break;
            case SETTINGS: drawOptions(c, w, h, "Settings", settings, false); break;
            case NOTES: drawNotes(c, w, h); break;
            case STATS: drawStats(c, w, h); break;
            case TUTORIAL: drawTutorial(c, w, h); break;
            case RECORDS: drawRecords(c, w, h); break;
            case SAVES: drawSaves(c, w, h); break;
            case LOG: drawLog(c, w, h); break;
            case REPLAY: drawReplay(c, w, h, dt); break;
        }
        for (Button b : buttons) drawButton(c, b);
    }

    private Button button(String label, int action, boolean primary, float l, float t, float r, float b) {
        Button btn = pool.isEmpty() ? new Button() : pool.remove(pool.size() - 1);
        btn.label = label;
        btn.action = action;
        btn.primary = primary;
        btn.invisible = false;
        btn.r.set(l, t, r, b);
        buttons.add(btn);
        return btn;
    }

    private void drawButton(Canvas c, Button b) {
        if (b.invisible) return;
        fill.setColor(b.primary ? 0xFF3F8A3A : 0xE0262A30);
        c.drawRoundRect(b.r, 12 * dp, 12 * dp, fill);
        stroke.setStrokeWidth(1.5f * dp);
        stroke.setColor(b.primary ? 0xFF9BE08A : 0x50FFFFFF);
        c.drawRoundRect(b.r, 12 * dp, 12 * dp, stroke);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Math.min(18 * dp, b.r.height() * 0.38f));
        float fit = text.measureText(b.label);
        if (fit > b.r.width() - 16 * dp) text.setTextSize(text.getTextSize() * (b.r.width() - 16 * dp) / fit);
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
        boolean slots = host.slotInfo(1) != null || host.slotInfo(2) != null || host.slotInfo(3) != null;
        int n = (game ? 7 : 6) + (slots ? 1 : 0);
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
            by = h * 0.38f;
            bh = Math.min(bh, (h * 0.6f - gap * (n - 1)) / n);
        }
        if (game) {
            button("Continue", A_CONTINUE, true, bx, by, bx + bw, by + bh);
            by += bh + gap;
        }
        button("New Game", A_NEW, !game, bx, by, bx + bw, by + bh);
        by += bh + gap;
        if (slots) {
            button("Load Game", A_LOAD, false, bx, by, bx + bw, by + bh);
            by += bh + gap;
        }
        button("How to Play", A_HOW_TO, false, bx, by, bx + bw, by + bh);
        by += bh + gap;
        button("Settings", A_SETTINGS, false, bx, by, bx + bw, by + bh);
        by += bh + gap;
        button("Records & Achievements", A_RECORDS, false, bx, by, bx + bw, by + bh);
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
        String[] labels = {"Resume", "Save / Load", "Screenshot", "Radio Log", "Hide Buttons", "Stats", "New Game", "Settings",
                "Main Menu"};
        int[] actions = {A_RESUME, A_SAVES, A_SHOT, A_LOG, A_HUD, A_STATS, A_NEW, A_SETTINGS, A_MAIN_MENU};
        int cols = w > h ? 2 : 1, rowsN = (labels.length + cols - 1) / cols;
        float gap = 7 * dp, bh = Math.min(46 * dp, (h - 120 * dp - gap * (rowsN - 1)) / rowsN);
        float colW = Math.min(300 * dp, (w - 60 * dp) / cols);
        float pw = colW * cols + gap * (cols - 1) + 40 * dp, ph = 76 * dp + bh * rowsN + gap * (rowsN - 1) + 20 * dp;
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
        World world = host.world();
        c.drawText(world.city.name + "  -  city code " + world.city.cfg.code(), w / 2f, tmp.top + 58 * dp, plain);
        float top = tmp.top + 72 * dp;
        for (int i = 0; i < labels.length; i++) {
            int col = cols == 2 ? i % 2 : 0, row = cols == 2 ? i / 2 : i;
            float bx = tmp.left + 20 * dp + col * (colW + gap), by = top + row * (bh + gap);
            button(labels[i], actions[i], i == 0, bx, by, bx + colW, by + bh);
        }
    }

    /** The three save slots (and the autosave when loading). */
    private void drawSaves(Canvas c, int w, int h) {
        float top = header(c, w, h, savingMode ? "Save Game" : "Load Game");
        listArea.set(0, top, w, h - 12 * dp);
        float side = 20 * dp, maxW = Math.min(w - side * 2, 560 * dp), x0 = (w - maxW) / 2;
        float y = top + 10 * dp - scroll;
        int first = savingMode ? 1 : 0;
        for (int slot = first; slot <= 3; slot++) {
            String info = host.slotInfo(slot);
            if (slot == 0 && info == null) continue;
            tmp.set(x0, y, x0 + maxW, y + 86 * dp);
            fill.setColor(0xE0202328);
            c.drawRoundRect(tmp, 12 * dp, 12 * dp, fill);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(16 * dp);
            text.setColor(0xFF8BD450);
            c.drawText(slot == 0 ? "Autosave" : "Slot " + slot, x0 + 14 * dp, y + 26 * dp, text);
            plain.setTextAlign(Paint.Align.LEFT);
            plain.setTextSize(12.5f * dp);
            plain.setColor(0xFFC8CCD2);
            String[] lines = info == null ? new String[]{"Empty"} : info.split("\n");
            for (int k = 0; k < lines.length && k < 3; k++) c.drawText(lines[k], x0 + 14 * dp, y + 46 * dp + k * 15 * dp, plain);
            boolean can = savingMode || info != null;
            if (can) button(savingMode ? (info == null ? "Save" : "Overwrite") : "Load", A_SLOT + slot, true,
                    x0 + maxW - 120 * dp, y + 22 * dp, x0 + maxW - 14 * dp, y + 64 * dp);
            y += 96 * dp;
        }
        contentHeight = y + scroll - top;
        clampScroll();
    }

    /** Every radio message this game, newest first. Tap one to go there. */
    private void drawLog(Canvas c, int w, int h) {
        float top = header(c, w, h, "Radio Log");
        listArea.set(0, top, w, h - 12 * dp);
        float side = 16 * dp, maxW = Math.min(w - side * 2, 720 * dp), x0 = (w - maxW) / 2;
        c.save();
        c.clipRect(listArea);
        float y = top + 8 * dp - scroll;
        java.util.ArrayList<Dispatch.Message> log = host.world().dispatch.log;
        plain.setTextSize(13 * dp);
        text.setTextSize(13 * dp);
        for (int i = log.size() - 1; i >= 0; i--) {
            Dispatch.Message m = log.get(i);
            text.setTextAlign(Paint.Align.LEFT);
            float whoW = text.measureText(m.who + ": ");
            ArrayList<String> lines = wrap(m.text, maxW - whoW - 24 * dp, plain);
            float rh = 12 * dp + lines.size() * 18 * dp;
            if (y + rh > top && y < h) {
                tmp.set(x0, y, x0 + maxW, y + rh);
                fill.setColor(0xE0202328);
                c.drawRoundRect(tmp, 8 * dp, 8 * dp, fill);
                text.setColor(m.color);
                c.drawText(m.who + ": ", x0 + 10 * dp, y + 20 * dp, text);
                plain.setTextAlign(Paint.Align.LEFT);
                plain.setColor(0xFFE6E6E6);
                for (int k = 0; k < lines.size(); k++) c.drawText(lines.get(k), x0 + 10 * dp + whoW, y + 20 * dp + k * 18 * dp, plain);
                // The whole row is a button (drawn invisibly by keeping it out of the button list's paint).
                Button b = button("", A_LOG_ROW + (log.size() - 1 - i), false, x0, Math.max(y, top), x0 + maxW, y + rh);
                b.invisible = true;
            }
            y += rh + 6 * dp;
        }
        c.restore();
        if (log.isEmpty()) {
            plain.setTextAlign(Paint.Align.CENTER);
            plain.setColor(0xFF9AA0A8);
            c.drawText("Nothing on the radio yet.", w / 2f, top + 40 * dp, plain);
        }
        contentHeight = y + scroll - top;
        clampScroll();
        drawScrollHint(c, w);
    }

    /** Humans vs zombies over time (and police and army), plus the totals and heroes of this game. */
    /** What's been found out about the strain so far, with a "?" for each trait still unknown. */
    private static String strainTraits(World world) {
        StringBuilder sb = new StringBuilder();
        int hidden = 0;
        for (int t = 0; t < World.TR_COUNT; t++) {
            if (!world.trait[t]) continue;
            if (!world.traitKnown[t]) {
                hidden++;
                continue;
            }
            if (sb.length() > 0) sb.append(", ");
            sb.append(World.TRAIT_NAMES[t]);
        }
        for (int i = 0; i < hidden; i++) sb.append(sb.length() > 0 ? ", ?" : "?");
        return sb.length() == 0 ? "Nothing unusual" : sb.toString();
    }

    private void drawStats(Canvas c, int w, int h) {
        float top = header(c, w, h, "Stats");
        World world = host.world();
        button("Replay", A_REPLAY, true, w - 12 * dp - 110 * dp, 12 * dp, w - 12 * dp, 12 * dp + 42 * dp);
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
            int[][] series = {world.histHumans, world.histZombies, world.histArmed};
            int[] colors = {0xFF6FA8FF, 0xFF7CC24E, 0xFFE8C547};
            for (int sIdx = 0; sIdx < 3; sIdx++) {
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
        fill.setColor(0xFFE8C547);
        c.drawCircle(chart.left + 186 * dp, chart.top + 16 * dp, 4.5f * dp, fill);
        c.drawText("Police & army", chart.left + 196 * dp, chart.top + 20 * dp, text);

        int t = (int) world.time;
        String[][] totals = {
                {"Time", String.format("%d:%02d", t / 60, t % 60)},
                {"People now", String.valueOf(world.humanCount() + world.hiding + world.riding + world.visiting)},
                {"Zombies now", String.valueOf(world.zombieCount())},
                {"Most zombies at once", String.valueOf(world.peakZombies)},
                {"Zombies killed", String.valueOf(world.zombiesKilled)},
                {"People turned", String.valueOf(world.turned)},
                {"Civilians lost", String.valueOf(world.civiliansLost)},
                {"People patched up by medics", String.valueOf(world.healed)},
                {"911 calls", String.valueOf(world.dispatch.calls)},
                {"Shots fired", String.valueOf(world.shotsFired)},
                {"Volunteers joined (said no)", world.recruits + " (" + world.refused + ")"},
                {"City", world.city.name + "  (" + world.city.cfg.code() + ")"},
                {"Bitten people turned away at checkpoints", String.valueOf(world.turnedAway)},
                {"Outbreak started", world.outbreakPlace == null ? "-" : world.outbreakPlace},
                {"Infection chain", chainText(world)},
                {world.strainName, strainTraits(world)},
                {"Balance of power", world.warResult == 1 ? "The city survived" : world.warResult == 2 ? "The city fell"
                        : "People " + (int) (world.warBalance * 100) + "%"},
        };
        java.util.ArrayList<int[]> heroes = world.heroes(3);
        String[][] withHeroes = new String[totals.length + heroes.size()][];
        System.arraycopy(totals, 0, withHeroes, 0, totals.length);
        for (int i = 0; i < heroes.size(); i++) {
            int[] hr = heroes.get(i);
            String who = hr[1] == Entity.DOG ? Names.dog(hr[0]) : Names.person(hr[0]);
            withHeroes[totals.length + i] = new String[]{(i == 0 ? "Top killer: " : "   ") + who + (hr[4] == 0 ? " (fallen)" : ""),
                    hr[3] + " kills"};
        }
        totals = withHeroes;
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

    // ------------------------------------------------------------------ outbreak replay

    /** Where the replay is (0 to 1), whether it's playing, and the timeline's touch area. */
    private float replayPos;
    private boolean replayPlaying;
    private final RectF replayBar = new RectF(), replayMap = new RectF();
    /** A full replay lasts this many seconds, however long the game was. */
    private static final float REPLAY_SECONDS = 24;

    private static String chainText(World w) {
        if (w.patientZeroSeed == 0) return "-";
        int[] ch = w.chainFromPatientZero();
        Integer most = null;
        int mostN = 0;
        for (java.util.Map.Entry<Integer, Integer> en : w.victims.entrySet())
            if (en.getValue() > mostN) {
                mostN = en.getValue();
                most = en.getKey();
            }
        return "Patient zero " + Names.person(w.patientZeroSeed) + ": " + ch[0] + " people, " + ch[1] + " generations"
                + (most != null ? ". Worst: " + Names.person(most) + " (" + mostN + ")" : "");
    }

    private void scrubReplay(float x) {
        replayPos = Math.max(0, Math.min(1, (x - replayBar.left) / replayBar.width()));
        replayPlaying = false;
        // Snap to a big moment if one is close.
        World w = host.world();
        if (w.replayMeta.size() > 1) {
            float t0 = w.replayMeta.get(0)[0], t1 = w.replayMeta.get(w.replayMeta.size() - 1)[0];
            float best = 14 * dp;
            for (float[] h : w.highlightAt) {
                if (h[0] < t0 || h[0] > t1) continue;
                float hx = replayBar.left + replayBar.width() * (h[0] - t0) / (t1 - t0);
                if (Math.abs(hx - x) < best) {
                    best = Math.abs(hx - x);
                    replayPos = (h[0] - t0) / (t1 - t0);
                }
            }
        }
    }

    /**
     * The whole game played back on the city map: people in blue, the dead in green, and a red mark everywhere
     * someone turned, building up as the infection spreads.
     */
    private void drawReplay(Canvas c, int w, int h, float dt) {
        float top = header(c, w, h, "Outbreak replay");
        World world = host.world();
        java.util.ArrayList<float[]> meta = world.replayMeta;
        int frames = meta.size();
        if (replayPlaying && frames > 1) {
            replayPos += dt / REPLAY_SECONDS;
            if (replayPos >= 1) {
                replayPos = 1;
                replayPlaying = false;
            }
        }
        float barH = 44 * dp, bottom = h - 16 * dp - barH - 56 * dp;
        float size = Math.min(w - 32 * dp, bottom - top);
        replayMap.set((w - size) / 2, top, (w + size) / 2, top + size);
        City city = world.city;
        fill.setColor(0xFF1C1F24);
        c.drawRect(replayMap, fill);
        bmpSrc.set(0, 0, city.bitmap.getWidth(), city.bitmap.getHeight());
        bmpPaint.setAlpha(150);
        c.drawBitmap(city.bitmap, bmpSrc, replayMap, bmpPaint);
        bmpPaint.setAlpha(255);
        if (frames == 0) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(15 * dp);
            text.setColor(0xFFE6E6E6);
            c.drawText("Play a little longer to see the replay.", replayMap.centerX(), replayMap.centerY(), text);
            return;
        }
        int f = Math.min(frames - 1, (int) (replayPos * (frames - 1) + 0.5f));
        float[] m = meta.get(f);
        short[] dots = world.replayDots.get(f);
        float sx = replayMap.width() / city.worldW(), sy = replayMap.height() / city.worldH();
        float r = Math.max(1.4f * dp, size / 260);
        // Where people turned, up to now: old marks fade.
        for (int i = 0, n = world.turnEvents.size(); i < n; i++) {
            float[] t = world.turnEvents.get(i);
            if (t[0] > m[0]) break;
            float age = m[0] - t[0];
            fill.setColor(age < 20 ? 0xFFFF4A3A : 0x80B03028);
            c.drawCircle(replayMap.left + t[1] * sx, replayMap.top + t[2] * sy, r * (age < 20 ? 2.2f : 1.3f), fill);
        }
        int zn = (int) m[3], k = 0;
        for (int i = 0; i + 1 < dots.length; i += 2, k++) {
            fill.setColor(k < zn ? 0xFF8BE05A : 0xFF6FA8FF);
            c.drawCircle(replayMap.left + dots[i] * sx, replayMap.top + dots[i + 1] * sy, r, fill);
        }
        // Time and counts.
        int secs = (int) m[0];
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(14 * dp);
        text.setColor(0xFFFFFFFF);
        float ly = replayMap.bottom + 22 * dp;
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(String.format("%d:%02d", secs / 60, secs % 60), replayMap.centerX(), ly, text);
        ly += 22 * dp;
        text.setTextSize(13 * dp);
        text.setTextAlign(Paint.Align.RIGHT);
        int turnedSoFar = 0;
        for (int i = 0, n = world.turnEvents.size(); i < n && world.turnEvents.get(i)[0] <= m[0]; i++) turnedSoFar++;
        text.setColor(0xFF6FA8FF);
        String counts = "People " + (int) m[2];
        float cx = (w + text.measureText(counts + "   Zombies " + (int) m[1] + "   Turned " + turnedSoFar)) / 2;
        c.drawText(counts, cx - text.measureText("   Zombies " + (int) m[1] + "   Turned " + turnedSoFar), ly, text);
        text.setColor(0xFF8BE05A);
        c.drawText("   Zombies " + (int) m[1], cx - text.measureText("   Turned " + turnedSoFar), ly, text);
        text.setColor(0xFFFF6A5A);
        c.drawText("   Turned " + turnedSoFar, cx, ly, text);
        // The timeline: drag it to scrub, the button plays and pauses.
        float bw = 90 * dp;
        float by = Math.min(h - 16 * dp - barH, ly + 18 * dp);
        button(replayPlaying ? "Pause" : replayPos >= 1 ? "Again" : "Play", A_REPLAY_PLAY, true, replayMap.left, by, replayMap.left + bw, by + barH);
        replayBar.set(replayMap.left + bw + 14 * dp, by, replayMap.right, by + barH);
        float mid = replayBar.centerY();
        fill.setColor(0xFF2A2E34);
        tmp.set(replayBar.left, mid - 4 * dp, replayBar.right, mid + 4 * dp);
        c.drawRoundRect(tmp, 4 * dp, 4 * dp, fill);
        fill.setColor(0xFF3F8A3A);
        tmp.set(replayBar.left, mid - 4 * dp, replayBar.left + replayBar.width() * replayPos, mid + 4 * dp);
        c.drawRoundRect(tmp, 4 * dp, 4 * dp, fill);
        // The big moments, as ticks on the timeline; tap near one to jump to it.
        float t0 = meta.get(0)[0], t1 = meta.get(frames - 1)[0];
        java.util.ArrayList<float[]> hl = world.highlightAt;
        String showing = null;
        for (int i = 0; i < hl.size(); i++) {
            float ht = hl.get(i)[0];
            if (ht < t0 || ht > t1 || t1 <= t0) continue;
            float hx = replayBar.left + replayBar.width() * (ht - t0) / (t1 - t0);
            fill.setColor(0xFFE8C547);
            c.drawRect(hx - 1.5f * dp, mid - 12 * dp, hx + 1.5f * dp, mid - 5 * dp, fill);
            if (Math.abs(ht - m[0]) < Math.max(6, (t1 - t0) / 60)) {
                showing = world.highlightText.get(i);
                float[] at = hl.get(i);
                stroke.setColor(0xFFE8C547);
                stroke.setStrokeWidth(2 * dp);
                c.drawCircle(replayMap.left + at[1] * sx, replayMap.top + at[2] * sy, 12 * dp, stroke);
            }
        }
        if (showing != null) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(14 * dp);
            float tw = text.measureText(showing) + 24 * dp;
            tmp.set(replayMap.centerX() - tw / 2, replayMap.top + 8 * dp, replayMap.centerX() + tw / 2, replayMap.top + 34 * dp);
            fill.setColor(0xE0101216);
            c.drawRoundRect(tmp, 8 * dp, 8 * dp, fill);
            text.setColor(0xFFE8C547);
            c.drawText(showing, replayMap.centerX(), replayMap.top + 26 * dp, text);
        }
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(replayBar.left + replayBar.width() * replayPos, mid, 10 * dp, fill);
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
        // Keep long titles clear of the Back button.
        float room = w - 2 * (12 * dp + 96 * dp + 8 * dp), fit = text.measureText(title);
        if (fit > room) text.setTextSize(22 * dp * room / fit);
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

    /** Best results across all games, and the achievements (locked ones greyed out). */
    private void drawRecords(Canvas c, int w, int h) {
        float top = header(c, w, h, "Records");
        listArea.set(0, top, w, h - 12 * dp);
        float side = 20 * dp, maxW = Math.min(w - side * 2, 640 * dp), x0 = (w - maxW) / 2;
        c.save();
        c.clipRect(listArea);
        float y = top + 10 * dp - scroll;
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(17 * dp);
        text.setColor(0xFF8BD450);
        c.drawText("Records", x0, y + 18 * dp, text);
        y += 34 * dp;
        for (int i = 0; i < Records.RECORD_NAMES.length; i++) {
            plain.setTextAlign(Paint.Align.LEFT);
            plain.setTextSize(14.5f * dp);
            plain.setColor(0xFFB8BDC4);
            c.drawText(Records.RECORD_NAMES[i], x0, y + 14 * dp, plain);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTextSize(14.5f * dp);
            text.setColor(0xFFFFFFFF);
            c.drawText(records.recordText(i), x0 + maxW, y + 14 * dp, text);
            y += 26 * dp;
        }
        y += 14 * dp;
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(17 * dp);
        text.setColor(0xFF8BD450);
        c.drawText("Achievements  (" + records.unlockedCount() + "/" + Records.ACHIEVEMENTS.length + ")", x0, y + 18 * dp, text);
        y += 34 * dp;
        for (int i = 0; i < Records.ACHIEVEMENTS.length; i++) {
            boolean got = records.unlocked(i);
            plain.setTextSize(12.5f * dp);
            ArrayList<String> desc = wrap(Records.ACHIEVEMENTS[i][1], maxW - 56 * dp, plain);
            float ih = 36 * dp + desc.size() * 16 * dp;
            tmp.set(x0, y, x0 + maxW, y + ih);
            fill.setColor(got ? 0xE0233322 : 0xE0202328);
            c.drawRoundRect(tmp, 10 * dp, 10 * dp, fill);
            fill.setColor(got ? 0xFF8BD450 : 0xFF4A4E56);
            c.drawCircle(x0 + 24 * dp, y + ih / 2, 11 * dp, fill);
            if (got) {
                stroke.setColor(0xFF0F2A0C);
                stroke.setStrokeWidth(2.5f * dp);
                c.drawLine(x0 + 18 * dp, y + ih / 2, x0 + 23 * dp, y + ih / 2 + 5 * dp, stroke);
                c.drawLine(x0 + 23 * dp, y + ih / 2 + 5 * dp, x0 + 31 * dp, y + ih / 2 - 5 * dp, stroke);
            }
            text.setTextSize(15 * dp);
            text.setColor(got ? 0xFFFFFFFF : 0xFF9AA0A8);
            c.drawText(Records.ACHIEVEMENTS[i][0], x0 + 46 * dp, y + 22 * dp, text);
            plain.setColor(got ? 0xFFC8CCD2 : 0xFF7A8088);
            for (int k = 0; k < desc.size(); k++) c.drawText(desc.get(k), x0 + 46 * dp, y + 41 * dp + k * 16 * dp, plain);
            y += ih + 8 * dp;
        }
        c.restore();
        contentHeight = y + scroll - top;
        clampScroll();
        drawScrollHint(c, w);
    }

    // ------------------------------------------------------------------ New Game

    private Seg seg(int option, int value, float l, float t, float r, float b) {
        Seg sg = segPool.isEmpty() ? new Seg() : segPool.remove(segPool.size() - 1);
        sg.option = option;
        sg.value = value;
        sg.r.set(l, t, r, b);
        segs.add(sg);
        return sg;
    }

    /** Starts building the preview picture if the city changed since the last one. */
    private void updatePreview() {
        final String key = config.code();
        if (key.equals(previewKey) || key.equals(buildingKey)) return;
        buildingKey = key;
        final CityConfig copy = new CityConfig();
        System.arraycopy(config.v, 0, copy.v, 0, copy.v.length);
        copy.seed = config.seed;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    City city = new City(copy, 360f / (copy.tiles() * City.T));
                    if (key.equals(buildingKey)) {
                        preview = city.bitmap;
                        previewResidents = city.totalResidents;
                        previewKey = key;
                    }
                } catch (Throwable ignored) {
                    // No preview; the game itself still works.
                }
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** A row of equal buttons, one per value, with the chosen one lit up. */
    private float segRow(Canvas c, int option, String label, float x, float y, float width) {
        plain.setTextAlign(Paint.Align.LEFT);
        plain.setTextSize(13 * dp);
        plain.setColor(0xFFB8BDC4);
        // Long labels wrap rather than run off a narrow screen.
        for (String line : wrap(label, width - 4 * dp, plain)) {
            c.drawText(line, x + 2 * dp, y + 14 * dp, plain);
            y += 17 * dp;
        }
        y += 5 * dp;
        String[] values = config.values(option);
        // (Choices that are no longer offered are left out, unless a loaded city code is using one.)
        int shown = 0;
        int[] index = new int[values.length];
        for (int i = 0; i < values.length; i++)
            if (!CityConfig.retired(option, i) || config.get(option) == i) index[shown++] = i;
        // A long list (the countries) goes over two rows rather than squeezing into one.
        int lines = shown > 12 ? 3 : shown > 6 ? 2 : 1;
        int perRow = (shown + lines - 1) / lines;
        int rows = (shown + perRow - 1) / perRow;
        float gap = 4 * dp, bh = 36 * dp, bw = (width - gap * (perRow - 1)) / perRow;
        for (int k = 0; k < shown; k++) {
            int i = index[k];
            float l = x + (k % perRow) * (bw + gap), t = y + (k / perRow) * (bh + gap);
            Seg sg = seg(option, i, l, t, l + bw, t + bh);
            boolean on = config.get(option) == i;
            fill.setColor(on ? 0xFF3F8A3A : 0xFF23262C);
            c.drawRoundRect(sg.r, 8 * dp, 8 * dp, fill);
            if (on) {
                stroke.setColor(0xFF9BE08A);
                stroke.setStrokeWidth(1.5f * dp);
                c.drawRoundRect(sg.r, 8 * dp, 8 * dp, stroke);
            }
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(Math.min(14 * dp, bw / Math.max(3, values[i].length()) * 1.5f));
            text.setColor(on ? 0xFFFFFFFF : 0xFFC8CCD2);
            c.drawText(values[i], sg.r.centerX(), sg.r.centerY() + text.getTextSize() * 0.36f, text);
        }
        return y + rows * bh + (rows - 1) * gap + 14 * dp;
    }

    private void drawSetup(Canvas c, int w, int h) {
        float top = header(c, w, h, "New Game");
        updatePreview();
        boolean landscape = w > h;
        float footer = 72 * dp, side = 16 * dp;
        // The city preview: left in landscape, on top in portrait.
        float pv = landscape ? Math.min(h - top - footer - 72 * dp, w * 0.34f) : Math.min(w - side * 2, (h - top) * 0.3f);
        float px = landscape ? side : (w - pv) / 2, py = top + 4 * dp;
        tmp.set(px - 3 * dp, py - 3 * dp, px + pv + 3 * dp, py + pv + 3 * dp);
        fill.setColor(0xFF2A2E34);
        c.drawRoundRect(tmp, 10 * dp, 10 * dp, fill);
        Bitmap bmp = preview;
        boolean current = config.code().equals(previewKey);
        if (bmp != null) {
            bmpSrc.set(0, 0, bmp.getWidth(), bmp.getHeight());
            tmp.set(px, py, px + pv, py + pv);
            bmpPaint.setAlpha(current ? 255 : 110);
            c.drawBitmap(bmp, bmpSrc, tmp, bmpPaint);
        }
        if (!current) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(14 * dp);
            text.setColor(0xFFE6E6E6);
            c.drawText("Building city...", px + pv / 2, py + pv / 2, text);
        }
        int preset = config.v[CityConfig.OPT_PRESET];
        float infoY = py + pv + 20 * dp;
        float infoW = landscape ? pv : w - side * 2, infoX = landscape ? px : side;
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(16 * dp);
        text.setColor(0xFF8BD450);
        c.drawText(CityConfig.PRESETS[preset], infoX, infoY, text);
        plain.setTextAlign(Paint.Align.RIGHT);
        plain.setTextSize(12 * dp);
        plain.setColor(0xFF9AA0A8);
        c.drawText("City " + config.code(), infoX + infoW, infoY, plain);
        float ly = infoY + 10 * dp;
        if (landscape) {
            button("New map", A_REROLL, false, px, ly, px + pv, ly + 34 * dp);
        } else {
            ly = drawInfo(c, preset, infoX, infoY + 18 * dp, infoW);
        }

        // The options, scrolling if they don't fit.
        float lx = landscape ? px + pv + 24 * dp : side, lw = w - lx - side;
        float listTop = landscape ? top : ly + 2 * dp;
        if (!landscape) {
            button("New map", A_REROLL, false, w - side - 110 * dp, py + pv - 36 * dp, w - side - 6 * dp, py + pv - 6 * dp);
        }
        listArea.set(0, listTop, w, h - footer);
        c.save();
        c.clipRect(listArea);
        float y = listTop + 4 * dp - scroll;
        if (landscape) y = drawInfo(c, preset, lx, y + 14 * dp, lw) + 4 * dp;
        // Map cards.
        plain.setTextAlign(Paint.Align.LEFT);
        plain.setTextSize(13 * dp);
        plain.setColor(0xFFB8BDC4);
        c.drawText("Map", lx + 2 * dp, y + 14 * dp, plain);
        y += 22 * dp;
        int perRow = lw > 520 * dp ? 5 : lw > 330 * dp ? 4 : 3;
        float gap = 6 * dp, cw = (lw - gap * (perRow - 1)) / perRow, ch = 40 * dp;
        // (Maps no longer offered are left out, unless a loaded city code is using one.)
        int cards = 0;
        int[] shown = new int[CityConfig.PRESETS.length];
        for (int i = 0; i < CityConfig.PRESETS.length; i++)
            if (!CityConfig.retired(CityConfig.OPT_PRESET, i) || preset == i) shown[cards++] = i;
        for (int k = 0; k < cards; k++) {
            int i = shown[k];
            float l = lx + (k % perRow) * (cw + gap), t = y + (k / perRow) * (ch + gap);
            Seg sg = seg(CityConfig.OPT_PRESET, i, l, t, l + cw, t + ch);
            boolean on = preset == i;
            fill.setColor(on ? 0xFF2F5E2B : 0xFF23262C);
            c.drawRoundRect(sg.r, 9 * dp, 9 * dp, fill);
            // A strip in the map's colour.
            fill.setColor(MAP_COLORS[i]);
            tmp.set(l, t, l + 5 * dp, t + ch);
            c.drawRoundRect(tmp, 3 * dp, 3 * dp, fill);
            if (on) {
                stroke.setColor(0xFF9BE08A);
                stroke.setStrokeWidth(1.5f * dp);
                c.drawRoundRect(sg.r, 9 * dp, 9 * dp, stroke);
            }
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(Math.min(13.5f * dp, cw / 7.5f));
            text.setColor(on ? 0xFFFFFFFF : 0xFFC8CCD2);
            c.drawText(CityConfig.PRESETS[i], l + cw / 2 + 2 * dp, t + ch / 2 + text.getTextSize() * 0.36f, text);
        }
        y += ((cards + perRow - 1) / perRow) * (ch + gap) + 12 * dp;
        if (preset == CityConfig.ISLANDS) {
            // Islands comes in one size only.
            plain.setTextAlign(Paint.Align.LEFT);
            plain.setTextSize(13 * dp);
            plain.setColor(0xFFB8BDC4);
            for (String line : wrap("Map size: Islands is always one size, " + CityConfig.ISLANDS_TILES
                    + " tiles across (bigger than Massive).", lw - 4 * dp, plain)) {
                c.drawText(line, lx + 2 * dp, y + 14 * dp, plain);
                y += 17 * dp;
            }
            y += 14 * dp;
        } else y = segRow(c, CityConfig.OPT_SIZE, "Map size", lx, y, lw);
        y = segRow(c, CityConfig.OPT_COUNTRY, "Country: how the city looks and what it's called", lx, y, lw);
        plain.setTextSize(12.5f * dp);
        plain.setColor(0xFF9AA0A8);
        y -= 6 * dp;
        for (String line : wrap(Country.INFO[config.v[CityConfig.OPT_COUNTRY]], lw - 4 * dp, plain)) {
            c.drawText(line, lx + 2 * dp, y + 10 * dp, plain);
            y += 16 * dp;
        }
        y += 12 * dp;
        int homes = previewResidents;
        boolean known = homes > 0 && config.code().equals(previewKey);
        y = segRow(c, CityConfig.OPT_CIVILIANS, known
                ? "People: how many of the " + homes + " residents are about (" + config.civilians(homes) + ")"
                : "People: how many residents are about", lx, y, lw);
        // Police and the army are set by the size of the city.
        plain.setTextSize(12.5f * dp);
        plain.setColor(0xFF9AA0A8);
        String force = known ? "Police and army are set by the city: " + config.cops(homes) + " officers on duty"
                + (config.soldiers(homes) > 0 ? " and " + config.soldiers(homes) + " soldiers at the base." : ", and no army base on this map.")
                : "Police and army are set by the size of the city.";
        for (String line : wrap(force, lw - 4 * dp, plain)) {
            c.drawText(line, lx + 2 * dp, y + 10 * dp, plain);
            y += 16 * dp;
        }
        y += 10 * dp;
        y = segRow(c, CityConfig.OPT_ZOMBIES, "Zombies", lx, y, lw);
        y = segRow(c, CityConfig.OPT_RESERVES, "Reinforcements: who comes to help once the fighting starts", lx, y, lw);
        plain.setTextSize(12.5f * dp);
        plain.setColor(0xFF9AA0A8);
        y -= 6 * dp;
        for (String line : wrap(CityConfig.RESERVE_INFO[config.v[CityConfig.OPT_RESERVES]], lw - 4 * dp, plain)) {
            c.drawText(line, lx + 2 * dp, y + 10 * dp, plain);
            y += 16 * dp;
        }
        y += 12 * dp;
        c.restore();
        contentHeight = y + scroll - listTop;
        clampScroll();
        drawScrollHint(c, w, landscape ? lx - 8 * dp : 0);

        float bgap = 8 * dp, bw = Math.min(180 * dp, (w - 32 * dp - bgap * 2) / 3), bh = 50 * dp, by = h - footer + 11 * dp;
        float x = (w - bw * 3 - bgap * 2) / 2;
        button("Randomize", A_RANDOM, false, x, by, x + bw, by + bh);
        button("City code", A_CODE, false, x + bw + bgap, by, x + bw * 2 + bgap, by + bh);
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

    /** The chosen map's description; returns the y below it. */
    private float drawInfo(Canvas c, int preset, float x, float y, float width) {
        plain.setTextAlign(Paint.Align.LEFT);
        plain.setTextSize(12.5f * dp);
        plain.setColor(0xFFC8CCD2);
        for (String line : wrap(CityConfig.PRESET_INFO[preset], width, plain)) {
            c.drawText(line, x, y, plain);
            y += 16 * dp;
        }
        return y;
    }

    private static final int[] MAP_COLORS = {0xFF8BD450, 0xFF6F8EC8, 0xFFE0A050, 0xFF9A9A90, 0xFF4CAF50, 0xFFB07050,
            0xFFD8C050, 0xFF50A0C0, 0xFFC050C0, 0xFF90B060, 0xFF3A9AD8, 0xFF2F6EA8, 0xFF48B0B0, 0xFF6A8098, 0xFF30B8D0};

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
        drawScrollHint(c, w, 0);
    }

    private void drawScrollHint(Canvas c, int w, float left) {
        float max = contentHeight - listArea.height() + 12 * dp;
        if (scroll >= max - 2 * dp) return;
        float cx = (left + w) / 2f, y = listArea.bottom - 8 * dp;
        fill.setColor(0xF2121418);
        c.drawRect(left, y - 26 * dp, w, listArea.bottom, fill);
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
                if (screen == REPLAY && replayBar.contains(downX, downY)) {
                    scrubReplay(x);
                    break;
                }
                if (!dragging && Math.abs(y - downY) > 10 * dp && listArea.contains(downX, downY)
                        && (screen == SETUP || screen == SETTINGS || screen == NOTES || screen == RECORDS || screen == LOG
                        || screen == SAVES)) dragging = true;
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
        if (screen == REPLAY && replayBar.contains(x, y)) {
            scrubReplay(x);
            return;
        }
        for (Button b : buttons) {
            if (b.r.contains(x, y)) {
                host.click();
                act(b.action);
                return;
            }
        }
        if (!listArea.contains(x, y)) return;
        if (screen == SETUP) {
            for (Seg sg : segs) {
                if (!sg.r.contains(x, y)) continue;
                if (config.get(sg.option) != sg.value) {
                    config.set(sg.option, sg.value);
                    // A different map or size is a different city.
                    if (sg.option == CityConfig.OPT_PRESET || sg.option == CityConfig.OPT_SIZE) config.keepCity = false;
                }
                host.click();
                return;
            }
            return;
        }
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
        if (action >= A_LOG_ROW) {
            java.util.ArrayList<Dispatch.Message> log = host.world().dispatch.log;
            int idx = log.size() - 1 - (action - A_LOG_ROW);
            if (idx >= 0 && idx < log.size()) {
                Dispatch.Message m = log.get(idx);
                host.continueGame();
                host.jumpTo(m.x, m.y);
            }
            return;
        }
        if (action >= A_SLOT) {
            int slot = action - A_SLOT;
            if (savingMode) {
                boolean ok = host.saveSlot(slot);
                host.world().say(ok ? "Saved to slot " + slot : "Could not save");
                host.continueGame();
            } else if (!host.loadSlot(slot)) {
                note = "That save couldn't be loaded.";
                noteTime = 3;
            }
            return;
        }
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
                config.newSeed(rnd);
                break;
            case A_REROLL:
                config.newSeed(rnd);
                config.keepCity = false;
                break;
            case A_CODE:
                host.askCityCode(config.code());
                break;
            case A_START:
                // The seed is the one in the preview.
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
            case A_REPLAY:
                screen = REPLAY;
                replayPos = 0;
                replayPlaying = true;
                break;
            case A_REPLAY_PLAY:
                if (replayPos >= 1) replayPos = 0;
                replayPlaying = !replayPlaying;
                break;
            case A_RECORDS:
                open(RECORDS);
                break;
            case A_SAVES:
                savingMode = true;
                open(SAVES);
                break;
            case A_LOAD:
                savingMode = false;
                open(SAVES);
                break;
            case A_LOG:
                open(LOG);
                break;
            case A_HUD:
                host.continueGame();
                host.hideHud();
                break;
            case A_SHOT:
                host.continueGame();
                host.screenshot();
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
