package com.instantaxtion.zombiesandbox;

import java.util.ArrayList;

/**
 * Vehicles: police cruisers and army trucks that drive reinforcements from the precinct or base along the
 * roads (running over zombies on the way), and the helicopter that flies in for air support.
 */
final class Fleet {
    static final int CRUISER = 0, TRUCK = 1, HELI = 2, CAR = 3;
    private static final int WAIT = 0, DRIVE = 1, RETURN = 2, FLY_IN = 3, CIRCLE = 4, FLY_OUT = 5, CRUISE = 6, ABANDONED = 7;
    private static final int[] CAR_COLORS = {0xFFB03A2E, 0xFF2E5FB0, 0xFFE0E0E0, 0xFF222428, 0xFFD4A21C, 0xFF3C8A4E,
            0xFF8A8F96, 0xFF6B2E8A, 0xFFE07A2E};

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
    }

    private final World w;
    private final City city;
    final ArrayList<Vehicle> vehicles = new ArrayList<Vehicle>();

    Fleet(World w) {
        this.w = w;
        this.city = w.city;
    }

    void clear() {
        vehicles.clear();
    }

    /** Ordinary traffic: cars driving around town between random spots on the roads. */
    void spawnTraffic(int n) {
        for (int i = 0; i < n; i++) {
            float[] start = randomRoad();
            if (start == null) return;
            Vehicle v = new Vehicle();
            v.type = CAR;
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
            int[] field = new int[city.w * city.h];
            if (!city.driveField(field, d[0], d[1]) || field[city.tileIndex(v.x, v.y)] >= City.FAR) continue;
            v.field = field;
            v.tx = d[0];
            v.ty = d[1];
            v.lastDist = Float.MAX_VALUE;
            v.stuckTimer = 0;
            return true;
        }
        return false;
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
            Vehicle v = new Vehicle();
            v.type = cops ? CRUISER : TRUCK;
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

    /** The helicopter: flies from the helipad (or the map edge) and circles the target with a door gunner. */
    void sendHeli(float fromX, float fromY, float toX, float toY, String place) {
        Vehicle v = new Vehicle();
        v.type = HELI;
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
                if (vehicles.get(i).parked && ++parked > 25) vehicles.remove(i);
        }
        for (int i = vehicles.size() - 1; i >= 0; i--) {
            Vehicle v = vehicles.get(i);
            v.anim += dt;
            v.soundCd -= dt;
            boolean done;
            if (v.type == HELI) done = updateHeli(v, dt);
            else if (v.type == CAR) done = updateTraffic(v, dt);
            else done = updateCar(v, dt);
            if (done) vehicles.remove(i);
        }
    }

    private boolean updateCar(Vehicle v, float dt) {
        if (v.state == WAIT) {
            v.timer -= dt;
            if (v.timer <= 0) {
                v.state = DRIVE;
                if (v.type == CRUISER) w.emit(Sfx.SIREN, v.x, v.y);
            }
            return false;
        }
        int ti = city.tileIndex(v.x, v.y);
        int cur = v.field[ti];
        boolean arrived = cur <= 4 || cur >= City.FAR;
        // Give up and stop where we are if the car hasn't made progress for a while.
        v.stuckTimer += dt;
        if (cur < v.lastDist - 1) {
            v.lastDist = cur;
            v.stuckTimer = 0;
        }
        if (v.stuckTimer > 4) arrived = true;
        if (arrived) {
            if (v.state == DRIVE) {
                unload(v);
                v.state = RETURN;
                v.speed = 0;
                v.stuckTimer = 0;
                v.lastDist = Float.MAX_VALUE;
                int[] back = new int[city.w * city.h];
                if (!city.driveField(back, v.homeX, v.homeY)) return true;
                v.field = back;
                return false;
            }
            return true;
        }
        // Head for the neighbouring tile that is cheapest to the goal.
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
        float want = (float) Math.atan2(gy - v.y, gx - v.x);
        float diff = want - v.angle;
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        v.angle += Math.max(-dt * 5, Math.min(dt * 5, diff));
        float max = v.type == CRUISER ? 105 : 80;
        float target = max * (0.35f + 0.65f * Math.max(0, (float) Math.cos(diff)));
        v.speed += (target - v.speed) * Math.min(1, dt * 2.5f);
        v.x += (float) Math.cos(v.angle) * v.speed * dt;
        v.y += (float) Math.sin(v.angle) * v.speed * dt;
        w.runOver(v.x, v.y, 8, v.speed, v.angle);
        if (v.type == CRUISER && v.soundCd <= 0 && v.state == DRIVE) {
            v.soundCd = 3.5f;
            w.emit(Sfx.SIREN, v.x, v.y);
        }
        return false;
    }

    /** One step of driving downhill in the vehicle's field. Returns false once it has arrived. */
    private boolean driveStep(Vehicle v, float dt, float max) {
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
        float want = (float) Math.atan2(gy - v.y, gx - v.x);
        float diff = want - v.angle;
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        v.angle += Math.max(-dt * 5, Math.min(dt * 5, diff));
        float target = max * (0.35f + 0.65f * Math.max(0, (float) Math.cos(diff)));
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
     * Traffic: drives around, brakes for people, ploughs through zombies, and gets abandoned when the
     * driver is surrounded.
     */
    private boolean updateTraffic(Vehicle v, float dt) {
        if (v.parked) return false;
        boolean zombiesClose = w.countZombiesNear(v.x, v.y, 30) >= 2;
        v.timer += dt;
        if (w.personAhead(v.x, v.y, v.angle) && v.timer < 2.5f) {
            // Brake for pedestrians, then creep through, nudging them aside.
            v.speed = Math.max(0, v.speed - dt * 200);
        } else {
            if (!w.personAhead(v.x, v.y, v.angle)) v.timer = 0;
            v.stuckTimer += dt;
            if (!driveStep(v, dt, zombiesClose ? 60 : 70)) {
                if (!newDestination(v)) v.stuckTimer = 99;
            } else {
                w.runOver(v.x, v.y, 8, v.speed, v.angle);
            }
        }
        if (v.stuckTimer > 8 && !zombiesClose) {
            v.stuckTimer = 0;
            if (!newDestination(v)) return true;
        }
        // Surrounded: the driver gets out and runs.
        if (zombiesClose && v.speed < 12) {
            v.parked = true;
            v.speed = 0;
            v.field = null;
            Entity driver = w.spawn(Entity.CIVILIAN, v.x + (float) Math.cos(v.angle + 1.57f) * 9,
                    v.y + (float) Math.sin(v.angle + 1.57f) * 9);
            if (driver != null) {
                driver.fleeTimer = 3;
                driver.threatX = v.x;
                driver.threatY = v.y;
            }
        }
        return false;
    }

    private void unload(Vehicle v) {
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
        if (first != null && v.place != null)
            w.dispatch.say(v.passengerType == Entity.SOLDIER ? Dispatch.WHO_MILITARY : Dispatch.WHO_POLICE, first,
                    "On the ground at " + v.place + ". Moving in.", v.x, v.y);
    }

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
