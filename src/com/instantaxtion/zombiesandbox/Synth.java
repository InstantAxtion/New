package com.instantaxtion.zombiesandbox;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 * Generates every sound in the game from code (no audio files ship with the app): gunshots, explosions,
 * zombie groans and two looping music tracks. Output is 16-bit mono PCM at {@link #RATE}.
 */
final class Synth {
    static final int RATE = 22050;
    static final int TRACK_MENU = 0, TRACK_GAME = 1, TRACK_CALM = 2, TRACK_ACTION = 3;
    static final int TRACKS = 4;
    private static final float TAU = (float) (Math.PI * 2);

    private Synth() {
    }

    // ------------------------------------------------------------------ sound effects

    static short[] effect(int id) {
        Random r = new Random(1234 + id);
        float[] out;
        switch (id) {
            case Sfx.PISTOL: out = gunshot(r, 0.22f, 26, 0.5f, 140); break;
            case Sfx.RIFLE: out = gunshot(r, 0.16f, 36, 0.32f, 190); break;
            case Sfx.EXPLOSION: out = explosion(r); break;
            case Sfx.BITE: out = bite(r); break;
            case Sfx.GROAN: out = groan(r, 88, 1.1f); break;
            case Sfx.GROAN_DEEP: out = groan(r, 52, 1.5f); break;
            case Sfx.SCREAM: out = scream(r); break;
            case Sfx.RADIO: out = radio(r); break;
            case Sfx.PHONE: out = phone(); break;
            case Sfx.SHRIEK: out = shriek(r); break;
            case Sfx.THUD: out = thud(r); break;
            case Sfx.SIREN: out = siren(); break;
            case Sfx.ROTOR: out = rotor(r); break;
            case Sfx.BARK: out = bark(r); break;
            case Sfx.HORN: out = horn(); break;
            case Sfx.CANNON: out = cannon(r); break;
            case Sfx.MG: out = gunshot(r, 0.1f, 45, 0.4f, 170); break;
            case Sfx.SNIPER: out = sniper(r); break;
            case Sfx.COLLAPSE: out = collapse(r); break;
            case Sfx.CRASH: out = crash(r); break;
            case Sfx.HOSE: out = hose(r); break;
            case Sfx.AMB_SIREN: out = ambulance(); break;
            case Sfx.ENGINE: out = engine(r); break;
            default: out = click(); break;
        }
        return toPcm(out, id == Sfx.CLICK ? 0.5f : 0.92f);
    }

    private static float[] gunshot(Random r, float dur, float decay, float bright, float thump) {
        int n = (int) (dur * RATE);
        float[] o = new float[n];
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            lp += (r.nextFloat() * 2 - 1 - lp) * bright;
            float crack = i < 40 ? (r.nextFloat() * 2 - 1) * (1 - i / 40f) : 0;
            o[i] = lp * (float) Math.exp(-t * decay) * 1.2f
                    + (float) Math.sin(TAU * thump * t * (1 - t * 2)) * (float) Math.exp(-t * 20) * 0.8f
                    + crack * 0.6f;
        }
        return o;
    }

    private static float[] explosion(Random r) {
        int n = (int) (1.8f * RATE);
        float[] o = new float[n];
        float lp = 0, lp2 = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float white = r.nextFloat() * 2 - 1;
            lp += (white - lp) * 0.06f;
            lp2 += (lp - lp2) * 0.2f;
            float env = Math.min(1, t / 0.005f) * (float) Math.exp(-t * 2.4f);
            o[i] = lp2 * 6 * env
                    + (float) Math.sin(TAU * (48 - 18 * t) * t) * (float) Math.exp(-t * 2.8f) * 0.7f
                    + white * (float) Math.exp(-t * 35) * 0.5f;
        }
        return o;
    }

    private static float[] bite(Random r) {
        int n = (int) (0.3f * RATE);
        float[] o = new float[n];
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            lp += (r.nextFloat() * 2 - 1 - lp) * 0.18f;
            float wobble = 0.5f + 0.5f * (float) Math.sin(TAU * 28 * t);
            float crunch = r.nextFloat() < 0.004f ? (r.nextFloat() * 2 - 1) * 3 : 0;
            o[i] = (lp * 2.5f * wobble + crunch) * (float) Math.exp(-t * 9)
                    + (float) Math.sin(TAU * 90 * t) * (float) Math.exp(-t * 14) * 0.4f;
        }
        return o;
    }

    private static float[] groan(Random r, float f0, float dur) {
        int n = (int) (dur * RATE);
        float[] o = new float[n];
        float phase = 0, lp = 0, lp2 = 0, breath = 0;
        float drift = 0.85f + r.nextFloat() * 0.3f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE, u = t / dur;
            float f = f0 * drift * (1 + 0.22f * (float) Math.sin(Math.PI * u) - 0.15f * u)
                    * (1 + 0.03f * (float) Math.sin(TAU * 5.5f * t));
            phase += f / RATE;
            phase -= (int) phase;
            float saw = phase * 2 - 1;
            lp += (saw - lp) * 0.16f;
            lp2 += (lp - lp2) * 0.3f;
            breath += (r.nextFloat() * 2 - 1 - breath) * 0.1f;
            float env = smooth(Math.min(1, t / 0.18f)) * smooth(Math.min(1, (dur - t) / 0.35f));
            float rasp = 0.75f + 0.25f * (float) Math.sin(TAU * 31 * t);
            o[i] = (lp2 * 1.6f * rasp + breath * 0.5f) * env;
        }
        return o;
    }

    private static float[] scream(Random r) {
        float dur = 0.6f;
        int n = (int) (dur * RATE);
        float[] o = new float[n];
        float phase = 0, lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE, u = t / dur;
            float f = 600 + 200 * (float) Math.sin(Math.PI * u) + 30 * (float) Math.sin(TAU * 7 * t);
            phase += f / RATE;
            phase -= (int) phase;
            float v = (float) Math.sin(TAU * phase) * 0.7f + (phase * 2 - 1) * 0.3f;
            lp += (v - lp) * 0.45f;
            float env = smooth(Math.min(1, t / 0.03f)) * (float) Math.pow(1 - u, 1.4f);
            o[i] = (lp + (r.nextFloat() * 2 - 1) * 0.08f) * env;
        }
        return o;
    }

    /** Walkie-talkie: a burst of squelch, then a short chirp. */
    private static float[] radio(Random r) {
        int n = (int) (0.32f * RATE);
        float[] o = new float[n];
        float lp = 0, lp2 = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float white = r.nextFloat() * 2 - 1;
            lp += (white - lp) * 0.5f;
            lp2 += (lp - lp2) * 0.08f;
            float squelch = (lp - lp2) * (t < 0.14f ? 1 : 0) * Math.min(1, t / 0.01f);
            float chirp = t > 0.17f && t < 0.26f ? (float) Math.sin(TAU * (1500 + 900 * (t - 0.17f) / 0.09f) * t) * 0.5f : 0;
            o[i] = squelch * 1.4f + chirp;
        }
        return o;
    }

    /** A phone ringing: two quick trills of mixed tones. */
    private static float[] phone() {
        int n = (int) (0.9f * RATE);
        float[] o = new float[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            boolean on = (t < 0.35f) || (t > 0.5f && t < 0.85f);
            float trill = (float) Math.sin(TAU * 20 * t) > 0 ? 1 : 0.35f;
            float v = (float) Math.sin(TAU * 1300 * t) + (float) Math.sin(TAU * 1700 * t);
            o[i] = on ? v * trill * 0.5f : 0;
        }
        return o;
    }

    /** The screamer: a harsh, rising shriek. */
    private static float[] shriek(Random r) {
        float dur = 1.1f;
        int n = (int) (dur * RATE);
        float[] o = new float[n];
        float phase = 0, lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE, u = t / dur;
            float f = 900 + 700 * u + 120 * (float) Math.sin(TAU * 13 * t);
            phase += f / RATE;
            phase -= (int) phase;
            float v = (phase * 2 - 1) * 0.6f + (float) Math.sin(TAU * phase * 2) * 0.3f + (r.nextFloat() * 2 - 1) * 0.3f;
            lp += (v - lp) * 0.6f;
            float env = smooth(Math.min(1, t / 0.05f)) * (float) Math.pow(1 - u, 0.8f);
            o[i] = lp * env;
        }
        return o;
    }

    /** A dull impact: a shove, a rifle butt, a car hitting something, a door being battered. */
    private static float[] thud(Random r) {
        int n = (int) (0.18f * RATE);
        float[] o = new float[n];
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            lp += (r.nextFloat() * 2 - 1 - lp) * 0.08f;
            o[i] = ((float) Math.sin(TAU * (95 - 160 * t) * t) * 0.9f + lp * 2) * (float) Math.exp(-t * 22);
        }
        return o;
    }

    /** A train horn: two notes together, long and loud. */
    private static float[] horn() {
        float dur = 1.3f;
        int n = (int) (dur * RATE);
        float[] o = new float[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float v = 0;
            float[] f = {311, 370, 466};
            for (float fr : f) {
                float ph = (fr * t) % 1f;
                v += (ph < 0.5f ? 1 : -1) * 0.22f + (float) Math.sin(TAU * fr * t) * 0.2f;
            }
            o[i] = v * smooth(Math.min(1, t / 0.08f)) * smooth(Math.min(1, (dur - t) / 0.2f));
        }
        return o;
    }

    /** A tank gun: a sharp crack and a long deep boom. */
    private static float[] cannon(Random r) {
        int n = (int) (1.4f * RATE);
        float[] o = new float[n];
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float white = r.nextFloat() * 2 - 1;
            lp += (white - lp) * 0.1f;
            o[i] = white * (float) Math.exp(-t * 60) * 0.9f + lp * 4 * (float) Math.exp(-t * 4)
                    + (float) Math.sin(TAU * (70 - 30 * t) * t) * (float) Math.exp(-t * 3.5f) * 0.9f;
        }
        return o;
    }

    /** A sniper rifle: a hard crack with a long echo. */
    private static float[] sniper(Random r) {
        int n = (int) (0.9f * RATE);
        float[] o = new float[n];
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float white = r.nextFloat() * 2 - 1;
            lp += (white - lp) * 0.3f;
            float echo = t > 0.18f ? (float) Math.exp(-(t - 0.18f) * 8) * 0.35f : 0;
            o[i] = white * (float) Math.exp(-t * 90) + lp * ((float) Math.exp(-t * 25) * 1.2f + echo)
                    + (float) Math.sin(TAU * 110 * t) * (float) Math.exp(-t * 18) * 0.6f;
        }
        return o;
    }

    /** A building coming down: a long low rumble with crashes in it. */
    private static float[] collapse(Random r) {
        int n = (int) (2.6f * RATE);
        float[] o = new float[n];
        float lp = 0, lp2 = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float white = r.nextFloat() * 2 - 1;
            lp += (white - lp) * 0.04f;
            lp2 += (white - lp2) * 0.25f;
            float crashes = (float) Math.max(0, Math.sin(t * 23) * Math.sin(t * 7.3)) * (float) Math.exp(-t * 1.2f);
            o[i] = lp * 7 * smooth(Math.min(1, t / 0.1f)) * (float) Math.exp(-t * 1.1f) + lp2 * crashes * 0.9f;
        }
        return o;
    }

    /** Two cars colliding: crunching metal and breaking glass. */
    private static float[] crash(Random r) {
        int n = (int) (0.6f * RATE);
        float[] o = new float[n];
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float white = r.nextFloat() * 2 - 1;
            lp += (white - lp) * 0.15f;
            float glass = t > 0.05f ? (float) Math.sin(TAU * (2600 + r.nextFloat() * 1800) * t) * (float) Math.exp(-(t - 0.05f) * 14) * 0.25f : 0;
            o[i] = lp * 3 * (float) Math.exp(-t * 9) + (float) Math.sin(TAU * 80 * t) * (float) Math.exp(-t * 16) + glass;
        }
        return o;
    }

    /** Water from a fire hose: steady hiss. */
    private static float[] hose(Random r) {
        float dur = 1f;
        int n = (int) (dur * RATE);
        float[] o = new float[n];
        float hp = 0, prev = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float white = r.nextFloat() * 2 - 1;
            hp = 0.9f * (hp + white - prev);
            prev = white;
            o[i] = hp * 0.35f * smooth(Math.min(1, t / 0.1f)) * smooth(Math.min(1, (dur - t) / 0.15f));
        }
        return o;
    }

    /** An ambulance: a two-tone hi-lo siren. */
    private static float[] ambulance() {
        float dur = 1.4f;
        int n = (int) (dur * RATE);
        float[] o = new float[n];
        float phase = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float f = ((int) (t / 0.35f)) % 2 == 0 ? 960 : 770;
            phase += f / RATE;
            phase -= (int) phase;
            o[i] = ((float) Math.sin(TAU * phase) * 0.6f + (phase < 0.5f ? 0.2f : -0.2f))
                    * smooth(Math.min(1, t / 0.05f)) * smooth(Math.min(1, (dur - t) / 0.1f));
        }
        return o;
    }

    /** A tank engine and tracks: a low diesel rumble with clanking. */
    private static float[] engine(Random r) {
        float dur = 1.2f;
        int n = (int) (dur * RATE);
        float[] o = new float[n];
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            lp += (r.nextFloat() * 2 - 1 - lp) * 0.05f;
            float firing = (float) Math.pow(Math.max(0, Math.sin(TAU * 28 * t)), 6);
            float clank = ((int) (t * 9)) % 2 == 0 && (t * 9) % 1 < 0.05f ? (r.nextFloat() - 0.5f) : 0;
            o[i] = (firing * 0.6f + lp * 2 + clank * 0.5f) * smooth(Math.min(1, t / 0.1f)) * smooth(Math.min(1, (dur - t) / 0.15f));
        }
        return o;
    }

    /** A dog: two quick barks. */
    private static float[] bark(Random r) {
        float dur = 0.42f;
        int n = (int) (dur * RATE);
        float[] o = new float[n];
        float phase = 0, lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float u = t < 0.2f ? t : t - 0.22f;
            if (u < 0 || u > 0.14f) continue;
            float f = 520 - 1500 * u;
            phase += f / RATE;
            phase -= (int) phase;
            lp += (r.nextFloat() * 2 - 1 - lp) * 0.35f;
            float tone = (float) Math.sin(TAU * phase) + 0.5f * (float) Math.sin(TAU * phase * 2) + 0.3f * (phase < 0.5f ? 1 : -1);
            float env = smooth(Math.min(1, u / 0.012f)) * (float) Math.exp(-u * 18);
            o[i] = (tone * 0.55f + lp * 0.6f) * env;
        }
        return o;
    }

    /** A police siren: one rise-and-fall wail. */
    private static float[] siren() {
        float dur = 1.4f;
        int n = (int) (dur * RATE);
        float[] o = new float[n];
        float phase = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE, u = t / dur;
            float f = 650 + 450 * (float) Math.sin(Math.PI * u);
            phase += f / RATE;
            phase -= (int) phase;
            float v = (float) Math.sin(TAU * phase) * 0.7f + (phase < 0.5f ? 0.25f : -0.25f);
            o[i] = v * smooth(Math.min(1, t / 0.05f)) * smooth(Math.min(1, (dur - t) / 0.1f));
        }
        return o;
    }

    /** Helicopter rotor: a few low chops. */
    private static float[] rotor(Random r) {
        int n = (int) (0.6f * RATE);
        float[] o = new float[n];
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            lp += (r.nextFloat() * 2 - 1 - lp) * 0.05f;
            float chop = (float) Math.pow(Math.max(0, Math.sin(TAU * 11 * t)), 6);
            o[i] = (lp * 4 * chop + (float) Math.sin(TAU * 55 * t) * 0.3f * chop) * smooth(Math.min(1, t / 0.05f))
                    * smooth(Math.min(1, (0.6f - t) / 0.05f));
        }
        return o;
    }

    private static float[] click() {
        int n = (int) (0.05f * RATE);
        float[] o = new float[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            o[i] = (float) Math.sin(TAU * 1250 * t) * (float) Math.exp(-t * 110);
        }
        return o;
    }

    // ------------------------------------------------------------------ music

    private static float midi(int note) {
        return 440f * (float) Math.pow(2, (note - 69) / 12.0);
    }

    /** Renders one seamlessly looping music track. */
    static short[] music(int track) {
        return track == TRACK_MENU ? menuTrack() : track == TRACK_CALM ? calmTrack() : track == TRACK_ACTION ? actionTrack()
                : gameTrack();
    }

    /** Slow, dark ambient loop for the menus: A minor, F, D minor, E. */
    private static short[] menuTrack() {
        float bar = 60f / 70f * 4;
        int[][] chords = {{45, 48, 52}, {41, 45, 48}, {45, 50, 53}, {44, 47, 52}};
        int n = (int) (bar * 8 * RATE);
        float[] pad = new float[n], rest = new float[n];
        Random r = new Random(7);
        for (int k = 0; k < 4; k++) {
            float start = k * bar * 2, len = bar * 2;
            for (int note : chords[k]) {
                addPadNote(pad, midi(note), start, len, 1.2f, 1.8f, 0.16f);
                addPadNote(pad, midi(note + 12), start, len, 1.5f, 1.8f, 0.05f);
            }
            addSine(rest, midi(chords[k][0] - 12), start, len + 1f, 0.8f, 1.5f, 0.3f);
            // Sparse music-box notes.
            for (int s = 0; s < 16; s++) {
                if (r.nextFloat() > 0.4f) continue;
                int note = chords[k][r.nextInt(3)] + 24 + (r.nextFloat() < 0.3f ? 12 : 0);
                addPluck(rest, midi(note), start + s * bar / 8, 1.6f, 0.12f);
            }
        }
        lowpass(pad, 0.08f);
        float[] mix = new float[n];
        for (int i = 0; i < n; i++) mix[i] = pad[i] + rest[i];
        echo(mix, 0.42f, 0.4f);
        return toPcm(mix, 0.8f);
    }

    /** Tense 112 BPM loop for gameplay: drums, driving bass, pads and a plucked arpeggio. */
    /** Quiet streets: slow pads and a few lonely notes. */
    private static short[] calmTrack() {
        float beat = 60f / 70f, bar = beat * 4;
        int[][] chords = {{50, 53, 57}, {46, 50, 53}, {48, 52, 55}, {45, 48, 52}};
        int n = (int) (bar * 8 * RATE);
        float[] pad = new float[n], lead = new float[n];
        Random r = new Random(5);
        for (int b = 0; b < 8; b++) {
            float t0 = b * bar;
            int[] ch = chords[b % 4];
            for (int note : ch) addPadNote(pad, midi(note), t0, bar, 1.2f, 1.5f, 0.07f);
            for (int s = 0; s < 3; s++)
                if (r.nextFloat() < 0.6f) addPluck(lead, midi(ch[r.nextInt(3)] + 12), t0 + (s * 1.3f + r.nextFloat()) * beat, 1.2f, 0.06f);
        }
        lowpass(pad, 0.07f);
        echo(lead, 0.45f, 0.4f);
        float[] mix = new float[n];
        for (int i = 0; i < n; i++) mix[i] = (float) Math.tanh(pad[i] + lead[i]);
        return toPcm(mix, 0.6f);
    }

    /** Heavy fighting: fast drums, driving bass and a stabbing lead. */
    private static short[] actionTrack() {
        float beat = 60f / 140f, bar = beat * 4;
        int[][] chords = {{40, 43, 47}, {40, 43, 47}, {36, 40, 43}, {38, 42, 45}};
        int n = (int) (bar * 8 * RATE);
        float[] pad = new float[n], bass = new float[n], drums = new float[n], lead = new float[n];
        Random r = new Random(23);
        for (int b = 0; b < 8; b++) {
            float t0 = b * bar;
            int[] ch = chords[b % 4];
            for (int note : ch) addPadNote(pad, midi(note + 12), t0, bar, 0.1f, 0.3f, 0.06f);
            for (int e = 0; e < 16; e++) addBass(bass, midi(ch[0] - 12 + (e % 4 == 3 ? 7 : 0)), t0 + e * beat / 4, beat / 4);
            for (int q = 0; q < 4; q++) {
                float t = t0 + q * beat;
                addKick(drums, t);
                if (q % 2 == 1) addSnare(drums, t, r);
                for (int h = 0; h < 4; h++) addHat(drums, t + h * beat / 4, r, h % 2 == 0 ? 0.1f : 0.05f);
            }
            if (b % 2 == 1)
                for (int s = 0; s < 6; s++) addPluck(lead, midi(ch[s % 3] + 24), t0 + s * beat * 0.66f, 0.3f, 0.08f);
        }
        lowpass(bass, 0.15f);
        float[] mix = new float[n];
        echo(lead, 0.21f, 0.3f);
        for (int i = 0; i < n; i++) mix[i] = (float) Math.tanh((pad[i] + bass[i] * 0.9f + drums[i] * 1.1f + lead[i]) * 1.4f);
        return toPcm(mix, 0.8f);
    }

    private static short[] gameTrack() {
        float beat = 60f / 112f, bar = beat * 4;
        int[][] chords = {{45, 48, 52}, {45, 48, 52}, {41, 45, 48}, {43, 47, 50},
                {45, 48, 52}, {45, 48, 52}, {41, 45, 48}, {44, 47, 52}};
        int n = (int) (bar * 8 * RATE);
        float[] pad = new float[n], bass = new float[n], drums = new float[n], lead = new float[n];
        Random r = new Random(11);
        for (int b = 0; b < 8; b++) {
            float t0 = b * bar;
            int[] ch = chords[b];
            for (int note : ch) addPadNote(pad, midi(note), t0, bar, 0.25f, 0.5f, 0.08f);
            int[] pattern = {0, 0, 12, 0, 0, 12, 0, 7};
            for (int e = 0; e < 8; e++) addBass(bass, midi(ch[0] - 24 + pattern[e]), t0 + e * beat / 2, beat / 2);
            for (int q = 0; q < 4; q++) {
                float t = t0 + q * beat;
                if (q == 0 || q == 2) addKick(drums, t);
                if (q == 1 || q == 3) addSnare(drums, t, r);
                addHat(drums, t, r, 0.12f);
                addHat(drums, t + beat / 2, r, 0.07f);
            }
            if (b % 4 == 3) addKick(drums, t0 + beat * 3.5f);
            if (b >= 4) {
                int[] arp = {0, 1, 2, 1};
                for (int s = 0; s < 8; s++)
                    addPluck(lead, midi(ch[arp[s % 4]] + 12), t0 + s * beat / 2, 0.5f, 0.07f);
            }
        }
        lowpass(pad, 0.1f);
        lowpass(bass, 0.12f);
        float[] mix = new float[n];
        for (int i = 0; i < n; i++) mix[i] = pad[i] + bass[i] * 0.8f + drums[i] + lead[i];
        echo(lead, 0.27f, 0.3f);
        for (int i = 0; i < n; i++) mix[i] = (float) Math.tanh((mix[i] + lead[i] * 0.4f) * 1.3f);
        return toPcm(mix, 0.8f);
    }

    /** Two detuned saws with a slow attack and release; the tail wraps around to the loop start. */
    private static void addPadNote(float[] buf, float f, float start, float len, float attack, float release, float vol) {
        int s0 = (int) (start * RATE), total = (int) ((len + release) * RATE);
        float p1 = 0, p2 = 0.5f;
        for (int i = 0; i < total; i++) {
            float t = i / (float) RATE;
            float env = smooth(Math.min(1, t / attack));
            if (t > len) env *= smooth(Math.max(0, 1 - (t - len) / release));
            p1 += f * 1.003f / RATE;
            p2 += f * 0.997f / RATE;
            p1 -= (int) p1;
            p2 -= (int) p2;
            buf[(s0 + i) % buf.length] += (p1 + p2 - 1) * env * vol;
        }
    }

    private static void addSine(float[] buf, float f, float start, float len, float attack, float release, float vol) {
        int s0 = (int) (start * RATE), total = (int) ((len + release) * RATE);
        for (int i = 0; i < total; i++) {
            float t = i / (float) RATE;
            float env = smooth(Math.min(1, t / attack));
            if (t > len) env *= smooth(Math.max(0, 1 - (t - len) / release));
            buf[(s0 + i) % buf.length] += (float) Math.sin(TAU * f * t) * env * vol;
        }
    }

    private static void addPluck(float[] buf, float f, float start, float decay, float vol) {
        int s0 = (int) (start * RATE), total = (int) (decay * 2 * RATE);
        for (int i = 0; i < total; i++) {
            float t = i / (float) RATE;
            float env = Math.min(1, t / 0.004f) * (float) Math.exp(-t * 3 / decay);
            float v = (float) Math.sin(TAU * f * t) + 0.3f * (float) Math.sin(TAU * f * 2 * t);
            buf[(s0 + i) % buf.length] += v * env * vol;
        }
    }

    private static void addBass(float[] buf, float f, float start, float len) {
        int s0 = (int) (start * RATE), total = (int) (len * RATE);
        float p = 0;
        for (int i = 0; i < total; i++) {
            float t = i / (float) RATE;
            p += f / RATE;
            p -= (int) p;
            float env = Math.min(1, t / 0.005f) * (float) Math.exp(-t * 5) * Math.min(1, (len - t) / 0.01f);
            buf[(s0 + i) % buf.length] += (p * 2 - 1) * env * 0.5f;
        }
    }

    private static void addKick(float[] buf, float start) {
        int s0 = (int) (start * RATE), total = (int) (0.3f * RATE);
        float p = 0;
        for (int i = 0; i < total; i++) {
            float t = i / (float) RATE;
            p += (45 + 90 * (float) Math.exp(-t * 30)) / RATE;
            buf[(s0 + i) % buf.length] += (float) Math.sin(TAU * p) * (float) Math.exp(-t * 9) * 0.8f;
        }
    }

    private static void addSnare(float[] buf, float start, Random r) {
        int s0 = (int) (start * RATE), total = (int) (0.22f * RATE);
        float lp = 0;
        for (int i = 0; i < total; i++) {
            float t = i / (float) RATE;
            float white = r.nextFloat() * 2 - 1;
            lp += (white - lp) * 0.3f;
            float body = (float) Math.sin(TAU * 190 * t) * (float) Math.exp(-t * 25) * 0.3f;
            buf[(s0 + i) % buf.length] += ((white - lp) * 0.5f + lp * 0.3f) * (float) Math.exp(-t * 16) + body;
        }
    }

    private static void addHat(float[] buf, float start, Random r, float vol) {
        int s0 = (int) (start * RATE), total = (int) (0.06f * RATE);
        float lp = 0;
        for (int i = 0; i < total; i++) {
            float t = i / (float) RATE;
            float white = r.nextFloat() * 2 - 1;
            lp += (white - lp) * 0.5f;
            buf[(s0 + i) % buf.length] += (white - lp) * (float) Math.exp(-t * 60) * vol;
        }
    }

    /** One-pole low-pass run twice around the loop so the filter state matches at the seam. */
    private static void lowpass(float[] buf, float a) {
        float y = 0;
        float[] out = new float[buf.length];
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < buf.length; i++) {
                y += (buf[i] - y) * a;
                out[i] = y;
            }
        System.arraycopy(out, 0, buf, 0, buf.length);
    }

    /** Feedback echo that wraps around the loop. */
    private static void echo(float[] buf, float delaySec, float feedback) {
        int d = (int) (delaySec * RATE);
        float[] src = buf.clone();
        for (int pass = 0; pass < 3; pass++)
            for (int i = 0; i < buf.length; i++)
                buf[i] = src[i] + buf[(i - d + buf.length) % buf.length] * feedback;
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    private static short[] toPcm(float[] in, float peak) {
        float max = 1e-6f;
        for (float v : in) max = Math.max(max, Math.abs(v));
        float g = peak / max;
        short[] out = new short[in.length];
        for (int i = 0; i < in.length; i++) out[i] = (short) Math.round(in[i] * g * 32767);
        return out;
    }

    // ------------------------------------------------------------------ WAV output

    static void writeWav(File file, short[] pcm) throws IOException {
        File tmp = new File(file.getPath() + ".tmp");
        OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp));
        try {
            int dataLen = pcm.length * 2;
            out.write(new byte[]{'R', 'I', 'F', 'F'});
            writeInt(out, 36 + dataLen);
            out.write(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
            writeInt(out, 16);
            writeShort(out, 1);
            writeShort(out, 1);
            writeInt(out, RATE);
            writeInt(out, RATE * 2);
            writeShort(out, 2);
            writeShort(out, 16);
            out.write(new byte[]{'d', 'a', 't', 'a'});
            writeInt(out, dataLen);
            byte[] buf = new byte[pcm.length * 2];
            for (int i = 0; i < pcm.length; i++) {
                buf[i * 2] = (byte) pcm[i];
                buf[i * 2 + 1] = (byte) (pcm[i] >> 8);
            }
            out.write(buf);
        } finally {
            out.close();
        }
        if (!tmp.renameTo(file)) throw new IOException("rename failed: " + file);
    }

    private static void writeInt(OutputStream o, int v) throws IOException {
        o.write(v);
        o.write(v >> 8);
        o.write(v >> 16);
        o.write(v >> 24);
    }

    private static void writeShort(OutputStream o, int v) throws IOException {
        o.write(v);
        o.write(v >> 8);
    }
}
