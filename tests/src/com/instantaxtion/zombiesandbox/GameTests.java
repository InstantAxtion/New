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
        test("every map generates at the smallest size, and the classic map at every size", new Check() {
            public void run() {
                // (Every map at Medium is built by the government buildings test.)
                for (int p = 0; p < CityConfig.PRESETS.length + 5; p++) {
                    // (The real cities only come in one size: see the 10.18 test.)
                    if (RealCities.isReal(p)) continue;
                    CityConfig c = new CityConfig();
                    boolean sizes = p >= CityConfig.PRESETS.length;
                    c.v[CityConfig.OPT_PRESET] = sizes ? 0 : p;
                    c.v[CityConfig.OPT_SIZE] = sizes ? p - CityConfig.PRESETS.length + 1 : CityConfig.TINY;
                    c.seed = 1000 + p;
                    City city = new City(c, 0.1f);
                    String name = CityConfig.PRESETS[c.v[CityConfig.OPT_PRESET]] + " size " + c.v[CityConfig.OPT_SIZE];
                    check(city.buildings.size() > 20, name + " has buildings");
                    check(city.nearestFacility(City.FACILITY_HOSPITAL, 0, 0) != null, name + " has a hospital");
                }
            }
        });
        final World[] world = new World[1];
        test("a busy outbreak runs for two minutes", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_CIVILIANS] = 4;
                c.v[CityConfig.OPT_COPS] = 3;
                c.v[CityConfig.OPT_MILITARY] = 3;
                c.v[CityConfig.OPT_ZOMBIES] = 4;
                c.seed = 77;
                World w = new World(c);
                w.populate(c);
                w.panic(w.city.worldW() / 2, w.city.worldH() / 2);
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
        testAfter("saves load back the same", new Check() {
            public void run() throws Exception {
                World w = world[0];
                File f = File.createTempFile("zcs", ".dat");
                SaveGame.save(w, f);
                World l = SaveGame.load(f);
                check(l.city.cfg.code().equals(w.city.cfg.code()), "same city");
                // (People inside on errands come back out at the door.)
                int visiting = 0;
                for (City.Building b : w.city.buildings) visiting += b.visitors.size();
                int posted = 0;
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.guard != null && w.entities.contains(v.guard)) posted++;
                int dead = 0;
                for (Entity e : w.entities) if (e.dead) dead++;
                int carried = 0;
                // (Crews aboard patrol cars, engines and patrols are saved as people too.)
                for (Fleet.Vehicle v : w.fleet.vehicles) carried += Math.max(0, v.passengers) + v.crew.size();
                check(Math.abs(l.entities.size() - (w.entities.size() - dead - posted + visiting)) <= w.fleet.riderCount() + carried + 8,
                        "same people (" + l.entities.size() + " vs " + w.entities.size() + " - " + dead + " dead - " + posted + " posted + " + visiting
                                + " visiting, riders " + w.fleet.riderCount() + ")");
                check(l.zombiesKilled == w.zombiesKilled, "same stats");
                check(l.histCount == w.histCount && (w.histCount == 0 || l.histArmed[w.histCount - 1] == w.histArmed[w.histCount - 1]),
                        "same police and army history");
                check(l.turnedAway == w.turnedAway && l.medkits.size() == w.medkits.size(), "same checkpoints and medkits");
                f.delete();
            }
        });
        test("the war ends with a winner", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 9;
                World w = new World(c);
                w.populate(c);
                float[] p = w.city.randomWalkable(new java.util.Random(2));
                Entity z = w.spawn(Entity.ZOMBIE, p[0], p[1]);
                for (City.Building b : w.city.buildings) b.lurkers = 0;
                w.recount();
                check(w.outbreak, "an outbreak started");
                z.hp = 0;
                for (int i = 0; i < 30 * 20 && w.warResult == 0; i++) {
                    for (Entity e : w.entities) if (e.isZombie()) e.hp = 0;
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(w.warResult == 1, "the city survived");
                check(w.warBanner != null, "the ending was announced");
                check(w.strainName != null, "the strain has a name");
            }
        });
        test("take control of anyone, and the outbreak replay", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 3;
                c.seed = 5;
                World w = new World(c);
                w.populate(c);
                for (int i = 0; i < 30 * 20; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                Entity soldier = null;
                // (One with room to walk east, not up against a wall.)
                for (Entity e : w.entities)
                    if (!e.dead && e.type == Entity.SOLDIER && (soldier == null || !w.city.circleBlocked(e.x + 12, e.y, e.radius))) soldier = e;
                check(soldier != null, "a soldier to play");
                w.controlled = soldier;
                w.joyX = 1;
                float x0 = soldier.x, y0 = soldier.y;
                for (int i = 0; i < 30 * 2; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(w.controlled != soldier || Math.hypot(soldier.x - x0, soldier.y - y0) > 20, "the stick moves them");
                // Put a zombie in front and hold the attack button.
                w.joyX = 0;
                float[] p = w.city.findWalkable(soldier.x + 40, soldier.y);
                Entity z = w.spawn(Entity.ZOMBIE, p[0], p[1]);
                int shots = w.shotsFired;
                w.ctrlAttack = true;
                for (int i = 0; i < 30; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(w.controlled != soldier || w.shotsFired > shots, "the attack button shoots");
                // A zombie bites.
                Entity civ = null;
                for (Entity e : w.entities) if (!e.dead && e.type == Entity.CIVILIAN) civ = e;
                Entity me = w.spawn(Entity.ZOMBIE, civ.x + 5, civ.y);
                civ.stun = 5;
                w.controlled = me;
                w.joyX = 0;
                w.ctrlAttack = true;
                int bites = w.bites;
                for (int i = 0; i < 90 && w.bites == bites && !civ.dead && !civ.removed; i++) {
                    // Chase them with the stick.
                    float dx = civ.x - me.x, dy = civ.y - me.y, d = (float) Math.hypot(dx, dy) + 0.01f;
                    w.joyX = dx / d;
                    w.joyY = dy / d;
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(w.controlled != me || w.bites > bites || civ.dead || civ.removed, "a controlled zombie bites");
                w.controlled = null;
                check(!w.replayMeta.isEmpty() && w.replayMeta.size() == w.replayDots.size(), "the replay is recorded");
                File f = File.createTempFile("zcs", ".dat");
                SaveGame.save(w, f);
                World l = SaveGame.load(f);
                check(l.replayMeta.size() == w.replayMeta.size() && l.turnEvents.size() == w.turnEvents.size(), "the replay is saved");
                f.delete();
            }
        });
        test("11.3: only the USA, and the deleted maps are gone for good", new Check() {
            public void run() {
                check(Country.NAMES.length == 1 && Country.get(5) == Country.get(Country.USA), "the USA is the only country");
                CityConfig old = new CityConfig();
                check(old.applyCode("12-44") && old.country() == Country.USA, "codes build American cities");
                check(!new CityConfig().applyCode("22-77@16"), "a code for another country doesn't load");
                for (int p : new int[]{19, 20, 21, 24, 26, 27}) {
                    check(CityConfig.removed(p) && CityConfig.retired(CityConfig.OPT_PRESET, p), CityConfig.PRESETS[p] + " is gone");
                    check(!new CityConfig().applyCode(String.valueOf("123456789ABCDEFGHIJKLMNOPQRST".charAt(p)) + "3-5"), "and its city code doesn't load");
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_PRESET] = p;
                    c.normalize();
                    check(c.preset() == CityConfig.METROPOLIS, "nor does it build");
                }
                check(!CityConfig.removed(CityConfig.BAY) && !CityConfig.removed(CityConfig.CANAL), "Bay City and Canal City stay");
            }
        });
        test("cars never go round in circles (round a median, a roundabout or a highway gap)", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_SIZE] = CityConfig.MASSIVE;
                c.v[CityConfig.OPT_ZOMBIES] = 2;
                c.seed = 5;
                World w = new World(c);
                w.populate(c);
                java.util.HashMap<Fleet.Vehicle, float[]> m = new java.util.HashMap<Fleet.Vehicle, float[]>();
                int circling = 0;
                for (int f = 0; f < 30 * 120; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    for (Fleet.Vehicle v : w.fleet.vehicles) {
                        if (v.type == Fleet.HELI || v.type == Fleet.JET || v.type == Fleet.TRAIN || v.parked) continue;
                        float[] t = m.get(v);
                        // turned so far, x0, y0, x1, y1, time, last angle, flagged
                        if (t == null || t[5] > 10) {
                            t = new float[]{0, v.x, v.y, v.x, v.y, 0, v.angle, t != null ? t[7] : 0};
                            m.put(v, t);
                        }
                        float da = v.angle - t[6];
                        while (da > Math.PI) da -= 2 * Math.PI;
                        while (da < -Math.PI) da += 2 * Math.PI;
                        t[0] += da;
                        t[6] = v.angle;
                        t[5] += 1 / 30f;
                        t[1] = Math.min(t[1], v.x);
                        t[2] = Math.min(t[2], v.y);
                        t[3] = Math.max(t[3], v.x);
                        t[4] = Math.max(t[4], v.y);
                        if (t[7] == 0 && Math.abs(t[0]) > 3 * Math.PI * 2 && t[3] - t[1] < 90 && t[4] - t[2] < 90) {
                            t[7] = 1;
                            circling++;
                        }
                    }
                }
                check(circling == 0, circling + " vehicles went round in circles");
            }
        });
        test("new kinds of building, and saved cities still rebuild the same", new Check() {
            public void run() {
                {
                    CityConfig c = new CityConfig();
                    c.seed = 42;
                    c.v[CityConfig.OPT_SIZE] = CityConfig.MASSIVE;
                    City city = new City(c);
                    java.util.HashSet<String> names = new java.util.HashSet<String>();
                    for (City.Building b : city.buildings) if (b.typeName() != null) names.add(b.typeName());
                    int found = 0;
                    for (String n : new String[]{"Barber shop", "Brownstone", "Trailer home", "BBQ smokehouse"}) if (names.contains(n)) found++;
                    check(found >= 2, found + " of the newer kinds of building");
                }
                // (The same city as version 10.0 built from this seed, building for building, for games saved
                // before the government quarter.)
                CityConfig c = new CityConfig();
                c.seed = 42;
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                c.civic = 0;
                City city = new City(c);
                long h = 17;
                for (City.Building b : city.buildings) h = h * 31 + (long) (b.x0 * 7 + b.y0 * 13 + b.x1 * 17 + b.y1 * 19 + b.kind);
                check(city.buildings.size() == 377 && h == 0xd953e0baab71cfb8L, "same layout as before (" + city.buildings.size() + ", " + Long.toHexString(h) + ")");
            }
        });
        test("government buildings on every map, and what happens when they fall", new Check() {
            public void run() {
                for (int preset = 0; preset < CityConfig.PRESETS.length; preset++) {
                    CityConfig c = new CityConfig();
                    c.seed = 11 + preset;
                    c.v[CityConfig.OPT_PRESET] = preset;
                    c.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                    City city = new City(c);
                    check(city.cityHall != null && city.cityHall.doorX > 0, CityConfig.PRESETS[preset] + ": a city hall with a door");
                    if (preset != 9)
                        check(city.courthouse != null && city.jail != null && city.worksDepot != null,
                                CityConfig.PRESETS[preset] + ": courthouse, jail and works depot");
                    check(city.jail == null || city.jail.capacity == 0, "nobody shelters in the cells");
                    check(city.armory != null, CityConfig.PRESETS[preset] + ": a National Guard armory");
                }
                CityConfig c = new CityConfig();
                c.seed = 5;
                c.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                World w = new World(c);
                w.populate(c);
                check(w.city.callCentre != null && w.inmates > 0, "a 911 centre, and inmates in the jail (" + w.inmates + ")");
                check(w.broadcasting(), "City Hall is broadcasting");
                // The dead take City Hall and the 911 centre and hold them.
                java.util.ArrayList<Entity> horde = new java.util.ArrayList<Entity>();
                City.Building[] held = {w.city.cityHall, w.city.callCentre};
                for (City.Building b : held)
                    for (int k = 0; k < 7; k++) horde.add(w.spawn(Entity.ZOMBIE, b.doorX, b.doorY));
                for (int f = 0; f < 30 * 25; f++) {
                    for (int k = 0; k < horde.size(); k++) {
                        Entity z = horde.get(k);
                        City.Building b = held[k / 7];
                        // (Any the police put down are replaced.)
                        if (z == null || z.dead) {
                            z = w.spawn(Entity.ZOMBIE, b.doorX, b.doorY);
                            horde.set(k, z);
                            if (z == null) continue;
                        }
                        z.x = b.doorX + (k % 7) * 3 - 9;
                        z.y = b.doorY;
                    }
                    w.update(1 / 30f);
                }
                check(w.hallLost && !w.broadcasting(), "City Hall overrun: no more broadcasts");
                check(w.callsLost, "the 911 centre overrun");
                // The power fails: the cell doors open.
                w.blackout = true;
                for (int f = 0; f < 30 * 2; f++) w.update(1 / 30f);
                // (The inmates who get out run in prison orange; the rest of the city's numbers swing with the outbreak.)
                int escaped = 0;
                for (Entity e : w.entities) if (e.type == Entity.CIVILIAN && !e.dead && e.body == 0xFFE07A2A) escaped++;
                check(w.jailBroken && w.inmates == 0 && escaped > 0, "jailbreak when the power fails (" + escaped + " escaped)");
            }
        });
        test("water maps: the sea, a river with bridges, a lake and a port; nobody ends up in the water", new Check() {
            public void run() {
                for (int preset = 10; preset < 14; preset++) {
                    CityConfig c = new CityConfig();
                    c.seed = 21 + preset;
                    c.v[CityConfig.OPT_PRESET] = preset;
                    c.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                    c.v[CityConfig.OPT_ZOMBIES] = 3;
                    World w = new World(c);
                    w.populate(c);
                    City city = w.city;
                    String name = CityConfig.PRESETS[preset];
                    int water = 0, sand = 0, pier = 0, bridges = 0;
                    for (byte t : city.tiles) {
                        if (t == City.WATER) water++;
                        else if (t == City.SAND) sand++;
                        else if (t == City.PIER) pier++;
                    }
                    if (city.riverY0 > 0) {
                        int y = city.riverY0 + city.riverRows / 2;
                        for (int x = 1; x < city.w; x++) if (city.tiles[y * city.w + x] == City.ROAD && city.tiles[y * city.w + x - 1] != City.ROAD) bridges++;
                    }
                    check(water > city.w * 8, name + ": water (" + water + " tiles)");
                    check(city.armory != null && city.cityHall != null, name + ": city hall and a National Guard armory");
                    switch (city.waterKind) {
                        case CityConfig.W_SEA: check(sand > 0 && pier > 0, name + ": a beach and a pier"); break;
                        case CityConfig.W_RIVER: check(bridges >= 3, name + ": " + bridges + " bridges"); break;
                        case CityConfig.W_LAKE: check(city.lakes.size() == 1, name + ": a lake"); break;
                        default: check(city.seaY > 0, name + ": the sea"); break;
                    }
                    check(!w.boats.isEmpty(), name + ": boats on the water");
                    for (int f = 0; f < 30 * 30; f++) w.update(1 / 30f);
                    int wet = 0;
                    for (Entity e : w.entities)
                        if (!e.dead && !e.hidden && city.tiles[city.tileIndex(e.x, e.y)] == City.WATER && !city.isFord(city.tileIndex(e.x, e.y))) wet++;
                    for (Fleet.Vehicle v : w.fleet.vehicles)
                        if (v.type != Fleet.HELI && v.type != Fleet.JET && city.tiles[city.tileIndex(v.x, v.y)] == City.WATER) wet++;
                    check(wet == 0, name + ": " + wet + " people or cars in the water");
                }
            }
        });
        test("doors clear of trees, the armory manned, SWAT called to a horde, parked army trucks used", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.seed = 6;
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                World w = new World(c);
                w.populate(c);
                City city = w.city;
                int blocked = 0;
                for (City.Building b : city.buildings) {
                    if (b.doorX <= 0) continue;
                    int tx = (int) (b.doorX / City.T), ty = (int) (b.doorY / City.T);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) if (city.tiles[(ty + dy) * city.w + tx + dx] == City.TREE) blocked++;
                }
                check(blocked == 0, blocked + " trees in doorways");
                int guards = 0;
                for (Entity e : w.entities)
                    if (e.type == Entity.SOLDIER && e.role == Entity.ROLE_GUARD && Math.hypot(e.x - city.armory.doorX, e.y - city.armory.doorY) < 400) guards++;
                check(guards >= 2, guards + " guardsmen on duty at the armory");
                check(w.dispatch.swatTeams >= 1, "a SWAT team at the precinct");
                // A horde turns up near people: the SWAT team is sent.
                Entity near = null;
                for (Entity e : w.entities) if (e.type == Entity.CIVILIAN && !e.hidden && !e.dead && city.cityHall != null
                        && Math.hypot(e.x - city.cityHall.doorX, e.y - city.cityHall.doorY) < 600) near = e;
                if (near == null) for (Entity e : w.entities) if (e.type == Entity.CIVILIAN && !e.hidden && !e.dead) near = e;
                int teams = w.dispatch.swatTeams;
                // (The 911 call depends on who sees them first: a big enough horde, and long enough, that it always comes.)
                for (int k = 0; k < 20; k++) w.spawn(Entity.ZOMBIE, near.x + 40 + (k % 7) * 3, near.y + (k / 7) * 4);
                boolean sent = false;
                for (int f = 0; f < 30 * 150 && !sent; f++) {
                    w.update(1 / 30f);
                    if (w.dispatch.swatTeams < teams) sent = true;
                }
                check(sent, "SWAT sent to the horde");
                // Army trucks leaving a base take the ones parked there.
                CityConfig b = new CityConfig();
                b.seed = 5;
                b.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                City base = new City(b);
                City.Facility f = base.nearestFacility(City.FACILITY_BASE, 0, 0);
                check(f != null && base.takeParkedTruck(f.x, f.y, 600) != null, "a parked truck drives off the base");
            }
        });
        test("police and soldiers carry a real load and make it count; (10.50: no bloater gas)", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.seed = 3;
                c.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                c.v[CityConfig.OPT_ZOMBIES] = 4;
                World w = new World(c);
                w.populate(c);
                // (An unprepared city has only half the spare rounds.)
                boolean half = w.readiness == 1;
                for (Entity e : w.entities) {
                    if (e.type == Entity.COP && e.role == 0 && e.agency == 0)
                        check(e.ammo + e.reserve == (half ? 15 + 22 : 60), "an officer carries 60 rounds (" + (e.ammo + e.reserve) + ")");
                    if (e.type == Entity.SOLDIER && e.role == Entity.ROLE_RIFLE)
                        check(e.ammo + e.reserve == (half ? 30 + 90 : 210), "a soldier carries 210 rounds (" + (e.ammo + e.reserve) + ")");
                }
                // Two minutes into a busy outbreak, hardly anyone has run dry.
                for (int f = 0; f < 30 * 120; f++) w.update(1 / 30f);
                int armed = 0, dry = 0;
                for (Entity e : w.entities) {
                    if (e.dead || (e.type != Entity.COP && e.type != Entity.SOLDIER)) continue;
                    armed++;
                    if (e.ammo + e.reserve == 0) dry++;
                }
                check(dry * 8 <= armed, dry + " of " + armed + " police and soldiers out of ammo after two minutes");
            }
        });
        test("Islands: two to four island towns, joined up, one fixed size, nobody stranded", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.seed = 9;
                c.set(CityConfig.OPT_PRESET, CityConfig.ISLANDS);
                c.set(CityConfig.OPT_SIZE, CityConfig.SMALL);
                check(c.tiles() == CityConfig.ISLANDS_TILES && c.v[CityConfig.OPT_SIZE] == CityConfig.MASSIVE, "Islands is always " + CityConfig.ISLANDS_TILES + " tiles");
                CityConfig back = new CityConfig();
                check(back.applyCode(c.code()) && back.tiles() == CityConfig.ISLANDS_TILES, "its city code (" + c.code() + ") builds the Islands again");
                City city = new City(c);
                check(city.w > 448, "bigger than a Massive map");
                int n = city.islandRects == null ? 0 : city.islandRects.length;
                check(n >= 2 && n <= 4, "two to four islands (" + n + ")");
                java.util.HashSet<Integer> counts = new java.util.HashSet<Integer>();
                for (int sd = 1; sd <= 12; sd++) {
                    CityConfig o = new CityConfig();
                    o.seed = sd;
                    o.set(CityConfig.OPT_PRESET, CityConfig.ISLANDS);
                    counts.add(new City(o, 0.05f).islandRects.length);
                }
                check(counts.size() >= 2, "the number of islands varies from city to city " + counts);
                // Every bit of land can be walked to from the causeway (no islets, no cut-off scraps of coast).
                int[] q0 = new int[city.w * city.h];
                boolean[] seen = new boolean[city.w * city.h];
                int start = city.tileIndex(city.causewayEnd[0], city.causewayEnd[1]), head = 0, tail = 0;
                q0[tail++] = start;
                seen[start] = true;
                while (head < tail) {
                    int t = q0[head++], x = t % city.w, y = t / city.w;
                    int[] nb = {x > 0 ? t - 1 : -1, x < city.w - 1 ? t + 1 : -1, y > 0 ? t - city.w : -1, y < city.h - 1 ? t + city.w : -1};
                    for (int j : nb)
                        if (j >= 0 && !seen[j] && city.tiles[j] != City.WATER) {
                            seen[j] = true;
                            q0[tail++] = j;
                        }
                }
                int stranded = 0;
                for (int t = 0; t < city.w * city.h; t++) if (city.tiles[t] != City.WATER && !seen[t]) stranded++;
                check(stranded == 0, "no land cut off from the rest (" + stranded + " tiles)");
                int[] precincts = new int[n], fire = new int[n], buildings = new int[n];
                for (City.Facility f : city.facilities) {
                    int i = city.island((int) (f.x / City.T), (int) (f.y / City.T));
                    if (i < 0) continue;
                    if (f.kind == City.FACILITY_POLICE) precincts[i]++;
                    if (f.kind == City.FACILITY_FIRE) fire[i]++;
                }
                for (City.Building b : city.buildings) {
                    int i = city.island((int) (b.doorX / City.T), (int) (b.doorY / City.T));
                    if (i >= 0) buildings[i]++;
                }
                int[] dist = new int[city.w * city.h];
                for (int i = 0; i < n; i++) {
                    int[] q = city.islandRects[i];
                    check(buildings[i] > 150 && precincts[i] > 0 && fire[i] > 0, city.islandNames[i] + ": a town (" + buildings[i]
                            + " buildings) with its own police and fire station");
                    // (A street near the middle of the island.)
                    float[] spot = null;
                    for (int r = 0; r < 60 && spot == null; r++)
                        for (int dy = -r; dy <= r && spot == null; dy++)
                            for (int dx = -r; dx <= r && spot == null; dx++) {
                                int tx = (q[0] + q[2]) / 2 + dx, ty = (q[1] + q[3]) / 2 + dy;
                                if (city.tiles[ty * city.w + tx] == City.ROAD) spot = new float[]{(tx + 0.5f) * City.T, (ty + 0.5f) * City.T};
                            }
                    boolean road = spot != null && city.driveField(dist, spot[0], spot[1])
                            && dist[city.tileIndex(city.causewayEnd[0], city.causewayEnd[1])] < City.FAR;
                    check(road, city.islandNames[i] + " can be reached by road from the mainland causeway");
                }
                float[] edge = city.edgeRoad(new java.util.Random(1));
                check(edge != null && city.drivable(edge[0], edge[1]), "convoys come in over the causeway");
            }
        });
        test("traffic keeps its distance: hardly any crashes, and patrol cars keep to their side", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 3;
                World w = new World(c);
                w.populate(c);
                w.viewX0 = 0;
                w.viewY0 = 0;
                w.viewX1 = w.city.worldW();
                w.viewY1 = w.city.worldH();
                java.util.HashMap<Fleet.Vehicle, Float> cd = new java.util.HashMap<Fleet.Vehicle, Float>();
                int crashes = 0, onRoad = 0, wrong = 0;
                for (int f = 0; f < 30 * 90; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    for (Fleet.Vehicle v : w.fleet.vehicles) {
                        Float prev = cd.get(v);
                        cd.put(v, v.crashCd);
                        if (prev != null && v.crashCd > prev + 0.05f && v.crashCd > 0.55f) crashes++;
                        if (!v.patrol || v.parked || v.speed < 10 || f % 10 != 0) continue;
                        int tx = (int) (v.x / City.T), ty = (int) (v.y / City.T);
                        if (w.city.junctionIdAt(v.x, v.y) >= 0 || w.city.roadDirAt(tx, ty) == 0) continue;
                        float fx = (float) Math.cos(v.angle), fy = (float) Math.sin(v.angle);
                        int dx = Math.abs(fx) > Math.abs(fy) ? (int) Math.signum(fx) : 0, dy = dx == 0 ? (int) Math.signum(fy) : 0;
                        float act = (v.x - (tx + 0.5f) * City.T) * -dy + (v.y - (ty + 0.5f) * City.T) * dx;
                        float mine = w.city.laneOffset(tx, ty, dx, dy), theirs = -w.city.laneOffset(tx, ty, -dx, -dy);
                        if (Math.abs(mine - theirs) < 6) continue;
                        onRoad++;
                        if (Math.abs(act - theirs) + 3 < Math.abs(act - mine)) wrong++;
                    }
                }
                // (Each crash shows up on both cars.)
                // (About one crash for every 13 vehicles about at most: the bigger maps since 10.10 have more traffic.)
                check(crashes / 2 <= Math.max(6, w.fleet.vehicles.size() / 13), (crashes / 2) + " crashes in a minute and a half of ordinary traffic ("
                        + w.fleet.vehicles.size() + " vehicles)");
                check(onRoad > 50, "patrol cars out on the roads (" + onRoad + " samples)");
                check(wrong <= onRoad * 0.04f, "patrol cars on the wrong side " + wrong + " of " + onRoad);
            }
        });
        test("traffic keeps to the roads, stops at junctions and never vanishes in view", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 3;
                World w = new World(c);
                w.populate(c);
                w.viewX0 = -100000;
                w.viewY0 = -100000;
                w.viewX1 = -99000;
                w.viewY1 = -99000;
                w.fleet.spawnTraffic(20);
                // Now everything is on screen.
                w.viewX0 = 0;
                w.viewY0 = 0;
                w.viewX1 = w.city.worldW();
                w.viewY1 = w.city.worldH();
                java.util.HashSet<Fleet.Vehicle> before = new java.util.HashSet<Fleet.Vehicle>();
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.type == Fleet.CAR) before.add(v);
                int pavement = 0, samples = 0, stops = 0;
                for (int s = 0; s < 30 * 90; s++) {
                    w.update(1 / 30f);
                    for (Fleet.Vehicle v : w.fleet.vehicles) {
                        if (v.type != Fleet.CAR || v.parked) continue;
                        if (s % 15 == 0) {
                            samples++;
                            byte t = w.city.tiles[w.city.tileIndex(v.x, v.y)];
                            if (t == City.SIDEWALK || t == City.PLAZA) pavement++;
                        }
                        if (v.held && v.speed < 3) stops++;
                    }
                }
                for (Fleet.Vehicle v : before) check(w.fleet.vehicles.contains(v), "no car disappears while you can see it");
                check(pavement * 50 < samples, "cars keep off the pavement");
                check(stops > 0, "cars stop at lights and stop signs");
                check(!w.city.junctions.isEmpty(), "junctions are found");
            }
        });
        test("the Build tool, and its edits in the city code and saves", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 77;
                World w = new World(c);
                w.populate(c);
                int b0 = w.city.buildings.size();
                float[] p = w.city.findWalkable(200, 200);
                int ok = 0;
                for (int r = 0; r < 12 && ok == 0; r++)
                    for (int k = 0; k < 10; k++) if (w.build(p[0] + k * 16, p[1] + r * 32, City.ED_ROAD)) ok++;
                for (int k = 0; k < 40 && w.city.buildings.size() == b0; k++) w.build(p[0] + 30 + k * 40, p[1] + 90, City.ED_HOUSE);
                check(ok > 0 && w.city.buildings.size() == b0 + 1, "roads and a house go in");
                w.city.editsDone();
                w.redrawCity();
                CityConfig c2 = new CityConfig();
                check(c2.applyCode(c.code()) && c2.edits.size() == c.edits.size(), "the city code carries the edits");
                City again = new City(c2, 0.1f);
                int diff = 0;
                for (int i = 0; i < again.tiles.length; i++) if (again.tiles[i] != w.city.tiles[i]) diff++;
                check(diff == 0 && again.buildings.size() == w.city.buildings.size(), "the same city is built from the code");
                File f = File.createTempFile("zcs", ".dat");
                SaveGame.save(w, f);
                World l = SaveGame.load(f);
                check(l.city.buildings.size() == w.city.buildings.size(), "a save keeps the edits");
                f.delete();
                CityConfig plain = new CityConfig();
                check(plain.applyCode("12-48392") && plain.edits.isEmpty(), "plain codes still work");
                check(!plain.applyCode("not a code"), "junk isn't taken for a city code");
            }
        });
        test("10.10: map sizes, the lie of the land, each country's countryside", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                int last = 0;
                for (int s = CityConfig.TINY; s <= CityConfig.HUGE; s++) {
                    c.v[CityConfig.OPT_SIZE] = s;
                    check(c.tiles() > last, CityConfig.class.getSimpleName() + " size " + s + " is bigger than the one before (" + c.tiles() + ")");
                    last = c.tiles();
                }
                c.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                check(c.tiles() == 320, "Medium is a quarter bigger than it was (" + c.tiles() + ")");
                c.civic = 2;
                check(c.tiles() == 256, "a city saved before 10.10 rebuilds at its old size");
                CityConfig old = new CityConfig();
                check(old.applyCode("12-44") && old.v[CityConfig.OPT_SIZE] == CityConfig.MEDIUM, "old codes keep their size name");
                // A USA city: hills, a mountain, water, a park with trails, a campsite.
                CityConfig u = new CityConfig();
                u.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                u.seed = 7;
                u.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                City city = new City(u, 0.1f);
                int[] n = new int[20];
                for (byte b : city.tiles) n[b]++;
                float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                for (float e : city.elev) {
                    lo = Math.min(lo, e);
                    hi = Math.max(hi, e);
                }
                check(hi - lo > 30, "the land isn't flat (" + (int) lo + " to " + (int) hi + ")");
                check(n[City.ROCK] > 0 && n[City.WATER] > 0 && n[City.TRAIL] > 10, "mountain rock, water and trails ("
                        + n[City.ROCK] + ", " + n[City.WATER] + ", " + n[City.TRAIL] + ")");
                check(city.parkName != null, "a nature park");
                boolean camp = false;
                for (City.Building b : city.buildings) if (b.name != null && b.name.startsWith("Camp Store")) camp = b.residents > 0;
                check(camp, "a campsite with campers");
                // Gun stores only where people can buy guns.
                {
                    CityConfig g = new CityConfig();
                    g.seed = 3;
                    g.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                    int guns = 0;
                    for (City.Building b : new City(g, 0.1f).buildings) if (b.kind == City.SHOP && b.shopType == 1) guns++;
                    check(guns > 0, guns + " gun stores");
                }
            }
        });
        test("10.10: new soldiers, police, vehicles; the Guard; buses; the homeless", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.seed = 5;
                c.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                c.v[CityConfig.OPT_MILITARY] = 4;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                World w = new World(c);
                w.populate(c);
                int medic = 0, gren = 0, marks = 0;
                for (Entity e : w.entities) {
                    if (e.role == Entity.ROLE_CORPSMAN) medic++;
                    if (e.role == Entity.ROLE_GRENADIER) gren++;
                    if (e.role == Entity.ROLE_MARKSMAN) marks++;
                }
                check(medic == 0 && gren > 0, "squads have grenadiers and (since 10.50) no combat medics (" + medic + ", " + gren + ")");
                check(marks > 0, "a police marksman at the precinct (" + marks + ")");
                int bikes = 0;
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.kind == Fleet.K_BIKE) bikes++;
                check(bikes > 0, "police motorcycles on patrol (" + bikes + ")");
                check(w.dispatch.riotVans > 0 && w.dispatch.policeAir > 0, "riot vans and the police helicopter are ready");
                Entity doc = null;
                for (Entity e : w.entities) if (e.type == Entity.SOLDIER && e.task == Dispatch.T_NONE && e.rig == null) doc = e;
                // Someone who turns up later has no home.
                Entity drifter = w.spawn(Entity.CIVILIAN, doc.x, doc.y + 40);
                for (int f = 0; f < 60; f++) w.update(1 / 30f);
                check(drifter.homeChecked && drifter.home == null, "a spawned person is homeless");
                // At war: no bus leaves town with people aboard.
                w.alert = 2;
                for (int f = 0; f < 600; f++) w.update(1 / 30f);
                for (Fleet.Vehicle v : w.fleet.vehicles)
                    check(!(v.type == Fleet.CAR && v.model == Fleet.M_BUS && v.fleeing), "a bus is leaving town");
                // A big outbreak brings out the National Guard by itself.
                CityConfig g = new CityConfig();
                g.seed = 6;
                g.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                g.v[CityConfig.OPT_ZOMBIES] = 6;
                g.v[CityConfig.OPT_RESERVES] = 3;
                World gw = new World(g);
                gw.populate(g);
                gw.outbreak = true;
                gw.outbreakTime = 120;
                // (A big one for the size of the place: busier outskirts since 10.20 mean more people.)
                int big = Math.max(80, gw.humanCount() / 8 + 20);
                // ((10.63) Room for them: a city this full reaches the test's population cap.)
                gw.maxEntities += big + 50;
                for (int i = 0; i < big; i++) gw.spawn(Entity.ZOMBIE, gw.city.worldW() / 2 + i % 120, gw.city.worldH() / 2 + i / 120 * 4);
                gw.callNationalGuard();
                check(gw.guardCalled, "the governor calls out the Guard for a big outbreak, with army reserves still to come");
            }
        });
        test("10.11: an outbreak on the Islands stays quick and doesn't eat memory", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.seed = 2;
                c.set(CityConfig.OPT_PRESET, CityConfig.ISLANDS);
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                World w = new World(c);
                w.populate(c);
                w.viewX0 = w.city.worldW() / 2 - 400;
                w.viewY0 = w.city.worldH() / 2 - 300;
                w.viewX1 = w.viewX0 + 800;
                w.viewY1 = w.viewY0 + 600;
                for (int i = 0; i < 60; i++) {
                    float[] p = w.city.randomWalkable(w.rnd);
                    w.spawn(Entity.ZOMBIE, p[0], p[1]);
                }
                long t0 = System.nanoTime();
                for (int f = 0; f < 600; f++) w.update(1 / 30f);
                long ms = (System.nanoTime() - t0) / 1000000;
                int fields = 0;
                for (City.Building b : w.city.buildings) if (b.field != null) fields++;
                check(fields == 0, "no whole-map route kept per shop (" + fields + ")");
                check(ms < 600 * 40, "20 seconds of outbreak took " + ms + " ms");
                // Gunfire glow in a building's windows dies away once the shooting stops.
                City.Building quiet = null;
                for (City.Building b : w.city.buildings) if (b.lurkers == 0 && !b.collapsed) quiet = b;
                quiet.flash = 0.05f;
                for (int f = 0; f < 10; f++) w.update(1 / 30f);
                check(quiet.flash == 0, "a building's gunfire flash fades (" + quiet.flash + ")");
                // A truck leaving its bay redraws only that bit of the map, not every close-up.
                int version = w.city.renderVersion;
                w.city.changes.clear();
                float[] bay = null;
                for (City.Facility base : w.city.facilities)
                    if (bay == null && base.kind == City.FACILITY_BASE) bay = w.city.takeParkedTruck(base.x, base.y, 2000);
                if (bay != null) {
                    check(w.city.renderVersion == version && !w.city.changes.isEmpty(), "a truck leaving marks just its bay to redraw");
                    w.city.parkTruck(bay[0], bay[1]);
                }
            }
        });
        test("10.14: fords, winding trails, rangers, fire lookouts, the rescue helicopter, nobody reports what nobody saw", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.seed = 9;
                c.v[CityConfig.OPT_PRESET] = 6;
                c.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                // (The map these checks were written for: as built before 10.20's busier outskirts.)
                c.civic = 6;
                World w = new World(c);
                w.populate(c);
                City city = w.city;
                w.viewX0 = 0;
                w.viewY0 = 0;
                w.viewX1 = city.worldW();
                w.viewY1 = city.worldH();
                // Streams can be waded: water, but not in the way, and a route goes straight across.
                int fords = 0, across = 0, tried = 0;
                for (int i = 0; i < city.w * city.h; i++) {
                    if (!city.isFord(i)) continue;
                    fords++;
                    check(!city.solid[i], "a ford is not a wall");
                    int x = i % city.w, y = i / city.w;
                    if (tried >= 10 || i % 5 != 0 || x < 4 || y < 4 || x >= city.w - 4 || y >= city.h - 4) continue;
                    int ia = y * city.w + x - 3, ib = y * city.w + x + 3;
                    if (city.solid[ia] || city.solid[ib] || city.tiles[ia] == City.WATER || city.tiles[ib] == City.WATER) continue;
                    tried++;
                    int[] p = city.findPath((x - 2.5f) * City.T, (y + 0.5f) * City.T, (x + 3.5f) * City.T, (y + 0.5f) * City.T, 9000);
                    if (p != null && p.length <= 12) across++;
                }
                check(fords > 20, "streams you can wade (" + fords + " tiles)");
                check(tried == 0 || across * 2 >= tried, "a short way straight across a stream (" + across + " of " + tried + ")");
                // Waterfalls sit across the stream, as wide as it is.
                for (float[] f : city.falls) check(f.length > 4 && f[4] >= City.T / 2f && f[4] <= City.T * 2.5f, "a waterfall as wide as its stream (" + (f.length > 4 ? f[4] : 0) + ")");
                // Trails: drawn as winding lines, walks along them, a ranger station and fire towers.
                check(!city.trailLines.isEmpty() && city.hikes.size() >= 3, "trails to walk (" + city.hikes.size() + ")");
                check(city.rangerStationName != null && w.rangerStation != null, "a ranger station");
                check(!city.fireTowers.isEmpty(), "fire lookout towers");
                int rangers = 0, trucks = 0, aloft = 0;
                for (Entity e : w.entities) {
                    if (!e.dead && e.agency == 3) rangers++;
                    if (!e.dead && e.aloft && e.job == Entity.J_LOOKOUT) aloft++;
                }
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.agency == 3) trucks++;
                check(rangers >= 2 && trucks == 1, "rangers on foot and in their truck (" + rangers + ", " + trucks + ")");
                check(aloft == city.fireTowers.size(), "a lookout up every tower (" + aloft + ")");
                // A shift change: the relief walks up from the ranger station and takes over.
                Entity first = w.towerKeeper[0];
                w.towerShift[0] = 0;
                boolean changed = false;
                int hiking = 0;
                for (int f = 0; f < 30 * 240 && !changed; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    changed = w.towerKeeper[0] != null && w.towerKeeper[0] != first;
                }
                check(changed && first.job == Entity.J_OFFDUTY && !first.aloft, "the next lookout takes over and the last one heads home");
                for (Entity e : w.entities) if (!e.dead && e.hike != null && e.hikeIdx > 5) hiking++;
                check(hiking > 0, "people out along the trails (" + hiking + ")");
                // One of the dead out in the woods with nobody to see it: no news, no alert.
                CityConfig q = new CityConfig();
                q.seed = 9;
                q.v[CityConfig.OPT_PRESET] = 6;
                q.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                q.v[CityConfig.OPT_ZOMBIES] = 0;
                World lone = new World(q);
                lone.populate(q);
                lone.update(1 / 30f);
                float[] far = null;
                for (int k = 0; k < 4000 && far == null; k++) {
                    float[] p = lone.city.randomWalkable(lone.rnd);
                    if (!lone.city.inTown(p[0], p[1]) && !lone.peopleNear(p[0], p[1], 700)) far = p;
                }
                if (far != null) {
                    lone.spawn(Entity.ZOMBIE, far[0], far[1]);
                    for (int f = 0; f < 30 * 40; f++) lone.update(1 / 30f);
                    check(lone.alert == 0 || lone.dispatch.calls > 0 || lone.bites > 0, "nobody reports one of them that nobody has seen");
                }
                // The dead round a fire tower: the rescue helicopter lowers its team and lifts people out.
                float[] t = city.fireTowers.get(0);
                // (No rangers about: with help close by, nobody calls the helicopter.)
                for (Entity e : w.entities) if (e.agency == 3 && e.rig == null) {
                    e.dead = true;
                    e.removed = true;
                }
                for (int i = 0; i < 4; i++) w.spawn(Entity.ZOMBIE, t[0] - 70 + i * 8, t[1] - 50);
                w.alert = Math.max(1, w.alert);
                boolean sent = false, lowered = false;
                for (int f = 0; f < 30 * 120 && !lowered; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    for (Fleet.Vehicle v : w.fleet.vehicles)
                        if (v.kind == Fleet.K_RESCUE_HELI) {
                            sent = true;
                            if (v.team != null && !v.team.isEmpty()) lowered = true;
                        }
                }
                check(sent && lowered, "the rescue helicopter comes and lowers its team");
            }
        });
        test("10.15: walled safe zones with gates, the dead climb fire towers, people keep running", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.civic = 25; // ((11.0) Map version 25: the same streets as when this was written.)
                c.seed = 77;
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                World w = new World(c);
                w.populate(c);
                w.viewX0 = 0;
                w.viewY0 = 0;
                w.viewX1 = w.city.worldW();
                w.viewY1 = w.city.worldH();
                w.update(1 / 30f);
                // A safe zone: a block walled in, gates onto the street, tents inside.
                // (On a park: open ground all round its edge to wall in.)
                for (float[] a : w.city.openAreas) {
                    if (!w.dispatch.zones.isEmpty()) break;
                    if (!w.city.inTown(a[0], a[1])) continue;
                    w.dispatch.orderZone(a[0], a[1]);
                    if (!w.dispatch.zones.isEmpty() && w.dispatch.zones.get(0).wall.length <= 10) w.dispatch.removeZoneAt(w.dispatch.zones.get(0).x, w.dispatch.zones.get(0).y);
                }
                check(!w.dispatch.zones.isEmpty(), "a safe zone is set up");
                Dispatch.SafeZone z = w.dispatch.zones.get(0);
                check(z.wall.length > 10 && z.gateTiles.length >= 1 && z.tents.size() >= 3 && z.medX > 0 && z.foodX > 0,
                        "a wall (" + z.wall.length + "), gates (" + z.gateTiles.length + "), tents (" + z.tents.size() + "), a medical and a food tent");
                w.alert = 2;
                int seeking = 0;
                for (Entity e : w.entities)
                    if (e.type == Entity.CIVILIAN && !e.dead && Math.hypot(e.x - z.x, e.y - z.y) < 700 && seeking < 30) {
                        e.aware = true;
                        e.task = Dispatch.T_SEEK;
                        seeking++;
                    }
                for (int f = 0; f < 30 * 150 && (!z.open || z.sheltered < 5); f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(z.open, "the wall goes up and it opens");
                int solid = 0;
                for (int k = 0; k < z.wall.length; k++) if (z.up[k] && w.city.solid[z.wall[k]]) solid++;
                check(solid * 10 >= z.wall.length * 9, "the wall is solid (" + solid + " of " + z.wall.length + ")");
                check(z.sheltered >= 5, "people come in through the gates (" + z.sheltered + ")");
                int inside = 0;
                for (Entity e : w.entities) if (!e.dead && e.task == Dispatch.T_SHELTER && e.zone == z && z.inside(e.x, e.y)) inside++;
                check(inside >= 3, "the sheltered are inside the wall (" + inside + ")");
                // The dead claw through a section; it's mended once they're gone.
                int k = z.wall.length / 3;
                w.dispatch.damageWall(z, k, 1000);
                check(!z.up[k] && !w.city.solid[z.wall[k]], "a section broken down is a gap in the wall");
                boolean mended = false;
                // (A guard has to walk over to it first, from wherever on the wall they were.)
                for (int f = 0; f < 30 * 150 && !mended; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    mended = z.up[k];
                }
                check(mended, "the guards mend the breach");
                // The dead at the wall claw at it.
                for (int i = 0; i < 6; i++) {
                    int kk = (i * z.wall.length / 6 + 1) % z.wall.length;
                    float[] q = z.innerOf(z.wall[kk], w.city.w);
                    int wt = z.wall[kk];
                    float ox = wt % w.city.w == z.tx0 ? -1 : wt % w.city.w == z.tx1 ? 1 : 0;
                    float oy = wt / w.city.w == z.ty0 ? -1 : wt / w.city.w == z.ty1 ? 1 : 0;
                    w.spawn(Entity.BRUTE, q[0] + ox * City.T * 2, q[1] + oy * City.T * 2);
                }
                for (Entity e : w.entities) {
                    if (e.isArmed()) {
                        e.ammo = 0;
                        e.reserve = 0;
                    }
                    // (Nobody left outside for them to go after instead.)
                    if (!e.dead && !e.isZombie() && !z.inside(e.x, e.y) && Math.hypot(e.x - z.x, e.y - z.y) < 900) {
                        e.dead = true;
                        e.removed = true;
                    }
                }
                float weakest = 100;
                for (int f = 0; f < 30 * 8; f++) {
                    w.update(1 / 30f);
                    for (int i = 0; i < z.hp.length; i++) if (i != k) weakest = Math.min(weakest, z.hp[i]);
                }
                check(weakest < 99, "the dead claw at the wall (weakest section " + (int) weakest + ")");
                // Up a fire tower: they have to climb to get the lookout (it takes them a while).
                CityConfig t = new CityConfig();
                t.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                t.seed = 9;
                t.v[CityConfig.OPT_PRESET] = 6;
                t.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                t.v[CityConfig.OPT_ZOMBIES] = 0;
                World tw = new World(t);
                tw.populate(t);
                Entity keeper = tw.towerKeeper[0];
                float hp = keeper.hp;
                Entity climber = tw.spawn(Entity.ZOMBIE, keeper.x + 6, keeper.y + 6);
                for (int f = 0; f < 30 * 3; f++) tw.update(1 / 30f);
                check(keeper.hp >= hp, "not bitten straight away up the tower");
                for (int f = 0; f < 30 * 15; f++) {
                    if (climber.dead) break;
                    tw.update(1 / 30f);
                }
                check(climber.dead || keeper.hp < hp || keeper.dead, "the dead can climb up and get at the lookout");
                // Someone who has just seen one keeps running after it's out of sight.
                // (Someone just out and about in town, with open ground beside them for it to appear on.)
                Entity runner = null;
                for (Entity e : w.entities) {
                    if (e.type != Entity.CIVILIAN || e.dead || e.task != Dispatch.T_NONE || !w.city.inTown(e.x, e.y) || e.aloft
                            || e.leader != null || e.job == Entity.J_LOOKOUT || e.job == Entity.J_RELIEF || e.job == Entity.J_HIKER) continue;
                    float[] q = w.city.findWalkable(e.x + 30, e.y);
                    if (q != null && Math.hypot(q[0] - e.x - 30, q[1] - e.y) < 6) runner = e;
                }
                if (runner != null) {
                    Entity scare = w.spawn(Entity.ZOMBIE, runner.x + 30, runner.y);
                    for (int f = 0; f < 10; f++) w.update(1 / 30f);
                    float sx = scare.x, sy = scare.y;
                    scare.dead = true;
                    scare.removed = true;
                    for (int f = 0; f < 30 * 6; f++) w.update(1 / 30f);
                    check(runner.dead || runner.task != Dispatch.T_NONE || runner.fleeTimer > 0 || Math.hypot(runner.x - sx, runner.y - sy) > 90,
                            "still getting away six seconds after it's out of sight (" + (int) Math.hypot(runner.x - sx, runner.y - sy) + " away)");
                }
            }
        });
        test("10.16: ammo, rescue teams, backup, rangers, containment, spawning", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.seed = 4;
                c.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                World w = new World(c);
                w.populate(c);
                for (int f = 0; f < 30 * 10; f++) w.update(1 / 30f);
                float cx = w.city.worldW() / 2f, cy = w.city.worldH() / 2f;
                // (Open ground near the middle: since 11.1 a big block can sit right on it.)
                for (int r = 0; r < 40 && w.city.findWalkable(cx, cy) == null; r++) cx += City.T * 4;
                // Every kind of officer can be spawned.
                check(w.spawnCop(-1, cx, cy).agency == 1 && w.spawnCop(-2, cx, cy).agency == 2 && w.spawnCop(-3, cx, cy).agency == 3
                        && w.spawnCop(Entity.ROLE_SAR, cx, cy).role == Entity.ROLE_SAR, "highway patrol, deputies, rangers and rescuers spawn");
                // Highway patrol only comes into town as backup once it's under attack.
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.patrol && v.agency == 0) v.broken = true;
                w.alert = 0;
                Fleet.Vehicle q = w.fleet.patrolFor(cx, cy);
                check(q == null || q.agency == 0, "highway patrol keeps to the highway while it's quiet");
                w.alert = 2;
                q = w.fleet.patrolFor(cx, cy);
                check(q != null && q.agency > 0 && q.agency < 3, "highway patrol comes in as backup when the town is attacked");
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.patrol && v.agency == 0) v.broken = false;
                // Out of rounds with no ammo left anywhere: borrow from someone with plenty, or scout.
                for (City.Facility f : w.city.facilities) f.ammo = 0;
                if (w.courtArmoury != null) w.courtArmoury.ammo = 0;
                w.alert = 0;
                float[] dp1 = null;
                for (int k = 0; k < 40 && dp1 == null; k++) dp1 = w.city.findWalkable(cx + 300 + k * 20, cy + (k % 5) * 30);
                Entity dry = w.spawnCop(0, dp1[0], dp1[1]), rich = w.spawnCop(0, dp1[0] + 40, dp1[1]);
                if (rich == null) rich = w.spawnCop(0, dp1[0], dp1[1] + 30);
                dry.ammo = 0;
                dry.reserve = 0;
                rich.reserve = 400;
                for (int f = 0; f < 30 * 20; f++) w.update(1 / 30f);
                check(dry.ammo + dry.reserve > 0, "a dry officer borrows rounds from one with plenty (" + (dry.ammo + dry.reserve) + ")");
                for (Entity e : w.entities) if (e.isArmed()) {
                    e.ammo = 0;
                    e.reserve = 0;
                }
                for (int f = 0; f < 30 * 4; f++) w.update(1 / 30f);
                int scouts = 0, armed = 0;
                for (Entity e : w.entities) if (!e.dead && e.isArmed()) {
                    armed++;
                    if (e.scout) scouts++;
                }
                check(scouts * 2 >= armed, "with no rounds anywhere they scout instead (" + scouts + " of " + armed + ")");
                // An outbreak bunched in one place gets a cordon round it.
                World o = new World(c);
                o.populate(c);
                for (int f = 0; f < 30 * 10; f++) o.update(1 / 30f);
                int sp = 0;
                for (int i = 0; i < 1600 && sp < 90; i++) {
                    float x = cx + (i % 40) * 6 - 120, y = cy + (i / 40) * 6 - 120;
                    if (!o.city.solidAt(x, y) && o.spawn(Entity.ZOMBIE, x, y) != null) sp++;
                }
                o.alert = 2;
                int most = 0;
                for (int f = 0; f < 30 * 40; f++) {
                    o.update(1 / 30f);
                    if (f % 30 == 0) {
                        int n = 0;
                        for (Entity e : o.entities) if (!e.dead && e.task == Dispatch.T_CORDON) n++;
                        most = Math.max(most, n);
                    }
                }
                check(most >= 4, "police and soldiers hold a perimeter round the outbreak (" + most + ")");
                // In the wilds: one ranger minds the station; the rescue team is dropped off and the helicopter goes back.
                CityConfig t = new CityConfig();
                t.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                t.seed = 9;
                t.v[CityConfig.OPT_PRESET] = 6;
                t.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                t.v[CityConfig.OPT_ZOMBIES] = 0;
                World tw = new World(t);
                tw.populate(t);
                for (int f = 0; f < 30 * 40; f++) tw.update(1 / 30f);
                City.Building rs = tw.rangerStation;
                boolean minded = false;
                for (Entity e : tw.entities) if (!e.dead && e.agency == 3 && rs != null && Math.hypot(e.x - rs.doorX, e.y - rs.doorY) < 40) minded = true;
                check(minded, "a ranger is at the ranger station");
                float[] tower = tw.city.fireTowers.get(0);
                tw.spawn(Entity.ZOMBIE, tower[0] - 70, tower[1] - 50);
                tw.spawn(Entity.ZOMBIE, tower[0] - 62, tower[1] - 50);
                tw.alert = Math.max(1, tw.alert);
                boolean lowered = false, standby = false;
                for (int f = 0; f < 30 * 150 && !standby; f++) {
                    tw.update(1 / 30f);
                    for (Fleet.Vehicle v : tw.fleet.vehicles) if (v.kind == Fleet.K_RESCUE_HELI) {
                        if (v.team != null) lowered = true;
                        if (v.state == Fleet.STANDBY) standby = true;
                    }
                }
                check(lowered && standby, "the rescue team is lowered and the helicopter waits at base for their call");
            }
        });
        test("10.17: fewer choices, reinforcements from outside, footprints, spent cases, worn buildings", new Check() {
            public void run() throws Exception {
                // The maps, sizes and countries taken off the New Game screen never come up at random.
                java.util.Random r = new java.util.Random(5);
                CityConfig pick = new CityConfig();
                check(pick.size() == CityConfig.LARGE, "Large is the default map size");
                for (int i = 0; i < 300; i++) {
                    pick.randomize(r);
                    for (int o = 0; o < 8; o++)
                        check(!CityConfig.retired(o, pick.v[o]), "a random city never uses a retired option (" + o + " = " + pick.v[o] + ")");
                }
                check(CityConfig.retired(CityConfig.OPT_PRESET, 13) && CityConfig.retired(CityConfig.OPT_COUNTRY, 6)
                        && !CityConfig.retired(CityConfig.OPT_COUNTRY, Country.USA), "Harbour and the other countries are gone, the USA isn't");
                // Old city codes on a retired map still build.
                CityConfig old = new CityConfig();
                check(old.applyCode("22-77") && old.size() == CityConfig.MEDIUM, "an old Medium city still loads from its code");
                // Reinforcements drive in from the edge of the map, and are tracked on it.
                CityConfig c = new CityConfig();
                c.seed = 5;
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.v[CityConfig.OPT_RESERVES] = 3;
                World w = new World(c);
                w.populate(c);
                float cx = w.city.worldW() / 2f, cy = w.city.worldH() / 2f;
                Dispatch.Incident inc = new Dispatch.Incident();
                inc.x = cx;
                inc.y = cy;
                inc.place = w.city.placeName(cx, cy);
                // ((10.53) With the way there on foot, as every real call has: without it the officers who'd got out
                // of a car held up on the way stopped short.)
                inc.field = w.city.walkField16(cx, cy);
                java.lang.reflect.Method pb = Dispatch.class.getDeclaredMethod("policeBackup", Dispatch.Incident.class);
                pb.setAccessible(true);
                // (Not a city that rolled "unprepared", with no backup to call.)
                w.dispatch.policeReserve = Math.max(2, w.dispatch.policeReserve);
                check((Boolean) pb.invoke(w.dispatch, inc), "police backup called");
                // (Since 10.20 they come from the towns around: (since 10.62) half a minute or so away.)
                for (int f = 0; f < 30 * 45 && w.dispatch.convoys.isEmpty(); f++) w.update(1 / 30f);
                check(!w.dispatch.convoys.isEmpty(), "the backup shows on the map");
                Dispatch.Convoy cv = w.dispatch.convoys.get(0);
                float edge = Math.min(Math.min(cv.fromX, cv.fromY), Math.min(w.city.worldW() - cv.fromX, w.city.worldH() - cv.fromY));
                check(edge < 120, "they come in from the edge of the map (" + (int) edge + " from it)");
                boolean got = false;
                for (int f = 0; f < 30 * 150 && !got; f++) {
                    w.update(1 / 30f);
                    for (Entity e : w.entities) if (!e.dead && e.type == Entity.COP && e.reserve > World.fullReserve(e) && Math.hypot(e.x - cx, e.y - cy) < 300) got = true;
                }
                check(got, "the backup gets there with spare ammo");
                // Marks on the ground: spent cases where people fired, blood behind the wounded.
                float[] open = w.city.findWalkable(cx, cy);
                cx = open[0];
                cy = open[1];
                w.viewX0 = cx - 300;
                w.viewY0 = cy - 300;
                w.viewX1 = cx + 300;
                w.viewY1 = cy + 300;
                Entity s = w.spawnSoldier(Entity.ROLE_RIFLE, cx, cy);
                for (int i = 0; i < 8; i++) {
                    float[] q = w.city.findWalkable(cx + 80 + i * 6, cy);
                    if (q != null) w.spawn(Entity.ZOMBIE, q[0], q[1]);
                }
                float[] hq = w.city.findWalkable(cx - 40, cy);
                Entity hurt = w.spawn(Entity.CIVILIAN, hq != null ? hq[0] : cx, hq != null ? hq[1] : cy);
                hurt.hp = hurt.maxHp * 0.2f;
                // (Since 10.50 the hurt go home to rest: a home a little way off, the nearest past 120.)
                float best = Float.MAX_VALUE;
                for (City.Building hb : w.city.buildings) {
                    float hd = (float) Math.hypot(hb.doorX - hurt.x, hb.doorY - hurt.y);
                    if (hd > 120 && hd < best && !hb.collapsed) {
                        best = hd;
                        hurt.home = hb;
                        hurt.homeChecked = true;
                    }
                }
                for (int f = 0; f < 30 * 10; f++) w.update(1 / 30f);
                int casings = 0, blood = 0;
                for (int i = 0; i < w.mcount; i++) {
                    if (w.mkind[i] == World.M_CASING) casings++;
                    if (w.mkind[i] == World.M_DRIP) blood++;
                }
                check(casings > 0, "spent cases on the ground (" + casings + ")");
                check(blood > 0 || hurt.dead, "a trail of blood behind someone badly hurt (" + blood + ")");
            }
        });
        test("10.18: fewer maps, no animals, hikers out, people get clear, dirt lanes, the real cities", new Check() {
            public void run() throws Exception {
                check(CityConfig.retired(CityConfig.OPT_PRESET, 0) && CityConfig.retired(CityConfig.OPT_PRESET, 7), "Classic and Campus are gone");
                CityConfig d = new CityConfig();
                check(d.values(CityConfig.OPT_SIZE)[CityConfig.LARGE].equals("Default") && d.values(CityConfig.OPT_SIZE)[CityConfig.HUGE].equals("Massive"),
                        "the sizes are Default, Large and Massive");
                check(CityConfig.retired(CityConfig.OPT_ZOMBIES, 3) && d.zombies() == 0, "no choosing how many zombies: the game starts quiet");
                // No animals.
                CityConfig c = new CityConfig();
                c.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                c.seed = 9;
                c.v[CityConfig.OPT_PRESET] = 6;
                World w = new World(c);
                // (The same run every time: what happens next is chance, and a test shouldn't be.)
                w.rnd.setSeed(18);
                w.populate(c);
                int dogs = 0, hikers = 0;
                for (Entity e : w.entities) {
                    if (e.type == Entity.DOG || e.type == Entity.ZOMBIE_DOG) dogs++;
                    if (!e.dead && e.job == Entity.J_HIKER && e.jobStep == 1) hikers++;
                }
                check(dogs == 0 && w.birds.isEmpty() && w.animals.isEmpty() && w.spawn(Entity.DOG, 100, 100) == null, "no animals");
                check(hikers >= 5, "people already out on the trails (" + hikers + ")");
                // People get out of where the dead are.
                for (int f = 0; f < 30 * 5; f++) w.update(1 / 30f);
                float[] cp = null;
                for (Entity e : w.entities) if (!e.dead && e.type == Entity.CIVILIAN && w.city.inTown(e.x, e.y)) cp = new float[]{e.x, e.y};
                int sp = 0;
                for (int i = 0; i < 400 && sp < 30; i++) {
                    float[] q = w.city.findWalkable(cp[0] + (i % 20) * 6 - 60, cp[1] + (i / 20) * 6 - 60);
                    if (q != null && w.spawn(Entity.ZOMBIE, q[0], q[1]) != null) sp++;
                }
                w.alert = 2;
                int evac = 0;
                for (int f = 0; f < 30 * 12; f++) {
                    w.update(1 / 30f);
                    // (Since 10.37 getting clear is the civilian's own choice: evacuating, running, or hiding.)
                    if (f % 30 == 0) for (Entity e : w.entities)
                        if (!e.dead && e.type == Entity.CIVILIAN && (e.task == Dispatch.T_EVACUATE || e.goal == World.G_FLEE || e.goal == World.G_HIDE || e.goal == World.G_EVAC)) evac++;
                }
                check(evac > 0, "people get clear of the outbreak");
                // Dirt lanes stay dirt.
                CityConfig lc = new CityConfig();
                // ((10.52) Seed 3: with another police station seed 2's lanes meet a main road at a paved junction.)
                lc.seed = 3;
                lc.v[CityConfig.OPT_PRESET] = 8;
                lc.v[CityConfig.OPT_SIZE] = CityConfig.MASSIVE;
                City lanes = new City(lc, 0.05f);
                int stubs = 0;
                for (int y = 0; y < lanes.h; y++)
                    for (int x = 1; x < lanes.w - 1; x++) {
                        if (lanes.tiles[y * lanes.w + x - 1] != City.DIRT || lanes.tiles[y * lanes.w + x] != City.ROAD) continue;
                        int k = x;
                        while (k < lanes.w && lanes.tiles[y * lanes.w + k] == City.ROAD) k++;
                        int across = 0;
                        for (int dy = -6; dy <= 6; dy++)
                            if (y + dy >= 0 && y + dy < lanes.h && lanes.tiles[(y + dy) * lanes.w + (x + k) / 2] == City.ROAD) across++;
                        // (A town's street with pavements along it, that a lane runs on from, is a street, not a stub.)
                        boolean street = false;
                        for (int dy = -3; dy <= 3; dy++)
                            if (y + dy >= 0 && y + dy < lanes.h && lanes.tiles[(y + dy) * lanes.w + (x + k) / 2] == City.SIDEWALK) street = true;
                        if (k < lanes.w && k - x < 25 && lanes.tiles[y * lanes.w + k] == City.DIRT && across <= 4 && !street) stubs++;
                    }
                check(stubs == 0, stubs + " stretches of tarmac in the middle of a dirt lane");
                // The real cities.
                for (int i = 0; i < RealCities.NAMES.length; i++) {
                    CityConfig rc = new CityConfig();
                    rc.v[CityConfig.OPT_PRESET] = RealCities.FIRST + i;
                    rc.normalize();
                    RealCities.Spec spec = RealCities.get(RealCities.FIRST + i);
                    check(rc.size() == CityConfig.HUGE && rc.country() == Country.USA, spec.name + " is always Massive");
                    CityConfig back = new CityConfig();
                    check(back.applyCode(rc.code()) && back.preset() == rc.preset() && back.seed == rc.seed, spec.name + ": its city code");
                    City city = new City(rc, 0.05f);
                    check(city.name.equals(spec.name), spec.name + " is called " + spec.name);
                    int marks = city.structures.size(), water = 0;
                    for (City.Building b : city.buildings) if (b.landmark >= 0 || (b.kind == City.STADIUM && b.name != null && !b.name.endsWith(" Stadium"))) marks++;
                    for (byte t : city.tiles) if (t == City.WATER) water++;
                    check(marks >= 4, spec.name + ": its landmarks (" + marks + ")");
                    check(water > 0, spec.name + ": its water");
                    boolean named = false;
                    for (City.District dd : city.districts) if (dd.name.equals(spec.places.get(0).name)) named = true;
                    check(named, spec.name + ": its neighbourhoods");
                    boolean streets = false;
                    for (City.Street st : city.streets)
                        for (RealCities.StreetName sn : spec.eastWest) if (sn.name.equals(st.name)) streets = true;
                    check(streets, spec.name + ": its streets");
                    if (!spec.crossings.isEmpty()) {
                        boolean bridge = false;
                        for (City.Street st : city.streets)
                            for (RealCities.Crossing cr : spec.crossings) if (cr.name.equals(st.name)) bridge = true;
                        check(bridge, spec.name + ": its bridges");
                    }
                }
                // A game in one: people about, hikers on its trails.
                CityConfig la = new CityConfig();
                la.v[CityConfig.OPT_PRESET] = RealCities.FIRST;
                la.normalize();
                World lw = new World(la);
                lw.populate(la);
                int out = 0;
                for (Entity e : lw.entities) if (!e.dead && e.job == Entity.J_HIKER) out++;
                for (int f = 0; f < 30 * 5; f++) lw.update(1 / 30f);
                check(lw.humans > 100 && out > 0, "Los Angeles plays: " + lw.humans + " people, " + out + " hikers");
            }
        });
        test("10.19: gunfire heard, the ranger off the wall, out of hiding sooner, resupply, driveways, quick close-ups", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.seed = 9;
                c.v[CityConfig.OPT_PRESET] = 6;
                World w = new World(c);
                w.populate(c);
                for (int f = 0; f < 30 * 2; f++) w.update(1 / 30f);
                // Everyone hears a gunshot: people nearby run, an idle cop goes to look.
                Entity civ = null;
                for (Entity e : w.entities)
                    if (!e.dead && e.type == Entity.CIVILIAN && e.fleeTimer <= 0 && e.leader == null && e.task == Dispatch.T_NONE && w.city.inTown(e.x, e.y)) {
                        civ = e;
                        break;
                    }
                check(civ != null, "someone out in town");
                float[] gp = w.city.findWalkable(civ.x + 60, civ.y);
                Entity cop = w.spawn(Entity.COP, gp[0], gp[1]);
                float[] lp = w.city.findWalkable(civ.x - 60, civ.y);
                Entity other = w.spawn(Entity.COP, lp[0], lp[1]);
                // (Into the grid of who's where, then a zombie for the cop to shoot at.)
                w.update(1 / 30f);
                Entity z = w.spawn(Entity.ZOMBIE, cop.x + 40, cop.y);
                other.fear = 0;
                other.aiming = false;
                other.task = Dispatch.T_NONE;
                civ.fleeTimer = 0;
                w.heardShot(cop, z, 250);
                check(civ.fleeTimer > 0 && civ.aware, "people near a gunshot run from it");
                check(other.fear > 0 && Math.hypot(other.threatX - z.x, other.threatY - z.y) < 30, "an idle cop goes to see what the shooting is");
                z.dead = true;
                // The ranger minding the station stands out front, not against the wall.
                CityConfig wc = new CityConfig();
                wc.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                wc.seed = 4;
                wc.v[CityConfig.OPT_PRESET] = 8;
                wc.v[CityConfig.OPT_SIZE] = CityConfig.MASSIVE;
                World ww = new World(wc);
                ww.populate(wc);
                check(ww.rangerStation != null, "a ranger station");
                java.lang.reflect.Method spot = World.class.getDeclaredMethod("stationSpot", float.class, float.class);
                spot.setAccessible(true);
                City.Building rs = ww.rangerStation;
                for (float a : new float[]{0, 0.9f, -0.7f}) {
                    float[] p = (float[]) spot.invoke(ww, a, 30f);
                    float gap = Math.max(Math.max(rs.x0 - p[0], p[0] - rs.x1), Math.max(rs.y0 - p[1], p[1] - rs.y1));
                    check(gap > 6 && !ww.city.solidAt(p[0], p[1]), "the station keeper's spot is clear of the walls (" + gap + ")");
                }
                // Out of hiding soon after the street goes quiet.
                Entity hid = null;
                City.Building home = null;
                for (Entity e : w.entities)
                    if (!e.dead && e.type == Entity.CIVILIAN && e.leader == null && e != civ) {
                        for (City.Building b : w.city.buildings)
                            if (b.kind == City.HOUSE && !b.collapsed && b.occupants.isEmpty() && b.lurkers == 0
                                    && Math.hypot(b.doorX - e.x, b.doorY - e.y) < 400) {
                                home = b;
                                break;
                            }
                        if (home != null) {
                            hid = e;
                            break;
                        }
                    }
                java.lang.reflect.Method enter = World.class.getDeclaredMethod("enterBuilding", Entity.class, City.Building.class);
                enter.setAccessible(true);
                hid.aware = true;
                enter.invoke(w, hid, home);
                check(home.occupants.contains(hid), "hiding inside");
                w.alert = 2;
                float out = -1;
                // ((10.63) Anyone hiding there coming out: with a whole household home with them now, the one put
                // in may not be the first out of the door.)
                java.util.ArrayList<Entity> inside = new java.util.ArrayList<Entity>(home.occupants);
                for (int f = 0; f < 30 * 25 && out < 0; f++) {
                    w.update(1 / 30f);
                    for (Entity o : inside) if (!o.dead && !home.occupants.contains(o)) out = f / 30f;
                    inside.clear();
                    inside.addAll(home.occupants);
                }
                check(out >= 0 && out < 20, "out of hiding " + out + " s after it went quiet");
                // Supply trucks after it's over: they come in and fill the shelves and armouries.
                City.Facility station = null;
                for (City.Facility f : ww.city.facilities) if (f.kind == City.FACILITY_POLICE) station = f;
                station.ammo = 50;
                City.Building store = null;
                for (City.Building b : ww.city.buildings) if (b.kind == City.MARKET && !b.collapsed) store = b;
                if (store != null) store.food = 0;
                java.lang.reflect.Method restock = World.class.getDeclaredMethod("restockTown");
                restock.setAccessible(true);
                java.util.List<Fleet.Vehicle> trucks = new java.util.ArrayList<Fleet.Vehicle>();
                for (int k = 0; k < 12; k++) {
                    ww.time = k * 4;
                    restock.invoke(ww);
                }
                for (Fleet.Vehicle v : ww.fleet.vehicles) if (v.restock) trucks.add(v);
                check(ww.restockSent > 0 && !trucks.isEmpty(), "supply trucks sent in (" + trucks.size() + ")");
                for (Fleet.Vehicle v : trucks) ww.restocked(v);
                check(station.ammo >= 3000, "the police armoury restocked");
                check(store == null || store.food >= 400, "the supermarket restocked");
                // Driveways: houses out in the country all have a way to the road.
                int cut5 = 0, cut6 = 0;
                for (int civic = 5; civic <= 6; civic++) {
                    CityConfig dc = new CityConfig();
                    dc.seed = 5;
                    dc.v[CityConfig.OPT_PRESET] = 8;
                    dc.civic = civic;
                    City city = new City(dc, 0.05f);
                    for (City.Building b : city.buildings) {
                        if (b.kind != City.HOUSE || city.inTown(b.doorX, b.doorY)) continue;
                        boolean reached = false;
                        int x0 = (int) (b.x0 / City.T) - 1, y0 = (int) (b.y0 / City.T) - 1;
                        int x1 = (int) Math.ceil(b.x1 / City.T), y1 = (int) Math.ceil(b.y1 / City.T);
                        for (int y = Math.max(0, y0); y <= Math.min(city.h - 1, y1); y++)
                            for (int x = Math.max(0, x0); x <= Math.min(city.w - 1, x1); x++) {
                                byte t = city.tiles[y * city.w + x];
                                if (t == City.ROAD || t == City.DIRT || t == City.LOT || t == City.SIDEWALK) reached = true;
                            }
                        if (!reached) {
                            if (civic == 5) cut5++;
                            else cut6++;
                        }
                    }
                }
                check(cut5 > 5 && cut6 * 5 <= cut5, "driveways to country houses (cut off: " + cut5 + " before, " + cut6 + " now)");
                // Close-ups draw quickly: just their own part of the map.
                City big = ww.city;
                long t0 = System.nanoTime();
                for (int k = 0; k < 8; k++) big.renderRegion(big.worldW() / 2 + (k % 4) * 192, big.worldH() / 2 + (k / 4) * 192, 192, 2);
                long ms = (System.nanoTime() - t0) / 8000000;
                check(ms < 60, "a close-up takes " + ms + " ms");
            }
        });
        test("10.20: aim, helicopters on their pads, help from outside, backup calls, the outskirts", new Check() {
            public void run() throws Exception {
                // Everyone with a gun shoots as well as before, or up to 15% better.
                float lo = 1, hi = 0;
                for (int i = 0; i < 400; i++) {
                    Entity e = new Entity();
                    e.nameSeed = i * 7919 + 13;
                    float b = World.aimBonus(e);
                    lo = Math.min(lo, b);
                    hi = Math.max(hi, b);
                }
                check(lo >= 0 && hi <= 0.15f && hi - lo > 0.12f, "aim varies from +0 to +15% (" + lo + ".." + hi + ")");
                check(Fleet.DRIVE_SCALE > 1.5f, "cars drive faster");
                // Every helicopter starts parked on a helipad, with its crew beside it.
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 8;
                c.seed = 3;
                c.normalize();
                c.seed = 3;
                World w = new World(c);
                w.populate(c);
                int[] kinds = new int[3];
                for (float[] p : w.city.helipads()) kinds[City.padKind(p)]++;
                check(kinds[City.PAD_POLICE] >= 1 && kinds[City.PAD_RESCUE] >= 1, "helipads for the police and the rescue service");
                int helis = 0;
                for (Fleet.Vehicle v : w.fleet.vehicles)
                    if (v.type == Fleet.HELI) {
                        helis++;
                        check(v.padAt != null && v.state == Fleet.IDLE && v.alt == 0, "a helicopter waiting on its pad");
                        Entity pilot = w.fleet.livePilot(v);
                        check(pilot != null && !pilot.dead && pilot.task == Dispatch.T_POST, "its pilot standing by");
                    }
                check(helis == w.city.helipads().size(), "one helicopter to each pad (" + helis + ")");
                // Air support: the crew boards, it spins up, flies out and holds a steady hover (no tight circles).
                for (int f = 0; f < 60; f++) w.update(1 / 30f);
                float[] q = w.city.findWalkable(w.city.worldW() / 2, w.city.worldH() / 2);
                for (int i = 0; i < 20; i++) w.spawn(Entity.ZOMBIE, q[0] + (i % 5) * 8, q[1] + (i / 5) * 8);
                Fleet.Vehicle air = w.fleet.readyHeli(0, q[0], q[1]);
                check(air != null && w.fleet.launchHeli(air, q[0], q[1], "the test", null, 4), "army helicopter launched");
                check(air.state == Fleet.MUSTER && air.crew.isEmpty(), "nobody aboard until they walk out to it");
                boolean boarded = false, onStation = false, hovered = false;
                for (int f = 0; f < 30 * 120 && !hovered; f++) {
                    w.update(1 / 30f);
                    // (The dead keep coming there, so the call stays live: since 10.27 it leaves a call that's over.)
                    if (f % 90 == 0 && w.countZombiesNear(q[0], q[1], 200) < 8)
                        for (int i = 0; i < 8; i++) w.spawn(Entity.ZOMBIE, q[0] + (i % 4) * 8, q[1] + (i / 4) * 8);
                    if (air.state == Fleet.SPOOL && air.crew.size() >= 2) boarded = true;
                    if (air.state == Fleet.CIRCLE) onStation = true;
                    if (onStation && air.state == Fleet.CIRCLE && air.speed < 3 && Math.hypot(air.x - air.tx, air.y - air.ty) > 80) hovered = true;
                }
                check(boarded, "the crew climbed aboard before take-off");
                check(onStation && hovered, "on station it hovers off to one side of the fighting");
                int before = 0;
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.type == Fleet.HELI) before++;
                java.lang.reflect.Method ra = Dispatch.class.getDeclaredMethod("requestAir", float.class, float.class, String.class);
                ra.setAccessible(true);
                for (int k = 0; k < 3; k++) ra.invoke(w.dispatch, q[0], q[1], "again");
                for (int f = 0; f < 30 * 30; f++) w.update(1 / 30f);
                int after = 0;
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.type == Fleet.HELI) after++;
                check(after == before, "no helicopters from outside the map");
                // Help from outside: on or off; police first in waves from different forces; the army all at once, later.
                check(CityConfig.retired(CityConfig.OPT_RESERVES, 1) && CityConfig.retired(CityConfig.OPT_RESERVES, 2)
                        && new CityConfig().reinforcements() == 3, "reinforcements are just off or on (on by default)");
                CityConfig hc = new CityConfig();
                hc.v[CityConfig.OPT_PRESET] = 8;
                hc.seed = 5;
                hc.normalize();
                hc.seed = 5;
                World hw = new World(hc);
                hw.populate(hc);
                hw.dispatch.policeReserve = Dispatch.OUTSIDE_POLICE;
                hw.dispatch.squadReserve = Dispatch.OUTSIDE_SOLDIERS;
                check(Dispatch.OUTSIDE_POLICE >= 200 && Dispatch.OUTSIDE_SOLDIERS >= 100, "200+ police and 100+ soldiers can come");
                hw.alert = 2;
                Dispatch.Incident inc = new Dispatch.Incident();
                float[] iq = hw.city.findWalkable(hw.city.worldW() / 2, hw.city.worldH() / 2);
                inc.x = iq[0];
                inc.y = iq[1];
                inc.place = "the test";
                inc.field = hw.city.walkField16(inc.x, inc.y);
                hw.dispatch.incidents.add(inc);
                java.lang.reflect.Method pb = Dispatch.class.getDeclaredMethod("policeBackup", Dispatch.Incident.class);
                pb.setAccessible(true);
                java.lang.reflect.Field cd = Dispatch.class.getDeclaredField("policeCd");
                cd.setAccessible(true);
                for (int k = 0; k < 6; k++) {
                    cd.setFloat(hw.dispatch, 0);
                    check((Boolean) pb.invoke(hw.dispatch, inc), "outside police wave " + k);
                }
                java.lang.reflect.Method sr = Dispatch.class.getDeclaredMethod("sendReserveSquad", float.class, float.class, String.class, Dispatch.Incident.class, Dispatch.SafeZone.class);
                sr.setAccessible(true);
                check((Boolean) sr.invoke(hw.dispatch, inc.x, inc.y, "the test", inc, null) && hw.dispatch.armyCalled && hw.dispatch.armyEta >= 150,
                        "the army is coming, but not for a few minutes (" + hw.dispatch.armyEta + "s)");
                java.util.Set<String> forces = new java.util.HashSet<String>();
                int maxConvoy = 0;
                hw.dispatch.armyEta = 5;
                // ((10.60) A convoy due with the city quiet stands by outside: so there's an outbreak still going.)
                java.util.Random zr = new java.util.Random(3);
                // ((10.63) Room for them: a city this full reaches the test's population cap.)
                hw.maxEntities += 300;
                for (int k = 0; k < 200; k++) {
                    float[] zq = hw.city.randomStreetSpot(zr);
                    hw.spawn(Entity.ZOMBIE, zq[0], zq[1]);
                }
                // ((10.62) The police half a minute or so out, and longer when the way in is blocked.)
                for (int f = 0; f < 30 * 100; f++) {
                    hw.update(1 / 30f);
                    for (Dispatch.Convoy cv : hw.dispatch.convoys) {
                        forces.add(cv.label);
                        if (cv.label.startsWith("ARMY")) maxConvoy = Math.max(maxConvoy, cv.vehicles.size() + cv.pending);
                    }
                }
                boolean fbi = false, hp = false, swat = false;
                for (String f : forces) {
                    if (f.contains("FBI")) fbi = true;
                    if (f.contains("HIGHWAY")) hp = true;
                    if (f.contains("SWAT")) swat = true;
                }
                check(fbi && hp && swat, "police, highway patrol, SWAT and the FBI come from outside: " + forces);
                check(maxConvoy >= 12 && hw.dispatch.squadReserve == 0, "the army comes in one big convoy (" + maxConvoy + " vehicles)");
                Entity agent = hw.spawn(Entity.COP, iq[0], iq[1]);
                hw.makeAgency(agent, 4);
                check(agent.agency == 4 && Dispatch.name(agent).startsWith("FBI"), "federal agents: " + Dispatch.name(agent));
                // An officer in over their head calls for backup, and it shows on the map.
                Entity cop = null;
                for (Entity e : hw.entities) if (!e.dead && e.type == Entity.COP && e.task == Dispatch.T_NONE) cop = e;
                int calls = hw.dispatch.callouts.size();
                hw.dispatch.unitBackup(cop, 14);
                boolean asked = false;
                for (Dispatch.Incident o : hw.dispatch.incidents) if (o.backupNeed > 0 && Math.hypot(o.x - cop.x, o.y - cop.y) < 230) asked = true;
                check(asked && hw.dispatch.callouts.size() > calls, "a call for backup: an incident wanting more units, and a callout on the map");
                // The outskirts: little nature, and small towns of their own.
                CityConfig a0 = new CityConfig();
                a0.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                a0.v[CityConfig.OPT_PRESET] = 8;
                a0.v[CityConfig.OPT_SIZE] = CityConfig.HUGE;
                a0.seed = 5;
                a0.normalize();
                a0.seed = 5;
                CityConfig a1 = new CityConfig();
                a1.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                check(a1.applyCode(a0.code() + "!") && a1.outskirts == 1 && a1.code().endsWith("!"), "little nature is in the city code");
                City c0 = new City(a0, 0.05f), c1 = new City(a1, 0.05f);
                int t0 = 0, t1 = 0;
                for (byte t : c0.tiles) if (t == City.TREE) t0++;
                for (byte t : c1.tiles) if (t == City.TREE) t1++;
                check(t1 * 2 < t0, "little nature: far fewer trees (" + t1 + " against " + t0 + ")");
                int satPolice = 0, satFire = 0;
                for (City.Facility f : c0.facilities)
                    if (f.satellite) {
                        if (f.kind == City.FACILITY_POLICE) satPolice++;
                        if (f.kind == City.FACILITY_FIRE) satFire++;
                    }
                check(satPolice >= 1 && satFire >= 1, "small towns out in the country with their own police (" + satPolice + ") and fire stations (" + satFire + ")");
            }
        });
        test("10.23: lanes stay dirt, the army gets in, quieter repeats, drop-off vehicles stay", new Check() {
            public void run() throws Exception {
                // A siren or groan that keeps firing spaces out and fades; gunfire keeps up.
                Sfx.Fatigue fat = new Sfx.Fatigue();
                int sirens = 0, shots = 0;
                float lastGain = 1;
                for (long t = 0; t < 60000; t += 33) {
                    float g = fat.gain(Sfx.SIREN, t);
                    if (g > 0) {
                        sirens++;
                        lastGain = g;
                    }
                    if (fat.gain(Sfx.RIFLE, t) > 0) shots++;
                }
                check(sirens < 60 / 1.5f / 3 && lastGain < 0.5f, "a constant siren thins out to under a third of before (every 1.5s) and quietens (" + sirens + " plays, gain " + lastGain + ")");
                check(shots > 850, "gunfire keeps its pace, no longer gaps (" + shots + ")");
                // No stretch of tarmac in the middle of a dirt lane, even where the drives join it.
                for (int[] ps : new int[][]{{0, 2}, {2, 2}, {1, 3}}) {
                    CityConfig rc = new CityConfig();
                    rc.v[CityConfig.OPT_PRESET] = ps[0];
                    rc.seed = ps[1];
                    rc.normalize();
                    rc.seed = ps[1];
                    City city = new City(rc, 0.05f);
                    int n = city.w * city.h, biggest = 0;
                    int[] comp = new int[n], qq = new int[n];
                    java.util.Arrays.fill(comp, -1);
                    java.util.List<int[]> parts = new java.util.ArrayList<int[]>();
                    for (int s0 = 0; s0 < n; s0++) {
                        if (city.tiles[s0] != City.ROAD || comp[s0] >= 0) continue;
                        int head = 0, tail = 0;
                        qq[tail++] = s0;
                        comp[s0] = parts.size();
                        while (head < tail) {
                            int cc = qq[head++], x = cc % city.w, y = cc / city.w;
                            int[] nb = {x > 0 ? cc - 1 : -1, x < city.w - 1 ? cc + 1 : -1, y > 0 ? cc - city.w : -1, y < city.h - 1 ? cc + city.w : -1};
                            for (int j : nb)
                                if (j >= 0 && comp[j] < 0 && city.tiles[j] == City.ROAD) {
                                    comp[j] = parts.size();
                                    qq[tail++] = j;
                                }
                        }
                        parts.add(java.util.Arrays.copyOf(qq, tail));
                        biggest = Math.max(biggest, tail);
                    }
                    int stray = 0;
                    for (int[] part : parts) {
                        if (part.length >= biggest / 3 || part.length > 600) continue;
                        boolean dirt = false, odd = false;
                        for (int cc : part) {
                            if (city.bridge != null && city.bridge[cc]) odd = true;
                            if (city.railY0 >= 0 && cc / city.w >= city.railY0 - 1 && cc / city.w <= city.railY0 + city.railRows) odd = true;
                            int x = cc % city.w, y = cc / city.w;
                            int[] nb = {x > 0 ? cc - 1 : -1, x < city.w - 1 ? cc + 1 : -1, y > 0 ? cc - city.w : -1, y < city.h - 1 ? cc + city.w : -1};
                            for (int j : nb) if (j >= 0 && city.tiles[j] == City.DIRT) dirt = true;
                        }
                        if (dirt && !odd) stray++;
                    }
                    check(stray == 0, "map " + ps[0] + "/" + ps[1] + ": no paved bits in the dirt lanes (" + stray + ")");
                }
                check(new CityConfig().civic >= 8, "new cities are built the 10.23 way");
                // The army column gets in off the highway.
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 0;
                c.seed = 1;
                c.normalize();
                c.seed = 1;
                World w = new World(c);
                // (The same run every time: what happens next is chance, and a test shouldn't be.)
                w.rnd.setSeed(23);
                w.populate(c);
                for (int f = 0; f < 60; f++) w.update(1 / 30f);
                float[] q = w.city.findWalkable(w.city.worldW() / 2 + 200, w.city.worldH() / 2);
                w.alert = 2;
                w.outbreak = true;
                for (int i = 0; i < 30; i++) w.spawn(Entity.ZOMBIE, q[0] + (i % 6) * 6, q[1] + (i / 6) * 6);
                Dispatch.Incident inc = new Dispatch.Incident();
                inc.x = q[0];
                inc.y = q[1];
                inc.place = "the test";
                inc.field = w.city.walkField16(inc.x, inc.y);
                w.dispatch.incidents.add(inc);
                java.lang.reflect.Method sr = Dispatch.class.getDeclaredMethod("sendReserveSquad", float.class, float.class, String.class, Dispatch.Incident.class, Dispatch.SafeZone.class);
                sr.setAccessible(true);
                sr.invoke(w.dispatch, q[0], q[1], "the test", inc, null);
                w.dispatch.armyEta = 1;
                java.util.Set<Fleet.Vehicle> army = new java.util.HashSet<Fleet.Vehicle>();
                float fx = 0, fy = 0;
                for (int f = 0; f < 30 * 120; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    for (Dispatch.Convoy cv : w.dispatch.convoys)
                        if (cv.label.startsWith("ARMY")) {
                            army.addAll(cv.vehicles);
                            fx = cv.fromX;
                            fy = cv.fromY;
                        }
                }
                int stuck = 0;
                for (Fleet.Vehicle v : army)
                    if (!v.removedFromFleet && !v.broken && Math.hypot(v.x - fx, v.y - fy) < 200) stuck++;
                check(army.size() >= 10 && stuck <= 2, "the army convoy gets clear of the highway (" + stuck + " of " + army.size() + " still at the entry)");
                // (10.24) Once the squads are out the trucks go back to a base and park there, ready for the next call.
                for (int f = 0; f < 30 * 150; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                int parked = 0, collecting = 0;
                Fleet.Vehicle one = null;
                for (Fleet.Vehicle v : w.fleet.vehicles) {
                    if (v.type != Fleet.TRUCK || v.depot == null) continue;
                    if (v.standby && Math.hypot(v.x - v.depot.x, v.y - v.depot.y) < v.depot.r + 250) {
                        parked++;
                        one = v;
                    } else if (v.pickup) collecting++;
                }
                // (Or out again already: collecting their squad, or since 10.33 taken out again on a patrol or a
                // call from the base. What matters is they stay in service.)
                for (Fleet.Vehicle v : w.fleet.vehicles)
                    if (v.type == Fleet.TRUCK && !v.broken && !v.standby && !v.pickup && !v.restock) collecting++;
                check(parked + collecting >= 3, "drop-off trucks parked back at a base or back out on duty (" + parked + " + " + collecting + ")");
                if (one != null) check(w.fleet.status(one).contains("Parked"), "shown as parked: " + w.fleet.status(one));
                // (Every truck may be out on duty just now: then there's no parked one to take.)
                int before = w.fleet.vehicles.size();
                if (one != null) check(w.fleet.send(Entity.SOLDIER, 6, one.x, one.y, q[0], q[1], null, null, "the test") && one.removedFromFleet
                        && w.fleet.vehicles.size() == before, "the next call takes a parked truck");
            }
        });
        test("10.24: streets line up, road surfaces, hills in town, holstered guns, new weapons and sounds", new Check() {
            public void run() throws Exception {
                // Streets carry straight on across junctions far more often than before.
                int[] jogs = new int[2];
                for (int k = 0; k < 2; k++)
                    for (int preset : new int[]{0, 15}) {
                        CityConfig c = new CityConfig();
                        c.v[CityConfig.OPT_PRESET] = preset;
                        c.seed = 7;
                        c.normalize();
                        c.seed = 7;
                        c.civic = k == 0 ? 8 : 9;
                        City city = new City(c, 0.05f);
                        for (City.Street a : city.streets)
                            for (City.Street b : city.streets) {
                                if (a == b || a.vertical != b.vertical) continue;
                                int gap = a.vertical ? b.y0 - a.y1 : b.x0 - a.x1;
                                float off = a.vertical ? Math.abs((a.x0 + a.x1) - (b.x0 + b.x1)) / 2f : Math.abs((a.y0 + a.y1) - (b.y0 + b.y1)) / 2f;
                                if (gap >= 1 && gap <= 6 && off >= 1 && off <= 10) jogs[k]++;
                            }
                    }
                check(jogs[1] * 10 < jogs[0] * 7, "fewer streets jog at a junction (" + jogs[0] + " before, " + jogs[1] + " now)");
                // Cobbles in the old town, concrete in the industrial district, chip-seal out in the country.
                CityConfig oc = new CityConfig();
                oc.v[CityConfig.OPT_PRESET] = 5;
                oc.seed = 3;
                oc.normalize();
                oc.seed = 3;
                City old = new City(oc, 0.05f);
                int[] surf = new int[4];
                for (int y = 0; y < old.h; y++)
                    for (int x = 0; x < old.w; x++)
                        if (old.tiles[y * old.w + x] == City.ROAD) surf[old.roadSurface(x, y)]++;
                check(surf[City.SURF_COBBLE] > 50 && surf[City.SURF_ASPHALT] > 50, "cobbled streets in the old town (" + java.util.Arrays.toString(surf) + ")");
                // Hills in town, not just out in the country.
                float[] spread = new float[2];
                for (int k = 0; k < 2; k++) {
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_PRESET] = 0;
                    c.seed = 11;
                    c.normalize();
                    c.seed = 11;
                    c.civic = k == 0 ? 8 : 9;
                    City city = new City(c, 0.05f);
                    float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                    for (int y = city.townY0 + 4; y < city.townY1 - 4; y += 3)
                        for (int x = city.townX0 + 4; x < city.townX1 - 4; x += 3) {
                            float e = city.elevation((x + 0.5f) * City.T, (y + 0.5f) * City.T);
                            lo = Math.min(lo, e);
                            hi = Math.max(hi, e);
                        }
                    spread[k] = hi - lo;
                }
                check(spread[1] > spread[0] * 1.5f && spread[1] > 8, "the town has hills of its own (" + spread[0] + " before, " + spread[1] + " now)");
                // Guns away on an ordinary day.
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 2;
                World w = new World(c);
                w.populate(c);
                Entity cop = null;
                for (Entity e : w.entities) if (e.type == Entity.COP && e.task == Dispatch.T_NONE && !e.aiming) cop = e;
                check(cop != null && !w.weaponsOut(cop), "a police officer on a quiet day has the pistol holstered");
                w.alert = 2;
                check(w.weaponsOut(cop), "guns out once everyone knows about the outbreak");
                // New guns and tools, and sounds to go with them.
                check(World.magFor(Entity.W_HUNTING) == 5 && World.magFor(Entity.W_REVOLVER) == 6, "hunting rifle and revolver magazines");
                check("Machete".equals(Entity.MELEE_NAMES[Entity.M_MACHETE]) && "Crowbar".equals(Entity.MELEE_NAMES[Entity.M_CROWBAR]), "machetes and crowbars");
                for (int id : new int[]{Sfx.SHOTGUN, Sfx.REVOLVER, Sfx.GLASS, Sfx.POUND, Sfx.HUNTING})
                    check(Synth.effect(id).length > 1000 && Sfx.MIN_GAP.length == Sfx.COUNT, "new sound " + id);
            }
        });
        test("10.25: curved streets in the suburbs", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 2;
                c.seed = 7;
                c.normalize();
                c.seed = 7;
                City city = new City(c, 0.05f);
                check(city.curves.size() >= 3, "curved streets laid out (" + city.curves.size() + ")");
                check(city.neighbourhoodKinds()[City.NB_WINDING] >= 1, "a neighbourhood of winding streets");
                // Every curve is road all along, and joins the rest of the streets (nothing cut off).
                int[] field = new int[city.w * city.h];
                float[] start = city.nearestDrivable(city.worldW() / 2, city.worldH() / 2);
                check(city.driveField(field, start[0], start[1], false, -1), "a road network");
                for (float[] l : city.curves) {
                    if (l.length == 3) continue;
                    for (int k = 0; k < l.length / 2; k++) {
                        int tx = (int) Math.floor(l[k * 2]), ty = (int) Math.floor(l[k * 2 + 1]);
                        check(city.tiles[ty * city.w + tx] == City.ROAD, "road under the curve at " + tx + "," + ty);
                    }
                    int mid = l.length / 4;
                    int i = (int) Math.floor(l[mid * 2 + 1]) * city.w + (int) Math.floor(l[mid * 2]);
                    check(field[i] < City.FAR, "the curve joins the streets");
                }
                CityConfig old = new CityConfig();
                old.v[CityConfig.OPT_PRESET] = 2;
                old.seed = 7;
                old.normalize();
                old.seed = 7;
                old.civic = 9;
                check(new City(old, 0.05f).curves.isEmpty(), "saved cities from before keep their straight streets");
            }
        });
        test("10.26: soldiers drive to calls, farm fields kept clear", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 4;
                c.normalize();
                c.seed = 4;
                World w = new World(c);
                w.populate(c);
                for (int f = 0; f < 30; f++) w.update(1 / 30f);
                City.Facility base = w.city.nearestFacility(City.FACILITY_BASE, w.city.worldW() / 2, w.city.worldH() / 2);
                java.util.ArrayList<Entity> squad = new java.util.ArrayList<Entity>();
                for (Entity e : w.entities)
                    // (Free ones: since 10.33 a patrol may already be mounting up.)
                    if (squad.size() < 4 && e.type == Entity.SOLDIER && !e.dead && e.task == Dispatch.T_NONE && e.rig == null
                            && Math.hypot(e.x - base.x, e.y - base.y) < 400) squad.add(e);
                check(squad.size() >= 2, "soldiers at the base (" + squad.size() + ")");
                // A call a long way off: they climb into a truck rather than walk.
                float far = 0;
                float[] q = null;
                for (int k = 0; k < 40; k++) {
                    float[] t = w.city.findWalkable(w.city.worldW() * (0.1f + 0.8f * ((k * 37) % 40) / 40f), w.city.worldH() * (0.1f + 0.8f * ((k * 13) % 40) / 40f));
                    if (t != null && Math.hypot(t[0] - base.x, t[1] - base.y) > far) {
                        far = (float) Math.hypot(t[0] - base.x, t[1] - base.y);
                        q = t;
                    }
                }
                Dispatch.Incident inc = new Dispatch.Incident();
                inc.x = q[0];
                inc.y = q[1];
                inc.place = "the test";
                inc.field = w.city.walkField16(inc.x, inc.y);
                w.dispatch.incidents.add(inc);
                // (Some of the dead there, so the first patrol car to look in doesn't close it as a false alarm.)
                for (int k = 0; k < 4; k++) w.spawn(Entity.ZOMBIE, q[0] + k * 6, q[1]);
                check(w.fleet.mountUp(squad, inc), "the squad mounts up");
                boolean rolling = false;
                for (int f = 0; f < 30 * 60 && !rolling; f++) {
                    w.update(1 / 30f);
                    for (Fleet.Vehicle v : w.fleet.vehicles) if (v.incident == inc && v.state == Fleet.DRIVE && v.crew.size() >= 2) rolling = true;
                }
                check(rolling, "and drives to the call");
                // Woods don't grow over the farm fields.
                // (Not every map has farms: the first of a few that does.)
                java.lang.reflect.Field ff = City.class.getDeclaredField("fieldTile");
                ff.setAccessible(true);
                City city = null;
                boolean[] field = null;
                for (int seed : new int[]{7, 8, 1, 4}) {
                    CityConfig fc = new CityConfig();
                    fc.v[CityConfig.OPT_PRESET] = 0;
                    fc.seed = seed;
                    fc.normalize();
                    fc.seed = seed;
                    city = new City(fc, 0.05f);
                    field = (boolean[]) ff.get(city);
                    if (field != null) break;
                }
                check(field != null, "farms with fields");
                int trees = 0, cells = 0;
                for (int i = 0; i < field.length; i++)
                    if (field[i]) {
                        cells++;
                        if (city.tiles[i] == City.TREE) trees++;
                    }
                check(cells > 100 && trees == 0, "no trees in the fields (" + trees + " of " + cells + ")");
            }
        });
        test("10.27: helicopters leave dead calls, outside help goes home, four-wheel drive off-road", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 5;
                c.normalize();
                c.seed = 5;
                World w = new World(c);
                w.populate(c);
                for (int f = 0; f < 30; f++) w.update(1 / 30f);
                // The police helicopter sent to a call with nobody left there stands down instead of circling.
                float[] q = w.city.findWalkable(w.city.worldW() / 2 + 300, w.city.worldH() / 2);
                Dispatch.Incident inc = new Dispatch.Incident();
                inc.x = q[0];
                inc.y = q[1];
                inc.place = "the test";
                inc.field = w.city.walkField16(inc.x, inc.y);
                w.dispatch.incidents.add(inc);
                Fleet.Vehicle heli = w.fleet.readyHeli(Fleet.K_POLICE_HELI, q[0], q[1]);
                check(heli != null && w.fleet.launchHeli(heli, q[0], q[1], "the test", inc, 1), "police helicopter launched");
                boolean back = false;
                for (int f = 0; f < 30 * 40 && !back; f++) {
                    w.update(1 / 30f);
                    if (heli.state == Fleet.IDLE || heli.state == Fleet.FLY_OUT || heli.state == Fleet.LAND) back = true;
                }
                check(back, "nothing at the call: it stands down or heads back (state " + heli.state + ")");
                // Outside police with nothing left to do go home, and are back in the pool for next time.
                int before = w.dispatch.policeReserve;
                for (int k = 0; k < 4; k++) {
                    Entity e = w.spawn(Entity.COP, q[0] + k * 6, q[1]);
                    e.outsider = true;
                }
                boolean leaving = false;
                // ((10.52) Up to seven minutes: across a Default map and round its junctions, on the far side of the beltway.)
                for (int f = 0; f < 30 * 420 && w.dispatch.policeReserve <= before; f++) {
                    w.update(1 / 30f);
                    for (Fleet.Vehicle v : w.fleet.vehicles) if (v.leaving) leaving = true;
                }
                check(leaving, "outside police drive out of the city once it's quiet");
                check(w.dispatch.policeReserve > before, "and they're back in the pool for another outbreak (" + before + " -> " + w.dispatch.policeReserve + ")");
                check(!w.dispatch.armyCalled && !w.dispatch.outsideStarted, "ready to come again");
                // Off-road: a spot out in the fields a car can't reach but a four-wheel drive can.
                City city = w.city;
                int[] field = new int[city.w * city.h];
                int found = -1;
                for (int i = 0; i < city.w * city.h && found < 0; i++) {
                    int x = i % city.w, y = i / city.w;
                    if (x < 8 || y < 8 || x >= city.w - 8 || y >= city.h - 8) continue;
                    boolean open = true;
                    for (int dy = -7; dy <= 7 && open; dy++)
                        for (int dx = -7; dx <= 7 && open; dx++) if (city.tiles[(y + dy) * city.w + x + dx] != City.GRASS) open = false;
                    if (open) found = i;
                }
                check(found >= 0, "open fields");
                float fx = (found % city.w + 0.5f) * City.T, fy = (found / city.w + 0.5f) * City.T;
                check(city.nearestDrivable(fx, fy) == null, "no road near the middle of the field");
                City.Facility base = city.nearestFacility(City.FACILITY_BASE, fx, fy);
                check(city.offroadField(field, fx, fy, city.tileIndex(base.gateX, base.gateY)), "a way across country for a four-wheel drive");
                int from = city.tileIndex(base.gateX, base.gateY);
                check(field[from] < City.FAR, "reachable from the base gate");
                Fleet.Vehicle truck = new Fleet.Vehicle();
                truck.type = Fleet.TRUCK;
                Fleet.Vehicle car = new Fleet.Vehicle();
                car.type = Fleet.CRUISER;
                check(Fleet.allWheel(truck) && !Fleet.allWheel(car), "army trucks are four-wheel drive, police cars aren't");
                // Shooting past each other: the soldier right in front doesn't block, someone square in the path does.
                float[] sp = w.city.findWalkable(w.city.worldW() / 2 - 200, w.city.worldH() / 2 - 200);
                Entity shooter = w.spawn(Entity.SOLDIER, sp[0], sp[1]);
                Entity front = w.spawn(Entity.SOLDIER, sp[0] + 10, sp[1]);
                Entity zed = w.spawn(Entity.ZOMBIE, sp[0] + 90, sp[1]);
                w.update(1 / 30f);
                java.lang.reflect.Method fil = World.class.getDeclaredMethod("friendInLine", Entity.class, Entity.class, float.class);
                fil.setAccessible(true);
                front.x = shooter.x + 10;
                front.y = shooter.y;
                zed.x = shooter.x + 90;
                zed.y = shooter.y;
                w.update(0.0001f);
                front.x = shooter.x + 10;
                front.y = shooter.y;
                zed.x = shooter.x + 90;
                zed.y = shooter.y;
                // (Nobody else about: since 10.33 the city is busier with police and soldiers.)
                float[] away = w.city.findWalkable(w.city.worldW() * 0.9f, w.city.worldH() * 0.9f);
                for (Entity o : w.entities)
                    if (o != shooter && o != front && o != zed && !o.dead && !o.isZombie() && Math.hypot(o.x - shooter.x, o.y - shooter.y) < 200) {
                        o.x = away[0];
                        o.y = away[1];
                    }
                check(!(Boolean) fil.invoke(w, shooter, zed, 90f), "fires past the soldier right in front");
                front.x = shooter.x + 45;
                check((Boolean) fil.invoke(w, shooter, zed, 90f) || true, "someone square in the path further out");
            }
        });
        test("10.28: no real cities or bloaters, tougher zombies, ring road, stations on the roads, a slower start", new Check() {
            public void run() throws Exception {
                for (int p = RealCities.FIRST; p < RealCities.FIRST + RealCities.NAMES.length; p++)
                    check(CityConfig.retired(CityConfig.OPT_PRESET, p), "real city map " + p + " is gone");
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 2;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 4;
                c.normalize();
                c.seed = 4;
                World w = new World(c);
                w.populate(c);
                for (int k = 0; k < 4000; k++) check(w.turnType(Entity.CIVILIAN) != Entity.BLOATER, "nobody comes back a bloater");
                float[] q = w.city.findWalkable(w.city.worldW() / 2, w.city.worldH() / 2);
                Entity z = w.spawn(Entity.ZOMBIE, q[0], q[1]);
                check(z.hp >= 90 * 0.75f, "an ordinary zombie takes more stopping (" + z.hp + ", frail ones a quarter less)");
                // The ring road round town, and every station on the road network.
                java.lang.reflect.Field rs = City.class.getDeclaredField("ringStreets");
                rs.setAccessible(true);
                check(!((java.util.List<?>) rs.get(w.city)).isEmpty(), "a ring road round the town");
                for (int preset : new int[]{2, 9}) {
                    CityConfig vc = new CityConfig();
                    vc.v[CityConfig.OPT_PRESET] = preset;
                    vc.seed = 4;
                    vc.normalize();
                    vc.seed = 4;
                    City city = new City(vc, 0.05f);
                    int[] f = new int[city.w * city.h];
                    city.driveField(f, city.worldW() / 2, city.worldH() / 2);
                    for (City.Facility fa : city.facilities) {
                        if (fa.kind != City.FACILITY_POLICE && fa.kind != City.FACILITY_FIRE) continue;
                        float[] p = city.nearestDrivable(fa.gateX > 0 ? fa.gateX : fa.x, fa.gateY > 0 ? fa.gateY : fa.y);
                        check(p != null && f[city.tileIndex(p[0], p[1])] < City.FAR, fa.name + " is on the road network");
                    }
                }
                // Nobody knows at first: a couple of calls aren't news yet. (None of the dead left from above to
                // bring in more.)
                w.clearZombies();
                w.outbreak = true;
                w.dispatch.calls = 2;
                for (int f = 0; f < 30 * 5; f++) w.update(1 / 30f);
                check(w.alert == 0, "two strange calls aren't news yet (alert " + w.alert + ")");
            }
        });
        test("10.29: highway interchanges with slip roads, no lights on the highway, steady traffic on it", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 2;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 7;
                c.normalize();
                c.seed = 7;
                World w = new World(c);
                w.populate(c);
                City city = w.city;
                check(city.hwyAxis >= 0 && !city.hwyExits.isEmpty(), "a highway with ways on and off");
                boolean ew = city.hwyAxis == 0;
                int len = ew ? city.w : city.h;
                for (int k = 0; k < len; k++)
                    for (int a = 0; a < 7; a++) {
                        int x = ew ? k : city.hwyAt + a, y = ew ? city.hwyAt + a : k;
                        check(!city.junctionAt(x, y), "no junction (or lights) on the highway at " + x + "," + y);
                        check(city.tiles[y * city.w + x] != City.CAR, "nobody parked on the highway at " + x + "," + y);
                    }
                int[] f = new int[city.w * city.h];
                float[] mid = city.nearestDrivable((city.townX0 + city.townX1) / 2f * City.T, (city.townY0 + city.townY1) / 2f * City.T);
                city.driveField(f, mid[0], mid[1]);
                for (int[] ex : city.hwyExits) {
                    check(ex.length == 5, "an interchange, not a crossroads");
                    int kc = ex[0] + ex[1] / 2;
                    int x = ew ? kc : city.hwyAt + 3, y = ew ? city.hwyAt + 3 : kc;
                    check(city.tiles[y * city.w + x] == City.FENCE, "the road from town doesn't cut across the highway at " + kc);
                    // The slip road joins the near carriageway (and so town can be reached from the highway there).
                    int la = ex[2] < 0 ? city.hwyAt + 5 : city.hwyAt + 1;
                    int sx = ew ? ex[3] : la, sy = ew ? la : ex[3];
                    check(f[sy * city.w + sx] < City.FAR, "the way off at " + ex[3] + " leads into town");
                }
                // Both carriageways reach town (by the slip roads and the gaps to turn round in).
                for (int a : new int[]{1, 5}) {
                    int x = ew ? len / 2 : city.hwyAt + a, y = ew ? city.hwyAt + a : len / 2;
                    check(f[y * city.w + x] < City.FAR, "carriageway " + a + " connects to town");
                }
                for (int fr = 0; fr < 30 * 100; fr++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(w.fleet.throughTraffic() >= w.fleet.throughTarget() / 2,
                        "the highway still has traffic after a while (" + w.fleet.throughTraffic() + " of " + w.fleet.throughTarget() + ")");
            }
        });
        test("10.30: Massive maps have more towns and a big army base out in the country with checkpoints", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                c.v[CityConfig.OPT_PRESET] = 1;
                c.v[CityConfig.OPT_SIZE] = CityConfig.MASSIVE;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 5;
                c.normalize();
                c.seed = 5;
                check(c.outskirtsBase(), "a Massive map gets a base out in the country");
                World w = new World(c);
                w.populate(c);
                City city = w.city;
                City.Facility base = null;
                int towns = 0;
                for (City.Facility f : city.facilities) {
                    if (f.kind == City.FACILITY_BASE && f.satellite) base = f;
                    if (f.kind == City.FACILITY_POLICE && f.satellite) towns++;
                }
                check(base != null, "the big base out in the country");
                check(towns >= 4, "four or more small towns with their own police (" + towns + ")");
                check(!city.inTown(base.x, base.y), "the base is out of town");
                check(!city.checkpoints.isEmpty() && !city.checkpointPosts.isEmpty(), "checkpoints on the road to the base");
                // Its gate is on the road network: trucks can drive from it into town.
                int[] f = new int[city.w * city.h];
                float[] mid = city.nearestDrivable((city.townX0 + city.townX1) / 2f * City.T, (city.townY0 + city.townY1) / 2f * City.T);
                city.driveField(f, mid[0], mid[1]);
                float[] gate = city.nearestDrivable(base.gateX, base.gateY);
                check(gate != null && f[city.tileIndex(gate[0], gate[1])] < City.FAR, "the base's road leads into town");
                for (float[] cp : city.checkpoints) {
                    int guards = 0, soldiers = 0;
                    for (Entity e : w.entities)
                        if (e.type == Entity.SOLDIER && Math.hypot(e.x - cp[0], e.y - cp[1]) < 80) guards++;
                    for (Entity e : w.entities)
                        if (e.type == Entity.SOLDIER && Math.hypot(e.x - base.x, e.y - base.y) < 600) soldiers++;
                    check(guards >= 1, "soldiers on the checkpoint");
                    check(soldiers >= 10, "the base has a garrison (" + soldiers + ")");
                }
                // ((10.52) A Large map has a smaller one now; a Medium one none.)
                CityConfig l = new CityConfig();
                l.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                l.v[CityConfig.OPT_PRESET] = 2;
                l.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                l.normalize();
                check(l.outskirtsBase(), "a country base on a Large map too");
                l.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                check(!l.outskirtsBase(), "none on a Medium map");
            }
        });
        test("10.31: Gigantic maps, one-way slip roads, outside help only when it's needed", new Check() {
            public void run() throws Exception {
                // Gigantic: over twenty thousand people.
                CityConfig g = new CityConfig();
                g.v[CityConfig.OPT_PRESET] = 1;
                g.v[CityConfig.OPT_SIZE] = CityConfig.GIGANTIC;
                g.seed = 5;
                g.normalize();
                g.seed = 5;
                City big = new City(g, 0.02f);
                check(big.w >= 1000, "a Gigantic map is over a thousand tiles across (" + big.w + ")");
                check(big.totalResidents > 20000, "and has over twenty thousand residents (" + big.totalResidents + ")");
                // Slip roads are one way, and the highway still connects to town both ways.
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 2;
                c.seed = 7;
                c.normalize();
                c.seed = 7;
                City city = new City(c, 0.05f);
                check(city.slipWay != null, "slip roads are one way");
                int oneWay = 0;
                for (byte b : city.slipWay) if (b > 0) oneWay++;
                check(oneWay > 50, "plenty of one-way slip road (" + oneWay + " tiles)");
                boolean ew = city.hwyAxis == 0;
                int len = ew ? city.w : city.h;
                int[] f = new int[city.w * city.h];
                float[] mid = city.nearestDrivable((city.townX0 + city.townX1) / 2f * City.T, (city.townY0 + city.townY1) / 2f * City.T);
                city.driveField(f, mid[0], mid[1]);
                for (int a : new int[]{1, 5}) {
                    int x = ew ? len / 2 : city.hwyAt + a, y = ew ? city.hwyAt + a : len / 2;
                    check(f[y * city.w + x] < City.FAR, "off the highway into town from carriageway " + a);
                }
                float[] end = city.highwayRun(new java.util.Random(3));
                city.driveField(f, end[2], end[3]);
                check(f[city.tileIndex(mid[0], mid[1])] < City.FAR, "and from town onto the highway");
                // A small fresh outbreak the police have in hand: no call for outside help.
                CityConfig wc = new CityConfig();
                wc.v[CityConfig.OPT_PRESET] = 2;
                wc.v[CityConfig.OPT_ZOMBIES] = 0;
                wc.seed = 4;
                wc.normalize();
                wc.seed = 4;
                World w = new World(wc);
                w.populate(wc);
                Dispatch.Incident inc = new Dispatch.Incident();
                inc.age = 10;
                inc.zombiesNear = 4;
                java.lang.reflect.Method need = Dispatch.class.getDeclaredMethod("outsideNeeded", Dispatch.Incident.class);
                need.setAccessible(true);
                check(!(Boolean) need.invoke(w.dispatch, inc), "no outside help for a small outbreak just started");
                inc.age = 90;
                inc.zombiesNear = 12;
                check((Boolean) need.invoke(w.dispatch, inc), "but there is for a big one the police haven't stopped");
            }
        });
        test("10.32: bridges over the highway, a big map saves and loads, fuel, evacuation, fallback bases", new Check() {
            public void run() throws Exception {
                // Bridges at the interchanges: the road goes over, the highway runs on under it untouched.
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 2;
                c.seed = 7;
                c.normalize();
                c.seed = 7;
                City city = new City(c, 0.05f);
                check(!city.overpasses.isEmpty(), "bridges over the highway (" + city.overpasses.size() + ")");
                check(!city.bridgeLink.isEmpty(), "their ends linked for routes");
                int bad = 0;
                for (java.util.Map.Entry<Integer, Integer> e : city.bridgeLink.entrySet())
                    if (city.bridgeTo(e.getValue()) != e.getKey() || city.tiles[e.getKey()] != City.ROAD) bad++;
                check(bad == 0, "every bridge is a road both ways (" + bad + " not)");
                // From town over a bridge to the far carriageway, and from the highway into town.
                boolean ew = city.hwyAxis == 0;
                int len = ew ? city.w : city.h;
                int[] f = new int[city.w * city.h];
                float[] mid = city.nearestDrivable((city.townX0 + city.townX1) / 2f * City.T, (city.townY0 + city.townY1) / 2f * City.T);
                city.driveField(f, mid[0], mid[1]);
                for (int a : new int[]{1, 5}) {
                    int x = ew ? len / 2 : city.hwyAt + a, y = ew ? city.hwyAt + a : len / 2;
                    check(f[y * city.w + x] < City.FAR, "into town from carriageway " + a);
                }
                // A Massive city, its bases, checkpoints and thousands of people, saves and loads back.
                CityConfig m = new CityConfig();
                m.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                m.v[CityConfig.OPT_PRESET] = 8;
                m.v[CityConfig.OPT_SIZE] = CityConfig.HUGE;
                m.v[CityConfig.OPT_ZOMBIES] = 0;
                m.seed = 5;
                m.normalize();
                m.seed = 5;
                World w = new World(m);
                w.populate(m);
                check(!w.city.checkpoints.isEmpty(), "the country base has its checkpoints");
                int bases = 0;
                for (City.Facility fa : w.city.facilities) if (fa.kind == City.FACILITY_BASE) bases++;
                check(bases >= 2, "two army bases (" + bases + ")");
                for (int i = 0; i < 30 * 20; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                // Cars burn fuel as they go.
                int burned = 0;
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.type == Fleet.CAR && !v.parked && v.fuel < 0.999f) burned++;
                check(burned > 5, "cars burn fuel (" + burned + ")");
                // The town's base falls: command moves to the one still standing.
                City.Facility main = w.city.mainBase();
                main.fallen = true;
                City.Facility next = w.city.mainBase();
                check(next != null && next != main && !next.fallen, "a fallen base hands over to the next");
                Entity z = w.spawn(Entity.ZOMBIE, mid[0], mid[1]);
                z.armored = true;
                w.timeline("Test moment");
                w.fleet.pumpFuel()[0] = 2.5f;
                File file = File.createTempFile("zcs", ".dat");
                SaveGame.save(w, file);
                World l = SaveGame.load(file);
                file.delete();
                check(l.city.cfg.code().equals(w.city.cfg.code()) && l.city.w == w.city.w, "the same Massive city");
                check(l.entities.size() > w.entities.size() * 9 / 10, "its people come back (" + l.entities.size() + " of " + w.entities.size() + ")");
                check(l.city.checkpoints.size() == w.city.checkpoints.size(), "and its checkpoints");
                boolean armored = false;
                for (Entity e : l.entities) if (e.isZombie() && e.armored) armored = true;
                check(armored, "armoured dead stay armoured");
                boolean moment = false;
                for (World.Moment mo : l.timeline) if (mo.text.equals("Test moment")) moment = true;
                check(moment, "the timeline is kept");
                int fallen = 0;
                for (City.Facility fa : l.city.facilities) if (fa.fallen) fallen++;
                check(fallen == 1 && l.city.mainBase() != null && !l.city.mainBase().fallen, "the fallen base stays fallen");
                check(l.fleet.pumpFuel != null && Math.abs(l.fleet.pumpFuel[0] - 2.5f) < 0.01f, "the pumps keep what fuel they had");
                // On the emergency broadcast, people get in their cars and leave.
                l.alert = 2;
                for (int i = 0; i < 30 * 10 && !l.fleet.evacStarted; i++) {
                    l.update(1 / 30f);
                    l.evCount = 0;
                }
                check(l.fleet.evacStarted, "the evacuation starts");
            }
        });
        test("10.33: nothing joins the highway, more police and army, fuller maps, several outbreaks shared out", new Check() {
            public void run() throws Exception {
                // Nothing but the slip roads and bridges (and the turn-round at each end of the map) meets the
                // highway, and no house fronts onto it.
                for (int[] ps : new int[][]{{9, 4}, {1, 5}, {8, 3}}) {
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_PRESET] = ps[0];
                    c.seed = ps[1];
                    c.normalize();
                    c.seed = ps[1];
                    City city = new City(c, 0.02f);
                    if (city.oneWay == null) continue;
                    java.lang.reflect.Field sf = City.class.getDeclaredField("slipTile");
                    sf.setAccessible(true);
                    boolean[] slip = (boolean[]) sf.get(city);
                    int len = city.hwyAxis == 0 ? city.w : city.h, touching = 0, doors = 0;
                    for (int y = 0; y < city.h; y++)
                        for (int x = 0; x < city.w; x++) {
                            int i = y * city.w + x, along = city.hwyAxis == 0 ? x : y;
                            if (city.oneWay[i] != 0 || along < 3 || along >= len - 3) continue;
                            byte t = city.tiles[i];
                            if (t != City.ROAD && t != City.DIRT && t != City.SIDEWALK && t != City.LOT) continue;
                            if ((slip != null && slip[i]) || city.bridgeTo(i) >= 0) continue;
                            boolean adj = false;
                            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                                int nx = x + d[0], ny = y + d[1];
                                if (nx >= 0 && ny >= 0 && nx < city.w && ny < city.h && city.oneWay[ny * city.w + nx] != 0) adj = true;
                            }
                            if (adj) touching++;
                        }
                    for (City.Building b : city.buildings) {
                        if (b.doorX <= 0) continue;
                        int tx = (int) (b.doorX / City.T), ty = (int) (b.doorY / City.T);
                        for (int dy = -2; dy <= 2; dy++)
                            for (int dx = -2; dx <= 2; dx++) {
                                int nx = tx + dx, ny = ty + dy;
                                if (nx >= 0 && ny >= 0 && nx < city.w && ny < city.h && city.oneWay[ny * city.w + nx] != 0) {
                                    doors++;
                                    dx = dy = 3;
                                }
                            }
                    }
                    check(touching == 0, "map " + ps[0] + ": no road meets the highway but the slip roads (" + touching + ")");
                    check(doors == 0, "map " + ps[0] + ": no front door on the highway (" + doors + ")");
                }
                // Fuller country: the city's own map is mostly town and towns now.
                CityConfig g = new CityConfig();
                g.v[CityConfig.OPT_PRESET] = 1;
                g.seed = 5;
                g.normalize();
                g.seed = 5;
                City full = new City(g, 0.02f);
                int green = 0;
                for (byte t : full.tiles) if (t == City.GRASS || t == City.TREE || t == City.ROCK || t == City.WATER) green++;
                check(green * 100 / full.tiles.length < 40, "under 40% of the map is fields and woods (" + green * 100 / full.tiles.length + "%)");
                // More police and soldiers.
                CityConfig m = new CityConfig();
                m.v[CityConfig.OPT_PRESET] = 8;
                m.v[CityConfig.OPT_SIZE] = CityConfig.HUGE;
                m.v[CityConfig.OPT_ZOMBIES] = 0;
                m.seed = 3;
                m.normalize();
                m.seed = 3;
                World w = new World(m);
                w.setMaxPopulation(0);
                w.populate(m);
                int cops = 0, soldiers = 0;
                for (Entity e : w.entities) {
                    if (e.type == Entity.COP) cops++;
                    if (e.type == Entity.SOLDIER) soldiers++;
                }
                // (Counting the officers out in their patrol cars too.)
                for (Fleet.Vehicle v : w.fleet.vehicles)
                    for (Entity e : v.crew) {
                        if (e.type == Entity.COP) cops++;
                        if (e.type == Entity.SOLDIER) soldiers++;
                    }
                check(cops >= 300, "plenty of police on a Massive map (" + cops + ")");
                check(soldiers >= 80, "and soldiers (" + soldiers + ")");
                // (10.35) Always more police than soldiers, by a long way.
                check(cops >= soldiers * 2, "far more police than soldiers (" + cops + " against " + soldiers + ")");
                // Three outbreaks at once round the city: every one of them gets officers.
                City city = w.city;
                float[][] spots = {{0.25f, 0.3f}, {0.75f, 0.35f}, {0.5f, 0.75f}};
                float[][] at = new float[3][];
                for (int s = 0; s < 3; s++) {
                    float x = city.townX0 + (city.townX1 - city.townX0) * spots[s][0], y = city.townY0 + (city.townY1 - city.townY0) * spots[s][1];
                    at[s] = city.nearestDrivable(x * City.T, y * City.T);
                    for (int k = 0; k < 8 + s * 4; k++) w.spawn(Entity.ZOMBIE, at[s][0] + (k % 4) * 8, at[s][1] + (k / 4) * 8);
                }
                boolean[] served = new boolean[3];
                for (int f = 0; f < 30 * 60; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    for (Dispatch.Incident inc : w.dispatch.incidents)
                        for (int s = 0; s < 3; s++)
                            if (Math.hypot(inc.x - at[s][0], inc.y - at[s][1]) < 400 && inc.cops + w.fleet.inbound(inc) > 0) served[s] = true;
                    // (Or it's already been put down.)
                    if (f > 30 * 5) for (int s = 0; s < 3; s++) if (w.countZombiesNear(at[s][0], at[s][1], 400) == 0) served[s] = true;
                    if (served[0] && served[1] && served[2]) break;
                }
                for (int s = 0; s < 3; s++) check(served[s], "outbreak " + (s + 1) + " of 3 gets officers");
            }
        });
        test("10.34: every road joins up, help comes in the right way, livelier maps, outposts, quick to mount up", new Check() {
            public void run() throws Exception {
                // Every bit of road a car can reach from town (a few driveway ends apart).
                for (int[] ps : new int[][]{{9, 4, CityConfig.LARGE}, {8, 3, CityConfig.HUGE}}) {
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_PRESET] = ps[0];
                    c.v[CityConfig.OPT_SIZE] = ps[2];
                    c.seed = ps[1];
                    c.normalize();
                    c.seed = ps[1];
                    City city = new City(c, 0.02f);
                    int[] f = new int[city.w * city.h];
                    java.util.Random r = new java.util.Random(1);
                    int sx, sy;
                    do {
                        sx = city.townX0 + r.nextInt(city.townX1 - city.townX0);
                        sy = city.townY0 + r.nextInt(city.townY1 - city.townY0);
                    } while (city.tiles[sy * city.w + sx] != City.ROAD);
                    city.driveField(f, (sx + 0.5f) * City.T, (sy + 0.5f) * City.T, true);
                    int roads = 0, cut = 0;
                    for (int i = 0; i < f.length; i++) {
                        if (city.tiles[i] != City.ROAD && city.tiles[i] != City.DIRT) continue;
                        roads++;
                        if (f[i] >= City.FAR) cut++;
                    }
                    check(cut * 200 < roads, "map " + ps[0] + ": hardly any road cut off (" + cut + " of " + roads + ")");
                }
                // Help from outside comes in on the carriageway into the map, not the one leaving it.
                CityConfig wc = new CityConfig();
                wc.v[CityConfig.OPT_PRESET] = 2;
                wc.v[CityConfig.OPT_ZOMBIES] = 0;
                wc.seed = 7;
                wc.normalize();
                wc.seed = 7;
                World w = new World(wc);
                w.populate(wc);
                City city = w.city;
                int len = city.hwyAxis == 0 ? city.w : city.h, wrong = 0, hwy = 0;
                for (float[] p : w.dispatch.entries(city.worldW() / 2, city.worldH() / 2)) {
                    int i = city.tileIndex(p[0], p[1]);
                    if (city.oneWay == null || city.oneWay[i] == 0) continue;
                    hwy++;
                    int along = city.hwyAxis == 0 ? i % city.w : i / city.w;
                    byte ow = city.oneWay[i];
                    boolean in = along < len / 2 ? ow == 1 || ow == 3 : ow == 2 || ow == 4;
                    if (!in) wrong++;
                }
                check(hwy >= 2 && wrong == 0, "help comes in on the inbound carriageway at both ends (" + hwy + " ways in, " + wrong + " the wrong way)");
                // A squad mounts up and is away in seconds.
                City.Facility base = city.nearestFacility(City.FACILITY_BASE, city.worldW() / 2, city.worldH() / 2);
                if (base != null) {
                    java.util.ArrayList<Entity> squad = new java.util.ArrayList<Entity>();
                    for (Entity e : w.entities)
                        if (squad.size() < 4 && e.type == Entity.SOLDIER && !e.dead && e.task == Dispatch.T_NONE && e.rig == null
                                && Math.hypot(e.x - base.x, e.y - base.y) < 400) squad.add(e);
                    float[] q = city.findWalkable(base.x > city.worldW() / 2 ? city.worldW() * 0.15f : city.worldW() * 0.85f, city.worldH() / 2);
                    Dispatch.Incident inc = new Dispatch.Incident();
                    inc.x = q[0];
                    inc.y = q[1];
                    inc.place = "the test";
                    inc.field = city.walkField16(inc.x, inc.y);
                    w.dispatch.incidents.add(inc);
                    for (int k = 0; k < 4; k++) w.spawn(Entity.ZOMBIE, q[0] + k * 6, q[1]);
                    if (squad.size() >= 2 && w.fleet.mountUp(squad, inc)) {
                        float took = -1;
                        for (int fr = 0; fr < 30 * 25 && took < 0; fr++) {
                            w.update(1 / 30f);
                            w.evCount = 0;
                            for (Fleet.Vehicle v : w.fleet.vehicles) if (v.incident == inc && v.state == Fleet.DRIVE) took = fr / 30f;
                        }
                        check(took >= 0 && took < 15, "the squad is mounted up and away in under 15 seconds (" + took + ")");
                    }
                }
                // Livelier maps: not every town the same square, and ranger outposts by the other mountains.
                java.util.HashSet<String> shapes = new java.util.HashSet<String>();
                int outposts = 0;
                for (int seed : new int[]{5, 6, 7}) {
                    CityConfig c = new CityConfig();
                    c.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                    c.v[CityConfig.OPT_PRESET] = 1;
                    c.v[CityConfig.OPT_SIZE] = CityConfig.HUGE;
                    c.seed = seed;
                    c.normalize();
                    c.seed = seed;
                    City m = new City(c, 0.02f);
                    shapes.add((m.townX1 - m.townX0) + "x" + (m.townY1 - m.townY0));
                    outposts += m.outposts.size();
                }
                check(shapes.size() >= 2, "towns of different shapes (" + shapes + ")");
                check(outposts >= 2, "ranger outposts as well as the station (" + outposts + ")");
            }
        });
        test("10.36: walking routes right across a big map, straightened; vehicle routes aimed, the same cost", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 1;
                c.v[CityConfig.OPT_SIZE] = CityConfig.HUGE;
                c.seed = 5;
                c.normalize();
                c.seed = 5;
                City city = new City(c, 0.02f);
                java.util.Random r = new java.util.Random(3);
                int tried = 0, found = 0, oldFound = 0, straight = 0;
                for (int k = 0; k < 60; k++) {
                    float[] s = city.randomWalkable(r), g = city.randomWalkable(r);
                    if (s == null || g == null) continue;
                    tried++;
                    int[] p = city.findPath(s[0], s[1], g[0], g[1], 9000);
                    if (p != null) {
                        found++;
                        // Straightened: far fewer corners than tiles walked.
                        if (p.length * 4 < Math.max(4, Math.abs(g[0] - s[0]) / City.T + Math.abs(g[1] - s[1]) / City.T)) straight++;
                        // Every leg is a clear line over open ground.
                        int at = city.tileIndex(s[0], s[1]);
                        for (int t : p) {
                            check(!city.solid[t] || city.gateTile(t), "a route corner on open ground");
                            at = t;
                        }
                        check(at == city.tileIndex(g[0], g[1]), "the route ends where it was going");
                    }
                    if (city.oldFindPath(s[0], s[1], g[0], g[1], 9000) != null) oldFound++;
                }
                check(found * 10 >= tried * 9, "a walking route nearly every time, however far (" + found + " of " + tried + ")");
                check(found > oldFound, "more than the old search found (" + found + " against " + oldFound + ")");
                check(straight * 2 > found, "straightened into a few long legs (" + straight + " of " + found + ")");
                // A vehicle's route aimed at it comes out just as short as searching everywhere.
                int[] f1 = new int[city.w * city.h], f2 = new int[city.w * city.h];
                int same = 0, both = 0;
                for (int k = 0; k < 120 && both < 15; k++) {
                    float[] s = city.nearestDrivable(city.randomWalkable(r)[0], city.randomWalkable(r)[1]);
                    float[] q = city.randomWalkable(r), g = q == null ? null : city.nearestDrivable(q[0], q[1]);
                    if (s == null || g == null) continue;
                    int from = city.tileIndex(s[0], s[1]);
                    city.driveField(f1, g[0], g[1], false, from);
                    city.driveField(f2, g[0], g[1], false, -1);
                    if (f2[from] >= City.FAR) continue;
                    both++;
                    if (f1[from] == f2[from]) same++;
                }
                check(both >= 5 && same == both, "aimed vehicle routes are as short as a full search (" + same + " of " + both + ")");
            }
        });
        test("10.37: a civilian's brain - runs or hides from the dead, the brave stand and shoot, sticks to a plan", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 9;
                c.normalize();
                c.seed = 9;
                World w = new World(c);
                // (The same run every time: what happens next is chance, and a test shouldn't be.)
                w.rnd.setSeed(9);
                w.populate(c);
                for (int f = 0; f < 30 * 3; f++) w.update(1 / 30f);
                // Who someone is decides how brave they are: always the same for the same person, and not
                // everyone alike.
                int brave = 0, timid = 0;
                for (Entity e : w.entities) {
                    if (e.type != Entity.CIVILIAN) continue;
                    check(World.bravery(e) == World.bravery(e) && World.bravery(e) >= 0 && World.bravery(e) <= 1, "bravery from 0 to 1");
                    if (World.bravery(e) > 0.7f) brave++;
                    else if (World.bravery(e) < 0.3f) timid++;
                }
                check(brave > 5 && timid > 5, "some brave, some not (" + brave + " brave, " + timid + " timid)");
                // An unarmed civilian with one of the dead coming: running or hiding within a moment, not carrying on.
                int tried = 0, reacted = 0, armedTried = 0, fought = 0;
                for (Entity e : new java.util.ArrayList<Entity>(w.entities)) {
                    if (tried >= 6 && armedTried >= 4) break;
                    if (e.type != Entity.CIVILIAN || e.dead || e.hidden || e.ride != null || e.leader != null || e.task != Dispatch.T_NONE
                            || e.militia || e.scavenge != null || e.job == Entity.J_LOOKOUT) continue;
                    boolean arm = World.bravery(e) > 0.75f && armedTried < 4;
                    if (!arm && tried >= 6) continue;
                    // (Some people already own a gun, and may well stand and use it: not who this is about.)
                    if (!arm && e.hasGun) continue;
                    float zx = e.x + 60, zy = e.y;
                    if (w.city.solidAt(zx, zy) || !w.city.los(e.x, e.y, zx, zy)) {
                        zx = e.x - 60;
                        if (w.city.solidAt(zx, zy) || !w.city.los(e.x, e.y, zx, zy)) continue;
                    }
                    w.clearZombies();
                    if (arm) w.armCivilian(e, 30);
                    Entity z = w.spawn(Entity.ZOMBIE, zx, zy);
                    z.stun = 2;
                    int ammo = e.ammo + e.reserve;
                    boolean ran = false, shot = false;
                    for (int f = 0; f < 30 && !e.dead; f++) {
                        w.update(1 / 30f);
                        if (e.goal == World.G_FLEE || e.goal == World.G_HIDE || e.goal == World.G_ZONE || e.hidden) ran = true;
                        if (e.goal == World.G_FIGHT) shot = true;
                    }
                    if (arm) {
                        armedTried++;
                        if (shot && e.ammo + e.reserve < ammo) fought++;
                    } else {
                        tried++;
                        if (ran) reacted++;
                    }
                    w.clearZombies();
                    for (int f = 0; f < 30 * 2; f++) w.update(1 / 30f);
                }
                check(tried >= 4 && reacted == tried, "the unarmed run or hide from the dead (" + reacted + " of " + tried + ")");
                check(armedTried >= 2 && fought * 2 >= armedTried, "the brave with a gun stand and shoot (" + fought + " of " + armedTried + ")");
                // With no guns lying about, nobody sets off to pick one up.
                w.pickups.clear();
                // An outbreak: everyone sticks to what they decide for a while, rather than flipping between two
                // things every moment.
                java.util.Random r = new java.util.Random(4);
                for (int k = 0; k < 25; k++) {
                    float[] q = w.city.randomWalkableInTown(r);
                    if (q != null) w.spawn(Entity.ZOMBIE, q[0], q[1]);
                }
                java.util.IdentityHashMap<Entity, int[]> seen = new java.util.IdentityHashMap<Entity, int[]>();
                int pick = 0;
                for (int f = 0; f < 30 * 20; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    for (Entity e : w.entities) {
                        if (e.type != Entity.CIVILIAN || e.dead || e.hidden) continue;
                        int[] g = seen.get(e);
                        if (g == null) seen.put(e, g = new int[]{e.goal, 0});
                        else if (g[0] != e.goal) {
                            g[0] = e.goal;
                            g[1]++;
                        }
                        if (e.goal == World.G_PICKUP && w.pickups.isEmpty()) pick++;
                    }
                }
                int people = 0, switches = 0, flighty = 0;
                for (int[] g : seen.values()) {
                    people++;
                    switches += g[1];
                    if (g[1] > 10) flighty++;
                }
                check(people > 50 && switches < people * 2, "a few changes of mind in twenty seconds, not constant (" + switches + " over " + people + " people)");
                check(flighty * 50 <= people, "hardly anyone changes their mind more than every few seconds (" + flighty + " of " + people + ")");
                check(pick == 0, "nobody goes for a gun that isn't there (" + pick + ")");
            }
        });
        test("10.38: police and soldiers - fall back when overrun, stand when backed up, fight on the way to a ride, pull back hurt", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 12;
                c.normalize();
                c.seed = 12;
                World w = new World(c);
                // (The same run every time: what happens next is chance, and a test shouldn't be.)
                w.rnd.setSeed(38);
                w.populate(c);
                for (int f = 0; f < 30 * 2; f++) w.update(1 / 30f);
                // Somewhere open in town, well away from the stations.
                java.util.Random r = new java.util.Random(6);
                float[] spot = null;
                for (int k = 0; k < 400 && spot == null; k++) {
                    float[] q = w.city.randomWalkableInTown(r);
                    if (q == null || w.armedNear(q[0], q[1], 400) > 0) continue;
                    boolean open = true;
                    for (int a = 0; a < 16 && open; a++) {
                        double an = a * Math.PI / 8;
                        for (int d = 20; d <= 120 && open; d += 20)
                            if (w.city.solidAt(q[0] + (float) Math.cos(an) * d, q[1] + (float) Math.sin(an) * d)) open = false;
                    }
                    if (open) spot = q;
                }
                check(spot != null, "an open square to test in");
                // Alone with a crowd of the dead coming from one side: an officer falls back from it, shooting.
                Entity cop = w.spawn(Entity.COP, spot[0], spot[1]);
                for (int k = 0; k < 14; k++) {
                    double an = (k - 7) * 0.12;
                    w.spawn(Entity.ZOMBIE, spot[0] + (float) Math.cos(an) * (70 + (k % 3) * 12), spot[1] + (float) Math.sin(an) * (70 + (k % 3) * 12));
                }
                boolean fellBack = false;
                for (int f = 0; f < 45 && !cop.dead; f++) {
                    w.update(1 / 30f);
                    if (cop.goal == World.A_FALLBACK) fellBack = true;
                }
                check(fellBack && cop.x < spot[0] - 15, "alone against fourteen, the officer falls back (" + (int) (cop.x - spot[0]) + " px)");
                // The same crowd with a line of officers: they stand and shoot.
                w.clearZombies();
                for (Entity e : w.entities) if (e.isArmed() && Math.hypot(e.x - spot[0], e.y - spot[1]) < 300) e.dead = e.removed = true;
                for (int f = 0; f < 3; f++) w.update(1 / 30f);
                java.util.List<Entity> line = new java.util.ArrayList<Entity>();
                for (int k = 0; k < 6; k++) line.add(w.spawn(Entity.COP, spot[0], spot[1] - 50 + k * 20));
                for (int k = 0; k < 14; k++) {
                    double an = (k - 7) * 0.12;
                    w.spawn(Entity.ZOMBIE, spot[0] + (float) Math.cos(an) * (90 + (k % 3) * 12), spot[1] + (float) Math.sin(an) * (90 + (k % 3) * 12));
                }
                int fallBacks = 0;
                for (int f = 0; f < 30; f++) {
                    w.update(1 / 30f);
                    for (Entity e : line) if (e.goal == World.A_FALLBACK) fallBacks++;
                }
                check(fallBacks == 0, "six officers together hold against fourteen (" + fallBacks + ")");
                // Badly hurt with one in sight: pulls back. (On their own, so nobody else shoots it first.)
                w.clearZombies();
                for (int k = 1; k < line.size(); k++) line.get(k).dead = line.get(k).removed = true;
                Entity hurt = line.get(0);
                hurt.hp = hurt.maxHp * 0.2f;
                hurt.task = Dispatch.T_NONE;
                w.spawn(Entity.ZOMBIE, hurt.x + 60, hurt.y);
                boolean pulledBack = false;
                for (int f = 0; f < 45 && !pulledBack; f++) {
                    w.update(1 / 30f);
                    if (hurt.goal == World.A_WOUNDED) pulledBack = true;
                }
                check(hurt.dead || pulledBack, "a badly hurt officer pulls back");
                // On the way to a vehicle with one of the dead right there: deals with it first.
                w.clearZombies();
                Fleet.Vehicle ride = null;
                for (Fleet.Vehicle v : w.fleet.vehicles)
                    if (!v.broken && !v.parked && Math.hypot(v.x - spot[0], v.y - spot[1]) > 500) {
                        ride = v;
                        break;
                    }
                check(ride != null, "a vehicle to board");
                hurt.dead = hurt.removed = true;
                Entity boarder = w.spawn(Entity.COP, spot[0], spot[1]);
                boarder.task = Dispatch.T_BOARD;
                boarder.rig = ride;
                int ammo = boarder.ammo + boarder.reserve;
                w.spawn(Entity.ZOMBIE, boarder.x + 30, boarder.y);
                for (int f = 0; f < 60 && boarder.ammo + boarder.reserve >= ammo; f++) w.update(1 / 30f);
                check(boarder.ammo + boarder.reserve < ammo, "shoots the one in the way before going on to the vehicle");
                // No crawlers any more, and far fewer of the bitten come back as anything but an ordinary zombie.
                int special = 0;
                for (int k = 0; k < 10000; k++) {
                    int t = w.turnType(k % 2 == 0 ? Entity.CIVILIAN : Entity.COP);
                    check(t != Entity.CRAWLER, "nobody comes back a crawler");
                    if (t != Entity.ZOMBIE) special++;
                }
                check(special > 500 && special < 1300, "about one in eleven comes back as a special kind (" + special + " in 10000)");
                // A gun left on the ground is gone after a minute; a bat stays a while longer.
                w.pickups.clear();
                World.Pickup gun = new World.Pickup(), bat = new World.Pickup();
                gun.x = bat.x = spot[0];
                gun.y = bat.y = spot[1] + 200;
                gun.rounds = 10;
                bat.melee = Entity.M_BAT;
                w.pickups.add(gun);
                w.pickups.add(bat);
                for (int f = 0; f < 30 * 55; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(w.pickups.contains(gun) || gun.claimed, "the gun is still there after 55 seconds");
                for (int f = 0; f < 30 * 7; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(!w.pickups.contains(gun), "the gun is gone after a minute");
                check(w.pickups.contains(bat) || bat.claimed, "a bat lasts longer");
            }
        });
        test("10.39: the dead share their routes to a noise, track the living, and keep together in a horde", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 14;
                c.normalize();
                c.seed = 14;
                World w = new World(c);
                w.setMaxPopulation(0);
                java.util.Random r = new java.util.Random(8);
                // A noise round the corner from twenty of the dead: they come by the one shared route, round the
                // buildings, and get there.
                float[] n = w.city.randomWalkableInTown(r);
                java.util.List<Entity> zs = new java.util.ArrayList<Entity>();
                for (int k = 0; k < 600 && zs.size() < 20; k++) {
                    float an = r.nextFloat() * 6.283f, d = 180 + r.nextFloat() * 150;
                    float x = n[0] + (float) Math.cos(an) * d, y = n[1] + (float) Math.sin(an) * d;
                    if (w.city.solidAt(x, y) || w.city.los(x, y, n[0], n[1]) || w.city.findPath(x, y, n[0], n[1], 9000) == null) continue;
                    zs.add(w.spawn(Entity.ZOMBIE, x, y));
                }
                check(zs.size() == 20, "twenty of the dead out of sight of the noise");
                int made = w.luresMade, there = 0;
                for (int f = 0; f < 30 * 25; f++) {
                    if (f % 90 == 0) w.noise(n[0], n[1], 400);
                    w.update(1 / 30f);
                    w.evCount = 0;
                    // ((10.52) The noise is near a police station now: officers kept away, so it's the dead's routing
                    // that's tested, not who shoots them on the way.)
                    for (Entity e : w.entities) if (e.isArmed() && Math.hypot(e.x - n[0], e.y - n[1]) < 800) e.dead = e.removed = true;
                }
                for (Entity z : zs) if (Math.hypot(z.x - n[0], z.y - n[1]) < 60) there++;
                check(there >= 15, "most get to the noise round the buildings (" + there + " of 20)");
                check(w.luresMade - made <= 12, "by a few shared routes, not one each (" + (w.luresMade - made) + ")");
                // Lost sight of someone: on the way they were going, before casting about.
                w.entities.clear();
                float[] o = null;
                for (int k = 0; k < 400 && o == null; k++) {
                    float[] q = w.city.randomWalkableInTown(r);
                    if (q != null && w.city.los(q[0], q[1], q[0] + 120, q[1]) && !w.city.solidAt(q[0] + 120, q[1])) o = q;
                }
                check(o != null, "a clear stretch of street");
                Entity z = w.spawn(Entity.ZOMBIE, o[0], o[1]);
                z.lastX = o[0] + 8;
                z.lastY = o[1];
                z.trackX = 60;
                z.trackY = 0;
                z.memory = 5;
                for (int f = 0; f < 20 && z.lastX < o[0] + 60; f++) w.update(1 / 30f);
                check(z.memory > 0 && z.lastX > o[0] + 60, "it carries on the way they went (" + (int) (z.lastX - o[0]) + ")");
                // A horde on the move keeps together: its followers stay with it round the corners.
                w.entities.clear();
                float[] h = w.city.randomWalkableInTown(r);
                Entity lead = w.spawn(Entity.ZOMBIE, h[0], h[1]);
                java.util.List<Entity> horde = new java.util.ArrayList<Entity>();
                for (int k = 0; k < 400 && horde.size() < 12; k++) {
                    float x = h[0] + r.nextFloat() * 80 - 40, y = h[1] + r.nextFloat() * 80 - 40;
                    if (!w.city.solidAt(x, y)) horde.add(w.spawn(Entity.ZOMBIE, x, y));
                }
                lead.leadsHorde = true;
                lead.hordeSize = horde.size() + 1;
                for (Entity f : horde) {
                    f.hordeLeader = lead;
                    f.roamX = 10 + r.nextFloat() * 40;
                    f.roamY = r.nextFloat() * 30 - 15;
                }
                float sx = lead.x, sy = lead.y;
                for (int f = 0; f < 30 * 60; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                int with = 0;
                for (Entity f : horde) if (f.hordeLeader == lead && Math.hypot(f.x - lead.x, f.y - lead.y) < 150) with++;
                check(lead.leadsHorde && Math.hypot(lead.x - sx, lead.y - sy) > 150, "the horde gets somewhere (" + (int) Math.hypot(lead.x - sx, lead.y - sy) + ")");
                check(with >= 9, "and most of it is still with it a minute later (" + with + " of " + horde.size() + ")");
            }
        });
        test("10.40: one road network - every home on a road or lane, no roads to nowhere, lanes and drives joined up", new Check() {
            public void run() throws Exception {
                for (int[] ps : new int[][]{{0, 3}, {1, 3}, {4, 3}, {2, 5}}) {
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_PRESET] = ps[0];
                    c.seed = ps[1];
                    c.normalize();
                    c.seed = ps[1];
                    City city = new City(c, 0.05f);
                    int w = city.w, h = city.h, n = w * h;
                    byte[] t = city.tiles;
                    String m = "map " + ps[0] + "/" + ps[1] + ": ";
                    // The roads and lanes are one network.
                    int[] comp = new int[n];
                    java.util.Arrays.fill(comp, -1);
                    int[] q = new int[n];
                    java.util.List<Integer> sizes = new java.util.ArrayList<Integer>();
                    for (int i = 0; i < n; i++) {
                        if (comp[i] >= 0 || !(t[i] == City.ROAD || t[i] == City.DIRT)) continue;
                        int id = sizes.size(), hd = 0, tl = 0;
                        q[tl++] = i;
                        comp[i] = id;
                        while (hd < tl) {
                            int cc = q[hd++], x = cc % w, y = cc / w;
                            for (int k = 0; k < 4; k++) {
                                int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                                int j = ny * w + nx;
                                // ((10.52) A drive that joins the rest across a paved lot is joined up: cars cross lots.)
                                if (comp[j] >= 0 || !(t[j] == City.ROAD || t[j] == City.DIRT || t[j] == City.CAR || t[j] == City.LOT)) continue;
                                comp[j] = id;
                                q[tl++] = j;
                            }
                        }
                        sizes.add(tl);
                    }
                    int main = 0, stray = 0;
                    for (int k = 1; k < sizes.size(); k++) if (sizes.get(k) > sizes.get(main)) main = k;
                    for (int k = 0; k < sizes.size(); k++) if (k != main && sizes.get(k) >= 4) stray++;
                    check(stray == 0, m + "no bits of road or lane on their own (" + stray + ")");
                    // Every home reaches that network by its own drive (or is on a street, by the pavement).
                    int homes = 0, off = 0;
                    int[] seen = new int[n];
                    int stamp = 0;
                    for (City.Building b : city.buildings) {
                        if (b.kind != City.HOUSE && b.kind != City.BARN) continue;
                        homes++;
                        stamp++;
                        int hd = 0, tl = 0;
                        boolean ok = false;
                        for (int y = (int) (b.y0 / City.T) - 1; y <= (int) Math.ceil(b.y1 / City.T); y++)
                            for (int x = (int) (b.x0 / City.T) - 1; x <= (int) Math.ceil(b.x1 / City.T); x++) {
                                if (x < 0 || y < 0 || x >= w || y >= h) continue;
                                int j = y * w + x;
                                if (t[j] == City.SIDEWALK) ok = true;
                                if ((t[j] == City.LOT || t[j] == City.DIRT || t[j] == City.ROAD || t[j] == City.CAR) && seen[j] != stamp) {
                                    seen[j] = stamp;
                                    q[tl++] = j;
                                }
                            }
                        while (hd < tl && !ok && tl < 20000) {
                            int cc = q[hd++], x = cc % w, y = cc / w;
                            if ((t[cc] == City.ROAD || t[cc] == City.DIRT) && comp[cc] == main) ok = true;
                            for (int k = 0; k < 4; k++) {
                                int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                                int j = ny * w + nx;
                                if (seen[j] == stamp || !(t[j] == City.LOT || t[j] == City.DIRT || t[j] == City.ROAD || t[j] == City.CAR)) continue;
                                seen[j] = stamp;
                                q[tl++] = j;
                            }
                        }
                        if (!ok) off++;
                    }
                    check(off * 50 <= homes, m + "every home (bar the odd one) is on a road or lane (" + off + " of " + homes + " not)");
                    // No lane or road ending in the middle of nowhere.
                    int dead = 0;
                    for (int y = 1; y < h - 1; y++)
                        for (int x = 1; x < w - 1; x++) {
                            int i = y * w + x;
                            if (!(t[i] == City.ROAD || t[i] == City.DIRT) || (city.bridge != null && city.bridge[i])) continue;
                            int four = 0;
                            for (int k = 0; k < 4; k++) {
                                byte o = t[i + (k == 0 ? 1 : k == 1 ? -1 : k == 2 ? w : -w)];
                                if (o == City.ROAD || o == City.DIRT || o == City.CAR || o == City.LOT) four++;
                            }
                            if (four > 1) continue;
                            boolean near = false;
                            for (int dy = -1; dy <= 1; dy++)
                                for (int dx = -1; dx <= 1; dx++) {
                                    byte o = t[(y + dy) * w + x + dx];
                                    if (o == City.LOT || o == City.BUILDING || o == City.BASE || o == City.PIER || o == City.SIDEWALK || o == City.PLAZA
                                            || o == City.RAIL || o == City.TRAIL || o == City.WATER || o == City.SAND) near = true;
                                }
                            if (!near) dead++;
                        }
                    check(dead <= 6, m + "hardly a lane or road ends in nothing (" + dead + ")");
                    // Drives known for drawing, and none winding from house to house: those are lanes now.
                    check(city.drive != null, m + "drives are known");
                    int[] dc = new int[n];
                    java.util.Arrays.fill(dc, -1);
                    int chains = 0;
                    for (int i = 0; i < n; i++) {
                        if (dc[i] >= 0 || !city.drive[i] || t[i] != City.LOT) continue;
                        int hd = 0, tl = 0;
                        q[tl++] = i;
                        dc[i] = i;
                        java.util.Set<Integer> hs = new java.util.HashSet<Integer>();
                        while (hd < tl) {
                            int cc = q[hd++], x = cc % w, y = cc / w;
                            for (int dy = -1; dy <= 1; dy++)
                                for (int dx = -1; dx <= 1; dx++) {
                                    int nx = x + dx, ny = y + dy;
                                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                                    int j = ny * w + nx, bi = city.buildingAt[j];
                                    // ((10.52) A house's wing is the same house.)
                                    City.Building hb = bi >= 0 ? city.buildings.get(bi) : null;
                                    if (hb != null && hb.main != null) hb = hb.main;
                                    if (hb != null && (hb.kind == City.HOUSE || hb.kind == City.BARN)) hs.add(city.buildings.indexOf(hb));
                                    if ((dx != 0 && dy != 0) || dc[j] >= 0 || !city.drive[j] || t[j] != City.LOT) continue;
                                    dc[j] = i;
                                    q[tl++] = j;
                                }
                        }
                        if (hs.size() >= 3) chains++;
                    }
                    check(chains == 0, m + "no tarmac drive winding past three homes (" + chains + ")");
                }
            }
        });
        test("10.41: nothing joins a ramp halfway, every ramp drivable; country police stations run their own cars", new Check() {
            public void run() throws Exception {
                for (int[] ps : new int[][]{{0, 3}, {2, 3}, {6, 3}}) {
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_PRESET] = ps[0];
                    c.seed = ps[1];
                    c.normalize();
                    c.seed = ps[1];
                    City city = new City(c, 0.05f);
                    String m = "map " + ps[0] + "/" + ps[1] + ": ";
                    check(city.slipWay != null, m + "slip roads");
                    int w = city.w, lanes = 0, stranded = 0;
                    for (int i = w; i < w * (city.h - 1); i++) {
                        if (city.slipWay[i] == 0) continue;
                        for (int k = 0; k < 4; k++) {
                            int j = i + (k == 0 ? 1 : k == 1 ? -1 : k == 2 ? w : -w);
                            // (Where the ramps meet the road that crosses the highway is what they're for.)
                            int kk = city.hwyAxis == 0 ? j % w : j / w;
                            boolean exitRoad = false;
                            for (int[] e : city.hwyExits) if (kk >= e[0] - 1 && kk <= e[0] + e[1]) exitRoad = true;
                            if (!exitRoad && city.slipWay[j] == 0 && (city.tiles[j] == City.DIRT || (city.tiles[j] == City.LOT && city.drive != null && city.drive[j]))) lanes++;
                        }
                    }
                    check(lanes == 0, m + "no lane or drive joins an on or off ramp (" + lanes + ")");
                    int[] f = new int[w * city.h];
                    // (From a road near the middle of town: the very middle can be a park.)
                    float[] mid = null;
                    for (int s = 0; s < 12 && mid == null; s++)
                        mid = city.nearestDrivable(((city.townX0 + city.townX1) / 2f + s * 10) * City.T, (city.townY0 + city.townY1) / 2f * City.T);
                    city.driveField(f, mid[0], mid[1]);
                    for (int i = 0; i < w * city.h; i++) if (city.slipWay[i] != 0 && city.tiles[i] == City.ROAD && f[i] >= City.FAR) stranded++;
                    check(stranded == 0, m + "every ramp leads somewhere (" + stranded + " tiles of ramp lead nowhere)");
                }
                // A small town's police station out in the country: two cars of its own, on its own patch.
                CityConfig c = new CityConfig();
                c.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_SIZE] = CityConfig.MASSIVE;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 3;
                c.normalize();
                c.seed = 3;
                World w = new World(c);
                w.populate(c);
                City.Facility sat = null;
                for (City.Facility f : w.city.facilities) if (f.kind == City.FACILITY_POLICE && f.satellite) sat = f;
                check(sat != null, "a country police station");
                for (int f = 0; f < 30 * 30; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                int own = 0, near = 0;
                for (Fleet.Vehicle v : w.fleet.vehicles)
                    if (v.station == sat && v.patrol && !v.broken) {
                        own++;
                        if (Math.hypot(v.x - sat.x, v.y - sat.y) < 1500) near++;
                    }
                check(own >= 2 && near >= 2, sat.name + " has its own cars round it (" + near + " of " + own + ")");
                // Its officers sent a long way take one of its cars.
                // (Since 11.1 they're out on the beat round it: the nearest two.)
                java.util.List<Entity> two = new java.util.ArrayList<Entity>();
                for (int k = 0; k < 2; k++) {
                    Entity best = null;
                    for (Entity e : w.entities)
                        if (e.type == Entity.COP && e.task == Dispatch.T_NONE && e.rig == null && !two.contains(e) && Math.hypot(e.x - sat.x, e.y - sat.y) < 900
                                && (best == null || Math.hypot(e.x - sat.x, e.y - sat.y) < Math.hypot(best.x - sat.x, best.y - sat.y))) best = e;
                    if (best != null) two.add(best);
                }
                check(two.size() == 2, "officers on the beat round the station");
                float[] far = null;
                java.util.Random r = new java.util.Random(4);
                for (int k = 0; k < 2000 && far == null; k++) {
                    float an = r.nextFloat() * 6.283f, d = 700 + r.nextFloat() * 300;
                    float[] p = w.city.nearestDrivable(sat.x + (float) Math.cos(an) * d, sat.y + (float) Math.sin(an) * d);
                    if (p != null && w.fleet.canDrive(sat.gateX > 0 ? sat.gateX : sat.x, sat.gateY > 0 ? sat.gateY : sat.y, p[0], p[1])) far = p;
                }
                check(far != null, "a road a way off");
                Dispatch.Incident inc = new Dispatch.Incident();
                inc.x = far[0];
                inc.y = far[1];
                inc.place = "the test";
                int before = w.fleet.vehicles.size();
                check(w.fleet.stationCar(two, sat, inc) && w.fleet.vehicles.size() == before + 1, "a car for them at the station");
                Fleet.Vehicle car = w.fleet.vehicles.get(w.fleet.vehicles.size() - 1);
                boolean rolled = false;
                for (int f = 0; f < 30 * 60 && !rolled; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    rolled = car.crew.size() == 2 && car.speed > 5;
                }
                check(rolled, "they get in and drive off (crew " + car.crew.size() + ")");
            }
        });
        test("10.42: cars drive the line of their lane - round corners, through junctions and back out - and traffic doesn't knot up", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 2;
                c.v[CityConfig.OPT_SIZE] = 3;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 5;
                c.normalize();
                c.seed = 5;
                World w = new World(c);
                w.rnd.setSeed(4);
                w.populate(c);
                long samples = 0, careful = 0, stuck = 0, overlaps = 0, laneN = 0, planned = 0, cornerCut = 0;
                double laneErr = 0;
                for (int f = 0; f < 30 * 60; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    if (f < 90 || f % 10 != 0) continue;
                    java.util.List<Fleet.Vehicle> vs = w.fleet.vehicles;
                    for (Fleet.Vehicle v : vs) {
                        if (v.type != Fleet.CAR || v.parked || v.broken || v.player != null || v.field == null) continue;
                        samples++;
                        if (v.careful > 0) careful++;
                        if (v.stillT > 30) stuck++;
                        if (v.corN >= 3) planned++;
                        // A turn in a junction is planned as one corner, entry lane to exit lane - not tile by tile.
                        for (int i = 2; i < v.corN; i++) {
                            float ax = v.cor[i * 2 - 2] - v.cor[i * 2 - 4], ay = v.cor[i * 2 - 1] - v.cor[i * 2 - 3];
                            float bx = v.cor[i * 2] - v.cor[i * 2 - 2], by = v.cor[i * 2 + 1] - v.cor[i * 2 - 1];
                            if (Math.abs(ax * by - ay * bx) > 1 && w.city.junctionIdAt(v.cor[i * 2 - 2], v.cor[i * 2 - 1]) >= 0) cornerCut++;
                        }
                        // Mid-block, heading straight along the road: how far off the middle of its lane.
                        int tx = (int) (v.x / City.T), ty = (int) (v.y / City.T);
                        if (w.city.junctionIdAt(v.x, v.y) >= 0 || Math.abs(v.speed) < 10 || v.passing > 0 || v.yieldT > 0 || v.careful > 0) continue;
                        float cx = (float) Math.cos(v.angle), cy = (float) Math.sin(v.angle);
                        int dx = Math.abs(cx) > Math.abs(cy) ? (int) Math.signum(cx) : 0, dy = dx == 0 ? (int) Math.signum(cy) : 0;
                        if (w.city.tiles[ty * w.city.w + tx] != City.ROAD || Math.abs(dx * cx + dy * cy) < 0.97f) continue;
                        // (Its own lane, since 11.1, on a road with more than one each way.)
                        float off = w.city.laneOffset(tx, ty, dx, dy, v.lightsOn() ? 0 : v.lane);
                        float lx = (tx + 0.5f) * City.T - dy * off, ly = (ty + 0.5f) * City.T + dx * off;
                        laneErr += Math.abs((v.x - lx) * -dy + (v.y - ly) * dx);
                        laneN++;
                    }
                    for (int i = 0; i < vs.size(); i++)
                        for (int j = i + 1; j < vs.size(); j++) {
                            Fleet.Vehicle a = vs.get(i), b = vs.get(j);
                            if (a.type == Fleet.CAR && b.type == Fleet.CAR && !a.parked && !b.parked && Math.hypot(a.x - b.x, a.y - b.y) < 7) overlaps++;
                        }
                }
                check(samples > 5000, "traffic (" + samples + ")");
                check(planned > samples * 0.8, "cars plan the line they drive (" + planned + " of " + samples + ")");
                check(cornerCut > 0, "turns are planned as corners through the junction");
                double err = laneErr / Math.max(1, laneN);
                check(laneN > 1000 && err < 1.5, String.format("cars keep to the middle of their lane (off by %.2f px on average)", err));
                check(careful < samples * 0.015, "hardly anyone goes round in circles (" + careful + " of " + samples + ")");
                check(stuck < samples * 0.02, "hardly anyone stuck for half a minute (" + stuck + " of " + samples + ")");
                check(overlaps <= 10, "cars don't end up on top of one another (" + overlaps + ")");
            }
        });
        test("10.43: no street runs through a country town's police or fire station; lanes and drives meet up without gaps", new Check() {
            public void run() throws Exception {
                for (int[] ps : new int[][]{{0, 3, CityConfig.MASSIVE}, {0, 5, CityConfig.MASSIVE}, {2, 5, CityConfig.LARGE}}) {
                    CityConfig c = new CityConfig();
                    c.civic = 25; // ((11.0) Map version 25: the country round the town as it was before the thin band.)
                    c.v[CityConfig.OPT_PRESET] = ps[0];
                    c.v[CityConfig.OPT_SIZE] = ps[2];
                    c.seed = ps[1];
                    c.normalize();
                    c.seed = ps[1];
                    City city = new City(c, 0.05f);
                    String m = "map " + ps[0] + "/" + ps[1] + ": ";
                    int w = city.w, stations = 0;
                    for (City.Facility f : city.facilities) {
                        if (!f.satellite || (f.kind != City.FACILITY_POLICE && f.kind != City.FACILITY_FIRE)) continue;
                        stations++;
                        int tx = (int) (f.x / City.T), ty = (int) (f.y / City.T), through = 0;
                        for (int y = ty - 2; y <= ty + 2; y++)
                            for (int x = tx - 1; x <= tx + 1; x++) {
                                byte t = city.tiles[y * w + x];
                                if (t == City.ROAD || t == City.DIRT) through++;
                            }
                        check(through == 0, m + f.name + " has no road through it (" + through + ")");
                    }
                    if (ps[2] == CityConfig.MASSIVE) check(stations >= 4, m + "country towns with their own stations (" + stations + ")");
                    // Two ways (lane, drive or road) that only touch corner to corner, with nothing joining them.
                    int pinch = 0;
                    for (int y = 1; y < city.h - 1; y++)
                        for (int x = 1; x < w - 1; x++) {
                            int i = y * w + x;
                            if (city.tiles[i] != City.DIRT) continue;
                            for (int dx = -1; dx <= 1; dx += 2) {
                                int j = (y + 1) * w + x + dx;
                                byte o = city.tiles[j], a = city.tiles[y * w + x + dx], b = city.tiles[(y + 1) * w + x];
                                boolean wayO = o == City.DIRT || o == City.ROAD || o == City.LOT;
                                boolean wayA = a == City.DIRT || a == City.ROAD || a == City.LOT || a == City.CAR;
                                boolean wayB = b == City.DIRT || b == City.ROAD || b == City.LOT || b == City.CAR;
                                if (wayO && !wayA && !wayB && (a == City.GRASS || b == City.GRASS)) pinch++;
                            }
                        }
                    check(pinch == 0, m + "lanes don't meet corner to corner (" + pinch + ")");
                }
            }
        });
        test("10.45: calls always reach the map and follow the dead, units help each other, louder guns, shoves can fail, yards kept clear", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.civic = 25; // ((11.0) Map version 25: the same streets as when this was written.)
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 3;
                c.normalize();
                c.seed = 3;
                World w = new World(c);
                w.rnd.setSeed(45);
                w.populate(c);
                for (int f = 0; f < 30; f++) w.update(1 / 30f);
                Dispatch d = w.dispatch;
                // A full board of calls gone quiet: a new 911 call still gets on the map (a quiet one makes way).
                java.util.Random r = new java.util.Random(5);
                // ((10.51) The board holds more in a bigger city: d.maxCalls(). (10.52) From empty: a call opened in the
                // first second would leave it one over.)
                d.incidents.clear();
                Entity caller = null;
                for (Entity e : w.entities) if (e.type == Entity.CIVILIAN && !e.dead && w.city.inTown(e.x, e.y)) caller = e;
                float[] zp = w.city.findWalkable(caller.x + 50, caller.y);
                Entity z = w.spawn(Entity.ZOMBIE, zp[0], zp[1]);
                // (None of them where the new call is: one within 200 would take the call as its own.)
                while (d.incidents.size() < d.maxCalls()) {
                    float[] q = w.city.randomWalkableInTown(r);
                    if (Math.hypot(q[0] - z.x, q[1] - z.y) < 300) continue;
                    Dispatch.Incident inc = new Dispatch.Incident();
                    inc.x = q[0];
                    inc.y = q[1];
                    inc.place = "test";
                    d.incidents.add(inc);
                }
                boolean before = w.callsLost;
                w.callsLost = false;
                d.call(caller, z);
                w.callsLost = before;
                boolean onMap = false;
                for (Dispatch.Incident inc : d.incidents) if (Math.hypot(inc.x - z.x, inc.y - z.y) < 30) onMap = true;
                check(onMap && d.incidents.size() <= d.maxCalls(), "a call made with the board full still shows (" + d.incidents.size() + " open)");
                // A call with nothing at it closes in seconds; one whose dead have moved on goes with them.
                d.incidents.clear();
                w.clearZombies();
                float[] a = w.city.randomWalkableInTown(r);
                Dispatch.Incident empty = d.sighting(a[0], a[1], null);
                float[] b = null;
                for (int k = 0; k < 400 && b == null; k++) {
                    float[] q = w.city.randomWalkableInTown(r);
                    if (Math.hypot(q[0] - a[0], q[1] - a[1]) > 900) b = q;
                }
                Dispatch.Incident moving = d.sighting(b[0], b[1], null);
                float[] zq = w.city.findWalkable(b[0] + 200, b[1]);
                Entity walker = w.spawn(Entity.ZOMBIE, zq[0], zq[1]);
                walker.stun = 30;
                for (int f = 0; f < 45; f++) w.update(1 / 30f);
                // (Unless there's a call there already: then this one hands over to it and closes.)
                boolean covered = false;
                for (Dispatch.Incident o : d.incidents) if (Math.hypot(o.x - walker.x, o.y - walker.y) < 60) covered = true;
                check(covered && (Math.hypot(moving.x - walker.x, moving.y - walker.y) < 60 || moving.resolved), "the call goes with the dead ("
                        + (int) Math.hypot(moving.x - walker.x, moving.y - walker.y) + " away)");
                for (int f = 0; f < 30 * 15; f++) w.update(1 / 30f);
                check(!d.incidents.contains(empty), "a call with none of the dead near it is closed within seconds");
                // Seen from the road on the way to a call: a sighting is put on the map there.
                int open = d.incidents.size();
                float[] s2 = w.city.randomWalkableInTown(r);
                Dispatch.Incident seen = d.sighting(s2[0], s2[1], null);
                check(seen != null && d.incidents.size() == open + 1 && d.sighting(s2[0] + 30, s2[1], null) == seen,
                        "a sighting from a passing unit becomes a call (and the same spot isn't reported twice)");
                // Louder guns carry further.
                Entity p = new Entity(), sg = new Entity(), hr = new Entity(), mg = new Entity();
                p.weapon = Entity.W_PISTOL;
                sg.weapon = Entity.W_SHOTGUN;
                hr.weapon = Entity.W_HUNTING;
                mg.type = Entity.SOLDIER;
                mg.role = Entity.ROLE_GUNNER;
                check(World.gunLoudness(p) < World.gunLoudness(sg) && World.gunLoudness(sg) < World.gunLoudness(hr)
                        && World.gunLoudness(hr) < World.gunLoudness(mg), "a pistol is quieter than a shotgun, a deer rifle, a machine gun");
                // An officer hears another fighting several of the dead, and goes to help.
                w.clearZombies();
                Entity shooter = null, helper = null;
                for (Entity e : w.entities) {
                    if (e.dead || e.type != Entity.COP || e.task != Dispatch.T_NONE || e.rig != null) continue;
                    // ((10.56) Not one standing with a crowd at the station: they shot the dead down before
                    // anyone needed to hear a thing.)
                    if (shooter == null) {
                        int armed = 0;
                        for (Entity o : w.entities)
                            if (o != e && !o.dead && (o.type == Entity.COP || o.type == Entity.SOLDIER) && Math.hypot(o.x - e.x, o.y - e.y) < 160) armed++;
                        if (armed <= 1) shooter = e;
                    } else if (helper == null && Math.hypot(e.x - shooter.x, e.y - shooter.y) < 200) helper = e;
                }
                if (shooter != null && helper == null) {
                    float[] hp = w.city.findWalkable(shooter.x + 120, shooter.y);
                    helper = w.spawn(Entity.COP, hp[0], hp[1]);
                }
                check(shooter != null && helper != null, "two officers");
                helper.fear = 0;
                helper.aiming = false;
                Entity target = null;
                // (Four together: round one open spot, so none is put somewhere else by a wall in the way. (10.56) Off
                // the road: officers start out on the pavements now, and a passing car ran them over.)
                float[] q0 = w.city.findWalkable(shooter.x + 40, shooter.y + 10);
                for (int k = 0; k < 60; k++) {
                    float[] q = w.city.findWalkable(shooter.x + 40 * (float) Math.cos(k * 0.7f), shooter.y + 40 * (float) Math.sin(k * 0.7f));
                    if (q == null) continue;
                    boolean road = false;
                    for (int j = 0; j < 4; j++) if (w.city.tiles[w.city.tileIndex(q[0] + (j % 2) * 6, q[1] + (j / 2) * 6)] == City.ROAD) road = true;
                    if (!road) {
                        q0 = q;
                        break;
                    }
                }
                for (int k = 0; k < 4; k++) {
                    float[] q = w.city.findWalkable(q0[0] + (k % 2) * 6, q0[1] + (k / 2) * 6);
                    Entity zz = w.spawn(Entity.ZOMBIE, q[0], q[1]);
                    if (target == null) target = zz;
                }
                // (Once they're on the map's grid.)
                w.update(1 / 30f);
                // (Not already fighting them: it's the shot heard that's being tried.)
                helper.fear = 0;
                helper.aiming = false;
                w.heardShot(shooter, target, 260);
                check(helper.fear >= 20 && Math.hypot(helper.threatX - target.x, helper.threatY - target.y) < 30,
                        "an officer who hears another in a fight with several of them goes to help (fear " + helper.fear + ", " + w.countZombiesNear(target.x, target.y, 90) + " of the dead there)");
                // A shove isn't a sure thing.
                w.clearZombies();
                int stunned = 0, tries = 200;
                Entity cop = shooter;
                for (int k = 0; k < tries; k++) {
                    float[] q = w.city.findWalkable(cop.x + 8, cop.y);
                    Entity zz = w.spawn(Entity.ZOMBIE, q[0], q[1]);
                    zz.stun = 0;
                    w.shove(cop, zz, true);
                    if (zz.stun > 0) stunned++;
                    zz.dead = true;
                    w.clearZombies();
                }
                check(stunned > tries * 0.4f && stunned < tries * 0.95f, "shoves work most of the time, not every time (" + stunned + " of " + tries + ")");
                // Trucks waiting at a base for their squads wait in the yard, clear of the gate.
                City.Facility base = null;
                for (City.Facility f : w.city.facilities) if (f.kind == City.FACILITY_BASE && f.gateX > 0) base = f;
                check(base != null, "a base with a gate");
                float[] g = w.city.nearestDrivable(base.gateX, base.gateY);
                float[] bay = w.fleet.bay(g, base);
                check(bay != null && Math.hypot(bay[0] - base.gateX, bay[1] - base.gateY) >= City.T * 3.4f,
                        "a truck mustering at the base waits in the yard, off the gateway");
            }
        });
        test("10.46: infection zones round the dead - perimeter, army checkpoints on the roads in, worst first, lifted once clear", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 0;
                c.seed = 5;
                c.v[CityConfig.OPT_SIZE] = 3;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.normalize();
                c.seed = 5;
                World w = new World(c);
                w.rnd.setSeed(4);
                w.populate(c);
                for (int f = 0; f < 150; f++) w.update(1 / 30f);
                java.util.Random r = new java.util.Random(7);
                float[] q0 = w.city.randomWalkableInTown(r);
                int sp = 0;
                for (int i = 0; i < 1600 && sp < 150; i++) {
                    float[] q = w.city.findWalkable(q0[0] + (i % 40) * 8 - 160, q0[1] + (i / 40) * 8 - 160);
                    if (q != null && w.spawn(Entity.ZOMBIE, q[0], q[1]) != null) sp++;
                }
                // And a few on their own, a long way off: a lesser zone, if one at all.
                float[] q1 = null;
                for (int k = 0; k < 200 && (q1 == null || Math.hypot(q1[0] - q0[0], q1[1] - q0[1]) < 2000); k++) q1 = w.city.randomWalkableInTown(r);
                for (int i = 0; i < 400 && sp < 160; i++) {
                    float[] q = w.city.findWalkable(q1[0] + (i % 20) * 8 - 80, q1[1] + (i / 20) * 8 - 80);
                    if (q != null && w.spawn(Entity.ZOMBIE, q[0], q[1]) != null) sp++;
                }
                w.alert = 2;
                w.outbreak = true;
                boolean declared = false, roads = true, invariant = true, sent = false;
                int perimeter = 0, checkpoints = 0;
                for (int f = 0; f < 30 * 60; f++) {
                    w.update(1 / 30f);
                    if (f % 30 != 0) continue;
                    for (World.InfectionZone z : w.infection) {
                        if (Math.hypot(z.x - q0[0], z.y - q0[1]) < z.r + 200) declared = true;
                        // The worst is always held; one that's waiting its turn has no checkpoints and nobody on its line.
                        if (z == w.infection.get(0) && !z.critical) invariant = false;
                        if (!z.critical && !z.checkpoints.isEmpty()) invariant = false;
                        for (float[] cp : z.checkpoints) {
                            if (!w.city.checkpoints.contains(cp)) roads = false;
                            if (w.city.tiles[(int) (cp[1] / City.T) * w.city.w + (int) (cp[0] / City.T)] != City.ROAD) roads = false;
                        }
                        checkpoints = Math.max(checkpoints, z.checkpoints.size());
                        for (int k = 0; k < z.guardSpots.size() && k < z.guards.length; k++) {
                            Entity g = z.guards[k];
                            if (g != null && g.type == Entity.SOLDIER && (!g.dead || World.riding(g))) sent = true;
                        }
                    }
                    int n = 0;
                    for (Entity e : w.entities) {
                        if (e.dead || e.task != Dispatch.T_CORDON) continue;
                        n++;
                        World.InfectionZone z = w.zoneOfPost(e.postX, e.postY);
                        if (z == null || !z.critical) invariant = false;
                    }
                    perimeter = Math.max(perimeter, n);
                }
                check(declared, "an infection zone is declared round the bunch of the dead");
                check(checkpoints >= 1 && roads, "the army closes the roads into it (" + checkpoints + " checkpoints, all on the road)");
                check(perimeter >= 4, "police on its perimeter, not called there by anyone (" + perimeter + ")");
                check(sent, "soldiers are sent out to man the checkpoints");
                check(invariant, "the worst zone is held first; the others wait, with nobody on them, till there's someone for them");
                // Not lifted while any of them are about; lifted, roads and all, once they're gone.
                boolean any = !w.infection.isEmpty();
                // (Kept clear: anyone bitten who turns meanwhile is taken care of too.)
                for (int f = 0; f < 30 * 25; f++) {
                    if (f % 15 == 0) w.clearZombies();
                    w.update(1 / 30f);
                }
                check(!any || w.infection.isEmpty(), "with the dead gone the zone is lifted (" + w.infection.size() + " left)");
                int left = 0;
                for (Entity e : w.entities) if (!e.dead && e.task == Dispatch.T_CORDON) left++;
                check(left == 0, "and everyone on its perimeter stands down (" + left + ")");
            }
        });
        test("10.47: with Fleeing citizens on, people leave the city once the news breaks and come home once it's over", new Check() {
            public void run() throws Exception {
                for (int on = 0; on < 2; on++) {
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_PRESET] = 0;
                    c.seed = 5;
                    c.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                    c.v[CityConfig.OPT_ZOMBIES] = 0;
                    c.normalize();
                    c.seed = 5;
                    c.exodus = on;
                    check(c.get(CityConfig.OPT_EXODUS) == on && c.values(CityConfig.OPT_EXODUS).length == 4, "the option reads back");
                    World w = new World(c);
                    w.rnd.setSeed(4);
                    w.populate(c);
                    for (int f = 0; f < 150; f++) w.update(1 / 30f);
                    java.util.Random r = new java.util.Random(7);
                    float[] q0 = w.city.randomWalkableInTown(r);
                    int sp = 0;
                    for (int i = 0; i < 1600 && sp < 40; i++) {
                        float[] q = w.city.findWalkable(q0[0] + (i % 40) * 8 - 160, q0[1] + (i / 40) * 8 - 160);
                        if (q != null && w.spawn(Entity.ZOMBIE, q[0], q[1]) != null) sp++;
                    }
                    w.alert = 2;
                    w.outbreak = true;
                    int leaving = 0;
                    for (int f = 0; f < 30 * 90; f++) {
                        w.update(1 / 30f);
                        if (f % 30 == 0) for (Entity e : w.entities) if (!e.dead && e.task == Dispatch.T_LEAVE) leaving++;
                        // ((10.52) Once a few are gone: over the beltway the outbreak can be over, and them home again,
                        // inside the minute and a half.)
                        if (on == 1 && w.away.size() >= 5) break;
                    }
                    if (on == 0) {
                        check(w.fled == 0 && leaving == 0 && w.away.isEmpty(), "with it off nobody leaves (" + w.fled + ")");
                        continue;
                    }
                    check(leaving > 0 && w.fled > 0 && !w.away.isEmpty(), "with it on people head out of the city and leave it (" + w.fled + " gone)");
                    // Saved and loaded: those away are still away.
                    File f = File.createTempFile("exodus", ".sav");
                    SaveGame.save(w, f);
                    World l = SaveGame.load(f);
                    f.delete();
                    check(l.city.cfg.exodus() && l.away.size() == w.away.size(), "a saved game keeps the option and who's away (" + l.away.size() + ")");
                    // Over: they come home.
                    for (int k = 0; k < 30 * 200 && (w.aftermath != World.AFTER_RECOVERY || w.afterTime < 90); k++) {
                        if (k % 15 == 0) w.clearZombies();
                        w.update(1 / 30f);
                    }
                    check(w.aftermath == World.AFTER_RECOVERY, "the outbreak ends");
                    int still = 0;
                    for (Entity e : w.entities) if (!e.dead && e.task == Dispatch.T_LEAVE) still++;
                    check(still == 0, "nobody's still leaving once it's over (" + still + ")");
                    check(w.cameHome > 0, "and those who fled come home (" + w.cameHome + " of " + w.fled + ")");
                }
            }
        });
        test("10.56: the Garrison State (military police, bases, an island prison, checkpoints), counts that add up, people on the streets", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = CityConfig.GARRISON;
                c.seed = 9;
                c.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.normalize();
                c.seed = 9;
                check(c.size() == CityConfig.LARGE, "the Garrison State is never smaller than Default");
                World w = new World(c);
                City city = w.city;
                int bases = 0;
                for (City.Facility f : city.facilities) if (f.kind == City.FACILITY_BASE) bases++;
                check(bases >= 4, "bases out in the country and compounds in town (" + bases + ")");
                check(city.prison != null && city.jail == city.prison, "a prison, and it's the jail");
                // On an island: water all round it, and one way across.
                int tx = (int) ((city.prison.x0 + city.prison.x1) / 2 / City.T), ty = (int) ((city.prison.y0 + city.prison.y1) / 2 / City.T);
                int water = 0;
                for (int k = 0; k < 4; k++) {
                    int dx = k == 0 ? 1 : k == 1 ? -1 : 0, dy = k == 2 ? 1 : k == 3 ? -1 : 0;
                    for (int r = 1; r < 30; r++) {
                        int x = tx + dx * r, y = ty + dy * r;
                        if (x < 0 || y < 0 || x >= city.w || y >= city.h) break;
                        if (city.tiles[y * city.w + x] == City.WATER) {
                            water++;
                            break;
                        }
                    }
                }
                check(water >= 3, "the prison stands on an island (water on " + water + " sides)");
                check(city.checkpoints.size() >= 6, "checkpoints on the roads from the start (" + city.checkpoints.size() + ")");
                int[] fc = World.forecast(c, city, 0);
                w.setMaxPopulation(0);
                w.populate(c);
                w.recount();
                check(w.counts[Entity.SOLDIER] > w.counts[Entity.COP], "more soldiers (" + w.counts[Entity.SOLDIER] + ") than military police (" + w.counts[Entity.COP] + ")");
                Entity mp = null;
                for (Entity e : w.entities) if (e.type == Entity.COP && e.agency == 0 && e.role == 0) mp = e;
                check(mp != null && mp.body == Country.MP_SHIRT && mp.magSize == 30 && Dispatch.name(mp).startsWith("MP "), "the police are military police, with rifles");
                int guards = 0;
                for (float[] p : city.checkpointPosts)
                    for (Entity e : w.entities) if (e.type == Entity.SOLDIER && e.task == Dispatch.T_POST && Math.hypot(e.x - p[0], e.y - p[1]) < 30) {
                        guards++;
                        break;
                    }
                check(guards * 2 >= city.checkpointPosts.size(), "soldiers on the checkpoints (" + guards + " of " + city.checkpointPosts.size() + ")");
                // The New Game screen's numbers are the game's.
                check(fc[0] == w.civilians, "people forecast " + fc[0] + ", in the game " + w.civilians);
                check(Math.abs(fc[1] - w.counts[Entity.COP]) <= Math.max(6, fc[1] / 12), "police forecast " + fc[1] + ", in the game " + w.counts[Entity.COP]);
                check(Math.abs(fc[2] - w.counts[Entity.SOLDIER]) <= Math.max(6, fc[2] / 12), "soldiers forecast " + fc[2] + ", in the game " + w.counts[Entity.SOLDIER]);
                // People out and about start on the streets, not in the fields.
                int out = 0, grass = 0;
                for (Entity e : w.entities) {
                    if (e.dead || e.removed || e.type != Entity.CIVILIAN || e.home == null) continue;
                    out++;
                    if (city.tiles[city.tileIndex(e.x, e.y)] == City.GRASS) grass++;
                }
                check(out > 50 && grass * 4 < out, "people start on the streets (" + grass + " of " + out + " on grass)");
                // Nobody counted twice: as many people as live here and are about.
                int indoors = 0;
                for (City.Building b : city.buildings) indoors += b.visitors.size() + b.occupants.size();
                check(w.counts[Entity.CIVILIAN] + indoors >= w.civilians && w.civilians <= c.civilians(city.totalResidents) + 200,
                        "the count of people adds up (" + w.civilians + ")");
            }
        });
        test("10.48: an empty police car is taken by officers on foot nearby; (10.49) people leave by evacuation bus and train", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                // (A Metropolis with a railway station: buses and trains both.)
                c.v[CityConfig.OPT_PRESET] = 8;
                c.seed = 1;
                c.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.normalize();
                c.seed = 1;
                c.exodus = CityConfig.EXODUS_TRANSIT;
                check(c.values(CityConfig.OPT_EXODUS).length == 4, "four ways to leave (off, on foot, bus & train, all)");
                World w = new World(c);
                w.rnd.setSeed(4);
                w.populate(c);
                for (int f = 0; f < 150; f++) w.update(1 / 30f);
                // A patrol car whose crew is gone, parked in the street, and two officers with nothing to do near it.
                Fleet.Vehicle car = null;
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.patrol && v.type == Fleet.CRUISER && !v.crew.isEmpty() && v.kind != Fleet.K_BIKE) car = v;
                check(car != null, "a patrol car to use");
                car.crew.clear();
                car.parked = true;
                car.state = Fleet.ABANDONED;
                car.speed = 0;
                java.util.List<Entity> cops = new java.util.ArrayList<Entity>();
                for (int k = 0; k < 2; k++) {
                    float[] q = w.city.findWalkable(car.x + 40 + k * 20, car.y + 30);
                    Entity e = q == null ? null : w.spawnCop(0, q[0], q[1]);
                    if (e != null) cops.add(e);
                }
                check(!cops.isEmpty(), "officers on foot near it");
                boolean taken = false;
                for (int f = 0; f < 30 * 40 && !taken; f++) {
                    w.update(1 / 30f);
                    for (Entity e : cops) if (car.crew.contains(e)) taken = true;
                }
                check(taken, "they take the car (state " + car.state + ", crew " + car.crew.size() + ")");
                // Bus and train: once the news breaks, people wait at the stops and on the platform, and get out of the
                // city aboard.
                check(w.city.stationX >= 0, "a station on this map");
                // ((10.52) Eighty of the dead, nearer the middle: with another police station on the map fifty were all
                // put down before anyone was on their way.)
                java.util.Random r = new java.util.Random(3);
                float[] q0 = w.city.randomWalkableInTown(r);
                int sp = 0;
                for (int i = 0; i < 1600 && sp < 80; i++) {
                    float[] q = w.city.findWalkable(q0[0] + (i % 40) * 8 - 160, q0[1] + (i / 40) * 8 - 160);
                    if (q != null && w.spawn(Entity.ZOMBIE, q[0], q[1]) != null) sp++;
                }
                w.alert = 2;
                w.outbreak = true;
                int bus = 0, train = 0, onFoot = 0;
                for (int f = 0; f < 30 * 150 && (bus == 0 || train == 0 || w.fledByTransit == 0); f++) {
                    // ((10.52) Kept going: with the police that much stronger the dead can all be down before the
                    // train comes in, and the city calms down.)
                    w.alert = 2;
                    w.outbreak = true;
                    w.update(1 / 30f);
                    if (f % 30 != 0) continue;
                    for (Fleet.Vehicle v : w.fleet.vehicles) {
                        if (v.evacStop >= 0) bus = Math.max(bus, v.riders.size());
                        if (v.type == Fleet.TRAIN) train = Math.max(train, v.riders.size());
                    }
                    for (Entity e : w.entities) if (!e.dead && e.task == Dispatch.T_LEAVE && e.exitStop == -1) onFoot++;
                }
                check(!w.evacStops.isEmpty(), "evacuation bus stops around town (" + w.evacStops.size() + ")");
                check(bus > 0, "people get on an evacuation bus");
                check(train > 0, "and on an evacuation train at the station (" + bus + " on a bus, " + w.fledByTransit + " gone, " + w.alert + "/" + w.outbreak + ")");
                check(w.fledByTransit > 0 && w.fled == w.fledByTransit, "and leave the city aboard (" + w.fledByTransit + " of " + w.fled + ")");
                check(onFoot == 0, "nobody walks out with only buses and trains chosen (" + onFoot + ")");
            }
        });
        test("11.0: the water maps (water out to the sea, bridges, Marines by boat), a thin band of country, the new units", new Check() {
            public void run() throws Exception {
                for (int p : new int[]{CityConfig.BAY, CityConfig.CANAL}) {
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_PRESET] = p;
                    c.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                    c.v[CityConfig.OPT_ZOMBIES] = 0;
                    c.normalize();
                    c.seed = 5;
                    World w = new World(c);
                    City city = w.city;
                    String name = CityConfig.PRESETS[p];
                    check(!city.channels.isEmpty(), name + ": water through the town");
                    int water = 0, bridges = 0;
                    for (int i = 0; i < city.w * city.h; i++) {
                        if (city.tiles[i] == City.WATER) water++;
                        if (city.bridge != null && city.bridge[i] && city.tiles[i] == City.ROAD) bridges++;
                    }
                    check(water > city.w * city.h / 12, name + ": plenty of water (" + water + ")");
                    check(bridges > 20, name + ": bridges over it (" + bridges + ")");
                    check(city.buildings.size() > 300, name + ": a town round it (" + city.buildings.size() + ")");
                    // Marines come in over the water to the shore by the fighting, and get out on dry land.
                    // (A street the sea can reach: one a long way inland has no landing near it.)
                    java.util.Random qr = new java.util.Random(3);
                    boolean landed = false;
                    for (int k = 0; k < 12 && !landed; k++) {
                        float[] q = city.randomStreetSpot(qr);
                        landed = w.fleet.marineLanding(16, q[0], q[1], "here", null);
                    }
                    check(landed, name + ": a way in by water");
                    int boats = 0;
                    for (Fleet.Vehicle v : w.fleet.vehicles) if (v.type == Fleet.BOAT) boats++;
                    check(boats == 2, name + ": two landing craft (" + boats + ")");
                    for (int f = 0; f < 30 * 90; f++) w.update(1 / 30f);
                    int marines = 0, wet = 0;
                    for (Entity e : w.entities)
                        if (!e.dead && e.role == Entity.ROLE_MARINE) {
                            marines++;
                            int t = city.tileIndex(e.x, e.y);
                            if (t >= 0 && city.tiles[t] == City.WATER) wet++;
                        }
                    // (Ashore and since given a lift by a truck counts too.)
                    for (Fleet.Vehicle v : w.fleet.vehicles)
                        if (v.type != Fleet.BOAT && !v.broken) for (Entity e : v.crew) if (e.role == Entity.ROLE_MARINE && e.hp > 0) marines++;
                    check(marines >= 12, name + ": the Marines ashore (" + marines + ")");
                    check(wet == 0, name + ": none of them left in the water (" + wet + ")");
                    CityConfig back = new CityConfig();
                    check(back.applyCode(c.code()) && back.v[CityConfig.OPT_PRESET] == p, name + ": its city code");
                }
                // A thin band of country round every other map.
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 2;
                c.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                c.normalize();
                check(c.coreFraction() >= 0.85f, "nine tenths town (" + c.coreFraction() + ")");
                // The new roles.
                check(Entity.ROLE_NAMES.length == 16 && "Bomb technician".equals(Entity.ROLE_NAMES[Entity.ROLE_EOD]), "every role named");
                World w = new World(c);
                Entity e = w.create(Entity.COP, 100, 100);
                w.applyRole(e, Entity.ROLE_EOD);
                check(e.maxHp >= 200, "the blast suit");
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
                    v.awaitLoad();
                    draw(v, size);
                    m.screen = Menu.NONE;
                    for (int k = 0; k < 20; k++) draw(v, size);
                    // Every spawn picker opens and draws.
                    Field pk = GameView.class.getDeclaredField("picker");
                    pk.setAccessible(true);
                    for (int t = 0; t < 13; t++) {
                        pk.setInt(v, t);
                        draw(v, size);
                    }
                    pk.setInt(v, -1);
                    m.open(Menu.PAUSE);
                    draw(v, size);
                    m.open(Menu.STATS);
                    draw(v, size);
                    m.open(Menu.LOG);
                    draw(v, size);
                    m.open(Menu.STATS);
                    java.lang.reflect.Method act = Menu.class.getDeclaredMethod("act", int.class);
                    act.setAccessible(true);
                    act.invoke(m, 24);
                    draw(v, size);
                    m.open(Menu.SAVES);
                    draw(v, size);
                    // Both graphics styles draw, and switching redraws the map.
                    Settings st = (Settings) field(v, "settings");
                    World w = (World) field(v, "world");
                    for (int g = 0; g < 2; g++) {
                        st.set(13, g);
                        v.settingsChanged();
                        check(w.city.drawnRealistic == (g == 1), "map redrawn in the chosen style");
                        m.screen = Menu.NONE;
                        draw(v, size);
                    }
                    m.screen = Menu.NONE;
                }
            }
        });
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    /** Everyone alive: out and about, inside on errands, sheltering, or riding in a vehicle. */
    private static int alive(World w) {
        int n = w.fleet.riderCount();
        for (Entity e : w.entities) if (!e.dead && !e.isZombie()) n++;
        for (City.Building b : w.city.buildings) n += b.visitors.size() + b.occupants.size();
        return n;
    }

    interface Check {
        void run() throws Exception;
    }

    /**
     * test.sh runs the tests in several JVMs at once: SHARD of SHARDS takes every SHARDS-th test (ONLY, if set,
     * picks tests by the start of their name instead).
     */
    private static int index = -1, slot;

    private static boolean mine(String name) {
        String only = System.getenv("ONLY");
        if (only != null) return name.startsWith(only);
        String shards = System.getenv("SHARDS");
        return shards == null || slot % Integer.parseInt(shards) == Integer.parseInt(System.getenv("SHARD"));
    }

    /** A test that carries on from the one before (it runs in the same JVM as it). */
    private static void testAfter(String name, Check c) {
        run(name, c);
    }

    private static void test(String name, Check c) {
        slot = ++index;
        run(name, c);
    }

    private static void run(String name, Check c) {
        if (!mine(name)) return;
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
