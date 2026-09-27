package com.instantaxtion.zombiesandbox;

import java.util.ArrayList;

/**
 * Vehicles: police cruisers and army trucks that drive reinforcements from the precinct or base along the
 * roads (running over zombies on the way), fire engines, ordinary traffic and the helicopter that flies in
 * for air support. Cars take damage from zombies, crashes and blasts, and break down when it is too much.
 */
final class Fleet {
    static final int CRUISER = 0, TRUCK = 1, HELI = 2, CAR = 3, FIRE_ENGINE = 4, TANK = 5, AMBULANCE = 6, TRAIN = 7;
    /** A train: a locomotive and three carriages, this long in world units. */
    static final float TRAIN_LENGTH = 136;
    private static final int WAIT = 0, DRIVE = 1, RETURN = 2, FLY_IN = 3, CIRCLE = 4, FLY_OUT = 5, CRUISE = 6,
            ABANDONED = 7, SPRAY = 8, SPOOL = 9, LAND = 10, ENGAGE = 11, LOAD = 12;
    private static final int[] CAR_COLORS = {0xFFB03A2E, 0xFF2E5FB0, 0xFFE0E0E0, 0xFF222428, 0xFFD4A21C, 0xFF3C8A4E,
            0xFF8A8F96, 0xFF6B2E8A, 0xFFE07A2E};
    private static final float[] MAX_HP = {160, 260, 1, 100, 240, 700, 160, 1};
    /** Most people a car will squeeze in. */
    static final int SEATS = 4;

    static final class Vehicle {
        int type, state;
        float x, y, angle, speed, timer, stuckTimer, lastDist, gunCd, soundCd, circle;
        float tx, ty, homeX, homeY;
        int[] field;
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
        /** A stopped car rolls over to the kerb. */
        float pullX, pullY;
        boolean pulling;

        float length() {
            return type == TANK ? 11f : type == TRUCK || type == FIRE_ENGINE || type == AMBULANCE ? 9.5f : 8f;
        }
    }

    /** A short line about what a vehicle is doing, shown above it on the map. */
    String status(Vehicle v) {
        String where = v.place != null ? v.place : null;
        switch (v.type) {
            case HELI:
                switch (v.state) {
                    case SPOOL: return "Air 1: Starting up";
                    case FLY_IN: return v.alt < 0.9f ? "Air 1: Taking off" : "Air 1: En route" + (where != null ? " to " + where : "");
                    case CIRCLE: return "Air 1: Door gunner engaging (" + (int) v.timer + "s)";
                    case LAND: return "Air 1: Landing";
                    default: return "Air 1: Returning to base";
                }
            case TRAIN:
                return "Train: Passing through";
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
                String who = v.type == CRUISER ? "Police car" : "Army truck";
                if (v.broken) return who + ": Wrecked";
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
    private int engineCount;

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

    /** Ordinary traffic: cars driving around town between random spots on the roads. */
    void spawnTraffic(int n) {
        for (int i = 0; i < n; i++) {
            float[] start = randomRoad();
            if (start == null) return;
            Vehicle v = make(CAR);
            v.state = CRUISE;
            v.x = start[0];
            v.y = start[1];
            v.color = CAR_COLORS[w.rnd.nextInt(CAR_COLORS.length)];
            v.angle = w.rnd.nextInt(4) * (float) Math.PI / 2;
            if (!newDestination(v)) continue;
            vehicles.add(v);
        }
    }

    private float[] randomRoad() {
        for (int k = 0; k < 200; k++) {
            int tx = w.rnd.nextInt(city.w), ty = w.rnd.nextInt(city.h);
            if (city.tiles[ty * city.w + tx] == City.ROAD)
                return new float[]{tx * City.T + City.T / 2f, ty * City.T + City.T / 2f};
        }
        return null;
    }

    private boolean newDestination(Vehicle v) {
        for (int tries = 0; tries < 5; tries++) {
            float[] d = randomRoad();
            if (d == null || Math.hypot(d[0] - v.x, d[1] - v.y) < 400) continue;
            if (route(v, d[0], d[1])) return true;
        }
        return false;
    }

    /** Points the vehicle at a new goal. Returns false if it can't be reached by road. */
    private boolean route(Vehicle v, float x, float y) {
        int[] field = new int[city.w * city.h];
        if (!city.driveField(field, x, y) || field[city.tileIndex(v.x, v.y)] >= City.FAR) return false;
        v.field = field;
        v.tx = x;
        v.ty = y;
        v.lastDist = Float.MAX_VALUE;
        v.stuckTimer = 0;
        return true;
    }

    int movingTraffic() {
        int n = 0;
        for (int i = 0; i < vehicles.size(); i++) if (vehicles.get(i).type == CAR && !vehicles.get(i).parked) n++;
        return n;
    }

    boolean heliBusy() {
        for (int i = 0; i < vehicles.size(); i++) if (vehicles.get(i).type == HELI) return true;
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
        float[] start = city.nearestDrivable(fromX, fromY);
        if (start == null) return false;
        int[] field = new int[city.w * city.h];
        if (!city.driveField(field, toX, toY) || field[city.tileIndex(start[0], start[1])] >= City.FAR) return false;
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
            vehicles.add(v);
        }
        return true;
    }

    /** A fire engine leaves the station for a fire. Returns null if it can't get there. */
    Vehicle sendFireEngine(City.Facility station, World.Fire f) {
        float[] start = city.nearestDrivable(station.x, station.y);
        if (start == null) return null;
        Vehicle v = make(FIRE_ENGINE);
        v.x = start[0];
        v.y = start[1];
        if (!route(v, f.x, f.y)) return null;
        v.state = WAIT;
        v.timer = 1.2f;
        v.homeX = station.gateX;
        v.homeY = station.gateY;
        v.fire = f;
        v.number = ++engineCount;
        v.angle = (float) Math.atan2(f.y - v.y, f.x - v.x);
        vehicles.add(v);
        return v;
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
        v.color = CAR_COLORS[w.rnd.nextInt(CAR_COLORS.length)];
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
        vehicles.add(v);
        return v;
    }

    /** The helicopter: flies from the helipad (or the map edge) and circles the target with a door gunner. */
    void sendHeli(float fromX, float fromY, float toX, float toY, String place) {
        Vehicle v = make(HELI);
        float[] hp = city.nearestHelipad(fromX, fromY);
        v.pad = hp != null && Math.hypot(hp[0] - fromX, hp[1] - fromY) < 10;
        v.state = v.pad ? SPOOL : FLY_IN;
        v.alt = v.pad ? 0 : 1;
        v.timer = 2.5f;
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
    private float trafficTimer, trainTimer = 20;

    /** A train is on the line near this car's crossing: wait. */
    private boolean trainComing(Vehicle v) {
        if (city.railY0 < 0) return false;
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

    private boolean updateTrain(Vehicle v, float dt) {
        float dir = (float) Math.cos(v.angle);
        v.x += dir * v.speed * dt;
        // Anything on the line gets hit: zombies go flying, people are knocked aside, cars are wrecked.
        for (float k = 0; k < TRAIN_LENGTH; k += 16) w.runOver(v.x - dir * k, v.y, 9, v.speed, v.angle);
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle o = vehicles.get(i);
            if (o == v || o.type == HELI || o.type == TRAIN || o.broken) continue;
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
        // Keep the streets busy while the city is still calm; clear old wrecks.
        trafficTimer -= dt;
        trainTimer -= dt;
        if (trainTimer <= 0 && city.railY0 >= 0) {
            trainTimer = 70 + w.rnd.nextFloat() * 50;
            spawnTrain();
        }
        if (trafficTimer <= 0) {
            trafficTimer = 6;
            if (movingTraffic() < trafficTarget && w.zombieCount() < 10) spawnTraffic(1);
            int parked = 0;
            for (int i = vehicles.size() - 1; i >= 0; i--)
                if (vehicles.get(i).parked && vehicles.get(i).riders.isEmpty() && ++parked > 25) vehicles.remove(i);
        }
        for (int i = vehicles.size() - 1; i >= 0; i--) {
            Vehicle v = vehicles.get(i);
            v.anim += dt;
            v.soundCd -= dt;
            v.crashCd -= dt;
            boolean done;
            if (v.type == HELI) done = updateHeli(v, dt);
            else if (v.type == TRAIN) done = updateTrain(v, dt);
            else if (v.broken || v.parked) done = updateWreck(v, dt);
            else if (v.type == TANK) done = updateTank(v, dt);
            else if (v.type == AMBULANCE) done = updateAmbulance(v, dt);
            else if (v.type == CAR) done = updateTraffic(v, dt);
            else if (v.type == FIRE_ENGINE) done = updateFireEngine(v, dt);
            else done = updateCar(v, dt);
            if (done) {
                if (v.fire != null) v.fire.engine = null;
                vehicles.remove(i);
                continue;
            }
            if (v.type != HELI && v.hp < v.maxHp * 0.5f) smoke(v, dt);
        }
        collide();
    }

    // ------------------------------------------------------------------ damage

    /** Hurts a vehicle; at zero it breaks down and everyone inside gets out. */
    void damage(Vehicle v, float amount, boolean blast) {
        if (v.type == HELI || v.type == TRAIN || amount <= 0) return;
        v.hp -= amount;
        if (v.broken) {
            if (!v.burnt && (blast || v.hp < -v.maxHp * 0.5f)) burn(v);
            return;
        }
        if (v.hp > 0) return;
        v.broken = true;
        v.parked = true;
        w.carsWrecked++;
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
        for (int i = 0, n = vehicles.size(); i < n; i++) {
            Vehicle a = vehicles.get(i);
            if (a.type == HELI || a.type == TRAIN) continue;
            for (int j = i + 1; j < n; j++) {
                Vehicle b = vehicles.get(j);
                if (b.type == HELI || b.type == TRAIN) continue;
                float dx = b.x - a.x, dy = b.y - a.y, reach = (a.length() + b.length()) * 0.62f;
                float d2 = dx * dx + dy * dy;
                if (d2 >= reach * reach) continue;
                float d = (float) Math.sqrt(d2) + 0.001f, nx = dx / d, ny = dy / d;
                // Closing speed along the line between them.
                float va = a.speed * ((float) Math.cos(a.angle) * nx + (float) Math.sin(a.angle) * ny);
                float vb = b.speed * ((float) Math.cos(b.angle) * nx + (float) Math.sin(b.angle) * ny);
                float closing = va - vb;
                float push = (reach - d) * 0.5f;
                a.x -= nx * push;
                a.y -= ny * push;
                b.x += nx * push;
                b.y += ny * push;
                if (closing > 25 && a.crashCd <= 0 && b.crashCd <= 0) {
                    a.crashCd = b.crashCd = 0.6f;
                    float dmg = closing * 0.35f;
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

    /** Someone else's car close in front: time to brake. */
    private boolean carAhead(Vehicle v) {
        float fx = (float) Math.cos(v.angle), fy = (float) Math.sin(v.angle);
        for (int i = 0, n = vehicles.size(); i < n; i++) {
            Vehicle o = vehicles.get(i);
            if (o == v || o.type == HELI || o.type == TRAIN) continue;
            float dx = o.x - v.x, dy = o.y - v.y;
            float ahead = dx * fx + dy * fy, side = Math.abs(dx * -fy + dy * fx);
            if (ahead > 0 && ahead < 24 && side < 8) return true;
        }
        return false;
    }

    private boolean updateWreck(Vehicle v, float dt) {
        v.speed = 0;
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
        if (v.state == WAIT) {
            v.timer -= dt;
            if (v.timer <= 0) {
                v.state = DRIVE;
                if (v.type == CRUISER) w.emit(Sfx.SIREN, v.x, v.y);
            }
            return false;
        }
        v.stuckTimer += dt;
        if (trainComing(v)) {
            v.speed = Math.max(0, v.speed - dt * 150);
            return false;
        }
        boolean arrived = !driveStep(v, dt, v.type == CRUISER ? 105 : 80, carAhead(v) ? 0.5f : 1f);
        // Don't drive into a horde: stop short and let the troops out.
        if (v.state == DRIVE && v.passengers > 0) {
            float ax = v.x + (float) Math.cos(v.angle) * 60, ay = v.y + (float) Math.sin(v.angle) * 60;
            if (w.countZombiesNear(ax, ay, 60) >= 4 || w.countZombiesNear(v.x, v.y, 40) >= 3) {
                arrived = true;
                v.speed *= 0.3f;
            }
        }
        // Give up and stop where we are if the car hasn't made progress for a while.
        if (v.stuckTimer > 4) arrived = true;
        if (arrived) {
            if (v.state == DRIVE) {
                unload(v, true);
                v.passengers = 0;
                v.state = RETURN;
                v.speed = 0;
                return !route(v, v.homeX, v.homeY);
            }
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
        int ti = city.tileIndex(v.x, v.y);
        int cur = v.field[ti];
        if (cur <= 4 || cur >= City.FAR) return false;
        int tx = ti % city.w, ty = ti / city.w, best = cur, bx = tx, by = ty;
        for (int k = 0; k < 4; k++) {
            int nx = tx + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = ty + (k == 2 ? 1 : k == 3 ? -1 : 0);
            if (nx < 0 || ny < 0 || nx >= city.w || ny >= city.h) continue;
            int d = v.field[ny * city.w + nx];
            if (d < best) {
                best = d;
                bx = nx;
                by = ny;
            }
        }
        float gx = bx * City.T + City.T / 2f, gy = by * City.T + City.T / 2f;
        // Keep right so cars going the other way pass instead of meeting head on.
        float ddx = bx - tx, ddy = by - ty;
        gx += -ddy * 4.5f;
        gy += ddx * 4.5f;
        float want = (float) Math.atan2(gy - v.y, gx - v.x);
        float diff = want - v.angle;
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        v.angle += Math.max(-dt * 5, Math.min(dt * 5, diff));
        float target = max * throttle * (0.35f + 0.65f * Math.max(0, (float) Math.cos(diff)));
        // A badly damaged car limps along.
        if (v.hp < v.maxHp * 0.3f) target *= 0.6f;
        v.speed += (target - v.speed) * Math.min(1, dt * 2.5f);
        v.x += (float) Math.cos(v.angle) * v.speed * dt;
        v.y += (float) Math.sin(v.angle) * v.speed * dt;
        if (cur < v.lastDist - 1) {
            v.lastDist = cur;
            v.stuckTimer = 0;
        }
        return true;
    }

    /**
     * Traffic: drives around, brakes for people and other cars, ploughs through zombies, stops for people
     * waving it down, and gets abandoned when the driver is surrounded.
     */
    private boolean updateTraffic(Vehicle v, float dt) {
        if (v.parked) return false;
        boolean zombiesClose = w.countZombiesNear(v.x, v.y, 30) >= 2;
        v.waitTimer -= dt;
        v.hailCd -= dt;
        // Someone is waving: pick them up if there's room.
        if (v.hailCd <= 0 && v.riders.size() < SEATS && !zombiesClose) {
            v.hailCd = 0.5f;
            if (w.hail(v)) v.waitTimer = 4;
        }
        v.timer += dt;
        boolean person = w.personAhead(v.x, v.y, v.angle) || trainComing(v);
        if ((person && v.timer < 2.5f) || (v.waitTimer > 0 && !zombiesClose) || trainComing(v)) {
            // Brake for pedestrians (then creep through, nudging them aside) or wait for someone to get in.
            if (v.speed > 45 && person) w.skid(v.x, v.y, v.angle, 6);
            v.speed = Math.max(0, v.speed - dt * 200);
            if (!person) v.timer = 0;
        } else {
            if (!person) v.timer = 0;
            v.stuckTimer += dt;
            float throttle = carAhead(v) && v.stuckTimer < 3 ? 0.15f : 1f;
            if (!driveStep(v, dt, zombiesClose ? 60 : 70, throttle)) {
                arrive(v);
                if (!newDestination(v)) v.stuckTimer = 99;
            } else {
                hit(v, w.runOver(v.x, v.y, 8, v.speed, v.angle));
                if (v.broken) return false;
            }
        }
        if (v.stuckTimer > 8 && !zombiesClose) {
            v.stuckTimer = 0;
            if (!newDestination(v)) {
                dropRiders(v, false);
                return true;
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

    private boolean updateFireEngine(Vehicle v, float dt) {
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
        // Too many zombies around the crew: pull out.
        if (v.state != RETURN && w.countZombiesNear(v.x, v.y, 30) >= 4) {
            v.spraying = false;
            if (v.fire != null) v.fire.engine = null;
            v.fire = null;
            v.state = RETURN;
            w.dispatch.say(Dispatch.WHO_FIRE, null, "Fire Dept: Engine " + v.number + " pulling back, too many of them!",
                    v.x, v.y);
            return !route(v, v.homeX, v.homeY);
        }
        if (v.state == SPRAY) {
            v.speed = 0;
            World.Fire f = v.fire;
            if (f == null || f.life <= 0 || !w.fires.contains(f)) {
                v.spraying = false;
                World.Fire next = w.nearestFire(v.x, v.y, 300);
                if (next != null) {
                    next.engine = v;
                    v.fire = next;
                    if (Math.hypot(next.x - v.x, next.y - v.y) < 70) return false;
                    if (route(v, next.x, next.y)) {
                        v.state = DRIVE;
                        return false;
                    }
                    next.engine = null;
                }
                v.fire = null;
                v.state = RETURN;
                w.dispatch.say(Dispatch.WHO_FIRE, null, "Fire Dept: Engine " + v.number + ", fire's out. Returning to station.",
                        v.x, v.y);
                return !route(v, v.homeX, v.homeY);
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
        boolean moving = driveStep(v, dt, 85, carAhead(v) ? 0.5f : 1f);
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
            }
            return false;
        }
        return !moving || v.stuckTimer > 6;
    }

    private static float turnTo(float from, float to, float amount) {
        float diff = to - from;
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        return from + Math.max(-amount, Math.min(amount, diff));
    }

    // ------------------------------------------------------------------ helicopter

    private boolean updateHeli(Vehicle v, float dt) {
        if (v.soundCd <= 0) {
            v.soundCd = v.state == SPOOL ? 0.9f : 0.6f;
            w.emit(Sfx.ROTOR, v.x, v.y);
        }
        // Rotor wash kicks up dust when it's low.
        if (v.alt < 0.6f && v.state != SPOOL || (v.state == SPOOL && v.timer < 1.5f)) {
            if (w.rnd.nextFloat() < dt * 25) {
                float a = w.rnd.nextFloat() * (float) Math.PI * 2;
                w.particle(v.x + (float) Math.cos(a) * 10, v.y + (float) Math.sin(a) * 10, (float) Math.cos(a) * 45,
                        (float) Math.sin(a) * 45, 0.8f, 2.5f, 0xFFB8AE98, World.P_SMOKE);
            }
        }
        if (v.state == SPOOL) {
            // Spinning up on the pad.
            v.timer -= dt;
            if (v.timer <= 0) v.state = FLY_IN;
            return false;
        }
        float gx, gy, speed = 130;
        if (v.state == FLY_IN) {
            v.alt = Math.min(1, v.alt + dt * 0.35f);
            gx = v.tx;
            gy = v.ty;
            // Climb before speeding off.
            speed = 20 + 110 * v.alt;
            if (Math.hypot(gx - v.x, gy - v.y) < 60) {
                v.state = CIRCLE;
                v.timer = 25;
                v.circle = (float) Math.atan2(v.y - v.ty, v.x - v.tx);
            }
        } else if (v.state == CIRCLE) {
            v.timer -= dt;
            v.circle += dt * 0.7f;
            gx = v.tx + (float) Math.cos(v.circle) * 80;
            gy = v.ty + (float) Math.sin(v.circle) * 80;
            speed = 70;
            // Drops a little lower to give the gunner a better shot.
            v.alt += (0.75f - v.alt) * Math.min(1, dt);
            // Door gunner.
            v.gunCd -= dt;
            if (v.gunCd <= 0) {
                Entity z = w.nearestZombie(v.x, v.y, 170);
                if (z != null) {
                    v.gunCd = 0.2f;
                    w.airShot(v.x, v.y, z);
                } else {
                    v.gunCd = 0.5f;
                }
            }
            if (v.timer <= 0) {
                v.state = FLY_OUT;
                w.dispatch.say(Dispatch.WHO_MILITARY, null, "Military: Air support is Winchester, returning to base.", v.x, v.y);
            }
        } else if (v.state == FLY_OUT) {
            v.alt += (1 - v.alt) * Math.min(1, dt);
            gx = v.homeX;
            gy = v.homeY;
            float d = (float) Math.hypot(gx - v.x, gy - v.y);
            speed = Math.min(130, 25 + d * 0.8f);
            if (d < 12) {
                if (!v.pad) return true;
                v.state = LAND;
            }
        } else {
            // Settle onto the pad, then shut down.
            gx = v.homeX;
            gy = v.homeY;
            speed = 8;
            v.alt -= dt * 0.4f;
            if (v.alt <= 0) return true;
        }
        float want = (float) Math.atan2(gy - v.y, gx - v.x);
        float diff = want - v.angle;
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        float turn = Math.max(-dt * 2.5f, Math.min(dt * 2.5f, diff));
        v.angle += turn;
        v.bank += (turn / Math.max(dt, 0.001f) * 0.25f - v.bank) * Math.min(1, dt * 3);
        v.speed += (speed - v.speed) * Math.min(1, dt * 1.5f);
        v.x += (float) Math.cos(v.angle) * v.speed * dt;
        v.y += (float) Math.sin(v.angle) * v.speed * dt;
        return false;
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
            if (!moving || v.stuckTimer > 5) {
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
