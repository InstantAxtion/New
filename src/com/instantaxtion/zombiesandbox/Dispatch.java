package com.instantaxtion.zombiesandbox;

import java.util.ArrayList;
import java.util.Random;

/**
 * Police dispatch and military command: 911 calls become incidents that cops respond to, big incidents
 * are escalated to the military, and both forces set up guarded safe zones that civilians run to.
 * Everything they say to each other goes into a radio log that GameView shows on screen.
 */
final class Dispatch {
    static final int T_NONE = 0, T_RESPOND = 1, T_GUARD = 2, T_SEEK = 3, T_SHELTER = 4, T_POST = 5, T_MOVE = 6,
            T_HOLD = 7, T_HIDE = 8, T_PICKUP = 9, T_RESUPPLY = 10, T_HEAL = 11, T_RIDE = 12, T_ENLIST = 13,
            T_CLEANUP = 14, T_LOOT = 15, T_PATROL = 16, T_BOARD = 17;
    static final int WHO_911 = 0, WHO_POLICE = 1, WHO_MILITARY = 2, WHO_INFO = 3, WHO_FIRE = 4;
    static final int[] WHO_COLORS = {0xFFFFA64D, 0xFF7FB0FF, 0xFFA6DC72, 0xFFBDBDBD, 0xFFFF7A5C};
    static final String[] SQUADS = {"Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel"};

    private static final String[] CALLS = {
            "There's a zombie attacking people on %s!",
            "Help! Something is eating my neighbor on %s!",
            "I can see %d of them near %s. Please hurry!",
            "People are turning into monsters on %s!",
            "Somebody bit a man outside my building on %s!",
    };

    static final class Incident {
        float x, y, age, clearTimer, sceneTime;
        String place;
        int reported, lastLogged, cops, soldiers, zombiesNear, officersDown;
        boolean militaryRequested, onScene, resolved, airRequested;
        int[] field;
    }

    static final class SafeZone {
        float x, y, r, age, attackCd, statusTimer, quietTime, checkCd, emptyTime;
        boolean military, removed, full, fullAnnounced;
        String place;
        int guards, sheltered, wantGuards, capacity;
        /** Ammunition the zone has on hand for its guards, and whether a supply truck is on its way. */
        int ammo = 300;
        boolean supplyComing;

        static final int SECTORS = 24;
        /**
         * Setting up: the guards make their way there and put the sandbags up a section at a time (0 to 1).
         * Nobody is let in until it opens.
         */
        float built;
        boolean open;
        /** How many guards are actually there (not still on their way). */
        int onSite;
        /** The size it was planned at. */
        float baseR;
        /**
         * The perimeter, as its distance from the centre in each direction. It grows as people come in and
         * guards arrive, follows the streets around it, and gives ground where the dead push on it.
         */
        final float[] edge = new float[SECTORS];
        final float[] shape = new float[SECTORS];
        final int[] pressure = new int[SECTORS];
        boolean fallingBack;
        /** The way there, for the guards. */
        int[] field;

        void initEdge(float r, java.util.Random rnd) {
            baseR = r;
            float[] raw = new float[SECTORS];
            for (int k = 0; k < SECTORS; k++) raw[k] = 0.82f + rnd.nextFloat() * 0.36f;
            for (int k = 0; k < SECTORS; k++)
                shape[k] = (raw[(k + SECTORS - 1) % SECTORS] + raw[k] * 2 + raw[(k + 1) % SECTORS]) / 4;
            for (int k = 0; k < SECTORS; k++) edge[k] = r * shape[k] * 0.7f;
            this.r = r * 0.7f;
        }

        /** How far the line is from the centre in the direction of an angle. */
        float edgeAt(float a) {
            float f = (float) (a / (Math.PI * 2) * SECTORS);
            f -= (float) Math.floor(f / SECTORS) * SECTORS;
            int k = (int) f % SECTORS;
            float t = f - (int) f;
            return edge[k] * (1 - t) + edge[(k + 1) % SECTORS] * t;
        }

        /** How far the line is from the centre in the direction of (px, py). */
        float reach(float px, float py) {
            return edgeAt((float) Math.atan2(py - y, px - x));
        }

        boolean contains(float px, float py, float margin) {
            float dx = px - x, dy = py - y, e = reach(px, py) * margin;
            return dx * dx + dy * dy < e * e;
        }

        float area() {
            float a = 0, s = (float) Math.sin(Math.PI * 2 / SECTORS);
            for (int k = 0; k < SECTORS; k++) a += edge[k] * edge[(k + 1) % SECTORS] * s / 2;
            return a;
        }
    }

    static final class Message {
        final String who, text;
        final int color;
        final float x, y;
        float age;

        Message(String who, String text, int color, float x, float y) {
            this.who = who;
            this.text = text;
            this.color = color;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Arrival {
        float time;
        int type, count;
        Incident incident;
        SafeZone zone;
        float x, y;
        String place;
    }

    private final World w;
    private final City city;
    private final Random rnd = new Random();
    final ArrayList<Incident> incidents = new ArrayList<Incident>();
    final ArrayList<SafeZone> zones = new ArrayList<SafeZone>();
    final ArrayList<Message> log = new ArrayList<Message>();
    private final ArrayList<Arrival> arrivals = new ArrayList<Arrival>();
    private final ArrayList<Entity> picked = new ArrayList<Entity>();
    final int[] zoneField;

    /** Reserves left to call in: police backup waves and military squads (4 each). Never refilled. */
    int policeReserve, squadReserve, airSorties, tankReserve;
    private float ambulanceTimer;
    int calls, sheltered, messageCount;
    int copCount, soldierCount;
    private int freeCops, freeSoldiers;
    private float tick, zoneFieldTimer, policeZoneCd = 10, militaryZoneCd = 6, militaryCd, policeCd;
    private boolean zonesDirty = true;

    private static final int[] POLICE_RESERVE = {0, 1, 2, 3}, SQUAD_RESERVE = {0, 1, 2, 3}, AIR_SORTIES = {0, 1, 1, 2},
            TANKS = {0, 1, 1, 2};

    Dispatch(World w, int reinforcementLevel) {
        this.w = w;
        this.city = w.city;
        policeReserve = POLICE_RESERVE[reinforcementLevel];
        squadReserve = SQUAD_RESERVE[reinforcementLevel];
        airSorties = AIR_SORTIES[reinforcementLevel];
        tankReserve = TANKS[reinforcementLevel];
        zoneField = new int[city.w * city.h];
        java.util.Arrays.fill(zoneField, City.FAR);
    }

    static String name(Entity e) {
        if (e.type == Entity.SOLDIER && e.role == Entity.ROLE_COMMANDER) return "Command";
        if (e.type == Entity.SOLDIER && e.role == Entity.ROLE_SNIPER) return "Overwatch-" + (Math.max(0, e.squad) + 1);
        if (e.type == Entity.COP) {
            Country c = Country.current;
            if (e.agency == 1 && c != null) return c.hpShort + " " + e.callsign;
            if (e.agency == 2 && c != null && c.ruralShort != null) return c.ruralShort + " " + e.callsign;
            return "Unit " + e.callsign;
        }
        if (e.type == Entity.SOLDIER) return SQUADS[Math.max(0, e.squad) % SQUADS.length] + "-" + e.member;
        return "Caller";
    }

    void say(int who, Entity speaker, String text, float x, float y) {
        String label = who == WHO_911 ? "911" : who == WHO_POLICE ? "Police" : who == WHO_MILITARY ? "Military"
                : who == WHO_FIRE ? "Fire Dept" : "Info";
        if (speaker != null) {
            label = name(speaker);
            speaker.talkTimer = 2.5f;
        } else {
            // "Police Command: ..." -> speaker "Police Command", text "...".
            int colon = text.indexOf(": ");
            if (colon > 0 && colon < 20 && !text.startsWith("\"")) {
                label = text.substring(0, colon);
                text = text.substring(colon + 2);
            }
        }
        log.add(new Message(label, text, WHO_COLORS[who], x, y));
        if (log.size() > 200) log.remove(0);
        messageCount++;
    }

    void clear() {
        incidents.clear();
        zones.clear();
        arrivals.clear();
        log.clear();
        zonesDirty = true;
    }

    // ------------------------------------------------------------------ 911

    /** A civilian who saw a zombie calls 911. */
    void call(Entity caller, Entity zombie) {
        calls++;
        caller.phoneTimer = 3f;
        w.emit(Sfx.PHONE, caller.x, caller.y);
        int count = Math.max(1, w.countZombiesNear(zombie.x, zombie.y, 90));
        for (int i = 0; i < incidents.size(); i++) {
            Incident inc = incidents.get(i);
            float dx = inc.x - zombie.x, dy = inc.y - zombie.y;
            if (dx * dx + dy * dy < 200 * 200) {
                inc.reported = Math.max(inc.reported, count);
                if (inc.reported >= inc.lastLogged + 4) {
                    inc.lastLogged = inc.reported;
                    say(WHO_911, null, "More calls from " + inc.place + ". Callers report " + inc.reported + " of them.",
                            inc.x, inc.y);
                }
                return;
            }
        }
        if (incidents.size() >= 12) return;
        Incident inc = new Incident();
        inc.x = zombie.x;
        inc.y = zombie.y;
        inc.place = city.placeName(zombie.x, zombie.y);
        inc.reported = inc.lastLogged = count;
        inc.field = new int[city.w * city.h];
        city.walkFieldFromPoints(inc.field, new float[]{inc.x}, new float[]{inc.y}, 1);
        incidents.add(inc);
        String line = CALLS[rnd.nextInt(CALLS.length)];
        String text = line.contains("%d") ? String.format(line, count, inc.place) : String.format(line, inc.place);
        say(WHO_911, null, "\"" + text + "\"", inc.x, inc.y);
        if (w.counts[Entity.COP] == 0 && w.counts[Entity.SOLDIER] == 0) {
            if (!policeBackup(inc)) say(WHO_POLICE, null, "Dispatch: No units available for " + inc.place + ".", inc.x, inc.y);
        }
    }

    // ------------------------------------------------------------------ update

    void update(float dt) {
        for (int i = 0; i < log.size(); i++) log.get(i).age += dt;
        updateAir(dt);
        for (int i = arrivals.size() - 1; i >= 0; i--) {
            Arrival a = arrivals.get(i);
            a.time -= dt;
            if (a.time <= 0) {
                arrivals.remove(i);
                arrive(a);
            }
        }
        zoneFieldTimer -= dt;
        if (zonesDirty || zoneFieldTimer <= 0) updateZoneField();
        tick += dt;
        if (tick < 0.5f) return;
        float step = tick;
        tick = 0;
        sendAmbulances(step);
        policeZoneCd -= step;
        militaryZoneCd -= step;
        militaryCd -= step;
        policeCd -= step;
        census();
        updateIncidents(step);
        updateZones(step);
    }

    private void census() {
        freeCops = freeSoldiers = 0;
        sheltered = 0;
        for (int i = 0; i < zones.size(); i++) {
            zones.get(i).guards = 0;
            zones.get(i).sheltered = 0;
            zones.get(i).onSite = 0;
        }
        for (int i = 0; i < incidents.size(); i++) incidents.get(i).cops = incidents.get(i).soldiers = 0;
        for (int i = 0, n = w.entities.size(); i < n; i++) {
            Entity e = w.entities.get(i);
            if (e.dead || e.isZombie()) continue;
            if (e.task == T_RESPOND && (e.incident == null || e.incident.resolved)) release(e);
            if ((e.task == T_GUARD || e.task == T_SHELTER) && (e.zone == null || e.zone.removed)) release(e);
            switch (e.task) {
                case T_RESPOND:
                    if (e.type == Entity.COP) e.incident.cops++;
                    else e.incident.soldiers++;
                    break;
                case T_GUARD:
                    e.slot = e.zone.guards++;
                    if (Math.hypot(e.x - e.zone.x, e.y - e.zone.y) < e.zone.baseR * 1.3f) e.zone.onSite++;
                    break;
                case T_SHELTER:
                    e.zone.sheltered++;
                    sheltered++;
                    break;
                case T_NONE:
                    if (e.type == Entity.COP) freeCops++;
                    else if (e.type == Entity.SOLDIER) freeSoldiers++;
                    break;
            }
        }
    }

    /** Frees a unit from its job so it can go and resupply. */
    void releaseForResupply(Entity e) {
        release(e);
    }

    /** Player orders: sends the selected units to a spot, where they hold position until released. */
    void order(java.util.List<Entity> units, float x, float y) {
        float[] p = city.findWalkable(x, y);
        if (p == null || units.isEmpty()) return;
        int[] field = new int[city.w * city.h];
        city.walkFieldFromPoints(field, new float[]{p[0]}, new float[]{p[1]}, 1);
        for (int i = 0; i < units.size(); i++) {
            Entity e = units.get(i);
            release(e);
            e.task = T_MOVE;
            e.orderField = field;
            double a = i * Math.PI * 2 / units.size();
            float spread = units.size() > 1 ? 12 : 0;
            float[] q = city.findWalkable(p[0] + (float) Math.cos(a) * spread, p[1] + (float) Math.sin(a) * spread);
            e.postX = q != null ? q[0] : p[0];
            e.postY = q != null ? q[1] : p[1];
        }
        Entity lead = units.get(0);
        say(lead.type == Entity.SOLDIER ? WHO_MILITARY : WHO_POLICE, lead, "Copy, moving to " + city.placeName(p[0], p[1])
                + (units.size() > 1 ? " with " + (units.size() - 1) + " more." : "."), lead.x, lead.y);
    }

    /** Player order: back to normal duties. */
    void dismiss(java.util.List<Entity> units) {
        for (Entity e : units) release(e);
        if (!units.isEmpty())
            say(units.get(0).type == Entity.SOLDIER ? WHO_MILITARY : WHO_POLICE, units.get(0), "Copy, resuming patrol.",
                    units.get(0).x, units.get(0).y);
    }

    /** Calls the helicopter in, if a sortie is left and it isn't already flying. */
    /**
     * Heavy fighting (a commander calling it in, or a big horde): the army sends a tank if it has one,
     * otherwise the helicopter.
     */
    void heavyContact(Entity commander, float x, float y, int count) {
        String place = city.placeName(x, y);
        City.Facility base = city.nearestFacility(City.FACILITY_BASE, x, y);
        if (tankReserve > 0 && base != null && w.fleet.count(Fleet.TANK) == 0
                && w.fleet.sendTank(base.x, base.y, x, y, place)) {
            tankReserve--;
            if (commander != null) say(WHO_MILITARY, commander, "Heavy contact at " + place + ", " + count
                    + "+ hostiles. Sending in armor!", x, y);
            else say(WHO_MILITARY, null, "Military: Big horde at " + place + ". A tank is rolling out."
                    + (tankReserve == 0 ? " It's the only one we have." : ""), x, y);
            return;
        }
        if (airSorties > 0 && !airBusy()) {
            if (commander != null) say(WHO_MILITARY, commander, "Heavy contact at " + place + ". Requesting air support!", x, y);
            requestAir(x, y, place);
        }
    }

    /** The hospital sends an ambulance for someone badly hurt when the street around them is quiet. */
    private void sendAmbulances(float step) {
        ambulanceTimer -= step;
        if (ambulanceTimer > 0) return;
        ambulanceTimer = 4;
        City.Facility hospital = city.nearestFacility(City.FACILITY_HOSPITAL, 0, 0);
        if (hospital == null || w.fleet.count(Fleet.AMBULANCE) >= 2 || w.countZombiesNear(hospital.x, hospital.y, 160) > 0) return;
        Entity best = null;
        float worst = 1;
        for (int i = 0, n = w.entities.size(); i < n; i++) {
            Entity e = w.entities.get(i);
            if (e.dead || e.type != Entity.CIVILIAN || e.ride != null || e.task == T_SHELTER) continue;
            float f = e.hp / e.maxHp;
            if (f >= Math.min(worst, 0.5f)) continue;
            if (Math.hypot(e.x - hospital.x, e.y - hospital.y) < 300 || w.countZombiesNear(e.x, e.y, 60) > 0) continue;
            if (w.fleet.hasPatient(e)) continue;
            worst = f;
            best = e;
        }
        if (best != null && w.fleet.sendAmbulance(hospital, best))
            say(WHO_INFO, null, "Hospital: Ambulance on its way to an injured person on " + city.placeName(best.x, best.y) + ".",
                    best.x, best.y);
    }

    /** Air support that has been approved but hasn't taken off yet: seconds to go, and where it's going. */
    float airDelay;
    private float airX, airY;
    private String airPlace;
    private boolean airPad;

    boolean airBusy() {
        return airDelay > 0 || w.fleet.heliBusy();
    }

    /**
     * Air support takes time: the request goes up the chain, the crew is briefed and the aircraft is fuelled and
     * armed. A helicopter on a pad in town is quicker than one flying in from outside the city.
     */
    private void requestAir(float x, float y, String place) {
        if (airSorties <= 0 || airBusy()) return;
        airSorties--;
        airX = x;
        airY = y;
        airPlace = place;
        airPad = city.nearestHelipad(x, y) != null || city.nearestFacility(City.FACILITY_BASE, x, y) != null;
        airDelay = airPad ? 45 + w.rnd.nextFloat() * 25 : 75 + w.rnd.nextFloat() * 35;
        int eta = Math.round((airDelay + (airPad ? 30 : 25)) / 60f);
        say(WHO_MILITARY, null, "Military: Air support approved for " + place + ". The crew is " + (airPad ? "being briefed" : "lifting off from outside the city")
                + ", ETA " + (eta <= 1 ? "one minute" : eta + " minutes") + "." + (airSorties == 0 ? " That's our last sortie." : ""), x, y);
    }

    private void updateAir(float dt) {
        if (airDelay <= 0) return;
        airDelay -= dt;
        if (airDelay > 0) return;
        airDelay = 0;
        // Head for the worst of the fighting now, if it has moved.
        Entity z = w.nearestZombie(airX, airY, 500);
        float x = airX, y = airY;
        if (z != null) {
            x = z.x;
            y = z.y;
        }
        launchAir(x, y, airPlace);
    }

    private void launchAir(float x, float y, String place) {
        float fx, fy;
        City.Facility base = city.nearestFacility(City.FACILITY_BASE, x, y);
        float[] pad = city.nearestHelipad(x, y);
        if (pad != null) {
            fx = pad[0];
            fy = pad[1];
        } else if (base != null) {
            fx = base.x;
            fy = base.y;
        } else {
            float[] edge = city.edgeSpawn(x, y);
            fx = edge != null ? edge[0] : 0;
            fy = edge != null ? edge[1] : 0;
        }
        w.fleet.sendHeli(fx, fy, x, y, place);
        say(WHO_MILITARY, null, "Military: Air 1 is " + (pad != null ? "spinning up" : "inbound") + " to " + place + ". Stay clear of the area!", x, y);
    }

    private static void release(Entity e) {
        e.task = T_NONE;
        e.incident = null;
        e.zone = null;
        e.onScene = false;
        e.orderField = null;
    }

    private void updateIncidents(float step) {
        int open = incidents.size();
        for (int i = incidents.size() - 1; i >= 0; i--) {
            Incident inc = incidents.get(i);
            inc.age += step;
            if (inc.onScene) inc.sceneTime += step;
            inc.zombiesNear = w.countZombiesNear(inc.x, inc.y, 140);
            if (inc.zombiesNear == 0) inc.clearTimer += step;
            else inc.clearTimer = 0;
            if ((inc.clearTimer > 5 && inc.onScene && inc.sceneTime > 5) || inc.clearTimer > 30) {
                resolve(inc);
                continue;
            }
            int need = Math.min(4, 1 + inc.zombiesNear / 3);
            int have = inc.cops + inc.soldiers + w.fleet.inbound(inc);
            // The nearest patrol car takes the call first; officers on foot make up the rest.
            if (have < need) {
                Fleet.Vehicle car = w.fleet.patrolFor(inc.x, inc.y);
                if (car != null && w.fleet.respond(car, inc)) have += car.crew.size();
            }
            if (have < need) {
                int got = assign(inc, Entity.COP, need - have);
                if (got == 0 && have == 0) policeBackup(inc);
            }
            if (!inc.airRequested && inc.zombiesNear >= 12) {
                inc.airRequested = true;
                requestAir(inc.x, inc.y, inc.place);
            }
            if (!inc.militaryRequested && (inc.zombiesNear >= 8 || (inc.officersDown > 0 && inc.zombiesNear >= 3)
                    || (open >= 4 && inc.zombiesNear >= 4))) {
                requestMilitary(inc.x, inc.y, inc.place, inc, null);
            }
        }
    }

    private void resolve(Incident inc) {
        inc.resolved = true;
        incidents.remove(inc);
        Entity speaker = null;
        for (int i = 0, n = w.entities.size(); i < n; i++) {
            Entity e = w.entities.get(i);
            if (e.task == T_RESPOND && e.incident == inc) {
                if (speaker == null) speaker = e;
                release(e);
            }
        }
        if (speaker != null && inc.onScene) {
            say(speaker.type == Entity.COP ? WHO_POLICE : WHO_MILITARY, speaker,
                    inc.place + " is clear. Returning to patrol.", inc.x, inc.y);
        } else {
            say(WHO_POLICE, null, "Dispatch: No sign of hostiles at " + inc.place + ". Closing the call.", inc.x, inc.y);
        }
    }

    /** Picks up to n of the nearest free units of a type. */
    private ArrayList<Entity> nearestFree(int type, float x, float y, int n, float maxDist) {
        picked.clear();
        for (int k = 0; k < n; k++) {
            Entity best = null;
            float bd = maxDist * maxDist;
            for (int i = 0, count = w.entities.size(); i < count; i++) {
                Entity e = w.entities.get(i);
                if (e.dead || e.type != type || e.task != T_NONE || picked.contains(e)) continue;
                float dx = e.x - x, dy = e.y - y, d = dx * dx + dy * dy;
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            if (best == null) break;
            picked.add(best);
        }
        return picked;
    }

    private int assign(Incident inc, int type, int n) {
        ArrayList<Entity> units = nearestFree(type, inc.x, inc.y, n, Float.MAX_VALUE);
        if (units.isEmpty()) return 0;
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < units.size(); i++) {
            Entity e = units.get(i);
            e.task = T_RESPOND;
            e.incident = inc;
            e.onScene = false;
            if (i > 0) names.append(i == units.size() - 1 ? " and " : ", ");
            names.append(name(e));
            if (type == Entity.COP) inc.cops++;
            else inc.soldiers++;
        }
        if (type == Entity.COP) {
            int reported = Math.max(1, Math.max(inc.reported, inc.zombiesNear));
            say(WHO_POLICE, null, "Dispatch: " + names + ", respond to " + inc.place + ". " + reported
                    + " hostile" + (reported == 1 ? "" : "s") + " reported.", inc.x, inc.y);
            Entity first = units.get(0);
            say(WHO_POLICE, first, "10-4, en route.", first.x, first.y);
        }
        int got = units.size();
        units.clear();
        return got;
    }

    void onScene(Entity e, Incident inc) {
        if (inc.onScene) return;
        inc.onScene = true;
        String text = inc.zombiesNear > 0 ? "On scene at " + inc.place + ". Engaging " + inc.zombiesNear + " hostile"
                + (inc.zombiesNear == 1 ? "" : "s") + "." : "On scene at " + inc.place + ". Searching the area.";
        say(e.type == Entity.COP ? WHO_POLICE : WHO_MILITARY, e, text, inc.x, inc.y);
    }

    void onDeath(Entity e) {
        if (e.task == T_RESPOND && e.incident != null && !e.incident.resolved) {
            Incident inc = e.incident;
            inc.officersDown++;
            if (e.type == Entity.COP)
                say(WHO_POLICE, null, "Dispatch: Officer down at " + inc.place + "! " + name(e) + " is down!", e.x, e.y);
            else say(WHO_MILITARY, null, "Military: Man down at " + inc.place + "! Lost " + name(e) + ".", e.x, e.y);
        } else if (e.task == T_GUARD && e.zone != null && !e.zone.removed) {
            SafeZone z = e.zone;
            if (e.type == Entity.COP)
                say(WHO_POLICE, null, "Dispatch: Officer down at the " + z.place + " safe zone!", e.x, e.y);
        }
    }

    /** Police ask the military for help; the military sends a free squad or calls in reinforcements. */
    private void requestMilitary(float x, float y, String place, Incident inc, SafeZone zone) {
        if (militaryCd > 0) return;
        militaryCd = 20;
        if (inc != null) inc.militaryRequested = true;
        int hostiles = w.countZombiesNear(x, y, 160);
        say(WHO_POLICE, null, "Police Command: Requesting military support at " + place + ". " + hostiles
                + " hostiles.", x, y);
        ArrayList<Entity> squad = nearestFree(Entity.SOLDIER, x, y, 4, Float.MAX_VALUE);
        if (!squad.isEmpty()) {
            Entity lead = squad.get(0);
            for (Entity e : squad) {
                if (zone != null) {
                    e.task = T_GUARD;
                    e.zone = zone;
                } else {
                    e.task = T_RESPOND;
                    e.incident = inc;
                }
            }
            say(WHO_MILITARY, lead, "Copy that, Police. " + SQUADS[Math.max(0, lead.squad) % SQUADS.length]
                    + " squad moving to " + place + ".", lead.x, lead.y);
            squad.clear();
        } else if (!sendReserveSquad(x, y, place, inc, zone)) {
            say(WHO_MILITARY, null, "Military: Negative, no units or reserves left. Hold them off, Police.", x, y);
        }
    }

    /** Calls in one of the limited reserve squads. Returns false if none are left. */
    private boolean sendReserveSquad(float x, float y, String place, Incident inc, SafeZone zone) {
        if (squadReserve <= 0) return false;
        squadReserve--;
        Arrival a = new Arrival();
        a.time = 2;
        a.type = Entity.SOLDIER;
        a.count = 4;
        a.incident = inc;
        a.zone = zone;
        a.x = x;
        a.y = y;
        a.place = place;
        arrivals.add(a);
        City.Facility base = origin(Entity.SOLDIER, x, y);
        say(WHO_MILITARY, null, "Military: Copy. " + (base != null ? "Deploying a reserve squad from " + base.name
                : "Reserve squad inbound") + " to " + place + "."
                + (squadReserve == 0 ? " That's our last one." : " " + squadReserve + " left in reserve."), x, y);
        return true;
    }

    private boolean policeBackup(Incident inc) {
        if (policeReserve <= 0 || policeCd > 0) return false;
        policeCd = 40;
        policeReserve--;
        Arrival a = new Arrival();
        a.time = 2;
        a.type = Entity.COP;
        a.count = 4;
        a.incident = inc;
        a.x = inc.x;
        a.y = inc.y;
        a.place = inc.place;
        arrivals.add(a);
        City.Facility station = origin(Entity.COP, inc.x, inc.y);
        say(WHO_POLICE, null, "Dispatch: All units busy. Sending backup from " + (station != null ? station.name
                : "the precinct") + " to " + inc.place + "." + (policeReserve == 0 ? " That's the last of our officers."
                : ""), inc.x, inc.y);
        return true;
    }

    /** Where reinforcements come from: the base or nearest precinct, or else the edge of the map. */
    private City.Facility origin(int type, float x, float y) {
        return city.nearestFacility(type == Entity.SOLDIER ? City.FACILITY_BASE : City.FACILITY_POLICE, x, y);
    }

    private void arrive(Arrival a) {
        City.Facility from = origin(a.type, a.x, a.y);
        float[] p = from != null ? new float[]{from.gateX, from.gateY} : city.edgeSpawn(a.x, a.y);
        if (p == null) return;
        if (a.type == Entity.SOLDIER) soldierCount = (soldierCount + 3) / 4 * 4;
        // Drive there if the roads allow it; otherwise they go on foot.
        if (w.fleet.send(a.type, a.count, p[0], p[1], a.x, a.y, a.incident, a.zone, a.place)) {
            say(a.type == Entity.SOLDIER ? WHO_MILITARY : WHO_POLICE, null, (a.type == Entity.SOLDIER
                    ? "Military: Truck rolling out of " : "Dispatch: Cruisers leaving ")
                    + (from != null ? from.name : "the city limits") + " for " + a.place + ".", p[0], p[1]);
            return;
        }
        Entity first = null;
        for (int i = 0; i < a.count; i++) {
            Entity e = w.spawn(a.type, p[0] + rnd.nextFloat() * 24 - 12, p[1] + rnd.nextFloat() * 24 - 12);
            if (e == null) continue;
            if (first == null) first = e;
            if (a.incident != null && !a.incident.resolved) {
                e.task = T_RESPOND;
                e.incident = a.incident;
            } else if (a.zone != null && !a.zone.removed) {
                e.task = T_GUARD;
                e.zone = a.zone;
            }
        }
        if (first == null) return;
        if (a.type == Entity.SOLDIER)
            say(WHO_MILITARY, first, SQUADS[Math.max(0, first.squad) % SQUADS.length] + " squad "
                    + (from != null ? "rolling out of " + from.name : "on the ground") + ". Moving to " + a.place + ".", p[0], p[1]);
        else say(WHO_POLICE, first, (from != null ? "Leaving " + from.name : "Backup has arrived") + ". Heading to "
                + a.place + ".", p[0], p[1]);
    }

    // ------------------------------------------------------------------ safe zones

    private void updateZones(float step) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            SafeZone z = zones.get(i);
            z.age += step;
            z.checkCd -= step;
            z.attackCd -= step;
            int near = w.countZombiesNear(z.x, z.y, z.r * 1.6f);
            if (z.guards == 0 && z.age > 10) {
                removeZone(z, near > 0 && z.open);
                continue;
            }
            shapeEdge(z, step);
            if (!z.open) {
                // Setting up: the sandbags go up as fast as there are hands on site to fill them.
                if (w.countZombiesNear(z.x, z.y, z.baseR * 1.4f) >= 3) {
                    say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military" : "Police") + ": The dead got to "
                            + z.place + " before the safe zone was ready. Pulling out!", z.x, z.y);
                    z.removed = true;
                    zones.remove(i);
                    zonesDirty = true;
                    for (int k = 0, n = w.entities.size(); k < n; k++) if (w.entities.get(k).zone == z) release(w.entities.get(k));
                    if (z.military) militaryZoneCd = 40;
                    else policeZoneCd = 45;
                    continue;
                }
                z.built += step * Math.min(6, z.onSite) / (z.military ? 110f : 120f);
                if (z.built >= 1) {
                    z.built = 1;
                    z.open = true;
                    z.age = 0;
                    zonesDirty = true;
                    say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military: " : "Police Command: ")
                            + "The safe zone at " + z.place + " is open, room for " + z.capacity
                            + ". Civilians, head there now!", z.x, z.y);
                }
                if (z.guards < z.wantGuards) assignGuards(z, z.wantGuards - z.guards, 900, z.military ? Entity.SOLDIER : Entity.COP);
                continue;
            }
            // Overrun: more of the dead inside the line than the guards can hold.
            int inside = 0;
            for (int k = 0, n = w.entities.size(); k < n && near > 0; k++) {
                Entity o = w.entities.get(k);
                if (o.dead || !o.isZombie() || o.hidden) continue;
                if (Math.abs(o.x - z.x) < z.r * 1.5f && Math.abs(o.y - z.y) < z.r * 1.5f && z.contains(o.x, o.y, 1)) inside++;
            }
            if (inside >= Math.max(4, z.onSite * 2)) {
                removeZone(z, true);
                continue;
            }
            // Stand the zone down when its own neighbourhood has been quiet for a while, when nobody has
            // needed it for a while, or soon after the outbreak is over.
            if (w.countZombiesNear(z.x, z.y, 380) == 0) z.quietTime += step;
            else z.quietTime = 0;
            if (z.sheltered == 0 && near == 0) z.emptyTime += step;
            else z.emptyTime = 0;
            boolean over = !w.outbreak;
            if (z.age > 90 && (z.quietTime > (over ? 25 : 75) || z.emptyTime > 90)) {
                closeZone(z);
                if (z.military) militaryZoneCd = 90;
                else policeZoneCd = 90;
                continue;
            }
            if (z.guards < z.wantGuards) assignGuards(z, z.wantGuards - z.guards, 900, z.military ? Entity.SOLDIER : Entity.COP);
            if (near >= 5 && z.attackCd <= 0) {
                z.attackCd = 30;
                say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military" : "Police") + ": The "
                        + z.place + " safe zone is under attack! " + near + " hostiles at the perimeter!", z.x, z.y);
                if (!z.military) requestMilitary(z.x, z.y, z.place, null, z);
                else sendReserveSquad(z.x, z.y, z.place, null, z);
                if (near >= 8) requestAir(z.x, z.y, z.place);
            }
            // Capacity: announce when a zone fills up, and stop sending people there.
            boolean full = z.sheltered >= z.capacity;
            if (full != z.full) {
                z.full = full;
                zonesDirty = true;
                if (full && !z.fullAnnounced) {
                    z.fullAnnounced = true;
                    say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military" : "Police") + ": The "
                            + z.place + " safe zone is full (" + z.capacity + "). Turning people away!", z.x, z.y);
                }
            }
            if (z.sheltered < z.capacity * 0.8f) z.fullAnnounced = false;
            z.statusTimer += step;
            if (z.statusTimer > 60) {
                z.statusTimer = 0;
                if (z.sheltered > 0)
                    say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military" : "Police") + ": "
                            + z.place + " safe zone is holding. " + z.sheltered + " civilians sheltered.", z.x, z.y);
            }
        }
        int zombies = w.zombieCount();
        // New zones only while there are live 911 incidents to shelter people from.
        if (incidents.isEmpty()) return;
        if (policeZoneCd <= 0 && countZones(false) == 0 && zombies >= 4 && freeCops >= 3) {
            policeZoneCd = 45;
            establish(false, null);
        }
        if (militaryZoneCd <= 0 && countZones(true) < 2 && zombies >= 10 && freeSoldiers >= 3) {
            militaryZoneCd = 40;
            establish(true, null);
        }
    }

    private int countZones(boolean military) {
        int n = 0;
        for (int i = 0; i < zones.size(); i++) if (zones.get(i).military == military) n++;
        return n;
    }

    /** Picks an open area away from zombies and near the people who need it. */
    private float[] chooseSite() {
        float px = 0, py = 0;
        int n = 0;
        for (int i = 0, count = w.entities.size(); i < count; i++) {
            Entity e = w.entities.get(i);
            if (e.type == Entity.CIVILIAN) {
                px += e.x;
                py += e.y;
                n++;
            }
        }
        if (n == 0) {
            px = city.worldW() / 2;
            py = city.worldH() / 2;
        } else {
            px /= n;
            py /= n;
        }
        float bestScore = -Float.MAX_VALUE;
        float[] best = null;
        for (float[] a : city.openAreas) {
            boolean taken = false;
            for (int i = 0; i < zones.size(); i++) {
                float dx = zones.get(i).x - a[0], dy = zones.get(i).y - a[1];
                if (dx * dx + dy * dy < 260 * 260) taken = true;
            }
            if (taken) continue;
            int d = Math.min(40, city.fieldAt(city.zombieDist, a[0], a[1]));
            if (d < 5 || w.countZombiesNear(a[0], a[1], CLEAR_OF_DEAD) > 0) continue;
            float dx = a[0] - px, dy = a[1] - py;
            float score = d * 3 - (float) Math.sqrt(dx * dx + dy * dy) / City.T * 0.6f;
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    /** A police station or military base that doesn't have a zone yet and isn't swarmed. */
    private City.Facility freeFacility(boolean military) {
        for (City.Facility f : city.facilities) {
            if (f.kind != (military ? City.FACILITY_BASE : City.FACILITY_POLICE)) continue;
            boolean taken = false;
            for (int i = 0; i < zones.size(); i++)
                if (Math.hypot(zones.get(i).x - f.x, zones.get(i).y - f.y) < 150) taken = true;
            if (!taken && city.fieldAt(city.zombieDist, f.x, f.y) >= 3 && w.countZombiesNear(f.x, f.y, CLEAR_OF_DEAD) == 0) return f;
        }
        return null;
    }

    /**
     * A church, school or supermarket to shelter people at: big buildings with open ground outside.
     * Soldiers only use schools and supermarkets.
     */
    private City.Building freeLandmark(boolean military, float[] out) {
        City.Building best = null;
        float bestScore = -Float.MAX_VALUE;
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.collapsed || b.name == null || (military && b.kind == City.CHURCH)) continue;
            boolean taken = false;
            for (int k = 0; k < zones.size(); k++)
                if (Math.hypot(zones.get(k).x - b.doorX, zones.get(k).y - b.doorY) < 150) taken = true;
            if (taken) continue;
            int d = Math.min(30, city.fieldAt(city.zombieDist, b.doorX, b.doorY));
            if (d < 4 || w.countZombiesNear(b.doorX, b.doorY, CLEAR_OF_DEAD) > 0) continue;
            // Somewhere quiet, but not miles from the people who need it.
            float score = d - Math.min(40, city.fieldAt(city.humanDist, b.doorX, b.doorY)) * 0.3f + (b.kind == City.SCHOOL ? 2 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = b;
            }
        }
        if (best == null) return null;
        // The zone sits in front of the door.
        float cx = (best.x0 + best.x1) / 2, cy = (best.y0 + best.y1) / 2;
        float dx = best.doorX - cx, dy = best.doorY - cy, d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
        float[] p = city.findWalkable(best.doorX + dx / d * 20, best.doorY + dy / d * 20);
        if (p == null) return null;
        out[0] = p[0];
        out[1] = p[1];
        return best;
    }

    /** No safe zone is set up with the dead closer than this. */
    static final float CLEAR_OF_DEAD = 250;

    /**
     * The line follows the ground: each section reaches out as far as the zone needs (more people, more
     * room; more guards, a longer line they can hold), stops at walls, and falls back where the dead push.
     */
    private void shapeEdge(SafeZone z, float step) {
        int n = SafeZone.SECTORS;
        java.util.Arrays.fill(z.pressure, 0);
        float look = z.r + 70;
        for (int k = 0, count = w.entities.size(); k < count; k++) {
            Entity o = w.entities.get(k);
            if (o.dead || !o.isZombie() || Math.abs(o.x - z.x) > look || Math.abs(o.y - z.y) > look) continue;
            float a = (float) Math.atan2(o.y - z.y, o.x - z.x);
            int s = ((int) Math.floor(a / (Math.PI * 2) * n) % n + n) % n;
            if (Math.hypot(o.x - z.x, o.y - z.y) < z.edge[s] + 60) z.pressure[s]++;
        }
        float guardFactor = Math.min(1, z.onSite / (float) Math.max(1, z.wantGuards));
        float maxR = z.baseR * (0.7f + 0.6f * guardFactor);
        float needed = (float) Math.sqrt((z.sheltered + 10) * 330 / Math.PI);
        float target = Math.max(z.baseR * 0.6f, Math.min(maxR, needed));
        if (!z.open) target = z.baseR * 0.85f;
        float sum = 0;
        int squeezed = 0;
        for (int k = 0; k < n; k++) {
            int p = z.pressure[k] + (z.pressure[(k + 1) % n] + z.pressure[(k + n - 1) % n]) / 2;
            float want = target * z.shape[k] * (1 - 0.45f * Math.min(1, p / 4f));
            // Stop short of buildings and water: the line is sandbags across the street, not through walls.
            double a = k * Math.PI * 2 / n;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            for (float d = 14; d < want; d += 6)
                if (city.tiles[city.tileIndex(z.x + ca * d, z.y + sa * d)] == City.BUILDING) {
                    want = Math.max(z.baseR * 0.45f, d - 4);
                    break;
                }
            float rate = want < z.edge[k] ? 14 : 6;
            z.edge[k] += Math.max(-rate * step, Math.min(rate * step, want - z.edge[k]));
            sum += z.edge[k];
            if (p >= 4) squeezed++;
        }
        z.r = sum / n;
        // Room for as many as the line could stretch to hold with the guards it has.
        int cap = Math.max(8, (int) (Math.PI * maxR * maxR / 330));
        if (cap != z.capacity && z.open) zonesDirty = true;
        z.capacity = cap;
        boolean falling = z.open && squeezed >= n / 3;
        if (falling && !z.fallingBack)
            say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military" : "Police") + ": " + z.place
                    + " perimeter is giving way. Pulling the line back!", z.x, z.y);
        z.fallingBack = falling;
    }

    private SafeZone establish(boolean military, float[] at) {
        String place;
        float x, y;
        float radius = military ? 80 : 60;
        City.Facility facility = at == null ? freeFacility(military) : null;
        float[] spot = new float[2];
        City.Building landmark = at == null && facility == null ? freeLandmark(military, spot) : null;
        if (facility != null) {
            x = facility.x;
            y = facility.y;
            place = facility.name;
            radius = Math.max(radius, facility.r);
        } else if (landmark != null) {
            x = spot[0];
            y = spot[1];
            place = landmark.name;
            radius = 64;
        } else if (at == null) {
            float[] area = chooseSite();
            if (area == null) return null;
            float[] p = city.findWalkable(area[0], area[1]);
            if (p == null) return null;
            x = p[0];
            y = p[1];
            place = city.areaName(area);
        } else {
            x = at[0];
            y = at[1];
            place = city.placeName(x, y);
        }
        SafeZone z = new SafeZone();
        z.x = x;
        z.y = y;
        z.military = military;
        z.initEdge(radius, rnd);
        // Bases and stations hold more people than a zone thrown up in a park.
        z.capacity = Math.max(8, (int) (Math.PI * radius * radius / 330));
        z.wantGuards = military ? 6 : 4;
        z.place = place;
        int got = assignGuards(z, z.wantGuards, Float.MAX_VALUE, military ? Entity.SOLDIER : Entity.COP);
        if (got == 0) return null;
        zones.add(z);
        if (!military) {
            int n = w.fleet.roadblocks(z);
            if (n > 0) say(WHO_POLICE, null, "Police Command: Sending " + n + (n == 1 ? " car" : " cars")
                    + " to close the roads into " + place + ".", x, y);
        }
        zonesDirty = true;
        if (military)
            say(WHO_MILITARY, null, "Military: " + got + " soldiers moving to " + place
                    + " to set up a safe zone. It opens once the perimeter is up.", x, y);
        else
            say(WHO_POLICE, null, "Police Command: Officers on the way to set up a safe zone at " + place
                    + ". Stay put until it's ready.", x, y);
        return z;
    }

    private int assignGuards(SafeZone z, int n, float maxDist, int type) {
        ArrayList<Entity> units = nearestFree(type, z.x, z.y, n, maxDist);
        int got = units.size();
        for (Entity e : units) {
            e.task = T_GUARD;
            e.zone = z;
            z.guards++;
        }
        units.clear();
        return got;
    }

    /** The outbreak is over here: the zone closes and people go home. */
    void closeZone(SafeZone z) {
        say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military: " : "Police Command: ") + "The " + z.place
                + " safe zone is closing. Residents can go home.", z.x, z.y);
        removeZone(z, false, true);
    }

    private void removeZone(SafeZone z, boolean overrun) {
        removeZone(z, overrun, false);
    }

    private void removeZone(SafeZone z, boolean overrun, boolean closing) {
        z.removed = true;
        zones.remove(z);
        zonesDirty = true;
        for (int i = 0, n = w.entities.size(); i < n; i++) {
            Entity e = w.entities.get(i);
            if (e.zone == z) {
                if (e.task == T_SHELTER && overrun) {
                    e.fleeTimer = 3;
                    e.threatX = z.x;
                    e.threatY = z.y;
                }
                release(e);
            }
        }
        if (overrun) {
            say(WHO_INFO, null, "The " + z.place + " safe zone has been overrun!", z.x, z.y);
            w.highlight(z.place + " safe zone overrun", z.x, z.y);
            SafeZone fallback = null;
            for (int i = 0; i < zones.size(); i++) if (zones.get(i).military) fallback = zones.get(i);
            if (!z.military && fallback != null) {
                say(WHO_POLICE, null, "Police Command: All units, fall back to the military safe zone at "
                        + fallback.place + ".", fallback.x, fallback.y);
                assignGuards(fallback, 4, Float.MAX_VALUE, Entity.COP);
            }
        } else if (!closing) {
            say(WHO_INFO, null, "The " + z.place + " safe zone was abandoned.", z.x, z.y);
        }
    }

    /** The Safe Zone tool: the player orders a safe zone at a spot. */
    void orderZone(float x, float y) {
        float[] p = city.findWalkable(x, y);
        if (p == null) {
            say(WHO_INFO, null, "Can't set up a safe zone there.", x, y);
            return;
        }
        if (w.countZombiesNear(p[0], p[1], CLEAR_OF_DEAD) > 0) {
            say(WHO_INFO, null, "Too close to the dead to set up a safe zone. Pick somewhere quieter.", p[0], p[1]);
            return;
        }
        census();
        if (freeSoldiers + freeCops == 0) {
            // Pull units off incidents if nobody is free.
            for (int i = 0, n = w.entities.size(); i < n; i++) {
                Entity e = w.entities.get(i);
                if (e.isArmed() && e.task == T_RESPOND) release(e);
            }
            census();
        }
        if (freeSoldiers + freeCops == 0) {
            say(WHO_INFO, null, "No police or military available to guard a safe zone. Spawn some first!", p[0], p[1]);
            return;
        }
        boolean military = freeSoldiers >= 2 || freeCops == 0;
        SafeZone z = establish(military, p);
        if (z != null && z.guards < z.wantGuards)
            assignGuards(z, z.wantGuards - z.guards, Float.MAX_VALUE, military ? Entity.COP : Entity.SOLDIER);
    }

    /** The Erase tool removes a safe zone when used on its centre. */
    boolean removeZoneAt(float x, float y) {
        for (int i = 0; i < zones.size(); i++) {
            SafeZone z = zones.get(i);
            float dx = z.x - x, dy = z.y - y;
            if (dx * dx + dy * dy < (z.r * 0.4f) * (z.r * 0.4f)) {
                z.removed = true;
                zones.remove(i);
                zonesDirty = true;
                say(WHO_INFO, null, "Safe zone at " + z.place + " closed.", z.x, z.y);
                return true;
            }
        }
        return false;
    }

    /** Recreates a safe zone from a save. */
    void restoreZone(float x, float y, float r, boolean military, String place, int capacity, int wantGuards) {
        SafeZone z = new SafeZone();
        z.x = x;
        z.y = y;
        z.initEdge(r, rnd);
        java.util.Arrays.fill(z.edge, r);
        z.r = r;
        z.military = military;
        z.place = place;
        z.capacity = capacity;
        z.wantGuards = wantGuards;
        z.age = 20;
        z.built = 1;
        z.open = true;
        zones.add(z);
        zonesDirty = true;
        if (!military) w.fleet.roadblocks(z);
    }

    /** True if some safe zone still has room. */
    boolean hasRoom() {
        for (int i = 0; i < zones.size(); i++) if (zones.get(i).open && !zones.get(i).full) return true;
        return false;
    }

    /** Lets a civilian into a zone if there is room. */
    boolean admit(Entity e, SafeZone z) {
        if (z.sheltered >= z.capacity) return false;
        z.sheltered++;
        sheltered++;
        e.task = T_SHELTER;
        e.zone = z;
        return true;
    }

    /** The safe zone containing (x, y), if any. */
    SafeZone zoneAt(float x, float y, float margin) {
        for (int i = 0; i < zones.size(); i++) {
            SafeZone z = zones.get(i);
            if (z.open && z.contains(x, y, margin)) return z;
        }
        return null;
    }

    private void updateZoneField() {
        zonesDirty = false;
        zoneFieldTimer = 2;
        // Only zones with room attract people.
        int n = 0;
        float[] xs = new float[zones.size()], ys = new float[zones.size()];
        for (int i = 0; i < zones.size(); i++) {
            if (zones.get(i).full || !zones.get(i).open) continue;
            xs[n] = zones.get(i).x;
            ys[n] = zones.get(i).y;
            n++;
        }
        city.walkFieldFromPoints(zoneField, xs, ys, n);
    }
}
