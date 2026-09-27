package com.instantaxtion.zombiesandbox;

/** A person walking around the city: a civilian, a cop, a soldier, a medic or one of the zombie kinds. */
final class Entity {
    static final int CIVILIAN = 0, COP = 1, SOLDIER = 2, MEDIC = 3, ZOMBIE = 4, RUNNER = 5, BRUTE = 6,
            CRAWLER = 7, SCREAMER = 8;
    static final int TYPE_COUNT = 9;
    static final String[] NAMES = {"Civilian", "Cop", "Soldier", "Medic", "Zombie", "Runner", "Brute", "Crawler",
            "Screamer"};

    int type;
    float x, y, vx, vy, angle;
    float hp, maxHp, radius, speed, runSpeed, mass = 1f;

    // Desired movement for this tick: unit direction and speed.
    float mx, my, want;

    float cooldown, reload, biteCd, grenadeCd, meleeCd, stun;
    float wanderAngle, wanderTimer, fleeTimer, infectTimer;
    float hurt, phase;
    float threatX, threatY;
    /** Rounds in the magazine, magazine size and spare rounds. Nobody gets more without resupplying. */
    int ammo, magSize, reserve, burst, grenades;
    /** A civilian carrying a gun (their own, or one picked up). */
    boolean hasGun;

    boolean infected, dead, removed, killedByZombie, gibbed, aiming, blocked, paused;

    int body, head, skin;
    /** For zombies: what the zombie used to be, or -1 if it was spawned as a zombie. */
    int origin = -1;

    // Zombies: where a noise came from, and the screamer's cooldown.
    float noiseX, noiseY, noiseTimer, screamCd;

    // Dispatch: current job, radio callsign and speech icons.
    int task;
    Dispatch.Incident incident;
    Dispatch.SafeZone zone;
    int slot, callsign, squad, member;
    boolean onScene, outOfAmmoSaid, cureTried;
    float phoneTimer, talkTimer, callCd, taskTimer;
    /** A spot to stand at: guard posts and player orders. */
    float postX, postY;
    /** Path to an ordered destination. */
    int[] orderField;
    /** The building a civilian is running to hide in. */
    City.Building building;
    /** A dropped gun a civilian is going to pick up. */
    World.Pickup pickup;

    boolean isZombie() {
        return type >= ZOMBIE;
    }

    boolean isArmed() {
        return type == COP || type == SOLDIER;
    }

    /** Anyone who can shoot: police, soldiers and civilians with a gun. */
    boolean canShoot() {
        return isArmed() || hasGun;
    }
}
