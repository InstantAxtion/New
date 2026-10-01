package com.instantaxtion.zombiesandbox;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Saves and loads a game. The city itself is rebuilt from its settings and seed (generation is
 * deterministic); everything that changes while playing is written out: people, zombies, corpses, safe
 * zones, people hiding indoors, dropped guns, reserves, the stats history, families, burnt-out wrecks and
 * damaged or collapsed buildings.
 */
final class SaveGame {
    private static final int VERSION = 12;

    private SaveGame() {
    }

    static void save(World w, File file) throws IOException {
        File tmp = new File(file.getPath() + ".tmp");
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)));
        try {
            out.writeInt(VERSION);
            CityConfig cfg = w.city.cfg;
            out.writeInt(cfg.v.length);
            for (int v : cfg.v) out.writeInt(v);
            out.writeLong(cfg.seed);

            out.writeFloat(w.time);
            out.writeInt(w.turned);
            out.writeInt(w.zombiesKilled);
            out.writeInt(w.civiliansLost);
            out.writeInt(w.shotsFired);
            out.writeInt(w.cured);
            out.writeInt(w.peakZombies);
            out.writeInt(w.peakCops);
            out.writeInt(w.peakSoldiers);
            out.writeInt(w.recruits);
            out.writeInt(w.refused);
            out.writeInt(w.mutation);
            out.writeFloat(w.outbreakTime);
            out.writeFloat(w.statStep);
            out.writeInt(w.histCount);
            for (int i = 0; i < w.histCount; i++) {
                out.writeInt(w.histHumans[i]);
                out.writeInt(w.histZombies[i]);
            }

            Dispatch d = w.dispatch;
            out.writeInt(d.calls);
            out.writeInt(d.policeReserve);
            out.writeInt(d.squadReserve);
            out.writeInt(d.airSorties);
            out.writeInt(d.tankReserve);
            out.writeInt(d.copCount);
            out.writeInt(d.soldierCount);
            out.writeInt(d.zones.size());
            for (Dispatch.SafeZone z : d.zones) {
                out.writeFloat(z.x);
                out.writeFloat(z.y);
                out.writeFloat(z.r);
                out.writeBoolean(z.military);
                out.writeUTF(z.place);
                out.writeInt(z.capacity);
                out.writeInt(z.wantGuards);
            }

            // People in vehicles are saved as if they had just got out.
            ArrayList<Entity> all = new ArrayList<Entity>();
            // Roadblock officers are left out: the zone sets up its roadblocks again when it is loaded.
            java.util.HashSet<Entity> posted = new java.util.HashSet<Entity>();
            for (Fleet.Vehicle v : w.fleet.vehicles) if (v.guard != null) posted.add(v.guard);
            for (Entity e : w.entities) if (!e.dead && !posted.contains(e)) all.add(e);
            // People inside on errands come back out at the door.
            for (City.Building b : w.city.buildings)
                for (Entity e : b.visitors) {
                    e.x = b.doorX;
                    e.y = b.doorY;
                    all.add(e);
                }
            for (Fleet.Vehicle v : w.fleet.vehicles) {
                for (Entity e : v.riders) {
                    e.x = v.x;
                    e.y = v.y;
                    all.add(e);
                }
                if (Fleet.airborne(v) || v.type == Fleet.TRAIN || v.passengers <= 0 || v.state > 1 || v.broken) continue;
                for (int i = 0; i < v.passengers; i++) {
                    Entity p = w.create(v.passengerType, v.x, v.y);
                    if (v.guardUnit) w.applyRole(p, Entity.ROLE_GUARD);
                    all.add(p);
                }
            }
            out.writeInt(all.size());
            for (Entity e : all) writeEntity(out, e, d);
            // Families: who follows whom.
            for (Entity e : all) out.writeInt(e.leader == null ? -1 : all.indexOf(e.leader));

            int occupied = 0;
            for (City.Building b : w.city.buildings) if (!b.occupants.isEmpty()) occupied++;
            out.writeInt(occupied);
            for (int i = 0; i < w.city.buildings.size(); i++) {
                City.Building b = w.city.buildings.get(i);
                if (b.occupants.isEmpty()) continue;
                out.writeInt(i);
                out.writeFloat(b.barricade);
                out.writeInt(b.occupants.size());
                for (Entity e : b.occupants) writeEntity(out, e, d);
            }

            out.writeInt(w.pickups.size());
            for (World.Pickup p : w.pickups) {
                out.writeFloat(p.x);
                out.writeFloat(p.y);
                out.writeInt(p.rounds);
                out.writeInt(p.uses);
                out.writeByte(p.weapon);
                out.writeByte(p.melee);
            }
            out.writeInt(w.barriers.size());
            for (float[] b : w.barriers) {
                out.writeFloat(b[0]);
                out.writeFloat(b[1]);
                out.writeFloat(b[2]);
            }

            out.writeInt(w.corpses.size());
            for (World.Corpse c : w.corpses) {
                out.writeFloat(c.x);
                out.writeFloat(c.y);
                out.writeFloat(c.angle);
                out.writeFloat(c.radius);
                out.writeFloat(c.rise);
                out.writeFloat(c.age);
                out.writeInt(c.body);
                out.writeInt(c.head);
                out.writeInt(c.riseType);
                out.writeInt(c.origin);
                out.writeBoolean(c.zombie);
            }

            // Damaged, looted and collapsed buildings.
            int changed = 0;
            for (City.Building b : w.city.buildings) if (changed(b)) changed++;
            out.writeInt(changed);
            for (int i = 0; i < w.city.buildings.size(); i++) {
                City.Building b = w.city.buildings.get(i);
                if (!changed(b)) continue;
                out.writeInt(i);
                out.writeFloat(b.hp);
                out.writeBoolean(b.collapsed);
                out.writeInt(b.stock);
                out.writeInt(b.markCount);
                out.writeInt(b.markNext);
                for (int k = 0; k < b.markCount * 4; k++) out.writeFloat(b.marks[k]);
            }
            // Burnt-out cars and gas pumps.
            int burned = 0;
            for (boolean x : w.burned) if (x) burned++;
            out.writeInt(burned);
            for (int i = 0; i < w.burned.length; i++) if (w.burned[i]) out.writeInt(i);

            // Version 7: police and army history, checkpoints, where it started, the fallen and medkits.
            for (int i = 0; i < w.histCount; i++) out.writeInt(w.histArmed[i]);
            out.writeInt(w.turnedAway);
            out.writeInt(w.quarantined);
            out.writeUTF(w.outbreakPlace == null ? "" : w.outbreakPlace);
            out.writeInt(w.fallenHeroes.size());
            for (int[] h : w.fallenHeroes) for (int k = 0; k < 4; k++) out.writeInt(h[k]);
            out.writeInt(w.medkits.size());
            for (float[] m : w.medkits) for (int k = 0; k < 3; k++) out.writeFloat(m[k]);

            // Version 8: every building's supplies, hidden zombies and damage; the strain and the war.
            out.writeInt(w.city.buildings.size());
            for (City.Building b : w.city.buildings) {
                out.writeInt(b.food);
                out.writeInt(b.stock);
                out.writeByte(b.lurkers);
                out.writeByte((b.infestKnown ? 1 : 0) | (b.smashed ? 2 : 0) | (b.looted ? 4 : 0) | (b.outOfFood ? 8 : 0));
            }
            for (int t = 0; t < World.TR_COUNT; t++) out.writeByte((w.trait[t] ? 1 : 0) | (w.traitKnown[t] ? 2 : 0));
            out.writeUTF(w.strainName);
            out.writeInt(w.bites);
            out.writeInt(w.warResult);
            out.writeFloat(w.warBalance);
            out.writeInt(w.warLead);
            out.writeInt(w.startHumans);
            out.writeBoolean(w.blackout);
            out.writeBoolean(w.hospitalLost);
            out.writeInt(w.armedAtStores);
            out.writeBoolean(w.guardCalled);
            out.writeInt(w.evacuated);
            // The outbreak replay.
            out.writeInt(w.replayMeta.size());
            for (int i = 0; i < w.replayMeta.size(); i++) {
                for (float v : w.replayMeta.get(i)) out.writeFloat(v);
                short[] dots = w.replayDots.get(i);
                out.writeShort(dots.length);
                for (short v : dots) out.writeShort(v);
            }
            out.writeInt(w.turnEvents.size());
            for (float[] t : w.turnEvents) {
                out.writeFloat(t[0]);
                out.writeFloat(t[1]);
                out.writeFloat(t[2]);
            }
        } finally {
            out.close();
        }
        if (!tmp.renameTo(file)) throw new IOException("Could not write " + file);
    }

    private static boolean changed(City.Building b) {
        return b.collapsed || b.hp < b.maxHp || b.markCount > 0 || (b.kind == City.MARKET && b.stock < 240);
    }

    /** Version 1 saves came before dogs, which took entity type 4. */
    private static int type(int t, int version) {
        return version < 2 && t >= Entity.DOG ? t + 1 : t;
    }

    private static void writeEntity(DataOutputStream out, Entity e, Dispatch d) throws IOException {
        out.writeInt(e.type);
        out.writeFloat(e.x);
        out.writeFloat(e.y);
        out.writeFloat(e.angle);
        out.writeFloat(e.hp);
        out.writeFloat(e.maxHp);
        out.writeFloat(e.radius);
        out.writeFloat(e.speed);
        out.writeFloat(e.runSpeed);
        out.writeFloat(e.mass);
        out.writeInt(e.body);
        out.writeInt(e.head);
        out.writeInt(e.skin);
        out.writeInt(e.origin);
        out.writeBoolean(e.infected);
        out.writeFloat(e.infectTimer);
        out.writeBoolean(e.cureTried);
        out.writeInt(e.ammo);
        out.writeInt(e.magSize);
        out.writeInt(e.reserve);
        out.writeInt(e.grenades);
        out.writeBoolean(e.hasGun);
        out.writeInt(e.callsign);
        out.writeInt(e.squad);
        out.writeInt(e.member);
        // Only jobs that make sense after a reload are kept.
        int task = e.task;
        int zone = e.zone != null ? d.zones.indexOf(e.zone) : -1;
        if ((task == Dispatch.T_GUARD || task == Dispatch.T_SHELTER) && zone < 0) task = Dispatch.T_NONE;
        if (task != Dispatch.T_GUARD && task != Dispatch.T_SHELTER && task != Dispatch.T_POST && task != Dispatch.T_HOLD)
            task = Dispatch.T_NONE;
        out.writeInt(task);
        out.writeInt(zone);
        out.writeFloat(e.postX);
        out.writeFloat(e.postY);
        out.writeInt(e.role);
        // Someone on their way to enlist is saved as not yet asked, so they can volunteer again.
        out.writeBoolean(e.asked && e.task != Dispatch.T_ENLIST);
        out.writeInt(e.nameSeed);
        out.writeInt(e.kills);
        out.writeFloat(e.fresh);
        out.writeByte(e.weapon);
        out.writeByte(e.melee);
    }

    static World load(File file) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        try {
            int version = in.readInt();
            // Cities are generated differently since version 8 (districts), so older saves can't be rebuilt.
            if (version < 8 || version > VERSION) throw new IOException("Unsupported save version");
            CityConfig cfg = new CityConfig();
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                int v = in.readInt();
                if (i < cfg.v.length) cfg.v[i] = Math.max(0, Math.min(cfg.values(i).length - 1, v));
            }
            cfg.seed = in.readLong();
            World w = new World(cfg);

            w.time = in.readFloat();
            w.turned = in.readInt();
            w.zombiesKilled = in.readInt();
            w.civiliansLost = in.readInt();
            w.shotsFired = in.readInt();
            w.cured = in.readInt();
            w.peakZombies = in.readInt();
            if (version >= 4) {
                w.peakCops = in.readInt();
                w.peakSoldiers = in.readInt();
                w.recruits = in.readInt();
                w.refused = in.readInt();
                if (version >= 5) {
                    w.mutation = in.readInt();
                    w.outbreakTime = in.readFloat();
                }
            }
            w.statStep = in.readFloat();
            w.histCount = in.readInt();
            for (int i = 0; i < w.histCount; i++) {
                w.histHumans[i] = in.readInt();
                w.histZombies[i] = in.readInt();
            }

            Dispatch d = w.dispatch;
            d.calls = in.readInt();
            d.policeReserve = in.readInt();
            d.squadReserve = in.readInt();
            d.airSorties = in.readInt();
            if (version >= 3) d.tankReserve = in.readInt();
            int copCount = in.readInt(), soldierCount = in.readInt();
            int zones = in.readInt();
            for (int i = 0; i < zones; i++)
                d.restoreZone(in.readFloat(), in.readFloat(), in.readFloat(), in.readBoolean(), in.readUTF(),
                        in.readInt(), in.readInt());

            int count = in.readInt();
            for (int i = 0; i < count; i++) w.entities.add(readEntity(in, w, d, version));
            if (version >= 2)
                for (int i = 0; i < count; i++) {
                    int lead = in.readInt();
                    if (lead >= 0 && lead < count) w.entities.get(i).leader = w.entities.get(lead);
                }

            int occupied = in.readInt();
            for (int i = 0; i < occupied; i++) {
                City.Building b = w.city.buildings.get(in.readInt());
                b.barricade = in.readFloat();
                int people = in.readInt();
                for (int k = 0; k < people; k++) {
                    Entity e = readEntity(in, w, d, version);
                    e.dead = true;
                    e.removed = true;
                    b.occupants.add(e);
                }
            }

            int pickups = in.readInt();
            for (int i = 0; i < pickups; i++) {
                World.Pickup p = new World.Pickup();
                p.x = in.readFloat();
                p.y = in.readFloat();
                p.rounds = in.readInt();
                p.uses = in.readInt();
                if (version >= 12) {
                    p.weapon = in.readByte();
                    p.melee = in.readByte();
                }
                w.pickups.add(p);
            }
            int barriers = in.readInt();
            for (int i = 0; i < barriers; i++) {
                float[] b = w.placeBarricade(in.readFloat(), in.readFloat());
                float hp = in.readFloat();
                if (b != null) b[2] = hp;
            }

            int corpses = in.readInt();
            for (int i = 0; i < corpses; i++) {
                World.Corpse c = new World.Corpse();
                c.x = in.readFloat();
                c.y = in.readFloat();
                c.angle = in.readFloat();
                c.radius = in.readFloat();
                c.rise = in.readFloat();
                c.age = in.readFloat();
                c.body = in.readInt();
                c.head = in.readInt();
                c.riseType = type(in.readInt(), version);
                c.origin = type(in.readInt(), version);
                c.zombie = in.readBoolean();
                w.corpses.add(c);
            }
            if (version >= 2) {
                int changed = in.readInt();
                for (int i = 0; i < changed; i++) {
                    City.Building b = w.city.buildings.get(in.readInt());
                    b.hp = in.readFloat();
                    boolean collapsed = in.readBoolean();
                    b.stock = in.readInt();
                    b.markCount = in.readInt();
                    b.markNext = in.readInt();
                    for (int k = 0; k < b.markCount * 4; k++) b.marks[k] = in.readFloat();
                    if (collapsed) w.city.collapse(b);
                }
                int burned = in.readInt();
                for (int i = 0; i < burned; i++) {
                    int t = in.readInt();
                    if (t >= 0 && t < w.burned.length) w.burnTile(t);
                }
            }
            for (int i = 0; i < w.histCount; i++) w.histArmed[i] = in.readInt();
            w.turnedAway = in.readInt();
            w.quarantined = in.readInt();
            String place = in.readUTF();
            w.outbreakPlace = place.length() == 0 ? null : place;
            int fallen = in.readInt();
            for (int i = 0; i < fallen; i++) w.fallenHeroes.add(new int[]{in.readInt(), in.readInt(), in.readInt(), in.readInt()});
            int kits = in.readInt();
            for (int i = 0; i < kits; i++) w.medkits.add(new float[]{in.readFloat(), in.readFloat(), in.readFloat()});
            int nb = in.readInt();
            for (int i = 0; i < nb; i++) {
                int food = in.readInt(), stock = in.readInt(), lurkers = in.readByte(), flags = in.readByte();
                if (i >= w.city.buildings.size()) continue;
                City.Building b = w.city.buildings.get(i);
                b.food = food;
                b.stock = stock;
                b.lurkers = lurkers;
                b.infestKnown = (flags & 1) != 0;
                b.smashed = (flags & 2) != 0;
                b.looted = (flags & 4) != 0;
                b.outOfFood = (flags & 8) != 0;
            }
            for (int t = 0; t < World.TR_COUNT; t++) {
                int f = in.readByte();
                w.trait[t] = (f & 1) != 0;
                w.traitKnown[t] = (f & 2) != 0;
            }
            w.strainName = in.readUTF();
            w.bites = in.readInt();
            w.warResult = in.readInt();
            w.warBalance = in.readFloat();
            w.warLead = in.readInt();
            w.startHumans = in.readInt();
            w.blackout = in.readBoolean();
            w.hospitalLost = in.readBoolean();
            w.armedAtStores = in.readInt();
            if (version >= 9) w.guardCalled = in.readBoolean();
            if (version >= 10) w.evacuated = in.readInt();
            if (version >= 11) {
                int frames = in.readInt();
                for (int i = 0; i < frames; i++) {
                    float[] m = new float[5];
                    for (int k = 0; k < 5; k++) m[k] = in.readFloat();
                    short[] dots = new short[in.readShort() & 0xFFFF];
                    for (int k = 0; k < dots.length; k++) dots[k] = in.readShort();
                    w.replayMeta.add(m);
                    w.replayDots.add(dots);
                }
                int turns = in.readInt();
                for (int i = 0; i < turns; i++) w.turnEvents.add(new float[]{in.readFloat(), in.readFloat(), in.readFloat()});
            }
            d.copCount = copCount;
            d.soldierCount = soldierCount;
            w.afterLoad();
            return w;
        } finally {
            in.close();
        }
    }

    private static Entity readEntity(DataInputStream in, World w, Dispatch d, int version) throws IOException {
        int type = type(in.readInt(), version);
        float x = in.readFloat(), y = in.readFloat();
        Entity e = w.create(type, x, y);
        e.angle = in.readFloat();
        e.hp = in.readFloat();
        e.maxHp = in.readFloat();
        e.radius = in.readFloat();
        e.speed = in.readFloat();
        e.runSpeed = in.readFloat();
        e.mass = in.readFloat();
        e.body = in.readInt();
        e.head = in.readInt();
        e.skin = in.readInt();
        e.origin = in.readInt();
        if (e.origin >= 0) e.origin = type(e.origin, version);
        e.infected = in.readBoolean();
        e.infectTimer = in.readFloat();
        e.cureTried = in.readBoolean();
        e.ammo = in.readInt();
        e.magSize = in.readInt();
        e.reserve = in.readInt();
        e.grenades = in.readInt();
        e.hasGun = in.readBoolean();
        e.callsign = in.readInt();
        e.squad = in.readInt();
        e.member = in.readInt();
        e.task = in.readInt();
        int zone = in.readInt();
        e.postX = in.readFloat();
        e.postY = in.readFloat();
        if (version >= 3) e.role = in.readInt();
        if (version >= 4) e.asked = in.readBoolean();
        if (version >= 5) {
            e.nameSeed = in.readInt();
            e.kills = in.readInt();
            e.fresh = in.readFloat();
        }
        if (version >= 12) {
            e.weapon = in.readByte();
            e.melee = in.readByte();
        }
        if (zone >= 0 && zone < d.zones.size()) e.zone = d.zones.get(zone);
        return e;
    }
}
