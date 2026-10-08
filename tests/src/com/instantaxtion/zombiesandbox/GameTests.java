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
        test("every country: its own names, look and side of the road, kept in the city code", new Check() {
            public void run() {
                java.util.HashSet<String> streets = new java.util.HashSet<String>();
                for (int k = 0; k < Country.NAMES.length; k++) {
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_SIZE] = CityConfig.SMALL;
                    c.v[CityConfig.OPT_COUNTRY] = k;
                    c.seed = 9;
                    City city = new City(c);
                    check(city.country.id == k, "the city is in its country");
                    streets.add(city.streets.get(city.streets.size() / 2).name);
                    CityConfig d = new CityConfig();
                    check(d.applyCode(c.code()) && d.country() == k && d.seed == 9, "the country is in the city code");
                }
                check(streets.size() >= 9, "street names differ by country (" + streets.size() + ")");
                check(Country.get(Country.AUSTRALIA).leftHand && Country.get(Country.JAPAN).leftHand && Country.get(Country.MALAYSIA).leftHand
                        && !Country.get(Country.USA).leftHand && !Country.get(Country.RUSSIA).leftHand, "Australia, Japan and Malaysia drive on the left");
                CityConfig old = new CityConfig();
                check(old.applyCode("12-44") && old.country() == Country.USA, "old codes are American cities");
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
        test("new kinds of building for every country, and saved cities still rebuild the same", new Check() {
            public void run() {
                String[][] own = {{"Barber shop", "Brownstone", "Trailer home", "BBQ smokehouse"}, {"Pie shop", "Chemist", "Fibro shack", "Terrace house"},
                        {"Okonomiyaki shop", "Cram school", "Gasshō farmhouse", "Yakitori stand"}, {"Pharmacie", "Bistrot", "Maison bourgeoise", "Longère"},
                        {"Farmacia", "Birriería", "Casa de adobe", "Carnicería"}};
                for (int country = 0; country < 5; country++) {
                    CityConfig c = new CityConfig();
                    c.seed = 42 + country;
                    c.v[CityConfig.OPT_SIZE] = CityConfig.MASSIVE;
                    c.v[CityConfig.OPT_COUNTRY] = country;
                    City city = new City(c);
                    java.util.HashSet<String> names = new java.util.HashSet<String>();
                    for (City.Building b : city.buildings) if (b.typeName() != null) names.add(b.typeName());
                    int found = 0;
                    for (String n : own[country]) if (names.contains(n)) found++;
                    check(found >= 2, "country " + country + ": " + found + " of its new kinds of building");
                }
                // (The same city as version 10.0 built from this seed, building for building, for games saved
                // before the government quarter.)
                CityConfig c = new CityConfig();
                c.seed = 42;
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
                int before = w.counts[Entity.RAIDER];
                w.blackout = true;
                for (int f = 0; f < 30 * 2; f++) w.update(1 / 30f);
                check(w.jailBroken && w.inmates == 0 && w.counts[Entity.RAIDER] > before, "jailbreak when the power fails ("
                        + (w.counts[Entity.RAIDER] - before) + " escaped)");
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
                for (int k = 0; k < 10; k++) w.spawn(Entity.ZOMBIE, near.x + 40 + k * 3, near.y);
                boolean sent = false;
                for (int f = 0; f < 30 * 60 && !sent; f++) {
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
        test("police and soldiers carry a real load and make it count; people keep out of bloater gas", new Check() {
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
                // A cloud of gas on someone's way: they go round it.
                CityConfig q = new CityConfig();
                q.seed = 7;
                q.v[CityConfig.OPT_SIZE] = CityConfig.MEDIUM;
                q.v[CityConfig.OPT_ZOMBIES] = 0;
                World g = new World(q);
                g.populate(q);
                int walkedIn = 0, tried = 0;
                for (int round = 0; round < 12; round++) {
                    for (int f = 0; f < 30 * 3; f++) g.update(1 / 30f);
                    Entity walker = null;
                    for (Entity e : g.entities)
                        if (!e.dead && !e.hidden && e.type == Entity.CIVILIAN && e.want > 5 && e.fleeTimer <= 0 && (walker == null || e.want > walker.want)) walker = e;
                    if (walker == null) continue;
                    float l = (float) Math.hypot(walker.mx, walker.my) + 0.001f;
                    float[] cloud = {walker.x + walker.mx / l * 80, walker.y + walker.my / l * 80, 36, 9};
                    g.gases.add(cloud);
                    tried++;
                    boolean in = false;
                    for (int f = 0; f < 30 * 4; f++) {
                        g.update(1 / 30f);
                        if (!walker.dead && Math.hypot(walker.x - cloud[0], walker.y - cloud[1]) < cloud[2]) in = true;
                    }
                    if (in) walkedIn++;
                    g.gases.clear();
                }
                check(tried >= 6 && walkedIn * 6 <= tried, walkedIn + " of " + tried + " people walked into a gas cloud in their way");
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
                // Denmark is flat; Egypt is desert; Canada is wooded with birches and pines.
                CityConfig dk = new CityConfig();
                dk.seed = 7;
                dk.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                dk.v[CityConfig.OPT_COUNTRY] = Country.DENMARK;
                int rock = 0;
                for (byte b : new City(dk, 0.1f).tiles) if (b == City.ROCK) rock++;
                check(rock == 0, "no mountains in Denmark");
                CityConfig eg = new CityConfig();
                eg.seed = 7;
                eg.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                eg.v[CityConfig.OPT_COUNTRY] = Country.EGYPT;
                City egypt = new City(eg, 0.1f);
                int sand = 0, grass = 0;
                for (int t = 0; t < egypt.w * egypt.h; t++) {
                    float x = (t % egypt.w + 0.5f) * City.T, y = (t / egypt.w + 0.5f) * City.T;
                    if (egypt.inTown(x, y)) continue;
                    if (egypt.tiles[t] == City.SAND) sand++;
                    if (egypt.tiles[t] == City.GRASS) grass++;
                }
                check(sand > grass * 4, "Egypt's countryside is desert (" + sand + " sand, " + grass + " grass)");
                CityConfig ca = new CityConfig();
                ca.seed = 7;
                ca.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                ca.v[CityConfig.OPT_COUNTRY] = Country.CANADA;
                City canada = new City(ca, 0.1f);
                int birch = 0, pine = 0;
                for (float[] t : canada.trees) {
                    if (t.length < 4) continue;
                    if (t[3] == Country.TK_BIRCH) birch++;
                    if (t[3] == Country.TK_PINE) pine++;
                }
                check(birch > 100 && pine > 100, "Canada's woods are birch and pine (" + birch + ", " + pine + ")");
                // Gun stores only where people can buy guns.
                for (int k : new int[]{Country.USA, Country.JAPAN, Country.CANADA, Country.CHINA}) {
                    CityConfig g = new CityConfig();
                    g.seed = 3;
                    g.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                    g.v[CityConfig.OPT_COUNTRY] = k;
                    int guns = 0;
                    for (City.Building b : new City(g, 0.1f).buildings) if (b.kind == City.SHOP && b.shopType == 1) guns++;
                    check(Country.get(k).gunShops() ? guns > 0 : guns == 0, Country.NAMES[k] + ": " + guns + " gun stores");
                }
                check(Country.NAMES.length == 18 && Country.NAMES[Country.CANADA].equals("Canada"), "Canada, China, Egypt, Saudi Arabia and the Nordics");
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
                check(medic > 0 && gren > 0, "squads have combat medics and grenadiers (" + medic + ", " + gren + ")");
                check(marks > 0, "a police marksman at the precinct (" + marks + ")");
                int bikes = 0;
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.kind == Fleet.K_BIKE) bikes++;
                check(bikes > 0, "police motorcycles on patrol (" + bikes + ")");
                check(w.dispatch.riotVans > 0 && w.dispatch.policeAir > 0, "riot vans and the police helicopter are ready");
                // A wounded soldier gets patched up by the medic.
                w.viewX0 = 0;
                w.viewY0 = 0;
                w.viewX1 = w.city.worldW();
                w.viewY1 = w.city.worldH();
                Entity doc = null;
                for (Entity e : w.entities) if (e.role == Entity.ROLE_CORPSMAN && e.task == Dispatch.T_NONE && e.rig == null) doc = e;
                Entity hurt = w.spawn(Entity.SOLDIER, doc.x + 20, doc.y);
                hurt.hp = 30;
                hurt.task = Dispatch.T_POST;
                hurt.postX = hurt.x;
                hurt.postY = hurt.y;
                for (int f = 0; f < 300; f++) w.update(1 / 30f);
                check(hurt.hp >= hurt.maxHp * 0.69f, "the combat medic patched up a wounded soldier (" + (int) hurt.hp + "/" + (int) hurt.maxHp + ")");
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
                for (int i = 0; i < 80; i++) gw.spawn(Entity.ZOMBIE, gw.city.worldW() / 2 + i, gw.city.worldH() / 2);
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
                c.v[CityConfig.OPT_COUNTRY] = Country.SWEDEN;
                c.v[CityConfig.OPT_PRESET] = 6;
                c.v[CityConfig.OPT_SIZE] = CityConfig.LARGE;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
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
                q.v[CityConfig.OPT_COUNTRY] = Country.SWEDEN;
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
                for (int i = 0; i < 4; i++) w.spawn(Entity.ZOMBIE, t[0] - 70 + i * 8, t[1] - 70);
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
