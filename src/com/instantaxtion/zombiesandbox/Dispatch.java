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
            T_CLEANUP = 14, T_LOOT = 15, T_PATROL = 16, T_BOARD = 17, T_RESCUE = 18, T_BORROW = 19, T_CORDON = 20, T_EVACUATE = 21;
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
        boolean militaryRequested, onScene, resolved, airRequested, swatRequested, riotRequested, policeAirRequested;
        /** Officers on scene radioed for more (10.20): how many they want there, and who asked. */
        int backupNeed;
        String backupBy;
        /** Seconds before the SWAT van tries again, when it couldn't find a way there. */
        float swatWait;
        int[] field;
        /** A post on the cordon round the outbreak (World.updateCordon), or -1 for a real call. */
        int cordonPost = -1;
    }

    /**
     * A safe zone (since 10.15): a walled compound made of a whole city block. A wall of concrete barriers
     * and fencing goes up round the block's edge (its buildings stand in for the wall where they reach it),
     * with gates onto the streets where guards check everyone for bites before letting them through. Inside:
     * rows of tents to sleep in, a medical tent and a food tent. The dead have to claw through the wall to get
     * in; guards repair the breaches. People inside eat through the rations; supply trucks bring more.
     */
    static final class SafeZone {
        float x, y, r, age, attackCd, statusTimer, quietTime, checkCd, emptyTime, breachCd, foodSaid;
        boolean military, removed, full, fullAnnounced;
        String place;
        int guards, sheltered, wantGuards, capacity;
        /** Ammunition the zone has on hand for its guards, and whether a supply truck is on its way. */
        int ammo = 300;
        boolean supplyComing;
        /** Rations for the people sheltering there, and as many as the stores hold. */
        float food, foodMax;
        /** Setting up: the wall goes up a section at a time (0 to 1). Nobody is let in until it opens. */
        float built;
        boolean open;
        /** How many guards are actually there (not still on their way). */
        int onSite;
        /** Half its size (for how near is near). */
        float baseR;
        /** The dead are inside the wall: the guards fall back on the tents. */
        boolean fallingBack;
        /** The way there, for the guards. */
        int[] field;
        /** The compound in tiles, wall included (inclusive). */
        int tx0, ty0, tx1, ty1;
        /** The wall: its tiles in order round the block, each section's strength (0 to 100) and whether it's up. */
        int[] wall = new int[0];
        float[] hp = new float[0];
        boolean[] up = new boolean[0];
        /** Gates: each one's two tiles, the spot just outside and just inside, strength, open or shut. */
        int[][] gateTiles = new int[0][];
        float[][] gateOut = new float[0][], gateIn = new float[0][];
        float[] gateHp = new float[0], gateHold = new float[0];
        boolean[] gateOpen = new boolean[0];
        /** Tents to sleep in: {x, y}; the medical tent and the food tent. */
        final ArrayList<float[]> tents = new ArrayList<float[]>();
        float medX, medY, foodX, foodY;
        /** Sections of wall down right now. */
        int breaches;

        float left() {
            return tx0 * City.T;
        }

        float top() {
            return ty0 * City.T;
        }

        float right() {
            return (tx1 + 1) * City.T;
        }

        float bottom() {
            return (ty1 + 1) * City.T;
        }

        /** Within the compound (the wall included), scaled about its middle by margin. */
        boolean contains(float px, float py, float margin) {
            float cx = (left() + right()) / 2, cy = (top() + bottom()) / 2;
            float hw = (right() - left()) / 2 * margin, hh = (bottom() - top()) / 2 * margin;
            return Math.abs(px - cx) < hw && Math.abs(py - cy) < hh;
        }

        /** Inside the wall. */
        boolean inside(float px, float py) {
            return px > left() + City.T && px < right() - City.T && py > top() + City.T && py < bottom() - City.T;
        }

        /** How far the wall is from the middle in the direction of (px, py). */
        float reach(float px, float py) {
            float dx = px - x, dy = py - y, l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l < 0.001f) return baseR;
            dx /= l;
            dy /= l;
            float tx = dx > 0.0001f ? (right() - x) / dx : dx < -0.0001f ? (left() - x) / dx : Float.MAX_VALUE;
            float ty = dy > 0.0001f ? (bottom() - y) / dy : dy < -0.0001f ? (top() - y) / dy : Float.MAX_VALUE;
            return Math.min(tx, ty);
        }

        float edgeAt(float a) {
            return reach(x + (float) Math.cos(a), y + (float) Math.sin(a));
        }

        float area() {
            return (right() - left()) * (bottom() - top());
        }

        /** The spot just inside a wall tile (where a guard stands at it). */
        float[] innerOf(int t, int w) {
            int tx = t % w, ty = t / w;
            float x = (tx + 0.5f) * City.T, y = (ty + 0.5f) * City.T;
            if (ty == ty0) y += City.T;
            else if (ty == ty1) y -= City.T;
            if (tx == tx0) x += City.T;
            else if (tx == tx1) x -= City.T;
            return new float[]{x, y};
        }

        /** The nearest gate to (px, py), or -1. */
        int gateNear(float px, float py) {
            int best = -1;
            float bd = Float.MAX_VALUE;
            for (int g = 0; g < gateOut.length; g++) {
                float d = (gateOut[g][0] - px) * (gateOut[g][0] - px) + (gateOut[g][1] - py) * (gateOut[g][1] - py);
                if (d < bd) {
                    bd = d;
                    best = g;
                }
            }
            return best;
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
        /** Police from outside (10.20): whose (0 another city's police, 1 highway patrol, 2 the sheriff, 4 federal agents), a SWAT team, and the name on the column. */
        int agency;
        boolean swat;
        String label;
        int tries;
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
    /** SWAT teams still at the precinct, ready to go. */
    int swatTeams;
    /** Riot vans the police can send, and police helicopter flights left (since 10.10). */
    int riotVans, policeAir;
    private float ambulanceTimer;
    int calls, sheltered, messageCount;
    int copCount, soldierCount;
    private int freeCops, freeSoldiers;
    private float tick, zoneFieldTimer, policeZoneCd = 10, militaryZoneCd = 6, militaryCd, policeCd;
    private boolean zonesDirty = true;

    private static final int[] POLICE_RESERVE = {0, 2, 3, 5}, SQUAD_RESERVE = {0, 2, 4, 6}, AIR_SORTIES = {0, 1, 2, 3},
            TANKS = {0, 1, 2, 3};
    /** Officers in each wave of police backup. */
    static final int BACKUP_OFFICERS = 6;

    /**
     * Reinforcements on their way in from outside the city (10.17): where they came onto the map, where
     * they're going, and the vehicles (or people on foot) carrying them, for the map to show.
     */
    static final class Convoy {
        int type;
        String label, place;
        float fromX, fromY, toX, toY, age;
        final ArrayList<Fleet.Vehicle> vehicles = new ArrayList<Fleet.Vehicle>();
        final ArrayList<Entity> onFoot = new ArrayList<Entity>();
        /** Where the head of the column is now (updated each frame). */
        float x, y;
        /** Vehicles still waiting to come onto the map (10.20: a column comes on one at a time). */
        int pending;
    }

    /** One vehicle of a column waiting at the edge of the map for the road in to be clear. */
    private static final class Queued {
        Convoy cv;
        int type, count, agency, vkind;
        boolean swat, tank;
        float x, y, tx, ty;
        Incident inc;
        SafeZone zone;
        String place;
        int tries;
        /** Seconds this one has waited at its way in for the road to clear. */
        float wait;
    }

    private final ArrayList<Queued> column = new ArrayList<Queued>();

    private void enqueue(Convoy cv, int type, int count, float[] p, float tx, float ty, Incident inc, SafeZone zone, String place) {
        Queued q = new Queued();
        q.cv = cv;
        q.type = type;
        q.count = count;
        q.x = p[0];
        q.y = p[1];
        q.tx = tx;
        q.ty = ty;
        q.inc = inc;
        q.zone = zone;
        q.place = place;
        column.add(q);
        cv.pending++;
    }

    /**
     * Brings the waiting columns onto the map, one vehicle at a time at each way in, each as soon as the one
     * in front has pulled clear: no pile-up at the edge of the map.
     */
    private void feedColumns(float dt) {
        if (column.isEmpty()) return;
        ArrayList<float[]> used = new ArrayList<float[]>();
        for (int i = 0; i < column.size(); i++) {
            Queued q = column.get(i);
            boolean taken = false;
            for (float[] u : used) if (Math.abs(u[0] - q.x) < 1 && Math.abs(u[1] - q.y) < 1) taken = true;
            if (taken) continue;
            used.add(new float[]{q.x, q.y});
            boolean clear = true;
            for (int k = 0; k < w.fleet.vehicles.size() && clear; k++) {
                Fleet.Vehicle v = w.fleet.vehicles.get(k);
                if (Fleet.airborne(v) || Math.abs(v.x - q.x) >= 24 || Math.abs(v.y - q.y) >= 24) continue;
                // (An abandoned or broken-down car on the way in is towed off; anything else, wait for it.)
                if (w.fleet.clearFromEntry(v)) k--;
                else clear = false;
            }
            if (!clear) {
                q.wait += dt;
                if (q.wait > 10) {
                    // Blocked for good here: the rest of the column tries another way in.
                    q.wait = 0;
                    java.util.List<float[]> from = entries(q.tx, q.ty);
                    for (float[] e : from) {
                        if (Math.abs(e[0] - q.x) < 1 && Math.abs(e[1] - q.y) < 1) continue;
                        if (!w.fleet.canDrive(e[0], e[1], q.tx, q.ty)) continue;
                        float ox = q.x, oy = q.y;
                        for (Queued o : column)
                            if (o.cv == q.cv && Math.abs(o.x - ox) < 1 && Math.abs(o.y - oy) < 1) {
                                o.x = e[0];
                                o.y = e[1];
                            }
                        q.cv.fromX = e[0];
                        q.cv.fromY = e[1];
                        break;
                    }
                }
                continue;
            }
            column.remove(i--);
            q.cv.pending--;
            int before = w.fleet.vehicles.size();
            boolean sent;
            if (q.tank) sent = w.fleet.sendTank(q.x, q.y, q.tx, q.ty, q.place);
            else sent = w.fleet.send(q.type, q.count, q.x, q.y, q.tx, q.ty, q.inc, q.zone, q.place);
            if (!sent || w.fleet.vehicles.size() == before) {
                // (No route this moment: back of the line to try again, never left off the map.)
                if (++q.tries < 20) {
                    column.add(q);
                    q.cv.pending++;
                } else if (q.type == Entity.COP) policeReserve += q.count;
                continue;
            }
            Fleet.Vehicle v = w.fleet.vehicles.get(w.fleet.vehicles.size() - 1);
            v.timer = 0.3f;
            v.reinforcement = true;
            v.agency = q.agency;
            if (q.vkind != 0) {
                w.fleet.setKind(v, q.vkind);
                if (q.vkind == Fleet.K_HUMVEE) v.passengers = Math.min(v.passengers, 4);
            }
            if (q.swat) {
                v.swat = true;
                v.passengerType = Entity.COP;
            }
            q.cv.vehicles.add(v);
        }
    }

    final ArrayList<Convoy> convoys = new ArrayList<Convoy>();

    /**
     * Help from outside the city (10.20) is either on or off. On: about 220 police officers in waves from the
     * towns around (other cities' police, highway patrol, sheriff's deputies, SWAT teams and federal agents),
     * quick to come; and about 120 soldiers, slow to come but all at once in one big convoy with armour, flown
     * cover by the helicopters on the map. No helicopters come from outside.
     */
    static final int OUTSIDE_POLICE = 220, OUTSIDE_SOLDIERS = 120;

    Dispatch(World w, int reinforcementLevel) {
        this.w = w;
        this.city = w.city;
        policeReserve = reinforcementLevel > 0 ? OUTSIDE_POLICE : 0;
        squadReserve = reinforcementLevel > 0 ? OUTSIDE_SOLDIERS : 0;
        airSorties = 0;
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
            if (e.agency == 3) return "Ranger " + e.callsign;
            if (e.agency == 4) return federal(c) + " " + e.callsign;
            if (e.role == Entity.ROLE_PILOT) return "Pilot " + e.callsign;
            if (e.role == Entity.ROLE_SAR) return "Rescue " + e.callsign;
            return "Unit " + e.callsign;
        }
        if (e.type == Entity.SOLDIER && e.role == Entity.ROLE_PILOT) return "Air " + e.callsign + " pilot";
        if (e.type == Entity.SOLDIER) return SQUADS[Math.max(0, e.squad) % SQUADS.length] + "-" + e.member;
        return "Caller";
    }

    /** The federal police: the FBI in the USA, the national police elsewhere. */
    static String federal(Country c) {
        return c == null || c.id == Country.USA ? "FBI" : "National Police";
    }

    // ------------------------------------------------------------------ radio calls on the map (10.20)

    /** A call for help, shown on the map for a few seconds where it was made: rings going out, and a tag. */
    static final class Callout {
        float x, y, age;
        String label;
        int color;
    }

    final ArrayList<Callout> callouts = new ArrayList<Callout>();

    void callout(float x, float y, String label, int color) {
        for (int i = 0; i < callouts.size(); i++) {
            Callout c = callouts.get(i);
            if (c.age < 3 && Math.hypot(c.x - x, c.y - y) < 60 && c.label.equals(label)) return;
        }
        Callout c = new Callout();
        c.x = x;
        c.y = y;
        c.label = label;
        c.color = color;
        callouts.add(c);
        if (callouts.size() > 30) callouts.remove(0);
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
        convoys.clear();
        log.clear();
        zonesDirty = true;
    }

    // ------------------------------------------------------------------ 911

    /** A civilian who saw a zombie calls 911. */
    void call(Entity caller, Entity zombie) {
        calls++;
        caller.phoneTimer = 3f;
        w.emit(Sfx.PHONE, caller.x, caller.y);
        // With the call centre overrun, most calls just ring out.
        if (w.callsLost && rnd.nextFloat() < 0.6f) {
            if (w.time - unansweredSaid > 40) {
                unansweredSaid = w.time;
                say(WHO_911, null, "(The line rings and rings. Nobody at " + city.callCentre.name + " picks up.)", caller.x, caller.y);
            }
            return;
        }
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
        for (int i = callouts.size() - 1; i >= 0; i--) if ((callouts.get(i).age += dt) > 6) callouts.remove(i);
        updateArmy(dt);
        feedColumns(dt);
        updateAir(dt);
        updateConvoys(dt);
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
        updateGates(dt);
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
        sweep(step);
        updateIncidents(step);
        outsideFlow();
        mobilise(step);
        baseLife(step);
        updateZones(step);
    }

    private float baseCd = 5;

    /**
     * The bases don't just wait to be called (cities built since 10.10): once the city knows, a Humvee patrol
     * sweeps the roads near each base every few minutes; a call close by that has no soldiers gets a quick
     * reaction force in a Humvee straight away; and when the dead reach the wire the alarm goes up (and the
     * patrols wait until it's quiet again).
     */
    private void baseLife(float step) {
        baseCd -= step;
        if (baseCd > 0 || !city.cfg.nature()) return;
        baseCd = 4;
        for (City.Facility base : city.facilities) {
            if (base.kind != City.FACILITY_BASE) continue;
            base.patrolCd -= 4;
            float reach = Math.max(220, base.r * 2.5f);
            ArrayList<Entity> free = new ArrayList<Entity>();
            for (int i = 0, n = w.entities.size(); i < n; i++) {
                Entity e = w.entities.get(i);
                if (e.dead || e.type != Entity.SOLDIER || e.task != T_NONE || e.rig != null) continue;
                if (e.role == Entity.ROLE_GUARD || e.role == Entity.ROLE_COMMANDER || e.role == Entity.ROLE_SNIPER) continue;
                if (e.ammo <= 0 && e.reserve <= 0) continue;
                if ((e.x - base.x) * (e.x - base.x) + (e.y - base.y) * (e.y - base.y) > reach * reach) continue;
                free.add(e);
            }
            int near = w.countZombiesNear(base.x, base.y, base.r * 1.8f + 60);
            if (near >= 4 && !base.alarm) {
                base.alarm = true;
                base.quietFor = 0;
                w.noise(base.x, base.y, 420);
                say(WHO_MILITARY, null, base.name + ": Contact at the wire! Sound the alarm, all hands to the perimeter.", base.x, base.y);
            } else if (base.alarm) {
                base.quietFor = near == 0 ? base.quietFor + 4 : 0;
                if (base.quietFor >= 40) {
                    base.alarm = false;
                    say(WHO_MILITARY, null, base.name + ": Perimeter secure. Stand down the alarm.", base.x, base.y);
                }
            }
            if (base.alarm || free.size() < 6) continue;
            // A call close by with no soldiers at it: the quick reaction force goes now.
            Incident close = null;
            float cd = 700 * 700;
            for (int i = 0; i < incidents.size(); i++) {
                Incident inc = incidents.get(i);
                if (inc.resolved || inc.zombiesNear < 4 || inc.soldiers > 0 || w.fleet.inboundSoldiers(inc) > 0) continue;
                float d = (inc.x - base.x) * (inc.x - base.x) + (inc.y - base.y) * (inc.y - base.y);
                if (d < cd) {
                    cd = d;
                    close = inc;
                }
            }
            if (close != null && w.alert >= 1) {
                ArrayList<Entity> qrf = nearestOf(free, close.x, close.y, 3);
                if (w.fleet.troopTruck(qrf, base, close, Fleet.K_HUMVEE)) {
                    close.militaryRequested = true;
                    say(WHO_MILITARY, null, base.name + ": Quick reaction force rolling out to " + close.place + ".", close.x, close.y);
                    continue;
                }
            }
            // A patrol round the roads near the base.
            if (w.alert >= 1 && base.patrolCd <= 0 && free.size() >= 7 && w.fleet.basePatrols(base) == 0) {
                base.patrolCd = 120 + w.rnd.nextFloat() * 60;
                float a = w.rnd.nextFloat() * (float) Math.PI * 2, dist = 450 + w.rnd.nextFloat() * 350;
                float[] to = city.nearestDrivable(base.x + (float) Math.cos(a) * dist, base.y + (float) Math.sin(a) * dist);
                if (to != null) w.fleet.basePatrol(nearestOf(free, base.x, base.y, 3), base, to[0], to[1]);
            }
        }
    }

    /** The n of these nearest to (x, y). */
    private static ArrayList<Entity> nearestOf(ArrayList<Entity> from, final float x, final float y, int n) {
        ArrayList<Entity> sorted = new ArrayList<Entity>(from);
        java.util.Collections.sort(sorted, new java.util.Comparator<Entity>() {
            @Override
            public int compare(Entity a, Entity b) {
                return Float.compare((a.x - x) * (a.x - x) + (a.y - y) * (a.y - y), (b.x - x) * (b.x - x) + (b.y - y) * (b.y - y));
            }
        });
        while (sorted.size() > n) sorted.remove(sorted.size() - 1);
        return sorted;
    }

    private float sweepCd = 3, mobiliseCd = 2, unansweredSaid = -100;

    /**
     * Command keeps the whole outbreak in mind, not just the last 911 call: once the city knows, every few
     * seconds the worst clusters of the dead that nobody is dealing with become calls of their own (sightings
     * from patrols, cameras and the public), so units clearing one scene are sent straight on to the next.
     */
    private void sweep(float step) {
        sweepCd -= step;
        if (sweepCd > 0 || w.alert < 1) return;
        // The call centre watches the traffic cameras: it spots trouble sooner and keeps more calls going.
        // With it overrun, nobody's watching.
        boolean centre = city.callCentre != null;
        if (centre && w.callsLost) return;
        sweepCd = centre ? 2 : 3;
        int max = (city.w >= 400 ? 18 : city.w >= 300 ? 14 : 12) + (centre ? 4 : 0);
        if (incidents.size() >= max) return;
        int cell = 192, gw = (int) (city.worldW() / cell) + 1, gh = (int) (city.worldH() / cell) + 1;
        int[] count = new int[gw * gh];
        float[] sx = new float[gw * gh], sy = new float[gw * gh];
        for (int i = 0, n = w.entities.size(); i < n; i++) {
            Entity z = w.entities.get(i);
            if (z.dead || !z.isZombie() || z.hidden) continue;
            int c = Math.min(gh - 1, (int) (z.y / cell)) * gw + Math.min(gw - 1, (int) (z.x / cell));
            count[c]++;
            sx[c] += z.x;
            sy[c] += z.y;
        }
        int least = w.alert >= 2 ? 1 : 2;
        for (int made = 0; made < 1 && incidents.size() < max; made++) {
            int best = -1;
            for (int c = 0; c < count.length; c++) {
                if (count[c] < least || (best >= 0 && count[c] <= count[best])) continue;
                float cx = sx[c] / count[c], cy = sy[c] / count[c];
                // (Cameras watch the town; out in the wilds it takes someone there to see them.)
                if (!city.inTown(cx, cy) && !w.peopleNear(cx, cy, 300)) continue;
                boolean covered = false;
                for (int i = 0; i < incidents.size() && !covered; i++) {
                    Incident inc = incidents.get(i);
                    if ((inc.x - cx) * (inc.x - cx) + (inc.y - cy) * (inc.y - cy) < 260 * 260) covered = true;
                }
                if (!covered) best = c;
            }
            if (best < 0) return;
            float cx = sx[best] / count[best], cy = sy[best] / count[best];
            count[best] = 0;
            float[] p = city.findWalkable(cx, cy);
            if (p == null) continue;
            Incident inc = new Incident();
            inc.x = p[0];
            inc.y = p[1];
            inc.place = city.placeName(p[0], p[1]);
            inc.reported = inc.lastLogged = Math.max(1, w.countZombiesNear(p[0], p[1], 140));
            inc.field = new int[city.w * city.h];
            city.walkFieldFromPoints(inc.field, new float[]{inc.x}, new float[]{inc.y}, 1);
            incidents.add(inc);
            say(WHO_POLICE, null, "Dispatch: Patrols report " + inc.reported + " of them at " + inc.place
                    + ". All available units.", inc.x, inc.y);
        }
    }

    /**
     * Once the city is at full alert, the army doesn't sit at the base: free squads mount up and go to the
     * worst fighting the police have (by truck from the base when it's a long way), a squad to a call.
     */
    private void mobilise(float step) {
        mobiliseCd -= step;
        if (mobiliseCd > 0) return;
        mobiliseCd = 2;
        boolean war = w.alert >= 2;
        // The calls that need soldiers, worst first.
        Incident target = null;
        for (int i = 0; i < incidents.size(); i++) {
            Incident inc = incidents.get(i);
            if (inc.resolved) continue;
            int have = inc.soldiers + w.fleet.inboundSoldiers(inc);
            boolean wants = inc.militaryRequested || (war && inc.zombiesNear >= 3);
            if (!wants || have >= 3) continue;
            if (target == null || inc.zombiesNear > target.zombiesNear) target = inc;
        }
        if (target == null) return;
        // Free squads (the National Guard looks after the safe zones), nearest first; one stays home unless
        // the war is on.
        java.util.HashMap<Integer, ArrayList<Entity>> squads = new java.util.HashMap<Integer, ArrayList<Entity>>();
        for (int i = 0, n = w.entities.size(); i < n; i++) {
            Entity e = w.entities.get(i);
            if (e.dead || e.type != Entity.SOLDIER || e.task != T_NONE || e.role == Entity.ROLE_GUARD || e.rig != null) continue;
            if (e.ammo <= 0 && e.reserve <= 0) continue;
            ArrayList<Entity> sq = squads.get(e.squad);
            if (sq == null) squads.put(e.squad, sq = new ArrayList<Entity>());
            sq.add(e);
        }
        if (squads.isEmpty() || (!war && squads.size() < 2)) return;
        ArrayList<Entity> pick = null;
        float bd = Float.MAX_VALUE, px = 0, py = 0;
        for (ArrayList<Entity> sq : squads.values()) {
            float x = 0, y = 0;
            for (Entity e : sq) {
                x += e.x;
                y += e.y;
            }
            x /= sq.size();
            y /= sq.size();
            float d = (x - target.x) * (x - target.x) + (y - target.y) * (y - target.y);
            if (d < bd) {
                bd = d;
                pick = sq;
                px = x;
                py = y;
            }
        }
        Entity lead = pick.get(0);
        for (Entity e : pick) if (e.role == Entity.ROLE_COMMANDER) lead = e;
        String squadName = SQUADS[Math.max(0, lead.squad) % SQUADS.length];
        // A long way from the base: by truck.
        City.Facility base = city.nearestFacility(City.FACILITY_BASE, px, py);
        boolean atBase = base != null && Math.hypot(base.x - px, base.y - py) < Math.max(200, base.r * 2.5f);
        if (atBase && bd > 600 * 600 && w.fleet.troopTruck(pick, base, target)) {
            say(WHO_MILITARY, null, "Command: " + squadName + " squad, mount up. Support the police at " + target.place + ".",
                    target.x, target.y);
            target.militaryRequested = true;
            return;
        }
        for (Entity e : pick) {
            e.task = T_RESPOND;
            e.incident = target;
            e.onScene = false;
            target.soldiers++;
        }
        target.militaryRequested = true;
        say(WHO_MILITARY, lead, squadName + " squad moving to " + target.place + " to support the police.", target.x, target.y);
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
        if (airDelay <= 0 && w.fleet.readyHeli(0, x, y) != null) {
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
        return airDelay > 0 || w.fleet.readyHeli(0, airX, airY) == null;
    }

    /**
     * Air support takes time: the request goes up the chain, the crew is briefed and the aircraft is fuelled and
     * armed. A helicopter on a pad in town is quicker than one flying in from outside the city.
     */
    /**
     * Air support (10.20): only what's on the map. An army helicopter sitting on its pad with a pilot is
     * approved, the crew briefed, then they run out to it and climb aboard with door gunners and troops. No
     * helicopters come from outside the city.
     */
    private void requestAir(float x, float y, String place) {
        if (airDelay > 0) return;
        if (w.fleet.readyHeli(0, x, y) == null) {
            if (w.time - noAirSaid > 60 && w.fleet.helisOfKind(0) > 0) {
                noAirSaid = w.time;
                say(WHO_MILITARY, null, "Military: Negative on air support for " + place + ", every helicopter is up or rearming.", x, y);
            }
            return;
        }
        airX = x;
        airY = y;
        airPlace = place;
        airIncident = null;
        for (int i = 0; i < incidents.size(); i++)
            if (Math.hypot(incidents.get(i).x - x, incidents.get(i).y - y) < 200) airIncident = incidents.get(i);
        airDelay = 12 + w.rnd.nextFloat() * 10;
        say(WHO_MILITARY, null, "Military: Air support approved for " + place + ". Crew to the helicopter.", x, y);
    }

    private float noAirSaid = -100;
    private Incident airIncident;

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
        Fleet.Vehicle heli = w.fleet.readyHeli(0, x, y);
        // Pilot, two door gunners and four troops to put down.
        if (heli == null || !w.fleet.launchHeli(heli, x, y, place, airIncident != null && !airIncident.resolved ? airIncident : null, 6)) return;
        say(WHO_MILITARY, null, "Military: Air " + heli.number + ", crew and troops are boarding for " + place + ".", x, y);
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
            int need = w.alert >= 2 ? Math.min(6, 2 + inc.zombiesNear / 3) : Math.min(4, 1 + inc.zombiesNear / 3);
            // (Officers there asked for more: they get what they asked for, if it's to be had.)
            if (inc.backupNeed > 0) need = Math.max(need, Math.min(12, inc.backupNeed));
            int have = inc.cops + inc.soldiers + w.fleet.inbound(inc);
            // The nearest patrol car takes the call first; officers on foot make up the rest.
            if (have < need) {
                Fleet.Vehicle car = w.fleet.patrolFor(inc.x, inc.y);
                if (car != null && w.fleet.respond(car, inc)) have += car.crew.size();
            }
            if (have < need) {
                int got = assign(inc, Entity.COP, need - have);
                have += got;
                // Nobody free in town to send (or far too few for a big one): help from outside.
                if ((got == 0 && have == 0) || (have < need / 2 && inc.zombiesNear >= 6) || (inc.backupNeed > have && got == 0)) policeBackup(inc);
            }
            if (inc.backupNeed > 0 && have >= inc.backupNeed) inc.backupNeed = 0;
            // A big one, or officers down: the SWAT team goes in (before the army is asked).
            inc.swatWait -= step;
            if (!inc.swatRequested && swatTeams > 0 && inc.swatWait <= 0 && (inc.zombiesNear >= 6 || (inc.officersDown > 0 && inc.zombiesNear >= 3))) {
                inc.swatRequested = true;
                sendSwat(inc);
            }
            // A crowd of the dead: the riot van brings six officers with shields to hold a line.
            if (!inc.riotRequested && riotVans > 0 && inc.zombiesNear >= 10) {
                inc.riotRequested = true;
                City.Facility station = city.nearestFacility(City.FACILITY_POLICE, inc.x, inc.y);
                if (station != null && w.fleet.sendRiotVan(station.gateX, station.gateY, inc)) {
                    riotVans--;
                    say(WHO_POLICE, null, "Dispatch: Riot van leaving " + station.name + " for " + inc.place
                            + ". Six officers with shields.", station.gateX, station.gateY);
                }
            }
            // The police helicopter goes up over the bigger ones.
            if (!inc.policeAirRequested && inc.zombiesNear >= 8) {
                // (The police helicopter on its pad, when it's ready: the pilot and a marksman run out to it.)
                Fleet.Vehicle heli = w.fleet.readyHeli(Fleet.K_POLICE_HELI, inc.x, inc.y);
                if (heli != null && w.fleet.launchHeli(heli, inc.x, inc.y, inc.place, inc, 1)) {
                    inc.policeAirRequested = true;
                    say(WHO_POLICE, null, "Dispatch: Police helicopter, " + inc.place + ". Pilot and marksman to the pad.", inc.x, inc.y);
                }
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
            if (!armyCalled) say(WHO_MILITARY, null, "Military: Negative, no units or reserves left. Hold them off, Police.", x, y);
            else say(WHO_MILITARY, null, "Military: Nothing free in the city. The convoy is "
                    + (armyEta > 0 ? "still " + Math.max(1, Math.round(armyEta / 60f)) + " minute" + (Math.round(armyEta / 60f) > 1 ? "s" : "") + " out." : "on its way in."), x, y);
        }
    }

    /**
     * The army from outside (10.20): asked for once, it takes its time (the slowest help there is, several
     * minutes to mobilise) and then comes all at once, the whole convoy together. Returns false if there's no
     * army coming (help from outside is off, or it has already come).
     */
    private boolean sendReserveSquad(float x, float y, String place, Incident inc, SafeZone zone) {
        if (squadReserve <= 0 || armyArrived) return false;
        callout(x, y, "ARMY REQUESTED", WHO_COLORS[WHO_MILITARY]);
        if (armyCalled) return true;
        armyCalled = true;
        armyEta = Math.max(150, 330 - w.outbreakTime) + rnd.nextFloat() * 60;
        int mins = Math.max(2, Math.round(armyEta / 60f));
        say(WHO_MILITARY, null, "Military: Request approved. A full convoy is mobilising outside the city: " + squadReserve
                + " soldiers, armour and air cover from the base. It moves as one. ETA about " + mins + " minutes.", x, y);
        return true;
    }

    boolean armyCalled, armyArrived;
    float armyEta;
    private boolean armySaid;

    private void updateArmy(float dt) {
        if (!armyCalled || armyArrived) return;
        // (A city with no army of its own, losing badly, gets the convoy without waiting to be asked.)
        armyEta -= dt;
        if (armyEta < 60 && !armySaid) {
            armySaid = true;
            say(WHO_MILITARY, null, "Military: The convoy is a minute out. All units, hold on.", city.worldW() / 2, city.worldH() / 2);
        }
        if (armyEta <= 0) armyConvoy();
    }

    /** The army comes in: one column of trucks, Humvees, APCs and tanks along the highway, to the worst of it. */
    private void armyConvoy() {
        armyArrived = true;
        // Where the dead are thickest.
        Incident target = null;
        for (int i = 0; i < incidents.size(); i++) {
            Incident inc = incidents.get(i);
            if (inc.cordonPost < 0 && (target == null || inc.zombiesNear > target.zombiesNear)) target = inc;
        }
        float tx = target != null ? target.x : city.worldW() / 2, ty = target != null ? target.y : city.worldH() / 2;
        String place = target != null ? target.place : city.placeName(tx, ty);
        java.util.List<float[]> from = entries(tx, ty);
        if (from.isEmpty()) return;
        int soldiers = squadReserve;
        squadReserve = 0;
        float[] p = null;
        for (float[] q : from)
            if (w.fleet.canDrive(q[0], q[1], tx, ty)) {
                p = q;
                break;
            }
        if (p == null) {
            squadReserve = soldiers;
            armyArrived = false;
            armyEta = 30;
            return;
        }
        Convoy cv = trackConvoy(Entity.SOLDIER, "ARMY CONVOY", w.fleet.vehicles.size(), p[0], p[1], tx, ty, place);
        // One column: Humvees leading, trucks with APCs among them, two tanks at the back.
        int k = 0, trucks = 0;
        for (int left = soldiers; left > 0; k++, trucks++) {
            int n = Math.min(left, k < 2 ? 4 : 8);
            left -= n;
            enqueue(cv, Entity.SOLDIER, n, p, tx, ty, target, null, place);
            if (city.cfg.nature()) column.get(column.size() - 1).vkind = k < 2 ? Fleet.K_HUMVEE : k % 4 == 3 ? Fleet.K_APC : 0;
        }
        int tanks = 2;
        for (int t = 0; t < tanks; t++) {
            enqueue(cv, Entity.SOLDIER, 0, p, tx, ty, target, null, place);
            column.get(column.size() - 1).tank = true;
        }
        // Air cover: every army helicopter that's ready takes off to fly over the column.
        int air = 0;
        for (int hk = 0; hk < 4; hk++) {
            Fleet.Vehicle h = w.fleet.readyHeli(0, p[0], p[1]);
            if (h == null || !w.fleet.launchHeli(h, p[0], p[1], "the convoy", null, 2)) break;
            h.escort = cv;
            air++;
        }
        boolean hwy = city.onHighway(p[0], p[1]);
        say(WHO_MILITARY, null, "Military: The army convoy is entering the city from " + sideOfMap(p[0], p[1])
                + (hwy && city.hwyName != null ? " on " + city.hwyName : "") + ": " + soldiers + " soldiers in " + trucks
                + " vehicles" + (tanks > 0 ? ", " + tanks + " tank" + (tanks > 1 ? "s" : "") : "")
                + (air > 0 ? ", " + air + " helicopter" + (air > 1 ? "s" : "") + " flying cover" : "") + ". Heading for " + place + ".", p[0], p[1]);
        callout(p[0], p[1], "ARMY CONVOY", WHO_COLORS[WHO_MILITARY]);
    }

    /** Calls in one of the limited reserve squads (before 10.20). Returns false if none are left. */
    private boolean sendSmallReserve(float x, float y, String place, Incident inc, SafeZone zone) {
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
        say(WHO_MILITARY, null, "Military: Copy. Reserve squad inbound from outside the city to " + place + "."
                + (squadReserve == 0 ? " That's our last one." : " " + squadReserve + " left in reserve."), x, y);
        return true;
    }

    private int policeWaves, lastWave = -1;
    private final ArrayList<Integer> waveBag = new ArrayList<Integer>();
    /** Help from outside has been asked for once: from then on it keeps coming while the outbreak is serious. */
    boolean outsideStarted;

    /**
     * Once the city has asked for outside help, the towns around keep sending waves (one every ten seconds or
     * so) to wherever it's worst, for as long as the outbreak is serious and they have officers to send; and
     * the army is called in by itself when it gets bad enough, even if nobody asks.
     */
    private void outsideFlow() {
        if (w.alert < 1) return;
        Incident worst = null;
        for (int i = 0; i < incidents.size(); i++) {
            Incident inc = incidents.get(i);
            if (inc.resolved || inc.cordonPost >= 0 || inc.zombiesNear < 3) continue;
            if (worst == null || inc.zombiesNear > worst.zombiesNear) worst = inc;
        }
        if (worst == null) return;
        // (A serious outbreak: the towns around don't wait to be asked twice.)
        if (w.alert >= 2 || w.zombies >= 25) outsideStarted = true;
        if (outsideStarted && policeReserve > 0 && policeCd <= 0 && w.zombies >= 12) policeBackup(worst);
        if (!armyCalled && squadReserve > 0 && w.alert >= 2 && w.zombies >= 60)
            sendReserveSquad(worst.x, worst.y, worst.place, worst, null);
    }
    private final ArrayList<String> neighbours = new ArrayList<String>();

    /**
     * Help from the police outside the city (10.20): a wave from the next agency in turn, another town's
     * police, highway patrol, a SWAT team, the sheriff's deputies, federal agents. They drive in from the edge
     * of the map in their own cars, a minute or less away.
     */
    private boolean policeBackup(Incident inc) {
        if (policeReserve <= 0 || policeCd > 0) return false;
        policeCd = 10;
        outsideStarted = true;
        Country c = city.country;
        // (Who comes next is a random mix: each force in turn from a shuffled round, so any of them can be first.)
        if (waveBag.isEmpty()) {
            for (int k = 0; k < 6; k++) waveBag.add(k);
            java.util.Collections.shuffle(waveBag, rnd);
            if (waveBag.get(0) == lastWave && waveBag.size() > 1) java.util.Collections.swap(waveBag, 0, 1 + rnd.nextInt(waveBag.size() - 1));
        }
        int kind = waveBag.remove(0);
        lastWave = kind;
        policeWaves++;
        if (kind == 3 && c.ruralShort == null) kind = 0;
        Arrival a = new Arrival();
        a.type = Entity.COP;
        String sentence;
        switch (kind) {
            case 1:
                a.agency = 1;
                a.count = 6;
                a.label = c.hpName;
                sentence = "The " + c.hpName + " is sending %d officers";
                break;
            case 2:
                a.swat = true;
                a.count = 6;
                a.label = neighbour(policeWaves + 7) + " " + Country.swat(c.id)[0];
                sentence = neighbour(policeWaves + 7) + " is sending its " + Country.swat(c.id)[0] + " team, %d officers";
                break;
            case 3:
                a.agency = 2;
                a.count = 8;
                a.label = c.ruralName + "'s Office";
                sentence = "The " + c.ruralName + "'s office is sending %d deputies";
                break;
            case 5:
                a.agency = 4;
                a.count = 8;
                a.label = federal(c);
                sentence = "The " + federal(c) + " is sending %d agents";
                break;
            default:
                a.count = 10;
                a.label = neighbour(policeWaves) + " Police";
                sentence = "The " + neighbour(policeWaves) + " police are sending %d officers";
                break;
        }
        a.count = Math.min(a.count, policeReserve);
        policeReserve -= a.count;
        a.time = 4 + rnd.nextFloat() * 5;
        a.incident = inc;
        a.x = inc.x;
        a.y = inc.y;
        a.place = inc.place;
        arrivals.add(a);
        say(WHO_POLICE, null, "Dispatch: All our units are committed. Requesting outside help for " + inc.place + ". "
                + String.format(sentence, a.count) + ", less than a minute out." + (policeReserve == 0 ? " That's the last of the help from outside." : ""), inc.x, inc.y);
        callout(inc.x, inc.y, "OUTSIDE BACKUP", WHO_COLORS[WHO_POLICE]);
        return true;
    }

    /** A town near this one (the same few, for the whole game). */
    private String neighbour(int i) {
        while (neighbours.size() < 4) {
            String n = city.country.townName(new Random(w.city.cfg.seed * 31 + neighbours.size() * 7 + 3));
            if (!n.equals(city.name) && !neighbours.contains(n)) neighbours.add(n);
            else neighbours.add(n + (neighbours.size() + 1 == 2 ? " Heights" : " Falls"));
        }
        return neighbours.get(Math.abs(i) % neighbours.size());
    }

    /**
     * An officer or soldier in a fight that's too big for them radios for backup (10.20): the call goes on the
     * map where they are, Dispatch sends whoever is free (and outside help if nobody is), and the call shows
     * until enough have got there.
     */
    void unitBackup(Entity e, int hostiles) {
        Incident inc = null;
        for (int i = 0; i < incidents.size(); i++) {
            Incident o = incidents.get(i);
            if (!o.resolved && o.cordonPost < 0 && Math.hypot(o.x - e.x, o.y - e.y) < 220) inc = o;
        }
        if (inc == null) {
            if (incidents.size() >= 20) return;
            inc = new Incident();
            inc.x = e.x;
            inc.y = e.y;
            inc.place = city.placeName(e.x, e.y);
            inc.reported = inc.lastLogged = hostiles;
            inc.field = new int[city.w * city.h];
            city.walkFieldFromPoints(inc.field, new float[]{inc.x}, new float[]{inc.y}, 1);
            incidents.add(inc);
        }
        int want = Math.min(12, 2 + hostiles / 2);
        if (want <= inc.backupNeed) return;
        inc.backupNeed = want;
        inc.backupBy = name(e);
        boolean army = e.type == Entity.SOLDIER;
        say(army ? WHO_MILITARY : WHO_POLICE, e, (army ? "Contact at " : "Shots fired at ") + inc.place + ", " + hostiles
                + " hostiles on us. Requesting backup!", e.x, e.y);
        callout(e.x, e.y, "BACKUP", WHO_COLORS[army ? WHO_MILITARY : WHO_POLICE]);
        if (army) {
            // Soldiers send soldiers.
            int have = inc.soldiers + w.fleet.inboundSoldiers(inc);
            if (have < want) {
                ArrayList<Entity> squad = nearestFree(Entity.SOLDIER, e.x, e.y, Math.min(4, want - have), 1500);
                int n = squad.size();
                for (Entity s : squad) {
                    s.task = T_RESPOND;
                    s.incident = inc;
                    s.onScene = false;
                }
                squad.clear();
                if (n > 0) say(WHO_MILITARY, null, "Military: " + n + " moving to support at " + inc.place + ".", inc.x, inc.y);
                else requestMilitary(inc.x, inc.y, inc.place, inc, null);
            }
        }
    }

    /** The SWAT team rolls out from the nearest precinct in its armoured van (or on foot, if it can't drive). */
    private void sendSwat(Incident inc) {
        City.Facility station = city.nearestFacility(City.FACILITY_POLICE, inc.x, inc.y);
        if (station == null) return;
        swatTeams--;
        String unit = Country.swat(city.country.id)[0];
        if (w.fleet.sendSwat(station.gateX, station.gateY, inc)) {
            say(WHO_POLICE, null, "Dispatch: " + unit + " is rolling out of " + station.name + " to " + inc.place
                    + ". Six officers, heavy weapons." + (swatTeams == 0 ? " That's our last team." : ""), station.gateX, station.gateY);
            return;
        }
        // (No road out to it right now: the team waits for its van rather than going on foot; asked again later.)
        swatTeams++;
        inc.swatRequested = false;
        inc.swatWait = 20;
    }

    /** Where reinforcements come from: the base or nearest precinct, or else the edge of the map. */
    private City.Facility origin(int type, float x, float y) {
        return city.nearestFacility(type == Entity.SOLDIER ? City.FACILITY_BASE : City.FACILITY_POLICE, x, y);
    }

    /**
     * Where reinforcements from outside come onto the map, nearest the trouble first: the ends of the
     * highway (each carriageway), then the end of the nearest road at the edge of the map.
     */
    java.util.List<float[]> entries(float tx, float ty) {
        java.util.List<float[]> out = new ArrayList<float[]>();
        if (city.hwyAxis >= 0) {
            int len = city.hwyAxis == 0 ? city.w : city.h;
            for (int end = 0; end < 2; end++)
                for (int a : new int[]{1, 5}) {
                    int along = end == 0 ? 1 : len - 2;
                    int x = city.hwyAxis == 0 ? along : city.hwyAt + a, y = city.hwyAxis == 0 ? city.hwyAt + a : along;
                    out.add(new float[]{(x + 0.5f) * City.T, (y + 0.5f) * City.T});
                }
        }
        float[] e = city.edgeSpawn(tx, ty);
        if (e != null) out.add(e);
        final float fx = tx, fy = ty;
        // (The highway first: that's the way into town, and the one people can watch for them on.)
        final int hw = city.hwyAxis >= 0 ? 4 : 0;
        final java.util.List<float[]> all = out;
        java.util.Collections.sort(out, new java.util.Comparator<float[]>() {
            public int compare(float[] p, float[] q) {
                return Float.compare(cost(p), cost(q));
            }

            private float cost(float[] p) {
                return (float) Math.hypot(p[0] - fx, p[1] - fy) + (all.indexOf(p) >= hw ? 100000 : 0);
            }
        });
        return out;
    }

    /** Reinforcements out of their vehicles: the column on the map follows them on foot to the end. */
    void convoyUnloaded(Fleet.Vehicle v, Entity e) {
        for (int i = 0; i < convoys.size(); i++) if (convoys.get(i).vehicles.contains(v)) convoys.get(i).onFoot.add(e);
    }

    /** Which side of the map a point is on, for the radio. */
    String sideOfMap(float x, float y) {
        float u = x / city.worldW(), v = y / city.worldH();
        if (Math.abs(u - 0.5f) > Math.abs(v - 0.5f)) return u < 0.5f ? "the west" : "the east";
        return v < 0.5f ? "the north" : "the south";
    }

    /** Starts showing reinforcements on the map: the vehicles fleet.send just added (from index before). */
    Convoy trackConvoy(int type, String label, int before, float fx, float fy, float tx, float ty, String place) {
        Convoy c = new Convoy();
        c.type = type;
        c.label = label;
        c.place = place;
        c.fromX = c.x = fx;
        c.fromY = c.y = fy;
        c.toX = tx;
        c.toY = ty;
        for (int i = before; i < w.fleet.vehicles.size(); i++) {
            Fleet.Vehicle v = w.fleet.vehicles.get(i);
            v.reinforcement = true;
            c.vehicles.add(v);
        }
        convoys.add(c);
        return c;
    }

    /** Follows each column in; it's dropped once they're all out of their vehicles (or there on foot). */
    private void updateConvoys(float dt) {
        for (int i = convoys.size() - 1; i >= 0; i--) {
            Convoy c = convoys.get(i);
            c.age += dt;
            boolean moving = false;
            for (int k = 0; k < c.vehicles.size() && !moving; k++) {
                Fleet.Vehicle v = c.vehicles.get(k);
                if (v.removedFromFleet || v.broken || (v.state != Fleet.WAIT && v.state != Fleet.DRIVE) || v.passengers <= 0) continue;
                c.x = v.x;
                c.y = v.y;
                moving = true;
            }
            if (c.pending > 0) moving = true;
            for (int k = 0; k < c.onFoot.size() && !moving; k++) {
                Entity e = c.onFoot.get(k);
                if (e.dead || Math.hypot(e.x - c.toX, e.y - c.toY) < 80) continue;
                c.x = e.x;
                c.y = e.y;
                moving = true;
            }
            if ((!moving && c.age > 3) || c.age > 240) convoys.remove(i);
        }
    }

    private int arrivedWaves;

    private void arrive(Arrival a) {
        java.util.List<float[]> from = entries(a.x, a.y);
        if (from.isEmpty()) return;
        if (a.type == Entity.SOLDIER) soldierCount = (soldierCount + 3) / 4 * 4;
        boolean army = a.type == Entity.SOLDIER;
        String label = a.label != null ? a.label.toUpperCase() : army ? "ARMY RESERVES" : "POLICE BACKUP";
        // (They drive to the nearest bit of road to the call, then get out: a call in a park or a yard is still
        // somewhere a car can get near.)
        float[] road = city.nearestDrivable(a.x, a.y);
        float gx = road != null ? road[0] : a.x, gy = road != null ? road[1] : a.y;
        // Drive in if the roads allow it (the highway if there is one). Police always come in their cars: if no
        // road in is open yet they try again shortly. (Soldiers with no road in come on foot.)
        // (Police waves take turns between the first two ways in, both sides of the highway, so one column
        // doesn't wait behind another.)
        if (!army && from.size() > 1 && (arrivedWaves++ & 1) == 1) from.add(0, from.remove(1));
        for (float[] p : from) {
            if (!w.fleet.canDrive(p[0], p[1], gx, gy)) continue;
            Convoy cv = trackConvoy(a.type, label, w.fleet.vehicles.size(), p[0], p[1], a.x, a.y, a.place);
            // (Their own cars, in a line: highway patrol, the sheriff's, the federal agents' unmarked black ones,
            // two officers to a car; a SWAT team all in its armoured van.)
            int cars = 0;
            if (a.swat) {
                enqueue(cv, Entity.SOLDIER, a.count, p, gx, gy, a.incident, a.zone, a.place);
                column.get(column.size() - 1).swat = true;
                cars = 1;
            } else
                for (int left = a.count; left > 0; left -= army ? 8 : 2, cars++) {
                    enqueue(cv, a.type, Math.min(left, army ? 8 : 2), p, gx, gy, a.incident, a.zone, a.place);
                    column.get(column.size() - 1).agency = a.agency;
                }
            boolean hwy = city.onHighway(p[0], p[1]);
            say(army ? WHO_MILITARY : WHO_POLICE, null, (army ? "Military: Reserve truck" : "Dispatch: " + (a.label != null ? a.label : "Backup") + ", " + cars + (a.swat ? " armoured van," : cars == 1 ? " car," : " cars,"))
                    + " entering the city from " + sideOfMap(p[0], p[1]) + (hwy && city.hwyName != null ? " on " + city.hwyName : "")
                    + ". " + a.count + (army ? " soldiers" : " officers") + " for " + a.place + ".", p[0], p[1]);
            return;
        }
        if (!army) {
            // No way in by road right now (blocked, cut off): they wait and try again, never walk in.
            if (++a.tries < 10) {
                a.time = 15;
                arrivals.add(a);
            } else {
                policeReserve += a.count;
                say(WHO_POLICE, null, "Dispatch: " + (a.label != null ? a.label : "Backup") + " can't find a way into the city. They'll stand by.", a.x, a.y);
            }
            return;
        }
        float[] p = from.get(0);
        Convoy cv = trackConvoy(a.type, label, w.fleet.vehicles.size(), p[0], p[1], a.x, a.y, a.place);
        Entity first = null;
        w.arriving = true;
        for (int i = 0; i < a.count; i++) {
            Entity e = w.spawn(a.type, p[0] + rnd.nextFloat() * 24 - 12, p[1] + rnd.nextFloat() * 24 - 12);
            if (e == null) continue;
            if (a.type == Entity.COP) {
                if (a.swat) w.applyRole(e, Entity.ROLE_SWAT);
                else if (a.agency > 0) w.makeAgency(e, a.agency);
            }
            cv.onFoot.add(e);
            w.reinforce(e);
            if (first == null) first = e;
            if (a.incident != null && !a.incident.resolved) {
                e.task = T_RESPOND;
                e.incident = a.incident;
            } else if (a.zone != null && !a.zone.removed) {
                e.task = T_GUARD;
                e.zone = a.zone;
            }
        }
        w.arriving = false;
        if (first == null) return;
        if (a.type == Entity.SOLDIER)
            say(WHO_MILITARY, first, SQUADS[Math.max(0, first.squad) % SQUADS.length] + " squad on foot, coming in from "
                    + sideOfMap(p[0], p[1]) + ". Moving to " + a.place + ".", p[0], p[1]);
        else say(WHO_POLICE, first, "Backup on foot, coming in from " + sideOfMap(p[0], p[1]) + ". Heading to "
                + a.place + ".", p[0], p[1]);
    }

    // ------------------------------------------------------------------ safe zones (walled compounds, 10.15)

    /** Which zone's wall (or gate) is on each tile, and which section: index, or -2 - gate. */
    SafeZone[] wallOwner;
    int[] wallSeg;

    private void updateZones(float step) {
        int scentN = 0;
        for (int i = zones.size() - 1; i >= 0; i--) {
            SafeZone z = zones.get(i);
            z.age += step;
            z.checkCd -= step;
            z.attackCd -= step;
            z.breachCd -= step;
            int near = w.countZombiesNear(z.x, z.y, z.r * 1.5f);
            if (z.guards == 0 && z.age > 10) {
                removeZone(z, near > 0 && z.open);
                continue;
            }
            if (!z.open) {
                // Setting up: the wall goes up a section at a time, as fast as there are hands on site.
                if (w.countZombiesNear(z.x, z.y, z.baseR * 1.2f) >= 3 && z.built < 0.7f) {
                    say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military" : "Police") + ": The dead got to "
                            + z.place + " before the wall was up. Pulling out!", z.x, z.y);
                    removeZone(z, false, true);
                    if (z.military) militaryZoneCd = 40;
                    else policeZoneCd = 45;
                    continue;
                }
                z.built += step * Math.min(8, z.onSite) * 26f / Math.max(40, z.wall.length) / (z.military ? 26f : 30f);
                raiseWall(z);
                if (z.built >= 1) {
                    z.built = 1;
                    raiseWall(z);
                    z.open = true;
                    z.age = 0;
                    zonesDirty = true;
                    say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military: " : "Police Command: ")
                            + "The safe zone at " + z.place + " is open: walled in, room for " + z.capacity
                            + ". Civilians, come to the gates!", z.x, z.y);
                }
                if (z.guards < z.wantGuards) assignGuards(z, z.wantGuards - z.guards, 900, z.military ? Entity.SOLDIER : Entity.COP);
                continue;
            }
            // Overrun: more of the dead inside the wall than the guards can hold.
            int inside = 0;
            for (int k = 0, n = w.entities.size(); k < n && near > 0; k++) {
                Entity o = w.entities.get(k);
                if (o.dead || !o.isZombie() || o.hidden) continue;
                if (z.inside(o.x, o.y)) inside++;
            }
            boolean falling = inside > 0 && z.breaches > 0;
            if (falling && !z.fallingBack)
                say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military" : "Police") + ": They're through the wall at "
                        + z.place + "! Fall back on the tents!", z.x, z.y);
            z.fallingBack = falling;
            if (inside >= Math.max(4, z.onSite * 2)) {
                removeZone(z, true);
                continue;
            }
            repairWall(z, step);
            // Rations: a day's worth goes in a couple of minutes of game time.
            z.food = Math.max(0, z.food - z.sheltered * step / 110f);
            if (z.food <= 0 && z.sheltered > 0 && w.time - z.foodSaid > 90) {
                z.foodSaid = w.time;
                say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military" : "Police") + ": " + z.place
                        + " is out of food. We need that supply truck!", z.x, z.y);
            }
            // The dead can smell the people in there through the fence.
            if (z.sheltered > 0 || z.onSite > 0)
                for (int k = 0; k < z.wall.length && scentN < scent.length; k += 3) if (z.up[k]) scent[scentN++] = z.wall[k];
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
                        + z.place + " safe zone is under attack! " + near + " of them at the wall!", z.x, z.y);
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
                            + z.place + " safe zone is full (" + z.capacity + "). The gates are shut to newcomers.", z.x, z.y);
                }
            }
            if (z.sheltered < z.capacity * 0.8f) z.fullAnnounced = false;
            z.statusTimer += step;
            if (z.statusTimer > 60) {
                z.statusTimer = 0;
                if (z.sheltered > 0)
                    say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military" : "Police") + ": "
                            + z.place + " safe zone is holding. " + z.sheltered + " civilians inside"
                            + (z.breaches > 0 ? ", " + z.breaches + " breach" + (z.breaches == 1 ? "" : "es") + " in the wall" : "") + ".", z.x, z.y);
            }
        }
        city.extraScent = scent;
        city.extraScentN = scentN;
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

    private final int[] scent = new int[600];

    /** Puts up the wall sections the setting up has got to (not on top of anyone standing there). */
    private void raiseWall(SafeZone z) {
        int upTo = (int) Math.min(z.wall.length, Math.ceil(z.built * z.wall.length));
        for (int k = 0; k < upTo; k++) {
            if (z.up[k] || z.hp[k] < 80) continue;
            if (occupied(z.wall[k])) continue;
            z.up[k] = true;
            city.solid[z.wall[k]] = true;
        }
        if (z.built >= 1)
            for (int g = 0; g < z.gateTiles.length; g++) setGate(z, g, z.gateOpen[g]);
    }

    /** Someone standing on this tile. */
    private boolean occupied(int t) {
        float x = (t % city.w + 0.5f) * City.T, y = (t / city.w + 0.5f) * City.T;
        return w.peopleNear(x, y, City.T * 0.85f) || w.countZombiesNear(x, y, City.T * 0.85f) > 0;
    }

    /** Guards patch up the breaches once the dead have been driven off from them. */
    private void repairWall(SafeZone z, float step) {
        int down = 0;
        for (int k = 0; k < z.wall.length; k++) {
            if (z.up[k] && z.hp[k] < 100) {
                // Dented sections still standing are shored up once it's quiet there.
                float[] p = z.innerOf(z.wall[k], city.w);
                if (w.countZombiesNear(p[0], p[1], 50) == 0 && guardNear(z, p[0], p[1], 90)) z.hp[k] = Math.min(100, z.hp[k] + 10 * step);
                continue;
            }
            if (z.up[k] || z.hp[k] >= 80) continue;
            down++;
            float[] p = z.innerOf(z.wall[k], city.w);
            if (w.countZombiesNear(p[0], p[1], 50) > 0 || !guardNear(z, p[0], p[1], 40)) continue;
            z.hp[k] = Math.min(100, z.hp[k] + 28 * step);
        }
        // (Mended sections go back up as soon as nobody's standing in the gap.)
        raiseWall(z);
        for (int g = 0; g < z.gateTiles.length; g++) {
            if (z.gateHp[g] > 0) continue;
            down++;
            if (w.countZombiesNear(z.gateIn[g][0], z.gateIn[g][1], 50) > 0 || !guardNear(z, z.gateIn[g][0], z.gateIn[g][1], 40)) continue;
            z.gateHp[g] = 100;
        }
        z.breaches = down;
    }

    private boolean guardNear(SafeZone z, float x, float y, float r) {
        for (int i = 0, n = w.entities.size(); i < n; i++) {
            Entity e = w.entities.get(i);
            if (!e.dead && e.task == T_GUARD && e.zone == z && Math.abs(e.x - x) < r && Math.abs(e.y - y) < r) return true;
        }
        return false;
    }

    /**
     * The gates (every frame): open for people waiting to come in (and guards), shut the moment the dead are
     * close. A gate the dead have broken stays open until it's fixed.
     */
    void updateGates(float dt) {
        for (int i = 0; i < zones.size(); i++) {
            SafeZone z = zones.get(i);
            if (!z.open) continue;
            for (int g = 0; g < z.gateTiles.length; g++) {
                if (z.gateHp[g] <= 0) {
                    setGate(z, g, true);
                    continue;
                }
                float gx = (z.gateOut[g][0] + z.gateIn[g][0]) / 2, gy = (z.gateOut[g][1] + z.gateIn[g][1]) / 2;
                boolean danger = w.countZombiesNear(gx, gy, 75) > 0;
                boolean waiting = !z.full && w.peopleNear(z.gateOut[g][0], z.gateOut[g][1], 30);
                if (waiting && !danger) z.gateHold[g] = 2.5f;
                else z.gateHold[g] -= dt;
                boolean want = !danger && z.gateHold[g] > 0;
                if (want != z.gateOpen[g]) {
                    // (Not shut on someone standing in it.)
                    if (!want && (occupied(z.gateTiles[g][0]) || occupied(z.gateTiles[g][1]))) continue;
                    setGate(z, g, want);
                }
            }
        }
    }

    private void setGate(SafeZone z, int g, boolean open) {
        z.gateOpen[g] = open;
        for (int t : z.gateTiles[g]) city.solid[t] = !open;
    }

    /** The dead clawing at a section of wall (or a gate). */
    void damageWall(SafeZone z, int seg, float dmg) {
        if (seg >= 0) {
            if (!z.up[seg]) return;
            z.hp[seg] -= dmg;
            if (z.hp[seg] > 0) return;
            z.hp[seg] = 0;
            z.up[seg] = false;
            city.solid[z.wall[seg]] = false;
            z.breaches++;
            float[] p = z.innerOf(z.wall[seg], city.w);
            if (z.breachCd <= 0) {
                z.breachCd = 15;
                say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military" : "Police") + ": Breach in the wall at "
                        + z.place + "! Get it covered!", p[0], p[1]);
            }
        } else {
            int g = -2 - seg;
            if (g < 0 || g >= z.gateTiles.length || z.gateHp[g] <= 0) return;
            z.gateHp[g] -= dmg;
            if (z.gateHp[g] > 0) return;
            z.gateHp[g] = 0;
            z.breaches++;
            setGate(z, g, true);
            say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military" : "Police") + ": The gate at " + z.place
                    + " is down! Hold the gap!", z.gateIn[g][0], z.gateIn[g][1]);
        }
    }

    /** The nearest breach in a zone's wall (the spot just inside it), or null. */
    float[] nearestBreach(SafeZone z, float x, float y) {
        float[] best = null;
        float bd = Float.MAX_VALUE;
        for (int k = 0; k < z.wall.length; k++) {
            if (z.up[k] || (z.built < 1 && z.hp[k] > 0)) continue;
            float[] p = z.innerOf(z.wall[k], city.w);
            float d = (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        for (int g = 0; g < z.gateTiles.length; g++) {
            if (z.gateHp[g] > 0) continue;
            float d = (z.gateIn[g][0] - x) * (z.gateIn[g][0] - x) + (z.gateIn[g][1] - y) * (z.gateIn[g][1] - y);
            if (d < bd) {
                bd = d;
                best = z.gateIn[g];
            }
        }
        return best;
    }

    private int countZones(boolean military) {
        int n = 0;
        for (int i = 0; i < zones.size(); i++) if (zones.get(i).military == military) n++;
        return n;
    }

    /** No safe zone is set up with the dead closer than this. */
    static final float CLEAR_OF_DEAD = 250;

    /**
     * The block to wall in: one with room inside for tents, away from the dead and near the people who need
     * it; better still with the precinct (or the base) in it, or a school, church, market, mall or stadium.
     * With a spot given, the block there.
     */
    private int[] chooseBlock(boolean military, float[] at) {
        float px = city.worldW() / 2, py = city.worldH() / 2;
        if (at == null) {
            float sx = 0, sy = 0;
            int n = 0;
            for (int i = 0, count = w.entities.size(); i < count; i++) {
                Entity e = w.entities.get(i);
                if (e.type != Entity.CIVILIAN || e.dead) continue;
                sx += e.x;
                sy += e.y;
                n++;
            }
            if (n > 0) {
                px = sx / n;
                py = sy / n;
            }
        }
        int[] best = null;
        float bestScore = -Float.MAX_VALUE;
        for (int[] b : city.blocks()) {
            int tx0 = b[0] + 1, ty0 = b[1] + 1, tx1 = b[2] - 2, ty1 = b[3] - 2;
            int bw = tx1 - tx0 + 1, bh = ty1 - ty0 + 1;
            if (bw < 9 || bh < 9 || bw > 60 || bh > 60) continue;
            float cx = (tx0 + tx1 + 1) * City.T / 2f, cy = (ty0 + ty1 + 1) * City.T / 2f;
            if (at != null) {
                float d = Math.max(Math.abs(at[0] - cx) - bw * City.T / 2f, Math.abs(at[1] - cy) - bh * City.T / 2f);
                if (d > 3 * City.T) continue;
                float score = -d;
                if (score > bestScore && free(tx0, ty0, tx1, ty1)) {
                    bestScore = score;
                    best = b;
                }
                continue;
            }
            if (!free(tx0, ty0, tx1, ty1)) continue;
            int zd = Math.min(40, city.fieldAt(city.zombieDist, cx, cy));
            if (zd < 5 || w.countZombiesNear(cx, cy, CLEAR_OF_DEAD) > 0) continue;
            int open = 0;
            for (int y = ty0 + 1; y < ty1; y++)
                for (int x = tx0 + 1; x < tx1; x++) if (!city.solid[y * city.w + x]) open++;
            if (open < (military ? 70 : 50)) continue;
            float score = Math.min(open, 500) * 0.03f + zd * 2 - (float) Math.hypot(cx - px, cy - py) / City.T * 0.5f;
            for (City.Facility f : city.facilities)
                if (f.x > tx0 * City.T && f.x < (tx1 + 1) * City.T && f.y > ty0 * City.T && f.y < (ty1 + 1) * City.T
                        && f.kind == (military ? City.FACILITY_BASE : City.FACILITY_POLICE)) score += 25;
            City.Building mark = landmarkIn(tx0, ty0, tx1, ty1);
            if (mark != null) score += 8;
            if (score > bestScore) {
                bestScore = score;
                best = b;
            }
        }
        return best;
    }

    /** Not taken by another zone (with a street between). */
    private boolean free(int tx0, int ty0, int tx1, int ty1) {
        for (int i = 0; i < zones.size(); i++) {
            SafeZone o = zones.get(i);
            if (tx0 <= o.tx1 + 3 && tx1 >= o.tx0 - 3 && ty0 <= o.ty1 + 3 && ty1 >= o.ty0 - 3) return false;
        }
        return true;
    }

    private City.Building landmarkIn(int tx0, int ty0, int tx1, int ty1) {
        for (int i = 0, n = city.buildings.size(); i < n; i++) {
            City.Building b = city.buildings.get(i);
            if (b.collapsed || b.name == null) continue;
            if (b.kind != City.SCHOOL && b.kind != City.CHURCH && b.kind != City.MARKET && b.kind != City.MALL
                    && b.kind != City.STADIUM && b.kind != City.HOSPITAL && b.kind != City.STATION) continue;
            if (b.doorX > tx0 * City.T && b.doorX < (tx1 + 1) * City.T && b.doorY > ty0 * City.T && b.doorY < (ty1 + 1) * City.T) return b;
        }
        return null;
    }

    /**
     * Lays out the compound on a block: the wall round its edge (where buildings don't already close it),
     * the gates onto the streets, and the tents inside. False if there's no proper way in.
     */
    private boolean buildCompound(SafeZone z, int[] b) {
        int W = city.w;
        z.tx0 = b[0] + 1;
        z.ty0 = b[1] + 1;
        z.tx1 = b[2] - 2;
        z.ty1 = b[3] - 2;
        // The ring, side by side (top, right, bottom, left).
        ArrayList<int[]> sides = new ArrayList<int[]>();
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int s = 0; s < 4; s++) {
            ArrayList<Integer> side = new ArrayList<Integer>();
            if (s == 0) for (int x = z.tx0; x <= z.tx1; x++) side.add(z.ty0 * W + x);
            else if (s == 1) for (int y = z.ty0 + 1; y <= z.ty1; y++) side.add(y * W + z.tx1);
            else if (s == 2) for (int x = z.tx1 - 1; x >= z.tx0; x--) side.add(z.ty1 * W + x);
            else for (int y = z.ty1 - 1; y > z.ty0; y--) side.add(y * W + z.tx0);
            int[] a = new int[side.size()];
            for (int k = 0; k < a.length; k++) a[k] = side.get(k);
            sides.add(a);
        }
        // Gates: on the two best sides facing a street, as near the middle as the ground allows.
        ArrayList<int[]> gates = new ArrayList<int[]>();
        ArrayList<Integer> gateSide = new ArrayList<Integer>();
        Integer[] order = {0, 1, 2, 3};
        final int[] lens = {sides.get(0).length, sides.get(1).length, sides.get(2).length, sides.get(3).length};
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            public int compare(Integer p, Integer q) {
                return lens[q] - lens[p];
            }
        });
        for (int oi = 0; oi < 4 && gates.size() < 2; oi++) {
            int s = order[oi];
            if (!gateSide.isEmpty() && gateSide.get(0) % 2 != s % 2 && oi < 2) continue;
            int[] side = sides.get(s);
            int bestK = -1;
            float bestD = Float.MAX_VALUE;
            for (int k = 1; k < side.length - 2; k++) {
                if (!walkable(side[k]) || !walkable(side[k + 1])) continue;
                int ox = dirs[s][0], oy = dirs[s][1];
                if (!walkable(side[k] + oy * W + ox) || !walkable(side[k + 1] + oy * W + ox)) continue;
                if (!walkable(side[k] - oy * W - ox) || !walkable(side[k + 1] - oy * W - ox)) continue;
                float d = Math.abs(k + 0.5f - side.length / 2f);
                if (d < bestD) {
                    bestD = d;
                    bestK = k;
                }
            }
            if (bestK < 0) continue;
            gates.add(new int[]{side[bestK], side[bestK + 1]});
            gateSide.add(s);
        }
        if (gates.isEmpty()) return false;
        // The wall: every open tile of the ring that isn't a gate (buildings and trees close the rest).
        ArrayList<Integer> wall = new ArrayList<Integer>();
        java.util.HashSet<Integer> gateSet = new java.util.HashSet<Integer>();
        for (int[] g : gates) {
            gateSet.add(g[0]);
            gateSet.add(g[1]);
        }
        for (int[] side : sides)
            for (int t : side) if (!gateSet.contains(t) && walkable(t)) wall.add(t);
        int n = wall.size(), ng = gates.size();
        z.wall = new int[n];
        z.hp = new float[n];
        z.up = new boolean[n];
        for (int k = 0; k < n; k++) {
            z.wall[k] = wall.get(k);
            z.hp[k] = 100;
        }
        z.gateTiles = new int[ng][];
        z.gateOut = new float[ng][];
        z.gateIn = new float[ng][];
        z.gateHp = new float[ng];
        z.gateHold = new float[ng];
        z.gateOpen = new boolean[ng];
        for (int g = 0; g < ng; g++) {
            int[] t = gates.get(g);
            int s = gateSide.get(g);
            float gx = ((t[0] % W + t[1] % W) / 2f + 0.5f) * City.T, gy = ((t[0] / W + t[1] / W) / 2f + 0.5f) * City.T;
            z.gateTiles[g] = t;
            z.gateOut[g] = new float[]{gx + dirs[s][0] * City.T * 1.6f, gy + dirs[s][1] * City.T * 1.6f};
            z.gateIn[g] = new float[]{gx - dirs[s][0] * City.T * 1.6f, gy - dirs[s][1] * City.T * 1.6f};
            z.gateHp[g] = 160;
        }
        if (wallOwner == null) {
            wallOwner = new SafeZone[W * city.h];
            wallSeg = new int[W * city.h];
        }
        for (int k = 0; k < n; k++) {
            wallOwner[z.wall[k]] = z;
            wallSeg[z.wall[k]] = k;
        }
        for (int g = 0; g < ng; g++)
            for (int t : z.gateTiles[g]) {
                wallOwner[t] = z;
                wallSeg[t] = -2 - g;
            }
        // Inside: the open ground, the middle of it, and the tents (the medical and food tents nearest the middle).
        float sx = 0, sy = 0;
        int open = 0;
        for (int y = z.ty0 + 1; y < z.ty1; y++)
            for (int x = z.tx0 + 1; x < z.tx1; x++)
                if (walkable(y * W + x)) {
                    sx += x;
                    sy += y;
                    open++;
                }
        if (open < 12) return false;
        float[] mid = city.findWalkable((sx / open + 0.5f) * City.T, (sy / open + 0.5f) * City.T);
        z.x = mid != null && z.inside(mid[0], mid[1]) ? mid[0] : (sx / open + 0.5f) * City.T;
        z.y = mid != null && z.inside(mid[0], mid[1]) ? mid[1] : (sy / open + 0.5f) * City.T;
        z.baseR = Math.max(z.tx1 - z.tx0 + 1, z.ty1 - z.ty0 + 1) * City.T / 2f;
        z.r = z.baseR;
        ArrayList<float[]> spots = new ArrayList<float[]>();
        for (int y = z.ty0 + 2; y < z.ty1 - 1; y += 2)
            for (int x = z.tx0 + 2; x < z.tx1 - 2; x += 3) {
                if (!walkable(y * W + x) || !walkable(y * W + x + 1)) continue;
                float tx = (x + 1) * City.T, ty = (y + 0.5f) * City.T;
                boolean lane = false;
                for (int g = 0; g < ng; g++) if (Math.hypot(z.gateIn[g][0] - tx, z.gateIn[g][1] - ty) < 3 * City.T) lane = true;
                if (!lane) spots.add(new float[]{tx, ty});
            }
        if (spots.size() < 3) return false;
        final float zx = z.x, zy = z.y;
        java.util.Collections.sort(spots, new java.util.Comparator<float[]>() {
            public int compare(float[] p, float[] q) {
                return Float.compare((p[0] - zx) * (p[0] - zx) + (p[1] - zy) * (p[1] - zy), (q[0] - zx) * (q[0] - zx) + (q[1] - zy) * (q[1] - zy));
            }
        });
        z.medX = spots.get(0)[0];
        z.medY = spots.get(0)[1];
        z.foodX = spots.get(1)[0];
        z.foodY = spots.get(1)[1];
        z.tents.clear();
        for (int k = 2; k < spots.size() && z.tents.size() < (z.military ? 36 : 24); k++) z.tents.add(spots.get(k));
        z.capacity = Math.max(10, Math.min(z.military ? 160 : 110, 6 + z.tents.size() * 4));
        z.foodMax = 60 + z.capacity * 2;
        z.food = z.foodMax;
        return true;
    }

    private boolean walkable(int t) {
        return t >= 0 && t < city.solid.length && !city.solid[t] && city.tiles[t] != City.WATER && city.tiles[t] != City.ROAD;
    }

    private SafeZone establish(boolean military, float[] at) {
        int[] b = chooseBlock(military, at);
        if (b == null) {
            if (at != null) say(WHO_INFO, null, "No block there to wall in as a safe zone.", at[0], at[1]);
            return null;
        }
        SafeZone z = new SafeZone();
        z.military = military;
        if (!buildCompound(z, b)) {
            releaseWallMap(z);
            return null;
        }
        City.Facility fac = null;
        for (City.Facility f : city.facilities)
            if (z.contains(f.x, f.y, 1) && (f.kind == City.FACILITY_POLICE || f.kind == City.FACILITY_BASE)) fac = f;
        City.Building mark = landmarkIn(z.tx0, z.ty0, z.tx1, z.ty1);
        z.place = fac != null ? fac.name : mark != null ? mark.name : city.placeName(z.x, z.y);
        // Two at each gate, the rest along the wall.
        z.wantGuards = z.gateTiles.length * 2 + (military ? 6 : 3);
        int got = assignGuards(z, z.wantGuards, Float.MAX_VALUE, military ? Entity.SOLDIER : Entity.COP);
        if (got == 0) {
            releaseWallMap(z);
            return null;
        }
        zones.add(z);
        if (!military) {
            int n = w.fleet.roadblocks(z);
            if (n > 0) say(WHO_POLICE, null, "Police Command: Sending " + n + (n == 1 ? " car" : " cars")
                    + " to close the roads into " + z.place + ".", z.x, z.y);
        }
        zonesDirty = true;
        if (military)
            say(WHO_MILITARY, null, "Military: " + got + " soldiers moving to " + z.place
                    + " to wall it in as a safe zone. It opens once the wall is up.", z.x, z.y);
        else
            say(WHO_POLICE, null, "Police Command: Officers on the way to wall in " + z.place
                    + " as a safe zone. Stay put until it's ready.", z.x, z.y);
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

    /** Takes the wall down (the ground is as it was). */
    private void releaseWallMap(SafeZone z) {
        for (int t : z.wall) {
            if (wallOwner != null && wallOwner[t] == z) wallOwner[t] = null;
            city.solid[t] = false;
        }
        for (int[] g : z.gateTiles)
            for (int t : g) {
                if (wallOwner != null && wallOwner[t] == z) wallOwner[t] = null;
                city.solid[t] = false;
            }
    }

    private void removeZone(SafeZone z, boolean overrun, boolean closing) {
        z.removed = true;
        zones.remove(z);
        zonesDirty = true;
        releaseWallMap(z);
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
        if (w.countZombiesNear(x, y, CLEAR_OF_DEAD) > 0) {
            say(WHO_INFO, null, "Too close to the dead to set up a safe zone. Pick somewhere quieter.", x, y);
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
            say(WHO_INFO, null, "No police or military available to guard a safe zone. Spawn some first!", x, y);
            return;
        }
        boolean military = freeSoldiers >= 2 || freeCops == 0;
        SafeZone z = establish(military, new float[]{x, y});
        if (z != null && z.guards < z.wantGuards)
            assignGuards(z, z.wantGuards - z.guards, Float.MAX_VALUE, military ? Entity.COP : Entity.SOLDIER);
    }

    /** The Erase tool removes a safe zone when used on its centre. */
    boolean removeZoneAt(float x, float y) {
        for (int i = 0; i < zones.size(); i++) {
            SafeZone z = zones.get(i);
            float dx = z.x - x, dy = z.y - y;
            if (dx * dx + dy * dy < (z.r * 0.4f) * (z.r * 0.4f)) {
                say(WHO_INFO, null, "Safe zone at " + z.place + " closed.", z.x, z.y);
                removeZone(z, false, true);
                return true;
            }
        }
        return false;
    }

    /** Recreates a safe zone from a save (the block it was on, walled in again). */
    void restoreZone(float x, float y, float r, boolean military, String place, int capacity, int wantGuards) {
        int[] b = chooseBlock(military, new float[]{x, y});
        if (b == null) return;
        SafeZone z = new SafeZone();
        z.military = military;
        if (!buildCompound(z, b)) {
            releaseWallMap(z);
            return;
        }
        z.place = place;
        z.wantGuards = Math.max(wantGuards, z.gateTiles.length * 2 + 2);
        z.age = 20;
        z.built = 1;
        z.open = true;
        java.util.Arrays.fill(z.up, false);
        zones.add(z);
        raiseWall(z);
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

    /** The way to the gates of the zones with room. */
    private void updateZoneField() {
        zonesDirty = false;
        zoneFieldTimer = 2;
        int n = 0;
        for (int i = 0; i < zones.size(); i++) n += zones.get(i).gateOut.length;
        float[] xs = new float[n], ys = new float[n];
        n = 0;
        for (int i = 0; i < zones.size(); i++) {
            SafeZone z = zones.get(i);
            if (z.full || !z.open) continue;
            for (int g = 0; g < z.gateOut.length; g++) {
                xs[n] = z.gateOut[g][0];
                ys[n] = z.gateOut[g][1];
                n++;
            }
        }
        city.walkFieldFromPoints(zoneField, xs, ys, n);
    }
}
