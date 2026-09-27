package com.instantaxtion.zombiesandbox;

import java.util.ArrayList;

/**
 * Vehicles: police cruisers and army trucks that drive reinforcements from the precinct or base along the
 * roads (running over zombies on the way), fire engines, ordinary traffic and the helicopter that flies in
 * for air support. Cars take damage from zombies, crashes and blasts, and break down when it is too much.
 */
final class Fleet {
    static final int CRUISER = 0, TRUCK = 1, HELI = 2, CAR = 3, FIRE_ENGINE = 4;
    private static final int WAIT = 0, DRIVE = 1, RETURN = 2, FLY_IN = 3, CIRCLE = 4, FLY_OUT = 5, CRUISE = 6,
            ABANDONED = 7, SPRAY = 8;
    private static final int[] CAR_COLORS = {0xFFB03A2E, 0xFF2E5FB0, 0xFFE0E0E0, 0xFF222428, 0xFFD4A21C, 0xFF3C8A4E,
            0xFF8A8F96, 0xFF6B2E8A, 0xFFE07A2E};
    private static final float[] MAX_HP = {160, 260, 1, 100, 240};
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

        float length() {
            return type == TRUCK || type == FIRE_ENGINE ? 9.5f : 8f;
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

    /** The helicopter: flies from the helipad (or the map edge) and circles the target with a door gunner. */
    void sendHeli(float fromX, float fromY, float toX, float toY, String place) {
        Vehicle v = make(HELI);
        v.state = FLY_IN;
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
    private float trafficTimer;

    void update(float dt) {
        // Keep the streets busy while the city is still calm; clear old wrecks.
        trafficTimer -= dt;
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
            else if (v.broken) done = updateWreck(v);
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
        if (v.type == HELI || amount <= 0) return;
        v.hp -= amount;
        if (v.broken) {
            if (!v.burnt && (blast || v.hp < -v.maxHp * 0.5f)) burn(v);
            return;
        }
        if (v.hp > 0) return;
        v.broken = true;
        v.parked = true;
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
        }
        dropRiders(v, true);
        if (v.type == FIRE_ENGINE)
            w.dispatch.say(Dispatch.WHO_FIRE, null, "Fire Dept: Engine " + v.number + " is out of action on "
                    + city.placeName(v.x, v.y) + ".", v.x, v.y);
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
            if (a.type == HELI) continue;
            for (int j = i + 1; j < n; j++) {
                Vehicle b = vehicles.get(j);
                if (b.type == HELI) continue;
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
                    a.speed *= 0.3f;
                    b.speed *= 0.3f;
                    float cx = (a.x + b.x) / 2, cy = (a.y + b.y) / 2;
                    w.emit(Sfx.THUD, cx, cy);
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
            if (o == v || o.type == HELI) continue;
            float dx = o.x - v.x, dy = o.y - v.y;
            float ahead = dx * fx + dy * fy, side = Math.abs(dx * -fy + dy * fx);
            if (ahead > 0 && ahead < 24 && side < 8) return true;
        }
        return false;
    }

    private boolean updateWreck(Vehicle v) {
        v.speed = 0;
        return false;
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
        boolean arrived = !driveStep(v, dt, v.type == CRUISER ? 105 : 80, carAhead(v) ? 0.5f : 1f);
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
        boolean person = w.personAhead(v.x, v.y, v.angle);
        if ((person && v.timer < 2.5f) || (v.waitTimer > 0 && !zombiesClose)) {
            // Brake for pedestrians (then creep through, nudging them aside) or wait for someone to get in.
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
            getOut(v, true);
            dropRiders(v, true);
        }
        return false;
    }

    /** Called by World when someone climbs in. */
    void boarded(Vehicle v, Entity e) {
        v.riders.add(e);
        if (v.rescue) return;
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
            v.soundCd = 0.6f;
            w.emit(Sfx.ROTOR, v.x, v.y);
        }
        float gx, gy, speed = 130;
        if (v.state == FLY_IN) {
            gx = v.tx;
            gy = v.ty;
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
        } else {
            gx = v.homeX;
            gy = v.homeY;
            if (Math.hypot(gx - v.x, gy - v.y) < 20) return true;
        }
        float want = (float) Math.atan2(gy - v.y, gx - v.x);
        float diff = want - v.angle;
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        v.angle += Math.max(-dt * 2.5f, Math.min(dt * 2.5f, diff));
        v.speed += (speed - v.speed) * Math.min(1, dt * 1.5f);
        v.x += (float) Math.cos(v.angle) * v.speed * dt;
        v.y += (float) Math.sin(v.angle) * v.speed * dt;
        return false;
    }
}
