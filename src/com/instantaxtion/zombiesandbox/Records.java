package com.instantaxtion.zombiesandbox;

import android.content.Context;
import android.content.SharedPreferences;

/** Best results across every game played, and achievements, saved on the phone. */
final class Records {
    static final String[][] ACHIEVEMENTS = {
            {"Contained", "Wipe out an outbreak of 20 or more zombies with people still alive."},
            {"Hold the Line", "Keep people alive through 10 minutes of outbreak."},
            {"Volunteers", "Have 10 civilians join the police or army in one game."},
            {"Back to Normal", "See the city start to recover after an outbreak."},
            {"Horde Breaker", "Kill 500 zombies in one game."},
            {"The Big One", "Watch a horde of 50 or more roam the city."},
            {"Fire Brigade", "Have fire crews put out 5 fires."},
            {"Armor", "Get a tank on the streets."},
            {"Medic!", "Medics patch up 10 hurt people in one game."},
            {"Evolution", "Let the infection reach stage 5."},
            {"Wrecking Ball", "Bring down 3 buildings in one game."},
            {"Road Rage", "Wreck 10 cars in one game."},
            {"Last Stand", "Watch the city fall."},
            {"Photographer", "Take a screenshot."},
            {"Veteran", "Play 10 games."},
    };
    static final int CONTAINED = 0, HOLD = 1, VOLUNTEERS = 2, RECOVER = 3, HORDE_BREAKER = 4, BIG_ONE = 5, FIRE = 6,
            ARMOR = 7, MEDIC = 8, EVOLUTION = 9, WRECKING = 10, ROAD_RAGE = 11, LAST_STAND = 12, PHOTO = 13, VETERAN = 14;
    static final String[] RECORD_NAMES = {"Longest outbreak survived", "Most zombies killed in a game",
            "Biggest horde", "Most volunteers in a game", "Zombies killed, all games", "Games played"};

    private final SharedPreferences prefs;

    Records(Context context) {
        prefs = context.getSharedPreferences("records", Context.MODE_PRIVATE);
    }

    boolean unlocked(int i) {
        return prefs.getInt("ach" + i, 0) == 1;
    }

    int unlockedCount() {
        int n = 0;
        for (int i = 0; i < ACHIEVEMENTS.length; i++) if (unlocked(i)) n++;
        return n;
    }

    /** Unlocks an achievement; returns true the first time. */
    boolean unlock(int i) {
        if (unlocked(i)) return false;
        prefs.edit().putInt("ach" + i, 1).apply();
        return true;
    }

    int record(int i) {
        return prefs.getInt("rec" + i, 0);
    }

    String recordText(int i) {
        int v = record(i);
        if (i == 0) return String.format("%d:%02d", v / 60, v % 60);
        return String.valueOf(v);
    }

    void best(int i, int value) {
        if (value > record(i)) prefs.edit().putInt("rec" + i, value).apply();
    }

    void add(int i, int amount) {
        if (amount != 0) prefs.edit().putInt("rec" + i, record(i) + amount).apply();
    }

    void gameStarted() {
        add(5, 1);
    }

    private int lastKilled;
    private World lastWorld;

    /**
     * Checks the current game against the records and achievements. Returns the name of an achievement
     * unlocked just now, or null.
     */
    String check(World w) {
        if (w != lastWorld) {
            lastWorld = w;
            lastKilled = w.zombiesKilled;
        }
        add(4, w.zombiesKilled - lastKilled);
        lastKilled = w.zombiesKilled;
        if (w.humans > 0) best(0, (int) w.outbreakTime);
        best(1, w.zombiesKilled);
        best(2, w.biggestHorde);
        best(3, w.recruits);
        String got = null;
        boolean[] now = {
                w.peakZombies >= 20 && w.zombieCount() == 0 && w.humans > 0,
                w.outbreakTime >= 600 && w.humans > 0,
                w.recruits >= 10,
                w.recovering,
                w.zombiesKilled >= 500,
                w.biggestHorde >= 50,
                w.firesOut >= 5,
                w.tanksDeployed > 0,
                w.healed >= 10,
                w.mutation >= 5,
                w.collapsedCount >= 3,
                w.carsWrecked >= 10,
                w.peakZombies > 0 && w.humans == 0 && w.time > 10,
                false,
                record(5) >= 10,
        };
        for (int i = 0; i < now.length; i++) if (now[i] && unlock(i)) got = ACHIEVEMENTS[i][0];
        return got;
    }
}
