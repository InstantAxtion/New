package com.instantaxtion.zombiesandbox;

/** Sound effect ids shared by the simulation (which emits them) and {@link Sound} (which plays them). */
final class Sfx {
    static final int PISTOL = 0, RIFLE = 1, EXPLOSION = 2, BITE = 3, GROAN = 4, GROAN_DEEP = 5, SCREAM = 6,
            CLICK = 7, RADIO = 8, PHONE = 9, SHRIEK = 10, THUD = 11, SIREN = 12, ROTOR = 13, BARK = 14,
            HORN = 15, CANNON = 16, MG = 17, SNIPER = 18, COLLAPSE = 19, CRASH = 20, HOSE = 21, AMB_SIREN = 22, ENGINE = 23,
            JET = 24, SPIT = 25, BURST = 26, ALARM = 27, SHIELD = 28;
    static final int COUNT = 29;

    /** Shortest gap between two plays of the same sound, in seconds, so crowds don't turn into noise. */
    static final float[] MIN_GAP = {0.05f, 0.04f, 0.08f, 0.09f, 0.9f, 1.4f, 0.8f, 0f, 2f, 2f, 2f, 0.1f, 4f, 1.2f, 0.8f,
            4f, 0.3f, 0.06f, 0.3f, 1f, 0.2f, 1.5f, 4f, 2.5f,
            4f, 0.3f, 0.2f, 8f, 0.15f};
    /** Background sounds that get on the nerves when they loop; these fade and space out with repetition. */
    static final boolean[] REPEATS = new boolean[COUNT];

    static {
        for (int id : new int[] {GROAN, GROAN_DEEP, SCREAM, RADIO, PHONE, SHRIEK, SIREN, ROTOR, BARK, HORN,
                HOSE, AMB_SIREN, ENGINE, JET, SPIT, ALARM, COLLAPSE}) REPEATS[id] = true;
    }

    /**
     * Listener fatigue: every play of an effect adds "heat" that cools by half every fifteen seconds. A hot repeating
     * sound waits longer between plays and comes in quieter, so a siren or groan that keeps firing settles into
     * the background instead of hammering the same sample.
     */
    static final class Fatigue {
        private final long[] last = new long[COUNT];
        private final float[] heat = new float[COUNT];

        Fatigue() {
            java.util.Arrays.fill(last, Long.MIN_VALUE / 2);
        }

        /** Volume multiplier for playing {@code id} at {@code now} (ms), or 0 to skip it. */
        float gain(int id, long now) {
            long since = now - last[id];
            float h = heat[id] * (float) Math.pow(0.5, since / 15000.0);
            boolean rep = REPEATS[id];
            float gap = MIN_GAP[id] * (rep ? 1 + h * 0.35f : 1);
            if (since < gap * 1000) return 0;
            last[id] = now;
            heat[id] = h + 1;
            return rep ? Math.max(0.25f, 1 / (1 + h * 0.6f)) : Math.max(0.6f, 1 / (1 + h * 0.03f));
        }
    }

    private Sfx() {
    }
}
