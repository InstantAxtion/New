package com.instantaxtion.zombiesandbox;

/** Sound effect ids shared by the simulation (which emits them) and {@link Sound} (which plays them). */
final class Sfx {
    static final int PISTOL = 0, RIFLE = 1, EXPLOSION = 2, BITE = 3, GROAN = 4, GROAN_DEEP = 5, SCREAM = 6,
            CLICK = 7, RADIO = 8, PHONE = 9, SHRIEK = 10, THUD = 11, SIREN = 12, ROTOR = 13, BARK = 14;
    static final int COUNT = 15;

    /** Shortest gap between two plays of the same sound, in seconds, so crowds don't turn into noise. */
    static final float[] MIN_GAP = {0.05f, 0.04f, 0.08f, 0.09f, 0.35f, 0.5f, 0.3f, 0f, 0.5f, 0.8f, 1f, 0.1f, 1.5f, 0.3f, 0.35f};

    private Sfx() {
    }
}
