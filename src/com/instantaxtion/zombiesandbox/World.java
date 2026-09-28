package com.instantaxtion.zombiesandbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/** The simulation: people, zombies, gunfire, explosions and all the visual effects they leave behind. */
final class World {
    private static final float TAU = (float) (Math.PI * 2);

    private static final int[] SHIRTS = {
            0xFFD9534F, 0xFF5BC0DE, 0xFFF0AD4E, 0xFF5CB85C, 0xFFEEEEEE, 0xFF9B59B6, 0xFFE67E22, 0xFF34495E,
            0xFFF1C40F, 0xFF1ABC9C, 0xFFE84393, 0xFF795548, 0xFF222222, 0xFF8BC34A
    };
    private static final int[] HAIR = {0xFF2B1B10, 0xFF4A2E1A, 0xFF111111, 0xFFC8A45A, 0xFF8B4A2B, 0xFF777777};
    private static final int[] DOGS = {0xFF8B5A2B, 0xFF2B2320, 0xFFD9C49A, 0xFFB07A3E, 0xFF6E6A66, 0xFFEDE6DA};
    private static final int[] SKINS = {0xFFF1C27D, 0xFFE0AC69, 0xFFC68642, 0xFF8D5524, 0xFFFFDBAC};

    static final class Corpse {
        float x, y, angle, radius, rise, age;
        int body, head, riseType, origin;
        boolean zombie;
    }

    static final class Grenade {
        float sx, sy, tx, ty, t, dur;
    }

    static final class Explosion {
        float x, y, r, life, max;
    }

    /** A burning car or gas pump. */
    static final class Fire {
        float x, y, life;
        /** The fire engine on its way to (or fighting) this fire. */
        Fleet.Vehicle engine;
    }

    /** A pigeon: pecks around on the ground and flies off when startled. */
    static final class Bird {
        float x, y, tx, ty, flap, timer;
        boolean flying;
    }

    /** A gun dropped by a fallen cop or soldier. */
    static final class Pickup {
        float x, y, age;
        int rounds;
        boolean claimed;
        /** A supply crate: guns for several people, and ammo for anyone passing. Drops by parachute. */
        int uses;
        float drop;
    }

    /** A barricade on a tile: {x, y, hp, tile index, the tile it replaced}. */
    final ArrayList<float[]> barriers = new ArrayList<float[]>();

    final City city;
    final Dispatch dispatch;
    final Random rnd = new Random();
    final ArrayList<Entity> entities = new ArrayList<Entity>();
    final ArrayList<Corpse> corpses = new ArrayList<Corpse>();
    final ArrayList<Grenade> grenades = new ArrayList<Grenade>();
    final ArrayList<Explosion> explosions = new ArrayList<Explosion>();
    final ArrayList<Pickup> pickups = new ArrayList<Pickup>();
    final ArrayList<Fire> fires = new ArrayList<Fire>();
    final ArrayList<Bird> birds = new ArrayList<Bird>();
    /** Explosions waiting to go off (gas pumps catching): {x, y, radius, damage, delay}. */
    private final ArrayList<float[]> pendingBlasts = new ArrayList<float[]>();
    /** Tiles holding a burnt-out car or gas pump (saved with the game). */
    final boolean[] burned;
    final Fleet fleet;
    /** Rings drawn where a screamer screamed: {x, y, age}. */
    final ArrayList<float[]> screams = new ArrayList<float[]>();

    // History for the stats screen: humans and zombies sampled every statStep seconds.
    static final int HISTORY = 240;
    final int[] histHumans = new int[HISTORY], histZombies = new int[HISTORY], histArmed = new int[HISTORY];
    /** People who killed zombies and have died since: {name seed, type, role, kills}. */
    final ArrayList<int[]> fallenHeroes = new ArrayList<int[]>();
    int histCount;
    float statStep = 2f;
    private float statTimer;
    int shotsFired, cured, peakZombies, hiding;
    /** Recruitment: the most cops and soldiers there have been, how many civilians joined up or refused. */
    int peakCops, peakSoldiers, recruits, refused;
    /** How far the infection has evolved (0-5), how long the outbreak has run, and the recovery afterwards. */
    int mutation;
    /** Counters for records and achievements. */
    int firesOut, tanksDeployed, collapsedCount, carsWrecked, biggestHorde;
    float outbreakTime, calmTime;
    boolean recovering;
    private float lifeTimer;
    private float recruitTimer, lastRecruitSay = -100;
    private boolean policeDrive, armyDrive;
    private final ArrayList<Entity> newRecruits = new ArrayList<Entity>();

    // Spatial hash (counting sort into cells).
    private static final int CELL = 32;
    private final int gw, gh;
    private final int[] cellStart, cellCount, cellFill;
    private Entity[] sorted = new Entity[512];

    // Particles.
    static final int MAXP = 2500;
    final float[] px = new float[MAXP], py = new float[MAXP], pvx = new float[MAXP], pvy = new float[MAXP];
    final float[] plife = new float[MAXP], pmax = new float[MAXP], psize = new float[MAXP];
    final int[] pcol = new int[MAXP];
    final byte[] ptype = new byte[MAXP];
    static final byte P_DOT = 0, P_SMOKE = 1, P_FLASH = 2, P_FIRE = 3, P_CASING = 4, P_DEBRIS = 5;
    private int pnext;

    // Bullet tracers.
    static final int MAXT = 300;
    final float[] tx0 = new float[MAXT], ty0 = new float[MAXT], tx1 = new float[MAXT], ty1 = new float[MAXT];
    final float[] tlife = new float[MAXT];
    private int tnext;

    // Ground decals (blood, scorch marks).
    static final int MAXD = 700;
    final float[] dx = new float[MAXD], dy = new float[MAXD], dr = new float[MAXD];
    final int[] dcol = new int[MAXD];
    /** Decal shape: a round splat, a skid streak (dang = direction, dr = length) or a puddle. */
    static final byte D_SPLAT = 0, D_SKID = 1, D_PUDDLE = 2, D_ACID = 3;
    final byte[] dkind = new byte[MAXD];
    final float[] dang = new float[MAXD];
    int dcount;
    private int dnext;

    /** Sound events for GameView to play: {@link Sfx} id plus position. */
    static final int MAX_EVENTS = 128;
    final int[] evType = new int[MAX_EVENTS];
    final float[] evX = new float[MAX_EVENTS], evY = new float[MAX_EVENTS];
    int evCount;

    int maxEntities = 1600;
    boolean gore = true;

    float time, shake;
    private float fieldTimer, engineTimer;
    final int[] counts = new int[Entity.TYPE_COUNT];
    int humans, zombies, turned, zombiesKilled, civiliansLost;
    String message;
    float messageTime;
    private boolean outbreak;

    World(CityConfig cfg) {
        city = new City(cfg);
        dispatch = new Dispatch(this, cfg.reinforcements());
        fleet = new Fleet(this);
        burned = new boolean[city.w * city.h];
        gw = (int) Math.ceil(city.worldW() / CELL);
        gh = (int) Math.ceil(city.worldH() / CELL);
        cellStart = new int[gw * gh + 1];
        cellCount = new int[gw * gh];
        cellFill = new int[gw * gh];
    }

    void populate(CityConfig cfg) {
        spawnRandom(Entity.CIVILIAN, cfg.civilians());
        // Families and friends out together, and people walking their dogs.
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.type != Entity.CIVILIAN || e.leader != null || entities.size() >= maxEntities) continue;
            float roll = rnd.nextFloat();
            if (roll < 0.22f) {
                int extra = 1 + rnd.nextInt(3);
                for (int k = 0; k < extra; k++) follower(e, Entity.CIVILIAN);
            }
            if (roll > 0.9f) follower(e, Entity.DOG);
        }
        // A few strays.
        spawnRandom(Entity.DOG, cfg.civilians() / 60);
        // A few civilians own a gun.
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.type == Entity.CIVILIAN && rnd.nextFloat() < 0.07f) armCivilian(e, 12 + rnd.nextInt(3) * 6);
        }
        City.Facility hospital = city.nearestFacility(City.FACILITY_HOSPITAL, 0, 0);
        if (hospital != null)
            for (int i = 0; i < 3; i++) spawn(Entity.MEDIC, hospital.x + rnd.nextFloat() * 40 - 20, hospital.y + rnd.nextFloat() * 40 - 20);
        // Half the cops start at their precinct, the rest on patrol; soldiers start on base.
        City.Facility base = city.nearestFacility(City.FACILITY_BASE, 0, 0);
        int stations = 0;
        for (City.Facility f : city.facilities) if (f.kind == City.FACILITY_POLICE) stations++;
        int copsAtStations = stations > 0 ? cfg.cops() / 2 : 0;
        int k = 0;
        for (City.Facility f : city.facilities) {
            if (f.kind != City.FACILITY_POLICE) continue;
            int n = copsAtStations / stations + (k++ < copsAtStations % stations ? 1 : 0);
            for (int i = 0; i < n; i++) spawn(Entity.COP, f.x + rnd.nextFloat() * 40 - 20, f.y + rnd.nextFloat() * 40 - 20);
        }
        spawnRandom(Entity.COP, cfg.cops() - copsAtStations);
        int firstSoldier = entities.size();
        if (base != null) {
            for (int i = 0; i < cfg.soldiers(); i++)
                spawn(Entity.SOLDIER, base.x + rnd.nextFloat() * base.r - base.r / 2, base.y + rnd.nextFloat() * base.r - base.r / 2);
        } else {
            spawnRandom(Entity.SOLDIER, cfg.soldiers());
        }
        // Every fourth soldier carries the squad's machine gun, big garrisons have snipers, and a
        // commander runs the show.
        int snipers = cfg.soldiers() >= 10 ? 2 : 0;
        for (int i = firstSoldier, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.type != Entity.SOLDIER) continue;
            if (e.member == 4) applyRole(e, Entity.ROLE_GUNNER);
            else if (e.member == 3 && snipers > 0) {
                applyRole(e, Entity.ROLE_SNIPER);
                snipers--;
            }
        }
        if (cfg.soldiers() >= 5) {
            float[] p = base != null ? new float[]{base.x, base.y} : city.randomWalkable(rnd);
            spawnSoldier(Entity.ROLE_COMMANDER, p[0], p[1]);
        }
        // Guards on the precinct doors and the base gates.
        for (City.Facility f : city.facilities) {
            int type = f.kind == City.FACILITY_POLICE ? Entity.COP : f.kind == City.FACILITY_BASE ? Entity.SOLDIER : -1;
            if (type < 0) continue;
            for (float[] post : f.posts) {
                Entity best = null;
                float bd = Float.MAX_VALUE;
                for (int i = 0, n = entities.size(); i < n; i++) {
                    Entity e = entities.get(i);
                    if (e.type != type || e.task != Dispatch.T_NONE) continue;
                    float d = (e.x - post[0]) * (e.x - post[0]) + (e.y - post[1]) * (e.y - post[1]);
                    if (d < bd && d < 400 * 400) {
                        bd = d;
                        best = e;
                    }
                }
                if (best == null) break;
                best.task = Dispatch.T_POST;
                best.postX = post[0];
                best.postY = post[1];
            }
        }
        spawnBirds();
        fleet.trafficTarget = new int[]{0, 8, 16}[cfg.traffic()] * city.w / 96;
        fleet.spawnTraffic(fleet.trafficTarget);
        // Zombies start in a few small outbreaks rather than spread evenly.
        int left = cfg.zombies();
        while (left > 0) {
            // Some outbreaks start indoors: people sheltering inside, one of them bitten.
            if (left >= 3 && rnd.nextFloat() < 0.3f) {
                City.Building b = city.buildings.get(rnd.nextInt(city.buildings.size()));
                if (b.capacity > 0 && b.occupants.isEmpty()) {
                    indoorOutbreak(b, 2 + rnd.nextInt(3));
                    left -= 2;
                    continue;
                }
            }
            float[] p = city.randomWalkable(rnd);
            int group = Math.min(left, 1 + rnd.nextInt(6));
            for (int i = 0; i < group; i++)
                spawn(Entity.ZOMBIE, p[0] + rnd.nextFloat() * 30 - 15, p[1] + rnd.nextFloat() * 30 - 15);
            left -= group;
        }
        recount();
    }

    private void follower(Entity leader, int type) {
        Entity f = spawn(type, leader.x + rnd.nextFloat() * 10 - 5, leader.y + rnd.nextFloat() * 10 - 5);
        if (f != null) f.leader = leader;
    }

    /** Flocks of pigeons in parks and plazas. */
    void spawnBirds() {
        int flocks = Math.min(10, city.openAreas.size() + 3);
        for (int f = 0; f < flocks; f++) {
            float[] c;
            if (f < city.openAreas.size()) c = city.findWalkable(city.openAreas.get(f)[0], city.openAreas.get(f)[1]);
            else c = city.randomWalkable(rnd);
            if (c == null) continue;
            int n = 4 + rnd.nextInt(5);
            for (int i = 0; i < n; i++) {
                Bird b = new Bird();
                b.x = c[0] + rnd.nextFloat() * 30 - 15;
                b.y = c[1] + rnd.nextFloat() * 30 - 15;
                b.flap = rnd.nextFloat() * 6;
                birds.add(b);
            }
        }
    }

    /** Startles pigeons near a point into the air. */
    private void scareBirds(float x, float y, float radius) {
        for (int i = 0, n = birds.size(); i < n; i++) {
            Bird b = birds.get(i);
            if (b.flying) continue;
            float ddx = b.x - x, ddy = b.y - y;
            if (ddx * ddx + ddy * ddy > radius * radius) continue;
            b.flying = true;
            float[] land = city.randomWalkable(rnd);
            float a = (float) Math.atan2(ddy, ddx) + (rnd.nextFloat() - 0.5f);
            // Fly away from the scare, landing somewhere open a few hundred units off.
            b.tx = Math.max(0, Math.min(city.worldW(), b.x + (float) Math.cos(a) * (200 + rnd.nextFloat() * 250)));
            b.ty = Math.max(0, Math.min(city.worldH(), b.y + (float) Math.sin(a) * (200 + rnd.nextFloat() * 250)));
            float[] p = city.findWalkable(b.tx, b.ty);
            if (p == null) p = land;
            b.tx = p[0] + rnd.nextFloat() * 20 - 10;
            b.ty = p[1] + rnd.nextFloat() * 20 - 10;
        }
    }

    private void updateBirds(float dt) {
        for (int i = 0, n = birds.size(); i < n; i++) {
            Bird b = birds.get(i);
            b.flap += dt * (b.flying ? 18 : 2);
            if (b.flying) {
                float ddx = b.tx - b.x, ddy = b.ty - b.y;
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                if (d < 3) {
                    b.flying = false;
                } else {
                    float sp = Math.min(d, 90 * dt);
                    b.x += ddx / d * sp;
                    b.y += ddy / d * sp;
                }
                continue;
            }
            // Peck about, and take off if anyone comes close.
            b.timer -= dt;
            if (b.timer <= 0) {
                b.timer = 0.3f + rnd.nextFloat() * 0.6f;
                if (rnd.nextFloat() < 0.3f) {
                    float nx = b.x + rnd.nextFloat() * 6 - 3, ny = b.y + rnd.nextFloat() * 6 - 3;
                    if (!city.solidAt(nx, ny)) {
                        b.x = nx;
                        b.y = ny;
                    }
                }
                int cx = Math.max(0, Math.min(gw - 1, (int) (b.x / CELL))), cy = Math.max(0, Math.min(gh - 1, (int) (b.y / CELL)));
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    float ddx = o.x - b.x, ddy = o.y - b.y;
                    if (ddx * ddx + ddy * ddy < 20 * 20) {
                        scareBirds(b.x, b.y, 40);
                        break;
                    }
                }
            }
        }
    }

    /** Burning wrecks: flames and smoke, and they hurt anyone standing in them. */
    private void updateFires(float dt) {
        engineTimer -= dt;
        if (engineTimer <= 0) {
            engineTimer = 2;
            dispatchEngines();
        }
        for (int i = fires.size() - 1; i >= 0; i--) {
            Fire f = fires.get(i);
            f.life -= dt;
            if (f.life <= 0) {
                fires.remove(i);
                continue;
            }
            float strength = Math.min(1, f.life / 10);
            if (rnd.nextFloat() < dt * 25 * strength)
                particle(f.x + rnd.nextFloat() * 10 - 5, f.y + rnd.nextFloat() * 8 - 4, rnd.nextFloat() * 10 - 5,
                        -8 - rnd.nextFloat() * 10, 0.4f + rnd.nextFloat() * 0.3f, 1.5f + rnd.nextFloat() * 2,
                        rnd.nextBoolean() ? 0xFFFFB030 : 0xFFFF6A1A, P_FIRE);
            if (rnd.nextFloat() < dt * 6)
                particle(f.x, f.y - 4, 6 + rnd.nextFloat() * 6, -10 - rnd.nextFloat() * 8, 2.5f + rnd.nextFloat() * 2,
                        3 + rnd.nextFloat() * 3, 0xFF2E2E2E, P_SMOKE);
            for (int k = 0, n = entities.size(); k < n; k++) {
                Entity o = entities.get(k);
                float ddx = o.x - f.x, ddy = o.y - f.y;
                if (ddx * ddx + ddy * ddy < 10 * 10) {
                    o.hp -= 12 * dt;
                    o.hurt = Math.max(o.hurt, 0.3f);
                    if (!o.isZombie()) {
                        o.fleeTimer = 2;
                        o.threatX = f.x;
                        o.threatY = f.y;
                    }
                }
            }
        }
        for (int i = pendingBlasts.size() - 1; i >= 0; i--) {
            float[] b = pendingBlasts.get(i);
            b[4] -= dt;
            if (b[4] <= 0) {
                pendingBlasts.remove(i);
                explode(b[0], b[1], b[2], b[3]);
            }
        }
    }

    /** Marks a car or pump tile as burnt and scorches it into the map. */
    void burnTile(int i) {
        burned[i] = true;
        city.charTile(i % city.w, i / city.w);
    }

    /** An explosion that goes off after a delay (a car's fuel tank, a gas pump). */
    void blastLater(float x, float y, float radius, float damage, float delay) {
        pendingBlasts.add(new float[]{x, y, radius, damage, delay});
    }

    /** The nearest fire no fire engine is looking after. */
    Fire nearestFire(float x, float y, float radius) {
        Fire best = null;
        float bd = radius * radius;
        for (int i = 0, n = fires.size(); i < n; i++) {
            Fire f = fires.get(i);
            if (f.engine != null || f.life < 4) continue;
            float d = (f.x - x) * (f.x - x) + (f.y - y) * (f.y - y);
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        return best;
    }

    /** Water from a fire hose: the fire dies down fast and anything about to blow up is made safe. */
    void douse(Fire f, float dt) {
        f.life -= 9 * dt;
        if (f.life <= 0 && f.life > -9 * dt) firesOut++;
        if (rnd.nextFloat() < dt * 1.5f)
            decal(f.x + rnd.nextFloat() * 24 - 12, f.y + rnd.nextFloat() * 24 - 12, 4 + rnd.nextFloat() * 6, 0x3060A0D0, D_PUDDLE, 0);
        if (rnd.nextFloat() < dt * 5)
            particle(f.x, f.y - 3, rnd.nextFloat() * 8 - 4, -12, 1.5f, 3, 0xFFE8E8E8, P_SMOKE);
        for (int i = pendingBlasts.size() - 1; i >= 0; i--) {
            float[] b = pendingBlasts.get(i);
            if ((b[0] - f.x) * (b[0] - f.x) + (b[1] - f.y) * (b[1] - f.y) < 25 * 25 && b[2] < 60) pendingBlasts.remove(i);
        }
    }

    /** Fire stations send an engine to each fire that is burning for real. */
    private void dispatchEngines() {
        for (int i = 0, n = fires.size(); i < n; i++) {
            Fire f = fires.get(i);
            if (f.engine != null || f.life < 12) continue;
            City.Facility best = null;
            float bd = Float.MAX_VALUE;
            for (City.Facility st : city.facilities) {
                if (st.kind != City.FACILITY_FIRE || fleet.enginesOut(st) >= 2) continue;
                // An overrun station can't send anyone.
                if (countZombiesNear(st.x, st.y, 160) > 0) continue;
                float d = (st.x - f.x) * (st.x - f.x) + (st.y - f.y) * (st.y - f.y);
                if (d < bd) {
                    bd = d;
                    best = st;
                }
            }
            if (best == null) return;
            // One engine covers every fire close to this one.
            Fleet.Vehicle v = fleet.sendFireEngine(best, f);
            if (v == null) continue;
            f.engine = v;
            for (int k = 0; k < n; k++) {
                Fire o = fires.get(k);
                if (o.engine == null && (o.x - f.x) * (o.x - f.x) + (o.y - f.y) * (o.y - f.y) < 90 * 90) o.engine = v;
            }
        }
    }

    void ignite(float x, float y, float life) {
        Fire f = new Fire();
        f.x = x;
        f.y = y;
        f.life = life;
        fires.add(f);
        if (fires.size() > 40) fires.remove(0);
    }

    /** Anyone standing just ahead of a car (so drivers can brake). */
    boolean personAhead(float x, float y, float angle) {
        float ax = x + (float) Math.cos(angle) * 16, ay = y + (float) Math.sin(angle) * 16;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o.isZombie()) continue;
            float ddx = o.x - ax, ddy = o.y - ay;
            if (ddx * ddx + ddy * ddy < 11 * 11) return true;
        }
        return false;
    }

    void armCivilian(Entity e, int rounds) {
        e.hasGun = true;
        e.magSize = 6;
        e.ammo = Math.min(6, rounds);
        e.reserve = rounds - e.ammo;
    }

    int zombieCount() {
        return counts[Entity.ZOMBIE] + counts[Entity.RUNNER] + counts[Entity.BRUTE] + counts[Entity.CRAWLER]
                + counts[Entity.SCREAMER] + counts[Entity.ZOMBIE_DOG];
    }

    int humanCount() {
        return counts[Entity.CIVILIAN] + counts[Entity.COP] + counts[Entity.SOLDIER] + counts[Entity.MEDIC];
    }

    /** People sitting in cars right now. */
    int riding;

    private void spawnRandom(int type, int n) {
        for (int i = 0; i < n; i++) {
            float[] p = city.randomWalkable(rnd);
            spawn(type, p[0], p[1]);
        }
    }

    void emit(int sound, float x, float y) {
        if (evCount >= MAX_EVENTS) return;
        evType[evCount] = sound;
        evX[evCount] = x;
        evY[evCount] = y;
        evCount++;
    }

    // ------------------------------------------------------------------ creation

    Entity spawn(int type, float x, float y) {
        if (entities.size() >= maxEntities) return null;
        float[] p = city.findWalkable(x, y);
        if (p == null) return null;
        Entity e = make(type, p[0], p[1], -1, 0);
        entities.add(e);
        // Counted straight away, so the numbers go up even while the game is paused.
        counts[type]++;
        if (e.isZombie() && patientZero == null) markPatientZero(e);
        fieldTimer = 0;
        return e;
    }

    private Entity make(int type, float x, float y, int origin, int originBody) {
        Entity e = new Entity();
        e.type = type;
        e.x = x;
        e.y = y;
        e.angle = rnd.nextFloat() * TAU;
        e.wanderAngle = e.angle;
        e.phase = rnd.nextFloat() * 10;
        e.origin = origin;
        e.nameSeed = rnd.nextInt();
        e.skin = SKINS[rnd.nextInt(SKINS.length)];
        if (type == Entity.COP) e.callsign = 11 + dispatch.copCount++;
        if (type == Entity.SOLDIER) {
            e.squad = dispatch.soldierCount / 4;
            e.member = dispatch.soldierCount % 4 + 1;
            dispatch.soldierCount++;
        }
        switch (type) {
            case Entity.CIVILIAN:
                e.hp = 30;
                e.radius = 3.6f;
                e.speed = 15 + rnd.nextFloat() * 6;
                e.runSpeed = 36 + rnd.nextFloat() * 10;
                e.body = SHIRTS[rnd.nextInt(SHIRTS.length)];
                e.head = HAIR[rnd.nextInt(HAIR.length)];
                break;
            case Entity.COP:
                e.hp = 60;
                e.radius = 3.8f;
                e.speed = 22;
                e.runSpeed = 40;
                e.magSize = 12;
                e.reserve = 48;
                e.body = 0xFF23408E;
                e.head = 0xFF141C38;
                break;
            case Entity.SOLDIER:
                e.hp = 100;
                e.radius = 4.0f;
                e.speed = 24;
                e.runSpeed = 40;
                e.magSize = 30;
                e.reserve = 150;
                e.grenades = 3;
                e.body = 0xFF55623A;
                e.head = 0xFF3C4628;
                e.mass = 1.3f;
                break;
            case Entity.DOG:
                e.hp = 25;
                e.radius = 2.8f;
                e.speed = 18 + rnd.nextFloat() * 6;
                e.runSpeed = 55 + rnd.nextFloat() * 8;
                e.body = DOGS[rnd.nextInt(DOGS.length)];
                e.head = City.darken(e.body, 0.8f);
                e.skin = e.body;
                break;
            case Entity.RAIDER:
                e.hp = 55;
                e.radius = 3.7f;
                e.speed = 18 + rnd.nextFloat() * 4;
                e.runSpeed = 40;
                e.body = rnd.nextBoolean() ? 0xFF2E2622 : 0xFF3A3A30;
                e.head = rnd.nextBoolean() ? 0xFF9E2A22 : 0xFF1A1A1A;
                e.hasGun = true;
                e.magSize = 12;
                e.reserve = 36;
                break;
            case Entity.SPITTER:
                e.hp = 45;
                e.radius = 3.6f;
                e.speed = 16;
                e.runSpeed = 26;
                e.skin = 0xFFA8BE52;
                break;
            case Entity.BLOATER:
                e.hp = 90;
                e.radius = 5.4f;
                e.speed = 10;
                e.runSpeed = 15;
                e.skin = 0xFF8E9A5A;
                e.mass = 2.2f;
                break;
            case Entity.ZOMBIE_DOG:
                e.hp = 22;
                e.radius = 2.8f;
                e.speed = 28;
                e.runSpeed = 60 + rnd.nextFloat() * 6;
                e.skin = 0xFF7A8058;
                break;
            case Entity.MEDIC:
                e.hp = 40;
                e.radius = 3.7f;
                e.speed = 20;
                e.runSpeed = 40;
                e.body = 0xFFF2F2F2;
                e.head = HAIR[rnd.nextInt(HAIR.length)];
                break;
            case Entity.CRAWLER:
                e.hp = 35;
                e.radius = 3.0f;
                e.speed = 7 + rnd.nextFloat() * 3;
                e.runSpeed = 11;
                e.skin = 0xFF73905A;
                break;
            case Entity.SCREAMER:
                e.hp = 30;
                e.radius = 3.5f;
                e.speed = 20;
                e.runSpeed = 30;
                e.skin = 0xFFC8D0B4;
                break;
            case Entity.ZOMBIE:
                e.hp = 60;
                e.radius = 3.8f;
                e.speed = 15 + rnd.nextFloat() * 6;
                e.runSpeed = 27 + rnd.nextFloat() * 6;
                e.skin = rnd.nextBoolean() ? 0xFF7C9A5E : 0xFF8FA173;
                break;
            case Entity.RUNNER:
                e.hp = 28;
                e.radius = 3.5f;
                e.speed = 30 + rnd.nextFloat() * 6;
                e.runSpeed = 50 + rnd.nextFloat() * 6;
                e.skin = 0xFF9DAA70;
                break;
            default:
                e.hp = 240;
                e.radius = 6.5f;
                e.speed = 12;
                e.runSpeed = 19;
                e.skin = 0xFF6F8D55;
                e.mass = 4f;
                break;
        }
        if (e.isZombie()) {
            int base = originBody != 0 ? originBody
                    : type == Entity.BRUTE ? 0xFF4D3F4F : type == Entity.ZOMBIE_DOG ? DOGS[rnd.nextInt(DOGS.length)]
                    : SHIRTS[rnd.nextInt(SHIRTS.length)];
            e.body = mix(base, 0xFF2F3A26, type == Entity.BRUTE ? 0.2f : 0.45f);
            e.head = type == Entity.ZOMBIE_DOG ? City.darken(e.body, 0.8f) : e.skin;
            // The longer the outbreak goes on, the tougher new zombies get.
            if (mutation > 0) {
                e.hp *= 1 + 0.12f * mutation;
                e.speed *= 1 + 0.05f * mutation;
                e.runSpeed *= 1 + 0.05f * mutation;
            }
        }
        e.maxHp = e.hp;
        e.ammo = e.magSize;
        e.flank = (rnd.nextFloat() - 0.5f) * 1.8f;
        return e;
    }

    /** Spawns a police officer: a regular cop, a riot officer with a shield, or a K9 handler with a dog. */
    Entity spawnCop(int role, float x, float y) {
        Entity e = spawn(Entity.COP, x, y);
        if (e == null) return null;
        if (role == Entity.ROLE_RIOT) {
            e.role = Entity.ROLE_RIOT;
            e.hp = e.maxHp = 90;
            e.speed = 18;
            e.runSpeed = 32;
            e.mass = 1.4f;
        } else if (role == Entity.ROLE_K9) {
            e.role = Entity.ROLE_K9;
            Entity dog = spawn(Entity.DOG, x + 6, y + 4);
            if (dog != null) {
                dog.leader = e;
                dog.body = 0xFF8B5A2B;
                dog.head = 0xFF2B2320;
                dog.hp = dog.maxHp = 45;
                dog.role = Entity.ROLE_K9;
            }
        }
        return e;
    }

    /** Spawns a soldier with a particular job. */
    Entity spawnSoldier(int role, float x, float y) {
        Entity e = spawn(Entity.SOLDIER, x, y);
        if (e == null) return null;
        applyRole(e, role);
        if (role == Entity.ROLE_COMMANDER) {
            // The commander isn't part of a squad.
            dispatch.soldierCount--;
            e.squad = -1;
        }
        return e;
    }

    /** Commanders carry a pistol and a radio, snipers a scoped rifle, gunners a belt-fed machine gun. */
    void applyRole(Entity e, int role) {
        e.role = role;
        switch (role) {
            case Entity.ROLE_COMMANDER:
                e.hp = e.maxHp = 130;
                e.magSize = 15;
                e.reserve = 60;
                e.grenades = 0;
                e.body = 0xFF4A5634;
                e.head = 0xFF8E1F1F;
                break;
            case Entity.ROLE_SNIPER:
                e.hp = e.maxHp = 80;
                e.magSize = 5;
                e.reserve = 45;
                e.grenades = 0;
                e.body = 0xFF434B2C;
                e.head = 0xFF363D22;
                break;
            case Entity.ROLE_GUNNER:
                e.hp = e.maxHp = 130;
                e.magSize = 100;
                e.reserve = 200;
                e.grenades = 0;
                e.speed = 19;
                e.runSpeed = 31;
                e.mass = 1.6f;
                e.radius = 4.3f;
                break;
        }
        e.ammo = e.magSize;
    }

    /** Full spare ammo for a unit's weapon. */
    static int fullReserve(Entity e) {
        if (e.type != Entity.SOLDIER) return 48;
        switch (e.role) {
            case Entity.ROLE_COMMANDER: return 60;
            case Entity.ROLE_SNIPER: return 45;
            case Entity.ROLE_GUNNER: return 200;
            default: return 150;
        }
    }

    /** Makes a person or zombie without adding it to the city (used when loading a save). */
    Entity create(int type, float x, float y) {
        return make(type, x, y, -1, 0);
    }

    /** The best zombie killers of this game, living or fallen: {name seed, type, role, kills, alive}. */
    ArrayList<int[]> heroes(int n) {
        ArrayList<int[]> all = new ArrayList<int[]>();
        for (int i = 0, c = entities.size(); i < c; i++) {
            Entity e = entities.get(i);
            if (e.kills > 0 && !e.isZombie()) all.add(new int[]{e.nameSeed, e.type, e.role, e.kills, 1});
        }
        for (int[] h : fallenHeroes) all.add(new int[]{h[0], h[1], h[2], h[3], 0});
        java.util.Collections.sort(all, new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return b[3] - a[3];
            }
        });
        while (all.size() > n) all.remove(all.size() - 1);
        return all;
    }

    /** Recomputes counts and paths after a save has been loaded. */
    void afterLoad() {
        fieldTimer = 0;
        spawnBirds();
        fleet.trafficTarget = new int[]{0, 8, 16}[city.cfg.traffic()] * city.w / 96;
        fleet.spawnTraffic(fleet.trafficTarget);
        buildHash();
        city.computeFields(entities);
        recount();
    }

    static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    void erase(float x, float y, float r) {
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            float ddx = e.x - x, ddy = e.y - y;
            if (ddx * ddx + ddy * ddy < r * r) {
                e.dead = true;
                e.removed = true;
                e.hordeLeader = null;
                e.leadsHorde = false;
            }
        }
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.leader != null && e.leader.removed && e.leader.dead && !isAway(e.leader)) e.leader = null;
        }
        for (int i = corpses.size() - 1; i >= 0; i--) {
            Corpse c = corpses.get(i);
            float ddx = c.x - x, ddy = c.y - y;
            if (ddx * ddx + ddy * ddy < r * r) corpses.remove(i);
        }
        recountLive();
        for (int i = 0; i < dcount; i++) {
            float ddx = dx[i] - x, ddy = dy[i] - y;
            if (ddx * ddx + ddy * ddy < r * r) dr[i] = 0;
        }
    }

    /** True if someone is only out of sight: hiding in a building or riding in a car. */
    private boolean isAway(Entity e) {
        for (int i = 0, n = city.buildings.size(); i < n; i++) if (city.buildings.get(i).occupants.contains(e)) return true;
        for (int i = 0, n = fleet.vehicles.size(); i < n; i++) if (fleet.vehicles.get(i).riders.contains(e)) return true;
        return false;
    }

    void clearAll() {
        clearBarricades();
        spits.clear();
        acids.clear();
        gases.clear();
        medkits.clear();
        entities.clear();
        corpses.clear();
        grenades.clear();
        explosions.clear();
        pickups.clear();
        fires.clear();
        pendingBlasts.clear();
        fleet.clear();
        for (City.Building b : city.buildings) {
            b.occupants.clear();
            b.barricade = 100;
        }
        Arrays.fill(plife, 0);
        Arrays.fill(tlife, 0);
        dcount = 0;
        dnext = 0;
        outbreak = false;
        dispatch.clear();
        recount();
    }

    // ------------------------------------------------------------------ update

    void update(float dt) {
        time += dt;
        shake = Math.max(0, shake - dt * 3);
        if (messageTime > 0) messageTime -= dt;
        buildHash();
        fieldTimer -= dt;
        if (fieldTimer <= 0) {
            fieldTimer = 0.25f;
            city.computeFields(entities);
        }
        dispatch.update(dt);
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (!e.dead) think(e, dt);
        }
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (!e.dead) move(e, dt);
        }
        separate();
        fleet.update(dt);
        updateFires(dt);
        updateHazards(dt);
        if (alarmTime > 0) alarmTime -= dt;
        updateBirds(dt);
        updateGrenades(dt);
        cleanup();
        updateCorpses(dt);
        updateBuildings(dt);
        updatePickups(dt);
        updateEffects(dt);
        lifeTimer -= dt;
        if (lifeTimer <= 0) {
            lifeTimer = 1;
            cityLife();
        }
        recruitTimer -= dt;
        if (recruitTimer <= 0) {
            recruitTimer = 4;
            recruitment();
        }
        if (!newRecruits.isEmpty()) {
            for (int i = 0; i < newRecruits.size(); i++) entities.add(newRecruits.get(i));
            newRecruits.clear();
        }
        recount();
        statTimer += dt;
        if (statTimer >= statStep) {
            statTimer = 0;
            sampleStats();
        }
    }

    /** Records humans vs zombies; when the history fills, halves its resolution to keep going. */
    private void sampleStats() {
        if (histCount == HISTORY) {
            for (int i = 0; i < HISTORY / 2; i++) {
                histHumans[i] = (histHumans[i * 2] + histHumans[i * 2 + 1]) / 2;
                histZombies[i] = (histZombies[i * 2] + histZombies[i * 2 + 1]) / 2;
                histArmed[i] = (histArmed[i * 2] + histArmed[i * 2 + 1]) / 2;
            }
            histCount = HISTORY / 2;
            statStep *= 2;
        }
        histHumans[histCount] = humans;
        histZombies[histCount] = zombies;
        histArmed[histCount] = counts[Entity.COP] + counts[Entity.SOLDIER];
        histCount++;
    }

    /** Commanders on the map this tick (their soldiers shoot better when they're close). */
    final ArrayList<Entity> commanders = new ArrayList<Entity>();

    private boolean commanded(Entity e) {
        for (int i = 0, n = commanders.size(); i < n; i++) {
            Entity c = commanders.get(i);
            if (c != e && (c.x - e.x) * (c.x - e.x) + (c.y - e.y) * (c.y - e.y) < 170 * 170) return true;
        }
        return false;
    }

    /** Counts everyone right now (dead and removed people don't count), for when the game is paused. */
    void recountLive() {
        Arrays.fill(counts, 0);
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (!e.dead) counts[e.type]++;
        }
    }

    private void recount() {
        Arrays.fill(counts, 0);
        commanders.clear();
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            counts[e.type]++;
            if (e.role == Entity.ROLE_COMMANDER && e.type == Entity.SOLDIER) commanders.add(e);
        }
        hiding = 0;
        for (int i = 0, n = city.buildings.size(); i < n; i++) hiding += city.buildings.get(i).occupants.size();
        riding = fleet.riderCount();
        int h = humanCount() + hiding + riding;
        int z = zombieCount();
        peakZombies = Math.max(peakZombies, z);
        int rising = 0;
        for (int i = 0, n = corpses.size(); i < n; i++) if (corpses.get(i).rise > 0) rising++;
        if (z > 0 && h > 0) outbreak = true;
        if (outbreak && z == 0 && rising == 0 && h > 0) {
            say("Outbreak contained!");
            outbreak = false;
        } else if (outbreak && h == 0 && z > 0) {
            say("The city has fallen...");
            outbreak = false;
        }
        humans = h;
        zombies = z + rising;
    }

    void say(String msg) {
        message = msg;
        messageTime = 4f;
    }

    private void buildHash() {
        int n = entities.size();
        if (sorted.length < n) sorted = new Entity[n * 2];
        Arrays.fill(cellCount, 0);
        for (int i = 0; i < n; i++) cellCount[cellOf(entities.get(i))]++;
        int acc = 0;
        for (int c = 0; c < cellCount.length; c++) {
            cellStart[c] = acc;
            cellFill[c] = acc;
            acc += cellCount[c];
        }
        cellStart[cellCount.length] = acc;
        for (int i = 0; i < n; i++) {
            Entity e = entities.get(i);
            sorted[cellFill[cellOf(e)]++] = e;
        }
    }

    private int cellOf(Entity e) {
        int cx = Math.max(0, Math.min(gw - 1, (int) (e.x / CELL)));
        int cy = Math.max(0, Math.min(gh - 1, (int) (e.y / CELL)));
        return cy * gw + cx;
    }

    private Entity nearest(Entity from, float radius, boolean wantZombie, boolean needLos) {
        float best = radius * radius;
        Entity res = null;
        int cx0 = Math.max(0, (int) ((from.x - radius) / CELL)), cx1 = Math.min(gw - 1, (int) ((from.x + radius) / CELL));
        int cy0 = Math.max(0, (int) ((from.y - radius) / CELL)), cy1 = Math.min(gh - 1, (int) ((from.y + radius) / CELL));
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || o.isZombie() != wantZombie || o == from) continue;
                    float ddx = o.x - from.x, ddy = o.y - from.y, d2 = ddx * ddx + ddy * ddy;
                    if (d2 < best && (!needLos || city.los(from.x, from.y, o.x, o.y))) {
                        best = d2;
                        res = o;
                    }
                }
            }
        }
        return res;
    }

    int countZombiesNear(float x, float y, float radius) {
        int count = 0;
        int cx0 = Math.max(0, (int) ((x - radius) / CELL)), cx1 = Math.min(gw - 1, (int) ((x + radius) / CELL));
        int cy0 = Math.max(0, (int) ((y - radius) / CELL)), cy1 = Math.min(gh - 1, (int) ((y + radius) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || !o.isZombie()) continue;
                    float ddx = o.x - x, ddy = o.y - y;
                    if (ddx * ddx + ddy * ddy < radius * radius) count++;
                }
            }
        return count;
    }

    // ------------------------------------------------------------------ AI

    private void think(Entity e, float dt) {
        e.hurt = Math.max(0, e.hurt - dt * 4);
        e.phoneTimer -= dt;
        e.talkTimer -= dt;
        e.callCd -= dt;
        e.cooldown -= dt;
        e.biteCd -= dt;
        e.grenadeCd -= dt;
        e.meleeCd -= dt;
        e.wanderTimer -= dt;
        e.fleeTimer -= dt;
        e.taskTimer -= dt;
        e.fear -= dt;
        if (e.infected) {
            e.infectTimer -= dt;
            if (e.infectTimer <= 0) {
                e.hp = 0;
                e.killedByZombie = true;
                return;
            }
        }
        if (e.fresh > 0) {
            e.fresh -= dt;
            if (e.fresh <= 0) {
                e.speed /= 1.2f;
                e.runSpeed /= 1.3f;
            }
        }
        if (e.stun > 0) {
            e.stun -= dt;
            steer(e, 0, 0, 0);
            return;
        }
        // Stuck on a corner or in a doorway (big brutes especially): step aside and try another way.
        if (e.unstick > 0) {
            e.unstick -= dt;
            steer(e, (float) Math.cos(e.unstickAngle), (float) Math.sin(e.unstickAngle), e.speed);
            return;
        }
        if (e.stuckTime > 1.2f) {
            e.stuckTime = 0;
            float heading = (float) Math.atan2(e.my, e.mx);
            e.unstickAngle = heading + (rnd.nextBoolean() ? 1 : -1) * (1.6f + rnd.nextFloat() * 1f);
            e.unstick = 0.6f + rnd.nextFloat() * 0.6f;
            e.blocked = false;
        }
        if (e.reload > 0) {
            e.reload -= dt;
            if (e.reload <= 0) {
                int take = Math.min(e.magSize - e.ammo, e.reserve);
                e.ammo += take;
                e.reserve -= take;
            }
        }
        if (e.leader != null && (e.leader.dead && !e.leader.removed)) e.leader = null;
        if (e.isZombie()) thinkZombie(e, dt);
        else if (e.isArmed()) thinkArmed(e, dt);
        else if (e.type == Entity.MEDIC) thinkMedic(e, dt);
        else if (e.type == Entity.DOG) thinkDog(e, dt);
        else if (e.type == Entity.RAIDER) thinkRaider(e, dt);
        else thinkCivilian(e, dt);
    }

    /** Makes zombies within a radius head for a noise (gunfire, explosions, a screamer). */
    void noise(float x, float y, float radius) {
        scareBirds(x, y, radius);
        int cx0 = Math.max(0, (int) ((x - radius) / CELL)), cx1 = Math.min(gw - 1, (int) ((x + radius) / CELL));
        int cy0 = Math.max(0, (int) ((y - radius) / CELL)), cy1 = Math.min(gh - 1, (int) ((y + radius) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || !o.isZombie()) continue;
                    float ddx = o.x - x, ddy = o.y - y;
                    if (ddx * ddx + ddy * ddy > radius * radius) continue;
                    o.noiseX = x + rnd.nextFloat() * 30 - 15;
                    o.noiseY = y + rnd.nextFloat() * 30 - 15;
                    o.noiseTimer = 14;
                }
            }
    }

    private void thinkZombie(Entity z, float dt) {
        if (z.blocked && !barriers.isEmpty()) bashBarrier(z, dt);
        z.noiseTimer -= dt;
        z.screamCd -= dt;
        z.feedTimer -= dt;
        if (rnd.nextFloat() < dt * 0.03f) emit(z.type == Entity.BRUTE ? Sfx.GROAN_DEEP : Sfx.GROAN, z.x, z.y);
        Entity t = nearest(z, 110, false, true);
        if (t != null) {
            if (z.type == Entity.SCREAMER && z.screamCd <= 0) {
                // A screamer calls every zombie around to its prey.
                z.screamCd = 10;
                noise(t.x, t.y, 380);
                emit(Sfx.SHRIEK, z.x, z.y);
                screams.add(new float[]{z.x, z.y, 0});
            }
            z.feedTimer = 0;
            float ddx = t.x - z.x, ddy = t.y - z.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (z.type == Entity.SPITTER && d > 26) {
                // Spitters keep their distance and lob acid.
                z.grenadeCd -= dt;
                if (d < 110 && z.grenadeCd <= 0) {
                    z.grenadeCd = 3 + rnd.nextFloat();
                    spit(z, t);
                }
                if (d < 55) steer(z, -ddx / d, -ddy / d, z.speed);
                else if (d > 85) steer(z, ddx / d, ddy / d, z.speed);
                else steer(z, 0, 0, 0);
                z.angle = (float) Math.atan2(ddy, ddx);
                return;
            }
            float ax = ddx / d, ay = ddy / d;
            // Each zombie comes at its prey from its own angle, so a crowd closes in from all sides.
            if (d > 12 && d < 100) {
                float a = z.flank * Math.min(1, (d - 12) / 40f) * 0.7f;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                float rx = ax * ca - ay * sa, ry = ax * sa + ay * ca;
                ax = rx;
                ay = ry;
                if (z.blocked) {
                    z.blocked = false;
                    z.flank = -z.flank * 0.5f;
                }
            }
            steer(z, ax, ay, d < 45 ? z.runSpeed : z.speed);
            if (d < z.radius + t.radius + 2.5f && z.biteCd <= 0) bite(z, t, ddx / d, ddy / d);
            return;
        }
        if (z.taskTimer <= 0) {
            z.taskTimer = 1.5f + rnd.nextFloat();
            thinkHorde(z);
            // Stop to feed on a fresh body.
            if (z.feedTimer <= 0 && z.noiseTimer <= 0 && rnd.nextFloat() < 0.4f) {
                Corpse c = freshCorpse(z.x, z.y, 45);
                if (c != null) {
                    z.feedTimer = 5 + rnd.nextFloat() * 7;
                    z.threatX = c.x + rnd.nextFloat() * 6 - 3;
                    z.threatY = c.y + rnd.nextFloat() * 6 - 3;
                }
            }
        }
        if (z.feedTimer > 0) {
            float ddx = z.threatX - z.x, ddy = z.threatY - z.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d > 5 && !z.blocked) steer(z, ddx / d, ddy / d, z.speed);
            else {
                steer(z, 0, 0, 0);
                z.angle = turn(z.angle, (float) Math.atan2(ddy, ddx), dt * 4);
                if (gore && rnd.nextFloat() < dt * 1.5f) bloodBurst(z.threatX, z.threatY, 2, 0, 0);
            }
            return;
        }
        // People barricaded indoors: batter the door down.
        City.Building target = null;
        float best = 150 * 150;
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.occupants.isEmpty()) continue;
            float ddx = b.doorX - z.x, ddy = b.doorY - z.y, d2 = ddx * ddx + ddy * ddy;
            if (d2 < best) {
                best = d2;
                target = b;
            }
        }
        if (target != null) {
            float ddx = target.doorX - z.x, ddy = target.doorY - z.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d < 14) {
                steer(z, 0, 0, 0);
                float dps = z.type == Entity.BRUTE ? 22 : z.type == Entity.CRAWLER ? 3 : 7;
                target.barricade -= dps * dt;
                target.calmTimer = 0;
                if (rnd.nextFloat() < dt * 1.2f) emit(Sfx.THUD, target.doorX, target.doorY);
                return;
            }
            if (followField(z, city.humanDist, z.speed)) return;
            steer(z, ddx / d, ddy / d, z.speed);
            return;
        }
        // Cars: chase the ones driving past and claw at the ones that stop.
        Fleet.Vehicle car = carFor(z, 100);
        if (car != null) {
            float ddx = car.x - z.x, ddy = car.y - z.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d < car.length() * 0.7f + z.radius + 2) {
                if (car.speed < 15) {
                    steer(z, 0, 0, 0);
                    // Armour takes a lot of clawing; a car much less.
                    float dps = car.type == Fleet.TANK ? (z.type == Entity.BRUTE ? 5 : 0.6f) : (z.type == Entity.BRUTE ? 16 : 4);
                    fleet.damage(car, dps * dt, false);
                    if (rnd.nextFloat() < dt * 1.5f) emit(Sfx.THUD, car.x, car.y);
                } else {
                    steer(z, ddx / d, ddy / d, z.runSpeed);
                }
            } else {
                steer(z, ddx / d, ddy / d, d < 60 ? z.runSpeed : z.speed * 1.2f);
            }
            return;
        }
        if (z.noiseTimer > 0) {
            float ddx = z.noiseX - z.x, ddy = z.noiseY - z.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d < 25) z.noiseTimer = 0;
            else {
                steer(z, ddx / d, ddy / d, z.speed * 1.2f);
                if (z.blocked) {
                    z.blocked = false;
                    z.noiseTimer -= 2;
                }
                return;
            }
        }
        int dist = city.fieldAt(city.humanDist, z.x, z.y);
        if (dist < 40 && followField(z, city.humanDist, z.speed)) return;
        if (z.leadsHorde) {
            roam(z);
            return;
        }
        Entity lead = z.hordeLeader;
        if (lead != null) {
            float gx = lead.x + z.roamX - z.x, gy = lead.y + z.roamY - z.y;
            float d = (float) Math.sqrt(gx * gx + gy * gy) + 0.001f;
            if (d > 260 || (z.blocked && d > 60)) {
                z.blocked = false;
                z.hordeLeader = null;
            } else if (d > 8) {
                steer(z, gx / d, gy / d, Math.min(z.speed * (d > 40 ? 1.3f : 1f), lead.want + d * 0.4f));
            } else {
                steer(z, lead.mx, lead.my, lead.want);
            }
            return;
        }
        wander(z, z.speed * 0.45f);
    }

    /** Hordes: idle zombies bunch up behind a leader who roams the city, and small hordes merge. */
    private void thinkHorde(Entity z) {
        if (z.type == Entity.CRAWLER) return;
        if (z.hordeLeader != null && (z.hordeLeader.dead || !z.hordeLeader.leadsHorde)) z.hordeLeader = null;
        if (z.leadsHorde) {
            int size = 1;
            for (int i = 0, n = entities.size(); i < n; i++) if (entities.get(i).hordeLeader == z) size++;
            z.hordeSize = size;
            biggestHorde = Math.max(biggestHorde, size);
            Entity other = nearestHordeLeader(z, 140);
            if (size < 3 || (other != null && other.hordeSize >= size)) {
                z.leadsHorde = false;
                z.hordeAnnounced = false;
                if (other != null) join(z, other);
                return;
            }
            if (size >= 15 && !z.hordeAnnounced) {
                z.hordeAnnounced = true;
                dispatch.say(Dispatch.WHO_INFO, null, "A horde of about " + (size / 5 * 5) + " zombies is moving through "
                        + city.placeName(z.x, z.y) + ".", z.x, z.y);
                if (size >= 25) dispatch.heavyContact(null, z.x, z.y, size);
            }
            return;
        }
        if (z.hordeLeader != null) return;
        Entity lead = nearestHordeLeader(z, 170);
        if (lead != null) join(z, lead);
        else if (countZombiesNear(z.x, z.y, 70) >= 5) {
            z.leadsHorde = true;
            z.hordeSize = 1;
            pickRoam(z);
        }
    }

    private void join(Entity z, Entity lead) {
        z.hordeLeader = lead;
        float a = rnd.nextFloat() * TAU, r = 8 + rnd.nextFloat() * 30;
        z.roamX = (float) Math.cos(a) * r;
        z.roamY = (float) Math.sin(a) * r;
    }

    private Entity nearestHordeLeader(Entity z, float radius) {
        Entity best = null;
        float bd = radius * radius;
        int cx0 = Math.max(0, (int) ((z.x - radius) / CELL)), cx1 = Math.min(gw - 1, (int) ((z.x + radius) / CELL));
        int cy0 = Math.max(0, (int) ((z.y - radius) / CELL)), cy1 = Math.min(gh - 1, (int) ((z.y + radius) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o == z || o.dead || !o.leadsHorde) continue;
                    float d = (o.x - z.x) * (o.x - z.x) + (o.y - z.y) * (o.y - z.y);
                    if (d < bd) {
                        bd = d;
                        best = o;
                    }
                }
            }
        return best;
    }

    private void pickRoam(Entity z) {
        float a = rnd.nextFloat() * TAU, r = 200 + rnd.nextFloat() * 400;
        float[] p = city.findWalkable(Math.max(20, Math.min(city.worldW() - 20, z.x + (float) Math.cos(a) * r)),
                Math.max(20, Math.min(city.worldH() - 20, z.y + (float) Math.sin(a) * r)));
        if (p == null) p = city.randomWalkable(rnd);
        z.roamX = p[0];
        z.roamY = p[1];
    }

    /** A horde leader shambles towards a far-off spot, or towards people it can smell. */
    private void roam(Entity z) {
        if (city.fieldAt(city.humanDist, z.x, z.y) < 70 && followField(z, city.humanDist, z.speed * 0.55f)) return;
        float ddx = z.roamX - z.x, ddy = z.roamY - z.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d < 30 || z.blocked) {
            z.blocked = false;
            pickRoam(z);
            return;
        }
        steer(z, ddx / d, ddy / d, z.speed * 0.5f);
    }

    /** A body that isn't about to get up, for a zombie to feed on. */
    private Corpse freshCorpse(float x, float y, float radius) {
        for (int i = corpses.size() - 1; i >= 0; i--) {
            Corpse c = corpses.get(i);
            if (c.zombie || c.rise > 0 || c.age > 60) continue;
            if ((c.x - x) * (c.x - x) + (c.y - y) * (c.y - y) < radius * radius) return c;
        }
        return null;
    }

    /** A car worth going after: one driving by, or one with people in it. */
    private Fleet.Vehicle carFor(Entity z, float radius) {
        Fleet.Vehicle best = null;
        float bd = radius * radius;
        for (int i = 0, n = fleet.vehicles.size(); i < n; i++) {
            Fleet.Vehicle v = fleet.vehicles.get(i);
            if (Fleet.airborne(v) || v.type == Fleet.TRAIN || (v.broken && v.riders.isEmpty())) continue;
            if (v.speed < 5 && v.riders.isEmpty() && v.passengers == 0 && v.type != Fleet.FIRE_ENGINE && v.type != Fleet.CAR
                    && v.type != Fleet.TANK && v.type != Fleet.AMBULANCE)
                continue;
            if (v.parked) continue;
            float d = (v.x - z.x) * (v.x - z.x) + (v.y - z.y) * (v.y - z.y);
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        return best;
    }

    private void bite(Entity z, Entity t, float nx, float ny) {
        // A riot shield turns away most bites from the front.
        if (t.type == Entity.COP && t.role == Entity.ROLE_RIOT) {
            float face = (float) Math.cos(t.angle) * -nx + (float) Math.sin(t.angle) * -ny;
            if (face > 0.35f && rnd.nextFloat() < 0.85f) {
                z.biteCd = 0.8f;
                z.stun = Math.max(z.stun, z.type == Entity.BRUTE ? 0.2f : 0.5f);
                tryMove(z, -nx * 5 / z.mass, -ny * 5 / z.mass);
                emit(Sfx.SHIELD, t.x, t.y);
                return;
            }
        }
        float dmg = z.type == Entity.BRUTE ? 30 : z.type == Entity.RUNNER ? 8 : z.type == Entity.CRAWLER ? 10
                : z.type == Entity.ZOMBIE_DOG ? 7 : 12;
        z.biteCd = z.type == Entity.RUNNER || z.type == Entity.ZOMBIE_DOG ? 0.55f : 0.9f;
        t.hp -= dmg;
        t.hurt = 1;
        t.killedByZombie = true;
        t.fleeTimer = 2f;
        t.threatX = z.x;
        t.threatY = z.y;
        bloodBurst(t.x, t.y, 6, nx, ny);
        emit(Sfx.BITE, t.x, t.y);
        if (!t.infected && t.type != Entity.DOG && rnd.nextFloat() < 0.4f) {
            t.infected = true;
            t.infectTimer = 12 + rnd.nextFloat() * 14;
            t.cureTried = false;
        }
        if (z.type == Entity.BRUTE) tryMove(t, nx * 8, ny * 8);
    }

    /** Shoves a zombie back and stuns it; armed units also hit it with their weapon. */
    private void shove(Entity e, Entity z, boolean strong) {
        e.meleeCd = strong ? 1.2f : 2.2f;
        float ddx = z.x - e.x, ddy = z.y - e.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        float push = (strong ? 11 : 7) / z.mass;
        tryMove(z, ddx / d * push, ddy / d * push);
        z.stun = z.type == Entity.BRUTE ? 0.3f : strong ? 0.9f : 0.6f;
        if (strong) {
            z.hp -= 8;
            z.hurt = 1;
        }
        e.angle = (float) Math.atan2(ddy, ddx);
        emit(Sfx.THUD, z.x, z.y);
    }

    private Entity nearestInReach(Entity e) {
        Entity z = nearest(e, e.radius + 9, true, false);
        if (z == null) return null;
        float ddx = z.x - e.x, ddy = z.y - e.y, reach = e.radius + z.radius + 3;
        return ddx * ddx + ddy * ddy < reach * reach ? z : null;
    }

    private void thinkCivilian(Entity e, float dt) {
        Entity threat = nearest(e, 100, true, true);
        if (threat == null) threat = nearest(e, 26, true, false);
        // Raiders are frightening too (unless you're following one).
        if (threat == null && counts[Entity.RAIDER] > 0) threat = nearestRaider(e, 70);
        float threatDist = Float.MAX_VALUE;
        if (threat != null) {
            if (e.fleeTimer <= 0) {
                if (rnd.nextFloat() < 0.3f) emit(Sfx.SCREAM, e.x, e.y);
                alarm(e, threat);
            }
            e.fleeTimer = 4f;
            e.fear = 40;
            e.threatX = threat.x;
            e.threatY = threat.y;
            float ddx = threat.x - e.x, ddy = threat.y - e.y;
            threatDist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            // Some people call 911 when they see one.
            if (e.callCd <= 0 && threatDist > 20) {
                e.callCd = 45;
                if (rnd.nextFloat() < 0.35f) dispatch.call(e, threat);
            }
        }

        // Volunteers on their way to sign up.
        if (e.task == Dispatch.T_ENLIST) {
            if (threat != null && threatDist < 60 || e.enlistAt == null) {
                // Not now: run, and maybe try again later.
                e.task = Dispatch.T_NONE;
                e.enlistAt = null;
            } else {
                enlistWalk(e, dt);
                return;
            }
        }

        // Anyone grabbed can try to shove the zombie off.
        if (e.meleeCd <= 0) {
            Entity z = nearestInReach(e);
            if (z != null) {
                if (rnd.nextFloat() < 0.4f) shove(e, z, false);
                else e.meleeCd = 0.8f;
            }
        }

        // Waved down a car: run for it and jump in.
        if (boardRide(e)) return;
        if (cleanup(e, threat)) return;
        if (supplyRun(e, threat)) return;
        // Running for their life past an abandoned car with the keys still in it.
        if (e.fleeTimer > 0 && e.ride == null && rnd.nextFloat() < dt * 2) {
            Fleet.Vehicle car = abandonedCarNear(e.x, e.y, 40);
            if (car != null) {
                e.dead = true;
                e.removed = true;
                fleet.takeCar(car, e);
                return;
            }
        }

        // Family members pull zombies off each other.
        Entity kin = e.leader;
        if (kin != null && !kin.dead && e.meleeCd <= 0 && threatDist > 12) {
            Entity grab = nearestInReach(kin);
            if (grab != null) {
                float ddx = grab.x - e.x, ddy = grab.y - e.y;
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
                if (d < 30) {
                    if (d < e.radius + grab.radius + 3) shove(e, grab, false);
                    else steer(e, ddx / d, ddy / d, e.runSpeed);
                    return;
                }
            }
        }

        // Armed civilians shoot, backing off if the zombie gets too close.
        if (e.hasGun && threat != null && threatDist > 14 && threatDist < 90 && (e.ammo > 0 || e.reserve > 0)
                && e.task != Dispatch.T_SHELTER) {
            aimAndFire(e, threat, threatDist, 120, dt);
            if (threatDist < 38) {
                float ax = e.x - threat.x, ay = e.y - threat.y;
                flee(e, ax / threatDist, ay / threatDist, e.speed * 1.2f);
            } else {
                steer(e, 0, 0, 0);
            }
            return;
        }
        e.aiming = false;

        // Out of bullets and it's quiet: restock at a supermarket.
        if (e.hasGun && marketRun(e, threat)) return;

        // Someone dropped a gun nearby and it's quiet: go get it.
        if (!e.hasGun && e.task == Dispatch.T_NONE && threat == null && e.taskTimer <= 0) {
            e.taskTimer = 1;
            Pickup p = nearestPickup(e.x, e.y, 110);
            if (p != null && countZombiesNear(p.x, p.y, 50) == 0) {
                if (p.uses == 0) p.claimed = true;
                e.pickup = p;
                e.task = Dispatch.T_PICKUP;
            }
        }
        if (e.task == Dispatch.T_PICKUP) {
            Pickup p = e.pickup;
            if (p == null || !pickups.contains(p) || threatDist < 40) {
                if (p != null) p.claimed = false;
                e.task = Dispatch.T_NONE;
                e.pickup = null;
            } else {
                float ddx = p.x - e.x, ddy = p.y - e.y;
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
                if (d < 6 && p.drop <= 0) {
                    armCivilian(e, p.rounds);
                    if (p.uses > 0) {
                        if (--p.uses <= 0) pickups.remove(p);
                    } else pickups.remove(p);
                    e.task = Dispatch.T_NONE;
                    e.pickup = null;
                } else {
                    steer(e, ddx / d, ddy / d, e.speed * 1.6f);
                }
                return;
            }
        }

        // Badly hurt and it's quiet: walk to the hospital.
        if (healSeeking(e, threat, dt)) return;

        // Hide indoors: run to a nearby building with room if the zombie isn't too close.
        if (e.task == Dispatch.T_NONE && threat != null && threatDist > 30 && !e.hasGun && e.taskTimer <= 0) {
            e.taskTimer = 1.5f;
            City.Building b = shelterNear(e);
            if (b != null && rnd.nextFloat() < 0.6f) {
                e.task = Dispatch.T_HIDE;
                e.building = b;
            }
        }
        if (e.task == Dispatch.T_HIDE) {
            City.Building b = e.building;
            if (b == null || b.occupants.size() >= b.capacity || b.barricade <= 0) {
                e.task = Dispatch.T_NONE;
                e.building = null;
            } else {
                float ddx = b.doorX - e.x, ddy = b.doorY - e.y;
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
                if (d < 8) {
                    enterBuilding(e, b);
                } else if (!walkTo(e, b, ddx, ddy, d, e.runSpeed) && e.blocked && rnd.nextFloat() < 0.1f) {
                    e.task = Dispatch.T_NONE;
                    e.building = null;
                }
                return;
            }
        }

        // Safe zones: scared people head for one, and some go as soon as they hear it on the news.
        boolean room = dispatch.hasRoom();
        if (e.task == Dispatch.T_SHELTER && dispatch.zones.isEmpty()) e.task = Dispatch.T_NONE;
        if (e.task == Dispatch.T_SEEK && !room) e.task = Dispatch.T_NONE;
        if (e.task == Dispatch.T_NONE && room && (e.fleeTimer > 0 || rnd.nextFloat() < dt * 0.03f))
            e.task = Dispatch.T_SEEK;
        if (e.task == Dispatch.T_SEEK) {
            Dispatch.SafeZone z = dispatch.zoneAt(e.x, e.y, 0.7f);
            if (z != null) dispatch.admit(e, z);
        }

        if (e.task == Dispatch.T_SEEK && threatDist > 28) {
            e.paused = false;
            float speed = e.fleeTimer > 0 ? e.runSpeed : e.speed * 1.7f;
            if (!followField(e, dispatch.zoneField, speed)) wander(e, e.speed);
            return;
        }
        if (e.task == Dispatch.T_SHELTER && threatDist > 28 && e.zone != null) {
            float ddx = e.zone.x - e.x, ddy = e.zone.y - e.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            // Mill around inside the zone; head back in (around walls) if drifting out.
            if (d > e.zone.r * 0.75f) {
                if (!followField(e, dispatch.zoneField, e.speed)) steer(e, ddx / d, ddy / d, e.speed);
            } else {
                wander(e, e.speed * 0.4f);
            }
            return;
        }
        if (followLeader(e)) return;
        if (patrol(e)) return;
        if (e.fleeTimer > 0) {
            e.paused = false;
            float ax = e.x - e.threatX, ay = e.y - e.threatY;
            float d = (float) Math.sqrt(ax * ax + ay * ay) + 0.001f;
            // Run towards a cop or soldier if one is nearby and not back where the zombies are.
            if (e.protector == null || e.protector.dead || rnd.nextFloat() < dt) e.protector = nearestArmed(e, 180);
            Entity p = e.protector;
            if (p != null && !p.dead) {
                float px = p.x - e.x, py = p.y - e.y, pd = (float) Math.sqrt(px * px + py * py) + 0.001f;
                if (pd > 25 && (px * ax + py * ay) / (pd * d) > -0.2f) {
                    steer(e, ax / d * 0.4f + px / pd * 0.6f, ay / d * 0.4f + py / pd * 0.6f, e.runSpeed);
                    float l = (float) Math.sqrt(e.mx * e.mx + e.my * e.my) + 0.001f;
                    e.mx /= l;
                    e.my /= l;
                    return;
                }
            }
            flee(e, ax / d, ay / d, e.runSpeed);
        } else {
            wander(e, e.speed);
        }
    }

    /** Family members stick with whoever leads them: keeping up, and running the same way. */
    private boolean followLeader(Entity e) {
        Entity lead = e.leader;
        if (lead == null || lead.dead) return false;
        float ddx = lead.x - e.x, ddy = lead.y - e.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d > 300) {
            e.leader = null;
            return false;
        }
        if (e.fleeTimer > 0) {
            // Scared: run to them unless the zombie is right here.
            float tx = e.threatX - e.x, ty = e.threatY - e.y;
            if (d < 22 || tx * tx + ty * ty < 18 * 18) return false;
            steer(e, ddx / d, ddy / d, e.runSpeed);
            return true;
        }
        if (d > 16) steer(e, ddx / d, ddy / d, d > 50 ? e.runSpeed : Math.max(e.speed * 1.3f, lead.want));
        else if (lead.want > 1) steer(e, lead.mx, lead.my, lead.want);
        else steer(e, 0, 0, 0);
        return true;
    }

    // ------------------------------------------------------------------ spitters, bloaters, medkits

    /** Acid in flight {x, y, vx, vy, life}, acid puddles {x, y, life} and gas clouds {x, y, radius, life}. */
    final ArrayList<float[]> spits = new ArrayList<float[]>(), acids = new ArrayList<float[]>(), gases = new ArrayList<float[]>();
    /** Medkits {x, y, uses}: anyone hurt nearby is patched up, and fresh bites can be treated. */
    final ArrayList<float[]> medkits = new ArrayList<float[]>();

    private void spit(Entity z, Entity t) {
        float ddx = t.x + t.vx * 0.5f - z.x, ddy = t.y + t.vy * 0.5f - z.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f, flight = d / 110f;
        spits.add(new float[]{z.x, z.y, ddx / flight, ddy / flight, flight});
        emit(Sfx.SPIT, z.x, z.y);
    }

    /** A bloater pops: a cloud of infectious gas. */
    private void burst(float x, float y) {
        gases.add(new float[]{x, y, 36, 9});
        emit(Sfx.BURST, x, y);
        for (int i = 0; i < 18; i++) {
            float a = rnd.nextFloat() * TAU, sp = 20 + rnd.nextFloat() * 60;
            particle(x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, 0.5f, 1.2f, 0xFF7CA83A, P_DOT);
        }
        if (gore) decal(x, y, 9, 0x886A8A2A);
    }

    private void updateHazards(float dt) {
        for (int i = spits.size() - 1; i >= 0; i--) {
            float[] s = spits.get(i);
            s[0] += s[2] * dt;
            s[1] += s[3] * dt;
            s[4] -= dt;
            if (s[4] > 0 && !city.solidAt(s[0], s[1])) continue;
            spits.remove(i);
            acids.add(new float[]{s[0], s[1], 7});
            decal(s[0], s[1], 5 + rnd.nextFloat() * 2, 0x66A8D030, D_ACID, 0);
            hurtArea(s[0], s[1], 9, 12, false);
        }
        for (int i = acids.size() - 1; i >= 0; i--) {
            float[] a = acids.get(i);
            a[2] -= dt;
            if (a[2] <= 0) {
                acids.remove(i);
                continue;
            }
            hurtArea(a[0], a[1], 7, 10 * dt, false);
            if (rnd.nextFloat() < dt * 6)
                particle(a[0] + rnd.nextFloat() * 8 - 4, a[1] + rnd.nextFloat() * 8 - 4, 0, -6, 0.5f, 0.9f, 0xFFD8F06A, P_DOT);
        }
        for (int i = gases.size() - 1; i >= 0; i--) {
            float[] g = gases.get(i);
            g[3] -= dt;
            if (g[3] <= 0) {
                gases.remove(i);
                continue;
            }
            hurtArea(g[0], g[1], g[2], 3 * dt, true);
        }
        for (int i = medkits.size() - 1; i >= 0; i--) {
            float[] m = medkits.get(i);
            for (int k = 0, n = entities.size(); k < n && m[2] > 0; k++) {
                Entity e = entities.get(k);
                if (e.dead || e.isZombie() || e.type == Entity.RAIDER) continue;
                if ((e.x - m[0]) * (e.x - m[0]) + (e.y - m[1]) * (e.y - m[1]) > 40 * 40) continue;
                if (e.hp < e.maxHp) {
                    e.hp = Math.min(e.maxHp, e.hp + 15 * dt);
                    m[2] -= dt * 0.5f;
                }
                if (e.infected && !e.cureTried) {
                    tryCure(e, 0.5f);
                    m[2] -= 1;
                }
            }
            if (m[2] <= 0) medkits.remove(i);
        }
    }

    /** Hurts people (not zombies) standing in acid or gas; gas can infect them. */
    private void hurtArea(float x, float y, float r, float dmg, boolean infect) {
        int cx0 = Math.max(0, (int) ((x - r) / CELL)), cx1 = Math.min(gw - 1, (int) ((x + r) / CELL));
        int cy0 = Math.max(0, (int) ((y - r) / CELL)), cy1 = Math.min(gh - 1, (int) ((y + r) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || o.isZombie()) continue;
                    if ((o.x - x) * (o.x - x) + (o.y - y) * (o.y - y) > r * r) continue;
                    o.hp -= dmg;
                    o.hurt = Math.max(o.hurt, 0.4f);
                    o.fleeTimer = Math.max(o.fleeTimer, 1.5f);
                    o.threatX = x;
                    o.threatY = y;
                    if (infect && !o.infected && o.type != Entity.DOG && rnd.nextFloat() < 0.35f * dmg) {
                        o.infected = true;
                        o.infectTimer = 15 + rnd.nextFloat() * 15;
                        o.cureTried = false;
                    }
                }
            }
    }

    float[] placeMedkit(float x, float y) {
        float[] p = city.findWalkable(x, y);
        if (p == null) return null;
        float[] m = {p[0], p[1], 12};
        medkits.add(m);
        return m;
    }

    /** A jet flies over and drops a line of bombs across (x, y). */
    void airstrike(float x, float y) {
        fleet.sendJet(x, y);
        dispatch.say(Dispatch.WHO_MILITARY, null, "Military: Airstrike inbound on " + city.placeName(x, y)
                + ". Everyone clear the area!", x, y);
    }

    /** The emergency sirens: everyone out on the street heads indoors or for a safe zone. */
    void cityAlarm() {
        emit(Sfx.ALARM, city.worldW() / 2, city.worldH() / 2);
        alarmTime = 6;
        int sent = 0;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.type != Entity.CIVILIAN || e.task != Dispatch.T_NONE) continue;
            City.Building b = shelterWithin(e, 250);
            if (b != null) {
                e.task = Dispatch.T_HIDE;
                e.building = b;
                sent++;
            } else if (dispatch.hasRoom()) {
                e.task = Dispatch.T_SEEK;
                sent++;
            }
        }
        dispatch.say(Dispatch.WHO_INFO, null, "City Hall: Emergency sirens are sounding. Get indoors now!",
                city.worldW() / 2, city.worldH() / 2);
    }

    /** Seconds the siren is still wailing (GameView keeps playing it). */
    float alarmTime;

    /** Quietly bites the person nearest (x, y). Returns them, or null. */
    Entity infectAt(float x, float y) {
        Entity best = null;
        float bd = 30 * 30;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.dead || e.isZombie() || e.infected || e.type == Entity.DOG) continue;
            float d = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) return null;
        best.infected = true;
        best.infectTimer = 25 + rnd.nextFloat() * 20;
        best.cureTried = false;
        if (patientZero == null) markPatientZero(best);
        return best;
    }

    /** The first person (or zombie) of the outbreak, and where it started. */
    Entity patientZero;
    String outbreakPlace;

    void markPatientZero(Entity e) {
        patientZero = e;
        outbreakPlace = city.placeName(e.x, e.y);
    }

    private City.Building shelterWithin(Entity e, float radius) {
        City.Building best = null;
        float bd = radius * radius;
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.capacity == 0 || b.collapsed || b.occupants.size() >= b.capacity) continue;
            float d = (b.doorX - e.x) * (b.doorX - e.x) + (b.doorY - e.y) * (b.doorY - e.y);
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ sandbox tools

    /** Puts a barricade on the tile at (x, y). Returns it, or null if the tile can't take one. */
    float[] placeBarricade(float x, float y) {
        int tx = (int) Math.floor(x / City.T), ty = (int) Math.floor(y / City.T);
        if (tx < 0 || ty < 0 || tx >= city.w || ty >= city.h) return null;
        int i = ty * city.w + tx;
        byte t = city.tiles[i];
        if (city.solid[i] || t == City.RAIL) return null;
        // Not on top of someone.
        for (int k = 0, n = entities.size(); k < n; k++) {
            Entity e = entities.get(k);
            if (Math.abs(e.x - (tx * City.T + City.T / 2f)) < City.T / 2f + e.radius
                    && Math.abs(e.y - (ty * City.T + City.T / 2f)) < City.T / 2f + e.radius) return null;
        }
        float[] b = {tx * City.T + City.T / 2f, ty * City.T + City.T / 2f, 220, i, t};
        city.tiles[i] = City.FENCE;
        city.solid[i] = true;
        barriers.add(b);
        fieldTimer = 0;
        return b;
    }

    void removeBarricade(float[] b) {
        if (!barriers.remove(b)) return;
        int i = (int) b[3];
        city.tiles[i] = (byte) b[4];
        city.solid[i] = false;
        fieldTimer = 0;
    }

    private void bashBarrier(Entity z, float dt) {
        for (int i = 0; i < barriers.size(); i++) {
            float[] b = barriers.get(i);
            float ddx = b[0] - z.x, ddy = b[1] - z.y;
            if (ddx * ddx + ddy * ddy > 15 * 15) continue;
            b[2] -= (z.type == Entity.BRUTE ? 30 : 6) * dt;
            if (rnd.nextFloat() < dt * 1.5f) emit(Sfx.THUD, b[0], b[1]);
            if (b[2] <= 0) {
                removeBarricade(b);
                for (int k = 0; k < 8; k++)
                    particle(b[0], b[1], rnd.nextFloat() * 40 - 20, rnd.nextFloat() * 40 - 20, 0.6f, 1.2f, 0xFF7A5634, P_DEBRIS);
            }
            return;
        }
    }

    /** A crate of guns and ammo; with a drop time it comes down by parachute. */
    Pickup placeCrate(float x, float y, float drop) {
        float[] p = city.findWalkable(x, y);
        if (p == null) return null;
        Pickup c = new Pickup();
        c.x = p[0];
        c.y = p[1];
        c.rounds = 30;
        c.uses = 6;
        c.drop = drop;
        pickups.add(c);
        return c;
    }

    void supplyDrop(float x, float y) {
        Pickup c = placeCrate(x, y, 3.5f);
        if (c != null)
            dispatch.say(Dispatch.WHO_MILITARY, null, "Military: Supply drop coming down at " + city.placeName(c.x, c.y)
                    + ". Weapons and ammo for anyone who needs them.", c.x, c.y);
    }

    /** A big horde walks in from the nearest edge of the map, heading for (x, y). Returns who came. */
    ArrayList<Entity> hordeEvent(float x, float y) {
        ArrayList<Entity> out = new ArrayList<Entity>();
        float[] edge = city.edgeSpawn(x, y);
        if (edge == null) return out;
        Entity lead = null;
        int n = 25 + rnd.nextInt(15);
        for (int i = 0; i < n; i++) {
            float roll = rnd.nextFloat();
            int type = roll < 0.12f ? Entity.RUNNER : roll < 0.16f ? Entity.BRUTE : roll < 0.2f ? Entity.ZOMBIE_DOG : Entity.ZOMBIE;
            Entity z = spawn(type, edge[0] + rnd.nextFloat() * 50 - 25, edge[1] + rnd.nextFloat() * 50 - 25);
            if (z == null) continue;
            out.add(z);
            z.noiseX = x + rnd.nextFloat() * 40 - 20;
            z.noiseY = y + rnd.nextFloat() * 40 - 20;
            z.noiseTimer = 90;
            if (lead == null) {
                lead = z;
                z.leadsHorde = true;
                z.roamX = x;
                z.roamY = y;
            } else join(z, lead);
        }
        dispatch.say(Dispatch.WHO_INFO, null, "A huge horde of " + out.size() + " is coming into town from "
                + city.placeName(edge[0], edge[1]) + "!", edge[0], edge[1]);
        return out;
    }

    /** Panic: everyone near a point screams and runs. */
    void panic(float x, float y) {
        int n = 0;
        for (int i = 0, c = entities.size(); i < c; i++) {
            Entity e = entities.get(i);
            if (e.type != Entity.CIVILIAN && e.type != Entity.DOG) continue;
            if ((e.x - x) * (e.x - x) + (e.y - y) * (e.y - y) > 320 * 320) continue;
            e.fleeTimer = 5 + rnd.nextFloat() * 3;
            e.threatX = x;
            e.threatY = y;
            if (rnd.nextFloat() < 0.2f) emit(Sfx.SCREAM, e.x, e.y);
            n++;
        }
        scareBirds(x, y, 320);
        if (n > 0) dispatch.say(Dispatch.WHO_911, null, "\"Everyone's running! Something's happening on " + city.placeName(x, y) + "!\"", x, y);
    }

    /** An outbreak inside the building nearest a point. Returns false if there isn't one close by. */
    boolean indoorOutbreakAt(float x, float y) {
        City.Building best = null;
        float bd = 160 * 160;
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.capacity == 0 || b.collapsed) continue;
            float cx = (b.x0 + b.x1) / 2, cy = (b.y0 + b.y1) / 2;
            float d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        if (best == null) return false;
        indoorOutbreak(best, 2);
        dispatch.say(Dispatch.WHO_911, null, "\"Someone's been bitten inside " + (best.name != null ? best.name : "a building on "
                + city.placeName(best.doorX, best.doorY)) + ". People are trapped in there!\"", best.doorX, best.doorY);
        return true;
    }

    void clearZombies() {
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.isZombie()) {
                e.dead = true;
                e.removed = true;
            }
        }
        for (int i = corpses.size() - 1; i >= 0; i--) if (corpses.get(i).rise > 0) corpses.get(i).rise = -1;
    }

    void clearBodies() {
        corpses.clear();
        for (int i = 0; i < dcount; i++) if (dkind[i] == D_SPLAT && (dcol[i] & 0xFF0000) > 0x500000) dr[i] = 0;
    }

    void clearWrecks() {
        for (int i = fleet.vehicles.size() - 1; i >= 0; i--)
            if (fleet.vehicles.get(i).broken && fleet.vehicles.get(i).riders.isEmpty()) fleet.vehicles.remove(i);
        fires.clear();
        pendingBlasts.clear();
    }

    void clearBarricades() {
        while (!barriers.isEmpty()) removeBarricade(barriers.get(barriers.size() - 1));
    }

    // ------------------------------------------------------------------ outbreaks, evolution and recovery

    /** People sheltering in a building, some of them bitten: when one turns, the building is lost. */
    void indoorOutbreak(City.Building b, int infected) {
        int people = Math.min(b.capacity, infected + 3 + rnd.nextInt(5));
        for (int i = b.occupants.size(); i < people; i++) {
            Entity e = make(Entity.CIVILIAN, b.doorX, b.doorY, -1, 0);
            e.dead = true;
            e.removed = true;
            b.occupants.add(e);
        }
        for (int i = 0, n = 0; i < b.occupants.size() && n < infected; i++) {
            Entity e = b.occupants.get(i);
            if (e.type != Entity.CIVILIAN || e.infected) continue;
            e.infected = true;
            e.infectTimer = 4 + rnd.nextFloat() * 14;
            n++;
        }
        b.calmTimer = 0;
    }

    /** Once a second: the infection evolves, and after it's over the city slowly recovers. */
    private void cityLife() {
        int z = zombieCount();
        if (z > 0) {
            outbreakTime += 1;
            calmTime = 0;
            if (recovering) {
                recovering = false;
                dispatch.say(Dispatch.WHO_INFO, null, "The infected are back. Recovery efforts suspended.",
                        city.worldW() / 2, city.worldH() / 2);
            }
            int stage = Math.min(5, (int) (outbreakTime / 180));
            if (stage > mutation) {
                mutation = stage;
                String[] lines = {"", "New infections are turning faster and hitting harder.",
                        "Doctors say the virus is changing. The newly infected are tougher.",
                        "The infected are getting stronger and quicker.",
                        "Reports of infected shrugging off gunshots.",
                        "The virus has fully evolved. Expect the worst."};
                dispatch.say(Dispatch.WHO_INFO, null, "Hospital: " + lines[stage] + " (stage " + stage + ")",
                        city.worldW() / 2, city.worldH() / 2);
            }
            survivorPlans();
            return;
        }
        if (peakZombies < 5 || humans == 0) return;
        calmTime += 1;
        if (!recovering && calmTime > 45) {
            recovering = true;
            dispatch.say(Dispatch.WHO_INFO, null, "No infected seen for a while. The city is starting to recover.",
                    city.worldW() / 2, city.worldH() / 2);
        }
        if (recovering) recover();
    }

    /** Clean-up crews, repairs, wrecks towed away, safe zones closing and people going home. */
    private void recover() {
        // Volunteers clear away the bodies.
        int crews = 0;
        for (int i = 0, n = entities.size(); i < n; i++) if (entities.get(i).task == Dispatch.T_CLEANUP) crews++;
        for (int i = 0, n = entities.size(); i < n && crews < 8; i++) {
            Entity e = entities.get(i);
            if (e.type != Entity.CIVILIAN || e.task != Dispatch.T_NONE || e.leader != null || rnd.nextFloat() > 0.2f) continue;
            Corpse c = freshCorpseAny(e.x, e.y, 400);
            if (c == null) break;
            e.task = Dispatch.T_CLEANUP;
            e.threatX = c.x;
            e.threatY = c.y;
            e.postX = time;
            e.taskTimer = 0;
            crews++;
        }
        // Blood fades, buildings are patched up, wrecks towed away.
        for (int i = 0; i < dcount; i++) {
            int a = (dcol[i] >>> 24) & 0xFF;
            if (a > 0 && dkind[i] == D_SPLAT && (dcol[i] & 0xFF0000) > 0x500000) dcol[i] = (Math.max(0, a - 4) << 24) | (dcol[i] & 0xFFFFFF);
        }
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.collapsed) continue;
            b.hp = Math.min(b.maxHp, b.hp + 3);
            if (b.markCount > 0 && rnd.nextFloat() < 0.05f) b.markCount--;
        }
        if (rnd.nextFloat() < 0.07f)
            for (int i = 0; i < fleet.vehicles.size(); i++)
                if (fleet.vehicles.get(i).broken && fleet.vehicles.get(i).riders.isEmpty()) {
                    fleet.vehicles.remove(i);
                    break;
                }
        // After a while the safe zones close and everyone goes home.
        if (calmTime > 120 && !dispatch.zones.isEmpty() && rnd.nextFloat() < 0.04f) dispatch.closeZone(dispatch.zones.get(0));
    }

    private Corpse freshCorpseAny(float x, float y, float radius) {
        Corpse best = null;
        float bd = radius * radius;
        for (int i = 0, n = corpses.size(); i < n; i++) {
            Corpse c = corpses.get(i);
            if (c.rise > 0 || c.age < 0) continue;
            float d = (c.x - x) * (c.x - x) + (c.y - y) * (c.y - y);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        if (best != null) best.age = -1000; // claimed
        return best;
    }

    /** Clean-up crew: walk to a body, spend a moment with it, and it's gone. */
    private boolean cleanup(Entity e, Entity threat) {
        if (e.task != Dispatch.T_CLEANUP) return false;
        if (threat != null) {
            e.task = Dispatch.T_NONE;
            return false;
        }
        float ddx = e.threatX - e.x, ddy = e.threatY - e.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        // Can't get to it: someone else will deal with it later.
        boolean giveUp = time - e.postX > 45;
        if (d > 6 && !giveUp) {
            e.taskTimer = 0;
            if (!e.blocked) steer(e, ddx / d, ddy / d, e.speed);
            else {
                e.blocked = false;
                wander(e, e.speed);
            }
            return true;
        }
        steer(e, 0, 0, 0);
        if (e.taskTimer < -3 || giveUp) {
            for (int i = corpses.size() - 1; i >= 0; i--) {
                Corpse c = corpses.get(i);
                if (Math.abs(c.x - e.threatX) < 1 && Math.abs(c.y - e.threatY) < 1) corpses.remove(i);
            }
            for (int i = 0; i < dcount; i++)
                if ((dx[i] - e.x) * (dx[i] - e.x) + (dy[i] - e.y) * (dy[i] - e.y) < 20 * 20 && dkind[i] == D_SPLAT) dr[i] = 0;
            e.task = Dispatch.T_NONE;
        }
        return true;
    }

    /**
     * During the outbreak survivors make their own plans: families go on supply runs to the shops, and
     * armed civilians band together into patrols.
     */
    private void survivorPlans() {
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.type != Entity.CIVILIAN || e.task != Dispatch.T_NONE || e.leader != null || e.fleeTimer > 0) continue;
            if (!e.hasGun && rnd.nextFloat() < 0.004f && hasFollowers(e)) {
                City.Building shop = lootTarget(e.x, e.y);
                if (shop != null) {
                    e.task = Dispatch.T_LOOT;
                    e.building = shop;
                }
            } else if (e.hasGun && e.ammo + e.reserve > 6 && rnd.nextFloat() < 0.02f) {
                // Other armed people nearby fall in behind them.
                int joined = 0;
                for (int k = 0; k < n && joined < 3; k++) {
                    Entity o = entities.get(k);
                    if (o == e || o.type != Entity.CIVILIAN || !o.hasGun || o.leader != null || o.task != Dispatch.T_NONE) continue;
                    if ((o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y) > 90 * 90) continue;
                    o.leader = e;
                    joined++;
                }
                if (joined >= 1) {
                    e.task = Dispatch.T_PATROL;
                    e.talkTimer = 2;
                }
            }
        }
    }

    private boolean hasFollowers(Entity e) {
        for (int i = 0, n = entities.size(); i < n; i++) if (entities.get(i).leader == e) return true;
        return false;
    }

    /** A supermarket or pharmacy with something left on the shelves. */
    private City.Building lootTarget(float x, float y) {
        City.Building best = null;
        float bd = 800 * 800;
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.collapsed || b.stock <= 0 || (b.kind != City.MARKET && b.kind != City.PHARMACY)) continue;
            float d = (b.doorX - x) * (b.doorX - x) + (b.doorY - y) * (b.doorY - y);
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    /** A supply run: get to the shop, grab what's there, head off again. */
    private boolean supplyRun(Entity e, Entity threat) {
        if (e.task != Dispatch.T_LOOT) return false;
        City.Building b = e.building;
        if (b == null || b.stock <= 0 || b.collapsed || (threat != null && e.fleeTimer > 0)) {
            e.task = Dispatch.T_NONE;
            e.building = null;
            return false;
        }
        float ddx = b.doorX - e.x, ddy = b.doorY - e.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d < 10) {
            // A market's hunting counter arms the group; a pharmacy patches them up.
            for (int i = 0, n = entities.size(); i < n; i++) {
                Entity o = entities.get(i);
                if (o != e && o.leader != e) continue;
                if (b.kind == City.MARKET && !o.hasGun && o.type == Entity.CIVILIAN && b.stock >= 12) {
                    armCivilian(o, 18);
                    b.stock -= 18;
                } else if (b.kind == City.PHARMACY && b.stock > 0) {
                    o.hp = o.maxHp;
                    b.stock--;
                }
            }
            e.task = Dispatch.T_NONE;
            e.building = null;
            e.talkTimer = 2;
            return true;
        }
        walkTo(e, b, ddx, ddy, d, e.speed * 1.4f);
        return true;
    }

    /** A patrol of armed civilians goes looking for trouble nearby. */
    private boolean patrol(Entity e) {
        if (e.task != Dispatch.T_PATROL) return false;
        if (!e.hasGun || e.ammo + e.reserve <= 0 || !hasFollowers(e) || recovering) {
            // Patrol over: everyone goes their own way.
            e.task = Dispatch.T_NONE;
            for (int i = 0, n = entities.size(); i < n; i++)
                if (entities.get(i).leader == e && entities.get(i).hasGun) entities.get(i).leader = null;
            return false;
        }
        if (city.fieldAt(city.zombieDist, e.x, e.y) < 25 && followField(e, city.zombieDist, e.speed * 1.1f)) return true;
        wander(e, e.speed);
        return true;
    }

    // ------------------------------------------------------------------ raiders

    /**
     * Raiders: armed gangs out for themselves. They shoot zombies and anyone in uniform, rob armed
     * civilians of their guns and loot the shops.
     */
    private void thinkRaider(Entity e, float dt) {
        if (e.meleeCd <= 0) {
            Entity z = nearestInReach(e);
            if (z != null) shove(e, z, true);
        }
        Entity z = nearest(e, 110, true, true);
        Entity law = nearestLaw(e, 170);
        Entity t = z;
        if (law != null && (t == null || Math.hypot(law.x - e.x, law.y - e.y) < Math.hypot(t.x - e.x, t.y - e.y) + 40)) t = law;
        if (t != null && (e.ammo > 0 || e.reserve > 0)) {
            float ddx = t.x - e.x, ddy = t.y - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            aimAndFire(e, t, d, 150, dt);
            if (d < 35 || (t.isZombie() && d < 50)) flee(e, -ddx / d, -ddy / d, e.runSpeed * 0.9f);
            else steer(e, 0, 0, 0);
            return;
        }
        e.aiming = false;
        if (e.reload <= 0 && e.ammo < e.magSize / 2 && e.reserve > 0) e.reload = 1.6f;
        if (followLeader(e)) return;
        // The gang heads into town.
        e.noiseTimer -= dt;
        if (e.noiseTimer > 0) {
            float ddx = e.noiseX - e.x, ddy = e.noiseY - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d < 40) e.noiseTimer = 0;
            else if (city.fieldAt(city.humanDist, e.x, e.y) < 3 || !followField(e, city.humanDist, e.speed))
                steer(e, ddx / d, ddy / d, e.speed);
            if (e.blocked) {
                e.blocked = false;
                wander(e, e.speed);
            }
            return;
        }
        // Rob someone with a gun.
        Entity mark = nearestArmedCivilian(e, 100);
        if (mark != null) {
            float ddx = mark.x - e.x, ddy = mark.y - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d < e.radius + mark.radius + 3) {
                e.reserve += mark.ammo + mark.reserve;
                mark.hasGun = false;
                mark.ammo = mark.reserve = 0;
                mark.fleeTimer = 4;
                mark.threatX = e.x;
                mark.threatY = e.y;
                mark.hp -= 8;
                mark.hurt = 1;
                emit(Sfx.THUD, mark.x, mark.y);
            } else steer(e, ddx / d, ddy / d, e.runSpeed);
            return;
        }
        // Loot a shop.
        if (e.task != Dispatch.T_LOOT && rnd.nextFloat() < dt * 0.1f) {
            City.Building shop = lootTarget(e.x, e.y);
            if (shop != null) {
                e.task = Dispatch.T_LOOT;
                e.building = shop;
            }
        }
        if (e.task == Dispatch.T_LOOT) {
            City.Building b = e.building;
            if (b == null || b.stock <= 0 || b.collapsed) {
                e.task = Dispatch.T_NONE;
            } else {
                float ddx = b.doorX - e.x, ddy = b.doorY - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
                if (d < 10) {
                    int take = Math.min(b.stock, 30);
                    b.stock -= take;
                    e.reserve += b.kind == City.MARKET ? take : 0;
                    e.task = Dispatch.T_NONE;
                    e.talkTimer = 2;
                } else walkTo(e, b, ddx, ddy, d, e.speed * 1.3f);
                return;
            }
        }
        wander(e, e.speed * 0.8f);
    }

    private Entity nearestLaw(Entity e, float radius) {
        Entity best = null;
        float bd = radius * radius;
        int cx0 = Math.max(0, (int) ((e.x - radius) / CELL)), cx1 = Math.min(gw - 1, (int) ((e.x + radius) / CELL));
        int cy0 = Math.max(0, (int) ((e.y - radius) / CELL)), cy1 = Math.min(gh - 1, (int) ((e.y + radius) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || !o.isArmed()) continue;
                    float d = (o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y);
                    if (d < bd && city.los(e.x, e.y, o.x, o.y)) {
                        bd = d;
                        best = o;
                    }
                }
            }
        return best;
    }

    private Entity nearestArmedCivilian(Entity e, float radius) {
        Entity best = null;
        float bd = radius * radius;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o.dead || o.type != Entity.CIVILIAN || !o.hasGun) continue;
            float d = (o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y);
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    /** A raider close enough to be frightening. */
    private Entity nearestRaider(Entity e, float radius) {
        Entity best = null;
        float bd = radius * radius;
        int cx0 = Math.max(0, (int) ((e.x - radius) / CELL)), cx1 = Math.min(gw - 1, (int) ((e.x + radius) / CELL));
        int cy0 = Math.max(0, (int) ((e.y - radius) / CELL)), cy1 = Math.min(gh - 1, (int) ((e.y + radius) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || o.type != Entity.RAIDER) continue;
                    float d = (o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y);
                    if (d < bd) {
                        bd = d;
                        best = o;
                    }
                }
            }
        return best;
    }

    /** A gang of raiders comes into town from the nearest edge. */
    void raiderGang(float x, float y, int n) {
        float[] edge = city.edgeSpawn(x, y);
        if (edge == null) edge = new float[]{x, y};
        Entity boss = null;
        for (int i = 0; i < n; i++) {
            Entity r = spawn(Entity.RAIDER, edge[0] + rnd.nextFloat() * 30 - 15, edge[1] + rnd.nextFloat() * 30 - 15);
            if (r == null) continue;
            if (boss == null) boss = r;
            else r.leader = boss;
        }
        if (boss != null) {
            boss.noiseX = x;
            boss.noiseY = y;
            boss.noiseTimer = 90;
            dispatch.say(Dispatch.WHO_POLICE, null, "Police Command: Armed raiders reported entering town near "
                    + city.placeName(edge[0], edge[1]) + ". Units, use caution.", edge[0], edge[1]);
        }
    }

    // ------------------------------------------------------------------ recruitment

    /**
     * After the outbreak has cost the police or army people, they ask civilians to join up and fill the
     * gaps. Each person decides for themselves: some sign up, most don't.
     */
    private void recruitment() {
        peakCops = Math.max(peakCops, counts[Entity.COP]);
        peakSoldiers = Math.max(peakSoldiers, counts[Entity.SOLDIER]);
        if (peakZombies < 5) return;
        policeDrive = drive(Entity.COP, City.FACILITY_POLICE, peakCops, policeDrive);
        armyDrive = drive(Entity.SOLDIER, City.FACILITY_BASE, peakSoldiers, armyDrive);
    }

    private boolean drive(int type, int facilityKind, int peak, boolean active) {
        int enlisting = 0;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.task == Dispatch.T_ENLIST && e.enlistAt != null && e.enlistAt.kind == facilityKind) enlisting++;
        }
        int missing = peak - counts[type] - enlisting;
        if (missing <= 0) return enlisting > 0 && active;
        // Recruiting only happens where it's safe.
        City.Facility office = null;
        for (City.Facility f : city.facilities)
            if (f.kind == facilityKind && countZombiesNear(f.x, f.y, 220) == 0) {
                office = f;
                break;
            }
        if (office == null) return active;
        if (!active) {
            int lost = peak - counts[type];
            if (type == Entity.COP)
                dispatch.say(Dispatch.WHO_POLICE, null, "Police Command: We've lost " + lost + (lost == 1 ? " officer" : " officers")
                        + ". Any civilians willing to serve, report to " + office.name + " to be sworn in.", office.x, office.y);
            else
                dispatch.say(Dispatch.WHO_MILITARY, null, "Military: We're down " + lost + (lost == 1 ? " soldier" : " soldiers")
                        + ". Volunteers wanted at " + office.name + ". We'll train you and give you a rifle.", office.x, office.y);
        }
        // Ask a few people who are nearby and not in danger right now.
        int asks = Math.min(missing, 3) * 2;
        for (int i = 0, n = entities.size(); i < n && asks > 0 && missing > 0; i++) {
            Entity e = entities.get(i);
            if (e.dead || e.type != Entity.CIVILIAN || e.asked || e.infected || e.fleeTimer > 0) continue;
            if (e.task != Dispatch.T_NONE && e.task != Dispatch.T_SEEK) continue;
            if (Math.hypot(e.x - office.x, e.y - office.y) > 900 || city.fieldAt(office.field, e.x, e.y) >= City.FAR) continue;
            asks--;
            e.asked = true;
            if (rnd.nextFloat() < willingness(e)) {
                e.task = Dispatch.T_ENLIST;
                e.enlistAt = office;
                e.taskTimer = 0;
                e.talkTimer = 1.5f;
                missing--;
            } else {
                refused++;
            }
        }
        return true;
    }

    /** How likely someone is to sign up: gun owners are keen, people with family to look after and the hurt much less. */
    private float willingness(Entity e) {
        float p = 0.22f;
        if (e.hasGun) p += 0.35f;
        if (e.leader != null) p -= 0.12f;
        for (int i = 0, n = entities.size(); i < n; i++)
            if (entities.get(i).leader == e && entities.get(i).type == Entity.CIVILIAN) {
                p -= 0.12f;
                break;
            }
        if (e.hp < e.maxHp * 0.6f) p -= 0.1f;
        return Math.max(0.03f, p);
    }

    /** Walks to the precinct or base, trains for a few seconds, then comes out in uniform. */
    private void enlistWalk(Entity e, float dt) {
        City.Facility f = e.enlistAt;
        float ddx = f.x - e.x, ddy = f.y - e.y;
        if (ddx * ddx + ddy * ddy > f.r * f.r * 0.5f) {
            e.taskTimer = 0;
            if (!followField(e, f.field, e.speed * 1.4f)) {
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
                steer(e, ddx / d, ddy / d, e.speed * 1.4f);
            }
            return;
        }
        // Training.
        wander(e, e.speed * 0.3f);
        if (e.taskTimer > -6) return;
        int type = f.kind == City.FACILITY_BASE ? Entity.SOLDIER : Entity.COP;
        Entity r = make(type, e.x, e.y, -1, 0);
        r.skin = e.skin;
        r.angle = e.angle;
        e.dead = true;
        e.removed = true;
        for (int i = 0, n = entities.size(); i < n; i++) if (entities.get(i).leader == e) entities.get(i).leader = null;
        newRecruits.add(r);
        recruits++;
        String where = f.name;
        // Keep the radio readable when lots join at once.
        if (time - lastRecruitSay < 8) return;
        lastRecruitSay = time;
        if (type == Entity.COP)
            dispatch.say(Dispatch.WHO_POLICE, r, "New recruit, sworn in at " + where + ". Ready for duty.", r.x, r.y);
        else
            dispatch.say(Dispatch.WHO_MILITARY, r, "Volunteer reporting in at " + where + ", trained and armed.", r.x, r.y);
    }

    /** Word spreads: people near someone who spots a zombie start running too. */
    private void alarm(Entity e, Entity threat) {
        int cx0 = Math.max(0, (int) ((e.x - 45) / CELL)), cx1 = Math.min(gw - 1, (int) ((e.x + 45) / CELL));
        int cy0 = Math.max(0, (int) ((e.y - 45) / CELL)), cy1 = Math.min(gh - 1, (int) ((e.y + 45) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o == e || o.dead || o.fleeTimer > 0 || (o.type != Entity.CIVILIAN && o.type != Entity.DOG)) continue;
                    if ((o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y) > 45 * 45) continue;
                    o.fleeTimer = 1.8f;
                    o.threatX = threat.x;
                    o.threatY = threat.y;
                }
            }
    }

    private Entity nearestArmed(Entity e, float radius) {
        Entity best = null;
        float bd = radius * radius;
        int cx0 = Math.max(0, (int) ((e.x - radius) / CELL)), cx1 = Math.min(gw - 1, (int) ((e.x + radius) / CELL));
        int cy0 = Math.max(0, (int) ((e.y - radius) / CELL)), cy1 = Math.min(gh - 1, (int) ((e.y + radius) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || !o.isArmed() || (o.ammo <= 0 && o.reserve <= 0)) continue;
                    float d = (o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y);
                    if (d < bd) {
                        bd = d;
                        best = o;
                    }
                }
            }
        return best;
    }

    /** Running to a car that stopped for them, and climbing in. Returns true while that is happening. */
    private boolean boardRide(Entity e) {
        Fleet.Vehicle v = e.ride;
        if (v == null) return false;
        if (v.broken || v.parked || !fleet.vehicles.contains(v) || v.riders.size() >= Fleet.SEATS) {
            e.ride = null;
            if (e.task == Dispatch.T_RIDE) e.task = Dispatch.T_NONE;
            return false;
        }
        float ddx = v.x - e.x, ddy = v.y - e.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d > 150) {
            e.ride = null;
            if (e.task == Dispatch.T_RIDE) e.task = Dispatch.T_NONE;
            return false;
        }
        if (d < v.length() + 5 && v.speed < 20) {
            e.dead = true;
            e.removed = true;
            fleet.boarded(v, e);
            return true;
        }
        v.waitTimer = Math.max(v.waitTimer, 1.5f);
        steer(e, ddx / d, ddy / d, e.runSpeed);
        return true;
    }

    /** An armed civilian with no bullets left walks to the nearest supermarket that still has some. */
    private boolean marketRun(Entity e, Entity threat) {
        if (e.task == Dispatch.T_NONE && threat == null && e.ammo + e.reserve <= 2 && e.taskTimer <= 0) {
            e.taskTimer = 3;
            City.Building best = null;
            float bd = 1200 * 1200;
            for (int i = 0, n = city.buildings.size(); i < n; i++) {
                City.Building b = city.buildings.get(i);
                if (b.kind != City.MARKET || b.collapsed || b.stock <= 0) continue;
                float d = (b.doorX - e.x) * (b.doorX - e.x) + (b.doorY - e.y) * (b.doorY - e.y);
                if (d < bd) {
                    bd = d;
                    best = b;
                }
            }
            if (best != null) {
                e.task = Dispatch.T_RESUPPLY;
                e.building = best;
            }
        }
        if (e.task != Dispatch.T_RESUPPLY) return false;
        City.Building b = e.building;
        if (b == null || b.stock <= 0 || b.collapsed || (threat != null && e.ammo > 0)) {
            e.task = Dispatch.T_NONE;
            e.building = null;
            return false;
        }
        float ddx = b.doorX - e.x, ddy = b.doorY - e.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d < 10) {
            int take = Math.min(36, b.stock);
            b.stock -= take;
            e.reserve += take;
            e.reload = 1.5f;
            e.task = Dispatch.T_NONE;
            e.building = null;
            e.talkTimer = 2;
            return true;
        }
        if (b.field == null) {
            b.field = new int[city.w * city.h];
            city.fieldFromPoints(b.field, new float[]{b.doorX}, new float[]{b.doorY}, 1);
        }
        if (!followField(e, b.field, e.speed * 1.4f)) steer(e, ddx / d, ddy / d, e.speed * 1.4f);
        return true;
    }

    /** Dogs stay by their owner, bark at zombies and go for them if their owner is in danger. */
    private void thinkDog(Entity e, float dt) {
        if (boardRide(e)) return;
        Entity threat = nearest(e, 80, true, true);
        Entity owner = e.leader;
        boolean hasOwner = owner != null && !owner.dead;
        if (threat != null) {
            float ddx = threat.x - e.x, ddy = threat.y - e.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (rnd.nextFloat() < dt * 2.5f) emit(Sfx.BARK, e.x, e.y);
            boolean guarding = hasOwner && Math.hypot(owner.x - threat.x, owner.y - threat.y) < 50;
            // Police dogs go in hard.
            boolean k9 = e.role == Entity.ROLE_K9 && hasOwner;
            if (e.hp > e.maxHp * (k9 ? 0.25f : 0.4f) && (guarding || d < 20 || (k9 && d < 70))) {
                steer(e, ddx / d, ddy / d, e.runSpeed);
                if (d < e.radius + threat.radius + 2.5f && e.meleeCd <= 0) {
                    e.meleeCd = 0.7f;
                    threat.hp -= 6;
                    threat.hurt = 1;
                    threat.stun = Math.max(threat.stun, threat.type == Entity.BRUTE ? 0.1f : 0.5f);
                    tryMove(threat, ddx / d * 3 / threat.mass, ddy / d * 3 / threat.mass);
                    emit(Sfx.BARK, e.x, e.y);
                }
                return;
            }
            flee(e, -ddx / d, -ddy / d, e.runSpeed);
            return;
        }
        if (hasOwner) {
            float ddx = owner.x - e.x, ddy = owner.y - e.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d > 14) {
                steer(e, ddx / d, ddy / d, d > 40 ? e.runSpeed : Math.max(e.speed * 1.3f, owner.want * 1.1f));
                return;
            }
            if (owner.want < 1) {
                wander(e, e.speed * 0.3f);
                return;
            }
            steer(e, owner.mx, owner.my, owner.want);
            return;
        }
        wander(e, e.speed * 0.6f);
    }

    private final ArrayList<City.Building> doorPaths = new ArrayList<City.Building>();

    /**
     * Walks to a building's door around the building (not into its wall): a path field to the door is
     * made the first time anyone needs it. Returns false if it had to fall back to walking straight.
     */
    private boolean walkTo(Entity e, City.Building b, float ddx, float ddy, float d, float speed) {
        if (d < 20 && city.los(e.x, e.y, b.doorX, b.doorY) && !e.blocked) {
            steer(e, ddx / d, ddy / d, speed);
            return true;
        }
        if (b.field == null) {
            // Keep a limited number of these around.
            if (doorPaths.size() >= 60) doorPaths.remove(0).field = null;
            b.field = new int[city.w * city.h];
            city.fieldFromPoints(b.field, new float[]{b.doorX}, new float[]{b.doorY}, 1);
            doorPaths.add(b);
        }
        e.blocked = false;
        if (followField(e, b.field, speed)) return true;
        steer(e, ddx / d, ddy / d, speed);
        return false;
    }

    /** A pharmacy with stock left, if one is closer than the hospital. */
    private City.Building pharmacyNear(Entity e, float maxDist) {
        City.Building best = null;
        float bd = maxDist * maxDist;
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.kind != City.PHARMACY || b.collapsed || b.stock <= 0) continue;
            float d = (b.doorX - e.x) * (b.doorX - e.x) + (b.doorY - e.y) * (b.doorY - e.y);
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    /** Hurt people walk to the hospital when nothing is chasing them, and heal there. */
    private boolean healSeeking(Entity e, Entity threat, float dt) {
        City.Facility hospital = city.nearestFacility(City.FACILITY_HOSPITAL, e.x, e.y);
        boolean needs = e.hp < e.maxHp * 0.45f || (e.infected && e.infectTimer > 6 && !e.cureTried);
        if (e.task == Dispatch.T_NONE && needs && threat == null) e.task = Dispatch.T_HEAL;
        if (e.task != Dispatch.T_HEAL) return false;
        if (threat != null && e.fleeTimer > 0) {
            e.task = Dispatch.T_NONE;
            return false;
        }
        // A pharmacy on the way patches people up (and sometimes stops an infection).
        float hd = hospital == null ? Float.MAX_VALUE : (float) Math.hypot(hospital.x - e.x, hospital.y - e.y);
        City.Building ph = pharmacyNear(e, Math.min(hd, 700));
        if (ph != null) {
            float ddx = ph.doorX - e.x, ddy = ph.doorY - e.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d < 10) {
                ph.stock--;
                e.hp = Math.min(e.maxHp, e.hp + e.maxHp * 0.5f);
                if (e.infected && !e.cureTried) tryCure(e, 0.3f);
                e.task = Dispatch.T_NONE;
                e.talkTimer = 1.5f;
                for (int k = 0; k < 6; k++)
                    particle(e.x + rnd.nextFloat() * 6 - 3, e.y - 4, 0, -12, 0.6f, 1.2f, 0xFF63E06B, P_DOT);
                return true;
            }
            walkTo(e, ph, ddx, ddy, d, e.speed * 1.3f);
            return true;
        }
        if (hospital == null) {
            e.task = Dispatch.T_NONE;
            return false;
        }
        float ddx = hospital.x - e.x, ddy = hospital.y - e.y;
        if (ddx * ddx + ddy * ddy < hospital.r * hospital.r) {
            e.hp = Math.min(e.maxHp, e.hp + 8 * dt);
            if (e.infected && !e.cureTried) tryCure(e, 0.5f);
            if (e.hp >= e.maxHp && !e.infected) e.task = Dispatch.T_NONE;
            wander(e, e.speed * 0.3f);
            return true;
        }
        if (!followField(e, hospital.field, e.speed)) e.task = Dispatch.T_NONE;
        return true;
    }

    private void tryCure(Entity e, float chance) {
        e.cureTried = true;
        if (e.infectTimer > 4 && rnd.nextFloat() < chance) {
            e.infected = false;
            cured++;
        }
    }

    /** Medics run to the hurt and the infected, patch them up, and keep away from zombies. */
    private void thinkMedic(Entity e, float dt) {
        Entity threat = nearest(e, 60, true, true);
        if (e.meleeCd <= 0) {
            Entity z = nearestInReach(e);
            if (z != null) shove(e, z, false);
        }
        if (threat != null) {
            e.fleeTimer = 2;
            e.threatX = threat.x;
            e.threatY = threat.y;
        }
        if (e.fleeTimer > 0) {
            float ax = e.x - e.threatX, ay = e.y - e.threatY;
            float d = (float) Math.sqrt(ax * ax + ay * ay) + 0.001f;
            flee(e, ax / d, ay / d, e.runSpeed);
            return;
        }
        Entity patient = null;
        float best = 320 * 320;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o == e || o.dead || o.isZombie()) continue;
            boolean needs = o.hp < o.maxHp * 0.8f || (o.infected && !o.cureTried);
            if (!needs) continue;
            float ddx = o.x - e.x, ddy = o.y - e.y, d2 = ddx * ddx + ddy * ddy;
            if (d2 < best) {
                best = d2;
                patient = o;
            }
        }
        if (patient != null) {
            float ddx = patient.x - e.x, ddy = patient.y - e.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d < e.radius + patient.radius + 6) {
                steer(e, 0, 0, 0);
                e.angle = (float) Math.atan2(ddy, ddx);
                patient.hp = Math.min(patient.maxHp, patient.hp + 14 * dt);
                if (patient.infected && !patient.cureTried) tryCure(patient, 0.6f);
                if (rnd.nextFloat() < dt * 6)
                    particle(patient.x + rnd.nextFloat() * 6 - 3, patient.y - 4, 0, -12, 0.6f, 1.2f, 0xFF63E06B, P_DOT);
                return;
            }
            steer(e, ddx / d, ddy / d, e.runSpeed * 0.8f);
            return;
        }
        City.Facility hospital = city.nearestFacility(City.FACILITY_HOSPITAL, e.x, e.y);
        if (hospital != null) {
            float ddx = hospital.x - e.x, ddy = hospital.y - e.y;
            if (ddx * ddx + ddy * ddy > hospital.r * hospital.r * 4 && followField(e, hospital.field, e.speed)) return;
        }
        wander(e, e.speed * 0.6f);
    }

    private void thinkArmed(Entity e, float dt) {
        boolean soldier = e.type == Entity.SOLDIER;
        float range = soldier ? (e.role == Entity.ROLE_SNIPER ? 380 : e.role == Entity.ROLE_COMMANDER ? 180 : 230) : 160;
        if (e.role == Entity.ROLE_COMMANDER && soldier) command(e, dt);
        if (e.meleeCd <= 0) {
            Entity z = nearestInReach(e);
            if (z != null) shove(e, z, true);
        }
        boolean dry = e.ammo <= 0 && e.reserve <= 0 && e.reload <= 0;
        // Out of ammo: head back to the precinct or base to resupply.
        if (dry && e.task != Dispatch.T_RESUPPLY) {
            City.Facility f = supplyPoint(e);
            if (f != null && (f.x - e.x) * (f.x - e.x) + (f.y - e.y) * (f.y - e.y) < f.r * f.r * 2) {
                // Already at the armoury (guards on a post): restock on the spot.
                e.reserve = fullReserve(e);
                e.reload = 2;
                dry = false;
            }
        }
        if (dry && e.task != Dispatch.T_RESUPPLY) {
            City.Facility f = supplyPoint(e);
            if (!e.outOfAmmoSaid) {
                e.outOfAmmoSaid = true;
                dispatch.say(soldier ? Dispatch.WHO_MILITARY : Dispatch.WHO_POLICE, e, "I'm out of ammo!"
                        + (f != null ? " Heading back to " + f.name + " to resupply." : " Nothing left but my hands."), e.x, e.y);
            }
            if (f != null) {
                dispatch.releaseForResupply(e);
                e.task = Dispatch.T_RESUPPLY;
            }
        }
        if (e.task == Dispatch.T_RESUPPLY) {
            City.Facility f = supplyPoint(e);
            if (f == null) {
                e.task = Dispatch.T_NONE;
            } else {
                float ddx = f.x - e.x, ddy = f.y - e.y;
                if (ddx * ddx + ddy * ddy < f.r * f.r) {
                    e.reserve = fullReserve(e);
                    e.ammo = e.magSize;
                    e.task = Dispatch.T_NONE;
                    e.outOfAmmoSaid = false;
                } else {
                    Entity t = nearest(e, 50, true, false);
                    if (t != null) {
                        float tx = e.x - t.x, ty = e.y - t.y, d = (float) Math.sqrt(tx * tx + ty * ty) + 0.001f;
                        flee(e, tx / d, ty / d, e.runSpeed);
                    } else if (!followField(e, f.field, e.runSpeed)) {
                        e.task = Dispatch.T_NONE;
                    }
                    return;
                }
            }
        }
        // A supply crate tops up anyone passing.
        if (e.reserve < fullReserve(e) && !pickups.isEmpty() && rnd.nextFloat() < dt * 2)
            for (int i = 0; i < pickups.size(); i++) {
                Pickup p = pickups.get(i);
                if (p.uses > 0 && p.drop <= 0 && (p.x - e.x) * (p.x - e.x) + (p.y - e.y) * (p.y - e.y) < 30 * 30)
                    e.reserve = fullReserve(e);
            }
        // Standing at a supply point quietly tops up spare ammo.
        if (e.reserve < fullReserve(e) && rnd.nextFloat() < dt) {
            City.Facility f = supplyPoint(e);
            if (f != null && (f.x - e.x) * (f.x - e.x) + (f.y - e.y) * (f.y - e.y) < f.r * f.r * 1.5f)
                e.reserve = Math.min(fullReserve(e), e.reserve + e.magSize);
        }

        Entity t = e.ammo > 0 || e.reserve > 0 ? pickTarget(e, range) : null;
        if (t != null && t.isZombie()) {
            // Remember where it was, to go and look if it slips out of sight.
            e.threatX = t.x;
            e.threatY = t.y;
            e.fear = 20;
        }
        boolean stationary = e.task == Dispatch.T_POST || e.task == Dispatch.T_HOLD;
        if (t != null && e.task != Dispatch.T_MOVE) {
            float ddx = t.x - e.x, ddy = t.y - e.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            float keep = soldier ? (e.role == Entity.ROLE_COMMANDER ? 90 : e.role == Entity.ROLE_SNIPER ? 60 : 40) : 50;
            Dispatch.SafeZone guarding = e.task == Dispatch.T_GUARD ? e.zone : null;
            boolean swarmed = countZombiesNear(e.x, e.y, 40) >= 3;
            if (stationary) standAt(e, e.postX, e.postY, 0.5f);
            else if ((d < keep || (e.reload > 0 && d < 70) || swarmed) && (guarding == null || inside(e, guarding, 1f)))
                // Too close, reloading or outnumbered: fall back while shooting.
                flee(e, -ddx / d, -ddy / d, e.runSpeed * (swarmed ? 0.95f : 0.8f));
            else if (guarding != null) holdPost(e, guarding, 0.5f);
            else steer(e, 0, 0, 0);
            if (!aimAndFire(e, t, d, range, dt) && !stationary && guarding == null) {
                // Someone in the line of fire: step sideways for a clear shot.
                float side = (e.callsign + e.member) % 2 == 0 ? 1 : -1;
                steer(e, -ddy / d * side, ddx / d * side, e.speed);
            }
            if (soldier && e.role == Entity.ROLE_RIFLE && e.grenades > 0 && e.grenadeCd <= 0 && d > 70 && d < 200
                    && countZombiesNear(t.x, t.y, 40) >= 5 && !peopleNear(t.x, t.y, 60)) {
                throwGrenade(e, t.x, t.y);
            }
            return;
        }
        e.aiming = false;
        if (e.reload <= 0 && e.ammo < e.magSize / 2 && e.reserve > 0) e.reload = soldier ? 2.2f : 1.6f;
        if (stationary) {
            standAt(e, e.postX, e.postY, 1f);
            return;
        }
        if (e.task == Dispatch.T_MOVE) {
            float ddx = e.postX - e.x, ddy = e.postY - e.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d < 20) {
                e.task = Dispatch.T_HOLD;
                e.orderField = null;
            } else if (e.orderField == null || !followField(e, e.orderField, e.runSpeed)) {
                steer(e, ddx / d, ddy / d, e.runSpeed);
            }
            return;
        }
        if (e.task == Dispatch.T_GUARD && e.zone != null) {
            holdPost(e, e.zone, 1f);
            return;
        }
        if (e.task == Dispatch.T_RESPOND && e.incident != null) {
            Dispatch.Incident inc = e.incident;
            float ddx = inc.x - e.x, ddy = inc.y - e.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d > 40) {
                if (!followField(e, inc.field, e.runSpeed * 0.95f)) steer(e, ddx / d, ddy / d, e.runSpeed * 0.95f);
                return;
            }
            if (!e.onScene) {
                e.onScene = true;
                dispatch.onScene(e, inc);
            }
            if (city.fieldAt(city.zombieDist, e.x, e.y) < 10 && followField(e, city.zombieDist, e.speed * 1.2f)) return;
            wander(e, e.speed * 0.5f);
            return;
        }
        // Soldiers keep their squad together.
        if (soldier && e.task == Dispatch.T_NONE) {
            if (e.taskTimer <= 0) {
                e.taskTimer = 2;
                e.leader = e.role == Entity.ROLE_COMMANDER ? biggestGroup(e) : squadLead(e);
            }
            Entity lead = e.leader;
            if (lead != null && !lead.dead) {
                float lx = lead.x - e.x, ly = lead.y - e.y, ld = (float) Math.sqrt(lx * lx + ly * ly) + 0.001f;
                if (ld > 80) {
                    steer(e, lx / ld, ly / ld, e.runSpeed);
                    return;
                }
            }
        }
        // Go and check where the last one was seen.
        if (e.task == Dispatch.T_NONE && e.fear > 0 && (e.ammo > 0 || e.reserve > 0)) {
            float lx = e.threatX - e.x, ly = e.threatY - e.y, ld = (float) Math.sqrt(lx * lx + ly * ly) + 0.001f;
            if (ld < 20) e.fear = 0;
            else {
                steer(e, lx / ld, ly / ld, e.speed * 1.2f);
                return;
            }
        }
        int dist = city.fieldAt(city.zombieDist, e.x, e.y);
        int hunt = soldier ? 200 : 40;
        if ((e.ammo > 0 || e.reserve > 0) && dist < hunt && followField(e, city.zombieDist, e.speed * 1.3f)) return;
        if (soldier) {
            // With nothing to fight, soldiers drift back to base.
            City.Facility base = city.nearestFacility(City.FACILITY_BASE, e.x, e.y);
            if (base != null) {
                float bx = base.x - e.x, by = base.y - e.y;
                if (bx * bx + by * by > base.r * base.r * 2.2f && followField(e, base.field, e.speed * 0.8f)) return;
            }
        }
        wander(e, e.speed * 0.6f);
    }

    /** Where this unit can get more ammo: the base for soldiers, a precinct for cops (or either). */
    private City.Facility supplyPoint(Entity e) {
        City.Facility f = city.nearestFacility(e.type == Entity.SOLDIER ? City.FACILITY_BASE : City.FACILITY_POLICE, e.x, e.y);
        if (f == null) f = city.nearestFacility(e.type == Entity.SOLDIER ? City.FACILITY_POLICE : City.FACILITY_BASE, e.x, e.y);
        return f;
    }

    /** Stands on a spot (a guard post or an ordered position). */
    private void standAt(Entity e, float x, float y, float speedFactor) {
        float ddx = x - e.x, ddy = y - e.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d > 5) steer(e, ddx / d, ddy / d, (d > 30 ? e.runSpeed : e.speed) * speedFactor);
        else steer(e, 0, 0, 0);
    }

    /** Aims and shoots. Returns false if the shot is blocked by someone standing in the way. */
    private boolean aimAndFire(Entity e, Entity t, float d, float range, float dt) {
        e.aiming = true;
        e.angle = turn(e.angle, (float) Math.atan2(t.y - e.y, t.x - e.x), dt * 14);
        if (e.ammo <= 0) {
            if (e.reload <= 0 && e.reserve > 0) e.reload = e.type == Entity.SOLDIER ? (e.role == Entity.ROLE_GUNNER ? 4f : 2.2f) : 1.6f;
            return true;
        }
        if (e.reload <= 0 && e.cooldown <= 0) {
            if (friendInLine(e, t, d)) {
                e.cooldown = 0.15f;
                return false;
            }
            fire(e, t, d, range);
        }
        return true;
    }

    /**
     * The zombie most worth shooting: close ones first, but ones attacking someone, runners and screamers
     * jump the queue, and crawlers wait. Only targets in sight count.
     */
    private Entity pickTarget(Entity e, float range) {
        float best = Float.MAX_VALUE;
        Entity res = null;
        int cx0 = Math.max(0, (int) ((e.x - range) / CELL)), cx1 = Math.min(gw - 1, (int) ((e.x + range) / CELL));
        int cy0 = Math.max(0, (int) ((e.y - range) / CELL)), cy1 = Math.min(gh - 1, (int) ((e.y + range) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || !(o.isZombie() || (o.type == Entity.RAIDER && e.type != Entity.RAIDER))) continue;
                    float ddx = o.x - e.x, ddy = o.y - e.y, d2 = ddx * ddx + ddy * ddy;
                    if (d2 > range * range) continue;
                    float score = (float) Math.sqrt(d2);
                    if (o.type == Entity.RAIDER) score += 15;
                    // Don't pop a bloater right next to people.
                    if (o.type == Entity.BLOATER && peopleNear(o.x, o.y, 38)) continue;
                    if (o.biteCd > 0) score -= 60;
                    if (o.type == Entity.RUNNER) score -= 25;
                    else if (o.type == Entity.SCREAMER) score -= 35;
                    else if (o.type == Entity.CRAWLER) score += 20;
                    if (score < best && city.los(e.x, e.y, o.x, o.y)) {
                        best = score;
                        res = o;
                    }
                }
            }
        return res;
    }

    /** Is a person (not the target) standing between the shooter and the target? */
    private boolean friendInLine(Entity e, Entity t, float d) {
        float nx = (t.x - e.x) / d, ny = (t.y - e.y) / d;
        int cx0 = Math.max(0, (int) ((Math.min(e.x, t.x) - 4) / CELL)), cx1 = Math.min(gw - 1, (int) ((Math.max(e.x, t.x) + 4) / CELL));
        int cy0 = Math.max(0, (int) ((Math.min(e.y, t.y) - 4) / CELL)), cy1 = Math.min(gh - 1, (int) ((Math.max(e.y, t.y) + 4) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o == e || o == t || o.dead || o.isZombie()) continue;
                    float ox = o.x - e.x, oy = o.y - e.y;
                    float along = ox * nx + oy * ny;
                    if (along < 4 || along > d - 3) continue;
                    if (Math.abs(ox * -ny + oy * nx) < o.radius + 1.2f) return true;
                }
            }
        return false;
    }

    /** Anyone who isn't a zombie within a radius (so soldiers don't grenade their own side). */
    boolean peopleNear(float x, float y, float radius) {
        int cx0 = Math.max(0, (int) ((x - radius) / CELL)), cx1 = Math.min(gw - 1, (int) ((x + radius) / CELL));
        int cy0 = Math.max(0, (int) ((y - radius) / CELL)), cy1 = Math.min(gh - 1, (int) ((y + radius) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || o.isZombie()) continue;
                    if ((o.x - x) * (o.x - x) + (o.y - y) * (o.y - y) < radius * radius) return true;
                }
            }
        return false;
    }

    /** The commander stays with the soldier who has the most of the army around them. */
    private Entity biggestGroup(Entity e) {
        Entity best = null;
        int most = 0;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o == e || o.dead || o.type != Entity.SOLDIER || o.member != 1) continue;
            int near = 0;
            for (int k = 0; k < n; k++) {
                Entity q = entities.get(k);
                if (q.type == Entity.SOLDIER && Math.abs(q.x - o.x) < 120 && Math.abs(q.y - o.y) < 120) near++;
            }
            if (near > most) {
                most = near;
                best = o;
            }
        }
        return best;
    }

    /** The commander calls in armour or air support when the fighting around them gets heavy. */
    private void command(Entity e, float dt) {
        e.screamCd -= dt;
        if (e.screamCd > 0) return;
        e.screamCd = 3;
        int near = countZombiesNear(e.x, e.y, 220);
        if (near < 12) return;
        e.screamCd = 45;
        dispatch.heavyContact(e, e.x, e.y, near);
    }

    /** The squad member a soldier sticks with: the lowest-numbered one still standing. */
    private Entity squadLead(Entity e) {
        Entity best = null;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o == e || o.dead || o.type != Entity.SOLDIER || o.squad != e.squad || o.member >= e.member) continue;
            if (o.task != Dispatch.T_NONE) continue;
            if (best == null || o.member < best.member) best = o;
        }
        return best;
    }

    private void fire(Entity e, Entity t, float d, float range) {
        boolean soldier = e.type == Entity.SOLDIER;
        e.ammo--;
        shotsFired++;
        float dmg, accuracy = e.hasGun ? 0.5f : 0.7f;
        if (soldier && e.role == Entity.ROLE_SNIPER) {
            e.cooldown = 1.6f;
            dmg = 75;
            accuracy = 0.97f;
        } else if (soldier && e.role == Entity.ROLE_GUNNER) {
            e.cooldown = 0.075f;
            dmg = 10;
            accuracy = 0.55f;
        } else if (soldier && e.role == Entity.ROLE_COMMANDER) {
            e.cooldown = 0.5f;
            dmg = 12;
        } else if (soldier) {
            e.burst++;
            e.cooldown = e.burst % 3 == 0 ? 0.45f : 0.1f;
            dmg = 16;
        } else {
            e.cooldown = e.hasGun ? 0.8f : 0.55f;
            dmg = 10;
        }
        // Soldiers near their commander fight better.
        if (soldier && e.role != Entity.ROLE_COMMANDER && !commanders.isEmpty() && commanded(e)) accuracy += 0.12f;
        if (e.ammo <= 0 && e.reserve > 0) e.reload = soldier ? (e.role == Entity.ROLE_GUNNER ? 4f : 2.2f) : 1.6f;
        if (rnd.nextFloat() < (e.role == Entity.ROLE_GUNNER ? 0.08f : 0.25f)) noise(e.x, e.y, e.role == Entity.ROLE_SNIPER ? 300 : 220);
        // A spent case flies out of the side of the gun.
        float ea = e.angle + 1.57f + (rnd.nextFloat() - 0.5f) * 0.6f;
        if (rnd.nextFloat() < 0.7f)
            particle(e.x, e.y, (float) Math.cos(ea) * (25 + rnd.nextFloat() * 20), (float) Math.sin(ea) * (25 + rnd.nextFloat() * 20),
                    2.5f, 0.45f, 0xFFD9B44A, P_CASING);
        float ca = (float) Math.cos(e.angle), sa = (float) Math.sin(e.angle);
        float mx = e.x + ca * e.radius * (soldier ? 2.5f : 2f) - sa * e.radius * 0.3f;
        float my = e.y + sa * e.radius * (soldier ? 2.5f : 2f) + ca * e.radius * 0.3f;
        float hit = accuracy * (1 - (e.role == Entity.ROLE_SNIPER ? 0.1f : 0.4f) * d / range) + (t.type == Entity.BRUTE ? 0.1f : 0);
        if (t.type == Entity.CRAWLER) hit *= 0.6f;
        if (rnd.nextFloat() < hit) {
            t.hp -= dmg;
            t.hurt = 1;
            if (t.hp <= 0 && t.isZombie()) e.kills++;
            if (t.type != Entity.RAIDER) t.killedByZombie = false;
            float nx = (t.x - e.x) / d, ny = (t.y - e.y) / d;
            bloodBurst(t.x, t.y, 4, nx, ny);
            tracer(mx, my, t.x + rnd.nextFloat() * 2 - 1, t.y + rnd.nextFloat() * 2 - 1);
            tryMove(t, nx * 1.2f / t.mass, ny * 1.2f / t.mass);
        } else {
            float a = (float) Math.atan2(t.y - my, t.x - mx) + (rnd.nextBoolean() ? 1 : -1) * (0.05f + rnd.nextFloat() * 0.1f);
            float len = d + 20 + rnd.nextFloat() * 40;
            float ex = mx, ey = my;
            Fleet.Vehicle car = null;
            for (float s = 0; s < len && car == null; s += 4) {
                ex = mx + (float) Math.cos(a) * s;
                ey = my + (float) Math.sin(a) * s;
                if (city.solidAt(ex, ey)) {
                    for (int i = 0; i < 3; i++)
                        particle(ex, ey, rnd.nextFloat() * 60 - 30, rnd.nextFloat() * 60 - 30, 0.2f, 0.7f, 0xFFFFD27A, P_DOT);
                    bulletHole(ex, ey, e.x, e.y);
                    break;
                }
                if (s > 12 && ((int) s) % 8 == 0) car = vehicleAt(ex, ey);
            }
            // Stray rounds put holes in cars too.
            if (car != null) fleet.damage(car, dmg * 0.4f, false);
            tracer(mx, my, ex, ey);
        }
        particle(mx, my, 0, 0, 0.06f, soldier ? 3.2f : 2.6f, 0xFFFFE9A0, P_FLASH);
        emit(!soldier || e.role == Entity.ROLE_COMMANDER ? Sfx.PISTOL : e.role == Entity.ROLE_SNIPER ? Sfx.SNIPER
                : e.role == Entity.ROLE_GUNNER ? Sfx.MG : Sfx.RIFLE, e.x, e.y);
    }

    // ------------------------------------------------------------------ hiding indoors, pickups

    /** A building with room whose door is close and not swarmed. */
    private City.Building shelterNear(Entity e) {
        City.Building best = null;
        float bd = 80 * 80;
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.capacity == 0 || b.occupants.size() >= b.capacity || b.barricade < 40) continue;
            float ddx = b.doorX - e.x, ddy = b.doorY - e.y, d2 = ddx * ddx + ddy * ddy;
            if (d2 < bd && countZombiesNear(b.doorX, b.doorY, 35) == 0) {
                bd = d2;
                best = b;
            }
        }
        return best;
    }

    private void enterBuilding(Entity e, City.Building b) {
        e.task = Dispatch.T_NONE;
        e.building = null;
        e.dead = true;
        e.removed = true;
        b.occupants.add(e);
        b.calmTimer = 0;
        // The family follows them in.
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o.dead || o.isZombie() || o.isArmed()) continue;
            if (o.leader == e || (e.leader != null && (o == e.leader || o.leader == e.leader))) {
                if (o.type == Entity.DOG) {
                    // Dogs just come along.
                    if (Math.hypot(o.x - b.doorX, o.y - b.doorY) < 40 && b.occupants.size() < b.capacity + 2) {
                        o.dead = true;
                        o.removed = true;
                        b.occupants.add(o);
                    }
                } else if (o.task == Dispatch.T_NONE || o.task == Dispatch.T_SEEK) {
                    o.task = Dispatch.T_HIDE;
                    o.building = b;
                }
            }
        }
    }

    /** Barricades wear down under attack; when they break everyone inside bolts. Calm lets people out. */
    private void updateBuildings(float dt) {
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.occupants.isEmpty()) {
                b.barricade = Math.min(100, b.barricade + dt * 4);
                continue;
            }
            // Someone infected turns inside: the building is lost.
            boolean turnedInside = false;
            for (int k = 0; k < b.occupants.size(); k++) {
                Entity o = b.occupants.get(k);
                if (o.infected) {
                    o.infectTimer -= dt;
                    if (o.infectTimer <= 0) turnedInside = true;
                }
            }
            if (b.barricade <= 0 || turnedInside) {
                if (turnedInside) spawn(Entity.ZOMBIE, b.doorX, b.doorY);
                dispatch.say(Dispatch.WHO_INFO, null, "Zombies broke into a building on " + city.placeName(b.doorX, b.doorY)
                        + "! " + b.occupants.size() + (b.occupants.size() == 1 ? " person" : " people") + " fleeing.", b.doorX, b.doorY);
                while (!b.occupants.isEmpty()) {
                    Entity o = b.occupants.remove(b.occupants.size() - 1);
                    if (o.infected && o.infectTimer <= 0) continue;
                    leaveBuilding(o, b, true);
                }
                b.barricade = 0;
                continue;
            }
            // Everyone inside helps shore up the door: more people, a stronger barricade.
            if (countZombiesNear(b.doorX, b.doorY, 60) == 0)
                b.barricade = Math.min(100, b.barricade + dt * (2 + b.occupants.size() * 0.5f));
            else if (b.occupants.size() >= 4) b.barricade = Math.min(100, b.barricade + dt * b.occupants.size() * 0.25f);
            // Come out once the street has been quiet for a while.
            b.calmTimer += dt;
            if (countZombiesNear(b.doorX, b.doorY, 250) > 0) b.calmTimer = 0;
            if (b.calmTimer > 25) {
                b.releaseTimer -= dt;
                if (b.releaseTimer <= 0) {
                    b.releaseTimer = 1.5f;
                    leaveBuilding(b.occupants.remove(b.occupants.size() - 1), b, false);
                }
            }
        }
    }

    private void leaveBuilding(Entity o, City.Building b, boolean panic) {
        if (entities.size() >= maxEntities) return;
        float[] p = city.findWalkable(b.doorX + rnd.nextFloat() * 10 - 5, b.doorY + rnd.nextFloat() * 10 - 5);
        if (p == null) return;
        o.x = p[0];
        o.y = p[1];
        o.vx = o.vy = 0;
        o.dead = false;
        o.removed = false;
        o.task = Dispatch.T_NONE;
        o.taskTimer = 4;
        if (panic) {
            o.fleeTimer = 3;
            o.threatX = b.doorX + (b.doorX - (b.x0 + b.x1) / 2);
            o.threatY = b.doorY + (b.doorY - (b.y0 + b.y1) / 2);
        }
        entities.add(o);
    }

    Pickup nearestPickup(float x, float y, float radius) {
        Pickup best = null;
        float bd = radius * radius;
        for (int i = 0, n = pickups.size(); i < n; i++) {
            Pickup p = pickups.get(i);
            if (p.claimed || p.drop > 0) continue;
            float d = (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private void updatePickups(float dt) {
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup p = pickups.get(i);
            p.age += dt;
            if (p.drop > 0) {
                p.drop -= dt;
                if (p.drop <= 0) {
                    emit(Sfx.THUD, p.x, p.y);
                    for (int k = 0; k < 8; k++)
                        particle(p.x, p.y, rnd.nextFloat() * 30 - 15, rnd.nextFloat() * 30 - 15, 0.8f, 2, 0xFFB8AE98, P_SMOKE);
                }
            }
            if (p.age > (p.uses > 0 ? 600 : 180)) pickups.remove(i);
        }
        for (int i = screams.size() - 1; i >= 0; i--) {
            float[] sc = screams.get(i);
            sc[2] += dt;
            if (sc[2] > 1.2f) screams.remove(i);
        }
    }

    private static boolean inside(Entity e, Dispatch.SafeZone z, float margin) {
        float ddx = e.x - z.x, ddy = e.y - z.y, r = z.r * margin;
        return ddx * ddx + ddy * ddy < r * r;
    }

    /** Guards spread out around the edge of their safe zone, facing outward. */
    private void holdPost(Entity e, Dispatch.SafeZone z, float speedFactor) {
        float a = e.slot * TAU / Math.max(1, z.guards);
        float px = z.x + (float) Math.cos(a) * z.r * 0.8f, py = z.y + (float) Math.sin(a) * z.r * 0.8f;
        float ddx = px - e.x, ddy = py - e.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d > z.r * 2.5f && followField(e, dispatch.zoneField, e.runSpeed * speedFactor)) return;
        if (d > 5) {
            steer(e, ddx / d, ddy / d, (d > 30 ? e.runSpeed : e.speed) * speedFactor);
        } else {
            steer(e, 0, 0, 0);
            if (!e.aiming) e.angle = turn(e.angle, a, 0.1f);
        }
    }

    private void throwGrenade(Entity e, float x, float y) {
        e.grenades--;
        e.grenadeCd = 6;
        Grenade g = new Grenade();
        g.sx = e.x;
        g.sy = e.y;
        g.tx = x + rnd.nextFloat() * 16 - 8;
        g.ty = y + rnd.nextFloat() * 16 - 8;
        g.dur = 0.9f;
        grenades.add(g);
    }

    private void steer(Entity e, float x, float y, float speed) {
        e.mx = x;
        e.my = y;
        e.want = speed;
    }

    private void wander(Entity e, float speed) {
        if (e.wanderTimer <= 0 || e.blocked) {
            e.blocked = false;
            e.wanderTimer = 2 + rnd.nextFloat() * 4;
            e.paused = !e.isZombie() && rnd.nextFloat() < 0.2f;
            e.wanderAngle = rnd.nextInt(4) * TAU / 4 + (rnd.nextFloat() - 0.5f) * 0.4f;
            // People who saw a zombie recently keep away from where it was.
            if (e.fear > 0 && !e.isZombie() && !e.isArmed()) {
                float tx = e.threatX - e.x, ty = e.threatY - e.y;
                if (tx * (float) Math.cos(e.wanderAngle) + ty * (float) Math.sin(e.wanderAngle) > 0) e.wanderAngle += (float) Math.PI;
                e.paused = false;
            }
        }
        if (e.paused) steer(e, 0, 0, 0);
        else steer(e, (float) Math.cos(e.wanderAngle), (float) Math.sin(e.wanderAngle), speed);
    }

    /** Walks one tile downhill in a distance field. Returns false if there is nowhere lower to go. */
    private boolean followField(Entity e, int[] field, float speed) {
        int w = city.w;
        int tx = (int) (e.x / City.T), ty = (int) (e.y / City.T);
        int cur = field[city.tileIndex(e.x, e.y)];
        if (cur >= City.FAR || cur == 0) return false;
        int best = cur, bx = -1, by = -1;
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                if (ox == 0 && oy == 0) continue;
                int nx = tx + ox, ny = ty + oy;
                if (city.solidTile(nx, ny)) continue;
                if (ox != 0 && oy != 0 && (city.solidTile(tx + ox, ty) || city.solidTile(tx, ty + oy))) continue;
                int v = field[ny * w + nx];
                if (v < best) {
                    best = v;
                    bx = nx;
                    by = ny;
                }
            }
        }
        if (bx < 0) return false;
        float gx = bx * City.T + City.T / 2f - e.x, gy = by * City.T + City.T / 2f - e.y;
        float d = (float) Math.sqrt(gx * gx + gy * gy) + 0.001f;
        steer(e, gx / d, gy / d, speed);
        return true;
    }

    /** Runs away: prefers tiles far from zombies that also point away from the threat. */
    private void flee(Entity e, float ax, float ay, float speed) {
        int w = city.w;
        int tx = (int) (e.x / City.T), ty = (int) (e.y / City.T);
        float bestScore = -Float.MAX_VALUE;
        float bx = ax, by = ay;
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                if (ox == 0 && oy == 0) continue;
                int nx = tx + ox, ny = ty + oy;
                if (city.solidTile(nx, ny)) continue;
                if (ox != 0 && oy != 0 && (city.solidTile(tx + ox, ty) || city.solidTile(tx, ty + oy))) continue;
                float len = (ox != 0 && oy != 0) ? 1.414f : 1f;
                float dot = (ox * ax + oy * ay) / len;
                int v = Math.min(12, city.zombieDist[ny * w + nx]);
                float score = v * 1.2f + dot * 2.5f;
                if (score > bestScore) {
                    bestScore = score;
                    bx = ox / len;
                    by = oy / len;
                }
            }
        }
        steer(e, bx * 0.7f + ax * 0.3f, by * 0.7f + ay * 0.3f, speed);
        float l = (float) Math.sqrt(e.mx * e.mx + e.my * e.my) + 0.001f;
        e.mx /= l;
        e.my /= l;
    }

    private static float turn(float from, float to, float amount) {
        float diff = to - from;
        while (diff > Math.PI) diff -= TAU;
        while (diff < -Math.PI) diff += TAU;
        if (amount > 1) amount = 1;
        return from + diff * amount;
    }

    // ------------------------------------------------------------------ movement

    private void move(Entity e, float dt) {
        float k = Math.min(1, dt * 10);
        e.vx += (e.mx * e.want - e.vx) * k;
        e.vy += (e.my * e.want - e.vy) * k;
        float sx = e.vx * dt, sy = e.vy * dt;
        float bx = e.x, by = e.y;
        boolean moved = tryMove(e, sx, sy);
        if (!moved && e.want > 0) e.blocked = true;
        float went = Math.abs(e.x - bx) + Math.abs(e.y - by);
        if (e.want > 5 && went < e.want * dt * 0.2f) e.stuckTime += dt;
        else e.stuckTime = Math.max(0, e.stuckTime - dt * 2);
        float sp = (float) Math.sqrt(e.vx * e.vx + e.vy * e.vy);
        if (sp > 2 && !e.aiming) e.angle = turn(e.angle, (float) Math.atan2(e.vy, e.vx), dt * 10);
        e.phase += sp * dt * 0.35f;
    }

    /** Moves with wall sliding. Returns false if the move was (at least partly) blocked. */
    private boolean tryMove(Entity e, float sx, float sy) {
        boolean ok = true;
        if (sx != 0) {
            if (!city.circleBlocked(e.x + sx, e.y, e.radius)) e.x += sx;
            else ok = false;
        }
        if (sy != 0) {
            if (!city.circleBlocked(e.x, e.y + sy, e.radius)) e.y += sy;
            else ok = false;
        }
        return ok;
    }

    private void separate() {
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.dead) continue;
            int ecx = Math.max(0, Math.min(gw - 1, (int) (e.x / CELL)));
            int ecy = Math.max(0, Math.min(gh - 1, (int) (e.y / CELL)));
            float pushX = 0, pushY = 0;
            for (int cy = Math.max(0, ecy - 1); cy <= Math.min(gh - 1, ecy + 1); cy++) {
                for (int cx = Math.max(0, ecx - 1); cx <= Math.min(gw - 1, ecx + 1); cx++) {
                    int c = cy * gw + cx;
                    for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                        Entity o = sorted[k];
                        if (o == e || o.dead) continue;
                        float ddx = e.x - o.x, ddy = e.y - o.y;
                        float min = e.radius + o.radius;
                        float d2 = ddx * ddx + ddy * ddy;
                        if (d2 >= min * min) continue;
                        float d = (float) Math.sqrt(d2);
                        if (d < 0.01f) {
                            ddx = rnd.nextFloat() - 0.5f;
                            ddy = rnd.nextFloat() - 0.5f;
                            d = 0.5f;
                        }
                        float share = o.mass / (e.mass + o.mass);
                        float overlap = (min - d) * share * 0.5f;
                        pushX += ddx / d * overlap;
                        pushY += ddy / d * overlap;
                    }
                }
            }
            if (pushX != 0 || pushY != 0) tryMove(e, pushX, pushY);
        }
    }

    // ------------------------------------------------------------------ death, corpses, explosions

    private void cleanup() {
        for (int i = entities.size() - 1; i >= 0; i--) {
            Entity e = entities.get(i);
            if (e.hp <= 0) e.dead = true;
            if (!e.dead) continue;
            int last = entities.size() - 1;
            entities.set(i, entities.get(last));
            entities.remove(last);
            if (!e.removed) onDeath(e);
        }
    }

    private void onDeath(Entity e) {
        if (e.type == Entity.BLOATER) burst(e.x, e.y);
        if (e.kills > 0 && !e.isZombie()) {
            fallenHeroes.add(new int[]{e.nameSeed, e.type, e.role, e.kills});
            if (fallenHeroes.size() > 40) fallenHeroes.remove(0);
        }
        dispatch.onDeath(e);
        if (e.type == Entity.CIVILIAN) civiliansLost++;
        // Their gun stays where they fell for someone else to pick up.
        if (e.canShoot() && e.ammo + e.reserve > 0) {
            Pickup p = new Pickup();
            p.x = e.x + rnd.nextFloat() * 6 - 3;
            p.y = e.y + rnd.nextFloat() * 6 - 3;
            p.rounds = Math.min(60, e.ammo + e.reserve);
            pickups.add(p);
            if (pickups.size() > 60) pickups.remove(0);
        }
        // Blown-apart zombies sometimes keep crawling.
        if (e.isZombie() && e.gibbed && e.type != Entity.CRAWLER && e.type != Entity.BRUTE && rnd.nextFloat() < 0.35f
                && entities.size() < maxEntities) {
            Entity c = make(Entity.CRAWLER, e.x, e.y, e.origin, e.body);
            c.angle = e.angle;
            entities.add(c);
        }
        if (gore) decal(e.x + rnd.nextFloat() * 4 - 2, e.y + rnd.nextFloat() * 4 - 2, e.radius * (1.4f + rnd.nextFloat()), 0x996E0A0A);
        if (e.isZombie()) zombiesKilled++;
        Corpse c = new Corpse();
        c.x = e.x;
        c.y = e.y;
        c.angle = rnd.nextFloat() * TAU;
        c.radius = e.radius;
        c.body = e.body;
        c.head = e.head;
        c.zombie = e.isZombie();
        c.origin = e.type;
        boolean turns = !e.isZombie() && !e.gibbed && (e.killedByZombie || e.infected)
                && (e.type != Entity.DOG || rnd.nextFloat() < 0.6f);
        c.rise = turns ? 2.5f + rnd.nextFloat() * 3f : -1;
        float roll = rnd.nextFloat();
        c.riseType = e.type == Entity.DOG ? Entity.ZOMBIE_DOG
                : roll < 0.14f ? Entity.RUNNER : roll < 0.18f ? Entity.SCREAMER : roll < 0.22f ? Entity.CRAWLER : Entity.ZOMBIE;
        if (e.gibbed) {
            bloodBurst(e.x, e.y, 14, 0, 0);
            if (rnd.nextBoolean()) return;
        }
        corpses.add(c);
        if (corpses.size() > 350) {
            for (int i = 0; i < corpses.size(); i++)
                if (corpses.get(i).rise < 0) {
                    corpses.remove(i);
                    break;
                }
        }
    }

    private void updateCorpses(float dt) {
        for (int i = corpses.size() - 1; i >= 0; i--) {
            Corpse c = corpses.get(i);
            c.age += dt;
            if (c.rise > 0) {
                c.rise -= dt;
                if (c.rise <= 0) {
                    corpses.remove(i);
                    if (entities.size() >= maxEntities) continue;
                    Entity z = make(c.riseType, c.x, c.y, c.origin, c.body);
                    z.angle = c.angle;
                    // Freshly turned: fast and hungry for a while.
                    z.fresh = 45;
                    z.speed *= 1.2f;
                    z.runSpeed *= 1.3f;
                    entities.add(z);
                    turned++;
                    bloodBurst(c.x, c.y, 5, 0, 0);
                    emit(Sfx.GROAN, c.x, c.y);
                }
            } else if (c.age > 150) {
                corpses.remove(i);
            }
        }
    }

    /** A moving vehicle hits zombies in its way and nudges people aside. */
    float runOver(float x, float y, float r, float speed, float angle) {
        if (speed < 20) return 0;
        float impact = 0;
        int cx0 = Math.max(0, (int) ((x - r - 8) / CELL)), cx1 = Math.min(gw - 1, (int) ((x + r + 8) / CELL));
        int cy0 = Math.max(0, (int) ((y - r - 8) / CELL)), cy1 = Math.min(gh - 1, (int) ((y + r + 8) / CELL));
        float fx = (float) Math.cos(angle), fy = (float) Math.sin(angle);
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead) continue;
                    float ddx = o.x - x, ddy = o.y - y, reach = r + o.radius;
                    if (ddx * ddx + ddy * ddy > reach * reach) continue;
                    // Push sideways out of the lane.
                    float side = ddx * -fy + ddy * fx >= 0 ? 1 : -1;
                    tryMove(o, -fy * side * 6 + fx * 3, fx * side * 6 + fy * 3);
                    if (!o.isZombie() || o.stun > 0) continue;
                    o.hp -= speed * (o.type == Entity.BRUTE ? 0.3f : 0.9f);
                    o.hurt = 1;
                    o.stun = 1f;
                    o.killedByZombie = false;
                    bloodBurst(o.x, o.y, 8, fx, fy);
                    emit(Sfx.THUD, o.x, o.y);
                    // Hitting them dents the car: a brute is like hitting a wall.
                    impact += o.type == Entity.BRUTE ? 28 : o.type == Entity.CRAWLER ? 2 : 6;
                }
            }
        return impact;
    }

    /** A civilian waves the car down: the nearest person running for their life who can reach it. */
    boolean hail(Fleet.Vehicle v) {
        if (v.speed > 60) return false;
        Entity best = null;
        float bd = 70 * 70;
        int cx0 = Math.max(0, (int) ((v.x - 70) / CELL)), cx1 = Math.min(gw - 1, (int) ((v.x + 70) / CELL));
        int cy0 = Math.max(0, (int) ((v.y - 70) / CELL)), cy1 = Math.min(gh - 1, (int) ((v.y + 70) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || o.type != Entity.CIVILIAN || o.ride != null || o.infected) continue;
                    if (o.task != Dispatch.T_NONE && o.task != Dispatch.T_SEEK) continue;
                    if (o.fleeTimer <= 0 && o.task != Dispatch.T_SEEK) continue;
                    float d = (o.x - v.x) * (o.x - v.x) + (o.y - v.y) * (o.y - v.y);
                    if (d < bd && countZombiesNear(o.x, o.y, 14) == 0) {
                        bd = d;
                        best = o;
                    }
                }
            }
        if (best == null) return false;
        callRide(best, v);
        // Their family comes too.
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (!o.dead && o.ride == null && (o.leader == best || (best.leader != null && (o == best.leader || o.leader == best.leader))))
                callRide(o, v);
        }
        return true;
    }

    void callRide(Entity e, Fleet.Vehicle v) {
        e.ride = v;
        if (e.type == Entity.CIVILIAN) e.task = Dispatch.T_RIDE;
        e.talkTimer = 1.5f;
    }

    /** Puts someone who was in a car (or indoors) back on the street. Returns false if there's no room. */
    boolean release(Entity o, float x, float y) {
        if (entities.size() >= maxEntities) return false;
        float[] p = city.findWalkable(x, y);
        if (p == null) return false;
        o.x = p[0];
        o.y = p[1];
        o.vx = o.vy = 0;
        o.dead = false;
        o.removed = false;
        o.ride = null;
        o.task = Dispatch.T_NONE;
        o.taskTimer = 3;
        entities.add(o);
        return true;
    }

    /** A car left by the road that still runs, with no zombies around it. */
    private Fleet.Vehicle abandonedCarNear(float x, float y, float radius) {
        for (int i = 0, n = fleet.vehicles.size(); i < n; i++) {
            Fleet.Vehicle v = fleet.vehicles.get(i);
            if (v.type != Fleet.CAR || !v.parked || v.broken || v.burnt) continue;
            if ((v.x - x) * (v.x - x) + (v.y - y) * (v.y - y) > radius * radius) continue;
            if (countZombiesNear(v.x, v.y, 30) > 0) continue;
            return v;
        }
        return null;
    }

    /** A car (not the helicopter) at a point, or null. */
    Fleet.Vehicle vehicleAt(float x, float y) {
        for (int i = 0, n = fleet.vehicles.size(); i < n; i++) {
            Fleet.Vehicle v = fleet.vehicles.get(i);
            if (Fleet.airborne(v) || v.type == Fleet.TRAIN) continue;
            float dx = v.x - x, dy = v.y - y;
            if (dx * dx + dy * dy < 7 * 7) return v;
        }
        return null;
    }

    /** The nearest zombie to a point, ignoring walls (for the helicopter gunner). */
    Entity nearestZombie(float x, float y, float radius) {
        Entity best = null;
        float bd = radius * radius;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o.dead || !o.isZombie()) continue;
            float d = (o.x - x) * (o.x - x) + (o.y - y) * (o.y - y);
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    /** A tank round: a flash at the muzzle and a blast where it lands. */
    void tankShell(float fx, float fy, float tx, float ty) {
        particle(fx, fy, 0, 0, 0.12f, 7, 0xFFFFE9A0, P_FLASH);
        emit(Sfx.CANNON, fx, fy);
        for (int i = 0; i < 8; i++)
            particle(fx, fy, rnd.nextFloat() * 30 - 15, rnd.nextFloat() * 30 - 15, 1.2f, 4, 0xFF8A8478, P_SMOKE);
        tracer(fx, fy, tx, ty);
        shotsFired++;
        explode(tx, ty, 50, 230);
    }

    /** The nearest zombie a gunner on the ground can actually see. */
    Entity nearestVisibleZombie(float x, float y, float radius) {
        Entity best = null;
        float bd = radius * radius;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o.dead || !o.isZombie()) continue;
            float d = (o.x - x) * (o.x - x) + (o.y - y) * (o.y - y);
            if (d < bd && city.los(x, y, o.x, o.y)) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    /** One burst from the helicopter's door gun. */
    void airShot(float fx, float fy, Entity t) {
        shotsFired++;
        if (rnd.nextFloat() < 0.75f) {
            t.hp -= 22;
            t.hurt = 1;
            bloodBurst(t.x, t.y, 4, 0, 0);
            tracer(fx, fy, t.x, t.y);
        } else {
            tracer(fx, fy, t.x + rnd.nextFloat() * 24 - 12, t.y + rnd.nextFloat() * 24 - 12);
        }
        emit(Sfx.RIFLE, fx, fy);
    }

    void explode(float x, float y, float radius, float damage) {
        int cx0 = Math.max(0, (int) ((x - radius) / CELL)), cx1 = Math.min(gw - 1, (int) ((x + radius) / CELL));
        int cy0 = Math.max(0, (int) ((y - radius) / CELL)), cy1 = Math.min(gh - 1, (int) ((y + radius) / CELL));
        buildHash();
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead) continue;
                    float ddx = o.x - x, ddy = o.y - y;
                    float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                    if (d > radius) continue;
                    float f = 1 - d / radius;
                    o.hp -= damage * (0.25f + 0.75f * f);
                    o.hurt = 1;
                    o.killedByZombie = false;
                    if (o.hp <= 0 && f > 0.4f) o.gibbed = true;
                    if (d > 0.01f) tryMove(o, ddx / d * 16 * f / o.mass, ddy / d * 16 * f / o.mass);
                    if (!o.isZombie()) {
                        o.fleeTimer = 3;
                        o.threatX = x;
                        o.threatY = y;
                    }
                }
            }
        for (int i = corpses.size() - 1; i >= 0; i--) {
            Corpse c = corpses.get(i);
            float ddx = c.x - x, ddy = c.y - y;
            if (ddx * ddx + ddy * ddy < radius * radius * 0.5f) {
                corpses.remove(i);
                bloodBurst(c.x, c.y, 6, 0, 0);
            }
        }
        // Vehicles caught in the blast.
        for (int i = 0, n = fleet.vehicles.size(); i < n; i++) {
            Fleet.Vehicle v = fleet.vehicles.get(i);
            if (Fleet.airborne(v) || v.type == Fleet.TRAIN) continue;
            float d = (float) Math.hypot(v.x - x, v.y - y);
            if (d < radius) fleet.damage(v, damage * (1.1f - d / radius), d < radius * 0.5f);
        }
        // Buildings: scorched walls, and enough of a hit brings one down.
        int bx0 = Math.max(0, (int) ((x - radius) / City.T)), bx1 = Math.min(city.w - 1, (int) ((x + radius) / City.T));
        int by0 = Math.max(0, (int) ((y - radius) / City.T)), by1 = Math.min(city.h - 1, (int) ((y + radius) / City.T));
        for (int ty = by0; ty <= by1; ty++)
            for (int tx = bx0; tx <= bx1; tx++) {
                int bi = city.buildingAt[ty * city.w + tx];
                if (bi < 0) continue;
                City.Building b = city.buildings.get(bi);
                if (b.collapsed || hitBuildings.contains(b)) continue;
                hitBuildings.add(b);
            }
        for (int i = 0; i < hitBuildings.size(); i++) blastBuilding(hitBuildings.get(i), x, y, radius, damage);
        hitBuildings.clear();

        Explosion ex = new Explosion();
        ex.x = x;
        ex.y = y;
        ex.r = radius;
        ex.life = ex.max = 0.45f;
        explosions.add(ex);
        emit(Sfx.EXPLOSION, x, y);
        // Parked cars in the blast catch fire; gas pumps go up a moment later.
        int tx0 = Math.max(0, (int) ((x - radius) / City.T)), tx1 = Math.min(city.w - 1, (int) ((x + radius) / City.T));
        int ty0 = Math.max(0, (int) ((y - radius) / City.T)), ty1 = Math.min(city.h - 1, (int) ((y + radius) / City.T));
        for (int ty = ty0; ty <= ty1; ty++)
            for (int tx = tx0; tx <= tx1; tx++) {
                int i = ty * city.w + tx;
                byte t = city.tiles[i];
                if ((t != City.CAR && t != City.PUMP) || burned[i]) continue;
                float cx = tx * City.T + City.T / 2f, cy = ty * City.T + City.T / 2f;
                if ((cx - x) * (cx - x) + (cy - y) * (cy - y) > radius * radius * 0.8f) continue;
                if (t == City.CAR && rnd.nextFloat() > 0.6f) continue;
                burnTile(i);
                ignite(cx, cy, t == City.PUMP ? 90 : 40 + rnd.nextFloat() * 30);
                if (t == City.PUMP) pendingBlasts.add(new float[]{cx, cy, 90, 380, 0.3f + rnd.nextFloat() * 0.5f});
                else if (rnd.nextFloat() < 0.25f) pendingBlasts.add(new float[]{cx, cy, 45, 150, 1.5f + rnd.nextFloat() * 3});
            }
        noise(x, y, 450);
        for (int i = 0; i < 40; i++) {
            float a = rnd.nextFloat() * TAU, s = 30 + rnd.nextFloat() * radius * 2.2f;
            particle(x, y, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 0.3f + rnd.nextFloat() * 0.4f,
                    2 + rnd.nextFloat() * 3, rnd.nextBoolean() ? 0xFFFFB030 : 0xFFFF6A1A, P_FIRE);
        }
        // Chunks of debris thrown out.
        for (int i = 0; i < 18; i++) {
            float a = rnd.nextFloat() * TAU, s = 60 + rnd.nextFloat() * radius * 2.5f;
            particle(x, y, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 0.8f + rnd.nextFloat() * 0.6f,
                    0.8f + rnd.nextFloat() * 1.2f, rnd.nextBoolean() ? 0xFF3A3632 : 0xFF6A645C, P_DEBRIS);
        }
        for (int i = 0; i < 22; i++) {
            float a = rnd.nextFloat() * TAU, s = 5 + rnd.nextFloat() * radius * 0.8f;
            particle(x, y, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 1.2f + rnd.nextFloat() * 1.3f,
                    5 + rnd.nextFloat() * 6, 0xFF3A3A3A, P_SMOKE);
        }
        decal(x, y, radius * 0.55f, 0xAA121212);
        shake = Math.min(1.5f, shake + radius / 60f);
    }

    private final ArrayList<City.Building> hitBuildings = new ArrayList<City.Building>();

    /** Which wall of a building faces a point: 0 north, 1 south, 2 west, 3 east (as GameView draws them). */
    private static int wallSide(City.Building b, float x, float y) {
        float dn = Math.abs(y - b.y0), ds = Math.abs(y - b.y1), dw = Math.abs(x - b.x0), de = Math.abs(x - b.x1);
        if (y < b.y0) dn -= 1000;
        if (y > b.y1) ds -= 1000;
        if (x < b.x0) dw -= 1000;
        if (x > b.x1) de -= 1000;
        float m = Math.min(Math.min(dn, ds), Math.min(dw, de));
        return m == dn ? 0 : m == ds ? 1 : m == dw ? 2 : 3;
    }

    /** Distance along a wall from the corner GameView starts drawing it at. */
    private static float along(City.Building b, int side, float x, float y) {
        switch (side) {
            case 0: return Math.max(0, Math.min(b.x1 - b.x0, x - b.x0));
            case 1: return Math.max(0, Math.min(b.x1 - b.x0, b.x1 - x));
            case 2: return Math.max(0, Math.min(b.y1 - b.y0, b.y1 - y));
            default: return Math.max(0, Math.min(b.y1 - b.y0, y - b.y0));
        }
    }

    /** A bullet hole where a stray shot hit a wall. */
    private void bulletHole(float x, float y, float fromX, float fromY) {
        City.Building b = city.buildingAt(x, y);
        if (b == null) return;
        int side = wallSide(b, fromX, fromY);
        b.mark(side, along(b, side, x, y), 2 + rnd.nextFloat() * Math.min(14, b.height - 3), 0.5f + rnd.nextFloat() * 0.3f);
    }

    private void blastBuilding(City.Building b, float x, float y, float radius, float damage) {
        float nx = Math.max(b.x0, Math.min(x, b.x1)), ny = Math.max(b.y0, Math.min(y, b.y1));
        float d = (float) Math.hypot(nx - x, ny - y);
        if (d > radius) return;
        float f = 1 - d / radius;
        int side = wallSide(b, x, y);
        b.mark(side, along(b, side, nx, ny), 1 + rnd.nextFloat() * 8, 3 + f * 5);
        b.barricade -= damage * f * 0.3f;
        b.hp -= damage * f;
        if (b.hp > 0) return;
        // Down it comes.
        int inside = b.occupants.size();
        for (int i = 0; i < inside; i++) {
            Entity o = b.occupants.get(i);
            if (rnd.nextFloat() < 0.5f || !release(o, b.doorX, b.doorY)) {
                if (o.type == Entity.CIVILIAN) civiliansLost++;
            } else {
                o.hp *= 0.4f;
                o.fleeTimer = 3;
                o.threatX = (b.x0 + b.x1) / 2;
                o.threatY = (b.y0 + b.y1) / 2;
            }
        }
        float cx = (b.x0 + b.x1) / 2, cy = (b.y0 + b.y1) / 2;
        float h = b.height;
        city.collapse(b);
        collapsedCount++;
        dispatch.say(Dispatch.WHO_INFO, null, "A building collapsed on " + city.placeName(cx, cy) + "!"
                + (inside > 0 ? " People were trapped inside." : ""), cx, cy);
        // Anyone standing next to it gets hit by falling debris.
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o.x > b.x0 - 12 && o.x < b.x1 + 12 && o.y > b.y0 - 12 && o.y < b.y1 + 12) {
                o.hp -= 40 + h * 0.5f;
                o.hurt = 1;
            }
        }
        int dust = (int) Math.min(60, 12 + (b.x1 - b.x0) * (b.y1 - b.y0) / 200);
        for (int i = 0; i < dust; i++)
            particle(b.x0 + rnd.nextFloat() * (b.x1 - b.x0), b.y0 + rnd.nextFloat() * (b.y1 - b.y0), rnd.nextFloat() * 40 - 20,
                    rnd.nextFloat() * 40 - 20, 2.5f + rnd.nextFloat() * 2, 6 + rnd.nextFloat() * 6, 0xFF9A948A, P_SMOKE);
        emit(Sfx.COLLAPSE, cx, cy);
        shake = Math.min(2f, shake + 1f);
        noise(cx, cy, 400);
    }

    private void updateGrenades(float dt) {
        for (int i = grenades.size() - 1; i >= 0; i--) {
            Grenade g = grenades.get(i);
            g.t += dt;
            if (g.t >= g.dur) {
                grenades.remove(i);
                explode(g.tx, g.ty, 48, 140);
            }
        }
    }

    // ------------------------------------------------------------------ effects

    void particle(float x, float y, float vx, float vy, float life, float size, int color, byte type) {
        int i = pnext;
        pnext = (pnext + 1) % MAXP;
        px[i] = x;
        py[i] = y;
        pvx[i] = vx;
        pvy[i] = vy;
        plife[i] = pmax[i] = life;
        psize[i] = size;
        pcol[i] = color;
        ptype[i] = type;
    }

    private void bloodBurst(float x, float y, int n, float nx, float ny) {
        if (!gore) return;
        for (int i = 0; i < n; i++) {
            float a = rnd.nextFloat() * TAU, s = 10 + rnd.nextFloat() * 40;
            particle(x, y, (float) Math.cos(a) * s + nx * 40, (float) Math.sin(a) * s + ny * 40,
                    0.3f + rnd.nextFloat() * 0.3f, 0.8f + rnd.nextFloat() * 0.8f, 0xFF8E1010, P_DOT);
        }
        if (rnd.nextFloat() < 0.5f)
            decal(x + nx * 4 + rnd.nextFloat() * 4 - 2, y + ny * 4 + rnd.nextFloat() * 4 - 2, 1.5f + rnd.nextFloat() * 2.5f, 0x996E0A0A);
    }

    private void tracer(float x0, float y0, float x1, float y1) {
        int i = tnext;
        tnext = (tnext + 1) % MAXT;
        tx0[i] = x0;
        ty0[i] = y0;
        tx1[i] = x1;
        ty1[i] = y1;
        tlife[i] = 0.08f;
    }

    private void decal(float x, float y, float r, int color) {
        decal(x, y, r, color, D_SPLAT, 0);
    }

    void decal(float x, float y, float r, int color, byte kind, float angle) {
        int i = dnext;
        dnext = (dnext + 1) % MAXD;
        if (dcount < MAXD) dcount++;
        dx[i] = x;
        dy[i] = y;
        dr[i] = r;
        dcol[i] = color;
        dkind[i] = kind;
        dang[i] = angle;
    }

    /** Rubber left on the road by a car braking hard or skidding in a crash. */
    void skid(float x, float y, float angle, float length) {
        float sx = (float) -Math.sin(angle) * 2.8f, sy = (float) Math.cos(angle) * 2.8f;
        decal(x + sx, y + sy, length, 0x55101010, D_SKID, angle);
        decal(x - sx, y - sy, length, 0x55101010, D_SKID, angle);
    }

    private void updateEffects(float dt) {
        for (int i = 0; i < MAXP; i++) {
            if (plife[i] <= 0) continue;
            plife[i] -= dt;
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
            float drag = ptype[i] == P_SMOKE ? 1.5f : ptype[i] == P_CASING ? 9f : ptype[i] == P_DEBRIS ? 2.5f : 5f;
            pvx[i] -= pvx[i] * Math.min(1, drag * dt);
            pvy[i] -= pvy[i] * Math.min(1, drag * dt);
            if (ptype[i] == P_SMOKE) psize[i] += dt * 6;
        }
        for (int i = 0; i < MAXT; i++) if (tlife[i] > 0) tlife[i] -= dt;
        for (int i = explosions.size() - 1; i >= 0; i--) {
            Explosion e = explosions.get(i);
            e.life -= dt;
            if (e.life <= 0) explosions.remove(i);
        }
    }
}
