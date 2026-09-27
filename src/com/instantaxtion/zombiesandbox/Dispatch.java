package com.instantaxtion.zombiesandbox;

import java.util.ArrayList;
import java.util.Random;

/**
 * Police dispatch and military command: 911 calls become incidents that cops respond to, big incidents
 * are escalated to the military, and both forces set up guarded safe zones that civilians run to.
 * Everything they say to each other goes into a radio log that GameView shows on screen.
 */
final class Dispatch {
    static final int T_NONE = 0, T_RESPOND = 1, T_GUARD = 2, T_SEEK = 3, T_SHELTER = 4;
    static final int WHO_911 = 0, WHO_POLICE = 1, WHO_MILITARY = 2, WHO_INFO = 3;
    static final int[] WHO_COLORS = {0xFFFFA64D, 0xFF7FB0FF, 0xFFA6DC72, 0xFFBDBDBD};
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
        boolean militaryRequested, onScene, resolved;
        int[] field;
    }

    static final class SafeZone {
        float x, y, r, age, attackCd, statusTimer;
        boolean military, removed, full, fullAnnounced;
        String place;
        int guards, sheltered, wantGuards, capacity;
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
    int policeReserve, squadReserve;
    int calls, sheltered, messageCount;
    int copCount, soldierCount;
    private int freeCops, freeSoldiers;
    private float tick, zoneFieldTimer, policeZoneCd = 10, militaryZoneCd = 6, militaryCd, policeCd;
    private boolean zonesDirty = true;

    private static final int[] POLICE_RESERVE = {0, 1, 2, 3}, SQUAD_RESERVE = {0, 1, 2, 3};

    Dispatch(World w, int reinforcementLevel) {
        this.w = w;
        this.city = w.city;
        policeReserve = POLICE_RESERVE[reinforcementLevel];
        squadReserve = SQUAD_RESERVE[reinforcementLevel];
        zoneField = new int[city.w * city.h];
        java.util.Arrays.fill(zoneField, City.FAR);
    }

    static String name(Entity e) {
        if (e.type == Entity.COP) return "Unit " + e.callsign;
        if (e.type == Entity.SOLDIER) return SQUADS[e.squad % SQUADS.length] + "-" + e.member;
        return "Caller";
    }

    void say(int who, Entity speaker, String text, float x, float y) {
        String label = who == WHO_911 ? "911" : who == WHO_POLICE ? "Police" : who == WHO_MILITARY ? "Military" : "Info";
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
        if (log.size() > 60) log.remove(0);
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
        city.fieldFromPoints(inc.field, new float[]{inc.x}, new float[]{inc.y}, 1);
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

    private static void release(Entity e) {
        e.task = T_NONE;
        e.incident = null;
        e.zone = null;
        e.onScene = false;
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
            int have = inc.cops + inc.soldiers;
            if (have < need) {
                int got = assign(inc, Entity.COP, need - have);
                if (got == 0 && have == 0) policeBackup(inc);
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
            say(WHO_MILITARY, lead, "Copy that, Police. " + SQUADS[lead.squad % SQUADS.length]
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
        a.time = 8;
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
                : "Reserve squad inbound") + " to " + place + ", ETA 8 seconds."
                + (squadReserve == 0 ? " That's our last one." : " " + squadReserve + " left in reserve."), x, y);
        return true;
    }

    private boolean policeBackup(Incident inc) {
        if (policeReserve <= 0 || policeCd > 0) return false;
        policeCd = 40;
        policeReserve--;
        Arrival a = new Arrival();
        a.time = 6;
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
            say(WHO_MILITARY, first, SQUADS[first.squad % SQUADS.length] + " squad "
                    + (from != null ? "rolling out of " + from.name : "on the ground") + ". Moving to " + a.place + ".", p[0], p[1]);
        else say(WHO_POLICE, first, (from != null ? "Leaving " + from.name : "Backup has arrived") + ". Heading to "
                + a.place + ".", p[0], p[1]);
    }

    // ------------------------------------------------------------------ safe zones

    private void updateZones(float step) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            SafeZone z = zones.get(i);
            z.age += step;
            z.attackCd -= step;
            int near = w.countZombiesNear(z.x, z.y, z.r * 1.6f);
            if (z.guards == 0 && z.age > 10) {
                removeZone(z, near > 0);
                continue;
            }
            if (z.guards < z.wantGuards) assignGuards(z, z.wantGuards - z.guards, 900, z.military ? Entity.SOLDIER : Entity.COP);
            if (near >= 5 && z.attackCd <= 0) {
                z.attackCd = 30;
                say(z.military ? WHO_MILITARY : WHO_POLICE, null, (z.military ? "Military" : "Police") + ": The "
                        + z.place + " safe zone is under attack! " + near + " hostiles at the perimeter!", z.x, z.y);
                if (!z.military) requestMilitary(z.x, z.y, z.place, null, z);
                else sendReserveSquad(z.x, z.y, z.place, null, z);
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
        int zombies = w.counts[Entity.ZOMBIE] + w.counts[Entity.RUNNER] + w.counts[Entity.BRUTE];
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
            if (d < 5) continue;
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
            if ((f.kind == City.FACILITY_BASE) != military) continue;
            boolean taken = false;
            for (int i = 0; i < zones.size(); i++)
                if (Math.hypot(zones.get(i).x - f.x, zones.get(i).y - f.y) < 150) taken = true;
            if (!taken && city.fieldAt(city.zombieDist, f.x, f.y) >= 3) return f;
        }
        return null;
    }

    private SafeZone establish(boolean military, float[] at) {
        String place;
        float x, y;
        float radius = military ? 80 : 60;
        City.Facility facility = at == null ? freeFacility(military) : null;
        if (facility != null) {
            x = facility.x;
            y = facility.y;
            place = facility.name;
            radius = Math.max(radius, facility.r);
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
        z.r = radius;
        // Bases and stations hold more people than a zone thrown up in a park.
        z.capacity = military ? (facility != null ? 60 : 40) : (facility != null ? 35 : 25);
        z.wantGuards = military ? 6 : 4;
        z.place = place;
        int got = assignGuards(z, z.wantGuards, Float.MAX_VALUE, military ? Entity.SOLDIER : Entity.COP);
        if (got == 0) return null;
        zones.add(z);
        zonesDirty = true;
        if (military)
            say(WHO_MILITARY, null, "Military: Safe zone established at " + place + ", " + got
                    + " soldiers guarding. Civilians, head there now!", x, y);
        else
            say(WHO_POLICE, null, "Police Command: Setting up a safe zone at " + place
                    + ". All civilians, get there now!", x, y);
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

    private void removeZone(SafeZone z, boolean overrun) {
        z.removed = true;
        zones.remove(z);
        zonesDirty = true;
        for (int i = 0, n = w.entities.size(); i < n; i++) {
            Entity e = w.entities.get(i);
            if (e.zone == z) {
                if (e.task == T_SHELTER) {
                    e.fleeTimer = 3;
                    e.threatX = z.x;
                    e.threatY = z.y;
                }
                release(e);
            }
        }
        if (overrun) {
            say(WHO_INFO, null, "The " + z.place + " safe zone has been overrun!", z.x, z.y);
            SafeZone fallback = null;
            for (int i = 0; i < zones.size(); i++) if (zones.get(i).military) fallback = zones.get(i);
            if (!z.military && fallback != null) {
                say(WHO_POLICE, null, "Police Command: All units, fall back to the military safe zone at "
                        + fallback.place + ".", fallback.x, fallback.y);
                assignGuards(fallback, 4, Float.MAX_VALUE, Entity.COP);
            }
        } else {
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

    /** True if some safe zone still has room. */
    boolean hasRoom() {
        for (int i = 0; i < zones.size(); i++) if (!zones.get(i).full) return true;
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
            float dx = z.x - x, dy = z.y - y, r = z.r * margin;
            if (dx * dx + dy * dy < r * r) return z;
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
            if (zones.get(i).full) continue;
            xs[n] = zones.get(i).x;
            ys[n] = zones.get(i).y;
            n++;
        }
        city.fieldFromPoints(zoneField, xs, ys, n);
    }
}
