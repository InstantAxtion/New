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

    final City city;
    final Dispatch dispatch;
    final Random rnd = new Random();
    final ArrayList<Entity> entities = new ArrayList<Entity>();
    final ArrayList<Corpse> corpses = new ArrayList<Corpse>();
    final ArrayList<Grenade> grenades = new ArrayList<Grenade>();
    final ArrayList<Explosion> explosions = new ArrayList<Explosion>();

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
    static final byte P_DOT = 0, P_SMOKE = 1, P_FLASH = 2, P_FIRE = 3;
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
    private float fieldTimer;
    final int[] counts = new int[Entity.TYPE_COUNT];
    int humans, zombies, turned, zombiesKilled;
    String message;
    float messageTime;
    private boolean outbreak;

    World(CityConfig cfg) {
        city = new City(cfg);
        dispatch = new Dispatch(this, cfg.reinforcements());
        gw = (int) Math.ceil(city.worldW() / CELL);
        gh = (int) Math.ceil(city.worldH() / CELL);
        cellStart = new int[gw * gh + 1];
        cellCount = new int[gw * gh];
        cellFill = new int[gw * gh];
    }

    void populate(CityConfig cfg) {
        spawnRandom(Entity.CIVILIAN, cfg.civilians());
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
        if (base != null) {
            for (int i = 0; i < cfg.soldiers(); i++)
                spawn(Entity.SOLDIER, base.x + rnd.nextFloat() * base.r - base.r / 2, base.y + rnd.nextFloat() * base.r - base.r / 2);
        } else {
            spawnRandom(Entity.SOLDIER, cfg.soldiers());
        }
        // Zombies start in a few small outbreaks rather than spread evenly.
        int left = cfg.zombies();
        while (left > 0) {
            float[] p = city.randomWalkable(rnd);
            int group = Math.min(left, 1 + rnd.nextInt(6));
            for (int i = 0; i < group; i++)
                spawn(Entity.ZOMBIE, p[0] + rnd.nextFloat() * 30 - 15, p[1] + rnd.nextFloat() * 30 - 15);
            left -= group;
        }
        recount();
    }

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
                e.body = 0xFF23408E;
                e.head = 0xFF141C38;
                break;
            case Entity.SOLDIER:
                e.hp = 100;
                e.radius = 4.0f;
                e.speed = 24;
                e.runSpeed = 40;
                e.magSize = 30;
                e.grenades = 3;
                e.body = 0xFF55623A;
                e.head = 0xFF3C4628;
                e.mass = 1.3f;
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
                    : type == Entity.BRUTE ? 0xFF4D3F4F : SHIRTS[rnd.nextInt(SHIRTS.length)];
            e.body = mix(base, 0xFF2F3A26, type == Entity.BRUTE ? 0.2f : 0.45f);
            e.head = e.skin;
        }
        e.maxHp = e.hp;
        e.ammo = e.magSize;
        return e;
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
            }
        }
        for (int i = corpses.size() - 1; i >= 0; i--) {
            Corpse c = corpses.get(i);
            float ddx = c.x - x, ddy = c.y - y;
            if (ddx * ddx + ddy * ddy < r * r) corpses.remove(i);
        }
        for (int i = 0; i < dcount; i++) {
            float ddx = dx[i] - x, ddy = dy[i] - y;
            if (ddx * ddx + ddy * ddy < r * r) dr[i] = 0;
        }
    }

    void clearAll() {
        entities.clear();
        corpses.clear();
        grenades.clear();
        explosions.clear();
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
        updateGrenades(dt);
        cleanup();
        updateCorpses(dt);
        updateEffects(dt);
        recount();
    }

    private void recount() {
        Arrays.fill(counts, 0);
        for (int i = 0, n = entities.size(); i < n; i++) counts[entities.get(i).type]++;
        int h = counts[Entity.CIVILIAN] + counts[Entity.COP] + counts[Entity.SOLDIER];
        int z = counts[Entity.ZOMBIE] + counts[Entity.RUNNER] + counts[Entity.BRUTE];
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
        e.wanderTimer -= dt;
        e.fleeTimer -= dt;
        if (e.infected) {
            e.infectTimer -= dt;
            if (e.infectTimer <= 0) {
                e.hp = 0;
                e.killedByZombie = true;
                return;
            }
        }
        if (e.isZombie()) thinkZombie(e, dt);
        else if (e.isArmed()) thinkArmed(e, dt);
        else thinkCivilian(e, dt);
    }

    private void thinkZombie(Entity z, float dt) {
        if (rnd.nextFloat() < dt * 0.03f) emit(z.type == Entity.BRUTE ? Sfx.GROAN_DEEP : Sfx.GROAN, z.x, z.y);
        Entity t = nearest(z, 110, false, true);
        if (t != null) {
            float ddx = t.x - z.x, ddy = t.y - z.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            steer(z, ddx / d, ddy / d, d < 45 ? z.runSpeed : z.speed);
            if (d < z.radius + t.radius + 2.5f && z.biteCd <= 0) bite(z, t, ddx / d, ddy / d);
            return;
        }
        int dist = city.fieldAt(city.humanDist, z.x, z.y);
        if (dist < 40 && followField(z, city.humanDist, z.speed)) return;
        wander(z, z.speed * 0.45f);
    }

    private void bite(Entity z, Entity t, float nx, float ny) {
        float dmg = z.type == Entity.BRUTE ? 30 : z.type == Entity.RUNNER ? 8 : 12;
        z.biteCd = z.type == Entity.RUNNER ? 0.6f : 0.9f;
        t.hp -= dmg;
        t.hurt = 1;
        t.killedByZombie = true;
        t.fleeTimer = 2f;
        t.threatX = z.x;
        t.threatY = z.y;
        bloodBurst(t.x, t.y, 6, nx, ny);
        emit(Sfx.BITE, t.x, t.y);
        if (!t.infected && rnd.nextFloat() < 0.4f) {
            t.infected = true;
            t.infectTimer = 12 + rnd.nextFloat() * 14;
        }
        if (z.type == Entity.BRUTE) tryMove(t, nx * 8, ny * 8);
    }

    private void thinkCivilian(Entity e, float dt) {
        Entity threat = nearest(e, 100, true, true);
        if (threat == null) threat = nearest(e, 26, true, false);
        float threatDist = Float.MAX_VALUE;
        if (threat != null) {
            if (e.fleeTimer <= 0 && rnd.nextFloat() < 0.3f) emit(Sfx.SCREAM, e.x, e.y);
            e.fleeTimer = 2.5f;
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

        // Safe zones: scared people head for one, and some go as soon as they hear it on the news.
        boolean anyZone = !dispatch.zones.isEmpty();
        if ((e.task == Dispatch.T_SEEK || e.task == Dispatch.T_SHELTER) && !anyZone) e.task = Dispatch.T_NONE;
        if (e.task == Dispatch.T_NONE && anyZone && (e.fleeTimer > 0 || rnd.nextFloat() < dt * 0.03f))
            e.task = Dispatch.T_SEEK;
        if (e.task == Dispatch.T_SEEK) {
            Dispatch.SafeZone z = dispatch.zoneAt(e.x, e.y, 0.7f);
            if (z != null) {
                e.task = Dispatch.T_SHELTER;
                e.zone = z;
            }
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
        if (e.fleeTimer > 0) {
            e.paused = false;
            float ax = e.x - e.threatX, ay = e.y - e.threatY;
            float d = (float) Math.sqrt(ax * ax + ay * ay) + 0.001f;
            flee(e, ax / d, ay / d, e.runSpeed);
        } else {
            wander(e, e.speed);
        }
    }

    private void thinkArmed(Entity e, float dt) {
        boolean soldier = e.type == Entity.SOLDIER;
        float range = soldier ? 230 : 160;
        if (e.reload > 0) {
            e.reload -= dt;
            if (e.reload <= 0) e.ammo = e.magSize;
        }
        Entity t = nearest(e, range, true, true);
        if (t != null) {
            float ddx = t.x - e.x, ddy = t.y - e.y;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.001f;
            e.aiming = true;
            e.angle = turn(e.angle, (float) Math.atan2(ddy, ddx), dt * 14);
            float keep = soldier ? 40 : 50;
            Dispatch.SafeZone guarding = e.task == Dispatch.T_GUARD ? e.zone : null;
            if (d < keep && (guarding == null || inside(e, guarding, 1f))) steer(e, -ddx / d, -ddy / d, e.runSpeed * 0.8f);
            else if (guarding != null) holdPost(e, guarding, 0.5f);
            else steer(e, 0, 0, 0);
            if (e.reload <= 0 && e.cooldown <= 0) fire(e, t, d, range);
            if (soldier && e.grenades > 0 && e.grenadeCd <= 0 && d > 70 && d < 200
                    && countZombiesNear(t.x, t.y, 40) >= 5) {
                throwGrenade(e, t.x, t.y);
            }
            return;
        }
        e.aiming = false;
        if (e.reload <= 0 && e.ammo < e.magSize / 2) e.reload = soldier ? 2.2f : 1.6f;
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
        int dist = city.fieldAt(city.zombieDist, e.x, e.y);
        int hunt = soldier ? 200 : 40;
        if (dist < hunt && followField(e, city.zombieDist, e.speed * 1.3f)) return;
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

    private void fire(Entity e, Entity t, float d, float range) {
        boolean soldier = e.type == Entity.SOLDIER;
        e.ammo--;
        float dmg;
        if (soldier) {
            e.burst++;
            e.cooldown = e.burst % 3 == 0 ? 0.45f : 0.1f;
            dmg = 16;
        } else {
            e.cooldown = 0.55f;
            dmg = 10;
        }
        if (e.ammo <= 0) e.reload = soldier ? 2.2f : 1.6f;
        float ca = (float) Math.cos(e.angle), sa = (float) Math.sin(e.angle);
        float mx = e.x + ca * e.radius * (soldier ? 2.5f : 2f) - sa * e.radius * 0.3f;
        float my = e.y + sa * e.radius * (soldier ? 2.5f : 2f) + ca * e.radius * 0.3f;
        float hit = 0.7f * (1 - 0.4f * d / range) + (t.type == Entity.BRUTE ? 0.1f : 0);
        if (rnd.nextFloat() < hit) {
            t.hp -= dmg;
            t.hurt = 1;
            float nx = (t.x - e.x) / d, ny = (t.y - e.y) / d;
            bloodBurst(t.x, t.y, 4, nx, ny);
            tracer(mx, my, t.x + rnd.nextFloat() * 2 - 1, t.y + rnd.nextFloat() * 2 - 1);
            tryMove(t, nx * 1.2f / t.mass, ny * 1.2f / t.mass);
        } else {
            float a = (float) Math.atan2(t.y - my, t.x - mx) + (rnd.nextBoolean() ? 1 : -1) * (0.05f + rnd.nextFloat() * 0.1f);
            float len = d + 20 + rnd.nextFloat() * 40;
            float ex = mx, ey = my;
            for (float s = 0; s < len; s += 4) {
                ex = mx + (float) Math.cos(a) * s;
                ey = my + (float) Math.sin(a) * s;
                if (city.solidAt(ex, ey)) {
                    for (int i = 0; i < 3; i++)
                        particle(ex, ey, rnd.nextFloat() * 60 - 30, rnd.nextFloat() * 60 - 30, 0.2f, 0.7f, 0xFFFFD27A, P_DOT);
                    break;
                }
            }
            tracer(mx, my, ex, ey);
        }
        particle(mx, my, 0, 0, 0.06f, soldier ? 3.2f : 2.6f, 0xFFFFE9A0, P_FLASH);
        emit(soldier ? Sfx.RIFLE : Sfx.PISTOL, e.x, e.y);
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
        boolean moved = tryMove(e, sx, sy);
        if (!moved && e.want > 0) e.blocked = true;
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
        dispatch.onDeath(e);
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
        boolean turns = !e.isZombie() && !e.gibbed && (e.killedByZombie || e.infected);
        c.rise = turns ? 2.5f + rnd.nextFloat() * 3f : -1;
        c.riseType = rnd.nextFloat() < 0.15f ? Entity.RUNNER : Entity.ZOMBIE;
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
        Explosion ex = new Explosion();
        ex.x = x;
        ex.y = y;
        ex.r = radius;
        ex.life = ex.max = 0.45f;
        explosions.add(ex);
        emit(Sfx.EXPLOSION, x, y);
        for (int i = 0; i < 40; i++) {
            float a = rnd.nextFloat() * TAU, s = 30 + rnd.nextFloat() * radius * 2.2f;
            particle(x, y, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 0.3f + rnd.nextFloat() * 0.4f,
                    2 + rnd.nextFloat() * 3, rnd.nextBoolean() ? 0xFFFFB030 : 0xFFFF6A1A, P_FIRE);
        }
        for (int i = 0; i < 22; i++) {
            float a = rnd.nextFloat() * TAU, s = 5 + rnd.nextFloat() * radius * 0.8f;
            particle(x, y, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 1.2f + rnd.nextFloat() * 1.3f,
                    5 + rnd.nextFloat() * 6, 0xFF3A3A3A, P_SMOKE);
        }
        decal(x, y, radius * 0.55f, 0xAA121212);
        shake = Math.min(1.5f, shake + radius / 60f);
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

    private void particle(float x, float y, float vx, float vy, float life, float size, int color, byte type) {
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
        int i = dnext;
        dnext = (dnext + 1) % MAXD;
        if (dcount < MAXD) dcount++;
        dx[i] = x;
        dy[i] = y;
        dr[i] = r;
        dcol[i] = color;
    }

    private void updateEffects(float dt) {
        for (int i = 0; i < MAXP; i++) {
            if (plife[i] <= 0) continue;
            plife[i] -= dt;
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
            float drag = ptype[i] == P_SMOKE ? 1.5f : 5f;
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
