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
                    for (int size = 0; size < 4; size++) {
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
                for (Fleet.Vehicle v : w.fleet.vehicles) carried += Math.max(0, v.passengers);
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
                    c.v[CityConfig.OPT_SIZE] = 0;
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
                c.v[CityConfig.OPT_SIZE] = 3;
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
                    c.v[CityConfig.OPT_SIZE] = 3;
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
                c.v[CityConfig.OPT_SIZE] = 1;
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
                    c.v[CityConfig.OPT_SIZE] = 1;
                    City city = new City(c);
                    check(city.cityHall != null && city.cityHall.doorX > 0, CityConfig.PRESETS[preset] + ": a city hall with a door");
                    if (preset != 9)
                        check(city.courthouse != null && city.jail != null && city.worksDepot != null,
                                CityConfig.PRESETS[preset] + ": courthouse, jail and works depot");
                    check(city.jail == null || city.jail.capacity == 0, "nobody shelters in the cells");
                }
                CityConfig c = new CityConfig();
                c.seed = 5;
                c.v[CityConfig.OPT_SIZE] = 1;
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
                    c.v[CityConfig.OPT_SIZE] = 1;
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
                        if (!e.dead && !e.hidden && city.tiles[city.tileIndex(e.x, e.y)] == City.WATER) wet++;
                    for (Fleet.Vehicle v : w.fleet.vehicles)
                        if (v.type != Fleet.HELI && v.type != Fleet.JET && city.tiles[city.tileIndex(v.x, v.y)] == City.WATER) wet++;
                    check(wet == 0, name + ": " + wet + " people or cars in the water");
                }
                // Every other kind of map has its armory too.
                for (int preset = 0; preset < 10; preset++) {
                    CityConfig c = new CityConfig();
                    c.seed = 3 + preset;
                    c.v[CityConfig.OPT_PRESET] = preset;
                    c.v[CityConfig.OPT_SIZE] = 1;
                    check(new City(c).armory != null, CityConfig.PRESETS[preset] + ": a National Guard armory");
                }
            }
        });
        test("doors clear of trees, the armory manned, SWAT called to a horde, parked army trucks used", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.seed = 6;
                c.v[CityConfig.OPT_SIZE] = 1;
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
                b.v[CityConfig.OPT_SIZE] = 1;
                City base = new City(b);
                City.Facility f = base.nearestFacility(City.FACILITY_BASE, 0, 0);
                check(f != null && base.takeParkedTruck(f.x, f.y, 600) != null, "a parked truck drives off the base");
            }
        });
        test("police and soldiers carry a real load and make it count; people keep out of bloater gas", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.seed = 3;
                c.v[CityConfig.OPT_SIZE] = 1;
                c.v[CityConfig.OPT_ZOMBIES] = 4;
                World w = new World(c);
                w.populate(c);
                for (Entity e : w.entities) {
                    if (e.type == Entity.COP && e.role == 0) check(e.ammo + e.reserve == 60, "an officer carries 60 rounds (" + (e.ammo + e.reserve) + ")");
                    if (e.type == Entity.SOLDIER && e.role == Entity.ROLE_RIFLE) check(e.ammo + e.reserve == 210, "a soldier carries 210 rounds (" + (e.ammo + e.reserve) + ")");
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
                q.v[CityConfig.OPT_SIZE] = 1;
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
        test("traffic keeps its distance: hardly any crashes, and patrol cars keep to their side", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_SIZE] = 1;
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
                check(crashes / 2 <= 6, (crashes / 2) + " crashes in a minute and a half of ordinary traffic");
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
