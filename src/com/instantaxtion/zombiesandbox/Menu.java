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
    }

    static final int NONE = 0, MAIN = 1, SETUP = 2, SETTINGS = 3, NOTES = 4, PAUSE = 5;

    private static final int A_CONTINUE = 0, A_NEW = 1, A_SETTINGS = 2, A_NOTES = 3, A_QUIT = 4, A_RESUME = 5,
            A_MAIN_MENU = 6, A_BACK = 7, A_START = 8, A_RANDOM = 9;

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
        if (s == SETUP || s == SETTINGS || s == NOTES) {
            if (screen == MAIN || screen == PAUSE) returnTo = screen;
        } else {
            returnTo = MAIN;
        }
        screen = s;
        scroll = 0;
        if (s == NOTES) settings.markNotesRead();
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
        boolean game = host.hasGame();
        int n = game ? 5 : 4;
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
        float bh = Math.min(50 * dp, (h - 120 * dp) / 5.8f), gap = 10 * dp;
        float pw = Math.min(320 * dp, w - 40 * dp), ph = 70 * dp + bh * 5 + gap * 4 + 20 * dp;
        tmp.set((w - pw) / 2, (h - ph) / 2, (w + pw) / 2, (h + ph) / 2);
        fill.setColor(0xF0181A1E);
        c.drawRoundRect(tmp, 16 * dp, 16 * dp, fill);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(26 * dp);
        text.setColor(0xFF8BD450);
        c.drawText("PAUSED", w / 2f, tmp.top + 46 * dp, text);
        float bx = tmp.left + 20 * dp, bw = pw - 40 * dp, by = tmp.top + 70 * dp;
        String[] labels = {"Resume", "New Game", "Settings", "Patch Notes", "Main Menu"};
        int[] actions = {A_RESUME, A_NEW, A_SETTINGS, A_NOTES, A_MAIN_MENU};
        for (int i = 0; i < 5; i++) {
            button(labels[i], actions[i], i == 0, bx, by, bx + bw, by + bh);
            by += bh + gap;
        }
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
            float bw = Math.min(200 * dp, (w - 48 * dp) / 2), bh = 50 * dp, by = h - footer + 11 * dp;
            button("Randomize", A_RANDOM, false, w / 2f - bw - 8 * dp, by, w / 2f - 8 * dp, by + bh);
            button("Start", A_START, true, w / 2f + 8 * dp, by, w / 2f + 8 * dp + bw, by + bh);
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
            case A_START:
                config.seed = System.nanoTime();
                host.startGame(config);
                break;
        }
    }
}
