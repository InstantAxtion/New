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
        test("every city is split into named districts", new Check() {
            public void run() {
                for (int p = 0; p < CityConfig.PRESETS.length; p++) {
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_PRESET] = p;
                    c.seed = 77 + p;
                    City city = new City(c, 0.1f);
                    check(city.districts.size() >= 3, CityConfig.PRESETS[p] + " has districts");
                    int covered = 0;
                    for (City.District d : city.districts) {
                        check(d.name != null && d.name.length() > 0, "district named");
                        covered += d.tiles;
                    }
                    check(covered == city.w * city.h, "every tile is in a district");
                    check(city.districtOf(10, 10) != null, "districts can be looked up");
                }
            }
        });
        test("buildings: zombies inside burst out, survivors eat, gun stores arm people", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 5;
                World w = new World(c);
                w.populate(c);
                City.Building shelter = null, gunStore = null;
                for (City.Building b : w.city.buildings) {
                    if (shelter == null && b.capacity > 0 && b.kind == City.OFFICE) shelter = b;
                    if (gunStore == null && b.kind == City.SHOP && b.shopType == 1) gunStore = b;
                }
                check(shelter != null, "found an office");
                shelter.lurkers = 3;
                int before = w.zombieCount();
                w.burstOut(shelter);
                w.recount();
                check(shelter.lurkers == 0 && shelter.infestKnown, "lurkers came out");
                check(w.zombieCount() == before + 3, "three zombies on the street");
                // Survivors inside eat through the food (once the zombies that came out are dealt with).
                for (Entity e : w.entities) if (e.isZombie()) {
                    e.dead = true;
                    e.removed = true;
                }
                shelter.lurkers = 0;
                shelter.food = 5;
                for (int i = 0; i < 4; i++) {
                    Entity e = w.create(Entity.CIVILIAN, shelter.doorX, shelter.doorY);
                    e.dead = true;
                    e.removed = true;
                    shelter.occupants.add(e);
                }
                for (int i = 0; i < 30 * 70; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(shelter.food < 5, "food was eaten");
                check(gunStore == null || gunStore.stock > 0, "gun stores start stocked");
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
                int before = w.turnedAway, stopped = 0;
                for (int i = 0; i < 40; i++) {
                    Entity e = w.create(Entity.CIVILIAN, z.x, z.y);
                    e.infected = true;
                    e.infectTimer = 30;
                    if ((Boolean) m.invoke(w, e, z)) stopped++;
                    check(e.screened, "checked at the gate");
                }
                int caught = w.turnedAway - before;
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
        test("villages and massive maps have countryside, lanes and few roads", new Check() {
            public void run() {
                int rails = 0;
                for (int p = 0; p < CityConfig.PRESETS.length; p++) {
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_PRESET] = p;
                    c.v[CityConfig.OPT_SIZE] = 1;
                    c.seed = 77 + p;
                    if (c.hasRail()) rails++;
                }
                check(rails > 0 && rails < CityConfig.PRESETS.length, "only some maps have a railway");
                for (int size : new int[]{1, 3}) {
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_PRESET] = 9;
                    c.v[CityConfig.OPT_SIZE] = size;
                    c.seed = 5;
                    City city = new City(c, 0.1f);
                    int road = 0, dirt = 0;
                    for (byte t : city.tiles) {
                        if (t == City.ROAD) road++;
                        if (t == City.DIRT) dirt++;
                    }
                    check(road < city.tiles.length / 25, "a village has few paved roads (" + road + ")");
                    check(dirt > 100, "dirt lanes lead out of the village");
                    check(city.railY0 < 0, "no railway through the village");
                    check(city.edgeRoad(new java.util.Random(1)) != null, "convoys can drive in from the edge");
                }
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_SIZE] = 3;
                c.seed = 9;
                City big = new City(c, 0.1f);
                check(big.w == 448, "massive maps are 448 tiles");
                int grass = 0;
                for (byte t : big.tiles) if (t == City.GRASS || t == City.TREE) grass++;
                check(grass > big.tiles.length / 4, "massive maps have open country around the city");
            }
        });
        test("firefighters fight fires, safe zones close, the Guard comes", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 21;
                World w = new World(c);
                w.populate(c);
                int ff = 0;
                for (Entity e : w.entities) if (e.type == Entity.FIREFIGHTER) ff++;
                check(ff >= 3, "firefighters wait at the fire station");
                check(!w.create(Entity.FIREFIGHTER, 0, 0).isZombie(), "firefighters are not zombies");
                City.Facility st = w.city.nearestFacility(City.FACILITY_FIRE, 0, 0);
                check(st != null, "the city has a fire station");
                float[] p = w.city.findWalkable(st.x + 120, st.y);
                w.ignite(p[0], p[1], 60);
                int fires = w.fires.size();
                World.Fire lit = w.fires.get(w.fires.size() - 1);
                w.dispatch.restoreZone(p[0] + 300, p[1], 120, false, "Test", 20, 2);
                for (int i = 0; i < 30 * 200; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(fires > 0 && (!w.fires.contains(lit) || lit.life <= 0), "the fire went out");
                check(w.dispatch.zones.isEmpty(), "a quiet, empty safe zone closes");
                for (Fleet.Vehicle v : w.fleet.vehicles) check(!v.cones && v.block == null, "roadblocks are cleared away");
                w.outbreak = true;
                w.outbreakTime = 120;
                w.warBalance = 0.3f;
                w.dispatch.squadReserve = 0;
                w.readiness = 0;
                w.callNationalGuard();
                check(w.guardCalled, "the Governor calls out the Guard");
                int trucks = 0;
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.guardUnit) trucks++;
                check(trucks == 2, "two Guard trucks are on the way");
                w.callNationalGuard();
                trucks = 0;
                for (Fleet.Vehicle v : w.fleet.vehicles) if (v.guardUnit) trucks++;
                check(trucks == 2, "the Guard comes only once");
            }
        });
        test("people live in homes, towns grow organically, trains stop and air support takes time", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 8;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 4;
                World w = new World(c);
                w.populate(c);
                check(w.city.totalResidents > 0, "the city's homes house people");
                int civ = 0, homed = 0;
                for (Entity e : w.entities)
                    if (e.type == Entity.CIVILIAN) {
                        civ++;
                        if (e.home != null && e.home.residents > 0) homed++;
                    }
                check(civ <= w.city.totalResidents + 10 && civ > 0, "no more people than homes (" + civ + ")");
                check(homed > civ * 9 / 10, "nearly everyone has a home (" + homed + "/" + civ + ")");
                CityConfig none = new CityConfig();
                none.v[CityConfig.OPT_CIVILIANS] = 0;
                check(none.civilians(500) == 0, "None means no residents out");
                check(w.city.crossings.size() >= 2, "the railway has level crossings");
                // Trains stop at the station.
                boolean stopped = false;
                for (int i = 0; i < 30 * 150 && !stopped; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    for (Fleet.Vehicle v : w.fleet.vehicles) if (v.type == Fleet.TRAIN && v.state == 0) stopped = true;
                }
                check(stopped, "a train stopped at the station");
                // Air support: approved at once, but it takes a while to arrive.
                w.dispatch.tankReserve = 0;
                w.dispatch.airSorties = 1;
                float[] p = w.city.findWalkable(w.city.worldW() / 2, w.city.worldH() / 2);
                w.dispatch.heavyContact(null, p[0], p[1], 30);
                check(w.dispatch.airDelay > 30, "air support takes time to get ready");
                boolean early = false, arrived = false;
                for (int i = 0; i < 30 * 200; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    if (w.fleet.heliBusy() && i < 30 * 30) early = true;
                    if (w.fleet.heliBusy()) arrived = true;
                }
                check(!early && arrived, "the helicopter comes, but not straight away");
                // A massive map's town is not a square.
                CityConfig mc = new CityConfig();
                mc.v[CityConfig.OPT_SIZE] = 3;
                mc.seed = 3;
                City big = new City(mc, 0.1f);
                int town = 0;
                for (int y = big.townY0; y < big.townY1; y++)
                    for (int x = big.townX0; x < big.townX1; x++) {
                        byte t = big.tiles[y * big.w + x];
                        if (t != City.GRASS && t != City.TREE && t != City.DIRT) town++;
                    }
                float fill = town / (float) ((big.townX1 - big.townX0) * (big.townY1 - big.townY0));
                check(fill < 0.9f, "the town has a ragged edge (" + fill + ")");
            }
        });
        test("people and zombies don't get stuck among trees", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 9;
                c.v[CityConfig.OPT_SIZE] = 3;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.v[CityConfig.OPT_CIVILIANS] = 0;
                c.seed = 2;
                World w = new World(c);
                w.populate(c);
                java.util.Random r = new java.util.Random(2);
                java.util.ArrayList<Entity> test = new java.util.ArrayList<Entity>();
                City city = w.city;
                for (int tries = 0; test.size() < 60 && tries < 100000; tries++) {
                    int tx = 1 + r.nextInt(city.w - 2), ty = 1 + r.nextInt(city.h - 2);
                    if (city.tiles[ty * city.w + tx] != City.GRASS) continue;
                    int trees = 0;
                    for (int oy = -1; oy <= 1; oy++)
                        for (int ox = -1; ox <= 1; ox++) if (city.tiles[(ty + oy) * city.w + tx + ox] == City.TREE) trees++;
                    if (trees < 4) continue;
                    Entity e = w.spawn(test.size() % 2 == 0 ? Entity.CIVILIAN : Entity.ZOMBIE, tx * City.T + 8, ty * City.T + 8);
                    if (e != null) test.add(e);
                }
                float[] px = new float[test.size()], py = new float[test.size()];
                for (int i = 0; i < test.size(); i++) {
                    px[i] = test.get(i).x;
                    py[i] = test.get(i).y;
                }
                for (int s = 0; s < 30 * 20; s++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                int stuck = 0;
                for (int i = 0; i < test.size(); i++)
                    if (Math.hypot(test.get(i).x - px[i], test.get(i).y - py[i]) < City.T) stuck++;
                check(test.size() >= 40, "found people in the woods");
                check(stuck <= test.size() / 8, "they walk out of the woods (" + stuck + " of " + test.size() + " stuck)");
            }
        });
        test("population matches the city, families are small and nobody queues at doors", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_PRESET] = 0;
                c.v[CityConfig.OPT_SIZE] = 2;
                c.v[CityConfig.OPT_CIVILIANS] = 4;
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 11;
                World w = new World(c);
                w.setMaxPopulation(0);
                check(w.maxEntities >= w.city.totalResidents * 2, "Auto leaves room for everyone who lives here");
                check(c.civilians(w.city.totalResidents) == w.city.totalResidents, "All means everyone, however big the city");
                w.populate(c);
                int[] followers = new int[1];
                java.util.HashMap<Entity, Integer> groups = new java.util.HashMap<Entity, Integer>();
                for (Entity e : w.entities) if (e.leader != null) groups.put(e.leader, groups.containsKey(e.leader) ? groups.get(e.leader) + 1 : 1);
                for (int g : groups.values()) followers[0] = Math.max(followers[0], g);
                check(followers[0] <= 5, "families are 1 to 5 people (" + followers[0] + " followers)");
                int start = alive(w);
                int worst = 0;
                for (int s = 0; s < 30 * 90; s++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    if (s % 150 != 0 || s < 30 * 20) continue;
                    for (City.Building b : w.city.buildings) {
                        if (b.doorX == 0) continue;
                        int n = 0;
                        for (Entity e : w.entities) if (!e.dead && Math.abs(e.x - b.doorX) < 24 && Math.abs(e.y - b.doorY) < 24) n++;
                        worst = Math.max(worst, n);
                    }
                }
                int inside = 0;
                for (City.Building b : w.city.buildings) inside += b.visitors.size();
                check(inside > 0, "people go inside on errands and at home");
                check(worst < 25, "no crowds at doors (" + worst + ")");
                check(Math.abs(alive(w) - start) <= start / 50 + 5, "nobody is lost going in and out (" + start + " -> " + alive(w) + ")");
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
                for (Entity e : w.entities) if (!e.dead && e.type == Entity.SOLDIER) soldier = e;
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
        test("drive, pick up weapons, go inside and lead a group", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 8;
                World w = new World(c);
                w.populate(c);
                int melee = 0;
                for (World.Pickup p : w.pickups) if (p.melee > 0) melee++;
                check(melee > 0, "bats and axes are lying about");
                Fleet.Vehicle car = null;
                // A car driving along a road (so the way ahead is clear).
                for (Fleet.Vehicle v : w.fleet.vehicles)
                    if (v.type == Fleet.CAR && !v.broken && !w.city.circleBlocked(v.x + (float) Math.cos(v.angle) * 20, v.y + (float) Math.sin(v.angle) * 20, 6))
                        car = v;
                Entity e = null;
                for (Entity o : w.entities) if (o.type == Entity.CIVILIAN && !o.dead && o.leader == null) e = o;
                float[] p = w.city.findWalkable(car.x + 8, car.y + 8);
                e.x = p[0];
                e.y = p[1];
                w.controlled = e;
                w.ctrlAction(World.CA_RALLY);
                w.ctrlAction(World.CA_ENTER_CAR);
                check(w.controlledCar != null && w.controlledCar.player == e, "gets behind the wheel");
                car = w.controlledCar;
                if (w.city.circleBlocked(car.x + (float) Math.cos(car.angle) * 20, car.y + (float) Math.sin(car.angle) * 20, 6)) car.angle += (float) Math.PI;
                float x0 = car.x, y0 = car.y;
                w.joyX = (float) Math.cos(car.angle);
                w.joyY = (float) Math.sin(car.angle);
                for (int i = 0; i < 90; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(Math.hypot(car.x - x0, car.y - y0) > 8, "the car drives");
                check(w.controlled == e, "still in control while driving");
                w.joyX = w.joyY = 0;
                w.ctrlAction(World.CA_EXIT_CAR);
                check(w.controlledCar == null && w.entities.contains(e) && !e.dead, "gets out");
                City.Building house = null;
                for (City.Building b : w.city.buildings) if (b.kind == City.HOUSE && b.doorX > 0 && b.lurkers == 0 && b.capacity > 0) house = b;
                e.x = house.doorX;
                e.y = house.doorY;
                w.ctrlAction(World.CA_ENTER);
                check(w.controlledIn == house && house.occupants.contains(e), "goes inside");
                for (int i = 0; i < 30 * 30; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(w.controlledIn == house && w.controlled == e, "the player isn't let out on their own");
                w.ctrlAction(World.CA_EXIT);
                check(w.controlledIn == null && w.entities.contains(e), "comes out");
                World.Pickup sg = new World.Pickup();
                sg.x = e.x + 3;
                sg.y = e.y;
                sg.weapon = Entity.W_SHOTGUN;
                sg.rounds = 12;
                w.pickups.add(sg);
                // (One thing a frame: there may be a bat by the door too.)
                for (int i = 0; i < 6; i++) w.update(1 / 30f);
                check(e.canShoot() && e.gunKind() == Entity.W_SHOTGUN, "picks up the shotgun by walking over it (" + w.pickups.contains(sg)
                        + ", controlled " + (w.controlled == e) + ", dead " + e.dead + ", in " + (w.controlledIn != null) + ", dist " + Math.hypot(sg.x - e.x, sg.y - e.y) + ")");
                w.controlled = null;
            }
        });
        test("fires spread, no cure, quarantine, supplies and militias", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 21;
                World w = new World(c);
                w.populate(c);
                int ammo = 0;
                for (City.Facility f : w.city.facilities) ammo += f.ammo;
                check(ammo > 0, "armouries are stocked");
                // A building left to burn comes down.
                City.Building b = null;
                for (City.Building o : w.city.buildings) if (o.kind == City.HOUSE && o.doorX > 0) b = o;
                w.igniteBuilding(b);
                check(b.fire != null, "the house catches fire");
                java.lang.reflect.Method m = World.class.getDeclaredMethod("updateBuildingFires", float.class);
                m.setAccessible(true);
                for (int i = 0; i < 30 * 150 && !b.collapsed; i++) {
                    b.fire.life += 1 / 30f;
                    m.invoke(w, 1 / 30f);
                }
                check(b.collapsed, "an unchecked fire burns it down");
                // There's no cure: a bite is for good, even at the hospital or with a medic.
                boolean noCure = true;
                for (java.lang.reflect.Method mm : World.class.getDeclaredMethods())
                    if (mm.getName().toLowerCase().contains("cure") || mm.getName().equals("revive")) noCure = false;
                for (java.lang.reflect.Field ff : World.class.getDeclaredFields())
                    if (ff.getName().toLowerCase().contains("cure")) noCure = false;
                check(noCure, "the cure is gone");
                Entity bitten = null;
                for (Entity e : w.entities) if (e.type == Entity.CIVILIAN && !e.dead) bitten = e;
                City.Facility hosp = w.city.nearestFacility(City.FACILITY_HOSPITAL, bitten.x, bitten.y);
                bitten.infected = true;
                bitten.infectTimer = 1000;
                bitten.x = hosp.x;
                bitten.y = hosp.y;
                Entity medic = w.spawn(Entity.MEDIC, hosp.x + 6, hosp.y);
                for (int i = 0; i < 30 * 10; i++) w.update(1 / 30f);
                check(bitten.infected || bitten.dead, "the bite can't be treated");
                // Quarantine.
                java.lang.reflect.Method seal = World.class.getDeclaredMethod("sealDistrict", int.class);
                seal.setAccessible(true);
                int before = w.barriers.size();
                seal.invoke(w, 0);
                check(w.barriers.size() > before, "roads out of a district are barricaded");
                File f = File.createTempFile("zcs", ".dat");
                SaveGame.save(w, f);
                World l = SaveGame.load(f);
                check(l.city.facilities.get(0).ammo == w.city.facilities.get(0).ammo, "the war's state is saved");
                f.delete();
            }
        });
        test("hordes roam between settlements, brutes charge, crawlers hide, wildlife", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_SIZE] = 3;
                c.v[CityConfig.OPT_ZOMBIES] = 3;
                c.seed = 6;
                World w = new World(c);
                w.populate(c);
                check(w.city.settlements.size() > 1, "a massive map has a town and hamlets");
                check(!w.animals.isEmpty(), "deer and foxes in the country");
                for (int i = 0; i < 6; i++) {
                    float[] p = w.city.randomWalkableInTown(w.rnd);
                    w.spawn(Entity.BRUTE, p[0], p[1]);
                }
                boolean charged = false, hid = false;
                for (int s = 0; s < 30 * 120 && !charged; s++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    for (Entity e : w.entities) if (e.charge > 0) charged = true;
                }
                check(charged, "a brute charges");
                // With nobody left to hunt (on foot or in a car), crawlers lie low.
                w.entities.clear();
                w.fleet.vehicles.clear();
                for (int i = 0; i < 6; i++) {
                    float[] q = w.city.randomWalkable(w.rnd);
                    w.spawn(Entity.CRAWLER, q[0], q[1]);
                }
                for (int s = 0; s < 30 * 90 && !hid; s++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    for (Entity e : w.entities) if (e.hidden) hid = true;
                }
                check(hid, "a crawler lies in wait");
            }
        });
        test("noise rings, who bit whom, and highlights", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 3;
                c.seed = 14;
                World w = new World(c);
                w.populate(c);
                w.noise(100, 100, 200);
                check(!w.noiseRings.isEmpty(), "a noise shows a ring");
                for (int s = 0; s < 30 * 120; s++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                }
                check(!w.bitBy.isEmpty(), "bites are traced");
                check(w.turned == 0 || !w.victims.isEmpty(), "the turned are credited to whoever bit them");
                int[] chain = w.chainFromPatientZero();
                check(w.patientZeroSeed != 0 && chain[0] >= 0, "patient zero is known");
                check(!w.highlightAt.isEmpty(), "big moments are noted");
                File f = File.createTempFile("zcs", ".dat");
                SaveGame.save(w, f);
                World l = SaveGame.load(f);
                check(l.bitBy.size() == w.bitBy.size() && l.highlightAt.size() == w.highlightAt.size(), "and saved");
                f.delete();
            }
        });
        test("safe zones take time to set up, keep clear of the dead and have a living border", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 21;
                World w = new World(c);
                w.populate(c);
                float[] far = w.city.findWalkable(w.city.worldW() * 0.3f, w.city.worldH() * 0.3f);
                float[] zp = w.city.findWalkable(far[0] + 120, far[1]);
                w.spawn(Entity.ZOMBIE, zp[0], zp[1]);
                w.update(1 / 30f);
                w.dispatch.orderZone(far[0], far[1]);
                check(w.dispatch.zones.isEmpty(), "no safe zone with the dead close by");
                for (Entity e : w.entities) if (e.isZombie()) e.dead = true;
                w.update(1 / 30f);
                w.dispatch.orderZone(far[0], far[1]);
                check(w.dispatch.zones.size() == 1, "a safe zone is ordered");
                Dispatch.SafeZone z = w.dispatch.zones.get(0);
                check(!z.open && w.dispatch.zoneAt(z.x, z.y, 1) == null, "it isn't open straight away");
                for (int s = 0; s < 30 * 150 && !z.open && !z.removed; s++) w.update(1 / 30f);
                check(z.open && w.dispatch.zoneAt(z.x, z.y, 1) == z, "it opens once the sandbags are up");
                float mn = Float.MAX_VALUE, mx = 0;
                for (float e : z.edge) {
                    mn = Math.min(mn, e);
                    mx = Math.max(mx, e);
                }
                check(mx > mn + 1, "the border isn't a perfect circle");
            }
        });
        test("five countries: their own names, look and side of the road, kept in the city code", new Check() {
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
                check(streets.size() >= 4, "street names differ by country");
                check(Country.get(Country.AUSTRALIA).leftHand && Country.get(Country.JAPAN).leftHand && !Country.get(Country.USA).leftHand,
                        "Australia and Japan drive on the left");
                CityConfig old = new CityConfig();
                check(old.applyCode("12-44") && old.country() == Country.USA, "old codes are American cities");
            }
        });
        test("a highway out in the country, with highway patrol and sheriffs", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.v[CityConfig.OPT_PRESET] = 6;
                c.v[CityConfig.OPT_SIZE] = 1;
                c.seed = 7;
                World w = new World(c);
                w.populate(c);
                City city = w.city;
                check(city.hwyAxis >= 0 && city.hwyName != null, "a small town has a highway past it");
                check(!city.hwyExits.isEmpty(), "a road from town joins it");
                int hp = 0, sheriff = 0;
                for (Fleet.Vehicle v : w.fleet.vehicles) {
                    if (v.patrol && v.agency == 1) hp++;
                    if (v.patrol && v.agency == 2) sheriff++;
                }
                check(hp >= 1 && sheriff >= 1, "highway patrol and sheriff's cars: " + hp + ", " + sheriff);
                // Traffic keeps to its own carriageway.
                int wrong = 0, samples = 0;
                java.util.HashMap<Fleet.Vehicle, float[]> last = new java.util.HashMap<Fleet.Vehicle, float[]>();
                for (int f = 0; f < 30 * 60; f++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    if (f % 15 != 0) continue;
                    for (Fleet.Vehicle v : w.fleet.vehicles) {
                        float[] p = last.get(v);
                        last.put(v, new float[]{v.x, v.y});
                        if (p == null || v.parked || !city.onHighway(v.x, v.y) || city.junctionIdAt(v.x, v.y) >= 0) continue;
                        float dx = v.x - p[0], dy = v.y - p[1];
                        int ow = city.oneWay[(int) (v.y / City.T) * city.w + (int) (v.x / City.T)];
                        float along = ow == 1 ? dx : ow == 2 ? -dx : ow == 3 ? dy : -dy, across = ow <= 2 ? dy : dx;
                        // (Crossing over to a side road isn't driving along it.)
                        if (Math.abs(along) < 3 || Math.abs(across) > Math.abs(along)) continue;
                        samples++;
                        if (along < 0) wrong++;
                    }
                }
                check(samples > 50 && wrong == 0, "nobody drives the wrong way: " + wrong + " of " + samples);
                // Outside the USA there are no sheriffs.
                CityConfig f = new CityConfig();
                f.v[CityConfig.OPT_ZOMBIES] = 0;
                f.v[CityConfig.OPT_PRESET] = 6;
                f.v[CityConfig.OPT_SIZE] = 1;
                f.v[CityConfig.OPT_COUNTRY] = Country.FRANCE;
                f.seed = 7;
                World fr = new World(f);
                fr.populate(f);
                for (Fleet.Vehicle v : fr.fleet.vehicles) check(v.agency != 2, "no sheriffs in France");
            }
        });
        test("fire engines need a crew to drive them; police patrol in cars", new Check() {
            public void run() {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 21;
                World w = new World(c);
                w.populate(c);
                int cars = 0, aboard = 0;
                Fleet.Vehicle engine = null;
                for (Fleet.Vehicle v : w.fleet.vehicles) {
                    if (v.patrol) {
                        cars++;
                        aboard += v.crew.size();
                    }
                    if (v.type == Fleet.FIRE_ENGINE) engine = v;
                }
                check(cars >= 2 && aboard == cars * 2, "patrol cars with two officers each: " + cars);
                check(aboard * 2 > w.counts[Entity.COP], "most police are in cars");
                check(engine != null && engine.crew.isEmpty() && !engine.lightsOn(), "the engine waits at its station, empty");
                City.Facility station = engine.station;
                City.Building b = null;
                float bd = Float.MAX_VALUE;
                for (City.Building o : w.city.buildings) {
                    float d = (float) Math.hypot(o.doorX - station.x, o.doorY - station.y);
                    if (o.kind == City.HOUSE && o.doorX > 0 && d < bd) {
                        bd = d;
                        b = o;
                    }
                }
                w.igniteBuilding(b);
                boolean drovenByCrew = true, drove = false, crewOut = false;
                for (int i = 0; i < 30 * 150; i++) {
                    w.update(1 / 30f);
                    w.evCount = 0;
                    if (engine.speed > 5) {
                        drove = true;
                        if (engine.crew.isEmpty()) drovenByCrew = false;
                    }
                    for (Entity e : w.entities) if (!e.dead && e.rig == engine && engine.speed < 1 && engine.lightsOn()) crewOut = true;
                }
                check(drove && drovenByCrew, "it only moves with firefighters aboard");
                check(crewOut, "the crew gets out at the fire");
            }
        });
        test("each country's traffic: kei cars in Japan, Beetles in Mexico, utes in Australia", new Check() {
            public void run() {
                int[][] want = {{Country.JAPAN, Fleet.M_KEI}, {Country.MEXICO, Fleet.M_BEETLE}, {Country.AUSTRALIA, Fleet.M_PICKUP}};
                for (int[] wv : want) {
                    CityConfig c = new CityConfig();
                    c.v[CityConfig.OPT_ZOMBIES] = 0;
                    c.v[CityConfig.OPT_COUNTRY] = wv[0];
                    c.seed = 5;
                    World w = new World(c);
                    w.populate(c);
                    w.fleet.spawnTraffic(60);
                    int found = 0, buses = 0, cars = 0;
                    for (Fleet.Vehicle v : w.fleet.vehicles) {
                        if (v.type != Fleet.CAR) continue;
                        cars++;
                        if (v.model == wv[1]) found++;
                        if (v.model == Fleet.M_BUS) buses++;
                    }
                    check(found > 0, Country.NAMES[wv[0]] + " has its own cars");
                    check(buses <= Math.max(6, cars / 10), "only a few buses");
                    for (int s = 0; s < 30 * 20; s++) w.update(1 / 30f);
                }
                check(Fleet.MODEL_HL[Fleet.M_BUS] > Fleet.MODEL_HL[Fleet.M_SEDAN] && Fleet.MODEL_HL[Fleet.M_KEI] < Fleet.MODEL_HL[Fleet.M_SEDAN],
                        "buses are long, kei cars are small");
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
        test("everyone has a life: some at home, some at work, some out running, in the park or chatting", new Check() {
            public void run() throws Exception {
                CityConfig c = new CityConfig();
                c.v[CityConfig.OPT_ZOMBIES] = 0;
                c.seed = 7;
                World w = new World(c);
                w.populate(c);
                int home = 0, work = 0;
                for (City.Building b : w.city.buildings) {
                    int[] in = w.insideCounts(b);
                    home += in[0];
                    work += in[1];
                }
                check(home > 50 && work > 20, "the day starts with people at home and at work");
                java.util.HashSet<Integer> jobs = new java.util.HashSet<Integer>();
                for (Entity e : w.entities) if (!e.dead && e.type == Entity.CIVILIAN) jobs.add(e.job);
                for (City.Building b : w.city.buildings) for (Entity e : b.visitors) jobs.add(e.job);
                check(jobs.size() >= 7, "many different lives");
                boolean ran = false, park = false, chat = false, commute = false;
                for (int s = 0; s < 30 * 150; s++) {
                    w.update(1 / 30f);
                    if (s % 30 != 0) continue;
                    for (Entity e : w.entities) {
                        if (e.dead || e.type != Entity.CIVILIAN) continue;
                        String a = w.lifeActivity(e);
                        ran |= a.startsWith("Out for a run");
                        park |= a.startsWith("Spending time");
                        chat |= e.chat > 0;
                        commute |= a.startsWith("On the way to work");
                    }
                }
                check(ran && park && commute, "people run, go to the park and go to work");
                check(chat, "neighbours stop to chat");
                // Lives survive a save.
                java.io.File f = java.io.File.createTempFile("zcs", ".dat");
                SaveGame.save(w, f);
                World l = SaveGame.load(f);
                int withJobs = 0;
                for (Entity e : l.entities) if (e.type == Entity.CIVILIAN && e.job != Entity.J_NONE) withJobs++;
                check(withJobs > 100, "and are saved");
                f.delete();
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
