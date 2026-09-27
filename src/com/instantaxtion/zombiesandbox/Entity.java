package com.instantaxtion.zombiesandbox;

/** A person walking around the city: a civilian, a cop, a soldier or one of the zombie kinds. */
final class Entity {
    static final int CIVILIAN = 0, COP = 1, SOLDIER = 2, ZOMBIE = 3, RUNNER = 4, BRUTE = 5;
    static final int TYPE_COUNT = 6;
    static final String[] NAMES = {"Civilian", "Cop", "Soldier", "Zombie", "Runner", "Brute"};

    int type;
    float x, y, vx, vy, angle;
    float hp, maxHp, radius, speed, runSpeed, mass = 1f;

    // Desired movement for this tick: unit direction and speed.
    float mx, my, want;

    float cooldown, reload, biteCd, grenadeCd;
    float wanderAngle, wanderTimer, fleeTimer, infectTimer;
    float hurt, phase;
    float threatX, threatY;
    int ammo, magSize, burst, grenades;

    boolean infected, dead, removed, killedByZombie, gibbed, aiming, blocked, paused;

    int body, head, skin;
    /** For zombies: what the zombie used to be, or -1 if it was spawned as a zombie. */
    int origin = -1;

    // Dispatch: current job, radio callsign and speech icons.
    int task;
    Dispatch.Incident incident;
    Dispatch.SafeZone zone;
    int slot, callsign, squad, member;
    boolean onScene;
    float phoneTimer, talkTimer, callCd;

    boolean isZombie() {
        return type >= ZOMBIE;
    }

    boolean isArmed() {
        return type == COP || type == SOLDIER;
    }
}
