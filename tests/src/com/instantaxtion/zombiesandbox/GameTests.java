package com.instantaxtion.zombiesandbox;

import android.graphics.Canvas;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;

/**
 * Runs the game on a desktop JVM (with small stand-ins for the Android classes in tests/stubs) and checks
 * that cities generate, the simulation runs, saves round-trip, checkpoints work and every screen draws. Run with ./test.sh.
 */
public final class GameTests {
    private static int passed, failed;

    public static void main(String[] args) throws Exception {
        test("every map and size generates", new Check() {
            public void run() {
                for (int p = 0; p < CityConfig.PRESETS.length; p++)
                    for (int size = 0; size < 3; size++) {
                        CityConfig c = new CityConfig();
                        c.v[CityConfig.OPT_PRESET] = p;
                        c.v[CityConfig.OPT_SIZE] = size;
                        c.seed = 1000 + p;
                        City city = new City(c, 0.1f);
                        check(city.buildings.size() > 20, CityConfig.PRESETS[p] + " has buildings");
                        check(city.nearestFacility(City.FACILITY_HOSPITAL, 0, 0) != null, CityConfig.PRESETS[p] + " has a hospital");
                    }
            }
        });
        test("city generation is repeatable from its code", new Check() {
            public void run() {
                CityConfig a = new CityConfig();
                a.v[CityConfig.OPT_PRESET] = 3;
                a.seed = 424242;
                CityConfig b = new CityConfig();
                check(b.applyCode(a.code()), "code parses");
                City ca = new City(a, 0.1f), cb = new City(b, 0.1f);
                check(java.util.Arrays.equals(ca.tiles, cb.tiles), "same tiles");
                check(!b.applyCode("not a code"), "rejects junk");
            }
        });
        final World[] world = new World[1];
        test("a busy outbreak runs for two minutes", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_CIVILIANS] = 5;
                c.v[CityConfig.OPT_COPS] = 3;
                c.v[CityConfig.OPT_MILITARY] = 3;
                c.v[CityConfig.OPT_ZOMBIES] = 4;
                c.seed = 77;
                World w = new World(c);
                w.populate(c);
                w.raiderGang(w.city.worldW() / 2, w.city.worldH() / 2, 4);
                w.hordeEvent(w.city.worldW() / 2, w.city.worldH() / 2);
                w.explode(w.city.worldW() / 3, w.city.worldH() / 3, 70, 400);
                for (int i = 0; i < 30 * 120; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(w.humans + w.zombies > 0, "someone is left");
                check(w.entities.size() <= w.maxEntities, "population cap holds");
                world[0] = w;
            }
        });
        test("saves load back the same", new Check() {
            public void run() throws Exception {
                World w = world[0];
                File f = File.createTempFile("zcs", ".dat");
                SaveGame.save(w, f);
                World l = SaveGame.load(f);
                check(l.city.cfg.code().equals(w.city.cfg.code()), "same city");
                check(Math.abs(l.entities.size() - w.entities.size()) <= w.fleet.riderCount() + 8, "same people");
                check(l.zombiesKilled == w.zombiesKilled, "same stats");
                check(l.histCount == w.histCount && (w.histCount == 0 || l.histArmed[w.histCount - 1] == w.histArmed[w.histCount - 1]),
                        "same police and army history");
                check(l.turnedAway == w.turnedAway && l.medkits.size() == w.medkits.size(), "same checkpoints and medkits");
                f.delete();
            }
        });
        test("new map features appear across the presets", new Check() {
            public void run() {
                int malls = 0, stadiums = 0, farms = 0, roundabouts = 0, sites = 0;
                for (int p = 0; p < CityConfig.PRESETS.length; p++)
                    for (int seed = 1; seed <= 2; seed++) {
                        CityConfig c = new CityConfig();
                        c.v[CityConfig.OPT_PRESET] = p;
                        c.v[CityConfig.OPT_SIZE] = 1;
                        c.seed = seed * 31 + p;
                        City city = new City(c, 0.1f);
                        for (City.Building b : city.buildings) {
                            if (b.kind == City.MALL) malls++;
                            if (b.kind == City.STADIUM) stadiums++;
                            if (b.kind == City.BARN) farms++;
                        }
                        for (float[] d : city.decor) {
                            if ((int) d[0] == City.D_ROUNDABOUT) roundabouts++;
                            if ((int) d[0] == City.D_SITE) sites++;
                        }
                    }
                check(malls > 0 && stadiums > 0 && farms > 0 && roundabouts > 0 && sites > 0,
                        "malls " + malls + ", stadiums " + stadiums + ", farms " + farms + ", roundabouts " + roundabouts + ", sites " + sites);
            }
        });
        test("new zombies, units and events run", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 2;
                c.seed = 12;
                World w = new World(c);
                w.populate(c);
                float cx = w.city.worldW() / 2, cy = w.city.worldH() / 2;
                float[] p = w.city.findWalkable(cx, cy);
                for (int i = 0; i < 4; i++) {
                    w.spawn(Entity.SPITTER, p[0] + i * 6, p[1]);
                    w.spawn(Entity.BLOATER, p[0], p[1] + i * 6);
                    w.spawnCop(i % 2 == 0 ? Entity.ROLE_RIOT : Entity.ROLE_K9, p[0] - 40, p[1] - 40);
                }
                w.placeMedkit(p[0] - 30, p[1]);
                w.airstrike(p[0] + 120, p[1] + 120);
                w.cityAlarm();
                w.infectAt(p[0] + 60, p[1]);
                for (int i = 0; i < 30 * 60; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(w.humans + w.zombies > 0, "someone is left");
                check(w.patientZero != null || w.outbreakPlace != null, "patient zero was tracked");
            }
        });
        test("army checkpoints stop most bitten people", new Check() {
            public void run() throws Exception {
                World w = world[0];
                Dispatch.SafeZone z = new Dispatch.SafeZone();
                z.x = w.city.worldW() / 2;
                z.y = w.city.worldH() / 2;
                z.r = 60;
                z.military = true;
                z.place = "Test";
                java.lang.reflect.Method m = World.class.getDeclaredMethod("checkpoint", Entity.class, Dispatch.SafeZone.class);
                m.setAccessible(true);
                int before = w.turnedAway + w.quarantined, stopped = 0;
                for (int i = 0; i < 40; i++) {
                    Entity e = w.create(Entity.CIVILIAN, z.x, z.y);
                    e.infected = true;
                    e.infectTimer = 30;
                    if ((Boolean) m.invoke(w, e, z)) stopped++;
                    check(e.screened, "checked at the gate");
                }
                int caught = w.turnedAway + w.quarantined - before;
                check(caught >= 20 && stopped <= caught, "caught " + caught + " of 40");
                Entity clean = w.create(Entity.CIVILIAN, z.x, z.y);
                check(!(Boolean) m.invoke(w, clean, z), "healthy people walk in");
            }
        });
        test("barricades block and come away cleanly", new Check() {
            public void run() {
                World w = world[0];
                float[] p = w.city.randomWalkable(new java.util.Random(3));
                int i = w.city.tileIndex(p[0], p[1]);
                byte before = w.city.tiles[i];
                float[] b = null;
                for (int k = 0; k < 50 && b == null; k++) {
                    p = w.city.randomWalkable(new java.util.Random(k));
                    i = w.city.tileIndex(p[0], p[1]);
                    before = w.city.tiles[i];
                    b = w.placeBarricade(p[0], p[1]);
                }
                check(b != null && w.city.solid[i], "barricade is solid");
                w.removeBarricade(b);
                check(w.city.tiles[i] == before && !w.city.solid[i], "tile restored");
            }
        });
        test("every screen draws", new Check() {
            public void run() throws Exception {
                GameView v = new GameView(new android.app.Activity());
                for (int[] size : new int[][]{{2400, 1080}, {1080, 2400}, {2560, 1600}}) {
                    v.layout(size[0], size[1]);
                    Menu m = (Menu) field(v, "menu");
                    for (int s : new int[]{Menu.MAIN, Menu.SETUP, Menu.SETTINGS, Menu.NOTES, Menu.RECORDS, Menu.TUTORIAL}) {
                        m.open(s);
                        draw(v, size);
                    }
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_ZOMBIES] = 3;
                    v.startGame(c);
                    m.screen = Menu.NONE;
                    for (int k = 0; k < 20; k++) draw(v, size);
                    m.open(Menu.PAUSE);
                    draw(v, size);
                    m.open(Menu.STATS);
                    draw(v, size);
                    m.open(Menu.LOG);
                    draw(v, size);
                    m.open(Menu.SAVES);
                    draw(v, size);
                    m.screen = Menu.NONE;
                }
            }
        });
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    interface Check {
        void run() throws Exception;
    }

    private static void test(String name, Check c) {
        long t = System.currentTimeMillis();
        try {
            c.run();
            passed++;
            System.out.println("PASS  " + name + "  (" + (System.currentTimeMillis() - t) + " ms)");
        } catch (Throwable e) {
            failed++;
            System.out.println("FAIL  " + name + ": " + e);
            e.printStackTrace(System.out);
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static void draw(GameView v, int[] size) throws Exception {
        Field last = GameView.class.getDeclaredField("lastFrame");
        last.setAccessible(true);
        last.set(v, System.nanoTime() - 33000000L);
        BufferedImage img = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
        java.lang.reflect.Method onDraw = GameView.class.getDeclaredMethod("onDraw", Canvas.class);
        onDraw.setAccessible(true);
        onDraw.invoke(v, new Canvas(img.createGraphics()));
    }
}
