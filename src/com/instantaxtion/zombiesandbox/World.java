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
        int body, head, riseType, origin, nameSeed;
        /** Thrown by a blast or a car: sliding (and spinning) to a stop. */
        float vx, vy, spin;
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
        /** The building this fire is burning, if any. */
        City.Building building;
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
        /** A particular gun (Entity.W_*), or a bat or axe (Entity.M_*), and when the player may pick it up again. */
        int weapon, melee;
        float lockUntil;
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
    int shotsFired, healed, peakZombies, hiding;
    /** Every living civilian: on the street, indoors, or in a car (so the count doesn't jump as people go in and out). */
    int civilians;
    /** Zombies shut inside buildings, out of sight. */
    int lurking;
    /** The power station or the hospital has been overrun. */
    boolean blackout, hospitalLost;
    private float powerThreat, hospitalThreat;
    /** Army checkpoints: bitten people turned away at the gate. */
    int turnedAway;
    /** Recruitment: the most cops and soldiers there have been, how many civilians joined up or refused. */
    int peakCops, peakSoldiers, recruits, refused;
    /** How far the infection has evolved (0-5), how long the outbreak has run, and the recovery afterwards. */
    int mutation;
    /** Counters for records and achievements. */
    int firesOut, tanksDeployed, collapsedCount, carsWrecked, biggestHorde;
    float outbreakTime, calmTime;
    /**
     * How far word of the outbreak has got: 0 nobody knows, 1 the first reports (word spreads person to
     * person), 2 everyone knows (emergency broadcast). Anyone who hears gunfire or sees one knows anyway.
     */
    int alert;
    boolean recovering;
    private float lifeTimer;
    private float recruitTimer, lastRecruitSay = -100;
    private boolean policeDrive, armyDrive;
    private final ArrayList<Entity> newRecruits = new ArrayList<Entity>();

    // Spatial hash (counting sort into cells).
    private static final int CELL = 32;
    private final int gw, gh;
    private final int[] cellStart, cellCount, cellFill, zCount, zFill;
    private Entity[] sorted = new Entity[512];
    private int lodFrame;
    /** Counts up each time the grid is rebuilt; a leader marked with the current one has someone following. */
    private int hashFrame;

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
    static final byte D_SPLAT = 0, D_SKID = 1, D_PUDDLE = 2, D_ACID = 3, D_GLASS = 4;
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
    /** Detail level, lowered on a phone that's struggling (see GameView.tuneQuality). */
    static final int Q_FULL = 0, Q_REDUCED = 1, Q_LOW = 2;
    int quality = Q_FULL;

    void setQuality(int q) {
        quality = Math.max(Q_FULL, Math.min(Q_LOW, q));
        fleet.trafficScale = quality == Q_FULL ? 1f : quality == Q_REDUCED ? 0.7f : 0.45f;
    }

    private boolean onScreen(Entity e) {
        return viewX1 >= 0 && e.x > viewX0 - 80 && e.x < viewX1 + 80 && e.y > viewY0 - 80 && e.y < viewY1 + 80;
    }

    /** Sets the population cap; 0 means Auto, sized to the city: everyone who lives there, and room for the dead. */
    void setMaxPopulation(int cap) {
        maxEntities = cap > 0 ? cap : Math.max(2500, city.totalResidents * 2 + 1000);
    }
    boolean gore = true;

    float time, shake;
    /** What the player can see (world units), so cars aren't made to vanish or appear in plain view. */
    float viewX0, viewY0, viewX1 = -1, viewY1 = -1;
    private float fieldTimer, engineTimer;
    final int[] counts = new int[Entity.TYPE_COUNT];
    int humans, zombies, turned, zombiesKilled, civiliansLost;
    /** People who got away on evacuation trains. */
    int evacuated;
    String message;
    float messageTime;
    /** How fast sprinting uses up stamina (per second, out of 1). */
    static float STAMINA_DRAIN = 0.14f;
    /** Balance: how much of a gun's damage counts against zombies, and how likely a bite is to infect. */
    static float GUN_DAMAGE = 0.3f, BITE_INFECT = 1.85f;
    /** Chance any hit on a zombie is a headshot that drops it on the spot. */
    static float HEADSHOT = 0.04f;
    /** How far (in tiles of walking) idle zombies can smell the living. */
    /** How far (in tiles, by the way round) the dead can smell the living: the street, not across town. */
    static int SCENT = 40;
    /** Balance: how much health police and soldiers have compared to their base. */
    static float ARMED_HP = 0.6f;
    /** The city has fallen once fewer than this share of its people are left alive (and the dead outnumber them). */
    static float FALLEN = 0.15f;

    /** An outbreak is under way (zombies and people both on the map). */
    boolean outbreak;

    // ------------------------------------------------------------------ the strain and the war

    static final int TR_FAST_TURN = 0, TR_SLOW_TURN = 1, TR_VIRULENT = 2, TR_WEAK_BITE = 3, TR_TOUGH = 4, TR_FRAIL = 5,
            TR_SWIFT = 6, TR_RESTLESS = 7, TR_KEEN = 8, TR_DULL = 9, TR_COUNT = 10;
    static final String[] TRAIT_NAMES = {"Fast-acting", "Slow-acting", "Virulent", "Weak bite", "Tough", "Frail",
            "Swift", "Restless dead", "Keen senses", "Dull senses"};
    private static final String[] TRAIT_REVEALS = {
            "Doctors: the bitten are turning in seconds. Get away from anyone who's been bitten.",
            "Doctors: this infection takes its time. The bitten can walk around for a while before they turn.",
            "Doctors: nearly every bite is infecting people.",
            "Doctors: a lot of bites aren't infecting people at all.",
            "Police: it takes far more bullets to put them down than it should.",
            "Police: they go down easier than we feared.",
            "Witnesses: they're faster than they look.",
            "Reports: people who were never bitten are getting back up too.",
            "Survivors: they can spot you from a long way off.",
            "Survivors: they don't notice you until you're close.",
    };
    /** This outbreak's strain: a few hidden traits, found out one by one as the war goes on. */
    final boolean[] trait = new boolean[TR_COUNT], traitKnown = new boolean[TR_COUNT];
    String strainName;
    int bites;
    /** Share of the fighting strength on the people's side (0 to 1), and who is ahead: 1 people, -1 zombies. */
    float warBalance = 0.5f;
    int warLead;
    /** How the last war ended: 0 not yet, 1 the city survived, 2 the city fell. */
    int warResult;
    /** A big announcement across the screen (the end of the war, a turning point) and how long it shows. */
    String warBanner, warSub;
    float warBannerTime;
    /** Counts banners, so the screen can queue each one once. */
    int bannerSeq;
    int startHumans;

    /** How ready the city is: 0 normal, 1 unprepared, 2 panicking, 3 a gun town, 4 well prepared. */
    int readiness;
    boolean readinessKnown;
    static final String[] READINESS = {"Normal", "Unprepared", "Panicking", "Gun town", "Well prepared"};
    private static final String[] READINESS_NEWS = {"",
            "Police: we weren't ready for this. Ammunition is short and there are no reserves coming.",
            "News: panic is sweeping the city. People are running instead of hiding.",
            "News: half the town owns a gun, and they're using them.",
            "Military: we've been preparing for this. Extra units are standing by."};

    /** Rolls how ready the city is (a hidden factor, revealed on the radio once the outbreak is under way). */
    private void applyReadiness() {
        float r = rnd.nextFloat();
        readiness = r < 0.2f ? 1 : r < 0.35f ? 2 : r < 0.5f ? 3 : r < 0.62f ? 4 : 0;
        if (readiness == 1) {
            dispatch.policeReserve = 0;
            dispatch.squadReserve = 0;
            for (Entity e : entities) if (e.isArmed()) e.reserve /= 2;
        } else if (readiness == 3) {
            for (Entity e : entities) if (e.type == Entity.CIVILIAN && !e.hasGun && rnd.nextFloat() < 0.2f) armCivilian(e, 12 + rnd.nextInt(3) * 6);
        } else if (readiness == 4) {
            dispatch.policeReserve += 1;
            dispatch.squadReserve += 1;
        }
    }

    private void rollStrain() {
        int[][] pairs = {{TR_FAST_TURN, TR_SLOW_TURN}, {TR_VIRULENT, TR_WEAK_BITE}, {TR_TOUGH, TR_FRAIL}, {TR_KEEN, TR_DULL}};
        for (int[] p : pairs) if (rnd.nextFloat() < 0.55f) trait[p[rnd.nextInt(2)]] = true;
        if (rnd.nextFloat() < 0.3f) trait[TR_SWIFT] = true;
        if (rnd.nextFloat() < 0.25f) trait[TR_RESTLESS] = true;
        strainName = "Strain " + (char) ('A' + rnd.nextInt(26)) + "-" + (1 + rnd.nextInt(99));
    }

    /** How long a bite takes to turn someone, for this strain. */
    float turnTime(float base) {
        return base * (trait[TR_FAST_TURN] ? 0.45f : trait[TR_SLOW_TURN] ? 1.8f : 1f);
    }

    private void reveal(int t) {
        if (!trait[t] || traitKnown[t]) return;
        traitKnown[t] = true;
        dispatch.say(Dispatch.WHO_INFO, null, TRAIT_REVEALS[t], city.worldW() / 2, city.worldH() / 2);
    }

    /** Once a second during an outbreak: who is winning, turning points, and what's been learned about the strain. */
    private void updateWar() {
        if (turned >= 3) {
            reveal(TR_FAST_TURN);
            reveal(TR_SLOW_TURN);
        }
        if (bites >= 10) {
            reveal(TR_VIRULENT);
            reveal(TR_WEAK_BITE);
        }
        if (zombiesKilled >= 15) {
            reveal(TR_TOUGH);
            reveal(TR_FRAIL);
        }
        if (outbreakTime >= 20) reveal(TR_SWIFT);
        if (outbreakTime >= 25 && !readinessKnown) {
            readinessKnown = true;
            if (readiness > 0) dispatch.say(Dispatch.WHO_INFO, null, READINESS_NEWS[readiness], city.worldW() / 2, city.worldH() / 2);
        }
        if (outbreakTime >= 45) {
            reveal(TR_KEEN);
            reveal(TR_DULL);
        }
        float hs = 0, zs = 0;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.dead) continue;
            switch (e.type) {
                case Entity.COP: hs += e.role == Entity.ROLE_RIOT ? 4 : 3; break;
                case Entity.SOLDIER: hs += e.role == Entity.ROLE_COMMANDER ? 5 : 4; break;
                case Entity.MEDIC: hs += 0.6f; break;
                case Entity.CIVILIAN: hs += e.hasGun ? 1.2f : 0.15f; break;
                case Entity.DOG: hs += 0.3f; break;
                case Entity.ZOMBIE: case Entity.ZOMBIE_DOG: zs += 1; break;
                case Entity.RUNNER: zs += 1.4f; break;
                case Entity.BRUTE: zs += 4; break;
                case Entity.CRAWLER: zs += 0.6f; break;
                case Entity.SCREAMER: zs += 1.5f; break;
                case Entity.SPITTER: case Entity.BLOATER: zs += 2; break;
            }
            if (e.infected) zs += 0.8f;
        }
        for (int i = 0, n = fleet.vehicles.size(); i < n; i++)
            if (fleet.vehicles.get(i).type == Fleet.TANK && !fleet.vehicles.get(i).broken) hs += 12;
        hs += hiding * 0.1f;
        zs += lurking;
        if (trait[TR_TOUGH]) zs *= 1.25f;
        if (trait[TR_FRAIL]) zs *= 0.8f;
        float b = hs + zs > 0 ? hs / (hs + zs) : 0.5f;
        warBalance += (b - warBalance) * 0.25f;
        if (warBalance > 0.62f && warLead != 1) {
            if (warLead == -1) banner("THE TIDE IS TURNING", "People are fighting back. The dead are losing ground.");
            warLead = 1;
        } else if (warBalance < 0.38f && warLead != -1) {
            if (warLead == 1) banner("THE DEAD ARE WINNING", "The city is slipping away. Every survivor counts now.");
            else if (outbreakTime > 30) banner("THE CITY IS IN DANGER", "The dead outnumber the living.");
            warLead = -1;
        }
    }

    void banner(String title, String sub) {
        highlight(title.charAt(0) + title.substring(1).toLowerCase(), controlled != null ? controlled.x : city.worldW() / 2,
                controlled != null ? controlled.y : city.worldH() / 2);
        warBanner = title;
        warSub = sub;
        warBannerTime = 6;
        bannerSeq++;
        dispatch.say(Dispatch.WHO_INFO, null, title.charAt(0) + title.substring(1).toLowerCase() + ". " + sub,
                city.worldW() / 2, city.worldH() / 2);
    }

    World(CityConfig cfg) {
        city = new City(cfg);
        Country.current = city.country;
        rollStrain();
        dispatch = new Dispatch(this, cfg.reinforcements());
        fleet = new Fleet(this);
        burned = new boolean[city.w * city.h];
        gw = (int) Math.ceil(city.worldW() / CELL);
        gh = (int) Math.ceil(city.worldH() / CELL);
        cellStart = new int[gw * gh + 1];
        zCount = new int[gw * gh];
        zFill = new int[gw * gh];
        cellCount = new int[gw * gh];
        cellFill = new int[gw * gh];
    }

    /** The Build tool: an edit that's remembered in the city code. */
    boolean build(float x, float y, int kind) {
        int tx = (int) (x / City.T), ty = (int) (y / City.T);
        if (kind == City.ED_HOUSE || kind == City.ED_SHOP) {
            tx -= kind == City.ED_HOUSE ? 1 : 1;
            ty -= 1;
        }
        for (int i = 0, n = entities.size(); i < n && kind != City.ED_CLEAR && kind != City.ED_GRASS; i++) {
            Entity e = entities.get(i);
            // Not on top of anyone.
            if (Math.abs(e.x - (tx * City.T + 8)) < 12 && Math.abs(e.y - (ty * City.T + 8)) < 12
                    && (kind == City.ED_WALL || kind == City.ED_TREES)) return false;
        }
        int before = city.buildings.size();
        byte old = city.tiles[Math.max(0, Math.min(city.tiles.length - 1, ty * city.w + tx))];
        City.Building b = city.applyEdit(tx, ty, kind, true);
        boolean changed = b != null || city.tiles[Math.max(0, Math.min(city.tiles.length - 1, ty * city.w + tx))] != old || kind == City.ED_CLEAR;
        if (!changed) return false;
        if (b != null) {
            b.residents = kind == City.ED_HOUSE ? 3 : 0;
            b.capacity = Math.max(b.capacity, 3);
        }
        city.cfg.edits.add(new int[]{tx, ty, kind});
        // Old routes may run through what's changed.
        city.walkCost = null;
        for (City.Building d : doorPaths) d.field = null;
        doorPaths.clear();
        errandSpots = null;
        return true;
    }

    void populate(CityConfig cfg) {
        scatterWeapons();
        stockArmouries();
        int people = Math.min(cfg.civilians(city.totalResidents), maxEntities * 4 / 5);
        residents(people);
        // A few strays.
        spawnRandom(Entity.DOG, people / 60);
        // A few civilians own a gun.
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.type == Entity.CIVILIAN && rnd.nextFloat() < 0.07f) armCivilian(e, 12 + rnd.nextInt(3) * 6);
        }
        City.Facility hospital = city.nearestFacility(City.FACILITY_HOSPITAL, 0, 0);
        // Medics: most at the hospital, and paramedics at each fire station so help is never far.
        java.util.ArrayList<City.Facility> fireStations = new java.util.ArrayList<City.Facility>();
        for (City.Facility f : city.facilities) if (f.kind == City.FACILITY_FIRE) fireStations.add(f);
        int medics = cfg.medics(city.totalResidents);
        for (int i = 0; i < medics; i++) {
            City.Facility at = hospital == null || (i % 2 == 1 && !fireStations.isEmpty()) ? (fireStations.isEmpty() ? null : fireStations.get(i / 2 % fireStations.size())) : hospital;
            if (at == null) break;
            spawn(Entity.MEDIC, at.x + rnd.nextFloat() * 40 - 20, at.y + rnd.nextFloat() * 40 - 20);
        }
        // A crew at every fire station: enough for the engine and a couple to spare.
        for (City.Facility f : fireStations)
            for (int i = 0; i < 5; i++) spawn(Entity.FIREFIGHTER, f.x + rnd.nextFloat() * 30 - 15, f.y + rnd.nextFloat() * 30 - 15);
        // Half the cops start at their precinct, the rest on patrol; soldiers start on base.
        City.Facility base = city.nearestFacility(City.FACILITY_BASE, 0, 0);
        int stations = 0;
        for (City.Facility f : city.facilities) if (f.kind == City.FACILITY_POLICE) stations++;
        int cops = cfg.cops(city.totalResidents), soldiers = cfg.soldiers(city.totalResidents);
        // Most officers are out in patrol cars, two to a car; the rest are at their precinct.
        int cars = stations > 0 ? Math.max(1, (int) (cops * 0.6f / 2)) : 0;
        fleet.patrolTarget = cars;
        int made = 0;
        City.Facility[] precincts = new City.Facility[Math.max(1, stations)];
        int pk = 0;
        for (City.Facility f : city.facilities) if (f.kind == City.FACILITY_POLICE) precincts[pk++] = f;
        for (int i = 0; i < cars; i++) if (fleet.startPatrol(precincts[i % precincts.length]) != null) made++;
        int copsAtStations = stations > 0 ? cops - made * 2 : 0;
        int k = 0;
        for (City.Facility f : city.facilities) {
            if (f.kind != City.FACILITY_POLICE) continue;
            int n = copsAtStations / stations + (k++ < copsAtStations % stations ? 1 : 0);
            for (int i = 0; i < n; i++) spawn(Entity.COP, f.x + rnd.nextFloat() * 40 - 20, f.y + rnd.nextFloat() * 40 - 20);
        }
        spawnRandom(Entity.COP, cops - copsAtStations - made * 2);
        fleet.stationEngines();
        // Out of town: highway patrol on the highway, and (in the USA) sheriff's deputies on the country roads.
        if (city.hwyAxis >= 0) for (int i = 0; i < (city.w > 300 ? 3 : 2); i++) fleet.startAgencyPatrol(1);
        if (city.country.ruralName != null && city.townX0 > 0) for (int i = 0; i < (city.w > 300 ? 3 : 2); i++) fleet.startAgencyPatrol(2);
        int firstSoldier = entities.size();
        if (base != null) {
            for (int i = 0; i < soldiers; i++)
                spawn(Entity.SOLDIER, base.x + rnd.nextFloat() * base.r - base.r / 2, base.y + rnd.nextFloat() * base.r - base.r / 2);
        } else {
            spawnRandom(Entity.SOLDIER, soldiers);
        }
        // Every fourth soldier carries the squad's machine gun, big garrisons have snipers, and a
        // commander runs the show.
        int snipers = soldiers >= 10 ? 2 : 0;
        for (int i = firstSoldier, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.type != Entity.SOLDIER) continue;
            if (e.member == 4) applyRole(e, Entity.ROLE_GUNNER);
            else if (e.member == 3 && snipers > 0) {
                applyRole(e, Entity.ROLE_SNIPER);
                snipers--;
            }
        }
        if (soldiers >= 5) {
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
        applyReadiness();
        spawnBirds();
        spawnWildlife();
        fleet.trafficTarget = Fleet.trafficFor(city);
        fleet.spawnTraffic(fleet.trafficTarget);
        // Zombies start in a few small outbreaks rather than spread evenly.
        int left = cfg.zombies();
        while (left > 0) {
            // Some zombies are shut inside buildings, waiting for someone to open the door.
            if (left >= 4 && rnd.nextFloat() < 0.25f) {
                City.Building b = city.buildings.get(rnd.nextInt(city.buildings.size()));
                if (b.capacity > 0 && b.occupants.isEmpty() && b.lurkers == 0) {
                    b.lurkers = Math.min(left, 2 + rnd.nextInt(4));
                    left -= b.lurkers;
                    continue;
                }
            }
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

    private Entity follower(Entity leader, int type) {
        Entity f = spawn(type, leader.x + rnd.nextFloat() * 10 - 5, leader.y + rnd.nextFloat() * 10 - 5);
        if (f != null) f.leader = leader;
        return f;
    }

    /**
     * Everyone lives somewhere. People are taken household by household from the city's homes: some are at
     * home, some out on errands, and some households out together (with the dog).
     */
    private void residents(int people) {
        ArrayList<City.Building> homes = new ArrayList<City.Building>();
        for (City.Building b : city.buildings) if (b.residents > 0) homes.add(b);
        java.util.Collections.shuffle(homes, rnd);
        int left = people;
        // Take a share of every household rather than whole households, so the whole city is lived in.
        float share = city.totalResidents > 0 ? Math.min(1f, people / (float) city.totalResidents) : 0;
        for (int pass = 0; pass < 3 && left > 0; pass++) {
            for (int h = 0; h < homes.size() && left > 0; h++) {
                City.Building b = homes.get(h);
                int n = pass == 0 ? Math.min(left, (int) (b.residents * share + rnd.nextFloat())) : Math.min(left, 1);
                if (n <= 0) continue;
                // The people living here, family by family (a flat or a house holds 1 to 5).
                while (n > 0) {
                    int size = Math.min(n, 1 + rnd.nextInt(b.kind == City.HOUSE ? 5 : 4));
                    n -= size;
                    boolean together = size >= 2 && rnd.nextFloat() < 0.3f;
                    boolean out = rnd.nextFloat() < 0.4f;
                    float[] p = out ? city.randomWalkableInTown(rnd) : new float[]{b.doorX + rnd.nextFloat() * 16 - 8, b.doorY + rnd.nextFloat() * 16 - 8};
                    Entity leader = null;
                    for (int k = 0; k < size; k++) {
                        Entity e;
                        if (together && leader != null) e = follower(leader, Entity.CIVILIAN);
                        else {
                            float[] q = k == 0 || together ? p : rnd.nextFloat() < 0.4f ? city.randomWalkableInTown(rnd)
                                    : new float[]{b.doorX + rnd.nextFloat() * 16 - 8, b.doorY + rnd.nextFloat() * 16 - 8};
                            e = spawn(Entity.CIVILIAN, q[0], q[1]);
                        }
                        left--;
                        if (e == null) continue;
                        e.home = b;
                        e.homeChecked = true;
                        if (leader == null) leader = e;
                    }
                    if (leader != null && rnd.nextFloat() < 0.1f) follower(leader, Entity.DOG);
                }
            }
        }
        // Everyone gets a life. Many start the day at work or at home, indoors; the rest are out doing
        // their thing, and those indoors come and go over the next few minutes.
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.dead || e.type != Entity.CIVILIAN) continue;
            assignJob(e);
            if (e.leader != null) continue;
            boolean worker = e.job == Entity.J_WORKER || e.job == Entity.J_SHOPKEEPER || e.job == Entity.J_STUDENT;
            if (worker && e.work != null && rnd.nextFloat() < 0.55f) {
                e.x = e.work.doorX;
                e.y = e.work.doorY;
                e.lastErrand = e.work;
                goInside(e, e.work, 10 + rnd.nextFloat() * (e.job == Entity.J_SHOPKEEPER ? 400 : 260));
                continue;
            }
            City.Building b = e.home;
            if (b == null) continue;
            boolean near = (e.x - b.doorX) * (e.x - b.doorX) + (e.y - b.doorY) * (e.y - b.doorY) <= 20 * 20;
            if (e.job == Entity.J_HOMEBODY && rnd.nextFloat() < 0.8f) near = true;
            if (!near) continue;
            e.x = b.doorX;
            e.y = b.doorY;
            e.lastErrand = b;
            goInside(e, b, 5 + rnd.nextFloat() * (e.job == Entity.J_HOMEBODY ? 500 : 240));
        }
        // A city with nowhere to live (or more people than homes): the rest find a place nearby.
        if (left > 0) spawnRandom(Entity.CIVILIAN, left);
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

    /** Redraws the map in the current Graphics setting, keeping the scorch marks. */
    void redrawCity() {
        city.redraw();
        for (int i = 0; i < burned.length; i++) if (burned[i]) city.charTile(i % city.w, i / city.w);
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
                if (st.kind != City.FACILITY_FIRE || fleet.idleEngine(st) == null) continue;
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
        // Keep the fires of burning buildings when trimming.
        if (fires.size() > 80)
            for (int i = 0; i < fires.size(); i++)
                if (fires.get(i).building == null) {
                    fires.remove(i);
                    break;
                }
    }

    /** Anyone standing just ahead of a car (so drivers can brake). */
    boolean personAhead(float x, float y, float angle) {
        float ax = x + (float) Math.cos(angle) * 16, ay = y + (float) Math.sin(angle) * 16;
        int cx0 = Math.max(0, (int) ((ax - 11) / CELL)), cx1 = Math.min(gw - 1, (int) ((ax + 11) / CELL));
        int cy0 = Math.max(0, (int) ((ay - 11) / CELL)), cy1 = Math.min(gh - 1, (int) ((ay + 11) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c] + zCount[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.isZombie()) continue;
                    float ddx = o.x - ax, ddy = o.y - ay;
                    if (ddx * ddx + ddy * ddy < 11 * 11) return true;
                }
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
                + counts[Entity.SCREAMER] + counts[Entity.ZOMBIE_DOG] + counts[Entity.SPITTER] + counts[Entity.BLOATER];
    }

    int humanCount() {
        return counts[Entity.CIVILIAN] + counts[Entity.COP] + counts[Entity.SOLDIER] + counts[Entity.MEDIC] + counts[Entity.FIREFIGHTER];
    }

    /** People sitting in cars right now. */
    int riding;

    private void spawnRandom(int type, int n) {
        for (int i = 0; i < n; i++) {
            // Where there's countryside, most people are in town.
            float[] p = rnd.nextFloat() < 0.85f ? city.randomWalkableInTown(rnd) : city.randomWalkable(rnd);
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

    /** A zombie to draw (the dead fighting inside a building), never part of the world. */
    Entity puppetZombie(int i) {
        Entity e = make(Entity.ZOMBIE, 0, 0, -1, 0);
        e.nameSeed = i * 7919 + 17;
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
            case Entity.FIREFIGHTER:
                e.hp = 70;
                e.radius = 3.9f;
                e.speed = 20;
                e.runSpeed = 38;
                e.body = 0xFF8A6A34;
                e.head = 0xFFC8302A;
                e.mass = 1.2f;
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
            // The strain.
            if (trait[TR_TOUGH]) e.hp *= 1.35f;
            if (trait[TR_FRAIL]) e.hp *= 0.75f;
            if (trait[TR_SWIFT]) {
                e.speed *= 1.12f;
                e.runSpeed *= 1.12f;
            }
        }
        if (type == Entity.COP || type == Entity.SOLDIER) e.hp *= ARMED_HP;
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
            case Entity.ROLE_GUARD:
                // Guardsmen: rifles and a little less training, in tan uniforms.
                e.hp = e.maxHp = 75;
                e.magSize = 30;
                e.reserve = 120;
                e.grenades = 1;
                e.body = 0xFF8C8260;
                e.head = 0xFF6A6248;
                break;
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
        spawnWildlife();
        fleet.trafficTarget = Fleet.trafficFor(city);
        fleet.stationEngines();
        int cops = 0;
        for (Entity e : entities) if (!e.dead && e.type == Entity.COP) cops++;
        fleet.patrolTarget = Math.max(1, (int) (cops * 0.6f / 2));
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
        for (Holdout h : holdouts) h.b.holdout = null;
        holdouts.clear();
        recount();
    }

    // ------------------------------------------------------------------ update

    void update(float dt) {
        pathBudget = Math.min(6, pathBudget + dt * 12);
        time += dt;
        shake = Math.max(0, shake - dt * 3);
        if (messageTime > 0) messageTime -= dt;
        if (warBannerTime > 0) warBannerTime -= dt;
        buildHash();
        fieldTimer -= dt;
        occTimer -= dt;
        if (fieldTimer <= 0) {
            fieldTimer = quality == Q_FULL ? 0.25f : quality == Q_REDUCED ? 0.4f : 0.6f;
            city.computeFields(entities);
        }
        dispatch.update(dt);
        if (controlled != null) {
            Fleet.Vehicle car = controlledCar;
            if (car != null && (!fleet.vehicles.contains(car) || car.player != controlled || !controlled.removed)) {
                if (car.player == controlled) car.player = null;
                controlledCar = null;
            }
            if (controlledIn != null && !controlledIn.occupants.contains(controlled)) controlledIn = null;
            if (controlledCar == null && controlledIn == null && (controlled.dead || controlled.removed)) {
                controlled = null;
                joyX = joyY = 0;
                ctrlAttack = false;
            }
        }
        recordReplay(dt);
        lodFrame++;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.dead) continue;
            // Far from any zombie and just going about their day: they decide what to do a third as often
            // (but still move every frame), which lets a city of thousands run smoothly.
            if (e.type == Entity.CIVILIAN && e.task == Dispatch.T_NONE && e.fleeTimer <= 0 && e != controlled && e.leader == null
                    && city.fieldAt(city.zombieDist, e.x, e.y) > 30) {
                // (On a struggling phone, those off screen even less often.)
                int every = quality == Q_FULL || onScreen(e) ? 3 : quality == Q_REDUCED ? 5 : 8;
                if ((i + lodFrame) % every != 0) continue;
                think(e, dt * every);
            } else if (quality > Q_FULL && e.isZombie() && e.charge <= 0 && e.noiseTimer <= 0 && e != controlled && !onScreen(e)
                    && city.fieldAt(city.humanDist, e.x, e.y) > 40) {
                // The same for the dead shuffling about off screen with nobody near.
                int every = quality == Q_REDUCED ? 2 : 3;
                if ((i + lodFrame) % every != 0) continue;
                think(e, dt * every);
            } else think(e, dt);
        }
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (!e.dead) move(e, dt);
        }
        separate();
        stampedeNews(dt);
        fleet.update(dt);
        updateFires(dt);
        updateBuildingFires(dt);
        updateFirebomb(dt);
        updateHazards(dt);
        if (alarmTime > 0) alarmTime -= dt;
        updateBirds(dt);
        for (int i = noiseRings.size() - 1; i >= 0; i--) {
            float[] r = noiseRings.get(i);
            r[3] += dt;
            if (r[3] > 1.4f) noiseRings.remove(i);
        }
        updateWildlife(dt);
        updateGrenades(dt);
        cleanup();
        updateCorpses(dt);
        updateBuildings(dt);
        updateHoldouts(dt);
        updateAftermath(dt);
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
        fleet.countCrews(counts);
    }

    void recount() {
        Arrays.fill(counts, 0);
        commanders.clear();
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            counts[e.type]++;
            if (e.role == Entity.ROLE_COMMANDER && e.type == Entity.SOLDIER) commanders.add(e);
        }
        fleet.countCrews(counts);
        hiding = 0;
        visiting = 0;
        civilians = counts[Entity.CIVILIAN];
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            hiding += b.occupants.size();
            visiting += b.visitors.size();
            for (int k = 0; k < b.occupants.size(); k++) if (b.occupants.get(k).type == Entity.CIVILIAN) civilians++;
            for (int k = 0; k < b.visitors.size(); k++) if (b.visitors.get(k).type == Entity.CIVILIAN) civilians++;
        }
        for (int i = 0, n = fleet.vehicles.size(); i < n; i++) {
            ArrayList<Entity> r = fleet.vehicles.get(i).riders;
            for (int k = 0; k < r.size(); k++) if (r.get(k).type == Entity.CIVILIAN) civilians++;
        }
        riding = fleet.riderCount();
        int h = humanCount() + hiding + riding + visiting;
        int z = zombieCount();
        peakZombies = Math.max(peakZombies, z);
        int rising = 0;
        for (int i = 0, n = corpses.size(); i < n; i++) if (corpses.get(i).rise > 0) rising++;
        lurking = 0;
        for (int i = 0, n = city.buildings.size(); i < n; i++) lurking += city.buildings.get(i).lurkers;
        if ((z > 0 || lurking > 0) && h > 0 && !outbreak && aftermath != AFTER_FALLEN) {
            if (aftermath == AFTER_RECOVERY) {
                // The dead are back while the city was getting on its feet again.
                aftermath = AFTER_NONE;
                warResult = 0;
                banner("THE DEAD ARE BACK", "Just as the city was getting back on its feet.");
            }
            outbreak = true;
            startHumans = h;
            warLead = 0;
            warBalance = 0.5f;
        }
        startHumans = Math.max(startHumans, h);
        if (outbreak) {
            int bitten = 0, armed = counts[Entity.COP] + counts[Entity.SOLDIER];
            for (int i = 0, n = entities.size(); i < n; i++) if (entities.get(i).infected) bitten++;
            // (Including the bitten hiding indoors.)
            for (int i = 0, n = city.buildings.size(); i < n; i++) {
                City.Building b = city.buildings.get(i);
                for (int k = 0; k < b.occupants.size(); k++) if (b.occupants.get(k).infected) bitten++;
                for (int k = 0; k < b.visitors.size(); k++) if (b.visitors.get(k).infected) bitten++;
            }
            // Over only once it's stayed over for a few seconds.
            boolean clear = z == 0 && rising == 0 && lurking == 0 && bitten == 0 && h > 0;
            if (!clear) clearSince = -1;
            else if (clearSince < 0) clearSince = time;
            if (clear && time - clearSince >= 4) {
                outbreak = false;
                warResult = 1;
                int t = (int) outbreakTime;
                banner(h < startHumans * 0.35f ? "THE CITY SURVIVES, BARELY" : "THE CITY SURVIVES", "The outbreak is over after "
                        + t / 60 + ":" + (t % 60 < 10 ? "0" : "") + t % 60 + ". " + zombiesKilled + " zombies destroyed, "
                        + civiliansLost + " civilians lost.");
                startAftermath(AFTER_RECOVERY);
            } else if (h == 0 || (h <= startHumans * FALLEN && z > h) || (armed == 0 && h <= Math.max(5, startHumans / 12) && z > h)) {
                outbreak = false;
                warResult = 2;
                banner("THE CITY HAS FALLEN", h == 0 ? "Nobody is left alive." : "Only " + h + (h == 1 ? " survivor is" : " survivors are")
                        + " left, hiding in the ruins.");
                if (h > 0) startAftermath(AFTER_FALLEN);
            }
        }
        humans = h;
        zombies = z + rising + lurking;
    }

    void say(String msg) {
        message = msg;
        messageTime = 4f;
    }

    private void buildHash() {
        int n = entities.size();
        if (sorted.length < n) sorted = new Entity[n * 2];
        Arrays.fill(cellCount, 0);
        Arrays.fill(zCount, 0);
        hashFrame++;
        for (int i = 0; i < n; i++) {
            Entity e = entities.get(i);
            if (e.leader != null && !e.dead) e.leader.followerFrame = hashFrame;
            int c = cellOf(e);
            cellCount[c]++;
            if (e.isZombie()) zCount[c]++;
        }
        // Each cell lists its zombies first, then everyone else, so a search for one or the other only looks at
        // what it wants (a crowd of thousands doesn't slow down looking for the one zombie among them).
        int acc = 0;
        for (int c = 0; c < cellCount.length; c++) {
            cellStart[c] = acc;
            zFill[c] = acc;
            cellFill[c] = acc + zCount[c];
            acc += cellCount[c];
        }
        cellStart[cellCount.length] = acc;
        for (int i = 0; i < n; i++) {
            Entity e = entities.get(i);
            int c = cellOf(e);
            if (e.isZombie()) sorted[zFill[c]++] = e;
            else sorted[cellFill[c]++] = e;
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
                for (int k = wantZombie ? cellStart[c] : cellStart[c] + zCount[c], end = wantZombie ? cellStart[c] + zCount[c] : cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || o.isZombie() != wantZombie || o == from) continue;
                    float ddx = o.x - from.x, ddy = o.y - from.y, d2 = ddx * ddx + ddy * ddy;
                    if (o.hidden && d2 > 22 * 22) continue;
                    if (d2 < best && (!needLos || city.los(from.x, from.y, o.x, o.y))) {
                        best = d2;
                        res = o;
                    }
                }
            }
        }
        return res;
    }

    /** Any of the dead within radius? Quick when there's none within a walk of here (the usual case). */
    boolean zombieWithin(float x, float y, float radius) {
        if (city.fieldAt(city.zombieDist, x, y) > radius / City.T + 3) return false;
        return countZombiesNear(x, y, radius) > 0;
    }

    int countZombiesNear(float x, float y, float radius) {
        int count = 0;
        int cx0 = Math.max(0, (int) ((x - radius) / CELL)), cx1 = Math.min(gw - 1, (int) ((x + radius) / CELL));
        int cy0 = Math.max(0, (int) ((y - radius) / CELL)), cy1 = Math.min(gh - 1, (int) ((y + radius) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + zCount[c]; k < end; k++) {
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
        if (e.knockX != 0 || e.knockY != 0) {
            float f = Math.max(0, 1 - dt * 4);
            e.knockX *= f;
            e.knockY *= f;
            if (Math.abs(e.knockX) + Math.abs(e.knockY) < 3) e.knockX = e.knockY = 0;
        }
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
        if (e == controlled) {
            e.stuckTime = 0;
            e.unstick = 0;
            thinkControlled(e, dt);
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
        if (e.task == Dispatch.T_BOARD && boardStep(e)) return;
        if (e.isZombie()) thinkZombie(e, dt);
        else if (e.isArmed()) thinkArmed(e, dt);
        else if (e.type == Entity.MEDIC) thinkMedic(e, dt);
        else if (e.type == Entity.FIREFIGHTER) thinkFirefighter(e, dt);
        else if (e.type == Entity.DOG) thinkDog(e, dt);
        else if (e.type == Entity.RAIDER) thinkRaider(e, dt);
        else thinkCivilian(e, dt);
    }

    /** Makes zombies within a radius head for a noise (gunfire, explosions, a screamer). */
    void noise(float x, float y, float radius) {
        scareBirds(x, y, radius);
        boolean near = false;
        for (int i = 0; i < noiseRings.size() && !near; i++) {
            float[] r = noiseRings.get(i);
            if (r[3] < 0.5f && Math.abs(r[0] - x) < 40 && Math.abs(r[1] - y) < 40) near = true;
        }
        if (!near && noiseRings.size() < 60) noiseRings.add(new float[]{x, y, radius, 0});
        int cx0 = Math.max(0, (int) ((x - radius) / CELL)), cx1 = Math.min(gw - 1, (int) ((x + radius) / CELL));
        int cy0 = Math.max(0, (int) ((y - radius) / CELL)), cy1 = Math.min(gh - 1, (int) ((y + radius) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + zCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || !o.isZombie()) continue;
                    float ddx = o.x - x, ddy = o.y - y;
                    if (ddx * ddx + ddy * ddy > radius * radius) continue;
                    // Already there and found nothing: it doesn't keep them hanging around.
                    if (ddx * ddx + ddy * ddy < 45 * 45 && o.memory <= 0) continue;
                    o.noiseX = x + rnd.nextFloat() * 30 - 15;
                    o.noiseY = y + rnd.nextFloat() * 30 - 15;
                    o.noiseTimer = 14;
                }
                // People who hear it know something is wrong (once there's anything to be wrong about).
                if (zombies > 0)
                    for (int k = cellStart[c] + zCount[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                        Entity o = sorted[k];
                        if (o.dead || o.type != Entity.CIVILIAN || o.aware) continue;
                        float ddx = o.x - x, ddy = o.y - y;
                        if (ddx * ddx + ddy * ddy < radius * radius * 0.6f) o.aware = true;
                    }
            }
    }

    private void thinkZombie(Entity z, float dt) {
        if (z.blocked && !barriers.isEmpty()) bashBarrier(z, dt);
        z.chargeCd -= dt;
        if (z.charge > 0) {
            charging(z, dt);
            return;
        }
        z.noiseTimer -= dt;
        z.screamCd -= dt;
        z.feedTimer -= dt;
        z.memory -= dt;
        z.searchTimer -= dt;
        z.moanCd -= dt;
        if (rnd.nextFloat() < dt * 0.03f) emit(z.type == Entity.BRUTE ? Sfx.GROAN_DEEP : Sfx.GROAN, z.x, z.y);
        // Senses: they see a long way in front, but only hear or smell what is close behind them. Once they
        // have spotted someone they keep track of them.
        float sight = trait[TR_KEEN] ? 180 : trait[TR_DULL] ? 100 : 140, near = trait[TR_KEEN] ? 75 : trait[TR_DULL] ? 35 : 55;
        Entity t = nearest(z, sight, false, true);
        if (t != null && z.memory <= 0) {
            float ddx = t.x - z.x, ddy = t.y - z.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            float facing = ((float) Math.cos(z.angle) * ddx + (float) Math.sin(z.angle) * ddy) / d;
            if (d > near && facing < 0.1f && t.want < t.runSpeed * 0.9f) t = null;
        }
        if (t != null) {
            z.hidden = false;
            // A brute lowers its head and charges.
            if (z.type == Entity.BRUTE && z.chargeCd <= 0) {
                float cdx = t.x - z.x, cdy = t.y - z.y, cd = (float) Math.sqrt(cdx * cdx + cdy * cdy) + 0.001f;
                if (cd > 35 && cd < 130 && city.los(z.x, z.y, t.x, t.y)) {
                    z.charge = 1.3f;
                    z.chargeCd = 7 + rnd.nextFloat() * 4;
                    z.chargeX = cdx / cd;
                    z.chargeY = cdy / cd;
                    emit(Sfx.GROAN_DEEP, z.x, z.y);
                    return;
                }
            }
            z.lastX = t.x + t.vx * 0.8f;
            z.lastY = t.y + t.vy * 0.8f;
            z.memory = 7;
            z.searchTimer = 0;
            // The moan: other zombies nearby hear it and come to join the hunt.
            if (z.moanCd <= 0) {
                z.moanCd = 4 + rnd.nextFloat() * 2;
                herd(z, t.x, t.y);
            }
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
        // Lost sight of them: go to where they were last seen, then search around there for a while.
        if (z.memory > 0) {
            float ddx = z.lastX - z.x, ddy = z.lastY - z.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d < 14 || (z.blocked && d < 40)) {
                z.memory = 0;
                z.searchTimer = 5 + rnd.nextFloat() * 4;
                z.blocked = false;
            } else {
                if (city.los(z.x, z.y, z.lastX, z.lastY) || !followField(z, city.humanDist, z.speed * 1.2f))
                    steer(z, ddx / d, ddy / d, z.speed * 1.25f);
                return;
            }
        }
        if (z.searchTimer > 0) {
            if (z.wanderTimer <= 0) {
                z.wanderTimer = 0.8f + rnd.nextFloat();
                z.wanderAngle = rnd.nextFloat() * TAU;
            }
            steer(z, (float) Math.cos(z.wanderAngle), (float) Math.sin(z.wanderAngle), z.speed * 0.7f);
            if (z.blocked) {
                z.blocked = false;
                z.wanderTimer = 0;
            }
            return;
        }
        if (z.taskTimer <= 0) {
            z.taskTimer = 1.5f + rnd.nextFloat();
            if (z.noiseTimer <= 0) mischief(z);
            if (z.dead) return;
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
        if (occGrid == null || occTimer <= 0) buildOccGrid();
        int ocx = (int) (z.x / OCC_CELL), ocy = (int) (z.y / OCC_CELL);
        for (int gy = Math.max(0, ocy - 1); gy <= Math.min(occGH - 1, ocy + 1); gy++)
            for (int gx = Math.max(0, ocx - 1); gx <= Math.min(occGW - 1, ocx + 1); gx++) {
                java.util.ArrayList<City.Building> cell = occGrid[gy * occGW + gx];
                if (cell == null) continue;
                for (int i = 0, n = cell.size(); i < n; i++) {
                    City.Building b = cell.get(i);
                    if (b.occupants.isEmpty()) continue;
                    float ddx = b.doorX - z.x, ddy = b.doorY - z.y, d2 = ddx * ddx + ddy * ddy;
                    if (d2 < best) {
                        best = d2;
                        target = b;
                    }
                }
            }
        if (target != null) {
            float ddx = target.doorX - z.x, ddy = target.doorY - z.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            // At the door, or pressed up behind the others at it: batter it.
            boolean atDoor = d < 14 || (d < 30 && (z.blocked || countZombiesNear(target.doorX, target.doorY, 16) >= 2));
            if (atDoor) {
                if (d > 9 && !z.blocked) steer(z, ddx / d, ddy / d, z.speed * 0.5f);
                else steer(z, 0, 0, 0);
                z.angle = turn(z.angle, (float) Math.atan2(ddy, ddx), dt * 4);
                float dps = (z.type == Entity.BRUTE ? 22 : z.type == Entity.CRAWLER ? 3 : 7) * (d < 14 ? 1 : 0.5f);
                // (A group's fortified door holds a lot longer.)
                if (target.holdout != null) dps *= 1 - target.holdout.fort * 0.006f;
                target.barricade -= dps * dt;
                target.calmTimer = 0;
                if (target.barricade < 70 && isShop(target)) smash(target);
                if (rnd.nextFloat() < dt * 1.2f) emit(Sfx.THUD, target.doorX, target.doorY);
                return;
            }
            // Find the way round to the door (not straight into the wall): every zombie at this building
            // shares one route.
            DoorRoute route = doorField(target);
            if (route != null && followDoor(z, route, z.speed * 1.1f)) return;
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
        if (z.noiseTimer > 0 && z.type == Entity.SCREAMER && z.screamCd <= 0) {
            // A screamer that hears gunfire shrieks, and the whole neighbourhood of the dead comes with it.
            z.screamCd = 12;
            noise(z.noiseX, z.noiseY, 380);
            emit(Sfx.SHRIEK, z.x, z.y);
            screams.add(new float[]{z.x, z.y, 0});
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
        // Hunger draws the dead towards the living they can smell nearby.
        int dist = city.fieldAt(city.humanDist, z.x, z.y);
        if (dist < SCENT && followField(z, city.humanDist, z.speed)) return;
        if (z.leadsHorde) {
            roam(z);
            return;
        }
        if (z.type == Entity.CRAWLER && z.hordeLeader == null) {
            if (!z.hidden && rnd.nextFloat() < dt * 0.3f && coverAt(z.x, z.y)) z.hidden = true;
            if (z.hidden) {
                steer(z, 0, 0, 0);
                return;
            }
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

    /**
     * What an idle zombie gets up to around town: bumping into parked cars (setting off their alarms), putting in
     * shop windows, and shuffling into an empty building to wait in the dark.
     */
    private void mischief(Entity z) {
        for (int i = 0, n = fleet.vehicles.size(); i < n; i++) {
            Fleet.Vehicle v = fleet.vehicles.get(i);
            if (v.type != Fleet.CAR || !v.parked || v.broken || v.alarm > 0) continue;
            if (Math.abs(v.x - z.x) < 14 && Math.abs(v.y - z.y) < 14 && rnd.nextFloat() < 0.3f) {
                v.alarm = 8 + rnd.nextFloat() * 4;
                noise(v.x, v.y, 180);
                return;
            }
        }
        for (int k = 0; k < 4; k++) {
            City.Building b = city.buildingAt(z.x + (k == 0 ? City.T : k == 1 ? -City.T : 0), z.y + (k == 2 ? City.T : k == 3 ? -City.T : 0));
            if (b == null || Math.abs(b.doorX - z.x) > 16 || Math.abs(b.doorY - z.y) > 16) continue;
            if (isShop(b) && !b.smashed && rnd.nextFloat() < 0.12f) {
                smash(b);
                return;
            }
            if (b.capacity > 0 && b.occupants.isEmpty() && b.lurkers < 5 && z.type != Entity.BRUTE && rnd.nextFloat() < 0.035f) {
                b.lurkers++;
                z.dead = true;
                z.removed = true;
                return;
            }
        }
    }

    /** A hunting zombie's moan draws in idle zombies nearby that haven't found anyone yet. */
    private void herd(Entity z, float x, float y) {
        float r = 90;
        int cx0 = Math.max(0, (int) ((z.x - r) / CELL)), cx1 = Math.min(gw - 1, (int) ((z.x + r) / CELL));
        int cy0 = Math.max(0, (int) ((z.y - r) / CELL)), cy1 = Math.min(gh - 1, (int) ((z.y + r) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + zCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o == z || o.dead || !o.isZombie() || o.memory > 0 || o.noiseTimer > 0) continue;
                    if ((o.x - z.x) * (o.x - z.x) + (o.y - z.y) * (o.y - z.y) > r * r) continue;
                    o.noiseX = x + rnd.nextFloat() * 20 - 10;
                    o.noiseY = y + rnd.nextFloat() * 20 - 10;
                    o.noiseTimer = 6;
                }
            }
        if (rnd.nextFloat() < 0.3f) emit(z.type == Entity.BRUTE ? Sfx.GROAN_DEEP : Sfx.GROAN, z.x, z.y);
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
                City.District dist = city.districtOf(z.x, z.y);
                dispatch.say(Dispatch.WHO_INFO, null, "A horde of about " + (size / 5 * 5) + " zombies is moving through "
                        + (dist != null ? dist.name + " (" + city.placeName(z.x, z.y) + ")" : city.placeName(z.x, z.y)) + ".", z.x, z.y);
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

    /** A brute's charge: straight ahead, bowling people over, until it hits something. */
    private void charging(Entity z, float dt) {
        z.charge -= dt;
        steer(z, z.chargeX, z.chargeY, z.runSpeed * 2.4f);
        z.angle = (float) Math.atan2(z.chargeY, z.chargeX);
        int cx0 = Math.max(0, (int) ((z.x - 14) / CELL)), cx1 = Math.min(gw - 1, (int) ((z.x + 14) / CELL));
        int cy0 = Math.max(0, (int) ((z.y - 14) / CELL)), cy1 = Math.min(gh - 1, (int) ((z.y + 14) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c] + zCount[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || o.stun > 0) continue;
                    float r = z.radius + o.radius + 2;
                    if ((o.x - z.x) * (o.x - z.x) + (o.y - z.y) * (o.y - z.y) > r * r) continue;
                    o.stun = 1.5f;
                    o.hp -= 20;
                    o.hurt = 1;
                    o.killedByZombie = true;
                    o.knockX = z.chargeX * 120 / o.mass;
                    o.knockY = z.chargeY * 120 / o.mass;
                    tryMove(o, z.chargeX * 14 / o.mass, z.chargeY * 14 / o.mass);
                    emit(Sfx.THUD, o.x, o.y);
                }
            }
        if (z.blocked) {
            // Slammed into something: barricades and cars take a beating; the brute is dazed for a moment.
            z.blocked = false;
            z.charge = 0;
            z.stun = 0.6f;
            if (!barriers.isEmpty()) for (int k = 0; k < 6; k++) bashBarrier(z, 0.5f);
            Fleet.Vehicle car = carFor(z, 20);
            if (car != null) fleet.damage(car, 40, false);
            shake = Math.max(shake, 0.3f);
            emit(Sfx.CRASH, z.x, z.y);
        }
    }

    /** Somewhere a crawler can lie low: beside a parked car, or in long grass and under trees. */
    private boolean coverAt(float x, float y) {
        int tx = (int) (x / City.T), ty = (int) (y / City.T);
        for (int oy = -1; oy <= 1; oy++)
            for (int ox = -1; ox <= 1; ox++) {
                int i = (ty + oy) * city.w + tx + ox;
                if (i < 0 || i >= city.tiles.length) continue;
                byte t = city.tiles[i];
                if (t == City.CAR || t == City.TREE) return true;
            }
        byte t = city.tiles[Math.max(0, Math.min(city.tiles.length - 1, ty * city.w + tx))];
        return t == City.GRASS && rnd.nextFloat() < 0.5f;
    }

    // ------------------------------------------------------------------ wildlife

    /** Deer and foxes out in the country: they bolt when the dead come, and foxes can be infected. */
    static final class Animal {
        float x, y, vx, vy, angle, timer, flee;
        int kind;
        boolean dead;
    }

    final ArrayList<Animal> animals = new ArrayList<Animal>();
    private float wildlifeSaidCd;

    void spawnWildlife() {
        animals.clear();
        if (city.settlements.isEmpty() && city.townX0 <= 0) return;
        int herds = city.w * city.h / 2600;
        for (int h = 0; h < herds; h++) {
            float[] p = null;
            for (int t = 0; t < 30 && p == null; t++) {
                int tx = rnd.nextInt(city.w), ty = rnd.nextInt(city.h);
                if (tx >= city.townX0 && tx < city.townX1 && ty >= city.townY0 && ty < city.townY1) continue;
                if (city.tiles[ty * city.w + tx] != City.GRASS) continue;
                p = new float[]{tx * City.T + 8, ty * City.T + 8};
            }
            if (p == null) continue;
            boolean fox = rnd.nextFloat() < 0.35f;
            int n = fox ? 1 : 2 + rnd.nextInt(4);
            for (int k = 0; k < n; k++) {
                Animal a = new Animal();
                a.kind = fox ? 1 : 0;
                a.x = p[0] + rnd.nextFloat() * 20 - 10;
                a.y = p[1] + rnd.nextFloat() * 20 - 10;
                a.angle = rnd.nextFloat() * TAU;
                animals.add(a);
            }
        }
    }

    private void updateWildlife(float dt) {
        wildlifeSaidCd -= dt;
        for (int i = animals.size() - 1; i >= 0; i--) {
            Animal a = animals.get(i);
            if (a.dead) {
                animals.remove(i);
                continue;
            }
            a.timer -= dt;
            a.flee -= dt;
            // Anything coming? The dead from a long way off, people closer.
            // (Nothing dead within a walk of here at all: no need to look.)
            Entity z = city.fieldAt(city.zombieDist, a.x, a.y) > 130 / City.T + 4 ? null : nearestZombie(a.x, a.y, 130);
            if (z != null) {
                float dx = a.x - z.x, dy = a.y - z.y, d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
                if (d < 6) {
                    // Caught. A fox comes back as one of them.
                    a.dead = true;
                    if (a.kind == 1 && rnd.nextFloat() < 0.6f) spawn(Entity.ZOMBIE_DOG, a.x, a.y);
                    else bloodBurst(a.x, a.y, 4, 0, 0);
                    continue;
                }
                if (a.flee <= 0 && a.kind == 0 && wildlifeSaidCd <= 0) {
                    // Deer bolting: an early warning.
                    wildlifeSaidCd = 60;
                    City.District dd = city.districtOf(a.x, a.y);
                    dispatch.say(Dispatch.WHO_INFO, null, "Deer are bolting out of the woods near " + (dd != null ? dd.name : city.placeName(a.x, a.y))
                            + ". Something is coming.", a.x, a.y);
                }
                a.flee = 3;
                a.vx = dx / d;
                a.vy = dy / d;
            } else if (a.flee <= 0 && rnd.nextFloat() < dt * 0.5f && peopleNear(a.x, a.y, 50)) {
                a.flee = 1.5f;
                a.angle = rnd.nextFloat() * TAU;
                a.vx = (float) Math.cos(a.angle);
                a.vy = (float) Math.sin(a.angle);
            }
            float speed;
            if (a.flee > 0) speed = a.kind == 0 ? 95 : 70;
            else {
                // Grazing: a few steps now and then.
                if (a.timer <= 0) {
                    a.timer = 2 + rnd.nextFloat() * 5;
                    float ang = rnd.nextFloat() * TAU;
                    a.vx = rnd.nextFloat() < 0.5f ? 0 : (float) Math.cos(ang);
                    a.vy = a.vx == 0 ? 0 : (float) Math.sin(ang);
                }
                speed = a.kind == 0 ? 8 : 12;
            }
            float nx = a.x + a.vx * speed * dt, ny = a.y + a.vy * speed * dt;
            if (!city.circleBlocked(nx, ny, 3) && nx > 4 && ny > 4 && nx < city.worldW() - 4 && ny < city.worldH() - 4) {
                a.x = nx;
                a.y = ny;
            } else {
                a.vx = -a.vx;
                a.vy = -a.vy;
            }
            if (a.vx != 0 || a.vy != 0) a.angle = (float) Math.atan2(a.vy, a.vx);
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
                for (int k = cellStart[c], end = cellStart[c] + zCount[c]; k < end; k++) {
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

    private float hordeLaneSaid;

    private void pickRoam(Entity z) {
        if (z.leadsHorde && city.settlements.size() > 1 && rnd.nextFloat() < 0.5f) {
            // Off down the lanes to another settlement.
            float[] here = null, there;
            float bd = Float.MAX_VALUE;
            for (float[] s : city.settlements) {
                float d = (s[0] - z.x) * (s[0] - z.x) + (s[1] - z.y) * (s[1] - z.y);
                if (d < bd) {
                    bd = d;
                    here = s;
                }
            }
            do there = city.settlements.get(rnd.nextInt(city.settlements.size()));
            while (there == here);
            float[] p = city.findWalkable(there[0] + rnd.nextFloat() * 60 - 30, there[1] + rnd.nextFloat() * 60 - 30);
            if (p != null) {
                z.roamX = p[0];
                z.roamY = p[1];
                if (z.hordeSize >= 8 && time > hordeLaneSaid) {
                    hordeLaneSaid = time + 45;
                    City.District d = city.districtOf(p[0], p[1]);
                    dispatch.say(Dispatch.WHO_INFO, null, "A horde of about " + Math.max(5, z.hordeSize / 5 * 5) + " is heading out along the lanes towards "
                            + (d != null ? d.name : city.placeName(p[0], p[1])) + ".", z.x, z.y);
                }
                return;
            }
        }
        float a = rnd.nextFloat() * TAU, r = 200 + rnd.nextFloat() * 400;
        float[] p = city.findWalkable(Math.max(20, Math.min(city.worldW() - 20, z.x + (float) Math.cos(a) * r)),
                Math.max(20, Math.min(city.worldH() - 20, z.y + (float) Math.sin(a) * r)));
        if (p == null) p = city.randomWalkable(rnd);
        z.roamX = p[0];
        z.roamY = p[1];
    }

    /** A horde leader shambles towards a far-off spot, or towards people it can smell. */
    private void roam(Entity z) {
        if (city.fieldAt(city.humanDist, z.x, z.y) < SCENT * 1.7f && followField(z, city.humanDist, z.speed * 0.55f)) return;
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
            if (Fleet.airborne(v) || v.type == Fleet.TRAIN || !v.occupied()) continue;
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
        bites++;
        if (!bitBy.containsKey(t.nameSeed) && bitBy.size() < 20000) bitBy.put(t.nameSeed, z.nameSeed);
        float chance = (trait[TR_VIRULENT] ? 0.65f : trait[TR_WEAK_BITE] ? 0.22f : 0.4f) * BITE_INFECT;
        if (!t.infected && t.type != Entity.DOG && rnd.nextFloat() < chance) {
            t.infected = true;
            t.infectTimer = turnTime(12 + rnd.nextFloat() * 14);
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

    // ------------------------------------------------------------------ taking control

    /** The one the player is controlling (their AI is off), or null; and the stick and attack button. */
    Entity controlled;
    float joyX, joyY;
    boolean ctrlAttack;

    /** The player's hands: walk or run with the stick; the attack button shoots, bites or shoves. */
    private void thinkControlled(Entity e, float dt) {
        if (e.reload > 0) {
            e.reload -= dt;
            if (e.reload <= 0) {
                int take = Math.min(e.magSize - e.ammo, e.reserve);
                e.ammo += take;
                e.reserve -= take;
            }
        }
        e.task = Dispatch.T_NONE;
        e.building = null;
        e.errand = null;
        e.aiming = false;
        float mag = Math.min(1, (float) Math.sqrt(joyX * joyX + joyY * joyY));
        if (mag < 0.12f) steer(e, 0, 0, 0);
        else {
            // Push the stick all the way to run.
            float speed = mag > 0.85f ? e.runSpeed : e.speed * (0.35f + 0.65f * mag / 0.85f);
            steer(e, joyX / mag, joyY / mag, speed);
        }
        // Walk over something to pick it up.
        Pickup p = nearestPlayerPickup(e);
        if (p != null) takePickup(e, p);
        if (!ctrlAttack) return;
        if (e.isZombie()) {
            Entity t = nearest(e, e.radius + 14, false, false);
            if (t == null) return;
            float ddx = t.x - e.x, ddy = t.y - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            e.angle = (float) Math.atan2(ddy, ddx);
            if (d < e.radius + t.radius + 4 && e.biteCd <= 0) bite(e, t, ddx / d, ddy / d);
        } else if (e.canShoot() && e.ammo + e.reserve > 0) {
            Entity t = aimTarget(e, 210);
            if (t != null) {
                float d = (float) Math.hypot(t.x - e.x, t.y - e.y);
                aimAndFire(e, t, d, 210, dt);
            }
        } else {
            Entity z = nearestInReach(e);
            if (z == null && e.melee > 0) z = nearest(e, e.radius + 12, true, false);
            if (z != null && e.meleeCd <= 0) {
                if (e.melee > 0 || e.type == Entity.FIREFIGHTER) swing(e, z);
                else shove(e, z, e.type == Entity.COP || e.type == Entity.SOLDIER || e.type == Entity.RAIDER);
            }
        }
    }

    // ------------------------------------------------------------------ weapons

    static int magFor(int kind) {
        return kind == Entity.W_SHOTGUN ? 6 : kind == Entity.W_RIFLE ? 30 : 12;
    }

    /** Puts a particular gun in someone's hands. */
    void setGun(Entity e, int kind, int rounds) {
        if (!e.isArmed()) e.hasGun = true;
        e.weapon = kind;
        e.magSize = magFor(kind);
        e.ammo = Math.min(e.magSize, rounds);
        e.reserve = Math.max(0, rounds - e.ammo);
        e.reload = 0;
    }

    /** A bat or an axe: a hard blow that stuns, and sometimes splits a skull. */
    private void swing(Entity e, Entity z) {
        boolean axe = e.melee == Entity.M_AXE || (e.melee == Entity.M_NONE && e.type == Entity.FIREFIGHTER);
        e.meleeCd = axe ? 0.8f : 0.6f;
        float ddx = z.x - e.x, ddy = z.y - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        e.angle = (float) Math.atan2(ddy, ddx);
        if (z.isZombie() && z.type != Entity.BRUTE && rnd.nextFloat() < (axe ? 0.25f : 0.1f)) z.hp = 0;
        else z.hp -= axe ? 36 : 22;
        z.hurt = 1;
        z.stun = Math.max(z.stun, z.type == Entity.BRUTE ? 0.2f : axe ? 0.4f : 0.8f);
        tryMove(z, ddx / d * (axe ? 5 : 9) / z.mass, ddy / d * (axe ? 5 : 9) / z.mass);
        bloodBurst(z.x, z.y, 3, ddx / d, ddy / d);
        emit(Sfx.THUD, z.x, z.y);
        if (z.hp <= 0 && z.isZombie()) e.kills++;
    }

    /** Shotgun pellets catch whoever is standing next to the target too. */
    private void pellets(Entity e, Entity t, float dmg) {
        int cx0 = Math.max(0, (int) ((t.x - 16) / CELL)), cx1 = Math.min(gw - 1, (int) ((t.x + 16) / CELL));
        int cy0 = Math.max(0, (int) ((t.y - 16) / CELL)), cy1 = Math.min(gh - 1, (int) ((t.y + 16) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + zCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o == t || o.dead) continue;
                    if ((o.x - t.x) * (o.x - t.x) + (o.y - t.y) * (o.y - t.y) > 16 * 16) continue;
                    o.hp -= dmg * 0.4f * GUN_DAMAGE;
                    o.hurt = 1;
                    if (o.hp <= 0) e.kills++;
                }
            }
    }

    private void dropMelee(Entity e, int kind) {
        Pickup p = new Pickup();
        p.x = e.x + rnd.nextFloat() * 6 - 3;
        p.y = e.y + rnd.nextFloat() * 6 - 3;
        p.melee = kind;
        p.lockUntil = time + 2;
        pickups.add(p);
    }

    private Pickup nearestPlayerPickup(Entity e) {
        if (e.isZombie() || e.type == Entity.DOG) return null;
        for (int i = 0, n = pickups.size(); i < n; i++) {
            Pickup p = pickups.get(i);
            if (p.drop > 0 || p.lockUntil > time) continue;
            if ((p.x - e.x) * (p.x - e.x) + (p.y - e.y) * (p.y - e.y) < 10 * 10) return p;
        }
        return null;
    }

    /** The player picks something up: ammo for the gun they have, or swaps for what's lying there. */
    private void takePickup(Entity e, Pickup p) {
        if (p.uses > 0) {
            // A supply crate: ammo (and a gun if they have none).
            if (!e.canShoot()) setGun(e, Entity.W_PISTOL, 30);
            else e.reserve += 30;
            p.lockUntil = time + 4;
            if (--p.uses <= 0) pickups.remove(p);
            say("Ammo from the crate");
            return;
        }
        pickups.remove(p);
        if (p.melee > 0) {
            if (e.melee > 0) dropMelee(e, e.melee);
            e.melee = p.melee;
            say("Picked up " + (p.melee == Entity.M_AXE ? "an axe" : "a bat"));
            return;
        }
        int kind = p.weapon == Entity.W_STD ? Entity.W_PISTOL : p.weapon;
        if (e.canShoot() && e.gunKind() == kind) {
            e.reserve += p.rounds;
            say("+" + p.rounds + " rounds");
            return;
        }
        if (e.canShoot() && e.ammo + e.reserve > 0) {
            Pickup old = new Pickup();
            old.x = e.x;
            old.y = e.y;
            old.rounds = e.ammo + e.reserve;
            old.weapon = e.gunKind();
            old.lockUntil = time + 2;
            pickups.add(old);
        }
        setGun(e, kind, Math.max(p.rounds, magFor(kind)));
        say("Picked up a " + Entity.WEAPON_NAMES[kind].toLowerCase());
    }

    /** Every precinct and base starts with a limited supply of ammunition. */
    void stockArmouries() {
        for (City.Facility f : city.facilities) {
            if (f.kind == City.FACILITY_POLICE) f.ammo = 3000;
            else if (f.kind == City.FACILITY_BASE) f.ammo = 9000;
        }
    }

    /** Bats lying about near homes, and axes at the fire stations. */
    private void scatterWeapons() {
        int n = 0;
        for (City.Building b : city.buildings) {
            if (b.doorX == 0) continue;
            boolean fire = b.kind == City.FIRE_STATION;
            if (!fire && (b.kind != City.HOUSE || rnd.nextFloat() > 0.14f)) continue;
            for (int k = 0; k < (fire ? 2 : 1); k++) {
                float[] q = city.findWalkable(b.doorX + rnd.nextFloat() * 16 - 8, b.doorY + rnd.nextFloat() * 16 - 8);
                if (q == null) continue;
                Pickup p = new Pickup();
                p.x = q[0];
                p.y = q[1];
                p.melee = fire ? Entity.M_AXE : Entity.M_BAT;
                pickups.add(p);
            }
            if (++n > 80) break;
        }
    }

    // ------------------------------------------------------------------ the player's options

    /** Where the player's character is when they aren't on foot. */
    Fleet.Vehicle controlledCar;
    City.Building controlledIn;
    /** Search each place once. */
    private final java.util.HashSet<City.Building> searched = new java.util.HashSet<City.Building>();
    static final int CA_ENTER_CAR = 0, CA_EXIT_CAR = 1, CA_ENTER = 2, CA_EXIT = 3, CA_BARRICADE = 4, CA_SEARCH = 5,
            CA_RALLY = 6, CA_GRENADE = 7;
    static final String[] CA_NAMES = {"Get in", "Get out", "Go inside", "Go out", "Barricade", "Search", "Rally", "Grenade"};

    /** What the player can do right now, besides moving and attacking. */
    int ctrlOptions(int[] out) {
        Entity e = controlled;
        if (e == null) return 0;
        int n = 0;
        if (controlledCar != null) {
            out[n++] = CA_EXIT_CAR;
            return n;
        }
        if (controlledIn != null) {
            out[n++] = CA_EXIT;
            out[n++] = CA_BARRICADE;
            if (!searched.contains(controlledIn)) out[n++] = CA_SEARCH;
            return n;
        }
        if (e.isZombie() || e.type == Entity.DOG) return 0;
        if (carToEnter(e) != null) out[n++] = CA_ENTER_CAR;
        if (doorToEnter(e) != null) out[n++] = CA_ENTER;
        out[n++] = CA_RALLY;
        if (e.grenades > 0 && e.grenadeCd <= 0) out[n++] = CA_GRENADE;
        return n;
    }

    private Fleet.Vehicle carToEnter(Entity e) {
        for (int i = 0, n = fleet.vehicles.size(); i < n; i++) {
            Fleet.Vehicle v = fleet.vehicles.get(i);
            if (Fleet.airborne(v) || v.type == Fleet.TRAIN || v.broken || v.player != null || v.block != null) continue;
            float r = v.length() * 0.7f + 10;
            if ((v.x - e.x) * (v.x - e.x) + (v.y - e.y) * (v.y - e.y) < r * r) return v;
        }
        return null;
    }

    private City.Building doorToEnter(Entity e) {
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.doorX == 0 || b.collapsed || b.capacity <= 0) continue;
            if ((b.doorX - e.x) * (b.doorX - e.x) + (b.doorY - e.y) * (b.doorY - e.y) < 16 * 16) return b;
        }
        return null;
    }

    void ctrlAction(int a) {
        Entity e = controlled;
        if (e == null) return;
        switch (a) {
            case CA_ENTER_CAR: {
                Fleet.Vehicle v = carToEnter(e);
                if (v == null) return;
                fleet.takeWheel(v, e);
                controlledCar = v;
                e.dead = true;
                e.removed = true;
                // The group piles in too.
                for (int i = 0, n = entities.size(); i < n && v.riders.size() < Fleet.SEATS; i++) {
                    Entity o = entities.get(i);
                    if (o.dead || o.leader != e || o.type == Entity.DOG) continue;
                    if ((o.x - v.x) * (o.x - v.x) + (o.y - v.y) * (o.y - v.y) > 60 * 60) continue;
                    o.dead = true;
                    o.removed = true;
                    v.riders.add(o);
                }
                break;
            }
            case CA_EXIT_CAR: {
                Fleet.Vehicle v = controlledCar;
                if (v == null) return;
                fleet.leaveWheel(v, e);
                controlledCar = null;
                break;
            }
            case CA_ENTER: {
                City.Building b = doorToEnter(e);
                if (b == null) return;
                if (b.lurkers > 0) {
                    enterBuilding(e, b);
                    return;
                }
                e.dead = true;
                e.removed = true;
                b.occupants.add(e);
                b.calmTimer = 0;
                controlledIn = b;
                for (int i = 0, n = entities.size(); i < n; i++) {
                    Entity o = entities.get(i);
                    if (o.dead || o.leader != e) continue;
                    if ((o.x - b.doorX) * (o.x - b.doorX) + (o.y - b.doorY) * (o.y - b.doorY) > 60 * 60) continue;
                    o.dead = true;
                    o.removed = true;
                    b.occupants.add(o);
                }
                break;
            }
            case CA_EXIT: {
                City.Building b = controlledIn;
                if (b == null) return;
                b.occupants.remove(e);
                controlledIn = null;
                leaveBuilding(e, b, false);
                // The group comes with you.
                for (int i = b.occupants.size() - 1; i >= 0; i--) {
                    Entity o = b.occupants.get(i);
                    if (o.leader != e) continue;
                    b.occupants.remove(i);
                    leaveBuilding(o, b, false);
                }
                break;
            }
            case CA_BARRICADE: {
                City.Building b = controlledIn;
                if (b == null) return;
                b.barricade = Math.min(100, b.barricade + 20);
                emit(Sfx.THUD, b.doorX, b.doorY);
                say("Barricade " + (int) b.barricade + "%");
                break;
            }
            case CA_SEARCH:
                search(e, controlledIn);
                break;
            case CA_RALLY: {
                int have = 0;
                for (int i = 0, n = entities.size(); i < n; i++) if (entities.get(i).leader == e) have++;
                int got = 0;
                for (int i = 0, n = entities.size(); i < n && have + got < 8; i++) {
                    Entity o = entities.get(i);
                    if (o.dead || o == e || o.leader != null || o.isArmed() || o.isZombie()) continue;
                    if (o.type != Entity.CIVILIAN && o.type != Entity.DOG && o.type != Entity.MEDIC) continue;
                    if ((o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y) > 110 * 110) continue;
                    if (hasFollowers(o)) continue;
                    o.leader = e;
                    o.task = Dispatch.T_NONE;
                    o.errand = null;
                    got++;
                }
                say(got == 0 ? (have > 0 ? have + " with you" : "Nobody near enough to join you")
                        : (have + got) + (have + got == 1 ? " person is" : " people are") + " with you");
                break;
            }
            case CA_GRENADE: {
                if (e.grenades <= 0 || e.grenadeCd > 0) return;
                Entity t = aimTarget(e, 170);
                float face = e.want > 1 ? (float) Math.atan2(e.my, e.mx) : e.angle;
                float tx = t != null ? t.x : e.x + (float) Math.cos(face) * 90, ty = t != null ? t.y : e.y + (float) Math.sin(face) * 90;
                throwGrenade(e, tx, ty);
                break;
            }
        }
    }

    /** Searching a building: food and medicine patch you up, gun stores and homes may have something, and some
     *  places have something waiting. */
    private void search(Entity e, City.Building b) {
        if (b == null || !searched.add(b)) return;
        if (b.lurkers > 0) {
            e.hp -= 30;
            e.hurt = 1;
            b.lurkers--;
            spawn(Entity.ZOMBIE, b.doorX, b.doorY);
            say("Something was in here!");
            return;
        }
        if (b.kind == City.SHOP && b.shopType == 1 && b.stock > 0) {
            int take = Math.min(40, b.stock);
            b.stock -= take;
            if (e.canShoot() && e.gunKind() == Entity.W_SHOTGUN) e.reserve += take;
            else setGun(e, Entity.W_SHOTGUN, take);
            say("Found a shotgun and " + take + " shells");
        } else if (b.kind == City.PHARMACY && b.stock > 0) {
            b.stock = Math.max(0, b.stock - 10);
            e.hp = e.maxHp;
            say("Patched yourself up with what was on the shelves");
        } else if (b.food > 0) {
            int eat = Math.min(10, b.food);
            b.food -= eat;
            e.hp = Math.min(e.maxHp, e.hp + 25);
            e.stamina = 1;
            if (b.kind == City.HOUSE && e.melee == 0 && rnd.nextFloat() < 0.4f) {
                e.melee = rnd.nextFloat() < 0.3f ? Entity.M_AXE : Entity.M_BAT;
                say("Ate something, and found " + (e.melee == Entity.M_AXE ? "an axe" : "a bat"));
            } else if (b.kind == City.HOUSE && !e.canShoot() && rnd.nextFloat() < 0.15f) {
                setGun(e, Entity.W_PISTOL, 18);
                say("Ate something, and found a pistol in a drawer");
            } else say("Found something to eat");
        } else say("Nothing left in here");
    }

    /** The last person in the building who isn't the player's character. */
    private Entity popOccupant(City.Building b) {
        for (int i = b.occupants.size() - 1; i >= 0; i--)
            if (b.occupants.get(i) != controlled) return b.occupants.remove(i);
        return null;
    }

    /** Auto-aim for the player: the zombie (or raider) most in line with where they're facing, that they can see. */
    private Entity aimTarget(Entity e, float range) {
        float face = e.want > 1 ? (float) Math.atan2(e.my, e.mx) : e.angle;
        float fx = (float) Math.cos(face), fy = (float) Math.sin(face);
        Entity best = null;
        float bestScore = Float.MAX_VALUE;
        int cx0 = Math.max(0, (int) ((e.x - range) / CELL)), cx1 = Math.min(gw - 1, (int) ((e.x + range) / CELL));
        int cy0 = Math.max(0, (int) ((e.y - range) / CELL)), cy1 = Math.min(gh - 1, (int) ((e.y + range) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o.dead || !(o.isZombie() || (o.type == Entity.RAIDER && e.type != Entity.RAIDER))) continue;
                    float ddx = o.x - e.x, ddy = o.y - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
                    if (d > range) continue;
                    float dot = (ddx * fx + ddy * fy) / d;
                    float score = d * (1.6f - dot);
                    if (score < bestScore && city.los(e.x, e.y, o.x, o.y)) {
                        bestScore = score;
                        best = o;
                    }
                }
            }
        return best;
    }

    // ------------------------------------------------------------------ outbreak replay

    /** Snapshots every few seconds: {time, zombies, people, sampled zombies, sampled people} and dots as x,y pairs. */
    final ArrayList<float[]> replayMeta = new ArrayList<float[]>();
    final ArrayList<short[]> replayDots = new ArrayList<short[]>();
    /** Where and when people turned: {time, x, y}. */
    final ArrayList<float[]> turnEvents = new ArrayList<float[]>();
    private float replayTimer, replayStep = 4;
    private static final int REPLAY_SAMPLE = 250, REPLAY_FRAMES = 900;

    private void recordReplay(float dt) {
        replayTimer -= dt;
        if (replayTimer > 0) return;
        replayTimer = replayStep;
        int z = 0, p = 0;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.dead) continue;
            if (e.isZombie()) z++;
            else if (e.type != Entity.DOG) p++;
        }
        int zs = Math.max(1, (z + REPLAY_SAMPLE - 1) / REPLAY_SAMPLE), ps = Math.max(1, (p + REPLAY_SAMPLE - 1) / REPLAY_SAMPLE);
        short[] dots = new short[(Math.min(z, REPLAY_SAMPLE) + Math.min(p, REPLAY_SAMPLE)) * 2 + 4];
        int k = 0, zi = 0, pi = 0, zn = 0, pn = 0;
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0, n = entities.size(); i < n; i++) {
                Entity e = entities.get(i);
                if (e.dead || e.type == Entity.DOG || e.isZombie() != (pass == 0)) continue;
                if (pass == 0 ? zi++ % zs != 0 : pi++ % ps != 0) continue;
                if (k + 2 > dots.length) break;
                dots[k++] = (short) e.x;
                dots[k++] = (short) e.y;
                if (pass == 0) zn++;
                else pn++;
            }
        replayMeta.add(new float[]{time, z, p + hiding + visiting + riding, zn, pn});
        replayDots.add(k == dots.length ? dots : Arrays.copyOf(dots, k));
        if (replayMeta.size() >= REPLAY_FRAMES) {
            // A long game: keep every other snapshot and take them half as often from now on.
            for (int i = replayMeta.size() - 1; i > 0; i -= 2) {
                replayMeta.remove(i);
                replayDots.remove(i);
            }
            replayStep *= 2;
        }
    }

    private void noteTurn(float x, float y) {
        if (turnEvents.size() < 6000) turnEvents.add(new float[]{time, x, y});
    }

    private Entity nearestInReach(Entity e) {
        Entity z = nearest(e, e.radius + 9, true, false);
        if (z == null) return null;
        float ddx = z.x - e.x, ddy = z.y - e.y, reach = e.radius + z.radius + 3;
        return ddx * ddx + ddy * ddy < reach * reach ? z : null;
    }

    private void thinkCivilian(Entity e, float dt) {
        if (e.scavenge != null || e.homeward) {
            thinkScavenger(e, dt);
            if (e.scavenge != null || e.homeward) return;
        }
        if (e.militia) {
            thinkMilitia(e, dt);
            if (e.militia) return;
        }
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
            e.aware = true;
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

        // Anyone grabbed can try to shove the zombie off (or hit it, with something to hit it with).
        if (e.meleeCd <= 0) {
            Entity z = nearestInReach(e);
            if (z != null) {
                if (e.melee > 0) swing(e, z);
                else if (rnd.nextFloat() < 0.4f) shove(e, z, false);
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

        // Scared and unarmed, with a gun store round the corner: go and get a gun.
        if (!e.hasGun && e.task == Dispatch.T_NONE && e.fear > 0 && threatDist > 40 && e.taskTimer <= 0 && e.leader == null) {
            e.taskTimer = 2;
            City.Building g = gunStoreNear(e, 220);
            if (g != null && rnd.nextFloat() < 0.5f) {
                e.task = Dispatch.T_RESUPPLY;
                e.building = g;
            }
        }
        // Out of bullets and it's quiet: restock at a supermarket or gun store.
        if ((e.hasGun || e.task == Dispatch.T_RESUPPLY) && marketRun(e, threat)) return;

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
                    if (p.weapon != Entity.W_STD) setGun(e, p.weapon, p.rounds);
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
        if (e.task == Dispatch.T_NONE && threat != null && threatDist > 30 && !e.hasGun && e.taskTimer <= 0
                && (readiness != 2 || rnd.nextFloat() < 0.3f)) {
            e.taskTimer = 1.5f;
            City.Building b = shelterNear(e);
            // Home is where people run first, if it is close enough to reach.
            City.Building home = homeOf(e);
            if (home != null && home.occupants.size() < home.capacity && home.barricade >= 40 && !home.collapsed
                    && (home.doorX - e.x) * (home.doorX - e.x) + (home.doorY - e.y) * (home.doorY - e.y) < 220 * 220
                    && countZombiesNear(home.doorX, home.doorY, 35) == 0) b = home;
            if (b != null && (b == home || rnd.nextFloat() < 0.6f)) {
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
        if (e.task == Dispatch.T_SEEK && (!room || e.refused)) e.task = Dispatch.T_NONE;
        // With the power out, nobody hears the broadcasts about safe zones any more.
        if (e.task == Dispatch.T_NONE && room && !e.refused && (e.fleeTimer > 0 || (!blackout && readiness != 2 && rnd.nextFloat() < dt * 0.03f)))
            e.task = Dispatch.T_SEEK;
        if (e.task == Dispatch.T_SEEK) {
            Dispatch.SafeZone z = dispatch.zoneAt(e.x, e.y, 0.7f);
            if (z != null && (!z.military || !checkpoint(e, z))) dispatch.admit(e, z);
        }

        if (e.task == Dispatch.T_SEEK && threatDist > 28) {
            e.paused = false;
            float speed = e.fleeTimer > 0 ? e.runSpeed : e.speed * 1.7f;
            if (followField(e, dispatch.zoneField, speed)) return;
            // No open zone within reach yet: make for the nearest one (still being set up, perhaps) and wait by
            // it; with none at all, get indoors instead of milling about.
            Dispatch.SafeZone z = null;
            float bd = Float.MAX_VALUE;
            for (int i = 0; i < dispatch.zones.size(); i++) {
                Dispatch.SafeZone q = dispatch.zones.get(i);
                if (q.removed || q.full) continue;
                float d = (q.x - e.x) * (q.x - e.x) + (q.y - e.y) * (q.y - e.y);
                if (d < bd) {
                    bd = d;
                    z = q;
                }
            }
            if (z == null) {
                e.task = Dispatch.T_NONE;
                getToSafety(e, homeOf(e));
                return;
            }
            float d = (float) Math.sqrt(bd) + 0.001f, wait = z.baseR * 1.3f + 20;
            if (d > wait) {
                if (z.field == null || !followField(e, z.field, speed)) steer(e, (z.x - e.x) / d, (z.y - e.y) / d, speed);
            } else {
                // Waiting outside for it to open, a little way back.
                float a = (float) Math.atan2(e.y - z.y, e.x - z.x);
                standAt(e, z.x + (float) Math.cos(a) * wait, z.y + (float) Math.sin(a) * wait, 0.5f);
            }
            return;
        }
        if (e.task == Dispatch.T_SHELTER && threatDist > 28 && e.zone != null) {
            float ddx = e.zone.x - e.x, ddy = e.zone.y - e.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            // Mill around inside the zone; head back in (around walls) if drifting out.
            if (d > e.zone.reach(e.x, e.y) * 0.75f) {
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
        } else if (!routine(e, dt)) {
            // A moment between things (or nothing doing): stand about, or stroll a little, not drift for ever.
            if (e.aware) getToSafety(e, homeOf(e));
            else if (e.errandTimer > 0 && e.errandTimer < 6) steer(e, 0, 0, 0);
            else wander(e, e.speed * 0.6f);
        }
    }

    /** Once a second: word of the outbreak gets round. */
    private void updateAlert() {
        // A good while after the last of them is gone, the all-clear: people get back to their lives.
        if (alert > 0 && zombies == 0 && lurking == 0 && calmTime > 90) {
            alert = 0;
            for (int i = 0, n = entities.size(); i < n; i++) entities.get(i).aware = false;
            dispatch.say(Dispatch.WHO_INFO, null, "News: The all-clear has been given. Residents can go about their day.",
                    city.worldW() / 2, city.worldH() / 2);
            return;
        }
        if (!outbreak || zombies == 0) return;
        int was = alert;
        if (alert < 1 && (dispatch.calls >= 2 || bites >= 3 || outbreakTime > 15)) alert = 1;
        if (alert < 2 && (outbreakTime > 50 || zombies >= 8 || dispatch.calls >= 8)) alert = 2;
        if (alert == was) return;
        String where = outbreakPlace != null ? outbreakPlace : "the city";
        if (alert == 1) {
            dispatch.say(Dispatch.WHO_INFO, null, "News: Reports of people attacking people near " + where
                    + ". Police urge residents to stay indoors.", city.worldW() / 2, city.worldH() / 2);
        } else {
            banner("EMERGENCY BROADCAST", "Get indoors and lock your doors. Do not approach the infected.");
            for (int i = 0, n = entities.size(); i < n; i++) {
                Entity e = entities.get(i);
                if (!e.dead && e.type == Entity.CIVILIAN) e.aware = true;
            }
        }
    }

    /**
     * Someone who knows what's happening drops whatever they were doing and gets somewhere safe, quickly:
     * home if it's near enough, a safe zone if one is taking people, any building with room, or the police.
     * Returns true while they're on their way.
     */
    private boolean getToSafety(Entity e, City.Building home) {
        e.paused = false;
        e.errand = null;
        if (home != null && home.occupants.size() < home.capacity && home.barricade >= 40 && !home.collapsed
                && (home.doorX - e.x) * (home.doorX - e.x) + (home.doorY - e.y) * (home.doorY - e.y) < 700 * 700
                && countZombiesNear(home.doorX, home.doorY, 50) == 0) {
            e.task = Dispatch.T_HIDE;
            e.building = home;
            return false;
        }
        if (dispatch.hasRoom() && !e.refused && !blackout) {
            e.task = Dispatch.T_SEEK;
            return false;
        }
        if (e.taskTimer <= 0) {
            e.taskTimer = 2;
            City.Building b = shelterNear(e, 260);
            if (b != null) {
                e.task = Dispatch.T_HIDE;
                e.building = b;
                return false;
            }
        }
        // Nowhere to hide yet: hurry to the police station, and keep moving.
        City.Facility police = city.nearestFacility(City.FACILITY_POLICE, e.x, e.y);
        if (police != null && police.field != null && Math.hypot(police.x - e.x, police.y - e.y) > police.r * 0.8f
                && followField(e, police.field, e.speed * 1.6f)) return true;
        wander(e, e.speed * 1.5f);
        return true;
    }

    /**
     * A crew member walking back to their fire engine or patrol car. Returns true while they're on their way
     * (or just got in); false if there's nothing to get back to.
     */
    private boolean boardStep(Entity e) {
        Fleet.Vehicle v = e.rig;
        if (v == null || v.broken || v.parked || !fleet.vehicles.contains(v)) {
            e.rig = null;
            e.task = Dispatch.T_NONE;
            return false;
        }
        float ddx = v.x - e.x, ddy = v.y - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d < 14) {
            fleet.board(v, e);
            return true;
        }
        e.paused = false;
        e.aiming = false;
        steer(e, ddx / d, ddy / d, e.runSpeed * 0.85f);
        if (e.blocked) {
            e.blocked = false;
            e.unstick = 0.5f;
            e.unstickAngle = (float) Math.atan2(ddy, ddx) + (rnd.nextBoolean() ? 1.3f : -1.3f);
        }
        return true;
    }

    /** The home a civilian lives in: the nearest house or apartment block (found once). */
    private City.Building homeOf(Entity e) {
        if (!e.homeChecked) {
            e.homeChecked = true;
            float bd = 400 * 400;
            for (int i = 0, n = city.buildings.size(); i < n; i++) {
                City.Building b = city.buildings.get(i);
                if ((b.kind != City.HOUSE && b.kind != City.APARTMENT) || b.capacity == 0) continue;
                float d = (b.doorX - e.x) * (b.doorX - e.x) + (b.doorY - e.y) * (b.doorY - e.y) + rnd.nextFloat() * 3000;
                if (d < bd) {
                    bd = d;
                    e.home = b;
                }
            }
        }
        if (e.home != null && e.home.collapsed) e.home = null;
        return e.home;
    }

    /**
     * Everyday life while the city is calm: people go shopping, to church or the park, visit the mall and head
     * home again, stopping a while at each. Once they've heard about the outbreak they go home and stay there.
     */
    private boolean routine(Entity e, float dt) {
        if (e.leader != null || e.task != Dispatch.T_NONE || (e.hasGun && !e.aware)) return false;
        City.Building home = homeOf(e);
        // Word gets round: a phone call, a neighbour shouting, the radio.
        if (alert >= 1 && !e.aware && rnd.nextFloat() < dt * (alert >= 2 ? 10 : 0.05f)) e.aware = true;
        if (e.aware || (zombies > 0 && e.fear > 0)) {
            e.aware = true;
            return getToSafety(e, home);
        }
        if (e.job == Entity.J_NONE) assignJob(e);
        else if (e.work == null && (e.job == Entity.J_WORKER || e.job == Entity.J_SHOPKEEPER || e.job == Entity.J_STUDENT)) {
            // Back from a save: find the workplace again.
            e.work = e.job == Entity.J_WORKER ? pickWorkplace(e) : e.job == Entity.J_SHOPKEEPER ? pickShop(e) : nearestOfKind(e, City.SCHOOL);
            if (e.work == null) e.job = Entity.J_ERRANDS;
        }
        if (chatting(e, dt)) return true;
        e.errandTimer -= dt;
        if (e.errand == null && e.errandTimer <= 0 && outing(e, dt, home)) return true;
        if (e.errand == null || e.errand.collapsed) {
            if (e.errandTimer > 0) return false;
            e.errand = nextPlace(e, home);
            // Nowhere in particular to be: home, rather than drifting round the streets.
            if (e.errand == null && home != null && !home.collapsed
                    && (home.doorX - e.x) * (home.doorX - e.x) + (home.doorY - e.y) * (home.doorY - e.y) > 40 * 40) e.errand = home;
            e.lastErrand = e.errand;
            e.errandTimer = 0;
            if (e.errand != null) e.errand.heading++;
            if (e.errand == null) {
                e.errandTimer = 10 + rnd.nextFloat() * 20;
                return false;
            }
        }
        City.Building b = e.errand;
        float ddx = b.doorX - e.x, ddy = b.doorY - e.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d > 12) {
            walkTo(e, b, ddx, ddy, d, e.speed);
            if (e.errandTimer < -90) e.errand = null; // Taking too long: give up.
            return true;
        }
        // Arrived: go in for a while. If it's full, don't queue: go somewhere else.
        visit(e, b);
        return false;
    }

    /** Goes inside on an errand, with the family. A full place is skipped rather than queued at. */
    private void visit(Entity e, City.Building b) {
        e.errand = null;
        if (b.lurkers > 0) {
            // Something is waiting inside.
            enterBuilding(e, b);
            return;
        }
        boolean home = b == e.home, work = b == e.work;
        if ((!home && !work && b.visitors.size() + b.occupants.size() >= Math.max(4, b.capacity)) || b.collapsed) {
            e.errandTimer = 1 + rnd.nextFloat() * 3;
            return;
        }
        float stay = work ? (e.job == Entity.J_SHOPKEEPER ? 220 + rnd.nextFloat() * 300 : e.job == Entity.J_STUDENT
                ? 120 + rnd.nextFloat() * 150 : 120 + rnd.nextFloat() * 220)
                : home ? (e.job == Entity.J_HOMEBODY ? 180 + rnd.nextFloat() * 400 : 80 + rnd.nextFloat() * 180)
                : b.kind == City.SCHOOL || b.kind == City.CHURCH ? 40 + rnd.nextFloat() * 50 : 15 + rnd.nextFloat() * 35;
        goInside(e, b, stay);
    }

    /** Goes indoors (out of the simulation) for a while, with the family. */
    private void goInside(Entity e, City.Building b, float stay) {
        e.errand = null;
        e.errandTimer = stay;
        e.dead = true;
        e.removed = true;
        e.vx = e.vy = 0;
        b.visitors.add(e);
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o.dead || o.leader != e) continue;
            if ((o.x - b.doorX) * (o.x - b.doorX) + (o.y - b.doorY) * (o.y - b.doorY) > 50 * 50) continue;
            o.errandTimer = stay;
            o.dead = true;
            o.removed = true;
            b.visitors.add(o);
        }
    }

    /** Everyone out on errands inside buildings right now. */
    int visiting;

    /**
     * Shoppers and other visitors: they come out when they're done. If zombies turn up outside, they stay in
     * and shelter where they are.
     */
    private void updateVisitors(City.Building b, float dt) {
        if (b.visitors.isEmpty()) return;
        if (b.collapsed) {
            while (!b.visitors.isEmpty()) leaveBuilding(b.visitors.remove(b.visitors.size() - 1), b, true);
            return;
        }
        if (countZombiesNear(b.doorX, b.doorY, 120) > 0 || alert >= 2) {
            // (Once everyone knows, nobody inside comes out to carry on with their day.)
            while (!b.visitors.isEmpty()) {
                b.visitors.get(b.visitors.size() - 1).aware = true;
                b.occupants.add(b.visitors.remove(b.visitors.size() - 1));
                b.calmTimer = 0;
            }
            return;
        }
        for (int k = b.visitors.size() - 1; k >= 0; k--) {
            Entity o = b.visitors.get(k);
            o.errandTimer -= dt;
            if (o.errandTimer > 0) continue;
            b.visitors.remove(k);
            leaveBuilding(o, b, false);
            o.errandTimer = 2 + rnd.nextFloat() * 8;
            if (k > b.visitors.size()) k = b.visitors.size();
        }
    }

    // ------------------------------------------------------------------ everyday life

    /** Places people work: offices, warehouses, the hospital, schools, the mall and supermarkets. */
    private ArrayList<City.Building> workplaces;

    /** Gives someone a life: a job and somewhere to do it, or a way of spending the day. */
    void assignJob(Entity e) {
        if (e.job != Entity.J_NONE) return;
        City.Building home = homeOf(e);
        // Children in a family go to school.
        boolean child = e.leader != null && e.leader.type == Entity.CIVILIAN && (e.nameSeed & 3) != 0;
        if (child) {
            e.job = Entity.J_STUDENT;
            e.work = nearestOfKind(e, City.SCHOOL);
            if (e.work == null) e.job = Entity.J_HOMEBODY;
            return;
        }
        boolean dog = false;
        for (int i = 0, n = entities.size(); i < n && !dog; i++)
            if (entities.get(i).leader == e && entities.get(i).type == Entity.DOG) dog = true;
        if (dog) {
            e.job = Entity.J_PARK;
            return;
        }
        int posties = 0, vendors = 0, civs = 0;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o.type != Entity.CIVILIAN) continue;
            civs++;
            if (o.job == Entity.J_POSTIE) posties++;
            if (o.job == Entity.J_VENDOR) vendors++;
        }
        float r = rnd.nextFloat();
        if (posties < 1 + civs / 250 && r < 0.03f) e.job = Entity.J_POSTIE;
        else if (vendors < 1 + civs / 300 && r < 0.05f && !city.openAreas.isEmpty()) e.job = Entity.J_VENDOR;
        else if (r < 0.36f) e.job = Entity.J_WORKER;
        else if (r < 0.52f) e.job = Entity.J_HOMEBODY;
        else if (r < 0.70f) e.job = Entity.J_ERRANDS;
        else if (r < 0.79f) e.job = Entity.J_SHOPKEEPER;
        else if (r < 0.88f) e.job = Entity.J_JOGGER;
        else if (r < 0.95f) e.job = Entity.J_PARK;
        else e.job = Entity.J_STUDENT;
        if (e.job == Entity.J_WORKER) e.work = pickWorkplace(e);
        else if (e.job == Entity.J_SHOPKEEPER) e.work = pickShop(e);
        else if (e.job == Entity.J_STUDENT) e.work = nearestOfKind(e, City.SCHOOL);
        if ((e.job == Entity.J_WORKER || e.job == Entity.J_SHOPKEEPER || e.job == Entity.J_STUDENT) && e.work == null)
            e.job = Entity.J_ERRANDS;
    }

    private City.Building nearestOfKind(Entity e, int kind) {
        City.Building best = null;
        float bd = Float.MAX_VALUE;
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.kind != kind || b.collapsed || b.doorX == 0) continue;
            float d = (b.doorX - e.x) * (b.doorX - e.x) + (b.doorY - e.y) * (b.doorY - e.y);
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    private City.Building pickWorkplace(Entity e) {
        if (workplaces == null) {
            workplaces = new ArrayList<City.Building>();
            ArrayList<City.Building> offices = new ArrayList<City.Building>();
            for (City.Building b : city.buildings) {
                if (b.doorX == 0 || b.collapsed) continue;
                if (b.kind == City.HOSPITAL || b.kind == City.SCHOOL || b.kind == City.MALL || b.kind == City.MARKET)
                    workplaces.add(b);
                else if (b.kind == City.OFFICE || b.kind == City.WAREHOUSE) offices.add(b);
            }
            java.util.Collections.shuffle(offices, rnd);
            // A limited set of workplaces, so the routes to them stay remembered.
            for (int i = 0; i < offices.size() && workplaces.size() < 50; i++) workplaces.add(offices.get(i));
        }
        City.Building best = null;
        float bestScore = Float.MAX_VALUE;
        for (int k = 0; k < 8 && !workplaces.isEmpty(); k++) {
            City.Building b = workplaces.get(rnd.nextInt(workplaces.size()));
            float dd = (float) Math.hypot(b.doorX - e.x, b.doorY - e.y);
            if (dd > 1400) continue;
            float score = dd + rnd.nextFloat() * 300;
            if (score < bestScore) {
                bestScore = score;
                best = b;
            }
        }
        return best;
    }

    private City.Building pickShop(Entity e) {
        pickErrand(e, null);
        City.Building best = null;
        float bd = Float.MAX_VALUE;
        for (int k = 0; k < 10 && errandSpots != null && !errandSpots.isEmpty(); k++) {
            City.Building b = errandSpots.get(rnd.nextInt(errandSpots.size()));
            if (b.kind != City.SHOP && b.kind != City.PHARMACY && b.kind != City.KIOSK && b.kind != City.MARKET) continue;
            float d = (float) Math.hypot(b.doorX - e.x, b.doorY - e.y) + rnd.nextFloat() * 200;
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    /** Where someone goes next, by the life they lead. */
    private City.Building nextPlace(Entity e, City.Building home) {
        switch (e.job) {
            case Entity.J_WORKER:
            case Entity.J_SHOPKEEPER:
            case Entity.J_STUDENT:
                if (e.work != null && !e.work.collapsed && e.lastErrand != e.work) return e.work;
                return home != null && rnd.nextFloat() < 0.65f ? home : pickErrand(e, home);
            case Entity.J_HOMEBODY:
                if (home != null && e.lastErrand != home) return home;
                return rnd.nextFloat() < 0.5f || home == null ? pickErrand(e, home) : home;
            default:
                return home != null && e.lastErrand != home && rnd.nextFloat() < 0.35f ? home : pickErrand(e, home);
        }
    }

    /** Routes to the parks and plazas, made when first needed. */
    private final java.util.HashMap<float[], int[]> areaFields = new java.util.HashMap<float[], int[]>();

    private float[] pickArea(Entity e, boolean plaza) {
        float[] best = null;
        float bd = Float.MAX_VALUE;
        for (int k = 0; k < 8; k++) {
            float[] a = city.openAreas.get(rnd.nextInt(city.openAreas.size()));
            if (plaza && a[2] == 2) continue;
            float d = (float) Math.hypot(a[0] - e.x, a[1] - e.y) + rnd.nextFloat() * 250;
            if (d < bd && d < 1100) {
                bd = d;
                best = a;
            }
        }
        return best;
    }

    private final java.util.HashMap<float[], float[]> areaPoints = new java.util.HashMap<float[], float[]>();

    /** Somewhere in an open area people can actually stand (its middle may be a statue or a flower bed). */
    float[] areaPoint(float[] a) {
        float[] p = areaPoints.get(a);
        if (p == null) {
            p = city.findWalkable(a[0], a[1]);
            if (p == null) p = a;
            areaPoints.put(a, p);
        }
        return p;
    }

    /** A street vendor at their stall. */
    boolean atStall(Entity e) {
        if (e.job != Entity.J_VENDOR || e.spot == null) return false;
        float[] p = areaPoint(e.spot);
        return Math.hypot(p[0] - e.x, p[1] - e.y) < 18;
    }

    /** Walks to an open area by its own route. Returns true once there. */
    private boolean walkToArea(Entity e, float[] a, float near, float speed) {
        float[] p = areaPoint(a);
        float ddx = p[0] - e.x, ddy = p[1] - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d < near) return true;
        int[] f = areaFields.get(a);
        if (f == null) {
            if (pathBudget < 1) {
                wander(e, speed * 0.7f);
                return false;
            }
            pathBudget -= 1;
            f = new int[city.w * city.h];
            city.walkFieldFromPoints(f, new float[]{p[0]}, new float[]{p[1]}, 1, 260);
            areaFields.put(a, f);
        }
        if (d < 24 || !followField(e, f, speed)) steer(e, ddx / d, ddy / d, speed);
        return false;
    }

    /**
     * Lives lived out in the open: a run along the pavements, an afternoon in the park (walking the dog), a
     * food stall at a plaza, a delivery round. Returns false when there's nothing to do outdoors (the
     * building trips take over), and sends them home when the outing is over.
     */
    private boolean outing(Entity e, float dt, City.Building home) {
        switch (e.job) {
            case Entity.J_JOGGER: {
                e.jobTimer += dt;
                if (e.jobTimer > 70 + (e.nameSeed & 63)) break;
                if (e.wanderTimer > 4) e.wanderTimer = 4;
                wander(e, e.speed * 1.9f);
                e.paused = false;
                return true;
            }
            case Entity.J_PARK: {
                if (e.spot == null) {
                    if (city.openAreas.isEmpty()) return false;
                    e.spot = pickArea(e, false);
                    if (e.spot == null) return false;
                    e.jobTimer = 0;
                }
                if (walkToArea(e, e.spot, 70, e.speed)) {
                    e.jobTimer += dt;
                    // Amble about the park, drifting back towards the middle rather than bouncing off an edge.
                    float[] mid = areaPoint(e.spot);
                    if (Math.hypot(mid[0] - e.x, mid[1] - e.y) > 45 && e.jobStep != 1) {
                        // Head back in on a slant, so the next stretch takes them somewhere new in the park.
                        e.wanderAngle = (float) Math.atan2(mid[1] - e.y, mid[0] - e.x) + (rnd.nextFloat() - 0.5f) * 2.2f;
                        e.wanderTimer = 3 + rnd.nextFloat() * 3;
                        e.paused = false;
                        e.jobStep = 1;
                    } else if (Math.hypot(mid[0] - e.x, mid[1] - e.y) < 30) e.jobStep = 0;
                    // Stop a while (on a bench, watching the dog), then wander on.
                    if (((int) ((e.jobTimer + (e.nameSeed & 7)) / 7)) % 2 == 0) steer(e, 0, 0, 0);
                    else wander(e, e.speed * 0.4f);
                }
                if (e.jobTimer > 60 + (e.nameSeed & 127)) {
                    e.spot = null;
                    break;
                }
                return true;
            }
            case Entity.J_VENDOR: {
                if (e.spot == null) {
                    if (city.openAreas.isEmpty()) return false;
                    e.spot = pickArea(e, true);
                    if (e.spot == null) return false;
                    e.jobTimer = 0;
                }
                if (walkToArea(e, e.spot, 14, e.speed * 0.8f)) {
                    e.jobTimer += dt;
                    steer(e, 0, 0, 0);
                }
                if (e.jobTimer > 300 + (e.nameSeed & 255)) {
                    e.spot = null;
                    break;
                }
                return true;
            }
            case Entity.J_POSTIE: {
                if (e.round == null || e.jobStep >= e.round.size()) {
                    if (e.round != null && !e.round.isEmpty()) {
                        e.round = null;
                        break;
                    }
                    e.round = new ArrayList<City.Building>();
                    e.jobStep = 0;
                    for (int i = 0, n = city.buildings.size(); i < n && e.round.size() < 7; i++) {
                        City.Building b = city.buildings.get((i * 37 + (e.nameSeed & 0x7FFF)) % n);
                        if (b.residents > 0 && !b.collapsed && Math.hypot(b.doorX - e.x, b.doorY - e.y) < 320) e.round.add(b);
                    }
                    if (e.round.isEmpty()) {
                        e.round = null;
                        return false;
                    }
                }
                City.Building b = e.round.get(e.jobStep);
                float ddx = b.doorX - e.x, ddy = b.doorY - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
                if (d > 14) {
                    walkTo(e, b, ddx, ddy, d, e.speed * 1.1f);
                    return true;
                }
                // At the door: post goes through the letterbox.
                steer(e, 0, 0, 0);
                e.jobTimer += dt;
                if (e.jobTimer > 2.5f) {
                    e.jobTimer = 0;
                    e.jobStep++;
                }
                return true;
            }
            default:
                return false;
        }
        // The outing's over: home for a while.
        e.jobTimer = 0;
        e.errand = home != null ? home : pickErrand(e, home);
        e.lastErrand = e.errand;
        if (e.errand != null) e.errand.heading++;
        return false;
    }

    /** Neighbours stop for a chat now and then. */
    private boolean chatting(Entity e, float dt) {
        if (e.chat > 0) {
            Entity o = e.chatWith;
            e.chat -= dt;
            if (o == null || o.dead || o.chatWith != e || o.fleeTimer > 0) {
                e.chat = 0;
                return false;
            }
            steer(e, 0, 0, 0);
            e.angle = turn(e.angle, (float) Math.atan2(o.y - e.y, o.x - e.x), 0.2f);
            // They take turns talking.
            boolean mine = (((int) (e.chat / 2.2f)) % 2 == 0) == (e.nameSeed > o.nameSeed);
            e.talkTimer = mine ? 0.3f : 0;
            if (e.chat <= 0) {
                e.chatWith = null;
                e.errandTimer = Math.min(e.errandTimer, 0);
            }
            return true;
        }
        if (e.job == Entity.J_JOGGER || e.job == Entity.J_POSTIE || e.leader != null || rnd.nextFloat() > dt * 0.012f) return false;
        Entity o = nearestOfType(e, Entity.CIVILIAN, 14);
        if (o == null || o.chat > 0 || o.leader != null || o.task != Dispatch.T_NONE || o.fear > 0 || o.fleeTimer > 0
                || o.job == Entity.J_JOGGER || o == controlled || e == controlled) return false;
        float len = 4 + rnd.nextFloat() * 7;
        e.chat = o.chat = len;
        e.chatWith = o;
        o.chatWith = e;
        return true;
    }

    /** What someone's life is, for their card: "Nurse at City Hospital", "Postal worker". */
    String jobTitle(Entity e) {
        if (e.type != Entity.CIVILIAN || e.job == Entity.J_NONE) return null;
        String at = e.work != null ? placeLabel(e.work) : null;
        switch (e.job) {
            case Entity.J_WORKER:
                if (e.work == null) return "Works in town";
                switch (e.work.kind) {
                    case City.HOSPITAL: return "Nurse at " + at;
                    case City.SCHOOL: return "Teacher at " + at;
                    case City.WAREHOUSE: return "Warehouse worker";
                    case City.MALL: return "Works at " + at;
                    case City.MARKET: return "Cashier at " + at;
                    default: return "Office worker";
                }
            case Entity.J_SHOPKEEPER: return "Runs " + at;
            case Entity.J_STUDENT: return "Student at " + at;
            case Entity.J_HOMEBODY: return "Spends most of the day at home";
            case Entity.J_ERRANDS: return "Always out and about";
            case Entity.J_JOGGER: return "Keen runner";
            case Entity.J_PARK: return "Loves the park";
            case Entity.J_POSTIE: return "Postal worker";
            case Entity.J_VENDOR: return "Street food vendor";
        }
        return null;
    }

    private static String placeLabel(City.Building b) {
        if (b.name != null) return b.name;
        switch (b.kind) {
            case City.SHOP: return "a shop";
            case City.PHARMACY: return "a pharmacy";
            case City.KIOSK: return "a gas station";
            case City.WAREHOUSE: return "a warehouse";
            case City.HOUSE: return "a house";
            case City.APARTMENT: return "an apartment block";
            default: return "an office";
        }
    }

    /** What someone is doing with their day right now, for their card. */
    String lifeActivity(Entity e) {
        if (e.chat > 0) return "Chatting with a neighbour";
        switch (e.job) {
            case Entity.J_JOGGER:
                if (e.errand == null) return "Out for a run";
                break;
            case Entity.J_PARK:
                if (e.spot != null) return "Spending time at " + city.areaName(e.spot);
                break;
            case Entity.J_VENDOR:
                if (e.spot != null)
                    return (atStall(e) ? "Selling food at " : "Pushing the food cart to ")
                            + city.areaName(e.spot);
                break;
            case Entity.J_POSTIE:
                if (e.round != null && e.errand == null) return "Delivering the post (" + Math.min(e.jobStep + 1, e.round.size()) + " of " + e.round.size() + ")";
                break;
        }
        if (e.errand != null) {
            if (e.errand == e.home) return "Heading home";
            if (e.errand == e.work) return e.job == Entity.J_STUDENT ? "On the way to school" : "On the way to work";
            return "Going to " + placeLabel(e.errand);
        }
        return "Taking a stroll";
    }

    /** Who is inside a building going about their day: {at home, at work, visiting}. */
    int[] insideCounts(City.Building b) {
        int[] c = new int[3];
        for (int i = 0, n = b.visitors.size(); i < n; i++) {
            Entity o = b.visitors.get(i);
            if (o.home == b) c[0]++;
            else if (o.work == b) c[1]++;
            else c[2]++;
        }
        return c;
    }

    /** Places people go on errands: the named landmarks and a handful of shops (so their routes stay cached). */
    private ArrayList<City.Building> errandSpots;

    /** Somewhere to go: a shop, supermarket, the mall, a church or school, or a pharmacy. */
    private City.Building pickErrand(Entity e, City.Building home) {
        if (errandSpots == null) {
            errandSpots = new ArrayList<City.Building>();
            ArrayList<City.Building> shops = new ArrayList<City.Building>();
            for (City.Building b : city.buildings) {
                if (b.doorX == 0) continue;
                if (b.name != null) errandSpots.add(b);
                else if (b.kind == City.SHOP || b.kind == City.PHARMACY || b.kind == City.KIOSK) shops.add(b);
            }
            java.util.Collections.shuffle(shops, rnd);
            // A bigger city has more places to go (their routes are remembered, so not every shop).
            int want = Math.min(110, Math.max(24, city.buildings.size() / 5));
            for (int i = 0; i < shops.size() && errandSpots.size() < want; i++) errandSpots.add(shops.get(i));
        }
        if (errandSpots.isEmpty()) return null;
        City.Building best = null;
        float bestScore = Float.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            City.Building b = errandSpots.get(rnd.nextInt(errandSpots.size()));
            if (b.collapsed || b == e.lastErrand) continue;
            float d = (float) Math.hypot(b.doorX - e.x, b.doorY - e.y);
            if (d > 700) continue;
            // Busy places put people off: nobody wants to queue.
            int busy = b.heading + b.visitors.size();
            if (busy >= Math.max(4, b.capacity) * 2) continue;
            float score = d + rnd.nextFloat() * 200 + busy * 40;
            if (score < bestScore) {
                bestScore = score;
                best = b;
            }
        }
        return best;
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
    /** Medkits {x, y, uses}: anyone hurt nearby is patched up. */
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
                for (int k = cellStart[c] + zCount[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
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
                        o.infectTimer = turnTime(15 + rnd.nextFloat() * 15);
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
        if (blackout) {
            say("No power: the sirens are dead");
            return;
        }
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
        if (patientZero == null) markPatientZero(best);
        return best;
    }

    /** The first person (or zombie) of the outbreak, and where it started. */
    Entity patientZero;
    String outbreakPlace;

    void markPatientZero(Entity e) {
        patientZero = e;
        patientZeroSeed = e.nameSeed;
        outbreakPlace = city.placeName(e.x, e.y);
        highlight("Patient zero: " + Names.person(e.nameSeed), e.x, e.y);
    }

    // ------------------------------------------------------------------ the infection's family tree

    /** Who bit whom (by person), how many each one has turned, and patient zero. */
    final java.util.HashMap<Integer, Integer> bitBy = new java.util.HashMap<Integer, Integer>();
    final java.util.HashMap<Integer, Integer> victims = new java.util.HashMap<Integer, Integer>();
    int patientZeroSeed;

    /** How many people the infection has passed through from patient zero, and how many generations deep. */
    int[] chainFromPatientZero() {
        if (patientZeroSeed == 0) return new int[]{0, 0};
        java.util.HashMap<Integer, java.util.ArrayList<Integer>> kids = new java.util.HashMap<Integer, java.util.ArrayList<Integer>>();
        for (java.util.Map.Entry<Integer, Integer> en : bitBy.entrySet()) {
            java.util.ArrayList<Integer> l = kids.get(en.getValue());
            if (l == null) kids.put(en.getValue(), l = new java.util.ArrayList<Integer>());
            l.add(en.getKey());
        }
        int people = 0, depth = 0;
        java.util.ArrayList<Integer> level = new java.util.ArrayList<Integer>();
        java.util.HashSet<Integer> seen = new java.util.HashSet<Integer>();
        level.add(patientZeroSeed);
        seen.add(patientZeroSeed);
        while (!level.isEmpty() && depth < 500) {
            java.util.ArrayList<Integer> next = new java.util.ArrayList<Integer>();
            for (int p : level) {
                java.util.ArrayList<Integer> l = kids.get(p);
                if (l == null) continue;
                for (int k : l) if (seen.add(k)) next.add(k);
            }
            if (next.isEmpty()) break;
            people += next.size();
            depth++;
            level = next;
        }
        return new int[]{people, depth};
    }

    // ------------------------------------------------------------------ highlights and noise

    /** Big moments, for the replay: {time, x, y} and what happened. */
    final ArrayList<float[]> highlightAt = new ArrayList<float[]>();
    final ArrayList<String> highlightText = new ArrayList<String>();

    void highlight(String text, float x, float y) {
        if (highlightAt.size() >= 400) return;
        // Not the same thing twice in a row within a few seconds.
        int n = highlightAt.size();
        if (n > 0 && highlightText.get(n - 1).equals(text) && time - highlightAt.get(n - 1)[0] < 10) return;
        highlightAt.add(new float[]{time, x, y});
        highlightText.add(text);
    }

    /** Sounds spreading out, so you can see what the dead are hearing: {x, y, radius, age}. */
    final ArrayList<float[]> noiseRings = new ArrayList<float[]>();

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

    private City.Building powerStation;
    private boolean powerChecked;

    /** The power station and the hospital fall if zombies hold them for long enough, and come back once cleared. */
    private void keyBuildings() {
        if (!powerChecked) {
            powerChecked = true;
            for (City.Building b : city.buildings) if (b.kind == City.POWER) powerStation = b;
        }
        if (powerStation != null) {
            City.Building p = powerStation;
            boolean held = p.collapsed || countZombiesNear(p.doorX, p.doorY, 90) >= 6;
            powerThreat = held ? powerThreat + 1 : Math.max(0, powerThreat - 2);
            if (!blackout && powerThreat > 20) {
                blackout = true;
                dispatch.say(Dispatch.WHO_INFO, null, "The power station has been overrun. The power is out across the city: no more sirens or broadcasts.",
                        p.doorX, p.doorY);
            } else if (blackout && !p.collapsed && powerThreat == 0 && countZombiesNear(p.doorX, p.doorY, 200) == 0
                    && peopleNear(p.doorX, p.doorY, 120)) {
                blackout = false;
                dispatch.say(Dispatch.WHO_INFO, null, "Engineers have the power station running again. The lights are back on.", p.doorX, p.doorY);
            }
        }
        City.Facility h = city.nearestFacility(City.FACILITY_HOSPITAL, 0, 0);
        if (h != null) {
            boolean held = countZombiesNear(h.x, h.y, 110) >= 6;
            hospitalThreat = held ? hospitalThreat + 1 : Math.max(0, hospitalThreat - 2);
            if (!hospitalLost && hospitalThreat > 20) {
                hospitalLost = true;
                dispatch.say(Dispatch.WHO_INFO, null, h.name + " has fallen. There's nowhere left to treat the wounded.", h.x, h.y);
            } else if (hospitalLost && hospitalThreat == 0 && countZombiesNear(h.x, h.y, 220) == 0) {
                hospitalLost = false;
                dispatch.say(Dispatch.WHO_INFO, null, h.name + " is open again.", h.x, h.y);
            }
        }
    }

    /** Once a second: the infection evolves, and after it's over the city slowly recovers. */
    private void cityLife() {
        keyBuildings();
        updateAlert();
        callNationalGuard();
        escalate();
        supplies();
        militias();
        if (outbreak) updateWar();
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
        return e.followerFrame == hashFrame;
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
        // (A survivor group's guards are in the way of their supplies.)
        if (law == null && !holdouts.isEmpty()) law = nearestGuard(e, 170);
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
                for (int k = cellStart[c] + zCount[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
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
                for (int k = cellStart[c] + zCount[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
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
                for (int k = cellStart[c] + zCount[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
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
    /** People who armed themselves at a gun store. */
    int armedAtStores;

    private City.Building gunStoreNear(Entity e, float radius) {
        City.Building best = null;
        float bd = radius * radius;
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.kind != City.SHOP || b.shopType != 1 || b.stock <= 0 || b.collapsed || b.infestKnown) continue;
            float d = (b.doorX - e.x) * (b.doorX - e.x) + (b.doorY - e.y) * (b.doorY - e.y);
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    private boolean marketRun(Entity e, Entity threat) {
        if (e.task == Dispatch.T_NONE && threat == null && e.ammo + e.reserve <= 2 && e.taskTimer <= 0) {
            e.taskTimer = 3;
            City.Building best = null;
            float bd = 1200 * 1200;
            for (int i = 0, n = city.buildings.size(); i < n; i++) {
                City.Building b = city.buildings.get(i);
                if (!(b.kind == City.MARKET || (b.kind == City.SHOP && b.shopType == 1)) || b.collapsed || b.stock <= 0
                        || b.infestKnown) continue;
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
        boolean close = threat != null && (threat.x - e.x) * (threat.x - e.x) + (threat.y - e.y) * (threat.y - e.y) < 25 * 25;
        if (b == null || b.stock <= 0 || b.collapsed || (threat != null && e.ammo > 0) || (!e.hasGun && close)) {
            e.task = Dispatch.T_NONE;
            e.building = null;
            return false;
        }
        float ddx = b.doorX - e.x, ddy = b.doorY - e.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d < 10) {
            int take = Math.min(36, b.stock);
            b.stock -= take;
            if (e.hasGun) e.reserve += take;
            else {
                armCivilian(e, take);
                if (rnd.nextFloat() < 0.4f) setGun(e, Entity.W_SHOTGUN, take);
                armedAtStores++;
            }
            if (b.stock <= 0 && b.kind == City.SHOP && !b.looted) {
                b.looted = true;
                dispatch.say(Dispatch.WHO_INFO, null, b.name + " has been cleaned out: every gun is gone.", b.doorX, b.doorY);
            }
            e.reload = 1.5f;
            e.task = Dispatch.T_NONE;
            e.building = null;
            e.talkTimer = 2;
            return true;
        }
        if (b.field == null) {
            b.field = new int[city.w * city.h];
            city.walkFieldFromPoints(b.field, new float[]{b.doorX}, new float[]{b.doorY}, 1);
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
    /** How many new door routes may be worked out right now (each is a search over the whole map). */
    private float pathBudget = 3;

    /**
     * Routes to the doors of buildings under siege, shared by all the dead outside. Each covers just the
     * streets round the building (the dead only besiege a building they're close to), so they're cheap.
     */
    static final int DOOR_R = 24, DOOR_S = DOOR_R * 2 + 1;

    static final class DoorRoute {
        int x0, y0;
        final int[] d = new int[DOOR_S * DOOR_S];
    }

    private final java.util.LinkedHashMap<City.Building, DoorRoute> doorFields = new java.util.LinkedHashMap<City.Building, DoorRoute>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<City.Building, DoorRoute> eldest) {
            return size() > 48;
        }
    };
    private int[][] doorBuckets;

    /** Buildings with people inside, by area (refreshed twice a second), for the dead looking for a way in. */
    static final int OCC_CELL = 160;
    private java.util.ArrayList<City.Building>[] occGrid;
    private int occGW, occGH;
    private float occTimer;

    @SuppressWarnings("unchecked")
    private void buildOccGrid() {
        occGW = (int) (city.worldW() / OCC_CELL) + 1;
        occGH = (int) (city.worldH() / OCC_CELL) + 1;
        if (occGrid == null || occGrid.length != occGW * occGH) occGrid = new java.util.ArrayList[occGW * occGH];
        for (java.util.ArrayList<City.Building> l : occGrid) if (l != null) l.clear();
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.occupants.isEmpty()) continue;
            int gx = Math.max(0, Math.min(occGW - 1, (int) (b.doorX / OCC_CELL))), gy = Math.max(0, Math.min(occGH - 1, (int) (b.doorY / OCC_CELL)));
            java.util.ArrayList<City.Building> l = occGrid[gy * occGW + gx];
            if (l == null) occGrid[gy * occGW + gx] = l = new java.util.ArrayList<City.Building>();
            l.add(b);
        }
        occTimer = 0.5f;
    }

    private DoorRoute doorField(City.Building b) {
        DoorRoute f = doorFields.get(b);
        if (f == null) {
            if (pathBudget < 0.05f) return null;
            pathBudget -= 0.05f;
            f = new DoorRoute();
            int dx = (int) (b.doorX / City.T), dy = (int) (b.doorY / City.T);
            f.x0 = dx - DOOR_R;
            f.y0 = dy - DOOR_R;
            java.util.Arrays.fill(f.d, City.FAR);
            if (doorBuckets == null) doorBuckets = new int[9][DOOR_S * DOOR_S * 4];
            if (city.walkCost == null) city.computeWalkCost();
            int[] sizes = new int[9];
            int start = DOOR_R * DOOR_S + DOOR_R;
            f.d[start] = 0;
            sizes[0] = 1;
            doorBuckets[0][0] = start;
            int pending = 1;
            for (int d = 0; pending > 0 && d < 400; d++) {
                int bi = d % 9;
                int[] bk = doorBuckets[bi];
                int size = sizes[bi];
                sizes[bi] = 0;
                pending -= size;
                for (int k = 0; k < size; k++) {
                    int t = bk[k];
                    if (f.d[t] != d) continue;
                    int lx = t % DOOR_S, ly = t / DOOR_S;
                    for (int q = 0; q < 4; q++) {
                        int nx = lx + (q == 0 ? -1 : q == 1 ? 1 : 0), ny = ly + (q == 2 ? -1 : q == 3 ? 1 : 0);
                        if (nx < 0 || ny < 0 || nx >= DOOR_S || ny >= DOOR_S) continue;
                        int wx = f.x0 + nx, wy = f.y0 + ny;
                        if (wx < 0 || wy < 0 || wx >= city.w || wy >= city.h) continue;
                        int wi = wy * city.w + wx;
                        if (city.solid[wi]) continue;
                        int nl = ny * DOOR_S + nx, nd = d + city.walkCost[wi];
                        if (nd >= f.d[nl]) continue;
                        f.d[nl] = nd;
                        int b2 = nd % 9;
                        if (sizes[b2] >= doorBuckets[b2].length) continue;
                        doorBuckets[b2][sizes[b2]++] = nl;
                        pending++;
                    }
                }
            }
            doorFields.put(b, f);
        }
        return f;
    }

    /** Follows a door route downhill; false if this one is off its patch or cut off. */
    private boolean followDoor(Entity e, DoorRoute f, float speed) {
        int tx = (int) (e.x / City.T), ty = (int) (e.y / City.T);
        int lx = tx - f.x0, ly = ty - f.y0;
        if (lx < 1 || ly < 1 || lx >= DOOR_S - 1 || ly >= DOOR_S - 1) return false;
        int cur = f.d[ly * DOOR_S + lx];
        if (cur >= City.FAR || cur == 0) return false;
        int best = cur, bx = -1, by = -1;
        for (int oy = -1; oy <= 1; oy++)
            for (int ox = -1; ox <= 1; ox++) {
                if (ox == 0 && oy == 0) continue;
                if (city.solidTile(tx + ox, ty + oy)) continue;
                if (ox != 0 && oy != 0 && (city.solidTile(tx + ox, ty) || city.solidTile(tx, ty + oy))) continue;
                int v = f.d[(ly + oy) * DOOR_S + lx + ox];
                if (v < best) {
                    best = v;
                    bx = tx + ox;
                    by = ty + oy;
                }
            }
        if (bx < 0) return false;
        float gx = bx * City.T + City.T / 2f - e.x, gy = by * City.T + City.T / 2f - e.y;
        float d = (float) Math.sqrt(gx * gx + gy * gy) + 0.001f;
        steer(e, gx / d, gy / d, speed);
        return true;
    }

    /**
     * Walks to a building's door around the building (not into its wall): a path field to the door is
     * made the first time anyone needs it. Returns false if it had to fall back to walking straight.
     */
    private boolean walkTo(Entity e, City.Building b, float ddx, float ddy, float d, float speed) {
        if (d < 20 && city.los(e.x, e.y, b.doorX, b.doorY) && !e.blocked) {
            steer(e, ddx / d, ddy / d, speed);
            return true;
        }
        if (e.pathDest != b || e.path == null) {
            if (pathBudget < 0.25f) {
                // Too many new routes this second: plan properly in a moment
                // (someone in no hurry strolls along the pavement meanwhile instead of cutting across the road).
                if (keepsRules(e)) wander(e, speed * 0.7f);
                else steer(e, ddx / d, ddy / d, speed);
                return false;
            }
            pathBudget -= 0.25f;
            e.path = city.findPath(e.x, e.y, b.doorX, b.doorY, 9000);
            e.pathIdx = 0;
            e.pathDest = b;
            if (e.path == null) {
                // No way there on foot: try again in a while.
                e.path = new int[0];
            }
        }
        e.blocked = false;
        if (followPath(e, speed)) return true;
        steer(e, ddx / d, ddy / d, speed);
        return false;
    }

    /** Follows a walking route tile by tile, waiting at the kerb for traffic. False when there's none to follow. */
    private boolean followPath(Entity e, float speed) {
        int[] p = e.path;
        if (p == null || p.length == 0) return false;
        int w = city.w;
        while (e.pathIdx < p.length) {
            int t = p[e.pathIdx];
            float px = (t % w + 0.5f) * City.T, py = (t / w + 0.5f) * City.T;
            float dx = px - e.x, dy = py - e.y, dd = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
            if (dd > 60) {
                // Pushed well off the route: plan it again.
                e.path = null;
                return false;
            }
            if (dd < 7 && e.pathIdx < p.length - 1) {
                e.pathIdx++;
                continue;
            }
            int cur = city.tileIndex(e.x, e.y);
            if (keepsRules(e) && city.tiles[t] == City.ROAD && city.tiles[cur] != City.ROAD && carComing(px, py)) {
                steer(e, 0, 0, 0);
                return true;
            }
            steer(e, dx / dd, dy / dd, speed);
            return true;
        }
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
        City.Facility hospital = hospitalLost ? null : city.nearestFacility(City.FACILITY_HOSPITAL, e.x, e.y);
        boolean needs = e.hp < e.maxHp * 0.45f;
        if (e.task == Dispatch.T_NONE && needs && threat == null) e.task = Dispatch.T_HEAL;
        if (e.task != Dispatch.T_HEAL) return false;
        if (threat != null && e.fleeTimer > 0) {
            e.task = Dispatch.T_NONE;
            return false;
        }
        // A pharmacy on the way patches people up.
        float hd = hospital == null ? Float.MAX_VALUE : (float) Math.hypot(hospital.x - e.x, hospital.y - e.y);
        City.Building ph = pharmacyNear(e, Math.min(hd, 700));
        if (ph != null) {
            float ddx = ph.doorX - e.x, ddy = ph.doorY - e.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d < 10) {
                ph.stock--;
                e.hp = Math.min(e.maxHp, e.hp + e.maxHp * 0.5f);
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
            if (e.hp >= e.maxHp) e.task = Dispatch.T_NONE;
            wander(e, e.speed * 0.3f);
            return true;
        }
        if (!followField(e, hospital.field, e.speed)) e.task = Dispatch.T_NONE;
        return true;
    }

    /**
     * The army checks everyone coming into its safe zones for bites. Most bites are found, and there's no
     * treating a bite: the person is turned away. Returns true if they were stopped at the gate.
     */
    private boolean checkpoint(Entity e, Dispatch.SafeZone z) {
        if (!e.infected || e.screened) return false;
        e.screened = true;
        if (rnd.nextFloat() < 0.2f) return false; // The bite was missed.
        String where = z.place;
        turnedAway++;
        e.refused = true;
        e.task = Dispatch.T_NONE;
        e.talkTimer = 2;
        if (z.checkCd <= 0) {
            z.checkCd = 20;
            dispatch.say(Dispatch.WHO_MILITARY, null, "Checkpoint: Bite found on someone at the " + where
                    + " gate. Turned away.", z.x, z.y);
        }
        return true;
    }

    /**
     * Firefighters: they put out fires with their hoses, give first aid to the hurt, and fight zombies off with
     * their axes (backing away from a crowd). With nothing to do they go back to the station.
     */
    private void thinkFirefighter(Entity e, float dt) {
        Entity z = nearest(e, 60, true, true);
        if (e.meleeCd <= 0) {
            Entity r = nearestInReach(e);
            if (r != null) {
                // A swing of the axe.
                shove(e, r, true);
                r.hp -= 10;
                e.meleeCd = 1.0f;
            }
        }
        if (z != null) {
            float ddx = z.x - e.x, ddy = z.y - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            int crowd = countZombiesNear(e.x, e.y, 45);
            if (crowd >= 3 || e.hp < e.maxHp * 0.4f || z.type == Entity.BRUTE) {
                flee(e, -ddx / d, -ddy / d, e.runSpeed);
                return;
            }
            if (d < 35) {
                steer(e, ddx / d, ddy / d, e.runSpeed);
                return;
            }
        }
        // Fires first: walk up close and hose them down. (On foot without their engine, only fires close by.)
        Fire f = null;
        float fd = e.rig != null ? 450 * 450 : 160 * 160;
        for (int i = 0, n = fires.size(); i < n; i++) {
            Fire o = fires.get(i);
            float d2 = (o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y);
            if (d2 < fd && o.life > 0.5f) {
                fd = d2;
                f = o;
            }
        }
        if (f != null) {
            float ddx = f.x - e.x, ddy = f.y - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d > 24) {
                steer(e, ddx / d, ddy / d, e.runSpeed * 0.9f);
                if (e.blocked) {
                    e.blocked = false;
                    e.unstick = 0.5f;
                    e.unstickAngle = (float) Math.atan2(ddy, ddx) + (rnd.nextBoolean() ? 1.3f : -1.3f);
                }
            } else {
                steer(e, 0, 0, 0);
                e.angle = turn(e.angle, (float) Math.atan2(ddy, ddx), dt * 6);
                f.life -= 3.5f * dt;
                if (f.life <= 0 && f.life > -3.5f * dt) firesOut++;
                if (rnd.nextFloat() < dt * 14)
                    particle(e.x + ddx / d * 4, e.y + ddy / d * 4, ddx / d * 60 + rnd.nextFloat() * 10 - 5,
                            ddy / d * 60 + rnd.nextFloat() * 10 - 5, 0.35f, 1.2f, 0xFFB8DCF0, P_DOT);
            }
            return;
        }
        // First aid for anyone badly hurt close by.
        Entity patient = null;
        float pd = 140 * 140;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o == e || o.dead || o.isZombie() || o.type == Entity.RAIDER || o.hp >= o.maxHp * 0.6f) continue;
            float d2 = (o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y);
            if (d2 < pd) {
                pd = d2;
                patient = o;
            }
        }
        if (patient != null && z == null) {
            float ddx = patient.x - e.x, ddy = patient.y - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d > e.radius + patient.radius + 5) steer(e, ddx / d, ddy / d, e.runSpeed * 0.8f);
            else {
                steer(e, 0, 0, 0);
                patient.hp = Math.min(patient.maxHp, patient.hp + 6 * dt);
            }
            return;
        }
        City.Facility st = city.nearestFacility(City.FACILITY_FIRE, e.x, e.y);
        if (st != null) {
            float ddx = st.x - e.x, ddy = st.y - e.y;
            if (ddx * ddx + ddy * ddy > st.r * st.r * 2 && followField(e, st.field, e.speed)) return;
        }
        wander(e, e.speed * 0.5f);
    }

    /** Whether the governor has already called out the National Guard this outbreak. */
    boolean guardCalled;

    /**
     * When the army has nothing left to send and the war is going badly, the governor calls out the National
     * Guard (once): two trucks of guardsmen drive in from the edge of town to protect the civilians.
     */
    void callNationalGuard() {
        if (guardCalled || !outbreak || outbreakTime < 90 || warBalance > 0.45f || dispatch.squadReserve > 0 || readiness == 1) return;
        guardCalled = true;
        float tx = city.worldW() / 2, ty = city.worldH() / 2;
        if (!dispatch.zones.isEmpty()) {
            tx = dispatch.zones.get(0).x;
            ty = dispatch.zones.get(0).y;
        }
        float[] edge = city.edgeRoad(rnd);
        if (edge == null) edge = new float[]{20, city.worldH() / 2};
        int before = fleet.vehicles.size();
        for (int k = 0; k < 2; k++) fleet.send(Entity.SOLDIER, 6, edge[0], edge[1], tx, ty, null, null, city.placeName(tx, ty));
        for (int i = before; i < fleet.vehicles.size(); i++) fleet.vehicles.get(i).guardUnit = true;
        if (fleet.vehicles.size() > before)
            dispatch.say(Dispatch.WHO_MILITARY, null, "Governor: I'm calling out the National Guard. Two trucks of guardsmen are on their way to "
                    + city.placeName(tx, ty) + ".", tx, ty);
        else guardCalled = false;
    }

    /** Medics run to the hurt, patch them up, and keep away from zombies. (There's no treating a bite.) */
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
        // Triage: the worst hurt first, then whoever is closest.
        Entity patient = null;
        float best = Float.MAX_VALUE;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o == e || o.dead || o.isZombie() || o.type == Entity.RAIDER) continue;
            if (o.hp >= o.maxHp * 0.8f) continue;
            float ddx = o.x - e.x, ddy = o.y - e.y, d2 = ddx * ddx + ddy * ddy;
            if (d2 > 320 * 320) continue;
            float urgency = 0.4f + o.hp / o.maxHp;
            if (o.isArmed()) urgency *= 0.8f;
            float score = d2 * urgency;
            if (score < best) {
                best = score;
                patient = o;
            }
        }
        if (patient != null) {
            float ddx = patient.x - e.x, ddy = patient.y - e.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (d < e.radius + patient.radius + 6) {
                steer(e, 0, 0, 0);
                e.angle = (float) Math.atan2(ddy, ddx);
                float before = patient.hp;
                patient.hp = Math.min(patient.maxHp, patient.hp + 14 * dt);
                if (before < patient.maxHp * 0.8f && patient.hp >= patient.maxHp * 0.8f) healed++;
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
            if (f != null && (f.x - e.x) * (f.x - e.x) + (f.y - e.y) * (f.y - e.y) < f.r * f.r * 2 && draw(f, e)) {
                // Already at the armoury (guards on a post): restock on the spot.
                e.reload = 2;
                dry = false;
            }
            // Guarding a safe zone that has ammo: restock there.
            if (dry && e.zone != null && !e.zone.removed && e.zone.ammo > 0
                    && (e.zone.x - e.x) * (e.zone.x - e.x) + (e.zone.y - e.y) * (e.zone.y - e.y) < e.zone.r * e.zone.r * 1.5f) {
                int take = Math.min(e.zone.ammo, fullReserve(e));
                e.zone.ammo -= take;
                e.reserve = take;
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
                    if (draw(f, e)) {
                        e.ammo = Math.min(e.magSize, e.reserve);
                        e.reserve -= e.ammo;
                    }
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
            if (f != null && (f.x - e.x) * (f.x - e.x) + (f.y - e.y) * (f.y - e.y) < f.r * f.r * 1.5f && f.ammo > 0) {
                int take = Math.min(f.ammo, Math.min(e.magSize, fullReserve(e) - e.reserve));
                f.ammo -= take;
                e.reserve += take;
            }
        }

        Entity t = e.ammo > 0 || e.reserve > 0 ? pickTarget(e, range) : null;
        e.shareCd -= dt;
        if (t != null && t.isZombie()) {
            // Remember where it was, to go and look if it slips out of sight.
            e.threatX = t.x;
            e.threatY = t.y;
            e.fear = 20;
            // Call it in: nearby units that can't see anything come and help.
            if (e.shareCd <= 0) {
                e.shareCd = 2.5f;
                shareSighting(e, t.x, t.y);
            }
        }
        // Badly hurt: pull back towards a medic or the squad while the others cover.
        if (t != null && e.hp < e.maxHp * 0.35f && (e.task == Dispatch.T_NONE || e.task == Dispatch.T_RESPOND)) {
            if (!e.retreatSaid) {
                e.retreatSaid = true;
                dispatch.say(soldier ? Dispatch.WHO_MILITARY : Dispatch.WHO_POLICE, e, "I'm hit! Falling back!", e.x, e.y);
            }
            Entity medic = nearestOfType(e, Entity.MEDIC, 300);
            float ddx = t.x - e.x, ddy = t.y - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (medic != null) {
                float mx = medic.x - e.x, my = medic.y - e.y, md = (float) Math.sqrt(mx * mx + my * my) + 0.001f;
                if (md > 20) steer(e, mx / md * 0.7f - ddx / d * 0.3f, my / md * 0.7f - ddy / d * 0.3f, e.runSpeed);
                else steer(e, 0, 0, 0);
            } else {
                flee(e, -ddx / d, -ddy / d, e.runSpeed);
            }
            if (d < 120) aimAndFire(e, t, d, range, dt);
            return;
        }
        if (e.hp >= e.maxHp * 0.6f) e.retreatSaid = false;
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
            else if (e.task == Dispatch.T_NONE && e.member % 3 == 2 && d > keep + 25) {
                // Flankers work round to the side for a clear angle, instead of all firing down one line.
                float side = e.callsign % 2 == 0 ? 1 : -1;
                steer(e, -ddy / d * side, ddx / d * side, e.speed * 0.5f);
            } else steer(e, 0, 0, 0);
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
            if (!soldier) {
                // Police throw a cordon round the scene: spread out in a ring, facing outwards.
                float a = (e.callsign * 2.4f + e.member * 1.7f) % TAU, r = 38;
                float px = inc.x + (float) Math.cos(a) * r, py = inc.y + (float) Math.sin(a) * r;
                if (!city.solidAt(px, py)) {
                    standAt(e, px, py, 0.8f);
                    if (e.want < 1) e.angle = turn(e.angle, a, dt * 3);
                    return;
                }
            }
            wander(e, e.speed * 0.5f);
            return;
        }
        // The National Guard's job is protecting civilians: they reinforce the nearest safe zone.
        if (soldier && e.role == Entity.ROLE_GUARD && e.task == Dispatch.T_NONE && e.taskTimer <= 0) {
            e.taskTimer = 3;
            Dispatch.SafeZone best = null;
            float bd = Float.MAX_VALUE;
            for (int i = 0; i < dispatch.zones.size(); i++) {
                Dispatch.SafeZone z = dispatch.zones.get(i);
                float d = (z.x - e.x) * (z.x - e.x) + (z.y - e.y) * (z.y - e.y);
                if (d < bd && z.guards < z.wantGuards + 6) {
                    bd = d;
                    best = z;
                }
            }
            if (best != null) {
                e.task = Dispatch.T_GUARD;
                e.zone = best;
                best.guards++;
                return;
            }
        }
        // Soldiers keep their squad together.
        if (soldier && e.task == Dispatch.T_NONE) {
            if (e.taskTimer <= 0) {
                e.taskTimer = 2;
                e.leader = e.role == Entity.ROLE_COMMANDER ? biggestGroup(e) : squadLead(e);
            }
            Entity lead = e.leader;
            if (lead != null && !lead.dead && lead != e) {
                float lx = lead.x - e.x, ly = lead.y - e.y, ld = (float) Math.sqrt(lx * lx + ly * ly) + 0.001f;
                if (ld > 80) {
                    steer(e, lx / ld, ly / ld, e.runSpeed);
                    return;
                }
                // On the move with the squad: keep a wedge behind the leader.
                if (lead.want > 1 && e.fear <= 0) {
                    float hx = lead.mx, hy = lead.my, hl = (float) Math.sqrt(hx * hx + hy * hy) + 0.001f;
                    hx /= hl;
                    hy /= hl;
                    int m = e.member + 1;
                    float back = 12 + (m / 2) * 12, side = (m % 2 == 0 ? 1 : -1) * (8 + (m / 2) * 9);
                    float sx = lead.x - hx * back - hy * side, sy = lead.y - hy * back + hx * side;
                    float dx = sx - e.x, dy = sy - e.y, sd = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
                    if (sd > 5 && !city.solidAt(sx, sy)) {
                        steer(e, dx / sd, dy / sd, Math.min(e.runSpeed, lead.want + sd * 0.8f));
                        return;
                    }
                }
            }
        }
        // Go and check where the last one was seen.
        if (e.task == Dispatch.T_NONE && e.fear > 0 && (e.ammo > 0 || e.reserve > 0)) {
            float lx = e.threatX - e.x, ly = e.threatY - e.y, ld = (float) Math.sqrt(lx * lx + ly * ly) + 0.001f;
            if (ld < 20) e.fear = 0;
            else {
                // Bounding overwatch: half the squad moves up while the other half covers, then they swap.
                boolean cover = soldier && e.leader != null && ld > 60 && ((int) (time / 3) + e.member) % 2 == 0;
                if (cover) {
                    steer(e, 0, 0, 0);
                    e.angle = turn(e.angle, (float) Math.atan2(ly, lx), dt * 4);
                } else {
                    steer(e, lx / ld, ly / ld, e.speed * 1.2f);
                }
                return;
            }
        }
        int dist = city.fieldAt(city.zombieDist, e.x, e.y);
        int hunt = soldier ? 200 : 40;
        if ((e.ammo > 0 || e.reserve > 0) && dist < hunt && followField(e, city.zombieDist, e.speed * 1.3f)) return;
        if (!soldier && alert >= 1 && e.task == Dispatch.T_NONE && e.rig == null) {
            // The city is under attack: officers with no call guard their precinct, spread round it facing out,
            // instead of milling about.
            City.Facility st = city.nearestFacility(City.FACILITY_POLICE, e.x, e.y);
            if (st != null) {
                float sx = st.x - e.x, sy = st.y - e.y;
                if (sx * sx + sy * sy > 500 * 500) {
                    if (followField(e, st.field, e.speed)) return;
                } else {
                    float a = (e.callsign * 2.39996f) % TAU, r = st.r * 0.95f + 10;
                    float px = st.x + (float) Math.cos(a) * r, py = st.y + (float) Math.sin(a) * r;
                    if (!city.solidAt(px, py)) {
                        standAt(e, px, py, 0.7f);
                        if (e.want < 1) e.angle = turn(e.angle, a, dt * 2);
                        return;
                    }
                }
            }
        }
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

    private static final String[] STREAK_LINES = {"That's %d! Keep them coming!", "%d down. I'm not done yet.",
            "%d of them! Somebody count for me!", "Make that %d. This street is ours."};

    /** A hero's milestone, called out on the radio. */
    private void killStreak(Entity e) {
        highlight(Names.person(e.nameSeed) + ": " + e.kills + " kills", e.x, e.y);
        String line = String.format(STREAK_LINES[rnd.nextInt(STREAK_LINES.length)], e.kills);
        if (e.isArmed()) dispatch.say(e.type == Entity.SOLDIER ? Dispatch.WHO_MILITARY : Dispatch.WHO_POLICE, e, line, e.x, e.y);
        else dispatch.say(Dispatch.WHO_INFO, null, Names.person(e.nameSeed) + ": \"" + line + "\"", e.x, e.y);
    }

    /** Radios a sighting: other police and soldiers nearby with nothing in view come to look. */
    private void shareSighting(Entity e, float x, float y) {
        float r = 180;
        int cx0 = Math.max(0, (int) ((e.x - r) / CELL)), cx1 = Math.min(gw - 1, (int) ((e.x + r) / CELL));
        int cy0 = Math.max(0, (int) ((e.y - r) / CELL)), cy1 = Math.min(gh - 1, (int) ((e.y + r) / CELL));
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cy * gw + cx;
                for (int k = cellStart[c] + zCount[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
                    Entity o = sorted[k];
                    if (o == e || o.dead || !o.isArmed() || o.aiming || o.fear > 0 || o.task != Dispatch.T_NONE) continue;
                    if ((o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y) > r * r) continue;
                    o.threatX = x;
                    o.threatY = y;
                    o.fear = 12;
                }
            }
    }

    private Entity nearestOfType(Entity e, int type, float radius) {
        Entity best = null;
        float bd = radius * radius;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o.type != type || o.dead || o == e) continue;
            float d = (o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y);
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    /** Takes a full load from an armoury, if it has any left. Returns false if it's empty. */
    private boolean draw(City.Facility f, Entity e) {
        if (f.ammo <= 0) {
            if (!f.dryAnnounced) {
                f.dryAnnounced = true;
                dispatch.say(e.type == Entity.SOLDIER ? Dispatch.WHO_MILITARY : Dispatch.WHO_POLICE, null, f.name
                        + " has run out of ammunition.", f.x, f.y);
            }
            return false;
        }
        int take = Math.min(f.ammo, fullReserve(e) - e.reserve);
        f.ammo -= take;
        e.reserve += take;
        return true;
    }

    // ------------------------------------------------------------------ the war: supplies, escalation

    /** Supply trucks: the army keeps safe zones in ammunition, while its own armoury lasts. */
    private void supplies() {
        if (!outbreak || ((int) time) % 15 != 0) return;
        for (int i = 0; i < dispatch.zones.size(); i++) {
            Dispatch.SafeZone z = dispatch.zones.get(i);
            if (z.removed || z.supplyComing || z.ammo > 150) continue;
            City.Facility best = null;
            float bd = Float.MAX_VALUE;
            for (City.Facility f : city.facilities) {
                if ((f.kind != City.FACILITY_BASE && f.kind != City.FACILITY_POLICE) || f.ammo < 600) continue;
                float d = (f.x - z.x) * (f.x - z.x) + (f.y - z.y) * (f.y - z.y);
                if (d < bd) {
                    bd = d;
                    best = f;
                }
            }
            if (best == null) continue;
            if (fleet.sendSupply(best, z, 500)) {
                best.ammo -= 500;
                z.supplyComing = true;
                dispatch.say(Dispatch.WHO_MILITARY, null, "Military: " + z.place + " is running low. A supply truck is leaving "
                        + best.name + ".", z.x, z.y);
            }
        }
    }

    /** The army escalates as the city is lost: first it seals off the worst district, then it bombs it. */
    int escalation;
    float firebombTime, firebombX, firebombY;
    String firebombPlace;

    private void escalate() {
        if (!outbreak) return;
        boolean army = city.nearestFacility(City.FACILITY_BASE, 0, 0) != null || counts[Entity.SOLDIER] > 0;
        if (!army) return;
        if (escalation == 0 && outbreakTime > 240 && warBalance < 0.42f) {
            int d = worstDistrict();
            if (d >= 0 && sealDistrict(d) > 0) escalation = 1;
            else escalation = 1;
        } else if (escalation == 1 && outbreakTime > 420 && warBalance < 0.3f) {
            escalation = 2;
            // Aim at the thickest crowd of the dead.
            float bx = 0, by = 0;
            int best = 0;
            for (int i = 0, n = entities.size(); i < n; i += Math.max(1, n / 300)) {
                Entity z = entities.get(i);
                if (z.dead || !z.isZombie()) continue;
                int c = countZombiesNear(z.x, z.y, 120);
                if (c > best) {
                    best = c;
                    bx = z.x;
                    by = z.y;
                }
            }
            if (best < 8) return;
            firebombX = bx;
            firebombY = by;
            firebombTime = 60;
            firebombPlace = city.placeName(bx, by);
            banner("AIR STRIKE IN 60 SECONDS", "The army is going to bomb " + firebombPlace + ". Get everyone out!");
            dispatch.say(Dispatch.WHO_MILITARY, null, "Military: We're losing the city. In 60 seconds we firebomb " + firebombPlace
                    + ". Anyone still there, get out now!", bx, by);
        }
    }

    private void updateFirebomb(float dt) {
        if (firebombTime <= 0) return;
        float before = firebombTime;
        firebombTime -= dt;
        // People in the target area run.
        if (((int) before) != ((int) firebombTime))
            for (int i = 0, n = entities.size(); i < n; i++) {
                Entity e = entities.get(i);
                if (e.dead || e.isZombie() || e == controlled) continue;
                if ((e.x - firebombX) * (e.x - firebombX) + (e.y - firebombY) * (e.y - firebombY) > 320 * 320) continue;
                e.fleeTimer = 3;
                e.threatX = firebombX;
                e.threatY = firebombY;
            }
        if (firebombTime > 0) return;
        firebombTime = 0;
        for (int k = 0; k < 14; k++) {
            float a = rnd.nextFloat() * TAU, r = (float) Math.sqrt(rnd.nextFloat()) * 230;
            float x = firebombX + (float) Math.cos(a) * r, y = firebombY + (float) Math.sin(a) * r;
            blastLater(x, y, 70, 500, k * 0.25f);
            City.Building b = city.buildingAt(x, y);
            if (b != null && rnd.nextFloat() < 0.5f) igniteBuilding(b);
            else ignite(x, y, 20 + rnd.nextFloat() * 20);
        }
        emit(Sfx.JET, firebombX, firebombY);
        highlight("Firebombing of " + firebombPlace, firebombX, firebombY);
        dispatch.say(Dispatch.WHO_MILITARY, null, "Military: Bombs away on " + firebombPlace + ".", firebombX, firebombY);
    }

    private int worstDistrict() {
        int[] z = new int[city.districts.size()];
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.dead || !e.isZombie()) continue;
            int d = city.districtIndex(e.x, e.y);
            if (d >= 0) z[d]++;
        }
        int best = -1;
        for (int d = 0; d < z.length; d++) if (z[d] > 5 && (best < 0 || z[d] > z[best])) best = d;
        return best;
    }

    /** Quarantine: barricades across every road out of a district. */
    private int sealDistrict(int d) {
        int placed = 0, w = city.w;
        for (int ty = 1; ty < city.h - 1 && placed < 70; ty++)
            for (int tx = 1; tx < w - 1 && placed < 70; tx++) {
                int i = ty * w + tx;
                if (city.tiles[i] != City.ROAD) continue;
                float x = tx * City.T + City.T / 2f, y = ty * City.T + City.T / 2f;
                if (city.districtIndex(x, y) != d) continue;
                boolean edge = false;
                int[] nb = {i - 1, i + 1, i - w, i + w};
                for (int j : nb) {
                    if (city.tiles[j] != City.ROAD) continue;
                    if (city.districtIndex((j % w) * City.T + 8, (j / w) * City.T + 8) != d) edge = true;
                }
                if (edge && placeBarricade(x, y) != null) placed++;
            }
        if (placed > 0) {
            City.District dd = city.districts.get(d);
            banner("QUARANTINE", "The army has sealed off " + dd.name + ".");
            dispatch.say(Dispatch.WHO_MILITARY, null, "Military: We're sealing off " + dd.name + ". Every road out is blocked. "
                    + "Nobody in or out.", dd.cx, dd.cy);
        }
        return placed;
    }

    /** Armed residents band together and hold a building, with their neighbours sheltering inside. */
    private void militias() {
        if (!outbreak || ((int) time) % 10 != 0) return;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.dead || e.type != Entity.CIVILIAN || !e.hasGun || e.militia || e.ammo + e.reserve < 6 || e == controlled) continue;
            java.util.ArrayList<Entity> group = new java.util.ArrayList<Entity>();
            for (int k = 0; k < n && group.size() < 6; k++) {
                Entity o = entities.get(k);
                if (o.dead || o.type != Entity.CIVILIAN || !o.hasGun || o.militia || o == controlled || o.ammo + o.reserve < 6) continue;
                if ((o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y) < 140 * 140) group.add(o);
            }
            if (group.size() < 3) continue;
            City.Building b = shelterWithin(e, 200);
            if (b == null || b.fire != null) continue;
            b.barricade = 100;
            for (int k = 0; k < group.size(); k++) {
                Entity o = group.get(k);
                o.militia = true;
                o.task = Dispatch.T_NONE;
                o.leader = null;
                float a = k * TAU / group.size();
                float[] p = city.findWalkable(b.doorX + (float) Math.cos(a) * 18, b.doorY + (float) Math.sin(a) * 18);
                o.postX = p != null ? p[0] : b.doorX;
                o.postY = p != null ? p[1] : b.doorY;
                o.building = b;
            }
            // Neighbours shelter behind them.
            for (int k = 0; k < n; k++) {
                Entity o = entities.get(k);
                if (o.dead || o.type != Entity.CIVILIAN || o.hasGun || o.task != Dispatch.T_NONE) continue;
                if ((o.x - b.doorX) * (o.x - b.doorX) + (o.y - b.doorY) * (o.y - b.doorY) > 200 * 200) continue;
                o.task = Dispatch.T_HIDE;
                o.building = b;
            }
            dispatch.say(Dispatch.WHO_INFO, null, group.size() + " armed residents have taken over a building on "
                    + city.placeName(b.doorX, b.doorY) + " and are holding the street.", b.doorX, b.doorY);
            Holdout h = b.holdout != null ? b.holdout : found(b, false);
            if (h != null) for (int k = 0; k < group.size(); k++) group.get(k).holdout = h;
            return;
        }
    }

    /** A militia member: hold the post, shoot what comes, and give up once the bullets are gone. */
    private void thinkMilitia(Entity e, float dt) {
        if (e.holdout != null && e.ammo + e.reserve <= 4 && e.holdout.stash > 0) {
            // Back to the group's stash for more.
            int take = Math.min(e.holdout.stash, 30);
            e.holdout.stash -= take;
            e.reserve += take;
        }
        if (e.ammo + e.reserve <= 0 && e.reload <= 0 || e.building == null || e.building.collapsed) {
            e.militia = false;
            // (One of a group: out of bullets, they go inside with the others.)
            if (e.holdout != null && e.building != null && !e.building.collapsed) {
                e.task = Dispatch.T_HIDE;
                e.fleeTimer = 0;
            }
            return;
        }
        if (e.reload > 0) {
            e.reload -= dt;
            if (e.reload <= 0) {
                int take = Math.min(e.magSize - e.ammo, e.reserve);
                e.ammo += take;
                e.reserve -= take;
            }
        }
        e.fear = 5;
        Entity t = pickTarget(e, 150);
        if (t != null) {
            float d = (float) Math.hypot(t.x - e.x, t.y - e.y);
            if (d < 14) {
                float ax = e.x - t.x, ay = e.y - t.y;
                flee(e, ax / (d + 0.01f), ay / (d + 0.01f), e.runSpeed);
            } else steer(e, 0, 0, 0);
            aimAndFire(e, t, d, 150, dt);
            return;
        }
        standAt(e, e.postX, e.postY, 1);
    }

    // ------------------------------------------------------------------ after the war

    /**
     * The war decided isn't the end. A city that survives starts to recover: people come out, the bodies
     * and wrecks are cleared, damaged buildings are patched up, shops reopen, survivor groups take their
     * barricades down - though the dead may yet come back. A city that falls has its survivors hold on in
     * hiding while the army gets together one last force to take it back.
     */
    static final int AFTER_NONE = 0, AFTER_RECOVERY = 1, AFTER_FALLEN = 2;
    int aftermath;
    private float clearSince = -1;
    float afterTime, flareCd;
    int flareUps, repaired, cleared, towed;
    boolean retakeSent, retakeWarned;
    private float afterTick;
    int afterSaid;

    private void startAftermath(int kind) {
        aftermath = kind;
        afterTime = 0;
        afterTick = 0;
        flareCd = 150 + rnd.nextFloat() * 150;
        repaired = cleared = towed = 0;
        afterSaid = 0;
    }

    private void updateAftermath(float dt) {
        if (aftermath == AFTER_NONE) return;
        afterTime += dt;
        afterTick -= dt;
        if (afterTick > 0) return;
        afterTick = 1;
        if (aftermath == AFTER_RECOVERY) recovery();
        else fallen();
    }

    private void recovery() {
        int t = (int) afterTime;
        if (t >= 20 && (afterSaid & 1) == 0) {
            afterSaid |= 1;
            banner("RECOVERY", "Survivors are coming out. Clean-up crews are on the streets.");
        }
        // Bodies collected (the ones that won't get up).
        if (t % 3 == 0)
            for (int i = 0; i < corpses.size(); i++)
                if (corpses.get(i).rise < 0) {
                    corpses.remove(i);
                    cleared++;
                    break;
                }
        // Blood and glass washed away, a little at a time.
        for (int k = 0; k < 4 && dcount > 0; k++) {
            int i = rnd.nextInt(dcount);
            if (dr[i] > 0 && dkind[i] != D_SKID) dr[i] = 0;
        }
        // Wrecks towed (never in front of you).
        if (t % 10 == 0) {
            for (int i = fleet.vehicles.size() - 1; i >= 0; i--) {
                Fleet.Vehicle v = fleet.vehicles.get(i);
                if ((v.broken || v.burnt) && v.riders.isEmpty() && v.player == null && !fleet.inView(v.x, v.y)) {
                    fleet.vehicles.remove(i);
                    v.removedFromFleet = true;
                    towed++;
                    break;
                }
            }
        }
        // Repairs: damaged buildings patched up, smashed shops boarded and reopened.
        if (t % 8 == 0) {
            for (int i = 0, n = city.buildings.size(); i < n; i++) {
                City.Building b = city.buildings.get(i);
                if (b.collapsed || b.fire != null) continue;
                if (b.hp < b.maxHp || (b.smashed && afterTime > 60)) {
                    b.hp = Math.min(b.maxHp, b.hp + b.maxHp * 0.34f);
                    if (afterTime > 60 && b.smashed) {
                        b.smashed = false;
                        b.looted = false;
                        if (b.kind == City.MARKET || b.kind == City.SHOP) b.stock = Math.max(b.stock, 40);
                        b.food = Math.max(b.food, 30);
                    }
                    if (b.hp >= b.maxHp) b.markCount = 0;
                    repaired++;
                    break;
                }
            }
        }
        // Survivor groups take their barricades down and go home.
        if (afterTime > 90)
            for (int i = 0; i < holdouts.size(); i++) {
                Holdout h = holdouts.get(i);
                h.fort = Math.max(0, h.fort - 1.5f);
                if (h.fort <= 0) {
                    dispatch.say(Dispatch.WHO_INFO, null, "News: " + cap(h.name) + " have taken down their barricades and gone home.", h.x, h.y);
                    disband(h);
                    break;
                }
            }
        if (t >= 45 && (afterSaid & 2) == 0 && (afterSaid |= 2) != 0)
            dispatch.say(Dispatch.WHO_INFO, null, "News: Clean-up crews are out across the city. Shops are boarding up their windows and getting ready to reopen.",
                    city.worldW() / 2, city.worldH() / 2);
        // But it isn't always over. (Twice at most.)
        flareCd -= 1;
        if (flareCd <= 0 && flareUps < 2) {
            flareCd = 150 + rnd.nextFloat() * 200;
            if (rnd.nextFloat() < 0.45f) {
                flareUps++;
                float[] edge = city.edgeRoad(rnd);
                if (edge != null) {
                    int n = 5 + rnd.nextInt(8);
                    Entity lead = null;
                    for (int k = 0; k < n; k++) {
                        Entity z = spawn(turnType(Entity.CIVILIAN), edge[0] + rnd.nextFloat() * 30 - 15, edge[1] + rnd.nextFloat() * 30 - 15);
                        if (z == null) continue;
                        if (lead == null) {
                            lead = z;
                            z.leadsHorde = true;
                            z.roamX = city.worldW() / 2;
                            z.roamY = city.worldH() / 2;
                        } else join(z, lead);
                    }
                    dispatch.say(Dispatch.WHO_INFO, null, "News: Reports of the dead coming in from " + city.placeName(edge[0], edge[1])
                            + ". Residents, get indoors!", edge[0], edge[1]);
                }
            }
        }
    }

    private void fallen() {
        int t = (int) afterTime;
        if (t >= 15 && (afterSaid & 4) == 0 && (afterSaid |= 4) != 0)
            dispatch.say(Dispatch.WHO_INFO, null, "News: The city has fallen. If you're still out there, stay hidden and stay quiet.",
                    city.worldW() / 2, city.worldH() / 2);
        boolean army = city.nearestFacility(City.FACILITY_BASE, 0, 0) != null;
        if (!army || retakeSent) return;
        if (t >= 100 && !retakeWarned) {
            retakeWarned = true;
            dispatch.say(Dispatch.WHO_MILITARY, null, "Military: We're putting together a force to take the city back. Hold on.",
                    city.worldW() / 2, city.worldH() / 2);
        }
        if (t >= 160) {
            retakeSent = true;
            // One last force (there's no other): three trucks from the edge of town, for the heart of it.
            float tx = city.worldW() / 2, ty = city.worldH() / 2;
            if (!holdouts.isEmpty()) {
                tx = holdouts.get(0).x;
                ty = holdouts.get(0).y;
            }
            float[] edge = city.edgeRoad(rnd);
            if (edge == null) return;
            for (int k = 0; k < 3; k++) fleet.send(Entity.SOLDIER, 6, edge[0], edge[1], tx, ty, null, null, city.placeName(tx, ty));
            aftermath = AFTER_NONE;
            warResult = 0;
            outbreak = true;
            startHumans = humans;
            warLead = 1;
            warBalance = 0.3f;
            banner("RETAKING THE CITY", "The army's last force is on its way in. Survivors, this is your chance.");
        }
    }

    private void disband(Holdout h) {
        holdouts.remove(h);
        h.gone = true;
        h.b.holdout = null;
        for (int k = 0, c = entities.size(); k < c; k++) {
            Entity e = entities.get(k);
            if (e.holdout == h) {
                e.holdout = null;
                e.militia = false;
                e.scavenge = null;
                e.homeward = false;
            }
        }
    }

    // ------------------------------------------------------------------ survivor groups

    /**
     * A group of survivors that has made a building its home and means to stay: they fortify the street
     * round it bit by bit, take in people who turn up, send out supply runs for food and ammunition, keep
     * guards on the door - and are a target for raiders, who want what they've got.
     */
    static final class Holdout {
        City.Building b;
        String name;
        /** How far the fortifications have got (0-100), the group's spare ammunition, and its record. */
        float fort, runTimer = 40, recruitTimer, raidTimer = 150, guardTimer;
        int stash, runs, raidsSeen, said, count;
        float x, y;
        boolean gone;
    }

    final java.util.ArrayList<Holdout> holdouts = new java.util.ArrayList<Holdout>();
    private float holdoutTimer = 5;

    /** People in a building become a group: named after their street. */
    Holdout found(City.Building b, boolean announce) {
        if (b.holdout != null || b.collapsed || b.capacity == 0 || holdouts.size() >= 8) return b.holdout;
        Holdout h = new Holdout();
        h.b = b;
        h.x = b.doorX;
        h.y = b.doorY;
        // Named after their street (the cross street if that's taken; failing that, what the building was).
        String place = city.placeName(b.doorX, b.doorY), other = null;
        int amp = place.indexOf(" & ");
        if (amp > 0) {
            other = place.substring(amp + 3);
            place = place.substring(0, amp);
        }
        h.name = "the " + place + " survivors";
        if (taken(h.name) && other != null) h.name = "the " + other + " survivors";
        if (taken(h.name) && b.typeName() != null) h.name = "the " + b.typeName() + " survivors";
        if (taken(h.name)) h.name = "the " + place + " " + (holdouts.size() + 1) + " survivors";
        b.holdout = h;
        h.count = b.occupants.size();
        h.stash = 10 * b.occupants.size();
        holdouts.add(h);
        if (announce)
            dispatch.say(Dispatch.WHO_INFO, null, "News: Survivors holed up on " + city.placeName(b.doorX, b.doorY)
                    + " have organised. They're calling themselves " + h.name + ".", b.doorX, b.doorY);
        return h;
    }

    private boolean taken(String name) {
        for (Holdout o : holdouts) if (o.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    int members(Holdout h) {
        int n = h.b.occupants.size();
        for (int i = 0, c = entities.size(); i < c; i++) {
            Entity e = entities.get(i);
            if (!e.dead && e.holdout == h) n++;
        }
        return n;
    }

    private void updateHoldouts(float dt) {
        holdoutTimer -= dt;
        boolean tick = holdoutTimer <= 0;
        if (tick) holdoutTimer = 1;
        // New groups: a building full of people that's been left alone for a while organises itself.
        if (tick && (outbreak || aftermath == AFTER_FALLEN) && ((int) time) % 10 == 0 && holdouts.size() < 8) {
            for (int i = 0, n = city.buildings.size(); i < n; i++) {
                City.Building b = city.buildings.get(i);
                if (b.holdout != null || b.occupants.size() < 6 || b.calmTimer < 40 || b.fighting || b.lurkers > 0 || b.fire != null) continue;
                found(b, true);
                break;
            }
        }
        for (int i = holdouts.size() - 1; i >= 0; i--) {
            Holdout h = holdouts.get(i);
            City.Building b = h.b;
            int people = tick ? members(h) : 1;
            if (tick) h.count = people;
            if (b.collapsed || b.fire != null || people == 0 || (b.lurkers > 0 && b.occupants.isEmpty())) {
                disband(h);
                dispatch.say(Dispatch.WHO_INFO, null, "News: We've lost contact with " + h.name + ".", h.x, h.y);
                continue;
            }
            if (!tick) continue;
            boolean quiet = !b.fighting && !zombieWithin(b.doorX, b.doorY, 120);
            // Fortifying: barricades, then cars pushed across the street and sandbags, then a proper wall.
            if (quiet && h.fort < 100) {
                h.fort = Math.min(100, h.fort + 0.25f + Math.min(12, people) * 0.06f);
                int level = h.fort >= 100 ? 3 : h.fort >= 60 ? 2 : h.fort >= 25 ? 1 : 0;
                if (level > h.said) {
                    h.said = level;
                    dispatch.say(Dispatch.WHO_INFO, null, "News: " + cap(h.name) + (level == 1 ? " have barricaded the doors and windows."
                            : level == 2 ? " have pushed cars across the street and are filling sandbags." : " have walled off their block."), h.x, h.y);
                }
            }
            if (quiet) b.barricade = Math.min(100, b.barricade + h.fort * 0.05f);
            // A group feeds whoever turns up (as long as there's room).
            h.recruitTimer -= 1;
            if (h.recruitTimer <= 0 && b.occupants.size() < b.capacity) {
                h.recruitTimer = 6;
                for (int k = 0, c = entities.size(); k < c; k++) {
                    Entity e = entities.get(k);
                    if (e.dead || e.type != Entity.CIVILIAN || e.task != Dispatch.T_NONE || e.leader != null || e.holdout != null
                            || e == controlled || e.militia) continue;
                    if ((e.x - h.x) * (e.x - h.x) + (e.y - h.y) * (e.y - h.y) > 260 * 260) continue;
                    e.task = Dispatch.T_HIDE;
                    e.building = b;
                    break;
                }
            }
            // Guards on the door: anyone inside with a gun takes a turn.
            h.guardTimer -= 1;
            if (h.guardTimer <= 0 && quiet) {
                h.guardTimer = 8;
                int guards = 0;
                for (int k = 0, c = entities.size(); k < c; k++) {
                    Entity e = entities.get(k);
                    if (!e.dead && e.holdout == h && e.militia) guards++;
                }
                if (guards < 3)
                    for (int k = 0; k < b.occupants.size(); k++) {
                        Entity o = b.occupants.get(k);
                        if (o.type != Entity.CIVILIAN || o == controlled) continue;
                        if (!o.hasGun && h.stash >= 20) {
                            o.hasGun = true;
                            o.ammo = 0;
                            o.magSize = 8;
                            o.reserve = 20;
                            h.stash -= 20;
                        }
                        if (!o.hasGun || o.ammo + o.reserve < 6) continue;
                        b.occupants.remove(k);
                        leaveBuilding(o, b, false);
                        o.militia = true;
                        o.holdout = h;
                        o.building = b;
                        float a = rnd.nextFloat() * TAU;
                        float[] p = city.findWalkable(b.doorX + (float) Math.cos(a) * 16, b.doorY + (float) Math.sin(a) * 16);
                        o.postX = p != null ? p[0] : b.doorX;
                        o.postY = p != null ? p[1] : b.doorY;
                        break;
                    }
            }
            // Supply runs when things are quiet and the larder (or the ammo box) is getting low.
            h.runTimer -= 1;
            if (h.runTimer <= 0 && quiet && aftermath != AFTER_RECOVERY && (b.food < 20 || h.stash < 40) && b.occupants.size() >= 2) {
                h.runTimer = 70 + rnd.nextFloat() * 40;
                sendRun(h);
            }
            // Raiders want what a well-stocked group has.
            if (outbreakTime > 240 && aftermath != AFTER_RECOVERY) {
                h.raidTimer -= 1;
                if (h.raidTimer <= 0) {
                    h.raidTimer = 160 + rnd.nextFloat() * 120;
                    if ((b.food > 25 || h.stash > 40) && rnd.nextFloat() < 0.35f) {
                        h.raidsSeen++;
                        raiderGang(h.x, h.y, 3 + rnd.nextInt(3));
                        dispatch.say(Dispatch.WHO_INFO, null, "News: Raiders are heading for " + h.name + " on " + city.placeName(h.x, h.y) + ".", h.x, h.y);
                    }
                }
            }
            // Raiders at an unguarded door help themselves.
            Entity r = counts[Entity.RAIDER] > 0 ? raiderNear(h.x, h.y, 24) : null;
            if (r != null && !b.fighting) {
                boolean guarded = false;
                for (int k = 0, c = entities.size(); k < c && !guarded; k++) {
                    Entity e = entities.get(k);
                    if (!e.dead && e.holdout == h && e.militia && Math.hypot(e.x - h.x, e.y - h.y) < 120) guarded = true;
                }
                if (!guarded && (b.food > 0 || h.stash > 0)) {
                    r.reserve += h.stash;
                    h.stash = 0;
                    b.food /= 3;
                    h.fort = Math.max(0, h.fort - 30);
                    h.said = Math.min(h.said, h.fort >= 60 ? 2 : h.fort >= 25 ? 1 : 0);
                    dispatch.say(Dispatch.WHO_INFO, null, "News: Raiders have looted " + h.name + ". Their food and ammunition are gone.", h.x, h.y);
                }
            }
        }
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private Entity nearestGuard(Entity e, float radius) {
        Entity best = null;
        float bd = radius * radius;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity o = entities.get(i);
            if (o.dead || o.holdout == null || !o.militia) continue;
            float d = (o.x - e.x) * (o.x - e.x) + (o.y - e.y) * (o.y - e.y);
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    private Entity raiderNear(float x, float y, float radius) {
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (!e.dead && e.type == Entity.RAIDER && Math.abs(e.x - x) < radius && Math.abs(e.y - y) < radius) return e;
        }
        return null;
    }

    /** Two of the group go for supplies: someone with a gun, and someone to carry. */
    private void sendRun(Holdout h) {
        City.Building b = h.b;
        City.Building target = null;
        float bd = 700 * 700;
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building o = city.buildings.get(i);
            if (o == b || o.collapsed || o.infestKnown || o.lurkers > 0 || o.fire != null || o.holdout != null) continue;
            boolean useful = (b.food < 20 && o.food >= 10) || (h.stash < 40 && (o.kind == City.SHOP && o.stock > 0 || o.kind == City.MARKET && o.stock > 0));
            if (!useful) continue;
            float d = (o.doorX - b.doorX) * (o.doorX - b.doorX) + (o.doorY - b.doorY) * (o.doorY - b.doorY);
            if (d < bd && !zombieWithin(o.doorX, o.doorY, 80)) {
                bd = d;
                target = o;
            }
        }
        if (target == null) return;
        int sent = 0;
        // The armed first (a guard comes off the door if nobody inside has a gun).
        for (int k = b.occupants.size() - 1; k >= 0 && sent < 2; k--) {
            Entity o = b.occupants.get(k);
            if (o.type != Entity.CIVILIAN || o == controlled || o.infected) continue;
            if (sent == 0 && !o.hasGun && k > 0) continue;
            b.occupants.remove(k);
            leaveBuilding(o, b, false);
            o.holdout = h;
            o.scavenge = target;
            o.homeward = false;
            sent++;
        }
        if (sent > 0) {
            h.runs++;
            dispatch.say(Dispatch.WHO_INFO, null, "News: " + cap(h.name) + " have sent " + (sent == 1 ? "someone" : "two of their own")
                    + " out for supplies (" + (target.typeName() != null ? "a " + target.typeName().toLowerCase(java.util.Locale.ROOT) : "a building")
                    + " on " + city.placeName(target.doorX, target.doorY) + ").", h.x, h.y);
        }
    }

    /** On a supply run: there, take what can be carried, and home again (fighting or running on the way). */
    private void thinkScavenger(Entity e, float dt) {
        Holdout h = e.holdout;
        if (h == null || h.gone) {
            e.scavenge = null;
            e.homeward = false;
            return;
        }
        Entity z = nearest(e, 70, true, true);
        if (z != null) {
            float ddx = z.x - e.x, ddy = z.y - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            if (e.hasGun && e.ammo + e.reserve > 0) {
                aimAndFire(e, z, d, 120, dt);
                if (d < 30) flee(e, -ddx / d, -ddy / d, e.runSpeed);
                else steer(e, 0, 0, 0);
                return;
            }
            if (d < 50) {
                flee(e, -ddx / d, -ddy / d, e.runSpeed);
                return;
            }
        }
        e.aiming = false;
        City.Building goal = e.homeward ? h.b : e.scavenge;
        if (goal == null || goal.collapsed) {
            e.scavenge = null;
            e.homeward = true;
            goal = h.b;
        }
        float ddx = goal.doorX - e.x, ddy = goal.doorY - e.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d > 12) {
            walkTo(e, goal, ddx, ddy, d, e.speed * 1.5f);
            return;
        }
        if (!e.homeward) {
            // There: whatever food and ammunition one person can carry.
            int food = Math.min(goal.food, 12);
            goal.food -= food;
            e.carryFood += food;
            if ((goal.kind == City.SHOP || goal.kind == City.MARKET) && goal.stock > 0) {
                int ammo = Math.min(goal.stock, 40);
                goal.stock -= ammo;
                e.carryAmmo += ammo;
                goal.looted = true;
            }
            e.scavenge = null;
            e.homeward = true;
            return;
        }
        // Home.
        h.b.food += e.carryFood;
        h.stash += e.carryAmmo;
        if (e.carryFood + e.carryAmmo > 0)
            dispatch.say(Dispatch.WHO_INFO, null, "News: A supply run is back at " + h.name + " with "
                    + (e.carryFood > 0 ? e.carryFood + " days of food" : "") + (e.carryFood > 0 && e.carryAmmo > 0 ? " and " : "")
                    + (e.carryAmmo > 0 ? e.carryAmmo + " rounds" : "") + ".", h.x, h.y);
        e.carryFood = e.carryAmmo = 0;
        e.homeward = false;
        if (!e.militia) {
            e.task = Dispatch.T_HIDE;
            e.building = h.b;
        }
    }

    // ------------------------------------------------------------------ buildings on fire

    /** Sets a building alight: flames on its edge that grow, spread and (unless put out) bring it down. */
    void igniteBuilding(City.Building b) {
        if (b.fire != null || b.collapsed) return;
        float cx = (b.x0 + b.x1) / 2, cy = (b.y0 + b.y1) / 2;
        float[] p = city.findWalkable(cx, cy);
        if (p == null) return;
        Fire f = new Fire();
        f.x = p[0];
        f.y = p[1];
        f.life = 8;
        f.building = b;
        fires.add(f);
        b.fire = f;
        b.lurkers = 0;
        // Everyone inside gets out.
        while (!b.occupants.isEmpty()) leaveBuilding(b.occupants.remove(b.occupants.size() - 1), b, true);
        while (!b.visitors.isEmpty()) leaveBuilding(b.visitors.remove(b.visitors.size() - 1), b, true);
        if (b.name != null || b.kind == City.HOSPITAL || b.kind == City.STATION)
            dispatch.say(Dispatch.WHO_FIRE, null, "Fire Dept: " + (b.name != null ? b.name : "A building") + " on "
                    + city.placeName(f.x, f.y) + " is on fire!", f.x, f.y);
    }

    private float spreadTimer;

    private void updateBuildingFires(float dt) {
        spreadTimer -= dt;
        boolean spread = spreadTimer <= 0;
        if (spread) spreadTimer = 1;
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            Fire f = b.fire;
            if (f == null) continue;
            if (!fires.contains(f) || b.collapsed) {
                // Put out (or nothing left to burn).
                b.fire = null;
                continue;
            }
            // A building fire grows unless it's being hosed down (the hose takes it faster than it grows).
            f.life = Math.min(40, f.life + dt * 1.8f);
            float heat = f.life / 40;
            b.hp -= dt * heat * b.maxHp / 70;
            if (rnd.nextFloat() < dt * 30 * heat) {
                float x = b.x0 + rnd.nextFloat() * (b.x1 - b.x0), y = b.y0 + rnd.nextFloat() * (b.y1 - b.y0);
                particle(x, y, rnd.nextFloat() * 8 - 4, -10 - rnd.nextFloat() * 10, 0.5f + rnd.nextFloat() * 0.4f, 2 + rnd.nextFloat() * 2.5f,
                        rnd.nextBoolean() ? 0xFFFFB030 : 0xFFFF6A1A, P_FIRE);
            }
            if (rnd.nextFloat() < dt * 8 * heat) {
                float x = b.x0 + rnd.nextFloat() * (b.x1 - b.x0), y = b.y0 + rnd.nextFloat() * (b.y1 - b.y0);
                particle(x, y, 6 + rnd.nextFloat() * 6, -12 - rnd.nextFloat() * 8, 3 + rnd.nextFloat() * 2, 4 + rnd.nextFloat() * 4,
                        0xFF2E2E2E, P_SMOKE);
            }
            if (b.hp <= 0) {
                fires.remove(f);
                b.fire = null;
                collapseBuilding(b);
                continue;
            }
            if (spread && heat > 0.4f) {
                // Sparks catch the buildings next door.
                for (int k = 0; k < n; k++) {
                    City.Building o = city.buildings.get(k);
                    if (o == b || o.fire != null || o.collapsed) continue;
                    float gx = Math.max(0, Math.max(o.x0 - b.x1, b.x0 - o.x1)), gy = Math.max(0, Math.max(o.y0 - b.y1, b.y0 - o.y1));
                    if (gx > 20 || gy > 20) continue;
                    if (rnd.nextFloat() < 0.05f * heat) igniteBuilding(o);
                }
            }
        }
        // Burning cars and fires right against a wall set it alight.
        if (spread)
            for (int i = 0; i < fires.size(); i++) {
                Fire f = fires.get(i);
                if (f.building != null) continue;
                City.Building b = city.buildingAt(f.x + 10, f.y);
                if (b == null) b = city.buildingAt(f.x - 10, f.y);
                if (b == null) b = city.buildingAt(f.x, f.y + 10);
                if (b == null) b = city.buildingAt(f.x, f.y - 10);
                if (b != null && rnd.nextFloat() < 0.08f) igniteBuilding(b);
            }
    }
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
                    if (o.hidden && score > 22) continue;
                    // The same senses as the dead: they see a long way in front, but only notice what's close behind
                    // them (unless they're already on alert).
                    if (e.fear <= 0 && score > 60) {
                        float facing = ((float) Math.cos(e.angle) * ddx + (float) Math.sin(e.angle) * ddy) / score;
                        if (facing < -0.2f) continue;
                    }
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
                for (int k = cellStart[c] + zCount[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
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
                for (int k = cellStart[c] + zCount[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
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
        // Picked-up guns.
        if (e.weapon == Entity.W_SHOTGUN) {
            e.cooldown = 0.9f;
            dmg = d < 45 ? 34 : d < 80 ? 20 : 9;
            accuracy = 0.92f;
        } else if (e.weapon == Entity.W_RIFLE && !soldier) {
            e.burst++;
            e.cooldown = e.burst % 3 == 0 ? 0.45f : 0.11f;
            dmg = 16;
            accuracy = 0.55f;
        } else if (e.weapon == Entity.W_PISTOL) {
            e.cooldown = 0.55f;
            dmg = 10;
            accuracy = 0.7f;
        }
        if (e.weapon == Entity.W_SHOTGUN && d < 60) pellets(e, t, dmg);
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
            // Only a hit to the head really stops a zombie; body shots just wear it down.
            boolean headshot = t.isZombie() && t.type != Entity.BRUTE
                    && rnd.nextFloat() < (e.role == Entity.ROLE_SNIPER ? 0.45f
                    : e.weapon == Entity.W_SHOTGUN && d < 45 ? 0.3f : HEADSHOT);
            if (headshot) {
                t.hp = 0;
                t.gibbed = rnd.nextFloat() < 0.3f;
                for (int k = 0; k < 6; k++)
                    particle(t.x, t.y, rnd.nextFloat() * 70 - 35, rnd.nextFloat() * 70 - 35, 0.4f, 1.1f, 0xFF7A1010, P_DOT);
                if (gore) decal(t.x + rnd.nextFloat() * 6 - 3, t.y + rnd.nextFloat() * 6 - 3, 2.5f + rnd.nextFloat() * 2, 0xAA5A0808);
            } else {
                t.hp -= t.isZombie() ? dmg * GUN_DAMAGE : dmg;
            }
            t.hurt = 1;
            if (t.hp <= 0 && t.isZombie()) {
                e.kills++;
                if (e.kills == 10 || e.kills == 25 || e.kills == 50 || e.kills == 100) killStreak(e);
            }
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
            boolean hitWall = false;
            for (float s = 0; s < len && car == null; s += 4) {
                ex = mx + (float) Math.cos(a) * s;
                ey = my + (float) Math.sin(a) * s;
                if (city.solidAt(ex, ey)) {
                    for (int i = 0; i < 3; i++)
                        particle(ex, ey, rnd.nextFloat() * 60 - 30, rnd.nextFloat() * 60 - 30, 0.2f, 0.7f, 0xFFFFD27A, P_DOT);
                    impactPuff(ex - (float) Math.cos(a) * 2, ey - (float) Math.sin(a) * 2, 0xFFB8B0A0);
                    bulletHole(ex, ey, e.x, e.y);
                    hitWall = true;
                    break;
                }
                if (s > 12 && ((int) s) % 8 == 0) car = vehicleAt(ex, ey);
            }
            // Stray rounds put holes in cars too.
            if (car != null) fleet.damage(car, dmg * 0.4f, false);
            // A miss kicks up a little puff of dust where it lands.
            else if (!hitWall) impactPuff(ex, ey, 0xFF8E8676);
            tracer(mx, my, ex, ey);
        }
        particle(mx, my, 0, 0, 0.06f, soldier ? 3.2f : 2.6f, 0xFFFFE9A0, P_FLASH);
        emit(!soldier || e.role == Entity.ROLE_COMMANDER ? Sfx.PISTOL : e.role == Entity.ROLE_SNIPER ? Sfx.SNIPER
                : e.role == Entity.ROLE_GUNNER ? Sfx.MG : Sfx.RIFLE, e.x, e.y);
    }

    // ------------------------------------------------------------------ hiding indoors, pickups

    /** A building with room whose door is close and not swarmed. */
    private City.Building shelterNear(Entity e) {
        return shelterNear(e, 80);
    }

    private City.Building shelterNear(Entity e, float range) {
        City.Building best = null;
        float bd = range * range;
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
        if (b.lurkers > 0) {
            // Something was waiting inside.
            e.hp -= 35 + rnd.nextFloat() * 40;
            e.hurt = 1;
            e.killedByZombie = true;
            if (!e.infected && rnd.nextFloat() < 0.6f) {
                e.infected = true;
                e.infectTimer = turnTime(10 + rnd.nextFloat() * 12);
            }
            e.fleeTimer = 3;
            e.threatX = b.doorX;
            e.threatY = b.doorY;
            emit(Sfx.SCREAM, e.x, e.y);
            bloodBurst(e.x, e.y, 6, 0, 0);
            if (!b.infestKnown) {
                b.infestKnown = true;
                dispatch.say(Dispatch.WHO_INFO, null, "Something was waiting inside a building on " + city.placeName(b.doorX, b.doorY)
                        + "! Stay away from it.", b.doorX, b.doorY);
            }
            return;
        }
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
    private float headingTimer;

    private void updateBuildings(float dt) {
        headingTimer -= dt;
        if (headingTimer <= 0) {
            // Recount who's on their way where (people change their minds, get scared or are bitten on the way).
            headingTimer = 2;
            for (int i = 0, n = city.buildings.size(); i < n; i++) city.buildings.get(i).heading = 0;
            for (int i = 0, n = entities.size(); i < n; i++) {
                Entity e = entities.get(i);
                if (!e.dead && e.errand != null) e.errand.heading++;
            }
        }
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            updateVisitors(b, dt);
            if (b.lurkers > 0 && !b.collapsed && b.occupants.isEmpty()) {
                // Zombies inside burst out when someone comes close, or when they get restless.
                boolean near = peopleNear(b.doorX, b.doorY, 32);
                if ((near && rnd.nextFloat() < dt * 1.2f) || rnd.nextFloat() < dt / 100f) burstOut(b);
            }
            if (b.occupants.isEmpty()) {
                b.barricade = Math.min(100, b.barricade + dt * 4);
                continue;
            }
            // Survivors eat what's in the building; when it runs out they have to go out and find more.
            b.hunger += dt;
            if (b.hunger > 20) {
                b.hunger = 0;
                int eat = (b.occupants.size() + 3) / 4;
                b.food = Math.max(0, b.food - eat);
            }
            if (b.food <= 0 && (b.holdout == null || b.holdout.runTimer > 200)) {
                if (!b.outOfFood) {
                    b.outOfFood = true;
                    dispatch.say(Dispatch.WHO_INFO, null, "Survivors holed up on " + city.placeName(b.doorX, b.doorY)
                            + " have run out of food. They're going out to find some.", b.doorX, b.doorY);
                }
                b.releaseTimer -= dt;
                if (b.releaseTimer <= 0) {
                    b.releaseTimer = 6;
                    Entity o = popOccupant(b);
                    if (o == null) continue;
                    leaveBuilding(o, b, false);
                    City.Building larder = foodNear(o, b);
                    if (larder != null) {
                        o.task = Dispatch.T_HIDE;
                        o.building = larder;
                    }
                }
                if (b.occupants.isEmpty()) continue;
            }
            // Someone infected turns inside: one of the dead among them, and a fight.
            for (int k = b.occupants.size() - 1; k >= 0; k--) {
                Entity o = b.occupants.get(k);
                if (!o.infected) continue;
                o.infectTimer -= dt;
                if (o.infectTimer <= 0) {
                    b.occupants.remove(k);
                    if (o.type == Entity.CIVILIAN) civiliansLost++;
                    turned++;
                    noteTurn(b.doorX, b.doorY);
                    b.lurkers++;
                    b.infestKnown = true;
                }
            }
            // The door's down: the dead outside it push their way in.
            if (b.barricade <= 0) breakIn(b, dt);
            if (b.lurkers > 0) {
                fightInside(b, dt);
                continue;
            }
            b.fighting = false;
            // Everyone inside helps shore up the door: more people, a stronger barricade.
            if (!zombieWithin(b.doorX, b.doorY, 60))
                b.barricade = Math.min(100, b.barricade + dt * (2 + b.occupants.size() * 0.5f));
            else if (b.occupants.size() >= 4) b.barricade = Math.min(100, b.barricade + dt * b.occupants.size() * 0.25f);
            // Come out once the street has been quiet for a while.
            b.calmTimer += dt;
            if (zombieWithin(b.doorX, b.doorY, 250)) b.calmTimer = 0;
            if (b.calmTimer > 25 && b.holdout == null) {
                b.releaseTimer -= dt;
                if (b.releaseTimer <= 0) {
                    b.releaseTimer = 1.5f;
                    Entity o = popOccupant(b);
                    if (o != null) leaveBuilding(o, b, false);
                }
            }
        }
    }

    /** Through a broken door: the dead right at it get inside (a few at a time). */
    private void breakIn(City.Building b, float dt) {
        if (b.lurkers >= 10 || rnd.nextFloat() > dt * 2.5f) return;
        Entity z = nearestZombie(b.doorX, b.doorY, 22);
        if (z == null || z == controlled || z.leadsHorde) return;
        z.dead = true;
        z.removed = true;
        b.lurkers++;
        b.infestKnown = true;
        if (isShop(b)) smash(b);
        emit(Sfx.THUD, b.doorX, b.doorY);
        if (b.lurkers == 1 && !b.fighting && time - b.fightSaid > 40) {
            b.fightSaid = time;
            dispatch.say(Dispatch.WHO_INFO, null, "The dead are inside a building on " + city.placeName(b.doorX, b.doorY) + "! "
                    + b.occupants.size() + (b.occupants.size() == 1 ? " person is" : " people are") + " fighting for their lives.", b.doorX, b.doorY);
        }
    }

    /**
     * People and the dead in the same building. Anyone with a gun fights; the rest fall back room by room
     * and get out of the back if they can. The bitten turn and join the dead. It ends when one side is gone:
     * the survivors shore the door back up, or the building belongs to the dead.
     */
    private void fightInside(City.Building b, float dt) {
        if (!b.fighting) {
            b.fighting = true;
            b.fightTime = 0;
            b.intruderHp = 45;
        }
        b.fightTime += dt;
        b.calmTimer = 0;
        b.flash = Math.max(0, b.flash - dt);
        int shooters = 0, others = 0;
        for (int k = 0; k < b.occupants.size(); k++) {
            Entity o = b.occupants.get(k);
            if (o.type == Entity.DOG) others++;
            else if (o.canShoot() && o.ammo + o.reserve > 0) shooters++;
            else others++;
        }
        // The defenders: guns do the work, everyone else lays into them with whatever is to hand.
        float dps = shooters * 12 + others * (b.occupants.size() > 3 ? 2.5f : 1.2f);
        b.intruderHp -= dps * dt;
        if (shooters > 0 && rnd.nextFloat() < dt * Math.min(6, shooters * 2)) {
            b.flash = 0.08f;
            emit(rnd.nextBoolean() ? Sfx.PISTOL : Sfx.RIFLE, b.doorX, b.doorY);
            if (rnd.nextFloat() < 0.3f) noise(b.doorX, b.doorY, 180);
            for (int k = 0; k < b.occupants.size(); k++) {
                Entity o = b.occupants.get(k);
                if (o.canShoot() && o.ammo + o.reserve > 0) {
                    if (o.ammo > 0) o.ammo--;
                    else o.reserve--;
                    break;
                }
            }
        }
        if (b.intruderHp <= 0) {
            b.lurkers--;
            zombiesKilled++;
            b.intruderHp = 45;
            Entity hero = null;
            for (int k = 0; k < b.occupants.size(); k++) {
                Entity o = b.occupants.get(k);
                if (o.canShoot() || hero == null) hero = o;
            }
            if (hero != null) hero.kills++;
            if (gore) bloodBurst(b.doorX, b.doorY, 3, 0, 0);
        }
        // The dead: each one inside catches someone now and then (the unarmed first).
        if (b.lurkers > 0 && !b.occupants.isEmpty() && rnd.nextFloat() < dt * b.lurkers * 0.15f) {
            Entity victim = null;
            for (int k = 0; k < b.occupants.size() && victim == null; k++) {
                Entity o = b.occupants.get(k);
                if (!o.canShoot()) victim = o;
            }
            if (victim == null || rnd.nextFloat() < 0.3f) victim = b.occupants.get(rnd.nextInt(b.occupants.size()));
            emit(Sfx.SCREAM, b.doorX, b.doorY);
            if (victim.type == Entity.DOG || rnd.nextFloat() < 0.45f) {
                // Pulled down: they're one of them in a few moments.
                b.occupants.remove(victim);
                onDeathInside(victim, b);
                if (victim.type != Entity.DOG) {
                    b.lurkers++;
                    turned++;
                    noteTurn(b.doorX, b.doorY);
                }
            } else if (!victim.infected) {
                victim.infected = true;
                victim.infectTimer = turnTime(20 + rnd.nextFloat() * 20);
            }
        }
        // Falling back: when the dead outnumber the guns, people make a run for it out of the other side.
        if (b.lurkers > shooters && rnd.nextFloat() < dt * 0.8f) {
            for (int k = b.occupants.size() - 1; k >= 0; k--) {
                Entity o = b.occupants.get(k);
                if (o.canShoot() && o.ammo + o.reserve > 0 && shooters <= b.lurkers + 1) continue;
                b.occupants.remove(k);
                leaveBuilding(o, b, true);
                break;
            }
        }
        if (b.lurkers <= 0) {
            b.lurkers = 0;
            b.fighting = false;
            b.barricade = Math.max(b.barricade, 15);
            b.infestKnown = false;
            if (b.fightTime > 3)
                dispatch.say(Dispatch.WHO_INFO, null, "Survivors on " + city.placeName(b.doorX, b.doorY) + " fought off the dead inside. "
                        + b.occupants.size() + " still standing.", b.doorX, b.doorY);
        } else if (b.occupants.isEmpty()) {
            b.fighting = false;
            dispatch.say(Dispatch.WHO_INFO, null, "A building on " + city.placeName(b.doorX, b.doorY) + " has fallen to the dead. Stay away from it.",
                    b.doorX, b.doorY);
        }
    }

    private void onDeathInside(Entity o, City.Building b) {
        if (o.type == Entity.CIVILIAN) civiliansLost++;
        if (o.kills > 0 && !o.isZombie()) {
            fallenHeroes.add(new int[]{o.nameSeed, o.type, o.role, o.kills});
            if (fallenHeroes.size() > 40) fallenHeroes.remove(0);
        }
        o.x = b.doorX;
        o.y = b.doorY;
        dispatch.onDeath(o);
    }

    /** Zombies shut inside a building smash their way out. */
    void burstOut(City.Building b) {
        int n = b.lurkers;
        b.lurkers = 0;
        for (int k = 0; k < n; k++) {
            float[] p = city.findWalkable(b.doorX + rnd.nextFloat() * 12 - 6, b.doorY + rnd.nextFloat() * 12 - 6);
            if (p == null) break;
            Entity z = spawn(turnType(Entity.CIVILIAN), p[0], p[1]);
            if (z != null) z.fresh = 0;
        }
        emit(Sfx.THUD, b.doorX, b.doorY);
        emit(Sfx.GROAN, b.doorX, b.doorY);
        for (int k = 0; k < 12; k++)
            particle(b.doorX, b.doorY, rnd.nextFloat() * 80 - 40, rnd.nextFloat() * 80 - 40, 0.5f, 1.2f, 0xFF8A6238, P_DEBRIS);
        if (isShop(b)) smash(b);
        b.infestKnown = true;
        dispatch.say(Dispatch.WHO_INFO, null, n + (n == 1 ? " zombie burst" : " zombies burst") + " out of "
                + (b.name != null ? b.name : "a building") + " on " + city.placeName(b.doorX, b.doorY) + "!", b.doorX, b.doorY);
    }

    static boolean isShop(City.Building b) {
        return b.kind == City.SHOP || b.kind == City.MARKET || b.kind == City.MALL || b.kind == City.PHARMACY || b.kind == City.KIOSK;
    }

    /** Shop windows put in: glass all over the pavement. */
    void smash(City.Building b) {
        if (b.smashed) return;
        b.smashed = true;
        for (int k = 0; k < 10; k++) {
            float gx = b.doorX + rnd.nextFloat() * 20 - 10, gy = b.doorY + rnd.nextFloat() * 8 - 4;
            decal(gx, gy, 0.8f + rnd.nextFloat() * 1.2f, 0xCCCFE8F2, D_GLASS, rnd.nextFloat() * TAU);
        }
        for (int k = 0; k < 8; k++)
            particle(b.doorX, b.doorY, rnd.nextFloat() * 60 - 30, rnd.nextFloat() * 60 - 30, 0.4f, 0.8f, 0xFFCFE8F2, P_DOT);
        emit(Sfx.CRASH, b.doorX, b.doorY);
    }

    /** The nearest building with food and room that isn't known to be full of zombies. */
    private City.Building foodNear(Entity e, City.Building except) {
        City.Building best = null;
        float bd = 500 * 500;
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b == except || b.food < 10 || b.capacity == 0 || b.collapsed || b.infestKnown || b.occupants.size() >= b.capacity)
                continue;
            float d = (b.doorX - e.x) * (b.doorX - e.x) + (b.doorY - e.y) * (b.doorY - e.y);
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    private void leaveBuilding(Entity o, City.Building b, boolean panic) {
        // (Never refused: they were already one of the population before they went in.)
        float[] p = city.findWalkable(b.doorX + rnd.nextFloat() * 10 - 5, b.doorY + rnd.nextFloat() * 10 - 5);
        if (p == null) p = new float[]{b.doorX, b.doorY};
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
            if (p.claimed || p.drop > 0 || p.melee > 0) continue;
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
        return z.contains(e.x, e.y, margin);
    }

    /** Guards spread out around the edge of their safe zone, facing outward. */
    private void holdPost(Entity e, Dispatch.SafeZone z, float speedFactor) {
        float a = e.slot * TAU / Math.max(1, z.guards);
        // On the line itself; while it's going up, filling sandbags at their stretch of it.
        float er = z.edgeAt(a) * (z.open ? 0.85f : 0.95f);
        float px = z.x + (float) Math.cos(a) * er, py = z.y + (float) Math.sin(a) * er;
        float ddx = px - e.x, ddy = py - e.y;
        float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
        if (d > z.r * 2.5f) {
            if (z.field == null) {
                z.field = new int[city.w * city.h];
                city.walkFieldFromPoints(z.field, new float[]{z.x}, new float[]{z.y}, 1);
            }
            if (followField(e, z.field, e.runSpeed * speedFactor)) return;
        }
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
            // Mostly carry on or turn a corner; only now and then turn right round (which looks like pacing).
            float roll = rnd.nextFloat();
            float base = Math.round(e.wanderAngle / (TAU / 4)) * (TAU / 4);
            e.wanderAngle = (e.isZombie() || e.wanderTimer < -1 ? rnd.nextInt(4) * TAU / 4
                    : roll < 0.5f ? base : roll < 0.92f ? base + (rnd.nextBoolean() ? 1 : -1) * TAU / 4 : base + TAU / 2)
                    + (rnd.nextFloat() - 0.5f) * 0.4f;
            // People who saw a zombie recently keep away from where it was.
            if (e.fear > 0 && !e.isZombie() && !e.isArmed()) {
                float tx = e.threatX - e.x, ty = e.threatY - e.y;
                if (tx * (float) Math.cos(e.wanderAngle) + ty * (float) Math.sin(e.wanderAngle) > 0) e.wanderAngle += (float) Math.PI;
                e.paused = false;
            }
        }
        if (!e.paused && keepsRules(e)) {
            // Strolling people stay on the pavement: they turn back at the kerb unless it's a crossing,
            // and wait there while a car goes by.
            int tx = (int) (e.x / City.T), ty = (int) (e.y / City.T);
            float ax = e.x + (float) Math.cos(e.wanderAngle) * City.T * 0.8f, ay = e.y + (float) Math.sin(e.wanderAngle) * City.T * 0.8f;
            int nx = (int) (ax / City.T), ny = (int) (ay / City.T);
            if ((nx != tx || ny != ty) && city.tileIndex(ax, ay) >= 0 && city.tiles[city.tileIndex(ax, ay)] == City.ROAD
                    && city.tiles[city.tileIndex(e.x, e.y)] != City.ROAD) {
                if (!city.crosswalk(nx, ny)) {
                    // At the kerb: turn to walk along the pavement (whichever way isn't road), not back.
                    float left = e.wanderAngle - TAU / 4, right = e.wanderAngle + TAU / 4;
                    boolean lOk = city.tiles[city.tileIndex(e.x + (float) Math.cos(left) * City.T, e.y + (float) Math.sin(left) * City.T)] != City.ROAD
                            && !city.solidAt(e.x + (float) Math.cos(left) * City.T, e.y + (float) Math.sin(left) * City.T);
                    boolean rOk = city.tiles[city.tileIndex(e.x + (float) Math.cos(right) * City.T, e.y + (float) Math.sin(right) * City.T)] != City.ROAD
                            && !city.solidAt(e.x + (float) Math.cos(right) * City.T, e.y + (float) Math.sin(right) * City.T);
                    if (lOk && (!rOk || rnd.nextBoolean())) e.wanderAngle = left;
                    else if (rOk) e.wanderAngle = right;
                    else e.wanderAngle += (float) Math.PI;
                    e.wanderTimer = Math.max(e.wanderTimer, 3);
                } else if (carComing(ax, ay)) {
                    steer(e, 0, 0, 0);
                    return;
                }
            }
        }
        if (e.paused) steer(e, 0, 0, 0);
        else steer(e, (float) Math.cos(e.wanderAngle), (float) Math.sin(e.wanderAngle), speed);
    }

    /** Ordinary folk going about their day mind the traffic; anyone running for their life doesn't. */
    private boolean keepsRules(Entity e) {
        if (e == controlled) return false;
        if (e.type == Entity.CIVILIAN) return e.fleeTimer <= 0 && e.fear <= 0 && e.task != Dispatch.T_SEEK;
        // Police and soldiers on an ordinary patrol walk on the pavement too; in a fight, anything goes.
        if (e.type == Entity.COP || e.type == Entity.SOLDIER || e.type == Entity.MEDIC)
            return (e.task == Dispatch.T_NONE || e.task == Dispatch.T_POST || e.task == Dispatch.T_GUARD)
                    && city.fieldAt(city.zombieDist, e.x, e.y) > 18 && !e.aiming;
        return false;
    }

    /** A vehicle on the ground heading for this spot and close enough that stepping out would be a mistake. */
    private boolean carComing(float x, float y) {
        for (int i = 0, n = fleet.vehicles.size(); i < n; i++) {
            Fleet.Vehicle v = fleet.vehicles.get(i);
            if (Fleet.airborne(v) || v.broken || Math.abs(v.speed) < 8) continue;
            float dx = x - v.x, dy = y - v.y;
            float reach = 36 + Math.abs(v.speed) * 1.6f;
            if (dx * dx + dy * dy > reach * reach) continue;
            float d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
            float s = v.speed < 0 ? -1 : 1;
            // In its path: ahead of it, and not far to one side.
            float along = ((float) Math.cos(v.angle) * dx + (float) Math.sin(v.angle) * dy) * s;
            if (along > -10 && Math.abs((float) Math.cos(v.angle) * dy - (float) Math.sin(v.angle) * dx) < 26) return true;
            if (d < 22) return true;
        }
        return false;
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
        // Calm people wait at the kerb for traffic to pass.
        if (keepsRules(e) && city.tiles[by * w + bx] == City.ROAD && city.tiles[ty * w + tx] != City.ROAD
                && carComing(bx * City.T + City.T / 2f, by * City.T + City.T / 2f)) {
            steer(e, 0, 0, 0);
            return true;
        }
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
        // The living tire: a sprint lasts a few seconds, then they slow to a jog until they get their breath back.
        // The dead never tire.
        if (!e.isZombie() && e.type != Entity.DOG) {
            if (e.want > e.speed * 1.35f) {
                e.stamina -= dt * STAMINA_DRAIN;
                if (e.stamina < 0.15f) e.want = Math.min(e.want, e.speed * 1.3f);
            } else {
                e.stamina = Math.min(1, e.stamina + dt * 0.05f);
            }
            if (e.stamina < 0) e.stamina = 0;
        }
        if (e.want > 1 && (e.mx != 0 || e.my != 0)) lookAhead(e);
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

    /**
     * Looks a step ahead: if a wall or corner is right in the way, turns to walk along it (keeping to the side
     * it last went round on) instead of pressing into it.
     */
    private void lookAhead(Entity e) {
        float l = (float) Math.sqrt(e.mx * e.mx + e.my * e.my);
        if (l < 0.01f) return;
        float dx = e.mx / l, dy = e.my / l, reach = e.radius + 5;
        if (!city.circleBlocked(e.x + dx * reach, e.y + dy * reach, e.radius)) return;
        for (int step = 1; step <= 4; step++) {
            for (int side = 0; side < 2; side++) {
                float a = step * 0.42f * (side == 0 ? e.slideSide : -e.slideSide);
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                float rx = dx * c - dy * s, ry = dx * s + dy * c;
                if (!city.circleBlocked(e.x + rx * reach, e.y + ry * reach, e.radius)) {
                    e.mx = rx * l;
                    e.my = ry * l;
                    if (side == 1) e.slideSide = -e.slideSide;
                    return;
                }
            }
        }
    }

    /** Moves with wall sliding. Returns false if the move was (at least partly) blocked. */
    private boolean tryMove(Entity e, float sx, float sy) {
        boolean ok = true;
        float bx0 = e.x, by0 = e.y;
        if (sx != 0) {
            if (!city.circleBlocked(e.x + sx, e.y, e.radius)) e.x += sx;
            else ok = false;
        }
        if (sy != 0) {
            if (!city.circleBlocked(e.x, e.y + sy, e.radius)) e.y += sy;
            else ok = false;
        }
        if (!ok && (sx != 0 || sy != 0) && e.x == bx0 && e.y == by0) {
            // Walked straight into something round (a tree trunk): step off at an angle, whichever side is free,
            // keeping to the side we last went round on so we don't dither.
            float[] turns = {0.8f, -0.8f, 1.4f, -1.4f};
            for (float t : turns) {
                float a = t * e.slideSide;
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                float rx = sx * c - sy * s, ry = sx * s + sy * c;
                if (!city.circleBlocked(e.x + rx, e.y + ry, e.radius)) {
                    e.x += rx;
                    e.y += ry;
                    if (t < 0) e.slideSide = -e.slideSide;
                    break;
                }
            }
        }
        return ok;
    }

    /** People knocked down in the crush, and when that was last reported. */
    int trampled;
    private int trampledSaid;
    private float stampedeCd;

    private void stampedeNews(float dt) {
        stampedeCd -= dt;
        if (stampedeCd > 0) return;
        stampedeCd = 6;
        if (trampled - trampledSaid >= 6) {
            // Where is the crush?
            Entity at = null;
            for (int i = 0, n = entities.size(); i < n && at == null; i++) {
                Entity e = entities.get(i);
                if (!e.dead && e.stun > 0 && e.type == Entity.CIVILIAN) at = e;
            }
            if (at != null)
                dispatch.say(Dispatch.WHO_INFO, null, "Stampede on " + city.placeName(at.x, at.y) + "! People are being trampled in the panic.",
                        at.x, at.y);
        }
        trampledSaid = trampled;
    }

    private void separate() {
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (e.dead) continue;
            // Only the cells within touching distance (the biggest body is under 8 across, plus a little for
            // movement since the grid was built).
            float reach = e.radius + 10;
            int cx0 = Math.max(0, (int) ((e.x - reach) / CELL)), cx1 = Math.min(gw - 1, (int) ((e.x + reach) / CELL));
            int cy0 = Math.max(0, (int) ((e.y - reach) / CELL)), cy1 = Math.min(gh - 1, (int) ((e.y + reach) / CELL));
            float pushX = 0, pushY = 0;
            for (int cy = cy0; cy <= cy1; cy++) {
                for (int cx = cx0; cx <= cx1; cx++) {
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
                        // A panicking crowd tramples people (but not a group that's sticking together).
                        if (e.type == Entity.CIVILIAN && o.type == Entity.CIVILIAN && e.fleeTimer > 0 && e.want > e.speed * 1.3f
                                && o.stun <= 0 && e.leader == null && o.leader != e && e.leader != o && rnd.nextFloat() < 0.012f) {
                            o.stun = 1.4f;
                            o.hp -= 4;
                            o.hurt = 1;
                            trampled++;
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
            p.weapon = e.gunKind();
            pickups.add(p);
            if (pickups.size() > 200) pickups.remove(0);
        }
        if (e.melee > 0) dropMelee(e, e.melee);
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
        c.nameSeed = e.nameSeed;
        c.vx = e.knockX;
        c.vy = e.knockY;
        c.spin = (e.knockX != 0 || e.knockY != 0) ? (rnd.nextFloat() - 0.5f) * 12 : 0;
        boolean turns = !e.isZombie() && !e.gibbed && (e.killedByZombie || e.infected)
                && (e.type != Entity.DOG || rnd.nextFloat() < 0.6f);
        if (!turns && trait[TR_RESTLESS] && !e.isZombie() && !e.gibbed && e.type != Entity.DOG && rnd.nextFloat() < 0.35f) {
            turns = true;
            reveal(TR_RESTLESS);
        }
        c.rise = turns ? 2.5f + rnd.nextFloat() * 3f : -1;
        c.riseType = e.type == Entity.DOG ? Entity.ZOMBIE_DOG : turnType(e.type);
        if (e.gibbed) {
            bloodBurst(e.x, e.y, 14, 0, 0);
            if (rnd.nextBoolean()) return;
        }
        corpses.add(c);
        if (corpses.size() > (quality == Q_FULL ? 350 : quality == Q_REDUCED ? 220 : 130)) {
            for (int i = 0; i < corpses.size(); i++)
                if (corpses.get(i).rise < 0) {
                    corpses.remove(i);
                    break;
                }
        }
    }

    /**
     * What someone comes back as. Most are ordinary zombies, but now and then one turns into one of the
     * other kinds (a big soldier or cop is likelier to come back a brute).
     */
    int turnType(int was) {
        float roll = rnd.nextFloat();
        float brute = was == Entity.SOLDIER || was == Entity.COP || was == Entity.FIREFIGHTER ? 0.05f : 0.02f;
        if (roll < 0.11f) return Entity.RUNNER;
        roll -= 0.11f;
        if (roll < 0.04f) return Entity.CRAWLER;
        roll -= 0.04f;
        if (roll < 0.03f) return Entity.SCREAMER;
        roll -= 0.03f;
        if (roll < 0.03f) return Entity.SPITTER;
        roll -= 0.03f;
        if (roll < 0.025f) return Entity.BLOATER;
        roll -= 0.025f;
        if (roll < brute) return Entity.BRUTE;
        return Entity.ZOMBIE;
    }

    private void updateCorpses(float dt) {
        for (int i = corpses.size() - 1; i >= 0; i--) {
            Corpse k = corpses.get(i);
            if (k.vx != 0 || k.vy != 0) {
                // Sliding to a stop; walls stop it dead.
                float nx = k.x + k.vx * dt, ny = k.y + k.vy * dt;
                if (city.circleBlocked(nx, ny, 3)) {
                    k.vx = k.vy = 0;
                } else {
                    k.x = nx;
                    k.y = ny;
                    k.angle += k.spin * dt;
                    float fr = Math.max(0, 1 - dt * 5);
                    k.vx *= fr;
                    k.vy *= fr;
                    k.spin *= fr;
                    if (Math.abs(k.vx) + Math.abs(k.vy) < 4) k.vx = k.vy = 0;
                }
            }
        }
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
                    // Same person, now one of them: who bit them gets the credit.
                    z.nameSeed = c.nameSeed;
                    Integer by = bitBy.get(c.nameSeed);
                    if (by != null) victims.put(by, victims.containsKey(by) ? victims.get(by) + 1 : 1);
                    // Freshly turned: fast and hungry for a while.
                    z.fresh = 45;
                    z.speed *= 1.2f;
                    z.runSpeed *= 1.3f;
                    entities.add(z);
                    turned++;
                    noteTurn(c.x, c.y);
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
                    o.knockX = fx * speed * 0.9f / o.mass;
                    o.knockY = fy * speed * 0.9f / o.mass;
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
                for (int k = cellStart[c] + zCount[c], end = cellStart[c] + cellCount[c]; k < end; k++) {
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
                    if (d > 0.01f) {
                        o.knockX = ddx / d * 180 * f / o.mass;
                        o.knockY = ddy / d * 180 * f / o.mass;
                    }
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
        if (b.hp > 0) {
            // A big blast can set it alight.
            if (f > 0.4f && damage > 100 && rnd.nextFloat() < 0.2f) igniteBuilding(b);
            return;
        }
        collapseBuilding(b);
    }

    /** Brings a building down (blasted or burnt out): some inside get out, rubble and dust everywhere. */
    void collapseBuilding(City.Building b) {
        if (b.collapsed) return;
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
        highlight("A building collapsed", cx, cy);
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
        if (quality > Q_FULL && ((pnext * 7 + 3) % 10) < (quality == Q_REDUCED ? 4 : 7)) {
            pnext = (pnext + 1) % MAXP; // (skips a slot so the pattern moves on)
            return;
        }
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

    /** A small puff of dust or plaster where a bullet strikes. */
    private void impactPuff(float x, float y, int color) {
        for (int k = 0; k < 2; k++)
            particle(x, y, rnd.nextFloat() * 16 - 8, rnd.nextFloat() * 16 - 8, 0.35f + rnd.nextFloat() * 0.2f,
                    1.6f + rnd.nextFloat(), color, P_SMOKE);
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
