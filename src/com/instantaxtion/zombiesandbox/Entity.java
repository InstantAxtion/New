package com.instantaxtion.zombiesandbox;

/** Someone in the city: a civilian, a cop, a soldier, a medic, a dog or one of the zombie kinds. */
final class Entity {
    static final int CIVILIAN = 0, COP = 1, SOLDIER = 2, MEDIC = 3, DOG = 4, RAIDER = 5, ZOMBIE = 6, RUNNER = 7,
            BRUTE = 8, CRAWLER = 9, SCREAMER = 10, ZOMBIE_DOG = 11, SPITTER = 12, BLOATER = 13;
    static final int TYPE_COUNT = 14;
    static final String[] NAMES = {"Civilian", "Cop", "Soldier", "Medic", "Dog", "Raider", "Zombie", "Runner", "Brute",
            "Crawler", "Screamer", "Zombie dog", "Spitter", "Bloater"};

    /** Soldiers' jobs: rifleman, the commander, snipers and machine gunners. */
    static final int ROLE_RIFLE = 0, ROLE_COMMANDER = 1, ROLE_SNIPER = 2, ROLE_GUNNER = 3;
    static final String[] ROLE_NAMES = {"Soldier", "Commander", "Sniper", "Gunner"};
    /** Police jobs: riot officers carry shields, K9 handlers work with a police dog. */
    static final int ROLE_RIOT = 4, ROLE_K9 = 5;

    int type, role;
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

    /** Safe zone checkpoints: already checked for bites, and turned away. */
    boolean screened, refused;
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

    /** Family: followers stay with their leader. A dog's owner is its leader too. */
    Entity leader;
    /** Zombie hordes: members follow a leader who roams the city. */
    Entity hordeLeader;
    boolean leadsHorde;
    float roamX, roamY, feedTimer;
    int hordeSize;
    boolean hordeAnnounced;
    /** Civilians: already asked to sign up (and said no), and where they are going to enlist. */
    boolean asked;
    City.Facility enlistAt;
    /** Zombies approach from their own angle so a crowd surrounds its prey. */
    float flank;
    /** Picks this person's name (see {@link Names}). */
    int nameSeed;
    /** Zombies shot or run down by this person. */
    int kills;
    /** Newly turned zombies are quicker for a while. */
    float fresh;
    /** How long someone remembers where they last saw a zombie (threatX/threatY). */
    float fear;
    /** Getting unstuck: how long they've been making no progress, and a sidestep in progress. */
    float stuckTime, unstick, unstickAngle;
    /** Civilians: a cop or soldier nearby to run towards. */
    Entity protector;
    /** A car this civilian has flagged down and is running to. */
    Fleet.Vehicle ride;

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
