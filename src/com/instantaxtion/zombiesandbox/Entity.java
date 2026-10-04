package com.instantaxtion.zombiesandbox;

/** Someone in the city: a civilian, a cop, a soldier, a medic, a dog or one of the zombie kinds. */
final class Entity {
    static final int CIVILIAN = 0, COP = 1, SOLDIER = 2, MEDIC = 3, DOG = 4, RAIDER = 5, ZOMBIE = 6, RUNNER = 7,
            BRUTE = 8, CRAWLER = 9, SCREAMER = 10, ZOMBIE_DOG = 11, SPITTER = 12, BLOATER = 13, FIREFIGHTER = 14;
    static final int TYPE_COUNT = 15;
    static final String[] NAMES = {"Civilian", "Cop", "Soldier", "Medic", "Dog", "Raider", "Zombie", "Runner", "Brute",
            "Crawler", "Screamer", "Zombie dog", "Spitter", "Bloater", "Firefighter"};

    /** Soldiers' jobs: rifleman, the commander, snipers and machine gunners. */
    static final int ROLE_RIFLE = 0, ROLE_COMMANDER = 1, ROLE_SNIPER = 2, ROLE_GUNNER = 3;
    /** Police jobs: riot officers carry shields, K9 handlers work with a police dog. */
    static final int ROLE_RIOT = 4, ROLE_K9 = 5;
    /** The National Guard: soldiers called up by the governor to protect civilians. */
    static final int ROLE_GUARD = 6;
    static final String[] ROLE_NAMES = {"Soldier", "Commander", "Sniper", "Gunner", "Riot officer", "K9 handler",
            "National Guard"};

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
    boolean onScene, outOfAmmoSaid;
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
    /** Zombies: where the prey was last seen, how long they remember it, and time left searching there. */
    float lastX, lastY, memory, searchTimer, moanCd;
    /** Civilians: their home, and the errand they are on (a place to go and how long to stay). */
    City.Building home, errand;
    /**
     * Everyday life: what this person does with their day (J_ constants), where they work, and the state of
     * whatever they're doing now (a run, a delivery round, a stall, a chat).
     */
    int job;
    /** A walking route (tile indices) to a building, how far along it they are, and where it goes. */
    int[] path;
    int pathIdx;
    City.Building pathDest;
    City.Building work;
    float jobTimer, chat;
    int jobStep;
    Entity chatWith;
    float[] spot;
    java.util.ArrayList<City.Building> round;
    static final int J_NONE = 0, J_WORKER = 1, J_HOMEBODY = 2, J_ERRANDS = 3, J_SHOPKEEPER = 4, J_STUDENT = 5,
            J_JOGGER = 6, J_PARK = 7, J_POSTIE = 8, J_VENDOR = 9;
    float errandTimer;
    boolean homeChecked;
    /** The last hard knock (blast, car, charge), so the body flies the right way if it kills them. Fades fast. */
    float knockX, knockY;
    /** Brutes: a charge in progress (seconds left), its direction and cooldown. Crawlers: lying in wait. */
    float charge, chargeCd, chargeX, chargeY;
    boolean hidden;
    /** Armed residents holding a building together. */
    boolean militia;
    /** The gun they carry if it isn't their standard issue (pistol, shotgun or rifle), and a bat or axe. */
    int weapon, melee;
    static final int W_STD = 0, W_PISTOL = 1, W_SHOTGUN = 2, W_RIFLE = 3;
    static final int M_NONE = 0, M_BAT = 1, M_AXE = 2;
    static final String[] WEAPON_NAMES = {"", "Pistol", "Shotgun", "Rifle"};
    static final String[] MELEE_NAMES = {"", "Bat", "Axe"};

    /** The kind of gun in their hands. */
    int gunKind() {
        return weapon != W_STD ? weapon : type == SOLDIER ? W_RIFLE : W_PISTOL;
    }
    /** The last place this one went on an errand (so they go somewhere else next). */
    City.Building lastErrand;
    /** Set to the world's grid frame when someone is following this one. */
    int followerFrame;
    /** Which way (1 or -1) this one steps round obstacles it walks straight into. */
    float slideSide = 1;
    /** Breath for sprinting (0 to 1): the living tire, the dead don't. */
    float stamina = 1;
    /** Police and soldiers: when they last radioed a sighting to the others, and a retreat already called. */
    float shareCd;
    boolean retreatSaid;

    boolean isZombie() {
        return type >= ZOMBIE && type <= BLOATER;
    }

    boolean isArmed() {
        return type == COP || type == SOLDIER;
    }

    /** Anyone who can shoot: police, soldiers and civilians with a gun. */
    boolean canShoot() {
        return isArmed() || hasGun;
    }
}
