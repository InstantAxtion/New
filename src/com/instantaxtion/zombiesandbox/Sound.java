package com.instantaxtion.zombiesandbox;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.util.Random;

/**
 * Plays sound effects (SoundPool) and looping music (MediaPlayer). The audio is synthesized by
 * {@link Synth} on a background thread the first time the app runs and cached as WAV files.
 */
final class Sound {
    /** Bump when the synthesized audio changes so old cached files are regenerated. */
    private static final int AUDIO_VERSION = 1;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final SoundPool pool;
    private final int[] ids = new int[Sfx.COUNT];
    private final boolean[] loaded = new boolean[Sfx.COUNT];
    private final long[] lastPlay = new long[Sfx.COUNT];
    private final String[] trackPaths = new String[Synth.TRACKS];
    private final AudioAttributes musicAttrs;

    private MediaPlayer player;
    private int playingTrack = -1, wantTrack = -1;
    private float musicVol = 0.5f, sfxVol = 0.75f;
    private boolean paused, released;

    Sound(Context context) {
        ctx = context.getApplicationContext();
        AudioAttributes sfxAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        musicAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(12).setAudioAttributes(sfxAttrs).build();
        pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                for (int i = 0; i < Sfx.COUNT; i++) if (ids[i] == sampleId && status == 0) loaded[i] = true;
            }
        });
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                prepareAudio();
            }
        }, "synth");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private void prepareAudio() {
        File dir = ctx.getCacheDir();
        for (int i = 0; i < Sfx.COUNT; i++) {
            final int id = i;
            final File f = new File(dir, "sfx" + AUDIO_VERSION + "_" + i + ".wav");
            try {
                if (!f.exists()) Synth.writeWav(f, Synth.effect(i));
            } catch (Exception e) {
                continue;
            }
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (!released) ids[id] = pool.load(f.getPath(), 1);
                }
            });
        }
        // Menu music first: it is what the player hears first.
        for (int track = 0; track < Synth.TRACKS; track++) {
            final int tr = track;
            final File f = new File(dir, "music" + AUDIO_VERSION + "_" + track + ".wav");
            try {
                if (!f.exists()) Synth.writeWav(f, Synth.music(track));
            } catch (Exception e) {
                continue;
            }
            main.post(new Runnable() {
                @Override
                public void run() {
                    trackPaths[tr] = f.getPath();
                    updateMusic();
                }
            });
        }
    }

    /** Plays an effect. {@code pan} runs from -1 (left) to 1 (right). */
    void play(int id, float volume, float pan) {
        if (released || !loaded[id] || sfxVol <= 0 || paused) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastPlay[id] < Sfx.MIN_GAP[id] * 1000) return;
        lastPlay[id] = now;
        float v = Math.max(0, Math.min(1, volume)) * sfxVol;
        float left = v * Math.min(1, 1 - pan), right = v * Math.min(1, 1 + pan);
        float rate = id == Sfx.CLICK ? 1f : 0.9f + rnd.nextFloat() * 0.2f;
        pool.play(ids[id], left, right, id == Sfx.CLICK ? 2 : 1, 0, rate);
    }

    void setVolumes(float music, float sfx) {
        musicVol = music;
        sfxVol = sfx;
        if (player != null) player.setVolume(music, music);
        updateMusic();
    }

    /** Selects the music track to loop (Synth.TRACK_MENU or Synth.TRACK_GAME). */
    void playTrack(int track) {
        if (track == wantTrack) return;
        wantTrack = track;
        updateMusic();
    }

    private void updateMusic() {
        if (released) return;
        int target = musicVol > 0 && wantTrack >= 0 && trackPaths[wantTrack] != null ? wantTrack : -1;
        if (target != playingTrack) {
            stopPlayer();
            if (target >= 0) {
                try {
                    MediaPlayer mp = new MediaPlayer();
                    mp.setAudioAttributes(musicAttrs);
                    mp.setDataSource(trackPaths[target]);
                    mp.setLooping(true);
                    mp.prepare();
                    player = mp;
                    playingTrack = target;
                } catch (Exception e) {
                    stopPlayer();
                }
            }
        }
        if (player != null) {
            player.setVolume(musicVol, musicVol);
            if (paused) {
                if (player.isPlaying()) player.pause();
            } else if (!player.isPlaying()) {
                player.start();
            }
        }
    }

    private void stopPlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
        }
        player = null;
        playingTrack = -1;
    }

    void pause() {
        paused = true;
        pool.autoPause();
        updateMusic();
    }

    void resume() {
        paused = false;
        pool.autoResume();
        updateMusic();
    }

    void release() {
        released = true;
        stopPlayer();
        pool.release();
    }
}
