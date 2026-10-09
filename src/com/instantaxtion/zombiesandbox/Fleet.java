package com.instantaxtion.zombiesandbox;

import java.util.ArrayList;

/**
 * Vehicles: police cruisers and army trucks that drive reinforcements from the precinct or base along the
 * roads (running over zombies on the way), fire engines, ordinary traffic and the helicopter that flies in
 * for air support. Cars take damage from zombies, crashes and blasts, and break down when it is too much.
 */
final class Fleet {
    static final int CRUISER = 0, TRUCK = 1, HELI = 2, CAR = 3, FIRE_ENGINE = 4, TANK = 5, AMBULANCE = 6, TRAIN = 7, JET = 8;

    /** Aircraft fly over everything: no crashes, no zombies clawing at them. */
    static boolean airborne(Vehicle v) {
        return v.type == HELI || v.type == JET;
    }
    /** A train: a locomotive and three carriages, this long in world units. */
    static final float TRAIN_LENGTH = 136;
    static final int WAIT = 0, DRIVE = 1, RETURN = 2, FLY_IN = 3, CIRCLE = 4, FLY_OUT = 5, CRUISE = 6,
            ABANDONED = 7, SPRAY = 8, SPOOL = 9, LAND = 10, ENGAGE = 11, LOAD = 12, BLOCK = 13,
            PATROL = 14, SCENE = 15, RECALL = 16, IDLE = 17, MUSTER = 18, HOVER = 19, STANDBY = 20, DROP = 21;
    private static final int[] CAR_COLORS = {0xFFB03A2E, 0xFF2E5FB0, 0xFFE0E0E0, 0xFF222428, 0xFFD4A21C, 0xFF3C8A4E,
            0xFF8A8F96, 0xFF6B2E8A, 0xFFE07A2E};
    private static final float[] MAX_HP = {160, 260, 1, 100, 240, 700, 160, 1, 1};
    /** Most people a car will squeeze in. */
    static final int SEATS = 4;
    /**
     * What a truck, police car or helicopter is beyond its basic type (since 10.10): an armoured personnel
     * carrier, a Humvee, a riot police van, a police motorcycle, the police helicopter.
     */
    static final int K_APC = 1, K_HUMVEE = 2, K_RIOT_VAN = 3, K_BIKE = 4, K_POLICE_HELI = 5, K_RESCUE_HELI = 6;

    /** Kinds of everyday car (which ones are about depends on the country). */
    static final int M_SEDAN = 0, M_PICKUP = 1, M_SUV = 2, M_KEI = 3, M_HATCH = 4, M_VAN = 5, M_TAXI = 6, M_BUS = 7,
            M_BEETLE = 8;
    static final String[] MODEL_NAMES = {"Car", "Pickup", "SUV", "Kei car", "Hatchback", "Van", "Taxi", "Bus", "Beetle"};
    /** Half length and half width of each kind of car. */
    static final float[] MODEL_HL = {8f, 8.8f, 8.4f, 6.2f, 7f, 8.6f, 8f, 15f, 6.8f};
    static final float[] MODEL_HW = {4.4f, 4.6f, 4.8f, 3.8f, 4.2f, 4.6f, 4.4f, 5.6f, 4.2f};

    static final class Vehicle {
        int type, state;
        /** Where it is in the step's grid of who's near whom; set once it has left the fleet. */
        int gi;
        boolean removedFromFleet;
        /** Seconds stopped; pulling out round something stopped in the lane (seconds left); queued behind traffic. */
        float stillT, passing;
        /** How far it has turned without getting any nearer (a car going round in circles), and how long it
         *  then drives carefully, slowly and straight at the middle of each tile of its route. */
        float spin, careful;
        /** Pulled over for lights and sirens coming up behind (or holding at a junction for one): seconds left. */
        float yieldT;
        boolean yieldStop;
        /** Pulled over by highway patrol (stopped on the shoulder); and the car a patrol car has pulled over. */
        boolean pulled;
        Vehicle stopping;
        float stopT, stopCd = 20;
        /** Parked at the kerb at a scene, nose angled in. */
        boolean kerbed;
        /** Queued behind traffic; and somewhere up that queue, someone waiting at a light or a junction. */
        boolean queued, queuedHeld;
        /** How many cars up the queue that someone waiting is (0 none): in a ring of cars it only grows. */
        int holdDepth;
        /** For ordinary cars: what kind of car it is (M_SEDAN and so on). */
        int model;
        /** The tile of its route it is driving along, and the route that belongs to. */
        int pathT = -1;
        Route pathField;
        /** At junctions: the one it has been let through, the one it is waiting at and for how long. */
        int clearedJ = -1, waitJ = -1;
        float waitT;
        boolean held;
        /** The way along the road it was last heading (one step of its route). */
        int pdx, pdy;
        float x, y, angle, speed, timer, stuckTimer, lastDist, gunCd, soundCd, circle;
        /** It went round in a circle (missed its turn); traffic gives up that route. */
        boolean orbited;
        /** A SWAT team's armoured van (a truck carrying police). */
        boolean swat;
        /** Which kind of truck, police car or helicopter (K_APC and so on; 0 the usual). */
        int kind;
        /** A patrol out from the base and back: the crew stay aboard unless they're needed. */
        boolean loop;
        /** How long a car on its way home has been holding back from the dead on the road ahead. */
        float hold;
        /** Rounds fired by the door gunner (it fires in short bursts). */
        int burst;
        float tx, ty, homeX, homeY;
        Route field;
        int passengerType, passengers;
        Dispatch.Incident incident;
        Dispatch.SafeZone zone;
        String place;
        /** Lights flash on cruisers; the helicopter's rotor spins. */
        float anim;
        int color;
        boolean parked;
        /** Damage: smoking below half, broken down (and maybe burning) at zero. */
        float hp, maxHp, crashCd, hailCd, waitTimer;
        boolean broken, burnt;
        /** People who flagged the car down and jumped in. */
        final ArrayList<Entity> riders = new ArrayList<Entity>();
        /** Driving the riders to a safe zone. */
        boolean rescue;
        /** Fire engines: the fire being fought and where the hose is pointing. */
        World.Fire fire;
        float sprayX, sprayY;
        boolean spraying;
        int number;
        /** Helicopter height (0 on the pad, 1 cruising) and how hard it is banking. */
        float alt, bank;
        /** The helicopter took off from (and lands back on) a helipad, rather than flying in from the edge. */
        boolean pad;
        /** Tanks: where the turret points and the cannon's reload. */
        float turret, cannonCd, idleTimer;
        /** Ambulances: the person they came for. */
        Entity patient;
        /** An army truck carrying the National Guard. */
        boolean guardUnit;
        /** Bringing reinforcements in from outside the city (10.17): tagged on the map. */
        boolean reinforcement;
        /** A fire engine's crew is already off fighting the fire. */
        /**
         * The real people aboard: a fire crew or two patrol officers. They're out of the world while they ride,
         * get out at the scene (and stay its crew), and walk back to it before it leaves.
         */
        final ArrayList<Entity> crew = new ArrayList<Entity>();
        /** A patrol car (not a backup cruiser): drives a beat and answers calls. */
        boolean patrol;
        /** Whose car it is: 0 the city police, 1 highway patrol, 2 the sheriff, 3 the park rangers. */
        int agency;
        /** The rescue helicopter: its team on the ground, calling them back up, and where it flies home to. */
        ArrayList<Entity> team;
        boolean recall;
        /** The rescue helicopter: back at base waiting for its team's call, or on its way to pick them up. */
        boolean standby, pickup;
        float baseX, baseY;
        /**
         * A helicopter's own helipad (10.20): every aircraft is parked on one from the start, its crew standing by
         * it, and comes back to it. Its flight crew (the pilot, and the rescue team), whether it's in the air
         * yet, its velocity (it can move any way it likes, not just nose first), and time to refuel.
         */
        float[] padAt;
        final ArrayList<Entity> flight = new ArrayList<Entity>();
        float hvx, hvy, refuel, spoolTime = 1;
        /** Air-assault troops still aboard to put down, and the incident they're for. */
        int troops;
        /** Flying cover for a convoy coming in. */
        Dispatch.Convoy escort;
        /** The station it belongs to (fire engines and patrol cars), and how many it waits to take aboard. */
        City.Facility station;
        int crewWanted, crewSpawns;
        float checkCd, quiet;

        /**
         * Whether anyone is inside for the dead to go after: a driver in moving traffic, the crew of an engine
         * or patrol car, riders, a tank or ambulance crew, or you at the wheel. An empty or abandoned vehicle
         * isn't worth their trouble.
         */
        boolean occupied() {
            if (player != null || !riders.isEmpty() || !crew.isEmpty() || passengers > 0) return true;
            if (parked || broken) return false;
            if (type == FIRE_ENGINE) return state == WAIT;
            if (type == CRUISER) return !patrol;
            return type == CAR || type == TANK || type == AMBULANCE || type == TRUCK;
        }

        /** Whether the roof lights are going (not on a quiet patrol, or parked at the station). */
        boolean lightsOn() {
            if (broken) return false;
            if (type == FIRE_ENGINE) return state != IDLE && state != MUSTER;
            if (type == CRUISER) return state != WAIT && (stopping != null || !(patrol && state != DRIVE && state != SCENE));
            if (type == AMBULANCE) return state != WAIT;
            return false;
        }
        /** A parked car's alarm going off (seconds left) after zombies bumped into it. */
        float alarm;
        /** Police roadblocks: the safe zone this car is closing a road for, which way that road runs, cones out,
         *  and the officer posted beside it. */
        Dispatch.SafeZone block;
        int blockDir;
        boolean cones;
        Entity guard;
        /** A supply truck: rounds of ammunition it's carrying to a safe zone. */
        int supply;
        /** A supply truck from outside, once it's over (10.19): restocking this store or armoury, then off the map. */
        boolean restock;
        City.Building restockShop;
        City.Facility restockFac;
        /** The player's character, at the wheel. */
        Entity player;
        /** A stopped car rolls over to the kerb. */
        float pullX, pullY;
        boolean pulling;
        /** Getting out of town now everyone knows (runs red lights once it's clear, drives faster). */
        boolean fleeing;
        /** Through traffic on the highway: leaves the map at the far end. */
        boolean through;
        /** After failing to find somewhere to go (or a way out), a while before trying again: routes are costly. */
        float destCd, fleeCd, rerouteCd;

        float length() {
            if (kind == K_APC) return 11f;
            if (kind == K_HUMVEE) return 8.5f;
            if (kind == K_BIKE) return 4.5f;
            return type == TANK ? 11f : type == TRUCK || type == FIRE_ENGINE || type == AMBULANCE ? 9.5f
                    : type == CAR ? MODEL_HL[model] : 8f;
        }
    }

    /** What a vehicle is, for its card: "Police car - Unit 14 (Highway Patrol)", "Taxi", "Engine 2"... */
    String describe(Vehicle v) {
        switch (v.type) {
            case CRUISER:
                if (v.kind == K_BIKE) return "Police motorcycle  -  " + callsign(v);
                if (v.agency == 3) return "Park rangers' truck  -  " + callsign(v);
                return v.number > 0 ? "Police car  -  " + callsign(v) : "Police car";
            case TRUCK:
                if (v.kind == K_APC) return "Armoured personnel carrier" + (v.guardUnit ? "  -  " + Country.guard(city.country.id)[1] : "");
                if (v.kind == K_HUMVEE) return (v.guardUnit ? Country.guard(city.country.id)[1] + " " : "") + "Humvee" + (v.loop ? "  -  base patrol" : "");
                if (v.kind == K_RIOT_VAN) return "Riot police van";
                return v.swat ? Country.swat(city.country.id)[0] + " van" : v.guardUnit ? Country.guard(city.country.id)[1] + " truck" : v.supply > 0 ? "Army supply truck" : "Army truck";
            case HELI: return v.kind == K_RESCUE_HELI ? "Rescue helicopter" : v.kind == K_POLICE_HELI ? "Police helicopter" : "Air " + Math.max(1, v.number) + "  -  army helicopter";
            case FIRE_ENGINE: return "Fire engine " + v.number;
            case TANK: return "Tank";
            case AMBULANCE: return "Ambulance";
            case TRAIN: return "Train";
            case JET: return "Jet";
            default:
                return MODEL_NAMES[v.model] + (v.through ? "  -  through traffic" : v.fleeing ? "  -  leaving town" : "");
        }
    }

    /** A short line about what a vehicle is doing, shown above it on the map. */
    String status(Vehicle v) {
        String where = v.place != null ? v.place : null;
        switch (v.type) {
            case HELI:
                if (v.state == IDLE) {
                    String who = v.kind == K_RESCUE_HELI ? "Rescue helicopter" : v.kind == K_POLICE_HELI ? "Police helicopter" : "Air " + v.number;
                    return who + ": " + (v.refuel > 0 ? "Refuelling on the pad (" + (int) v.refuel + "s)" : livePilot(v) == null ? "Grounded, no pilot" : "On the pad, ready");
                }
                if (v.state == MUSTER) return (v.kind == K_RESCUE_HELI ? "Rescue helicopter" : v.kind == K_POLICE_HELI ? "Police helicopter" : "Air " + v.number)
                        + ": Crew boarding (" + v.crew.size() + "/" + v.crewWanted + ")";
                if (v.kind == K_RESCUE_HELI) {
                    switch (v.state) {
                        case FLY_IN: return "Rescue helicopter: En route" + (where != null ? " to " + where : "");
                        case HOVER: return v.team == null ? "Rescue helicopter: Coming into a hover" : v.pickup ? "Rescue helicopter: Winching everyone up (" + v.passengers + " aboard)"
                                : "Rescue helicopter: Lowering the team";
                        case STANDBY: return "Rescue helicopter: At base, waiting for the team's call";
                        default: return v.passengers > 0 ? "Rescue helicopter: " + v.passengers + " aboard, to the hospital" : "Rescue helicopter: Returning";
                    }
                }
                if (v.kind == K_POLICE_HELI) {
                    switch (v.state) {
                        case FLY_IN: return "Police helicopter: En route" + (where != null ? " to " + where : "");
                        case CIRCLE: return "Police helicopter: Marksman engaging (" + (int) v.timer + "s)";
                        default: return "Police helicopter: Returning";
                    }
                }
                String air = "Air " + Math.max(1, v.number);
                switch (v.state) {
                    case SPOOL: return air + ": Starting up";
                    case FLY_IN: return v.alt < 0.9f ? air + ": Taking off" : air + ": En route" + (where != null ? " to " + where : "");
                    case DROP: return air + ": Putting troops down";
                    case CIRCLE: return v.escort != null ? air + ": Flying cover for the convoy" : air + ": Door gunners engaging (" + (int) v.timer + "s)";
                    case LAND: return air + ": Landing";
                    default: return air + ": Returning to base";
                }
            case TRAIN:
                if (v.state == WAIT) return "Train: At the platform (" + (int) v.timer + "s)";
                if (!v.pad && city.stationX >= 0) return "Train: Stopping at " + city.stationName;
                return "Train: Passing through";
            case JET:
                return v.timer > 0 ? "Jet: Bombs away!" : "Jet: Attack run";
            case TANK:
                if (v.broken) return "Tank: Knocked out";
                switch (v.state) {
                    case WAIT: return "Tank: Starting engine";
                    case DRIVE: return "Tank: Moving to " + (where != null ? where : "contact");
                    case ENGAGE: return v.cannonCd > 2.5f ? "Tank: Firing!" : "Tank: Engaging";
                    default: return "Tank: Returning to base";
                }
            case AMBULANCE:
                if (v.broken) return "Ambulance: Broken down";
                if (v.state == RETURN) return v.riders.isEmpty() ? "Ambulance: Returning" : "Ambulance: Patient on board";
                if (v.state == LOAD) return "Ambulance: Loading patient";
                return "Ambulance: Responding";
            case FIRE_ENGINE:
                if (v.broken) return "Engine " + v.number + ": Out of action";
                if (v.state == SPRAY) return "Engine " + v.number + ": Fighting fire";
                if (v.state == RETURN) return "Engine " + v.number + ": Returning";
                return "Engine " + v.number + ": Responding";
            case CRUISER:
            case TRUCK: {
                String who = v.kind == K_APC ? "APC" : v.kind == K_HUMVEE ? "Humvee" : v.kind == K_RIOT_VAN ? "Riot van"
                        : v.kind == K_BIKE ? "Police motorcycle" : v.type == CRUISER ? "Police car" : "Army truck";
                if (v.restock) return v.state == RETURN ? "Supply truck: Heading out of town"
                        : "Supply truck: Delivering" + (where != null ? " to " + where : "");
                if (v.type == TRUCK && v.state == SCENE) return who + ": Fire support" + (where != null ? " at " + where : "");
                if (v.loop && v.state == DRIVE) return who + ": Patrolling" + (where != null ? " to " + where : "");
                if (v.broken) return who + ": Wrecked";
                if (v.state == BLOCK) return "Police car: Roadblock" + (where != null ? " on " + where : "");
                if (v.block != null && v.state == DRIVE) return "Police car: Setting up a roadblock" + (where != null ? " on " + where : "");
                if (v.patrol && v.state == PATROL) return who + ": On patrol";
                if (v.patrol && v.state == SCENE) return who + ": At the scene" + (where != null ? " on " + where : "");
                if (v.patrol && v.state == RECALL) return who + ": Picking up its officers";
                if (v.type == TRUCK && v.state == MUSTER) return "Army truck: Squad mounting up";
                if (v.state == WAIT) return who + ": Loading up";
                if (v.state == DRIVE) return who + ": " + (v.type == TRUCK ? "Carrying squad" : "Responding") + (where != null ? " to " + where : "");
                return who + ": Returning";
            }
            default:
                if (v.broken) return v.burnt ? "Burnt out" : "Broken down";
                if (v.state == ABANDONED) return "Abandoned";
                if (v.rescue) return "Driving " + v.riders.size() + " to safety";
                if (v.waitTimer > 0) return "Picking someone up";
                if (!v.riders.isEmpty()) return v.riders.size() + " on board";
                return null;
        }
    }

    private final World w;
    private final City city;
    final ArrayList<Vehicle> vehicles = new ArrayList<Vehicle>();
    private int engineCount, unitCount;
    /** How many patrol cars the police keep on the streets. */
    int patrolTarget;
    private float staffCd;

    Fleet(World w) {
        this.w = w;
        this.city = w.city;
    }

    void clear() {
        vehicles.clear();
    }

    private Vehicle make(int type) {
        Vehicle v = new Vehicle();
        v.type = type;
        v.hp = v.maxHp = MAX_HP[type];
        return v;
    }

    /** Makes a truck, police car or helicopter into one of its other kinds (K_APC...). */
    void setKind(Vehicle v, int kind) {
        v.kind = kind;
        float hp = kind == K_APC ? 520 : kind == K_HUMVEE ? 220 : kind == K_RIOT_VAN ? 380 : kind == K_BIKE ? 70 : v.maxHp;
        v.hp = v.maxHp = hp;
    }

    /** The roof gun of an APC or a Humvee: short bursts at the nearest of the dead in reach. */
    private void turret(Vehicle v, float dt) {
        if (v.broken || (v.kind != K_APC && v.kind != K_HUMVEE)) return;
        v.gunCd -= dt;
        if (v.gunCd > 0) return;
        float range = v.kind == K_APC ? 170 : 140;
        Entity z = w.nearestZombie(v.x, v.y, range);
        if (z == null) {
            v.gunCd = 0.6f;
            return;
        }
        v.turret = (float) Math.atan2(z.y - v.y, z.x - v.x);
        if (w.turretShot(v.x, v.y, z, v.kind == K_APC ? 24 : 18)) {
            v.burst++;
            v.gunCd = v.burst % 5 == 0 ? 1.1f : 0.14f;
        } else v.gunCd = 0.4f;
    }

    /** Ordinary traffic: cars driving around town between random spots on the roads. */
    void spawnTraffic(int n) {
        int buses = 0;
        for (int i = 0; i < n; i++) {
            // Some of it is through traffic on the highway, end to end.
            if (city.hwyAxis >= 0 && w.rnd.nextFloat() < 0.3f) {
                float[] run = city.highwayRun(w.rnd);
                if (run != null && !inView(run[0], run[1])) {
                    Vehicle v = make(CAR);
                    v.state = CRUISE;
                    v.x = run[0];
                    v.y = run[1];
                    pickModel(v, false);
                    v.angle = (float) Math.atan2(run[3] - run[1], run[2] - run[0]);
                    v.through = true;
                    if (route(v, run[2], run[3])) vehicles.add(v);
                    continue;
                }
            }
            float[] start = randomRoad();
            // New cars turn up out of sight, not out of thin air in front of you.
            for (int k = 0; k < 8 && start != null && inView(start[0], start[1]); k++) start = randomRoad();
            if (start == null || inView(start[0], start[1])) continue;
            Vehicle v = make(CAR);
            v.state = CRUISE;
            v.x = start[0];
            v.y = start[1];
            pickModel(v, buses < Math.max(1, n / 14));
            if (v.model == M_BUS) buses++;
            v.angle = w.rnd.nextInt(4) * (float) Math.PI / 2;
            if (!newDestination(v)) continue;
            vehicles.add(v);
        }
    }

    /** What kind of car this is, and its paint, as the country's streets would have them. */
    private void pickModel(Vehicle v, boolean busOk) {
        Country c = city.country;
        int m = c.carModels[w.rnd.nextInt(c.carModels.length)];
        if (m == M_BUS && !busOk) m = M_SEDAN;
        v.model = m;
        v.color = m == M_TAXI ? c.taxiColor : m == M_BUS ? c.busColor : c.carColors[w.rnd.nextInt(c.carColors.length)];
    }

    private float[] randomRoad() {
        for (int k = 0; k < 200; k++) {
            int tx = w.rnd.nextInt(city.w), ty = w.rnd.nextInt(city.h);
            // (Nobody's trip ends on the highway.)
            if (city.tiles[ty * city.w + tx] == City.ROAD && (city.oneWay == null || city.oneWay[ty * city.w + tx] == 0))
                return new float[]{tx * City.T + City.T / 2f, ty * City.T + City.T / 2f};
        }
        return null;
    }

    /**
     * A vehicle's route, kept compact: the distance to go on each tile of a corridor three tiles either side of
     * the way from where it set off (everything else is off the route). A whole-map field per car took most of
     * a phone's memory on the big maps.
     */
    static final class Route {
        private int[] keys, vals;
        private int mask, size;

        Route(int entries) {
            int n = 16;
            while (n < entries * 2) n <<= 1;
            keys = new int[n];
            vals = new int[n];
            java.util.Arrays.fill(keys, -1);
            mask = n - 1;
        }

        private void grow() {
            int[] ok = keys, ov = vals;
            keys = new int[ok.length * 2];
            vals = new int[ok.length * 2];
            java.util.Arrays.fill(keys, -1);
            mask = keys.length - 1;
            size = 0;
            for (int i = 0; i < ok.length; i++) if (ok[i] != -1) put(ok[i], ov[i]);
        }

        private int slot(int k) {
            int h = k * 0x9E3779B1;
            return (h ^ (h >>> 15)) & mask;
        }

        void put(int k, int v) {
            if ((size + 1) * 2 > keys.length) grow();
            int i = slot(k);
            while (keys[i] != -1 && keys[i] != k) i = (i + 1) & mask;
            if (keys[i] == -1) size++;
            keys[i] = k;
            vals[i] = v;
        }

        int get(int k) {
            int i = slot(k);
            while (true) {
                int key = keys[i];
                if (key == k) return vals[i];
                if (key == -1) return City.FAR;
                i = (i + 1) & mask;
            }
        }
    }

    /** One whole-map buffer the route searches share. */
    private int[] scratch;

    private int[] scratchField() {
        if (scratch == null || scratch.length != city.w * city.h) scratch = new int[city.w * city.h];
        return scratch;
    }

    /** The corridor along the way down a full field from (x, y). */
    private Route corridor(int[] field, float x, float y) {
        int W = city.w, H = city.h, ti = city.tileIndex(x, y), tx0 = ti % W, ty0 = ti / W, t = ti, best = City.FAR;
        // From its own tile if that's on the way (not the nearest low tile, which may be over the barrier on the
        // other carriageway); otherwise from the best road beside it.
        if (field[ti] >= City.FAR)
            for (int j = ty0 - 2; j <= ty0 + 2; j++)
                for (int i = tx0 - 2; i <= tx0 + 2; i++)
                    if (i >= 0 && j >= 0 && i < W && j < H && field[j * W + i] < best) {
                        best = field[j * W + i];
                        t = j * W + i;
                    }
        int[] path = new int[64];
        int n = 0;
        path[n++] = ti;
        for (int guard = 0; guard < W * H; guard++) {
            if (n == path.length) path = java.util.Arrays.copyOf(path, n * 2);
            path[n++] = t;
            if (field[t] <= 4) break;
            int next = downhillRaw(field, t);
            if (next == t) break;
            t = next;
        }
        Route r = new Route(n * 10 + 64);
        for (int q = 0; q < n; q++) {
            int px = path[q] % W, py = path[q] / W;
            // (Along the highway, both carriageways and the verges: a car that ends up on the other side of the
            // barrier must still know which way is on.)
            int R = city.nearHighway((px + 0.5f) * City.T, (py + 0.5f) * City.T, 6 * City.T) ? 9 : 3;
            for (int j = Math.max(0, py - R); j <= Math.min(H - 1, py + R); j++)
                for (int i = Math.max(0, px - R); i <= Math.min(W - 1, px + R); i++) {
                    int f = field[j * W + i];
                    if (f < City.FAR) r.put(j * W + i, f);
                }
        }
        return r;
    }

    /** The route's distance at (x, y), or from the road right next to it. */
    private int nearField(int[] field, float x, float y) {
        int ti = city.tileIndex(x, y), tx = ti % city.w, ty = ti / city.w, best = City.FAR;
        for (int j = ty - 1; j <= ty + 1; j++)
            for (int i = tx - 1; i <= tx + 1; i++)
                if (i >= 0 && j >= 0 && i < city.w && j < city.h) best = Math.min(best, field[j * city.w + i]);
        return best;
    }

    /** True if (x, y) is on the player's screen (or near it). */
    boolean inView(float x, float y) {
        return x > w.viewX0 - 60 && x < w.viewX1 + 60 && y > w.viewY0 - 60 && y < w.viewY1 + 60;
    }

    private boolean newDestination(Vehicle v) {
        if (v.destCd > 0) return false;
        boolean ok = pickDestination(v);
        if (!ok) v.destCd = 2 + w.rnd.nextFloat() * 2;
        return ok;
    }

    private boolean pickDestination(Vehicle v) {
        // (At most a few full route searches each time.)
        int routes = 0;
        // Highway patrol keeps to the highway, sheriffs to the country roads.
        if (v.patrol && v.agency > 0 && v.state == PATROL)
            for (int tries = 0; tries < 12; tries++) {
                float[] d = v.agency == 1 ? city.randomHighway(w.rnd) : city.randomCountryRoad(w.rnd);
                // (Highway patrol: on ahead along its own carriageway, so it never has to turn round.)
                if (d != null && v.agency == 1 && city.onHighway(v.x, v.y)) {
                    // On ahead to the far end; once there, round onto the other side and back.
                    float[] mine = null, other = null;
                    for (int q = 0; q < 8 && (mine == null || other == null); q++) {
                        float[] run = city.highwayRun(w.rnd);
                        if (run == null) break;
                        if (Math.abs(city.hwyAxis == 0 ? run[1] - v.y : run[0] - v.x) < 3 * City.T) mine = run;
                        else other = run;
                    }
                    if (mine == null || other == null) continue;
                    float[] run = Math.hypot(mine[2] - v.x, mine[3] - v.y) < 300 ? other : mine;
                    d = new float[]{run[2], run[3]};
                }
                // (Sheriffs keep to their own patch of country rather than crossing town; rangers to the lanes
                // round their park.)
                if (d != null && v.agency == 2 && tries < 10 && Math.hypot(d[0] - v.x, d[1] - v.y) > 1400) continue;
                if (d != null && v.agency == 3 && city.rangerLot != null && tries < 10
                        && Math.hypot(d[0] - city.rangerLot[0], d[1] - city.rangerLot[1]) > 1100) continue;
                if (d != null && Math.hypot(d[0] - v.x, d[1] - v.y) > 300) {
                    if (route(v, d[0], d[1])) return true;
                    if (++routes >= 3) break;
                }
            }
        for (int tries = 0; tries < 12; tries++) {
            float[] d = randomRoad();
            if (d == null || Math.hypot(d[0] - v.x, d[1] - v.y) < (tries < 6 ? 400 : 160)) continue;
            // Somewhere on ahead, as a rule: a car doesn't spin round in the road for its next trip.
            float hx = (float) Math.cos(v.angle), hy = (float) Math.sin(v.angle);
            if (tries < 8 && (d[0] - v.x) * hx + (d[1] - v.y) * hy < 0) continue;
            if (route(v, d[0], d[1])) {
                if (tries >= 5 || (v.type != CAR && !v.patrol) || routes >= 3) return true;
                // And the way there starts ahead too (not a U-turn across the lanes, as a patrol car
                // turning round for every new beat would): no step back the way it came in the first few.
                int t = city.tileIndex(v.x, v.y);
                boolean back = false;
                for (int k = 0; k < 5 && !back; k++) {
                    int n = downhill(v.field, t);
                    if (n == t) break;
                    if ((n % city.w - t % city.w) * hx + (n / city.w - t / city.w) * hy < -0.5f) back = true;
                    t = n;
                }
                if (!back) return true;
            }
            if (++routes >= 4) break;
        }
        return false;
    }

    /** Road tiles on the edge of the map: the ways out of town. */
    private int[] exits;

    /** The nearest way out of town by road (a tile centre), or null. */
    float[] exitNear(float x, float y) {
        if (exits == null) {
            java.util.ArrayList<Integer> list = new java.util.ArrayList<Integer>();
            int W = city.w, H = city.h;
            for (int i = 0; i < W; i += 1) {
                if (city.tiles[i] == City.ROAD) list.add(i);
                if (city.tiles[(H - 1) * W + i] == City.ROAD) list.add((H - 1) * W + i);
            }
            for (int j = 1; j < H - 1; j++) {
                if (city.tiles[j * W] == City.ROAD) list.add(j * W);
                if (city.tiles[j * W + W - 1] == City.ROAD) list.add(j * W + W - 1);
            }
            exits = new int[list.size()];
            for (int i = 0; i < exits.length; i++) exits[i] = list.get(i);
        }
        float best = Float.MAX_VALUE;
        int bt = -1;
        for (int t : exits) {
            float ex = (t % city.w + 0.5f) * City.T, ey = (t / city.w + 0.5f) * City.T;
            float d = (ex - x) * (ex - x) + (ey - y) * (ey - y) + w.rnd.nextFloat() * 90000;
            if (d < best) {
                best = d;
                bt = t;
            }
        }
        return bt < 0 ? null : new float[]{(bt % city.w + 0.5f) * City.T, (bt / city.w + 0.5f) * City.T};
    }

    /** Points the vehicle at a new goal. Returns false if it can't be reached by road. */
    /**
     * Route searches left this step for everyday traffic: when the alarm goes up and everyone wants a new route
     * at once, they're spread over a few frames instead of one long stall (the rest try again a moment later).
     * Emergency services never wait.
     */
    private int routeBudget = Integer.MAX_VALUE;

    private boolean route(Vehicle v, float x, float y) {
        if (v.type == CAR && v.player == null) {
            if (routeBudget <= 0) return false;
            routeBudget--;
        }
        int[] field = scratchField();
        // (Everyday traffic and patrol cars keep to the roads; emergencies cut across whatever's paved.)
        boolean strict = v.type == CAR || (v.patrol && v.state != DRIVE);
        int from = city.tileIndex(v.x, v.y);
        if (!city.driveField(field, x, y, strict, from) || nearField(field, v.x, v.y) >= City.FAR) {
            // Off the road (shoved onto the pavement, or parked on a lot): find the way back over whatever's paved.
            if (!strict || !city.driveField(field, x, y, false, from) || nearField(field, v.x, v.y) >= City.FAR) return false;
        }
        v.field = corridor(field, v.x, v.y);
        v.tx = x;
        v.ty = y;
        v.lastDist = Float.MAX_VALUE;
        v.spin = 0;
        v.stuckTimer = 0;
        return true;
    }

    int movingTraffic() {
        int n = 0;
        for (int i = 0; i < vehicles.size(); i++) if (vehicles.get(i).type == CAR && !vehicles.get(i).parked) n++;
        return n;
    }

    boolean heliBusy() {
        // (The police helicopter doesn't count: it's a different service.)
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle v = vehicles.get(i);
            if (v.type == HELI && v.kind != K_POLICE_HELI && v.kind != K_RESCUE_HELI && v.state != IDLE) return true;
        }
        return false;
    }

    /** People riding in cars right now (they still count as alive). */
    int riderCount() {
        int n = 0;
        for (int i = 0; i < vehicles.size(); i++) n += vehicles.get(i).riders.size();
        return n;
    }

    /** Fire engines out on a call from this station. */
    int enginesOut(City.Facility station) {
        int n = 0;
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle v = vehicles.get(i);
            if (v.type == FIRE_ENGINE && !v.broken && v.homeX == station.gateX && v.homeY == station.gateY) n++;
        }
        return n;
    }

    /**
     * Sends units by road: cops in cruisers (two per car), soldiers in one truck. Returns false if there is
     * no road route, in which case the caller lets them walk.
     */
    boolean send(int unitType, int count, float fromX, float fromY, float toX, float toY, Dispatch.Incident inc,
                 Dispatch.SafeZone zone, String place) {
        float[] start = null;
        // An army truck from a base or the armory: one of the trucks parked there drives out.
        if (unitType == Entity.SOLDIER && fromParked) start = city.takeParkedTruck(fromX, fromY, 260);
        if (start == null) start = city.nearestDrivable(fromX, fromY);
        if (start == null) return false;
        int[] full = scratchField();
        if (!city.driveField(full, toX, toY, false, city.tileIndex(start[0], start[1]))
                || full[city.tileIndex(start[0], start[1])] >= City.FAR) return false;
        Route field = corridor(full, start[0], start[1]);
        boolean cops = unitType == Entity.COP;
        int cars = cops ? (count + 1) / 2 : 1;
        for (int i = 0; i < cars; i++) {
            Vehicle v = make(cops ? CRUISER : TRUCK);
            v.state = WAIT;
            v.timer = 1.5f + i * 1.2f;
            v.x = start[0];
            v.y = start[1];
            v.homeX = start[0];
            v.homeY = start[1];
            v.tx = toX;
            v.ty = toY;
            v.field = field;
            v.passengerType = unitType;
            v.passengers = cops ? Math.min(2, count - i * 2) : count;
            v.incident = inc;
            v.zone = zone;
            v.place = place;
            v.lastDist = Float.MAX_VALUE;
            v.spin = 0;
            vehicles.add(v);
        }
        return true;
    }

    /** Whether there's a way to drive from one point to another. */
    boolean canDrive(float fx, float fy, float tx, float ty) {
        float[] start = city.nearestDrivable(fx, fy);
        if (start == null) return false;
        int[] full = scratchField();
        int si = city.tileIndex(start[0], start[1]);
        return city.driveField(full, tx, ty, false, si) && full[si] < City.FAR;
    }

    /** Trucks sent out take one of the army trucks parked nearby (not the SWAT van, which has its own). */
    private boolean fromParked = true;

    /** The SWAT van: one armoured truck carrying six officers to an incident. */
    boolean sendSwat(float fromX, float fromY, Dispatch.Incident inc) {
        int before = vehicles.size();
        fromParked = false;
        boolean sent = send(Entity.SOLDIER, 6, fromX, fromY, inc.x, inc.y, inc, null, inc.place);
        fromParked = true;
        if (!sent || vehicles.size() == before) return false;
        Vehicle v = vehicles.get(vehicles.size() - 1);
        v.swat = true;
        v.passengerType = Entity.COP;
        return true;
    }

    /** The riot van: six riot officers with shields, from the precinct to a big crowd of the dead. */
    boolean sendRiotVan(float fromX, float fromY, Dispatch.Incident inc) {
        int before = vehicles.size();
        fromParked = false;
        boolean sent = send(Entity.SOLDIER, 6, fromX, fromY, inc.x, inc.y, inc, null, inc.place);
        fromParked = true;
        if (!sent || vehicles.size() == before) return false;
        Vehicle v = vehicles.get(vehicles.size() - 1);
        setKind(v, K_RIOT_VAN);
        v.passengerType = Entity.COP;
        return true;
    }

    /** The police helicopter: flies in over a big incident with a marksman at the open door. */
    void sendPoliceHeli(float fromX, float fromY, Dispatch.Incident inc) {
        int before = vehicles.size();
        sendHeli(fromX, fromY, inc.x, inc.y, inc.place);
        if (vehicles.size() == before) return;
        Vehicle v = vehicles.get(vehicles.size() - 1);
        setKind(v, K_POLICE_HELI);
        if (v.pad) {
            // (Police helicopters don't use the army's pads: it flies in.)
            v.pad = false;
            v.state = FLY_IN;
            v.alt = 1;
        }
    }

    /**
     * The rescue helicopter: flies out to people cut off by the dead, hovers, lowers a search and rescue team
     * on the winch to bring them in, lifts everyone out and drops them at the hospital.
     */
    void sendRescueHeli(float fromX, float fromY, float toX, float toY, String place) {
        int before = vehicles.size();
        sendHeli(fromX, fromY, toX, toY, place);
        if (vehicles.size() == before) return;
        Vehicle v = vehicles.get(vehicles.size() - 1);
        setKind(v, K_RESCUE_HELI);
        v.number = ++rescueNumber;
        if (v.pad) {
            v.pad = false;
            v.state = FLY_IN;
            v.alt = 1;
        }
        v.baseX = v.homeX;
        v.baseY = v.homeY;
    }

    /** The rescue helicopter is out. */
    boolean rescueHeliBusy() {
        return rescueHelis() > 0;
    }

    /** Rescue helicopters out (on a job, or waiting at base for their team). */
    int rescueHelis() {
        int n = 0;
        for (int i = 0; i < vehicles.size(); i++) if (vehicles.get(i).kind == K_RESCUE_HELI && vehicles.get(i).state != IDLE) n++;
        return n;
    }

    private int rescueNumber;

    /** The park rangers' truck: out of the ranger station along the dirt lanes, one ranger at the wheel. */
    Vehicle startRangerPatrol(City.Building station) {
        float[] start = city.rangerLot != null ? city.nearestDrivable(city.rangerLot[0], city.rangerLot[1])
                : city.nearestDrivable(station.doorX, station.doorY);
        if (start == null) return null;
        Vehicle v = make(CRUISER);
        v.x = start[0];
        v.y = start[1];
        v.patrol = true;
        v.agency = 3;
        v.state = PATROL;
        v.number = 30 + (++unitCount);
        if (!newDestination(v)) return null;
        Entity e = w.create(Entity.COP, v.x, v.y);
        w.makeRanger(e);
        e.rig = v;
        v.crew.add(e);
        vehicles.add(v);
        return v;
    }

    /** The police helicopter is up. */
    boolean policeHeliBusy() {
        for (int i = 0; i < vehicles.size(); i++) if (vehicles.get(i).kind == K_POLICE_HELI && vehicles.get(i).state != IDLE) return true;
        return false;
    }

    /** An army truck carrying ammunition from an armoury to a safe zone. */
    boolean sendSupply(City.Facility from, Dispatch.SafeZone z, int rounds) {
        int before = vehicles.size();
        if (!send(Entity.SOLDIER, 0, from.x, from.y, z.x, z.y, null, z, z.place) || vehicles.size() == before) return false;
        Vehicle v = vehicles.get(vehicles.size() - 1);
        v.supply = rounds;
        return true;
    }

    /**
     * After the outbreak, a supply truck from outside: in from the edge of the map at (fromX, fromY) to a store
     * or an armoury, unloads, and drives back out the way it came.
     */
    boolean sendRestock(float fromX, float fromY, City.Building b, City.Facility f) {
        int before = vehicles.size();
        fromParked = false;
        float tx = b != null ? b.doorX : f.x, ty = b != null ? b.doorY : f.y;
        boolean sent = send(Entity.SOLDIER, 0, fromX, fromY, tx, ty, null, null, b != null ? b.name : f.name);
        fromParked = true;
        if (!sent || vehicles.size() == before) return false;
        Vehicle v = vehicles.get(vehicles.size() - 1);
        v.restock = true;
        v.restockShop = b;
        v.restockFac = f;
        v.reinforcement = true;
        v.angle = (float) Math.atan2(ty - v.y, tx - v.x);
        return true;
    }

    /** Fire engines parked at their stations (one each), waiting for a call. Missing ones are put back. */
    void stationEngines() {
        for (City.Facility f : city.facilities) {
            if (f.kind != City.FACILITY_FIRE) continue;
            boolean have = false;
            for (Vehicle o : vehicles) if (o.type == FIRE_ENGINE && o.station == f && !o.broken) have = true;
            if (have) continue;
            float[] p = city.nearestDrivable(f.gateX, f.gateY);
            if (p == null) continue;
            Vehicle v = make(FIRE_ENGINE);
            v.x = p[0];
            v.y = p[1];
            v.homeX = p[0];
            v.homeY = p[1];
            v.angle = (float) Math.atan2(p[1] - f.y, p[0] - f.x);
            v.state = IDLE;
            v.station = f;
            v.number = ++engineCount;
            vehicles.add(v);
        }
    }

    /** The station's engine, if it's parked there ready to go. */
    Vehicle idleEngine(City.Facility station) {
        for (Vehicle v : vehicles) if (v.type == FIRE_ENGINE && v.station == station && v.state == IDLE && !v.broken && !v.parked) return v;
        return null;
    }

    /**
     * A fire engine is called out: free firefighters at the station run to it and climb aboard, and it
     * leaves once they're on (no crew, no engine). Returns null if it can't go.
     */
    Vehicle sendFireEngine(City.Facility station, World.Fire f) {
        Vehicle v = idleEngine(station);
        if (v == null) return null;
        int crew = 0;
        for (int i = 0, n = w.entities.size(); i < n && crew < 3; i++) {
            Entity e = w.entities.get(i);
            if (e.dead || e.type != Entity.FIREFIGHTER || e.rig != null || e.task != Dispatch.T_NONE) continue;
            if (Math.hypot(e.x - v.x, e.y - v.y) > 260) continue;
            e.rig = v;
            e.task = Dispatch.T_BOARD;
            crew++;
        }
        if (crew == 0) return null;
        v.crewWanted = crew;
        v.state = MUSTER;
        v.timer = 0;
        v.fire = f;
        return v;
    }

    /** A strike jet: comes in from the nearest edge, drops a stick of bombs across the target, flies on. */
    void sendJet(float tx, float ty) {
        Vehicle v = make(JET);
        float a = w.rnd.nextFloat() * (float) Math.PI * 2;
        v.angle = a;
        v.x = tx - (float) Math.cos(a) * 900;
        v.y = ty - (float) Math.sin(a) * 900;
        v.tx = tx;
        v.ty = ty;
        v.speed = 380;
        v.alt = 1;
        v.state = DRIVE;
        vehicles.add(v);
        w.emit(Sfx.JET, tx, ty);
    }

    private boolean updateJet(Vehicle v, float dt) {
        v.x += (float) Math.cos(v.angle) * v.speed * dt;
        v.y += (float) Math.sin(v.angle) * v.speed * dt;
        float d = (float) Math.hypot(v.tx - v.x, v.ty - v.y);
        v.gunCd -= dt;
        boolean over = d < 120;
        if (over && v.gunCd <= 0) {
            v.gunCd = 0.12f;
            v.timer = 1;
            // The bombs take a moment to fall.
            w.blastLater(v.x, v.y, 55, 280, 0.8f);
        }
        if (w.rnd.nextFloat() < dt * 40)
            w.particle(v.x - (float) Math.cos(v.angle) * 14, v.y - (float) Math.sin(v.angle) * 14, 0, 0, 2.5f, 2.5f, 0xFFE8E8E8,
                    World.P_SMOKE);
        return Math.hypot(v.x - v.tx, v.y - v.ty) > 1000 && v.timer > 0;
    }

    /** A tank rolls out of the base towards heavy fighting. */
    boolean sendTank(float fromX, float fromY, float toX, float toY, String place) {
        float[] start = city.nearestDrivable(fromX, fromY);
        if (start == null) return false;
        Vehicle v = make(TANK);
        v.x = start[0];
        v.y = start[1];
        if (!route(v, toX, toY)) return false;
        v.state = WAIT;
        v.timer = 2;
        v.homeX = start[0];
        v.homeY = start[1];
        v.place = place;
        v.angle = v.turret = (float) Math.atan2(toY - v.y, toX - v.x);
        vehicles.add(v);
        w.tanksDeployed++;
        return true;
    }

    int count(int type) {
        int n = 0;
        for (int i = 0; i < vehicles.size(); i++) if (vehicles.get(i).type == type && !vehicles.get(i).broken) n++;
        return n;
    }

    boolean hasPatient(Entity e) {
        for (int i = 0; i < vehicles.size(); i++) if (vehicles.get(i).patient == e) return true;
        return false;
    }

    /** An ambulance leaves the hospital to fetch someone who is hurt. */
    boolean sendAmbulance(City.Facility hospital, Entity patient) {
        float[] start = city.nearestDrivable(hospital.x, hospital.y);
        if (start == null) return false;
        Vehicle v = make(AMBULANCE);
        v.x = start[0];
        v.y = start[1];
        if (!route(v, patient.x, patient.y)) return false;
        v.state = WAIT;
        v.timer = 1;
        v.homeX = start[0];
        v.homeY = start[1];
        v.patient = patient;
        v.place = city.placeName(patient.x, patient.y);
        vehicles.add(v);
        return true;
    }

    /** A car dropped into traffic by the player. */
    Vehicle placeCar(float x, float y) {
        float[] p = city.nearestDrivable(x, y);
        if (p == null) return null;
        Vehicle v = make(CAR);
        v.state = CRUISE;
        v.x = p[0];
        v.y = p[1];
        pickModel(v, false);
        v.angle = w.rnd.nextInt(4) * (float) Math.PI / 2;
        if (!newDestination(v)) return null;
        vehicles.add(v);
        return v;
    }

    /** A police car with two officers, setting off on patrol from here. */
    Vehicle placePolice(float x, float y) {
        for (int tries = 0; tries < 6; tries++) {
            float[] d = randomRoad();
            if (d == null || Math.hypot(d[0] - x, d[1] - y) < 250) continue;
            int before = vehicles.size();
            if (send(Entity.COP, 2, x, y, d[0], d[1], null, null, city.placeName(d[0], d[1])) && vehicles.size() > before) {
                Vehicle v = vehicles.get(vehicles.size() - 1);
                v.timer = 0.3f;
                return v;
            }
        }
        return null;
    }

    /**
     * Police close the main approaches to a new safe zone. For each side of the zone, the nearest road running
     * towards it gets a cruiser sent from the precinct; on arrival it parks angled across the inbound lane with
     * cones out and an officer posted beside it. Returns how many were sent.
     */
    int roadblocks(Dispatch.SafeZone z) {
        City.Facility precinct = city.nearestFacility(City.FACILITY_POLICE, z.x, z.y);
        if (precinct == null) return 0;
        int tx0 = (int) (z.x / City.T), ty0 = (int) (z.y / City.T);
        int rMin = (int) ((z.r + 30) / City.T), rMax = (int) ((z.r + 90) / City.T);
        int sent = 0;
        // North, south, west, east: walk outwards along a narrow band looking for a road heading into the zone.
        int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] dv : dirs) {
            if (sent >= 3) break;
            float[] best = null;
            for (int dist = rMin; dist <= rMax && best == null; dist++)
                for (int side = -3; side <= 3 && best == null; side++) {
                    int tx = tx0 + dv[0] * dist + (dv[0] == 0 ? side : 0), ty = ty0 + dv[1] * dist + (dv[1] == 0 ? side : 0);
                    if (tx < 2 || ty < 2 || tx >= city.w - 2 || ty >= city.h - 2) continue;
                    int dir = city.roadDirAt(tx, ty);
                    // The road must run towards the zone (north-south above and below it, east-west to the sides).
                    if (dir != (dv[0] == 0 ? 1 : 2)) continue;
                    float x = tx * City.T + City.T / 2f, y = ty * City.T + City.T / 2f;
                    boolean taken = false;
                    for (int k = 0; k < vehicles.size() && !taken; k++)
                        if (Math.hypot(vehicles.get(k).x - x, vehicles.get(k).y - y) < 24) taken = true;
                    if (!taken) best = new float[]{x, y, dir};
                }
            if (best == null) continue;
            int before = vehicles.size();
            if (!send(Entity.COP, 1, precinct.x, precinct.y, best[0], best[1], null, null, city.placeName(best[0], best[1])))
                continue;
            for (int k = before; k < vehicles.size(); k++) {
                Vehicle v = vehicles.get(k);
                v.block = z;
                v.blockDir = (int) best[2];
                v.timer = 1 + sent * 2;
            }
            sent++;
        }
        return sent;
    }

    /** A roadblock car has arrived: it parks angled across the lane, lights on, and its officer takes post. */
    private void setUpRoadblock(Vehicle v) {
        float along = v.blockDir == 1 ? (float) Math.PI / 2 : 0;
        v.angle = along + (w.rnd.nextBoolean() ? 1 : -1) * 0.75f;
        v.parked = true;
        v.state = BLOCK;
        v.speed = 0;
        v.cones = true;
        float nx = (float) Math.cos(v.angle + 1.57f), ny = (float) Math.sin(v.angle + 1.57f);
        float[] p = city.findWalkable(v.x + nx * 10, v.y + ny * 10);
        if (p != null && v.passengers > 0) {
            Entity cop = w.spawn(Entity.COP, p[0], p[1]);
            if (cop != null) {
                cop.task = Dispatch.T_POST;
                cop.postX = p[0];
                cop.postY = p[1];
                v.guard = cop;
            }
        }
        v.passengers = 0;
    }

    /** A tank parked here that holds the area for a few minutes. */
    Vehicle placeTank(float x, float y) {
        float[] p = city.nearestDrivable(x, y);
        if (p == null) return null;
        Vehicle v = make(TANK);
        v.x = p[0];
        v.y = p[1];
        v.homeX = p[0];
        v.homeY = p[1];
        v.state = ENGAGE;
        v.timer = 240;
        v.place = city.placeName(p[0], p[1]);
        v.angle = v.turret = w.rnd.nextInt(4) * (float) Math.PI / 2;
        vehicles.add(v);
        w.tanksDeployed++;
        return v;
    }

    /** A fire engine that heads for the nearest fire. Null if nothing is burning. */
    Vehicle placeFireEngine(float x, float y) {
        World.Fire f = w.nearestFire(x, y, 2000);
        float[] p = city.nearestDrivable(x, y);
        if (f == null || p == null) return null;
        Vehicle v = make(FIRE_ENGINE);
        v.x = p[0];
        v.y = p[1];
        if (!route(v, f.x, f.y)) return null;
        v.state = WAIT;
        v.timer = 0.5f;
        v.homeX = p[0];
        v.homeY = p[1];
        v.fire = f;
        f.engine = v;
        v.number = ++engineCount;
        // It comes with its crew aboard.
        for (int k = 0; k < 2; k++) {
            Entity e = w.create(Entity.FIREFIGHTER, v.x, v.y);
            e.rig = v;
            v.crew.add(e);
        }
        vehicles.add(v);
        return v;
    }

    // ------------------------------------------------------------------ helicopters on their pads (10.20)

    private int airNumber;

    /**
     * Every helipad has its helicopter, parked there from the start, with its flight crew standing by it: the
     * army's (a pilot), the police's (a pilot) and the rescue service's (a pilot and a three-strong rescue team).
     * Run now and then: a helicopter lost is not replaced, but a pilot who's been killed is (from whoever's
     * nearby, while there are any).
     */
    void stationHelis() {
        java.util.List<float[]> pads = city.helipads();
        for (int k = 0; k < pads.size(); k++) {
            float[] p = pads.get(k);
            Vehicle v = null;
            for (int i = 0; i < vehicles.size() && v == null; i++) if (vehicles.get(i).padAt == p) v = vehicles.get(i);
            if (v == null) {
                if (padLost.contains(p)) continue;
                v = make(HELI);
                v.padAt = p;
                v.pad = true;
                v.x = v.homeX = v.baseX = p[0];
                v.y = v.homeY = v.baseY = p[1];
                v.angle = -(float) Math.PI / 2;
                v.alt = 0;
                v.state = IDLE;
                int kind = City.padKind(p);
                if (kind == City.PAD_POLICE) setKind(v, K_POLICE_HELI);
                else if (kind == City.PAD_RESCUE) {
                    setKind(v, K_RESCUE_HELI);
                    v.number = ++rescueNumber;
                } else v.number = ++airNumber;
                vehicles.add(v);
            }
            if (v.state != IDLE) continue;
            // The flight crew, standing by on the pad.
            for (int i = v.flight.size() - 1; i >= 0; i--) if (v.flight.get(i).dead && !v.crew.contains(v.flight.get(i))) v.flight.remove(i);
            boolean army = v.kind != K_POLICE_HELI && v.kind != K_RESCUE_HELI;
            int want = v.kind == K_RESCUE_HELI ? 4 : 1;
            // (After loading a saved game the crew are already standing about: they're taken back on, not doubled.)
            while (v.flight.size() < want) {
                boolean pilot = livePilot(v) == null;
                Entity found = null;
                float fd = Float.MAX_VALUE;
                for (int i = 0, n = w.entities.size(); i < n; i++) {
                    Entity e = w.entities.get(i);
                    if (e.dead || (pilot ? e.role != Entity.ROLE_PILOT : e.role != Entity.ROLE_SAR) || e.type != (army ? Entity.SOLDIER : Entity.COP)) continue;
                    float d = (float) Math.hypot(e.x - p[0], e.y - p[1]);
                    if (d >= fd || inFlight(e)) continue;
                    fd = d;
                    found = e;
                }
                if (found == null) break;
                v.flight.add(found);
            }
            while (v.flight.size() < want && v.crewSpawns < 8) {
                v.crewSpawns++;
                boolean pilot = livePilot(v) == null;
                Entity e = pilot ? w.spawn(army ? Entity.SOLDIER : Entity.COP, p[0] + 30, p[1] + 10)
                        : w.spawnCop(Entity.ROLE_SAR, p[0] + 30, p[1] + 10);
                if (e == null) break;
                if (pilot) {
                    e.role = Entity.ROLE_PILOT;
                    e.callsign = v.number;
                } else {
                    e.callsign = v.number;
                    e.member = v.flight.size() - 1;
                }
                v.flight.add(e);
            }
            for (int i = 0; i < v.flight.size(); i++) {
                Entity e = v.flight.get(i);
                if (e.dead || e.task == Dispatch.T_POST || e.task == Dispatch.T_BOARD) continue;
                if (e.task != Dispatch.T_NONE && e.task != Dispatch.T_RESCUE) continue;
                standBy(v, e, i);
            }
        }
    }

    private boolean inFlight(Entity e) {
        for (int i = 0; i < vehicles.size(); i++) if (vehicles.get(i).flight.contains(e)) return true;
        return false;
    }

    /** Helipads whose helicopter has been lost for good. */
    private final java.util.HashSet<float[]> padLost = new java.util.HashSet<float[]>();

    /** A member of the flight crew waits at the side of the pad. */
    private void standBy(Vehicle v, Entity e, int i) {
        e.task = Dispatch.T_POST;
        e.rig = null;
        e.postX = v.padAt[0] + 30 + (i % 2) * 8;
        e.postY = v.padAt[1] - 8 + i * 9;
    }

    /** Helicopters of a kind (0 the army's) the city has. */
    int helisOfKind(int kind) {
        int n = 0;
        for (int i = 0; i < vehicles.size(); i++) if (vehicles.get(i).type == HELI && vehicles.get(i).kind == kind) n++;
        return n;
    }

    /** The helicopter's pilot, alive (on foot or aboard), or null: no pilot, no flight. */
    Entity livePilot(Vehicle v) {
        for (int i = 0; i < v.flight.size(); i++) {
            Entity e = v.flight.get(i);
            if (e.role == Entity.ROLE_PILOT && (!e.dead || v.crew.contains(e))) return e;
        }
        return null;
    }

    /** The nearest helicopter of a kind (0 the army's) sitting on its pad ready to go, or null. */
    Vehicle readyHeli(int kind, float x, float y) {
        Vehicle best = null;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle v = vehicles.get(i);
            if (v.type != HELI || v.kind != kind || v.state != IDLE || v.refuel > 0 || v.padAt == null || livePilot(v) == null) continue;
            if (kind == K_RESCUE_HELI && sarOnFoot(v) == 0) continue;
            float d = (v.x - x) * (v.x - x) + (v.y - y) * (v.y - y);
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        return best;
    }

    private int sarOnFoot(Vehicle v) {
        int n = 0;
        for (Entity e : v.flight) if (!e.dead && e.role == Entity.ROLE_SAR) n++;
        return n;
    }

    /**
     * Sends a helicopter off its pad: the pilot (and the rescue team, door gunners from the base or a police
     * marksman) walk out to it and climb aboard, it spins up, and only then does it take off. extra: how many
     * more to take from the free units nearby (door gunners, troops).
     */
    boolean launchHeli(Vehicle v, float tx, float ty, String place, Dispatch.Incident inc, int extra) {
        if (v == null || v.state != IDLE) return false;
        Entity pilot = livePilot(v);
        if (pilot == null) return false;
        v.tx = tx;
        v.ty = ty;
        v.place = place;
        v.incident = inc;
        v.team = null;
        v.pickup = false;
        v.recall = false;
        v.standby = false;
        v.passengers = 0;
        v.troops = 0;
        v.state = MUSTER;
        v.timer = 0;
        int n = 0;
        for (Entity e : v.flight) {
            if (e.dead) continue;
            e.task = Dispatch.T_BOARD;
            e.rig = v;
            n++;
        }
        int type = v.kind == K_POLICE_HELI || v.kind == K_RESCUE_HELI ? Entity.COP : Entity.SOLDIER;
        for (int k = 0; k < extra; k++) {
            Entity best = null;
            float bd = 600 * 600;
            for (int i = 0, m = w.entities.size(); i < m; i++) {
                Entity e = w.entities.get(i);
                if (e.dead || e.type != type || e.task != Dispatch.T_NONE || e.rig != null || e.agency != 0 || e.scout) continue;
                if (e.role == Entity.ROLE_COMMANDER || e.role == Entity.ROLE_GUARD || e.ammo + e.reserve <= 0) continue;
                float d = (e.x - v.x) * (e.x - v.x) + (e.y - v.y) * (e.y - v.y);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            if (best == null) break;
            best.task = Dispatch.T_BOARD;
            best.rig = v;
            n++;
        }
        v.crewWanted = n;
        return true;
    }

    /** Lands it back on its pad: everyone climbs out, the flight crew stand by, the rest go back to their duties. */
    private void landed(Vehicle v) {
        v.alt = 0;
        v.hvx = v.hvy = v.speed = 0;
        v.x = v.padAt[0];
        v.y = v.padAt[1];
        v.state = IDLE;
        v.refuel = 40;
        v.escort = null;
        v.team = null;
        v.incident = null;
        ArrayList<Entity> out = new ArrayList<Entity>(v.crew);
        crewOut(v, true);
        for (Entity e : out) {
            int i = v.flight.indexOf(e);
            if (i >= 0 && !e.dead) standBy(v, e, i);
        }
    }

    /** The helicopter: flies from the helipad (or the map edge) and circles the target with a door gunner. */
    void sendHeli(float fromX, float fromY, float toX, float toY, String place) {
        Vehicle v = make(HELI);
        float[] hp = city.nearestHelipad(fromX, fromY);
        v.pad = hp != null && Math.hypot(hp[0] - fromX, hp[1] - fromY) < 10;
        v.state = v.pad ? SPOOL : FLY_IN;
        v.alt = v.pad ? 0 : 1;
        // Engines and rotor take a while to come up to speed.
        v.timer = 16;
        if (!v.pad) {
            // Coming from outside the city: it is heard before it is seen.
            float dx = fromX - toX, dy = fromY - toY, d = Math.max(1, (float) Math.hypot(dx, dy));
            fromX += dx / d * 500;
            fromY += dy / d * 500;
        }
        v.x = fromX;
        v.y = fromY;
        v.homeX = fromX;
        v.homeY = fromY;
        v.tx = toX;
        v.ty = toY;
        v.place = place;
        v.angle = (float) Math.atan2(toY - fromY, toX - fromX);
        vehicles.add(v);
    }

    int trafficTarget;
    /** Less background traffic on a struggling phone. */
    float trafficScale = 1;

    /** How many cars are about: as many to a street as on a small map, so it grows with the map's area. */
    static int trafficFor(City city) {
        int base = new int[]{0, 12, 24}[city.cfg.traffic()];
        return Math.min(base * 10, (int) (base * (long) city.w * city.h / (96 * 128)));
    }
    private float trafficTimer, trainTimer = 20;

    /** A train is on the line near this car's crossing: wait. */
    private boolean trainComing(Vehicle v) {
        if (city.railY0 < 0 || city.underRailBridge((int) (v.x / City.T))) return false;
        float ry = (city.railY0 + city.railRows / 2f) * City.T;
        float dy = ry - v.y;
        if (Math.abs(dy) > 60 || Math.abs(dy) < 26) return false;
        // Only if heading towards the tracks.
        if ((float) Math.sin(v.angle) * dy <= 0) return false;
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle t = vehicles.get(i);
            if (t.type != TRAIN) continue;
            float ahead = (v.x - t.x) * (float) Math.cos(t.angle);
            if (ahead > -TRAIN_LENGTH - 20 && ahead < 260) return true;
        }
        return false;
    }

    private void spawnTrain() {
        Vehicle v = make(TRAIN);
        boolean east = w.rnd.nextBoolean();
        v.state = DRIVE;
        v.y = (city.railY0 + city.railRows / 2f) * City.T;
        v.x = east ? -20 : city.worldW() + 20;
        v.angle = east ? 0 : (float) Math.PI;
        v.speed = 110;
        vehicles.add(v);
    }

    /** A train is at, or about to reach, the level crossing at x: the barriers are down. */
    boolean crossingClosed(float x) {
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle t = vehicles.get(i);
            if (t.type != TRAIN) continue;
            float ahead = (x - t.x) * (float) Math.cos(t.angle);
            if (ahead > -TRAIN_LENGTH - 24 && ahead < 300) return true;
        }
        return false;
    }

    private boolean updateTrain(Vehicle v, float dt) {
        float dir = (float) Math.cos(v.angle);
        if (v.state == WAIT) {
            // A short stop at the platform.
            v.speed = 0;
            v.timer -= dt;
            float mid = v.x - dir * TRAIN_LENGTH / 2;
            if (v.timer <= 0 || w.countZombiesNear(mid, v.y, 80) > 2) {
                v.state = DRIVE;
                w.emit(Sfx.HORN, v.x, v.y);
            }
            return false;
        }
        float cruise = 110;
        if (!v.pad && city.stationX >= 0) {
            // Slow down for the station and stop with the train alongside the platform.
            float ahead = (city.stationX + dir * TRAIN_LENGTH / 2 - v.x) * dir;
            if (ahead < -4) v.pad = true;
            else if (ahead < 340) {
                boolean danger = w.countZombiesNear(city.stationX, v.y, 150) > 0;
                if (danger) {
                    v.pad = true;
                } else {
                    cruise = Math.max(5, ahead * 0.38f);
                    if (ahead < 3) {
                        v.state = WAIT;
                        v.pad = true;
                        v.speed = 0;
                        v.passengers = 0;
                        v.timer = 9;
                        return false;
                    }
                }
            }
        }
        v.speed += (cruise - v.speed) * Math.min(1, dt * (cruise < v.speed ? 1.2f : 0.35f));
        v.x += dir * v.speed * dt;
        // Anything on the line gets hit: zombies go flying, people are knocked aside, cars are wrecked.
        // (Not on the bridge: the highway runs underneath.)
        for (float k = 0; k < TRAIN_LENGTH; k += 16)
            if (!city.underRailBridge((int) ((v.x - dir * k) / City.T))) w.runOver(v.x - dir * k, v.y, 9, v.speed, v.angle);
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle o = vehicles.get(i);
            if (o == v || airborne(o) || o.type == TRAIN || o.broken || city.underRailBridge((int) (o.x / City.T))) continue;
            float along = (o.x - v.x) * dir;
            if (along < 8 && along > -TRAIN_LENGTH && Math.abs(o.y - v.y) < 14) damage(o, 500, true);
        }
        if (v.soundCd <= 0) {
            v.soundCd = 4;
            w.emit(Sfx.HORN, v.x, v.y);
        }
        return v.x < -TRAIN_LENGTH - 40 || v.x > city.worldW() + TRAIN_LENGTH + 40;
    }

    void update(float dt) {
        staffPatrols(dt);
        // Keep the streets busy while the city is still calm; clear old wrecks.
        trafficTimer -= dt;
        trainTimer -= dt;
        if (trainTimer <= 0 && city.railY0 >= 0) {
            trainTimer = 70 + w.rnd.nextFloat() * 50;
            spawnTrain();
        }
        if (trafficTimer <= 0) {
            trafficTimer = 6;
            if (movingTraffic() < trafficTarget * trafficScale && w.zombieCount() < 10 && w.alert < 2) spawnTraffic(1);
            int parked = 0;
            // Old wrecks and parked cars are towed away, but never while you're watching.
            for (int i = vehicles.size() - 1; i >= 0; i--) {
                Vehicle o = vehicles.get(i);
                // (A wreck left in the middle of a junction goes first.)
                boolean blocking = o.parked && city.junctionIdAt(o.x, o.y) >= 0;
                if (o.parked && o.block == null && o.riders.isEmpty() && o.player == null && (++parked > 25 || blocking) && !inView(o.x, o.y))
                    vehicles.remove(i);
            }
        }
        buildGrid();
        routeBudget = 6;
        // Who's out with lights and sirens: everyone else gives way to them.
        sirens.clear();
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle o = vehicles.get(i);
            if (o.player == null && !airborne(o) && o.lightsOn() && Math.abs(o.speed) > 18) sirens.add(o);
        }
        for (int i = vehicles.size() - 1; i >= 0; i--) {
            Vehicle v = vehicles.get(i);
            if (v.alarm > 0) {
                // Whoop, whoop: every zombie for streets around comes to see.
                v.alarm -= dt;
                if (v.broken || v.burnt) v.alarm = 0;
                else if (((int) (v.alarm * 2)) != ((int) ((v.alarm + dt) * 2))) {
                    w.emit(Sfx.HORN, v.x, v.y);
                    if (((int) v.alarm) % 4 == 0) w.noise(v.x, v.y, 180);
                }
            }
            v.anim += dt;
            // (Time spent crawling: nudging back and forth in a jam still counts.)
            if (Math.abs(v.speed) < 8) v.stillT += dt;
            else if (Math.abs(v.speed) > 15) v.stillT = 0;
            v.soundCd -= dt;
            v.destCd -= dt;
            v.rerouteCd -= dt;
            v.crashCd -= dt;
            boolean done;
            if (v.player != null) done = updatePlayerVehicle(v, dt);
            else if (v.type == HELI) done = updateHeli(v, dt);
            else if (v.type == JET) done = updateJet(v, dt);
            else if (v.type == TRAIN) done = updateTrain(v, dt);
            else if (v.broken || v.parked) done = updateWreck(v, dt);
            else if (v.type == TANK) done = updateTank(v, dt);
            else if (v.type == AMBULANCE) done = updateAmbulance(v, dt);
            else if (v.type == CAR) done = updateTraffic(v, dt);
            else if (v.type == FIRE_ENGINE) done = updateFireEngine(v, dt);
            else if (v.patrol) done = updatePatrol(v, dt);
            else done = updateCar(v, dt);
            if (done) {
                if (v.fire != null) v.fire.engine = null;
                v.removedFromFleet = true;
                vehicles.remove(i);
                continue;
            }
            // At a scene: in to the kerb, out of the traffic's way.
            if (((v.type == CRUISER && v.state == SCENE) || (v.type == AMBULANCE && v.state == LOAD)) && Math.abs(v.speed) < 1 && !v.broken)
                kerbAtScene(v, dt);
            else if (Math.abs(v.speed) > 10) v.kerbed = false;
            if (!airborne(v) && v.type != TRAIN && v.hp < v.maxHp * 0.5f) smoke(v, dt);
        }
        routeBudget = Integer.MAX_VALUE;
        buildGrid();
        collide();
        city.updateLights(dt);
    }

    private final ArrayList<Vehicle> sirens = new ArrayList<Vehicle>();

    /**
     * Everyday traffic gives way to lights and sirens: one coming up behind and the car pulls over to the
     * kerb and slows to let it by; one crossing ahead (or racing up to the junction it's at) and it holds back.
     */
    private void giveWay(Vehicle v, float dt) {
        if (v.yieldT > 0) v.yieldT -= dt;
        if (sirens.isEmpty() || v.yieldT > 0.3f) return;
        float fx = (float) Math.cos(v.angle), fy = (float) Math.sin(v.angle);
        for (int i = 0; i < sirens.size(); i++) {
            Vehicle o = sirens.get(i);
            if (o == v) continue;
            float dx = o.x - v.x, dy = o.y - v.y, d2 = dx * dx + dy * dy;
            if (d2 > 140 * 140) continue;
            float ahead = dx * fx + dy * fy;
            float same = fx * (float) Math.cos(o.angle) + fy * (float) Math.sin(o.angle);
            if (ahead < -8 && same > 0.6f) {
                // Coming up behind in the same direction: over to the kerb, nearly stopped.
                v.yieldT = 2.5f;
                v.yieldStop = false;
                return;
            }
            float ox = (float) Math.cos(o.angle), oy = (float) Math.sin(o.angle);
            boolean closing = -dx * ox - dy * oy > 0;
            if (d2 < 70 * 70 && closing && Math.abs(same) < 0.6f) {
                // Coming across the junction ahead: hold back until it's through.
                v.yieldT = 1.2f;
                v.yieldStop = true;
                return;
            }
        }
    }

    /** Arrived at a scene: pull in to the kerb, nose angled in, rather than stopping in the middle of the lane. */
    private void kerbAtScene(Vehicle v, float dt) {
        if (!v.kerbed) {
            v.kerbed = true;
            float nx = (float) -Math.sin(v.angle), ny = (float) Math.cos(v.angle);
            int hand = city.country.leftHand ? -1 : 1;
            for (float s = 4; s <= 22; s += 3) {
                float x = v.x + nx * s * hand, y = v.y + ny * s * hand;
                int tx = (int) (x / City.T), ty = (int) (y / City.T);
                if (tx < 0 || ty < 0 || tx >= city.w || ty >= city.h) break;
                byte t = city.tiles[ty * city.w + tx];
                if (t == City.ROAD || t == City.CAR) continue;
                v.pullX = x - nx * hand * (v.type == FIRE_ENGINE ? 8f : 6.5f);
                v.pullY = y - ny * hand * (v.type == FIRE_ENGINE ? 8f : 6.5f);
                v.pulling = true;
                break;
            }
        }
        if (v.pulling) {
            float dx = v.pullX - v.x, dy = v.pullY - v.y, d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 0.5f || !city.drivable(v.pullX, v.pullY)) v.pulling = false;
            else {
                float step = Math.min(d, 18 * dt);
                v.x += dx / d * step;
                v.y += dy / d * step;
                // Nose in towards the kerb a little.
                v.angle += (city.country.leftHand ? -1 : 1) * dt * 0.5f * Math.min(1, d / 4);
            }
        }
    }

    // ------------------------------------------------------------------ who's near whom

    /** Vehicles on the ground bucketed by 128-unit square, rebuilt each step: who's near whom without asking everyone. */
    private static final int GC = 128;
    private Vehicle[] gridV = new Vehicle[64];
    private int[] gridStart = new int[1], gridFill = new int[1];
    private int gridW, gridH, gridN;

    private void buildGrid() {
        gridW = (int) (city.worldW() / GC) + 1;
        gridH = (int) (city.worldH() / GC) + 1;
        int cells = gridW * gridH;
        if (gridStart.length != cells + 1) {
            gridStart = new int[cells + 1];
            gridFill = new int[cells];
        }
        java.util.Arrays.fill(gridStart, 0);
        int n = vehicles.size();
        if (gridV.length < n) gridV = new Vehicle[n * 2];
        for (int i = 0; i < n; i++) {
            Vehicle v = vehicles.get(i);
            if (airborne(v) || v.type == TRAIN) continue;
            gridStart[cellOf(v.x, v.y) + 1]++;
        }
        for (int c = 0; c < cells; c++) gridStart[c + 1] += gridStart[c];
        System.arraycopy(gridStart, 0, gridFill, 0, cells);
        gridN = 0;
        for (int i = 0; i < n; i++) {
            Vehicle v = vehicles.get(i);
            if (airborne(v) || v.type == TRAIN) continue;
            int c = cellOf(v.x, v.y);
            v.gi = gridFill[c];
            gridV[gridFill[c]++] = v;
            gridN++;
        }
    }

    private int cellOf(float x, float y) {
        int cx = Math.max(0, Math.min(gridW - 1, (int) (x / GC))), cy = Math.max(0, Math.min(gridH - 1, (int) (y / GC)));
        return cy * gridW + cx;
    }

    // ------------------------------------------------------------------ damage

    /** Hurts a vehicle; at zero it breaks down and everyone inside gets out. */
    void damage(Vehicle v, float amount, boolean blast) {
        if (airborne(v) || v.type == TRAIN || amount <= 0) return;
        // (Armour: the dead can claw at an APC or the riot van all day.)
        if (!blast && (v.kind == K_APC || v.kind == K_RIOT_VAN)) amount *= 0.3f;
        v.hp -= amount;
        if (v.broken) {
            if (!v.burnt && (blast || v.hp < -v.maxHp * 0.5f)) burn(v);
            return;
        }
        if (v.hp > 0) return;
        v.broken = true;
        v.parked = true;
        w.carsWrecked++;
        // The crew bails out.
        if (!v.crew.isEmpty()) crewOut(v, true);
        v.speed = 0;
        v.spraying = false;
        v.field = null;
        if (v.fire != null) v.fire.engine = null;
        v.fire = null;
        w.emit(Sfx.THUD, v.x, v.y);
        for (int k = 0; k < 10; k++)
            w.particle(v.x, v.y, w.rnd.nextFloat() * 40 - 20, w.rnd.nextFloat() * 40 - 20, 0.3f, 0.8f, 0xFFFFD27A, World.P_DOT);
        // Anyone still inside gets out and carries on on foot.
        if ((v.type == CRUISER || v.type == TRUCK) && v.state <= DRIVE && v.passengers > 0) {
            unload(v, false);
            v.passengers = 0;
        }
        if (v.type == CAR && v.state == CRUISE) {
            v.state = ABANDONED;
            getOut(v, true);
            pullOver(v);
        }
        dropRiders(v, true);
        if (v.supply > 0) {
            w.dispatch.say(Dispatch.WHO_MILITARY, null, "Military: We've lost the supply truck on " + city.placeName(v.x, v.y)
                    + ". The ammo's gone.", v.x, v.y);
            if (v.zone != null) v.zone.supplyComing = false;
            v.supply = 0;
        }
        if (v.type == FIRE_ENGINE)
            w.dispatch.say(Dispatch.WHO_FIRE, null, "Fire Dept: Engine " + v.number + " is out of action on "
                    + city.placeName(v.x, v.y) + ".", v.x, v.y);
        if (v.type == TANK) {
            // The crew bails out.
            w.dispatch.say(Dispatch.WHO_MILITARY, null, "Military: Our tank is knocked out on " + city.placeName(v.x, v.y)
                    + "! Crew bailing out.", v.x, v.y);
            for (int k = 0; k < 2; k++) {
                Entity crew = w.spawn(Entity.SOLDIER, v.x + (float) Math.cos(v.angle + 1.57f) * (10 + k * 4),
                        v.y + (float) Math.sin(v.angle + 1.57f) * (10 + k * 4));
                if (crew != null) crew.hp *= 0.6f;
            }
        }
        if (blast || v.hp < -v.maxHp * 0.3f || w.rnd.nextFloat() < 0.3f) burn(v);
    }

    private void burn(Vehicle v) {
        v.burnt = true;
        w.ignite(v.x, v.y, 30 + w.rnd.nextFloat() * 25);
        if (w.rnd.nextFloat() < 0.3f) w.blastLater(v.x, v.y, 45, 150, 2 + w.rnd.nextFloat() * 4);
    }

    private void smoke(Vehicle v, float dt) {
        float f = 1 - Math.max(0, v.hp) / (v.maxHp * 0.5f);
        if (w.rnd.nextFloat() < dt * (2 + f * 6))
            w.particle(v.x + (float) Math.cos(v.angle) * v.length() * 0.6f, v.y + (float) Math.sin(v.angle) * v.length() * 0.6f,
                    4 + w.rnd.nextFloat() * 4, -8 - w.rnd.nextFloat() * 6, 1.5f + w.rnd.nextFloat(), 2 + w.rnd.nextFloat() * 2,
                    f > 0.8f ? 0xFF2A2A2A : 0xFF6A6A6A, World.P_SMOKE);
    }

    /** Vehicles bump into each other: hard hits damage both. */
    private void collide() {
        for (int i = 0; i < gridN; i++) {
            Vehicle a = gridV[i];
            int acx = Math.max(0, Math.min(gridW - 1, (int) (a.x / GC))), acy = Math.max(0, Math.min(gridH - 1, (int) (a.y / GC)));
            for (int gy = Math.max(0, acy - 1); gy <= Math.min(gridH - 1, acy + 1); gy++)
            for (int gx = Math.max(0, acx - 1); gx <= Math.min(gridW - 1, acx + 1); gx++)
            for (int j = gridStart[gy * gridW + gx], je = gridStart[gy * gridW + gx + 1]; j < je; j++) {
                if (j <= i) continue;
                Vehicle b = gridV[j];
                float dx = b.x - a.x, dy = b.y - a.y, reach = (a.length() + b.length()) * 0.62f;
                float d2 = dx * dx + dy * dy;
                if (d2 >= reach * reach) continue;
                float d = (float) Math.sqrt(d2) + 0.001f, nx = dx / d, ny = dy / d;
                // Closing speed along the line between them.
                float va = a.speed * ((float) Math.cos(a.angle) * nx + (float) Math.sin(a.angle) * ny);
                float vb = b.speed * ((float) Math.cos(b.angle) * nx + (float) Math.sin(b.angle) * ny);
                float closing = va - vb;
                float push = (reach - d) * 0.5f;
                // Nobody is shoved off the road (onto grass, a kerb, a median): the other car takes the push.
                boolean aOk = city.drivable(a.x - nx * push, a.y - ny * push), bOk = city.drivable(b.x + nx * push, b.y + ny * push);
                float pa = aOk ? (bOk ? push : push * 2) : 0, pb = bOk ? (aOk ? push : push * 2) : 0;
                if (pa > 0 && !city.drivable(a.x - nx * pa, a.y - ny * pa)) pa = push;
                if (pb > 0 && !city.drivable(b.x + nx * pb, b.y + ny * pb)) pb = push;
                a.x -= nx * pa;
                a.y -= ny * pa;
                b.x += nx * pb;
                b.y += ny * pb;
                // (A nudge in a queue is just a nudge: it takes a real impact to make a crash.)
                // (A crash is the same real closing speed as before 10.20, now that everything drives faster.)
                if (closing > 45 * DRIVE_SCALE && a.crashCd <= 0 && b.crashCd <= 0) {
                    a.crashCd = b.crashCd = 0.6f;
                    float dmg = closing * 0.35f / DRIVE_SCALE;
                    damage(a, dmg, false);
                    damage(b, dmg, false);
                    w.skid(a.x, a.y, a.angle, Math.min(14, a.speed * 0.2f + 4));
                    w.skid(b.x, b.y, b.angle, Math.min(14, b.speed * 0.2f + 4));
                    a.speed *= 0.3f;
                    b.speed *= 0.3f;
                    float cx = (a.x + b.x) / 2, cy = (a.y + b.y) / 2;
                    w.emit(Sfx.CRASH, cx, cy);
                    for (int k = 0; k < 8; k++)
                        w.particle(cx, cy, w.rnd.nextFloat() * 60 - 30, w.rnd.nextFloat() * 60 - 30, 0.25f, 0.7f,
                                0xFFFFD27A, World.P_DOT);
                    w.noise(cx, cy, 160);
                }
            }
        }
    }

    /**
     * The rules at junctions. Traffic lights: stop on red (and on amber if there's room to), go on green.
     * Stop junctions: come to a full stop at the line, then go when the junction is clear (or after a few
     * seconds, so nobody waits for ever). Roundabouts: give way to anything already on it. Returns the
     * fastest the car may go right now.
     */
    private float junctionRule(Vehicle v, float dt) {
        v.held = false;
        // Lights and sirens (and anyone racing to a rescue) go through on red, but slow down for the junction
        // and wait for anything already crossing it.
        boolean emergency = (v.type != CAR && !(v.patrol && v.state == PATROL)) || v.riders.size() > 0 && v.rescue;
        if (emergency && v.type != CRUISER && v.type != AMBULANCE && v.type != FIRE_ENGINE && v.type != TRUCK && v.type != CAR)
            return Float.MAX_VALUE;
        int here = city.junctionIdAt(v.x, v.y);
        if (here >= 0) {
            // Through: next time round it has to wait its turn again.
            if (here == v.clearedJ) v.clearedJ = -1;
            v.waitJ = -1;
            return Float.MAX_VALUE;
        }
        // Follow the route from the car's own tile to the first junction tile: that step is the way in,
        // and the line across the end of the tile before it is where the junction starts.
        int id = -1, W = city.w, dirx = 0, diry = 0;
        float dist = 0;
        if (v.field != null && v.pathT >= 0) {
            int prev = city.tileIndex(v.x, v.y), t = v.pathT;
            for (int k = 0; k < 7; k++) {
                int nid = t == prev ? -1 : city.junctionIdAt((t % W + 0.5f) * City.T, (t / W + 0.5f) * City.T);
                if (nid >= 0) {
                    int sx = t % W - prev % W, sy = t / W - prev / W;
                    if (Math.abs(sx) + Math.abs(sy) == 1) {
                        dirx = sx;
                        diry = sy;
                    } else {
                        dirx = v.pdx;
                        diry = v.pdy;
                    }
                    if (dirx == 0 && diry == 0) break;
                    float ex = (t % W + 0.5f - dirx * 0.5f) * City.T, ey = (t / W + 0.5f - diry * 0.5f) * City.T;
                    dist = (ex - v.x) * dirx + (ey - v.y) * diry;
                    if (dist < 90) id = nid;
                    break;
                }
                int next = downhill(v.field, t);
                if (next == t) break;
                prev = t;
                t = next;
            }
        }
        if (id < 0) {
            v.clearedJ = -1;
            v.waitJ = -1;
            return Float.MAX_VALUE;
        }
        if (id == v.clearedJ) return Float.MAX_VALUE;
        int[] j = city.junctions.get(id);
        // Stop with the front bumper at the line, before the zebra crossing.
        float stopAt = dist - City.T - v.length() - 1;
        float approach = Math.max(0, stopAt) * 1.8f;
        boolean vertical = diry != 0;
        // A bend: slow for the corner, but nobody stops.
        if (j[4] == City.J_BEND) return Float.MAX_VALUE;
        if (emergency) {
            if (!crossClear(id, v, dirx, diry)) {
                v.held = stopAt < 6;
                return approach;
            }
            return Math.max(35, stopAt * 1.8f);
        }
        if (j[4] == City.J_LIGHTS && !v.fleeing) {
            city.lightDemand(id, vertical);
            int light = city.lightState(id, vertical, w.time);
            if (light == 0) {
                // Green, but something is still crossing in front: let it clear first.
                if (stopAt > 2 && !crossClear(id, v, dirx, diry)) {
                    v.held = stopAt < 6;
                    return approach;
                }
                return Float.MAX_VALUE;
            }
            // Amber: carry on only if it's too late to stop.
            if (light == 1 && v.speed > 40 && stopAt < v.speed * 0.2f) {
                v.clearedJ = id;
                return Float.MAX_VALUE;
            }
            v.held = stopAt < 6;
            return approach;
        }
        if (v.waitJ != id) {
            if (j[4] == City.J_ROUNDABOUT) {
                if (stopAt < 10 && junctionClear(id, v)) {
                    v.clearedJ = id;
                    return Float.MAX_VALUE;
                }
                v.held = stopAt < 6;
                return Math.max(approach, stopAt < 10 ? 0 : 25);
            }
            if (stopAt < 4 && v.speed < 6) {
                v.waitJ = id;
                v.waitT = 0;
            }
            v.held = stopAt < 6;
            return Math.max(approach, 4);
        }
        v.waitT += dt;
        v.held = true;
        if (v.waitT > 1.2f && (junctionClear(id, v) || v.waitT > 7)) {
            v.clearedJ = id;
            v.waitJ = -1;
            return Float.MAX_VALUE;
        }
        return 0;
    }

    /** The next tile down a route from tile t (t itself at the end). */
    private int downhill(Route field, int t) {
        int W = city.w, tx = t % W, ty = t / W, here = field.get(t), best = Integer.MAX_VALUE, bt = t;
        boolean inJ = city.junctionAt(tx, ty);
        for (int k = 0; k < 4; k++) {
            int nx = tx + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = ty + (k == 2 ? 1 : k == 3 ? -1 : 0);
            if (nx < 0 || ny < 0 || nx >= W || ny >= city.h) continue;
            int d = field.get(ny * W + nx);
            if (d >= here) continue;
            // Downhill, but on a tie keep to the lane: a step across the road outside a junction only if
            // it's really the shorter way.
            int rd = city.roadDirAt(nx, ny);
            if (!inJ && !city.junctionAt(nx, ny) && ((rd == 1 && k < 2) || (rd == 2 && k >= 2))) d += 14;
            if (d < best) {
                best = d;
                bt = ny * W + nx;
            }
        }
        return bt;
    }

    private int downhillRaw(int[] field, int t) {
        int W = city.w, tx = t % W, ty = t / W, here = field[t], best = Integer.MAX_VALUE, bt = t;
        boolean inJ = city.junctionAt(tx, ty);
        for (int k = 0; k < 4; k++) {
            int nx = tx + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = ty + (k == 2 ? 1 : k == 3 ? -1 : 0);
            if (nx < 0 || ny < 0 || nx >= W || ny >= city.h) continue;
            int d = field[ny * W + nx];
            if (d >= here) continue;
            // Downhill, but on a tie keep to the lane: a step across the road outside a junction only if
            // it's really the shorter way.
            int rd = city.roadDirAt(nx, ny);
            if (!inJ && !city.junctionAt(nx, ny) && ((rd == 1 && k < 2) || (rd == 2 && k >= 2))) d += 14;
            if (d < best) {
                best = d;
                bt = ny * W + nx;
            }
        }
        return bt;
    }

    /** Nobody else driving in the junction. */
    /** Nothing moving across the junction in front of a vehicle coming in along (dx, dy). */
    private boolean crossClear(int id, Vehicle self, int dx, int dy) {
        for (int i = 0, n = vehicles.size(); i < n; i++) {
            Vehicle o = vehicles.get(i);
            if (o == self || airborne(o) || o.type == TRAIN || o.parked || Math.abs(o.speed) < 5) continue;
            if (city.junctionIdAt(o.x, o.y) != id) continue;
            if (Math.abs((float) Math.cos(o.angle) * dx + (float) Math.sin(o.angle) * dy) < 0.6f) return false;
        }
        return true;
    }

    private boolean junctionClear(int id, Vehicle self) {
        for (int i = 0, n = vehicles.size(); i < n; i++) {
            Vehicle o = vehicles.get(i);
            if (o == self || airborne(o) || o.type == TRAIN || o.parked) continue;
            if (city.junctionIdAt(o.x, o.y) == id) return false;
        }
        return true;
    }

    /** Someone else's car close in front: time to brake. */
    /**
     * The fastest a vehicle may go and still stop behind whatever is ahead of it in its lane: room to stop at
     * its own speed (a two-second gap, less in town), and never faster than the one in front once close.
     */
    /** How much quicker than the old open-road speeds vehicles drive (10.20). */
    static final float DRIVE_SCALE = 1.8f;

    private float followLimit(Vehicle v) {
        float fx = (float) Math.cos(v.angle), fy = (float) Math.sin(v.angle);
        float look = 30 + Math.abs(v.speed) * 1.6f, limit = Float.MAX_VALUE;
        v.queued = false;
        int depth = 0;
        int c0x = Math.max(0, (int) ((v.x - look) / GC)), c1x = Math.min(gridW - 1, (int) ((v.x + look) / GC));
        int c0y = Math.max(0, (int) ((v.y - look) / GC)), c1y = Math.min(gridH - 1, (int) ((v.y + look) / GC));
        for (int cy = c0y; cy <= c1y; cy++)
        for (int cx = c0x; cx <= c1x; cx++)
        for (int i = gridStart[cy * gridW + cx], ie = gridStart[cy * gridW + cx + 1]; i < ie; i++) {
            Vehicle o = gridV[i];
            if (o == v || o.removedFromFleet) continue;
            float dx = o.x - v.x, dy = o.y - v.y;
            float ahead = dx * fx + dy * fy;
            if (ahead <= 0 || ahead > look) continue;
            float side = Math.abs(dx * -fy + dy * fx);
            if (side > 9) continue;
            // How fast it's going our way (oncoming or crossing counts as standing still).
            float lead = Math.max(0, o.speed * ((float) Math.cos(o.angle) * fx + (float) Math.sin(o.angle) * fy));
            float gap = ahead - (v.length() + o.length()) * 0.5f - 5;
            // Something stopped for good in the lane (parked, wrecked, or stood there a while and not just
            // queueing): pull out and go round it, slowly, rather than wait behind it for ever.
            boolean stuckThere = o.parked || o.broken || (o.stillT > 5 && !o.held && !o.queued)
                    || (o.yieldT > 0 && !o.yieldStop && v.lightsOn())
                    // (Lights and sirens go round a queue sitting at a red light, too.)
                    || (v.lightsOn() && o.type == CAR && o.player == null && o.stillT > 1f && v.type != FIRE_ENGINE)
                    // (Gridlock, everyone waiting on everyone and nobody on a light: edge round and break it.)
                    || (o.stillT > 6 && v.stillT > 6 && !o.held && !o.queuedHeld);
            if (stuckThere && v.player == null && v.type != TANK) {
                if (gap < 40) v.passing = 1.4f;
                limit = Math.min(limit, 30 + Math.max(0, gap));
                continue;
            }
            if (gap < 25 && lead < 5) {
                v.queued = true;
                int d = o.held ? 1 : o.holdDepth > 0 ? o.holdDepth + 1 : 0;
                if (d > 0 && (depth == 0 || d < depth)) depth = d;
            }
            float ok = gap <= 0 ? 0 : Math.min(lead + gap * 1.2f, (float) Math.sqrt(2 * 260 * gap));
            if (gap < 10) ok = Math.min(ok, lead * 0.8f);
            limit = Math.min(limit, ok);
        }
        v.holdDepth = depth;
        v.queuedHeld = depth > 0 && depth <= 15;
        return limit;
    }

    private boolean carAhead(Vehicle v) {
        float fx = (float) Math.cos(v.angle), fy = (float) Math.sin(v.angle);
        int c0x = Math.max(0, (int) ((v.x - 24) / GC)), c1x = Math.min(gridW - 1, (int) ((v.x + 24) / GC));
        int c0y = Math.max(0, (int) ((v.y - 24) / GC)), c1y = Math.min(gridH - 1, (int) ((v.y + 24) / GC));
        for (int cy = c0y; cy <= c1y; cy++)
        for (int cx = c0x; cx <= c1x; cx++)
        for (int i = gridStart[cy * gridW + cx], ie = gridStart[cy * gridW + cx + 1]; i < ie; i++) {
            Vehicle o = gridV[i];
            if (o == v || o.removedFromFleet) continue;
            float dx = o.x - v.x, dy = o.y - v.y;
            float ahead = dx * fx + dy * fy, side = Math.abs(dx * -fy + dy * fx);
            if (ahead > 0 && ahead < 24 && side < 8) return true;
        }
        return false;
    }

    private boolean updateWreck(Vehicle v, float dt) {
        v.speed = 0;
        if (v.block != null && !v.broken && v.block.removed) {
            // The zone has closed: the officer gets back in, the cones come in and the car drives off.
            v.block = null;
            v.cones = false;
            if (v.guard != null && !v.guard.dead && Math.hypot(v.guard.x - v.x, v.guard.y - v.y) < 80) {
                v.guard.dead = true;
                v.guard.removed = true;
            }
            v.guard = null;
            v.parked = false;
            v.state = RETURN;
            float[] home = randomRoad();
            return home == null || !route(v, home[0], home[1]);
        }
        if (v.pulling) {
            // Rolling to a stop against the kerb.
            float dx = v.pullX - v.x, dy = v.pullY - v.y, d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 0.5f) v.pulling = false;
            else {
                float step = Math.min(d, 20 * dt);
                v.x += dx / d * step;
                v.y += dy / d * step;
            }
        }
        return false;
    }

    /** Where a stopped car ends up: the nearest kerb to its right, if it is close. */
    private void pullOver(Vehicle v) {
        if (v.type != CAR) return;
        float nx = (float) -Math.sin(v.angle), ny = (float) Math.cos(v.angle);
        for (int side = 1; side >= -1; side -= 2)
            for (float s = 4; s <= 26; s += 3) {
                float x = v.x + nx * s * side, y = v.y + ny * s * side;
                int tx = (int) (x / City.T), ty = (int) (y / City.T);
                if (tx < 0 || ty < 0 || tx >= city.w || ty >= city.h) break;
                byte t = city.tiles[ty * city.w + tx];
                if (t == City.ROAD || t == City.CAR) continue;
                v.pullX = x - nx * side * 6.5f;
                v.pullY = y - ny * side * 6.5f;
                v.pulling = true;
                return;
            }
    }

    // ------------------------------------------------------------------ driving

    private boolean updateCar(Vehicle v, float dt) {
        if (v.type == TRUCK) turret(v, dt);
        if (v.state == SCENE && v.type == TRUCK) {
            // An APC that dropped its squad stays a while to cover them with its gun, then heads home.
            v.speed = Math.max(0, v.speed - dt * 200);
            v.timer -= dt;
            v.quiet = w.countZombiesNear(v.x, v.y, 200) > 0 ? 0 : v.quiet + dt;
            if (v.timer <= 0 || v.quiet > 20) {
                v.state = RETURN;
                v.speed = 0;
                return !route(v, v.homeX, v.homeY);
            }
            return false;
        }
        if (v.state == MUSTER) {
            // A troop truck at the gate, waiting for its squad to climb aboard.
            v.speed = 0;
            v.timer += dt;
            boolean ready = v.crew.size() >= v.crewWanted || (v.timer > 20 && !v.crew.isEmpty());
            if (!ready) {
                if (v.timer > 35) {
                    dropCrew(v);
                    return true;
                }
                return false;
            }
            dropCrew(v);
            Dispatch.Incident inc = v.incident;
            if (v.loop) {
                // A patrol: off round the roads near the base.
                if (!route(v, v.tx, v.ty)) {
                    for (Entity e : new ArrayList<Entity>(v.crew)) e.rig = null;
                    crewOut(v, true);
                    return true;
                }
                v.state = DRIVE;
                v.stuckTimer = 0;
                w.dispatch.say(Dispatch.WHO_MILITARY, v.crew.get(0), "Patrol mounted up. Sweeping " + v.place + ".", v.x, v.y);
                return false;
            }
            if (inc == null || inc.resolved || !route(v, inc.x, inc.y)) {
                for (Entity e : new ArrayList<Entity>(v.crew)) e.rig = null;
                crewOut(v, true);
                return true;
            }
            v.state = DRIVE;
            v.stuckTimer = 0;
            w.dispatch.say(Dispatch.WHO_MILITARY, v.crew.get(0), "Mounted up, " + v.crew.size() + " aboard. Rolling out to "
                    + inc.place + ".", v.x, v.y);
            return false;
        }
        if (v.state == WAIT) {
            v.timer -= dt;
            if (v.timer <= 0) {
                v.state = DRIVE;
                if (v.type == CRUISER) w.emit(Sfx.SIREN, v.x, v.y);
            }
            return false;
        }
        // On the way home empty: hold back rather than drive through a crowd of the dead, and if they don't
        // move off, leave the car parked where it is.
        if (v.state == RETURN && (v.type == CRUISER || v.type == TRUCK) && hordeAhead(v)) {
            v.speed = Math.max(0, v.speed - dt * 220);
            v.hold += dt;
            v.stuckTimer = 0;
            if (v.hold > 25) {
                v.speed = 0;
                v.parked = true;
                v.state = ABANDONED;
                // (Anyone still aboard gets out.)
                if (!v.crew.isEmpty()) crewOut(v, true);
            }
            return false;
        }
        v.hold = 0;
        v.stuckTimer += dt;
        if (trainComing(v)) {
            v.speed = Math.max(0, v.speed - dt * 150);
            return false;
        }
        float top = v.type == CRUISER ? 105 : v.kind == K_APC ? 68 : v.kind == K_HUMVEE ? 100 : v.kind == K_RIOT_VAN ? 85 : 80;
        boolean arrived = !driveStep(v, dt, top, carAhead(v) ? 0.5f : 1f);
        // A patrol that comes on a crowd of the dead stops and lets the gunner work; the crew stay aboard.
        if (v.loop && v.state == DRIVE && hordeAhead(v)) {
            v.speed = Math.max(0, v.speed - dt * 200);
            v.stuckTimer = 0;
            v.idleTimer += dt;
            if (v.idleTimer < 12) return false;
            arrived = true;
        }
        // Don't drive into a horde: stop short and let the troops out (an APC drives on in: it's armoured).
        if (v.state == DRIVE && !v.loop && v.kind != K_APC && (v.passengers > 0 || !v.crew.isEmpty()) && hordeAhead(v)) {
            arrived = true;
            v.speed *= 0.3f;
        }
        // Give up and stop where we are if the car hasn't made progress for a while.
        // (Reinforcements on their long drive in wait a little longer in a jam before getting out to walk.)
        if (v.stuckTimer > (v.reinforcement ? 12 : 4)) arrived = true;
        if (arrived) {
            if (v.state == DRIVE && v.block != null) {
                // A roadblock car: only set up if the zone still needs it and it got close to its spot.
                if (v.block.removed || Math.hypot(v.x - v.tx, v.y - v.ty) > 90) {
                    v.block = null;
                } else {
                    setUpRoadblock(v);
                    return false;
                }
            }
            if (v.state == DRIVE && v.supply > 0 && v.zone != null) {
                // The ammo gets through (if the truck reached the zone).
                Dispatch.SafeZone z = v.zone;
                z.supplyComing = false;
                if (!z.removed && Math.hypot(v.x - z.x, v.y - z.y) < z.r + 90) {
                    z.ammo += v.supply;
                    z.food = Math.min(z.foodMax, z.food + z.foodMax * 0.7f);
                    w.dispatch.say(Dispatch.WHO_MILITARY, null, "Military: Supply truck at " + z.place + ". " + v.supply
                            + " rounds and food delivered.", v.x, v.y);
                } else {
                    w.dispatch.say(Dispatch.WHO_MILITARY, null, "Military: The supply truck couldn't get through to "
                            + z.place + ". Turning back.", v.x, v.y);
                }
                v.supply = 0;
            }
            if (v.state == DRIVE && v.restock) {
                if (Math.hypot(v.x - v.tx, v.y - v.ty) < 120) w.restocked(v);
                v.state = RETURN;
                v.speed = 0;
                return !route(v, v.homeX, v.homeY);
            }
            if (v.state == DRIVE && v.loop) {
                // The end of the patrol's sweep: back to base.
                v.state = RETURN;
                v.idleTimer = 0;
                v.speed = 0;
                return !route(v, v.homeX, v.homeY);
            }
            if (v.state == RETURN && v.loop) {
                // Back at base: the patrol gets out.
                crewOut(v, true);
                city.parkTruck(v.homeX, v.homeY);
                return true;
            }
            if (v.state == DRIVE && !v.crew.isEmpty()) {
                // The squad jumps down and goes in; the truck heads back.
                ArrayList<Entity> out = new ArrayList<Entity>(v.crew);
                crewOut(v, true);
                Dispatch.Incident inc = v.incident;
                for (Entity e : out) {
                    e.rig = null;
                    if (inc != null && !inc.resolved) {
                        e.task = Dispatch.T_RESPOND;
                        e.incident = inc;
                        e.onScene = false;
                    }
                }
                if (!out.isEmpty() && v.place != null)
                    w.dispatch.say(Dispatch.WHO_MILITARY, out.get(0), "On the ground at " + v.place + ". Moving in.", v.x, v.y);
            }
            if (v.state == DRIVE) {
                unload(v, true);
                v.passengers = 0;
                if (v.kind == K_APC && v.supply == 0) {
                    // The APC stays to give its squad covering fire.
                    v.state = SCENE;
                    v.timer = 90;
                    v.quiet = 0;
                    v.speed = 0;
                    return false;
                }
                v.state = RETURN;
                v.speed = 0;
                return !route(v, v.homeX, v.homeY);
            }
            // An army truck back at base parks in its bay again.
            if (v.state == RETURN && v.type == TRUCK && !v.swat && !v.restock && !v.broken && !w.peopleNear(v.homeX, v.homeY, 14)
                    && w.countZombiesNear(v.homeX, v.homeY, 14) == 0)
                city.parkTruck(v.homeX, v.homeY);
            return true;
        }
        hit(v, w.runOver(v.x, v.y, 8, v.speed, v.angle));
        if (v.type == CRUISER && v.soundCd <= 0 && v.state == DRIVE) {
            v.soundCd = 3.5f;
            w.emit(Sfx.SIREN, v.x, v.y);
        }
        return false;
    }

    /** Running zombies over dents the car. */
    private void hit(Vehicle v, float impact) {
        if (impact > 0) damage(v, impact, false);
    }

    /**
     * One step of driving downhill in the vehicle's field, keeping to the right-hand side of the road.
     * Returns false once it has arrived (or has no route).
     */
    private boolean driveStep(Vehicle v, float dt, float max, float throttle) {
        if (v.field == null) return false;
        // (10.20) Open-road speeds are nearly twice what they were, next to people on foot (who walk briskly in
        // this world): a car in town now covers ground like a car. Crawls, turns and queues are as before.
        if (max > 40) max = 40 + (max - 40) * DRIVE_SCALE;
        // Keep a safe distance behind whatever is ahead in the lane.
        max = Math.min(max, followLimit(v));
        int W = city.w;
        // Follow the route tile by tile (the "path tile"), and drive in the lane beside it.
        if (v.pathField != v.field || v.pathT < 0 || v.field.get(v.pathT) >= City.FAR
                || Math.hypot((v.pathT % W + 0.5f) * City.T - v.x, (v.pathT / W + 0.5f) * City.T - v.y) > 44) {
            v.pathField = v.field;
            v.pathT = -1;
            int ti = city.tileIndex(v.x, v.y), tx0 = ti % W, ty0 = ti / W, bestV = City.FAR;
            // On the route where it is: carry on from here. Knocked off the road: head back to the nearest bit.
            if (v.field.get(ti) < City.FAR) v.pathT = ti;
            for (int r = 0; r <= 2 && v.pathT < 0; r++)
                for (int y = ty0 - r; y <= ty0 + r; y++)
                    for (int x = tx0 - r; x <= tx0 + r; x++) {
                        if (x < 0 || y < 0 || x >= W || y >= city.h) continue;
                        int fv = v.field.get(y * W + x);
                        if (fv < bestV) {
                            bestV = fv;
                            v.pathT = y * W + x;
                        }
                    }
            if (v.pathT < 0) {
                // Off the route's corridor altogether (shoved aside, or round something): plan it again from here.
                if (v.rerouteCd > 0 || !route(v, v.tx, v.ty)) return false;
                v.rerouteCd = 3;
                v.pathField = null;
                return driveStep(v, dt, max, throttle);
            }
        }
        // Pushed over onto the far side of a barrier (or a kerb) from its route tile: carry on from where it is
        // instead of driving along the barrier towards a lane it can't reach (the wrong way, on the highway).
        {
            int own = city.tileIndex(v.x, v.y);
            if (own != v.pathT) {
                int mid = city.tileIndex(((v.pathT % W + 0.5f) * City.T + v.x) / 2, ((v.pathT / W + 0.5f) * City.T + v.y) / 2);
                if (v.field.get(mid) >= City.FAR) {
                    if (v.field.get(own) < City.FAR) v.pathT = own;
                    // (Only a real barrier between: a kerb corner cut in a junction is nothing to plan round.)
                    else if (v.rerouteCd <= 0 && (city.solidTile(mid % W, mid / W) || city.onHighway(v.x, v.y))
                            && route(v, v.tx, v.ty)) {
                        // (Off the route on the wrong side: plan the way again from where it is.)
                        v.rerouteCd = 3;
                        v.pathField = null;
                        return driveStep(v, dt, max, throttle);
                    }
                }
            }
        }
        int cur = v.field.get(v.pathT);
        if (cur <= 4) return false;
        int tx = v.pathT % W, ty = v.pathT / W, nt = downhill(v.field, v.pathT), bx = nt % W, by = nt / W;
        if (bx == tx && by == ty) return false;
        int ddx = bx - tx, ddy = by - ty;
        // Keep to your own side of the road (left in Australia and Japan), in the middle of the lane.
        float off = city.laneOffset(bx, by, ddx, ddy);
        if (v.careful > 0) v.careful -= dt;
        // (Going round something stopped in the lane: out towards the middle of the road for a moment.)
        if (v.passing > 0) {
            v.passing -= dt;
            // (In a junction only to get out of a jam: there a stopped car is usually just waiting to turn.)
            if (city.junctionIdAt(v.x, v.y) < 0 || v.stillT > 0 || v.speed < 20) off -= (off == 0 ? 1 : Math.signum(off)) * City.T * 1.1f;
        }
        // Into a junction in your own lane (measured on the road you're on): the turn is made inside it,
        // not by cutting across the lanes before the stop line.
        if (ddx * v.pdx + ddy * v.pdy > 0 && city.junctionIdAt((bx + 0.5f) * City.T, (by + 0.5f) * City.T) >= 0
                && city.junctionIdAt((tx + 0.5f) * City.T, (ty + 0.5f) * City.T) < 0)
            off = city.laneOffset(tx, ty, ddx, ddy);
        // A step across the road outside a junction (a U-turn mid-block, or pulling out from the far kerb):
        // straight over, slowly, not along a "lane" that runs the wrong way.
        int rd = city.junctionIdAt(v.x, v.y) < 0 && city.junctionIdAt((bx + 0.5f) * City.T, (by + 0.5f) * City.T) < 0
                ? city.roadDirAt(bx, by) : 0;
        if ((rd == 1 && ddx != 0) || (rd == 2 && ddy != 0)) {
            off = 0;
            max = Math.min(max, 22);
        }
        if (v.pulled) {
            // Pulled over by highway patrol: onto the shoulder, and stop.
            off += City.T * 0.5f * (city.country.leftHand ? -1 : 1);
            max = Math.min(max, Math.max(0, Math.abs(v.speed) - 3));
        } else if (v.yieldT > 0 && v.type == CAR && v.player == null) {
            // Giving way: over to the kerb and slow (or held back from the junction).
            if (v.yieldStop) max = Math.min(max, city.junctionIdAt(v.x, v.y) >= 0 ? max : 2);
            else {
                off += City.T * 0.45f * (city.country.leftHand ? -1 : 1);
                max = Math.min(max, 7);
            }
        }
        if (v.careful > 0) {
            // Going round in circles: slowly, straight for the middle of each tile until it's back on track.
            off = 0;
            max = Math.min(max, 16);
        }
        float gx = bx * City.T + City.T / 2f - ddy * off, gy = by * City.T + City.T / 2f + ddx * off;
        if (Math.hypot(gx - v.x, gy - v.y) < 11 || ((v.x - (bx + 0.5f) * City.T) * ddx + (v.y - (by + 0.5f) * City.T) * ddy) > -2) {
            v.pathT = by * W + bx;
            // Only a step along the road counts as the way it's going, not a turn inside a junction.
            if (city.junctionIdAt(v.x, v.y) < 0) {
                v.pdx = ddx;
                v.pdy = ddy;
            }
        }
        // Slow down for a turn coming up on the route: round a corner at a walking-pace crawl, not at cruising speed.
        // (Emergency vehicles take corners a little quicker, but still slow enough to keep to their own side:
        // the faster it's going, the further ahead it looks for the turn.)
        {
            float round = v.type == CAR ? 26 : 31;
            int t = v.pathT, steps = 0, look = Math.min(8, 4 + (int) (Math.abs(v.speed) / 30));
            for (; steps < look; steps++) {
                int n = downhill(v.field, t);
                if (n == t) break;
                int sx = n % W - t % W, sy = n / W - t / W;
                if (sx != ddx || sy != ddy) {
                    max = Math.min(max, round + steps * City.T * 0.75f);
                    break;
                }
                t = n;
            }
        }
        float want = (float) Math.atan2(gy - v.y, gx - v.x);
        float diff = want - v.angle;
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        // Wheels only turn the car while it's moving.
        // (Standing still, or as good as - held at a junction or in a jam - it doesn't turn on the spot.)
        float steer = Math.abs(v.speed) < 1.5f ? 0 : dt * 5 * Math.min(1, 0.05f + Math.abs(v.speed) / 18f);
        float turned = Math.max(-steer, Math.min(steer, diff));
        v.angle += turned;
        // A full circle and more without getting any nearer: it's missed its turn and is orbiting.
        v.spin += turned;
        if (Math.abs(v.spin) > 7.5f && v.player == null) {
            v.spin = 0;
            v.careful = 4;
            v.orbited = true;
            int own = city.tileIndex(v.x, v.y);
            if (v.field.get(own) < City.FAR) v.pathT = own;
        }
        float target = max * throttle * (0.35f + 0.65f * Math.max(0, (float) Math.cos(diff)));
        // A badly damaged car limps along.
        if (v.hp < v.maxHp * 0.3f) target *= 0.6f;
        // Brake harder than you accelerate.
        v.speed += (target - v.speed) * Math.min(1, dt * (target < v.speed ? 5f : 3.2f));
        v.x += (float) Math.cos(v.angle) * v.speed * dt;
        v.y += (float) Math.sin(v.angle) * v.speed * dt;
        if (cur < v.lastDist - 1) {
            v.lastDist = cur;
            v.stuckTimer = 0;
            v.spin = 0;
        }
        return true;
    }

    /**
     * Traffic: drives around, brakes for people and other cars, ploughs through zombies, stops for people
     * waving it down, and gets abandoned when the driver is surrounded.
     */
    private boolean updateTraffic(Vehicle v, float dt) {
        if (v.parked) return false;
        if (v.pulled) {
            // (Waiting for the officer: not stuck, not a jam.)
            v.stuckTimer = 0;
            if (w.alert > 0) v.pulled = false;
        }
        giveWay(v, dt);
        boolean zombiesClose = w.countZombiesNear(v.x, v.y, 30) >= 2;
        v.waitTimer -= dt;
        v.hailCd -= dt;
        // Someone is waving: pick them up if there's room.
        if (v.hailCd <= 0 && v.riders.size() < SEATS && !zombiesClose) {
            v.hailCd = 0.5f;
            if (w.hail(v)) v.waitTimer = 4;
        }
        v.timer += dt;
        // Everyone knows: drop the errand and get out of town.
        v.fleeCd -= dt;
        // (Not through traffic, already on its way out, nor anyone on the highway: no turning round on it.)
        if (w.alert >= 2 && !v.fleeing && !v.through && !v.rescue && v.player == null && v.fleeCd <= 0
                && !city.onHighway(v.x, v.y)) {
            v.fleeCd = 4 + w.rnd.nextFloat() * 3;
            if (v.model == M_BUS) {
                // Buses never leave town with people aboard: with a safe zone open the bus takes its passengers
                // there; otherwise the service stops, and the driver pulls in and lets everyone off.
                if (!v.riders.isEmpty()) boarded(v, v.riders.remove(v.riders.size() - 1));
                if (!v.rescue) {
                    dropRiders(v, false);
                    v.parked = true;
                    v.speed = 0;
                    v.field = null;
                    pullOver(v);
                    getOut(v, false);
                    return false;
                }
            } else {
                float[] exit = exitNear(v.x, v.y);
                if (exit != null && route(v, exit[0], exit[1])) v.fleeing = true;
            }
        }
        float limit = junctionRule(v, dt);
        boolean person = w.personAhead(v.x, v.y, v.angle) || trainComing(v);
        if ((person && v.timer < 2.5f) || (v.waitTimer > 0 && !zombiesClose) || trainComing(v)) {
            // Brake for pedestrians (then creep through, nudging them aside) or wait for someone to get in.
            if (v.speed > 45 && person) w.skid(v.x, v.y, v.angle, 6);
            v.speed = Math.max(0, v.speed - dt * 200);
            if (!person) v.timer = 0;
        } else {
            if (!person) v.timer = 0;
            v.stuckTimer += dt;
            boolean queue = carAhead(v);
            float throttle = queue && v.stuckTimer < 3 ? 0.15f : 1f;
            // Waiting at a red light, a stop sign or behind a queue isn't being stuck.
            if (v.held || queue) v.stuckTimer = Math.min(v.stuckTimer, 1);
            // (On the highway: highway speeds.)
            float cruise = city.onHighway(v.x, v.y) ? 115 : v.fleeing ? 90 : 70;
            boolean going = driveStep(v, dt, Math.min(limit, zombiesClose ? 60 : cruise), throttle);
            // A panicking driver who's gone round in a circle gives up on that way out and goes somewhere else.
            if (v.orbited) {
                v.orbited = false;
                if (v.fleeing) {
                    v.fleeing = false;
                    v.fleeCd = 20;
                    if (!newDestination(v)) v.stuckTimer = Math.max(v.stuckTimer, 5);
                }
            }
            if (!going) {
                arrive(v);
                // Out of town (where nobody sees it go, or off the edge of the map): gone. (Waiting at the edge
                // in sight, they used to block the way out for everyone behind.)
                float edge = 3 * City.T;
                boolean atEdge = v.x < edge || v.y < edge || v.x > city.worldW() - edge || v.y > city.worldH() - edge;
                if ((v.fleeing || v.through) && (!inView(v.x, v.y) || atEdge)) {
                    dropRiders(v, false);
                    return true;
                }
                if (v.fleeing || v.through) {
                    // (At the edge of town, in sight: pull in and wait.)
                    dropRiders(v, false);
                    v.parked = true;
                    v.speed = 0;
                    v.field = null;
                    pullOver(v);
                    return false;
                }
                // (Nowhere yet: wait a moment and look again before giving up.)
                if (!newDestination(v)) v.stuckTimer = Math.max(v.stuckTimer, 5);
            } else {
                hit(v, w.runOver(v.x, v.y, 8, v.speed, v.angle));
                if (v.broken) return false;
            }
        }
        if (v.stuckTimer > 8 && !zombiesClose && v.destCd <= 0) {
            v.stuckTimer = 0;
            if (!newDestination(v)) {
                if (!inView(v.x, v.y)) {
                    dropRiders(v, false);
                    return true;
                }
                // Nowhere to go: pull in and park (it's towed later, out of sight).
                dropRiders(v, false);
                v.parked = true;
                v.speed = 0;
                v.field = null;
                pullOver(v);
            }
        }
        // Surrounded: the driver gets out and runs.
        if (zombiesClose && v.speed < 12) {
            v.parked = true;
            v.state = ABANDONED;
            v.speed = 0;
            v.field = null;
            pullOver(v);
            getOut(v, true);
            dropRiders(v, true);
        }
        return false;
    }

    // ------------------------------------------------------------------ the player driving

    /** The player gets behind the wheel (anyone else in it stays aboard). */
    void takeWheel(Vehicle v, Entity e) {
        v.player = e;
        v.parked = false;
        v.pulling = false;
        v.rescue = false;
        v.state = DRIVE;
        v.field = null;
        v.alarm = 0;
        e.ride = v;
        v.riders.add(e);
        w.emit(Sfx.ENGINE, v.x, v.y);
    }

    /** The player gets out; the car stays where it is, and anyone riding with them gets out too. */
    void leaveWheel(Vehicle v, Entity e) {
        v.player = null;
        v.speed = 0;
        v.parked = true;
        v.state = ABANDONED;
        v.riders.remove(e);
        float sx = v.x + (float) Math.cos(v.angle + 1.57f) * 10, sy = v.y + (float) Math.sin(v.angle + 1.57f) * 10;
        float[] p = city.findWalkable(sx, sy);
        if (p == null) p = new float[]{v.x, v.y};
        e.x = p[0];
        e.y = p[1];
        e.vx = e.vy = 0;
        e.dead = false;
        e.removed = false;
        e.ride = null;
        w.entities.add(e);
        for (int i = 0; i < v.riders.size(); i++) w.release(v.riders.get(i), sx, sy);
        v.riders.clear();
    }

    /** Driving: the stick says where to go (pull back to reverse); the button sounds the horn, or fires the cannon. */
    private boolean updatePlayerVehicle(Vehicle v, float dt) {
        Entity e = v.player;
        if (v.broken) {
            // Wrecked: out you get.
            if (v.riders.contains(e)) leaveWheel(v, e);
            else v.player = null;
            return false;
        }
        float jx = w.joyX, jy = w.joyY, mag = Math.min(1, (float) Math.sqrt(jx * jx + jy * jy));
        float max = v.type == TANK ? 45 : v.type == CAR || v.type == CRUISER ? 125 : 95;
        float target = 0;
        if (mag > 0.15f) {
            float want = (float) Math.atan2(jy, jx);
            float diff = want - v.angle;
            while (diff > Math.PI) diff -= Math.PI * 2;
            while (diff < -Math.PI) diff += Math.PI * 2;
            float grip = Math.min(1, Math.abs(v.speed) / 30 + 0.35f) * (v.type == TANK ? 1.4f : 2.6f);
            if (Math.abs(diff) > 2.4f) {
                // Stick pulled back: reverse, steering the tail round.
                float rd = diff > 0 ? diff - (float) Math.PI : diff + (float) Math.PI;
                v.angle -= Math.max(-dt * grip, Math.min(dt * grip, rd));
                target = -max * 0.35f * mag;
            } else {
                v.angle += Math.max(-dt * grip, Math.min(dt * grip, diff));
                target = max * mag * (0.35f + 0.65f * Math.max(0, (float) Math.cos(diff)));
            }
        }
        if (v.hp < v.maxHp * 0.3f) target *= 0.6f;
        v.speed += (target - v.speed) * Math.min(1, dt * (Math.abs(target) < Math.abs(v.speed) ? 2.5f : 1.4f));
        float nx = v.x + (float) Math.cos(v.angle) * v.speed * dt, ny = v.y + (float) Math.sin(v.angle) * v.speed * dt;
        float r = v.type == TANK ? 8 : 5.5f;
        // (A car pulled over against the kerb may start out touching something: let it drive away.)
        if (!city.circleBlocked(v.x, v.y, r) && city.circleBlocked(nx, ny, r)) {
            if (Math.abs(v.speed) > 35 && v.crashCd <= 0) {
                v.crashCd = 0.5f;
                damage(v, Math.abs(v.speed) * (v.type == TANK ? 0.03f : 0.25f), false);
                w.emit(Sfx.CRASH, v.x, v.y);
                w.shake = Math.max(w.shake, 0.4f);
            }
            v.speed *= -0.25f;
        } else {
            v.x = nx;
            v.y = ny;
        }
        if (Math.abs(v.speed) > 8) w.runOver(v.x, v.y, v.length() * 0.5f + 2, Math.abs(v.speed), v.angle);
        e.x = v.x;
        e.y = v.y;
        if (v.soundCd <= 0 && Math.abs(v.speed) > 20) {
            v.soundCd = 1.2f;
            w.emit(Sfx.ENGINE, v.x, v.y);
        }
        v.cannonCd -= dt;
        if (w.ctrlAttack) {
            if (v.type == TANK) {
                Entity z = w.nearestVisibleZombie(v.x, v.y, 260);
                if (z != null) {
                    v.turret = turnTo(v.turret, (float) Math.atan2(z.y - v.y, z.x - v.x), dt * 2.5f);
                    if (v.cannonCd <= 0) {
                        v.cannonCd = 2.5f;
                        w.tankShell(v.x + (float) Math.cos(v.turret) * 14, v.y + (float) Math.sin(v.turret) * 14, z.x, z.y);
                    }
                }
            } else if (v.soundCd <= 0.6f) {
                // The horn: every zombie for streets around comes to see.
                v.soundCd = 1.2f;
                w.emit(v.type == CRUISER ? Sfx.SIREN : Sfx.HORN, v.x, v.y);
                w.noise(v.x, v.y, 260);
            }
        }
        return false;
    }

    /** A survivor jumps into an abandoned car and drives off. */
    void takeCar(Vehicle v, Entity driver) {
        v.parked = false;
        v.pulling = false;
        v.state = CRUISE;
        v.riders.add(driver);
        v.timer = 0;
        if (!newDestination(v)) {
            v.riders.remove(driver);
            w.release(driver, v.x, v.y);
            v.parked = true;
            v.state = ABANDONED;
            return;
        }
        v.rescue = false;
        boarded(v, driver);
        v.riders.remove(v.riders.size() - 1);
    }

    /** Called by World when someone climbs in. */
    void boarded(Vehicle v, Entity e) {
        v.riders.add(e);
        if (v.rescue || v.type != CAR) return;
        // Take them to a safe zone with room if there is one.
        Dispatch.SafeZone best = null;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < w.dispatch.zones.size(); i++) {
            Dispatch.SafeZone z = w.dispatch.zones.get(i);
            if (z.full) continue;
            float d = (z.x - v.x) * (z.x - v.x) + (z.y - v.y) * (z.y - v.y);
            if (d < bd) {
                bd = d;
                best = z;
            }
        }
        if (best != null && route(v, best.x, best.y)) {
            v.rescue = true;
            v.zone = best;
        }
    }

    /**
     * A crowd of the dead on the road ahead (up to about 160 units out) or already round the car: police
     * and troops stop short there and go in on foot, rather than driving into the middle of them.
     */
    private boolean hordeAhead(Vehicle v) {
        if (!w.zombieWithin(v.x, v.y, 200)) return false;
        float ca = (float) Math.cos(v.angle), sa = (float) Math.sin(v.angle);
        for (float d = 45; d <= 125; d += 40)
            if (w.countZombiesNear(v.x + ca * d, v.y + sa * d, 50) >= 4) return true;
        return w.countZombiesNear(v.x, v.y, 45) >= 3;
    }

    /** At the end of a trip the riders get out (at the safe zone, if that's where they were going). */
    private void arrive(Vehicle v) {
        if (v.riders.isEmpty()) return;
        boolean zone = v.rescue && v.zone != null && !v.zone.removed;
        if (zone)
            w.dispatch.say(Dispatch.WHO_INFO, null, "A driver dropped " + v.riders.size()
                    + (v.riders.size() == 1 ? " person" : " people") + " at the " + v.zone.place + " safe zone.", v.x, v.y);
        for (int i = 0; i < v.riders.size(); i++) {
            Entity e = v.riders.get(i);
            if (!w.release(e, v.x + (float) Math.cos(v.angle + 1.57f) * 9, v.y + (float) Math.sin(v.angle + 1.57f) * 9))
                continue;
            if (zone) e.task = Dispatch.T_SEEK;
        }
        v.riders.clear();
        v.rescue = false;
        v.zone = null;
    }

    private void dropRiders(Vehicle v, boolean panic) {
        for (int i = 0; i < v.riders.size(); i++) {
            Entity e = v.riders.get(i);
            float a = v.angle + (i % 2 == 0 ? 1.57f : -1.57f);
            if (!w.release(e, v.x + (float) Math.cos(a) * 9, v.y + (float) Math.sin(a) * 9)) continue;
            if (panic) {
                e.fleeTimer = 3;
                e.threatX = v.x;
                e.threatY = v.y;
            }
        }
        v.riders.clear();
        v.rescue = false;
    }

    private void getOut(Vehicle v, boolean panic) {
        Entity driver = w.spawn(Entity.CIVILIAN, v.x + (float) Math.cos(v.angle + 1.57f) * 9,
                v.y + (float) Math.sin(v.angle + 1.57f) * 9);
        if (driver != null && panic) {
            driver.fleeTimer = 3;
            driver.threatX = v.x;
            driver.threatY = v.y;
        }
    }

    private void unload(Vehicle v, boolean announce) {
        Entity first = null;
        for (int i = 0; i < v.passengers; i++) {
            Entity e = w.spawn(v.passengerType, v.x + (float) Math.cos(v.angle + 1.57f) * (8 + i * 3),
                    v.y + (float) Math.sin(v.angle + 1.57f) * (8 + i * 3));
            if (e == null) continue;
            if (first == null) first = e;
            if (v.guardUnit) w.applyRole(e, Entity.ROLE_GUARD);
            if (v.kind == K_RIOT_VAN) w.applyRole(e, Entity.ROLE_RIOT);
            // (Each SWAT team has a marksman with it.)
            if (v.swat) w.applyRole(e, i == v.passengers - 1 && city.cfg.nature() ? Entity.ROLE_MARKSMAN : Entity.ROLE_SWAT);
            else if (v.passengerType == Entity.SOLDIER && !v.guardUnit && city.cfg.nature()) w.kitOut(e);
            else if (v.passengerType == Entity.COP && v.agency > 0) w.makeAgency(e, v.agency);
            if (v.reinforcement) {
                w.reinforce(e);
                w.dispatch.convoyUnloaded(v, e);
            }
            if (v.incident != null && !v.incident.resolved) {
                e.task = Dispatch.T_RESPOND;
                e.incident = v.incident;
            } else if (v.zone != null && !v.zone.removed) {
                e.task = Dispatch.T_GUARD;
                e.zone = v.zone;
            }
        }
        if (first == null) return;
        int who = v.passengerType == Entity.SOLDIER ? Dispatch.WHO_MILITARY : Dispatch.WHO_POLICE;
        if (announce && v.place != null)
            w.dispatch.say(who, first, "On the ground at " + v.place + ". Moving in.", v.x, v.y);
        else if (!announce)
            w.dispatch.say(who, first, (v.type == TRUCK ? "Our truck is wrecked" : "Our car is wrecked")
                    + " on " + city.placeName(v.x, v.y) + ". Continuing on foot.", v.x, v.y);
    }

    // ------------------------------------------------------------------ fire engines

    // ------------------------------------------------------------------ crews

    /** Someone climbs aboard (out of the world until they get out again). */
    void board(Vehicle v, Entity e) {
        e.task = Dispatch.T_NONE;
        e.rig = v;
        e.dead = true;
        e.removed = true;
        e.vx = e.vy = 0;
        v.crew.add(e);
    }

    /** Everyone aboard gets out beside it. They stay its crew (unless it's home: then they're off duty). */
    void crewOut(Vehicle v, boolean offDuty) {
        for (int i = 0; i < v.crew.size(); i++) {
            Entity e = v.crew.get(i);
            float a = v.angle + (i % 2 == 0 ? 1.57f : -1.57f);
            if (!w.release(e, v.x + (float) Math.cos(a) * (10 + i * 3), v.y + (float) Math.sin(a) * (10 + i * 3))) continue;
            e.rig = offDuty ? null : v;
            e.taskTimer = 0;
        }
        v.crew.clear();
    }

    /** Calls the crew on foot back aboard. Returns how many are still out. */
    int recall(Vehicle v) {
        int out = 0;
        for (int i = 0, n = w.entities.size(); i < n; i++) {
            Entity e = w.entities.get(i);
            if (e.dead || e.rig != v) continue;
            out++;
            if (e.task != Dispatch.T_BOARD) {
                w.dispatch.releaseForResupply(e);
                e.task = Dispatch.T_BOARD;
            }
        }
        return out;
    }

    /** Crew members still on foot. */
    int crewOnFoot(Vehicle v) {
        int out = 0;
        for (int i = 0, n = w.entities.size(); i < n; i++) {
            Entity e = w.entities.get(i);
            if (!e.dead && e.rig == v) out++;
        }
        return out;
    }

    /** People aboard vehicles, by type (they still count). */
    void countCrews(int[] counts) {
        for (int i = 0, n = vehicles.size(); i < n; i++) {
            ArrayList<Entity> c = vehicles.get(i).crew;
            for (int k = 0; k < c.size(); k++) counts[c.get(k).type]++;
        }
    }

    // ------------------------------------------------------------------ patrol cars

    /** A patrol car out on the roads with two officers aboard (at the start of the game). */
    Vehicle startPatrol(City.Facility station) {
        float[] start = randomRoad();
        if (start == null) return null;
        Vehicle v = make(CRUISER);
        v.x = start[0];
        v.y = start[1];
        v.angle = w.rnd.nextInt(4) * (float) Math.PI / 2;
        v.patrol = true;
        v.station = station;
        v.state = PATROL;
        v.number = ++unitCount;
        if (!newDestination(v)) return null;
        // (Every fifth unit is a motorcycle officer, riding alone.)
        boolean bike = city.cfg.nature() && v.number % 5 == 0;
        if (bike) setKind(v, K_BIKE);
        for (int k = 0; k < (bike ? 1 : 2); k++) {
            Entity e = w.create(Entity.COP, v.x, v.y);
            e.rig = v;
            v.crew.add(e);
        }
        vehicles.add(v);
        return v;
    }

    /** What a patrol car is called on the radio. */
    String callsign(Vehicle v) {
        Country c = city.country;
        return v.agency == 1 ? c.hpName + " " + v.number : v.agency == 2 ? c.ruralName + " " + v.number : v.agency == 3 ? "Ranger " + v.number
                : v.agency == 4 ? Dispatch.federal(c) + " " + v.number : (v.kind == K_BIKE ? "Motor " : "Car ") + v.number;
    }

    /**
     * A highway patrol car on the highway, or a sheriff's car out on the country roads, with its officer
     * aboard (at the start of the game).
     */
    Vehicle startAgencyPatrol(int agency) {
        float[] start = agency == 1 ? city.randomHighway(w.rnd) : city.randomCountryRoad(w.rnd);
        if (start == null) return null;
        Vehicle v = make(CRUISER);
        v.x = start[0];
        v.y = start[1];
        v.patrol = true;
        v.agency = agency;
        v.state = PATROL;
        v.number = agency * 10 + (++unitCount);
        if (!newDestination(v)) return null;
        Entity e = w.create(Entity.COP, v.x, v.y);
        e.agency = agency;
        Country c = city.country;
        e.body = agency == 1 ? c.hpShirt : c.ruralShirt;
        e.rig = v;
        v.crew.add(e);
        vehicles.add(v);
        return v;
    }

    /**
     * Highway patrol, while all is quiet, pulls the odd driver over: the car pulls onto the shoulder, the
     * patrol car stops behind it with its lights going, and after a while both drive on. Returns true while
     * it's stopped behind one.
     */
    private boolean trafficStop(Vehicle v, float dt) {
        Vehicle c = v.stopping;
        if (c != null) {
            v.stopT += dt;
            boolean over = c.removedFromFleet || c.broken || c.parked || v.stopT > 26 || w.alert > 0
                    || Math.hypot(c.x - v.x, c.y - v.y) > 220 || v.state != PATROL;
            if (over) {
                c.pulled = false;
                v.stopping = null;
                v.stopCd = 40 + w.rnd.nextFloat() * 50;
                return false;
            }
            // Up behind it, then stop.
            if (Math.hypot(c.x - v.x, c.y - v.y) < 34 && Math.abs(c.speed) < 3) {
                v.speed = Math.max(0, v.speed - dt * 120);
                return true;
            }
            return false;
        }
        v.stopCd -= dt;
        if (v.stopCd > 0 || w.alert > 0 || v.state != PATROL || !city.onHighway(v.x, v.y)) return false;
        v.stopCd = 8;
        float fx = (float) Math.cos(v.angle), fy = (float) Math.sin(v.angle);
        for (int i = 0, n = vehicles.size(); i < n; i++) {
            Vehicle o = vehicles.get(i);
            if (o.type != CAR || o.player != null || o.pulled || o.fleeing || o.parked || o.broken || !o.riders.isEmpty()) continue;
            float dx = o.x - v.x, dy = o.y - v.y, ahead = dx * fx + dy * fy;
            if (ahead < 30 || ahead > 170 || Math.abs(dx * fy - dy * fx) > 30) continue;
            if (fx * (float) Math.cos(o.angle) + fy * (float) Math.sin(o.angle) < 0.8f || !city.onHighway(o.x, o.y)) continue;
            o.pulled = true;
            v.stopping = o;
            v.stopT = 0;
            w.emit(Sfx.SIREN, v.x, v.y);
            return false;
        }
        return false;
    }

    /** Patrol cars on the streets (not wrecked or abandoned). */
    int patrolCars() {
        int n = 0;
        for (int i = 0, m = vehicles.size(); i < m; i++) {
            Vehicle v = vehicles.get(i);
            if (v.patrol && v.agency == 0 && !v.broken && !v.parked) n++;
        }
        return n;
    }

    /** Every so often a station puts another car on the streets if it has two officers free. */
    private void staffPatrols(float dt) {
        staffCd -= dt;
        if (staffCd > 0) return;
        staffCd = 8;
        if (patrolCars() >= patrolTarget) return;
        for (City.Facility f : city.facilities) {
            if (f.kind != City.FACILITY_POLICE || w.countZombiesNear(f.x, f.y, 120) > 0) continue;
            boolean bike = city.cfg.nature() && (unitCount + 1) % 5 == 0;
            int want = bike ? 1 : 2;
            ArrayList<Entity> two = new ArrayList<Entity>();
            for (int i = 0, n = w.entities.size(); i < n && two.size() < want; i++) {
                Entity e = w.entities.get(i);
                if (e.dead || e.type != Entity.COP || e.task != Dispatch.T_NONE || e.rig != null) continue;
                if (e.role == Entity.ROLE_MARKSMAN || e.role == Entity.ROLE_K9) continue;
                if (Math.hypot(e.x - f.x, e.y - f.y) < 220) two.add(e);
            }
            if (two.size() < want) continue;
            float[] p = city.nearestDrivable(f.gateX > 0 ? f.gateX : f.x, f.gateY > 0 ? f.gateY : f.y);
            if (p == null) continue;
            Vehicle v = make(CRUISER);
            v.x = p[0];
            v.y = p[1];
            v.angle = (float) Math.atan2(p[1] - f.y, p[0] - f.x);
            v.patrol = true;
            v.station = f;
            v.state = MUSTER;
            v.crewWanted = want;
            v.number = ++unitCount;
            if (bike) setKind(v, K_BIKE);
            for (Entity e : two) {
                e.rig = v;
                e.task = Dispatch.T_BOARD;
            }
            vehicles.add(v);
            return;
        }
    }

    /** The nearest patrol car free to answer a call. */
    Vehicle patrolFor(float x, float y) {
        Vehicle best = null;
        float bd = Float.MAX_VALUE;
        for (int i = 0, n = vehicles.size(); i < n; i++) {
            Vehicle v = vehicles.get(i);
            if (!v.patrol || v.state != PATROL || v.broken || v.parked || v.crew.isEmpty()) continue;
            // Highway patrol and sheriffs keep to their own roads until the town is under attack; then they
            // come in as backup (when no town car is nearer). Rangers stay out in the country.
            boolean town = city.inTown(x, y) && !city.nearHighway(x, y, 300);
            if (town && (v.agency == 3 || (v.agency > 0 && w.alert < 2))) continue;
            float d = (v.x - x) * (v.x - x) + (v.y - y) * (v.y - y);
            if (town && v.agency > 0) d += 500 * 500;
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        return best;
    }

    /** Sends a patrol car to a call, lights and siren. */
    boolean respond(Vehicle v, Dispatch.Incident inc) {
        v.state = DRIVE;
        if (!route(v, inc.x, inc.y)) {
            v.state = PATROL;
            return false;
        }
        v.incident = inc;
        v.stuckTimer = 0;
        w.emit(Sfx.SIREN, v.x, v.y);
        w.dispatch.say(Dispatch.WHO_POLICE, null, "Dispatch: " + callsign(v) + ", respond to " + inc.place + ".", inc.x, inc.y);
        w.dispatch.say(Dispatch.WHO_POLICE, v.crew.get(0), callsign(v) + ", en route.", v.x, v.y);
        return true;
    }

    /**
     * A squad from the base mounts up: a truck pulls up at the gate, the soldiers climb aboard, and it drives
     * them to the fighting (the same soldiers, names, kills and all). False if there's no road for it.
     */
    boolean troopTruck(java.util.List<Entity> squad, City.Facility base, Dispatch.Incident inc) {
        return troopTruck(squad, base, inc, -1);
    }

    /** A truck (or, kind >= 0, a vehicle of that kind) for this squad from the base. */
    boolean troopTruck(java.util.List<Entity> squad, City.Facility base, Dispatch.Incident inc, int forceKind) {
        // (One of the trucks parked on the base, if there's one left; otherwise one comes round to the gate.)
        float[] p = city.takeParkedTruck(base.x, base.y, Math.max(260, base.r * 2.5f));
        if (p == null) p = city.nearestDrivable(base.gateX > 0 ? base.gateX : base.x, base.gateY > 0 ? base.gateY : base.y);
        if (p == null || squad.isEmpty()) return false;
        Vehicle v = make(TRUCK);
        v.x = p[0];
        v.y = p[1];
        v.homeX = p[0];
        v.homeY = p[1];
        if (city.cfg.nature()) {
            // An APC into a big fight (or from an armoured base), a Humvee for a fast run a long way out.
            float far = (float) Math.hypot(inc.x - p[0], inc.y - p[1]);
            int kind = inc.zombiesNear >= 10 || (base.baseType == City.BT_ARMOUR && inc.zombiesNear >= 5) ? K_APC
                    : far > 900 && squad.size() <= 4 ? K_HUMVEE : 0;
            if (forceKind >= 0) kind = forceKind;
            if (kind != 0) setKind(v, kind);
        }
        v.angle = (float) Math.atan2(inc.y - p[1], inc.x - p[0]);
        v.state = MUSTER;
        v.crewWanted = squad.size();
        v.incident = inc;
        v.tx = inc.x;
        v.ty = inc.y;
        v.place = inc.place;
        v.passengerType = Entity.SOLDIER;
        for (Entity e : squad) {
            e.rig = v;
            e.task = Dispatch.T_BOARD;
        }
        vehicles.add(v);
        return true;
    }

    /**
     * A Humvee patrol from the base: the squad mounts up, drives out to (tx, ty) and back, its gunner firing on
     * any of the dead it passes. They stay aboard unless the Humvee is wrecked.
     */
    boolean basePatrol(java.util.List<Entity> squad, City.Facility base, float tx, float ty) {
        float[] p = city.takeParkedTruck(base.x, base.y, Math.max(260, base.r * 2.5f));
        if (p == null) p = city.nearestDrivable(base.gateX > 0 ? base.gateX : base.x, base.gateY > 0 ? base.gateY : base.y);
        if (p == null || squad.isEmpty()) return false;
        Vehicle v = make(TRUCK);
        setKind(v, K_HUMVEE);
        v.loop = true;
        v.x = p[0];
        v.y = p[1];
        v.homeX = p[0];
        v.homeY = p[1];
        v.angle = (float) Math.atan2(ty - p[1], tx - p[0]);
        v.state = MUSTER;
        v.crewWanted = squad.size();
        v.tx = tx;
        v.ty = ty;
        v.place = city.placeName(tx, ty);
        v.passengerType = Entity.SOLDIER;
        for (Entity e : squad) {
            e.rig = v;
            e.task = Dispatch.T_BOARD;
        }
        vehicles.add(v);
        return true;
    }

    /** Patrols out from this base now. */
    int basePatrols(City.Facility base) {
        int n = 0;
        for (int i = 0, m = vehicles.size(); i < m; i++) {
            Vehicle v = vehicles.get(i);
            if (v.loop && !v.broken && Math.hypot(v.homeX - base.x, v.homeY - base.y) < Math.max(300, base.r * 3)) n++;
        }
        return n;
    }

    /** Soldiers mustering for, or riding to, this call in trucks. */
    int inboundSoldiers(Dispatch.Incident inc) {
        int n = 0;
        for (int i = 0, m = vehicles.size(); i < m; i++) {
            Vehicle v = vehicles.get(i);
            if (v.type == TRUCK && v.incident == inc && (v.state == MUSTER || v.state == DRIVE)) n += Math.max(v.crew.size(), v.crewWanted);
        }
        return n;
    }

    /** Officers in patrol cars on their way to this call. */
    int inbound(Dispatch.Incident inc) {
        int n = 0;
        for (int i = 0, m = vehicles.size(); i < m; i++) {
            Vehicle v = vehicles.get(i);
            if (v.patrol && v.incident == inc && v.state == DRIVE) n += v.crew.size();
        }
        return n;
    }

    /** Lets go of any crew member still tied to the vehicle (they carry on on foot). */
    private void dropCrew(Vehicle v) {
        for (int i = 0, n = w.entities.size(); i < n; i++) {
            Entity e = w.entities.get(i);
            if (e.rig == v) {
                e.rig = null;
                if (e.task == Dispatch.T_BOARD) e.task = Dispatch.T_NONE;
            }
        }
    }

    /**
     * A patrol car: drives its beat by the rules of the road, stops and gets out when the officers see
     * one of them, answers calls with lights and siren, and drives on once its officers are back in.
     */
    private boolean updatePatrol(Vehicle v, float dt) {
        v.checkCd -= dt;
        boolean check = v.checkCd <= 0;
        if (check) v.checkCd = 0.5f;
        if (v.state == MUSTER) {
            v.speed = 0;
            v.timer += dt;
            if (v.crew.size() >= v.crewWanted || (v.timer > 15 && !v.crew.isEmpty())) {
                dropCrew(v);
                v.state = PATROL;
                if (!newDestination(v)) v.stuckTimer = 99;
            } else if (v.timer > 30) {
                dropCrew(v);
                v.parked = true;
            }
            return false;
        }
        if (v.state == SCENE || v.state == RECALL) {
            v.speed = 0;
            if (!check) return false;
            if (v.state == SCENE) {
                // Waits while its officers deal with it, then calls them back.
                boolean busy = w.countZombiesNear(v.x, v.y, 130) > 0;
                for (int i = 0, n = w.entities.size(); i < n && !busy; i++) {
                    Entity e = w.entities.get(i);
                    if (!e.dead && e.rig == v && (e.task == Dispatch.T_RESPOND || e.task == Dispatch.T_CORDON)) busy = true;
                }
                v.quiet = busy ? 0 : v.quiet + 0.5f;
                if (crewOnFoot(v) == 0 && v.crew.isEmpty()) {
                    v.parked = true;
                    v.state = ABANDONED;
                    return false;
                }
                if (v.quiet >= 6) {
                    v.state = RECALL;
                    v.timer = 0;
                }
                return false;
            }
            v.timer += 0.5f;
            int out = recall(v);
            if (out > 0 && v.timer < 40) return false;
            dropCrew(v);
            if (v.crew.isEmpty()) {
                v.parked = true;
                v.state = ABANDONED;
                return false;
            }
            v.state = PATROL;
            v.incident = null;
            if (!newDestination(v)) v.stuckTimer = 99;
            return false;
        }
        if (v.agency == 1 && trafficStop(v, dt)) return false;
        // Officers spot one of them from the car: pull up and get out (well short of a crowd of them). A
        // moving car looks every moment, so it doesn't drive on into them between looks.
        boolean look = check || (Math.abs(v.speed) > 15 && w.zombieWithin(v.x, v.y, 190));
        if (look && v.crew.size() > 0 && (hordeAhead(v)
                || (w.countZombiesNear(v.x, v.y, 90) > 0 && w.countZombiesNear(v.x, v.y, 30) < 4))) {
            v.speed = 0;
            v.state = SCENE;
            v.quiet = 0;
            Dispatch.Incident inc = v.incident;
            ArrayList<Entity> out = new ArrayList<Entity>(v.crew);
            crewOut(v, false);
            if (inc != null && !inc.resolved) {
                for (Entity e : out) {
                    e.task = Dispatch.T_RESPOND;
                    e.incident = inc;
                    e.onScene = false;
                }
            } else if (v.soundCd <= 0 && !out.isEmpty()) {
                v.soundCd = 4;
                w.dispatch.say(Dispatch.WHO_POLICE, out.get(0), callsign(v) + ", contact on "
                        + city.placeName(v.x, v.y) + ". We're out of the car.", v.x, v.y);
            }
            return false;
        }
        if (v.state == DRIVE) {
            Dispatch.Incident inc = v.incident;
            if (inc == null || inc.resolved) {
                v.state = PATROL;
                v.incident = null;
                if (!newDestination(v)) v.stuckTimer = 99;
                return false;
            }
            if (v.soundCd <= 0) {
                v.soundCd = 3.5f;
                w.emit(Sfx.SIREN, v.x, v.y);
            }
            v.stuckTimer += dt;
            boolean moving = driveStep(v, dt, v.kind == K_BIKE ? 120 : 95, carAhead(v) ? 0.5f : 1f);
            if (moving) hit(v, w.runOver(v.x, v.y, 8, v.speed, v.angle));
            if (!moving || Math.hypot(inc.x - v.x, inc.y - v.y) < 50 || v.stuckTimer > 6) {
                v.speed = 0;
                v.state = SCENE;
                v.quiet = 0;
                ArrayList<Entity> out = new ArrayList<Entity>(v.crew);
                crewOut(v, false);
                for (Entity e : out) {
                    e.task = Dispatch.T_RESPOND;
                    e.incident = inc;
                    e.onScene = false;
                }
            }
            return false;
        }
        // On patrol: an ordinary car on the road, keeping to the rules.
        if (v.crew.isEmpty()) {
            v.parked = true;
            return false;
        }
        float limit = junctionRule(v, dt);
        boolean person = w.personAhead(v.x, v.y, v.angle);
        // Brake for someone in the road, then creep through (in a crowd nobody gets anywhere otherwise).
        if (trainComing(v) || (person && v.timer < 2.5f)) {
            v.speed = Math.max(0, v.speed - dt * 200);
            if (person) v.timer += dt;
            return false;
        }
        if (!person) v.timer = 0;
        v.stuckTimer += dt;
        boolean queue = carAhead(v);
        if (v.held || queue) v.stuckTimer = Math.min(v.stuckTimer, 1);
        if (!driveStep(v, dt, Math.min(limit, person ? 15 : city.onHighway(v.x, v.y) ? 105 : 60), queue ? 0.15f : 1f) || v.stuckTimer > 10) {
            v.stuckTimer = 0;
            newDestination(v);
        }
        return false;
    }

    private boolean updateFireEngine(Vehicle v, float dt) {
        if (v.state == IDLE) {
            v.speed = 0;
            return false;
        }
        if (v.state == MUSTER) {
            // Waiting at the station for the crew to get aboard.
            v.speed = 0;
            v.timer += dt;
            boolean ready = v.crew.size() >= v.crewWanted || (v.timer > 12 && !v.crew.isEmpty());
            if (!ready && v.timer > 25) {
                for (int i = 0, n = w.entities.size(); i < n; i++) {
                    Entity e = w.entities.get(i);
                    if (e.rig == v) {
                        e.rig = null;
                        if (e.task == Dispatch.T_BOARD) e.task = Dispatch.T_NONE;
                    }
                }
                if (v.fire != null) v.fire.engine = null;
                v.fire = null;
                v.state = IDLE;
                return false;
            }
            if (!ready) return false;
            // Anyone who didn't make it aboard stays behind.
            for (int i = 0, n = w.entities.size(); i < n; i++) {
                Entity e = w.entities.get(i);
                if (e.rig == v && !e.dead) {
                    e.rig = null;
                    e.task = Dispatch.T_NONE;
                }
            }
            if (v.fire == null || !w.fires.contains(v.fire) || !route(v, v.fire.x, v.fire.y)) {
                crewOut(v, true);
                v.state = IDLE;
                return false;
            }
            v.state = DRIVE;
            w.emit(Sfx.SIREN, v.x, v.y);
            w.dispatch.say(Dispatch.WHO_FIRE, null, "Fire Dept: Engine " + v.number + " responding to a fire on "
                    + city.placeName(v.fire.x, v.fire.y) + ", " + v.crew.size() + " aboard.", v.fire.x, v.fire.y);
            return false;
        }
        if (v.state == WAIT) {
            v.timer -= dt;
            if (v.timer <= 0) {
                v.state = DRIVE;
                w.emit(Sfx.SIREN, v.x, v.y);
                if (v.fire != null)
                    w.dispatch.say(Dispatch.WHO_FIRE, null, "Fire Dept: Engine " + v.number + " responding to a fire on "
                            + city.placeName(v.fire.x, v.fire.y) + ".", v.fire.x, v.fire.y);
            }
            return false;
        }
        if (v.state == RECALL) {
            // Hoses in and everyone back aboard before it goes.
            v.speed = 0;
            v.spraying = false;
            v.timer += dt;
            v.checkCd -= dt;
            if (v.checkCd > 0) return false;
            v.checkCd = 0.5f;
            int out = recall(v);
            if (out > 0 && v.timer < 30 && !(v.timer > 6 && w.countZombiesNear(v.x, v.y, 30) >= 4)) return false;
            // (Whoever is left behind carries on on foot.)
            for (int i = 0, n = w.entities.size(); i < n; i++) {
                Entity e = w.entities.get(i);
                if (e.rig == v) {
                    e.rig = null;
                    if (e.task == Dispatch.T_BOARD) e.task = Dispatch.T_NONE;
                }
            }
            v.state = RETURN;
            if (!route(v, v.homeX, v.homeY)) {
                crewOut(v, true);
                v.state = IDLE;
            }
            return false;
        }
        // Too many zombies around: get the crew back aboard and pull out.
        if (v.state == SPRAY && w.countZombiesNear(v.x, v.y, 30) >= 4) {
            v.spraying = false;
            if (v.fire != null) v.fire.engine = null;
            v.fire = null;
            v.state = RECALL;
            v.timer = 20;
            w.dispatch.say(Dispatch.WHO_FIRE, null, "Fire Dept: Engine " + v.number + " pulling back, too many of them!",
                    v.x, v.y);
            return false;
        }
        if (v.state == SPRAY) {
            v.speed = 0;
            World.Fire f = v.fire;
            if (f == null || f.life <= 0 || !w.fires.contains(f)) {
                v.spraying = false;
                World.Fire next = w.nearestFire(v.x, v.y, 300);
                if (next != null && (next.engine == null || next.engine == v)) {
                    next.engine = v;
                    v.fire = next;
                    if (Math.hypot(next.x - v.x, next.y - v.y) < 70) return false;
                    // Crew back aboard, then on to the next one.
                    if (crewOnFoot(v) == 0 && route(v, next.x, next.y)) {
                        v.state = DRIVE;
                        return false;
                    }
                    next.engine = null;
                }
                v.fire = null;
                v.state = RECALL;
                v.timer = 0;
                w.dispatch.say(Dispatch.WHO_FIRE, null, "Fire Dept: Engine " + v.number + ", fire's out. Packing up.",
                        v.x, v.y);
                return false;
            }
            // Hose it down.
            if (v.soundCd <= 0) {
                v.soundCd = 0.9f;
                w.emit(Sfx.HOSE, v.x, v.y);
            }
            v.spraying = true;
            v.sprayX = f.x;
            v.sprayY = f.y;
            v.angle = turnTo(v.angle, (float) Math.atan2(f.y - v.y, f.x - v.x), dt * 2);
            w.douse(f, dt);
            if (w.rnd.nextFloat() < dt * 30) {
                float t = w.rnd.nextFloat();
                float px = v.x + (f.x - v.x) * t, py = v.y + (f.y - v.y) * t - (float) Math.sin(t * Math.PI) * 8;
                w.particle(px, py, w.rnd.nextFloat() * 10 - 5, w.rnd.nextFloat() * 10 - 5, 0.4f, 1.2f, 0xFFA8D8FF, World.P_DOT);
            }
            return false;
        }
        v.stuckTimer += dt;
        // Slow down coming up to the fire (or the station).
        float tx = v.state == DRIVE && v.fire != null ? v.fire.x : v.homeX, ty = v.state == DRIVE && v.fire != null ? v.fire.y : v.homeY;
        float near = (float) Math.hypot(tx - v.x, ty - v.y);
        boolean moving = driveStep(v, dt, Math.max(18, Math.min(85, (near - 40) * 1.4f)), carAhead(v) ? 0.5f : 1f);
        if (moving) hit(v, w.runOver(v.x, v.y, 9, v.speed, v.angle));
        if (v.broken) return false;
        if (v.state == DRIVE) {
            if (v.soundCd <= 0) {
                v.soundCd = 3.5f;
                w.emit(Sfx.SIREN, v.x, v.y);
            }
            boolean close = v.fire != null && Math.hypot(v.fire.x - v.x, v.fire.y - v.y) < 60;
            if (!moving || close || v.stuckTimer > 4) {
                v.state = SPRAY;
                v.stuckTimer = 0;
                v.speed = 0;
                // The crew jumps down to help.
                crewOut(v, false);
            }
            return false;
        }
        // Back at the station: the crew gets out and it parks, ready for the next call.
        if (!moving || v.stuckTimer > 6 || Math.hypot(v.homeX - v.x, v.homeY - v.y) < 14) {
            v.speed = 0;
            v.stuckTimer = 0;
            crewOut(v, true);
            v.state = IDLE;
            if (v.station != null && v.station.gateX > 0)
                v.angle = (float) Math.atan2(v.y - v.station.y, v.x - v.station.x);
        }
        return false;
    }

    private static float turnTo(float from, float to, float amount) {
        float diff = to - from;
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        return from + Math.max(-amount, Math.min(amount, diff));
    }

    // ------------------------------------------------------------------ helicopter

    private boolean updateHeli(Vehicle v, float dt) {
        if (v.state == IDLE) {
            // On the pad: rotor still, refuelling after a flight.
            v.refuel = Math.max(0, v.refuel - dt);
            v.speed = v.hvx = v.hvy = 0;
            v.alt = 0;
            return false;
        }
        if (v.state == MUSTER) {
            // On the pad with the rotor still, waiting for the crew to walk out and climb aboard.
            v.timer += dt;
            Entity pilot = livePilot(v);
            boolean pilotIn = pilot != null && v.crew.contains(pilot);
            if (pilot == null || (!pilotIn && v.timer > 60)) {
                landed(v);
                v.refuel = 5;
                return false;
            }
            if (pilotIn && (v.crew.size() >= v.crewWanted || v.timer > 25)) {
                // Anyone who didn't make it in time stays behind.
                for (int i = 0, n = w.entities.size(); i < n; i++) {
                    Entity e = w.entities.get(i);
                    if (!e.dead && e.rig == v && e.task == Dispatch.T_BOARD) {
                        e.task = Dispatch.T_NONE;
                        e.rig = null;
                    }
                }
                if (v.kind != K_POLICE_HELI && v.kind != K_RESCUE_HELI) v.troops = Math.max(0, v.crew.size() - 3);
                v.state = SPOOL;
                v.spoolTime = v.timer = 7;
            }
            return false;
        }
        if (v.soundCd <= 0) {
            v.soundCd = v.state == SPOOL ? 0.9f : 0.6f;
            w.emit(Sfx.ROTOR, v.x, v.y);
        }
        // Rotor wash kicks up dust when it's low.
        if ((v.alt < 0.6f && v.state != SPOOL) || (v.state == SPOOL && v.timer < 3)) {
            if (w.rnd.nextFloat() < dt * 25) {
                float a = w.rnd.nextFloat() * (float) Math.PI * 2;
                w.particle(v.x + (float) Math.cos(a) * 10, v.y + (float) Math.sin(a) * 10, (float) Math.cos(a) * 45,
                        (float) Math.sin(a) * 45, 0.8f, 2.5f, 0xFFB8AE98, World.P_SMOKE);
            }
        }
        if (v.state == SPOOL) {
            // Spinning up on the pad (or wherever it set down).
            v.timer -= dt;
            if (v.timer <= 0) v.state = FLY_IN;
            return false;
        }
        float gx = v.x, gy = v.y, speed = 110, face = Float.NaN;
        // (Escorting a convoy: wherever its head is now.)
        if (v.escort != null) {
            if (w.dispatch.convoys.contains(v.escort)) {
                v.tx = v.escort.x;
                v.ty = v.escort.y;
            } else if (v.state == CIRCLE) {
                v.escort = null;
                v.timer = Math.min(v.timer, 40);
            }
        }
        if (v.state == FLY_IN) {
            // A careful climb off the pad, then cruise, slowing for the approach.
            v.alt = Math.min(1, v.alt + dt * 0.15f);
            gx = v.tx;
            gy = v.ty;
            float d = (float) Math.hypot(gx - v.x, gy - v.y);
            speed = 15 + 95 * v.alt * v.alt;
            if (v.alt < 0.35f) {
                // (Straight up first: no skimming across the rooftops.)
                gx = v.x;
                gy = v.y;
            }
            if (v.kind == K_RESCUE_HELI && d < 150) {
                v.state = HOVER;
                v.timer = 0;
                w.dispatch.say(Dispatch.WHO_POLICE, null, "Rescue " + v.number + ": Over " + (v.place != null ? v.place : "the scene")
                        + (v.pickup ? ". Coming in for the pickup, winch going down." : ". Coming into the hover, team going down on the winch."), v.x, v.y);
            } else if (v.kind != K_POLICE_HELI && v.kind != K_RESCUE_HELI && v.troops > 0 && d < 220) {
                // Air assault: put the troops down first, clear of the dead.
                v.state = DROP;
                float[] lz = landingZone(v.tx, v.ty);
                v.homeX = lz[0];
                v.homeY = lz[1];
            } else if (d < 170 && v.kind != K_RESCUE_HELI) {
                v.state = CIRCLE;
                v.circle = (float) Math.atan2(v.y - v.ty, v.x - v.tx);
                v.hold = 0;
                if (v.kind == K_POLICE_HELI) {
                    v.timer = 90;
                    w.dispatch.say(Dispatch.WHO_POLICE, null, "Police helicopter: Over " + (v.place != null ? v.place : "the scene")
                            + ". Marksman at the door, spotlight on. We'll call them out for you.", v.x, v.y);
                } else {
                    v.timer = v.escort != null ? 600 : 60;
                    w.dispatch.say(Dispatch.WHO_MILITARY, null, "Air " + v.number + ": " + (v.escort != null ? "With the convoy, flying cover."
                            : "On station over " + (v.place != null ? v.place : "the target") + ". Door gunners clear to engage."), v.x, v.y);
                }
            }
        } else if (v.state == DROP) {
            // Down low over the landing zone, steady, while the troops jump out.
            gx = v.homeX;
            gy = v.homeY;
            speed = 45;
            float d = (float) Math.hypot(gx - v.x, gy - v.y);
            if (d < 25) v.alt += (0.12f - v.alt) * Math.min(1, dt * 0.9f);
            if (d < 6 && v.alt < 0.2f && v.speed < 8) {
                unloadTroops(v);
                v.state = CIRCLE;
                v.circle = (float) Math.atan2(v.y - v.ty, v.x - v.tx);
                v.hold = 0;
                v.timer = 60;
            }
        } else if (v.state == HOVER) {
            // Right over them, low, holding steady while the team works.
            gx = v.tx;
            gy = v.ty;
            speed = 40;
            face = v.angle;
            v.alt += (0.5f - v.alt) * Math.min(1, dt * 0.8f);
            int step = Math.hypot(gx - v.x, gy - v.y) < 8 && v.speed < 10 ? w.rescueStep(v, dt) : 0;
            if (step == 1) {
                // The team's down: back to base to wait for their call.
                v.state = FLY_OUT;
                v.standby = true;
                City.Facility h = city.nearestFacility(City.FACILITY_HOSPITAL, v.x, v.y);
                boolean lift = v.passengers > 0 && h != null;
                v.homeX = lift ? h.x : v.baseX;
                v.homeY = lift ? h.y : v.baseY;
                w.dispatch.say(Dispatch.WHO_POLICE, null, "Rescue " + v.number + ": Team's on the ground. "
                        + (lift ? "Got the lookout off the tower, taking them to the hospital, then back to base." : "Heading back to base.")
                        + " Call us when you need us.", v.x, v.y);
            } else if (step == 2) {
                v.state = FLY_OUT;
                v.standby = false;
                City.Facility h = city.nearestFacility(City.FACILITY_HOSPITAL, v.x, v.y);
                if (v.passengers > 0 && h != null) {
                    v.homeX = h.x;
                    v.homeY = h.y;
                } else {
                    v.homeX = v.baseX;
                    v.homeY = v.baseY;
                }
                w.dispatch.say(Dispatch.WHO_POLICE, null, "Rescue " + v.number + ": Everyone's aboard. "
                        + (v.passengers > 0 ? v.passengers + " survivor" + (v.passengers == 1 ? "" : "s") + ", heading for the hospital." : "Nobody left to bring out. Returning."),
                        v.x, v.y);
            }
        } else if (v.state == STANDBY) {
            // Waiting on the pad, rotor turning slowly, for the team to call.
            gx = v.homeX;
            gy = v.homeY;
            speed = 12;
            v.alt = Math.max(0, v.alt - dt * 0.2f);
            if (w.rescueCalled(v)) {
                v.state = FLY_IN;
                v.pickup = true;
                v.standby = false;
            }
        } else if (v.state == CIRCLE) {
            v.timer -= dt;
            // Not a tight circle: it holds a hover off to one side with the door towards the fighting, then
            // shifts a sixth of the way round and holds again.
            v.hold -= dt;
            if (v.hold <= 0) {
                v.hold = 7 + w.rnd.nextFloat() * 4;
                v.circle += v.escort != null ? 0 : 1.05f;
            }
            float r = v.escort != null ? 70 : v.kind == K_POLICE_HELI ? 110 : 140;
            gx = v.tx + (float) Math.cos(v.circle) * r;
            gy = v.ty + (float) Math.sin(v.circle) * r;
            speed = v.escort != null ? 75 : 45;
            float d = (float) Math.hypot(gx - v.x, gy - v.y);
            // Side-on to the target (the door gunner's side), sliding sideways from one hover to the next
            // rather than swinging the nose round each time.
            if (v.escort == null && d < 200) face = (float) Math.atan2(v.ty - v.y, v.tx - v.x) + (float) Math.PI / 2;
            v.alt += (0.75f - v.alt) * Math.min(1, dt);
            // Door gunners (the police helicopter's marksman takes careful single shots).
            v.gunCd -= dt;
            if (v.kind == K_POLICE_HELI) {
                if (v.gunCd <= 0) {
                    Entity z = w.nearestZombie(v.x, v.y, 220);
                    v.gunCd = z != null ? 1.3f : 0.5f;
                    if (z != null) w.marksmanShot(v.x, v.y, z);
                }
            } else if (v.gunCd <= 0) {
                Entity z = w.nearestZombie(v.x, v.y, 210);
                if (z != null) {
                    v.burst++;
                    v.gunCd = v.burst % 6 == 0 ? 1.4f : 0.13f;
                    w.airShot(v.x, v.y, z);
                } else {
                    v.gunCd = 0.5f;
                }
            }
            if (v.timer <= 0) {
                v.state = FLY_OUT;
                v.escort = null;
                v.homeX = v.baseX;
                v.homeY = v.baseY;
                if (v.kind == K_POLICE_HELI)
                    w.dispatch.say(Dispatch.WHO_POLICE, null, "Police helicopter: Low on fuel, heading back to the pad.", v.x, v.y);
                else w.dispatch.say(Dispatch.WHO_MILITARY, null, "Air " + v.number + ": Winchester, returning to base to rearm.", v.x, v.y);
            }
        } else if (v.state == FLY_OUT) {
            v.alt += (1 - v.alt) * Math.min(1, dt);
            gx = v.homeX;
            gy = v.homeY;
            speed = 110;
            float d = (float) Math.hypot(gx - v.x, gy - v.y);
            if (d < 6 && v.speed < 12) {
                if (v.kind == K_RESCUE_HELI && v.passengers > 0) {
                    // Set them down at the hospital, then home.
                    w.dropRescued(v);
                    v.homeX = v.baseX;
                    v.homeY = v.baseY;
                    return false;
                }
                if (v.padAt == null) return true;
                v.state = LAND;
            }
        } else {
            // Settle onto the pad.
            gx = v.homeX;
            gy = v.homeY;
            speed = 8;
            face = v.angle;
            v.alt -= dt * 0.2f;
            if (v.alt <= 0) {
                if (v.padAt == null) return true;
                if (v.kind == K_RESCUE_HELI && v.standby) {
                    v.alt = 0;
                    v.state = STANDBY;
                    return false;
                }
                landed(v);
                return false;
            }
        }
        flyTo(v, gx, gy, speed, face, dt);
        return false;
    }

    /**
     * A helicopter moves any way it likes, not just nose first: it accelerates towards where it wants to be,
     * slows to arrive there, and can hang still over a spot. The nose follows the way it's going (or points
     * where it's told to while it hovers), and it banks into each change of course.
     */
    private void flyTo(Vehicle v, float gx, float gy, float speed, float face, float dt) {
        float dx = gx - v.x, dy = gy - v.y, d = (float) Math.sqrt(dx * dx + dy * dy);
        float acc = 32;
        float want = d < 0.5f ? 0 : Math.min(speed, (float) Math.sqrt(2 * acc * 0.8f * d));
        float wvx = d < 0.5f ? 0 : dx / d * want, wvy = d < 0.5f ? 0 : dy / d * want;
        float ax = wvx - v.hvx, ay = wvy - v.hvy, al = (float) Math.sqrt(ax * ax + ay * ay), max = acc * dt;
        if (al > max) {
            ax *= max / al;
            ay *= max / al;
        }
        v.hvx += ax;
        v.hvy += ay;
        v.x += v.hvx * dt;
        v.y += v.hvy * dt;
        v.speed = (float) Math.sqrt(v.hvx * v.hvx + v.hvy * v.hvy);
        float heading = !Float.isNaN(face) ? face : v.speed > 12 ? (float) Math.atan2(v.hvy, v.hvx) : v.angle;
        float diff = heading - v.angle;
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        v.angle += Math.max(-dt * 0.9f, Math.min(dt * 0.9f, diff));
        // Bank with the sideways push.
        float side = (-ax * (float) Math.sin(v.angle) + ay * (float) Math.cos(v.angle)) / Math.max(dt, 0.001f);
        v.bank += (Math.max(-1.2f, Math.min(1.2f, side * 0.03f)) - v.bank) * Math.min(1, dt * 3);
    }

    /** Somewhere open to put troops down near (x, y), a little way back from the nearest of the dead. */
    private float[] landingZone(float x, float y) {
        Entity z = w.nearestZombie(x, y, 300);
        float ax = 0, ay = 1;
        if (z != null) {
            float d = (float) Math.hypot(x - z.x, y - z.y) + 0.01f;
            ax = (x - z.x) / d;
            ay = (y - z.y) / d;
        }
        for (float back = 90; back < 260; back += 30) {
            float[] p = city.findWalkable(x + ax * back, y + ay * back);
            if (p != null && w.countZombiesNear(p[0], p[1], 50) == 0) return p;
        }
        float[] p = city.findWalkable(x, y);
        return p != null ? p : new float[]{x, y};
    }

    /** The troops jump down: everyone aboard but the pilot and two door gunners, off to the fighting. */
    private void unloadTroops(Vehicle v) {
        Entity pilot = livePilot(v);
        int keep = 2;
        Entity first = null;
        for (int i = v.crew.size() - 1; i >= 0; i--) {
            Entity e = v.crew.get(i);
            if (e == pilot) continue;
            if (keep > 0) {
                keep--;
                continue;
            }
            v.crew.remove(i);
            float a = v.angle + (i % 2 == 0 ? 1.57f : -1.57f);
            if (!w.release(e, v.x + (float) Math.cos(a) * 12, v.y + (float) Math.sin(a) * 12)) continue;
            e.rig = null;
            if (v.incident != null && !v.incident.resolved) {
                e.task = Dispatch.T_RESPOND;
                e.incident = v.incident;
                e.onScene = false;
            } else {
                e.fear = 15;
                e.threatX = v.tx;
                e.threatY = v.ty;
            }
            if (first == null) first = e;
        }
        v.troops = 0;
        if (first != null)
            w.dispatch.say(Dispatch.WHO_MILITARY, first, "Boots on the ground at " + (v.place != null ? v.place : "the LZ") + ". Moving up.", v.x, v.y);
    }

    // ------------------------------------------------------------------ tanks and ambulances

    private boolean updateTank(Vehicle v, float dt) {
        v.cannonCd -= dt;
        v.gunCd -= dt;
        if (v.state == WAIT) {
            v.timer -= dt;
            if (v.timer <= 0) v.state = DRIVE;
            return false;
        }
        // The turret tracks the nearest zombie; the cannon fires at crowds, the coax gun at stragglers.
        // Only what the crew can see: no shooting through buildings.
        Entity z = w.nearestVisibleZombie(v.x, v.y, 260);
        float aim = z != null ? (float) Math.atan2(z.y - v.y, z.x - v.x) : v.angle;
        v.turret = turnTo(v.turret, aim, dt * 1.8f);
        if (z != null) {
            float off = Math.abs(aim - v.turret);
            if (v.cannonCd <= 0 && off < 0.15f && w.countZombiesNear(z.x, z.y, 40) >= 3 && !w.peopleNear(z.x, z.y, 55)
                    && Math.hypot(z.x - v.x, z.y - v.y) > 60) {
                v.cannonCd = 3.5f;
                w.tankShell(v.x + (float) Math.cos(v.turret) * 14, v.y + (float) Math.sin(v.turret) * 14, z.x, z.y);
            } else if (v.gunCd <= 0 && Math.hypot(z.x - v.x, z.y - v.y) < 160) {
                v.gunCd = 0.15f;
                w.airShot(v.x, v.y, z);
            }
        }
        if (v.soundCd <= 0 && v.state != ENGAGE) {
            v.soundCd = 1.1f;
            w.emit(Sfx.ENGINE, v.x, v.y);
        }
        if (v.state == DRIVE) {
            v.stuckTimer += dt;
            boolean moving = driveStep(v, dt, 45, 1);
            if (moving) w.runOver(v.x, v.y, 10, Math.max(v.speed, 25), v.angle);
            // (A tank coming in from outside waits out a jam on the way in.)
            if (!moving || v.stuckTimer > (v.reinforcement ? 20 : 5)) {
                v.state = ENGAGE;
                v.timer = 70;
                v.speed = 0;
            }
            return false;
        }
        if (v.state == ENGAGE) {
            v.speed = 0;
            v.timer -= dt;
            v.idleTimer = z == null ? v.idleTimer + dt : 0;
            if (v.timer <= 0 || v.idleTimer > 15) {
                v.state = RETURN;
                w.dispatch.say(Dispatch.WHO_MILITARY, null, "Military: Armor pulling back to base.", v.x, v.y);
                return !route(v, v.homeX, v.homeY);
            }
            return false;
        }
        v.stuckTimer += dt;
        boolean moving = driveStep(v, dt, 45, 1);
        if (moving) w.runOver(v.x, v.y, 10, Math.max(v.speed, 25), v.angle);
        return !moving || v.stuckTimer > 8;
    }

    private boolean updateAmbulance(Vehicle v, float dt) {
        if (v.state == WAIT) {
            v.timer -= dt;
            if (v.timer <= 0) {
                v.state = DRIVE;
                w.emit(Sfx.AMB_SIREN, v.x, v.y);
            }
            return false;
        }
        Entity p = v.patient;
        if (v.state == DRIVE || v.state == LOAD) {
            boolean gone = p == null || (p.dead && !v.riders.contains(p)) || p.isZombie();
            if (!v.riders.isEmpty()) {
                // Patient on board: back to the hospital.
                v.state = RETURN;
                w.emit(Sfx.AMB_SIREN, v.x, v.y);
                return !route(v, v.homeX, v.homeY);
            }
            if (gone) {
                v.patient = null;
                v.state = RETURN;
                return !route(v, v.homeX, v.homeY);
            }
            float d = (float) Math.hypot(p.x - v.x, p.y - v.y);
            if (v.state == DRIVE) {
                v.stuckTimer += dt;
                if (v.soundCd <= 0) {
                    v.soundCd = 3.5f;
                    w.emit(Sfx.AMB_SIREN, v.x, v.y);
                }
                boolean moving = driveStep(v, dt, 90, carAhead(v) ? 0.5f : 1f);
                if (moving) hit(v, w.runOver(v.x, v.y, 8, v.speed, v.angle));
                if (d < 70 || !moving || v.stuckTimer > 4) {
                    v.state = LOAD;
                    v.timer = 12;
                    v.waitTimer = 12;
                    w.callRide(p, v);
                }
            } else {
                v.speed = Math.max(0, v.speed - dt * 150);
                v.timer -= dt;
                if (v.timer <= 0 || w.countZombiesNear(v.x, v.y, 30) >= 2) {
                    if (p.ride == v) p.ride = null;
                    v.patient = null;
                    v.state = RETURN;
                    return !route(v, v.homeX, v.homeY);
                }
            }
            return false;
        }
        v.stuckTimer += dt;
        boolean moving = driveStep(v, dt, 90, carAhead(v) ? 0.5f : 1f);
        if (moving) hit(v, w.runOver(v.x, v.y, 8, v.speed, v.angle));
        if (!moving || v.stuckTimer > 6) {
            // At the hospital: the patient goes inside to be treated.
            for (int i = 0; i < v.riders.size(); i++) {
                Entity e = v.riders.get(i);
                if (w.release(e, v.x, v.y)) {
                    e.task = Dispatch.T_HEAL;
                    e.hp = Math.max(e.hp, e.maxHp * 0.5f);
                }
            }
            v.riders.clear();
            return true;
        }
        return false;
    }
}
